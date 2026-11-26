package com.callrecorder.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Writes finished recordings into the folder the user picked with the system
 * folder picker (a SAF tree URI), and lists what is already there.
 */
object SafStore {

    fun copyIntoFolder(context: Context, treeUri: Uri, source: File): Uri? {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val mime = mimeFor(source.name)
        val target = tree.findFile(source.name) ?: tree.createFile(mime, source.name) ?: return null
        return try {
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            }
            target.uri
        } catch (e: Exception) {
            null
        }
    }

    fun listAudio(context: Context, treeUri: Uri): List<DocumentFile> {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return tree.listFiles()
            .filter { it.isFile && it.name != null && isAudio(it.name!!) }
            .sortedByDescending { it.lastModified() }
    }

    fun isAudio(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".wav") || lower.endsWith(".mp3") || lower.endsWith(".m4a") ||
            lower.endsWith(".aac") || lower.endsWith(".amr") || lower.endsWith(".ogg") ||
            lower.endsWith(".opus") || lower.endsWith(".3gp")
    }

    private fun mimeFor(name: String): String = when {
        name.endsWith(".wav", true) -> "audio/wav"
        name.endsWith(".mp3", true) -> "audio/mpeg"
        name.endsWith(".m4a", true) -> "audio/mp4"
        name.endsWith(".amr", true) -> "audio/amr"
        name.endsWith(".ogg", true) -> "audio/ogg"
        else -> "audio/*"
    }
}
