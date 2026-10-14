package com.verso.music.core

/** One parsed LRC line. timeMs < 0 means metadata/unsynced line. */
data class LrcLine(val timeMs: Long, val text: String)

data class ParsedLyrics(val lines: List<LrcLine>, val isSynced: Boolean)

private val LRC_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val LRC_META = Regex("""\[([a-zA-Z]+):(.*)]""")

object LrcParser {

    fun parse(raw: String?): ParsedLyrics {
        if (raw.isNullOrBlank()) return ParsedLyrics(emptyList(), false)
        val lines = mutableListOf<LrcLine>()
        var syncedCount = 0
        for (rawLine in raw.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val meta = LRC_META.matchEntire(line)
            if (meta != null && LRC_TAG.find(line) == null) continue // [ti:], [ar:], [length:] …
            val tags = LRC_TAG.findAll(line).toList()
            val text = LRC_TAG.replace(line, "").trim()
            if (tags.isEmpty()) {
                lines.add(LrcLine(-1, line))
            } else {
                for (t in tags) {
                    val min = t.groupValues[1].toLong()
                    val sec = t.groupValues[2].toLong()
                    val frac = t.groupValues[3]
                    val ms = when (frac.length) {
                        0 -> 0L
                        2 -> frac.toLong() * 10      // centiseconds
                        else -> frac.take(3).padEnd(3, '0').toLong() // millis
                    }
                    lines.add(LrcLine(min * 60_000 + sec * 1_000 + ms, text))
                    syncedCount++
                }
            }
        }
        val synced = lines.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }
        val unsynced = lines.filter { it.timeMs < 0 }
        return ParsedLyrics(synced + unsynced, syncedCount > 0)
    }

    fun serialize(lines: List<LrcLine>): String = buildString {
        for (l in lines.sortedBy { it.timeMs }) {
            if (l.timeMs < 0) appendLine(l.text)
            else {
                val m = l.timeMs / 60_000
                val s = (l.timeMs % 60_000) / 1_000
                val cs = (l.timeMs % 1_000) / 10
                appendLine("[%02d:%02d.%02d]%s".format(m, s, cs, l.text))
            }
        }
    }.trimEnd('\n')

    /** True if the text looks like LRC (at least one valid timestamp tag). */
    fun looksLikeLrc(text: String?): Boolean =
        !text.isNullOrBlank() && LRC_TAG.containsMatchIn(text)
}
