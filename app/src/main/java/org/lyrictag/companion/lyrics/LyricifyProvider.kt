package org.lyrictag.companion.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.lyrictag.companion.core.LrcParser
import org.lyrictag.companion.core.LyricifyLines
import java.util.Locale

/**
 * Lyricify (Apple-format synced lyrics, original implementation).
 * Flow: POST credits.fm track resolve -> ISRC -> GET lyricify mobile API
 * -> parse "[startMs,endMs]line" text. Fully keyless; verified 2026-10.
 */
class LyricifyProvider(
    private val client: OkHttpClient,
) : LyricsProvider {

    override suspend fun fetch(
        title: String, artist: String, album: String, durationMs: Int,
    ): List<LyricsResult> = withContext(Dispatchers.IO) {
        if (title.isBlank() || artist.isBlank()) return@withContext emptyList()
        try {
            val isrc = resolveIsrc(title, artist) ?: return@withContext emptyList()
            val b64 = android.util.Base64.encodeToString(
                isrc.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP,
            )
            val url = "https://api.lyricify.app/lyrics/get/mobile/new" +
                "?username=${randomName()}&isrc=${ProviderHttp.enc(b64)}"
            val req = Request.Builder().url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val root = runCatching {
                    JSONObject(resp.body?.string().orEmpty())
                }.getOrNull() ?: return@withContext emptyList()
                if (root.optBoolean("isInstrumental", false)) return@withContext emptyList()
                val text = root.optString("text").ifEmpty { return@withContext emptyList() }
                val lines = if ("[from:AppleSyllable]" in text) {
                    // Syllable-level format: fall back to plain lines of the same text.
                    LyricifyLines.parse(text.lines().drop(1).joinToString("\n"))
                        .ifEmpty { return@withContext emptyList() }
                } else {
                    val parsed = LyricifyLines.parse(text)
                    if (parsed.isEmpty()) return@withContext emptyList()
                    parsed
                }
                val writer = root.optString("writer").ifEmpty { null }
                val lrc = buildString {
                    writer?.let { appendLine("[by:$it]") }
                    append(LrcParser.serialize(lines))
                }
                listOf(
                    LyricsResult(
                        plain = null, synced = lrc,
                        title = title, artist = artist, album = album.ifBlank { null },
                        durationSec = if (durationMs > 0) durationMs / 1000L else null,
                        source = "Lyricify", score = 0,
                    )
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun resolveIsrc(title: String, artist: String): String? {
        val body = JSONObject().put("name", title).put("artist", artist).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://api.credits.fm/v1/resolve/track")
            .post(body)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return runCatching {
                JSONObject(resp.body?.string().orEmpty()).optString("isrc").ifEmpty { null }
            }.getOrNull()
        }
    }

    private fun randomName(): String {
        val r = java.util.Random()
        return (0 until (5 + r.nextInt(8))).map { 'a' + r.nextInt(26) }
            .joinToString("").lowercase(Locale.ROOT)
    }

    companion object {
        private const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
    }
}
