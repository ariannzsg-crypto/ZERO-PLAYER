package com.zeroplayer.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 1,
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
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "zero-player.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
