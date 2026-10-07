package org.lyrictag.companion.core

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Converts TTML (Apple Music / AMLL / LunaBeat style) timed text into
 * plain [LrcLine]s. Only the main vocal line is kept; translation and
 * romanization spans are skipped.
 *
 * Original implementation for LyricTag (no third-party TTML code).
 */
object TtmlConverter {

    private val CLOCK = Regex("""^(\d+):(\d{2}):(\d{2})(?:[.:](\d{1,3}))?$""")
    private val MS = Regex("""^(\d+(?:\.\d+)?)ms$""")
    private val SKIP_ROLES = setOf("x-translation", "translation", "x-roman", "romanization", "x-background")

    fun ttmlToLrcLines(ttml: String?): List<LrcLine> {
        if (ttml.isNullOrBlank()) return emptyList()
        return try {
            val db = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }.newDocumentBuilder()
            val doc = db.parse(ttml.byteInputStream(Charsets.UTF_8))
            val ps = doc.getElementsByTagNameNS("*", "p")
            val out = mutableListOf<LrcLine>()
            for (i in 0 until ps.length) {
                val p = ps.item(i) as? Element ?: continue
                val begin = parseTime(p.getAttribute("begin")) ?: continue
                val text = mainText(p).trim()
                if (text.isEmpty()) continue
                out.add(LrcLine(begin, text))
            }
            out.sortedBy { it.timeMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mainText(p: Element): String {
        val sb = StringBuilder()
        val kids = p.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            when (n.nodeType) {
                Node.TEXT_NODE -> sb.append(n.nodeValue)
                Node.ELEMENT_NODE -> {
                    val el = n as Element
                    val role = el.getAttribute("ttm:role").ifEmpty { el.getAttribute("role") }
                    if (role.lowercase() in SKIP_ROLES) continue
                    if (el.localName == "br") sb.append(' ')
                    else sb.append(mainText(el))
                }
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ")
    }

    /** "HH:MM:SS.mmm" or "1234ms" -> ms. Null when unparseable. */
    fun parseTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        MS.matchEntire(s)?.let { return it.groupValues[1].toDouble().toLong() }
        CLOCK.matchEntire(s)?.let { m ->
            val h = m.groupValues[1].toLong()
            val min = m.groupValues[2].toLong()
            val sec = m.groupValues[3].toLong()
            val frac = m.groupValues[4]
            val ms = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
            return h * 3_600_000 + min * 60_000 + sec * 1_000 + ms
        }
        return null
    }
}

/**
 * Lyricify "[startMs,endMs]line" text (from api.lyricify.app, ISRC-resolved
 * via credits.fm). Original parser.
 */
object LyricifyLines {

    private val LINE = Regex("""^\[(\d+),(\d+)](.*)$""")

    fun parse(text: String?): List<LrcLine> {
        if (text.isNullOrBlank()) return emptyList()
        val out = mutableListOf<LrcLine>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("[type:") || line.startsWith("[offset:")) continue
            val m = LINE.matchEntire(line) ?: continue
            val body = m.groupValues[3].trim()
            if (body.isEmpty()) continue
            out.add(LrcLine(m.groupValues[1].toLong(), body))
        }
        return out.sortedBy { it.timeMs }
    }
}
