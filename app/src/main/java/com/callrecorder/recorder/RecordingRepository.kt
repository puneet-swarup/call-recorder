package com.callrecorder.recorder

import com.callrecorder.model.CallType
import com.callrecorder.model.RecorderConfig
import com.callrecorder.storage.FileNamingPolicy
import com.callrecorder.storage.StorageConfig
import java.io.File
import java.time.LocalDateTime

/**
 * Resolves output files and persists them according to the storage configiuration.
 * The app-private directory is used as a fallback when no SAF folder is configured.
 */
class RecordingRepository(
    private val appPrivateDir: File,
) {
    fun createOutputFile(
        config: RecorderConfig,
        callType: CallType,
        phoneNumber: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): File {
        val storage = describeStorage(config.storageFolderUri)
        val dir = resolveDirectory(storage)
        val name = FileNamingPolicy.buildName(now, callType, phoneNumber, config.format)
        return File(dir, name)
    }

    fun describeStorage(storageUri: String?): StorageConfig =
        if (storageUri.isNullOrBlank()) StorageConfig.appPrivate()
        else StorageConfig.fromUri(storageUri)

    private fun resolveDirectory(storage: StorageConfig): File {
        return when (storage.kind) {
            StorageConfig.StorageKind.SAF_FOLDER -> {
                // True SAF writes go through ContentResolver in the service layer. Here we
                // return app-private files that will be copied to the SAF folder after completion.
                appPrivateDir
            }
            StorageConfig.StorageKind.MEDIA_STORE,
            StorageConfig.StorageKind.APP_PRIVATE -> appPrivateDir
        }
    }
}
