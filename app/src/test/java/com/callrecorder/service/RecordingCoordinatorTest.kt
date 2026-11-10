package com.callrecorder.service

import com.callrecorder.model.CallType
import com.callrecorder.model.RecorderConfig
import com.callrecorder.recorder.AudioRecorder
import com.callrecorder.recorder.RecordingRepository
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordingCoordinatorTest {
    @Rule
    @JvmField
    val temp = TemporaryFolder()

    private lateinit var repository: RecordingRepository
    private lateinit var config: RecorderConfig

    @Before
    fun setup() {
        repository = RecordingRepository(temp.root)
        config = RecorderConfig()
    }

    @Test
    fun startRecordingSucceedsWithValidRecorder() {
        val recorder = FakeRecorder()
        val coordinator = RecordingCoordinator(recorder, repository)

        val result = coordinator.startRecording(config, CallType.INCOMING, "9876543210")

        Assert.assertTrue(result is RecordingCoordinator.Result.Started)
        Assert.assertTrue(recorder.started)
        Assert.assertTrue(coordinator.isRecording())
    }

    @Test
    fun startTwiceReturnsAlreadyRecording() {
        val recorder = FakeRecorder()
        val coordinator = RecordingCoordinator(recorder, repository)
        coordinator.startRecording(config, CallType.INCOMING, "1")

        val result = coordinator.startRecording(config, CallType.INCOMING, "2")

        Assert.assertEquals(RecordingCoordinator.Result.AlreadyRecording, result)
    }

    @Test
    fun stopWhenNotRecordingReturnsNotRecording() {
        val coordinator = RecordingCoordinator(FakeRecorder(), repository)
        Assert.assertEquals(RecordingCoordinator.Result.NotRecording, coordinator.stopRecording())
    }

    @Test
    fun stopRecordingSucceedsAfterStart() {
        val recorder = FakeRecorder()
        val coordinator = RecordingCoordinator(recorder, repository)
        coordinator.startRecording(config, CallType.INCOMING, "1")

        val result = coordinator.stopRecording()

        Assert.assertEquals(RecordingCoordinator.Result.Stopped, result)
        Assert.assertTrue(recorder.stopped)
        Assert.assertFalse(coordinator.isRecording())
    }

    @Test
    fun startFailureReturnsFailure() {
        val recorder = FakeRecorder(throwOnNextStart = true)
        val coordinator = RecordingCoordinator(recorder, repository)

        val result = coordinator.startRecording(config, CallType.INCOMING, "1")

        Assert.assertTrue(result is RecordingCoordinator.Result.Failure)
        Assert.assertFalse(coordinator.isRecording())
    }

    private class FakeRecorder(
        private val throwOnNextStart: Boolean = false,
    ) : AudioRecorder {
        var started = false
        var stopped = false
        override var isRecording: Boolean = false

        override fun start(config: RecorderConfig, outputFile: File) {
            if (throwOnNextStart) throw IllegalStateException("fake failure")
            started = true
            isRecording = true
            outputFile.writeText("fake")
        }

        override fun stop() {
            stopped = true
            isRecording = false
        }
    }
}
