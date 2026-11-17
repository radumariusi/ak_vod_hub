package io.github.radumariusi.akvodhub.data.stremio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioJsonTest {
    @Test
    fun readsOpenSubtitlesManifest() {
        val manifest =
            StremioJson.manifest(
                """{"id":"org.stremio.opensubtitlesv3","version":"1.0.0","name":"OpenSubtitles v3",
                "description":"x","catalogs":[],"resources":["subtitles"],"types":["movie","series"],
                "idPrefixes":["tt"]}""",
            )!!
        assertEquals("OpenSubtitles v3", manifest.name)
        assertTrue(manifest.supportsSubtitles("movie", "tt0133093"))
        assertTrue(manifest.supportsSubtitles("series", "tt0903747:1:2"))
        assertFalse(manifest.supportsSubtitles("channel", "tt1"))
        assertFalse(manifest.supportsSubtitles("movie", "kitsu:1"))
    }

    @Test
    fun readsResourceObjects() {
        val manifest =
            StremioJson.manifest(
                """{"id":"a","name":"A","types":["movie"],"resources":[
                {"name":"catalog","types":["movie"]},
                {"name":"subtitles","types":["series"],"idPrefixes":["tt","tmdb:"]}]}""",
            )!!
        assertEquals(listOf("catalog", "subtitles"), manifest.resources.map { it.name })
        assertTrue(manifest.supportsSubtitles("series", "tt1:1:1"))
        assertFalse(manifest.supportsSubtitles("movie", "tt1"))
        assertNull(StremioJson.manifest("""{"name":"fără id"}"""))
        assertNull(StremioJson.manifest("<html>"))
    }

    @Test
    fun readsSubtitleList() {
        val list =
            StremioJson.subtitles(
                """{"subtitles":[
                {"id":"1","url":"https://subs5.strem.io/en/download/file/1","lang":"rum","SubEncoding":"CP1250"},
                {"id":"2","url":"https://subs5.strem.io/en/download/file/2","lang":"eng"},
                {"id":"3","lang":"fre"},
                {"url":"ftp://bad","lang":"eng"},
                {"url":"https://x/4.srt","lang":"pob"}]}""",
                "OpenSubtitles v3",
            )
        assertEquals(listOf("1", "2", "OpenSubtitles v3-4"), list.map { it.id })
        assertEquals(listOf("ro", "en", "pt"), list.map { it.language })
        assertEquals("OpenSubtitles v3", list[0].addon)
        assertTrue(StremioJson.subtitles("{}", "x").isEmpty())
        assertTrue(StremioJson.subtitles("nu e json", "x").isEmpty())
    }
}
