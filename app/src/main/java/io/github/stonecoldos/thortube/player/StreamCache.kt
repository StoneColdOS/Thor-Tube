package io.github.stonecoldos.thortube.player

import io.github.stonecoldos.thortube.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Stream details fetched ahead of time, so flipping to a channel the TV already looked up
 * only waits for the picture, not for YouTube. Stream links stay good for hours; we only
 * trust them for [TTL_MS] to be safe, and drop one if playing it fails.
 */
object StreamCache {

    private const val TTL_MS = 30 * 60 * 1000L
    private const val MAX = 16

    private class Entry(val info: StreamInfo, val at: Long)

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun get(url: String): StreamInfo? {
        val e = entries[url] ?: return null
        if (System.currentTimeMillis() - e.at > TTL_MS) {
            entries.remove(url)
            return null
        }
        return e.info
    }

    @Synchronized
    fun has(url: String) = get(url) != null

    @Synchronized
    fun drop(url: String) {
        entries.remove(url)
    }

    @Synchronized
    private fun put(url: String, info: StreamInfo) {
        entries.remove(url)
        entries[url] = Entry(info, System.currentTimeMillis())
        while (entries.size > MAX) entries.remove(entries.keys.first())
    }

    /** Cached details, or fetch them now. */
    suspend fun load(url: String): StreamInfo =
        get(url) ?: withContext(Dispatchers.IO) { StreamInfo.getInfo(ServiceList.YouTube, url) }.also { put(url, it) }

    /** Look one up in the background; failures don't matter, playing will just fetch it again. */
    suspend fun warm(url: String) {
        if (has(url)) return
        runCatching { load(url) }
            .onSuccess { AppLog.d("ThorTube", "Ready ahead: ${it.name}") }
            .onFailure { AppLog.d("ThorTube", "Couldn't get $url ready: ${it.message}") }
    }
}
