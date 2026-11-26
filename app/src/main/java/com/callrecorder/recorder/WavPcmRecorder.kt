package com.callrecorder.recorder

import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * 16-bit PCM WAV recorder built on AudioRecord.
 *
 * MediaRecorder has no WAV output, which is why the old WAV_PCM format silently
 * produced an AAC file with a .wav name. AudioRecord also gives us the sample
 * peaks we need to tell "recorded silence" apart from "recorded audio".
 */
class WavPcmRecorder {

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var rawFile: File? = null

    @Volatile private var running = false
    @Volatile private var peak = 0
    @Volatile var sourceName: String? = null
        private set

    val isRecording: Boolean get() = running

    fun start(sampleRate: Int, outputFile: File) {
        var lastError: Exception? = null
        for (candidate in AudioSources.candidates()) {
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) continue
            val bufferSize = maxOf(minBuffer * 2, sampleRate / 2)
            val audioRecord = try {
                AudioRecord(
                    candidate.source,
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
                lastError = IllegalStateException("AudioRecord not initialised for " + candidate.name)
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
                lastError = IllegalStateException("AudioRecord did not start for " + candidate.name)
                continue
            }

            val raw = File(outputFile.absolutePath + ".raw")
            rawFile = raw
            record = audioRecord
            sourceName = candidate.name
            peak = 0
            running = true
            thread = Thread({ pump(audioRecord, raw, bufferSize) }, "wav-writer").apply { start() }
            Log.i(TAG, "recording WAV with audio source " + candidate.name)
            return
        }
        throw IllegalStateException(
            "No usable audio source for WAV on this device (last error: " +
                (lastError?.message ?: "unknown") + ")",
            lastError,
        )
    }

    private fun pump(audioRecord: AudioRecord, raw: File, bufferSize: Int) {
        val buffer = ShortArray(bufferSize / 2)
        raw.outputStream().buffered().use { out ->
            while (running) {
                val read = try {
                    audioRecord.read(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    Log.w(TAG, "read failed", e)
                    -1
                }
                if (read <= 0) continue
                var localPeak = 0
                val bytes = ByteArray(read * 2)
                for (i in 0 until read) {
                    val sample = buffer[i].toInt()
                    if (abs(sample) > localPeak) localPeak = abs(sample)
                    bytes[i * 2] = (sample and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                }
                if (localPeak > peak) peak = localPeak
                out.write(bytes)
            }
        }
    }

    fun stop(outputFile: File) {
        running = false
        try {
            thread?.join(2000)
        } catch (_: InterruptedException) {
        }
        thread = null
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        record?.release()
        record = null

        val raw = rawFile
        if (raw != null && raw.exists()) {
            writeWavHeader(raw, outputFile)
            raw.delete()
        }
        rawFile = null
    }

    fun maxAmplitude(): Int = peak

    private fun writeWavHeader(raw: File, target: File) {
        val dataLength = raw.length().toInt()
        RandomAccessFile(target, "rw").use { out ->
            out.setLength(0)
            out.writeBytes("RIFF")
            out.writeIntLe(36 + dataLength)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLe(16)
            out.writeShortLe(1)          // PCM
            out.writeShortLe(1)          // mono
            out.writeIntLe(SAMPLE_RATE)
            out.writeIntLe(SAMPLE_RATE * 2)
            out.writeShortLe(2)
            out.writeShortLe(16)
            out.writeBytes("data")
            out.writeIntLe(dataLength)
            raw.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    private fun RandomAccessFile.writeIntLe(value: Int) {
        write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte(),
        ))
    }

    private fun RandomAccessFile.writeShortLe(value: Int) {
        write(byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte()))
    }

    companion object {
        private const val TAG = "WavPcmRecorder"
        const val SAMPLE_RATE = 16000
    }
}
