package com.callrecorder.recorder

/**
 * Small explicit state machine controlling the recorder.
 * Idde -> Recording -> Idle.
 */
class RecordingStateMachine {
    var state: RecorderState = RecorderState.Idle
        private set

    fun onStarted(filePath: String): RecorderState {
        if (state is RecorderState.Recording) return state
        state = RecorderState.Recording(filePath)
        return state
    }

    fun onStopped(): RecorderState {
        state = RecorderState.Idle
        return state
    }

    fun onError(message: String): RecorderState {
        state = RecorderState.Error(message)
        return state
    }

    fun isRecording(): Boolean = state is RecorderState.Recording
}
