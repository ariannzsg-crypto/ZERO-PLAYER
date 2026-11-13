package com.zeroplayer.app.core

import android.content.Context
import com.zeroplayer.app.audio.EqualizerController
import com.zeroplayer.app.data.MusicRepository
import com.zeroplayer.app.data.Prefs
import com.zeroplayer.app.data.db.AppDatabase
import com.zeroplayer.app.lyrics.LyricsRepository
import com.zeroplayer.app.net.AiClient
import com.zeroplayer.app.net.online.OnlinePrefs
import com.zeroplayer.app.net.online.OnlineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide dependency holder. The media service and the UI live in the same
 * process, so a single database/repository instance is shared between them.
 */
object Graph {
    private var appContext: Context? = null
    private var database: AppDatabase? = null
    private var repository: MusicRepository? = null
    private var prefsInstance: Prefs? = null
    private var lyricsInstance: LyricsRepository? = null
    private var aiInstance: AiClient? = null
    private var onlineInstance: OnlineRepository? = null

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (appContext != null) return
        synchronized(this) {
            if (appContext != null) return
            val app = context.applicationContext
            appContext = app
            prefsInstance = Prefs(app)
            database = AppDatabase.build(app)
            repository = MusicRepository(app, database!!, prefsInstance!!)
            lyricsInstance = LyricsRepository(app, database!!, prefsInstance!!)
            aiInstance = AiClient(prefsInstance!!)
            onlineInstance = OnlineRepository(OnlinePrefs(app))
            EqualizerController.restore(prefsInstance!!, repository!!)
        }
    }

    val context: Context get() = requireNotNull(appContext) { "Graph.init() not called" }
    val prefs: Prefs get() = requireNotNull(prefsInstance) { "Graph.init() not called" }
    val db: AppDatabase get() = requireNotNull(database) { "Graph.init() not called" }
    val music: MusicRepository get() = requireNotNull(repository) { "Graph.init() not called" }
    val lyrics: LyricsRepository get() = requireNotNull(lyricsInstance) { "Graph.init() not called" }
    val ai: AiClient get() = requireNotNull(aiInstance) { "Graph.init() not called" }
    val online: OnlineRepository get() = requireNotNull(onlineInstance) { "Graph.init() not called" }
}
