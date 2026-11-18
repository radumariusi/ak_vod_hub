package io.github.radumariusi.akvodhub.parser.catalog

data class LogoCandidate(val path: String, val language: String?, val votes: Int)

object TitleLogo {
    /** Preferă sigla engleză, apoi varianta neutră. */
    fun choose(candidates: List<LogoCandidate>): String? {
        val languages = listOf("en", null)
        return languages.firstNotNullOfOrNull { language ->
            candidates
                .filter { it.language == language && it.path.startsWith("/") }
                .maxByOrNull { it.votes }
                ?.path
        }
    }
}
