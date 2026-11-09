package com.zeroplayer.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.playback.PlaybackController

/**
 * Home screen widget: current track plus previous / play-pause / next.
 *
 * The widget never talks to ExoPlayer directly. Buttons go through
 * [WidgetActionReceiver], which reconnects [PlaybackController] (starting the
 * media service if needed) and then issues the command, so the widget keeps
 * working after the activity has been closed or the process was killed.
 */
class ZeroWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val app = context.applicationContext
        runCatching { Graph.init(app) }
        runCatching { PlaybackController.connect(app) }
        val state = PlaybackController.state.value
        val views = ZeroWidgetBridge.build(app, state)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }
}
