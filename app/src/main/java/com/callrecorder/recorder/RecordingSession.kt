package com.callrecorder.recorder

import com.callrecorder.model.CallType
import com.callrecorder.model.RecorderConfig
import java.io.File

/**
 * Crud information about a single recording session.
 */
data class RecordingSession(
    val phoneNumber: String,
    val callType: CallType,
    val config: RecorderConfig,
    val outputFile: File,
    val startedAtMillis: Long,
)
