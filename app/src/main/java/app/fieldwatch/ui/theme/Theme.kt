package app.fieldwatch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Phosphor = Color(0xFF3DFF9A)
/** Checked switch / slider fill — same hue as Phosphor, less neon. */
val PhosphorActive = Color(0xFF35D683)
val Amber = Color(0xFFFFB020)
val SignalRed = Color(0xFFFF3D5A)
val Cyan = Color(0xFF4FC3F7)
val Night = Color(0xFF0B0F14)
val Panel = Color(0xFF141A22)
val Panel2 = Color(0xFF1B232D)

private val DarkColors = darkColorScheme(
    primary = Phosphor,
    onPrimary = Color(0xFF003820),
    primaryContainer = Color(0xFF163326),
    onPrimaryContainer = Phosphor,
    secondary = Amber,
    onSecondary = Color(0xFF2A1A00),
    tertiary = Cyan,
    background = Night,
    onBackground = Color(0xFFD5DCE3),
    surface = Panel,
    onSurface = Color(0xFFD5DCE3),
    surfaceVariant = Panel2,
    onSurfaceVariant = Color(0xFF9AA6B2),
    outline = Color(0xFF2A3340),
    error = SignalRed,
)

/**
 * Red-on-black field display. Background stays dark; chrome and accents
 * are red ramps. Used only while Settings → Night mode is on.
 */
private val NightColors = darkColorScheme(
    primary = Color(0xFFFF5A5A),
    onPrimary = Color(0xFF2A0808),
    primaryContainer = Color(0xFF3A1212),
    onPrimaryContainer = Color(0xFFFF8A8A),
    secondary = Color(0xFFE07070),
    onSecondary = Color(0xFF2A0808),
    tertiary = Color(0xFFCC6666),
    background = Color(0xFF0B0808),
    onBackground = Color(0xFFFFC9C9),
    surface = Color(0xFF161010),
    onSurface = Color(0xFFFFC9C9),
    surfaceVariant = Color(0xFF1E1414),
    onSurfaceVariant = Color(0xFFC48A8A),
    outline = Color(0xFF5A3030),
    error = Color(0xFFFF7A7A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B7A48),
    onPrimary = Color.White,
    secondary = Color(0xFF9A6400),
    tertiary = Color(0xFF0277BD),
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF12171C),
    surface = Color.White,
    onSurface = Color(0xFF12171C),
    surfaceVariant = Color(0xFFE6EBEF),
    onSurfaceVariant = Color(0xFF3F4A55),
    outline = Color(0xFFC5CDD4),
    error = Color(0xFFB00020),
)

val Mono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 0.3.sp,
)

@Composable
fun FieldwatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    nightMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        nightMode -> NightColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalNightMode provides nightMode) {
        MaterialTheme(
            colorScheme = scheme,
            content = content,
        )
    }
}
