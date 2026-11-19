package io.github.radumariusi.akvodhub.parser.player

/**
 * Cum a mers o sursă ultima dată: când a pornit ultima oară ([okAt]) și câte eșecuri la rând are
 * ([failures], ultimul la [failedAt]). 0 = niciodată.
 */
data class SourceHealthRecord(
    val okAt: Long = 0,
    val failures: Int = 0,
    val failedAt: Long = 0,
)

/**
 * Sănătatea surselor, ținută minte între redări:
 * - sursa care a pornit ultima dată pentru un titlu se încearcă prima;
 * - o sursă cu [DEAD_FAILURES] eșecuri la rând coboară la final (și primește „⚠” în liste) timp de
 *   [FORGET_MS]; după aceea primește o nouă șansă.
 */
object SourceHealth {
    const val DEAD_FAILURES = 2
    const val FORGET_MS = 7L * 24 * 60 * 60 * 1000
    const val MAX_RECORDS = 400

    fun isDead(record: SourceHealthRecord?, now: Long): Boolean =
        record != null && record.failures >= DEAD_FAILURES && now - record.failedAt in 0 until FORGET_MS

    fun started(record: SourceHealthRecord?, now: Long): SourceHealthRecord = SourceHealthRecord(okAt = now)

    fun failed(record: SourceHealthRecord?, now: Long): SourceHealthRecord =
        (record ?: SourceHealthRecord()).copy(failures = (record?.failures ?: 0) + 1, failedAt = now)

    /** [ordered] = ordinea profilului. Ultima sursă pornită vine prima, sursele „moarte” la final. */
    fun reorder(
        ordered: List<Long>,
        health: Map<Long, SourceHealthRecord>,
        now: Long,
    ): List<Long> {
        val (dead, alive) = ordered.partition { isDead(health[it], now) }
        val last = alive.filter { (health[it]?.okAt ?: 0) > 0 }.maxByOrNull { health.getValue(it).okAt }
        return if (last == null) alive + dead else listOf(last) + alive.filter { it != last } + dead
    }

    /** Forma salvată: un rând `id:okAt:failures:failedAt` pentru fiecare sursă, cele mai recente primele. */
    fun encode(health: Map<Long, SourceHealthRecord>): String =
        health.entries
            .sortedByDescending { maxOf(it.value.okAt, it.value.failedAt) }
            .take(MAX_RECORDS)
            .joinToString("\n") { (id, r) -> "$id:${r.okAt}:${r.failures}:${r.failedAt}" }

    fun decode(value: String?): Map<Long, SourceHealthRecord> =
        value
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split(':')
                if (parts.size != 4) return@mapNotNull null
                val id = parts[0].toLongOrNull() ?: return@mapNotNull null
                val record =
                    SourceHealthRecord(
                        okAt = parts[1].toLongOrNull() ?: return@mapNotNull null,
                        failures = parts[2].toIntOrNull() ?: return@mapNotNull null,
                        failedAt = parts[3].toLongOrNull() ?: return@mapNotNull null,
                    )
                id to record
            }.toMap()
}
