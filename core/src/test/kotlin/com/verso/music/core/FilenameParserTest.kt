package com.verso.music.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilenameParserTest {

    @Test fun `underscore dump splits`() {
        // No dash to split on: whole cleaned stem becomes the title.
        val p = FilenameParser.parse("Akon_Right_Now_Na_Na_Na.mp3")
        assertEquals("Akon Right Now Na Na Na", p.title)
        assertNull(p.artist)
    }

    @Test fun `dash split with video noise`() {
        val p = FilenameParser.parse(
            "Adnan Sami - Lift Karadey Video _ Kabhi To Nazar Milao.m4a"
        )
        assertEquals("Adnan Sami", p.artist)
        assertEquals("Lift Karadey, Kabhi To Nazar Milao", p.title)
    }

    @Test fun `lyrical suffix stripped`() {
        val p = FilenameParser.parse("Jeene Laga Hoon Lyrical - Ramaiya Vastavaiya.m4a")
        assertEquals("Jeene Laga Hoon", p.artist)
        assertEquals("Ramaiya Vastavaiya", p.title)
    }

    @Test fun `audio song and mc suffix stripped`() {
        val p = FilenameParser.parse(
            "Agar Tum Saath Ho FULL AUDIO Song _ Tamasha _ Ranbir Kapoor, Deepika Padukone _ -mc.m4a"
        )
        assertEquals("Agar Tum Saath Ho, Tamasha, Ranbir Kapoor, Deepika Padukone", p.title)
    }

    @Test fun `video glued to separator stripped, never crashes`() {
        // Regression: unbounded lookbehind crashed the scan on-device.
        assertEquals("Lift Karadey, Kabhi To Nazar Milao", FilenameParser.parse(
            "Adnan Sami - Lift Karadey Video _ Kabhi To Nazar Milao.m4a"
        ).title)
        assertEquals("Concert", FilenameParser.parse("Concert _, Video").title)
        // Bare leading Video is a real title — kept.
        assertEquals("Video Killed the Radio Star", FilenameParser.parse(
            "Video Killed the Radio Star.mp3"
        ).title)
    }

    @Test fun `plain name untouched`() {
        val p = FilenameParser.parse("Memory Reboot.mp3")
        assertEquals("Memory Reboot", p.title)
        assertNull(p.artist)
    }

    @Test fun `blank safe`() {
        val p = FilenameParser.parse(".mp3")
        assertEquals("", p.title)
        assertNull(p.artist)
    }
}
