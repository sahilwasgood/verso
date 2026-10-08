package org.lyrictag.companion.poweramp

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Reads Poweramp's *documented* content provider (`content://com.maxmpz.audioplayer.data/…`).
 *
 * This is a public, documented API — not reverse engineering. It exposes the
 * exact folders and files Poweramp has in its library, so LyricTag can scope
 * its scan to genuinely the same set ("39" because Poweramp says 39).
 *
 * All queries are wrapped: if Poweramp is absent, the provider is blocked, or
 * columns change, we return null and the scanner falls back to MediaStore.
 */
object PowerampLibrary {

    private const val TAG = "PowerampLibrary"
    private const val AUTH = "content://com.maxmpz.audioplayer.data"

    data class PaFile(
        val absPath: String,
        val title: String?,
        val artist: String?,
        val album: String?,
        val year: String?,
        val durationMs: Long?,
        val powerampHasLyrics: Boolean,
    )

    /** Exact file set of Poweramp's library, or null when unreadable. */
    fun readFiles(context: Context): List<PaFile>? {
        return try {
            val out = mutableListOf<PaFile>()
            for (folderId in folderIds(context)) {
                val folderPath = folderPath(context, folderId) ?: continue
                out += filesIn(context, folderId, folderPath)
            }
            out.ifEmpty { null }
        } catch (t: Throwable) {
            Log.w(TAG, "Poweramp provider unreadable, falling back", t)
            null
        }
    }

    /** One-line diagnosis for the dashboard ("why not Poweramp scope"). */
    fun diagnose(context: Context): String {
        return try {
            val ids = folderIds(context)
            if (ids.isEmpty()) return "provider answered, 0 folders"
            val paths = ids.mapNotNull { folderPath(context, it) }
            if (paths.isEmpty()) return "${ids.size} folders, paths unreadable"
            val n = paths.sumOf { p ->
                val id = ids[paths.indexOf(p)]
                filesIn(context, id, p).size
            }
            "provider ok ($n files)"
        } catch (t: Throwable) {
            "${t.javaClass.simpleName}: ${t.message?.take(80)}"
        }
    }

    fun isAvailable(context: Context): Boolean = isReadable(context)

    /** True when Poweramp is installed (regardless of provider access). */
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PowerampContract.POWERAMP_PACKAGE, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** True only when file listings answer (the folders table alone is not
     * enough — Poweramp gates folders/#/files behind its data-access grant).
     * NOTE: projections must be table-qualified — the provider joins
     * folder_files/albums/artists, so bare `_id`/`name` throw
     * "ambiguous column" SQLiteException (official TableDefs qualifies them). */
    fun isReadable(context: Context): Boolean {
        return try {
            val ids = folderIds(context)
            if (ids.isEmpty()) return false
            var rows = 0
            context.contentResolver.query(
                Uri.parse("$AUTH/folders/${ids.first()}/files"),
                arrayOf("folder_files._id"), null, null, null,
            )?.use { c -> rows = c.count }
            rows > 0
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Opens Poweramp's data-access grant screen. After the user approves,
     * the next scan reads the exact Poweramp library.
     *
     * A broadcast alone can't open UI, so this goes to Poweramp's API
     * Activity first (the documented alternate target); falls back to the
     * receiver broadcast on older builds.
     */
    fun requestAccess(context: Context) {
        val viaActivity = try {
            val intent = android.content.Intent(PowerampContract.ACTION_ASK_FOR_DATA_PERMISSION).apply {
                component = android.content.ComponentName(
                    PowerampContract.POWERAMP_PACKAGE,
                    PowerampContract.POWERAMP_API_ACTIVITY,
                )
                putExtra(PowerampContract.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(PowerampContract.EXTRA_PACKAGE, context.packageName)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "ask-for-data via activity failed, trying broadcast", t)
            false
        }
        if (!viaActivity) {
            try {
                val intent = android.content.Intent(PowerampContract.ACTION_ASK_FOR_DATA_PERMISSION).apply {
                    component = android.content.ComponentName(
                        PowerampContract.POWERAMP_PACKAGE,
                        PowerampContract.POWERAMP_API_RECEIVER,
                    )
                    putExtra(PowerampContract.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(PowerampContract.EXTRA_PACKAGE, context.packageName)
                }
                context.sendBroadcast(intent)
            } catch (t: Throwable) {
                Log.w(TAG, "ask-for-data failed", t)
            }
        }
    }

    private fun folderIds(context: Context): List<Long> {
        val ids = mutableListOf<Long>()
        context.contentResolver.query(
            Uri.parse("$AUTH/folders"), arrayOf("_id"), null, null, null,
        )?.use { c ->
            val i = c.getColumnIndex("_id")
            while (c.moveToNext()) ids += c.getLong(i)
        }
        return ids
    }

    /** "primary/Music/" -> "/storage/emulated/0/Music/" */
    private fun folderPath(context: Context, folderId: Long): String? {
        context.contentResolver.query(
            Uri.parse("$AUTH/folders/$folderId"), arrayOf("path"), null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val p = c.getString(c.getColumnIndex("path")) ?: return null
                return when {
                    p.startsWith("primary/") ->
                        "/storage/emulated/0/" + p.removePrefix("primary/")
                    Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}/").containsMatchIn(p) ->
                        "/storage/$p"
                    else -> null
                }
            }
        }
        return null
    }

    private fun filesIn(context: Context, folderId: Long, folderPath: String): List<PaFile> {
        val out = mutableListOf<PaFile>()
        // Qualified projections (see isReadable note): bare names are ambiguous
        // in Poweramp's folder_files⨝albums⨝artists join; a null projection
        // returns _id only, which would silently yield zero tracks.
        context.contentResolver.query(
            Uri.parse("$AUTH/folders/$folderId/files"),
            arrayOf(
                "folder_files.name", "title_tag", "artists.artist", "albums.album",
                "folder_files.year", "folder_files.duration", "has_lyrics_tag",
            ),
            null, null, null,
        )?.use { c ->
            val iName = c.getColumnIndex("name")
            val iTitle = c.getColumnIndex("title_tag")
            val iArtist = c.getColumnIndex("artist")
            val iAlbum = c.getColumnIndex("album")
            val iYear = c.getColumnIndex("year")
            val iDur = c.getColumnIndex("duration")
            val iLyr = c.getColumnIndex("has_lyrics_tag")
            while (c.moveToNext()) {
                val name = if (iName >= 0) c.getString(iName) else null ?: continue
                if (!isAudio(name)) continue
                out += PaFile(
                    absPath = folderPath + name,
                    title = iTitle.takeIf { it >= 0 }?.let { c.getString(it) },
                    artist = iArtist.takeIf { it >= 0 }?.let { c.getString(it) },
                    album = iAlbum.takeIf { it >= 0 }?.let { c.getString(it) },
                    year = iYear.takeIf { it >= 0 }?.let {
                        c.getInt(it).takeIf { y -> y in 1..9999 }?.toString()
                    },
                    durationMs = iDur.takeIf { it >= 0 }?.let { c.getLong(it) },
                    powerampHasLyrics = iLyr >= 0 && c.getInt(iLyr) != 0,
                )
            }
        }
        return out
    }

    private fun isAudio(name: String): Boolean =
        name.endsWith(".mp3", true) || name.endsWith(".flac", true) ||
            name.endsWith(".m4a", true) || name.endsWith(".ogg", true) ||
            name.endsWith(".opus", true) || name.endsWith(".mp4", true)
}
