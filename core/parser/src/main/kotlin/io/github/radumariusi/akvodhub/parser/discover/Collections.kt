package io.github.radumariusi.akvodhub.parser.discover

/** O parte dintr-o colecție TMDB (ex. trilogie), cu [titleId] != null dacă există în playlist. */
data class CollectionPart(
    val tmdbId: Long,
    val name: String,
    val year: Int?,
    val titleId: Long?,
) {
    val available: Boolean get() = titleId != null
}

/** Colecții / francize: ordinea părților, ce ai disponibil și ce urmează după ce ai văzut. */
object Collections {
    /** Părțile în ordinea apariției (cele fără an, la final). Fără anunțurile viitoare (an > [maxYear]). */
    fun ordered(
        parts: List<CollectionPart>,
        maxYear: Int = Int.MAX_VALUE,
    ): List<CollectionPart> =
        parts
            .filter { it.year == null || it.year <= maxYear }
            .distinctBy { it.tmdbId }
            .sortedWith(compareBy<CollectionPart> { it.year ?: Int.MAX_VALUE }.thenBy { it.tmdbId })

    /** Ex. „Ai 2 din 3 părți · lipsește: The Matrix Revolutions (2003)”; „Ai toate cele 3 părți”. */
    fun summary(parts: List<CollectionPart>): String {
        val have = parts.count { it.available }
        val missing = parts.filterNot { it.available }
        if (missing.isEmpty()) return if (parts.size == 1) "Ai singura parte" else "Ai toate cele ${parts.size} părți"
        val head = "Ai $have din ${parts.size} ${if (parts.size == 1) "parte" else "părți"}"
        val names = missing.take(MAX_MISSING).joinToString(", ") { p -> p.year?.let { "${p.name} ($it)" } ?: p.name }
        val more = if (missing.size > MAX_MISSING) ", +${missing.size - MAX_MISSING}" else ""
        val verb = if (missing.size == 1) "lipsește" else "lipsesc"
        return "$head · $verb: $names$more"
    }

    /**
     * Partea de văzut în continuare: prima parte disponibilă și nevăzută de după ultima parte văzută.
     * null dacă nu s-a văzut nimic din colecție sau nu mai e nimic disponibil după.
     */
    fun next(
        ordered: List<CollectionPart>,
        watched: Set<Long>,
    ): CollectionPart? {
        val last = ordered.indexOfLast { it.titleId != null && it.titleId in watched }
        if (last < 0) return null
        return ordered.drop(last + 1).firstOrNull { it.available && it.titleId !in watched }
    }

    private const val MAX_MISSING = 3
}
