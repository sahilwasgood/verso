package org.lyrictag.companion.core

import org.junit.Assert.*
import org.junit.Test

class TrackNormalizerTest {
    @Test fun `case punctuation whitespace normalized`() {
        assertEquals("hello world", TrackNormalizer.normalize("  Hello,  WORLD! "))
    }
    @Test fun `feat credit stripped`() {
        assertEquals("song", TrackNormalizer.normalize("Song feat. Someone"))
        assertEquals("song", TrackNormalizer.normalize("Song (ft. Someone)"))
    }
    @Test fun `live suffix preserved`() {
        val n = TrackNormalizer.normalize("Song Name (Live)")
        assertTrue(n.contains("live"))
    }
    @Test fun `unicode normalized`() {
        assertEquals(TrackNormalizer.normalize("café"), TrackNormalizer.normalize("café"))
    }
    @Test fun `blank safe`() {
        assertEquals("", TrackNormalizer.normalize(null))
        assertEquals("", TrackNormalizer.normalize("   "))
    }
    @Test fun `cache key stable`() {
        assertEquals(
            TrackNormalizer.cacheKey("Song FEAT. X", "Artist", "Album"),
            TrackNormalizer.cacheKey("song", "artist", "album")
        )
    }
}
