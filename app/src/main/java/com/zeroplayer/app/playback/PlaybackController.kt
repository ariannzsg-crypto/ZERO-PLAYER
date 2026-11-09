package com.zeroplayer.app.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.zeroplayer.app.core.Graph
import com.zeroplayer.app.data.model.Song
import com.zeroplayer.app.widget.ZeroWidgetBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** UI facing playback snapshot. */
data class PlaybackState(
    val connected: Boolean = false,
    val songId: Long = -1L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    val index: Int = 0,
    val speed: Float = 1f,
    /** Non-null when the player reported a real error; shown as-is to the user. */
    val error: String? = null,
) {
    val hasTrack: Boolean get() = songId > 0
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Single MediaController bridge between Compose UI, the home screen widget and
 * [PlaybackService]. State is mirrored into a StateFlow so every screen can
 * observe playback without touching the player directly.
 *
 * Commands issued before the controller finishes connecting are queued and
 * replayed, so a tap on the very first frame (or from a widget while the
 * process is cold) never gets dropped.
 */
object PlaybackController {

    private const val TAG = "ZeroPlayback"

    private var controller: MediaController? = null
    private var connecting = false
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var lastError: String? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    val isConnected: Boolean get() = controller != null

    fun connect(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            connecting = false
            runCatching { future.get() }
                .onSuccess { mediaController ->
                    controller = mediaController
                    mediaController.addListener(listener)
                    refresh()
                    startTicker()
                    restoreLastSession()
                    flushPending()
                }
                .onFailure { error ->
                    Log.w(TAG, "Could not connect to the playback service", error)
                }
        }, ContextCompat.getMainExecutor(app))
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        connecting = false
        pending.clear()
        _state.value = PlaybackState()
    }

    private fun flushPending() {
        val player = controller ?: return
        while (pending.isNotEmpty()) {
            runCatching { pending.removeFirst().invoke(player) }
        }
        refresh()
    }

    /** Runs [action] now, or as soon as the controller is ready. */
    private fun withPlayer(action: (MediaController) -> Unit) {
        val player = controller
        if (player == null) {
            runCatching { connect(Graph.context) }
            pending.addLast(action)
            return
        }
        runCatching { action(player) }
        refresh()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
        }

        override fun onPlayerError(error: PlaybackException) {
            lastError = error.errorCodeName + ": " + error.message.orEmpty()
            Log.e(TAG, "Playback failed: ${error.errorCodeName}", error)
            refresh()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastError = null
            refresh()
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                if (controller?.isPlaying == true) refresh()
                delay(500)
            }
        }
    }

    private fun refresh() {
        val player = controller ?: return
        val item = player.currentMediaItem
        val queue = buildList {
            for (index in 0 until player.mediaItemCount) {
                val mediaItem = player.getMediaItemAt(index)
                add(mediaItem.toQueueEntry(if (index == player.currentMediaItemIndex) player.duration.coerceAtLeast(0) else 0L))
            }
        }
        val snapshot = PlaybackState(
            connected = true,
            songId = item?.songId() ?: -1L,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            album = item?.mediaMetadata?.albumTitle?.toString().orEmpty(),
            artworkUri = item?.mediaMetadata?.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            bufferedMs = player.bufferedPosition.coerceAtLeast(0),
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = queue,
            index = player.currentMediaItemIndex,
            speed = player.playbackParameters.speed,
            error = lastError,
        )
        _state.value = snapshot
        runCatching { ZeroWidgetBridge.update(Graph.context, snapshot) }
    }

    /* ------------------------------- commands ------------------------------ */

    fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        withPlayer { player ->
            lastError = null
            val items = songs.map { it.toMediaItem() }
            val index = startIndex.coerceIn(0, items.lastIndex)
            player.shuffleModeEnabled = shuffle
            player.setMediaItems(items, index, 0L)
            player.prepare()
            player.play()
        }
    }

    fun playSong(song: Song, context: List<Song> = listOf(song)) {
        val index = context.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playSongs(if (context.isEmpty()) listOf(song) else context, index)
    }

    fun togglePlayPause() = withPlayer { player ->
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun play() = withPlayer { it.play() }

    fun pause() = withPlayer { it.pause() }

    fun next() = withPlayer { it.seekToNextMediaItem() }

    fun previous() = withPlayer { player ->
        if (player.currentPosition > 4_000) player.seekTo(0) else player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = withPlayer { it.seekTo(positionMs.coerceAtLeast(0)) }

    fun seekBy(deltaMs: Long) = withPlayer { player ->
        val target = (player.currentPosition + deltaMs).coerceIn(0, player.duration.coerceAtLeast(0))
        player.seekTo(target)
    }

    fun seekToQueueItem(index: Int) = withPlayer { player ->
        if (index in 0 until player.mediaItemCount) player.seekTo(index, 0L)
    }

    fun toggleShuffle() = withPlayer { player ->
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeat() = withPlayer { player ->
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2f)
        Graph.prefs.playbackSpeed = clamped
        withPlayer { it.setPlaybackSpeed(clamped) }
    }

    fun addToQueueNext(song: Song) = withPlayer { player ->
        player.addMediaItem(player.currentMediaItemIndex.coerceAtLeast(0) + 1, song.toMediaItem())
    }

    fun addToQueueEnd(song: Song) = withPlayer { player ->
        player.addMediaItem(song.toMediaItem())
    }

    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        withPlayer { player ->
            player.addMediaItems(
                player.currentMediaItemIndex.coerceAtLeast(0) + 1,
                songs.map { it.toMediaItem() },
            )
        }
    }

    fun clearQueue() = withPlayer { it.clearMediaItems() }

    fun removeQueueItem(index: Int) = withPlayer { player ->
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) = withPlayer { player ->
        if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount) {
            player.moveMediaItem(from, to)
        }
    }

    fun stop() = withPlayer { it.stop() }

    fun setSleepTimer(minutes: Int) = withPlayer { player ->
        val endAt = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        Graph.prefs.sleepTimerEndAt = endAt
        val args = android.os.Bundle().apply { putLong(PlaybackService.ARG_SLEEP_END_AT, endAt) }
        player.sendCustomCommand(PlaybackService.SLEEP_COMMAND, args)
    }

    /** Restores the previous queue paused at its last position. */
    private fun restoreLastSession() {
        val player = controller ?: return
        if (player.mediaItemCount > 0) return
        val ids = Graph.prefs.lastQueueIds
        if (ids.isEmpty()) return
        scope.launch {
            val songs = Graph.music.songsByIds(ids)
            if (songs.isEmpty() || controller == null) return@launch
            val index = Graph.prefs.lastQueueIndex.coerceIn(0, songs.lastIndex)
            player.setMediaItems(songs.map { it.toMediaItem() }, index, Graph.prefs.lastPositionMs)
            player.prepare()
            player.pause()
            refresh()
        }
    }

    /** Song id at the current queue position, used by the mini player. */
    fun currentSongId(): Long = _state.value.songId

    fun queueItems(): List<MediaItem> {
        val player = controller ?: return emptyList()
        return (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
    }
}
