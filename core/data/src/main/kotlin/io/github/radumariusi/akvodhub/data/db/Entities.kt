package io.github.radumariusi.akvodhub.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.radumariusi.akvodhub.parser.VodKind
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
 * La import se parsează numele ([parsed]: titlu curat, an, calitate, limbă), iar etapa 4 o leagă de TMDB.
 *
 * [providerKey] identifică intrarea la reîmprospătare (ID-ul Xtream sau URL-ul M3U), ca să știm
 * ce s-a adăugat și ce a dispărut. [url] e null la Xtream: linkul se construiește la redare din cont.
 */
@Entity(
    tableName = "raw_entry",
    indices = [
        Index(value = ["sourceId", "providerKey"], unique = true),
        Index(value = ["sourceId", "seriesId"]),
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
) {
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

/** Rezultat de agregare: câte intrări de un anumit tip are un playlist. */
data class KindCount(
    val sourceId: Long,
    val kind: VodKind,
    val count: Int,
)

data class ExistingKey(
    val id: Long,
    val providerKey: String,
    val firstSeenAt: Long,
)
