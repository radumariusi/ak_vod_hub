package io.github.radumariusi.akvodhub.parser

/**
 * Tipul unei intrări VOD din playlist. Modulul `core:parser` e Kotlin pur (fără Android),
 * ca să poată fi testat rapid pe JVM. Parserul de nume vine în etapa 3.
 */
enum class VodKind {
    MOVIE,
    SERIES,
    EPISODE,
    ;

    companion object {
        /** Deduce tipul din URL-ul Xtream (`/movie/`, `/series/`). Întoarce null dacă nu e VOD. */
        fun fromUrl(url: String): VodKind? {
            val lower = url.lowercase()
            return when {
                "/movie/" in lower -> MOVIE
                "/series/" in lower -> EPISODE
                else -> null
            }
        }
    }
}
