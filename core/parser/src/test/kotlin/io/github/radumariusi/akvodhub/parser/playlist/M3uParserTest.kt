package io.github.radumariusi.akvodhub.parser.playlist

import io.github.radumariusi.akvodhub.parser.VodKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.Reader

class M3uParserTest {
    private fun sample(): BufferedReader =
        javaClass.getResourceAsStream("/testdata/sample.m3u")!!.bufferedReader()

    @Test
    fun parsesAttributesNamesAndGroups() {
        val entries = sample().use { M3uParser.entries(it).toList() }
        assertEquals(9, entries.size)

        val matrix = entries[2]
        assertEquals("EN| The Matrix (1999) 4K", matrix.name)
        assertEquals("EN | Movies 4K", matrix.groupTitle)
        assertEquals("http://example.invalid/matrix.jpg", matrix.logo)
        assertEquals("http://example.invalid:8080/movie/user/pass/2001.mkv", matrix.url)
        assertEquals("", matrix.attributes["tvg-id"])

        val extGrp = entries[5]
        assertEquals("RO - Matrix (1999) FHD", extGrp.name)
        assertEquals("Filme RO", extGrp.groupTitle)
        assertNull(extGrp.logo)
    }

    @Test
    fun keepsOnlyVod() {
        val kinds =
            sample().use { reader ->
                M3uParser.entries(reader).map { VodClassifier.classify(it.url, it.groupTitle, it.name) }.toList()
            }
        assertEquals(
            listOf(
                null,
                null,
                VodKind.MOVIE,
                VodKind.MOVIE,
                VodKind.EPISODE,
                VodKind.MOVIE,
                null,
                VodKind.EPISODE,
                null,
            ),
            kinds,
        )
    }

    @Test
    fun handlesBomCrlfCommasInTitlesAndMissingExtinf() {
        val text =
            "#EXTM3U\r\n" +
                "#EXTINF:-1 tvg-name=\"A, B\" group-title=\"Movies, 2024\",Hello, World (2024)\r\n" +
                "http://h.invalid/movie/u/p/1.mkv\r\n" +
                "\r\n" +
                "http://h.invalid/files/Some.Movie.2020.1080p.mkv\r\n"
        val entries = M3uParser.entries(text.reader().buffered()).toList()
        assertEquals(2, entries.size)
        assertEquals("Hello, World (2024)", entries[0].name)
        assertEquals("A, B", entries[0].attributes["tvg-name"])
        assertEquals("Movies, 2024", entries[0].groupTitle)
        assertEquals("Some.Movie.2020.1080p.mkv", entries[1].name)
        assertNull(entries[1].groupTitle)
    }

    @Test
    fun streamsLargePlaylistsQuickly() {
        val count = 150_000
        val reader = BufferedReader(SyntheticPlaylist(count), 64 * 1024)
        val start = System.nanoTime()
        var movies = 0
        var episodes = 0
        var ignored = 0
        M3uParser.entries(reader).forEach {
            when (VodClassifier.classify(it.url, it.groupTitle, it.name)) {
                VodKind.MOVIE -> movies++
                VodKind.EPISODE -> episodes++
                else -> ignored++
            }
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(count, movies + episodes + ignored)
        assertEquals(count / 3, ignored)
        assertTrue("Parsarea a durat $ms ms", ms < 10_000)
    }

    /** Generează un playlist mare direct din memorie, fără să-l țină întreg (ca un download în flux). */
    private class SyntheticPlaylist(
        private val count: Int,
    ) : Reader() {
        private var index = -1
        private var chunk = "#EXTM3U\n"
        private var pos = 0

        override fun read(
            cbuf: CharArray,
            off: Int,
            len: Int,
        ): Int {
            if (pos >= chunk.length) {
                index++
                if (index >= count) return -1
                chunk = line(index)
                pos = 0
            }
            val n = minOf(len, chunk.length - pos)
            chunk.toCharArray(cbuf, off, pos, pos + n)
            pos += n
            return n
        }

        private fun line(i: Int): String =
            when (i % 3) {
                0 -> {
                    "#EXTINF:-1 tvg-name=\"Movie $i\" group-title=\"EN | Movies\",Movie $i (2001) FHD\n" +
                        "http://h.invalid:8080/movie/user/pass/$i.mkv\n"
                }

                1 -> {
                    "#EXTINF:-1 tvg-name=\"Show S01E$i\" group-title=\"EN | Series\",Show S01E${i % 99}\n" +
                        "http://h.invalid:8080/series/user/pass/$i.mp4\n"
                }

                else -> {
                    "#EXTINF:-1 tvg-name=\"Channel $i\" group-title=\"UK | News\",Channel $i\n" +
                        "http://h.invalid:8080/user/pass/$i\n"
                }
            }

        override fun close() = Unit
    }
}
