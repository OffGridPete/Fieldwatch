package app.fieldwatch.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.data.PathTiles
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.HuntHeat
import app.fieldwatch.domain.PayloadLocation
import app.fieldwatch.ui.theme.nightIf
import app.fieldwatch.domain.FlockCredit
import app.fieldwatch.domain.FlockIndex
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.MapFrame
import app.fieldwatch.domain.SitPathPlot
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val You = Color(0xFF37D6FF)

/**
 * Map of this hunt. North stays up until the compass is set to follow travel.
 * One field, colored by a smooth ramp from poor to powerful. The ramp blends
 * where hears differ. An edge fades out in that same color, so a weak edge stays blue.
 */
@Composable
fun HuntHeatMap(
    trail: List<GpsSample>,
    hereLat: Double?,
    hereLon: Double?,
    tiles: List<PathTiles.Tile>,
    position: String?,
    hint: String?,
    viewport: SitPathPlot.Viewport?,
    onViewport: (SitPathPlot.Viewport?) -> Unit,
    modifier: Modifier = Modifier,
    /** Circle around the dot, in meters. Zero hides it. */
    uncertaintyM: Double = 0.0,
    /** Amber circle while steps are paused. */
    holdFlat: Boolean = false,
    /** Degrees clockwise from north. NaN keeps this frame north-up. */
    courseDeg: Double = Double.NaN,
    /** [MapFrame]. The hunt follow stays its own close window in every frame. */
    mapFrame: Int = MapFrame.NORTH,
    /** Null hides the button and keeps the drawn N. */
    onRotate: (() -> Unit)? = null,
    /** Double-tap. The canvas also clears a held view. */
    onToggleClose: (() -> Unit)? = null,
    flock: Boolean = false,
    flockIndex: FlockIndex? = null,
    flockRevision: Int = 0,
    /** South, north, west, east of this picture, including a finger-held view. */
    onTileWindow: ((DoubleArray) -> Unit)? = null,
) {
    val here = if (PayloadLocation.validCoord(hereLat, hereLon)) {
        listOf(GpsSample(0L, hereLat!!, hereLon!!))
    } else {
        emptyList()
    }
    val model = remember(trail, hereLat, hereLon) {
        HuntHeat.followModel(hereLat, hereLon, trail)
    }
    val track = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val labelStyle = TextStyle(fontSize = 10.sp, color = muted)
    val night = app.fieldwatch.ui.theme.LocalNightMode.current
    val flockCountStyle = TextStyle(fontSize = 9.sp, color = Color.Black.nightIf(night))
    val flockGen = if (flock && flockIndex != null) flockRevision else -1
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val appliedCourse = if (onRotate == null) {
        Double.NaN
    } else {
        MapFrame.drawnCourse(
            mapFrame,
            if (viewport == null) null else viewport.heldCourseDeg,
            courseDeg,
        )
    }
    // Drawn angle turns through the gap between GPS courses. Tiles stay on the heading reached.
    val drawnCourse = rememberEasedCourse(appliedCourse)
    val layout = remember(model, boxSize, viewport, drawnCourse) {
        if (boxSize.width < 8 || boxSize.height < 8) null
        else SitPathPlot.layout(
            model,
            boxSize.width.toFloat(),
            boxSize.height.toFloat(),
            viewport = viewport,
            minHalfM = HuntHeat.PINCH_HALF_M,
            courseDeg = drawnCourse,
        )
    }
    val tileLayout = remember(model, boxSize, viewport, appliedCourse) {
        if (boxSize.width < 8 || boxSize.height < 8) null
        else SitPathPlot.layout(
            model,
            boxSize.width.toFloat(),
            boxSize.height.toFloat(),
            viewport = viewport,
            minHalfM = HuntHeat.PINCH_HALF_M,
            courseDeg = appliedCourse,
        )
    }
    val onTileWindowRef = remember { mutableStateOf(onTileWindow) }
    onTileWindowRef.value = onTileWindow
    LaunchedEffect(tileLayout) {
        val window = tileLayout?.tileWindow() ?: return@LaunchedEffect
        onTileWindowRef.value?.invoke(window)
    }
    // Rebuild only when the trail changes. A pinch reuses this picture.
    val heat = remember(trail) { rasterHuntHeat(trail) }
    val layoutRef = remember { mutableStateOf(layout) }
    val viewportRef = remember { mutableStateOf(viewport) }
    val onViewportRef = remember { mutableStateOf(onViewport) }
    val courseRef = remember { mutableStateOf(Double.NaN) }
    val onToggleRef = remember { mutableStateOf(onToggleClose) }
    val lastTapMs = remember { mutableLongStateOf(0L) }
    layoutRef.value = layout
    viewportRef.value = viewport
    onViewportRef.value = onViewport
    courseRef.value = drawnCourse
    onToggleRef.value = onToggleClose
    val gestures = Modifier.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var armed = false
            var zoomAccum = 1f
            var panAccum = Offset.Zero
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val focus = event.calculateCentroid(useCurrent = true)
                if (!armed) {
                    zoomAccum *= zoom
                    panAccum += pan
                    val past = abs(1f - zoomAccum) > 0.08f || panAccum.getDistance() > slop
                    if (past) {
                        armed = true
                        moveHuntCamera(
                            zoomAccum, panAccum, focus, layoutRef, viewportRef, onViewportRef, courseRef,
                        )
                        event.changes.forEach { it.consume() }
                    }
                } else {
                    moveHuntCamera(zoom, pan, focus, layoutRef, viewportRef, onViewportRef, courseRef)
                    event.changes.forEach { it.consume() }
                }
            }
            if (!armed) {
                val now = down.uptimeMillis
                if (now - lastTapMs.longValue <= 280L) {
                    lastTapMs.longValue = 0L
                    viewportRef.value = null
                    onViewportRef.value(null)
                    onToggleRef.value?.invoke()
                } else {
                    lastTapMs.longValue = now
                }
            }
        }
    }
    val ramp = Array(25) { i ->
        val t = i / 24f
        t to Color(HuntHeat.colorArgb(t))
    }
    Box(modifier.fillMaxSize().onSizeChanged { boxSize = it }) {
        Canvas(Modifier.fillMaxSize().then(gestures)) {
            val lay = layout ?: return@Canvas
            clipRect(lay.plotLeft, lay.plotTop, lay.plotRight, lay.plotBottom) {
                drawMapTiles(tiles, lay.project, alpha = 0.55f)
                if (flockGen >= 0 && flockIndex != null) {
                    drawFlockMarks(lay, flockIndex, night, measurer, flockCountStyle)
                }
                heat?.let { field ->
                    drawMapRaster(
                        field.image,
                        field.north,
                        field.west,
                        field.south,
                        field.east,
                        lay.project,
                        filter = FilterQuality.High,
                    )
                }
                val heard = HuntHeat.loudest(trail)
                val markLat = heat?.hotLat ?: heard?.lat
                val markLon = heat?.hotLon ?: heard?.lon
                if (markLat != null && markLon != null) {
                    val pt = lay.project(markLat, markLon)
                    val at = Offset(pt.x, pt.y)
                    drawCircle(Color.Black.copy(alpha = 0.85f), radius = 8f, center = at, style = Stroke(3.5f))
                    drawCircle(Color.White, radius = 6.2f, center = at, style = Stroke(1.8f))
                }
                if (here.isNotEmpty()) {
                    val pt = lay.project(here[0].lat, here[0].lon)
                    val at = Offset(pt.x, pt.y)
                    if (uncertaintyM > 0.0 && lay.metersPerPx > 0f) {
                        val radiusPx = (uncertaintyM / lay.metersPerPx).toFloat()
                        val ring = if (holdFlat) Color(0xFFFFC107) else You
                        drawCircle(ring, radius = radiusPx, center = at, style = Stroke(2.5f))
                    }
                    drawCircle(Color.White, radius = 8f, center = at)
                    drawCircle(You, radius = 5.6f, center = at)
                }
            }
            val barW = (size.width - 32f) * lay.scaleBarFrac
            val barLabel = if (lay.scaleBarM >= 1000) {
                "${(lay.scaleBarM / 1000).toInt()} km"
            } else {
                "${lay.scaleBarM.toInt()} m"
            }
            val measured = measurer.measure(barLabel, labelStyle)
            val north = measurer.measure("N", labelStyle)
            val groupW = barW + 8f + measured.size.width
            val barX = ((size.width - groupW) / 2f).coerceAtLeast(8f)
            val barY = size.height - 16f
            drawLine(muted, Offset(barX, barY), Offset(barX + barW, barY), strokeWidth = 3f, cap = StrokeCap.Round)
            drawLine(muted, Offset(barX, barY - 5f), Offset(barX, barY + 5f), strokeWidth = 3f)
            drawLine(muted, Offset(barX + barW, barY - 5f), Offset(barX + barW, barY + 5f), strokeWidth = 3f)
            drawText(measured, topLeft = Offset(barX + barW + 8f, barY - measured.size.height / 2f))
            if (onRotate == null) {
                drawText(north, topLeft = Offset(size.width - north.size.width - 10f, barY - north.size.height / 2f))
            }
        }
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .widthIn(max = 220.dp)
                .background(surface.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Brush.horizontalGradient(*ramp), RoundedCornerShape(4.dp)),
            )
            Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                LegendWord(LanguageText.text("hunt.heat.poor"), TextAlign.Start)
                LegendWord(LanguageText.text("hunt.heat.moderate"), TextAlign.Center)
                LegendWord(LanguageText.text("hunt.heat.strong"), TextAlign.Center)
                LegendWord(LanguageText.text("hunt.heat.powerful"), TextAlign.End)
            }
        }
        if (viewport != null) {
            TextButton(
                onClick = { onViewport(null) },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            ) {
                Text(LanguageText.text("display.recenter"))
            }
        }
        if (!hint.isNullOrBlank()) {
            Text(
                hint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(surface.copy(alpha = 0.88f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        val mapTile = tiles.firstOrNull()
        val layForCredit = layout
        if (layForCredit != null && boxSize.height > 0 && (mapTile != null || flockGen >= 0)) {
            val mapCredit = mapTile?.basemap
            val bottomPad = with(density) { (boxSize.height - layForCredit.plotBottom).toDp() + 2.dp }
            val half = with(density) {
                ((layForCredit.plotRight - layForCredit.plotLeft) / 2f).toDp() - 4.dp
            }.coerceAtLeast(0.dp)
            val lines = if (mapCredit != null) {
                mapCredit.shownCreditLines(
                    mapCredit.cautionKey?.let { LanguageText.text(it) },
                    mapTile.overlays,
                    flockGen >= 0,
                )
            } else {
                listOf(FlockCredit.LINE)
            }
            val besideCompass = onRotate != null && lines.size == 1
            val endPad = with(density) { (boxSize.width - layForCredit.plotRight).toDp() + 2.dp } +
                if (besideCompass) 52.dp else 0.dp
            val lift = if (onRotate != null && lines.size > 1) 52.dp else 0.dp
            val link = mapCredit?.creditUrl ?: if (flockGen >= 0) FlockCredit.URL else null
            MapCreditChip(
                lines = lines,
                link = link,
                onOpen = { runCatching { uriHandler.openUri(it) } },
                color = track,
                textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                maxWidth = half,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = endPad, bottom = bottomPad + lift),
            )
        }
        if (onRotate != null) {
            MapCompassButton(
                frame = mapFrame,
                northDeg = drawnCourse,
                onClick = onRotate,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
            )
        }
        if (!position.isNullOrBlank() && layForCredit != null && boxSize.height > 0) {
            val bottomPad = with(density) { (boxSize.height - layForCredit.plotBottom).toDp() + 2.dp }
            val startPad = with(density) { layForCredit.plotLeft.toDp() + 2.dp }
            val half = with(density) {
                ((layForCredit.plotRight - layForCredit.plotLeft) / 2f).toDp() - 4.dp
            }.coerceAtLeast(0.dp)
            Text(
                position,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = startPad, bottom = bottomPad)
                    .widthIn(max = half)
                    .background(surface.copy(alpha = 0.84f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                color = track,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowScope.LegendWord(text: String, align: TextAlign) {
    Text(
        text,
        modifier = Modifier.weight(1f),
        fontSize = 9.sp,
        lineHeight = 11.sp,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private class HuntHeatField(
    val image: ImageBitmap,
    val north: Double,
    val west: Double,
    val south: Double,
    val east: Double,
    val hotLat: Double?,
    val hotLon: Double?,
)

/**
 * One picture of the hunt, in meters, so a pinch only moves it.
 * Consecutive hears are one line. Color changes along that line.
 * Close parts of the walk fill the ground between them. A far gap stays open.
 * The rim fades that color out. It does not slide the color back toward blue.
 */
private fun rasterHuntHeat(trail: List<GpsSample>): HuntHeatField? {
    val hears = ArrayList<Pair<GpsSample, Float>>(trail.size)
    for (sample in trail) {
        val level = HuntHeat.level(sample.rssi) ?: continue
        hears.add(sample to level)
    }
    if (hears.isEmpty()) return null
    var lat0 = 0.0
    var lon0 = 0.0
    for ((sample, _) in hears) {
        lat0 += sample.lat
        lon0 += sample.lon
    }
    lat0 /= hears.size
    lon0 /= hears.size
    val cos0 = cos(Math.toRadians(lat0)).coerceAtLeast(0.2)
    val mLon = 111_320.0 * cos0
    val mLat = 110_540.0
    var minE = Double.POSITIVE_INFINITY
    var maxE = Double.NEGATIVE_INFINITY
    var minN = Double.POSITIVE_INFINITY
    var maxN = Double.NEGATIVE_INFINITY
    val east = DoubleArray(hears.size)
    val north = DoubleArray(hears.size)
    for (i in hears.indices) {
        val sample = hears[i].first
        val e = (sample.lon - lon0) * mLon
        val n = (sample.lat - lat0) * mLat
        east[i] = e
        north[i] = n
        if (e < minE) minE = e
        if (e > maxE) maxE = e
        if (n < minN) minN = n
        if (n > maxN) maxN = n
    }
    val pad = HuntHeat.RIBBON_M
    minE -= pad
    maxE += pad
    minN -= pad
    maxN += pad
    val spanE = (maxE - minE).coerceAtLeast(1.0)
    val spanN = (maxN - minN).coerceAtLeast(1.0)
    var cell = max(CELL_M, max(spanE, spanN) / MAX_HEAT_PX)
    val blobReach = ceil(HuntHeat.RIBBON_M / cell).toInt().coerceAtLeast(1)
    val stamps = hears.size.toLong() * blobReach * blobReach * 8L
    if (stamps > STAMP_BUDGET) cell *= sqrt(stamps / STAMP_BUDGET.toDouble())
    val cols = ceil(spanE / cell).toInt().coerceIn(1, MAX_HEAT_PX)
    val rows = ceil(spanN / cell).toInt().coerceIn(1, MAX_HEAT_PX)
    val count = cols * rows
    val sumW = FloatArray(count)
    val sumHw = FloatArray(count)
    val sumHl = FloatArray(count)
    val peak = FloatArray(count)
    val minDist = FloatArray(count) { Float.POSITIVE_INFINITY }
    val blob = HuntHeat.RIBBON_M
    val linked = BooleanArray(hears.size)
    fun visit(
        e0: Double,
        n0: Double,
        l0: Float,
        e1: Double,
        n1: Double,
        l1: Float,
        onDab: (index: Int, cover: Float, dist: Double, heard: Float) -> Unit,
    ) {
        val dx = e1 - e0
        val dy = n1 - n0
        val len2 = dx * dx + dy * dy
        val colLo = (((min(e0, e1) - blob) - minE) / cell).toInt().coerceIn(0, cols - 1)
        val colHi = (((max(e0, e1) + blob) - minE) / cell).toInt().coerceIn(0, cols - 1)
        val rowLo = ((maxN - (max(n0, n1) + blob)) / cell).toInt().coerceIn(0, rows - 1)
        val rowHi = ((maxN - (min(n0, n1) - blob)) / cell).toInt().coerceIn(0, rows - 1)
        for (row in rowLo..rowHi) {
            val cn = maxN - (row + 0.5) * cell
            val base = row * cols
            for (col in colLo..colHi) {
                val ce = minE + (col + 0.5) * cell
                val t = if (len2 < 1e-4) 0.0 else ((ce - e0) * dx + (cn - n0) * dy) / len2
                val u = t.coerceIn(0.0, 1.0)
                val dist = hypot(ce - (e0 + u * dx), cn - (n0 + u * dy))
                val w = HuntHeat.ribbonWeight(dist)
                if (w <= 0f) continue
                onDab(base + col, w, dist, HuntHeat.alongLevel(l0, l1, u))
            }
        }
    }
    fun nearest(e0: Double, n0: Double, l0: Float, e1: Double, n1: Double, l1: Float) {
        visit(e0, n0, l0, e1, n1, l1) { i, _, dist, _ ->
            if (dist < minDist[i]) minDist[i] = dist.toFloat()
        }
    }
    fun paint(e0: Double, n0: Double, l0: Float, e1: Double, n1: Double, l1: Float) {
        visit(e0, n0, l0, e1, n1, l1) { i, w, dist, heard ->
            val extra = (dist - minDist[i]).coerceAtLeast(0.0)
            val hw = HuntHeat.mixWeight(HuntHeat.shadeWeight(extra), heard)
            sumW[i] += w
            sumHw[i] += hw
            sumHl[i] += hw * heard
            if (w > peak[i]) peak[i] = w
        }
    }
    for (h in 1 until hears.size) {
        val dist = hypot(east[h] - east[h - 1], north[h] - north[h - 1])
        if (!HuntHeat.connects(dist)) continue
        linked[h] = true
        linked[h - 1] = true
    }
    val close = HuntHeat.CLOSE_M
    val bins = HashMap<Long, MutableList<Int>>()
    fun binKey(bx: Int, by: Int): Long = (bx.toLong() shl 32) xor (by.toLong() and 0xffffffffL)
    for (h in hears.indices) {
        val bx = floor(east[h] / close).toInt()
        val by = floor(north[h] / close).toInt()
        bins.getOrPut(binKey(bx, by)) { ArrayList() }.add(h)
    }
    fun eachStroke(draw: (Double, Double, Float, Double, Double, Float) -> Unit) {
        for (h in 1 until hears.size) {
            val dist = hypot(east[h] - east[h - 1], north[h] - north[h - 1])
            if (!HuntHeat.connects(dist)) continue
            draw(
                east[h - 1], north[h - 1], hears[h - 1].second,
                east[h], north[h], hears[h].second,
            )
        }
        for (h in hears.indices) {
            if (linked[h]) continue
            val level = hears[h].second
            draw(east[h], north[h], level, east[h], north[h], level)
        }
        for (h in hears.indices) {
            val bx = floor(east[h] / close).toInt()
            val by = floor(north[h] / close).toInt()
            for (oy in -1..1) {
                for (ox in -1..1) {
                    val list = bins[binKey(bx + ox, by + oy)] ?: continue
                    for (j in list) {
                        if (j <= h) continue
                        val dist = hypot(east[j] - east[h], north[j] - north[h])
                        if (!HuntHeat.bridges(dist)) continue
                        if (j == h + 1 && HuntHeat.connects(dist)) continue
                        draw(
                            east[h], north[h], hears[h].second,
                            east[j], north[j], hears[j].second,
                        )
                    }
                }
            }
        }
    }
    eachStroke(::nearest)
    eachStroke(::paint)
    val pixels = IntArray(count)
    for (i in 0 until count) {
        val weight = sumW[i]
        if (weight < 0.02f) continue
        val fade = peak[i]
        if (fade < 0.04f) continue
        val a = (fade * 208f).toInt().coerceIn(0, 208)
        if (a == 0) continue
        val heatW = sumHw[i]
        val level = if (heatW > 1e-4f) sumHl[i] / heatW else 0f
        val argb = HuntHeat.colorArgb(level)
        val pr = ((argb ushr 16) and 0xFF) * a / 255
        val pg = ((argb ushr 8) and 0xFF) * a / 255
        val pb = (argb and 0xFF) * a / 255
        pixels[i] = (a shl 24) or (pr shl 16) or (pg shl 8) or pb
    }
    val bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, cols, 0, 0, cols, rows)
    val widthM = cols * cell
    val heightM = rows * cell
    val spot = HuntHeat.hotSpot(sumHw, sumHl, peak, cols)
    val hotLat = spot?.let { lat0 + (maxN - it.second * cell) / mLat }
    val hotLon = spot?.let { lon0 + (minE + it.first * cell) / mLon }
    return HuntHeatField(
        image = bitmap.asImageBitmap(),
        north = lat0 + maxN / mLat,
        west = lon0 + minE / mLon,
        south = lat0 + (maxN - heightM) / mLat,
        east = lon0 + (minE + widthM) / mLon,
        hotLat = hotLat,
        hotLon = hotLon,
    )
}

private const val MAX_HEAT_PX = 640
private const val CELL_M = 0.18
private const val STAMP_BUDGET = 900_000L

private fun moveHuntCamera(
    zoom: Float,
    pan: Offset,
    focus: Offset,
    layoutRef: MutableState<SitPathPlot.Layout?>,
    viewportRef: MutableState<SitPathPlot.Viewport?>,
    onViewportRef: MutableState<(SitPathPlot.Viewport?) -> Unit>,
    courseRef: MutableState<Double>,
) {
    val lay = layoutRef.value ?: return
    if ((zoom == 1f || zoom <= 0f) && pan == Offset.Zero) return
    var next = viewportRef.value ?: SitPathPlot.viewportOf(lay, courseRef.value)
    if (zoom > 0f && zoom != 1f && focus != Offset.Unspecified) {
        val (lat, lon) = lay.unproject(focus.x, focus.y)
        next = next.zoomAbout(zoom, lat, lon, minHalfM = HuntHeat.PINCH_HALF_M)
    }
    if (pan != Offset.Zero) {
        next = next.panScreen(pan.x, pan.y, lay.metersPerPx, courseRef.value)
    }
    viewportRef.value = next
    onViewportRef.value(next)
}
