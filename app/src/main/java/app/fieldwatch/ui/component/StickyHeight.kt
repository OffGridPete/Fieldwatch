package app.fieldwatch.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun StickyHeight(
    latchKey: Any,
    content: @Composable ColumnScope.() -> Unit,
) {
    var minPx by remember(latchKey) { mutableIntStateOf(0) }
    val minDp = with(LocalDensity.current) { minPx.toDp() }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minDp)
            .onSizeChanged { size ->
                if (size.height > minPx) minPx = size.height
            },
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Caption that keeps the tallest of [text] and [variants] so toggles do not shove the page. */
@Composable
fun StableCaption(
    text: String,
    vararg variants: String,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.fillMaxWidth()) {
        (variants.toList() + text).distinct().forEach { sample ->
            Text(
                sample,
                style = style,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(text, style = style, color = color, modifier = Modifier.fillMaxWidth())
    }
}
