package com.verso.music.scan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which music folders Verso may edit: SAF trees picked by the user
 * (F-Droid policy: STORAGE ACCESS FRAMEWORK instead of MANAGE_EXTERNAL_STORAGE).
 * A tree lists its storage volume id (primary, 0F69-1920, ...); [dirOf]
 * maps it back to the concrete /storage path the write layer needs.
 */
object ScanFolders {

    private const val PREFS = "lyrictag"
    private const val KEY_TREES = "saf_trees"

    /** Persisted tree URIs (empty until the user picks at least one). */
    fun treeUris(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_TREES, null)?.toList() ?: emptyList()

    /** Grants persistable access and remembers the tree. */
    fun addTree(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = prefs.getStringSet(KEY_TREES, null)?.toMutableSet() ?: mutableSetOf()
        cur += uri.toString()
        prefs.edit().putStringSet(KEY_TREES, cur).apply()
    }

    fun removeTree(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = prefs.getStringSet(KEY_TREES, null)?.toMutableSet() ?: mutableSetOf()
        cur -= uri.toString()
        prefs.edit().putStringSet(KEY_TREES, cur).apply()
    }

    /** Tree URIs' storage volume id, e.g. "primary" or an SD card id. */
    fun volumeOf(uri: Uri): String? = runCatching {
        val id = android.provider.DocumentsContract.getTreeDocumentId(uri)
        id.substringBefore(':').takeIf { it.isNotBlank() }
    }.getOrNull()

    /** "primary" -> /storage/emulated/0; SD cards by their volume id. */
    fun dirOf(volumeId: String): String? =
        when (volumeId) {
            "primary" -> "/storage/emulated/0"
            else -> if (volumeId.isBlank() || volumeId.contains(':')) null
            else "/storage/$volumeId"
        }

    /** Absolute folder paths granted across all trees (for scope checks). */
    fun dirs(context: Context): Set<String> =
        treeUris(context).mapNotNull { raw ->
            runCatching { Uri.parse(raw) }.getOrNull()?.let {
                dirOf(runCatching {
                    it.path?.substringAfter("/tree/", "").orEmpty().substringBefore(':')
                }.getOrDefault(""))
            }
        }.toSet()

    /** True when [path] lives under one of [granted] absolute folders. */
    fun inScope(path: String, granted: Set<String>): Boolean =
        granted.any { path == it || path.startsWith("$it/") }

    /** Distinct top-level relative dirs holding audio, with file counts. */
    suspend fun candidates(context: Context): List<DirInfo> = withContext(Dispatchers.IO) {
        val counts = linkedMapOf<String, Int>()
        val proj = arrayOf(MediaStore.Audio.Media.DATA)
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null,
        )?.use { c ->
            val i = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val rel = relative(c.getString(i) ?: continue) ?: continue
                val top = rel.substringBefore('/')
                counts[top] = (counts[top] ?: 0) + 1
            }
        }
        counts.map { (dir, count) -> DirInfo(dir, count) }.sortedByDescending { it.count }
    }

    data class DirInfo(val dir: String, val count: Int)

    /** "/storage/emulated/0/Music/x.mp3" -> "Music/x.mp3"; SD cards too. */
    fun relative(path: String): String? {
        val m = Regex("^/storage/(?:emulated/0|[^/]+)/(.+)$").matchEntire(path)
        return m?.groupValues?.get(1)
    }
}
