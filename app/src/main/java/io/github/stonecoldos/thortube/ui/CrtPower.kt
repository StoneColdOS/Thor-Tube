package io.github.stonecoldos.thortube.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The old CRT power effect. [power] 1 = picture fully on; going down it squeezes into a bright
 * white line, then shrinks to a dot at 0. Played backwards it's the switch-on.
 */
fun Modifier.crtPower(power: Float): Modifier {
    if (power >= 1f) return this
    val p = power.coerceIn(0f, 1f)
    val line = 0.006f
    return this
        .graphicsLayer {
            if (p < 0.3f) {
                scaleX = (p / 0.3f).coerceAtLeast(0.02f)
                scaleY = line
            } else {
                val open = (p - 0.3f) / 0.7f
                scaleX = 1f
                scaleY = line + (1f - line) * open
            }
        }
        .drawWithContent {
            drawContent()
            // Washed white while squeezed, fading back to the real picture as it opens.
            drawRect(Color.White, alpha = ((1f - p) * 1.4f).coerceIn(0f, 1f))
        }
}
