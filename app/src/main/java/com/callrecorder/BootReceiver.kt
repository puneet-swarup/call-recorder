package com.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.callrecorder.settings.SettingsRepository

/**
 * Restarts the recorder after a reboot or an app update.
 *
 * Android 14+ refuses to launch a `microphone` foreground service from a
 * BOOT_COMPLETED receiver. We still try (it works up to Android 13), and fall
 * back to a notification whose action arms the service — that path is an
 * allowed exemption to the while-in-use restriction.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        val settings = SettingsRepository(context)
        if (!settings.isRecorderEnabled()) {
            Log.i(TAG, "recorder disabled, not restarting after " + action)
            return
        }

        try {
            val serviceIntent = Intent(context, CallRecorderService::class.java).apply {
                this.action = CallRecorderService.ACTION_ARM
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.w(TAG, "cannot start recorder from " + action + ", asking the user", e)
            CallRecorderService.postArmNotification(context, "after restart")
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
