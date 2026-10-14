package com.verso.music.core

import org.junit.Assert.*
import org.junit.Test

class LrcParserTest {
    @Test fun `parses synced lines`() {
        val p = LrcParser.parse("[00:12.34]Hello\n[01:02.500]World\n")
        assertTrue(p.isSynced)
        assertEquals(2, p.lines.size)
        assertEquals(12_340L, p.lines[0].timeMs)
        assertEquals("Hello", p.lines[0].text)
        assertEquals(62_500L, p.lines[1].timeMs)
    }
    @Test fun `multiple tags one line`() {
        val p = LrcParser.parse("[00:10.00][00:20.00]Chorus")
        assertEquals(2, p.lines.size)
    }
    @Test fun `metadata headers skipped`() {
        val p = LrcParser.parse("[ti:Title]\n[ar:Artist]\n[00:01.00]Hi")
        assertTrue(p.isSynced)
        assertEquals(1, p.lines.size)
        assertEquals("Hi", p.lines[0].text)
    }
    @Test fun `plain text not synced`() {
        val p = LrcParser.parse("Just\nsome\nlyrics")
        assertFalse(p.isSynced)
        assertEquals(3, p.lines.size)
    }
    @Test fun `blank safe`() {
        assertEquals(0, LrcParser.parse(null).lines.size)
        assertEquals(0, LrcParser.parse("").lines.size)
    }
    @Test fun `serialize round trip`() {
        val raw = "[00:12.34]Hello\n[01:02.50]World"
        val back = LrcParser.serialize(LrcParser.parse(raw).lines)
        assertEquals(raw, back)
    }
    @Test fun `looksLikeLrc`() {
        assertTrue(LrcParser.looksLikeLrc("[00:01.00]x"))
        assertFalse(LrcParser.looksLikeLrc("plain text"))
        assertFalse(LrcParser.looksLikeLrc(null))
    }
}
