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
            recorderEnabled = prefs.getBoolean(KEY_RECORDER_ENABLED, false),
            shareRecipient = prefs.getString(KEY_SHARE_RECIPIENT, null),
            autoShareOnComplete = prefs.getBoolean(KEY_AUTO_SHARE, false),
            minimiseMode = prefs.getBoolean(KEY_MINIMISE, false),
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
            .putBoolean(KEY_RECORDER_ENABLED, config.recorderEnabled)
            .putString(KEY_SHARE_RECIPIENT, config.shareRecipient)
            .putBoolean(KEY_AUTO_SHARE, config.autoShareOnComplete)
            .putBoolean(KEY_MINIMISE, config.minimiseMode)
            .apply()
    }

    fun setRecorderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECORDER_ENABLED, enabled).apply()
    }

    fun isRecorderEnabled(): Boolean = prefs.getBoolean(KEY_RECORDER_ENABLED, false)

    /**
     * Prefer the Shizuku capture path (both call legs, needs the Shizuku app and a
     * one-time permission grant). Falls back to the microphone automatically when
     * Shizuku is unavailable, so this can stay on by default.
     */
    fun isShizukuEnabled(): Boolean = prefs.getBoolean(KEY_USE_SHIZUKU, true)

    fun setShizukuEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_SHIZUKU, enabled).apply()
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
        private const val KEY_RECORDER_ENABLED = "recorder_enabled"
        private const val KEY_SHARE_RECIPIENT = "share_recipient"
        private const val KEY_AUTO_SHARE = "auto_share"
        private const val KEY_MINIMISE = "minimise"
        private const val KEY_USE_SHIZUKU = "use_shizuku"
    }
}
