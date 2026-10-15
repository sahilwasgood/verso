package com.verso.music.core

/**
 * Guesses title/artist from a bare filename (YTDL-style dumps with no tags).
 * Conservative: only fills blanks, never rewrites real tags. Handles
 * "Artist - Title", underscore_joined names, and strips obvious video-dump
 * noise ("Official Video", "Lyrical", "320kbps", site names, "-mc" suffixes).
 */
object FilenameParser {

    data class Parsed(val title: String, val artist: String?)

    private val NOISE = Regex(
        """(?i)\b(official\s+(music\s+)?videos?|official\s+audio|lyrical(\s+videos?)?|""" +
            """full(\s+hd)?\s+(video\s+)?songs?|full\s+audio(\s+songs?)?|audio\s+songs?|""" +
            """video\s+songs?|\d+\s*kbps|mp3wale(\.com)?|hd|4k|lyrics?(\s+video)?|""" +
            """with\s+lyrics|m4a|mp3|wav|flac)\b"""
    )
    private val TRAIL_MC = Regex("""(?i)[\s_\-]+mc$""")
    private val MULTI_SPACE = Regex("""\s+""")
    private val DASH_SPLIT = Regex("""\s+[_-]\s+""")

    fun parse(fileName: String): Parsed {
        var s = fileName.substringAfterLast('/').substringBeforeLast('.')
        // Spaced underscores separate phrases ("A _ B"); bare ones join words.
        s = s.replace(Regex(""" +_+ """), " , ")
        s = s.replace('_', ' ')
        s = MULTI_SPACE.replace(s, " ").trim()
        if (s.isEmpty()) return Parsed("", null)
        // "Artist - Title" (first split; titles may contain dashes too).
        val dash = DASH_SPLIT.find(s)
        var artist: String? = null
        var title = s
        if (dash != null) {
            val left = s.substring(0, dash.range.first).trim()
            val right = s.substring(dash.range.last + 1).trim()
            if (left.isNotEmpty() && right.isNotEmpty() && left.length <= 60) {
                artist = left
                title = right
            }
        }
        title = cleanTitle(title)
        artist = artist?.let { cleanTitle(it) }?.takeIf { it.isNotEmpty() }
        if (title.isEmpty()) return Parsed("", null)
        return Parsed(title, artist)
    }

    private fun cleanTitle(s: String): String {
        var t = NOISE.replace(s, " ")
        t = TRAIL_MC.replace(t, "")
        // Bare video/audio only when glued to a separator ("A Video _ B").
        // (No lookbehind: JVM requires bounded length there.)
        // A bare leading "Video Killed..." is a real title - keep it.
        t = t.replace(Regex("""(?i)\b(videos?|audios?)\b\s*([,_-])""")) {
            it.groupValues[2]
        }
        t = t.replace(Regex("""(?i)([,_-])\s*\b(videos?|audios?)\b""")) {
            it.groupValues[1]
        }
        t = t.replace(Regex("""\s*,\s*"""), ", ")
        t = MULTI_SPACE.replace(t, " ").trim(' ', ',', '-', '_')
        return t
    }
}
