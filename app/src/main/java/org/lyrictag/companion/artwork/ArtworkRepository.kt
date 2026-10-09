package org.lyrictag.companion.artwork

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lyrictag.companion.core.TrackNormalizer
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Album art from free keyless APIs: iTunes Search first (600px),
 * Deezer search as fallback (500px). Fully keyless; no auth, no quotas
 * that matter for a 39-track library. Original code.
 */
object ArtworkProviders {

    data class Art(val url: String, val source: String)

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun get(client: OkHttpClient, url: String): kotlinx.serialization.json.JsonObject? {
        val req = Request.Builder().url(url)
            .header("User-Agent", "LyricTag/0.1.0 (https://github.com/lyrictag/lyrictag)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return runCatching { json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject }
                .getOrNull()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** iTunes Search API: term=artist+title, entity=song. */
    fun itunes(client: OkHttpClient, title: String, artist: String): Art? {
        return try {
            val term = enc(listOf(artist, title).filter { it.isNotBlank() }.joinToString(" "))
            if (term.isEmpty()) return null
            val root = get(
                client,
                "https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=5"
            ) ?: return null
            val results = root["results"]?.jsonArray ?: return null
            val nt = TrackNormalizer.normalize(title)
            for (el in results) {
                val o = el.jsonObject
                val art = o["artworkUrl100"]?.jsonPrimitive?.contentOrNull ?: continue
                if (nt.isNotEmpty()) {
                    val rt = TrackNormalizer.normalize(
                        o["trackName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    )
                    if (rt.isEmpty()) continue
                    val agrees = rt == nt ||
                        (minOf(nt.length, rt.length) >= 5 &&
                            (rt.contains(nt) || nt.contains(rt)))
                    if (!agrees) continue
                }
                // 100px preview → 600px full art.
                return Art(art.replace("100x100bb.jpg", "600x600bb.jpg"), "iTunes")
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Deezer search API (no key): cover_big fallback. */
    fun deezer(client: OkHttpClient, title: String, artist: String): Art? {
        return try {
            val q = buildString {
                if (artist.isNotBlank()) append("artist:\"${enc(artist)}\" ")
                append("track:\"${enc(title)}\"")
            }
            val root = get(client, "https://api.deezer.com/search?q=$q") ?: return null
            val data = root["data"]?.jsonArray ?: return null
            val first = data.firstOrNull()?.jsonObject ?: return null
            val cover = first["cover_big"]?.jsonPrimitive?.contentOrNull
                ?: first["cover_medium"]?.jsonPrimitive?.contentOrNull
                ?: return null
            Art(cover, "Deezer")
        } catch (_: Exception) {
            null
        }
    }
}

/** Cached artwork URLs (bytes live in Coil's disk cache). */
class ArtworkRepository(
    private val cache: org.lyrictag.companion.db.ArtCache,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** True when a URL is already cached (cheap Room read, no network). */
    suspend fun hasArt(title: String, artist: String): Boolean = withContext(Dispatchers.IO) {
        cache.get(title, artist) != null
    }

    /** Cached URL or fresh lookup (primary-artist query, iTunes → Deezer). */
    suspend fun artUrl(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        cache.get(title, artist)?.let { return@withContext it }
        if (title.isBlank()) return@withContext null
        val qArtist = TrackNormalizer.primaryArtist(artist).ifEmpty { artist }
        val art = ArtworkProviders.itunes(client, title, qArtist)
            ?: ArtworkProviders.deezer(client, title, qArtist)
            ?: return@withContext null
        cache.put(title, artist, art.url)
        art.url
    }
}
