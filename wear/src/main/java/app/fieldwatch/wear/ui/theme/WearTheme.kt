package app.fieldwatch.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val DarkGreen = Color(0xFF00E676)
val BrightCyan = Color(0xFF00E5FF)
val AlertRed = Color(0xFFFF1744)
val WarningAmber = Color(0xFFFFC400)
val DarkSurface = Color(0xFF121212)
val PureBlack = Color(0xFF000000)

val WearColorPalette = Colors(
    primary = DarkGreen,
    primaryVariant = Color(0xFF00B0FF),
    secondary = BrightCyan,
    background = PureBlack,
    surface = DarkSurface,
    error = AlertRed,
    onPrimary = PureBlack,
    onSecondary = PureBlack,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = PureBlack,
)

@Composable
fun FieldwatchWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColorPalette,
        content = content,
    )
}
