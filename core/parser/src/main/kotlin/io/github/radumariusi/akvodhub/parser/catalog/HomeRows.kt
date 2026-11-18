package io.github.radumariusi.akvodhub.parser.catalog

object HomeRows {
    const val CONTINUE = "continue"
    const val EPISODES = "new-episodes"
    const val WATCHLIST = "watchlist"
    const val RECOMMENDATIONS = "d:for-you"
    const val NEWS = "news"
    const val COLLECTIONS = "d:collections"
    const val BECAUSE = "d:because"
    const val ADDONS = "d:catalog"

    val labels = linkedMapOf(
        CONTINUE to "Continuă vizionarea",
        EPISODES to "Episoade noi",
        WATCHLIST to "Lista mea",
        RECOMMENDATIONS to "Recomandări",
        NEWS to "Nou în playlist",
        "movies" to "Filme populare",
        "series" to "Seriale populare",
        "rated" to "Cele mai apreciate",
    ) + Genres.home.associate { "genre:${it.key}" to it.label } + mapOf(
        COLLECTIONS to "Continuă colecția",
        BECAUSE to "Pentru că ai văzut",
        ADDONS to "Cataloage din addon-uri",
    )

    fun group(key: String): String =
        when {
            key.startsWith("$BECAUSE:") -> BECAUSE
            key.startsWith("$ADDONS:") -> ADDONS
            else -> key
        }

    fun order(value: String): List<String> = keys(value) + labels.keys.filterNot { it in keys(value) }

    fun keys(value: String): List<String> = value.split(',').filter { it in labels }.distinct()
}
