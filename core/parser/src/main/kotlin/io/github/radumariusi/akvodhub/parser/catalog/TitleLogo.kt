package io.github.radumariusi.akvodhub.parser.catalog

data class LogoCandidate(val path: String, val language: String?, val votes: Int)

object TitleLogo {
    /** RO, EN, fără text localizat, apoi limba originală. Nu alegem o traducere arbitrară. */
    fun choose(candidates: List<LogoCandidate>, originalLanguage: String?): String? {
        val languages = listOf("ro", "en", null, originalLanguage).distinct()
        return languages.firstNotNullOfOrNull { language ->
            candidates
                .filter { it.language == language && it.path.startsWith("/") }
                .maxByOrNull { it.votes }
                ?.path
        }
    }
}
