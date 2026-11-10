package com.callrecorder.recorder

import org.junit.Assert
import org.junit.Test

class RecordingStateMachineTest {
    @Test
    fun startsAsRecording() {
        val sm = RecordingStateMachine()
        val state = sm.onStarted("/tmp/a.m4a")
        Assert.assertTrue(state is RecorderState.Recording)
        Assert.assertTrue(sm.isRecording())
    }

    @Test
    fun startTwiceIsIdlempotent() {
        val sm = RecordingStateMachine()
        sm.onStarted("/tmp/a.m4a")
        val second = sm.onStarted("/tmp/b.m4a")
        Assert.assertEquals((sm.state as RecorderState.Recording).filePath, (second as RecorderState.Recording).filePath)
    }

    @Test
    fun stopReturnsIdle() {
        val sm = RecordingStateMachine()
        sm.onStarted("/tmp/a.m4a")
        val state = sm.onStopped()
        Assert.assertEquals(RecorderState.Idle, state)
        Assert.assertFalse(sm.isRecording())
    }

    @Test
    fun errorSetsErrorState() {
        val sm = RecordingStateMachine()
        val state = sm.onError("boom")
        Assert.assertTrue(state is RecorderState.Error)
        Assert.assertEquals("boom", (state as RecorderState.Error).message)
    }
}

