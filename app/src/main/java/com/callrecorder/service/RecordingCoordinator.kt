package com.callrecorder.service

import com.callrecorder.model.CallType
import com.callrecorder.model.RecorderConfig
import com.callrecorder.recorder.AudioRecorder
import com.callrecorder.recorder.RecordingRepository
import com.callrecorder.recorder.RecordingStateMachine

/**
 * Orchestrates start/stop of recordings.
 * Pure JVM class (except for the abstracted AudioRecorder), so it can be tested easily.
 */
class RecordingCoordinator(
    private val recorder: AudioRecorder,
    private val repository: RecordingRepository,
    private val stateMachine: RecordingStateMachine = RecordingStateMachine(),
) {
    fun startRecording(
        config: RecorderConfig,
        callType: CallType,
        phoneNumber: String,
    ): Result {
        if (stateMachine.isRecording()) return Result.AlreadyRecording
        return try {
            val outputFile = repository.createOutputFile(config, callType, phoneNumber)
            recorder.start(config, outputFile)
            stateMachine.onStarted(outputFile.absolutePath)
            Result.Started(outputFile.absolutePath)
        } catch (e: Exception) {
            stateMachine.onError(e.message ?: "Unknown error")
            Result.Failure(e.message ?: "Unknown error")
        }
    }

    fun stopRecording(): Result {
        if (!stateMachine.isRecording()) return Result.NotRecording
        return try {
            recorder.stop()
            stateMachine.onStopped()
            Result.Stopped
        } catch (e: Exception) {
            stateMachine.onError(e.message ?: "Unknown error")
            Result.Failure(e.message ?: "Unknown error")
        }
    }

    fun isRecording(): Boolean = stateMachine.isRecording()

    sealed class Result {
        data class Started(val filePath: String) : Result()
        object Stopped : Result()
        object AlreadyRecording : Result()
        object NotRecording : Result()
        data class Failure(val message: String) : Result()
    }
}
