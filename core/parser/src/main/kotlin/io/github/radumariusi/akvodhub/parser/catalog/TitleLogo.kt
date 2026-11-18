package io.github.radumariusi.akvodhub.parser.catalog

data class LogoCandidate(val path: String, val language: String?, val votes: Int)

object TitleLogo {
    private val supportedPath = Regex("/[a-zA-Z0-9_-]+[.](png|svg|webp|jpe?g)", RegexOption.IGNORE_CASE)

    /** Până la două variante pe limbă, în ordinea voturilor. O siglă engleză are prioritate. */
    fun orderedPaths(candidates: List<LogoCandidate>): List<String> =
        listOf("en", null)
            .flatMap { language ->
                candidates
                    .asSequence()
                    .filter {
                        it.language
                            ?.trim()
                            ?.lowercase()
                            ?.ifEmpty { null } == language
                    }.filter { supportedPath.matches(it.path) }
                    .sortedByDescending { it.votes }
                    .map { it.path }
                    .distinct()
                    .take(2)
                    .toList()
            }.distinct()

    fun cachedPaths(data: String): List<String> =
        data
            .lineSequence()
            .filter(supportedPath::matches)
            .distinct()
            .take(4)
            .toList()

    fun isCached(fetchedAt: Long, now: Long, hasLogos: Boolean): Boolean {
        val days = if (hasLogos) 30 else 1
        return now - fetchedAt in 0 until days * 86_400_000L
    }
}
