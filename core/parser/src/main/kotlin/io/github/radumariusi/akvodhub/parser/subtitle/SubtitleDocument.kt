package io.github.radumariusi.akvodhub.parser.subtitle

/** O replică: textul (cu `\n` între rânduri) afișat între [startMs] și [endMs]. */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** O subtitrare externă gata de afișat: replicile ordonate după început, cu căutare rapidă după poziție. */
class SubtitleDocument(
    cues: List<SubtitleCue>,
) {
    val cues: List<SubtitleCue> = cues.filter { it.endMs > it.startMs && it.text.isNotBlank() }.sortedBy { it.startMs }

    /** maxEnd[i] = cel mai mare sfârșit dintre replicile 0..i (pentru replici care se suprapun). */
    private val maxEnd: LongArray =
        LongArray(this.cues.size).also { arr ->
            var max = Long.MIN_VALUE
            this.cues.forEachIndexed { i, cue ->
                max = maxOf(max, cue.endMs)
                arr[i] = max
            }
        }

    val isEmpty: Boolean get() = cues.isEmpty()

    /** Textul de afișat la [positionMs] (replicile active, în ordine), sau șir gol. */
    fun textAt(positionMs: Long): String {
        var i = lastStartingAtOrBefore(positionMs)
        if (i < 0) return ""
        val active = ArrayList<String>(2)
        while (i >= 0 && maxEnd[i] > positionMs) {
            val cue = cues[i]
            if (cue.endMs > positionMs) active += cue.text
            i--
        }
        return active.asReversed().joinToString("\n")
    }

    private fun lastStartingAtOrBefore(positionMs: Long): Int {
        var lo = 0
        var hi = cues.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }
}
