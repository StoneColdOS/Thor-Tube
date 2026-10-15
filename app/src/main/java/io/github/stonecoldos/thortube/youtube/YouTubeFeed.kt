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

/**
 * Your YouTube home screen (and subscriptions), read the way the YouTube TV app reads them:
 * signed in, as a grid of "tiles". Ads, Shorts and live streams are left out.
 */
object YouTubeFeed {

    private const val TAG = "ThorTube"
    private const val BROWSE = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"
    const val TV_VERSION = "7.20260901.15.00"
    const val TV_USER_AGENT =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"
    private val JSON = "application/json".toMediaType()

    /** The TV app's home screen is "default"; subscriptions are "FEsubscriptions". */
    suspend fun home(want: Int = 60): List<Video> = load("default", want)

    suspend fun subscriptions(want: Int = 60): List<Video> = load("FEsubscriptions", want)

    /** First page, then follow "more" tokens (whole sections first, then single shelves) until there's enough. */
    private suspend fun load(browseId: String, want: Int): List<Video> = withContext(Dispatchers.IO) {
        val videos = LinkedHashMap<String, Video>()
        val first = Page.parse(browse(JSONObject().put("browseId", browseId)) ?: return@withContext emptyList())
        first.videos.forEach { videos.putIfAbsent(it.url, it) }
        val sections = ArrayDeque(first.sections)
        val shelves = ArrayDeque(first.shelves)
        var requests = 1
        while (videos.size < want && requests < 10 && (sections.isNotEmpty() || shelves.isNotEmpty())) {
            val token = sections.removeFirstOrNull() ?: shelves.removeFirst()
            val page = runCatching { browse(JSONObject().put("continuation", token))?.let(Page::parse) }
                .onFailure { AppLog.d(TAG, "YouTube page failed: ${it.message}") }.getOrNull() ?: break
            requests++
            page.videos.forEach { videos.putIfAbsent(it.url, it) }
            sections.addAll(page.sections)
            shelves.addAll(page.shelves)
        }
        AppLog.i(TAG, "YouTube $browseId: ${videos.size} videos in $requests requests")
        videos.values.toList()
    }

    /** One signed-in request. Retries once with a fresh token if YouTube says it's stale. */
    private suspend fun browse(what: JSONObject): JSONObject? {
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
            val request = Request.Builder().url(BROWSE)
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
                check(r.isSuccessful) { "YouTube said ${r.code}" }
                return JSONObject(r.body!!.string())
            }
        }
        return null
    }

    /** What one response holds: videos, plus tokens for more. */
    private class Page(val videos: List<Video>, val sections: List<String>, val shelves: List<String>) {
        companion object {
            private val DURATION = Regex("""^\d{1,2}(:\d{2}){1,2}$""")

            fun parse(root: JSONObject): Page {
                val videos = ArrayList<Video>()
                val sections = ArrayList<String>()
                val shelves = ArrayList<String>()
                fun walk(o: Any?, ad: Boolean, inShelf: Boolean) {
                    when (o) {
                        is JSONObject -> o.keys().forEach { k ->
                            val v = o.opt(k)
                            when {
                                k == "tileRenderer" && v is JSONObject -> if (!ad) tile(v)?.let(videos::add)
                                k == "nextContinuationData" && v is JSONObject ->
                                    v.optString("continuation").takeIf { it.isNotEmpty() }?.let { (if (inShelf) shelves else sections) += it }
                                else -> walk(v, ad || k == "adSlotRenderer", inShelf || k == "horizontalListRenderer")
                            }
                        }
                        is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i), ad, inShelf)
                    }
                }
                walk(root, false, false)
                return Page(videos, sections, shelves)
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
