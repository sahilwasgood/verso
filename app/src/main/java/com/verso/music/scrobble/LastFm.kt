package com.verso.music.scrobble

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.TreeMap
import java.util.concurrent.TimeUnit

/**
 * Last.fm scrobbling with user-supplied credentials (original implementation).
 * Browser auth: auth.getToken -> user approves at last.fm/api/auth ->
 * auth.getSession -> session key -> track.scrobble / track.updateNowPlaying.
 * All calls MD5-signed per Last.fm docs.
 * Get a free key at last.fm/api/account/create — nothing is bundled.
 * (kotlinx.serialization, not org.json: the latter is an Android stub that
 * throws in local unit tests.)
 */
object LastFm {

    private const val TAG = "LastFm"
    private const val BASE = "https://ws.audioscrobbler.com/2.0/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Step 1 of browser auth: a one-time token. The user opens
     * https://www.last.fm/api/auth/?api_key=KEY&token=TOKEN, approves,
     * then step 2 exchanges it for a session key. (Password-based
     * mobile auth is rejected for new API keys — verified 2026-10.)
     */
    suspend fun requestToken(apiKey: String, secret: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val params = TreeMap<String, String>().apply {
                    put("method", "auth.getToken")
                    put("api_key", apiKey)
                }
                val root = post(params, secret) ?: return@withContext null
                root["token"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null }
            } catch (_: Exception) {
                null
            }
        }

    /** Step 2: exchange the approved token for a session key + username. */
    suspend fun fetchSession(
        apiKey: String, secret: String, token: String,
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val params = TreeMap<String, String>().apply {
                put("method", "auth.getSession")
                put("api_key", apiKey)
                put("token", token)
            }
            val root = post(params, secret) ?: return@withContext null
            val session = root["session"]?.jsonObject ?: return@withContext null
            val key = session["key"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null }
                ?: return@withContext null
            val user = session["name"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null }
                ?: return@withContext null
            key to user
        } catch (_: Exception) {
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
            !root.containsKey("error")
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

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun post(params: TreeMap<String, String>, secret: String): JsonObject? {
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
            .header("User-Agent", "Verso/0.4.0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "post ${params["method"]} failed: ${resp.code}")
                return null
            }
            return runCatching {
                json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            }.getOrNull()
        }
    }
}
