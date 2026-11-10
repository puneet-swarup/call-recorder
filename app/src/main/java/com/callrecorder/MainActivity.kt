package com.callrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.callrecorder.settings.SettingsRepository

class MainActivity : AppCompatActivity() {
    private lateinit var settings: SettingsRepository
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)
        setContentView(buildContent())
        requestPermissionsIfNeeded()
        updateStatus()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        statusView = TextView(this).apply {
            textSize = 16F
            setPadding(0, 0, 0, 24)
        }
        root.addView(statusView)

        root.addView(
            Button(this).apply {
                text = getString(R.string.btn_start)
                setOnClickListener {
                    ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, CallRecorderService::class.java))
                    updateStatus()
                }
            }
        )

        root.addView(
            Button(this).apply {
                text = getString(R.string.btn_stop)
                setOnClickListener {
                    stopService(Intent(this@MainActivity, CallRecorderService::class.java))
                    updateStatus()
                }
            }
        )

        root.addView(
            TextView(this).apply {
                text = getString(R.string.permissions_needed)
                setPadding(0, 24, 0, 8)
            }
        )

        root.addView(
            Button(this).apply {
                text = getString(R.string.grant_permissions)
                setOnClickListener { requestPermissionsIfNeeded() }
            }
        )

        return root
    }

    private fun updateStatus() {
        val config = settings.load()
        statusView.text = buildString {
            append("${getString(R.string.label_status)}: ")
            append(if (hasPermissions()) "Ready" else "Permissions required")
            append("\n")
            append("${getString(R.string.label_format)}: ${config.format.label}\n")
            append("${getString(R.string.label_quality)}: ${config.quality.label}\n")
            append("${getString(R.string.label_folder)}: ${config.storageFolderUri ?: "App-private"}")
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms
    }

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            updateStatus()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateStatus()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}
