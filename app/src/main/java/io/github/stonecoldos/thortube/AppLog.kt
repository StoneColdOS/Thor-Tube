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
 * Logs to logcat AND to a file kept inside the app's private storage (no other app can read it):
 *   thor.log           this run
 *   thor-previous.log  the run before
 *   thor-crash.txt     last crash, if any
 * Setup > Share logs sends them on through Android's share sheet.
 * Line one of every log says which build and device it came from. Logs never hold video links,
 * titles, searches or account details.
 */
object AppLog {

    private val writer = Executors.newSingleThreadExecutor()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    @Volatile private var logFile: File? = null
    @Volatile private var crashFile: File? = null

    fun init(context: Context) {
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
        }
    }

    private fun pickDir(context: Context): File {
        // Earlier test builds wrote to the public Download folder; remove what's left there.
        runCatching {
            val old = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Thor-Tube-logs")
            if (old.isDirectory) {
                old.listFiles()?.forEach { it.delete() }
                old.delete()
            }
        }
        return File(context.filesDir, "logs").apply { mkdirs() }
    }

    /** The log files that exist right now, newest first, for sharing. */
    fun files(): List<File> = listOfNotNull(logFile, logFile?.parentFile?.let { File(it, "thor-previous.log") }, crashFile)
        .filter { it.exists() && it.length() > 0 }

    private fun now() = synchronized(timeFormat) { timeFormat.format(Date()) }

    private fun stackTrace(tr: Throwable) =
        StringWriter().also { tr.printStackTrace(PrintWriter(it)) }.toString()
}
