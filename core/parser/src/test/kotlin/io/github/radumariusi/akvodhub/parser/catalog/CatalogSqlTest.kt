package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.io.IOException

class CatalogSqlTest {
    @Test
    fun sameSourceFilterAndAdultCategoriesOnLargeSyntheticCatalog() {
        val query = CatalogQuery.page(CatalogFilter(quality = VideoQuality.UHD, language = "en"), 48)
        val hideAdult = ContentVisibility.adultMovieSources()
        val script =
            """
            import sqlite3
            import sys

            db = sqlite3.connect(":memory:")
            db.executescript('''
                CREATE TABLE title (
                    id INTEGER PRIMARY KEY, mediaType, name, year, posterPath, backdropPath, rating,
                    bestQuality, overview, dubLabel, imdbRating, imdbVotes, tmdbBadge, imdbBadge, hdrBadge,
                    isNew, watchProgress, popularity, addedAt, playlistAddedAt, sourceCount, originalLanguage
                );
                CREATE TABLE raw_entry (titleId INTEGER, quality, languages, visible, kind, groupTitle);
                CREATE TABLE title_source_option (
                    titleId, quality, language, originalAudio, PRIMARY KEY(titleId, quality, language, originalAudio)
                );
                WITH RECURSIVE ids(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM ids WHERE n < 111000)
                INSERT INTO title (id, mediaType, name, popularity, sourceCount, originalLanguage)
                SELECT n, 'MOVIE', 'Synthetic', 1, 1, 'en' FROM ids;
                INSERT INTO raw_entry SELECT id, 'UHD', 'de', 1, 'MOVIE', 'Film' FROM title;
                INSERT INTO raw_entry VALUES (1, 'FHD', 'en', 1, 'MOVIE', 'Film');
                INSERT INTO raw_entry VALUES (2, 'UHD', 'en', 1, 'MOVIE', 'Filme pentru ADULȚI');
                INSERT INTO raw_entry VALUES (3, 'UHD', 'mul', 1, 'MOVIE', 'Film');
                INSERT INTO raw_entry VALUES (4, 'UHD', 'en,de', 1, 'MOVIE', 'Young Adult');
            ''')
            build, page, hide = sys.argv[1:4]
            params = sys.argv[4:7]
            db.execute(build)
            assert [row[0] for row in db.execute(page, params)] == [4, 2]
            db.execute(hide, sys.argv[7:])
            db.execute('DELETE FROM title_source_option')
            db.execute(build)
            assert [row[0] for row in db.execute(page, params)] == [4]
            assert db.execute("SELECT visible FROM raw_entry WHERE titleId = 2 AND languages = 'en'").fetchone() == (0,)
            """.trimIndent()
        val process =
            try {
                ProcessBuilder(
                    listOf("python3", "-c", script, ContentVisibility.SOURCE_OPTIONS, query.sql, hideAdult.sql) +
                        query.args.map { it.toString() } + hideAdult.args.map { it.toString() },
                ).redirectErrorStream(true).start()
            } catch (e: IOException) {
                assumeNoException("Python 3 cu modulul SQLite este opțional pentru testul SQL local.", e)
                return
            }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.waitFor())
    }
}
