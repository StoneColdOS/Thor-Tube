package io.github.stonecoldos.thortube.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.stonecoldos.thortube.AppLog
import io.github.stonecoldos.thortube.ThorTubeApp
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request

/**
 * Video thumbnails for the guide, kept in memory. The guide asks for its pictures ahead of time
 * ([prefetch]) so moving around never waits on a download, and each picture is only ever
 * downloaded once even if several places ask for it at the same time.
 */
object Thumbnails {

    /** 320x180 thumbnails in RGB_565 are about 115 KB each, so this is roughly 35 MB at most. */
    private val cache = LruCache<String, ImageBitmap>(300)
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(8)

    fun cached(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? = cache.get(url) ?: fetch(url).await()

    /** Starts downloading these in order (most important first), skipping ones already here. */
    fun prefetch(urls: List<String>) {
        urls.asSequence().filter { it.isNotBlank() && cache.get(it) == null }.distinct().forEach { fetch(it) }
    }

    private fun fetch(url: String): Deferred<ImageBitmap?> = inFlight.computeIfAbsent(url) {
        scope.async {
            try {
                gate.withPermit { cache.get(url) ?: download(url)?.also { cache.put(url, it) } }
            } finally {
                inFlight.remove(url)
            }
        }
    }

    private fun download(url: String): ImageBitmap? = runCatching {
        ThorTubeApp.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                BitmapFactory.decodeStream(response.body!!.byteStream(), null, options)?.asImageBitmap()
            }
        }
    }.onFailure { AppLog.d("ThorTube", "Thumbnail failed: $url ${it.message}") }.getOrNull()
}

/** A video's thumbnail. Fades in when it arrives; already-loaded ones show at once. */
@Composable
fun Thumb(url: String?, modifier: Modifier, dim: Boolean = false) {
    // Keyed on the URL so a reused cell drops the old picture the moment its video changes.
    val cached = remember(url) { url?.let { Thumbnails.cached(it) } }
    var image by remember(url) { mutableStateOf(cached) }
    val alpha = remember(url) { Animatable(if (cached != null) 1f else 0f) }
    LaunchedEffect(url) {
        if (image == null && !url.isNullOrBlank()) {
            image = Thumbnails.load(url)
            if (image != null) alpha.animateTo(1f, tween(220))
        }
    }
    Box(modifier.background(LocalPalette.current.card)) {
        image?.let {
            Image(
                it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = alpha.value * (if (dim) 0.45f else 1f),
            )
        }
    }
}
