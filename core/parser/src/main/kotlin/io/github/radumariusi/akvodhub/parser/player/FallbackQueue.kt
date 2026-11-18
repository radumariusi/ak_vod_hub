package io.github.radumariusi.akvodhub.parser.player

/** O încercare eșuată: sursa și motivul (ex. „HTTP 404”, „nu răspunde”). */
data class FailedAttempt(
    val entryId: Long,
    val reason: String,
)

/**
 * Lista de surse a unei redări, în ordinea de preferință. Dacă sursa curentă nu merge, [fail] trece la
 * următoarea sursă neîncercată (după cea curentă, apoi de la începutul listei); null = s-au terminat.
 */
class FallbackQueue(
    candidates: List<Long>,
    start: Long? = null,
) {
    /** Sursele în ordinea încercării: [start] (dacă e ales de utilizator) prima, apoi restul. */
    val candidates: List<Long> =
        if (start != null) (listOf(start) + candidates).distinct() else candidates.distinct()

    private val failedIds = LinkedHashMap<Long, String>()

    var current: Long? = this.candidates.firstOrNull()
        private set

    val failures: List<FailedAttempt> get() = failedIds.map { (id, reason) -> FailedAttempt(id, reason) }

    /** Poziția sursei curente (de la 1) din [size]. */
    val position: Int get() = current?.let { candidates.indexOf(it) + 1 } ?: 0
    val size: Int get() = candidates.size

    val hasMore: Boolean get() = nextUntried() != null

    /** Sursa curentă nu merge: o marchează și întoarce următoarea de încercat (sau null). */
    fun fail(reason: String, preferred: Long? = null): Long? {
        current?.let { failedIds[it] = reason }
        current = preferred?.takeIf { it in candidates && it !in failedIds } ?: nextUntried()
        return current
    }

    /** Utilizatorul alege altă sursă din listă (poate fi și una eșuată: o reîncercăm). */
    fun switchTo(entryId: Long): Boolean {
        if (entryId !in candidates) return false
        failedIds.remove(entryId)
        current = entryId
        return true
    }

    /** Toate au eșuat; „Încearcă din nou” pornește lista de la capăt. */
    fun reset(): Long? {
        failedIds.clear()
        current = candidates.firstOrNull()
        return current
    }

    fun reasonFor(entryId: Long): String? = failedIds[entryId]

    private fun nextUntried(): Long? {
        val from = current?.let { candidates.indexOf(it) } ?: -1
        val after = candidates.drop(from + 1).firstOrNull { it !in failedIds && it != current }
        return after ?: candidates.firstOrNull { it !in failedIds && it != current }
    }
}
