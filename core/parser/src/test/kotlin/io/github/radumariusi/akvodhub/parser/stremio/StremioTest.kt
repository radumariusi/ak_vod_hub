package io.github.radumariusi.akvodhub.parser.stremio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioTest {
    @Test
    fun normalizesManifestUrls() {
        val os = StremioIds.OPENSUBTITLES_MANIFEST
        assertEquals(os, StremioIds.normalizeManifestUrl("opensubtitles-v3.strem.io"))
        assertEquals(os, StremioIds.normalizeManifestUrl(" https://opensubtitles-v3.strem.io/ "))
        assertEquals(os, StremioIds.normalizeManifestUrl("stremio://opensubtitles-v3.strem.io/manifest.json"))
        assertEquals(
            "https://a.example.com/cfg%7B%7D/manifest.json",
            StremioIds.normalizeManifestUrl("https://a.example.com/cfg%7B%7D/manifest.json"),
        )
        assertNull(StremioIds.normalizeManifestUrl(""))
        assertNull(StremioIds.normalizeManifestUrl("nu e adresa"))
        assertNull(StremioIds.normalizeManifestUrl("abc"))
        assertEquals("https://opensubtitles-v3.strem.io", StremioIds.baseUrl(os))
    }

    @Test
    fun buildsSubtitleUrls() {
        val base = "https://opensubtitles-v3.strem.io"
        assertEquals("tt0133093", StremioIds.videoId("tt0133093", false, null, null))
        assertEquals("tt0903747:1:2", StremioIds.videoId("tt0903747", true, 1, 2))
        assertNull(StremioIds.videoId("tt0903747", true, 1, null))
        assertEquals(
            "$base/subtitles/series/tt0903747:1:2.json",
            StremioIds.subtitlesUrl(base, "series", "tt0903747:1:2"),
        )
        assertTrue(StremioIds.isImdb("tt0133093"))
        assertFalse(StremioIds.isImdb("123"))
    }

    @Test
    fun mapsLanguages() {
        assertEquals("ro", SubtitleLanguages.toIso1("rum"))
        assertEquals("ro", SubtitleLanguages.toIso1("ron"))
        assertEquals("ro", SubtitleLanguages.toIso1("ro"))
        assertEquals("en", SubtitleLanguages.toIso1("eng"))
        assertEquals("en", SubtitleLanguages.toIso1("English"))
        assertEquals("pt", SubtitleLanguages.toIso1("pob"))
        assertEquals("de", SubtitleLanguages.toIso1("ger"))
        assertEquals("pt", SubtitleLanguages.toIso1("pt-BR"))
        assertNull(SubtitleLanguages.toIso1("nonsense"))
        assertEquals("Română", SubtitleLanguages.displayName("rum"))
        assertEquals("Portugheză (BR)", SubtitleLanguages.displayName("pob"))
    }

    private fun sub(
        id: String,
        lang: String,
    ) = AddonSubtitle(id, "https://x/$id", lang, SubtitleLanguages.toIso1(lang), "OpenSubtitles")

    @Test
    fun ordersByPreferredLanguages() {
        val subs = listOf(sub("1", "eng"), sub("2", "fre"), sub("3", "rum"), sub("4", "eng"), sub("5", "rum"))
        assertEquals(
            listOf("3", "5", "1", "4", "2"),
            SubtitleChoice.order(subs, listOf("ro", "en")).map { it.id },
        )
    }

    @Test
    fun autoPicksAddonOnlyWhenBetterThanEmbedded() {
        val subs = listOf(sub("1", "eng"), sub("2", "rum"))
        val prefs = listOf("ro", "en")
        assertEquals("2", SubtitleChoice.autoPick(emptyList(), subs, prefs)?.id)
        assertEquals("2", SubtitleChoice.autoPick(listOf("en"), subs, prefs)?.id)
        assertNull(SubtitleChoice.autoPick(listOf("ro"), subs, prefs))
        assertNull(SubtitleChoice.autoPick(listOf("en"), listOf(sub("1", "eng")), prefs))
        assertEquals("1", SubtitleChoice.autoPick(listOf(null), listOf(sub("1", "eng")), prefs)?.id)
        assertNull(SubtitleChoice.autoPick(emptyList(), subs, emptyList()))
        assertNull(SubtitleChoice.autoPick(emptyList(), listOf(sub("9", "fre")), prefs))
    }
}
