package com.callrecorder.model

/**
 * Supported container/codec combinations for recording.
 * Each entry maps to an audio encoder used by the recording engine.
 */
enum class RecordingFormat(
    val id: String,
    val label: String,
    val extension: String,
    val mimeType: String,
) {
    MP4_AAC(
        id = "mp4_aac",
        label = "MP4 (AAC)",
        extension = "m4a",
        mimeType = "audio/mp4",
    ),
    MP3(
        id = "mp3",
        label = "MP3",
        extension = "mp3",
        mimeType = "audio/mpeg",
    ),
    AMR_NB(
        id = "amr_nb",
        label = "AMR-NB",
        extension = "amr",
        mimeType = "audio/amr",
    ),
    AMR_WB(
        id = "amr_wb",
        label = "AMR-WB",
        extension = "awb",
        mimeType = "audio/amr-wb",
    ),
    WAV_PCM(
        id = "wav",
        label = "WAV (PCM)",
        extension = "wav",
        mimeType = "audio/wav",
    );

    companion object {
        fun fromId(id: String): RecordingFormat =
            entries.firstOrNull { it.id == id } ?: default()

        fun default(): RecordingFormat = MP4_AAC
    }
}
