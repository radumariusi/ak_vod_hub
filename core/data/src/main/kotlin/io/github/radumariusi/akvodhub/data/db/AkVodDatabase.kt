package io.github.radumariusi.akvodhub.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.radumariusi.akvodhub.parser.catalog.CatalogDirty

@Database(
    entities = [
        PlaylistSourceEntity::class,
        RawEntryEntity::class,
        TitleEntity::class,
        MatchCacheEntity::class,
        WatchStateEntity::class,
        WatchHistoryEntity::class,
        DiscoverCacheEntity::class,
        ImdbMapEntity::class,
        ImdbRatingStage::class,
        PersonCreditEntity::class,
        WatchlistEntity::class,
        TitleSourceOption::class,
        EpisodeProgressEntity::class,
        CatalogDirtyEntity::class,
    ],
    version = 19,
    exportSchema = false,
)
abstract class AkVodDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    abstract fun catalogDao(): CatalogDao

    abstract fun discoverDao(): DiscoverDao

    companion object {
        fun create(context: Context): AkVodDatabase =
            Room
                .databaseBuilder(context, AkVodDatabase::class.java, "akvodhub.db")
                .addMigrations(*MIGRATIONS)
                .addCallback(OPEN_CALLBACK)
                .build()

        /** Pe lângă tabelele Room: indexul FTS și trigger-ele reindexării incrementale (idempotent). */
        val OPEN_CALLBACK =
            object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    createSearchTable(db)
                    CatalogDirty.TRIGGERS.forEach(db::execSQL)
                }
            }

        /** Reindexare incrementală: titlurile atinse se notează în `catalog_dirty` (vezi `CatalogDirty`). */
        val MIGRATION_18_19 =
            object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `catalog_dirty` (`titleId` INTEGER NOT NULL, PRIMARY KEY(`titleId`))",
                    )
                }
            }

        val MIGRATION_17_18 =
            object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE raw_entry ADD COLUMN providerQuality TEXT")
                    db.execSQL("ALTER TABLE raw_entry ADD COLUMN confirmedQuality TEXT")
                }
            }

        val MIGRATION_16_17 =
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS episode_progress (titleId INTEGER NOT NULL, " +
                            "season INTEGER NOT NULL, " +
                            "episode INTEGER NOT NULL, positionMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, " +
                            "finished INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(titleId, season, episode))",
                    )
                    db.execSQL(
                        "INSERT INTO episode_progress SELECT titleId, COALESCE(season, 0), episode, positionMs, " +
                            "durationMs, 0, updatedAt FROM watch_state WHERE episode IS NOT NULL AND positionMs > 0",
                    )
                }
            }

        val MIGRATION_15_16 =
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_title_year ON title (year)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_title_imdbRating ON title (imdbRating)")
                }
            }

        val MIGRATION_14_15 =
            object : Migration(14, 15) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE raw_entry ADD COLUMN visible INTEGER NOT NULL DEFAULT 1")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS title_source_option (titleId INTEGER NOT NULL, " +
                            "quality TEXT NOT NULL, language TEXT NOT NULL, originalAudio INTEGER NOT NULL, " +
                            "PRIMARY KEY(titleId, quality, language, originalAudio))",
                    )
                }
            }

        /** Etapa 12.7: rezumate locale ale noutăților, completate în fundal de indexer. */
        val MIGRATION_13_14 =
            object : Migration(13, 14) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    for (column in listOf("playlistAddedAt", "newEpisodeAt")) {
                        db.execSQL("ALTER TABLE title ADD COLUMN $column INTEGER DEFAULT NULL")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_title_$column ON title ($column)")
                    }
                }
            }

        /** Etapa 12.4: listă personală, fără atingerea progresului/istoricului existent. */
        val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS watchlist (titleId INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(titleId))",
                    )
                }
            }

        /** Etapa 10.8: descriere RO la cerere, inclusiv cache pentru traducerea absentă. */
        val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE title ADD COLUMN overviewRo TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE title ADD COLUMN overviewRoFetchedAt INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** Etapa 10.7: cache mic de filmografii, populat numai la cerere. */
        val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS person_credit (personId INTEGER NOT NULL, " +
                            "mediaType TEXT NOT NULL, tmdbId INTEGER NOT NULL, PRIMARY KEY(personId, mediaType, tmdbId))",
                    )
                }
            }

        /** Etapa 10.6: insigne precalculate și progres, fără interogări pe poster. */
        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    for (column in listOf("tmdbBadge", "imdbBadge", "hdrBadge")) {
                        db.execSQL("ALTER TABLE title ADD COLUMN $column TEXT DEFAULT NULL")
                    }
                    db.execSQL("ALTER TABLE title ADD COLUMN isNew INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE title ADD COLUMN watchProgress REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("ALTER TABLE imdb_rating_stage ADD COLUMN badge TEXT DEFAULT NULL")
                    db.execSQL(
                        "UPDATE title SET watchProgress = COALESCE((SELECT CASE WHEN w.durationMs > 0 THEN " +
                            "MIN(1.0, MAX(0.0, w.positionMs * 1.0 / w.durationMs)) ELSE 0.0 END " +
                            "FROM watch_state w WHERE w.titleId = title.id), " +
                            "(SELECT MIN(1.0, MAX(0.0, h.progress)) FROM watch_history h WHERE h.titleId = title.id), 0.0)",
                    )
                }
            }

        /** Etapa 10.5: note IMDb și cache de lucru pentru aplicare atomică. */
        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE title ADD COLUMN imdbRating REAL DEFAULT NULL")
                    db.execSQL("ALTER TABLE title ADD COLUMN imdbVotes INTEGER DEFAULT NULL")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS imdb_rating_stage (imdbId TEXT NOT NULL, " +
                            "rating REAL NOT NULL, votes INTEGER NOT NULL, PRIMARY KEY(imdbId))",
                    )
                }
            }

        /** Etapa 10.4: metadate opționale și progresul completării în fundal. */
        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE title ADD COLUMN runtimeMinutes INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE title ADD COLUMN certification TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE title ADD COLUMN originCountries TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE title ADD COLUMN tagline TEXT DEFAULT NULL")
                    db.execSQL("ALTER TABLE title ADD COLUMN metadataFetched INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** Etapa 10.2: disponibilitatea audio original, completată la reindexare. */
        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE title ADD COLUMN hasOriginalAudio INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** Etapa 10.1: eticheta DUB, completată de indexer fără reimport. */
        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE title ADD COLUMN dubLabel TEXT DEFAULT NULL")
                }
            }

        /**
         * Indexul de căutare (FTS4): titlul TMDB, titlul original și numele din playlist ale fiecărui titlu
         * (deci și „Der Pate” găsește „The Godfather”). `docid` = `title.id`. Nu e entitate Room (Room ar cere
         * schema exactă la migrare); îl umple `CatalogIndexer`. `unicode61` ignoră diacriticele și majusculele.
         */
        fun createSearchTable(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS title_search USING fts4(name, alt, tokenize=unicode61)")
        }

        /** Etapa 3: câmpurile parsate din nume. Se completează la următoarea reîmprospătare a playlist-ului. */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val columns =
                        listOf(
                            "title TEXT",
                            "year INTEGER",
                            "quality TEXT",
                            "hdr TEXT",
                            "codec TEXT",
                            "languages TEXT",
                            "subtitles TEXT",
                        )
                    for (column in columns) {
                        db.execSQL("ALTER TABLE raw_entry ADD COLUMN $column")
                    }
                }
            }

        /**
         * Etapa 4: titluri TMDB, cache-ul căutărilor și legătura intrare → titlu. Intrările existente
         * rămân „de potrivit” (matchState = 0), deci potrivirea pornește fără reimport.
         * SQL-ul trebuie să fie identic cu ce generează Room pentru entități (altfel Room refuză baza).
         */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE raw_entry ADD COLUMN titleId INTEGER")
                    db.execSQL("ALTER TABLE raw_entry ADD COLUMN matchState INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_entry_titleId` ON `raw_entry` (`titleId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_raw_entry_matchState` ON `raw_entry` (`matchState`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `title` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mediaType` TEXT NOT NULL, " +
                            "`tmdbId` INTEGER NOT NULL, `name` TEXT NOT NULL, `originalName` TEXT, `year` INTEGER, " +
                            "`overview` TEXT, `posterPath` TEXT, `backdropPath` TEXT, `rating` REAL, " +
                            "`voteCount` INTEGER, `popularity` REAL, `genreIds` TEXT, `originalLanguage` TEXT, " +
                            "`imdbId` TEXT, `detailsFetched` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_title_mediaType_tmdbId` ON `title` (`mediaType`, `tmdbId`)",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `match_cache` (`queryKey` TEXT NOT NULL, `tmdbId` INTEGER, " +
                            "`score` REAL NOT NULL, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`queryKey`))",
                    )
                }
            }

        /**
         * Etapa 5: rezumatele surselor pe titlu (pentru listele din hub), indexuri pentru ordonare,
         * „Continuă vizionarea” și indexul de căutare. Rezumatele se calculează la prima pornire (`CatalogIndexer`).
         */
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE title ADD COLUMN sourceCount INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE title ADD COLUMN addedAt INTEGER")
                    db.execSQL("ALTER TABLE title ADD COLUMN bestQuality INTEGER")
                    db.execSQL("ALTER TABLE title ADD COLUMN qualities TEXT")
                    db.execSQL("ALTER TABLE title ADD COLUMN audioLanguages TEXT")
                    for (column in listOf("popularity", "addedAt", "rating", "name")) {
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_title_$column` ON `title` (`$column`)")
                    }
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `watch_state` (`titleId` INTEGER NOT NULL, " +
                            "`entryId` INTEGER NOT NULL, `season` INTEGER, `episode` INTEGER, " +
                            "`positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`titleId`))",
                    )
                    createSearchTable(db)
                }
            }

        /**
         * Etapa 8: istoricul de vizionare, cache-ul pentru descoperire, legătura IMDb → TMDB și indexul pe `imdbId`.
         * Istoricul pornește din ce era deja în „Continuă vizionarea”.
         */
        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `watch_history` (`titleId` INTEGER NOT NULL, " +
                            "`progress` REAL NOT NULL, `finished` INTEGER NOT NULL, `watchedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`titleId`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `discover_cache` (`cacheKey` TEXT NOT NULL, " +
                            "`data` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `imdb_map` (`imdbId` TEXT NOT NULL, `mediaType` TEXT, " +
                            "`tmdbId` INTEGER, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`imdbId`))",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_title_imdbId` ON `title` (`imdbId`)")
                    db.execSQL(
                        "INSERT OR IGNORE INTO watch_history (titleId, progress, finished, watchedAt) " +
                            "SELECT titleId, CASE WHEN durationMs > 0 THEN MIN(1.0, positionMs * 1.0 / durationMs) " +
                            "ELSE 0.5 END, 0, updatedAt FROM watch_state",
                    )
                }
            }

        /** Toate migrările, în ordine (folosite și de testul de migrare). */
        val MIGRATIONS: Array<Migration>
            get() =
                arrayOf(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                )
    }
}
