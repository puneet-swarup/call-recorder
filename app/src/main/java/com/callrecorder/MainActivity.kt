package com.callrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.callrecorder.model.RecordingFormat
import com.callrecorder.recorder.MediaRecorderAudioRecorder
import com.callrecorder.settings.SettingsRepository
import com.callrecorder.update.UpdateChecker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Home screen: the list of recordings, the way the original app worked.
 * Everything configurable lives behind the menu button, in SettingsActivity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository

    private lateinit var statusValue: TextView
    private lateinit var btnToggleService: Button
    private lateinit var btnTestRecording: Button
    private lateinit var btnSettings: Button
    private lateinit var recordingsContainer: LinearLayout
    private lateinit var recordingsEmpty: TextView

    private var player: MediaPlayer? = null
    private var playingKey: String? = null

    private var testRecorder: MediaRecorderAudioRecorder? = null
    private var testFile: File? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private data class Item(
        val key: String,
        val name: String,
        val detail: String,
        val file: File?,
        val uri: Uri?,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)
        setContentView(R.layout.activity_main)
        bindViews()
        wireListeners()
        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopTestRecording()
        releasePlayer()
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun bindViews() {
        statusValue = findViewById(R.id.statusValue)
        btnToggleService = findViewById(R.id.btnToggleService)
        btnTestRecording = findViewById(R.id.btnTestRecording)
        btnSettings = findViewById(R.id.btnSettings)
        recordingsContainer = findViewById(R.id.recordingsContainer)
        recordingsEmpty = findViewById(R.id.recordingsEmpty)
    }

    private fun wireListeners() {
        btnToggleService.setOnClickListener { onToggleClicked() }
        btnTestRecording.setOnClickListener {
            if (testRecorder == null) startTestRecording() else stopTestRecording()
        }
        btnSettings.setOnClickListener { showMenu() }
    }

    private fun showMenu() {
        val menu = PopupMenu(this, btnSettings)
        menu.menu.add(0, 1, 0, getString(R.string.menu_settings))
        menu.menu.add(0, 2, 1, getString(R.string.menu_share_all))
        menu.menu.add(0, 3, 2, getString(R.string.menu_check_updates))
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                2 -> {
                    shareAll()
                    true
                }
                3 -> {
                    checkForUpdates()
                    true
                }
                else -> false
            }
        }
        menu.show()
    }

    private fun onToggleClicked() {
        val nowEnabled = !settings.isRecorderEnabled()
        settings.setRecorderEnabled(nowEnabled)
        if (nowEnabled) {
            CallRecorderService.arm(this)
            if (!isAccessibilityServiceEnabled()) {
                showAccessibilityPrompt()
            }
        } else {
            stopService(Intent(this, CallRecorderService::class.java))
        }
        refresh()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = packageName + "/" + com.callrecorder.call.CallAudioAccessibilityService::class.java.name
        val enabled = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun showAccessibilityPrompt() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.accessibility_title)
            .setMessage(R.string.accessibility_message)
            .setPositiveButton(R.string.accessibility_open_settings) { _, _ ->
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                    toast(getString(R.string.accessibility_open_settings))
                }
            }
            .setNegativeButton(R.string.accessibility_later, null)
            .show()
    }

    // ------------------------------------------------------------- recordings

    private fun refresh() {
        val granted = hasPermissions()
        statusValue.text = when {
            !granted -> getString(R.string.status_permissions_missing)
            settings.isRecorderEnabled() -> getString(R.string.status_ready)
            else -> getString(R.string.status_disabled)
        }
        btnToggleService.setText(
            if (settings.isRecorderEnabled()) R.string.btn_stop else R.string.btn_start,
        )
        buildList(collectLocal())
        ioExecutor.execute {
            val external = try {
                collectExternal()
            } catch (_: Throwable) {
                emptyList()
            }
            if (external.isEmpty()) return@execute
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                buildList(collectLocal() + external)
            }
        }
    }

    private fun buildList(items: List<Item>) {
        recordingsContainer.removeAllViews()
        recordingsEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEach { item -> recordingsContainer.addView(buildRow(item)) }
    }

    private fun buildRow(item: Item): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(48, 24, 32, 24)
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(this).apply {
            text = item.name
            textSize = 16f
        })
        column.addView(TextView(this).apply {
            text = item.detail
            textSize = 12f
            setTextColor(0xFF888888.toInt())
        })
        column.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(column)

        val isPlaying = playingKey == item.key
        row.addView(Button(this).apply {
            text = if (isPlaying) "■" else "▶"
            setOnClickListener { togglePlay(item) }
        })
        return row
    }

    private fun collectLocal(): List<Item> {
        val dir = File(filesDir, "recordings")
        val files = dir.listFiles { f -> f.isFile && !f.name.startsWith(".") } ?: emptyArray()
        return files.sortedByDescending { it.lastModified() }.map { file ->
            Item(
                key = file.absolutePath,
                name = file.name,
                detail = describe(file.length(), file.lastModified()),
                file = file,
                uri = null,
            )
        }
    }

    /**
     * Recordings already on the phone, including the ones carried over from the
     * previous handset. MediaStore indexes them, so no folder permission is needed
     * beyond audio read access.
     */
    private fun collectExternal(): List<Item> {
        if (!hasExternalAudioPermission()) return emptyList()
        val results = mutableListOf<Item>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        val selection = "lower(${MediaStore.Audio.Media.DISPLAY_NAME}) LIKE ?"
        val args = arrayOf("%call%")
        contentResolver.query(collection, projection, selection, args, "${MediaStore.Audio.Media.DATE_MODIFIED} DESC")
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                var count = 0
                while (cursor.moveToNext() && count < 100) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val modified = cursor.getLong(dateCol) * 1000
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    results.add(
                        Item(
                            key = uri.toString(),
                            name = name,
                            detail = describe(size, modified),
                            file = null,
                            uri = uri,
                        ),
                    )
                    count++
                }
            }
        return results
    }

    private fun describe(size: Long, modified: Long): String {
        val kb = size / 1024
        val sizeText = if (kb > 1024) String.format(Locale.US, "%.1f MB", kb / 1024.0) else "$kb kB"
        val stamp = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(modified))
        return "$stamp  ·  $sizeText"
    }

    private fun togglePlay(item: Item) {
        if (playingKey == item.key) {
            releasePlayer()
            buildList(currentItems())
            return
        }
        releasePlayer()
        try {
            player = MediaPlayer().apply {
                if (item.file != null) setDataSource(item.file.absolutePath)
                else setDataSource(this@MainActivity, item.uri!!)
                prepare()
                start()
                setOnCompletionListener { releasePlayer(); buildList(currentItems()) }
            }
            playingKey = item.key
        } catch (_: Exception) {
            toast(getString(R.string.playback_error))
            releasePlayer()
        }
        buildList(currentItems())
    }

    private fun currentItems(): List<Item> = collectLocal()

    private fun releasePlayer() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        playingKey = null
    }

    private fun shareAll() {
        val files = collectLocal().mapNotNull { it.file }.take(20)
        if (files.isEmpty()) {
            toast(getString(R.string.recordings_empty))
            return
        }
        val uris = ArrayList(files.map {
            androidx.core.content.FileProvider.getUriForFile(this, packageName + ".recordings", it)
        })
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
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.sharing_title)))
        } catch (_: Exception) {
            toast(getString(R.string.sharing_no_app))
        }
    }

    // ------------------------------------------------------------------ updates

    private fun checkForUpdates() {
        toast(getString(R.string.updates_checking))
        ioExecutor.execute {
            val fetcher = UpdateChecker.Fetcher { url: String -> httpGet(url) }
            val result = try {
                UpdateChecker(BuildConfig.VERSION_NAME, fetcher).check()
            } catch (e: Exception) {
                UpdateChecker.Result.Failure(e.message ?: "unknown")
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when (result) {
                    is UpdateChecker.Result.UpdateAvailable -> offerUpdate(result)
                    is UpdateChecker.Result.UpToDate ->
                        toast(getString(R.string.updates_up_to_date))
                    is UpdateChecker.Result.NoReleases ->
                        toast(getString(R.string.updates_no_releases))
                    is UpdateChecker.Result.Failure ->
                        toast(getString(R.string.updates_error) + " " + result.reason)
                }
            }
        }
    }

    private fun offerUpdate(update: UpdateChecker.Result.UpdateAvailable) {
        val message = if (update.apkUrl == null) {
            getString(R.string.updates_available, update.latestVersion) + "\n" +
                getString(R.string.updates_no_apk)
        } else {
            getString(R.string.updates_available, update.latestVersion)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.notification_title)
            .setMessage(message)
            .setPositiveButton(R.string.updates_install) { _, _ ->
                if (update.apkUrl != null) downloadAndInstall(update.apkUrl) else openUrl(update.releaseUrl)
            }
            .setNegativeButton(R.string.accessibility_later, null)
            .show()
    }

    private fun downloadAndInstall(url: String) {
        toast(getString(R.string.updates_downloading))
        ioExecutor.execute {
            val file = try {
                val dir = File(cacheDir, "updates").apply { mkdirs() }
                val target = File(dir, "call-recorder-update.apk")
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "call-recorder")
                connection.inputStream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                connection.disconnect()
                target
            } catch (e: Exception) {
                runOnUiThread { toast(getString(R.string.updates_error) + " " + (e.message ?: "")) }
                return@execute
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                launchInstaller(file)
            }
        }
    }

    /**
     * Android will not install an APK without the user turning on "install unknown
     * apps" for this app, so ask for that first and come back.
     */
    private fun launchInstaller(file: File) {
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            !packageManager.canRequestPackageInstalls()
        ) {
            toast(getString(R.string.updates_allow_install))
            try {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + packageName),
                    ),
                )
            } catch (_: Exception) {
                toast(getString(R.string.updates_error))
            }
            return
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                packageName + ".recordings",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast(getString(R.string.updates_error) + " " + (e.message ?: ""))
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast(getString(R.string.updates_error))
        }
    }

    private fun httpGet(url: String): String {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
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

    // ---------------------------------------------------------- test recording

    private fun startTestRecording() {
        btnTestRecording.isEnabled = false
        try {
            val dir = File(filesDir, "recordings").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "test_" + stamp + ".wav")
            val recorder = MediaRecorderAudioRecorder()
            recorder.start(settings.load().copy(format = RecordingFormat.WAV_PCM), file)
            testRecorder = recorder
            testFile = file
            btnTestRecording.setText(R.string.test_recording_stop)
            toast(getString(R.string.notification_recording))
            handler.postDelayed({ stopTestRecording() }, 5000)
        } catch (e: Exception) {
            toast(getString(R.string.test_recording_failed) + ": " + (e.message ?: ""))
            testRecorder = null
        } finally {
            btnTestRecording.isEnabled = true
        }
    }

    private fun stopTestRecording() {
        val recorder = testRecorder ?: return
        testRecorder = null
        handler.removeCallbacksAndMessages(null)
        try {
            recorder.stop()
        } catch (_: Exception) {
        }
        btnTestRecording.setText(R.string.test_recording_short)
        btnTestRecording.isEnabled = true
        testFile = null
        refresh()
    }

    // ---------------------------------------------------------------- plumbing

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hasExternalAudioPermission(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPermissions(): Boolean =
        requiredPermissions().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun requiredPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
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
            refresh()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}
