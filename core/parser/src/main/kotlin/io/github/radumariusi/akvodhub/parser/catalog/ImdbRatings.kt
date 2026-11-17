package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.stremio.StremioIds
import java.io.BufferedReader

data class ImdbRating(val imdbId: String, val rating: Double, val votes: Int)

/** TSV oficial, citit leneș: cel mult o linie în memorie aici; loturile sunt decise de apelant. */
object ImdbRatings {
    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private const val HEADER = "tconst\taverageRating\tnumVotes"

    fun isDue(lastUpdated: Long?, now: Long): Boolean =
        lastUpdated == null || now < lastUpdated || now - lastUpdated >= WEEK_MS

    fun read(reader: BufferedReader): Sequence<ImdbRating> =
        sequence {
            require(reader.readLine()?.removePrefix("\uFEFF") == HEADER) { "Antet IMDb invalid." }
            while (true) {
                val line = reader.readLine() ?: break
                parse(line)?.let { yield(it) }
            }
        }

    private fun parse(line: String): ImdbRating? {
        val columns = line.split('\t', limit = 4)
        if (columns.size != 3 || !StremioIds.isImdb(columns[0])) return null
        val rating = columns[1].toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..10.0 } ?: return null
        val votes = columns[2].toIntOrNull()?.takeIf { it > 0 } ?: return null
        return ImdbRating(columns[0], rating, votes)
    }
}
