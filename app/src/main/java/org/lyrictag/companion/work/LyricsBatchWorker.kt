package org.lyrictag.companion.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.lyrictag.companion.LyricTagApp
import org.lyrictag.companion.lyrics.FetchCoordinator
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fetches lyrics for every track missing them. User-triggered from the
 * dashboard; cancellable; survives app backgrounding via WorkManager.
 *
 * Tracks resolve several at a time (bounded parallelism); when Poweramp
 * asks for the now-playing song mid-run, the batch yields so that fetch
 * goes first, then resumes automatically. Progress (done/total) is
 * published for the UI; per-track failures are skipped silently.
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
        val done = AtomicInteger(0)
        val found = AtomicInteger(0)
        val slots = Semaphore(PARALLEL_TRACKS)
        suspend fun publish() {
            setProgress(
                Data.Builder().putInt(KEY_DONE, done.get()).putInt(KEY_TOTAL, total)
                    .putInt(KEY_FOUND, found.get()).build()
            )
        }
        try {
            missing.map { t ->
                async {
                    slots.withPermit {
                        if (isStopped) return@async
                        // A live Poweramp request outranks batch throughput.
                        FetchCoordinator.yieldWhilePriority()
                        if (isStopped) return@async
                        try {
                            val text = repo.fetchForPoweramp(
                                t.title.orEmpty(), t.artist.orEmpty(), t.album.orEmpty(),
                                (t.durationMs ?: 0).toInt(),
                            )
                            if (text != null) found.incrementAndGet()
                        } catch (_: Exception) {
                            // Per-track failure: skip, keep going.
                        }
                        if (done.incrementAndGet() % 2 == 0) publish()
                    }
                }
            }.awaitAll()
        } catch (_: Exception) {
            // Cancellation lands here; partial progress is kept.
        }
        publish()
        Result.success(
            Data.Builder().putInt(KEY_DONE, done.get()).putInt(KEY_TOTAL, total)
                .putInt(KEY_FOUND, found.get()).build()
        )
    }

    companion object {
        const val UNIQUE = "lyrics_batch"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_FOUND = "found"
        /** Simultaneous tracks: fast without hammering free APIs. */
        const val PARALLEL_TRACKS = 4

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE, ExistingWorkPolicy.APPEND,
                OneTimeWorkRequestBuilder<LyricsBatchWorker>().build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
