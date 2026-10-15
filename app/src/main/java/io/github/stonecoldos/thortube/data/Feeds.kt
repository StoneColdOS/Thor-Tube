package io.github.stonecoldos.thortube.data

import io.github.stonecoldos.thortube.AppLog
import io.github.stonecoldos.thortube.youtube.YouTubeAccount
import io.github.stonecoldos.thortube.youtube.YouTubeFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** A row of tabs across the top of Home. */
data class Tab(val label: String, val query: String?) {
    val isForYou get() = query == null
}

val HOME_TABS = listOf(
    Tab("For You", null),
    Tab("Gaming", "gaming"),
    Tab("Handhelds", "retro handheld"),
    Tab("Tech", "tech news"),
    Tab("Music", "music video"),
    Tab("Science", "science explained"),
    Tab("Cooking", "easy cooking recipes"),
)

/** Where the videos for each screen come from. */
object Feeds {

    private const val TAG = "ThorTube"

    /** Signed in: your YouTube home. Otherwise a mix of the topic tabs, newest first. */
    suspend fun forYou(): List<Video> {
        if (YouTubeAccount.isSignedIn) {
            val home = runCatching { YouTubeFeed.home() }
                .onFailure { AppLog.w(TAG, "YouTube home failed", it) }
                .getOrDefault(emptyList())
            if (home.isNotEmpty()) return home
        }
        return coroutineScope {
            HOME_TABS.mapNotNull { it.query }.map { q -> async { runCatching { search(q) }.getOrDefault(emptyList()).take(8) } }
                .awaitAll()
                .let { lists -> (0 until 8).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } } }
                .distinctBy { it.url }
        }
    }

    /** A topic tab: what YouTube finds for the search, newest first. */
    suspend fun topic(query: String): List<Video> = search(query).sortedByDescending { it.uploadedAt }

    /** YouTube search for videos (no Shorts, no live streams). */
    suspend fun search(query: String): List<Video> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory
            .fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        SearchInfo.getInfo(service, handler).relatedItems.videos()
            .also { AppLog.i(TAG, "Search: ${it.size} videos") }
    }

    fun List<InfoItem>.videos(): List<Video> = filterIsInstance<StreamInfoItem>()
        .filter { it.streamType == StreamType.VIDEO_STREAM && !it.isShortFormContent && "/shorts/" !in it.url }
        .map {
            Video(
                url = it.url,
                title = it.name,
                durationSecs = it.duration,
                uploaded = it.textualUploadDate ?: "",
                uploadedAt = it.uploadDate?.instant?.toEpochMilli() ?: 0,
                uploaderName = it.uploaderName ?: "",
                uploaderUrl = it.uploaderUrl ?: "",
            )
        }
}
