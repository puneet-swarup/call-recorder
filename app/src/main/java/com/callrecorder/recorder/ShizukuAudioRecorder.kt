package com.callrecorder.recorder

import com.callrecorder.model.RecorderConfig
import com.callrecorder.shizuku.ShizukuRecorder
import java.io.File

/** Wraps the Shizuku-backed capture path in the AudioRecorder contract. */
class ShizukuAudioRecorder(private val controller: ShizukuRecorder) : AudioRecorder {

    override var isRecording: Boolean = false
        private set

    var lastUsedSourceName: String? = null
        private set

    override fun start(config: RecorderConfig, outputFile: File) {
        controller.start(outputFile, ShizukuRecorder.SAMPLE_RATE)
        isRecording = true
        lastUsedSourceName = "SHIZUKU:" + controller.sourceName()
    }

    override fun stop() {
        controller.stop()
        isRecording = false
    }

    fun maxAmplitude(): Int = controller.peakAmplitude()
}
