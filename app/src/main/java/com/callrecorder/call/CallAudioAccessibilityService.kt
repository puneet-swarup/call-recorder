package com.callrecorder.call

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Does nothing on purpose.
 *
 * Since Android 10 the audio framework silences an ordinary app's capture while a
 * voice call is active, with two documented exemptions: a privileged app holding
 * CAPTURE_AUDIO_OUTPUT, or an enabled AccessibilityService. A sideloaded app can
 * have the second one. Enabling this service is what stops the microphone stream
 * from being replaced with silence during a call on stock Android.
 *
 * Android 16 refuses to *enable* an accessibility service while a call is in
 * progress, so this has to be switched on before you dial.
 */
class CallAudioAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "accessibility service connected; call audio capture is no longer silenced")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No window content is read, ever.
    }

    override fun onInterrupt() {
        // Nothing to interrupt.
    }

    companion object {
        private const val TAG = "CallAudioA11y"
    }
}
