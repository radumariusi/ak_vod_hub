package io.github.radumariusi.akvodhub.parser.player

/** Detectează întreruperi scurte repetate după ce redarea a pornit. Folosește ceasul monoton. */
class RebufferHistory {
    private val stalls = ArrayDeque<Long>()
    private var bufferingSince: Long? = null
    private var ignoreBefore = 0L

    fun buffering(now: Long) {
        if (now >= ignoreBefore && bufferingSince == null) bufferingSince = now
    }

    fun ready(now: Long): Boolean {
        val start = bufferingSince ?: return false
        bufferingSince = null
        if (now - start < MIN_STALL_MS) return false
        while (stalls.firstOrNull()?.let { now - it >= WINDOW_MS } == true) stalls.removeFirst()
        stalls.addLast(now)
        if (stalls.size < 3) return false
        stalls.clear()
        return true
    }

    fun reset(ignoreBefore: Long = 0) {
        stalls.clear()
        bufferingSince = null
        this.ignoreBefore = ignoreBefore
    }

    private companion object {
        const val MIN_STALL_MS = 1_000L
        const val WINDOW_MS = 60_000L
    }
}
