package org.lyrictag.companion.poweramp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lyrictag.companion.ServiceLocator
import org.lyrictag.companion.lyrics.LyricsRepository

/**
 * Receives Poweramp ACTION_NEED_LYRICS and answers with ACTION_UPDATE_LYRICS.
 *
 * Exported (required — Poweramp sends an explicit intent here). Validates
 * realId/title before doing any work; heavy lifting happens on IO dispatcher.
 */
class LyricsRequestReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LyricsRequestReceiver"
    }

    // ponytail: process-global scope is fine — receiver callbacks are fire-and-forget
    // work items tracked by WorkManager/Repository, never by this scope.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PowerampContract.ACTION_NEED_LYRICS) return

        val realId = intent.getLongExtra(PowerampContract.TRACK_REAL_ID, PowerampContract.NO_ID)
        val title = intent.getStringExtra(PowerampContract.TRACK_TITLE)
        if (realId == PowerampContract.NO_ID || title.isNullOrBlank()) {
            Log.w(TAG, "Ignoring request: realId=$realId title=$title")
            return
        }
        val album = intent.getStringExtra(PowerampContract.TRACK_ALBUM).orEmpty()
        val artist = intent.getStringExtra(PowerampContract.TRACK_ARTIST).orEmpty()
        var durationMs = intent.getIntExtra(PowerampContract.TRACK_DURATION_MS, 0)
        val fileType = intent.getIntExtra(PowerampContract.TRACK_FILE_TYPE, -1)
        if (fileType == PowerampContract.FILE_TYPE_STREAM && durationMs <= 0) {
            // Streams carry no duration; skip network search, answer failure fast.
            sendResponse(context.applicationContext, realId, null)
            return
        }

        val appContext = context.applicationContext
        scope.launch {
            val app = org.lyrictag.companion.LyricTagApp.of(appContext)
            if (!app.autoFetchEnabled()) return@launch // user disabled auto-fetch
            val repo = app.lyricsRepository
            val lyrics = try {
                repo.fetchForPoweramp(title, artist, album, durationMs)
            } catch (t: Throwable) {
                Log.w(TAG, "fetch failed", t)
                null
            }
            sendResponse(appContext, realId, lyrics)
        }
    }

    private fun sendResponse(context: Context, realId: Long, lyrics: String?) {
        val out = Intent(PowerampContract.ACTION_UPDATE_LYRICS).apply {
            // Explicit component: implicit broadcasts don't reliably reach
            // manifest receivers on Android 8+ (mirrors PowerampAPIHelper.sendPAIntent).
            component = android.content.ComponentName(
                PowerampContract.POWERAMP_PACKAGE,
                PowerampContract.POWERAMP_API_RECEIVER,
            )
            putExtra(PowerampContract.EXTRA_PACKAGE, context.packageName)
            putExtra(PowerampContract.EXTRA_ID, realId)
            if (lyrics != null) putExtra(PowerampContract.EXTRA_LYRICS, lyrics)
            putExtra(PowerampContract.EXTRA_INFO_LINE, PowerampContract.INFO_LINE)
        }
        try {
            context.sendBroadcast(out)
        } catch (t: Throwable) {
            Log.w(TAG, "sendBroadcast failed", t)
        }
    }
}
