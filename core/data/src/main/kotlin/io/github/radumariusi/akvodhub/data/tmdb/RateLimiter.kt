package io.github.radumariusi.akvodhub.data.tmdb

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Împarte cererile uniform în timp: cel mult [perSecond] pe secundă, oricâte corutine ar cere. */
class RateLimiter(
    perSecond: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val intervalMs = 1000.0 / perSecond
    private val mutex = Mutex()
    private var next = 0.0

    suspend fun acquire() {
        val wait =
            mutex.withLock {
                val now = clock().toDouble()
                val slot = maxOf(now, next)
                next = slot + intervalMs
                (slot - now).toLong()
            }
        if (wait > 0) delay(wait)
    }

    /** Serverul a cerut o pauză (HTTP 429): nimeni nu mai trimite până la [untilMs]. */
    suspend fun pauseUntil(untilMs: Long) {
        mutex.withLock { next = maxOf(next, untilMs.toDouble()) }
    }
}
