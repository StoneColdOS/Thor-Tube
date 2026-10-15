package io.github.stonecoldos.thortube

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Logs to logcat AND to a file on the Thor, so testing doesn't need adb:
 *   Download/Thor-Tube-logs/thor.log           this run
 *   Download/Thor-Tube-logs/thor-previous.log  the run before
 *   Download/Thor-Tube-logs/thor-crash.txt     last crash, if any
 * Line one of every log says which build and device it came from.
 *
 * If Download isn't writable (e.g. files left over from an older install that Android
 * no longer lets us touch), it falls back to Android/data/<package>/files/logs.
 */
object AppLog {

    private val writer = Executors.newSingleThreadExecutor()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    @Volatile private var logFile: File? = null
    @Volatile private var crashFile: File? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var lastScan = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
        val dir = pickDir(context)
        val current = File(dir, "thor.log")
        val previous = File(dir, "thor-previous.log")
        if (current.exists()) {
            previous.delete()
            if (!current.renameTo(previous)) current.delete()
        }
        logFile = current
        crashFile = File(dir, "thor-crash.txt")

        val pm = context.packageManager.getPackageInfo(context.packageName, 0)
        val soc = "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
        append(
            "Thor-Tube ${pm.versionName} (${pm.longVersionCode}) · ${Build.MANUFACTURER} ${Build.MODEL} · " +
                "$soc · Android ${Build.VERSION.RELEASE} · started ${Date()}\n" +
                "Log folder: ${dir.absolutePath}\n",
        )

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val trace = stackTrace(error)
            runCatching {
                crashFile?.writeText("Crash in ${thread.name} at ${Date()}\n${pm.versionName}\n$trace")
                crashFile?.let { android.media.MediaScannerConnection.scanFile(context, arrayOf(it.absolutePath), null, null) }
                logFile?.appendText("${now()} CRASH ${thread.name}\n$trace")
            }
            previousHandler?.uncaughtException(thread, error)
        }
    }

    fun d(tag: String, msg: String) { Log.d(tag, msg); line("D", msg) }
    fun i(tag: String, msg: String) { Log.i(tag, msg); line("I", msg) }
    fun w(tag: String, msg: String, tr: Throwable? = null) { Log.w(tag, msg, tr); line("W", msg, tr) }
    fun e(tag: String, msg: String, tr: Throwable? = null) { Log.e(tag, msg, tr); line("E", msg, tr) }

    private fun line(level: String, msg: String, tr: Throwable? = null) {
        val text = buildString {
            append(now()).append(' ').append(level).append(' ').append(msg).append('\n')
            if (tr != null) append(stackTrace(tr))
        }
        append(text)
    }

    private fun append(text: String) {
        val file = logFile ?: return
        writer.execute {
            runCatching { FileWriter(file, true).use { it.write(text) } }
            // Files written straight to Download don't show in the Files app until Android's
            // media index hears about them, so nudge it now and then.
            val now = System.currentTimeMillis()
            if (now - lastScan > 5_000) {
                lastScan = now
                appContext?.let { ctx ->
                    val paths = listOfNotNull(file, File(file.parentFile, "thor-previous.log"), crashFile)
                        .filter { it.exists() }.map { it.absolutePath }.toTypedArray()
                    runCatching { android.media.MediaScannerConnection.scanFile(ctx, paths, null, null) }
                }
            }
        }
    }

    private fun pickDir(context: Context): File {
        val shared = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Thor-Tube-logs",
        )
        if (isWritable(shared)) return shared
        val private = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
        private.mkdirs()
        return private
    }

    private fun isWritable(dir: File): Boolean = runCatching {
        dir.mkdirs()
        val probe = File(dir, ".probe")
        probe.writeText("ok")
        // A leftover thor.log we can't modify would also break things, so check it too.
        val log = File(dir, "thor.log")
        val ok = probe.delete() && (!log.exists() || log.canWrite())
        ok
    }.getOrDefault(false)

    private fun now() = synchronized(timeFormat) { timeFormat.format(Date()) }

    private fun stackTrace(tr: Throwable) =
        StringWriter().also { tr.printStackTrace(PrintWriter(it)) }.toString()
}
