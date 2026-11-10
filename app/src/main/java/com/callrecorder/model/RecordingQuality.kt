package com.callrecorder.model

/**
 * Quality presets for the audio encoder.
 * Bitrate is in bits per second; sample rate in Hz.
 */
data class RecordingQuality(
    val id: String,
    val label: String,
    val bitRate: Int,
    val sampleRate: Int,
) {
    companion object {
        val High = RecordingQuality("high", "High", 128_000, 44_100)
        val Medium = RecordingQuality("medium", "Medium", 64_000, 22_050)
        val Low = RecordingQuality("low", "Low", 32_000, 16_000)

        val Default = Medium

        val all: List<RecordingQuality> = listOf(High, Medium, Low)

        fun fromId(id: String): RecordingQuality =
            all.firstOrNull { it.id == id } ?: Default
    }
}
