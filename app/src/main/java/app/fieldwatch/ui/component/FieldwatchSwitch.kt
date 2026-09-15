package app.fieldwatch.ui.component

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
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
