package com.callrecorder.model

data class RecorderConfig(
    val format: RecordingFormat = RecordingFormat.default(),
    val quality: RecordingQuality = RecordingQuality.Default,
    val storageFolderUri: String? = null,
    val recordCellularCalls: Boolean = true,
    val recordVoipCalls: Boolean = true,
    val encryptionEnabled: Boolean = false,
    val autoStart: Boolean = true,
)
