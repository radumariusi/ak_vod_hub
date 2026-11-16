package io.github.radumariusi.akvodhub.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaylistSourceEntity::class, RawEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AkVodDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun create(context: Context): AkVodDatabase =
            Room
                .databaseBuilder(context, AkVodDatabase::class.java, "akvodhub.db")
                // Până la prima versiune stabilă, o schimbare de schemă reface baza (playlist-ul se reimportă).
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
