package com.callrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import com.callrecorder.model.CallType
import com.callrecorder.recorder.MediaRecorderAudioRecorder
import com.callrecorder.recorder.RecordingRepository
import com.callrecorder.service.RecordingCoordinator
import com.callrecorder.settings.SettingsRepository
import org.json.JSONObject
import java.io.File

/**
 * Long-lived foreground service.
 *
 * Android 14+ forbids creating a `microphone` foreground service while the app is
 * in the background, and forbids launching one from a BOOT_COMPLETED receiver. So
 * the service is *armed once* from a place the platform allows (the app's own UI,
 * or a notification tap) and then stays alive, listening for call-state intents.
 * When a call starts we only start MediaRecorder on an already-running service,
 * which is allowed.
 */
class CallRecorderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: SettingsRepository
    private lateinit var coordinator: RecordingCoordinator
    private val recorder = MediaRecorderAudioRecorder()

    private var currentPhoneNumber: String? = null
    private var currentCallType: CallType = CallType.UNKNOWN
    private var lastCompletedPath: String? = null

    private var pendingStart: Runnable? = null
    private var meter: Runnable? = null
    private var peakAmplitude = 0
    private var amplitudeSamples = 0L
    private var amplitudeSum = 0L
    private var startedAt = 0L

    private var armed = false

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        coordinator = RecordingCoordinator(recorder, RecordingRepository(recordingsDir()))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_ARM
        when {
            action == ACTION_DISARM -> {
                disarm()
                stopSelf()
                return START_NOT_STICKY
            }
            action == ACTION_CALL_STATE && intent != null -> onCallState(intent)
            else -> arm()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        pendingStart?.let { handler.removeCallbacks(it) }
        stopMeter()
        if (coordinator.isRecording()) coordinator.stopRecording()
        armed = false
        isRunning = false
        super.onDestroy()
    }

    // ---------------------------------------------------------------- arming

    private fun arm() {
        if (armed) return
        try {
            val notification = buildNotification(getString(R.string.notification_idle))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            armed = true
            isRunning = true
            settings.setRecorderEnabled(true)
            Log.i(TAG, "armed")
        } catch (t: Throwable) {
            // SecurityException (RECORD_AUDIO missing) or
            // ForegroundServiceStartNotAllowedException (started from background).
            armed = false
            isRunning = false
            Log.e(TAG, "cannot arm microphone foreground service", t)
            postArmNotification(this, t.javaClass.simpleName + ": " + t.message)
            stopSelf()
        }
    }

    private fun disarm() {
        armed = false
        isRunning = false
        settings.setRecorderEnabled(false)
        pendingStart?.let { handler.removeCallbacks(it) }
        pendingStart = null
        if (coordinator.isRecording()) coordinator.stopRecording()
        stopMeter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.i(TAG, "disarmed")
    }

    // ------------------------------------------------------------- call state

    private fun onCallState(intent: Intent) {
        val state = intent.getStringExtra(EXTRA_STATE) ?: return
        val number = intent.getStringExtra(EXTRA_PHONE_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                currentPhoneNumber = number
                currentCallType = CallType.INCOMING
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (currentCallType == CallType.UNKNOWN) currentCallType = CallType.OUTGOING
                if (number != null) currentPhoneNumber = number
                val config = settings.load()
                if (!config.recordCellularCalls) return
                if (!armed) {
                    postArmNotification(this, "not armed")
                    return
                }
                scheduleStart(config)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                pendingStart?.let { handler.removeCallbacks(it) }
                pendingStart = null
                finishRecording()
                currentPhoneNumber = null
                currentCallType = CallType.UNKNOWN
                if (armed) updateNotification(getString(R.string.notification_idle))
            }
        }
    }

    /**
     * The audio path is only valid a moment after OFFHOOK; starting immediately
     * produces silent or clipped recordings on most devices.
     */
    private fun scheduleStart(config: com.callrecorder.model.RecorderConfig) {
        pendingStart?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingStart = null
            if (coordinator.isRecording()) return@Runnable
            val result = coordinator.startRecording(
                config,
                currentCallType,
                currentPhoneNumber ?: "UNKNOWN",
            )
            when (result) {
                is RecordingCoordinator.Result.Started -> {
                    startedAt = System.currentTimeMillis()
                    peakAmplitude = 0
                    amplitudeSum = 0
                    amplitudeSamples = 0
                    startMeter()
                    updateNotification(getString(R.string.notification_recording))
                }
                is RecordingCoordinator.Result.Failure ->
                    Log.w(TAG, "start failed: " + result.message)
                else -> Unit
            }
        }
        pendingStart = runnable
        handler.postDelayed(runnable, START_DELAY_MS)
    }

    private fun finishRecording() {
        val path = coordinator.currentRecordingPath()
        if (coordinator.isRecording()) {
            stopMeter()
            coordinator.stopRecording()
        }
        if (path != null) {
            lastCompletedPath = path
            writeDiagnostics(path)
            maybeAutoShare(path)
        }
    }

    private fun startMeter() {
        stopMeter()
        val runnable = object : Runnable {
            override fun run() {
                if (!coordinator.isRecording()) return
                val amplitude = recorder.maxAmplitude()
                if (amplitude > peakAmplitude) peakAmplitude = amplitude
                amplitudeSum += amplitude
                amplitudeSamples++
                handler.postDelayed(this, METER_INTERVAL_MS)
            }
        }
        meter = runnable
        handler.postDelayed(runnable, METER_INTERVAL_MS)
    }

    private fun stopMeter() {
        meter?.let { handler.removeCallbacks(it) }
        meter = null
    }

    /**
     * Writes a .json sidecar next to the recording. This is what tells us whether
     * the platform delivered audio (peak > 0) or silence (peak == 0) on a device.
     */
    private fun writeDiagnostics(path: String) {
        try {
            val mean = if (amplitudeSamples == 0L) 0 else (amplitudeSum / amplitudeSamples)
            val json = JSONObject().apply {
                put("file", File(path).name)
                put("source", recorder.lastUsedSourceName ?: "unknown")
                put("peakAmplitude", peakAmplitude)
                put("meanAmplitude", mean)
                put("durationMs", System.currentTimeMillis() - startedAt)
                put("bytes", File(path).length())
                put("sdkInt", Build.VERSION.SDK_INT)
                put("device", Build.MANUFACTURER + " " + Build.MODEL)
                put("audioMode", audioManagerMode())
                put("silent", peakAmplitude == 0)
            }
            File(path + ".json").writeText(json.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "could not write diagnostics", e)
        }
    }

    private fun audioManagerMode(): Int = try {
        (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).mode
    } catch (_: Exception) {
        -1
    }

    // ------------------------------------------------------------ notification

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun maybeAutoShare(filePath: String) {
        val config = settings.load()
        if (!config.autoShareOnComplete) return
        val file = File(filePath)
        if (!file.exists()) return
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                packageName + ".recordings",
                file,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (!config.shareRecipient.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(config.shareRecipient))
            }
            val chooser = Intent.createChooser(send, getString(R.string.sharing_title)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
        } catch (e: Exception) {
            Log.w(TAG, "auto-share failed", e)
        }
    }

    private fun recordingsDir(): File {
        val dir = File(filesDir, "recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    companion object {
        private const val TAG = "CallRecorderService"

        /** Time to wait after OFFHOOK before opening the audio source. */
        private const val START_DELAY_MS = 1200L
        private const val METER_INTERVAL_MS = 250L

        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_ARM = "com.callrecorder.ACTION_ARM"
        const val ACTION_DISARM = "com.callrecorder.ACTION_DISARM"
        const val ACTION_CALL_STATE = "com.callrecorder.ACTION_CALL_STATE"

        const val EXTRA_STATE = "state"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val CHANNEL_ID = "call_recorder_channel"
        const val CHANNEL_NAME = "Call Recording"
        const val NOTIFICATION_ID = 1001
        const val ARM_NOTIFICATION_ID = 1002
        const val ARM_CHANNEL_ID = "call_recorder_arm"
        const val ARM_CHANNEL_NAME = "Recorder needs arming"

        fun handleCallState(context: Context, state: String, phoneNumber: String?) {
            val intent = Intent(context, CallRecorderService::class.java).apply {
                action = ACTION_CALL_STATE
                putExtra(EXTRA_STATE, state)
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            }
            try {
                // The service is already running as a foreground service, so a
                // plain startService is enough and avoids the Android 12+
                // foreground-service-start check.
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "could not deliver call state", e)
                postArmNotification(context, "service not running")
            }
        }

        fun arm(context: Context) {
            val intent = Intent(context, CallRecorderService::class.java).apply {
                action = ACTION_ARM
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Android 14+ does not allow a microphone foreground service to be started
         * from the background, but a notification action is an allowed exemption.
         * The user taps once and the recorder arms itself.
         */
        fun postArmNotification(context: Context, reason: String?) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    ARM_CHANNEL_ID,
                    ARM_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                manager.createNotificationChannel(channel)

                val armIntent = Intent(context, CallRecorderService::class.java).apply {
                    action = ACTION_ARM
                }
                val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                val pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PendingIntent.getForegroundService(context, 10, armIntent, flags)
                } else {
                    PendingIntent.getService(context, 10, armIntent, flags)
                }
                val open = PendingIntent.getActivity(
                    context, 11, Intent(context, MainActivity::class.java), flags,
                )

                val text = if (reason.isNullOrBlank()) {
                    "Tap to arm call recording"
                } else {
                    "Tap to arm call recording (" + reason + ")"
                }
                val notification = Notification.Builder(context, ARM_CHANNEL_ID)
                    .setContentTitle(context.getString(R.string.notification_title))
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentIntent(open)
                    .addAction(Notification.Action.Builder(null, "Arm", pending).build())
                    .setAutoCancel(true)
                    .build()
                manager.notify(ARM_NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.w(TAG, "could not post arm notification", e)
            }
        }
    }
}
