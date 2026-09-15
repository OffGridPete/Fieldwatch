package app.fieldwatch.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pointy hexagon — hex bytes in a packet. Decode-map mark on Live / detail.
 */
@Composable
fun DecodeGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    contentDescription: String = "Decode fields",
) {
    Canvas(
        modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val r = this.size.minDimension / 2f - 0.8f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val hex = Path()
        for (i in 0..5) {
            val a = (-PI / 2.0 + i * PI / 3.0).toFloat()
            val x = c.x + r * cos(a)
            val y = c.y + r * sin(a)
            if (i == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
        }
        hex.close()
        drawPath(hex, tint, style = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(tint, radius = r * 0.22f, center = c)
    }
}
