package io.github.radumariusi.akvodhub.data.backup

import io.github.radumariusi.akvodhub.data.catalog.CatalogRepository
import io.github.radumariusi.akvodhub.data.player.PlaybackSettings
import io.github.radumariusi.akvodhub.data.playlist.PlaylistBackup
import io.github.radumariusi.akvodhub.data.playlist.PlaylistRepository
import io.github.radumariusi.akvodhub.data.stremio.StremioAddon
import io.github.radumariusi.akvodhub.data.stremio.StremioAddons
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Fișierul de backup: setările aplicației, fără catalog (acela se reface din playlist-uri). */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdAt: Long = 0,
    val playlists: List<PlaylistBackup> = emptyList(),
    val tmdbKey: String? = null,
    val addons: List<StremioAddon> = emptyList(),
    val playback: Map<String, String> = emptyMap(),
) {
    companion object {
        const val FORMAT = "ak-vod-hub-backup"
    }
}

data class RestoreResult(
    val playlistsAdded: Int,
    val playlistsSkipped: Int,
    val tmdbKey: Boolean,
    val addons: Int,
) {
    fun summary(): String =
        buildList {
            val skipped = if (playlistsSkipped > 0) " ($playlistsSkipped existau deja)" else ""
            add("$playlistsAdded playlist-uri adăugate$skipped")
            if (tmdbKey) add("cheia TMDB")
            if (addons > 0) add("$addons addon-uri")
            add("preferințele de redare")
        }.joinToString(", ") + "."
}

/**
 * Backup / restaurare setări: playlist-uri (cu conturile Xtream), cheia TMDB, addon-urile Stremio și
 * preferințele de redare și subtitrări. Se descarcă și se încarcă de pe telefon (Configurare de pe telefon).
 */
class SettingsBackup(
    private val playlists: PlaylistRepository,
    private val catalog: CatalogRepository,
    private val addons: StremioAddons,
    private val playback: PlaybackSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun export(): String =
        json.encodeToString(
            BackupFile(
                createdAt = clock(),
                playlists = playlists.backup(),
                tmdbKey = catalog.tmdbKey(),
                addons = addons.addons.value,
                playback = playback.backup(),
            ),
        )

    /** Aruncă [IllegalArgumentException] dacă fișierul nu e un backup AK VOD Hub. */
    suspend fun restore(text: String): RestoreResult {
        val file =
            runCatching { json.decodeFromString<BackupFile>(text) }
                .getOrElse { throw IllegalArgumentException("Fișierul nu e un backup AK VOD Hub.") }
        require(file.format == BackupFile.FORMAT) { "Fișierul nu e un backup AK VOD Hub." }
        require(file.version <= 1) { "Backup făcut cu o versiune mai nouă a aplicației. Actualizează întâi." }
        var added = 0
        var skipped = 0
        for (p in file.playlists) {
            if (playlists.restore(p)) added++ else skipped++
        }
        val key = file.tmdbKey?.takeIf { it.isNotBlank() }
        key?.let(catalog::restoreTmdbKey)
        if (file.addons.isNotEmpty()) addons.replaceAll(file.addons)
        playback.restore(file.playback)
        return RestoreResult(added, skipped, key != null, file.addons.size)
    }

    private companion object {
        val json =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                encodeDefaults = true
            }
    }
}
