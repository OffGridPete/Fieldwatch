package app.fieldwatch.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Section-tile green (surface at 1.dp). */
@Composable
internal fun spectreSectionFill(): Color {
    return MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
}

/** Section-tile green, darkened. Shared by action buttons and switch tracks. */
@Composable
internal fun spectreTileFill(): Color {
    return lerp(Color.Black, spectreSectionFill(), 0.78f)
}

/** Gray outline shared by action buttons and switches. */
@Composable
internal fun spectreTileEdge(): Color {
    val scheme = MaterialTheme.colorScheme
    return lerp(scheme.outline, scheme.onSurfaceVariant, 0.32f)
}

/** Action button: section-tile green, darkened; gray outline, off-white label. */
@Composable
fun FieldwatchActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = spectreTileFill()
    val edge = spectreTileEdge()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = fill,
            contentColor = scheme.onSurface,
            disabledContainerColor = fill.copy(alpha = 0.4f),
            disabledContentColor = scheme.onSurface.copy(alpha = 0.38f),
        ),
        border = BorderStroke(1.dp, if (enabled) edge else edge.copy(alpha = 0.4f)),
        content = content,
    )
}
