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

    @Test
    fun readsCatalogs() {
        val manifest =
            StremioJson.manifest(
                """{"id":"com.linvo.cinemeta","name":"Cinemeta","resources":["catalog","meta"],
                "types":["movie","series"],"catalogs":[
                {"type":"movie","id":"top","name":"Popular","extra":[{"name":"search"},{"name":"skip"}]},
                {"type":"movie","id":"search","name":"Caută","extra":[{"name":"search","isRequired":true}]},
                {"type":"series","id":"imdbRating","extraSupported":["skip","genre"]},
                {"type":"channel","id":"ch","name":"Canale"},
                {"id":"broken"}]}""",
            )!!
        assertEquals(4, manifest.catalogs.size)
        val top = manifest.catalogs[0]
        assertEquals("Popular", top.name)
        assertTrue(top.browsable)
        assertTrue(top.skip)
        assertFalse(manifest.catalogs[1].browsable)
        assertEquals("imdbRating", manifest.catalogs[2].name)
        assertTrue(manifest.catalogs[2].skip)
        assertFalse(manifest.catalogs[3].browsable)
    }

    @Test
    fun readsCatalogMetas() {
        val metas =
            StremioJson.metas(
                """{"metas":[
                {"id":"tt0133093","type":"movie","name":"The Matrix","releaseInfo":"1999"},
                {"id":"tmdb:1399","type":"series","name":"Game of Thrones","releaseInfo":"2011-2019"},
                {"id":"mdblist:5","type":"movie","name":"X","imdb_id":"tt0000005","moviedb_id":77,"year":2020},
                {"id":"tt1","type":"movie"},
                {"type":"movie","name":"Fără ID"}]}""",
            )
        assertEquals(3, metas.size)
        assertEquals("tt0133093", metas[0].imdbId)
        assertEquals(1999, metas[0].year)
        assertNull(metas[0].tmdbId)
        assertEquals(1399L, metas[1].tmdbId)
        assertEquals(2011, metas[1].year)
        assertEquals("tt0000005", metas[2].imdbId)
        assertEquals(77L, metas[2].tmdbId)
        assertEquals(2020, metas[2].year)
        assertTrue(StremioJson.metas("nu e json").isEmpty())
    }
}
