package io.github.radumariusi.akvodhub.data.playlist.xtream

import io.github.radumariusi.akvodhub.parser.title.VideoQuality

/** Datele de conectare la un cont Xtream. [server] e normalizat (`http://host:port`, fără `/` la final). */
data class XtreamAccount(
    val server: String,
    val username: String,
    val password: String,
) {
    companion object {
        /** Acceptă „host:port”, „http://host:port/” sau un link complet (ex. `.../get.php?...`). */
        fun normalizeServer(input: String): String {
            var s = input.trim()
            if (!s.contains("://")) s = "http://$s"
            val scheme = s.substringBefore("://")
            val rest = s.substringAfter("://").substringBefore('/').substringBefore('?')
            return "$scheme://$rest"
        }
    }
}

data class XtreamMovie(
    val streamId: Long,
    val name: String,
    val categoryId: String?,
    val icon: String?,
    val containerExtension: String?,
    val tmdbId: Long?,
    val added: Long?,
    val quality: VideoQuality? = null,
)

data class XtreamSeries(
    val seriesId: Long,
    val name: String,
    val categoryId: String?,
    val cover: String?,
    val tmdbId: Long?,
    val lastModified: Long?,
    val quality: VideoQuality? = null,
)

data class XtreamEpisode(
    val id: Long,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val containerExtension: String?,
    val added: Long?,
    val quality: VideoQuality? = null,
    /** Limba pistei audio declarate de furnizor (`info.audio.tags.language`), ISO 639-1. */
    val language: String? = null,
)

class XtreamException(
    message: String,
) : Exception(message)
