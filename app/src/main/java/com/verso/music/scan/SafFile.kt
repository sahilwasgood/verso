package com.verso.music.scan

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

/**
 * Writes audio/sidecar files through Storage Access Framework trees the user
 * picked (F-Droid policy: SAF instead of MANAGE_EXTERNAL_STORAGE). Attempts,
 * in order: a picked tree covering the file, its MediaStore row, a direct
 * write (files Verso created itself). New files (e.g. .lrc sidecars) are
 * created inside a picked tree.
 */
object SafFile {

    private var app: Context? = null

    fun bind(c: Context) {
        if (app == null) app = c.applicationContext
    }

    /** True once at least one folder grant exists (after the user picks). */
    fun canWrite(): Boolean {
        val c = app ?: return false
        return ScanFolders.treeUris(c).isNotEmpty()
    }

    /** Error message or null on success. */
    fun write(target: File, bytes: ByteArray): String? {
        val c = app ?: return "File access not initialised yet - try again."
        return try {
            val ok = writeTree(c, target, bytes)
                || writeMediaStore(c, target, bytes)
                || writeDirect(target, bytes)
            if (ok) null
            else "No writable location for ${target.name}: pick the folder " +
                "on the home screen first."
        } catch (e: Exception) {
            e.message?.take(140) ?: e.javaClass.simpleName
        }
    }

    /** True when [target] was rewritten inside a picked tree. */
    private fun writeTree(c: Context, target: File, bytes: ByteArray): Boolean {
        for (raw in ScanFolders.treeUris(c)) {
            val tree = runCatching { Uri.parse(raw) }.getOrNull() ?: continue
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }
                .getOrNull() ?: continue
            val vol = treeId.substringBefore(':')
            val dir = ScanFolders.dirOf(vol) ?: continue
            val rel = target.absolutePath.removePrefix("$dir/")
            if (rel == target.absolutePath) continue // different volume

            // Task 1: the document already exists (audio files, tracked sidecars).
            val child = DocumentsContract.buildDocumentUriUsingTree(
                tree, "$vol:" + rel,
            )
            if (runCatching {
                    c.contentResolver.openOutputStream(child, "wt")?.use {
                        it.write(bytes)
                    } != null
                }.getOrDefault(false)
            ) return true

            if (target.exists()) continue // exists but not ours to touch

            // Task 2: create it (.lrc/.txt sidecars) in the covering tree.
            var parent = runCatching { DocumentFile.fromTreeUri(c, tree) }
                .getOrNull() ?: continue
            for (seg in rel.split('/').dropLast(1)) {
                parent = parent.findFile(seg)?.takeIf { it.isDirectory }
                    ?: parent.createDirectory(seg)
                    ?: return false
            }
            val name = target.name
            val parentDocId = runCatching {
                DocumentsContract.getDocumentId(parent.uri)
            }.getOrNull() ?: DocumentsContract.getTreeDocumentId(tree)
            val docUri = parent.findFile(name)?.uri
                ?: DocumentsContract.createDocument(
                    c.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(tree, parentDocId),
                    mimeFor(name), name,
                ) ?: continue
            val ok = runCatching {
                c.contentResolver.openOutputStream(docUri, "wt")?.use {
                    it.write(bytes)
                } != null
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /** Rewrites [target] through its MediaStore row when one exists. */
    private fun writeMediaStore(c: Context, target: File, bytes: ByteArray): Boolean {
        val coll = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val id = runCatching {
            c.contentResolver.query(
                coll, arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DATA}=?", arrayOf(target.absolutePath), null,
            )?.use { if (it.moveToFirst()) it.getLong(0) else null }
        }.getOrNull() ?: return false
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        return runCatching {
            c.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(bytes)
            } != null
        }.getOrDefault(false)
    }

    /** Last resort: files on app-owned paths (cache, exported dirs). */
    private fun writeDirect(target: File, bytes: ByteArray): Boolean = runCatching {
        FileOutputStream(target).use { it.write(bytes) }
    }.isSuccess

    private fun mimeFor(name: String): String = when (Regex("\\.(\\w+)$").find(name)
        ?.groupValues?.get(1)?.lowercase()) {
        "lrc" -> "text/plain"
        "txt" -> "text/plain"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a", "mp4" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}
