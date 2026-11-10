package com.callrecorder.settings

import android.content.Context
import android.content.SharedPreferences
import com.callrecorder.model.RecorderConfig
import com.callrecorder.model.RecordingFormat
import com.callrecorder.model.RecordingQuality

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): RecorderConfig {
        return RecorderConfig(
            format = RecordingFormat.fromId(prefs.getString(KEY_FORMAT, null) ?: ""),
            quality = RecordingQuality.fromId(prefs.getString(KEY_QUALITY, null) ?: ""),
            storageFolderUri = prefs.getString(KEY_STORAGE_URI, null),
            recordCellularCalls = prefs.getBoolean(KEY_RECORD_CELLULAR, true),
            recordVoipCalls = prefs.getBoolean(KEY_RECORD_VOIP, true),
            encryptionEnabled = prefs.getBoolean(KEY_ENCRYPTION, false),
            autoStart = prefs.getBoolean(KEY_AUTO_START, true),
        )
    }

    fun save(config: RecorderConfig) {
        prefs.edit()
            .putString(KEY_FORMAT, config.format.id)
            .putString(KEY_QUALITY, config.quality.id)
            .putString(KEY_STORAGE_URI, config.storageFolderUri)
            .putBoolean(KEY_RECORD_CELLULAR, config.recordCellularCalls)
            .putBoolean(KEY_RECORD_VOIP, config.recordVoipCalls)
            .putBoolean(KEY_ENCRYPTION, config.encryptionEnabled)
            .putBoolean(KEY_AUTO_START, config.autoStart)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "callrecorder_settings"
        private const val KEY_FORMAT = "format"
        private const val KEY_QUALITY = "quality"
        private const val KEY_STORAGE_URI = "storage_uri"
        private const val KEY_RECORD_CELLULAR = "record_cellular"
        private const val KEY_RECORD_VOIP = "record_voip"
        private const val KEY_ENCRYPTION = "encryption"
        private const val KEY_AUTO_START = "auto_start"
    }
}
