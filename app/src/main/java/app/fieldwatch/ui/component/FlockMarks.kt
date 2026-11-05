package app.fieldwatch.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import app.fieldwatch.domain.CameraGather
import app.fieldwatch.domain.FlockApproach
import app.fieldwatch.domain.FlockIndex
import app.fieldwatch.domain.FlockMark
import app.fieldwatch.domain.SensorKind
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.ui.theme.nightIf
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.PI

/** Marks for saved roadside sensors. A crowd of one type keeps that color. One sensor is its own mark. */
fun DrawScope.drawFlockMarks(
    lay: SitPathPlot.Layout,
    index: FlockIndex,
    night: Boolean,
    measurer: TextMeasurer,
    countStyle: TextStyle,
    /** Sit path and the live list map. Larger, with a light ring, so they read on the blue drive line. */
    bold: Boolean = false,
    /** Marks already chosen for this picture. Null asks the index for the same set. */
    prepared: List<FlockMark>? = null,
) {
    val marks = prepared ?: flockMarksOn(lay, index)
    val metersPerPx = lay.metersPerPx.coerceAtLeast(0.05f)
    val wide = bold && !flockWedges(metersPerPx)
    for (mark in marks) {
        val pt = lay.project(mark.lat, mark.lon)
        val at = Offset(pt.x, pt.y)
        if (wide) {
            drawCircle(markDot(mark), WIDE_DOT_PX, at)
            continue
        }
        if (mark.count > 1) {
            drawFlockCount(at, mark, night, measurer, countStyle, bold)
        } else {
            drawFlockSight(at, mark, lay, metersPerPx, bold)
        }
    }
}

/**
 * A long path keeps the dot. The wedge and the white ring return once a 150-foot
 * cone is large enough to read without covering the road.
 */
internal fun flockWedges(metersPerPx: Float): Boolean {
    if (!metersPerPx.isFinite() || metersPerPx <= 0f) return true
    return CONE_M / metersPerPx >= WEDGE_SHOW_PX
}

/**
 * One sensor. A plate reader, speed camera, or red light with a direction is a 150-foot wedge,
 * 50 degrees wide. It follows the map when the map turns. Another camera uses a shorter wedge.
 * A gunshot detector is a dot. No stored direction means the dot only.
 */
private fun DrawScope.drawFlockSight(
    at: Offset,
    mark: FlockMark,
    lay: SitPathPlot.Layout,
    metersPerPx: Float,
    bold: Boolean,
) {
    val paint = sightPaint(mark)
    val minPx = if (bold) paint.minPx * 2.5f else paint.minPx
    val fill = if (bold) paint.fill.copy(alpha = (paint.fill.alpha * 1.55f).coerceAtMost(0.72f)) else paint.fill
    val stroke = if (bold) paint.stroke * 1.8f else paint.stroke
    var reachPx = 0f
    if (paint.coneM > 0.0) {
        for (facing in mark.facings) {
            val (lat, lon) = FlockApproach.destination(mark.lat, mark.lon, facing.toDouble(), paint.coneM)
            val end = lay.project(lat, lon)
            val dx = end.x - at.x
            val dy = end.y - at.y
            if (dx == 0f && dy == 0f) continue
            val radius = (paint.coneM / metersPerPx).toFloat().coerceIn(minPx, paint.maxPx)
            reachPx = max(reachPx, radius)
            val bearing = atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI)
            val start = (bearing - CONE_DEG / 2.0).toFloat()
            val box = Offset(at.x - radius, at.y - radius)
            val size = Size(radius * 2f, radius * 2f)
            drawArc(fill, start, CONE_DEG, useCenter = true, topLeft = box, size = size)
            drawArc(
                paint.line,
                start,
                CONE_DEG,
                useCenter = true,
                topLeft = box,
                size = size,
                style = Stroke(width = stroke),
            )
        }
    }
    if (bold) {
        val disc = 13f
        drawCircle(Color.White, disc, at)
        drawCircle(Color(0xFF0F172A), disc, at, style = Stroke(width = 2.4f))
        drawCircle(markDot(mark), 8f, at)
        return
    }
    val glow = if (reachPx > 0f) {
        (reachPx * paint.glowScale).coerceIn(paint.glowMin, paint.glowMax)
    } else {
        (paint.dotM / metersPerPx).toFloat().coerceIn(paint.glowMin, paint.glowMax)
    }
    drawCircle(paint.glow, glow, at)
    drawCircle(paint.core, glow * 0.42f, at)
    drawCircle(Color.White, glow * 0.28f, at)
}

private fun markDot(mark: FlockMark): Color = when (mark.kind) {
    SensorKind.SPEED -> Color(0xFF14B8A6)
    SensorKind.RED -> Color(0xFFEF4444)
    SensorKind.GUNSHOT -> Color(0xFFA78BFA)
    SensorKind.CAMERA -> Color(0xFF64748B)
    SensorKind.PLATE -> if (mark.flock) Color(0xFF3B82F6) else Color(0xFFF59E0B)
}

private data class SightPaint(
    val fill: Color,
    val line: Color,
    val glow: Color,
    val core: Color,
    val coneM: Double,
    val minPx: Float,
    val maxPx: Float,
    val stroke: Float,
    val glowScale: Float,
    val glowMin: Float,
    val glowMax: Float,
    val dotM: Double,
)

private fun countPaint(kind: SensorKind, night: Boolean): Pair<Color, Color> = when (kind) {
    SensorKind.PLATE -> Color.White.nightIf(night) to Color.Black.nightIf(night)
    SensorKind.SPEED -> Color(0xFF14B8A6) to Color.White
    SensorKind.RED -> Color(0xFFEF4444) to Color.White
    SensorKind.GUNSHOT -> Color(0xFF7C3AED) to Color.White
    SensorKind.CAMERA -> Color(0xFF475569) to Color.White
}

private fun sightPaint(mark: FlockMark): SightPaint = when (mark.kind) {
    SensorKind.SPEED -> SightPaint(
        SpeedFill, SpeedLine, SpeedGlow, SpeedCore,
        CONE_M, CONE_MIN_PX, CONE_MAX_PX, 1.7f, 0.16f, 8f, 18f, 12.0,
    )
    SensorKind.RED -> SightPaint(
        RedFill, RedLine, RedGlow, RedCore,
        CONE_M, CONE_MIN_PX, CONE_MAX_PX, 1.7f, 0.16f, 8f, 18f, 12.0,
    )
    SensorKind.GUNSHOT -> SightPaint(
        GunFill, GunLine, GunGlow, GunCore,
        0.0, CONE_MIN_PX, CONE_MAX_PX, 1.7f, 0.16f, 7f, 14f, 10.0,
    )
    SensorKind.CAMERA -> SightPaint(
        CamFill, CamLine, CamGlow, CamCore,
        CONE_M * 0.5, 10f, 90f, 1.2f, 0.10f, 4f, 9f, 7.0,
    )
    SensorKind.PLATE -> if (mark.flock) {
        SightPaint(
            ConeFill, ConeLine, ConeGlow, ConeCore,
            CONE_M, CONE_MIN_PX, CONE_MAX_PX, 1.7f, 0.16f, 8f, 18f, 12.0,
        )
    } else {
        SightPaint(
            OtherFill, OtherLine, OtherGlow, OtherCore,
            CONE_M, CONE_MIN_PX, CONE_MAX_PX, 1.7f, 0.16f, 8f, 18f, 12.0,
        )
    }
}

private fun DrawScope.drawFlockCount(
    at: Offset,
    mark: FlockMark,
    night: Boolean,
    measurer: TextMeasurer,
    countStyle: TextStyle,
    bold: Boolean,
) {
    val (fill, ink) = countPaint(mark.kind, night)
    val measured = measurer.measure(mark.count.toString(), countStyle)
    val radius = max(if (bold) 14f else 8f, measured.size.width / 2f + if (bold) 5f else 3f)
    if (bold) {
        drawCircle(Color.White, radius + 3f, at)
        drawCircle(Color(0xFF0F172A), radius + 3f, at, style = Stroke(width = 2f))
    }
    drawCircle(fill, radius, at)
    drawCircle(ink, radius, at, style = Stroke(if (bold) 1.8f else 1.2f))
    drawText(
        measured,
        color = ink,
        topLeft = Offset(at.x - measured.size.width / 2f, at.y - measured.size.height / 2f),
    )
}

/**
 * The camera picture for a finished sit path. Built when the view changes.
 * A drag draws these paths instead of painting each wedge again.
 */
internal class CameraStamp(
    val shapes: List<StampShape>,
    val labels: List<StampLabel>,
)

internal class StampShape(val color: Color, val path: Path, val strokeWidth: Float)

internal class StampLabel(val text: TextLayoutResult, val color: Color, val x: Float, val y: Float)

/** One gathered shape, before it is turned into a [Path]. */
internal class PlannedShape(
    val color: Color,
    val strokeWidth: Float,
    val wedges: Int,
    val circles: Int,
    val ops: List<StampOp>,
)

internal class StampOp(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val startDeg: Float,
    val wedge: Boolean,
)

internal class PlannedLabel(val text: TextLayoutResult, val color: Color, val x: Float, val y: Float)

internal class StampPlan(
    val shapes: List<PlannedShape>,
    val labels: List<PlannedLabel>,
)

internal fun DrawScope.drawCameraStamp(stamp: CameraStamp) {
    for (shape in stamp.shapes) {
        if (shape.strokeWidth > 0f) {
            drawPath(shape.path, shape.color, style = Stroke(width = shape.strokeWidth))
        } else {
            drawPath(shape.path, shape.color)
        }
    }
    for (label in stamp.labels) {
        drawText(label.text, color = label.color, topLeft = Offset(label.x, label.y))
    }
}

/**
 * Same marks as [drawFlockMarks], gathered into a few paths.
 * Wedge fills of one color are one shape, so where two wedges of that color
 * overlap the tint is painted once.
 */
internal fun recordFlockMarks(
    lay: SitPathPlot.Layout,
    marks: List<FlockMark>,
    night: Boolean,
    countStyle: TextStyle,
    bold: Boolean,
    measurer: TextMeasurer,
): CameraStamp {
    val plan = planFlockMarks(lay, marks, night, bold) { text ->
        measurer.measure(text, countStyle)
    }
    val shapes = ArrayList<StampShape>(plan.shapes.size)
    for (shape in plan.shapes) {
        val path = Path()
        for (op in shape.ops) {
            if (op.wedge) path.wedge(op.cx, op.cy, op.r, op.startDeg)
            else path.circle(op.cx, op.cy, op.r)
        }
        shapes.add(StampShape(shape.color, path, shape.strokeWidth))
    }
    val labels = ArrayList<StampLabel>(plan.labels.size)
    for (label in plan.labels) {
        labels.add(StampLabel(label.text, label.color, label.x, label.y))
    }
    return CameraStamp(shapes, labels)
}

/**
 * Groups the marks [drawFlockMarks] would paint. One color is one shape.
 * [measure] is used only for a numbered crowd.
 */
internal fun planFlockMarks(
    lay: SitPathPlot.Layout,
    marks: List<FlockMark>,
    night: Boolean,
    bold: Boolean,
    measure: (String) -> TextLayoutResult = { error("count") },
): StampPlan {
    val metersPerPx = lay.metersPerPx.coerceAtLeast(0.05f)
    val wide = bold && !flockWedges(metersPerPx)
    val wedgeFill = LinkedHashMap<Color, OpList>()
    val wedgeLine = LinkedHashMap<StrokeKey, OpList>()
    val backFill = LinkedHashMap<Color, OpList>()
    val rings = LinkedHashMap<StrokeKey, OpList>()
    val midFill = LinkedHashMap<Color, OpList>()
    val frontFill = LinkedHashMap<Color, OpList>()
    val frontLine = LinkedHashMap<StrokeKey, OpList>()
    val labels = ArrayList<PlannedLabel>()
    for (mark in marks) {
        val pt = lay.project(mark.lat, mark.lon)
        val at = Offset(pt.x, pt.y)
        if (wide) {
            frontFill.ops(markDot(mark)).circle(at.x, at.y, WIDE_DOT_PX)
            continue
        }
        if (mark.count > 1) {
            val (fill, ink) = countPaint(mark.kind, night)
            val measured = measure(mark.count.toString())
            val radius = max(if (bold) 14f else 8f, measured.size.width / 2f + if (bold) 5f else 3f)
            if (bold) {
                backFill.ops(Color.White).circle(at.x, at.y, radius + 3f)
                rings.ops(Color(0xFF0F172A), 2f).circle(at.x, at.y, radius + 3f)
            }
            frontFill.ops(fill).circle(at.x, at.y, radius)
            frontLine.ops(ink, if (bold) 1.8f else 1.2f).circle(at.x, at.y, radius)
            labels.add(
                PlannedLabel(
                    measured,
                    ink,
                    at.x - measured.size.width / 2f,
                    at.y - measured.size.height / 2f,
                ),
            )
            continue
        }
        val paint = sightPaint(mark)
        val minPx = if (bold) paint.minPx * 2.5f else paint.minPx
        val fill = if (bold) paint.fill.copy(alpha = (paint.fill.alpha * 1.55f).coerceAtMost(0.72f)) else paint.fill
        val stroke = if (bold) paint.stroke * 1.8f else paint.stroke
        var reachPx = 0f
        if (paint.coneM > 0.0) {
            for (facing in mark.facings) {
                val (lat, lon) = FlockApproach.destination(mark.lat, mark.lon, facing.toDouble(), paint.coneM)
                val end = lay.project(lat, lon)
                val dx = end.x - at.x
                val dy = end.y - at.y
                if (dx == 0f && dy == 0f) continue
                val radius = (paint.coneM / metersPerPx).toFloat().coerceIn(minPx, paint.maxPx)
                reachPx = max(reachPx, radius)
                val start = (atan2(dy.toDouble(), dx.toDouble()) * (180.0 / PI) - CONE_DEG / 2.0).toFloat()
                wedgeFill.ops(fill).wedge(at.x, at.y, radius, start)
                wedgeLine.ops(paint.line, stroke).wedge(at.x, at.y, radius, start)
            }
        }
        if (bold) {
            backFill.ops(Color.White).circle(at.x, at.y, 13f)
            rings.ops(Color(0xFF0F172A), 2.4f).circle(at.x, at.y, 13f)
            frontFill.ops(markDot(mark)).circle(at.x, at.y, 8f)
            continue
        }
        val glow = if (reachPx > 0f) {
            (reachPx * paint.glowScale).coerceIn(paint.glowMin, paint.glowMax)
        } else {
            (paint.dotM / metersPerPx).toFloat().coerceIn(paint.glowMin, paint.glowMax)
        }
        backFill.ops(paint.glow).circle(at.x, at.y, glow)
        midFill.ops(paint.core).circle(at.x, at.y, glow * 0.42f)
        frontFill.ops(Color.White).circle(at.x, at.y, glow * 0.28f)
    }
    val shapes = ArrayList<PlannedShape>()
    fun addFills(paths: Map<Color, OpList>) {
        for ((color, ops) in paths) {
            shapes.add(PlannedShape(color, 0f, ops.wedges, ops.circles, ops.ops))
        }
    }
    fun addLines(paths: Map<StrokeKey, OpList>) {
        for ((key, ops) in paths) {
            shapes.add(PlannedShape(key.color, key.width, ops.wedges, ops.circles, ops.ops))
        }
    }
    addFills(wedgeFill)
    addLines(wedgeLine)
    addFills(backFill)
    addLines(rings)
    addFills(midFill)
    addFills(frontFill)
    addLines(frontLine)
    return StampPlan(shapes, labels)
}

private data class StrokeKey(val color: Color, val width: Float)

private class OpList {
    val ops = ArrayList<StampOp>()
    var wedges = 0
    var circles = 0

    fun circle(cx: Float, cy: Float, r: Float) {
        ops.add(StampOp(cx, cy, r, 0f, false))
        circles += 1
    }

    fun wedge(cx: Float, cy: Float, r: Float, startDeg: Float) {
        ops.add(StampOp(cx, cy, r, startDeg, true))
        wedges += 1
    }
}

private fun MutableMap<Color, OpList>.ops(color: Color): OpList = getOrPut(color) { OpList() }

private fun MutableMap<StrokeKey, OpList>.ops(color: Color, width: Float): OpList =
    getOrPut(StrokeKey(color, width)) { OpList() }

private fun Path.circle(cx: Float, cy: Float, r: Float) {
    addOval(Rect(cx - r, cy - r, cx + r, cy + r))
}

private fun Path.wedge(cx: Float, cy: Float, r: Float, startDeg: Float) {
    moveTo(cx, cy)
    arcTo(Rect(cx - r, cy - r, cx + r, cy + r), startDeg, CONE_DEG, false)
    close()
}

/** 150 feet. The view cone is a direction, not a measured coverage range. */
private const val CONE_M = 150.0 * 0.3048

/** Below this on-screen size the wedge and the white ring cover a long path. */
private const val WEDGE_SHOW_PX = 28f
private const val WIDE_DOT_PX = 6f
private const val CONE_DEG = 50f
private const val CONE_MIN_PX = 16f
private const val CONE_MAX_PX = 170f
private val ConeFill = Color(0xFF3B82F6).copy(alpha = 0.40f)
private val ConeLine = Color(0xFF93C5FD).copy(alpha = 0.92f)
private val ConeGlow = Color(0xFF60A5FA).copy(alpha = 0.55f)
private val ConeCore = Color(0xFFBFDBFE)
private val OtherFill = Color(0xFFF59E0B).copy(alpha = 0.42f)
private val OtherLine = Color(0xFFFCD34D).copy(alpha = 0.92f)
private val OtherGlow = Color(0xFFFBBF24).copy(alpha = 0.55f)
private val OtherCore = Color(0xFFFDE68A)
private val SpeedFill = Color(0xFF14B8A6).copy(alpha = 0.40f)
private val SpeedLine = Color(0xFF5EEAD4).copy(alpha = 0.92f)
private val SpeedGlow = Color(0xFF2DD4BF).copy(alpha = 0.55f)
private val SpeedCore = Color(0xFF99F6E4)
private val RedFill = Color(0xFFEF4444).copy(alpha = 0.40f)
private val RedLine = Color(0xFFFCA5A5).copy(alpha = 0.92f)
private val RedGlow = Color(0xFFF87171).copy(alpha = 0.55f)
private val RedCore = Color(0xFFFECACA)
private val GunFill = Color(0xFF8B5CF6).copy(alpha = 0.40f)
private val GunLine = Color(0xFFC4B5FD).copy(alpha = 0.92f)
private val GunGlow = Color(0xFFA78BFA).copy(alpha = 0.55f)
private val GunCore = Color(0xFFDDD6FE)
private val CamFill = Color(0xFF64748B).copy(alpha = 0.28f)
private val CamLine = Color(0xFF94A3B8).copy(alpha = 0.70f)
private val CamGlow = Color(0xFF64748B).copy(alpha = 0.45f)
private val CamCore = Color(0xFFCBD5E1)

private data class FlockBox(val south: Double, val north: Double, val west: Double, val east: Double)

/** The marks [drawFlockMarks] paints for this picture. Hit testing uses this list. */
fun flockMarksOn(lay: SitPathPlot.Layout, index: FlockIndex): List<FlockMark> =
    cameraMarksFor(lay, index, null).second

/**
 * Packed marks for this picture, plus the gather to keep for the next frame.
 * The gather is the geographic read. Packing still uses this frame's pixels.
 */
internal fun cameraMarksFor(
    lay: SitPathPlot.Layout,
    index: FlockIndex,
    previous: CameraGather?,
): Pair<CameraGather?, List<FlockMark>> {
    val box = flockBox(lay) ?: return null to emptyList()
    val gather = index.gatherFor(previous, box.south, box.north, box.west, box.east)
    val raw = gather.visible(box.south, box.north, box.west, box.east)
    val packed = FlockIndex.pack(
        raw,
        project = { lat, lon ->
            val pt = lay.project(lat, lon)
            pt.x to pt.y
        },
        inside = { x, y ->
            x in lay.plotLeft..lay.plotRight && y in lay.plotTop..lay.plotBottom
        },
    )
    return gather to packed
}

private fun flockBox(lay: SitPathPlot.Layout): FlockBox? {
    if (lay.plotRight <= lay.plotLeft || lay.plotBottom <= lay.plotTop) return null
    val corners = arrayOf(
        lay.unproject(lay.plotLeft, lay.plotTop),
        lay.unproject(lay.plotRight, lay.plotTop),
        lay.unproject(lay.plotLeft, lay.plotBottom),
        lay.unproject(lay.plotRight, lay.plotBottom),
    )
    var south = 90.0
    var north = -90.0
    var west = 180.0
    var east = -180.0
    for ((lat, lon) in corners) {
        if (!lat.isFinite() || !lon.isFinite()) return null
        if (lat < south) south = lat
        if (lat > north) north = lat
        if (lon < west) west = lon
        if (lon > east) east = lon
    }
    if (north - south > 180.0) return null
    return FlockBox(south, north, west, east)
}
