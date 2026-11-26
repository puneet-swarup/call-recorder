package com.callrecorder.recorder

import android.media.MediaRecorder
import android.util.Log
import com.callrecorder.model.RecorderConfig
import com.callrecorder.model.RecordingFormat
import java.io.File

/**
 * MediaRecorder-backed audio recorder, with a WAV path handled by AudioRecord.
 *
 * The audio source is chosen at runtime by trial, best first (see [AudioSources]).
 * On stock Android 10+ only MIC-family sources are actually available to a normal
 * app, so expect the fallback to be the one that sticks.
 */
class MediaRecorderAudioRecorder : AudioRecorder {
    private var mediaRecorder: MediaRecorder? = null
    private val wavRecorder = WavPcmRecorder()
    private var wavOutput: File? = null

    override var isRecording: Boolean = false
        private set

    /** Name of the source that actually started, e.g. "MIC". */
    @Volatile
    var lastUsedSourceName: String? = null
        private set

    /** Last error text, for the diagnostic screen. */
    @Volatile
    var lastError: String? = null
        private set

    override fun start(config: RecorderConfig, outputFile: File) {
        if (isRecording) return
        outputFile.parentFile?.mkdirs()

        lastUsedSourceName = null
        lastError = null

        if (config.format == RecordingFormat.WAV_PCM) {
            wavOutput = outputFile
            wavRecorder.start(WavPcmRecorder.SAMPLE_RATE, outputFile)
            isRecording = true
            lastUsedSourceName = wavRecorder.sourceName
            return
        }

        var failure: Exception? = null
        for (candidate in AudioSources.candidates()) {
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
                failure = e
                Log.w(TAG, "audio source " + candidate.name + " rejected: " + e.message)
                try {
                    recorder.release()
                } catch (_: Exception) {
                    // best effort
                }
            }
        }

        isRecording = false
        val message = "No usable audio source on this device (last error: " +
            (failure?.message ?: "unknown") + ")"
        lastError = message
        throw IllegalStateException(message, failure)
    }

    override fun stop() {
        if (wavRecorder.isRecording) {
            val path = wavOutput
            if (path != null) wavRecorder.stop(path)
            wavOutput = null
            isRecording = false
            return
        }
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

    /** Peak amplitude since the last call, 0..32767. Used for silence detection. */
    fun maxAmplitude(): Int = when {
        wavRecorder.isRecording -> wavRecorder.maxAmplitude()
        else -> try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
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
            RecordingFormat.WAV_PCM -> MediaRecorder.OutputFormat.MPEG_4
        }
    }
}
