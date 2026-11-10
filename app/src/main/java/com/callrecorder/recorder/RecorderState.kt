package com.callrecorder.recorder

sealed class RecorderState {
    object Idle : RecorderState()
    data class Recording(val filePath: String) : RecorderState()
    data class Error(val message: String) : RecorderState()
}
