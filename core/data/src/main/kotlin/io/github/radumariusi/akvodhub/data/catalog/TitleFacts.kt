package io.github.radumariusi.akvodhub.data.catalog

import io.github.radumariusi.akvodhub.data.db.TitleEntity
import io.github.radumariusi.akvodhub.data.tmdb.TmdbTitleInfo
import java.util.Locale

/** Informațiile suplimentare de pe fișă (secțiunea „Informații”), în română. */
object TitleFacts {
    private val romanian = Locale.forLanguageTag("ro")
    private val months =
        listOf("ian", "feb", "mar", "apr", "mai", "iun", "iul", "aug", "sep", "oct", "nov", "dec")

    fun from(info: TmdbTitleInfo, title: TitleEntity): List<Pair<String, String>> {
        val facts = mutableListOf<Pair<String, String>>()

        fun add(label: String, value: String?) {
            value?.takeIf { it.isNotBlank() }?.let { facts += label to it }
        }
        val original = info.originalTitle ?: info.originalName
        if (original != null && !original.equals(title.name, ignoreCase = true)) add("Titlu original", original)
        add(
            if (info.firstAirDate !=
                null
            ) {
                "Prima difuzare"
            } else {
                "Premieră"
            },
            date(info.releaseDate ?: info.firstAirDate),
        )
        info.lastAirDate?.takeIf { info.firstAirDate != null }?.let { add("Ultima difuzare", date(it)) }
        add("Stare", status(info.status))
        if (info.seasons != null && info.episodes != null) {
            add("Sezoane", "${info.seasons} (${info.episodes} episoade)")
        }
        add(
            "Limbi",
            info.spokenLanguages
                .mapNotNull {
                    language(it.code) ?: it.englishName
                }.distinct()
                .take(5)
                .joinToString(", "),
        )
        add(
            "Țări",
            info.countries
                .mapNotNull { it.name }
                .take(4)
                .joinToString(", "),
        )
        add(
            "Rețea",
            info.networks
                .mapNotNull { it.name }
                .take(3)
                .joinToString(", "),
        )
        add(
            "Producție",
            info.companies
                .mapNotNull { it.name }
                .take(4)
                .joinToString(", "),
        )
        info.budget?.takeIf { it > 0 }?.let { add("Buget", money(it)) }
        info.revenue?.takeIf { it > 0 }?.let { add("Încasări", money(it)) }
        info.voteCount?.takeIf { it > 0 }?.let { add("Voturi TMDB", "%,d".format(Locale.US, it).replace(',', '.')) }
        return facts
    }

    /** „2026-03-14” → „14 mar 2026”. */
    fun date(value: String?): String? {
        val parts = value?.split('-') ?: return null
        val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val month = parts.getOrNull(1)?.toIntOrNull()?.let { months.getOrNull(it - 1) } ?: return "$year"
        val day = parts.getOrNull(2)?.toIntOrNull() ?: return "$month $year"
        return "$day $month $year"
    }

    private fun language(code: String?): String? {
        if (code.isNullOrBlank() || code == "xx") return null
        val name = Locale.forLanguageTag(code).getDisplayLanguage(romanian)
        return name.takeIf { it.isNotBlank() && it != code }?.replaceFirstChar { it.titlecase(romanian) }
    }

    private fun status(value: String?): String? =
        when (value) {
            "Released" -> "Lansat"
            "Returning Series" -> "În desfășurare"
            "Ended" -> "Încheiat"
            "Canceled" -> "Anulat"
            "In Production" -> "În producție"
            "Post Production" -> "Post-producție"
            "Planned" -> "Planificat"
            "Rumored" -> null
            else -> value
        }

    private fun money(value: Long): String =
        when {
            value >= 1_000_000 -> "%.1f mil. $".format(romanian, value / 1_000_000.0).replace(",0 ", " ")
            else -> "%,d $".format(Locale.US, value).replace(',', '.')
        }
}
