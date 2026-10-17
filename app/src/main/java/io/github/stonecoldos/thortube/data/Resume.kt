package io.github.stonecoldos.thortube.data

import android.content.Context
import org.json.JSONObject

/**
 * Where you stopped in longer videos, kept on the Thor only, so they pick up where you left off.
 * Holds the most recent 300 videos; finished ones are forgotten.
 */
object Resume {

    /** Only videos at least this long are remembered. */
    private const val MIN_LENGTH_MS = 3 * 60_000L
    private const val MAX = 300

    private class Spot(val atMs: Long, val lengthMs: Long)

    private val spots = LinkedHashMap<String, Spot>(64, 0.75f, true)
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)
        runCatching {
            val o = JSONObject(prefs?.getString("spots", null) ?: return)
            o.keys().forEach { id ->
                val s = o.getJSONArray(id)
                spots[id] = Spot(s.getLong(0), s.getLong(1))
            }
        }
    }

    /** The spot to start from, or 0. */
    fun startAt(id: String): Long = spots[id]?.atMs ?: 0L

    /** How far through a video you got (0..1), or 0 if not started. For the bar on video cards. */
    fun progress(id: String): Float = spots[id]?.let { (it.atMs.toFloat() / it.lengthMs).coerceIn(0f, 1f) } ?: 0f

    fun remember(id: String, atMs: Long, lengthMs: Long) {
        if (id.isEmpty() || lengthMs < MIN_LENGTH_MS) return
        // The first half minute isn't worth resuming; the last bit means you're done.
        if (atMs < 30_000 || atMs > lengthMs - 30_000) spots.remove(id)
        else spots[id] = Spot(atMs, lengthMs)
        while (spots.size > MAX) spots.remove(spots.keys.first())
        save()
    }

    fun finished(id: String) {
        if (spots.remove(id) != null) save()
    }

    private fun save() {
        val o = JSONObject()
        spots.forEach { (id, s) -> o.put(id, org.json.JSONArray().put(s.atMs).put(s.lengthMs)) }
        prefs?.edit()?.putString("spots", o.toString())?.apply()
    }
}
