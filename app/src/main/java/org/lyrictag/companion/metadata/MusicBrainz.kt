package org.lyrictag.companion.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * MusicBrainz recording lookup (original implementation).
 * Public, keyless JSON API (musicbrainz.org/ws/2/). Honors their rate
 * policy: one request per second + descriptive User-Agent.
 */
object MusicBrainz {

    data class Candidate(
        val title: String?,
        val artist: String?,
        val album: String?,
        val year: String?,
        val score: Int, // MusicBrainz's own 0-100 match score
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var lastCallMs = 0L

    suspend fun searchRecording(
        title: String, artist: String, album: String,
    ): List<Candidate> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext emptyList()
        try {
            throttle()
            val q = buildString {
                append("recording:\"${title}\"".enc())
                if (artist.isNotBlank()) append(" AND artist:\"${artist}\"".enc())
                if (album.isNotBlank()) append(" AND release:\"${album}\"".enc())
            }
            val url = "https://musicbrainz.org/ws/2/recording/?query=$q&fmt=json&limit=5"
            val req = Request.Builder().url(url)
                .header("User-Agent", "LyricTag/0.1.0 (https://github.com/lyrictag/lyrictag)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val root = runCatching {
                    JSONObject(resp.body?.string().orEmpty())
                }.getOrNull() ?: return@withContext emptyList()
                val arr = root.optJSONArray("recordings") ?: return@withContext emptyList()
                val out = mutableListOf<Candidate>()
                for (i in 0 until arr.length()) {
                    val r = arr.optJSONObject(i) ?: continue
                    val artists = r.optJSONArray("artist-credit")
                    val artistName = (0 until (artists?.length() ?: 0))
                        .mapNotNull { artists?.optJSONObject(it)?.optString("name") }
                        .joinToString(", ").ifEmpty { null }
                    val releases = r.optJSONArray("releases")
                    val rel = releases?.optJSONObject(0)
                    out += Candidate(
                        title = r.optString("title").ifEmpty { null },
                        artist = artistName,
                        album = rel?.optString("title")?.ifEmpty { null },
                        year = rel?.optString("date")?.take(4)?.ifEmpty { null },
                        score = r.optInt("score", 0),
                    )
                }
                out
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun throttle() {
        val wait = 1100 - (System.currentTimeMillis() - lastCallMs)
        if (wait > 0) delay(wait)
        lastCallMs = System.currentTimeMillis()
    }

    private fun String.enc(): String = URLEncoder.encode(this, "UTF-8")
}
