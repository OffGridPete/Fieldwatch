package app.fieldwatch.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.data.PathTiles
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.ui.theme.Cyan
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun SitPathCanvas(
    model: SitPathPlot.Model,
    modifier: Modifier = Modifier,
    tiles: List<PathTiles.Tile> = emptyList(),
    onOpenRadio: (String) -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.onSurface
    val extra = MaterialTheme.colorScheme.error
    val named = Cyan.nightIf(LocalNightMode.current)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = muted)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val layout = remember(model, boxSize) {
        if (boxSize.width < 8 || boxSize.height < 8) null
        else SitPathPlot.layout(model, boxSize.width.toFloat(), boxSize.height.toFloat())
    }
    val clusters = remember(layout) {
        layout?.let { SitPathPlot.clusters(it.dots) } ?: emptyList()
    }
    val selected = clusters.firstOrNull { it.id == selectedId && it.stacked }
    Box(
        modifier.then(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .onSizeChanged { boxSize = it },
        ),
    ) {
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(clusters) {
                    detectTapGestures { pos ->
                        val hit = clusters.minByOrNull { c ->
                            hypot((c.center.x - pos.x).toDouble(), (c.center.y - pos.y).toDouble())
                        }
                        val dist = hit?.let {
                            hypot((it.center.x - pos.x).toDouble(), (it.center.y - pos.y).toDouble())
                        } ?: 999.0
                        selectedId = when {
                            hit == null || dist > 40.0 -> null
                            hit.stacked -> if (selectedId == hit.id) null else hit.id
                            else -> {
                                onOpenRadio(hit.members.single().dot.key)
                                null
                            }
                        }
                    }
                },
        ) {
            val lay = layout ?: return@Canvas
            clipRect(lay.plotLeft, lay.plotTop, lay.plotRight, lay.plotBottom) {
                tiles.forEach { tile ->
                    val nw = lay.project(tile.north, tile.west)
                    val se = lay.project(tile.south, tile.east)
                    val left = nw.x.roundToInt()
                    val top = nw.y.roundToInt()
                    val w = (se.x - nw.x).roundToInt().coerceAtLeast(1)
                    val h = (se.y - nw.y).roundToInt().coerceAtLeast(1)
                    runCatching {
                        drawImage(
                            tile.bitmap.asImageBitmap(),
                            dstOffset = IntOffset(left, top),
                            dstSize = IntSize(w, h),
                            alpha = 0.55f,
                        )
                    }
                }
            }
            if (lay.path.size >= 2) {
                val path = Path().apply {
                    moveTo(lay.path[0].x, lay.path[0].y)
                    for (i in 1 until lay.path.size) lineTo(lay.path[i].x, lay.path[i].y)
                }
                drawPath(
                    path,
                    color = track.copy(alpha = 0.85f),
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                val stays = Geo.legs(model.samples).filter { it.stay }
                for (i in 1 until lay.path.size) {
                    val t = model.samples.getOrNull(i)?.at ?: continue
                    if (stays.none { t in it.startAt..it.endAt }) continue
                    drawLine(
                        PhosphorActive.copy(alpha = 0.9f),
                        Offset(lay.path[i - 1].x, lay.path[i - 1].y),
                        Offset(lay.path[i].x, lay.path[i].y),
                        strokeWidth = 9f,
                        cap = StrokeCap.Round,
                    )
                }
                stays.forEach { stay ->
                    val pt = lay.project(stay.lat, stay.lon)
                    drawCircle(PhosphorActive.copy(alpha = 0.22f), radius = 16f, center = Offset(pt.x, pt.y))
                }
                val dur = (model.samples.last().at - model.samples.first().at).coerceAtLeast(1L)
                listOf(0.25, 0.5, 0.75).forEach { frac ->
                    val want = model.samples.first().at + (dur * frac).toLong()
                    val idx = model.samples.indices.minByOrNull { abs(model.samples[it].at - want) } ?: return@forEach
                    if (idx == 0 || idx == model.samples.lastIndex) return@forEach
                    val pt = lay.path.getOrNull(idx) ?: return@forEach
                    val label = TIME_FMT.format(Date(model.samples[idx].at))
                    val measured = measurer.measure(label, labelStyle)
                    drawCircle(muted, radius = 3f, center = Offset(pt.x, pt.y))
                    drawText(
                        measured,
                        topLeft = Offset(
                            (pt.x + 6f).coerceAtMost(size.width - measured.size.width),
                            (pt.y - measured.size.height - 2f).coerceAtLeast(0f),
                        ),
                    )
                }
                val start = lay.path.first()
                val end = lay.path.last()
                drawCircle(track, radius = 6f, center = Offset(start.x, start.y))
                drawCircle(track, radius = 8f, center = Offset(end.x, end.y))
                val startT = measurer.measure("Start", labelStyle)
                drawText(startT, topLeft = Offset((start.x + 8f).coerceAtMost(size.width - startT.size.width), start.y - 6f))
                val endLabel = if (model.live) "Now" else "End"
                val endT = measurer.measure(endLabel, labelStyle)
                drawText(
                    endT,
                    topLeft = Offset(
                        (end.x + 10f).coerceAtMost(size.width - endT.size.width),
                        (end.y - endT.size.height - 4f).coerceAtLeast(0f),
                    ),
                )
            }
            clusters.forEach { cluster ->
                val pt = Offset(cluster.center.x, cluster.center.y)
                val color = when {
                    cluster.members.any { it.dot.extraAttention } -> extra
                    cluster.members.any { it.dot.named } -> named
                    else -> track
                }
                if (cluster.stacked) {
                    val hot = selected?.id == cluster.id
                    drawCircle(color, radius = if (hot) 18f else 16f, center = pt)
                    val num = measurer.measure(
                        "${cluster.members.size}",
                        labelStyle.copy(color = Color.White, fontSize = 11.sp),
                    )
                    drawText(num, topLeft = Offset(pt.x - num.size.width / 2f, pt.y - num.size.height / 2f))
                } else {
                    val m = cluster.members.single()
                    drawCircle(color, radius = 9f, center = pt)
                    val num = measurer.measure(
                        "${m.number}",
                        labelStyle.copy(color = Color.White, fontSize = 9.sp),
                    )
                    drawText(num, topLeft = Offset(pt.x - num.size.width / 2f, pt.y - num.size.height / 2f))
                }
            }
            if (selected != null) {
                val insetOnRight = selected.center.x < size.width / 2f
                val dest = Offset(
                    if (insetOnRight) size.width - 12f else 12f,
                    28f,
                )
                drawLine(
                    outline,
                    Offset(selected.center.x, selected.center.y),
                    dest,
                    strokeWidth = 2f,
                )
            }
            val barW = (size.width - 32f) * lay.scaleBarFrac
            val barLabel = scaleLabel(lay.scaleBarM)
            val measured = measurer.measure(barLabel, labelStyle)
            val groupW = barW + 8f + measured.size.width
            val barX = ((size.width - groupW) / 2f).coerceAtLeast(8f)
            val barY = size.height - 16f
            drawLine(muted, Offset(barX, barY), Offset(barX + barW, barY), strokeWidth = 3f)
            drawLine(muted, Offset(barX, barY - 5f), Offset(barX, barY + 5f), strokeWidth = 3f)
            drawLine(muted, Offset(barX + barW, barY - 5f), Offset(barX + barW, barY + 5f), strokeWidth = 3f)
            drawText(measured, topLeft = Offset(barX + barW + 8f, barY - measured.size.height / 2f))
            val n = measurer.measure("N", labelStyle)
            drawText(n, topLeft = Offset(size.width - n.size.width - 10f, 8f))
        }
        if (selected != null) {
            val onRight = selected.center.x < (boxSize.width / 2f)
            Column(
                Modifier
                    .align(if (onRight) Alignment.TopEnd else Alignment.TopStart)
                    .padding(8.dp)
                    .widthIn(max = 200.dp)
                    .background(surface, RoundedCornerShape(8.dp))
                    .border(1.dp, outline, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Text(
                    "${selected.members.size} stacked",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
                selected.members.forEach { m ->
                    val tag = if (m.dot.extraAttention) "Extra attention" else ""
                    val obs = m.dot.observerNotes.trim()
                    Text(
                        "${m.number}. ${m.dot.label}" +
                            (if (tag.isNotEmpty()) " · $tag" else "") +
                            (if (obs.isNotEmpty()) " · Observer: $obs" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (m.dot.extraAttention) extra else track,
                        modifier = Modifier
                            .clickable { onOpenRadio(m.dot.key) }
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private fun scaleLabel(m: Double): String =
    if (m >= 1000) "${(m / 1000).toInt()} km" else "${m.toInt()} m"

private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())
