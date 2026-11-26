package com.callrecorder.recorder

import android.media.MediaRecorder

/**
 * Ordered list of capture sources to try, best first.
 *
 * VOICE_CALL is the only source that carries both call legs, but since Android 10
 * it is reserved for apps holding the signature permission CAPTURE_AUDIO_OUTPUT
 * (system / privileged / OEM apps, or a helper running as the ADB shell user).
 * A normal sideloaded app always gets SecurityException here, so the list falls
 * through to sources that a normal app can open.
 *
 * MIC is the source that actually works on stock Android; when the call is on
 * speakerphone the far end is picked up acoustically. It is silenced during a
 * call unless the app also runs an enabled AccessibilityService (see
 * CallAudioAccessibilityService).
 */
object AudioSources {

    data class Candidate(val name: String, val source: Int)

    fun candidates(): List<Candidate> = listOf(
        Candidate("VOICE_CALL", MediaRecorder.AudioSource.VOICE_CALL),
        Candidate("VOICE_COMMUNICATION", MediaRecorder.AudioSource.VOICE_COMMUNICATION),
        Candidate("MIC", MediaRecorder.AudioSource.MIC),
        Candidate("VOICE_RECOGNITION", MediaRecorder.AudioSource.VOICE_RECOGNITION),
    )
}
