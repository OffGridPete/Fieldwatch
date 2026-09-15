package app.fieldwatch.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

val LocalNightMode = staticCompositionLocalOf { false }

/**
 * Map any sRGB color to a red luminance ramp (cockpit / field night display).
 * Relative brightness is kept so class chips stay distinguishable.
 */
fun nightForegroundArgb(argb: Int): Int {
    val a = (argb ushr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val y = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    val nr = (0.40f + 0.60f * y).coerceIn(0f, 1f)
    val ng = (0.05f + 0.16f * y).coerceIn(0f, 1f)
    val nb = (0.05f + 0.10f * y).coerceIn(0f, 1f)
    return (a shl 24) or
        (((nr * 255f).toInt() and 0xFF) shl 16) or
        (((ng * 255f).toInt() and 0xFF) shl 8) or
        ((nb * 255f).toInt() and 0xFF)
}

fun Color.asNightForeground(): Color = Color(nightForegroundArgb(toArgb()))

/** Identity when [night] is false — day/dark colors are unchanged. */
fun Color.nightIf(night: Boolean): Color = if (night) asNightForeground() else this
