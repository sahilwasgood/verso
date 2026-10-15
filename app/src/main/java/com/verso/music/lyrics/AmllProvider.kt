package com.verso.music.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import com.verso.music.core.LrcParser
import com.verso.music.core.TtmlConverter

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
            var root = ProviderHttp.get(client, url, LyricsRepository.USER_AGENT)
            var items = root?.get("data")?.jsonObject?.get("items")?.jsonArray
            if ((items == null || items.isEmpty()) && album.isNotBlank()) {
                // Local album tags are often wrong (e.g. filename dumps) -
                // retry unfiltered rather than missing a good match.
                root = ProviderHttp.get(
                    client,
                    "https://api.amll.dev/v1/lyrics/search" +
                        "?musicName=${ProviderHttp.enc(title)}&artistName=${ProviderHttp.enc(artist)}",
                    LyricsRepository.USER_AGENT,
                )
                items = root?.get("data")?.jsonObject?.get("items")?.jsonArray
            }
            if (items == null || items.isEmpty()) return@withContext emptyList()
            val best = items.firstOrNull()?.jsonObject ?: return@withContext emptyList()
            val id = best["id"]?.jsonPrimitive?.longOrNull ?: -1L
            if (id < 0) return@withContext emptyList()
            val got = ProviderHttp.get(
                client, "https://api.amll.dev/v1/lyrics/get?id=$id&format=ttml",
                LyricsRepository.USER_AGENT,
            ) ?: return@withContext emptyList()
            val ttml = got["data"]?.jsonObject?.get("lyrics")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
            val lines = TtmlConverter.ttmlToLrcLines(ttml)
            if (lines.isEmpty()) return@withContext emptyList()
            val lrc = LrcParser.serialize(lines)
            val names = best["musicNames"]?.jsonArray
            val artists = best["artistNames"]?.jsonArray
            val albums = best["albumNames"]?.jsonArray
            listOf(
                LyricsResult(
                    plain = null, synced = lrc,
                    title = names?.firstOrNull()?.jsonPrimitive?.contentOrNull?.ifEmpty { null },
                    artist = artists?.firstOrNull()?.jsonPrimitive?.contentOrNull?.ifEmpty { null },
                    album = albums?.firstOrNull()?.jsonPrimitive?.contentOrNull?.ifEmpty { null },
                    durationSec = if (durationMs > 0) durationMs / 1000L else null,
                    source = "AMLL", score = 0,
                )
            )
        } catch (_: Exception) {
            emptyList()
        }
    }
}
