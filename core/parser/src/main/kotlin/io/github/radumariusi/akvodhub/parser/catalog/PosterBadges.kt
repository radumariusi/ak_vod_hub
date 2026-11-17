package io.github.radumariusi.akvodhub.parser.catalog

import java.util.Locale

enum class PosterBadge(val label: String) {
    TMDB("Nota TMDB"),
    IMDB("Nota IMDb"),
    QUALITY("4K / HDR"),
    DUB("Dublaj (DUB)"),
    PROGRESS("Progres vizionare"),
    NEW("Nou în playlist"),
}

object PosterBadges {
    val defaults = PosterBadge.entries.toSet()
    const val NEW_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    private val romanian = Locale.forLanguageTag("ro")

    fun decode(value: String?): Set<PosterBadge> =
        if (value ==
            null
        ) {
            defaults
        } else {
            value.split(',').mapNotNull { name -> PosterBadge.entries.firstOrNull { it.name == name } }.toSet()
        }

    fun encode(values: Set<PosterBadge>): String =
        PosterBadge.entries.filter { it in values }.joinToString(
            ",",
        ) { it.name }

    fun rating(prefix: String, value: Double?): String? =
        value?.takeIf { it.isFinite() && it > 0 && it <= 10 }?.let { "$prefix " + String.format(romanian, "%.1f", it) }

    fun hdrRank(value: String?): Int =
        when (value) {
            "HDR" -> 1
            "HDR10" -> 2
            "HDR10_PLUS" -> 3
            "DOLBY_VISION" -> 4
            else -> 0
        }

    fun hdrLabel(rank: Int): String? = listOf(null, "HDR", "HDR10", "HDR10+", "DV").getOrNull(rank)

    fun isNew(addedAt: Long?, now: Long): Boolean = addedAt != null && addedAt <= now && now - addedAt < NEW_WINDOW_MS

    fun progress(position: Long, duration: Long): Double =
        if (duration <= 0) 0.0 else (position.toDouble() / duration).coerceIn(0.0, 1.0)
}
