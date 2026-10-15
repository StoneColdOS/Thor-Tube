package io.github.stonecoldos.thortube.youtube

import io.github.stonecoldos.thortube.AppLog
import org.json.JSONArray
import org.json.JSONObject

/** Where you stand with a video: liked it? subscribed to who made it? */
data class VideoState(val liked: Boolean, val subscribed: Boolean, val channelId: String?)

/**
 * Like, subscribe and Watch Later, sent the way the YouTube TV app sends them.
 * All of these need you signed in; each returns true if YouTube accepted it.
 */
object YouTubeActions {

    private const val TAG = "ThorTube"

    /** Reads your like and subscribe state from the signed-in "watch next" response. */
    suspend fun state(videoId: String): VideoState? {
        val r = runCatching { YouTubeFeed.call("next", JSONObject().put("videoId", videoId)) }
            .onFailure { AppLog.d(TAG, "Couldn't read like/subscribe state: ${it.message}") }
            .getOrNull() ?: return null
        var liked: Boolean? = null
        var subscribed: Boolean? = null
        var channel: String? = null
        fun walk(o: Any?, path: String) {
            when (o) {
                is JSONObject -> o.keys().forEach { k ->
                    val v = o.opt(k)
                    when {
                        // The video's own like button lives in its metadata (not in the playlist or related items).
                        k == "likeStatus" && v is String && liked == null && "videoMetadataRenderer" in path -> liked = v == "LIKE"
                        // The first owner block is the video's creator.
                        k == "subscribeButtonRenderer" && v is JSONObject && subscribed == null && "videoOwnerRenderer" in path -> {
                            subscribed = v.optBoolean("subscribed")
                            channel = v.optString("channelId").ifEmpty { null }
                        }
                        else -> walk(v, "$path>$k")
                    }
                }
                is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i), path)
            }
        }
        walk(r, "")
        return VideoState(liked ?: false, subscribed ?: false, channel)
    }

    suspend fun like(videoId: String, on: Boolean) =
        send(if (on) "like/like" else "like/removelike", JSONObject().put("target", JSONObject().put("videoId", videoId)))

    suspend fun subscribe(channelId: String, on: Boolean) =
        send(
            if (on) "subscription/subscribe" else "subscription/unsubscribe",
            JSONObject().put("channelIds", JSONArray().put(channelId)).put("params", ""),
        )

    suspend fun watchLater(videoId: String, on: Boolean) =
        send(
            "browse/edit_playlist",
            JSONObject().put("playlistId", "WL").put(
                "actions",
                JSONArray().put(
                    if (on) JSONObject().put("addedVideoId", videoId).put("action", "ACTION_ADD_VIDEO")
                    else JSONObject().put("removedVideoId", videoId).put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID"),
                ),
            ),
        )

    private suspend fun send(endpoint: String, body: JSONObject): Boolean =
        runCatching { YouTubeFeed.call(endpoint, body) != null }
            .onFailure { AppLog.w(TAG, "YouTube $endpoint failed", it) }
            .getOrDefault(false)
            .also { AppLog.i(TAG, "YouTube $endpoint: ${if (it) "ok" else "failed"}") }
}
