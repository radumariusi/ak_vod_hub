package io.github.radumariusi.akvodhub.data.playlist.xtream

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
)

data class XtreamSeries(
    val seriesId: Long,
    val name: String,
    val categoryId: String?,
    val cover: String?,
    val tmdbId: Long?,
    val lastModified: Long?,
)

data class XtreamEpisode(
    val id: Long,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val containerExtension: String?,
    val added: Long?,
)

class XtreamException(
    message: String,
) : Exception(message)
