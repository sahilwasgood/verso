package org.lyrictag.companion.core

import org.junit.Assert.*
import org.junit.Test

class TtmlConverterTest {
    private val ttml = """<?xml version="1.0" encoding="UTF-8"?>
<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
<body><div>
<p begin="00:00:02.690" end="00:00:06.126">Oh, whoa</p>
<p begin="00:00:06.126" end="00:00:10.032">You know you <span ttm:role="x-translation">tu sais</span> love me</p>
<p begin="2692ms" end="6126ms">Plain ms timing</p>
<p>No timing here</p>
</div></body></tt>"""

    @Test fun `parses clock and ms timings, skips translations`() {
        val lines = TtmlConverter.ttmlToLrcLines(ttml)
        assertEquals(3, lines.size)
        assertEquals(2690L, lines[0].timeMs)
        assertEquals("Oh, whoa", lines[0].text)
        assertEquals(2692L, lines[1].timeMs)
        assertEquals("Plain ms timing", lines[1].text)
        assertEquals(6126L, lines[2].timeMs)
        assertEquals("You know you love me", lines[2].text)
    }
    @Test fun `blank and garbage safe`() {
        assertTrue(TtmlConverter.ttmlToLrcLines(null).isEmpty())
        assertTrue(TtmlConverter.ttmlToLrcLines("not xml at all <").isEmpty())
    }
    @Test fun `parseTime units`() {
        assertEquals(3723456L, TtmlConverter.parseTime("01:02:03.456"))
        assertEquals(2690L, TtmlConverter.parseTime("00:00:02.69"))
        assertEquals(2692L, TtmlConverter.parseTime("2692ms"))
        assertNull(TtmlConverter.parseTime("soon"))
        assertNull(TtmlConverter.parseTime(null))
    }
}

class LyricifyLinesTest {
    @Test fun `parses lyricify lines`() {
        val text = "[type:LyricifyLines]\n[2692,6126]Oh, whoa\n[6126,10032]  \n[10032,14447]Second line"
        val lines = LyricifyLines.parse(text)
        assertEquals(2, lines.size)
        assertEquals(2692L, lines[0].timeMs)
        assertEquals("Second line", lines[1].text)
    }
    @Test fun `blank safe`() {
        assertTrue(LyricifyLines.parse(null).isEmpty())
        assertTrue(LyricifyLines.parse("[type:LyricifyLines]\n").isEmpty())
    }
}
