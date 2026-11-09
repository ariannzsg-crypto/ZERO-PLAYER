package com.zeroplayer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.zeroplayer.app.R
import com.zeroplayer.app.playback.PlaybackState

/**
 * Renders the home screen widget from a [PlaybackState] snapshot and throttles
 * redraws so the 500 ms position ticker cannot flood the launcher.
 */
object ZeroWidgetBridge {

    private var lastSignature: String? = null

    fun update(context: Context, state: PlaybackState) {
        val signature = buildString {
            append(state.songId)
            append('|').append(state.isPlaying)
            append('|').append(state.title)
            append('|').append(state.artist)
            append('|').append(state.connected)
            append('|').append(state.error != null)
        }
        if (signature == lastSignature) return
        lastSignature = signature
        push(context, state)
    }

    /** Forces a redraw, used by [ZeroWidgetProvider.onUpdate]. */
    fun refresh(context: Context, state: PlaybackState) {
        lastSignature = null
        update(context, state)
    }

    private fun push(context: Context, state: PlaybackState) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, ZeroWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = build(context, state)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    fun build(context: Context, state: PlaybackState): RemoteViews =
        RemoteViews(context.packageName, R.layout.zero_widget).apply {
            val hasTrack = state.songId > 0
            setTextViewText(
                R.id.widget_title,
                if (hasTrack) state.title.ifBlank { "Unknown title" } else "ZERO PLAYER",
            )
            setTextViewText(
                R.id.widget_artist,
                when {
                    state.error != null -> "Playback error · tap to open"
                    hasTrack -> state.artist.ifBlank { state.album.ifBlank { "ZERO PLAYER" } }
                    else -> "Nothing playing"
                },
            )
            setImageViewResource(
                R.id.widget_toggle,
                if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )
            setInt(R.id.widget_toggle, "setAlpha", if (hasTrack) 255 else 110)
            setInt(R.id.widget_prev, "setAlpha", if (hasTrack) 255 else 110)
            setInt(R.id.widget_next, "setAlpha", if (hasTrack) 255 else 110)
            setOnClickPendingIntent(R.id.widget_prev, action(context, WidgetActionReceiver.ACTION_PREV, 1))
            setOnClickPendingIntent(
                R.id.widget_toggle,
                action(context, WidgetActionReceiver.ACTION_TOGGLE, 2),
            )
            setOnClickPendingIntent(R.id.widget_next, action(context, WidgetActionReceiver.ACTION_NEXT, 3))
            setOnClickPendingIntent(R.id.widget_root, action(context, WidgetActionReceiver.ACTION_OPEN, 4))
        }

    private fun action(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
