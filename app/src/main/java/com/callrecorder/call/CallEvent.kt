package com.callrecorder.call

sealed class CallEvent {
    data class StartRecording(val phoneNumber: String) : CallEvent()
    data class StopRecording(val reason: StopReason) : CallEvent()
}

enum class StopReason {
    CALL_ENDED,
    SERVICE_STOPPED,
    PERMISSION_LOST
}
