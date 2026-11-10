package com.callrecorder.model

import org.junit.Assert
import org.junit.Test

class RecordingQualityTest {
    @Test
    fun fromIdReturnsMatchingQuality() {
        Assert.assertEquals(RecordingQuality.High, RecordingQuality.fromId("high"))
        Assert.assertEquals(RecordingQuality.Low, RecordingQuality.fromId("low"))
    }

    @Test
    fun fromIdFallsBackToDefault() {
        Assert.assertEquals(RecordingQuality.Default, RecordingQuality.fromId("bogus"))
    }

    @Test
    fun defaultIsMedium() {
        Assert.assertEquals(RecordingQuality.Medium, RecordingQuality.Default)
    }

    @Test
    fun bitrateDecreasesAsQualityDecreases() {
        Assert.assertTrue(RecordingQuality.High.bitRate > RecordingQuality.Medium.bitRate)
        Assert.assertTrue(RecordingQuality.Medium.bitRate > RecordingQuality.Low.bitRate)
    }
}


