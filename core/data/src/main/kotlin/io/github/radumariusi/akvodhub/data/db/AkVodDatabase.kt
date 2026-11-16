package io.github.radumariusi.akvodhub.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlaylistSourceEntity::class, RawEntryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AkVodDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun create(context: Context): AkVodDatabase =
            Room
                .databaseBuilder(context, AkVodDatabase::class.java, "akvodhub.db")
                // Până la prima versiune stabilă, o schimbare de schemă reface baza (playlist-ul se reimportă).
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

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
    }
}
