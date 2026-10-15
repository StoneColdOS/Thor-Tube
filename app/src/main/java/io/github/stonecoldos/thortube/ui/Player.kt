package io.github.stonecoldos.thortube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.opengl.GLSurfaceView
import io.github.stonecoldos.thortube.crt.CrtRenderer
import io.github.stonecoldos.thortube.data.Comment
import io.github.stonecoldos.thortube.data.formatDuration
import kotlinx.coroutines.delay

/** The picture. Each place that shows the video (full player, mini-player) gets its own view. */
@Composable
private fun VideoSurface(app: MainActivity, modifier: Modifier) {
    // Switching the CRT effect swaps in a different kind of view.
    key(app.crtMode == CrtMode.OFF) {
        if (app.crtMode == CrtMode.OFF) PlainSurface(app, modifier) else TubeSurface(app, modifier)
    }
}

/** The video through the CRT shader (CRT effect on). */
@Composable
private fun TubeSurface(app: MainActivity, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                preserveEGLContextOnPause = true
                lateinit var r: CrtRenderer
                r = CrtRenderer(this) { surface -> app.crtSurfaceReady(r, surface) }
                setRenderer(r)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                tag = r
                app.crtAttached(r)
            }
        },
        update = { (it.tag as CrtRenderer).applyLook(flat = app.crtMode == CrtMode.NOUGHTIES) },
        onRelease = { gl ->
            val r = gl.tag as CrtRenderer
            app.crtDetached(r)
            r.release()
        },
        modifier = modifier,
    )
}

/** The plain video (CRT effect off). */
@Composable
private fun PlainSurface(app: MainActivity, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                isFocusable = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { it.player = app.player },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

// ------------------------------------------------------------------ full player

@Composable
fun PlayerScreen(app: MainActivity, np: NowPlaying) {
    val p = LocalPalette.current
    // Back leaves full screen first, then shrinks the video into the corner instead of stopping it.
    BackHandler { if (app.fullscreen) app.fullscreen = false else app.minimize() }
    var controls by remember { mutableStateOf(true) }
    var lastPoke by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(app.controlsPing) {
        controls = true
        lastPoke = System.currentTimeMillis()
    }
    LaunchedEffect(lastPoke, app.isPlaying) {
        delay(3_000)
        if (app.isPlaying) controls = false
    }
    val videoFocus = remember { FocusRequester() }
    LaunchedEffect(np.video.url) { runCatching { videoFocus.requestFocus() } }

    val commentsList = remember(np.video.url) { LazyListState() }
    val threadList = remember(app.thread?.parent?.id) { LazyListState() }
    val activeList = if (app.thread != null) threadList else commentsList
    // Scrolling down the comments slides the title away to make room; back at the top it returns.
    val showInfo by remember(activeList) {
        derivedStateOf {
            app.playerTab != PlayerTab.COMMENTS ||
                (activeList.firstVisibleItemIndex == 0 && activeList.firstVisibleItemScrollOffset < 12)
        }
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        // The video. Tap for controls, swipe down to shrink it. Controller: A pauses, left/right skip 10 s.
        Box(
            (if (app.fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                .background(Color.Black)
                .focusRequester(videoFocus)
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { app.seekBy(-10_000); true }
                        Key.DirectionRight -> { app.seekBy(10_000); true }
                        Key.DirectionCenter, Key.Enter -> { app.togglePause(); true }
                        else -> false
                    }
                }
                .focusable()
                .pointerInput(Unit) {
                    var drag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            // Swipe up to fill the screen; swipe down to leave full screen, then to shrink.
                            when {
                                drag < -60.dp.toPx() && !app.fullscreen -> app.toggleFullscreen()
                                drag > 60.dp.toPx() && app.fullscreen -> app.toggleFullscreen()
                                drag > 60.dp.toPx() -> app.minimize()
                            }
                        },
                    ) { _, dy -> drag += dy }
                }
                .clickable(indication = null, interactionSource = null) {
                    controls = !controls
                    lastPoke = System.currentTimeMillis()
                },
        ) {
            VideoSurface(app, Modifier.fillMaxSize())
            if (!app.firstFrame && app.crtMode == CrtMode.OFF) Thumb(np.video.bigThumbnail, Modifier.fillMaxSize())
            app.playerProblem?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Center).background(Color(0xC0000000), RoundedCornerShape(8.dp)).padding(10.dp),
                )
            }
            val controlsAlpha by animateFloatAsState(if (controls) 1f else 0f, tween(if (controls) 180 else 260), label = "controls")
            if (controlsAlpha > 0f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = controlsAlpha }) { PlayerControls(app) }
            }
        }

        if (!app.fullscreen) PlayerDetails(app, np, showInfo, activeList)
    }
}

/** Everything under the video: title, creator, and the Up next / Comments tabs. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.PlayerDetails(app: MainActivity, np: NowPlaying, showInfo: Boolean, activeList: LazyListState) {
    val p = LocalPalette.current
    run {
        AnimatedVisibility(showInfo, enter = expandVertically(tween(200)) + fadeIn(tween(200)), exit = shrinkVertically(tween(200)) + fadeOut(tween(150))) {
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp)) {
                Text(np.title, color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(np.channel, np.avatar, 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(np.channel, color = p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        val sub = listOf(np.subscribers, np.meta).filter { it.isNotBlank() }.joinToString("  ·  ")
                        if (sub.isNotEmpty()) Text(sub, color = p.dim, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (app.signedIn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            ActionPill(if (app.subscribed) "Subscribed" else "Subscribe", strong = !app.subscribed) { app.toggleSubscribe() }
                            ActionPill(if (app.liked) "▲ Liked" else "▲ Like", lit = app.liked) { app.toggleLike() }
                            ActionPill(if (app.saved) "✓ Saved" else "+ Save", lit = app.saved) { app.toggleSave() }
                        }
                    }
                }
            }
        }

        // Up next / Comments.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip("Up next", app.playerTab == PlayerTab.UP_NEXT) { app.playerTab = PlayerTab.UP_NEXT }
            Chip(commentsLabel(app.comments), app.playerTab == PlayerTab.COMMENTS) { app.playerTab = PlayerTab.COMMENTS }
            Spacer(Modifier.weight(1f))
            if (app.playerTab == PlayerTab.COMMENTS) Pill("⤢ Expand") { app.openCommentsPage() }
        }
        AnimatedContent(
            targetState = app.playerTab,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.weight(1f),
            label = "playerTab",
        ) { t ->
            when (t) {
                PlayerTab.UP_NEXT -> if (np.upNext.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(np.upNext, key = { _, v -> v.url }) { _, v ->
                            VideoCard(v, width = 112.dp, titleLines = 1, onDwell = { app.warm(v) }) { app.open(v, np.upNext) }
                        }
                    }
                } else {
                    Message("Nothing queued up.")
                }
                PlayerTab.COMMENTS -> CommentsPane(app, activeList)
            }
        }
    }
}

/** Subscribe / Like / Save. [strong] is the bright "do this" look; [lit] shows it's on. */
@Composable
private fun ActionPill(label: String, strong: Boolean = false, lit: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (strong) p.chipOn else p.card)
            .border(2.dp, if (focused) p.accent else Color.Transparent, RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = when {
                strong -> p.chipOnText
                lit -> p.accent
                else -> p.text
            },
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A short message along the bottom of the screen. */
@Composable
fun Toast(text: String?, modifier: Modifier) {
    val p = LocalPalette.current
    var shown by remember { mutableStateOf("") }
    if (text != null) shown = text
    val alpha by animateFloatAsState(if (text != null) 1f else 0f, tween(200), label = "toast")
    if (alpha > 0f) {
        Text(
            shown,
            color = p.bg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = modifier
                .graphicsLayer { this.alpha = alpha }
                .clip(RoundedCornerShape(50))
                .background(p.text)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

private fun commentsLabel(c: CommentsUi) = when {
    c.count > 0 -> "Comments  ${shortCount(c.count)}"
    else -> "Comments"
}

private fun shortCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
    n >= 1_000 -> "%.1fK".format(n / 1_000.0).replace(".0K", "K")
    else -> n.toString()
}

@Composable
private fun PlayerControls(app: MainActivity) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize().background(Color(0x55000000))) {
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton("-10") { app.seekBy(-10_000) }
            RoundButton(if (app.isPlaying) "||" else "▶", big = true) { app.togglePause() }
            RoundButton("+10") { app.seekBy(10_000) }
        }
        RoundButton("⌄", modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) { app.minimize() }
        RoundButton(if (app.fullscreen) "⤡" else "⛶", modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { app.toggleFullscreen() }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp)) {
            Text(
                "${formatDuration(app.positionMs / 1000).ifEmpty { "0:00" }} / ${formatDuration(app.durationMs / 1000).ifEmpty { "--:--" }}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            val dur = app.durationMs.coerceAtLeast(1)
            Slider(
                value = (app.positionMs.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { app.seekTo((it * dur).toLong()) },
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = Color(0x80FFFFFF)),
                modifier = Modifier.height(22.dp),
            )
        }
    }
}

@Composable
private fun RoundButton(label: String, big: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = if (big) 44.dp else 34.dp
    Box(
        modifier
            .size(s)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = if (big) 15.sp else 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ comments

/** The comment list (or one comment's replies). Loads more as you near the end. */
@Composable
fun CommentsPane(app: MainActivity, list: LazyListState) {
    val p = LocalPalette.current
    val thread = app.thread
    BackHandler(enabled = thread != null) { app.closeThread() }
    val c = app.comments
    val items = thread?.items ?: c.items
    val loading = thread?.loading ?: c.loading
    LaunchedEffect(list, thread?.parent?.id) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= list.layoutInfo.totalItemsCount - 4) app.moreComments() }
    }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (thread != null) {
            item(key = "back") {
                Pill("‹  Back to all comments") { app.closeThread() }
            }
            item(key = "parent") { CommentRow(thread.parent, highlight = true) { } }
        }
        itemsIndexed(items, key = { i, it -> "$i-${it.id}" }) { _, comment ->
            CommentRow(comment, indent = thread != null) { if (thread == null && comment.replies != null) app.openThread(comment) }
        }
        when {
            loading -> item(key = "loading") { Text("Loading comments...", color = p.dim, fontSize = 9.sp, modifier = Modifier.padding(8.dp)) }
            thread == null && c.disabled -> item(key = "off") { Text("Comments are turned off for this video.", color = p.dim, fontSize = 10.sp, modifier = Modifier.padding(8.dp)) }
            thread == null && c.error != null -> item(key = "err") { Text(c.error, color = p.dim, fontSize = 10.sp, modifier = Modifier.padding(8.dp)) }
            items.isEmpty() -> item(key = "none") { Text("No comments yet.", color = p.dim, fontSize = 10.sp, modifier = Modifier.padding(8.dp)) }
        }
    }
}

@Composable
private fun CommentRow(c: Comment, indent: Boolean = false, highlight: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 18.dp else 0.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) p.card else Color.Transparent)
            .border(2.dp, if (focused) p.accent else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Avatar(c.author, c.avatar, 20.dp)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.author + if (c.verified) " ✓" else "",
                    color = if (c.byCreator) p.accent else p.text,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text("  ${c.ago}", color = p.dim, fontSize = 8.sp, maxLines = 1)
                if (c.pinned) Text("  · Pinned", color = p.dim, fontSize = 8.sp)
            }
            Text(c.text, color = p.text, fontSize = 9.5.sp, lineHeight = 12.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (c.likes.isNotEmpty()) Text("▲ ${c.likes}", color = p.dim, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                if (c.hearted) Text("   ♥ from the creator", color = Color(0xFFFF5C7A), fontSize = 8.sp)
                if (c.replyCount > 0 && !highlight) {
                    Text(
                        "   ${c.replyCount} ${if (c.replyCount == 1) "reply" else "replies"}",
                        color = p.accent,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Comments with the whole screen; the video keeps playing in the corner. */
@Composable
fun CommentsPage(app: MainActivity) {
    val p = LocalPalette.current
    val np = app.lastPlaying ?: return
    BackHandler(enabled = app.thread == null) { app.closeCommentsPage() }
    val list = remember(np.video.url, app.thread?.parent?.id) { LazyListState() }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(commentsLabel(app.comments), color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(np.title, color = p.dim, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Pill("Close") { app.closeCommentsPage() }
        }
        Box(Modifier.weight(1f)) { CommentsPane(app, list) }
    }
}

// ------------------------------------------------------------------ mini-player

/** The video shrunk into the corner while you browse. Tap (or X) to bring it back; ✕ stops it. */
@Composable
fun MiniPlayer(app: MainActivity, np: NowPlaying, modifier: Modifier) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(176.dp)
            .shadow(10.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(p.card)
            .border(2.dp, if (focused) p.accent else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.hasFocus }
            .clickable { app.expand() },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            VideoSurface(app, Modifier.fillMaxSize())
            if (!app.firstFrame && app.crtMode == CrtMode.OFF) Thumb(np.video.thumbnail, Modifier.fillMaxSize())
            // Progress along the bottom edge.
            val frac = if (app.durationMs > 0) (app.positionMs.toFloat() / app.durationMs).coerceIn(0f, 1f) else 0f
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(frac).height(2.dp).background(p.accent))
        }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(np.title, color = p.text, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            MiniButton(if (app.isPlaying) "||" else "▶") { app.togglePause() }
            MiniButton("✕") { app.close() }
        }
    }
}

@Composable
private fun MiniButton(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(p.bg.copy(alpha = 0.6f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = p.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ search

@Composable
fun SearchScreen(app: MainActivity) {
    val p = LocalPalette.current
    BackHandler { app.screen = Screen.HOME }
    var text by remember { mutableStateOf(app.searchQuery) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (app.search.videos.isEmpty()) runCatching { field.requestFocus() } }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 12.sp),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { app.runSearch(text) }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(field)
                    .clip(RoundedCornerShape(50))
                    .background(p.card)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) Text("Search YouTube", color = p.dim, fontSize = 12.sp)
                    inner()
                },
            )
            Pill("Search", primary = true) { app.runSearch(text) }
        }
        val st = app.search
        when {
            st.loading && st.videos.isEmpty() -> Message("Searching...")
            st.error != null -> Message(st.error)
            st.videos.isEmpty() -> Message(if (app.searchQuery.isEmpty()) "Type something and press Search." else "Nothing found.")
            else -> VideoGrid(app, st.videos, SEARCH_GRID + app.searchRound, header = null) { v -> app.open(v, st.videos) }
        }
    }
}

/** Grid keys for search results, kept apart from the Home tabs. */
const val SEARCH_GRID = 1_000
