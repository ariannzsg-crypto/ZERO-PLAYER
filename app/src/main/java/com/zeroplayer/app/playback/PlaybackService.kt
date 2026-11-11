package com.zeroplayer.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.zeroplayer.app.MainActivity
import com.zeroplayer.app.R
import com.zeroplayer.app.audio.dsp.AudioTap
import com.zeroplayer.app.audio.dsp.EqDsp
import com.zeroplayer.app.core.DiagLog
import com.zeroplayer.app.core.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground media service owning the ExoPlayer instance. Media3 supplies the
 * notification, lock screen controls, Bluetooth/headset commands and audio focus
 * handling; this class adds queue persistence, play statistics, the sleep timer
 * and the DSP chain.
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null

    private var currentItemStartedAt = 0L
    private var trackedSongId = -1L

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        DiagLog.d("SVC", "onCreate: building ExoPlayer with the EQ renderers factory")

        val renderersFactory = EqRenderersFactory(this)
        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        DiagLog.d(
            "SVC",
            "player built: audioSession=${exoPlayer.audioSessionId} " +
                "shuffle=${Graph.prefs.shuffleEnabled} repeat=${Graph.prefs.repeatMode}",
        )
        exoPlayer.shuffleModeEnabled = Graph.prefs.shuffleEnabled
        exoPlayer.repeatMode = Graph.prefs.repeatMode
        exoPlayer.addListener(playerListener)
        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSinkError(
                eventTime: AnalyticsListener.EventTime,
                audioSinkError: Exception,
            ) {
                DiagLog.e(
                    "SND",
                    "audio sink error: ${DiagLog.describe(audioSinkError)}",
                    audioSinkError,
                )
            }
        })

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.channel_playback_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_stat_zero)
        setMediaNotificationProvider(notificationProvider)

        session = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(openAppIntent())
            .setCallback(SessionCallback())
            .build()

        player = exoPlayer
        AudioTap.active = true
        DiagLog.d("SVC", "media session ready")
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        DiagLog.d("SVC", "onGetSession for ${controllerInfo.packageName} uid=${controllerInfo.uid}")
        return session
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = player
        if (activePlayer == null || !activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        DiagLog.d("SVC", "onDestroy")
        persistQueue()
        sleepJob?.cancel()
        AudioTap.active = false
        player?.removeListener(playerListener)
        player?.release()
        player = null
        session?.release()
        session = null
        super.onDestroy()
    }

    /* ------------------------------- playback ------------------------------ */

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            commitPlayCount()
            trackedSongId = mediaItem?.songId() ?: -1L
            currentItemStartedAt = System.currentTimeMillis()
            persistQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                commitPlayCount()
                persistQueue()
            } else {
                currentItemStartedAt = System.currentTimeMillis()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            Graph.prefs.shuffleEnabled = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            Graph.prefs.repeatMode = repeatMode
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            DiagLog.d(
                "SVC",
                "playbackState=$playbackState items=${player?.mediaItemCount} " +
                    "playWhenReady=${player?.playWhenReady} position=${player?.currentPosition}",
            )
            if (playbackState == Player.STATE_ENDED) persistQueue()
        }

        override fun onPlayerError(error: PlaybackException) {
            DiagLog.e(
                "SVC",
                "player error ${error.errorCodeName} (${error.errorCode}) " +
                    "uri=${player?.currentMediaItem?.localConfiguration?.uri} " +
                    "detail=${DiagLog.describe(error)}",
                error,
            )
            val activePlayer = player ?: return
            if (error.errorCode != PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK) return
            if (EqDsp.hardBypass) {
                DiagLog.d("SVC", "runtime check failed again with the DSP already bypassed")
                return
            }
            val index = activePlayer.currentMediaItemIndex
            val position = activePlayer.currentPosition
            EqDsp.hardBypass = true
            DiagLog.d(
                "SVC",
                "retrying item=$index position=${position}ms without the DSP chain",
            )
            activePlayer.prepare()
            activePlayer.seekTo(index, position)
            activePlayer.play()
        }
    }

    /** A track counts as played after 20 seconds of listening. */
    private fun commitPlayCount() {
        val activePlayer = player ?: return
        val songId = trackedSongId
        if (songId <= 0) return
        val listened = System.currentTimeMillis() - currentItemStartedAt
        val position = activePlayer.currentPosition
        if (listened >= MIN_PLAY_MS || position >= MIN_PLAY_MS) {
            val id = songId
            Graph.appScope.launch { Graph.music.recordPlay(id) }
            currentItemStartedAt = System.currentTimeMillis()
        }
    }

    private fun persistQueue() {
        val activePlayer = player ?: return
        runCatching {
            val ids = (0 until activePlayer.mediaItemCount).map { activePlayer.getMediaItemAt(it).songId() }
            Graph.prefs.lastQueueIds = ids
            Graph.prefs.lastQueueIndex = activePlayer.currentMediaItemIndex.coerceAtLeast(0)
            Graph.prefs.lastPositionMs = activePlayer.currentPosition
            Graph.prefs.lastSongId = activePlayer.currentMediaItem?.songId() ?: -1L
        }
    }

    private fun scheduleSleep(endAt: Long) {
        sleepJob?.cancel()
        val remaining = endAt - System.currentTimeMillis()
        if (remaining <= 0) return
        sleepJob = scope.launch {
            delay(remaining)
            player?.pause()
            Graph.prefs.sleepTimerEndAt = 0L
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            DiagLog.d("SVC", "onConnect from ${controller.packageName} uid=${controller.uid}")
            val available = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SLEEP_COMMAND)
                .build()
            // Media3's default player commands deliberately leave out the queue
            // changing commands, so a MediaController's setMediaItems() was
            // rejected by the session: tapping a song queued nothing, played
            // nothing and reported no error. Grant them explicitly.
            val playerCommands = Player.Commands.Builder()
                .addAll(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .add(Player.COMMAND_SET_MEDIA_ITEM)
                .build()
            DiagLog.d(
                "SVC",
                "granted: changeQueue=${playerCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS)} " +
                    "setMediaItem=${playerCommands.contains(Player.COMMAND_SET_MEDIA_ITEM)} " +
                    "playPause=${playerCommands.contains(Player.COMMAND_PLAY_PAUSE)} " +
                    "prepare=${playerCommands.contains(Player.COMMAND_PREPARE)}",
            )
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(available)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == SLEEP_COMMAND.customAction) {
                val endAt = args.getLong(ARG_SLEEP_END_AT, 0L)
                Graph.prefs.sleepTimerEndAt = endAt
                if (endAt <= System.currentTimeMillis()) {
                    sleepJob?.cancel()
                    player?.pause()
                } else {
                    scheduleSleep(endAt)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            val endAt = Graph.prefs.sleepTimerEndAt
            if (endAt > System.currentTimeMillis()) scheduleSleep(endAt)
        }
    }

    companion object {
        const val CHANNEL_ID = "zero_playback"
        const val NOTIFICATION_ID = 1101
        private const val MIN_PLAY_MS = 20_000L

        const val ARG_SLEEP_END_AT = "endAt"
        val SLEEP_COMMAND = SessionCommand("com.zeroplayer.app.CMD_SLEEP_TIMER", Bundle.EMPTY)
    }
}
