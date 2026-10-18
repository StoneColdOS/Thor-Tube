package io.github.stonecoldos.thortube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Asks GitHub, at most every 6 hours, whether a newer Thor-Tube release is out.
 *
 * One plain request to the public releases page of this repo: no sign-in, no account details,
 * nothing about the device. It only reads the newest version number and its page link; nothing is
 * downloaded or installed. Release builds only. If anything goes wrong it quietly does nothing.
 */
object UpdateCheck {

    private const val URL = "https://api.github.com/repos/StoneColdOS/Thor-Tube/releases/latest"
    private const val EVERY_MS = 6 * 60 * 60 * 1000L

    data class Update(val version: String, val url: String)

    /** Release builds only; test builds never check. */
    val enabled get() = !BuildConfig.DEBUG && "dev" !in BuildConfig.VERSION_NAME

    /**
     * The newest release if it's newer than this app, else null. Uses the saved answer if it's recent,
     * unless [force] is true (Setup > Check now), which always asks and also shows a version you said "Later" to.
     */
    suspend fun check(context: Context, force: Boolean = false): Result<Update?> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        runCatching {
            var tag = prefs.getString("tag", null)
            var url = prefs.getString("url", null)
            if (force || tag == null || now - prefs.getLong("at", 0) > EVERY_MS) {
                val request = Request.Builder().url(URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Thor-Tube")
                    .build()
                ThorTubeApp.http.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    val json = JSONObject(r.body!!.string())
                    tag = json.getString("tag_name")
                    url = json.getString("html_url")
                }
                prefs.edit().putString("tag", tag).putString("url", url).putLong("at", now).apply()
            }
            val latest = tag!!.removePrefix("v")
            when {
                !newer(latest, BuildConfig.VERSION_NAME) -> null
                !force && prefs.getString("later", null) == latest -> null
                // Only ever send people to this repo's own release pages.
                !url!!.startsWith("https://github.com/StoneColdOS/Thor-Tube/") -> null
                else -> Update(latest, url!!)
            }
        }.onFailure { AppLog.d("ThorTube", "Update check skipped: ${it.javaClass.simpleName}") }
    }

    /** "Later": don't mention this version again. */
    fun later(context: Context, version: String) {
        context.getSharedPreferences("update", Context.MODE_PRIVATE).edit().putString("later", version).apply()
    }

    /** True if version [a] is higher than [b] (numbers compared part by part; anything after a dash ignored). */
    internal fun newer(a: String, b: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
