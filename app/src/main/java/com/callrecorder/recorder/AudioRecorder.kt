package com.callrecorder.recorder

import com.callrecorder.model.RecorderConfig
import java.io.File

/**
 * Abstraction around the platform audio recorder.
 * Enables unit testing of service logic without touching Android APIs.
 */
interface AudioRecorder {
    fun start(config: RecorderConfig, outputFile: File)
    fun stop()
    val isRecording: Boolean
}
