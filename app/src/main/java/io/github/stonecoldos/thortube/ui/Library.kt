package io.github.stonecoldos.thortube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stonecoldos.thortube.youtube.YtChannel
import io.github.stonecoldos.thortube.youtube.YtPlaylist

/** Shown on account screens when you're not signed in. */
@Composable
private fun NeedsSignIn(app: MainActivity, what: String) {
    Message("Sign in to see $what.", "Sign in") { app.screen = Screen.SETUP }
}

// ------------------------------------------------------------------ subscriptions

/** Newest from everyone you follow, with your channels across the top to narrow it to one. */
@Composable
fun SubsScreen(app: MainActivity) {
    BackHandler { app.screen = Screen.HOME }
    if (!app.signedIn) {
        NeedsSignIn(app, "your subscriptions")
        return
    }
    LaunchedEffect(Unit) { app.loadSubs(app.subsSelected, refresh = false) }
    Column(Modifier.fillMaxSize()) {
        if (app.subsChannels.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all") { ChannelBubble("All", null, app.subsSelected == -1) { app.loadSubs(-1) } }
                items(app.subsChannels.size, key = { "c$it" }) { i ->
                    val c: YtChannel = app.subsChannels[i]
                    ChannelBubble(c.name, c.avatar, app.subsSelected == i) { app.loadSubs(i) }
                }
            }
        }
        val st = app.subsFeed
        AnimatedContent(app.subsSelected, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "subs") { sel ->
            when {
                st.loading && st.videos.isEmpty() -> Message("Loading...")
                st.error != null -> Message(st.error, "Try again") { app.loadSubs(sel) }
                st.videos.isEmpty() -> Message("Nothing new here.")
                else -> VideoGrid(app, st.videos, SUBS_GRID + sel + 1, header = null) { v -> app.open(v, st.videos) }
            }
        }
    }
}

@Composable
private fun ChannelBubble(name: String, avatar: String?, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .border(2.dp, if (on || focused) p.accent else Color.Transparent, CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (avatar == null) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(p.card), contentAlignment = Alignment.Center) {
                    Text("All", color = p.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Avatar(name, avatar, 32.dp)
            }
        }
        Text(
            name,
            color = if (on) p.text else p.dim,
            fontSize = 7.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ------------------------------------------------------------------ library

enum class LibTab(val label: String) { HISTORY("History"), WATCH_LATER("Watch later"), LIKED("Liked"), PLAYLISTS("Playlists") }

@Composable
fun LibraryScreen(app: MainActivity) {
    val open = app.openPlaylist
    BackHandler { if (open != null) app.closePlaylist() else app.screen = Screen.HOME }
    if (!app.signedIn) {
        NeedsSignIn(app, "your history, Watch Later and playlists")
        return
    }
    LaunchedEffect(app.libTab) { app.loadLibrary(app.libTab, refresh = false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (open != null) {
                Pill("‹ Back") { app.closePlaylist() }
                Text(open.title, color = LocalPalette.current.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                LibTab.entries.forEach { t ->
                    Chip(t.label, t == app.libTab) {
                        if (t == app.libTab) app.loadLibrary(t, refresh = true) else app.libTab = t
                    }
                }
            }
        }
        AnimatedContent(
            targetState = open?.browseId ?: app.libTab.name,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "library",
        ) { key ->
            if (open != null && key == open.browseId) {
                val st = app.playlistFeed
                when {
                    st.loading && st.videos.isEmpty() -> Message("Loading...")
                    st.error != null -> Message(st.error)
                    st.videos.isEmpty() -> Message("This playlist is empty.")
                    else -> VideoGrid(app, st.videos, PLAYLIST_GRID + app.playlistRound, header = null) { v -> app.open(v, st.videos) }
                }
            } else if (app.libTab == LibTab.PLAYLISTS) {
                val st = app.libPlaylists
                when {
                    st.loading && st.items.isEmpty() -> Message("Loading...")
                    st.error != null -> Message(st.error, "Try again") { app.loadLibrary(LibTab.PLAYLISTS, refresh = true) }
                    st.items.isEmpty() -> Message("No playlists yet.")
                    else -> PlaylistGrid(st.items) { app.openPlaylist(it) }
                }
            } else {
                val st = app.libFeeds[app.libTab] ?: FeedState(loading = true)
                when {
                    st.loading && st.videos.isEmpty() -> Message("Loading...")
                    st.error != null -> Message(st.error, "Try again") { app.loadLibrary(app.libTab, refresh = true) }
                    st.videos.isEmpty() -> Message("Nothing here yet.")
                    else -> VideoGrid(app, st.videos, LIB_GRID + app.libTab.ordinal, header = null) { v -> app.open(v, st.videos) }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGrid(items: List<YtPlaylist>, onOpen: (YtPlaylist) -> Unit) {
    val p = LocalPalette.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.browseId }) { pl ->
            var focused by remember { mutableStateOf(false) }
            Column(Modifier.onFocusChanged { focused = it.isFocused }.clickable { onOpen(pl) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .border(2.5.dp, if (focused) p.accent else Color.Transparent, RoundedCornerShape(10.dp))
                        .padding(2.5.dp)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    Thumb(pl.thumbnail.ifEmpty { null }, Modifier.fillMaxSize())
                    Text(
                        "≡ Playlist",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                            .background(Color(0xD0000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                Text(pl.title, color = p.text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                if (pl.subtitle.isNotEmpty()) Text(pl.subtitle, color = p.dim, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

const val SUBS_GRID = 10_000
const val LIB_GRID = 20_000
const val PLAYLIST_GRID = 30_000

// ------------------------------------------------------------------ CRT

/** The retro picture effect: off, a 90s curved tube, or a 2000s flat tube. Applies to every video. */
@Composable
fun CrtScreen(app: MainActivity) {
    val p = LocalPalette.current
    BackHandler { app.screen = Screen.HOME }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("CRT effect", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Makes videos look like they're on an old TV. Works in the player and the mini-player.", color = p.dim, fontSize = 9.5.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
        CrtMode.entries.forEach { m ->
            val on = m == app.crtMode
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) p.railOn else p.card)
                    .border(2.dp, if (focused) p.accent else Color.Transparent, RoundedCornerShape(12.dp))
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { app.setCrt(m) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(14.dp).clip(CircleShape).border(2.dp, if (on) p.accent else p.dim, CircleShape).padding(3.dp),
                ) {
                    if (on) Box(Modifier.fillMaxSize().clip(CircleShape).background(p.accent))
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(m.label, color = p.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(m.blurb, color = p.dim, fontSize = 9.sp)
                }
            }
        }
        if (app.nowPlaying != null) {
            Text("Your video in the corner shows the effect live.", color = p.dim, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ------------------------------------------------------------------ channel page

/** A channel: who they are, Subscribe and "Add to Home", then their newest uploads. */
@Composable
fun ChannelScreen(app: MainActivity, ui: ChannelUi) {
    val p = LocalPalette.current
    BackHandler { app.closeChannel() }
    Box(Modifier.fillMaxSize().background(p.bg)) {
        val d = ui.data
        when {
            d == null && ui.loading -> Message("Loading ${ui.name.ifBlank { "channel" }}...")
            d == null -> Message(ui.error ?: "Couldn't load this channel.", "Try again") { app.openChannel(ui.url, ui.name) }
            else -> VideoGrid(
                app = app,
                videos = d.videos,
                tab = MainActivity.CHANNEL_GRID,
                header = { ChannelHeader(app, ui, d) },
                onOpen = { v -> app.open(v, d.videos) },
            )
        }
    }
}

@Composable
private fun ChannelHeader(app: MainActivity, ui: ChannelUi, d: io.github.stonecoldos.thortube.data.ChannelData) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Pill("‹  Back") { app.closeChannel() }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(d.name, d.avatar, 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subs = if (d.subscribers > 0) "${shortCount(d.subscribers)} subscribers" else ""
                Text(listOf(subs, "${d.videos.size} recent videos").filter { it.isNotBlank() }.joinToString("  ·  "), color = p.dim, fontSize = 9.5.sp)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (app.signedIn) {
                Pill(if (ui.subscribed == true) "✓ Subscribed" else "Subscribe", primary = ui.subscribed != true) { app.toggleChannelSubscribe() }
            }
            val pinned = app.homeTabs.any { it.channel == d.url }
            Pill(if (pinned) "✓ On Home" else "+ Add to Home") { if (!pinned) app.addChannelTab() }
        }
        if (d.videos.isEmpty()) Text("No videos yet.", color = p.dim, fontSize = 10.sp, modifier = Modifier.padding(top = 14.dp))
    }
}

private fun shortCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
    n >= 1_000 -> "%.1fK".format(n / 1_000.0).replace(".0K", "K")
    else -> n.toString()
}
