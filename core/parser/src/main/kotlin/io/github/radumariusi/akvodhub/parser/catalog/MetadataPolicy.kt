package io.github.radumariusi.akvodhub.parser.catalog

import java.util.Locale

data class CertificationEntry(
    val country: String,
    val value: String,
    val releaseType: Int? = null,
)

/** Selectarea deterministă a metadatelor opționale, fără dependențe Android sau TMDB. */
object MetadataPolicy {
    private val countries = Locale.getISOCountries().toSet()

    fun originCountries(codes: List<String>): List<String> =
        codes.map { it.trim().uppercase(Locale.ROOT) }.filter { it in countries }.distinct()

    fun runtime(movieMinutes: Int?, episodeMinutes: List<Int>): Int? =
        movieMinutes?.takeIf { it > 0 } ?: episodeMinutes.firstOrNull { it > 0 }

    /** RO, apoi US, apoi țările de origine. Prefixul țării evită confundarea sistemelor de vârstă. */
    fun certification(entries: List<CertificationEntry>, origins: List<String>): String? {
        val preferred = (listOf("RO", "US") + originCountries(origins)).distinct()
        val selected =
            entries
                .map { it.copy(country = it.country.trim().uppercase(Locale.ROOT), value = it.value.trim()) }
                .filter { it.country in countries && it.value.isNotEmpty() }
                .sortedWith(
                    compareBy<CertificationEntry> {
                        preferred.indexOf(it.country).takeIf { rank -> rank >= 0 }
                            ?: preferred.size
                    }.thenBy { if (it.releaseType == 3) 0 else 1 }
                        .thenBy { it.country }
                        .thenBy { it.value },
                ).firstOrNull() ?: return null
        return "${selected.country}: ${selected.value}"
    }
}
