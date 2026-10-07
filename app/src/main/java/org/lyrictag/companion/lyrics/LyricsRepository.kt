package org.lyrictag.companion.lyrics

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.lyrictag.companion.core.MatchInput
import org.lyrictag.companion.core.TrackMatcher
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Orchestrates providers → scores candidates with TrackMatcher →
 * returns best text (synced preferred) for Poweramp.
 */
class LyricsRepository(
    val providers: List<LyricsProvider>,
    private val cache: LyricsCache,
) {
    /** Poweramp path: cache first (normalized key), else providers. */
    suspend fun fetchForPoweramp(
        title: String, artist: String, album: String, durationMs: Int,
    ): String? {
        cache.get(title, artist, album)?.let { return it }
        val best = search(title, artist, album, durationMs.toLong()).firstOrNull()
            ?: return null
        val text = best.result.synced ?: best.result.plain ?: return null
        cache.put(title, artist, album, text)
        return text
    }

    /** Full search with confidence-scored results (UI path). */
    suspend fun search(
        title: String, artist: String, album: String, durationMs: Long,
    ): List<ScoredLyrics> {
        val all = mutableListOf<LyricsResult>()
        for (p in providers) {
            try { all += p.fetch(title, artist, album, durationMs.toInt()) }
            catch (_: Exception) { /* provider failure: next provider */ }
        }
        return all.map { r ->
            val s = TrackMatcher.score(
                MatchInput(title, artist, album, durationMs,
                    r.title, r.artist, r.album, r.durationSec)
            )
            ScoredLyrics(r, s.score, s.confidence, s.reasons)
        }.sortedByDescending { it.score }
    }

    companion object {
        const val BASE_URL = "https://lrclib.net/"
        const val USER_AGENT = "LyricTag/0.1.0 (https://github.com/lyrictag/lyrictag)"

        /** Registry of all built-in providers (order = lookup priority). */
        val ALL_PROVIDERS = listOf("LRCLIB", "AMLL", "KuGou", "Lyricify", "LunaBeat")

        /** User-enabled subset (all on by default). */
        fun enabledNames(context: android.content.Context): Set<String> =
            context.getSharedPreferences("lyrictag", android.content.Context.MODE_PRIVATE)
                .getStringSet("lyric_providers", null) ?: ALL_PROVIDERS.toSet()

        fun setEnabled(context: android.content.Context, names: Set<String>) {
            context.getSharedPreferences("lyrictag", android.content.Context.MODE_PRIVATE)
                .edit().putStringSet("lyric_providers", names.toSet()).apply()
        }

        fun default(cache: LyricsCache): LyricsRepository {
            val json = Json { ignoreUnknownKeys = true }
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", USER_AGENT) // required by LRCLIB
                            .build()
                    )
                }
                .build()
            val api = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(LrcLibApi::class.java)
            // NOTE: provider enable/disable is applied per-fetch in LyricTagApp,
            // which rebuilds this list from enabledNames(). Order = priority.
            return LyricsRepository(
                listOf(
                    LrclibProvider(api),
                    AmllProvider(client),
                    KugouProvider(client),
                    LyricifyProvider(client),
                    LunaBeatProvider(client),
                ),
                cache,
            )
        }

        /** Repository honoring the user's enabled-provider set + order. */
        fun forPrefs(context: android.content.Context, cache: LyricsCache): LyricsRepository {
            val all = default(cache).providers
            val enabled = enabledNames(context)
            val ordered = ALL_PROVIDERS.filter { it in enabled }
                .mapNotNull { name -> all.firstOrNull { providerName(it) == name } }
            return LyricsRepository(ordered.ifEmpty { all }, cache)
        }

        private fun providerName(p: LyricsProvider): String = when (p) {
            is LrclibProvider -> "LRCLIB"
            is AmllProvider -> "AMLL"
            is KugouProvider -> "KuGou"
            is LyricifyProvider -> "Lyricify"
            is LunaBeatProvider -> "LunaBeat"
            else -> p.javaClass.simpleName
        }
    }
}

data class ScoredLyrics(
    val result: LyricsResult,
    val score: Int,
    val confidence: org.lyrictag.companion.core.Confidence,
    val reasons: List<String>,
)

/** Minimal cache seam; Room-backed impl in db package. */
interface LyricsCache {
    suspend fun get(title: String, artist: String, album: String): String?
    suspend fun put(title: String, artist: String, album: String, lyrics: String)
}
