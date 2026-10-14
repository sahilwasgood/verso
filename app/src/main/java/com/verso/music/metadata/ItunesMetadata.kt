package com.verso.music.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import com.verso.music.core.TrackNormalizer
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * iTunes Search as a metadata source (free, keyless): forgiving fuzzy
 * matching where MusicBrainz's strict query finds nothing (YTDL dumps).
 * Score is heuristic: exact title agreement 95, containment 90, else 70.
 * Fully keyless; verified working 2026-10.
 */
object ItunesMetadata {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        title: String, artist: String,
    ): List<MusicBrainz.Candidate> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext emptyList()
        try {
            val qArtist = TrackNormalizer.primaryArtist(artist).ifEmpty { artist }
            val term = URLEncoder.encode(
                listOf(qArtist, title).filter { it.isNotBlank() }.joinToString(" "), "UTF-8"
            )
            val root = com.verso.music.lyrics.ProviderHttp.get(
                client,
                "https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=5",
                "Verso/0.4.0 (https://github.com/sahilwasgood/verso)",
            ) ?: return@withContext emptyList()
            val results = root["results"]?.jsonArray ?: return@withContext emptyList()
            val nt = TrackNormalizer.normalize(title)
            val na = TrackNormalizer.normalize(qArtist)
            val out = mutableListOf<MusicBrainz.Candidate>()
            for (el in results) {
                val o = el.jsonObject
                val rt = o["trackName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val ra = o["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val nrt = TrackNormalizer.normalize(rt)
                if (nrt.isEmpty()) continue
                // Containment needs enough length both sides, or 2-letter
                // titles ("Up", "YAD") would match anything at score 90
                // and get auto-applied by the fix-all batch.
                val longEnough = minOf(nt.length, nrt.length) >= 5
                val score = when {
                    nrt == nt -> 95
                    longEnough && (nt.contains(nrt) || nrt.contains(nt)) -> 90
                    else -> 70
                }
                // Artist must agree when we know one.
                if (na.isNotEmpty()) {
                    val rna = TrackNormalizer.normalize(ra)
                    if (rna.isNotEmpty() && rna != na &&
                        !rna.contains(na) && !na.contains(rna)
                    ) continue
                }
                out += MusicBrainz.Candidate(
                    title = rt.ifEmpty { null },
                    artist = ra.ifEmpty { null },
                    album = o["collectionName"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null },
                    year = o["releaseDate"]?.jsonPrimitive?.contentOrNull?.take(4)?.ifEmpty { null },
                    score = score,
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}
