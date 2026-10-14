package com.verso.music.core

import org.junit.Assert.*
import org.junit.Test

class TrackMatcherTest {
    private fun input(
        lt: String? = "Song", la: String? = "Artist", lb: String? = "Album", ld: Long? = 200_000,
        ct: String? = "Song", ca: String? = "Artist", cb: String? = "Album", cd: Long? = 200,
    ) = MatchInput(lt, la, lb, ld, ct, ca, cb, cd)

    @Test fun `exact match is high`() {
        val s = TrackMatcher.score(input())
        assertEquals(Confidence.HIGH, s.confidence)
        assertTrue(TrackMatcher.mayAutoApply(s))
    }
    @Test fun `title mismatch rejects`() {
        val s = TrackMatcher.score(input(ct = "Totally Different"))
        assertEquals(Confidence.REJECT, s.confidence)
    }
    @Test fun `artist mismatch caps at medium`() {
        val s = TrackMatcher.score(input(ca = "Someone Else"))
        assertTrue(s.confidence != Confidence.HIGH)
    }
    @Test fun `duration far off lowers score`() {
        val near = TrackMatcher.score(input(cd = 200)).score
        val far = TrackMatcher.score(input(cd = 400)).score
        assertTrue(far < near)
    }
    @Test fun `missing local title rejects`() {
        assertEquals(Confidence.REJECT, TrackMatcher.score(input(lt = null)).confidence)
    }
    @Test fun `feat variant still matches`() {
        val s = TrackMatcher.score(input(lt = "Song feat. Guest"))
        assertEquals(Confidence.HIGH, s.confidence)
    }
}

class MetadataRulesTest {
    @Test fun `missing detected`() {
        assertEquals(FieldState.MISSING, MetadataField(null, "X").state())
        assertEquals(FieldState.MISSING, MetadataField("  ", "X").state())
    }
    @Test fun `suspicious detected`() {
        assertEquals(FieldState.SUSPICIOUS, MetadataField("Track 01", "X").state())
        assertEquals(FieldState.SUSPICIOUS, MetadataField("Unknown", "X").state())
    }
    @Test fun `never policy keeps values`() {
        assertNull(MetadataField("Good", "Other").resolved(OverwritePolicy.NEVER))
    }
    @Test fun `only empty fills blanks`() {
        assertEquals("X", MetadataField(null, "X").resolved(OverwritePolicy.ONLY_EMPTY))
        assertNull(MetadataField("Good", "X").resolved(OverwritePolicy.ONLY_EMPTY))
    }
    @Test fun `identical is no-op`() {
        assertNull(MetadataField("X", "X").resolved(OverwritePolicy.ALLOW_WITH_REVIEW))
    }
}
