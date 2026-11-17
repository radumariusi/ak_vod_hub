package io.github.radumariusi.akvodhub.parser.player

/** Deadline pe ceas monoton: pauza redării nu oprește timpul de somn. Nu se persistă. */
data class SleepTimer(val deadlineMs: Long? = null, val endOfEpisode: Boolean = false) {
    fun shouldStop(nowMs: Long, ended: Boolean): Boolean =
        (deadlineMs?.let { nowMs >= it } == true) || (endOfEpisode && ended)

    companion object {
        fun after(minutes: Int, nowMs: Long): SleepTimer =
            if (minutes in listOf(30, 60, 90)) SleepTimer(nowMs + minutes * 60_000L) else SleepTimer()
    }
}
