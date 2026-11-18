package io.github.radumariusi.akvodhub.parser.catalog

import java.security.MessageDigest

object HomeRows {
    const val CONTINUE = "continue"
    const val EPISODES = "new-episodes"
    const val WATCHLIST = "watchlist"
    const val RECOMMENDATIONS = "d:for-you"
    const val NEWS = "news"
    const val COLLECTIONS = "d:collections"
    const val BECAUSE = "d:because"
    const val ADDONS = "d:catalog"

    val labels =
        linkedMapOf(
            CONTINUE to "Continuă vizionarea",
            EPISODES to "Episoade noi",
            WATCHLIST to "Lista mea",
            RECOMMENDATIONS to "Recomandări",
            NEWS to "Nou în playlist",
            "movies" to "Filme populare",
            "series" to "Seriale populare",
            "rated" to "Cele mai apreciate",
        ) + Genres.home.associate { "genre:${it.key}" to it.label } +
            mapOf(
                COLLECTIONS to "Continuă colecția",
                because(0) to "Pentru că ai văzut · primul rând",
                because(1) to "Pentru că ai văzut · al doilea rând",
                ADDONS to "Cataloage din addon-uri",
            )

    fun because(index: Int): String = "$BECAUSE:${index + 1}"

    fun catalog(manifest: String, type: String, id: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = listOf(manifest, type, id).joinToString("\u0000")
        val hash = digest.digest(key.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) }
        return "$ADDONS:$hash"
    }

    fun group(key: String): String = if (catalogKey.matches(key)) ADDONS else key

    fun order(value: String, availableCatalogs: Set<String> = emptySet()): List<String> {
        val explicit = keys(value)
        return (explicit + labels.keys + availableCatalogs).distinct()
    }

    fun keys(value: String): List<String> =
        value
            .split(',')
            .filter { it in labels || catalogKey.matches(it) }
            .distinct()
            .take(80)

    private val catalogKey = Regex("""d:catalog:[0-9a-f]{24}""")
}
