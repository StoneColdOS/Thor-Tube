package io.github.stonecoldos.thortube.data

import android.text.Html
import io.github.stonecoldos.thortube.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description

/** One comment, ready to show. */
data class Comment(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val ago: String,
    val likes: String,
    val replyCount: Int,
    val replies: Page?,
    val pinned: Boolean,
    val hearted: Boolean,
    val byCreator: Boolean,
    val verified: Boolean,
)

/** A page of comments and where the next one starts. */
data class CommentsPage(
    val items: List<Comment>,
    val next: Page?,
    val count: Int = -1,
    val disabled: Boolean = false,
)

/** YouTube comments for a video (YouTube's "Top comments" order), read-only. */
object Comments {

    private const val TAG = "ThorTube"

    suspend fun first(videoUrl: String): CommentsPage = withContext(Dispatchers.IO) {
        val info = CommentsInfo.getInfo(ServiceList.YouTube, videoUrl)
        CommentsPage(
            items = info.relatedItems.map(::toComment),
            next = info.nextPage?.takeIf { Page.isValid(it) },
            count = info.commentsCount,
            disabled = info.isCommentsDisabled,
        ).also { AppLog.d(TAG, "Comments: ${it.items.size} of ${it.count}") }
    }

    /** The next page of comments, or of one comment's replies (pass its [Comment.replies]). */
    suspend fun more(videoUrl: String, page: Page): CommentsPage = withContext(Dispatchers.IO) {
        val p = CommentsInfo.getMoreItems(ServiceList.YouTube, videoUrl, page)
        CommentsPage(p.items.map(::toComment), p.nextPage?.takeIf { Page.isValid(it) })
    }

    private fun toComment(c: CommentsInfoItem) = Comment(
        id = c.commentId.orEmpty(),
        author = c.uploaderName.orEmpty(),
        avatar = c.uploaderAvatars.maxByOrNull { it.height }?.url,
        text = plain(c.commentText),
        ago = c.uploadDate?.instant?.toEpochMilli()?.let { ago(it) } ?: c.textualUploadDate.orEmpty(),
        likes = c.textualLikeCount?.takeIf { it.isNotBlank() } ?: count(c.likeCount),
        replyCount = c.replyCount.coerceAtLeast(0),
        replies = c.replies?.takeIf { Page.isValid(it) },
        pinned = c.isPinned,
        hearted = c.isHeartedByUploader,
        byCreator = c.isChannelOwner,
        verified = c.isUploaderVerified,
    )

    private fun plain(d: Description?): String {
        val content = d?.content.orEmpty()
        return if (d?.type == Description.HTML) Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT).toString().trim() else content.trim()
    }

    private fun count(n: Int): String = when {
        n <= 0 -> ""
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
        n >= 1_000 -> "%.1fK".format(n / 1_000.0).replace(".0K", "K")
        else -> n.toString()
    }
}
