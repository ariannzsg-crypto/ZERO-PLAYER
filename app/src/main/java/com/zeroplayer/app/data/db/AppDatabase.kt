package com.zeroplayer.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SongEntity::class,
        FavoriteEntity::class,
        SongStatEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        EqProfileEntity::class,
        LyricsEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songs(): SongDao
    abstract fun favorites(): FavoriteDao
    abstract fun stats(): StatsDao
    abstract fun playlists(): PlaylistDao
    abstract fun eqProfiles(): EqProfileDao
    abstract fun lyrics(): LyricsDao

    companion object {
        /** Adds the artist-playlist column used by the optional AI assist. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN autoArtist TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_autoArtist ON playlists(autoArtist)")
            }
        }

        /** Playlist covers and real listening time per track. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN coverUri TEXT")
                db.execSQL("ALTER TABLE song_stats ADD COLUMN listenedMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "zero-player.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
