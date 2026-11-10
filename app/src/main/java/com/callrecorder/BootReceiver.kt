package com.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.callrecorder.settings.SettingsRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = SettingsRepository(context)
        if (!settings.load().autoStart) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, CallRecorderService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "BootReceiver failed to start service", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
