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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
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
import io.github.stonecoldos.thortube.data.Comment
import io.github.stonecoldos.thortube.data.Comments
import io.github.stonecoldos.thortube.data.Feeds
import io.github.stonecoldos.thortube.data.HOME_TABS
import io.github.stonecoldos.thortube.data.Video
import io.github.stonecoldos.thortube.data.ago
import io.github.stonecoldos.thortube.player.MediaSources
import io.github.stonecoldos.thortube.player.PlaybackAttempt
import io.github.stonecoldos.thortube.player.PlaybackPlanner
import io.github.stonecoldos.thortube.data.CaptionTrack
import io.github.stonecoldos.thortube.data.Captions
import io.github.stonecoldos.thortube.data.Cue
import io.github.stonecoldos.thortube.player.StreamCache
import io.github.stonecoldos.thortube.youtube.YouTubeAccount
import io.github.stonecoldos.thortube.youtube.YouTubeActions
import io.github.stonecoldos.thortube.crt.CrtRenderer
import io.github.stonecoldos.thortube.youtube.YouTubeFeed
import io.github.stonecoldos.thortube.youtube.YtChannel
import io.github.stonecoldos.thortube.youtube.YtPlaylist
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

enum class PlayerTab { UP_NEXT, COMMENTS }

/** The comments under the current video. */
data class CommentsUi(
    val items: List<Comment> = emptyList(),
    val next: org.schabi.newpipe.extractor.Page? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val count: Int = -1,
    val disabled: Boolean = false,
)

/** One comment's replies, opened from the list. */
data class ThreadUi(
    val parent: Comment,
    val items: List<Comment> = emptyList(),
    val next: org.schabi.newpipe.extractor.Page? = null,
    val loading: Boolean = false,
)

/** Your playlists in the Library. */
data class PlaylistsUi(
    val items: List<io.github.stonecoldos.thortube.youtube.YtPlaylist> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

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

    /** Drives the player's slide in and out; [lastPlaying] keeps it drawn while it slides away. */
    val playerShown = MutableTransitionState(false)
    var lastPlaying by mutableStateOf<NowPlaying?>(null)
        private set

    /** Where each Home tab was scrolled to, so coming back from a video lands where you left. */
    private val gridStates = HashMap<Int, LazyGridState>()
    fun gridState(tab: Int): LazyGridState = gridStates.getOrPut(tab) { LazyGridState() }

    /** The video you last opened, so Home can put the highlight back on it. */
    var returnTo by mutableStateOf<String?>(null)

    private var attempts: List<PlaybackAttempt> = emptyList()
    private var currentInfo: org.schabi.newpipe.extractor.stream.StreamInfo? = null

    // ---- player options: quality, speed, captions
    var optionsOpen by mutableStateOf(false)
    /** Tallest picture wanted (0 = Auto); remembered between videos and launches. */
    var quality by mutableStateOf(0)
        private set
    /** Picture sizes the current video offers, biggest first. */
    var qualities by mutableStateOf<List<Int>>(emptyList())
        private set
    /** Height actually playing right now (0 = unknown). */
    var playingHeight by mutableStateOf(0)
        private set
    var speed by mutableStateOf(1f)
        private set
    var captionTracks by mutableStateOf<List<CaptionTrack>>(emptyList())
        private set
    var caption by mutableStateOf<CaptionTrack?>(null)
        private set
    var captionText by mutableStateOf("")
        private set
    private var cues: List<Cue> = emptyList()
    private var captionJob: Job? = null

    fun chooseQuality(height: Int) {
        quality = height
        settings.quality = height
        val info = currentInfo ?: return
        val wasPlaying = player.playWhenReady
        val at = player.currentPosition
        attempts = PlaybackPlanner.plan(info, height)
        attemptIndex = 0
        if (attempts.isNotEmpty()) {
            playAttempt(at)
            player.playWhenReady = wasPlaying
        }
    }

    fun chooseSpeed(value: Float) {
        speed = value
        player.setPlaybackSpeed(value)
    }

    /** null turns captions off. The choice sticks for later videos. */
    fun chooseCaption(track: CaptionTrack?) {
        settings.captionLang = track?.lang
        useCaption(track)
    }

    private fun useCaption(track: CaptionTrack?) {
        captionJob?.cancel()
        caption = track
        cues = emptyList()
        captionText = ""
        if (track == null) return
        captionJob = lifecycleScope.launch {
            cues = Captions.load(track)
            if (cues.isEmpty() && caption == track) say("Couldn't load captions")
        }
    }
    private var attemptIndex = 0
    private var playJob: Job? = null
    private var tickJob: Job? = null
    private var durationSecs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        settings = Settings(this)
        theme = settings.theme
        crtMode = settings.crt
        quality = settings.quality
        sources = MediaSources(ThorTubeApp.http)
        player = ExoPlayer.Builder(this).build().apply { addListener(listener) }

        setContent {
            val palette = paletteFor(theme, isSystemInDarkTheme())
            CompositionLocalProvider(LocalPalette provides palette) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxSize().crtPower(power.value).background(palette.bg)) {
                    // The player slides up over everything. Once it fully covers the screen, the screens
                    // underneath step aside so the controller can't wander behind it; they come back
                    // (scrolled to where you were) the moment it starts sliding away.
                    val shown = playerShown
                    if (!(shown.currentState && shown.targetState) && !commentsPageOpen) {
                        Row(Modifier.fillMaxSize()) {
                            LaunchedEffect(signedIn) {
                                me = if (signedIn) runCatching { YouTubeFeed.me() }.getOrNull() else null
                                if (signedIn) AppLog.d(TAG, "Account: name=${me?.first?.isNotBlank()} photo=${me?.second}")
                            }
                            Rail(screen, signedIn, me, onSwipe = { down -> moveScreen(down) }) { screen = it }
                            Crossfade(screen, Modifier.weight(1f).fillMaxSize(), animationSpec = tween(180), label = "screen") { s ->
                                Box(Modifier.fillMaxSize()) {
                                    when (s) {
                                        Screen.HOME -> HomeScreen(this@MainActivity)
                                        Screen.SEARCH -> SearchScreen(this@MainActivity)
                                        Screen.SUBS -> SubsScreen(this@MainActivity)
                                        Screen.LIBRARY -> LibraryScreen(this@MainActivity)
                                        Screen.CRT -> CrtScreen(this@MainActivity)
                                        Screen.SETUP -> SetupScreen(this@MainActivity)
                                        else -> ComingSoon(s)
                                    }
                                }
                            }
                        }
                    }
                    // Comments with the whole screen, video in the corner.
                    AnimatedVisibility(commentsPageOpen, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
                        CommentsPage(this@MainActivity)
                    }
                    AnimatedVisibility(
                        visibleState = shown,
                        enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)),
                        exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180)),
                    ) {
                        lastPlaying?.let { PlayerScreen(this@MainActivity, it) }
                    }
                    // The mini-player, bottom right, over whatever you're doing.
                    AnimatedVisibility(
                        visible = nowPlaying != null && minimized && !shown.targetState,
                        enter = fadeIn(tween(200)) + scaleIn(tween(220), initialScale = 0.85f),
                        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.85f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                    ) {
                        lastPlaying?.let { MiniPlayer(this@MainActivity, it, Modifier) }
                    }
                    Toast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
                }
                    ScreenBanner(screenBanner, Modifier.align(Alignment.Center))
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
        if (!moving) player.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (moving) {
            moving = false
            if (resumeAfterMove) player.play()
        }
    }

    // ---------------------------------------------------------------- moving between the Thor's screens

    private var moving = false
    /** CRT power effect: 1 = picture fully on, 0 = collapsed to a dot. */
    val power = androidx.compose.animation.core.Animatable(1f)

    // Animations need Compose's frame clock, which lifecycleScope doesn't carry; AndroidUiDispatcher does.
    private suspend fun powerOff() = kotlinx.coroutines.withContext(androidx.compose.ui.platform.AndroidUiDispatcher.Main) {
        power.animateTo(0f, androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutLinearInEasing))
    }
    private suspend fun powerOn() = kotlinx.coroutines.withContext(androidx.compose.ui.platform.AndroidUiDispatcher.Main) {
        power.snapTo(0f)
        power.animateTo(1f, androidx.compose.animation.core.tween(520, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
    }

    /** Shown for a moment after a move: true = arrived on the bottom screen, false = the top. */
    var screenBanner by mutableStateOf<Boolean?>(null)
        private set
    private var bannerJob: Job? = null

    private fun buzz() = runCatching {
        val v = getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        v.vibrate(android.os.VibrationEffect.createOneShot(45, 200))
    }

    private fun announceScreen(down: Boolean) {
        bannerJob?.cancel()
        screenBanner = down
        bannerJob = lifecycleScope.launch {
            delay(1_400)
            screenBanner = null
        }
    }
    private var resumeAfterMove = false
    private var pendingStartMs = 0L

    /** The Thor's top screen is the default display; the bottom one is the other built-in screen. */
    private fun otherScreenId(down: Boolean): Int? = if (!down) android.view.Display.DEFAULT_DISPLAY else
        getSystemService(android.hardware.display.DisplayManager::class.java).displays.firstOrNull {
            it.displayId != android.view.Display.DEFAULT_DISPLAY && it.isValid &&
                (it.flags and android.view.Display.FLAG_PRIVATE) == 0
        }?.displayId

    /** Swipe on the sidebar: send the whole app to the bottom screen (down) or the top one (up). */
    fun moveScreen(down: Boolean) {
        val here = display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
        val target = otherScreenId(down)
        if (target == null) return say("Couldn't find the bottom screen")
        if (target == here) return say(if (down) "Already on the bottom screen" else "Already on the top screen")
        AppLog.i(TAG, "Moving from display $here to $target")
        buzz()
        resumeAfterMove = player.playWhenReady
        moving = true
        val options = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(target).toBundle()
        lifecycleScope.launch {
            // The old TV switch-off: picture squeezes to a white line, then a dot.
            powerOff()
            startActivity(Intent(this@MainActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
            // Android normally carries the running app across. If it didn't, start over on the other
            // screen and pick the video back up where it was.
            repeat(12) {
                delay(100)
                if ((display?.displayId ?: here) != here) {
                    AppLog.i(TAG, "Moved to display $target")
                    buzz()
                    launch { powerOn() }
                    announceScreen(down)
                    return@launch
                }
            }
            AppLog.w(TAG, "Android kept us on display $here; relaunching on $target")
            val restart = Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_MOVED, if (down) 1 else 0)
            nowPlaying?.let {
                restart.putExtra(EXTRA_URL, it.video.url)
                restart.putExtra(EXTRA_POSITION, player.currentPosition)
            }
            val ctx = applicationContext
            moving = false
            finishAndRemoveTask()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                runCatching { ctx.startActivity(restart, options) }
                    .onFailure { AppLog.w(TAG, "Relaunch on display $target failed", it) }
            }, 350)
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private fun handleLink(intent: Intent?) {
        intent?.getIntExtra(EXTRA_MOVED, -1)?.takeIf { it >= 0 }?.let {
            intent.removeExtra(EXTRA_MOVED)
            buzz()
            lifecycleScope.launch { powerOn() }
            announceScreen(it == 1)
        }
        intent?.getStringExtra(EXTRA_URL)?.let { url ->
            intent.removeExtra(EXTRA_URL)
            pendingStartMs = intent.getLongExtra(EXTRA_POSITION, 0L)
            intent.removeExtra(EXTRA_POSITION)
            open(Video(url, "", 0, ""), emptyList())
        }
        intent?.getStringExtra(EXTRA_CHANNEL)?.let {
            intent.removeExtra(EXTRA_CHANNEL)
            AppLog.i(TAG, "Channel links aren't supported yet: $it")
        }
    }

    // ---------------------------------------------------------------- settings

    // ---------------------------------------------------------------- CRT effect

    var crtMode by mutableStateOf(CrtMode.OFF)
        private set
    private val crtViews = mutableListOf<CrtRenderer>()
    private var crtActive: CrtRenderer? = null

    fun setCrt(mode: CrtMode) {
        crtMode = mode
        settings.crt = mode
    }

    /** A tube view came up (full player or mini-player): its renderer takes over the picture. */
    fun crtAttached(r: CrtRenderer) {
        crtViews += r
        r.applyLook(crtMode.look)
        player.videoSize.let { v -> r.setVideoSize(v.width, v.height, v.pixelWidthHeightRatio) }
        r.targetStatic = if (firstFrame) 0f else 1f
    }

    fun crtSurfaceReady(r: CrtRenderer, surface: android.view.Surface) {
        crtActive = r
        player.setVideoSurface(surface)
    }

    fun crtDetached(r: CrtRenderer) {
        crtViews -= r
        if (crtActive === r) {
            r.playerSurface?.let { player.clearVideoSurface(it) }
            crtActive = null
        }
    }

    // ---------------------------------------------------------------- messages

    /** A short message along the bottom, e.g. "Saved to Watch later". */
    var toast by mutableStateOf<String?>(null)
        private set
    private var toastJob: Job? = null

    fun say(text: String) {
        toast = text
        toastJob?.cancel()
        toastJob = lifecycleScope.launch {
            delay(2_200)
            toast = null
        }
    }

    // ---------------------------------------------------------------- like, subscribe, save

    var liked by mutableStateOf(false)
        private set
    var subscribed by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set
    /** True once we know the like/subscribe state for what's playing. */
    var actionsReady by mutableStateOf(false)
        private set
    private var channelId: String? = null
    private var stateJob: Job? = null

    private fun loadVideoState(video: Video, uploaderUrl: String?) {
        liked = false
        subscribed = false
        saved = false
        actionsReady = false
        channelId = uploaderUrl?.let { Regex("UC[A-Za-z0-9_-]{22}").find(it)?.value }
        stateJob?.cancel()
        if (!signedIn || video.id.isEmpty()) return
        stateJob = lifecycleScope.launch {
            YouTubeActions.state(video.id)?.let {
                liked = it.liked
                subscribed = it.subscribed
                if (channelId == null) channelId = it.channelId
            }
            actionsReady = true
        }
    }

    fun toggleLike() {
        val v = nowPlaying?.video ?: return
        if (!signedIn) return say("Sign in (Setup) to like videos")
        val on = !liked
        liked = on
        lifecycleScope.launch {
            if (!YouTubeActions.like(v.id, on)) {
                liked = !on
                say("Couldn't update the like")
            }
        }
    }

    fun toggleSubscribe() {
        val np = nowPlaying ?: return
        if (!signedIn) return say("Sign in (Setup) to subscribe")
        val id = channelId ?: return say("Couldn't find this channel")
        val on = !subscribed
        subscribed = on
        lifecycleScope.launch {
            if (YouTubeActions.subscribe(id, on)) {
                say(if (on) "Subscribed to ${np.channel}" else "Unsubscribed from ${np.channel}")
                subsChannels = emptyList()
                subsFeed = FeedState()
            } else {
                subscribed = !on
                say("Couldn't update the subscription")
            }
        }
    }

    fun toggleSave() {
        val v = nowPlaying?.video ?: return
        if (!signedIn) return say("Sign in (Setup) to save videos")
        val on = !saved
        saved = on
        lifecycleScope.launch {
            if (YouTubeActions.watchLater(v.id, on)) {
                say(if (on) "Saved to Watch later" else "Removed from Watch later")
                libFeeds.remove(LibTab.WATCH_LATER)
            } else {
                saved = !on
                say("Couldn't update Watch later")
            }
        }
    }

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

    var minimized by mutableStateOf(false)
        private set
    /** The video filling the whole screen. */
    var fullscreen by mutableStateOf(false)

    fun toggleFullscreen() {
        if (nowPlaying == null || minimized) return
        fullscreen = !fullscreen
        controlsPing++
    }
    var commentsPageOpen by mutableStateOf(false)
        private set

    /** Shrink the video into the corner and keep it playing. */
    fun minimize() {
        if (nowPlaying == null) return
        fullscreen = false
        minimized = true
        playerShown.targetState = false
    }

    /** Back to the full player. */
    fun expand() {
        if (nowPlaying == null) return
        minimized = false
        commentsPageOpen = false
        playerShown.targetState = true
    }

    fun openCommentsPage() {
        playerTab = PlayerTab.COMMENTS
        commentsPageOpen = true
        minimize()
    }

    fun closeCommentsPage() {
        commentsPageOpen = false
        thread = null
    }

    fun open(video: Video, list: List<Video>, stayMinimized: Boolean = false) {
        val upNext = list.dropWhile { it.url != video.url }.drop(1).ifEmpty { list.filter { it.url != video.url } }
        nowPlaying?.let { YouTubeHistory.flush(it.video.url, player.currentPosition / 1000f) }
        nowPlaying = NowPlaying(video, upNext)
        lastPlaying = nowPlaying
        if (!(stayMinimized && minimized)) {
            minimized = false
            commentsPageOpen = false
            playerShown.targetState = true
        }
        returnTo = video.url
        if (!commentsPageOpen) playerTab = PlayerTab.UP_NEXT
        thread = null
        loadComments(video.url)
        firstFrame = false
        crtViews.forEach { it.targetStatic = 1f }   // a burst of snow until the new picture arrives
        playerProblem = null
        optionsOpen = false
        currentInfo = null
        qualities = emptyList()
        captionTracks = emptyList()
        useCaption(null)
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
        fullscreen = false
        minimized = false
        commentsPageOpen = false
        playerShown.targetState = false
        commentsJob?.cancel()
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
            if (nowPlaying != null) lastPlaying = nowPlaying
            loadVideoState(video, info.uploaderUrl)
            currentInfo = info
            qualities = PlaybackPlanner.heights(info)
            captionTracks = Captions.tracks(info)
            useCaption(settings.captionLang?.let { Captions.pick(captionTracks, it) })
            attempts = PlaybackPlanner.plan(info, quality)
            attemptIndex = 0
            if (attempts.isEmpty()) {
                playerProblem = "This video can't be played here."
                return
            }
            playAttempt(pendingStartMs.also { pendingStartMs = 0 })
            YouTubeHistory.start(video.url, durationSecs)
        } catch (e: Exception) {
            AppLog.e(TAG, "Couldn't load ${video.url}", e)
            playerProblem = "Couldn't load this video.\n${e.message?.take(120).orEmpty()}"
        }
    }

    private fun playAttempt(startMs: Long) {
        val attempt = attempts[attemptIndex]
        AppLog.i(TAG, "Playing via ${attempt.label} (${attemptIndex + 1}/${attempts.size}) from ${startMs}ms")
        playingHeight = when (attempt) {
            is PlaybackAttempt.Split -> attempt.video.height
            is PlaybackAttempt.Muxed -> attempt.stream.height
            is PlaybackAttempt.Hls -> 0
        }
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
                if (cues.isNotEmpty()) captionText = Captions.at(cues, positionMs)
                player.duration.takeIf { it > 0 }?.let { durationMs = it }
                val now = SystemClock.uptimeMillis()
                if (now - lastHistory > 5_000) {
                    lastHistory = now
                    nowPlaying?.let { YouTubeHistory.tick(it.video.url, player.currentPosition / 1000f, player.isPlaying) }
                }
                delay(if (cues.isNotEmpty()) 100 else 250)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onRenderedFirstFrame() {
            firstFrame = true
            crtViews.forEach { it.targetStatic = 0f }
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            crtViews.forEach { it.setVideoSize(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio) }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                // Autoplay, like YouTube: on to the next one.
                nowPlaying?.let { np -> np.upNext.firstOrNull()?.let { open(it, np.upNext, stayMinimized = true) } }
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

    // ---------------------------------------------------------------- subscriptions

    var subsChannels by mutableStateOf<List<YtChannel>>(emptyList())
        private set
    /** -1 = everyone. */
    var subsSelected by mutableStateOf(-1)
        private set
    var subsFeed by mutableStateOf(FeedState())
        private set
    private var subsJob: Job? = null

    fun loadSubs(index: Int, refresh: Boolean = true) {
        if (!refresh && subsSelected == index && subsFeed.videos.isNotEmpty() &&
            System.currentTimeMillis() - subsFeed.at < 10 * 60_000L
        ) return
        subsSelected = index
        subsFeed = FeedState(loading = true)
        subsJob?.cancel()
        subsJob = lifecycleScope.launch {
            val params = subsChannels.getOrNull(index)?.params
            subsFeed = runCatching { YouTubeFeed.subscriptions(params) }.fold(
                { page ->
                    if (index == -1 && page.channels.isNotEmpty()) subsChannels = page.channels
                    FeedState(page.videos, at = System.currentTimeMillis())
                },
                {
                    AppLog.w(TAG, "Subscriptions failed", it)
                    FeedState(error = "Couldn't load your subscriptions.")
                },
            )
            Thumbnails.prefetch(subsFeed.videos.take(24).map { it.thumbnail })
        }
    }

    // ---------------------------------------------------------------- library

    var libTab by mutableStateOf(LibTab.HISTORY)
    val libFeeds = mutableStateMapOf<LibTab, FeedState>()
    var libPlaylists by mutableStateOf(PlaylistsUi())
        private set
    var openPlaylist by mutableStateOf<YtPlaylist?>(null)
        private set
    var playlistFeed by mutableStateOf(FeedState())
        private set
    var playlistRound by mutableStateOf(0)
        private set

    fun loadLibrary(tab: LibTab, refresh: Boolean) {
        if (tab == LibTab.PLAYLISTS) {
            if (!refresh && libPlaylists.items.isNotEmpty()) return
            libPlaylists = PlaylistsUi(libPlaylists.items, loading = true)
            lifecycleScope.launch {
                libPlaylists = runCatching { YouTubeFeed.playlists() }.fold(
                    { PlaylistsUi(it) },
                    {
                        AppLog.w(TAG, "Playlists failed", it)
                        PlaylistsUi(error = "Couldn't load your playlists.")
                    },
                )
            }
            return
        }
        val have = libFeeds[tab]
        // History changes as you watch, so it refreshes more eagerly.
        val maxAge = if (tab == LibTab.HISTORY) 60_000L else 10 * 60_000L
        if (!refresh && have != null && have.error == null && System.currentTimeMillis() - have.at < maxAge) return
        if (have?.loading == true) return
        libFeeds[tab] = (have ?: FeedState()).copy(loading = true, error = null)
        lifecycleScope.launch {
            val result = runCatching {
                when (tab) {
                    LibTab.HISTORY -> YouTubeFeed.history()
                    LibTab.WATCH_LATER -> YouTubeFeed.watchLater()
                    LibTab.LIKED -> YouTubeFeed.liked()
                    LibTab.PLAYLISTS -> emptyList()
                }
            }
            libFeeds[tab] = result.fold(
                { FeedState(it, at = System.currentTimeMillis()) },
                {
                    AppLog.w(TAG, "${tab.label} failed", it)
                    FeedState(libFeeds[tab]?.videos.orEmpty(), error = "Couldn't load ${tab.label}.")
                },
            )
            Thumbnails.prefetch(libFeeds[tab]?.videos.orEmpty().take(24).map { it.thumbnail })
        }
    }

    fun openPlaylist(pl: YtPlaylist) {
        openPlaylist = pl
        playlistRound++
        playlistFeed = FeedState(loading = true)
        lifecycleScope.launch {
            playlistFeed = runCatching { YouTubeFeed.playlist(pl.browseId) }.fold(
                { FeedState(it, at = System.currentTimeMillis()) },
                {
                    AppLog.w(TAG, "Playlist ${pl.browseId} failed", it)
                    FeedState(error = "Couldn't load this playlist.")
                },
            )
        }
    }

    fun closePlaylist() {
        openPlaylist = null
    }

    // ---------------------------------------------------------------- search

    var searchQuery by mutableStateOf("")
        private set
    var search by mutableStateOf(FeedState())
        private set
    /** Bumps each search so the results grid starts fresh. */
    var searchRound by mutableStateOf(0)
        private set

    fun runSearch(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        searchQuery = q
        searchRound++
        search = FeedState(loading = true)
        currentFocus?.let { v ->
            getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
        }
        lifecycleScope.launch {
            search = runCatching { Feeds.search(q) }.fold(
                { FeedState(it, at = System.currentTimeMillis()) },
                {
                    AppLog.w(TAG, "Search failed for $q", it)
                    FeedState(error = "Search failed. Check the connection.")
                },
            )
            Thumbnails.prefetch(search.videos.take(24).map { it.thumbnail })
        }
    }

    // ---------------------------------------------------------------- comments

    var playerTab by mutableStateOf(PlayerTab.UP_NEXT)
    var comments by mutableStateOf(CommentsUi())
        private set
    var thread by mutableStateOf<ThreadUi?>(null)
        private set
    private var commentsJob: Job? = null

    private fun loadComments(url: String) {
        commentsJob?.cancel()
        comments = CommentsUi(loading = true)
        commentsJob = lifecycleScope.launch {
            comments = runCatching { Comments.first(url) }.fold(
                { CommentsUi(it.items, it.next, count = it.count, disabled = it.disabled) },
                {
                    AppLog.w(TAG, "Comments failed for $url", it)
                    CommentsUi(error = "Couldn't load comments.")
                },
            )
        }
    }

    /** Called as the list nears its end. */
    fun moreComments() {
        val np = nowPlaying ?: return
        val t = thread
        if (t != null) {
            val next = t.next ?: return
            if (t.loading) return
            thread = t.copy(loading = true)
            lifecycleScope.launch {
                val page = runCatching { Comments.more(np.video.url, next) }.getOrNull()
                thread = thread?.takeIf { it.parent.id == t.parent.id }?.let { cur ->
                    if (page == null) cur.copy(loading = false, next = null)
                    else cur.copy(items = cur.items + page.items, next = page.next, loading = false)
                }
            }
            return
        }
        val c = comments
        val next = c.next ?: return
        if (c.loading) return
        comments = c.copy(loading = true)
        lifecycleScope.launch {
            val page = runCatching { Comments.more(np.video.url, next) }.getOrNull()
            comments = if (page == null) comments.copy(loading = false, next = null)
            else comments.copy(items = comments.items + page.items, next = page.next, loading = false)
        }
    }

    fun openThread(c: Comment) {
        val first = c.replies ?: return
        thread = ThreadUi(c, next = first)
        moreComments()
    }

    fun closeThread() {
        thread = null
    }

    /** Highlighting a card for a moment starts looking the video up, so opening it is quick. */
    fun warm(video: Video) {
        lifecycleScope.launch { StreamCache.warm(video.url) }
    }

    // ---------------------------------------------------------------- controller

    /** Shoulder buttons: tabs on Home, skip 10 seconds in the player. */
    private fun shoulder(dir: Int) {
        when {
            nowPlaying != null && !minimized -> seekBy(dir * 10_000L)
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
            // Y fills the screen with the video (and back).
            KeyEvent.KEYCODE_BUTTON_Y -> {
                if (down && event.repeatCount == 0 && nowPlaying != null && !minimized) toggleFullscreen()
                return true
            }
            // X brings a minimized video back.
            KeyEvent.KEYCODE_BUTTON_X -> {
                if (down && event.repeatCount == 0 && minimized) expand()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_START -> {
                if (down && event.repeatCount == 0 && (nowPlaying == null || minimized)) screen = Screen.SETUP
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
    /** Signed-in account's name and picture, for the sidebar. */
    var me by mutableStateOf<Pair<String, String?>?>(null)
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
        subsChannels = emptyList()
        subsFeed = FeedState()
        libFeeds.clear()
        libPlaylists = PlaylistsUi()
        openPlaylist = null
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
        const val EXTRA_POSITION = "io.github.stonecoldos.thortube.POSITION"
        const val EXTRA_MOVED = "io.github.stonecoldos.thortube.MOVED"
        private const val TAG = "ThorTube"
    }
}
