package org.lyrictag.companion.scrobble

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.lyrictag.companion.db.AppDatabase
import org.lyrictag.companion.db.ScrobbleEntity

/**
 * Applies standard scrobble rules and fans out to enabled services.
 *
 * A track scrobbles when playback of it ends (next track / pause / stop)
 * after playing at least 30s AND (half its duration OR 4 minutes).
 * Queued offline with original timestamps; flushed on next playback event
 * or app start. All credentials are user-supplied (Settings) — nothing bundled.
 */
object ScrobbleManager {

    private const val TAG = "ScrobbleManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class NowPlaying(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val startedAtSec: Long,
    )

    @Volatile private var current: NowPlaying? = null

    // The in-memory `current` dies with the process (receivers wake us per
    // event), so mirror it in prefs: a track that played minutes ago still
    // scrobbles when the *next* event arrives in a fresh process.
    private fun persistCurrent(p: SharedPreferences, np: NowPlaying?) {
        p.edit()
            .putString("np_title", np?.title)
            .putString("np_artist", np?.artist)
            .putString("np_album", np?.album)
            .putLong("np_dur", np?.durationMs ?: -1L)
            .putLong("np_started", np?.startedAtSec ?: -1L)
            .apply()
    }

    private fun readPersisted(p: SharedPreferences): NowPlaying? {
        val started = p.getLong("np_started", -1L)
        val title = p.getString("np_title", null)
        if (started < 0 || title.isNullOrEmpty()) return null
        return NowPlaying(
            title = title,
            artist = p.getString("np_artist", null).orEmpty(),
            album = p.getString("np_album", null).orEmpty(),
            durationMs = p.getLong("np_dur", -1L).takeIf { it >= 0 } ?: 0L,
            startedAtSec = started,
        )
    }

    fun prefs(context: Context) =
        context.getSharedPreferences("lyrictag", Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean("scrobble_enabled", false)

    fun pendingCount(db: AppDatabase) = db.scrobbles().observePending()

    /** Called when a new track starts (or resumes) in Poweramp. */
    fun onTrackStarted(context: Context, db: AppDatabase, np: NowPlaying) {
        // The previous track just ended — evaluate it first (falling back
        // to the persisted one when this process is fresh).
        val prev = current ?: readPersisted(prefs(context))
        current = np
        persistCurrent(prefs(context), np)
        prev?.let { maybeScrobble(context, db, it, playedSec = nowSec() - it.startedAtSec) }
        // Now-playing updates (fire and forget).
        scope.launch {
            try {
                val p = prefs(context)
                p.getString("lf_session", null)?.let { sk ->
                    LastFm.nowPlaying(
                        p.getString("lf_key", "").orEmpty(),
                        p.getString("lf_secret", "").orEmpty(), sk,
                        np.title, np.artist, np.album.ifBlank { null },
                        np.durationMs.takeIf { it > 0 }?.div(1000),
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "nowPlaying failed", t)
            }
            flushQueue(context.applicationContext, db)
        }
    }

    /** Called on pause/stop with the player's reported position (seconds). */
    fun onPaused(context: Context, db: AppDatabase, positionSec: Long) {
        val prev = current ?: readPersisted(prefs(context))
        current = null
        persistCurrent(prefs(context), null)
        prev?.let { maybeScrobble(context, db, it, playedSec = positionSec) }
    }

    private fun maybeScrobble(
        context: Context, db: AppDatabase, np: NowPlaying, playedSec: Long,
    ) {
        if (!isEnabled(context)) return
        if (np.title.isBlank()) {
            Log.i(TAG, "skip: blank title")
            return
        }
        val durSec = np.durationMs / 1000
        val qualifies = playedSec >= 30 && (playedSec >= 240 || (durSec > 0 && playedSec * 2 >= durSec))
        Log.i(TAG, "evaluate ${np.title}: played=${playedSec}s dur=${durSec}s qualifies=$qualifies")
        if (!qualifies) return
        scope.launch {
            try {
                // IGNORE + unique index make the identical listen insert
                // once even when broadcast and listener sources race.
                val rowId = db.scrobbles().enqueue(
                    ScrobbleEntity(
                        title = np.title, artist = np.artist,
                        album = np.album.ifBlank { null },
                        durationMs = np.durationMs.takeIf { it > 0 },
                        listenedAtSec = np.startedAtSec,
                    )
                )
                if (rowId != -1L) Log.i(TAG, "queued scrobble: ${np.title}")
            } catch (t: Throwable) {
                Log.w(TAG, "enqueue failed", t)
            }
            flushQueue(context.applicationContext, db)
        }
    }

    /** Sends queued listens to every configured service, oldest first. */
    fun flushQueue(context: Context, db: AppDatabase) {
        scope.launch {
            flushMutex.withLock {
                flushLocked(context, db)
            }
        }
    }

    private val flushMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun flushLocked(context: Context, db: AppDatabase) {
            val p = prefs(context)
            val lbToken = p.getString("lb_token", "").orEmpty()
            val lfSession = p.getString("lf_session", null)
            val lfKey = p.getString("lf_key", "").orEmpty()
            val lfSecret = p.getString("lf_secret", "").orEmpty()
            if (lbToken.isBlank() && lfSession.isNullOrBlank()) return
            try {
                for (e in db.scrobbles().oldest()) {
                    var ok = true
                    if (lbToken.isNotBlank()) {
                        ok = ListenBrainz.submit(
                            lbToken, e.title, e.artist, e.album,
                            e.durationMs, e.listenedAtSec,
                        ) && ok
                    }
                    if (!lfSession.isNullOrBlank() && lfKey.isNotBlank()) {
                        ok = LastFm.scrobble(
                            lfKey, lfSecret, lfSession,
                            e.title, e.artist, e.album, e.listenedAtSec,
                            e.durationMs?.div(1000),
                        ) && ok
                    }
                    // Drop only when at least one service accepted it; a total
                    // failure keeps it queued for the next flush (offline-safe).
                    if (ok) {
                        db.scrobbles().remove(e.id)
                        Log.i(TAG, "scrobbled: ${e.title}")
                    } else break
                }
            } catch (t: Throwable) {
                Log.w(TAG, "flush failed", t)
            }
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000
}
