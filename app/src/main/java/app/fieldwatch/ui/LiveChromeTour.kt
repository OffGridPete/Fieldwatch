package app.fieldwatch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class LiveTourTargets(
    val tune: Rect? = null,
    val pause: Rect? = null,
    val filters: Rect? = null,
    val signatures: Rect? = null,
    val reports: Rect? = null,
    val settings: Rect? = null,
) {
    val ready: Boolean get() = tune != null && pause != null && filters != null &&
        signatures != null && reports != null && settings != null
}

private data class Spot(
    val target: Rect,
    val title: String,
    val body: String,
    val box: Rect,
    val from: Offset,
)

@Composable
fun LiveChromeTour(
    targets: LiveTourTargets,
    onDismiss: () -> Unit,
) {
    if (!targets.ready) return
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val holePad = with(density) { 5.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val spots = layoutSpots(targets, density, screenW, screenH)
        val measuredH = remember { mutableStateMapOf<String, Float>() }
        val pad = with(density) { 10.dp.toPx() }
        val fitted = spots.map { spot ->
            val h = measuredH[spot.title] ?: return@map spot
            val box = Rect(spot.box.left, spot.box.top, spot.box.right, spot.box.top + h)
            val fromY = if (spot.title == "Tune") box.top else box.bottom
            spot.copy(box = box, from = Offset(spot.from.x, fromY))
        }
        val placed = separateBubbles(fitted, pad, with(density) { 8.dp.toPx() })
        val holes = placed.map { it.target.inflate(holePad) }
        val tuneBottom = placed.first().box.bottom
        val tabsTop = placed.drop(1).minOf { it.box.top }
        val gotItY = (tuneBottom + tabsTop) / 2f - with(density) { 20.dp.toPx() }

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(Color.Black.copy(alpha = 0.55f))
            holes.forEach { hole ->
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = hole.topLeft,
                    size = Size(hole.width, hole.height),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    blendMode = BlendMode.Clear,
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val stroke = with(density) { 2.dp.toPx() }
            placed.forEach { spot ->
                arrow(spot.from, closestEdge(spot.target, spot.from), primary, stroke)
            }
        }
        placed.forEach { spot ->
            Callout(
                title = spot.title,
                body = spot.body,
                border = primary,
                modifier = Modifier
                    .offset { IntOffset(spot.box.left.roundToInt(), spot.box.top.roundToInt()) }
                    .width(with(density) { spot.box.width.toDp() })
                    .onSizeChanged { measuredH[spot.title] = it.height.toFloat() },
            )
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, gotItY.roundToInt()) },
        ) { Text("Got it") }
    }
}

@Composable
private fun Callout(
    title: String,
    body: String,
    border: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.5.dp, border),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = border,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun layoutSpots(
    targets: LiveTourTargets,
    density: Density,
    screenW: Float,
    screenH: Float,
): List<Spot> {
    val edge = with(density) { 10.dp.toPx() }
    val gap = with(density) { 12.dp.toPx() }
    val pad = with(density) { 8.dp.toPx() }
    val bodyH = with(density) { 72.dp.toPx() }
    val topH = with(density) { 80.dp.toPx() }
    val wide = with(density) { 152.dp.toPx() }
    val mid = with(density) { 148.dp.toPx() }

    val tune = targets.tune!!
    val tabs = listOf(
        Triple(targets.pause!!, "Pause", "Freeze the picture. Tap Live again to run."),
        Triple(targets.filters!!, "Filters", "Who is shown."),
        Triple(targets.signatures!!, "Signatures", "Pattern catalog."),
        Triple(targets.reports!!, "Reports", "Debrief, sits, log."),
        Triple(targets.settings!!, "Settings", "Scan, GPS, TAK."),
    )
    val heights = floatArrayOf(topH, bodyH, bodyH, bodyH, topH)
    val tabTop = tabs.minOf { it.first.top }
    val yLow = tabTop - gap - bodyH
    val yMid = yLow - gap - bodyH
    val yHigh = yMid - gap - topH - with(density) { 16.dp.toPx() }
    val rows = floatArrayOf(yHigh, yMid, yLow)
    // Pause + Settings high, Signatures mid, Filters + Reports low — arrows do not cross.
    val rowOf = intArrayOf(0, 2, 1, 2, 0)

    fun boxFor(target: Rect, width: Float, y: Float, height: Float): Rect {
        var x = target.center.x - width / 2f
        x = x.coerceIn(edge, (screenW - width - edge).coerceAtLeast(edge))
        val yy = y.coerceIn(edge, (screenH - height - edge).coerceAtLeast(edge))
        return Rect(x, yy, x + width, yy + height)
    }

    val widths = floatArrayOf(wide, mid, mid, mid, mid)
    val boxes = Array(5) { i ->
        boxFor(tabs[i].first, widths[i], rows[rowOf[i]], heights[i])
    }

    // Keep each bubble off every other tab's center so arrows stay clear.
    val centers = tabs.map { it.first.center.x }
    for (n in 0 until 10) {
        var moved = false
        for (i in boxes.indices) {
            for (j in centers.indices) {
                if (i == j) continue
                val b = boxes[i]
                if (centers[j] in (b.left - pad)..(b.right + pad)) {
                    val shift = if (b.center.x <= centers[j]) {
                        -(b.right - centers[j] + pad)
                    } else {
                        (centers[j] - b.left + pad)
                    }
                    val x = (b.left + shift).coerceIn(edge, (screenW - b.width - edge).coerceAtLeast(edge))
                    boxes[i] = Rect(x, b.top, x + b.width, b.bottom)
                    moved = true
                }
            }
        }
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                if (!overlap(boxes[i], boxes[j], pad)) continue
                val a = boxes[i]
                val b = boxes[j]
                val (upper, ui) = if (a.top <= b.top) a to i else b to j
                val newTop = (upper.top - (overlapHeight(a, b) + pad)).coerceAtLeast(edge)
                boxes[ui] = Rect(upper.left, newTop, upper.right, newTop + upper.height)
                moved = true
            }
        }
        if (!moved) break
    }

    val minGap = with(density) { 8.dp.toPx() }
    val sigArrowX = tabs[2].first.center.x
    val filterGap = (sigArrowX - boxes[1].right).coerceAtLeast(minGap)
    val reports = boxes[3]
    var reportsLeft = (sigArrowX + filterGap)
        .coerceAtLeast(sigArrowX + minGap)
        .coerceAtMost((screenW - reports.width - edge).coerceAtLeast(edge))
    boxes[3] = Rect(reportsLeft, reports.top, reportsLeft + reports.width, reports.bottom)
    if (overlap(boxes[3], boxes[4], pad)) {
        val s = boxes[4]
        val sx = (boxes[3].right + pad).coerceAtMost((screenW - s.width - edge).coerceAtLeast(edge))
        boxes[4] = Rect(sx, s.top, sx + s.width, s.bottom)
    }

    val tuneW = with(density) { 176.dp.toPx() }
    val tuneH = topH
    var tuneX = (tune.right - tuneW).coerceIn(edge, (screenW - tuneW - edge).coerceAtLeast(edge))
    var tuneY = (tune.bottom + gap)
    var tuneBox = Rect(tuneX, tuneY, tuneX + tuneW, tuneY + tuneH)
    boxes.forEach { other ->
        if (overlap(tuneBox, other, pad)) {
            tuneY = (other.top - pad - tuneH).coerceAtLeast(edge)
            tuneBox = Rect(tuneX, tuneY, tuneX + tuneW, tuneY + tuneH)
        }
    }

    val inset = with(density) { 10.dp.toPx() }
    val filterBox = boxes[1]
    val reportBox = boxes[3]
    fun fromOn(box: Rect, icon: Rect, i: Int): Offset {
        val x = when (i) {
            0 -> min(box.left + inset, filterBox.left - inset).coerceIn(box.left + inset, box.right - inset)
            4 -> max(box.right - inset, reportBox.right + inset).coerceIn(box.left + inset, box.right - inset)
            else -> icon.center.x.coerceIn(box.left + inset, box.right - inset)
        }
        return Offset(x, box.bottom)
    }
    val tuneFromX = tune.center.x.coerceIn(tuneBox.left + inset, tuneBox.right - inset)
    return listOf(
        Spot(tune, "Tune", "Display — Radar, list, timeline, hybrid, By class.", tuneBox, Offset(tuneFromX, tuneBox.top)),
    ) + tabs.mapIndexed { i, t ->
        Spot(t.first, t.second, t.third, boxes[i], fromOn(boxes[i], t.first, i))
    }
}

private fun separateBubbles(spots: List<Spot>, pad: Float, edge: Float): List<Spot> {
    val boxes = spots.map { it.box }.toMutableList()
    for (n in 0 until 16) {
        var moved = false
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                if (!overlap(boxes[i], boxes[j], pad)) continue
                val (ui, uj) = if (boxes[i].top <= boxes[j].top) i to j else j to i
                val upper = boxes[ui]
                val lower = boxes[uj]
                val newTop = (lower.top - pad - upper.height).coerceAtLeast(edge)
                if (kotlin.math.abs(newTop - upper.top) > 0.5f) {
                    boxes[ui] = Rect(upper.left, newTop, upper.right, newTop + upper.height)
                    moved = true
                }
            }
        }
        if (!moved) break
    }
    return spots.mapIndexed { i, s ->
        val b = boxes[i]
        val fromY = if (s.title == "Tune") b.top else b.bottom
        s.copy(box = b, from = Offset(s.from.x, fromY))
    }
}

private fun overlap(a: Rect, b: Rect, pad: Float): Boolean =
    a.left < b.right + pad && a.right > b.left - pad &&
        a.top < b.bottom + pad && a.bottom > b.top - pad

private fun overlapHeight(a: Rect, b: Rect): Float =
    max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))

private fun closestEdge(r: Rect, from: Offset): Offset {
    val left = from.x <= r.left
    val right = from.x >= r.right
    val top = from.y <= r.top
    val bottom = from.y >= r.bottom
    if (left || right || top || bottom) {
        return Offset(
            when {
                left -> r.left
                right -> r.right
                else -> from.x.coerceIn(r.left, r.right)
            },
            when {
                top -> r.top
                bottom -> r.bottom
                else -> from.y.coerceIn(r.top, r.bottom)
            },
        )
    }
    val dl = from.x - r.left
    val dr = r.right - from.x
    val dt = from.y - r.top
    val db = r.bottom - from.y
    return when (minOf(dl, dr, dt, db)) {
        dl -> Offset(r.left, from.y)
        dr -> Offset(r.right, from.y)
        dt -> Offset(from.x, r.top)
        else -> Offset(from.x, r.bottom)
    }
}

private fun DrawScope.arrow(from: Offset, to: Offset, color: Color, width: Float) {
    drawLine(color, from, to, strokeWidth = width, cap = StrokeCap.Round)
    val angle = atan2(to.y - from.y, to.x - from.x)
    val len = width * 4.2f
    val path = Path()
    path.moveTo(to.x, to.y)
    path.lineTo(to.x + cos(angle + 2.55f) * len, to.y + sin(angle + 2.55f) * len)
    path.lineTo(to.x + cos(angle - 2.55f) * len, to.y + sin(angle - 2.55f) * len)
    path.close()
    drawPath(path, color)
}
