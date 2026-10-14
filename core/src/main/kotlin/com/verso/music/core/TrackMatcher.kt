package com.verso.music.core

/** Confidence bucket driving auto-apply vs review UI. */
enum class Confidence { HIGH, MEDIUM, LOW, REJECT }

data class MatchInput(
    val localTitle: String?,
    val localArtist: String?,
    val localAlbum: String?,
    val localDurationMs: Long?,
    val candTitle: String?,
    val candArtist: String?,
    val candAlbum: String?,
    val candDurationSec: Long?,
)

data class MatchScore(val score: Int, val confidence: Confidence, val reasons: List<String>)

object TrackMatcher {

    private const val DURATION_TOLERANCE_MS = 5_000L

    fun score(m: MatchInput): MatchScore {
        val reasons = mutableListOf<String>()
        var score = 0

        val lt = TrackNormalizer.normalize(m.localTitle)
        val la = TrackNormalizer.normalize(m.localArtist)
        val lb = TrackNormalizer.normalize(m.localAlbum)
        val ct = TrackNormalizer.normalize(m.candTitle)
        val ca = TrackNormalizer.normalize(m.candArtist)
        val cb = TrackNormalizer.normalize(m.candAlbum)

        if (lt.isEmpty()) return MatchScore(0, Confidence.REJECT, listOf("local title missing"))

        // Title: exact normalized = 50, substring either way = 25.
        when {
            lt == ct && ct.isNotEmpty() -> { score += 50; reasons += "exact title" }
            ct.isNotEmpty() && (ct.contains(lt) || lt.contains(ct)) -> { score += 25; reasons += "partial title" }
            else -> { reasons += "title mismatch"; return MatchScore(score, Confidence.REJECT, reasons) }
        }
        // Artist: exact = 30, missing-on-either-side = +5 (weak), mismatch caps at MEDIUM.
        var artistOk = false
        when {
            la.isNotEmpty() && la == ca -> { score += 30; reasons += "exact artist"; artistOk = true }
            la.isEmpty() || ca.isEmpty() -> { score += 5; reasons += "artist unknown on one side" }
            else -> { reasons += "artist differs" }
        }
        // Album bonus: +8 only when both known and equal.
        if (lb.isNotEmpty() && lb == cb) { score += 8; reasons += "album agrees" }
        // Duration proximity: +12 within tolerance, +4 within 15s, else −10.
        val ld = m.localDurationMs
        val cd = m.candDurationSec?.let { it * 1_000 }
        if (ld != null && ld > 0 && cd != null && cd > 0) {
            val d = kotlin.math.abs(ld - cd)
            when {
                d <= DURATION_TOLERANCE_MS -> { score += 12; reasons += "duration within ±5s" }
                d <= 15_000 -> { score += 4; reasons += "duration within ±15s" }
                else -> { score -= 10; reasons += "duration differs >15s" }
            }
        }

        val confidence = when {
            score >= 85 && artistOk -> Confidence.HIGH
            score >= 55 -> Confidence.MEDIUM
            score >= 30 -> Confidence.LOW
            else -> Confidence.REJECT
        }
        // Artist mismatch can never be HIGH.
        val capped = if (!artistOk && confidence == Confidence.HIGH) Confidence.MEDIUM else confidence
        return MatchScore(score.coerceIn(0, 100), capped, reasons)
    }

    /** Auto-apply only HIGH confidence; everything else needs review. */
    fun mayAutoApply(s: MatchScore): Boolean = s.confidence == Confidence.HIGH
}
