package org.lyrictag.companion.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.lyrictag.companion.core.LrcParser
import org.lyrictag.companion.core.TtmlConverter

/**
 * AMLL lyrics DB (original implementation).
 * Flow: /v1/lyrics/search -> best id -> /v1/lyrics/get?format=ttml -> TTML to LRC.
 * Fully keyless; verified working 2026-10.
 */
class AmllProvider(
    private val client: OkHttpClient,
) : LyricsProvider {

    override suspend fun fetch(
        title: String, artist: String, album: String, durationMs: Int,
    ): List<LyricsResult> = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) return@withContext emptyList()
        try {
            var url = "https://api.amll.dev/v1/lyrics/search" +
                "?musicName=${ProviderHttp.enc(title)}&artistName=${ProviderHttp.enc(artist)}"
            if (album.isNotBlank()) url += "&albumName=${ProviderHttp.enc(album)}"
            val root = ProviderHttp.get(client, url, LyricsRepository.USER_AGENT)
                ?: return@withContext emptyList()
            val items = root.optJSONObject("data")?.optJSONArray("items")
                ?: return@withContext emptyList()
            val best = items.optJSONObject(0) ?: return@withContext emptyList()
            val id = best.optLong("id", -1)
            if (id < 0) return@withContext emptyList()
            val got = ProviderHttp.get(
                client, "https://api.amll.dev/v1/lyrics/get?id=$id&format=ttml",
                LyricsRepository.USER_AGENT,
            ) ?: return@withContext emptyList()
            val ttml = got.optJSONObject("data")?.optString("lyrics")
                ?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
            val lines = TtmlConverter.ttmlToLrcLines(ttml)
            if (lines.isEmpty()) return@withContext emptyList()
            val lrc = LrcParser.serialize(lines)
            val names = best.optJSONArray("musicNames")
            val artists = best.optJSONArray("artistNames")
            val albums = best.optJSONArray("albumNames")
            listOf(
                LyricsResult(
                    plain = null, synced = lrc,
                    title = names?.optString(0)?.ifEmpty { null },
                    artist = artists?.optString(0)?.ifEmpty { null },
                    album = albums?.optString(0)?.ifEmpty { null },
                    durationSec = if (durationMs > 0) durationMs / 1000L else null,
                    source = "AMLL", score = 0,
                )
            )
        } catch (_: Exception) {
            emptyList()
        }
    }
}
