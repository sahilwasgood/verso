package org.lyrictag.companion.lyrics

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * Priority lane for lyrics fetching.
 *
 * When Poweramp asks for the now-playing track (user staring at the lyrics
 * panel), that fetch jumps the queue: batch work pauses until it finishes,
 * so the visible song always resolves first. Batch resumes automatically.
 */
object FetchCoordinator {

    private val priorityActive = AtomicInteger(0)

    fun priorityStart() {
        priorityActive.incrementAndGet()
    }

    fun priorityDone() {
        priorityActive.decrementAndGet()
    }

    /** Batch loops call this before each unit of work. */
    suspend fun yieldWhilePriority() {
        var waited = 0
        while (priorityActive.get() > 0 && waited < 60_000) {
            delay(250)
            waited += 250
        }
    }

    fun isPriorityActive(): Boolean = priorityActive.get() > 0
}
