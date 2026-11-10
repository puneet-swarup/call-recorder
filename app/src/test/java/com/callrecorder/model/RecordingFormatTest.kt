package com.callrecorder.model

import org.junit.Assert
import org.junit.Test

class RecordingFormatTest {
    @Test
    fun fromIdReturnsMatchingFormat() {
        Assert.assertEquals(RecordingFormat.MP3, RecordingFormat.fromId("mp3"))
        Assert.assertEquals(RecordingFormat.WAV_PCM, RecordingFormat.fromId("wav"))
    }

    @Test
    fun fromIdFallsBackToDefault() {
        Assert.assertEquals(RecordingFormat.default(), RecordingFormat.fromId("unknown"))
        Assert.assertEquals(RecordingFormat.default(), RecordingFormat.fromId(""))
    }

    @Test
    fun defaultIsMp4Aac() {
        Assert.assertEquals(RecordingFormat.MP4_AAC, RecordingFormat.default())
    }

    @Test
    fun allFormatsHaveUniqueIdsAndExtensions() {
        val ids = RecordingFormat.entries.map { it.id }
        Assert.assertEquals(ids.size, ids.toSet().size)
        val exts = RecordingFormat.entries.map { it.extension }
        Assert.assertEquals(exts.size, exts.toSet().size)
    }

    @Test
    fun mimeTypesAreNonBlank() {
        RecordingFormat.entries.forEach {
            Assert.assertTrue(it.mimeType.isNotBlank())
            Assert.assertTrue(it.label.isNotBlank())
        }
    }
}



