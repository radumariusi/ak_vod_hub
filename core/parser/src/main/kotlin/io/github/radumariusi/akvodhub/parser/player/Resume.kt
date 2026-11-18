package io.github.radumariusi.akvodhub.parser.player

import io.github.radumariusi.akvodhub.parser.catalog.MergedEpisode
import io.github.radumariusi.akvodhub.parser.catalog.MergedSeason

/** Când reluăm de unde ai rămas și când un titlu e „văzut”. */
object Resume {
    /** Sub 30 de secunde nu merită reluat (și nici salvat). */
    const val MIN_POSITION_MS = 30_000L

    /** Văzut = a trecut de 94% sau au rămas sub 2 minute (genericul de final). */
    fun isFinished(
        positionMs: Long,
        durationMs: Long,
    ): Boolean {
        if (durationMs <= 0) return false
        return positionMs >= durationMs * 94 / 100 || (durationMs > 600_000L && durationMs - positionMs < 120_000L)
    }

    fun shouldResume(
        positionMs: Long,
        durationMs: Long,
    ): Boolean = positionMs >= MIN_POSITION_MS && !isFinished(positionMs, durationMs)

    /** Ex. „1:02:05” sau „12:34”. */
    fun format(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

/** Ordinea episoadelor unui serial (pentru „următorul episod”). */
object EpisodeOrder {
    /** Episoadele numerotate, în ordine; sezonul 0 (speciale) doar dacă e singurul. */
    fun linear(seasons: List<MergedSeason>): List<MergedEpisode> {
        val regular = seasons.filter { it.number > 0 }.ifEmpty { seasons }
        return regular.flatMap { s -> s.episodes.filter { it.number != null } }
    }

    fun find(
        seasons: List<MergedSeason>,
        season: Int?,
        episode: Int?,
    ): MergedEpisode? =
        seasons
            .firstOrNull { it.number == (season ?: 0) }
            ?.episodes
            ?.firstOrNull { it.number == episode }

    fun resumeOrNext(
        seasons: List<MergedSeason>,
        resumeSeason: Int?,
        resumeEpisode: Int?,
        lastSeason: Int?,
        lastEpisode: Int?,
        lastFinished: Boolean,
        watched: (MergedEpisode) -> Boolean,
    ): MergedEpisode? {
        if (resumeEpisode != null) find(seasons, resumeSeason, resumeEpisode)?.let { return it }
        if (lastEpisode != null && !lastFinished) {
            find(seasons, lastSeason, lastEpisode)?.takeUnless(watched)?.let { return it }
        }
        return linear(seasons).firstOrNull {
            val afterLast =
                lastSeason == null || lastEpisode == null || !lastFinished ||
                    it.season > lastSeason || (it.season == lastSeason && (it.number ?: 0) > lastEpisode)
            afterLast && !watched(it)
        }
    }

    /** Episodul de după [season]/[episode], sau null dacă e ultimul (sau nu are număr). */
    fun next(
        seasons: List<MergedSeason>,
        season: Int?,
        episode: Int?,
    ): MergedEpisode? {
        if (episode == null) return null
        val all = linear(seasons)
        val i = all.indexOfFirst { it.season == (season ?: 0) && it.number == episode }
        return if (i >= 0) all.getOrNull(i + 1) else null
    }
}
