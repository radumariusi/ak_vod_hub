package io.github.radumariusi.akvodhub.parser.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeNoException
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class CatalogDirtyTest {
    @Test
    fun sourceOptionsAreScopedToDirtyTitles() {
        assertTrue(ContentVisibility.DIRTY_SOURCE_OPTIONS.contains("r.titleId IN ${CatalogDirty.IDS}"))
        assertEquals(
            ContentVisibility.SOURCE_OPTIONS,
            ContentVisibility.DIRTY_SOURCE_OPTIONS.replace(" AND r.titleId IN ${CatalogDirty.IDS}", ""),
        )
    }

    @Test
    fun triggersMarkOnlyTitlesWhoseIndexedDataChanged() {
        val script =
            """
            import sqlite3
            import sys

            db = sqlite3.connect(":memory:")
            db.executescript('''
                CREATE TABLE title (id INTEGER PRIMARY KEY, name, originalName, originalLanguage, rating,
                    updatedAt, sourceCount, posterPath);
                CREATE TABLE raw_entry (id INTEGER PRIMARY KEY, titleId INTEGER, quality TEXT, languages TEXT,
                    hdr TEXT, title TEXT, groupTitle TEXT, season INT, episode INT, providerAddedAt INT,
                    visible INT, seenRun INT, confirmedQuality TEXT);
                CREATE TABLE `catalog_dirty` (`titleId` INTEGER NOT NULL, PRIMARY KEY(`titleId`));
                INSERT INTO title VALUES (1, 'A', NULL, 'en', 7, 0, 1, NULL), (2, 'B', NULL, 'en', 7, 0, 1, NULL),
                    (3, 'C', NULL, 'en', 7, 0, 0, NULL);
                INSERT INTO raw_entry VALUES (1, 1, 'HD', 'en', NULL, 'A', 'g', NULL, NULL, NULL, 1, 1, NULL),
                    (2, 2, 'HD', 'en', NULL, 'B', 'g', NULL, NULL, NULL, 1, 1, NULL),
                    (3, NULL, 'HD', 'en', NULL, 'C', 'g', NULL, NULL, NULL, 1, 1, NULL);
            ''')
            for trigger in sys.argv[1:]:
                db.execute(trigger)
            dirty = lambda: sorted(row[0] for row in db.execute('SELECT titleId FROM catalog_dirty'))
            # Scrierile indexerului, importul și actualizările fără schimbare nu marchează nimic.
            db.execute("UPDATE raw_entry SET visible = 0")
            db.execute("UPDATE raw_entry SET seenRun = 2")
            db.execute("UPDATE raw_entry SET titleId = titleId, quality = quality")
            db.execute("UPDATE title SET sourceCount = 5, updatedAt = 9, posterPath = 'p'")
            assert dirty() == [], dirty()
            db.execute("UPDATE raw_entry SET titleId = 3 WHERE id = 3")
            assert dirty() == [3], dirty()
            db.execute("UPDATE raw_entry SET titleId = 2 WHERE id = 1")
            assert dirty() == [1, 2, 3], dirty()
            db.execute("DELETE FROM catalog_dirty")
            db.execute("UPDATE raw_entry SET quality = 'FHD', confirmedQuality = 'FHD' WHERE id = 2")
            assert dirty() == [2], dirty()
            db.execute("UPDATE title SET rating = 8 WHERE id = 1")
            assert dirty() == [1, 2], dirty()
            # Room scrie `UPDATE OR ABORT`: un titlu deja marcat nu trebuie să dea eroare de constrângere.
            db.execute("UPDATE OR ABORT raw_entry SET quality = 'UHD'")
            db.execute("UPDATE OR ABORT title SET rating = 9")
            assert dirty() == [1, 2, 3], dirty()
            """.trimIndent()
        val process =
            try {
                ProcessBuilder(listOf("python3", "-c", script) + CatalogDirty.TRIGGERS)
                    .redirectErrorStream(true)
                    .start()
            } catch (e: IOException) {
                assumeNoException("Python 3 cu modulul SQLite este opțional pentru testul SQL local.", e)
                return
            }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            fail("Testul trigger-elor nu a terminat în 30 de secunde.")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.exitValue())
    }
}
