package com.callrecorder.recorder

import android.content.Context
import android.util.Log
import com.callrecorder.model.RecorderConfig
import com.callrecorder.model.RecordingFormat
import com.callrecorder.shizuku.ShizukuRecorder
import java.io.File

/**
 * Chooses between the Shizuku (shell-identity) path and the plain MediaRecorder
 * path at start time, and falls back without losing the call if Shizuku is
 * missing, unauthorised or dies mid-way.
 */
class PreferredAudioRecorder(
    context: Context,
    private val shizukuPreferred: () -> Boolean,
) : AudioRecorder {

    private val shizuku = ShizukuRecorder(context.applicationContext)
    private val shizukuRecorder = ShizukuAudioRecorder(shizuku)
    private val media = MediaRecorderAudioRecorder()

    private var usingShizuku = false

    override var isRecording: Boolean = false
        private set

    @Volatile
    var lastUsedSourceName: String? = null
        private set

    /** True when the next start() would go through Shizuku. */
    fun willUseShizuku(): Boolean = try {
        shizukuPreferred() && shizuku.isSupported && shizuku.hasPermission
    } catch (e: Throwable) {
        false
    }

    /** Binds the user service early so the call does not pay for it. */
    fun prepare() {
        if (willUseShizuku()) {
            Thread({ shizuku.bind() }, "shizuku-bind").start()
        }
    }

    override fun start(config: RecorderConfig, outputFile: File) {
        if (willUseShizuku()) {
            try {
                shizukuRecorder.start(config, outputFile)
                usingShizuku = true
                isRecording = true
                lastUsedSourceName = shizukuRecorder.lastUsedSourceName
                Log.i(TAG, "recording through Shizuku (" + lastUsedSourceName + ")")
                return
            } catch (e: Throwable) {
                Log.w(TAG, "Shizuku capture failed, falling back to MediaRecorder", e)
                try {
                    shizukuRecorder.stop()
                } catch (_: Throwable) {
                }
                usingShizuku = false
            }
        }
        media.start(config, outputFile)
        isRecording = true
        lastUsedSourceName = media.lastUsedSourceName
    }

    override fun stop() {
        if (usingShizuku) {
            shizukuRecorder.stop()
        } else {
            media.stop()
        }
        usingShizuku = false
        isRecording = false
    }

    fun maxAmplitude(): Int = if (usingShizuku) shizukuRecorder.maxAmplitude() else media.maxAmplitude()

    fun release() {
        try {
            shizuku.unbind()
        } catch (_: Throwable) {
        }
    }

    /** The container the Shizuku path always produces. */
    fun forcedFormat(config: RecorderConfig): RecordingFormat =
        if (willUseShizuku()) RecordingFormat.WAV_PCM else config.format

    companion object {
        private const val TAG = "PreferredRecorder"
    }
}
