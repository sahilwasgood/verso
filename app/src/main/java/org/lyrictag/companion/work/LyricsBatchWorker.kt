package org.lyrictag.companion.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lyrictag.companion.LyricTagApp

/**
 * Fetches lyrics for every track missing them. User-triggered from the
 * dashboard; cancellable; survives app backgrounding via WorkManager.
 * Progress (done/total) is published for the UI; failures per-track are
 * skipped silently (user can retry — nothing is overwritten).
 */
class LyricsBatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = LyricTagApp.of(applicationContext)
        val dao = app.db.tracks()
        val repo = app.lyricsRepository
        // Snapshot once: tracks missing every lyric source.
        val missing = dao.allTracks()
            .filter { !it.ignored && !it.hasEmbeddedLyrics && !it.hasExternalLyrics && !it.hasCachedLyrics }
        val total = missing.size
        if (total == 0) return@withContext Result.success()
        var done = 0
        var found = 0
        for (t in missing) {
            if (isStopped) return@withContext Result.success(
                Data.Builder().putInt(KEY_DONE, done).putInt(KEY_TOTAL, total).build()
            )
            try {
                val text = repo.fetchForPoweramp(
                    t.title.orEmpty(), t.artist.orEmpty(), t.album.orEmpty(),
                    (t.durationMs ?: 0).toInt(),
                )
                if (text != null) found++
            } catch (_: Exception) {
                // Per-track failure: skip, keep going.
            }
            done++
            if (done % 5 == 0 || done == total) {
                setProgress(
                    Data.Builder().putInt(KEY_DONE, done).putInt(KEY_TOTAL, total)
                        .putInt(KEY_FOUND, found).build()
                )
            }
        }
        Result.success(
            Data.Builder().putInt(KEY_DONE, done).putInt(KEY_TOTAL, total)
                .putInt(KEY_FOUND, found).build()
        )
    }

    companion object {
        const val UNIQUE = "lyrics_batch"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_FOUND = "found"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LyricsBatchWorker>().build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
