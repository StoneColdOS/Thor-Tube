package io.github.stonecoldos.thortube.player

import io.github.stonecoldos.thortube.AppLog
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import io.github.stonecoldos.thortube.source.ExtractorDownloader
import java.io.ByteArrayInputStream
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.Stream

/** Turns a [PlaybackAttempt] into something ExoPlayer can play. */
@OptIn(UnstableApi::class)
class MediaSources(private val client: OkHttpClient) {

    fun build(attempt: PlaybackAttempt, durationSecs: Long): MediaSource = when (attempt) {
        is PlaybackAttempt.Split -> MergingMediaSource(
            dashOrProgressive(attempt.video, durationSecs),
            dashOrProgressive(attempt.audio, durationSecs),
        )
        is PlaybackAttempt.Muxed -> progressive(attempt.stream.content)
        is PlaybackAttempt.Hls -> HlsMediaSource.Factory(http(attempt.url))
            .createMediaSource(MediaItem.fromUri(attempt.url))
    }

    /**
     * YouTube's video-only and audio-only files carry a seek index, but ExoPlayer's progressive
     * loader can't use it well, so seeking stalls. Wrapping each one in a tiny DASH manifest
     * (the same trick NewPipe uses) gives ExoPlayer the index and makes seeking quick.
     */
    private fun dashOrProgressive(stream: Stream, durationSecs: Long): MediaSource {
        val url = stream.content
        val itag = when (stream) {
            is org.schabi.newpipe.extractor.stream.VideoStream -> stream.itagItem
            is org.schabi.newpipe.extractor.stream.AudioStream -> stream.itagItem
            else -> null
        }
        if (itag != null) {
            try {
                val manifestXml = YoutubeProgressiveDashManifestCreator
                    .fromProgressiveStreamingUrl(url, itag, durationSecs)
                val manifest = DashManifestParser().parse(
                    Uri.parse(url),
                    ByteArrayInputStream(manifestXml.toByteArray()),
                )
                return DashMediaSource.Factory(http(url))
                    .createMediaSource(manifest, MediaItem.fromUri(url))
            } catch (e: Exception) {
                AppLog.w(TAG, "DASH wrap failed for itag ${itag.id}, using progressive", e)
            }
        }
        return progressive(url)
    }

    private fun progressive(url: String): MediaSource =
        ProgressiveMediaSource.Factory(http(url))
            .createMediaSource(MediaItem.fromUri(url))

    /** YouTube checks that the user agent matches the client the stream URL was issued to. */
    private fun http(url: String): DataSource.Factory {
        val userAgent = when {
            YoutubeParsingHelper.isAndroidStreamingUrl(url) ->
                YoutubeParsingHelper.getAndroidUserAgent(null)
            YoutubeParsingHelper.isIosStreamingUrl(url) ->
                YoutubeParsingHelper.getIosUserAgent(null)
            else -> ExtractorDownloader.USER_AGENT
        }
        return OkHttpDataSource.Factory(client).setUserAgent(userAgent)
    }

    private companion object {
        const val TAG = "ThorTube"
    }
}
