package io.github.stonecoldos.thortube.player

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** One way of playing a video. If it fails, the TV moves on to the next one. */
sealed interface PlaybackAttempt {
    val label: String

    data class Split(val video: VideoStream, val audio: AudioStream) : PlaybackAttempt {
        override val label get() = "split ${video.height}p ${video.codec ?: "?"} + ${audio.format?.name ?: "audio"} ${audio.audioTrackType ?: ""} ${audio.audioLocale ?: ""}".trim()
    }

    data class Muxed(val stream: VideoStream) : PlaybackAttempt {
        override val label get() = "muxed ${stream.height}p"
    }

    data class Hls(val url: String) : PlaybackAttempt {
        override val label get() = "HLS"
    }
}

object PlaybackPlanner {

    /** Plenty for a ~4 inch screen, and keeps channel changes fast. */
    private const val MAX_HEIGHT = 720

    fun plan(info: StreamInfo): List<PlaybackAttempt> {
        val attempts = mutableListOf<PlaybackAttempt>()

        val hls = info.hlsUrl?.takeIf { it.isNotBlank() }?.let { PlaybackAttempt.Hls(it) }
        val isLive = info.streamType == StreamType.LIVE_STREAM ||
            info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (isLive && hls != null) attempts += hls

        val video = info.videoOnlyStreams
            .filter { it.isPlayableUrl() && it.height in 1..MAX_HEIGHT }
            .maxWithOrNull(
                compareBy<VideoStream> { it.height }
                    // Same height: AVC is the safest bet for hardware decoding
                    .thenBy { if (it.codec?.startsWith("avc") == true) 1 else 0 },
            )
        val audio = info.audioStreams
            .filter { it.isPlayableUrl() }
            .maxWithOrNull(
                // YouTube adds AI-dubbed tracks to many videos. Always prefer the creator's own voice,
                // then a track in the device's language, then M4A, then quality.
                compareBy<AudioStream> { audioRank(it) }
                    .thenBy { if (it.format == MediaFormat.M4A) 1 else 0 }
                    .thenBy { it.averageBitrate },
            )
        if (video != null && audio != null) attempts += PlaybackAttempt.Split(video, audio)

        info.videoStreams
            .filter { it.isPlayableUrl() }
            .maxByOrNull { it.height }
            ?.let { attempts += PlaybackAttempt.Muxed(it) }

        if (!isLive && hls != null) attempts += hls
        return attempts
    }

    private fun audioRank(a: AudioStream): Int = when (a.audioTrackType) {
        AudioTrackType.ORIGINAL -> 3
        null -> if (a.audioLocale?.language == java.util.Locale.getDefault().language) 2 else 1
        AudioTrackType.DUBBED, AudioTrackType.SECONDARY -> 0
        AudioTrackType.DESCRIPTIVE -> -1
    }

    private fun org.schabi.newpipe.extractor.stream.Stream.isPlayableUrl() =
        isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
}
