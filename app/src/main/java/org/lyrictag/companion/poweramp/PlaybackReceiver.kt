package org.lyrictag.companion.poweramp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import org.lyrictag.companion.LyricTagApp
import org.lyrictag.companion.scrobble.ScrobbleManager

/**
 * Listens to Poweramp's explicit playback intents for scrobbling.
 *
 * Uses only documented actions (see PowerampAPI):
 * - TRACK_CHANGED_EXPLICIT: background-safe, carries `track` Bundle + `ts`.
 * - STATUS_CHANGED_EXPLICIT: background-safe, carries `paused` + `pos`.
 *
 * No-ops entirely when scrobbling is disabled in Settings.
 */
class PlaybackReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PlaybackReceiver"
        const val ACTION_TRACK_CHANGED_EXPLICIT = "com.maxmpz.audioplayer.TRACK_CHANGED_EXPLICIT"
        const val ACTION_STATUS_CHANGED_EXPLICIT = "com.maxmpz.audioplayer.STATUS_CHANGED_EXPLICIT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "playback intent: ${intent.action}")
        val appContext = context.applicationContext
        if (!ScrobbleManager.isEnabled(appContext)) {
            Log.i(TAG, "ignored: scrobbling disabled")
            return
        }
        try {
            val app = LyricTagApp.of(appContext)
            when (intent.action) {
                ACTION_TRACK_CHANGED_EXPLICIT -> {
                    val track: Bundle = intent.getBundleExtra("track") ?: return
                    val title = track.getString(PowerampContract.TRACK_TITLE).orEmpty()
                    if (title.isBlank()) return
                    // NOTE: Poweramp's ts is System.currentTimeMillis() (MILLIS);
                    // treating it as seconds underflows played time and no
                    // track ever qualifies (verified on-device).
                    val startedMs = intent.getLongExtra(
                        "ts", System.currentTimeMillis()
                    )
                    ScrobbleManager.onTrackStarted(
                        appContext, app.db,
                        ScrobbleManager.NowPlaying(
                            title = title,
                            artist = track.getString(PowerampContract.TRACK_ARTIST).orEmpty(),
                            album = track.getString(PowerampContract.TRACK_ALBUM).orEmpty(),
                            durationMs = track.getInt(PowerampContract.TRACK_DURATION_MS, 0).toLong(),
                            startedAtSec = startedMs / 1000,
                        ),
                    )
                }
                ACTION_STATUS_CHANGED_EXPLICIT -> {
                    if (intent.getBooleanExtra("paused", true)) {
                        ScrobbleManager.onPaused(
                            appContext, app.db,
                            intent.getIntExtra("pos", 0).toLong(),
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "playback intent failed", t)
        }
    }
}
