package io.github.radumariusi.akvodhub.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pornește de la o bază v5 (schema generată de Room pentru entitățile din etapa 8, cu date) și o deschide cu
 * toate migrările. Room verifică la final că schema rezultată e identică cu entitățile curente, deci testul
 * prinde orice coloană, index sau valoare implicită uitată într-o migrare (ex. instalare v0.1.14 → ultima).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun cleanUp() {
        context.deleteDatabase(NAME)
    }

    @Test
    fun migratesVersion5ToLatestAndKeepsData() {
        context.deleteDatabase(NAME)
        val file = context.getDatabasePath(NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V5_SCHEMA.forEach(db::execSQL)
            V5_DATA.forEach(db::execSQL)
            db.version = 5
        }

        val room = open()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(LATEST, db.version)
            assertEquals(2L, db.long("SELECT COUNT(*) FROM raw_entry WHERE visible = 1"))
            assertEquals(0.25, db.double("SELECT watchProgress FROM title WHERE id = 10"), 0.0001)
            assertEquals(0L, db.long("SELECT metadataFetched + isNew + hasOriginalAudio FROM title WHERE id = 10"))
            // 16→17: progresul episodului din „Continuă vizionarea” trece în episode_progress.
            assertEquals(600_000L, db.long("SELECT positionMs FROM episode_progress WHERE titleId = 10"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM watch_history"))
            assertEquals(1L, db.long("SELECT COUNT(*) FROM playlist_source"))
            assertEquals(0L, db.long("SELECT COUNT(*) FROM catalog_dirty"))
            assertEquals(
                2L,
                db.long(TRIGGERS),
            )
            assertEquals(1L, db.long("SELECT COUNT(*) FROM sqlite_master WHERE name = 'title_search'"))

            // Trigger-ele reindexării incrementale funcționează pe baza migrată.
            db.execSQL("UPDATE raw_entry SET titleId = 10 WHERE id = 2")
            assertEquals(1L, db.long("SELECT COUNT(*) FROM catalog_dirty WHERE titleId = 10"))
        } finally {
            room.close()
        }
    }

    @Test
    fun freshInstallCreatesLatestSchemaWithTriggers() {
        context.deleteDatabase(NAME)
        val room = open()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(LATEST, db.version)
            assertEquals(
                2L,
                db.long(TRIGGERS),
            )
        } finally {
            room.close()
        }
    }

    private fun open(): AkVodDatabase =
        Room
            .databaseBuilder(context, AkVodDatabase::class.java, NAME)
            .addMigrations(*AkVodDatabase.MIGRATIONS)
            .addCallback(AkVodDatabase.OPEN_CALLBACK)
            .allowMainThreadQueries()
            .build()

    private fun SupportSQLiteDatabase.long(sql: String): Long =
        query(sql).use {
            it.moveToFirst()
            it.getLong(0)
        }

    private fun SupportSQLiteDatabase.double(sql: String): Double =
        query(sql).use {
            it.moveToFirst()
            it.getDouble(0)
        }

    private companion object {
        const val NAME = "migration-test.db"
        const val LATEST = 20
        const val TRIGGERS = "SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'catalog_dirty_%'"

        /** Exact ce genera Room 2.x pentru entitățile v5 (commit 3396cc7, etapa 8). */
        val V5_SCHEMA =
            listOf(
                "CREATE TABLE IF NOT EXISTS `playlist_source` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `label` TEXT NOT NULL, `location` TEXT NOT NULL, " +
                    "`username` TEXT, `password` TEXT, `createdAt` INTEGER NOT NULL, `lastImportAt` INTEGER, " +
                    "`lastImportError` TEXT, `lastAdded` INTEGER NOT NULL, `lastRemoved` INTEGER NOT NULL, " +
                    "`lastDurationMs` INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS `raw_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sourceId` INTEGER NOT NULL, `providerKey` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `groupTitle` TEXT, `url` TEXT, `logo` TEXT, `streamId` INTEGER, " +
                    "`seriesId` INTEGER, `season` INTEGER, `episode` INTEGER, `containerExt` TEXT, " +
                    "`tmdbId` INTEGER, `providerAddedAt` INTEGER, `firstSeenAt` INTEGER NOT NULL, " +
                    "`seenRun` INTEGER NOT NULL, " +
                    "`title` TEXT, `year` INTEGER, `quality` TEXT, `hdr` TEXT, `codec` TEXT, `languages` TEXT, " +
                    "`subtitles` TEXT, `titleId` INTEGER, `matchState` INTEGER NOT NULL)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_raw_entry_sourceId_providerKey` " +
                    "ON `raw_entry` (`sourceId`, `providerKey`)",
                "CREATE INDEX IF NOT EXISTS `index_raw_entry_sourceId_seriesId` ON `raw_entry` (`sourceId`, `seriesId`)",
                "CREATE INDEX IF NOT EXISTS `index_raw_entry_titleId` ON `raw_entry` (`titleId`)",
                "CREATE INDEX IF NOT EXISTS `index_raw_entry_matchState` ON `raw_entry` (`matchState`)",
                "CREATE TABLE IF NOT EXISTS `title` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`mediaType` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`originalName` TEXT, `year` INTEGER, `overview` TEXT, `posterPath` TEXT, `backdropPath` TEXT, " +
                    "`rating` REAL, " +
                    "`voteCount` INTEGER, `popularity` REAL, `genreIds` TEXT, `originalLanguage` TEXT, " +
                    "`imdbId` TEXT, `detailsFetched` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`sourceCount` INTEGER NOT NULL, `addedAt` INTEGER, `bestQuality` INTEGER, `qualities` TEXT, " +
                    "`audioLanguages` TEXT)",
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_title_mediaType_tmdbId` ON `title` (`mediaType`, `tmdbId`)",
                "CREATE INDEX IF NOT EXISTS `index_title_popularity` ON `title` (`popularity`)",
                "CREATE INDEX IF NOT EXISTS `index_title_addedAt` ON `title` (`addedAt`)",
                "CREATE INDEX IF NOT EXISTS `index_title_rating` ON `title` (`rating`)",
                "CREATE INDEX IF NOT EXISTS `index_title_name` ON `title` (`name`)",
                "CREATE INDEX IF NOT EXISTS `index_title_imdbId` ON `title` (`imdbId`)",
                "CREATE TABLE IF NOT EXISTS `match_cache` (`queryKey` TEXT NOT NULL, `tmdbId` INTEGER, " +
                    "`score` REAL NOT NULL, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`queryKey`))",
                "CREATE TABLE IF NOT EXISTS `watch_state` (`titleId` INTEGER NOT NULL, `entryId` INTEGER NOT NULL, " +
                    "`season` INTEGER, `episode` INTEGER, `positionMs` INTEGER NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`titleId`))",
                "CREATE TABLE IF NOT EXISTS `watch_history` (`titleId` INTEGER NOT NULL, `progress` REAL NOT NULL, " +
                    "`finished` INTEGER NOT NULL, `watchedAt` INTEGER NOT NULL, PRIMARY KEY(`titleId`))",
                "CREATE TABLE IF NOT EXISTS `discover_cache` (`cacheKey` TEXT NOT NULL, `data` TEXT NOT NULL, " +
                    "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))",
                "CREATE TABLE IF NOT EXISTS `imdb_map` (`imdbId` TEXT NOT NULL, `mediaType` TEXT, `tmdbId` INTEGER, " +
                    "`checkedAt` INTEGER NOT NULL, PRIMARY KEY(`imdbId`))",
                "CREATE VIRTUAL TABLE IF NOT EXISTS title_search USING fts4(name, alt, tokenize=unicode61)",
            )

        val V5_DATA =
            listOf(
                "INSERT INTO playlist_source VALUES (1, 'Test', 'XTREAM', 'server', 'enc', 'u', 'p', 1, NULL, NULL, " +
                    "0, 0, 0)",
                "INSERT INTO title (id, mediaType, tmdbId, name, detailsFetched, updatedAt, sourceCount) " +
                    "VALUES (10, 'TV', 1399, 'Serial', 1, 1, 2)",
                "INSERT INTO raw_entry (id, sourceId, providerKey, kind, name, firstSeenAt, seenRun, titleId, " +
                    "matchState, seriesId) VALUES (1, 1, 'series:5', 'SERIES', 'Serial', 1, 1, 10, 1, 5)",
                "INSERT INTO raw_entry (id, sourceId, providerKey, kind, name, firstSeenAt, seenRun, titleId, " +
                    "matchState, seriesId, season, episode) VALUES (2, 1, 'ep:7', 'EPISODE', 'Serial S01E02', 1, 1, " +
                    "NULL, 3, 5, 1, 2)",
                "INSERT INTO watch_state VALUES (10, 2, 1, 2, 600000, 2400000, 5)",
                "INSERT INTO watch_history VALUES (10, 0.25, 0, 5)",
            )
    }
}
