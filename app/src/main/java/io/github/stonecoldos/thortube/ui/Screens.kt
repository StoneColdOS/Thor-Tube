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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.stonecoldos.thortube.BuildConfig
import io.github.stonecoldos.thortube.data.Tab
import io.github.stonecoldos.thortube.data.Video
import io.github.stonecoldos.thortube.data.ago
import io.github.stonecoldos.thortube.data.formatDuration
import kotlinx.coroutines.delay

// ------------------------------------------------------------------ rail

/** The slim strip on the left: one button per screen. */
@Composable
fun Rail(current: Screen, signedIn: Boolean, me: Pair<String, String?>?, focus: FocusRequester? = null, onSwipe: (down: Boolean) -> Unit = {}, onPick: (Screen) -> Unit) {
    val p = LocalPalette.current
    // Every button spread evenly down the full height: no dead gap, more room for thumbs.
    Column(
        Modifier
            .width(60.dp)
            .fillMaxHeight()
            .background(p.rail)
            .border(width = 0.5.dp, color = p.railLine)
            // Swipe down the sidebar to send the app to the bottom screen, up to send it to the top.
            .pointerInput(Unit) {
                var drag = 0f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag > 90.dp.toPx()) onSwipe(true)
                        else if (drag < -90.dp.toPx()) onSwipe(false)
                    },
                ) { _, dy -> drag += dy }
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Screen.entries.forEach { s -> RailButton(s.label, s == current, { onPick(s) }, if (s == current) focus else null) { RailIcon(s, it) } }
        // Your account: profile picture when signed in, a plain person when not. Opens Setup.
        RailButton("You", false, { onPick(Screen.SETUP) }) { tint ->
            if (signedIn && me != null) {
                Avatar(me.first.ifBlank { "You" }, me.second, 24.dp)
            } else {
                Canvas(Modifier.size(24.dp)) {
                    val c = if (signedIn) p.accent else tint
                    val line = Stroke(size.width * 0.085f, cap = StrokeCap.Round)
                    drawCircle(c, size.width * 0.44f, style = line)
                    drawCircle(c, size.width * 0.15f, Offset(size.width / 2f, size.height * 0.4f), style = line)
                    drawArc(c, 210f, 120f, false, Offset(size.width * 0.24f, size.height * 0.62f),
                        Size(size.width * 0.52f, size.height * 0.44f), style = line)
                }
            }
        }
    }
}

@Composable
private fun RailButton(label: String, on: Boolean, onClick: () -> Unit, focus: FocusRequester? = null, icon: @Composable (Color) -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(52.dp)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) p.railOn else Color.Transparent)
            .border(2.dp, if (focused) p.accent else Color.Transparent, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(if (on) p.accent else p.dim)
        Spacer(Modifier.height(2.dp))
        Text(label, color = if (on) p.text else p.dim, fontSize = 10.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Sidebar icons: all outlines, same line weight and size, so they read as one set. */
@Composable
private fun RailIcon(s: Screen, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val line = Stroke(w * 0.085f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = line)
        when (s) {
            Screen.HOME -> {
                path { moveTo(w * 0.1f, h * 0.48f); lineTo(w * 0.5f, h * 0.12f); lineTo(w * 0.9f, h * 0.48f) }
                path {
                    moveTo(w * 0.22f, h * 0.38f); lineTo(w * 0.22f, h * 0.88f); lineTo(w * 0.78f, h * 0.88f); lineTo(w * 0.78f, h * 0.38f)
                    moveTo(w * 0.42f, h * 0.88f); lineTo(w * 0.42f, h * 0.64f); lineTo(w * 0.58f, h * 0.64f); lineTo(w * 0.58f, h * 0.88f)
                }
            }
            Screen.SUBS -> {
                drawRoundRect(color, Offset(w * 0.08f, h * 0.2f), Size(w * 0.84f, h * 0.6f), CornerRadius(w * 0.16f), style = line)
                path { moveTo(w * 0.42f, h * 0.38f); lineTo(w * 0.62f, h * 0.5f); lineTo(w * 0.42f, h * 0.62f); close() }
            }
            Screen.SEARCH -> {
                drawCircle(color, w * 0.28f, Offset(w * 0.43f, h * 0.43f), style = line)
                path { moveTo(w * 0.64f, h * 0.64f); lineTo(w * 0.88f, h * 0.88f) }
            }
            Screen.LIBRARY -> {
                drawRoundRect(color, Offset(w * 0.12f, h * 0.14f), Size(w * 0.18f, h * 0.74f), CornerRadius(w * 0.04f), style = line)
                drawRoundRect(color, Offset(w * 0.38f, h * 0.14f), Size(w * 0.18f, h * 0.74f), CornerRadius(w * 0.04f), style = line)
                path { moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.78f, h * 0.16f); lineTo(w * 0.92f, h * 0.84f); lineTo(w * 0.76f, h * 0.88f); close() }
            }
            Screen.CRT -> {
                drawRoundRect(color, Offset(w * 0.08f, h * 0.32f), Size(w * 0.84f, h * 0.58f), CornerRadius(w * 0.14f), style = line)
                path { moveTo(w * 0.32f, h * 0.1f); lineTo(w * 0.5f, h * 0.3f); lineTo(w * 0.68f, h * 0.1f) }
            }
            Screen.SETUP -> {
                drawCircle(color, w * 0.14f, center, style = line)
                drawCircle(color, w * 0.3f, center, style = line)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = kotlin.math.cos(a).toFloat()
                    val sn = kotlin.math.sin(a).toFloat()
                    path { moveTo(center.x + c * w * 0.3f, center.y + sn * h * 0.3f); lineTo(center.x + c * w * 0.42f, center.y + sn * h * 0.42f) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ home

@Composable
fun HomeScreen(app: MainActivity) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        app.update?.let { u ->
            Row(
                Modifier
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.card)
                    .border(1.dp, p.accent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Thor-Tube v${u.version} is out", color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Pill("Download", primary = true) { app.openUpdate() }
                Pill("Later") { app.updateLater() }
            }
        }
        // Tabs.
        val chips = rememberLazyListState()
        LaunchedEffect(app.tab) { chips.animateScrollToItem(app.tab) }
        LazyRow(
            state = chips,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(app.homeTabs) { i, t ->
                Chip(
                    t.label,
                    i == app.tab,
                    onLongClick = if (i > 0) ({ app.editingTab = i }) else null,
                    onFocus = { f -> if (f) app.focusedChip = i else if (app.focusedChip == i) app.focusedChip = null },
                ) { app.selectTab(i, refresh = i == app.tab) }
            }
            item {
                Chip("+ Add", false, outlined = true) {
                    app.loadTabSuggestions()
                    app.tabSheet = -1
                }
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
                    header = if (app.homeTabs.getOrNull(tab)?.isForYou == true && !app.signedIn) {
                        { SignInBanner { app.screen = Screen.SETUP } }
                    } else null,
                    onOpen = { v -> app.open(v, st.videos) },
                )
            }
        }
    }
    // Add / rename sheet and the per-tab menu, over the feed.
    app.tabSheet?.let { TabSheet(app, it) }
    app.editingTab?.let { TabMenu(app, it) }
    }
}

@Composable
fun Chip(
    label: String,
    on: Boolean,
    outlined: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocus: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) p.chipOn else if (outlined) Color.Transparent else p.card)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) p.accent else if (outlined) p.dim else Color.Transparent,
                RoundedCornerShape(50),
            )
            .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (on) p.chipOnText else if (outlined) p.accent else p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Add a tab (index -1) or rename one: type anything, or pick a suggestion. */
@Composable
private fun TabSheet(app: MainActivity, index: Int) {
    val p = LocalPalette.current
    val renaming = index > 0
    BackHandler { app.tabSheet = null }
    var text by remember(index) { mutableStateOf(if (renaming) app.homeTabs.getOrNull(index)?.label.orEmpty() else "") }
    val field = remember { FocusRequester() }
    LaunchedEffect(index) { if (renaming) runCatching { field.requestFocus() } }
    fun submit() {
        if (renaming) app.renameTab(index, text) else app.addTab(Tab(text.trim(), text.trim()))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xA0000000))
            .clickable(indication = null, interactionSource = null) { app.tabSheet = null },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(p.bg)
                .border(1.dp, p.railLine, RoundedCornerShape(16.dp))
                .clickable(indication = null, interactionSource = null) { }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (renaming) "Rename tab" else "Add a tab", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(32) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = p.text, fontSize = 12.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(field)
                        .clip(RoundedCornerShape(50))
                        .background(p.card)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(if (renaming) "Tab name" else "Topic or search, like \"PS2 lost media\"", color = p.dim, fontSize = 12.sp)
                        inner()
                    },
                )
                Pill(if (renaming) "Save" else "Add tab", primary = true) { submit() }
            }
            if (!renaming) {
                val (topics, channels) = app.tabSuggestions.partition { it.channel == null }
                if (topics.isNotEmpty()) SuggestionRow("Quick picks", topics) { app.addTab(it) }
                if (channels.isNotEmpty()) SuggestionRow("Your subscriptions (newest uploads)", channels) { app.addTab(it) }
            }
        }
    }
}

@Composable
private fun SuggestionRow(title: String, tabs: List<Tab>, onPick: (Tab) -> Unit) {
    val p = LocalPalette.current
    Column {
        Text(title, color = p.dim, fontSize = 9.sp, modifier = Modifier.padding(bottom = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tabs.size) { i -> Chip(tabs[i].label, false) { onPick(tabs[i]) } }
        }
    }
}

/** Hold a tab (or highlight it and press Y): rename, move, or remove it. */
@Composable
private fun TabMenu(app: MainActivity, index: Int) {
    val p = LocalPalette.current
    val t = app.homeTabs.getOrNull(index) ?: return
    BackHandler { app.editingTab = null }
    val first = remember { FocusRequester() }
    LaunchedEffect(index) { runCatching { first.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xA0000000))
            .clickable(indication = null, interactionSource = null) { app.editingTab = null },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(p.bg)
                .border(1.dp, p.railLine, RoundedCornerShape(16.dp))
                .clickable(indication = null, interactionSource = null) { }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(t.label, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (t.channel != null) "Channel: newest uploads" else "Search: ${t.query}", color = p.dim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MenuRow("Rename", Modifier.focusRequester(first)) { app.editingTab = null; app.tabSheet = index }
            if (index > 1) MenuRow("Move left") { app.moveTab(index, -1) }
            if (index < app.homeTabs.lastIndex) MenuRow("Move right") { app.moveTab(index, 1) }
            MenuRow("Remove", danger = true) { app.removeTab(index) }
        }
    }
}

@Composable
private fun MenuRow(label: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (danger) Color(0xFFFF6E6E) else p.text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(p.card)
            .border(2.dp, if (focused) p.accent else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
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
            Section("Controls") {
                CONTROLS.forEach { (keys, what) ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(keys, color = p.text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(118.dp))
                        Text(what, color = p.dim, fontSize = 9.5.sp)
                    }
                }
            }
        }
        item {
            Section("About") {
                Text("Thor-Tube ${BuildConfig.VERSION_NAME}. Unofficial; not made by YouTube or AYN.", color = p.dim, fontSize = 9.sp)
                Text(app.updateStatus, color = if (app.update != null) p.accent else p.dim, fontSize = 9.sp, fontWeight = if (app.update != null) FontWeight.Bold else FontWeight.Normal)
                if (app.update != null) {
                    Spacer(Modifier.height(6.dp))
                    Pill("Get v${app.update?.version}", primary = true) { app.openUpdate() }
                }
                Spacer(Modifier.height(8.dp))
                Pill("Share logs") { app.shareLogs() }
                Text("Sends this app's logs (no video links, searches or account details) through Android's share menu.", color = p.dim, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
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

/** The cheat sheet in Setup: button or gesture, then what it does. */
private val CONTROLS = listOf(
    "D-pad / left stick" to "Move the highlight. Hold to repeat.",
    "A / B" to "Open / back",
    "L1 / R1" to "Switch tabs. In a video: back / ahead 10 s",
    "L2 / R2 tap" to "Jump a page up / down",
    "L2 or R2 + stick" to "In a video: scrub, let go to jump there",
    "Right stick" to "Scroll smoothly",
    "Y" to "Video full screen. On a Home tab: edit it",
    "X / Start" to "Bring back the small video / Setup",
    "Swipe the sidebar" to "Down: to the bottom screen. Up: to the top",
    "Double-tap video sides" to "Back / ahead 10 s (keep tapping for more)",
    "Swipe up on video" to "Full screen (top screen: Up next & comments)",
    "Swipe down on video" to "Leave full screen, then shrink to the corner",
    "Hold a Home tab" to "Rename, move or remove it",
)
