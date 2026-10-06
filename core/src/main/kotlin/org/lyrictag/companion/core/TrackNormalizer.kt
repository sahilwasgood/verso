package org.lyrictag.companion.core

import java.text.Normalizer

/**
 * Track title/artist normalization for lyric search matching.
 *
 * Conservative by design: never strips parenthesized suffixes like (Live),
 * only removes provably-noise tokens (feat. credits) and normalizes
 * case/punctuation/unicode/whitespace.
 */
object TrackNormalizer {

    private val FEAT_PATTERN = Regex(
        """\s*[\(\[]?\s*(?:feat\.?|ft\.?|featuring|with)\s+[^\)\]]*[\)\]]?""",
        RegexOption.IGNORE_CASE
    )
    private val MULTI_SPACE = Regex("""\s+""")
    private val PUNCT = Regex("""[’‘`´]""")

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        s = PUNCT.replace(s, "'")
        // Strip feat. credits but keep everything else, incl. (Live), [Remix].
        s = FEAT_PATTERN.replace(s, " ")
        s = s.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s'&-]"""), " ")
        s = MULTI_SPACE.replace(s, " ").trim()
        return s
    }

    /** Normalized cache key: stable across minor tag edits. */
    fun cacheKey(title: String?, artist: String?, album: String?): String =
        listOf(normalize(title), normalize(artist), normalize(album)).joinToString("|")
}
