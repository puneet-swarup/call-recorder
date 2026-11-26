package com.callrecorder.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.callrecorder.CallRecorderService

/**
 * Forwards telephony call-state changes to the recorder service.
 *
 * The service must already be armed: since Android 14 the platform refuses to
 * create a microphone foreground service from the background, so this receiver
 * never tries to start one. If the recorder is not armed we post a notification
 * the user can tap instead of throwing ForegroundServiceStartNotAllowedException.
 */
class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (!CallRecorderService.isRunning) {
            Log.i(TAG, "call state " + state + " but recorder is not armed")
            CallRecorderService.postArmNotification(context, "not armed")
            return
        }
        CallRecorderService.handleCallState(context, state, incomingNumber)
    }

    companion object {
        private const val TAG = "CallStateReceiver"
    }
}
