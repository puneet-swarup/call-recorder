package com.callrecorder.call

import org.junit.Assert
import org.junit.Test

class CallStateTrackerTest {
    @Test
    fun idleToOffHookEmitsStart() {
        val tracker = CallStateTracker()
        val events = tracker.onStateChanged(CallState.OFF_HOOK, "+919876543210")
        Assert.assertEquals(1, events.size)
        val start = events.first() as CallEvent.StartRecording
        Assert.assertEquals("+919876543210", start.phoneNumber)
    }

    @Test
    fun offHookToIdleEmitsStop() {
        val tracker = CallStateTracker()
        tracker.onStateChanged(CallState.OFF_HOOK, "9876543210")
        val events = tracker.onStateChanged(CallState.IDLE)
        Assert.assertEquals(1, events.size)
        val stop = events.first() as CallEvent.StopRecording
        Assert.assertEquals(StopReason.CALL_ENDED, stop.reason)
    }

    @Test
    fun ringingDoesNotEmitEvents() {
        val tracker = CallStateTracker()
        val events = tracker.onStateChanged(CallState.RINGING, "9876543210")
        Assert.assertTrue(events.isEmpty())
    }

    @Test
    fun idleToIdleEmitsNothing() {
        val tracker = CallStateTracker()
        val events = tracker.onStateChanged(CallState.IDLE)
        Assert.assertTrue(events.isEmpty())
    }

    @Test
    fun resetClearsState() {
        val tracker = CallStateTracker()
        tracker.onStateChanged(CallState.OFF_HOOK, "123")
        tracker.reset()
        Assert.assertEquals(CallState.IDLE, tracker.currentState())
        Assert.assertNull(tracker.currentPhoneNumber())
    }

    @Test
    fun missingNumberForRecordingFallsBackToUnknown() {
        val tracker = CallStateTracker()
        val events = tracker.onStateChanged(CallState.OFF_HOOK, null)
        val start = events.first() as CallEvent.StartRecording
        Assert.assertEquals("UNKNOWN", start.phoneNumber)
    }

    @Test
    fun currentStateTracksLastState() {
        val tracker = CallStateTracker()
        tracker.onStateChanged(CallState.RINGING)
        Assert.assertEquals(CallState.RINGING, tracker.currentState())
    }
}
