package app.fieldwatch.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.data.PathTiles
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.ui.ClassGlyphs
import app.fieldwatch.ui.RadioClassBadge
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
    val night = LocalNightMode.current
    val you = Cyan.nightIf(night)
    val glyphs = classGlyphPainters()
    val pilotPainter = rememberVectorPainter(Icons.Outlined.Person)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = muted)
    val gnssInk = GnssDiamond.nightIf(night)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedGnss by remember { mutableStateOf<String?>(null) }
    val layout = remember(model, boxSize) {
        if (boxSize.width < 8 || boxSize.height < 8) null
        else SitPathPlot.layout(model, boxSize.width.toFloat(), boxSize.height.toFloat())
    }
    val clusters = remember(layout, model) {
        val lay = layout
        if (lay == null) {
            emptyList()
        } else {
            val numberOf: (SitPathPlot.Dot) -> Int = { dot ->
                val i = model.dots.indexOfFirst { it.key == dot.key }
                if (i < 0) 1 else i + 1
            }
            SitPathPlot.clusters(lay.dots, numberOf = numberOf)
        }
    }
    val selected = clusters.firstOrNull { it.id == selectedId }
    val markers = remember(layout, model.live, boxSize.width) {
        pathMarkers(layout, model.live, boxSize.width.toFloat(), measurer, labelStyle)
    }
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
                .pointerInput(clusters, markers, layout, model.gnss) {
                    detectTapGestures { pos ->
                        val lay = layout
                        val gnssHit = if (lay == null) emptyList() else gnssAt(model.gnss, lay, pos.x, pos.y)
                        val radio = SitPathPlot.clusterAt(clusters, pos.x, pos.y, markers)
                        val gnssD = gnssHit.minOfOrNull { pin ->
                            val pt = lay!!.project(pin.lat, pin.lon)
                            hypot(pt.x - pos.x, pt.y - pos.y)
                        }
                        val radioD = radio?.let { hypot(it.center.x - pos.x, it.center.y - pos.y) }
                        val pickGnss = gnssHit.isNotEmpty() && (radioD == null || gnssD!! <= radioD)
                        if (pickGnss) {
                            val id = gnssHit.joinToString("|") { gnssId(it) }
                            selectedGnss = if (selectedGnss == id) null else id
                            selectedId = null
                        } else {
                            selectedGnss = null
                            selectedId = when {
                                radio == null -> null
                                selectedId == radio.id -> null
                                else -> radio.id
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
            }
            if (lay.path.size >= 2) {
                val trace = if (lay.samples.size >= 2) lay.samples else model.samples
                val stays = Geo.legs(trace).filter { it.stay }
                for (i in 1 until lay.path.size) {
                    val t = trace.getOrNull(i)?.at ?: continue
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
                val dur = (trace.last().at - trace.first().at).coerceAtLeast(1L)
                listOf(0.25, 0.5, 0.75).forEach { frac ->
                    val want = trace.first().at + (dur * frac).toLong()
                    val idx = trace.indices.minByOrNull { abs(trace[it].at - want) } ?: return@forEach
                    if (idx == 0 || idx == trace.lastIndex) return@forEach
                    val pt = lay.path.getOrNull(idx) ?: return@forEach
                    val label = TIME_FMT.format(Date(trace[idx].at))
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
                drawStartDot(start.x, start.y)
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
            drawAdvertised(lay, model.craft, model.pilots, model.dots, pilotPainter)
            clusters.forEach { cluster ->
                val pt = Offset(cluster.center.x, cluster.center.y)
                if (cluster.stacked) {
                    val hot = selected?.id == cluster.id
                    drawCountBadge(pt, cluster.members.size, if (hot) 18f else 15f, measurer)
                } else {
                    val m = cluster.members.single()
                    val fill = discColor(m.dot, night)
                    val painter = glyphs[m.dot.classKind] ?: glyphs[null]!!
                    drawClassDisc(pt, 12f, fill, painter)
                }
            }
            if (lay.path.isNotEmpty()) {
                val end = lay.path.last()
                drawYouDot(end.x, end.y, 6.2f, you)
            }
            model.gnss.forEach { pin ->
                val pt = lay.project(pin.lat, pin.lon)
                drawGnssDiamond(Offset(pt.x, pt.y), gnssInk)
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
        val gnssCard = model.gnss.filter { gnssId(it) in selectedGnss.orEmpty().split("|") }
        if (gnssCard.isNotEmpty()) {
            val anchor = layout?.project(gnssCard.first().lat, gnssCard.first().lon)
            val onRight = (anchor?.x ?: 0f) < (boxSize.width / 2f)
            Column(
                Modifier
                    .align(if (onRight) Alignment.TopEnd else Alignment.TopStart)
                    .padding(8.dp)
                    .widthIn(max = 248.dp)
                    .background(surface, RoundedCornerShape(8.dp))
                    .border(1.dp, outline, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Text(
                    if (gnssCard.size == 1) "GNSS detection" else "${gnssCard.size} GNSS detections",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
                gnssCard.forEach { pin ->
                    Text(
                        "${pin.title()} · ${pin.confidence}",
                        style = MaterialTheme.typography.bodySmall,
                        color = track,
                    )
                    Text(
                        pin.whenLine(),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    Text(
                        pin.detail.trim().ifBlank { "Signal changed." },
                        style = MaterialTheme.typography.bodySmall,
                        color = track,
                    )
                }
            }
        } else if (selected != null) {
            val onRight = selected.center.x < (boxSize.width / 2f)
            Column(
                Modifier
                    .align(if (onRight) Alignment.TopEnd else Alignment.TopStart)
                    .padding(8.dp)
                    .widthIn(max = 248.dp)
                    .background(surface, RoundedCornerShape(8.dp))
                    .border(1.dp, outline, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Text(
                    if (selected.members.size == 1) "1 alert here" else "${selected.members.size} alerts here",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
                selected.members.forEach { m ->
                    val accent = discColor(m.dot, night)
                    val obs = m.dot.observerNotes.trim()
                    Row(
                        modifier = Modifier
                            .clickable { onOpenRadio(m.dot.key) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${m.number}",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted,
                            modifier = Modifier.width(16.dp),
                        )
                        RadioClassBadge(m.dot.classKind, accent, compact = true)
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.dot.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = track,
                                maxLines = 2,
                            )
                            if (obs.isNotEmpty()) {
                                Text(
                                    obs,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = you,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal val AircraftAmber = Color(0xFFC47A00)

private val ClusterFill = Color(0xFF1A2330)
private val ClusterInk = Color(0xFFF4F7FB)
private val UnmatchedDisc = Color(0xFF8D6E63)

@Composable
private fun classGlyphPainters(): Map<SignatureClass?, Painter> {
    val out = LinkedHashMap<SignatureClass?, Painter>()
    out[null] = rememberVectorPainter(ClassGlyphs.unmatched)
    for (kind in SignatureClass.entries) {
        out[kind] = rememberVectorPainter(ClassGlyphs.of(kind))
    }
    return out
}

private fun discColor(dot: SitPathPlot.Dot, night: Boolean): Color {
    val raw = if (dot.accentArgb != 0) Color(dot.accentArgb) else UnmatchedDisc
    return raw.nightIf(night)
}

private fun pathMarkers(
    layout: SitPathPlot.Layout?,
    live: Boolean,
    width: Float,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
): List<SitPathPlot.HitMarker> {
    val lay = layout ?: return emptyList()
    if (lay.path.size < 2 || width < 8f) return emptyList()
    val start = lay.path.first()
    val end = lay.path.last()
    val startText = measurer.measure("Start", labelStyle)
    val endText = measurer.measure(if (live) "Now" else "End", labelStyle)
    return listOf(
        hitMarker(start, 12f, startText, width, dx = 8f, dy = -6f),
        hitMarker(end, 12f, endText, width, dx = 10f, dy = -(endText.size.height + 4f).toFloat()),
    )
}

private fun hitMarker(
    dot: SitPathPlot.Pt,
    radius: Float,
    text: androidx.compose.ui.text.TextLayoutResult,
    width: Float,
    dx: Float,
    dy: Float,
): SitPathPlot.HitMarker {
    val left = (dot.x + dx).coerceAtMost((width - text.size.width).coerceAtLeast(0f))
    val top = (dot.y + dy).coerceAtLeast(0f)
    return SitPathPlot.HitMarker(
        x = dot.x,
        y = dot.y,
        radius = radius,
        labelLeft = left,
        labelTop = top,
        labelRight = left + text.size.width,
        labelBottom = top + text.size.height,
    )
}

private val GnssDiamond = Color(0xFFB91C1C)

private fun gnssId(pin: SitPathPlot.GnssPin) = "${pin.at}:${pin.kind}:${pin.secondary}"

private fun gnssAt(
    pins: List<SitPathPlot.GnssPin>,
    lay: SitPathPlot.Layout,
    x: Float,
    y: Float,
    slop: Float = 22f,
): List<SitPathPlot.GnssPin> = pins.filter { pin ->
    val pt = lay.project(pin.lat, pin.lon)
    hypot(pt.x - x, pt.y - y) <= slop
}

private fun DrawScope.drawGnssDiamond(center: Offset, color: Color) {
    fun diamond(r: Float) = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + r * 0.86f, center.y)
        lineTo(center.x, center.y + r)
        lineTo(center.x - r * 0.86f, center.y)
        close()
    }
    drawPath(diamond(9.2f), Color.White)
    drawPath(diamond(7.2f), color)
}

private fun DrawScope.drawStartDot(x: Float, y: Float) {
    drawCircle(Color.White, radius = 8.4f, center = Offset(x, y))
    drawCircle(Color.Black, radius = 6.2f, center = Offset(x, y))
}

private fun DrawScope.drawYouDot(x: Float, y: Float, radius: Float, you: Color) {
    drawCircle(Color.White, radius = radius + 2.2f, center = Offset(x, y))
    drawCircle(you, radius = radius, center = Offset(x, y))
}

private fun DrawScope.drawClassDisc(center: Offset, radius: Float, fill: Color, painter: Painter) {
    drawCircle(Color.White, radius = radius + 1.6f, center = center)
    drawCircle(fill, radius = radius, center = center)
    val icon = radius * 1.25f
    translate(center.x - icon / 2f, center.y - icon / 2f) {
        with(painter) {
            draw(Size(icon, icon), colorFilter = ColorFilter.tint(Color.White))
        }
    }
}

private fun DrawScope.drawCountBadge(center: Offset, count: Int, radius: Float, measurer: TextMeasurer) {
    drawCircle(ClusterInk, radius = radius + 2f, center = center)
    drawCircle(ClusterFill, radius = radius, center = center)
    val measured = measurer.measure(
        "$count",
        TextStyle(color = ClusterInk, fontSize = 12.sp, fontWeight = FontWeight.Bold),
    )
    drawText(
        measured,
        topLeft = Offset(center.x - measured.size.width / 2f, center.y - measured.size.height / 2f),
    )
}

private val PilotInk = Color(0xFF3D4A55)

private val AircraftDots = PathEffect.dashPathEffect(floatArrayOf(4f, 9f), 0f)
private val AircraftInk = Color.White

private fun DrawScope.drawAdvertised(
    lay: SitPathPlot.Layout,
    tracks: List<SitPathPlot.FigureTrack>,
    pilots: List<SitPathPlot.Mark>,
    dots: List<SitPathPlot.Dot>,
    pilotPainter: Painter,
) {
    tracks.forEach { track ->
        val samples = track.samples
        if (samples.isEmpty()) return@forEach
        val pts = samples.map { lay.project(it.lat, it.lon) }
        if (pts.size >= 2) {
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            }
            drawPath(
                path,
                AircraftInk,
                style = Stroke(
                    width = 4.2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = AircraftDots,
                ),
            )
            val start = pts.first()
            drawCircle(Color.White, radius = 4.6f, center = Offset(start.x, start.y))
            drawCircle(
                AircraftInk,
                radius = 4.6f,
                center = Offset(start.x, start.y),
                style = Stroke(width = 1.8f),
            )
        }
        val end = pts.last()
        val covered = dots.any { SitPathPlot.sitsOnCraft(it, listOf(track)) }
        if (!covered) {
            drawCircle(Color.White, radius = 4.6f, center = Offset(end.x, end.y))
            drawCircle(
                AircraftInk,
                radius = 4.6f,
                center = Offset(end.x, end.y),
                style = Stroke(width = 1.8f),
            )
        }
    }
    pilots.forEach { pilot ->
        val pt = lay.project(pilot.lat, pilot.lon)
        drawPilotMark(Offset(pt.x, pt.y), pilotPainter)
    }
}

private fun DrawScope.drawPilotMark(center: Offset, painter: Painter) {
    val radius = 11f
    drawCircle(Color.White, radius = radius + 1.6f, center = center)
    drawCircle(Color(0xFFF4F7FB), radius = radius, center = center)
    drawCircle(PilotInk, radius = radius, center = center, style = Stroke(width = 1.5f))
    val icon = radius * 1.35f
    translate(center.x - icon / 2f, center.y - icon / 2f) {
        with(painter) {
            draw(Size(icon, icon), colorFilter = ColorFilter.tint(PilotInk))
        }
    }
}

private fun scaleLabel(m: Double): String =
    if (m >= 1000) "${(m / 1000).toInt()} km" else "${m.toInt()} m"

private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())
