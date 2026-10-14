package com.verso.music

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import com.verso.music.lyrics.AmllProvider
import com.verso.music.lyrics.KugouProvider
import com.verso.music.lyrics.LunaBeatProvider
import com.verso.music.lyrics.LyricifyProvider
import java.util.concurrent.TimeUnit

/**
 * Live integration tests against the real keyless endpoints.
 * These need network; they fail loudly when a provider dies (like bLyrics
 * did) so breakage is caught instead of degrading silently on-device.
 */
class ProviderSmokeTest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun check(name: String, block: suspend () -> Int) {
        val n = try {
            block()
        } catch (t: Throwable) {
            throw AssertionError("$name threw ${t.javaClass.simpleName}: ${t.message}", t)
        }
        println("$name returned $n results")
        assertTrue("$name returned nothing for Baby/Justin Bieber", n > 0)
    }

    @Test fun kugou() = runBlocking {
        check("KuGou") {
            KugouProvider(client).fetch("Baby", "Justin Bieber", "My World 2.0", 225_000).size
        }
    }

    @Test fun amll() = runBlocking {
        check("AMLL") {
            AmllProvider(client).fetch("Baby", "Justin Bieber", "My World 2.0", 225_000).size
        }
    }

    @Test fun lyricify() = runBlocking {
        check("Lyricify") {
            LyricifyProvider(client).fetch("Baby", "Justin Bieber", "My World 2.0", 225_000).size
        }
    }

    @Test fun lunabeat() = runBlocking {
        // LunaBeat's index is C-pop heavy; test with an indexed track.
        check("LunaBeat") {
            LunaBeatProvider(client).fetch("爱琴海", "周杰伦", "", 0).size
        }
    }

    @Test fun musicbrainzSuggest() = runBlocking {
        val list = com.verso.music.metadata.MusicBrainz.suggestFor(
            "Lut Gaye", "Jubin Nautiyal", "", "Lut_Gaye.mp3",
        )
        println("MusicBrainz returned ${list.size} candidates")
        assertTrue("MusicBrainz found nothing for Lut Gaye", list.isNotEmpty())
    }

    @Test fun lastfmRequestToken() = runBlocking {
        // Step 1 needs no user action; step 2 (approval) is manual.
        // Credentials come from env so no secret lands in git.
        val key = System.getenv("LASTFM_TEST_KEY")
        val secret = System.getenv("LASTFM_TEST_SECRET")
        org.junit.Assume.assumeTrue("set LASTFM_TEST_KEY/SECRET to run", key != null && secret != null)
        val token = com.verso.music.scrobble.LastFm.requestToken(key!!, secret!!)
        println("Last.fm token: ${token?.take(8)}...")
        assertTrue("Last.fm issued no token", !token.isNullOrBlank())
    }

    @Test fun itunesMetadataJunkTitle() = runBlocking {
        val list = com.verso.music.metadata.MusicBrainz.suggestFor(
            "Akon Right Now Na Na Na", null, "", "Akon_Right_Now_Na_Na_Na.mp3",
        )
        println("Suggest returned ${list.size}: ${list.take(2)}")
        val best = list.firstOrNull()
        assertTrue(
            "No usable metadata for the Akon dump",
            best != null && (best.score >= 90) &&
                (best.artist?.contains("Akon", ignoreCase = true) == true),
        )
    }
}
