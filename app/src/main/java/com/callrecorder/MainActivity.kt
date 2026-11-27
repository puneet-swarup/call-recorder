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

    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var storageText: TextView
    private lateinit var fabRecord: com.google.android.material.floatingactionbutton.FloatingActionButton
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
        val date: String,
        val size: String,
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
        toolbar = findViewById(R.id.toolbar)
        storageText = findViewById(R.id.storageText)
        fabRecord = findViewById(R.id.fabRecord)
        recordingsContainer = findViewById(R.id.recordingsContainer)
        recordingsEmpty = findViewById(R.id.recordingsEmpty)
        setSupportActionBar(toolbar)
    }

    private fun wireListeners() {
        fabRecord.setOnClickListener { onToggleClicked() }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add(0, MENU_TOGGLE, 0, R.string.btn_start)
        menu.add(0, MENU_TEST, 1, R.string.menu_test)
        menu.add(0, MENU_SHARE, 2, R.string.menu_share_all)
        menu.add(0, MENU_UPDATE, 3, R.string.menu_check_updates)
        menu.add(0, MENU_SETTINGS, 4, R.string.menu_settings)
        return true
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        menu.findItem(MENU_TOGGLE)?.setTitle(
            if (settings.isRecorderEnabled()) R.string.btn_stop else R.string.btn_start,
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean = when (item.itemId) {
        MENU_TOGGLE -> { onToggleClicked(); true }
        MENU_TEST -> {
            if (testRecorder == null) startTestRecording() else stopTestRecording()
            true
        }
        MENU_SHARE -> { shareAll(); true }
        MENU_UPDATE -> { checkForUpdates(); true }
        MENU_SETTINGS -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
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
        storageText.text = freeSpaceText()
        invalidateOptionsMenu()
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
        val playing = playingKey == item.key
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 20, 16, 20)
            isClickable = true
            setOnClickListener { togglePlay(item) }
        }

        val line1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        line1.addView(TextView(this).apply {
            text = item.name
            textSize = 18f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        line1.addView(TextView(this).apply {
            text = if (playing) "\u25A0" else "\u25B6"
            textSize = 14f
            setPadding(12, 0, 0, 0)
        })
        row.addView(line1)

        val line2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 0)
        }
        line2.addView(View(this).apply {
            setBackgroundColor(0xFF00C853.toInt())
            layoutParams = LinearLayout.LayoutParams(14, 14).apply { marginEnd = 12 }
        })
        line2.addView(TextView(this).apply {
            text = item.date
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        line2.addView(TextView(this).apply {
            text = item.size
            textSize = 12f
            setTextColor(0xFF888888.toInt())
        })
        row.addView(line2)

        return row
    }

    private fun collectLocal(): List<Item> {
        val dir = File(filesDir, "recordings")
        val files = dir.listFiles { f -> f.isFile && !f.name.startsWith(".") } ?: emptyArray()
        return files.sortedByDescending { it.lastModified() }.map { file ->
            Item(
                key = file.absolutePath,
                name = file.name,
                date = describeDate(file.lastModified()),
                size = describeSize(file.length()),
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
                            date = describeDate(modified),
                            size = describeSize(size),
                            file = null,
                            uri = uri,
                        ),
                    )
                    count++
                }
            }
        return results
    }

    private fun describeDate(modified: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(modified))

    private fun describeSize(size: Long): String {
        val kb = size / 1024
        return if (kb > 1024) String.format(Locale.US, "%.1f MB", kb / 1024.0) else "$kb kB"
    }

    private fun freeSpaceText(): String {
        return try {
            val stat = android.os.StatFs(filesDir.absolutePath)
            val freeGb = stat.availableBytes / (1024.0 * 1024.0 * 1024.0)
            String.format(Locale.US, "%.1f GB free", freeGb)
        } catch (_: Exception) {
            ""
        }
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
        try {
            val dir = File(filesDir, "recordings").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "test_" + stamp + ".wav")
            val recorder = MediaRecorderAudioRecorder()
            recorder.start(settings.load().copy(format = RecordingFormat.WAV_PCM), file)
            testRecorder = recorder
            testFile = file
            toast(getString(R.string.notification_recording))
            handler.postDelayed({ stopTestRecording() }, 5000)
        } catch (e: Exception) {
            toast(getString(R.string.test_recording_failed) + ": " + (e.message ?: ""))
            testRecorder = null
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
        private const val MENU_TOGGLE = 1
        private const val MENU_TEST = 2
        private const val MENU_SHARE = 3
        private const val MENU_UPDATE = 4
        private const val MENU_SETTINGS = 5
    }
}
