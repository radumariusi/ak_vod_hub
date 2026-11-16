package io.github.radumariusi.akvodhub.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistSourceEntity::class,
        RawEntryEntity::class,
        TitleEntity::class,
        MatchCacheEntity::class,
        WatchStateEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AkVodDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    abstract fun catalogDao(): CatalogDao

    companion object {
        fun create(context: Context): AkVodDatabase =
            Room
                .databaseBuilder(context, AkVodDatabase::class.java, "akvodhub.db")
                // Până la prima versiune stabilă, o schimbare de schemă reface baza (playlist-ul se reimportă).
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) = createSearchTable(db)
                    },
                ).build()

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
    }
}
