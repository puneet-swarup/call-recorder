package com.callrecorder.storage

import org.junit.Assert
import org.junit.Test

class StorageConfigTest {
    @Test
    fun defaultsToAppPrivate() {
        val config = StorageConfig.appPrivate()
        Assert.assertEquals(StorageConfig.StorageKind.APP_PRIVATE, config.kind)
        Assert.assertFalse(config.isSaf)
    }

    @Test
    fun fromUriMarksAsSaf() {
        val config = StorageConfig.fromUri("content://com.example/folder")
        Assert.assertTrue(config.isSaf)
        Assert.assertEquals(StorageConfig.StorageKind.SAF_FOLDER, config.kind)
        Assert.assertEquals("content://com.example/folder", config.safTreeUri)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromUriRejectsBlank() {
        StorageConfig.fromUri("   ")
    }

    @Test
    fun mediaStoreKind() {
        val config = StorageConfig(useMediaStore = true)
        Assert.assertEquals(StorageConfig.StorageKind.MEDIA_STORE, config.kind)
    }
}



