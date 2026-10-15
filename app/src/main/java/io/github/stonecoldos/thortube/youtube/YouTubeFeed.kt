package io.github.stonecoldos.thortube.youtube

import io.github.stonecoldos.thortube.AppLog
import io.github.stonecoldos.thortube.ThorTubeApp
import io.github.stonecoldos.thortube.data.Video
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** A playlist on your account (yours or saved). [browseId] starts with "VL". */
data class YtPlaylist(val browseId: String, val title: String, val thumbnail: String, val subtitle: String)

/** A channel you're subscribed to; [params] narrows the subscriptions feed to just them. */
data class YtChannel(val name: String, val avatar: String, val browseId: String, val params: String?)

/** What one signed-in page holds. */
data class YtPage(val videos: List<Video>, val playlists: List<YtPlaylist>, val channels: List<YtChannel>)

/**
 * Your YouTube home screen (and subscriptions), read the way the YouTube TV app reads them:
 * signed in, as a grid of "tiles". Ads, Shorts and live streams are left out.
 */
object YouTubeFeed {

    private const val TAG = "ThorTube"
    const val TV_VERSION = "7.20260901.15.00"
    const val TV_USER_AGENT =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"
    private val JSON = "application/json".toMediaType()

    /** The TV app's home screen is "default". */
    suspend fun home(want: Int = 60): List<Video> = page("default", want = want).videos

    /** The signed-in account's name and profile picture, or null if YouTube won't say. */
    suspend fun me(): Pair<String, String?>? {
        val r = call(
            "account/accounts_list",
            JSONObject().put("accountReadMask", JSONObject().put("returnOwner", true).put("returnBrandAccounts", true)),
        ) ?: return null
        val items = mutableListOf<JSONObject>()
        fun walk(o: Any?) {
            when (o) {
                is JSONObject -> {
                    o.optJSONObject("accountItem")?.let { items += it }
                    o.keys().forEach { walk(o.get(it)) }
                }
                is org.json.JSONArray -> for (i in 0 until o.length()) walk(o.get(i))
            }
        }
        walk(r)
        val me = items.firstOrNull { it.optBoolean("isSelected") } ?: items.firstOrNull() ?: return null
        val name = me.optJSONObject("accountName")?.let { n ->
            n.optString("simpleText").ifBlank {
                n.optJSONArray("runs")?.let { runs -> (0 until runs.length()).joinToString("") { runs.getJSONObject(it).optString("text") } }.orEmpty()
            }
        }.orEmpty()
        val thumbs = me.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")
        val photo = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
        return name to photo
    }

    /** Your subscriptions feed, plus the list of channels you're subscribed to. Pass a channel's [YtChannel.params] for just that one. */
    suspend fun subscriptions(params: String? = null, want: Int = 60): YtPage = page("FEsubscriptions", params, want)

    suspend fun history(want: Int = 60): List<Video> = page("FEhistory", want = want).videos
    suspend fun watchLater(want: Int = 100): List<Video> = page("VLWL", want = want).videos
    suspend fun liked(want: Int = 100): List<Video> = page("VLLL", want = want).videos
    suspend fun playlists(): List<YtPlaylist> = page("FEplaylist_aggregation", want = 0).playlists
    suspend fun playlist(browseId: String, want: Int = 100): List<Video> = page(browseId, want = want).videos

    /** First page, then follow "more" tokens (whole sections first, then single shelves) until there's enough. */
    suspend fun page(browseId: String, params: String? = null, want: Int = 60): YtPage = withContext(Dispatchers.IO) {
        val videos = LinkedHashMap<String, Video>()
        val request = JSONObject().put("browseId", browseId)
        if (params != null) request.put("params", params)
        val first = Page.parse(browse(request) ?: return@withContext YtPage(emptyList(), emptyList(), emptyList()))
        first.videos.forEach { videos.putIfAbsent(it.url, it) }
        val playlists = LinkedHashMap<String, YtPlaylist>()
        first.playlists.forEach { playlists.putIfAbsent(it.browseId, it) }
        val sections = ArrayDeque(first.sections)
        val shelves = ArrayDeque(first.shelves)
        var requests = 1
        while (videos.size < want && requests < 10 && (sections.isNotEmpty() || shelves.isNotEmpty())) {
            val token = sections.removeFirstOrNull() ?: shelves.removeFirst()
            val page = runCatching { browse(JSONObject().put("continuation", token))?.let(Page::parse) }
                .onFailure { AppLog.d(TAG, "YouTube page failed: ${it.message}") }.getOrNull() ?: break
            requests++
            page.videos.forEach { videos.putIfAbsent(it.url, it) }
            page.playlists.forEach { playlists.putIfAbsent(it.browseId, it) }
            sections.addAll(page.sections)
            shelves.addAll(page.shelves)
        }
        AppLog.i(TAG, "YouTube $browseId: ${videos.size} videos, ${playlists.size} playlists, ${first.channels.size} channels in $requests requests")
        YtPage(videos.values.toList(), playlists.values.toList(), first.channels)
    }

    private suspend fun browse(what: JSONObject): JSONObject? = call("browse", what)

    /**
     * One signed-in request to YouTube's API as the TV app ("browse", "next", "like/like", ...).
     * Retries once with a fresh token if YouTube says it's stale. Null when not signed in.
     */
    internal suspend fun call(endpoint: String, what: JSONObject): JSONObject? = withContext(Dispatchers.IO) { callBlocking(endpoint, what) }

    private suspend fun callBlocking(endpoint: String, what: JSONObject): JSONObject? {
        for (attempt in 0..1) {
            val token = YouTubeAccount.token(forceRefresh = attempt == 1) ?: return null
            val locale = Locale.getDefault()
            val body = JSONObject()
                .put(
                    "context",
                    JSONObject()
                        .put(
                            "client",
                            JSONObject()
                                .put("clientName", "TVHTML5")
                                .put("clientVersion", TV_VERSION)
                                .put("hl", locale.language.ifBlank { "en" })
                                .put("gl", locale.country.ifBlank { "US" })
                                .put("tvAppInfo", JSONObject().put("appQuality", "TV_APP_QUALITY_FULL_ANIMATION").put("zylonLeftNav", true))
                                .put("webpSupport", false),
                        )
                        .put("user", JSONObject().put("lockedSafetyMode", false)),
                )
            what.keys().forEach { body.put(it, what.get(it)) }
            val request = Request.Builder().url("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", TV_USER_AGENT)
                .header("Referer", "https://www.youtube.com/tv")
                .header("Origin", "https://www.youtube.com")
                .header("X-YouTube-Client-Name", "7")
                .header("X-YouTube-Client-Version", TV_VERSION)
                .post(body.toString().toRequestBody(JSON))
                .build()
            ThorTubeApp.http.newCall(request).execute().use { r ->
                if (r.code == 401 && attempt == 0) return@use   // stale token: refresh and try again
                check(r.isSuccessful) { "YouTube $endpoint said ${r.code}" }
                return JSONObject(r.body!!.string())
            }
        }
        return null
    }

    /** What one response holds: videos, plus tokens for more. */
    private class Page(
        val videos: List<Video>,
        val sections: List<String>,
        val shelves: List<String>,
        val playlists: List<YtPlaylist> = emptyList(),
        val channels: List<YtChannel> = emptyList(),
    ) {
        companion object {
            private val DURATION = Regex("""^\d{1,2}(:\d{2}){1,2}$""")

            fun parse(root: JSONObject): Page {
                val videos = ArrayList<Video>()
                val sections = ArrayList<String>()
                val shelves = ArrayList<String>()
                val playlists = ArrayList<YtPlaylist>()
                val channels = ArrayList<YtChannel>()
                fun walk(o: Any?, ad: Boolean, inShelf: Boolean) {
                    when (o) {
                        is JSONObject -> o.keys().forEach { k ->
                            val v = o.opt(k)
                            when {
                                k == "tileRenderer" && v is JSONObject -> if (!ad) {
                                    if (v.optString("contentType") == "TILE_CONTENT_TYPE_PLAYLIST") playlist(v)?.let(playlists::add)
                                    else tile(v)?.let(videos::add)
                                }
                                // Older TV layout, still used on some pages (history, channels).
                                k == "gridVideoRenderer" && v is JSONObject -> if (!ad) gridVideo(v)?.let(videos::add)
                                // The subscriptions page's left column: one tab per channel you follow.
                                k == "tvSecondaryNavSectionRenderer" && v is JSONObject && channels.isEmpty() -> {
                                    channels += navChannels(v)
                                    walk(v, ad, inShelf)
                                }
                                k == "nextContinuationData" && v is JSONObject ->
                                    v.optString("continuation").takeIf { it.isNotEmpty() }?.let { (if (inShelf) shelves else sections) += it }
                                else -> walk(v, ad || k == "adSlotRenderer", inShelf || k == "horizontalListRenderer")
                            }
                        }
                        is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i), ad, inShelf)
                    }
                }
                walk(root, false, false)
                return Page(videos, sections, shelves, playlists, channels)
            }

            private fun bestImage(o: JSONObject?): String {
                val list = o?.optJSONArray("thumbnails") ?: return ""
                val url = list.optJSONObject(list.length() - 1)?.optString("url").orEmpty()
                return if (url.startsWith("//")) "https:$url" else url
            }

            private fun navChannels(section: JSONObject): List<YtChannel> {
                val tabs = section.optJSONArray("tabs") ?: return emptyList()
                return (0 until tabs.length()).mapNotNull { i ->
                    val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: return@mapNotNull null
                    val ep = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint") ?: return@mapNotNull null
                    val params = ep.optString("params").ifEmpty { null } ?: return@mapNotNull null   // "All" has none
                    YtChannel(
                        name = tab.optString("title").ifEmpty { text(tab.optJSONObject("title")) },
                        avatar = bestImage(tab.optJSONObject("thumbnail")),
                        browseId = ep.optString("browseId"),
                        params = params,
                    )
                }
            }

            private fun playlist(t: JSONObject): YtPlaylist? {
                val browse = t.optJSONObject("onSelectCommand")?.optJSONObject("browseEndpoint")?.optString("browseId")
                    ?.ifEmpty { null } ?: t.optString("contentId").ifEmpty { null }?.let { if (it.startsWith("VL")) it else "VL$it" }
                    ?: return null
                val md = t.optJSONObject("metadata")?.optJSONObject("tileMetadataRenderer")
                val lines = md?.optJSONArray("lines")?.let { ls ->
                    (0 until ls.length()).flatMap { li ->
                        val items = ls.optJSONObject(li)?.optJSONObject("lineRenderer")?.optJSONArray("items")
                        (0 until (items?.length() ?: 0)).map { text(items!!.optJSONObject(it)?.optJSONObject("lineItemRenderer")?.optJSONObject("text")).trim() }
                    }
                }.orEmpty().filter { it.isNotEmpty() && it != "•" }
                return YtPlaylist(
                    browseId = browse,
                    title = text(md?.optJSONObject("title")),
                    thumbnail = bestImage(t.optJSONObject("header")?.optJSONObject("tileHeaderRenderer")?.optJSONObject("thumbnail")),
                    subtitle = lines.joinToString(" · "),
                )
            }

            private fun gridVideo(g: JSONObject): Video? {
                val id = g.optString("videoId").ifEmpty { return null }
                var duration = ""
                g.optJSONArray("thumbnailOverlays")?.let { overlays ->
                    for (i in 0 until overlays.length()) {
                        val st = overlays.optJSONObject(i)?.optJSONObject("thumbnailOverlayTimeStatusRenderer") ?: continue
                        if (st.optString("style") in setOf("LIVE", "UPCOMING", "SHORTS")) return null
                        duration = text(st.optJSONObject("text"))
                    }
                }
                val whenText = text(g.optJSONObject("publishedTimeText"))
                return Video(
                    url = "https://www.youtube.com/watch?v=$id",
                    title = text(g.optJSONObject("title")),
                    durationSecs = if (DURATION.matches(duration)) seconds(duration) else -1,
                    uploaded = whenText,
                    uploadedAt = approxTime(whenText),
                    uploaderName = text(g.optJSONObject("shortBylineText")),
                )
            }

            private fun text(o: JSONObject?): String {
                if (o == null) return ""
                if (o.has("simpleText")) return o.optString("simpleText")
                val runs = o.optJSONArray("runs") ?: return ""
                return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
            }

            private fun tile(t: JSONObject): Video? {
                if (t.optString("contentType") != "TILE_CONTENT_TYPE_VIDEO") return null   // Shorts, channels, playlists
                val id = t.optString("contentId").ifEmpty {
                    t.optJSONObject("onSelectCommand")?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
                }
                if (id.isEmpty()) return null
                val header = t.optJSONObject("header")?.optJSONObject("tileHeaderRenderer")
                var duration = ""
                header?.optJSONArray("thumbnailOverlays")?.let { overlays ->
                    for (i in 0 until overlays.length()) {
                        val s = overlays.optJSONObject(i)?.optJSONObject("thumbnailOverlayTimeStatusRenderer") ?: continue
                        if (s.optString("style") in setOf("LIVE", "UPCOMING")) return null
                        duration = text(s.optJSONObject("text"))
                    }
                }
                val md = t.optJSONObject("metadata")?.optJSONObject("tileMetadataRenderer") ?: return null
                val lines = md.optJSONArray("lines")?.let { ls ->
                    (0 until ls.length()).map { li ->
                        val items = ls.optJSONObject(li)?.optJSONObject("lineRenderer")?.optJSONArray("items")
                        (0 until (items?.length() ?: 0)).map { text(items!!.optJSONObject(it)?.optJSONObject("lineItemRenderer")?.optJSONObject("text")).trim() }
                            .filter { it.isNotEmpty() && it != "•" }
                    }
                }.orEmpty()
                val channel = lines.firstOrNull()?.firstOrNull().orEmpty()
                val rest = lines.drop(1).flatten() + lines.firstOrNull().orEmpty().drop(1)
                val whenText = rest.firstOrNull { "ago" in it.lowercase() }.orEmpty()
                return Video(
                    url = "https://www.youtube.com/watch?v=$id",
                    title = text(md.optJSONObject("title")),
                    durationSecs = if (DURATION.matches(duration)) seconds(duration) else -1,
                    uploaded = whenText,
                    uploadedAt = approxTime(whenText),
                    uploaderName = channel,
                )
            }

            private fun seconds(d: String) = d.split(":").fold(0L) { acc, p -> acc * 60 + (p.toLongOrNull() ?: 0) }

            /** "3 days ago" (or "Streamed 2 hours ago") to a rough timestamp; 0 if it can't tell. */
            private fun approxTime(s: String): Long {
                val m = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)""").find(s.lowercase()) ?: return 0
                val n = m.groupValues[1].toLong()
                val unit = when (m.groupValues[2]) {
                    "second" -> 1_000L
                    "minute" -> 60_000L
                    "hour" -> 3_600_000L
                    "day" -> 86_400_000L
                    "week" -> 7 * 86_400_000L
                    "month" -> 30 * 86_400_000L
                    else -> 365 * 86_400_000L
                }
                return System.currentTimeMillis() - n * unit
            }
        }
    }
}
