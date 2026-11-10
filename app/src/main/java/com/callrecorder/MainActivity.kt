package com.callrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.callrecorder.model.RecorderConfig
import com.callrecorder.model.RecordingFormat
import com.callrecorder.model.RecordingQuality
import com.callrecorder.settings.SettingsRepository
import com.callrecorder.update.UpdateChecker
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository
    private lateinit var statusValue: TextView
    private lateinit var statusSummary: TextView
    private lateinit var statusFolder: TextView
    private lateinit var btnToggleService: Button
    private lateinit var permissionsCard: LinearLayout
    private lateinit var btnGrantPermissions: Button
    private lateinit var storagePath: TextView
    private lateinit var btnChooseFolder: Button
    private lateinit var btnResetFolder: Button
    private lateinit var formatGroup: RadioGroup
    private lateinit var qualityGroup: RadioGroup
    private lateinit var checkCellular: CheckBox
    private lateinit var checkVoip: CheckBox
    private lateinit var checkAutoStart: CheckBox
    private lateinit var recordingsEmpty: TextView
    private lateinit var recordingsContainer: LinearLayout

    private var player: MediaPlayer? = null

 private lateinit var currentVersion: TextView
 private lateinit var updateStatus: TextView
 private lateinit var btnCheckUpdates: Button
 private lateinit var btnOpenRelease: Button

 private val updateExecutor = Executors.newSingleThreadExecutor()
 private var latestReleaseUrl: String? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            toast(getString(R.string.playback_error))
            return@registerForActivityResult
        }
        settings.save(settings.load().copy(storageFolderUri = uri.toString()))
        refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)
        setContentView(R.layout.activity_main)
        bindViews()
        buildOptions()
        wireListeners()
        requestPermissionsIfNeeded()
        refreshUi()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun bindViews() {
        statusValue = findViewById(R.id.statusValue)
        statusSummary = findViewById(R.id.statusSummary)
        statusFolder = findViewById(R.id.statusFolder)
        btnToggleService = findViewById(R.id.btnToggleService)
        permissionsCard = findViewById(R.id.permissionsCard)
        btnGrantPermissions = findViewById(R.id.btnGrantPermissions)
        storagePath = findViewById(R.id.storagePath)
        btnChooseFolder = findViewById(R.id.btnChooseFolder)
        btnResetFolder = findViewById(R.id.btnResetFolder)
        formatGroup = findViewById(R.id.formatGroup)
        qualityGroup = findViewById(R.id.qualityGroup)
        checkCellular = findViewById(R.id.checkCellular)
        checkVoip = findViewById(R.id.checkVoip)
        checkAutoStart = findViewById(R.id.checkAutoStart)
        recordingsEmpty = findViewById(R.id.recordingsEmpty)
        recordingsContainer = findViewById(R.id.recordingsContainer)
        currentVersion = findViewById(R.id.currentVersion)
        updateStatus = findViewById(R.id.updateStatus)
        btnCheckUpdates = findViewById(R.id.btnCheckUpdates)
        btnOpenRelease = findViewById(R.id.btnOpenRelease)
    }

    private fun buildOptions() {
        formatGroup.removeAllViews()
        RecordingFormat.entries.forEach { format ->
            formatGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = format.label
                tag = format.id
                textSize = 15f
            })
        }
        qualityGroup.removeAllViews()
        RecordingQuality.all.forEach { quality ->
            qualityGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = quality.label
                tag = quality.id
                textSize = 15f
            })
        }
    }

    private fun wireListeners() {
        btnGrantPermissions.setOnClickListener { requestPermissionsIfNeeded() }
        btnChooseFolder.setOnClickListener { folderPicker.launch(null) }
        btnResetFolder.setOnClickListener {
            settings.save(settings.load().copy(storageFolderUri = null))
            refreshUi()
        }
        formatGroup.setOnCheckedChangeListener { group, checkedId ->
            val tag = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            settings.save(settings.load().copy(format = RecordingFormat.fromId(tag)))
            refreshUi()
        }
        qualityGroup.setOnCheckedChangeListener { group, checkedId ->
            val tag = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            settings.save(settings.load().copy(quality = RecordingQuality.fromId(tag)))
            refreshUi()
        }
        checkCellular.setOnCheckedChangeListener { _, c -> settings.save(settings.load().copy(recordCellularCalls = c)) }
        checkVoip.setOnCheckedChangeListener { _, c -> settings.save(settings.load().copy(recordVoipCalls = c)) }
        checkAutoStart.setOnCheckedChangeListener { _, c -> settings.save(settings.load().copy(autoStart = c)) }
        btnToggleService.setOnClickListener { toggleService() }
        btnCheckUpdates.setOnClickListener { checkForUpdates() }
        btnOpenRelease.setOnClickListener { openReleasePage() }
    }

    private fun checkForUpdates() {
        btnCheckUpdates.isEnabled = false
        updateStatus.text = getString(R.string.updates_checking)
        btnOpenRelease.visibility = View.GONE

        updateExecutor.execute {
            val fetcher = UpdateChecker.Fetcher { url: String -> httpGet(url) }
            val checker = UpdateChecker(BuildConfig.VERSION_CODE, fetcher)
            val result = checker.check()
            runOnUiThread {
                btnCheckUpdates.isEnabled = true
                when (result) {
                    is UpdateChecker.Result.UpToDate -> {
                        updateStatus.text = getString(R.string.updates_up_to_date)
                        latestReleaseUrl = null
                        btnOpenRelease.visibility = View.GONE
                    }
                    is UpdateChecker.Result.UpdateAvailable -> {
                        updateStatus.text = getString(R.string.updates_available, result.latestVersion)
                        latestReleaseUrl = result.releaseUrl
                        btnOpenRelease.visibility = View.VISIBLE
                    }
                    is UpdateChecker.Result.Failure -> {
                        updateStatus.text = getString(R.string.updates_error)
                        latestReleaseUrl = null
                        btnOpenRelease.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun openReleasePage() {
        val url = latestReleaseUrl ?: UpdateChecker.RELEASES_PAGE
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "call-recorder")
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP " + code)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun toggleService() {
        if (CallRecorderService.isRunning) {
            stopService(Intent(this, CallRecorderService::class.java))
        } else {
            ContextCompat.startForegroundService(this, Intent(this, CallRecorderService::class.java))
        }
        refreshUi()
    }

    private fun refreshUi() {
        val config = settings.load()
        val granted = hasPermissions()
        permissionsCard.visibility = if (granted) View.GONE else View.VISIBLE
        statusValue.text = getString(if (granted) R.string.status_ready else R.string.status_permissions_missing)
        statusSummary.text = getString(R.string.format_title) + ": " + config.format.label + "  \u00b7  " + getString(R.string.quality_title) + ": " + config.quality.label
        val folderLabel = config.storageFolderUri?.let { friendlyFolder(it) } ?: getString(R.string.storage_app_private)
        statusFolder.text = getString(R.string.storage_title) + ": " + folderLabel
        storagePath.text = folderLabel
        btnToggleService.setText(if (CallRecorderService.isRunning) R.string.btn_stop else R.string.btn_start)
        checkCellular.isChecked = config.recordCellularCalls
        checkVoip.isChecked = config.recordVoipCalls
        checkAutoStart.isChecked = config.autoStart
        currentVersion.text = getString(
 R.string.updates_current,
 BuildConfig.VERSION_NAME,
 BuildConfig.VERSION_CODE,
 )

 currentVersion.text = getString(
 R.string.updates_current,
 BuildConfig.VERSION_NAME,
 BuildConfig.VERSION_CODE,
 )

 syncRadioGroup(formatGroup, config.format.id)
        syncRadioGroup(qualityGroup, config.quality.id)
        refreshRecordings(config)
    }

    private fun syncRadioGroup(group: RadioGroup, tagValue: String) {
        for (i in 0 until group.childCount) {
            val rb = group.getChildAt(i) as RadioButton
            if (rb.tag == tagValue) { rb.isChecked = true; return }
        }
    }

    private fun friendlyFolder(uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            docId.substringAfter(':').ifBlank { docId }
        } catch (_: Exception) { uriString }
    }

    private fun refreshRecordings(config: RecorderConfig) {
        recordingsContainer.removeAllViews()
        val files = listRecordings()
        recordingsEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        files.take(20).forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this).apply {
                text = file.name
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val play = Button(this).apply {
                text = "Play"
                setOnClickListener { playFile(file) }
            }
            row.addView(label)
            row.addView(play)
            recordingsContainer.addView(row)
        }
    }

    private fun listRecordings(): List<File> {
        val dir = File(getDir("recordings", MODE_PRIVATE), "")
        val files = dir.listFiles { f -> f.isFile && !f.name.startsWith(".") } ?: emptyArray()
        return files.sortedByDescending { it.lastModified() }
    }

    private fun playFile(file: File) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        } catch (_: Exception) {
            toast(getString(R.string.playback_error))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hasPermissions(): Boolean =
        requiredPermissions().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun requiredPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        return perms
    }

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            refreshUi()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshUi()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}
