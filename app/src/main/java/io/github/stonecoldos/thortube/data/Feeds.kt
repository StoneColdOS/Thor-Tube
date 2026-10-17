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

/**
 * A tab across the top of Home. For You is built in; every other tab is one you added:
 * either a search ([query]) or a channel's newest uploads ([channel] = its page link).
 */
data class Tab(val label: String, val query: String?, val channel: String? = null) {
    val isForYou get() = query == null && channel == null
}

val FOR_YOU = Tab("For You", null)

/** What a channel page shows. */
data class ChannelData(val url: String, val name: String, val avatar: String?, val subscribers: Long, val videos: List<Video>)

/** Quick picks offered when adding a tab (and the mix For You falls back to when signed out). */
val STARTER_TOPICS = listOf(
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

    /** Signed in: your YouTube home. Otherwise a mix of your tabs (or some starter topics), newest first. */
    suspend fun forYou(tabs: List<Tab> = emptyList()): List<Video> {
        if (YouTubeAccount.isSignedIn) {
            val home = runCatching { YouTubeFeed.home() }
                .onFailure { AppLog.w(TAG, "YouTube home failed", it) }
                .getOrDefault(emptyList())
            if (home.isNotEmpty()) return home
        }
        return coroutineScope {
            (tabs.mapNotNull { it.query }.ifEmpty { STARTER_TOPICS.mapNotNull { it.query } }).map { q -> async { runCatching { search(q) }.getOrDefault(emptyList()).take(8) } }
                .awaitAll()
                .let { lists -> (0 until 8).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } } }
                .distinctBy { it.url }
        }
    }

    /** What a tab shows: For You, a channel's newest uploads, or a search (newest first). */
    suspend fun forTab(tab: Tab, all: List<Tab>): List<Video> = when {
        tab.isForYou -> forYou(all)
        tab.channel != null -> channel(tab.channel)
        else -> topic(tab.query!!)
    }

    /** A channel page: who they are, plus their newest uploads. */
    suspend fun channelPage(url: String): ChannelData = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val info = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(service, url)
        val videosTab = info.tabs.firstOrNull { org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS in it.contentFilters }
        val videos = videosTab?.let {
            org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(service, it).relatedItems.videos()
        }.orEmpty()
        ChannelData(
            url = info.url ?: url,
            name = info.name.orEmpty(),
            avatar = info.avatars.maxByOrNull { it.height }?.url,
            subscribers = info.subscriberCount,
            videos = videos.map { v -> if (v.uploaderName.isBlank()) v.copy(uploaderName = info.name.orEmpty(), uploaderUrl = url) else v },
        )
    }

    /** A channel's newest uploads. */
    suspend fun channel(url: String): List<Video> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val info = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(service, url)
        val videosTab = info.tabs.firstOrNull { org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS in it.contentFilters }
            ?: return@withContext emptyList()
        org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(service, videosTab).relatedItems.videos()
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
