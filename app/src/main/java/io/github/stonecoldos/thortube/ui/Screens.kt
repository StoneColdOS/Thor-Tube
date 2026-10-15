package io.github.stonecoldos.thortube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stonecoldos.thortube.BuildConfig
import io.github.stonecoldos.thortube.data.HOME_TABS
import io.github.stonecoldos.thortube.data.Video
import io.github.stonecoldos.thortube.data.ago
import io.github.stonecoldos.thortube.data.formatDuration
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ rail

/** The slim strip on the left: one button per screen. */
@Composable
fun Rail(current: Screen, signedIn: Boolean, me: Pair<String, String?>?, onPick: (Screen) -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .width(60.dp)
            .fillMaxHeight()
            .background(p.rail)
            .border(width = 0.5.dp, color = p.railLine)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TvLogo(Modifier.padding(bottom = 8.dp).size(32.dp))
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Screen.entries.filter { it != Screen.SETUP }.forEach { s -> RailButton(s, s == current) { onPick(s) } }
        }
        Box(Modifier.padding(vertical = 4.dp).width(36.dp).height(0.5.dp).background(p.railLine))
        RailButton(Screen.SETUP, current == Screen.SETUP) { onPick(Screen.SETUP) }
        // Your account: profile picture when signed in, a plain person when not. Opens Setup.
        var focused by remember { mutableStateOf(false) }
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(34.dp)
                .clip(CircleShape)
                .border(2.dp, if (focused) p.accent else Color.Transparent, CircleShape)
                .onFocusChanged { focused = it.isFocused }
                .clickable { onPick(Screen.SETUP) }
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (signedIn && me != null) {
                Avatar(me.first.ifBlank { "You" }, me.second, 28.dp)
            } else {
                Canvas(Modifier.size(28.dp)) {
                    val c = if (signedIn) p.accent else p.dim
                    drawCircle(c, size.width / 2f, style = Stroke(size.width * 0.07f))
                    drawCircle(c, size.width * 0.17f, Offset(size.width / 2f, size.height * 0.4f))
                    drawArc(c, 200f, 140f, false, Offset(size.width * 0.22f, size.height * 0.6f),
                        androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.5f), style = Stroke(size.width * 0.07f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

/** The app icon boiled down: a 2000s TV with a green hammer on the screen. */
@Composable
fun TvLogo(modifier: Modifier) {
    val p = LocalPalette.current
    val body = androidx.compose.ui.graphics.lerp(p.rail, p.text, 0.22f)
    val hammer = Color(0xFF39FF5A)
    Canvas(modifier) {
        val k = size.width / 66f   // drawn on the icon's 21..87 grid
        fun o(x: Float, y: Float) = Offset((x - 21f) * k, (y - 21f) * k)
        val depth = Path().apply {
            moveTo(o(24f, 31f).x, o(24f, 31f).y); lineTo(o(36f, 24f).x, o(36f, 24f).y)
            lineTo(o(87f, 24f).x, o(87f, 24f).y); lineTo(o(87f, 72f).x, o(87f, 72f).y)
            lineTo(o(77f, 83f).x, o(77f, 83f).y); close()
        }
        drawPath(depth, androidx.compose.ui.graphics.lerp(body, Color.Black, 0.35f))
        drawRoundRect(body, o(21f, 30f), Size(57f * k, 54f * k), CornerRadius(5f * k))
        drawRoundRect(Color(0xFF0B1118), o(26.5f, 35f), Size(46f * k, 32f * k), CornerRadius(2f * k))
        rotate(-15f, o(49.5f, 51f)) {
            drawRoundRect(hammer, o(39.5f, 40f), Size(20f * k, 12f * k), CornerRadius(1.6f * k))
            drawRoundRect(hammer, o(47.6f, 51.5f), Size(3.8f * k, 12f * k), CornerRadius(1.2f * k))
            drawRoundRect(hammer, o(46.6f, 62f), Size(5.8f * k, 3.2f * k), CornerRadius(1.4f * k))
        }
        drawCircle(hammer, 1.1f * k, o(47f, 77f))
    }
}

@Composable
private fun RailButton(s: Screen, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) p.railOn else Color.Transparent)
            .border(2.dp, if (focused) p.accent else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailIcon(s, if (on) p.accent else p.dim)
        Spacer(Modifier.height(2.dp))
        Text(s.label, color = if (on) p.text else p.dim, fontSize = 9.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun RailIcon(s: Screen, color: Color) {
    Canvas(Modifier.size(20.dp)) {
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
        AnimatedContent(
            targetState = app.tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { dir * it / 12 }) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { -dir * it / 12 })
            },
            label = "tab",
        ) { tab ->
            val st = app.feeds[tab] ?: FeedState(loading = true)
            when {
                st.videos.isEmpty() && st.loading -> Message("Loading...")
                st.videos.isEmpty() && st.error != null -> Message(st.error, "Try again") { app.selectTab(tab, refresh = true) }
                else -> VideoGrid(
                    app = app,
                    videos = st.videos,
                    tab = tab,
                    header = if (HOME_TABS[tab].isForYou && !app.signedIn) {
                        { SignInBanner { app.screen = Screen.SETUP } }
                    } else null,
                    onOpen = { v -> app.open(v, st.videos) },
                )
            }
        }
    }
}

@Composable
fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
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

/** Which card the highlight is on, and where it is on screen. */
class RingState {
    var owner by mutableStateOf<String?>(null)
    var rect by mutableStateOf<Rect?>(null)

    fun report(url: String, r: Rect?) {
        if (r != null) {
            owner = url
            rect = r
        } else if (owner == url) {
            owner = null
        }
    }
}

/**
 * Three-across grid of videos that scrolls by touch or with the D-pad. One highlight ring glides
 * from card to card instead of each card drawing its own. Each tab keeps its scroll position, and
 * coming back from a video puts the highlight back on it.
 */
@Composable
fun VideoGrid(app: MainActivity, videos: List<Video>, tab: Int, header: (@Composable () -> Unit)?, onOpen: (Video) -> Unit) {
    val grid = app.gridState(tab)
    val ring = remember(tab) { RingState() }
    val focus = remember(tab) { FocusRequester() }
    var focusUrl by remember(tab) { mutableStateOf<String?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val offset = if (header != null) 1 else 0
    LaunchedEffect(tab) {
        if (videos.isEmpty()) return@LaunchedEffect
        val back = app.returnTo?.let { u -> videos.indexOfFirst { it.url == u } } ?: -1
        val index = if (back >= 0) back else (grid.firstVisibleItemIndex - offset).coerceIn(0, videos.lastIndex)
        val visible = grid.layoutInfo.visibleItemsInfo.map { it.index }
        if (index + offset !in visible) grid.scrollToItem(index + offset)
        focusUrl = videos[index].url
        withFrameNanos { }
        runCatching { focus.requestFocus() }
        app.returnTo = null
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = grid,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
            itemsIndexed(videos, key = { _, v -> v.url }) { _, v ->
                VideoCard(
                    v,
                    if (v.url == focusUrl) Modifier.focusRequester(focus) else Modifier,
                    ownRing = false,
                    onFocusRect = { r -> ring.report(v.url, r) },
                    onDwell = { app.warm(v) },
                ) { onOpen(v) }
            }
        }
        FocusRing(ring, origin)
    }
}

/** The one highlight ring for a grid: snaps in, then glides to whichever card is highlighted. */
@Composable
private fun FocusRing(ring: RingState, origin: Offset) {
    val p = LocalPalette.current
    val anim = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (ring.owner != null) 1f else 0f, tween(140), label = "ring")
    LaunchedEffect(ring.rect, ring.owner) {
        val target = ring.rect ?: return@LaunchedEffect
        if (ring.owner == null) return@LaunchedEffect
        if (!placed) {
            anim.snapTo(target)
            placed = true
        } else {
            anim.animateTo(target, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    LaunchedEffect(ring.owner) {
        if (ring.owner == null) {
            delay(200)
            if (ring.owner == null) placed = false
        }
    }
    Canvas(Modifier.fillMaxSize().clipToBounds()) {
        if (alpha <= 0f || !placed) return@Canvas
        val r = anim.value.translate(-origin)
        val pad = 3.dp.toPx()
        drawRoundRect(
            p.accent.copy(alpha = alpha),
            topLeft = Offset(r.left - pad, r.top - pad),
            size = Size(r.width + pad * 2, r.height + pad * 2),
            cornerRadius = CornerRadius(11.dp.toPx()),
            style = Stroke(2.5.dp.toPx()),
        )
    }
}

@Composable
fun VideoCard(
    v: Video,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp? = null,
    titleLines: Int = 2,
    ownRing: Boolean = true,
    onFocusRect: (Rect?) -> Unit = {},
    onDwell: () -> Unit = {},
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Resting on a card for a moment starts looking the video up.
    LaunchedEffect(focused) {
        if (focused) {
            delay(400)
            onDwell()
        }
    }
    val ringAlpha by animateFloatAsState(if (focused && ownRing) 1f else 0f, tween(140), label = "card")
    Column(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) coords?.takeIf { c -> c.isAttached }?.let { c -> onFocusRect(c.boundsInRoot()) } else onFocusRect(null)
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onGloballyPositioned {
                    coords = it
                    if (focused) onFocusRect(it.boundsInRoot())
                }
                .border(2.5.dp, p.accent.copy(alpha = ringAlpha), RoundedCornerShape(10.dp))
                .padding(if (ownRing) 2.5.dp else 0.dp)
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
fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
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
