package org.lyrictag.companion

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lyrictag.companion.lyrics.AmllProvider
import org.lyrictag.companion.lyrics.KugouProvider
import org.lyrictag.companion.lyrics.LunaBeatProvider
import org.lyrictag.companion.lyrics.LyricifyProvider
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
}
