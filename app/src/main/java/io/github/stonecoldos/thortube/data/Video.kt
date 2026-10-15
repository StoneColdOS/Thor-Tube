package io.github.stonecoldos.thortube.data

/** One YouTube video, as the lists and grids show it. */
data class Video(
    val url: String,
    val title: String,
    val durationSecs: Long,
    /** "3 days ago", as YouTube said it. */
    val uploaded: String,
    /** Upload time in epoch ms, 0 if unknown. */
    val uploadedAt: Long = 0,
    val uploaderName: String = "",
    val uploaderUrl: String = "",
) {
    val id: String by lazy { ID.find(url)?.groupValues?.get(1).orEmpty() }

    /** YouTube's 320x180 thumbnail, worked out from the video id. */
    val thumbnail: String by lazy { if (id.isEmpty()) "" else "https://i.ytimg.com/vi/$id/mqdefault.jpg" }

    /** Bigger one for the player screen while the video loads. */
    val bigThumbnail: String by lazy { if (id.isEmpty()) "" else "https://i.ytimg.com/vi/$id/hqdefault.jpg" }

    private companion object {
        val ID = Regex("(?:[?&]v=|youtu\\.be/|/shorts/|/live/)([A-Za-z0-9_-]{11})")
    }
}

/** "34:12" or "1:02:11". */
fun formatDuration(secs: Long): String {
    if (secs <= 0) return ""
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "3 days ago", the way YouTube says it. */
fun ago(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val mins = (System.currentTimeMillis() - epochMs) / 60_000
    fun plural(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
    return when {
        mins < 1 -> "just now"
        mins < 60 -> plural(mins, "minute")
        mins < 60 * 24 -> plural(mins / 60, "hour")
        mins < 60 * 24 * 30 -> plural(mins / (60 * 24), "day")
        mins < 60 * 24 * 365 -> plural(mins / (60 * 24 * 30), "month")
        else -> plural(mins / (60 * 24 * 365), "year")
    }
}
