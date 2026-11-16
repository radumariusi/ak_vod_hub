package io.github.radumariusi.akvodhub.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.radumariusi.akvodhub.parser.VodKind
import io.github.radumariusi.akvodhub.parser.match.MediaType
import io.github.radumariusi.akvodhub.parser.title.HdrFormat
import io.github.radumariusi.akvodhub.parser.title.TitleParser
import io.github.radumariusi.akvodhub.parser.title.VideoCodec
import io.github.radumariusi.akvodhub.parser.title.VideoQuality

enum class PlaylistType {
    M3U_URL,
    M3U_FILE,
    XTREAM,
}

/**
 * Un playlist adăugat de utilizator. [location], [username] și [password] sunt criptate
 * (vezi `SecretCipher`), pentru că link-urile M3U conțin de obicei userul și parola.
 */
@Entity(tableName = "playlist_source")
data class PlaylistSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    /** Ce afișăm în listă (serverul sau „Fișier local”), fără user/parolă. */
    val label: String,
    val location: String,
    val username: String? = null,
    val password: String? = null,
    val createdAt: Long,
    val lastImportAt: Long? = null,
    val lastImportError: String? = null,
    val lastAdded: Int = 0,
    val lastRemoved: Int = 0,
    val lastDurationMs: Long = 0,
)

/**
 * O intrare VOD din playlist (film, serial sau episod), exact cum vine de la furnizor.
 * La import se parsează numele ([parsed]: titlu curat, an, calitate, limbă), apoi potrivirea TMDB o leagă
 * de un [TitleEntity] ([titleId]).
 *
 * [providerKey] identifică intrarea la reîmprospătare (ID-ul Xtream sau URL-ul M3U), ca să știm
 * ce s-a adăugat și ce a dispărut. [url] e null la Xtream: linkul se construiește la redare din cont.
 */
@Entity(
    tableName = "raw_entry",
    indices = [
        Index(value = ["sourceId", "providerKey"], unique = true),
        Index(value = ["sourceId", "seriesId"]),
        Index(value = ["titleId"]),
        Index(value = ["matchState"]),
    ],
)
data class RawEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val providerKey: String,
    val kind: VodKind,
    val name: String,
    val groupTitle: String?,
    val url: String?,
    val logo: String?,
    val streamId: Long? = null,
    val seriesId: Long? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val containerExt: String? = null,
    val tmdbId: Long? = null,
    val providerAddedAt: Long? = null,
    val firstSeenAt: Long,
    val seenRun: Long,
    /** Titlul curat scos de parser din [name] (ex. „The Matrix” din „EN| The Matrix (1999) 4K”). */
    val title: String? = null,
    val year: Int? = null,
    val quality: VideoQuality? = null,
    val hdr: HdrFormat? = null,
    val codec: VideoCodec? = null,
    /** Limbile audio, coduri ISO 639-1 separate prin virgulă (ex. `en,de`; `mul` = mai multe). */
    val languages: String? = null,
    /** Limbile subtitrărilor declarate în nume, la fel ca [languages]. */
    val subtitles: String? = null,
    /** Titlul TMDB ([TitleEntity]) de care aparține intrarea; toate sursele aceluiași film au același [titleId]. */
    val titleId: Long? = null,
    /** Starea potrivirii TMDB, vezi [MatchState]. */
    val matchState: Int = MatchState.PENDING,
) {
    /** Furnizorul a trimis exact aceeași intrare ca data trecută (deci nu trebuie rescrisă sau re-parsată). */
    fun sameFromProvider(other: RawEntryEntity): Boolean =
        name == other.name &&
            groupTitle == other.groupTitle &&
            url == other.url &&
            logo == other.logo &&
            kind == other.kind &&
            streamId == other.streamId &&
            seriesId == other.seriesId &&
            containerExt == other.containerExt &&
            tmdbId == other.tmdbId &&
            providerAddedAt == other.providerAddedAt

    /** Aceeași intrare, cu câmpurile parsate din nume și categorie. Sezonul/episodul dat de furnizor are prioritate. */
    fun parsed(): RawEntryEntity {
        val p = TitleParser.parse(name, groupTitle)
        return copy(
            title = p.title,
            year = p.year,
            season = season ?: p.season,
            episode = episode ?: p.episode,
            quality = p.quality,
            hdr = p.hdr,
            codec = p.codec,
            languages = p.languages.joinToString(",").ifEmpty { null },
            subtitles = p.subtitles.joinToString(",").ifEmpty { null },
        )
    }
}

/** Stările potrivirii TMDB a unei intrări din playlist. */
object MatchState {
    /** De potrivit (nouă sau cu numele schimbat). */
    const val PENDING = 0
    const val MATCHED = 1

    /** Căutată pe TMDB, dar fără un rezultat destul de sigur. */
    const val UNMATCHED = 2

    /** Episod Xtream: ia titlul serialului din care face parte. */
    const val INHERITED = 3

    /** De căutat după nume: ID-ul TMDB dat de furnizor s-a dovedit greșit. */
    const val PENDING_SEARCH = 4
}

/**
 * Un film sau un serial TMDB: o singură fișă pentru toate sursele din playlist-uri.
 * [detailsFetched] = false înseamnă că avem doar ID-ul (de la furnizor) sau date în altă limbă; detaliile
 * se completează în fundal.
 */
@Entity(
    tableName = "title",
    indices = [Index(value = ["mediaType", "tmdbId"], unique = true)],
)
data class TitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: MediaType,
    val tmdbId: Long,
    val name: String,
    val originalName: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val rating: Double? = null,
    val voteCount: Int? = null,
    val popularity: Double? = null,
    /** ID-uri de gen TMDB, separate prin virgulă. */
    val genreIds: String? = null,
    val originalLanguage: String? = null,
    val imdbId: String? = null,
    val detailsFetched: Boolean = false,
    val updatedAt: Long,
)

/**
 * Cache-ul căutărilor TMDB: cheia e titlul normalizat + an + tip + limbă (`MatchQuery.key`), deci
 * duplicatele din playlist (și din alte playlist-uri) costă o singură cerere. [tmdbId] null = negăsit.
 */
@Entity(tableName = "match_cache")
data class MatchCacheEntity(
    @PrimaryKey val queryKey: String,
    val tmdbId: Long?,
    val score: Double,
    val checkedAt: Long,
)

/** Rezultat de agregare: câte intrări de un anumit tip are un playlist. */
data class KindCount(
    val sourceId: Long,
    val kind: VodKind,
    val count: Int,
)
