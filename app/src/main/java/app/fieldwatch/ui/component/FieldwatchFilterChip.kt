package app.fieldwatch.ui.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf

/**
 * Toggle chip in action-button colors. Same pill shape as before.
 * Unselected: dark button fill + gray outline.
 * Selected: section-tile fill + phosphor outline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldwatchFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = spectreTileFill()
    val selectedFill = spectreSectionFill()
    val edge = spectreTileEdge()
    val active = PhosphorActive.nightIf(LocalNightMode.current)
    val labelColor = scheme.onSurface
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.heightIn(min = 32.dp),
        enabled = enabled,
        leadingIcon = leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = fill,
            selectedContainerColor = selectedFill,
            labelColor = labelColor,
            selectedLabelColor = labelColor,
            iconColor = labelColor,
            selectedLeadingIconColor = active,
            disabledContainerColor = fill.copy(alpha = 0.4f),
            disabledSelectedContainerColor = selectedFill.copy(alpha = 0.4f),
            disabledLabelColor = labelColor.copy(alpha = 0.38f),
            disabledLeadingIconColor = labelColor.copy(alpha = 0.38f),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = edge,
            selectedBorderColor = active,
            disabledBorderColor = edge.copy(alpha = 0.4f),
            disabledSelectedBorderColor = active.copy(alpha = 0.4f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}
