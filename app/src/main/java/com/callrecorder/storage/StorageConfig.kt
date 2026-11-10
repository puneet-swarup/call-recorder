package com.callrecorder.storage

/**
 * Describes where recordings are persisted.
 * A null SAF Tree URI means the app will fall back to app-private storage.
 */
data class StorageConfig(
    val safTreeUri: String? = null,
    val useMediaStore: Boolean = false,
) {
    val isSaf: Boolean
        get() = safTreeUri != null

    enum class StorageKind {
        SAF_FOLDER,
        MEDIA_STORE,
        APP_PRIVATE
    }

    val kind: StorageKind
        get() = when {
            safTreeUri != null -> StorageKind.SAF_FOLDER
            useMediaStore -> StorageKind.MEDIA_STORE
            else -> StorageKind.APP_PRIVATE
        }

    companion object {
        fun fromUri(uri: String): StorageConfig {
            require(uri.isNotBlank()) { "storage URI must not be empty" }
            return StorageConfig(safTreeUri = uri)
        }

        fun appPrivate(): StorageConfig = StorageConfig()
    }
}
