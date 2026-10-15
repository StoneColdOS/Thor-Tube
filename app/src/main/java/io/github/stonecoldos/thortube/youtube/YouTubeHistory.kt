package io.github.stonecoldos.thortube.youtube

import android.net.Uri
import io.github.stonecoldos.thortube.AppLog
import io.github.stonecoldos.thortube.ThorTubeApp
import java.security.SecureRandom
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Tells YouTube what you watch here, so it lands in your watch history (with how far you got)
 * and your recommendations learn from it. Same reports the YouTube TV app sends:
 * one "playback" ping creates the history entry, "watchtime" pings update the position.
 * Only when signed in; failures are ignored (history is a nice-to-have, never a blocker).
 */
object YouTubeHistory {

    private const val TAG = "ThorTube"
    private const val REPORT_EVERY_MS = 30_000L
    private const val RECORD_RENEW_MS = 30 * 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val random = SecureRandom()

    private class Session(
        val videoId: String,
        val lengthSec: Float,
        val cpn: String,
        val ei: String,
        val vm: String,
        val of: String,
    ) {
        var lastSent = 0L
        var lastPos = 0f
        var recordAt = 0L
        var finished = false
    }

    private var session: Session? = null
    private var pendingId: String? = null

    /** A new video started. Looks up its tracking ids in the background. */
    fun start(videoUrl: String, lengthSec: Long) {
        if (!YouTubeAccount.isSignedIn) return
        val id = videoId(videoUrl) ?: return
        if (session?.videoId == id || pendingId == id) return
        pendingId = id
        scope.launch {
            lock.withLock {
                val s = runCatching { open(id, lengthSec) }
                    .onFailure { AppLog.d(TAG, "No YouTube history for $id: ${it.message}") }
                    .getOrNull()
                if (pendingId == id) {
                    session = s
                    pendingId = null
                }
            }
        }
    }

    /** Called every few seconds while something plays; sends a report every 30 seconds. */
    fun tick(videoUrl: String?, positionSec: Float, playing: Boolean) {
        val s = session ?: return
        if (videoId(videoUrl ?: return) != s.videoId || !playing) return
        if (System.currentTimeMillis() - s.lastSent < REPORT_EVERY_MS) return
        report(s, positionSec)
    }

    /** Leaving a video (or the app): send where you stopped. */
    fun flush(videoUrl: String?, positionSec: Float) {
        val s = session ?: return
        if (videoId(videoUrl ?: return) != s.videoId) return
        if (positionSec - s.lastPos >= 1f || s.lastSent == 0L) report(s, positionSec)
    }

    fun reset() {
        session = null
        pendingId = null
    }

    private fun report(s: Session, positionSec: Float) {
        if (s.finished) return
        s.lastSent = System.currentTimeMillis()
        val from = s.lastPos
        s.lastPos = positionSec
        scope.launch {
            runCatching { send(s, from, positionSec) }
                .onFailure { AppLog.d(TAG, "YouTube history report failed: ${it.message}") }
        }
    }

    private suspend fun send(s: Session, fromSec: Float, toSec: Float) {
        val token = YouTubeAccount.token() ?: return
        val len = s.lengthSec
        // Within the last 5%: mark it watched to the end, like the TV app does.
        if (len > 0 && len - toSec < len * 0.05f) {
            ping(token, "playback", s, "final" to "1")
            ping(token, "watchtime", s, "final" to "1", "st" to fmt(len), "et" to fmt(len))
            s.finished = true
            AppLog.d(TAG, "YouTube history: ${s.videoId} watched to the end")
            return
        }
        val now = System.currentTimeMillis()
        if (s.recordAt == 0L || now - s.recordAt > RECORD_RENEW_MS) {
            ping(token, "playback", s, "len" to fmt(len), "cmt" to fmt(fromSec))
            s.recordAt = now
        }
        ping(token, "watchtime", s, "len" to fmt(len), "st" to fmt(fromSec), "et" to fmt(toSec), "cmt" to fmt(toSec))
        AppLog.d(TAG, "YouTube history: ${s.videoId} at ${toSec.toInt()}s")
    }

    private fun ping(token: String, kind: String, s: Session, vararg extra: Pair<String, String>) {
        val url = "https://www.youtube.com/api/stats/$kind".toHttpUrl().newBuilder()
            .addQueryParameter("ns", "yt")
            .addQueryParameter("ver", "2")
            .addQueryParameter("docid", s.videoId)
            .addQueryParameter("cpn", s.cpn)
            .addQueryParameter("ei", s.ei)
            .addQueryParameter("vm", s.vm)
            .addQueryParameter("of", s.of)
            .apply { extra.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        ThorTubeApp.http.newCall(
            Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", YouTubeFeed.TV_USER_AGENT)
                .header("Referer", "https://www.youtube.com/tv")
                .build(),
        ).execute().use { check(it.isSuccessful) { "$kind ping: ${it.code}" } }
    }

    /** Asks YouTube's player (signed in, as the TV app) for this video's tracking ids. */
    private suspend fun open(id: String, lengthSec: Long): Session? {
        val token = YouTubeAccount.token() ?: return null
        val locale = Locale.getDefault()
        val body = JSONObject()
            .put(
                "context",
                JSONObject()
                    .put(
                        "client",
                        JSONObject()
                            .put("clientName", "TVHTML5")
                            .put("clientVersion", YouTubeFeed.TV_VERSION)
                            .put("hl", locale.language.ifBlank { "en" })
                            .put("gl", locale.country.ifBlank { "US" }),
                    )
                    .put("user", JSONObject().put("lockedSafetyMode", false)),
            )
            .put("videoId", id)
            .put("racyCheckOk", true)
            .put("contentCheckOk", true)
        val json = ThorTubeApp.http.newCall(
            Request.Builder().url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", YouTubeFeed.TV_USER_AGENT)
                .header("Referer", "https://www.youtube.com/tv")
                .header("X-YouTube-Client-Name", "7")
                .header("X-YouTube-Client-Version", YouTubeFeed.TV_VERSION)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build(),
        ).execute().use {
            check(it.isSuccessful) { "player: ${it.code}" }
            JSONObject(it.body!!.string())
        }
        val tracking = json.optJSONObject("playbackTracking")
        val base = tracking?.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl")
            ?: tracking?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")
            ?: error("no tracking urls (${json.optJSONObject("playabilityStatus")?.optString("status")})")
        val q = Uri.parse(base)
        val ei = q.getQueryParameter("ei") ?: error("no ei")
        val vm = q.getQueryParameter("vm").orEmpty()
        val of = q.getQueryParameter("of").orEmpty()
        val len = lengthSec.takeIf { it > 0 }?.toFloat()
            ?: json.optJSONObject("videoDetails")?.optString("lengthSeconds")?.toFloatOrNull() ?: 0f
        AppLog.d(TAG, "YouTube history on for $id")
        return Session(id, len, cpn(), ei, vm, of)
    }

    private fun cpn(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return String(CharArray(16) { chars[random.nextInt(chars.length)] })
    }

    private fun fmt(sec: Float) = String.format(Locale.US, "%.3f", sec)

    private fun videoId(url: String): String? =
        Regex("""(?:[?&]v=|youtu\.be/|/shorts/|/live/)([A-Za-z0-9_-]{11})""").find(url)?.groupValues?.get(1)
}
