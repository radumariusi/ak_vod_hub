package io.github.radumariusi.akvodhub.parser.catalog

data class TrailerCandidate(
    val key: String,
    val site: String,
    val type: String,
    val official: Boolean,
    val language: String?,
)

object Trailer {
    private val youtubeId = Regex("[A-Za-z0-9_-]{11}")

    fun isValidId(value: String): Boolean = youtubeId.matches(value)

    /** Numai trailere YouTube; nu alegem teaser, clip sau URL arbitrar din metadate. */
    fun choose(videos: List<TrailerCandidate>, originalLanguage: String?): String? {
        val languages = listOf("ro", "en", originalLanguage, null).distinct()
        return videos
            .filter { it.site == "YouTube" && it.type == "Trailer" && isValidId(it.key) }
            .sortedWith(
                compareBy<TrailerCandidate> { languages.indexOf(it.language).let { i -> if (i < 0) 99 else i } }
                    .thenByDescending { it.official },
            ).firstOrNull()
            ?.key
    }
}
