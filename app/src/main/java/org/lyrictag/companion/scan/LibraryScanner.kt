package org.lyrictag.companion.scan

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lyrictag.companion.db.TrackDao
import org.lyrictag.companion.db.TrackEntity
import org.lyrictag.companion.tags.TagManager
import org.lyrictag.companion.tags.TagResult
import java.io.File

/**
 * Scans audio files via MediaStore (fast enumeration) + jaudiotagger
 * (authoritative tags incl. embedded lyrics). Skips unchanged files by
 * lastModified. Writes TrackEntity rows.
 */
class LibraryScanner(
    private val context: Context,
    private val tracks: TrackDao,
    private val cache: org.lyrictag.companion.db.LyricsCacheDao,
) {

    data class Progress(val done: Int, val total: Int)

    data class ScanResult(val scanned: Int, val addedOrUpdated: Int, val removed: Int)

    suspend fun scan(onProgress: (Progress) -> Unit = {}): ScanResult {
        val uris = collectAudio(onProgress)
        var done = 0
        var updated = 0
        val batch = mutableListOf<TrackEntity>()
        for ((path, lastMod, durationMs, mime) in uris) {
            val existing = tracks.byPath(path)
            if (existing != null && existing.lastModified == lastMod && existing.cacheKey.isNotEmpty()) {
                done++
                if (done % 50 == 0) onProgress(Progress(done, uris.size))
                continue
            }
            val entity = withContext(Dispatchers.IO) {
                readEntity(path, lastMod, durationMs, mime, existing?.ignored ?: false)
            }
            if (entity != null) { batch += entity; updated++ }
            if (batch.size >= 100) { tracks.upsertAll(batch); batch.clear() }
            done++
            if (done % 20 == 0) onProgress(Progress(done, uris.size))
        }
        if (batch.isNotEmpty()) tracks.upsertAll(batch)
        // Prune files that no longer exist (deleted/renamed/moved).
        val seen = uris.map { it.path }.toSet()
        val stale = tracks.allPaths().filter { it !in seen }
        for (chunk in stale.chunked(200)) tracks.deletePaths(chunk)
        // Reconcile: flag tracks whose lyrics arrived via cache since last scan.
        tracks.resetCached()
        for (key in cache.allKeys()) {
            val (full, loose) = org.lyrictag.companion.db.splitCacheKey(key)
            tracks.markCached(full, loose)
        }
        onProgress(Progress(uris.size, uris.size))
        return ScanResult(uris.size, updated, stale.size)
    }

    private data class AudioRow(val path: String, val lastMod: Long, val durationMs: Long?, val mime: String?)

    private suspend fun collectAudio(onProgress: (Progress) -> Unit): List<AudioRow> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<AudioRow>()
            val coll = if (Build.VERSION.SDK_INT >= 29)
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE,
            )
            context.contentResolver.query(
                coll, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
            )?.use { c ->
                val iPath = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val iMod = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                while (c.moveToNext()) {
                    val p = c.getString(iPath) ?: continue
                    if (!p.endsWith(".mp3", true) && !p.endsWith(".flac", true) &&
                        !p.endsWith(".m4a", true) && !p.endsWith(".ogg", true) &&
                        !p.endsWith(".opus", true) && !p.endsWith(".mp4", true)
                    ) continue
                    out += AudioRow(p, c.getLong(iMod) * 1000,
                        c.getLong(iDur).takeIf { it > 0 }, c.getString(iMime))
                }
            }
            out
        }

    private fun readEntity(
        path: String, lastMod: Long, durationMs: Long?, mime: String?, ignored: Boolean,
    ): TrackEntity? {
        val file = File(path)
        if (!file.canRead()) return null
        return when (val r = TagManager.read(file)) {
            is TagResult.Ok -> TrackEntity(
                path = path, title = r.tags.title, artist = r.tags.artist,
                album = r.tags.album, albumArtist = r.tags.albumArtist,
                trackNo = r.tags.track?.substringBefore('/')?.toIntOrNull(),
                discNo = r.tags.disc?.substringBefore('/')?.toIntOrNull(),
                year = r.tags.year, genre = r.tags.genre,
                durationMs = durationMs ?: r.info.durationMs,
                mimeType = mime ?: r.info.mimeType, lastModified = lastMod,
                hasEmbeddedLyrics = !r.tags.lyrics.isNullOrBlank(),
                hasExternalLyrics = hasSidecar(path),
                hasCachedLyrics = false, // reconciled against lyrics_cache after scan
                cacheKey = org.lyrictag.companion.core.TrackNormalizer.cacheKey(
                    r.tags.title, r.tags.artist, r.tags.album,
                ),
                needsReview = r.tags.title.isNullOrBlank() || r.tags.artist.isNullOrBlank(),
                ignored = ignored,
            )
            else -> null // unreadable files are skipped, never crash the scan
        }
    }

    private fun hasSidecar(path: String): Boolean {
        val base = path.substringBeforeLast('.')
        return File("$base.lrc").exists() || File("$base.txt").exists()
    }
}
