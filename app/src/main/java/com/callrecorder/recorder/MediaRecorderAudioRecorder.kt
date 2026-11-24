package com.callrecorder.recorder

import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.callrecorder.model.RecorderConfig
import com.callrecorder.model.RecordingFormat
import java.io.File

/**
 * MediaRecorder-backed audio recorder.
 *
 * The audio source is chosen at runtime by trial: VOICE_CALL is the only source
 * that reliably captures both parties, but stock Android 10+ reserves it for
 * system apps. Many OEM ROMs (MIUI, some Samsung/Realme builds) do not enforce
 * that restriction, so we try it first and step down on failure.
 *
 * Order: VOICE_CALL -> VOICE_COMMUNICATION -> MIC
 */
class MediaRecorderAudioRecorder : AudioRecorder {
    private var mediaRecorder: MediaRecorder? = null
    override var isRecording: Boolean = false
        private set

    /** Name of the source that actually started, e.g. "VOICE_CALL". */
    @Volatile
    var lastUsedSourceName: String? = null
        private set

    override fun start(config: RecorderConfig, outputFile: File) {
        if (isRecording) return
        outputFile.parentFile?.mkdirs()

        lastUsedSourceName = null
        var lastError: Exception? = null

        for (candidate in candidates()) {
            val recorder = MediaRecorder()
            try {
                recorder.setAudioSource(candidate.source)
                recorder.setOutputFile(outputFile.absolutePath)
                applyFormat(recorder, config)
                recorder.prepare()
                recorder.start()

                mediaRecorder = recorder
                isRecording = true
                lastUsedSourceName = candidate.name
                Log.i(TAG, "recording with audio source " + candidate.name)
                return
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "audio source " + candidate.name + " rejected: " + e.message)
                try {
                    recorder.release()
                } catch (_: Exception) {
                    // best effort
                }
                // A failed attempt may leave a zero-byte file; the next attempt
                // overwrites it because setOutputFile truncates.
            }
        }

        isRecording = false
        throw IllegalStateException(
            "No usable audio source on this device (last error: " +
                (lastError?.message ?: "unknown") + ")",
            lastError,
        )
    }

    override fun stop() {
        val recorder = mediaRecorder ?: return
        try {
            recorder.stop()
        } catch (_: RuntimeException) {
            // stop() throws when no frames were recorded; safe to ignore
        } finally {
            recorder.release()
            mediaRecorder = null
            isRecording = false
        }
    }

    private data class Source(val name: String, val source: Int)

    private fun candidates(): List<Source> {
        val list = mutableListOf<Source>()
        // VOICE_CALL captures both parties but is system-only on stock Android 10+.
        // Still worth trying: several OEM ROMs permit it, which is why some
        // third-party recorders appear to capture both sides on those devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(Source("VOICE_CALL", MediaRecorder.AudioSource.VOICE_CALL))
        } else {
            list.add(Source("VOICE_CALL", MediaRecorder.AudioSource.VOICE_CALL))
        }
        list.add(Source("VOICE_COMMUNICATION", MediaRecorder.AudioSource.VOICE_COMMUNICATION))
        list.add(Source("MIC", MediaRecorder.AudioSource.MIC))
        return list
    }

    private fun applyFormat(recorder: MediaRecorder, config: RecorderConfig) {
        recorder.setAudioEncoder(encoderFor(config.format))
        recorder.setAudioSamplingRate(config.quality.sampleRate)
        recorder.setAudioEncodingBitRate(config.quality.bitRate)
        recorder.setOutputFormat(outputFormatFor(config.format))
    }

    companion object {
        private const val TAG = "AudioRecorder"

        fun encoderFor(format: RecordingFormat): Int = when (format) {
            RecordingFormat.MP4_AAC -> MediaRecorder.AudioEncoder.AAC
            RecordingFormat.AMR_NB -> MediaRecorder.AudioEncoder.AMR_NB
            RecordingFormat.AMR_WB -> MediaRecorder.AudioEncoder.AMR_WB
            RecordingFormat.WAV_PCM -> MediaRecorder.AudioEncoder.AAC
            RecordingFormat.MP3 -> MediaRecorder.AudioEncoder.AAC
        }

        fun outputFormatFor(format: RecordingFormat): Int = when (format) {
            RecordingFormat.MP4_AAC -> MediaRecorder.OutputFormat.MPEG_4
            RecordingFormat.MP3 -> MediaRecorder.OutputFormat.MPEG_4
            RecordingFormat.AMR_NB -> MediaRecorder.OutputFormat.AMR_NB
            RecordingFormat.AMR_WB -> MediaRecorder.OutputFormat.AMR_WB
            RecordingFormat.WAV_PCM -> MediaRecorder.OutputFormat.THREE_GPP
        }
    }
}
