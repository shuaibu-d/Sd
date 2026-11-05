package com.sdtech.studio.media

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Lifecycle-aware holder managing a single shared Media3 [ExoPlayer] instance across
 * Quick Preview, Voice-Over playback, and Exports preview.
 *
 * - Configures Media3 [AudioAttributes] with automatic audio focus handling (`handleAudioFocus = true`)
 *   and `handleAudioBecomingNoisy = true`, plus an explicit [AudioManager] focus listener fallback.
 * - Releases the shared [ExoPlayer] instance whenever the active screen reaches [Lifecycle.Event.ON_STOP].
 */
class PlayerHolder(
    context: Context,
    private val playerFactory: ((Context) -> ExoPlayer)? = null
) : LifecycleEventObserver {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private var exoPlayer: ExoPlayer? = null
    private var onEndedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((Throwable) -> Unit)? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentUri = MutableStateFlow<String?>(null)
    val currentUri: StateFlow<String?> = _currentUri.asStateFlow()

    var activePlayerCount: Int = 0
        private set

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
                abandonAudioFocus()
                val callback = onEndedCallback
                onEndedCallback = null
                callback?.invoke()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _isPlaying.value = false
            abandonAudioFocus()
            val callback = onErrorCallback
            onErrorCallback = null
            callback?.invoke(error)
        }
    }

    /**
     * Returns the single shared [ExoPlayer] instance, creating it lazily if needed.
     */
    fun getOrCreatePlayer(isSpeech: Boolean = true): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) {
            updateAudioAttributes(existing, isSpeech)
            return existing
        }
        val created = (playerFactory?.invoke(appContext) ?: ExoPlayer.Builder(appContext)
            .setLooper(Looper.getMainLooper())
            .setHandleAudioBecomingNoisy(true)
            .build()).apply {
            updateAudioAttributes(this, isSpeech)
            addListener(playerListener)
        }
        exoPlayer = created
        activePlayerCount = 1
        return created
    }

    fun currentPlayerOrNull(): ExoPlayer? = exoPlayer

    private fun updateAudioAttributes(player: ExoPlayer, isSpeech: Boolean) {
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(if (isSpeech) C.AUDIO_CONTENT_TYPE_SPEECH else C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        runCatching {
            player.setAudioAttributes(attrs, /* handleAudioFocus = */ true)
        }
    }

    private fun requestAudioFocus(isSpeech: Boolean): Boolean {
        val manager = audioManager ?: return true
        return runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(
                        if (isSpeech) android.media.AudioAttributes.CONTENT_TYPE_SPEECH
                        else android.media.AudioAttributes.CONTENT_TYPE_MOVIE
                    )
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
                focusRequest = req
                manager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        }.getOrDefault(true)
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest?.let { manager.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(focusChangeListener)
            }
        }
    }

    fun playFile(
        file: File,
        startPositionMs: Long = 0L,
        isSpeech: Boolean = true,
        onEnded: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        if (!file.isFile) {
            onError?.invoke(IllegalArgumentException("Audio file does not exist: ${file.path}"))
            return
        }
        playUri(
            uri = Uri.fromFile(file),
            startPositionMs = startPositionMs,
            isSpeech = isSpeech,
            onEnded = onEnded,
            onError = onError
        )
    }

    fun playUri(
        uri: Uri,
        startPositionMs: Long = 0L,
        isSpeech: Boolean = true,
        onEnded: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        if (!requestAudioFocus(isSpeech)) {
            _isPlaying.value = false
            onError?.invoke(IllegalStateException("Audio focus could not be acquired."))
            return
        }
        onEndedCallback = onEnded
        onErrorCallback = onError
        _currentUri.value = uri.toString()
        runCatching {
            val player = getOrCreatePlayer(isSpeech)
            player.stop()
            player.setMediaItem(MediaItem.fromUri(uri), startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = true
            _isPlaying.value = true
        }.onFailure { err ->
            _isPlaying.value = false
            abandonAudioFocus()
            onError?.invoke(err)
        }
    }

    fun pause() {
        runCatching { exoPlayer?.pause() }
        _isPlaying.value = false
        abandonAudioFocus()
    }

    fun resume(isSpeech: Boolean = true) {
        val player = exoPlayer ?: return
        if (requestAudioFocus(isSpeech)) {
            runCatching {
                player.play()
                _isPlaying.value = true
            }
        }
    }

    fun seekTo(positionMs: Long) {
        runCatching { exoPlayer?.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    fun stop() {
        onEndedCallback = null
        onErrorCallback = null
        runCatching { exoPlayer?.stop() }
        _isPlaying.value = false
        _currentUri.value = null
        abandonAudioFocus()
    }

    /**
     * Stops playback, abandons audio focus, and releases the shared [ExoPlayer] instance.
     */
    fun release() {
        onEndedCallback = null
        onErrorCallback = null
        val current = exoPlayer
        exoPlayer = null
        activePlayerCount = 0
        _isPlaying.value = false
        _currentUri.value = null
        abandonAudioFocus()
        if (current != null) {
            runCatching {
                current.removeListener(playerListener)
                current.stop()
                current.release()
            }
        }
    }

    fun currentPositionMs(): Long = runCatching { exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L }.getOrDefault(0L)

    fun durationMs(): Long = runCatching {
        val d = exoPlayer?.duration ?: C.TIME_UNSET
        if (d == C.TIME_UNSET || d < 0L) 0L else d
    }.getOrDefault(0L)

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
            release()
        }
    }
}

/**
 * Binds [playerHolder] to the current [LocalLifecycleOwner] so the shared [ExoPlayer]
 * is automatically released on `ON_STOP` and when the composable leaves composition.
 */
@Composable
fun BindPlayerHolderLifecycle(
    playerHolder: PlayerHolder,
    onStop: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playerHolder) {
        val observer = LifecycleEventObserver { source, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) {
                onStop()
                playerHolder.onStateChanged(source, event)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onStop()
            playerHolder.release()
        }
    }
}
