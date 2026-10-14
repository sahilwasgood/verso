package com.verso.music.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import com.verso.music.core.TrackNormalizer
import java.net.URLEncoder

/** Shared HTTP+JSON plumbing for the keyless providers. Original code. */
internal object ProviderHttp {
    private val json = Json { ignoreUnknownKeys = true }

    fun get(client: OkHttpClient, url: String, userAgent: String): kotlinx.serialization.json.JsonObject? {
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            return runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

private fun kotlinx.serialization.json.JsonObject.str(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

/**
 * KuGou lyrics (original implementation).
 * Flow: mobiles song search -> best hash -> lyrics search (id+accesskey)
 * -> download with fmt=lrc (base64 LRC, no KRC crypto needed).
 * Fully keyless; verified working 2026-10.
 */
class KugouProvider(
    private val client: OkHttpClient,
) : LyricsProvider {

    override suspend fun fetch(
        title: String, artist: String, album: String, durationMs: Int,
    ): List<LyricsResult> = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) return@withContext emptyList()
        try {
            val keyword = ProviderHttp.enc("$artist $title")
            val search = ProviderHttp.get(
                client,
                "https://mobiles.kugou.com/api/v3/search/song?version=10000&plat=0&correct=1&pagesize=10&keyword=$keyword",
                LyricsRepository.USER_AGENT,
            ) ?: return@withContext emptyList()
            val info = search["data"]?.jsonObject?.get("info")?.jsonArray
                ?: return@withContext emptyList()
            // Pick best: normalized title+artist agreement, duration proximity.
            var bestHash = ""
            var bestTitle = ""
            var bestArtist = ""
            var bestDur = 0L
            var bestScore = -1
            val nt = TrackNormalizer.normalize(title)
            val na = TrackNormalizer.normalize(artist)
            for (item in info) {
                val o = item.jsonObject
                val hash = o.str("hash")
                if (hash.isEmpty()) continue
                val st = TrackNormalizer.normalize(o.str("songname"))
                val sa = TrackNormalizer.normalize(o.str("singername"))
                if (st.isEmpty()) continue
                var score = 0
                if (st == nt) score += 50
                else if (st.contains(nt) || nt.contains(st)) score += 25
                else continue
                if (na.isNotEmpty() && sa == na) score += 30
                val dur = o["duration"]?.jsonPrimitive?.longOrNull
                    ?: o["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                if (dur > 0 && durationMs > 0 &&
                    kotlin.math.abs(dur * 1000 - durationMs) <= 5_000
                ) score += 12
                if (score > bestScore) {
                    bestScore = score; bestHash = hash
                    bestTitle = o.str("songname")
                    bestArtist = o.str("singername")
                    bestDur = dur
                }
            }
            if (bestHash.isEmpty() || bestScore < 30) return@withContext emptyList()
            // Resolve lyric file id + per-song accesskey.
            val cand = ProviderHttp.get(
                client,
                "https://lyrics.kugou.com/search?ver=1&man=yes&client=mobi&hash=${ProviderHttp.enc(bestHash)}",
                LyricsRepository.USER_AGENT,
            ) ?: return@withContext emptyList()
            val first = cand["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return@withContext emptyList()
            val id = first.str("id").ifEmpty { return@withContext emptyList() }
            val key = first.str("accesskey").ifEmpty { return@withContext emptyList() }
            val dl = ProviderHttp.get(
                client,
                "https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&charset=utf8&id=${ProviderHttp.enc(id)}&accesskey=${ProviderHttp.enc(key)}",
                LyricsRepository.USER_AGENT,
            ) ?: return@withContext emptyList()
            val b64 = dl.str("content").ifEmpty { return@withContext emptyList() }
            val lrc = String(java.util.Base64.getMimeDecoder().decode(b64), Charsets.UTF_8)
            if (!com.verso.music.core.LrcParser.looksLikeLrc(lrc)) return@withContext emptyList()
            listOf(
                LyricsResult(
                    plain = null, synced = lrc,
                    title = bestTitle.ifEmpty { null },
                    artist = bestArtist.ifEmpty { null },
                    album = album.ifBlank { null },
                    durationSec = bestDur.takeIf { it > 0 },
                    source = "KuGou", score = 0,
                )
            )
        } catch (_: Exception) {
            emptyList()
        }
    }
}
