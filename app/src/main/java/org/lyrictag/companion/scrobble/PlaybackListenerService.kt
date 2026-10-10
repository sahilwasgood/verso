package org.lyrictag.companion.scrobble

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import org.lyrictag.companion.LyricTagApp

/**
 * Player-agnostic playback tracking for scrobbling.
 *
 * Some Poweramp builds don't emit track broadcasts to third parties
 * (verified: explicit TRACK_CHANGED never arrives on build-1025 despite
 * a registered receiver). This listener follows Poweramp's MediaSession
 * instead and feeds the same [ScrobbleManager] — the shared
 * take-and-evaluate structure means both sources can coexist without
 * double scrobbles (only one `current` exists at a time).
 *
 * Needs "Notification access" (granted in system Settings by the user).
 */
class PlaybackListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "PlaybackListener"
        const val POWERAMP = "com.maxmpz.audioplayer"
    }

    private val controllers = mutableMapOf<String, MediaController>()
    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val m = metadata ?: return
            val title = m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            if (title.isBlank()) return
            val app = LyricTagApp.of(this@PlaybackListenerService)
            if (!ScrobbleManager.isEnabled(app)) return
            ScrobbleManager.onTrackStarted(
                app, app.db,
                ScrobbleManager.NowPlaying(
                    title = title,
                    artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                    album = m.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                    durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    startedAtSec = System.currentTimeMillis() / 1000,
                ),
            )
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            state ?: return
            if (state.state != PlaybackState.STATE_PAUSED &&
                state.state != PlaybackState.STATE_STOPPED
            ) return
            val app = LyricTagApp.of(this@PlaybackListenerService)
            if (!ScrobbleManager.isEnabled(app)) return
            ScrobbleManager.onPaused(app, app.db, state.position / 1000)
        }
    }

    override fun onListenerConnected() {
        refreshSessions()
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        // Poweramp reposts its notification on track change — cheap hook
        // to pick up session (re)creation without polling.
        if (sbn?.packageName == POWERAMP) refreshSessions()
    }

    private fun refreshSessions() {
        try {
            val mm = getSystemService(MediaSessionManager::class.java) ?: return
            val active = mm.getActiveSessions(ComponentName(this, javaClass))
            val seen = mutableSetOf<String>()
            for (c in active) {
                if (c.packageName != POWERAMP) continue
                val key = c.sessionToken.toString()
                seen += key
                if (!controllers.containsKey(key)) {
                    c.registerCallback(cb)
                    controllers[key] = c
                    // Seed current state so a track already playing counts.
                    try {
                        c.metadata?.let { cb.onMetadataChanged(it) }
                    } catch (_: Throwable) {
                    }
                }
            }
            // Drop dead controllers.
            controllers.keys.minus(seen).forEach { k ->
                try {
                    controllers[k]?.unregisterCallback(cb)
                } catch (_: Throwable) {
                }
                controllers.remove(k)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "session refresh failed", t)
        }
    }

    override fun onDestroy() {
        controllers.values.forEach {
            try {
                it.unregisterCallback(cb)
            } catch (_: Throwable) {
            }
        }
        controllers.clear()
        super.onDestroy()
    }
}
