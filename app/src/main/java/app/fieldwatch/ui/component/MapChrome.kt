package app.fieldwatch.ui.component

import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.MapCameraMarks

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.fieldwatch.data.PathTiles
import app.fieldwatch.domain.MapFrame
import app.fieldwatch.domain.SitPathPlot
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Map credit in the bottom-right corner.
 * One line stays as wide as its words. Extra lines stay on the right half so the grid on the left stays clear.
 */
@Composable
internal fun MapCreditChip(
    lines: List<String>,
    link: String?,
    onOpen: (String) -> Unit,
    color: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
) {
    val surface = MaterialTheme.colorScheme.surface
    val cap = if (lines.size > 1 && maxWidth != Dp.Unspecified) {
        Modifier.widthIn(max = maxWidth)
    } else {
        Modifier
    }
    Column(
        modifier
            .then(cap)
            .background(surface.copy(alpha = 0.84f), RoundedCornerShape(2.dp))
            .clickable(enabled = link != null) {
                if (link != null) onOpen(link)
            }
            .padding(horizontal = 4.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.End,
    ) {
        for (line in lines) {
            Text(
                line,
                style = textStyle,
                color = color,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One camera under the compass, flush with its right edge, with a small gap between them.
 * Tap turns the cameras in this picture on, then off.
 * A filled lens is on. Off is the same camera, dimmed. No label, so the detection list does not move.
 */
@Composable
internal fun MapCameraButton(
    mode: MapCameraMarks,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val plate = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .size(36.dp)
            .semantics { contentDescription = LanguageText.text(mode.descriptionKey()) }
            .clip(shape)
            .background(plate.copy(alpha = 0.88f), shape)
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCameraGlyph(ink, mode)
        }
    }
}

private fun DrawScope.drawCameraGlyph(ink: Color, mode: MapCameraMarks) {
    val paint = if (mode == MapCameraMarks.OFF) ink.copy(alpha = 0.38f) else ink
    val w = size.width
    val h = size.height
    val body = Size(w * 0.62f, h * 0.38f)
    val left = (w - body.width) / 2f
    val top = h * 0.32f
    val stroke = Stroke(width = w * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val radius = CornerRadius(w * 0.08f, w * 0.08f)
    drawRoundRect(paint, Offset(left, top), body, radius, style = stroke)
    val bump = body.width * 0.26f
    drawLine(
        paint,
        Offset(left + body.width * 0.16f, top),
        Offset(left + body.width * 0.16f + bump, top - h * 0.09f),
        stroke.width,
    )
    val lens = body.minDimension * 0.26f
    val center = Offset(left + body.width / 2f, top + body.height / 2f)
    if (mode == MapCameraMarks.VIEW) {
        drawCircle(paint, lens, center)
    } else {
        drawCircle(paint, lens, center, style = stroke)
    }
}

/**
 * One square, four pictures. Tap switches north-up and direction of travel.
 * Double-tap on the map switches the whole path and the close view.
 * Zoomed out is a bent path. North-up puts N above it. Direction of travel
 * uses the same compass letters as the close view, above a smaller path.
 * Zoomed in is those letters, or N above an arrow when north is up.
 * The letter N is not localized. Compass letters turn as the heading changes.
 * The arrow stays pointed up the screen.
 */
@Composable
internal fun MapCompassButton(
    frame: Int,
    northDeg: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val key = when (frame) {
        MapFrame.CLOSE -> "map.close.heading"
        MapFrame.CLOSE_NORTH -> "map.close.north"
        MapFrame.TRAVEL -> "map.rotate.travel"
        else -> "map.north.up"
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val plate = MaterialTheme.colorScheme.surface
    val shape = RoundedCornerShape(8.dp)
    val heading = if (northDeg.isFinite()) northDeg.toFloat() else 0f
    Box(
        modifier
            .size(48.dp)
            .semantics { contentDescription = LanguageText.text(key) }
            .clip(shape)
            .background(plate.copy(alpha = 0.88f), shape)
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.matchParentSize()) {
            when (frame) {
                MapFrame.CLOSE -> drawHeadingDial(heading, ink)
                MapFrame.CLOSE_NORTH -> drawCloseNorth(ink)
                MapFrame.TRAVEL -> drawZoomedOut(heading, ink, northLocked = false)
                else -> drawZoomedOut(heading, ink, northLocked = true)
            }
        }
    }
}

private val DIAL_NAMES = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

/** Heading letters slide along the top arc. The chevron stays pointed up. */
private fun DrawScope.drawHeadingDial(headingDeg: Float, ink: Color) {
    val cx = size.width / 2f
    val cy = size.height * 0.92f
    val radius = size.minDimension * 0.92f
    drawArc(
        color = ink.copy(alpha = 0.55f),
        startAngle = -158f,
        sweepAngle = 136f,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 1.6f, cap = StrokeCap.Round),
    )
    for (bearing in 0 until 360 step 15) {
        val fromTop = signedFromTop(bearing - headingDeg)
        if (abs(fromTop) > 64f) continue
        val theta = Math.toRadians((fromTop - 90f).toDouble())
        val outer = radius * 0.98f
        val inner = radius * if (bearing % 45 == 0) 0.84f else 0.90f
        val fade = (1f - abs(fromTop) / 72f).coerceIn(0.35f, 1f)
        drawLine(
            ink.copy(alpha = fade),
            Offset(cx + cos(theta).toFloat() * inner, cy + sin(theta).toFloat() * inner),
            Offset(cx + cos(theta).toFloat() * outer, cy + sin(theta).toFloat() * outer),
            strokeWidth = if (bearing % 45 == 0) 2f else 1.3f,
            cap = StrokeCap.Round,
        )
    }
    val paint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setColor(ink.toArgb())
    }
    val native = drawContext.canvas.nativeCanvas
    for (i in DIAL_NAMES.indices) {
        val fromTop = signedFromTop(i * 45f - headingDeg)
        if (abs(fromTop) > 58f) continue
        val theta = Math.toRadians((fromTop - 90f).toDouble())
        val r = radius * 0.62f
        val x = cx + cos(theta).toFloat() * r
        val y = cy + sin(theta).toFloat() * r
        val near = abs(fromTop) < 16f
        paint.textSize = size.height * if (near) 0.30f else 0.22f
        paint.alpha = ((if (near) 1f else (1f - abs(fromTop) / 70f).coerceIn(0.45f, 0.9f)) * 255f).toInt()
        native.drawText(DIAL_NAMES[i], x, y - (paint.ascent() + paint.descent()) / 2f, paint)
    }
    val chevron = Path().apply {
        moveTo(size.width * 0.50f, size.height * 0.58f)
        lineTo(size.width * 0.64f, size.height * 0.78f)
        lineTo(size.width * 0.36f, size.height * 0.78f)
        close()
    }
    drawPath(chevron, ink)
}

private fun signedFromTop(delta: Float): Float {
    var fromTop = delta % 360f
    if (fromTop < 0f) fromTop += 360f
    if (fromTop > 180f) fromTop -= 360f
    return fromTop
}

/** Close view, north up. N sits above the arrow, with an up tick on each side. */
private fun DrawScope.drawCloseNorth(ink: Color) {
    drawNorthLock(ink, size.width / 2f, size.height * 0.32f, size.minDimension * 0.22f)
    drawUpArrow(ink, size.height * 0.56f, size.height * 0.84f)
}

/** Whole path. A bent route, and either locked north or the same heading dial above it. */
private fun DrawScope.drawZoomedOut(headingDeg: Float, ink: Color, northLocked: Boolean) {
    if (northLocked) {
        drawNorthLock(ink, size.width / 2f, size.height * 0.20f, size.minDimension * 0.16f)
        drawRoute(ink, size.height * 0.38f, size.height * 0.94f)
        return
    }
    // Same dial as the close view, shrunk toward the top so the route fits under it.
    scale(scaleX = 0.72f, scaleY = 0.72f, pivot = Offset(size.width / 2f, 0f)) {
        drawHeadingDial(headingDeg, ink)
    }
    drawRoute(
        ink,
        boxTop = size.height * 0.60f,
        boxBottom = size.height * 0.98f,
        strokeFrac = 0.042f,
        side = 0.32f,
    )
}

/** N with a small up tick on each side. The ticks say north stays at the top. */
private fun DrawScope.drawNorthLock(ink: Color, cx: Float, cy: Float, scale: Float) {
    val triH = scale * 0.70f
    val triW = scale * 0.50f
    val gap = scale * 1.15f
    fun tick(x: Float) {
        val mark = Path().apply {
            moveTo(x, cy - triH / 2f)
            lineTo(x + triW / 2f, cy + triH / 2f)
            lineTo(x - triW / 2f, cy + triH / 2f)
            close()
        }
        drawPath(mark, ink)
    }
    tick(cx - gap)
    tick(cx + gap)
    val paint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setColor(ink.toArgb())
        textSize = scale * 1.35f
    }
    drawContext.canvas.nativeCanvas.drawText(
        "N",
        cx,
        cy - (paint.ascent() + paint.descent()) / 2f,
        paint,
    )
}

private fun DrawScope.drawUpArrow(ink: Color, tipY: Float, baseY: Float) {
    val cx = size.width / 2f
    val half = size.width * 0.16f
    val arrow = Path().apply {
        moveTo(cx, tipY)
        lineTo(cx + half, baseY)
        lineTo(cx - half, baseY)
        close()
    }
    drawPath(arrow, ink)
}

/** Whole path. Two rings joined by a bent route. */
private fun DrawScope.drawRoute(
    ink: Color,
    boxTop: Float,
    boxBottom: Float,
    strokeFrac: Float = 0.065f,
    side: Float = 0.24f,
) {
    val w = size.width
    val span = (boxBottom - boxTop).coerceAtLeast(1f)
    val stroke = size.minDimension * strokeFrac
    val left = w * side
    val right = w * (1f - side)
    val mid = (left + right) / 2f
    val top = boxTop + span * 0.22f
    val low = boxTop + span * 0.62f
    val ring = stroke * 0.75f
    val route = Path().apply {
        moveTo(left, low - ring)
        lineTo(left, top)
        lineTo(mid, top)
        lineTo(mid, low)
        lineTo(right, low)
        lineTo(right, top + ring)
    }
    drawPath(
        route,
        ink,
        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    val ringStroke = Stroke(width = stroke, cap = StrokeCap.Round)
    drawCircle(ink, ring, Offset(left, low), style = ringStroke)
    drawCircle(ink, ring, Offset(right, top), style = ringStroke)
}

/**
 * Map pictures for one view. The newest zoom is drawn on top of an older one.
 * They are flattened first, then faded, so a closer picture does not show through a wider one.
 */
internal fun DrawScope.drawMapTiles(
    tiles: List<PathTiles.Tile>,
    project: (Double, Double) -> SitPathPlot.Pt,
    alpha: Float = 0.55f,
) {
    if (tiles.isEmpty()) return
    val front = tiles.last()
    val frontSpan = front.east - front.west
    val top = ArrayList<PathTiles.Tile>(tiles.size)
    val back = ArrayList<PathTiles.Tile>()
    for (tile in tiles) {
        if (PathTiles.sameTileZoom(tile.east - tile.west, frontSpan)) top += tile
        else back += tile
    }
    if (back.isEmpty() || top.isEmpty()) {
        val only = if (top.isNotEmpty()) top else back
        for (tile in only) drawOneTile(tile, project, alpha)
        return
    }
    val fade = androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha }
    drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), fade)
    try {
        for (tile in back) drawOneTile(tile, project, 1f)
        for (tile in top) drawOneTile(tile, project, 1f)
    } finally {
        drawContext.canvas.restore()
    }
}

private fun DrawScope.drawOneTile(
    tile: PathTiles.Tile,
    project: (Double, Double) -> SitPathPlot.Pt,
    alpha: Float,
) {
    runCatching {
        drawMapRaster(
            tile.bitmap.asImageBitmap(),
            tile.north,
            tile.west,
            tile.south,
            tile.east,
            project,
            alpha = alpha,
        )
    }
}

/** A tile or heat picture. North-up stays an axis-aligned rectangle. A turned map rotates the bitmap so its top stays the north edge. */
internal fun DrawScope.drawMapRaster(
    image: ImageBitmap,
    north: Double,
    west: Double,
    south: Double,
    east: Double,
    project: (Double, Double) -> SitPathPlot.Pt,
    alpha: Float = 1f,
    filter: FilterQuality = FilterQuality.Low,
) {
    val nw = project(north, west)
    val ne = project(north, east)
    val sw = project(south, west)
    val se = project(south, east)
    val dx = ne.x - nw.x
    val dy = ne.y - nw.y
    val upright = abs(dy) <= 0.5f && dx > 0f && se.y >= nw.y
    if (upright) {
        drawImage(
            image,
            dstOffset = IntOffset(nw.x.roundToInt(), nw.y.roundToInt()),
            dstSize = IntSize(
                (se.x - nw.x).roundToInt().coerceAtLeast(1),
                (se.y - nw.y).roundToInt().coerceAtLeast(1),
            ),
            alpha = alpha,
            filterQuality = filter,
        )
        return
    }
    val w = hypot(dx.toDouble(), dy.toDouble()).roundToInt().coerceAtLeast(1)
    val h = hypot((sw.x - nw.x).toDouble(), (sw.y - nw.y).toDouble()).roundToInt().coerceAtLeast(1)
    val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    rotate(degrees = deg, pivot = Offset(nw.x, nw.y)) {
        drawImage(
            image,
            dstOffset = IntOffset(nw.x.roundToInt(), nw.y.roundToInt()),
            dstSize = IntSize(w, h),
            alpha = alpha,
            filterQuality = filter,
        )
    }
}
