package com.callrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.callrecorder.model.CallType
import com.callrecorder.recorder.MediaRecorderAudioRecorder
import com.callrecorder.recorder.RecordingRepository
import com.callrecorder.service.RecordingCoordinator
import com.callrecorder.settings.SettingsRepository
import java.io.File

class CallRecorderService : Service() {
    private lateinit var coordinator: RecordingCoordinator
    private lateinit var settings: SettingsRepository
    private var currentPhoneNumber: String? = null
    private var currentCallType: CallType = CallType.UNKNOWN

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        val repository = RecordingRepository(getPrivateRecordingsDir())
        coordinator = RecordingCoordinator(MediaRecorderAudioRecorder(), repository)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_idle)))
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = intent?.getStringExtra(EXTRA_STATE)
        if (state == null) {
            return START_STICKY
        }
        val number = intent.getStringExtra(EXTRA_PHONE_NUMBER)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                currentPhoneNumber = number
                currentCallType = CallType.INCOMING
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val config = settings.load()
                if (config.recordCellularCalls) {
                    val result = coordinator.startRecording(config, currentCallType, currentPhoneNumber ?: "UNKNOWN")
                    if (result is RecordingCoordinator.Result.Started) {
                        updateNotification(getString(R.string.notification_recording))
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (coordinator.isRecording()) {
                    coordinator.stopRecording()
                }
                currentPhoneNumber = null
                currentCallType = CallType.UNKNOWN
                updateNotification(getString(R.string.notification_idle))
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        if (coordinator.isRecording()) coordinator.stopRecording()
        isRunning = false
        super.onDestroy()
    }

    private fun getPrivateRecordingsDir(): File {
        val dir = File(getDir("recordings", MODE_PRIVATE), "")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
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

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        const val EXTRA_STATE = "state"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val CHANNEL_ID = "call_recorder_channel"
        const val CHANNEL_NAME = "Call Recording"
        const val NOTIFICATION_ID = 1001

        fun handleCallState(context: Context, state: String, phoneNumber: String?) {
            val intent = Intent(context, CallRecorderService::class.java).apply {
                putExtra(EXTRA_STATE, state)
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            }
            context.startForegroundService(intent)
        }
    }
}
