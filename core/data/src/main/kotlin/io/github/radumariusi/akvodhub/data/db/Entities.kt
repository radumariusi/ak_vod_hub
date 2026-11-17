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
 *
 * Titlurile cu [tmdbId] negativ sunt „locale”: intrări din playlist încă nepotrivite (sau fără rezultat pe
 * TMDB), grupate după nume + an, ca să apară și ele în hub. Pentru ele [posterPath] e un URL complet (logo-ul
 * din playlist). Când potrivirea le găsește pe TMDB, sursele trec la titlul real, iar cel local se șterge.
 *
 * [sourceCount], [addedAt], [bestQuality], [qualities] și [audioLanguages] sunt rezumate ale surselor,
 * calculate de `CatalogIndexer`, ca listele din hub să nu facă JOIN cu toate intrările din playlist.
 */
@Entity(
    tableName = "title",
    indices = [
        Index(value = ["mediaType", "tmdbId"], unique = true),
        Index(value = ["popularity"]),
        Index(value = ["addedAt"]),
        Index(value = ["rating"]),
        Index(value = ["name"]),
        Index(value = ["imdbId"]),
    ],
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
    /** Câte intrări din playlist-uri (inclusiv episoade) are titlul; 0 = nu apare în hub. */
    val sourceCount: Int = 0,
    /** Cea mai recentă adăugare în playlist a unei surse (ms), pentru „Adăugate recent”. */
    val addedAt: Long? = null,
    /** Cea mai bună calitate a surselor (`VideoQuality.ordinal`). */
    val bestQuality: Int? = null,
    /** Calitățile surselor, cu virgule la capete pentru filtrare: `,UHD,FHD,`. */
    val qualities: String? = null,
    /** Limbile audio ale surselor, cu virgule la capete: `,en,de,ro,`. */
    val audioLanguages: String? = null,
    /** Etichetă calculată la indexare; null dacă audio original există sau limba e necunoscută. */
    val dubLabel: String? = null,
)

/** Titlu local (din playlist, fără potrivire TMDB). */
val TitleEntity.isLocal: Boolean get() = tmdbId < 0

/** Un titlu într-o listă din hub (rând pe Acasă, grilă, rezultat de căutare): doar ce trebuie pe card. */
data class TitleCard(
    val id: Long,
    val mediaType: MediaType,
    val name: String,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val rating: Double?,
    val bestQuality: Int?,
    val overview: String?,
    val dubLabel: String? = null,
)

/** Un titlu din „Continuă vizionarea”, cu locul unde a rămas utilizatorul. */
data class ContinueItem(
    val titleId: Long,
    val mediaType: MediaType,
    val name: String,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val season: Int?,
    val episode: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/**
 * Unde a rămas utilizatorul cu un titlu (pentru „Continuă vizionarea”). Scris de player (etapa 6);
 * la seriale, [season]/[episode] arată episodul curent.
 */
@Entity(tableName = "watch_state")
data class WatchStateEntity(
    @PrimaryKey val titleId: Long,
    val entryId: Long,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

/**
 * Istoricul de vizionare (pentru recomandări): rămâne și după ce filmul s-a terminat (spre deosebire de
 * [WatchStateEntity], care ține doar ce e de continuat). [progress] = cât s-a văzut (0–1, maximul atins).
 */
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val titleId: Long,
    val progress: Double,
    val finished: Boolean,
    val watchedAt: Long,
)

/**
 * Cache pentru descoperire: listele TMDB (recomandări, similare, colecții, filmografii) și cataloagele din
 * addon-uri, ca JSON, cu momentul descărcării. Cheia spune ce e (ex. `rel:MOVIE:603`, `cat:…`).
 */
@Entity(tableName = "discover_cache")
data class DiscoverCacheEntity(
    @PrimaryKey val cacheKey: String,
    val data: String,
    val fetchedAt: Long,
)

/**
 * Legătura IMDb → TMDB pentru titlurile din cataloagele addon-urilor (aflată o dată, prin `/find`).
 * [tmdbId] null = TMDB nu are titlul (se reverifică după o vreme).
 */
@Entity(tableName = "imdb_map")
data class ImdbMapEntity(
    @PrimaryKey val imdbId: String,
    val mediaType: MediaType?,
    val tmdbId: Long?,
    val checkedAt: Long,
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
