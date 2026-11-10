package com.callrecorder.call

/**
 * Pure state machine that translates Android telephony states into
 * recording commands. Unit-testable with no Android dependencies.
 */
class CallStateTracker {
    private var lastState: CallState = CallState.IDLE
    private var activePhoneNumber: String? = null

    fun onStateChanged(next: CallState, phoneNumber: String? = null): List<CallEvent> {
        val events = mutableListOf<CallEvent>()

        when {
            lastState != CallState.OFF_HOOK && next == CallState.OFF_HOOK -> {
                activePhoneNumber = phoneNumber ?: "UNKNOWN"
                events.add(CallEvent.StartRecording(activePhoneNumber ?: "UNKNOWN"))
            }
            lastState == CallState.OFF_HOOK && next == CallState.IDLE -> {
                events.add(CallEvent.StopRecording(StopReason.CALL_ENDED))
                activePhoneNumber = null
            }
        }

        lastState = next
        return events
    }

    fun currentState(): CallState = lastState

    fun currentPhoneNumber(): String? = activePhoneNumber

    fun reset() {
        lastState = CallState.IDLE
        activePhoneNumber = null
    }
}
