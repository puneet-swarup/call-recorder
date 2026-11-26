package com.callrecorder.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * App-side controller for the Shizuku-backed capture path.
 *
 * Shizuku runs RecorderUserService in a separate process as the shell user
 * (uid 2000). That process opens the audio source and streams raw PCM back
 * through a pipe; this class owns the pipe, the reader thread and the WAV file.
 *
 * Everything here degrades to "not available" rather than throwing, so the caller
 * can fall back to the ordinary MediaRecorder path.
 */
class ShizukuRecorder(private val context: Context) {

    private var binder: IShizukuRecorder? = null
    private var readEnd: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var rawFile: File? = null
    private var outputFile: File? = null
    private var latch = CountDownLatch(1)

    @Volatile
    var isBound: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = if (service != null) IShizukuRecorder.Stub.asInterface(service) else null
            isBound = binder != null
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            isBound = false
        }
    }

    private val args: Shizuku.UserServiceArgs
        get() = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, RecorderUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("recorder")
            .debuggable(false)
            .version(USER_SERVICE_VERSION)

    val isSupported: Boolean
        get() = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }

    val hasPermission: Boolean
        get() = try {
            isSupported && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }

    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Throwable) {
            lastError = "permission request failed: " + e.message
        }
    }

    fun bind(timeoutMs: Long = 4000): Boolean {
        if (isBound && binder != null) return true
        if (!hasPermission) {
            lastError = "Shizuku is not running or permission not granted"
            return false
        }
        return try {
            latch = CountDownLatch(1)
            Shizuku.bindUserService(args, connection)
            val connected = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!connected) lastError = "timed out binding the Shizuku user service"
            connected && binder != null
        } catch (e: Throwable) {
            lastError = "bind failed: " + e.message
            false
        }
    }

    fun unbind() {
        try {
            Shizuku.unbindUserService(args, connection, true)
        } catch (e: Throwable) {
            Log.w(TAG, "unbind failed", e)
        }
        binder = null
        isBound = false
    }

    fun start(output: File, sampleRate: Int) {
        if (!bind()) {
            throw IllegalStateException(lastError ?: "Shizuku user service is not bound")
        }
        val service = binder ?: throw IllegalStateException(
            lastError ?: "Shizuku user service is not bound",
        )
        output.parentFile?.mkdirs()
        val pipe = ParcelFileDescriptor.createPipe()
        val read = pipe[0]
        val write = pipe[1]
        val raw = File(output.absolutePath + ".pcm")

        readEnd = read
        rawFile = raw
        outputFile = output

        reader = Thread({
            try {
                ParcelFileDescriptor.AutoCloseInputStream(read).use { input ->
                    raw.outputStream().buffered(64 * 1024).use { sink ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            sink.write(buffer, 0, n)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "pipe reader stopped", e)
            }
        }, "shizuku-pipe").apply { start() }

        try {
            service.start(write, 0, sampleRate)
        } catch (e: Throwable) {
            lastError = "capture failed: " + e.message
            closeQuietly()
            throw IllegalStateException(lastError, e)
        } finally {
            try {
                write.close()
            } catch (_: Exception) {
            }
        }
    }

    fun stop() {
        try {
            binder?.stop()
        } catch (e: Throwable) {
            Log.w(TAG, "stop failed", e)
        }
        closeQuietly()
        val raw = rawFile
        val target = outputFile
        if (raw != null && target != null && raw.exists()) {
            WavWriter.wrap(raw, target, SAMPLE_RATE)
            raw.delete()
        }
        rawFile = null
        outputFile = null
    }

    fun peakAmplitude(): Int = try {
        binder?.peakAmplitude() ?: 0
    } catch (e: Throwable) {
        0
    }

    fun sourceName(): String = try {
        binder?.lastSource() ?: "none"
    } catch (e: Throwable) {
        "none"
    }

    private fun closeQuietly() {
        try {
            readEnd?.close()
        } catch (_: Exception) {
        }
        readEnd = null
        try {
            reader?.join(1500)
        } catch (_: InterruptedException) {
        }
        reader = null
    }

    companion object {
        private const val TAG = "ShizukuRecorder"

        private const val USER_SERVICE_VERSION = 1

        const val SAMPLE_RATE = 16000
    }
}
