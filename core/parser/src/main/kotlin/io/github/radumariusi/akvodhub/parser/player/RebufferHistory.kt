package io.github.radumariusi.akvodhub.parser.player

/** Detectează întreruperile repetate după pornire, folosind ceasul monoton. */
class RebufferHistory {
    private data class Stall(val endedAt: Long, val duration: Long)

    private val stalls = ArrayDeque<Stall>()
    private var bufferingSince: Long? = null
    private var ignoreBefore = 0L

    var totalCount = 0
        private set
    var totalDurationMs = 0L
        private set

    fun buffering(now: Long) {
        if (now >= ignoreBefore && bufferingSince == null) bufferingSince = now
    }

    fun ready(now: Long): Boolean {
        val start = bufferingSince ?: return false
        bufferingSince = null
        val duration = now - start
        if (duration < MIN_STALL_MS) return false
        totalCount++
        totalDurationMs += duration
        while (stalls.firstOrNull()?.let { now - it.endedAt >= WINDOW_MS } == true) stalls.removeFirst()
        stalls.addLast(Stall(now, duration))
        if (stalls.size < 3 && (stalls.size < 2 || stalls.sumOf { it.duration } < 4_000L)) return false
        stalls.clear()
        return true
    }

    fun reset(ignoreBefore: Long = 0) {
        stalls.clear()
        bufferingSince = null
        this.ignoreBefore = ignoreBefore
    }

    private companion object {
        const val MIN_STALL_MS = 400L
        const val WINDOW_MS = 35_000L
    }
}
