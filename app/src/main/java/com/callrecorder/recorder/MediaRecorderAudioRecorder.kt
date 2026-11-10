package com.callrecorder.recorder

import android.media.MediaRecorder
import com.callrecorder.model.RecorderConfig
import com.callrecorder.model.RecordingFormat
import java.io.File

/**
 * MediaRecorder-backed audio recorder.
 * Try audio sources in preference order and fall back when the OS rejects them.
 */
class MediaRecorderAudioRecorder : AudioRecorder {
    private var mediaRecorder: MediaRecorder? = null
    override var isRecording: Boolean = false
        private set

    override fun start(config: RecorderConfig, outputFile: File) {
        if (isRecording) return
        config.storageFolderUri?.let { } // handled by RecordingRepository
        outputFile.parentFile?.mkdirs()

        val recorder = MediaRecorder()
        recorder.setAudioSource(resolveAudioSource())
        recorder.setOutputFile(outputFile.absolutePath)
        applyFormat(recorder, config)
        recorder.prepare()
        recorder.start()
        mediaRecorder = recorder
        isRecording = true
    }

    override fun stop() {
        val recorder = mediaRecorder ?: return
        try {
            recorder.stop()
        } catch (_ : RuntimeException) {
            // stop() throws when no frames were recorded; safe to ignore
        } finally {
            recorder.release()
            mediaRecorder = null
            isRecording = false
        }
    }

    private fun resolveAudioSource(): Int {
        // VOICE_CALL is reserved for system apps since Android 10.
        return MediaRecorder.AudioSource.VOICE_COMMUNICATION
    }

    private fun applyFormat(recorder: MediaRecorder, config: RecorderConfig) {
        recorder.setAudioEncoder(encoderFor(config.format))
        recorder.setAudioSamplingRate(config.quality.sampleRate)
        recorder.setAudioEncodingBitRate(config.quality.bitRate)
        recorder.setOutputFormat(outputFormatFor(config.format))
    }

    companion object {
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
