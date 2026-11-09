package com.zeroplayer.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zeroplayer.app.MainActivity
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.playback.PlaybackController

/**
 * Handles widget taps. The process may be cold when a widget button is pressed,
 * so every action first initialises the graph and asks the controller to
 * connect; queued commands run as soon as the media session is available.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext
        runCatching { Graph.init(app) }

        if (action == ACTION_OPEN) {
            val open = Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(open) }
            return
        }

        runCatching { PlaybackController.connect(app) }
        when (action) {
            ACTION_TOGGLE -> PlaybackController.togglePlayPause()
            ACTION_NEXT -> PlaybackController.next()
            ACTION_PREV -> PlaybackController.previous()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.zeroplayer.app.widget.TOGGLE"
        const val ACTION_NEXT = "com.zeroplayer.app.widget.NEXT"
        const val ACTION_PREV = "com.zeroplayer.app.widget.PREV"
        const val ACTION_OPEN = "com.zeroplayer.app.widget.OPEN"
    }
}
