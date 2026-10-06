package org.lyrictag.companion.core

/** Field-level state used by the metadata review UI. */
enum class FieldState { MISSING, SUSPICIOUS, COMPLETE, VERIFIED }

/** What the user allows us to do with existing values. */
enum class OverwritePolicy { NEVER, ONLY_EMPTY, ALLOW_WITH_REVIEW }

data class MetadataField(val local: String?, val candidate: String?) {
    fun state(): FieldState = when {
        local.isNullOrBlank() -> if (candidate.isNullOrBlank()) FieldState.MISSING else FieldState.MISSING
        isSuspicious(local) -> FieldState.SUSPICIOUS
        else -> FieldState.COMPLETE
    }

    /** Applies the overwrite policy. Returns the value to write, or null = keep local. */
    fun resolved(policy: OverwritePolicy, needsReview: (String) -> Boolean = { false }): String? {
        if (candidate.isNullOrBlank()) return null
        if (local == candidate) return null // identical: no-op
        return when (policy) {
            OverwritePolicy.NEVER -> null
            OverwritePolicy.ONLY_EMPTY ->
                if (local.isNullOrBlank()) candidate else null
            OverwritePolicy.ALLOW_WITH_REVIEW ->
                if (local.isNullOrBlank() || needsReview(local)) candidate else null
        }
    }

    companion object {
        private val SUSPICIOUS_PATTERNS = listOf(
            Regex("""^track\s*\d+$""", RegexOption.IGNORE_CASE),
            Regex("""^unknown( artist)?$""", RegexOption.IGNORE_CASE),
            Regex("""^\d+\s*-\s*.+"""), // "01 - Title" stuffed in title field
        )
        fun isSuspicious(value: String?): Boolean {
            if (value.isNullOrBlank()) return false
            val v = value.trim()
            if (v.length == 1) return true
            if (v.equals("untitled", ignoreCase = true)) return true
            return SUSPICIOUS_PATTERNS.any { it.containsMatchIn(v) }
        }
    }
}
