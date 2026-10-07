package org.lyrictag.companion.scan

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which device folders LyricTag scans — mirrors Poweramp's own model, where
 * only user-selected music folders are scanned (not the whole device).
 *
 * Poweramp exposes no public API for its folder list, so the user picks
 * folders here. Default is the platform Music directory.
 */
object ScanFolders {

    private const val PREFS = "lyrictag"
    private const val KEY = "scan_dirs"

    /** Default scope: everything (narrowed automatically when Poweramp's library is readable). */
    val ALL: Set<String> = emptySet()
    val DEFAULT: Set<String> = ALL

    data class DirInfo(val dir: String, val count: Int)

    fun selected(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, null) ?: DEFAULT

    fun save(context: Context, dirs: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, dirs.toSet()).apply()
    }

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

    /** True when [path] lives under one of [selected] relative dirs (empty = all). */
    fun inScope(path: String, selected: Set<String>): Boolean {
        if (selected.isEmpty()) return true
        val rel = relative(path) ?: return false
        return selected.any { rel == it || rel.startsWith(it + "/") }
    }

    /** "/storage/emulated/0/Music/x.mp3" -> "Music/x.mp3"; SD cards too. */
    fun relative(path: String): String? {
        val m = Regex("^/storage/(?:emulated/0|[^/]+)/(.+)$").matchEntire(path)
        return m?.groupValues?.get(1)
    }
}
