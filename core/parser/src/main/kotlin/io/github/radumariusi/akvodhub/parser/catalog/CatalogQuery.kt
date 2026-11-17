package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import java.text.Normalizer

enum class CatalogSort(
    val label: String,
) {
    POPULAR("Populare"),
    RECENT("Adăugate recent"),
    RATING("Cele mai apreciate"),
    TITLE("Alfabetic"),
}

enum class ProductionFilter(val label: String) {
    ALL("Toate"),
    US("Producții SUA"),
    ENGLISH("Limba originală engleză"),
}

/** Interval de ani (ex. un deceniu). Capetele null = fără limită. */
data class YearRange(
    val label: String,
    val from: Int?,
    val to: Int?,
) {
    companion object {
        val all =
            listOf(
                YearRange("2020+", 2020, null),
                YearRange("2010–2019", 2010, 2019),
                YearRange("2000–2009", 2000, 2009),
                YearRange("1990–1999", 1990, 1999),
                YearRange("1980–1989", 1980, 1989),
                YearRange("Înainte de 1980", null, 1979),
            )
    }
}

/**
 * Ce listă de titluri cerem: tip, gen, ani, calitate, limbă, text căutat și ordinea.
 * Toate filtrele sunt opționale; [text] gol = fără căutare.
 */
data class CatalogFilter(
    val type: MediaType? = null,
    val genre: Genre? = null,
    val years: YearRange? = null,
    /** Doar titlurile care au cel puțin o sursă în această calitate. */
    val quality: VideoQuality? = null,
    /** Doar titlurile cu cel puțin o sursă în această limbă audio (ISO 639-1 sau `mul`). */
    val language: String? = null,
    val sort: CatalogSort = CatalogSort.POPULAR,
    val text: String = "",
    val originalAudioOnly: Boolean = false,
    val production: ProductionFilter = ProductionFilter.ALL,
) {
    val hasFilters: Boolean get() =
        genre != null || years != null || quality != null || language != null ||
            originalAudioOnly || production != ProductionFilter.ALL
}

/** O interogare SQL cu argumentele ei, gata de trimis la Room (`SimpleSQLiteQuery`). */
data class SqlQuery(
    val sql: String,
    val args: List<Any>,
)

/**
 * Construiește interogările pentru listele din hub (rânduri pe Acasă, grilele Filme / Seriale, Căutare).
 *
 * Coloanele folosite din `title` sunt rezumate precalculate de indexarea catalogului (`sourceCount`, `addedAt`,
 * `bestQuality`, `qualities`, `audioLanguages`), deci nu e nevoie de JOIN cu cele ~100.000 de intrări din
 * playlist. Ordinea „populare”, „recente”, „apreciate” și „alfabetic” folosește indexuri, iar paginarea e
 * cu LIMIT/OFFSET. Căutarea folosește tabelul FTS `title_search` (titlu, titlu original, nume din playlist).
 */
object CatalogQuery {
    const val COLUMNS =
        "t.id, t.mediaType, t.name, t.year, t.posterPath, t.backdropPath, t.rating, t.bestQuality, t.overview, t.dubLabel, t.imdbRating, t.imdbVotes"

    /** Sub acest număr de voturi, nota TMDB nu e relevantă pentru „Cele mai apreciate”. */
    const val MIN_VOTES_FOR_RATING = 100

    fun page(
        filter: CatalogFilter,
        limit: Int,
        offset: Int,
    ): SqlQuery {
        val where = mutableListOf("t.sourceCount > 0")
        val args = mutableListOf<Any>()
        filter.type?.let {
            where += "t.mediaType = ?"
            args += it.name
        }
        filter.genre?.let { genre ->
            where += genre.tmdbIds.sorted().joinToString(" OR ", "(", ")") { "(',' || t.genreIds || ',') LIKE ?" }
            genre.tmdbIds.sorted().forEach { args += "%,$it,%" }
        }
        filter.years?.from?.let {
            where += "t.year >= ?"
            args += it
        }
        filter.years?.to?.let {
            where += "t.year <= ?"
            args += it
        }
        filter.quality?.let {
            where += "t.qualities LIKE ?"
            args += "%,${it.name},%"
        }
        filter.language?.let {
            where += "t.audioLanguages LIKE ?"
            args += "%,${it.lowercase()},%"
        }
        if (filter.production == ProductionFilter.US) {
            where += "t.originCountries LIKE ?"
            args += "%,US,%"
        } else if (filter.production == ProductionFilter.ENGLISH) {
            where += "t.originalLanguage = ?"
            args += "en"
        }
        if (filter.originalAudioOnly) where += "t.hasOriginalAudio = 1"
        val fts = ftsQuery(filter.text)
        if (fts != null) {
            where += "t.id IN (SELECT docid FROM title_search WHERE title_search MATCH ?)"
            args += fts
        }
        if (filter.sort == CatalogSort.RATING) {
            where += "t.voteCount >= ?"
            args += MIN_VOTES_FOR_RATING
        }
        val order =
            when {
                // La căutare: întâi titlurile care încep exact cu textul, apoi cele populare.
                fts != null && filter.sort == CatalogSort.POPULAR -> {
                    args += "${filter.text.trim()}%"
                    "(t.name LIKE ?) DESC, t.popularity DESC, t.id DESC"
                }

                filter.sort == CatalogSort.POPULAR -> {
                    "t.popularity DESC, t.id DESC"
                }

                filter.sort == CatalogSort.RECENT -> {
                    "t.addedAt DESC, t.id DESC"
                }

                filter.sort == CatalogSort.RATING -> {
                    "t.rating DESC, t.id DESC"
                }

                else -> {
                    "t.name, t.id"
                }
            }
        args += limit
        args += offset
        return SqlQuery(
            "SELECT $COLUMNS FROM title t WHERE ${where.joinToString(" AND ")} ORDER BY $order LIMIT ? OFFSET ?",
            args,
        )
    }

    private val marks = Regex("""\p{M}+""")
    private val separators = Regex("""[^\p{L}\p{N}]+""")

    /**
     * Textul scris de utilizator transformat într-o căutare FTS sigură: fiecare cuvânt devine prefix
     * (`matr* reload*`), fără diacritice și fără operatori FTS. null = nimic de căutat.
     */
    fun ftsQuery(text: String): String? {
        val plain = marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()
        val words = plain.split(separators).filter { it.isNotEmpty() }.take(MAX_WORDS)
        if (words.isEmpty()) return null
        return words.joinToString(" ") { "$it*" }
    }

    private const val MAX_WORDS = 8
}
