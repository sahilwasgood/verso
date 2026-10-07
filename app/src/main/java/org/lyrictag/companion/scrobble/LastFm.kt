package org.lyrictag.companion.scrobble

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.TreeMap
import java.util.concurrent.TimeUnit

/**
 * Last.fm scrobbling with user-supplied credentials (original implementation).
 * Standard public flow: auth.getMobileSession(username, password, api_key) ->
 * session key (password is then discarded, never stored) -> track.scrobble /
 * track.updateNowPlaying. All calls MD5-signed per Last.fm docs.
 * Get a free key at last.fm/api/account/create — nothing is bundled.
 */
object LastFm {

    private const val TAG = "LastFm"
    private const val BASE = "https://ws.audioscrobbler.com/2.0/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Exchanges username+password for a session key. The password is never stored. */
    suspend fun mobileSession(
        apiKey: String, secret: String, username: String, password: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val params = TreeMap<String, String>().apply {
                put("method", "auth.getMobileSession")
                put("api_key", apiKey)
                put("username", username)
                put("password", password)
            }
            val root = post(params, secret) ?: return@withContext null
            root.optJSONObject("session")?.optString("key")?.ifEmpty { null }
        } catch (t: Throwable) {
            Log.w(TAG, "mobileSession failed", t)
            null
        }
    }

    suspend fun scrobble(
        apiKey: String, secret: String, sessionKey: String,
        title: String, artist: String, album: String?, timestampSec: Long, durationSec: Long?,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val params = TreeMap<String, String>().apply {
                put("method", "track.scrobble")
                put("api_key", apiKey)
                put("sk", sessionKey)
                put("artist", artist.ifBlank { "Unknown Artist" })
                put("track", title.ifBlank { "Unknown Title" })
                put("timestamp", timestampSec.toString())
                if (!album.isNullOrBlank()) put("album", album)
                if (durationSec != null && durationSec > 0) put("duration", durationSec.toString())
            }
            val root = post(params, secret) ?: return@withContext false
            // Success = <lfm status="ok">; failures carry <error>.
            !root.has("error")
        } catch (t: Throwable) {
            Log.w(TAG, "scrobble failed", t)
            false
        }
    }

    suspend fun nowPlaying(
        apiKey: String, secret: String, sessionKey: String,
        title: String, artist: String, album: String?, durationSec: Long?,
    ) = withContext(Dispatchers.IO) {
        try {
            val params = TreeMap<String, String>().apply {
                put("method", "track.updateNowPlaying")
                put("api_key", apiKey)
                put("sk", sessionKey)
                put("artist", artist.ifBlank { "Unknown Artist" })
                put("track", title.ifBlank { "Unknown Title" })
                if (!album.isNullOrBlank()) put("album", album)
                if (durationSec != null && durationSec > 0) put("duration", durationSec.toString())
            }
            post(params, secret)
        } catch (t: Throwable) {
            Log.w(TAG, "nowPlaying failed", t)
            null
        }
    }

    private fun post(params: TreeMap<String, String>, secret: String): JSONObject? {
        // MD5(api_sig): md5 of sorted key+value pairs concatenated + secret.
        val sigSrc = buildString {
            for ((k, v) in params) append(k).append(v)
            append(secret)
        }
        val sig = MessageDigest.getInstance("MD5")
            .digest(sigSrc.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val form = buildString {
            for ((k, v) in params) append(k).append('=').append(URLEncoder.encode(v, "UTF-8")).append('&')
            append("api_sig=").append(sig).append("&format=json")
        }
        val req = Request.Builder().url(BASE)
            .post(form.toByteArray().let {
                okhttp3.RequestBody.create("application/x-www-form-urlencoded".toMediaType(), it)
            })
            .header("User-Agent", "LyricTag/0.1.0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "post ${params["method"]} failed: ${resp.code}")
                return null
            }
            return runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull()
        }
    }
}
