package com.callrecorder.shizuku

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.OutputStream
import kotlin.math.abs

/**
 * Runs in a process started by Shizuku as the shell user (uid 2000).
 *
 * That identity is what makes this work: the shell user holds
 * android.permission.CAPTURE_AUDIO_OUTPUT, so VOICE_CALL - the only source that
 * carries both call legs - opens here, while it always throws in the app process.
 *
 * There is no usable Context in this process, so nothing here touches resources,
 * receivers or content providers.
 */
class RecorderUserService : IShizukuRecorder.Stub() {

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var out: OutputStream? = null

    @Volatile private var running = false
    @Volatile private var peak = 0
    @Volatile private var sourceName: String = "none"

    override fun start(sink: ParcelFileDescriptor, source: Int, sampleRate: Int) {
        stop()
        val candidates = if (source > 0) {
            listOf(source)
        } else {
            listOf(
                MediaRecorder.AudioSource.VOICE_CALL,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC,
            )
        }

        var lastError: Exception? = null
        for (candidate in candidates) {
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) {
                lastError = IllegalStateException("bad buffer size for " + nameOf(candidate))
                continue
            }
            val bufferSize = maxOf(minBuffer * 4, sampleRate / 2)
            val audioRecord = try {
                AudioRecord(
                    candidate,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            } catch (e: Exception) {
                lastError = e
                continue
            }
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                lastError = IllegalStateException("not initialised: " + nameOf(candidate))
                continue
            }
            try {
                audioRecord.startRecording()
            } catch (e: Exception) {
                lastError = e
                audioRecord.release()
                continue
            }
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.release()
                lastError = IllegalStateException("did not start: " + nameOf(candidate))
                continue
            }

            val stream = ParcelFileDescriptor.AutoCloseOutputStream(sink)
            record = audioRecord
            out = stream
            sourceName = nameOf(candidate)
            peak = 0
            running = true
            thread = Thread({ pump(audioRecord, stream, bufferSize) }, "shizuku-pcm").apply { start() }
            Log.i(TAG, "capturing with " + sourceName + " as uid " + android.os.Process.myUid())
            return
        }

        try {
            sink.close()
        } catch (_: Exception) {
        }
        throw IllegalStateException(
            "no usable audio source in the shell process (last error: " +
                (lastError?.message ?: "unknown") + ")",
            lastError,
        )
    }

    private fun pump(audioRecord: AudioRecord, stream: OutputStream, bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)
        val bytes = ByteArray(buffer.size * 2)
        try {
            stream.buffered(64 * 1024).use { sink ->
                while (running) {
                    val read = try {
                        audioRecord.read(buffer, 0, buffer.size)
                    } catch (e: Exception) {
                        Log.w(TAG, "read failed", e)
                        -1
                    }
                    if (read <= 0) continue
                    var localPeak = 0
                    for (i in 0 until read) {
                        val sample = buffer[i].toInt()
                        if (abs(sample) > localPeak) localPeak = abs(sample)
                        bytes[i * 2] = (sample and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                    }
                    if (localPeak > peak) peak = localPeak
                    sink.write(bytes, 0, read * 2)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "pipe closed", e)
        }
    }

    override fun stop() {
        running = false
        try {
            thread?.join(1500)
        } catch (_: InterruptedException) {
        }
        thread = null
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        record?.release()
        record = null
        try {
            out?.close()
        } catch (_: Exception) {
        }
        out = null
    }

    override fun peakAmplitude(): Int = peak

    override fun lastSource(): String = sourceName

    override fun destroy() {
        stop()
        System.exit(0)
    }

    private fun nameOf(source: Int): String = when (source) {
        MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.VOICE_UPLINK -> "VOICE_UPLINK"
        MediaRecorder.AudioSource.VOICE_DOWNLINK -> "VOICE_DOWNLINK"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> "source:" + source
    }

    companion object {
        private const val TAG = "ShizukuRecorderSvc"
    }
}
