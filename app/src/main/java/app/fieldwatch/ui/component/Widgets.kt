package app.fieldwatch.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.RssiSample
import app.fieldwatch.domain.RssiTrend
import app.fieldwatch.domain.Sighting
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf
import kotlin.math.abs

@Composable
fun RssiBar(rssi: Int, color: Color, modifier: Modifier = Modifier) {
    val fraction = ((rssi + 100).coerceIn(0, 70) / 70f)
    val animated by animateFloatAsState(fraction, tween(350), label = "rssi")
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        val minFill = if (animated > 0f) size.height else 0f
        val fillW = (size.width * animated).coerceAtLeast(minFill).coerceAtMost(size.width)
        if (fillW > 0f) {
            drawRoundRect(
                color = color,
                size = Size(fillW, size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
fun Sparkline(
    samples: List<RssiSample>,
    color: Color,
    modifier: Modifier = Modifier.height(56.dp),
) {
    val major = MaterialTheme.colorScheme.outline.copy(alpha = 0.94f)
    val minor = MaterialTheme.colorScheme.outline.copy(alpha = 0.64f)
    val axis = MaterialTheme.colorScheme.outline.copy(alpha = 0.90f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = labelColor,
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
    )
    Canvas(modifier.fillMaxWidth()) {
        val ticks = listOf(-30, -40, -50, -60, -70, -80, -90, -100)
        val labeled = setOf(-30, -50, -70, -100)
        val gutter = measurer.measure("-100", labelStyle).size.width + 6f
        val left = gutter
        val right = size.width
        val plotW = (right - left).coerceAtLeast(1f)
        fun yOf(rssi: Int): Float {
            val t = ((rssi + 100).coerceIn(0, 70) / 70f)
            return size.height * (1f - t)
        }
        fun xOf(index: Int, last: Int): Float {
            if (last <= 0) return right
            return left + plotW * index / last.toFloat()
        }
        labeled.forEach { dbm ->
            val layout = measurer.measure("$dbm", labelStyle)
            val maxY = (size.height - layout.size.height).coerceAtLeast(0f)
            val y = (yOf(dbm) - layout.size.height / 2f).coerceIn(0f, maxY)
            drawText(layout, topLeft = Offset(0f, y))
        }
        ticks.forEach { dbm ->
            val y = yOf(dbm)
            val isLabeled = dbm in labeled
            val startX = if (isLabeled) {
                measurer.measure("$dbm", labelStyle).size.width + 5f
            } else {
                left
            }
            drawLine(
                color = if (isLabeled) major else minor,
                start = Offset(startX, y),
                end = Offset(right, y),
                strokeWidth = if (isLabeled) 1.95f else 1.3f,
                pathEffect = if (isLabeled) {
                    null
                } else {
                    PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                },
            )
        }
        drawLine(
            color = axis,
            start = Offset(left, yOf(-30)),
            end = Offset(left, yOf(-100)),
            strokeWidth = 1.85f,
        )
        val cols = 4
        for (c in 1 until cols) {
            val x = left + plotW * c / cols.toFloat()
            drawLine(
                color = minor,
                start = Offset(x, yOf(-30)),
                end = Offset(x, yOf(-100)),
                strokeWidth = 1.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f),
            )
        }
        if (samples.isEmpty()) return@Canvas
        if (samples.size == 1) {
            drawCircle(color, radius = 5f, center = Offset(right, yOf(samples[0].rssi)))
            return@Canvas
        }
        val path = Path()
        val lastIndex = samples.lastIndex
        samples.forEachIndexed { i, sample ->
            val x = xOf(i, lastIndex)
            val y = yOf(sample.rssi)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3.2f, cap = StrokeCap.Round))
        drawCircle(color, radius = 4.6f, center = Offset(right, yOf(samples.last().rssi)))
    }
}

@Composable
fun PresenceTrack(
    device: Sighting,
    now: Long,
    windowMs: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.height(18.dp).fillMaxWidth()) {
        val start = now - windowMs
        drawRect(color.copy(alpha = 0.08f))
        val last = device.presence.lastIndex
        device.presence.forEachIndexed { i, span ->
            val open = span.end == null || (!device.gone && i == last)
            val a = span.start.coerceAtLeast(start)
            val b = (if (open) now else span.end ?: now).coerceAtMost(now)
            if (b <= a) return@forEachIndexed
            val x1 = ((a - start).toFloat() / windowMs) * size.width
            val x2 = ((b - start).toFloat() / windowMs) * size.width
            drawRect(
                color = color.copy(alpha = 0.85f),
                topLeft = Offset(x1, size.height * 0.25f),
                size = androidx.compose.ui.geometry.Size(abs(x2 - x1).coerceAtLeast(2f), size.height * 0.5f),
            )
        }
    }
}

fun rssiColor(rssi: Int): Color = when {
    rssi >= -55 -> Color(0xFF3DFF9A)
    rssi >= -70 -> Color(0xFFFFB020)
    rssi >= -85 -> Color(0xFFFF8A4C)
    else -> Color(0xFFFF3D5A)
}

fun RssiTrend.mark(): String = when (this) {
    RssiTrend.UP_FAST -> ">>"
    RssiTrend.UP -> ">"
    RssiTrend.FLAT -> "="
    RssiTrend.DOWN -> "<"
    RssiTrend.DOWN_FAST -> "<<"
    RssiTrend.UNKNOWN -> ""
}

fun RssiTrend.tint(): Color = when (this) {
    RssiTrend.UP_FAST, RssiTrend.UP -> Color(0xFF3DFF9A)
    RssiTrend.DOWN, RssiTrend.DOWN_FAST -> Color(0xFFFF3D5A)
    RssiTrend.FLAT -> Color(0xFF8A93A0)
    RssiTrend.UNKNOWN -> Color.Transparent
}

@Composable
fun TrendMark(trend: RssiTrend, modifier: Modifier = Modifier) {
    val label = trend.mark()
    if (label.isEmpty()) return
    Text(
        label,
        modifier = modifier,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = trend.tint().nightIf(LocalNightMode.current),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )
}
