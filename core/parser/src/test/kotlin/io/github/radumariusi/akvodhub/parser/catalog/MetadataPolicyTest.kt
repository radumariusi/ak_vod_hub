package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataPolicyTest {
    @Test
    fun choosesRomanianCertificationBeforeUsAndSkipsEmptyValues() {
        val entries =
            listOf(
                CertificationEntry("US", "PG-13", 3),
                CertificationEntry("RO", " ", 3),
                CertificationEntry("RO", "15", 3),
            )
        assertEquals("RO: 15", MetadataPolicy.certification(entries, listOf("US")))
        assertEquals("US: PG-13", MetadataPolicy.certification(entries.take(2), listOf("US")))
    }

    @Test
    fun prefersTheatricalReleaseAndLabelsTheCountryOfFallback() {
        val entries = listOf(CertificationEntry("US", "R", 5), CertificationEntry("US", "PG-13", 3))
        assertEquals("US: PG-13", MetadataPolicy.certification(entries, emptyList()))
        assertEquals("DE: 12", MetadataPolicy.certification(listOf(CertificationEntry("de", " 12 ")), listOf("DE")))
        assertNull(MetadataPolicy.certification(listOf(CertificationEntry("XX", "18")), emptyList()))
        assertNull(MetadataPolicy.certification(emptyList(), listOf("US")))
    }

    @Test
    fun keepsOnlyKnownOriginCountriesAndNeverInfersUsFromEnglish() {
        assertEquals(listOf("US", "GB"), MetadataPolicy.originCountries(listOf(" us ", "GB", "US", "", "XX")))
        assertEquals(emptyList<String>(), MetadataPolicy.originCountries(listOf("en")))
    }

    @Test
    fun runtimeCanBeMissingAndTvUsesFirstPositiveEpisodeLength() {
        assertEquals(120, MetadataPolicy.runtime(120, listOf(45)))
        assertEquals(45, MetadataPolicy.runtime(null, listOf(0, -1, 45, 60)))
        assertEquals(30, MetadataPolicy.runtime(0, listOf(30)))
        assertNull(MetadataPolicy.runtime(null, emptyList()))
        assertNull(MetadataPolicy.runtime(-1, listOf(0)))
    }
}
