package io.github.stonecoldos.thortube.source

import android.content.Intent

/** A YouTube link handed to us by another app. */
sealed interface YouTubeLink {
    val url: String
    data class Video(override val url: String) : YouTubeLink
    data class Channel(override val url: String) : YouTubeLink
}

/** Pulls a YouTube video or channel link out of whatever another app handed us. */
object LinkParser {

    private val videoUrl = Regex(
        """https?://(?:www\.|m\.)?(?:youtube\.com/(?:watch|shorts/|live/)|youtu\.be/)\S+""",
        RegexOption.IGNORE_CASE,
    )

    private val channelUrl = Regex(
        """https?://(?:www\.|m\.)?youtube\.com/(?:@[\w.\-]+|channel/[\w\-]+|c/[\w.\-]+|user/[\w.\-]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun fromIntent(intent: Intent?): YouTubeLink? {
        intent ?: return null
        val raw = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return null
        videoUrl.find(raw)?.let { return YouTubeLink.Video(it.value) }
        channelUrl.find(raw)?.let { return YouTubeLink.Channel(it.value) }
        return null
    }
}
