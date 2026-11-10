package com.callrecorder.storage

import com.callrecorder.model.CallType
import com.callrecorder.model.RecordingFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Deterministic, unit-testable file naming for recordings.
 * Example: 2026-11-09_103455_INCOMING_+919876543210.m4a
 */
object FileNamingPolicy {
    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")

    private val UNSAFE_CHARS: Regex = Regex("[^A-Za-z0-9]+")

    fun buildName(
        startTime: LocalDateTime,
        callType: CallType,
        phoneNumber: String,
        format: RecordingFormat,
    ): String {
        val timestamp = startTime.format(FORMATTER)
        val safeNumber = sanitizePhoneNumber(phoneNumber)
        return "${timestamp}_${callType.name}_${safeNumber}.${format.extension}"
    }

    fun sanitizePhoneNumber(number: String): String {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return "UNKNOWN"
        val sanitized = trimmed.replace(UNSAFE_CHARS, "_").trim('_')
        return if (sanitized.isEmpty()) "UNKNOWN" else sanitized
    }
}
