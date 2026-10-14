package com.verso.music.scrobble

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ListenBrainz submitting (original implementation).
 * Endpoint facts: POST https://api.listenbrainz.org/1/submit-listens with
 * `Authorization: Token <user-token>`, body {listen_type, payload[{listened_at,
 * track_metadata{artist_name, track_name, release_name?, additional_info}}]}.
 * Token validation: GET /1/validate-token. No bundled secrets — the user
 * pastes their own token from listenbrainz.org/settings.
 */
object ListenBrainz {

    private const val TAG = "ListenBrainz"
    private const val BASE = "https://api.listenbrainz.org/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun validate(token: String): Boolean {
        return try {
            val req = Request.Builder().url(BASE + "1/validate-token")
                .header("Authorization", "Token $token").build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** Returns true when the server accepted the listen. */
    fun submit(
        token: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        listenedAtSec: Long,
    ): Boolean {
        return try {
            val meta = JSONObject()
                .put("artist_name", artist.ifBlank { "Unknown Artist" })
                .put("track_name", title.ifBlank { "Unknown Title" })
            if (!album.isNullOrBlank()) meta.put("release_name", album)
            val extra = JSONObject()
                .put("media_player", "Verso")
                .put("submission_client", "Verso")
            if (durationMs != null && durationMs > 0) {
                extra.put("duration_ms", durationMs)
            }
            meta.put("additional_info", extra)
            val body = JSONObject()
                .put("listen_type", "single")
                .put("payload", org.json.JSONArray().put(
                    JSONObject()
                        .put("listened_at", listenedAtSec)
                        .put("track_metadata", meta),
                ))
                .toString()
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(BASE + "1/submit-listens")
                .post(body)
                .header("Authorization", "Token $token")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "submit failed: ${resp.code}")
                    return false
                }
                true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "submit error", t)
            false
        }
    }
}
