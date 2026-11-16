package io.github.radumariusi.akvodhub.parser.catalog

/**
 * Un gen afișat în aplicație. TMDB are liste separate pentru filme și seriale (ex. „Action” = 28 la filme,
 * „Action & Adventure” = 10759 la seriale), deci un gen din aplicație poate acoperi mai multe ID-uri.
 */
data class Genre(
    val key: String,
    val label: String,
    val tmdbIds: Set<Int>,
)

object Genres {
    val all: List<Genre> =
        listOf(
            Genre("action", "Acțiune", setOf(28, 10759)),
            Genre("adventure", "Aventură", setOf(12, 10759)),
            Genre("animation", "Animație", setOf(16)),
            Genre("comedy", "Comedie", setOf(35)),
            Genre("crime", "Crimă", setOf(80)),
            Genre("documentary", "Documentar", setOf(99)),
            Genre("drama", "Dramă", setOf(18)),
            Genre("family", "Familie", setOf(10751)),
            Genre("kids", "Copii", setOf(10762)),
            Genre("fantasy", "Fantezie", setOf(14, 10765)),
            Genre("scifi", "SF", setOf(878, 10765)),
            Genre("horror", "Horror", setOf(27)),
            Genre("thriller", "Thriller", setOf(53)),
            Genre("mystery", "Mister", setOf(9648)),
            Genre("romance", "Romantic", setOf(10749)),
            Genre("history", "Istoric", setOf(36)),
            Genre("war", "Război", setOf(10752, 10768)),
            Genre("western", "Western", setOf(37)),
            Genre("music", "Muzică", setOf(10402)),
            Genre("reality", "Reality", setOf(10764)),
        )

    /** Genurile cu rând propriu pe ecranul Acasă, în ordinea afișării. */
    val home: List<Genre> =
        listOf(
            "action",
            "comedy",
            "drama",
            "thriller",
            "scifi",
            "animation",
            "horror",
            "crime",
            "romance",
            "documentary",
            "family",
        ).map { key -> all.first { it.key == key } }

    private val names: Map<Int, String> =
        mapOf(
            28 to "Acțiune",
            12 to "Aventură",
            16 to "Animație",
            35 to "Comedie",
            80 to "Crimă",
            99 to "Documentar",
            18 to "Dramă",
            10751 to "Familie",
            14 to "Fantezie",
            36 to "Istoric",
            27 to "Horror",
            10402 to "Muzică",
            9648 to "Mister",
            10749 to "Romantic",
            878 to "SF",
            10770 to "Film TV",
            53 to "Thriller",
            10752 to "Război",
            37 to "Western",
            10759 to "Acțiune și aventură",
            10762 to "Copii",
            10763 to "Știri",
            10764 to "Reality",
            10765 to "SF și fantezie",
            10766 to "Telenovelă",
            10767 to "Talk-show",
            10768 to "Război și politică",
        )

    fun byKey(key: String?): Genre? = all.firstOrNull { it.key == key }

    /** Numele genurilor dintr-o listă TMDB de forma `28,12`, ex. „Acțiune, Aventură”. */
    fun names(genreIds: String?): List<String> =
        genreIds
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull()?.let(names::get) }
            .orEmpty()
}
