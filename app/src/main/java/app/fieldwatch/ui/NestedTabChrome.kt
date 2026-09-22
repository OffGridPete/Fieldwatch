package app.fieldwatch.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Inner tab screens sit under the FIELDWATCH header; do not pad again for the status bar. */
val NestedTabInsets: WindowInsets = WindowInsets(0, 0, 0, 0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NestedTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight = 40.dp,
        windowInsets = NestedTabInsets,
    )
}
