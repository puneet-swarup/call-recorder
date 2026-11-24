package com.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.callrecorder.settings.SettingsRepository

/**
 * Restarts the recorder after a reboot or an app update, but only when the user
 * has actually enabled recording. The persisted recorderEnabled flag is the
 * single source of truth; the old autoStart preference was unrelated to it.
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
            val serviceIntent = Intent(context, CallRecorderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to restart recorder after " + action, e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
