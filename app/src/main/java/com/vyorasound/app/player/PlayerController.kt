package com.vyorasound.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vyorasound.app.domain.model.RepeatMode
import com.vyorasound.app.domain.model.Song
import com.vyorasound.app.domain.repository.LibraryRepository
import com.vyorasound.app.domain.repository.MusicRepository
import com.vyorasound.app.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val lyrics: String? = null,
    val relatedSongs: List<Song> = emptyList(),
    val isLoadingExtras: Boolean = false,
    val errorMessage: String? = null,
    val requiresOptionalLogin: Boolean = false
)

class PlayerController(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var watchNextJob: Job? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private val songCatalog = LinkedHashMap<String, Song>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying, errorMessage = null, requiresOptionalLogin = false) }
            if (isPlaying) startProgressLoop() else stopProgressLoop()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            _uiState.update {
                it.copy(
                    isBuffering = buffering,
                    durationMs = resolveDuration(mediaController, it.currentSong)
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncStateFromPlayer()
            val song = mediaItem?.toSong() ?: return
            scope.launch {
                libraryRepository.recordRecentlyPlayed(song)
                fetchExtrasForSong(song, appendUpNextIfSingle = false)
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncStateFromPlayer()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.update { it.copy(repeatMode = repeatMode.toDomainRepeatMode()) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val activeSong = _uiState.value.currentSong
            val rootCause = error.cause?.message ?: error.message ?: "Unknown playback error"
            val loginRequired = hasLoginRequiredCause(error) ||
                rootCause.contains("tanpa login", ignoreCase = true) ||
                rootCause.contains("LOGIN_REQUIRED", ignoreCase = true)
            PlaybackDiagnostics.w(
                "ControllerPlayerError | title=\"${activeSong?.title ?: "-"}\" | " +
                    "videoId=${activeSong?.id ?: "-"} | errorCode=${error.errorCode} (${error.errorCodeName}) | " +
                    "loginRequired=$loginRequired | rootCause=$rootCause"
            )
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    requiresOptionalLogin = loginRequired,
                    errorMessage = error.cause?.message
                        ?: error.message
                        ?: "Pemutaran gagal. Ketuk coba lagi atau pilih lagu lain."
                )
            }
        }
    }

    init {
        connectController()
    }

    private fun connectController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { controller ->
                mediaController = controller
                controller.addListener(playerListener)
                scope.launch {
                    val savedShuffle = libraryRepository.shuffleEnabled.first()
                    val savedRepeat = libraryRepository.repeatMode.first()
                    controller.shuffleModeEnabled = savedShuffle
                    controller.repeatMode = savedRepeat.toPlayerRepeatMode()
                    syncStateFromPlayer()
                    pendingAction?.invoke(controller)
                    pendingAction = null
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(action: (MediaController) -> Unit) {
        val controller = mediaController
        if (controller != null) {
            action(controller)
        } else {
            pendingAction = action
            if (controllerFuture == null) {
                connectController()
            }
        }
    }

    fun playSong(song: Song, queueContext: List<Song>? = null) {
        val list = if (!queueContext.isNullOrEmpty() && queueContext.any { it.id == song.id }) {
            queueContext
        } else {
            listOf(song)
        }
        val startIndex = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        list.forEach {
            songCatalog[it.id] = it
            musicRepository.registerSongTitle(it.id, it.title)
        }
        _uiState.update { it.copy(errorMessage = null, requiresOptionalLogin = false, isBuffering = true) }

        withController { controller ->
            val items = list.map {
                PlaybackService.buildMediaItem(it, musicRepository.getCachedStream(it.id))
            }
            controller.setMediaItems(items, startIndex, 0L)
            controller.prepare()
            controller.play()
            syncStateFromPlayer()
            scope.launch {
                libraryRepository.recordRecentlyPlayed(song)
                fetchExtrasForSong(song, appendUpNextIfSingle = list.size == 1)
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        songs.forEach {
            songCatalog[it.id] = it
            musicRepository.registerSongTitle(it.id, it.title)
        }
        _uiState.update { it.copy(errorMessage = null, requiresOptionalLogin = false, isBuffering = true) }

        withController { controller ->
            val items = songs.map {
                PlaybackService.buildMediaItem(it, musicRepository.getCachedStream(it.id))
            }
            controller.setMediaItems(items, safeIndex, 0L)
            controller.prepare()
            controller.play()
            syncStateFromPlayer()
            val active = songs[safeIndex]
            scope.launch {
                libraryRepository.recordRecentlyPlayed(active)
                fetchExtrasForSong(active, appendUpNextIfSingle = songs.size == 1)
            }
        }
    }

    fun playNext(song: Song) {
        songCatalog[song.id] = song
        musicRepository.registerSongTitle(song.id, song.title)
        withController { controller ->
            if (controller.mediaItemCount == 0) {
                playSong(song)
                return@withController
            }
            val insertIndex = (controller.currentMediaItemIndex + 1)
                .coerceIn(0, controller.mediaItemCount)
            controller.addMediaItem(
                insertIndex,
                PlaybackService.buildMediaItem(song, musicRepository.getCachedStream(song.id))
            )
            syncStateFromPlayer()
        }
    }

    fun addToQueue(song: Song) {
        songCatalog[song.id] = song
        musicRepository.registerSongTitle(song.id, song.title)
        withController { controller ->
            if (controller.mediaItemCount == 0) {
                playSong(song)
                return@withController
            }
            controller.addMediaItem(
                PlaybackService.buildMediaItem(song, musicRepository.getCachedStream(song.id))
            )
            syncStateFromPlayer()
        }
    }

    fun removeFromQueue(index: Int) {
        withController { controller ->
            if (index in 0 until controller.mediaItemCount) {
                controller.removeMediaItem(index)
                syncStateFromPlayer()
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        withController { controller ->
            if (fromIndex in 0 until controller.mediaItemCount &&
                toIndex in 0 until controller.mediaItemCount &&
                fromIndex != toIndex
            ) {
                controller.moveMediaItem(fromIndex, toIndex)
                syncStateFromPlayer()
            }
        }
    }

    fun jumpToQueueIndex(index: Int) {
        withController { controller ->
            if (index in 0 until controller.mediaItemCount) {
                controller.seekToDefaultPosition(index)
                controller.prepare()
                controller.play()
                syncStateFromPlayer()
            }
        }
    }

    fun togglePlayPause() {
        withController { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                    controller.prepare()
                }
                controller.play()
            }
        }
    }

    fun retryCurrent() {
        withController { controller ->
            if (controller.mediaItemCount > 0) {
                controller.currentMediaItem?.mediaId?.let { videoId ->
                    musicRepository.invalidateStreamCache(videoId)
                }
                _uiState.update { it.copy(errorMessage = null, requiresOptionalLogin = false, isBuffering = true) }
                controller.prepare()
                controller.play()
            }
        }
    }

    fun skipToNext() {
        withController { controller ->
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
                controller.play()
            }
        }
    }

    fun skipToPrevious() {
        withController { controller ->
            if (controller.currentPosition > 3500L) {
                controller.seekTo(0L)
            } else if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
                controller.play()
            } else {
                controller.seekTo(0L)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        withController { controller ->
            controller.seekTo(positionMs.coerceAtLeast(0L))
            _uiState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
        }
    }

    fun toggleShuffle() {
        withController { controller ->
            val next = !controller.shuffleModeEnabled
            controller.shuffleModeEnabled = next
            _uiState.update { it.copy(shuffleEnabled = next) }
            scope.launch { libraryRepository.setShuffleEnabled(next) }
        }
    }

    fun cycleRepeatMode() {
        withController { controller ->
            val next = when (_uiState.value.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            controller.repeatMode = next.toPlayerRepeatMode()
            _uiState.update { it.copy(repeatMode = next) }
            scope.launch { libraryRepository.setRepeatMode(next) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, requiresOptionalLogin = false) }
    }

    private fun hasLoginRequiredCause(error: Throwable?): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 10) {
            if (current is com.vyorasound.app.repository.LoginRequiredPlaybackException) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    private fun fetchExtrasForSong(song: Song, appendUpNextIfSingle: Boolean) {
        watchNextJob?.cancel()
        watchNextJob = scope.launch {
            _uiState.update { it.copy(isLoadingExtras = true, lyrics = null, relatedSongs = emptyList()) }
            val watchNext = musicRepository.getWatchNext(song.id).getOrNull()
            if (watchNext != null) {
                if (appendUpNextIfSingle && watchNext.upNextSongs.isNotEmpty()) {
                    val extras = watchNext.upNextSongs.filter { it.id != song.id }.take(20)
                    extras.forEach {
                        songCatalog[it.id] = it
                        musicRepository.registerSongTitle(it.id, it.title)
                    }
                    mediaController?.let { controller ->
                        val currentIds = (0 until controller.mediaItemCount)
                            .map { idx -> controller.getMediaItemAt(idx).mediaId }
                            .toSet()
                        val toAdd = extras.filterNot { it.id in currentIds }
                        if (toAdd.isNotEmpty()) {
                            controller.addMediaItems(
                                toAdd.map { PlaybackService.buildMediaItem(it, musicRepository.getCachedStream(it.id)) }
                            )
                            syncStateFromPlayer()
                        }
                    }
                }
                val lyrics = watchNext.lyricsBrowseId?.let { id ->
                    musicRepository.getLyrics(id).getOrNull()
                }
                val related = watchNext.relatedBrowseId?.let { id ->
                    musicRepository.getRelatedSongs(id).getOrNull().orEmpty()
                }.orEmpty().ifEmpty {
                    watchNext.upNextSongs.filter { it.id != song.id }.take(12)
                }
                _uiState.update {
                    it.copy(
                        lyrics = lyrics,
                        relatedSongs = related,
                        isLoadingExtras = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingExtras = false) }
            }
        }
    }

    private fun syncStateFromPlayer() {
        val controller = mediaController ?: return
        val queue = (0 until controller.mediaItemCount).map { idx ->
            controller.getMediaItemAt(idx).toSong()
        }
        val activeIndex = controller.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val current = queue.getOrNull(activeIndex)
        _uiState.update {
            it.copy(
                currentSong = current,
                queue = queue,
                currentIndex = activeIndex,
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = resolveDuration(controller, current),
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode.toDomainRepeatMode()
            )
        }
    }

    private fun resolveDuration(controller: MediaController?, song: Song?): Long {
        val playerDuration = controller?.duration ?: 0L
        return when {
            playerDuration > 0L -> playerDuration
            song != null && song.durationMs > 0L -> song.durationMs
            else -> 0L
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _uiState.update {
                        it.copy(
                            positionMs = controller.currentPosition.coerceAtLeast(0L),
                            durationMs = resolveDuration(controller, it.currentSong)
                        )
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun MediaItem.toSong(): Song {
        songCatalog[mediaId]?.let { return it }
        val meta = mediaMetadata
        val extras = meta.extras
        return Song(
            id = mediaId,
            title = meta.title?.toString().orEmpty().ifBlank { "Unknown Track" },
            artist = meta.artist?.toString().orEmpty().ifBlank { "Unknown Artist" },
            artistId = extras?.getString("artist_id"),
            album = meta.albumTitle?.toString(),
            albumId = extras?.getString("album_id"),
            durationText = extras?.getString("duration_text").orEmpty(),
            durationMs = extras?.getLong("duration_ms", 0L) ?: 0L,
            thumbnailUrl = meta.artworkUri?.toString().orEmpty(),
            isExplicit = extras?.getBoolean("is_explicit", false) ?: false
        )
    }

    private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    private fun Int.toDomainRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        else -> RepeatMode.OFF
    }

    fun release() {
        stopProgressLoop()
        watchNextJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
