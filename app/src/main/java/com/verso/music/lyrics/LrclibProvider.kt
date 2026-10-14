package com.verso.music.lyrics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LrcLibRecord(
    val id: Long = 0,
    val name: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

/** One lyric result with provenance + match info. */
data class LyricsResult(
    val plain: String?,
    val synced: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationSec: Long?,
    val source: String,
    val score: Int,
)

interface LrcLibApi {
    @retrofit2.http.GET("api/get")
    suspend fun get(
        @retrofit2.http.Query("track_name") track: String,
        @retrofit2.http.Query("artist_name") artist: String,
        @retrofit2.http.Query("album_name") album: String,
        @retrofit2.http.Query("duration") durationSec: Long,
    ): LrcLibRecord

    @retrofit2.http.GET("api/search")
    suspend fun search(
        @retrofit2.http.Query("q") q: String? = null,
        @retrofit2.http.Query("track_name") track: String? = null,
        @retrofit2.http.Query("artist_name") artist: String? = null,
        @retrofit2.http.Query("album_name") album: String? = null,
    ): List<LrcLibRecord>
}

/** ProviderA: LRCLIB — free, keyless, generous limits. */
class LrclibProvider(
    private val api: LrcLibApi,
    private val sourceName: String = "LRCLIB",
) : LyricsProvider {

    override suspend fun fetch(
        title: String, artist: String, album: String, durationMs: Int,
    ): List<LyricsResult> {
        if (title.isBlank()) return emptyList()
        val out = mutableListOf<LyricsResult>()
        // 1. Exact-signature lookup.
        try {
            val r = api.get(title, artist, album, (durationMs / 1000).toLong())
            toResult(r)?.let { out += it }
        } catch (_: retrofit2.HttpException) { /* 404 = no exact match; fall through */ }
        // 2. Controlled fallback: keyword search, take top few.
        if (out.isEmpty()) {
            val list = api.search(
                q = null, track = title.takeIf { it.isNotBlank() },
                artist = artist.takeIf { it.isNotBlank() },
                album = album.takeIf { it.isNotBlank() },
            )
            list.take(5).mapNotNullTo(out) { toResult(it) }
        }
        return out
    }

    private fun toResult(r: LrcLibRecord): LyricsResult? {
        if (r.instrumental) return null
        val plain = r.plainLyrics?.takeIf { it.isNotBlank() }
        val synced = r.syncedLyrics?.takeIf { it.isNotBlank() }
        if (plain == null && synced == null) return null
        return LyricsResult(
            plain = plain, synced = synced,
            title = r.trackName ?: r.name, artist = r.artistName, album = r.albumName,
            durationSec = r.duration?.toLong(), source = sourceName, score = 0,
        )
    }
}

/** LyricsProvider seam — future providers implement this. */
interface LyricsProvider {
    suspend fun fetch(title: String, artist: String, album: String, durationMs: Int): List<LyricsResult>
}
