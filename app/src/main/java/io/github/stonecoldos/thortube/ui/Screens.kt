package io.github.stonecoldos.thortube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.stonecoldos.thortube.BuildConfig
import io.github.stonecoldos.thortube.data.HOME_TABS
import io.github.stonecoldos.thortube.data.Video
import io.github.stonecoldos.thortube.data.ago
import io.github.stonecoldos.thortube.data.formatDuration
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ rail

/** The slim strip on the left: one button per screen. */
@Composable
fun Rail(current: Screen, onPick: (Screen) -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(p.rail)
            .border(width = 0.5.dp, color = p.railLine)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("TT", color = p.accent, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
        Screen.entries.forEach { s ->
            val on = s == current
            var focused by remember { mutableStateOf(false) }
            Column(
                Modifier
                    .width(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) p.railOn else Color.Transparent)
                    .border(2.dp, if (focused) p.accent else Color.Transparent, RoundedCornerShape(10.dp))
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onPick(s) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RailIcon(s, if (on) p.accent else p.dim)
                Text(s.label, color = if (on) p.text else p.dim, fontSize = 7.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun RailIcon(s: Screen, color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        when (s) {
            Screen.HOME -> {
                drawPath(Path().apply { moveTo(w * 0.5f, h * 0.05f); lineTo(w, h * 0.5f); lineTo(0f, h * 0.5f); close() }, color)
                drawRect(color, Offset(w * 0.18f, h * 0.48f), androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.45f))
            }
            Screen.SUBS -> {
                drawRoundRect(color, Offset(0f, h * 0.15f), androidx.compose.ui.geometry.Size(w, h * 0.7f), androidx.compose.ui.geometry.CornerRadius(w * 0.15f))
                drawPath(Path().apply { moveTo(w * 0.4f, h * 0.33f); lineTo(w * 0.68f, h * 0.5f); lineTo(w * 0.4f, h * 0.67f); close() }, Color.Black.copy(alpha = 0.7f))
            }
            Screen.SEARCH -> {
                drawCircle(color, w * 0.3f, Offset(w * 0.42f, h * 0.42f), style = Stroke(w * 0.13f))
                drawLine(color, Offset(w * 0.64f, h * 0.64f), Offset(w * 0.95f, h * 0.95f), w * 0.15f, StrokeCap.Round)
            }
            Screen.LIBRARY -> for (k in 0..2) {
                drawRect(color, Offset(k * w * 0.36f, h * 0.08f), androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.84f))
            }
            Screen.CRT -> {
                drawRoundRect(color, Offset(0f, h * 0.28f), androidx.compose.ui.geometry.Size(w, h * 0.66f), androidx.compose.ui.geometry.CornerRadius(w * 0.18f), style = Stroke(w * 0.12f))
                drawLine(color, Offset(w * 0.3f, 0f), Offset(w * 0.5f, h * 0.28f), w * 0.1f)
                drawLine(color, Offset(w * 0.7f, 0f), Offset(w * 0.5f, h * 0.28f), w * 0.1f)
            }
            Screen.SETUP -> {
                drawCircle(color, w * 0.4f, center, style = Stroke(w * 0.16f))
                drawCircle(color, w * 0.14f, center)
            }
        }
    }
}

// ------------------------------------------------------------------ home

@Composable
fun HomeScreen(app: MainActivity) {
    val p = LocalPalette.current
    val state = app.feeds[app.tab] ?: FeedState(loading = true)
    Column(Modifier.fillMaxSize()) {
        // Tabs.
        val chips = rememberLazyListState()
        LaunchedEffect(app.tab) { chips.animateScrollToItem(app.tab) }
        LazyRow(
            state = chips,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(HOME_TABS) { i, t ->
                Chip(t.label, i == app.tab) { app.selectTab(i, refresh = i == app.tab) }
            }
        }
        when {
            state.videos.isEmpty() && state.loading -> Message("Loading...")
            state.videos.isEmpty() && state.error != null -> Message(state.error, "Try again") { app.selectTab(app.tab, refresh = true) }
            else -> VideoGrid(
                videos = state.videos,
                key = app.tab,
                header = if (HOME_TABS[app.tab].isForYou && !app.signedIn) {
                    { SignInBanner { app.screen = Screen.SETUP } }
                } else null,
                onOpen = { v -> app.open(v, state.videos) },
            )
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) p.chipOn else p.card)
            .border(2.dp, if (focused) p.accent else Color.Transparent, RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (on) p.chipOnText else p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SignInBanner(onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.card)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Make this your YouTube home", color = p.text, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Sign in and For You shows what YouTube picks for you.", color = p.dim, fontSize = 9.sp)
        }
        Text("Sign in", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

/** Three-across grid of videos that scrolls by touch or with the D-pad. */
@Composable
fun VideoGrid(videos: List<Video>, key: Any, header: (@Composable () -> Unit)?, onOpen: (Video) -> Unit) {
    val grid = rememberLazyGridState()
    val first = remember(key) { FocusRequester() }
    LaunchedEffect(key) {
        grid.scrollToItem(0)
        runCatching { first.requestFocus() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = grid,
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
        itemsIndexed(videos, key = { _, v -> v.url }) { i, v ->
            VideoCard(v, if (i == 0) Modifier.focusRequester(first) else Modifier) { onOpen(v) }
        }
    }
}

@Composable
fun VideoCard(v: Video, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp? = null, titleLines: Int = 2, onClick: () -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .border(2.5.dp, if (focused) p.accent else Color.Transparent, RoundedCornerShape(10.dp))
                .padding(if (focused) 2.5.dp else 0.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            Thumb(v.thumbnail, Modifier.fillMaxSize())
            val d = formatDuration(v.durationSecs)
            if (d.isNotEmpty()) {
                Text(
                    d,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color(0xD0000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Row(Modifier.padding(top = 5.dp)) {
            Avatar(v.uploaderName, null, 16.dp)
            Spacer(Modifier.width(5.dp))
            Column {
                Text(v.title, color = p.text, fontSize = 9.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
                val meta = listOf(v.uploaderName, if (v.uploadedAt > 0) ago(v.uploadedAt) else v.uploaded).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = p.dim, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A creator's picture, or their initial on a color picked from their name. */
@Composable
fun Avatar(name: String, url: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.hsv(((name.hashCode() and 0x7fffffff) % 360).toFloat(), 0.45f, 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold)
        if (url != null) Thumb(url, Modifier.fillMaxSize())
    }
}

@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, color = p.dim, fontSize = 11.sp)
        if (action != null) {
            Spacer(Modifier.height(10.dp))
            Pill(action, primary = true, onClick = onAction)
        }
    }
}

@Composable
fun Pill(label: String, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) p.accent else p.card)
            .border(2.dp, if (focused) p.text else Color.Transparent, RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (primary) Color.Black else p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ComingSoon(s: Screen) {
    val p = LocalPalette.current
    val what = when (s) {
        Screen.SUBS -> "Your subscriptions"
        Screen.SEARCH -> "Search"
        Screen.LIBRARY -> "History, Watch Later and playlists"
        Screen.CRT -> "The retro CRT effect"
        else -> s.label
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(what, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("Coming in a next step.", color = p.dim, fontSize = 10.sp)
    }
}

// ------------------------------------------------------------------ player

@Composable
fun PlayerScreen(app: MainActivity, np: NowPlaying) {
    val p = LocalPalette.current
    BackHandler { app.close() }
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

    Column(Modifier.fillMaxSize()) {
        // The video. Tap for controls; with the controller, A pauses and left/right skip 10 seconds.
        var videoFocused by remember { mutableStateOf(false) }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .border(2.dp, if (videoFocused && !controls) p.accent.copy(alpha = 0.6f) else Color.Transparent)
                .focusRequester(videoFocus)
                .onFocusChanged { videoFocused = it.isFocused }
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
                .clickable(indication = null, interactionSource = null) {
                    controls = !controls
                    lastPoke = System.currentTimeMillis()
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = app.player
                        isFocusable = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (!app.firstFrame) Thumb(np.video.bigThumbnail, Modifier.fillMaxSize())
            app.playerProblem?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Center).background(Color(0xC0000000), RoundedCornerShape(8.dp)).padding(10.dp),
                )
            }
            if (controls) PlayerControls(app)
        }

        // Title, creator, and what's next.
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(np.title, color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(np.channel, np.avatar, 22.dp)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(np.channel, color = p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    val sub = listOf(np.subscribers, np.meta).filter { it.isNotBlank() }.joinToString("  ·  ")
                    if (sub.isNotEmpty()) Text(sub, color = p.dim, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (np.upNext.isNotEmpty()) {
            Text("Up next", color = p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(np.upNext, key = { _, v -> v.url }) { _, v ->
                    VideoCard(v, width = 112.dp, titleLines = 1) { app.open(v, np.upNext) }
                }
            }
        }
    }
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
private fun RoundButton(label: String, big: Boolean = false, onClick: () -> Unit) {
    val s = if (big) 44.dp else 34.dp
    Box(
        Modifier
            .size(s)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = if (big) 15.sp else 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ setup

@Composable
fun SetupScreen(app: MainActivity) {
    val p = LocalPalette.current
    BackHandler { app.screen = Screen.HOME }
    var confirmSignOut by remember { mutableStateOf(false) }
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Setup", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        item {
            Section("Theme") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { m -> Chip(m.label, m == app.theme) { app.setThemeMode(m) } }
                }
            }
        }
        item {
            Section("YouTube account") {
                val code = app.signInCode
                when {
                    app.signedIn -> {
                        Text("Signed in. For You shows your YouTube home, and what you watch here goes into your YouTube history.", color = p.dim, fontSize = 9.5.sp)
                        Spacer(Modifier.height(8.dp))
                        Pill(if (confirmSignOut) "Tap again to sign out" else "Sign out") {
                            if (confirmSignOut) { app.signOut(); confirmSignOut = false } else confirmSignOut = true
                        }
                    }
                    code != null -> {
                        Text("On your phone or computer, go to", color = p.dim, fontSize = 10.sp)
                        Text(code.verificationUrl.removePrefix("https://").removePrefix("www."), color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("sign in, and enter this code:", color = p.dim, fontSize = 10.sp)
                        Text(code.userCode, color = p.accent, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(vertical = 6.dp))
                        Text(app.signInStatus, color = p.dim, fontSize = 9.sp)
                        Spacer(Modifier.height(6.dp))
                        Pill("Cancel") { app.cancelSignIn() }
                    }
                    else -> {
                        Text(
                            "Sign in to see your YouTube home and have what you watch here count in your history. " +
                                "You enter a code on your phone; your password never touches Thor-Tube.",
                            color = p.dim,
                            fontSize = 9.5.sp,
                        )
                        Text(
                            "Unofficial: this uses the YouTube TV app's sign-in, which is against YouTube's terms. A spare account is safest.",
                            color = p.dim,
                            fontSize = 8.5.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (app.signInStatus.isNotEmpty()) Text(app.signInStatus, color = p.text, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(8.dp))
                        Pill("Sign in", primary = true) { app.startSignIn() }
                    }
                }
            }
        }
        item {
            Section("About") {
                Text("Thor-Tube ${BuildConfig.VERSION_NAME}. Unofficial; not made by YouTube or AYN.", color = p.dim, fontSize = 9.sp)
                Text("Logs: Download/Thor-Tube-logs", color = p.dim, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.card.copy(alpha = if (p.isLight) 0.6f else 0.5f))
            .padding(12.dp),
    ) {
        Text(title, color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}
