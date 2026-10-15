package io.github.stonecoldos.thortube.launcher

import io.github.stonecoldos.thortube.AppLog
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.stonecoldos.thortube.source.LinkParser
import io.github.stonecoldos.thortube.source.YouTubeLink
import io.github.stonecoldos.thortube.ui.MainActivity

/**
 * Invisible-ish entry point. Its only job is to open the app on the bottom screen.
 * If it can't find one, it shows what displays it sees, so we can debug on the Thor.
 */
class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    private fun route(intent: Intent?) {
        val url = LinkParser.fromIntent(intent)
        val here = display?.displayId ?: Display.DEFAULT_DISPLAY
        val target = findBottomScreen()?.displayId
        AppLog.i("ThorTube", "Launcher on display $here, bottom screen ${target ?: "not found"}, link ${url ?: "none"}")

        when {
            // Already on a secondary screen: play right here.
            here != Display.DEFAULT_DISPLAY -> launchApp(here, url)
            target != null && launchApp(target, url) -> Unit
            else -> showNoBottomScreen(url, here)
        }
    }

    /** On the Thor the top screen is the default display, so "bottom" = the other built-in one. */
    private fun findBottomScreen(): Display? {
        val dm = getSystemService(DisplayManager::class.java)
        return dm.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY &&
                it.isValid &&
                (it.flags and Display.FLAG_PRIVATE) == 0
        }
    }

    private fun launchApp(displayId: Int, url: YouTubeLink?): Boolean {
        val app = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        when (url) {
            is YouTubeLink.Video -> app.putExtra(MainActivity.EXTRA_URL, url.url)
            is YouTubeLink.Channel -> app.putExtra(MainActivity.EXTRA_CHANNEL, url.url)
            null -> Unit
        }
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        return try {
            startActivity(app, options.toBundle())
            finish()
            true
        } catch (e: Exception) {
            AppLog.w("ThorTube", "Couldn't launch on display $displayId", e)
            false
        }
    }

    private fun showNoBottomScreen(url: YouTubeLink?, here: Int) {
        val dm = getSystemService(DisplayManager::class.java)
        val seen = dm.displays.joinToString("\n") {
            "#${it.displayId} ${it.name} flags=${it.flags} ${it.mode.physicalWidth}x${it.mode.physicalHeight}"
        }
        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Couldn't find the bottom screen.", color = Color.White)
                Text(
                    "Displays I can see:\n$seen",
                    color = Color(0xFF7FE0C2),
                    fontFamily = FontFamily.Monospace,
                )
                Button(onClick = { launchApp(here, url) }) {
                    Text("Play on this screen instead")
                }
            }
        }
    }
}
