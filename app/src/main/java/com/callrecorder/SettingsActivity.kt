package com.callrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.callrecorder.model.RecordingFormat
import com.callrecorder.model.RecordingQuality
import com.callrecorder.settings.SettingsRepository
import com.callrecorder.update.UpdateChecker
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Everything that used to crowd the home screen, now on its own screen. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository

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
    private lateinit var checkShizuku: CheckBox
    private lateinit var shareRecipient: EditText
    private lateinit var btnSaveRecipient: Button
    private lateinit var checkAutoShare: CheckBox
    private lateinit var btnShareAll: Button
    private lateinit var checkMinimise: CheckBox
    private lateinit var currentVersion: TextView
    private lateinit var updateStatus: TextView
    private lateinit var btnCheckUpdates: Button
    private lateinit var btnOpenRelease: Button
    private lateinit var btnBack: Button

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
        setContentView(R.layout.activity_settings)
        bindViews()
        buildOptions()
        wireListeners()
        requestPermissionsIfNeeded()
        refreshUi()
    }

    override fun onDestroy() {
        updateExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun bindViews() {
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
        checkShizuku = findViewById(R.id.checkShizuku)
        checkShizuku.visibility = View.GONE
        shareRecipient = findViewById(R.id.shareRecipient)
        btnSaveRecipient = findViewById(R.id.btnSaveRecipient)
        checkAutoShare = findViewById(R.id.checkAutoShare)
        btnShareAll = findViewById(R.id.btnShareAll)
        checkMinimise = findViewById(R.id.checkMinimise)
        currentVersion = findViewById(R.id.currentVersion)
        updateStatus = findViewById(R.id.updateStatus)
        btnCheckUpdates = findViewById(R.id.btnCheckUpdates)
        btnOpenRelease = findViewById(R.id.btnOpenRelease)
        btnBack = findViewById(R.id.btnBack)
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
        btnBack.setOnClickListener { finish() }
        btnGrantPermissions.setOnClickListener { requestPermissionsIfNeeded() }
        btnChooseFolder.setOnClickListener { folderPicker.launch(null) }
        btnResetFolder.setOnClickListener {
            settings.save(settings.load().copy(storageFolderUri = null))
            refreshUi()
        }
        formatGroup.setOnCheckedChangeListener { group, checkedId ->
            val tag = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            settings.save(settings.load().copy(format = RecordingFormat.fromId(tag)))
        }
        qualityGroup.setOnCheckedChangeListener { group, checkedId ->
            val tag = group.findViewById<RadioButton>(checkedId)?.tag as? String ?: return@setOnCheckedChangeListener
            settings.save(settings.load().copy(quality = RecordingQuality.fromId(tag)))
        }
        checkCellular.setOnCheckedChangeListener { _, c -> settings.save(settings.load().copy(recordCellularCalls = c)) }
        checkVoip.setOnCheckedChangeListener { _, c -> settings.save(settings.load().copy(recordVoipCalls = c)) }
        checkAutoStart.setOnCheckedChangeListener { _, c -> settings.save(settings.load().copy(autoStart = c)) }
        btnSaveRecipient.setOnClickListener { saveRecipient() }
        checkAutoShare.setOnCheckedChangeListener { _, c ->
            settings.save(settings.load().copy(autoShareOnComplete = c))
        }
        btnShareAll.setOnClickListener { shareAllRecordings() }
        checkMinimise.setOnCheckedChangeListener { _, c ->
            settings.save(settings.load().copy(minimiseMode = c))
        }
        btnCheckUpdates.setOnClickListener { checkForUpdates() }
        btnOpenRelease.setOnClickListener { openReleasePage() }
    }

    private fun refreshUi() {
        val config = settings.load()
        permissionsCard.visibility = if (hasPermissions()) View.GONE else View.VISIBLE
        val folderLabel = config.storageFolderUri?.let { friendlyFolder(it) } ?: getString(R.string.storage_app_private)
        storagePath.text = folderLabel
        checkCellular.isChecked = config.recordCellularCalls
        checkVoip.isChecked = config.recordVoipCalls
        checkAutoStart.isChecked = config.autoStart
        shareRecipient.setText(config.shareRecipient ?: "")
        checkAutoShare.isChecked = config.autoShareOnComplete
        checkMinimise.isChecked = config.minimiseMode
        currentVersion.text = getString(
            R.string.updates_current,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
        syncRadioGroup(formatGroup, config.format.id)
        syncRadioGroup(qualityGroup, config.quality.id)
    }

    private fun syncRadioGroup(group: RadioGroup, tagValue: String) {
        for (i in 0 until group.childCount) {
            val button = group.getChildAt(i) as? RadioButton ?: continue
            if (button.tag == tagValue) {
                group.check(button.id)
                return
            }
        }
    }

    private fun friendlyFolder(uriString: String): String = try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
        val name = docId.substringAfterLast(':')
        if (name.isBlank()) getString(R.string.storage_app_private) else name
    } catch (_: Exception) {
        getString(R.string.storage_app_private)
    }

    private fun saveRecipient() {
        val value = shareRecipient.text.toString().trim()
        settings.save(settings.load().copy(shareRecipient = value.ifBlank { null }))
        toast(getString(R.string.sharing_saved))
    }

    private fun shareAllRecordings() {
        val files = listRecordings().take(20)
        if (files.isEmpty()) {
            toast(getString(R.string.recordings_empty))
            return
        }
        val uris = ArrayList(files.map { fileToShareUri(it) })
        val recipient = settings.load().shareRecipient
        val subject = getString(R.string.sharing_title)
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!recipient.isNullOrBlank()) intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
        try {
            startActivity(Intent.createChooser(intent, subject))
        } catch (_: Exception) {
            toast(getString(R.string.sharing_no_app))
        }
    }

    private fun listRecordings(): List<File> {
        val dir = File(filesDir, "recordings")
        val files = dir.listFiles { f -> f.isFile && !f.name.startsWith(".") } ?: emptyArray()
        return files.sortedByDescending { it.lastModified() }
    }

    private fun fileToShareUri(file: File): Uri =
        androidx.core.content.FileProvider.getUriForFile(this, packageName + ".recordings", file)

    private fun checkForUpdates() {
        btnCheckUpdates.isEnabled = false
        updateStatus.text = getString(R.string.updates_checking)
        btnOpenRelease.visibility = View.GONE
        updateExecutor.execute {
            val fetcher = UpdateChecker.Fetcher { url: String -> httpGet(url) }
            val checker = UpdateChecker(BuildConfig.VERSION_NAME, fetcher)
            val result = checker.check()
            runOnUiThread {
                btnCheckUpdates.isEnabled = true
                when (result) {
                    is UpdateChecker.Result.UpToDate -> {
                        updateStatus.text = getString(R.string.updates_up_to_date)
                        latestReleaseUrl = null
                    }
                    is UpdateChecker.Result.UpdateAvailable -> {
                        updateStatus.text = getString(R.string.updates_available, result.latestVersion)
                        latestReleaseUrl = result.releaseUrl
                        btnOpenRelease.visibility = View.VISIBLE
                    }
                    is UpdateChecker.Result.NoReleases -> {
                        updateStatus.text = getString(R.string.updates_no_releases)
                        latestReleaseUrl = null
                    }
                    is UpdateChecker.Result.Failure -> {
                        updateStatus.text = getString(R.string.updates_error)
                        latestReleaseUrl = null
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1002
    }
}
