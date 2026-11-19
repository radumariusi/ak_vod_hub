package io.github.radumariusi.akvodhub.parser.catalog

import io.github.radumariusi.akvodhub.parser.title.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

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
                CREATE TABLE raw_entry (
                    titleId INTEGER, quality TEXT, languages TEXT, visible INTEGER, kind TEXT, groupTitle TEXT
                );
                CREATE INDEX index_raw_entry_titleId ON raw_entry (titleId);
                CREATE TABLE title_source_option (
                    titleId INTEGER NOT NULL, quality TEXT NOT NULL, language TEXT NOT NULL,
                    originalAudio INTEGER NOT NULL, PRIMARY KEY(titleId, quality, language, originalAudio)
                );
                WITH RECURSIVE ids(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM ids WHERE n < 111000)
                INSERT INTO title (id, mediaType, name, popularity, sourceCount, originalLanguage)
                SELECT n, 'MOVIE', 'Synthetic', 1, 1, 'en' FROM ids;
                INSERT INTO raw_entry SELECT id, 'UHD', 'de', 1, 'MOVIE', 'Film' FROM title;
                INSERT INTO raw_entry VALUES (1, 'FHD', 'en', 1, 'MOVIE', 'Film');
                INSERT INTO raw_entry VALUES (2, 'UHD', 'en', 1, 'MOVIE', 'Filme pentru ADULȚI');
                INSERT INTO raw_entry VALUES (3, 'UHD', 'mul', 1, 'MOVIE', 'Film');
                INSERT INTO raw_entry VALUES (4, 'UHD', 'en,de', 1, 'MOVIE', 'Young Adult');
                INSERT INTO raw_entry VALUES (5, 'UHD', 'ori', 1, 'MOVIE', 'RO - FILME');
            ''')
            build, page, hide = sys.argv[1:4]
            params = sys.argv[4:7]
            db.execute(build)
            plan = [row[3] for row in db.execute('EXPLAIN QUERY PLAN ' + page, params)]
            assert any('SEARCH s ' in detail and '(titleId=' in detail for detail in plan), plan
            assert [row[0] for row in db.execute(page, params)] == [5, 4, 2]
            db.execute(hide, sys.argv[7:])
            db.execute('DELETE FROM title_source_option')
            db.execute(build)
            assert [row[0] for row in db.execute(page, params)] == [5, 4]
            original = "SELECT language, originalAudio FROM title_source_option WHERE titleId = 5 AND language = 'en'"
            assert db.execute(original).fetchall() == [('en', 1)]
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
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            fail("Testul SQLite sintetic nu a terminat în 45 de secunde.")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.exitValue())
    }
}
