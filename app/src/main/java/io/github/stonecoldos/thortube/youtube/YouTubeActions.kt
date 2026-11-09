package io.github.stonecoldos.thortube.youtube

import io.github.stonecoldos.thortube.AppLog
import org.json.JSONArray
import org.json.JSONObject

/** One of your playlists in the Save list; [contains] is null when YouTube didn't say. */
data class SaveTarget(val playlistId: String, val title: String, val contains: Boolean?)

/** Where you stand with a video: liked it? subscribed to who made it? */
data class VideoState(val liked: Boolean, val subscribed: Boolean, val channelId: String?)

/**
 * Like, subscribe and saving to playlists, sent the way the YouTube TV app sends them.
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

    /** Adds a video to (or removes it from) one of your playlists. "WL" is Watch later. */
    suspend fun inPlaylist(playlistId: String, videoId: String, on: Boolean) =
        send(
            "browse/edit_playlist",
            JSONObject().put("playlistId", playlistId).put(
                "actions",
                JSONArray().put(
                    if (on) JSONObject().put("addedVideoId", videoId).put("action", "ACTION_ADD_VIDEO")
                    else JSONObject().put("removedVideoId", videoId).put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID"),
                ),
            ),
        )

    /**
     * Your playlists, each marked with whether [videoId] is already in it, from the same "Save to"
     * list YouTube shows. Null if YouTube didn't answer that way.
     */
    suspend fun saveTargets(videoId: String): List<SaveTarget>? {
        val r = runCatching { YouTubeFeed.call("playlist/get_add_to_playlist", JSONObject().put("videoIds", JSONArray().put(videoId))) }
            .onFailure { AppLog.d(TAG, "Save list failed: ${it.javaClass.simpleName}") }
            .getOrNull() ?: return null
        val out = LinkedHashMap<String, SaveTarget>()
        fun walk(o: Any?) {
            when (o) {
                is JSONObject -> o.keys().forEach { k ->
                    val v = o.opt(k)
                    if (k == "playlistAddToOptionRenderer" && v is JSONObject) {
                        val id = v.optString("playlistId")
                        val title = text(v.opt("title"))
                        if (id.isNotEmpty() && title.isNotEmpty()) {
                            val contains = when (v.optString("containsSelectedVideos")) {
                                "ALL", "SOME" -> true
                                "NONE" -> false
                                else -> null
                            }
                            out.putIfAbsent(id, SaveTarget(id, title, contains))
                        }
                    } else walk(v)
                }
                is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i))
            }
        }
        walk(r)
        AppLog.i(TAG, "Save list: ${out.size} playlists")
        return out.values.toList().ifEmpty { null }
    }

    private fun text(o: Any?): String = when (o) {
        is String -> o
        is JSONObject -> o.optString("simpleText").ifEmpty {
            val runs = o.optJSONArray("runs") ?: return@ifEmpty ""
            (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
        }
        else -> ""
    }

    private suspend fun send(endpoint: String, body: JSONObject): Boolean =
        runCatching { YouTubeFeed.call(endpoint, body) != null }
            .onFailure { AppLog.w(TAG, "YouTube $endpoint failed", it) }
            .getOrDefault(false)
            .also { AppLog.i(TAG, "YouTube $endpoint: ${if (it) "ok" else "failed"}") }
}
