package com.callrecorder.storage

import com.callrecorder.model.CallType
import com.callrecorder.model.RecordingFormat
import org.junit.Assert
import org.junit.Test
import java.time.LocalDateTime

class FileNamingPolicyTest {
    private val fixedTime: LocalDateTime = LocalDateTime.of(2026, 11, 9, 10, 34, 55)

    @Test
    fun buildNameUsesTimestampTypeAndExtension() {
        val name = FileNamingPolicy.buildName(
            startTime = fixedTime,
            callType = CallType.INCOMING,
            phoneNumber = "9876543210",
            format = RecordingFormat.MP4_AAC,
        )
        Assert.assertEquals("2026-11-09_103455_INCOMING_9876543210.m4a", name)
    }

    @Test
    fun buildNameSanitizesPlusAndSpaces() {
        val name = FileNamingPolicy.buildName(
            startTime = fixedTime,
            callType = CallType.OUTGOING,
            phoneNumber = "+91 9876-543210",
            format = RecordingFormat.WAV_PCM,
        )
        Assert.assertEquals("2026-11-09_103455_OUTGOING_91_9876_543210.wav", name)
    }

    @Test
    fun buildNameUsesUNKNOWNForEmptyNumber() {
        val name = FileNamingPolicy.buildName(
            startTime = fixedTime,
            callType = CallType.UNKNOWN,
            phoneNumber = "   ",
            format = RecordingFormat.AMR_NB,
        )
        Assert.assertEquals("2026-11-09_103455_UNKNOWN_UNKNOWN.amr", name)
    }

    @Test
    fun sanitizePhoneNumberReturnsUNKNOWNForEmpty() {
        Assert.assertEquals("UNKNOWN", FileNamingPolicy.sanitizePhoneNumber(""))
        Assert.assertEquals("UNKNOWN", FileNamingPolicy.sanitizePhoneNumber("***"))
    }

    @Test
    fun sanitizePhoneNumberKeepsAlphanumeric() {
        Assert.assertEquals("9876543210", FileNamingPolicy.sanitizePhoneNumber("9876543210"))
        Assert.assertEquals("919876543210", FileNamingPolicy.sanitizePhoneNumber("+919876543210"))
    }

    @Test
    fun buildNameUsesFormatExtension() {
        val name = FileNamingPolicy.buildName(
            startTime = fixedTime,
            callType = CallType.INCOMING,
            phoneNumber = "123",
            format = RecordingFormat.MP3,
        )
        Assert.assertTrue(name.endsWith(".mp3"))
        Assert.assertTrue(name.contains("INCOMING"))
    }
}


