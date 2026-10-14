package com.verso.music.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
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
            // kotlinx.serialization (not org.json): the latter is an Android
            // stub that throws in local unit tests.
            val root = com.verso.music.lyrics.ProviderHttp.get(
                client, url,
                "Verso/0.4.0 (https://github.com/sahilwasgood/verso)",
            ) ?: return@withContext emptyList()
            val arr = root["recordings"]?.jsonArray ?: return@withContext emptyList()
            val out = mutableListOf<Candidate>()
            for (el in arr) {
                val r = el.jsonObject
                val artists = r["artist-credit"]?.jsonArray
                val artistName = artists
                    ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                    ?.joinToString(", ")?.ifEmpty { null }
                val rel = r["releases"]?.jsonArray?.firstOrNull()?.jsonObject
                out += Candidate(
                    title = r["title"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null },
                    artist = artistName,
                    album = rel?.get("title")?.jsonPrimitive?.contentOrNull?.ifEmpty { null },
                    year = rel?.get("date")?.jsonPrimitive?.contentOrNull?.take(4)?.ifEmpty { null },
                    score = r["score"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun throttle() {
        val wait = 1100 - (System.currentTimeMillis() - lastCallMs)
        if (wait > 0) delay(wait)
        lastCallMs = System.currentTimeMillis()
    }

    /**
     * Query with primary-artist + filename fallback (shared by the detail
     * screen and the fix-all batch): multi-artist tags and blank YTDL tags
     * match nothing on MusicBrainz otherwise. iTunes (fuzzy) backs
     * MusicBrainz (strict); best score wins per source ordering.
     */
    suspend fun suggestFor(
        title: String?, artist: String?, album: String?, fileName: String,
    ): List<Candidate> {
        val parsed = if (title.isNullOrBlank() || artist.isNullOrBlank()) {
            com.verso.music.core.FilenameParser.parse(fileName)
        } else null
        val qTitle = title?.takeIf { it.isNotBlank() } ?: parsed?.title.orEmpty()
        val qArtist = com.verso.music.core.TrackNormalizer.primaryArtist(artist)
            .ifEmpty { parsed?.artist.orEmpty() }
        val mb = searchRecording(qTitle, qArtist, album.orEmpty())
        if (mb.any { it.score >= 90 }) return mb
        val itunes = ItunesMetadata.search(qTitle, qArtist)
        return (mb + itunes).sortedByDescending { it.score }
    }

    private fun String.enc(): String = URLEncoder.encode(this, "UTF-8")
}
