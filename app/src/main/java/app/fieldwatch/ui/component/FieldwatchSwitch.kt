package app.fieldwatch.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf

@Composable
fun FieldwatchSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fill = spectreTileFill()
    val edge = spectreTileEdge()
    val active = PhosphorActive.nightIf(LocalNightMode.current)
    Box(
        modifier = modifier
            .requiredSize(width = 40.dp, height = 24.dp)
            .clip(RectangleShape),
        contentAlignment = Alignment.Center,
    ) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.scale(0.72f),
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = active,
            checkedBorderColor = edge,
            uncheckedTrackColor = fill,
            uncheckedBorderColor = edge,
            disabledCheckedTrackColor = active.copy(alpha = 0.38f),
            disabledCheckedBorderColor = edge.copy(alpha = 0.4f),
            disabledUncheckedTrackColor = fill.copy(alpha = 0.4f),
            disabledUncheckedBorderColor = edge.copy(alpha = 0.4f),
        ),
    )
    }
}
