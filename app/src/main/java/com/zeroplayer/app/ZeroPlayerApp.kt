package com.zeroplayer.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.playback.PlaybackService

class ZeroPlayerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        createPlaybackChannel()
    }

    private fun createPlaybackChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            PlaybackService.CHANNEL_ID,
            getString(R.string.channel_playback_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_playback_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }
}
