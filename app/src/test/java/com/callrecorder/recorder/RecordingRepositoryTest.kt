package com.callrecorder.recorder

import com.callrecorder.model.CallType
import com.callrecorder.model.RecorderConfig
import com.callrecorder.model.RecordingFormat
import com.callrecorder.storage.StorageConfig
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

class RecordingRepositoryTest {
    @Rule
    @JvmField
    val temp = TemporaryFolder()

    @Test
    fun createOutputFileInAppPrivateWhenNoUriSet() {
        val repo = RecordingRepository(temp.root)
        val file = repo.createOutputFile(
            config = RecorderConfig(),
            callType = CallType.INCOMING,
            phoneNumber = "9876543210",
            now = LocalDateTime.of(2026, 11, 9, 10, 34, 55),
        )
        Assert.assertEquals(temp.root.absolutePath, file.parentFile!!.absolutePath)
        Assert.assertTrue(file.name.startsWith("2026-11-09_103455_INCOMING_9876543210"))
        Assert.assertTrue(file.name.endsWith(".m4a"))
    }

    @Test
    fun describeStorageReturnsAppPrivateWhenNull() {
        val repo = RecordingRepository(temp.root)
        Assert.assertEquals(StorageConfig.StorageKind.APP_PRIVATE, repo.describeStorage(null).kind)
    }

    @Test
    fun describeStorageReturnsSafForContentUri() {
        val repo = RecordingRepository(temp.root)
        Assert.assertEquals(StorageConfig.StorageKind.SAF_FOLDER, repo.describeStorage("content://example/dir").kind)
    }

    @Test
    fun createOutputFileUsesConfiguredFormatExtension() {
        val repo = RecordingRepository(temp.root)
        val file = repo.createOutputFile(
            config = RecorderConfig(format = RecordingFormat.WAV_PCM),
            callType = CallType.OUTGOING,
            phoneNumber = "456",
            now = LocalDateTime.of(2026, 1, 2, 3, 4, 5),
        )
        Assert.assertTrue(file.name.endsWith(".wav"))
        Assert.assertTrue(file.name.contains("OUTGOING"))
    }
}
