package io.github.radumariusi.akvodhub.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.radumariusi.akvodhub.parser.VodKind

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
 * Etapa 3 o parsează (titlu, an, calitate, limbă), etapa 4 o leagă de TMDB.
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
)

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
