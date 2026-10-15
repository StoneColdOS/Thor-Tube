package io.github.stonecoldos.thortube.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.stonecoldos.thortube.AppLog
import io.github.stonecoldos.thortube.ThorTubeApp
import io.github.stonecoldos.thortube.data.Feeds
import io.github.stonecoldos.thortube.data.HOME_TABS
import io.github.stonecoldos.thortube.data.Video
import io.github.stonecoldos.thortube.data.ago
import io.github.stonecoldos.thortube.player.MediaSources
import io.github.stonecoldos.thortube.player.PlaybackAttempt
import io.github.stonecoldos.thortube.player.PlaybackPlanner
import io.github.stonecoldos.thortube.player.StreamCache
import io.github.stonecoldos.thortube.youtube.YouTubeAccount
import io.github.stonecoldos.thortube.youtube.YouTubeHistory
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The rail's destinations. */
enum class Screen(val label: String) { HOME("Home"), SUBS("Subs"), SEARCH("Search"), LIBRARY("Library"), CRT("CRT"), SETUP("Setup") }

/** What a grid shows: loading, a list, or what went wrong. */
data class FeedState(val videos: List<Video> = emptyList(), val loading: Boolean = false, val error: String? = null, val at: Long = 0)

/** The player screen's details about what's on. */
data class NowPlaying(
    val video: Video,
    val upNext: List<Video>,
    val title: String = video.title,
    val channel: String = video.uploaderName,
    val avatar: String? = null,
    val subscribers: String = "",
    val meta: String = "",
)

/**
 * Thor-Tube. One activity on the Thor's bottom screen: a rail on the left, the screen you picked,
 * and the player on top when something's on. Touch works everywhere; the controller moves a
 * highlight (the Thor's D-pad arrives as joystick motion, so it's turned into normal arrow keys).
 */
@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {

    private lateinit var settings: Settings
    lateinit var player: ExoPlayer
        private set
    private lateinit var sources: MediaSources

    var theme by mutableStateOf(ThemeMode.DARK)
        private set
    var screen by mutableStateOf(Screen.HOME)
    var tab by mutableStateOf(0)
        private set
    val feeds = mutableStateMapOf<Int, FeedState>()
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set
    var firstFrame by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var playerProblem by mutableStateOf<String?>(null)
        private set
    /** Bumps on L1/R1 and other keys the player screen reacts to. */
    var controlsPing by mutableStateOf(0)
        private set

    private var attempts: List<PlaybackAttempt> = emptyList()
    private var attemptIndex = 0
    private var playJob: Job? = null
    private var tickJob: Job? = null
    private var durationSecs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        settings = Settings(this)
        theme = settings.theme
        sources = MediaSources(ThorTubeApp.http)
        player = ExoPlayer.Builder(this).build().apply { addListener(listener) }

        setContent {
            val palette = paletteFor(theme, isSystemInDarkTheme())
            CompositionLocalProvider(LocalPalette provides palette) {
                Box(Modifier.fillMaxSize().background(palette.bg)) {
                    val playing = nowPlaying
                    if (playing != null) {
                        PlayerScreen(this@MainActivity, playing)
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            Rail(screen) { screen = it }
                            Box(Modifier.weight(1f).fillMaxSize()) {
                                when (screen) {
                                    Screen.HOME -> HomeScreen(this@MainActivity)
                                    Screen.SETUP -> SetupScreen(this@MainActivity)
                                    else -> ComingSoon(screen)
                                }
                            }
                        }
                    }
                }
            }
        }
        selectTab(0)
        handleLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    override fun onPause() {
        nowPlaying?.let { YouTubeHistory.flush(it.video.url, player.currentPosition / 1000f) }
        player.pause()
        super.onPause()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private fun handleLink(intent: Intent?) {
        intent?.getStringExtra(EXTRA_URL)?.let { url ->
            intent.removeExtra(EXTRA_URL)
            open(Video(url, "", 0, ""), emptyList())
        }
        intent?.getStringExtra(EXTRA_CHANNEL)?.let {
            intent.removeExtra(EXTRA_CHANNEL)
            AppLog.i(TAG, "Channel links aren't supported yet: $it")
        }
    }

    // ---------------------------------------------------------------- settings

    fun setThemeMode(mode: ThemeMode) {
        theme = mode
        settings.theme = mode
    }

    // ---------------------------------------------------------------- home

    fun selectTab(i: Int, refresh: Boolean = false) {
        tab = i.coerceIn(0, HOME_TABS.lastIndex)
        val state = feeds[tab]
        val stale = state == null || System.currentTimeMillis() - state.at > 15 * 60_000L
        if (!refresh && !stale && state?.error == null) return
        if (state?.loading == true) return
        val which = tab
        feeds[which] = (state ?: FeedState()).copy(loading = true, error = null)
        lifecycleScope.launch {
            val t = HOME_TABS[which]
            val result = runCatching { if (t.isForYou) Feeds.forYou() else Feeds.topic(t.query!!) }
            feeds[which] = result.fold(
                { FeedState(it, at = System.currentTimeMillis()) },
                {
                    AppLog.w(TAG, "Couldn't load ${t.label}", it)
                    FeedState(feeds[which]?.videos.orEmpty(), error = "Couldn't load ${t.label}. Check the connection.")
                },
            )
            Thumbnails.prefetch(feeds[which]?.videos.orEmpty().take(24).map { it.thumbnail })
        }
    }

    /** Forget cached feeds, e.g. after signing in or out. */
    fun reloadFeeds() {
        feeds.clear()
        selectTab(tab, refresh = true)
    }

    // ---------------------------------------------------------------- player

    fun open(video: Video, list: List<Video>) {
        val upNext = list.dropWhile { it.url != video.url }.drop(1).ifEmpty { list.filter { it.url != video.url } }
        nowPlaying?.let { YouTubeHistory.flush(it.video.url, player.currentPosition / 1000f) }
        nowPlaying = NowPlaying(video, upNext)
        firstFrame = false
        playerProblem = null
        positionMs = 0
        durationMs = video.durationSecs * 1000
        playJob?.cancel()
        playJob = lifecycleScope.launch { play(video) }
        startTicking()
    }

    fun close() {
        nowPlaying?.let { YouTubeHistory.flush(it.video.url, player.currentPosition / 1000f) }
        playJob?.cancel()
        tickJob?.cancel()
        player.stop()
        nowPlaying = null
    }

    fun togglePause() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.playWhenReady = !player.playWhenReady
        controlsPing++
    }

    fun seekBy(deltaMs: Long) {
        val d = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, d))
        positionMs = player.currentPosition
        controlsPing++
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        positionMs = ms
    }

    private suspend fun play(video: Video) {
        try {
            val info = StreamCache.load(video.url)
            durationSecs = info.duration
            durationMs = info.duration * 1000
            val subs = info.uploaderSubscriberCount
            nowPlaying = nowPlaying?.takeIf { it.video.url == video.url }?.copy(
                title = info.name,
                channel = info.uploaderName.orEmpty(),
                avatar = info.uploaderAvatars.maxByOrNull { it.height }?.url,
                subscribers = subscribers(subs),
                meta = listOfNotNull(
                    info.viewCount.takeIf { it > 0 }?.let { views(it) },
                    info.uploadDate?.instant?.toEpochMilli()?.let { ago(it) } ?: info.textualUploadDate,
                ).joinToString("  ·  "),
            )
            attempts = PlaybackPlanner.plan(info)
            attemptIndex = 0
            if (attempts.isEmpty()) {
                playerProblem = "This video can't be played here."
                return
            }
            playAttempt(0)
            YouTubeHistory.start(video.url, durationSecs)
        } catch (e: Exception) {
            AppLog.e(TAG, "Couldn't load ${video.url}", e)
            playerProblem = "Couldn't load this video.\n${e.message?.take(120).orEmpty()}"
        }
    }

    private fun playAttempt(startMs: Long) {
        val attempt = attempts[attemptIndex]
        AppLog.i(TAG, "Playing via ${attempt.label} (${attemptIndex + 1}/${attempts.size}) from ${startMs}ms")
        player.setMediaSource(sources.build(attempt, durationSecs), startMs)
        player.prepare()
        player.playWhenReady = true
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = lifecycleScope.launch {
            var lastHistory = 0L
            while (isActive) {
                positionMs = player.currentPosition.coerceAtLeast(0)
                player.duration.takeIf { it > 0 }?.let { durationMs = it }
                val now = SystemClock.uptimeMillis()
                if (now - lastHistory > 5_000) {
                    lastHistory = now
                    nowPlaying?.let { YouTubeHistory.tick(it.video.url, player.currentPosition / 1000f, player.isPlaying) }
                }
                delay(250)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onRenderedFirstFrame() {
            firstFrame = true
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                // Autoplay, like YouTube: on to the next one.
                nowPlaying?.let { np -> np.upNext.firstOrNull()?.let { open(it, np.upNext) } }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            AppLog.w(TAG, "Playback failed for ${attempts.getOrNull(attemptIndex)?.label}", error)
            if (attemptIndex + 1 < attempts.size) {
                attemptIndex++
                playAttempt(player.currentPosition)
            } else {
                playerProblem = "Couldn't play this video (${error.errorCodeName})."
            }
        }
    }

    // ---------------------------------------------------------------- controller

    /** Shoulder buttons: tabs on Home, skip 10 seconds in the player. */
    private fun shoulder(dir: Int) {
        when {
            nowPlaying != null -> seekBy(dir * 10_000L)
            screen == Screen.HOME -> selectTab((tab + dir).mod(HOME_TABS.size))
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            // A presses whatever's highlighted.
            KeyEvent.KEYCODE_BUTTON_A -> return super.dispatchKeyEvent(remap(event, KeyEvent.KEYCODE_DPAD_CENTER))
            // B goes back.
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (!down) onBackPressedDispatcher.onBackPressed()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (down && event.repeatCount == 0) shoulder(if (event.keyCode == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_START -> {
                if (down && event.repeatCount == 0 && nowPlaying == null) screen = Screen.SETUP
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun remap(e: KeyEvent, code: Int) =
        KeyEvent(e.downTime, e.eventTime, e.action, code, e.repeatCount, e.metaState, e.deviceId, e.scanCode, e.flags, e.source)

    private var heldX = 0
    private var heldY = 0
    private var repeatJob: Job? = null

    /** The Thor's D-pad (and left stick) arrive as joystick motion: turn them into arrow keys, with repeat while held. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val pad = event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!pad || event.action != MotionEvent.ACTION_MOVE) return super.dispatchGenericMotionEvent(event)
        val x = direction(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_X))
        val y = direction(event.getAxisValue(MotionEvent.AXIS_HAT_Y), event.getAxisValue(MotionEvent.AXIS_Y))
        if (x != heldX || y != heldY) {
            heldX = x
            heldY = y
            repeatJob?.cancel()
            val code = when {
                y < 0 -> KeyEvent.KEYCODE_DPAD_UP
                y > 0 -> KeyEvent.KEYCODE_DPAD_DOWN
                x < 0 -> KeyEvent.KEYCODE_DPAD_LEFT
                x > 0 -> KeyEvent.KEYCODE_DPAD_RIGHT
                else -> 0
            }
            if (code != 0) {
                arrow(code)
                repeatJob = lifecycleScope.launch {
                    delay(400)
                    while (isActive) {
                        arrow(code)
                        delay(110)
                    }
                }
            }
        }
        return true
    }

    private fun arrow(code: Int) {
        val t = SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    private fun direction(hat: Float, stick: Float): Int = when {
        hat.roundToInt() != 0 -> hat.roundToInt()
        stick <= -0.6f -> -1
        stick >= 0.6f -> 1
        else -> 0
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ---------------------------------------------------------------- YouTube account (used by Setup)

    var signInCode by mutableStateOf<YouTubeAccount.DeviceCode?>(null)
        private set
    var signInStatus by mutableStateOf("")
    var signedIn by mutableStateOf(YouTubeAccount.isSignedIn)
        private set
    private var signInJob: Job? = null

    fun startSignIn() {
        signInJob?.cancel()
        signInStatus = "Getting a code..."
        signInJob = lifecycleScope.launch {
            val code = runCatching { YouTubeAccount.startSignIn() }
                .onFailure { AppLog.w(TAG, "Couldn't start sign-in", it) }
                .getOrNull()
            if (code == null) {
                signInStatus = "Couldn't reach YouTube. Try again."
                return@launch
            }
            signInCode = code
            signInStatus = "Waiting for you to enter the code..."
            var wait = code.intervalSecs * 1000L
            while (isActive) {
                delay(wait)
                when (val r = YouTubeAccount.poll(code)) {
                    YouTubeAccount.Poll.Waiting -> Unit
                    YouTubeAccount.Poll.SlowDown -> wait += 2_000
                    YouTubeAccount.Poll.Done -> {
                        signInCode = null
                        signInStatus = ""
                        signedIn = true
                        reloadFeeds()
                        screen = Screen.HOME
                        return@launch
                    }
                    is YouTubeAccount.Poll.Failed -> {
                        signInCode = null
                        signInStatus = "${r.reason}. Try again."
                        return@launch
                    }
                }
            }
        }
    }

    fun cancelSignIn() {
        signInJob?.cancel()
        signInCode = null
        signInStatus = ""
    }

    fun signOut() {
        YouTubeAccount.signOut()
        YouTubeHistory.reset()
        signedIn = false
        reloadFeeds()
    }

    private fun subscribers(n: Long): String = when {
        n <= 0 -> ""
        n >= 1_000_000 -> "%.1fM subscribers".format(n / 1_000_000.0).replace(".0M", "M")
        n >= 1_000 -> "%dK subscribers".format(n / 1_000)
        else -> "$n subscribers"
    }

    private fun views(n: Long): String = when {
        n >= 1_000_000 -> "%.1fM views".format(n / 1_000_000.0).replace(".0M", "M")
        n >= 1_000 -> "%dK views".format(n / 1_000)
        else -> "$n views"
    }

    companion object {
        const val EXTRA_URL = "io.github.stonecoldos.thortube.URL"
        const val EXTRA_CHANNEL = "io.github.stonecoldos.thortube.CHANNEL"
        private const val TAG = "ThorTube"
    }
}
