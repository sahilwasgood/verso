package org.lyrictag.companion.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.lyrictag.companion.core.LrcParser
import org.lyrictag.companion.core.TrackNormalizer
import org.lyrictag.companion.core.TtmlConverter
import java.text.Normalizer
import java.util.Locale

/**
 * LunaBeat TTML hub (original implementation).
 * Flow: manifest revision check -> songs.json index -> exact normalized
 * title+artist match -> fetch static TTML -> convert to LRC.
 * Fully keyless static hosting; verified 2026-10.
 */
class LunaBeatProvider(
    private val client: OkHttpClient,
) : LyricsProvider {

    @Volatile private var index: List<Song> = emptyList()
    @Volatile private var revision: String? = null

    private data class Song(val title: String, val artists: List<String>, val path: String)

    override suspend fun fetch(
        title: String, artist: String, album: String, durationMs: Int,
    ): List<LyricsResult> = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) return@withContext emptyList()
        try {
            ensureIndex() ?: return@withContext emptyList()
            val nt = crush(title)
            val na = crush(artist)
            val match = index.firstOrNull { s ->
                crush(s.title) == nt && s.artists.any { crush(it) == na }
            } ?: return@withContext emptyList()
            val req = Request.Builder().url("$BASE${match.path}")
                .header("User-Agent", LyricsRepository.USER_AGENT).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val ttml = resp.body?.string()?.takeIf { it.isNotBlank() }
                    ?: return@withContext emptyList()
                val lines = TtmlConverter.ttmlToLrcLines(ttml)
                if (lines.isEmpty()) return@withContext emptyList()
                listOf(
                    LyricsResult(
                        plain = null, synced = LrcParser.serialize(lines),
                        title = match.title,
                        artist = match.artists.firstOrNull()?.ifEmpty { null },
                        album = album.ifBlank { null },
                        durationSec = if (durationMs > 0) durationMs / 1000L else null,
                        source = "LunaBeat", score = 0,
                    )
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun ensureIndex(): List<Song>? {
        val man = ProviderHttp.get(client, "$BASE/api/v1/manifest.json", LyricsRepository.USER_AGENT)
            ?: return index.ifEmpty { null }
        val rev = man.optString("revision").ifEmpty { return index.ifEmpty { null } }
        if (rev == revision && index.isNotEmpty()) return index
        val idx = man.optString("index", "songs.json")
        val req = Request.Builder().url("$BASE/api/v1/$idx")
            .header("User-Agent", LyricsRepository.USER_AGENT).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return index.ifEmpty { null }
            val songs: JSONArray = runCatching {
                org.json.JSONObject(resp.body?.string().orEmpty()).optJSONArray("songs")
            }.getOrNull() ?: return index.ifEmpty { null }
            val list = mutableListOf<Song>()
            for (i in 0 until songs.length()) {
                val o = songs.optJSONObject(i) ?: continue
                val t = o.optString("title")
                if (t.isEmpty()) continue
                val p = o.optString("path")
                if (p.isEmpty()) continue
                val arts = mutableListOf<String>()
                val arr = o.optJSONArray("artists")
                if (arr != null) for (a in 0 until arr.length()) arts += arr.optString(a)
                list += Song(t, arts, p)
            }
            index = list
            revision = rev
            return list.ifEmpty { null }
        }
    }

    private fun crush(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\s·・.\\-_]"), "")

    companion object {
        private const val BASE = "https://2755337087.github.io/ttml-hub/"
    }
}
