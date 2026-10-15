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

    /** "Auto": plenty for the Thor's screen, and starts fast. */
    const val AUTO_HEIGHT = 720
    /** The bottom screen is 1080 px tall, so anything above that is wasted bandwidth. */
    private const val TOP_HEIGHT = 1080

    /** The picture sizes this video offers that the Thor can use, biggest first. */
    fun heights(info: StreamInfo): List<Int> = info.videoOnlyStreams
        .filter { it.isPlayableUrl() && it.height in 1..TOP_HEIGHT }
        .map { it.height }
        .distinct()
        .sortedDescending()

    /** [maxHeight] = the tallest picture wanted (0 = Auto). */
    fun plan(info: StreamInfo, maxHeight: Int = 0): List<PlaybackAttempt> {
        val cap = if (maxHeight <= 0) AUTO_HEIGHT else maxHeight.coerceAtMost(TOP_HEIGHT)
        val attempts = mutableListOf<PlaybackAttempt>()

        val hls = info.hlsUrl?.takeIf { it.isNotBlank() }?.let { PlaybackAttempt.Hls(it) }
        val isLive = info.streamType == StreamType.LIVE_STREAM ||
            info.streamType == StreamType.AUDIO_LIVE_STREAM
        if (isLive && hls != null) attempts += hls

        val video = info.videoOnlyStreams
            .filter { it.isPlayableUrl() && it.height in 1..cap }
            .ifEmpty { info.videoOnlyStreams.filter { it.isPlayableUrl() && it.height > 0 } }
            .let { list -> if (list.any { it.height <= cap }) list.filter { it.height <= cap } else listOfNotNull(list.minByOrNull { it.height }) }
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
            .let { list -> list.filter { it.height <= cap }.maxByOrNull { it.height } ?: list.minByOrNull { it.height } }
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
