package app.fieldwatch.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.data.PathTiles
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.MapBasemap
import app.fieldwatch.domain.MapKeyProblem
import app.fieldwatch.domain.MapOverlay
import app.fieldwatch.domain.PlaceReport
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.ui.component.MapCreditChip
import app.fieldwatch.ui.component.drawMapTiles
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val PIN_R = 12f
private const val PIN_RING = 2.5f
private const val PIN_GAP = 18.0

/** A picture wider than this opens on the newest stop, where the street tiles still load. */
private const val WIDE_M = 40_000.0

private const val TILE_SETTLE_MS = 180L
private const val DOUBLE_TAP_MS = 280L
private val MapHeight = 280.dp
private val MapShape = RoundedCornerShape(12.dp)

/**
 * Where this phone was when it heard the radios on the places list.
 * One color per radio. Pinch, drag, and a double-tap match the other maps.
 * Saved pictures are drawn from the map cache as soon as they are read.
 * No saved picture, and no connection, leaves the map off. The list stays.
 */
@Composable
internal fun PlaceStopsMap(
    pins: List<PlaceReport.Pin>,
    focusLat: Double,
    focusLon: Double,
    basemap: MapBasemap,
    mapKey: String,
    overlays: Set<MapOverlay>,
    modifier: Modifier = Modifier,
) {
    if (pins.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp)
    val ink = MaterialTheme.colorScheme.onSurface
    val backing = MaterialTheme.colorScheme.surface
    val pinKey = remember(pins) { pins.joinToString(";") { "${it.lat},${it.lon},${it.color}" } }
    val spanM = remember(pinKey) { pinSpanM(pins) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = with(density) { MapHeight.toPx() }
        if (widthPx < 40f) return@BoxWithConstraints
        var viewport by remember(pinKey) { mutableStateOf<SitPathPlot.Viewport?>(null) }
        var seeded by remember(pinKey) { mutableStateOf(false) }
        if (!seeded) {
            seeded = true
            if (spanM > WIDE_M) viewport = closeViewport(focusLat, focusLon, widthPx, heightPx)
        }
        val layout = SitPathPlot.layout(
            pinModel(pins),
            widthPx,
            heightPx,
            pad = 16f,
            scaleBarReserve = 36f,
            viewport = viewport,
        ) ?: return@BoxWithConstraints
        val window = layout.tileWindow() ?: return@BoxWithConstraints
        val windowKey = SitPathPlot.tileWindowKey(window)
        var tiles by remember(pinKey) { mutableStateOf<List<PathTiles.Tile>>(emptyList()) }
        val layoutRef = remember { mutableStateOf(layout) }
        val viewportRef = remember { mutableStateOf(viewport) }
        layoutRef.value = layout
        viewportRef.value = viewport
        LaunchedEffect(windowKey, basemap, mapKey, overlays, pinKey) {
            if (viewport != null) delay(TILE_SETTLE_MS)
            val batch = PathTiles.loadBounds(
                context,
                window[0],
                window[1],
                window[2],
                window[3],
                onlineLookup = true,
                basemap = basemap,
                mapKey = mapKey.ifBlank { null },
                overlays = overlays,
                onTile = { tile ->
                    withContext(Dispatchers.Main.immediate) {
                        tiles = tiles.filterNot { samePicture(it, tile) } + tile
                    }
                },
            )
            if (batch.tiles.isEmpty() &&
                batch.problem == MapKeyProblem.NONE &&
                viewportRef.value == null &&
                spanM > WIDE_M
            ) {
                viewport = closeViewport(focusLat, focusLon, widthPx, heightPx)
            } else {
                tiles = placeMapTiles(batch, tiles)
            }
        }
        val placed = spreadPins(layout, pins)
        if (tiles.isEmpty()) return@BoxWithConstraints
        val uriHandler = LocalUriHandler.current
        val credit = tiles.first()
        val caution = credit.basemap.cautionKey?.let { LanguageText.text(it) }
        Surface(
            modifier = Modifier.fillMaxWidth().height(MapHeight),
            shape = MapShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Box(Modifier.fillMaxSize().clip(MapShape)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(pinKey) {
                            val slop = viewConfiguration.touchSlop
                            coroutineScope {
                                var lastTapMs = 0L
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    down.consume()
                                    var armed = false
                                    var zoomAccum = 1f
                                    var panAccum = Offset.Zero
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        event.changes.forEach { it.consume() }
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        val focus = event.calculateCentroid(useCurrent = true)
                                        if (!armed) {
                                            zoomAccum *= zoom
                                            panAccum += pan
                                            val past = pressed.size >= 2 ||
                                                abs(1f - zoomAccum) > 0.08f ||
                                                panAccum.getDistance() > slop
                                            if (past) {
                                                armed = true
                                                val lay = layoutRef.value
                                                viewportRef.value = movePlaceCamera(
                                                    zoomAccum, panAccum, focus, lay, viewportRef.value,
                                                )
                                                viewport = viewportRef.value
                                            }
                                        } else {
                                            val lay = layoutRef.value
                                            viewportRef.value = movePlaceCamera(
                                                zoom, pan, focus, lay, viewportRef.value,
                                            )
                                            viewport = viewportRef.value
                                        }
                                    }
                                    if (armed) {
                                        lastTapMs = 0L
                                    } else {
                                        val now = down.uptimeMillis
                                        if (now - lastTapMs <= DOUBLE_TAP_MS) {
                                            lastTapMs = 0L
                                            val next = if (spanM > WIDE_M) {
                                                closeViewport(focusLat, focusLon, widthPx, heightPx)
                                            } else {
                                                null
                                            }
                                            viewportRef.value = next
                                            viewport = next
                                        } else {
                                            lastTapMs = now
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    drawMapTiles(tiles, layout.project, alpha = 1f)
                    pins.forEachIndexed { index, pin ->
                        val at = placed.getOrNull(index) ?: return@forEachIndexed
                        if (!layout.nearPicture(at.x, at.y)) return@forEachIndexed
                        val color = Color(pin.color)
                        drawCircle(Color.Black.copy(alpha = 0.28f), PIN_R + 1.5f, Offset(at.x + 0.6f, at.y + 1.4f))
                        drawCircle(Color.White, PIN_R + PIN_RING, Offset(at.x, at.y))
                        drawCircle(color, PIN_R, Offset(at.x, at.y))
                    }
                    val barW = ((size.width - 96f) * layout.scaleBarFrac).coerceAtLeast(28f)
                    val barLabel = if (layout.scaleBarM >= 1000) {
                        LanguageText.text("pdf.km", mapOf("n" to "${(layout.scaleBarM / 1000).toInt()}"))
                    } else {
                        LanguageText.text("pdf.m", mapOf("n" to "${layout.scaleBarM.toInt()}"))
                    }
                    val measured = measurer.measure(barLabel, labelStyle.copy(color = ink))
                    val groupW = barW + 8f + measured.size.width
                    val barX = 12f
                    val barY = size.height - 16f
                    drawRoundRect(
                        color = backing.copy(alpha = 0.88f),
                        topLeft = Offset(barX - 6f, barY - measured.size.height / 2f - 3f),
                        size = Size(groupW + 12f, measured.size.height + 6f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                    drawLine(ink, Offset(barX, barY), Offset(barX + barW, barY), strokeWidth = 3f)
                    drawLine(ink, Offset(barX, barY - 5f), Offset(barX, barY + 5f), strokeWidth = 3f)
                    drawLine(ink, Offset(barX + barW, barY - 5f), Offset(barX + barW, barY + 5f), strokeWidth = 3f)
                    drawText(measured, topLeft = Offset(barX + barW + 8f, barY - measured.size.height / 2f))
                    val north = measurer.measure("N", labelStyle.copy(color = ink))
                    drawRoundRect(
                        color = backing.copy(alpha = 0.88f),
                        topLeft = Offset(size.width - north.size.width - 16f, 6f),
                        size = Size(north.size.width + 10f, north.size.height + 4f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                    drawText(north, topLeft = Offset(size.width - north.size.width - 11f, 8f))
                }
                MapCreditChip(
                    lines = credit.basemap.shownCreditLines(caution, credit.overlays, false),
                    link = credit.basemap.creditUrl,
                    onOpen = { runCatching { uriHandler.openUri(it) } },
                    color = ink,
                    textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 6.dp),
                )
            }
        }
    }
}

private fun pinModel(pins: List<PlaceReport.Pin>): SitPathPlot.Model = SitPathPlot.Model(
    samples = emptyList(),
    dots = pins.mapIndexed { index, pin ->
        SitPathPlot.Dot(
            key = "$index",
            lat = pin.lat,
            lon = pin.lon,
            label = "",
            extraAttention = false,
            named = false,
        )
    },
    lengthM = 0.0,
    spanM = 0.0,
    title = "",
    minHalfSpanM = SitPathPlot.MIN_HALF_SPAN_M,
)

private fun pinSpanM(pins: List<PlaceReport.Pin>): Double {
    if (pins.size < 2) return 0.0
    val south = pins.minOf { it.lat }
    val north = pins.maxOf { it.lat }
    val west = pins.minOf { it.lon }
    val east = pins.maxOf { it.lon }
    return Geo.meters(south, west, north, east)
}

/** Street-scale picture on the newest stop. The short side matches a sit that has not moved. */
private fun closeViewport(lat: Double, lon: Double, widthPx: Float, heightPx: Float): SitPathPlot.Viewport {
    val half = SitPathPlot.MIN_HALF_SPAN_M * SitPathPlot.FIT_PAD
    val aspect = (widthPx / heightPx).coerceIn(0.4f, 2.5f)
    val halfEast = if (aspect >= 1f) half * aspect else half
    val halfNorth = if (aspect >= 1f) half else half / aspect
    return SitPathPlot.Viewport(lat, lon, halfEast, halfNorth)
}

private fun placeMapTiles(batch: PathTiles.Batch, shown: List<PathTiles.Tile>): List<PathTiles.Tile> = when {
    batch.problem != MapKeyProblem.NONE -> emptyList()
    batch.tiles.isNotEmpty() -> batch.tiles
    else -> shown
}

private fun samePicture(a: PathTiles.Tile, b: PathTiles.Tile): Boolean =
    a.basemap == b.basemap &&
        abs(a.west - b.west) < 1e-7 &&
        abs(a.north - b.north) < 1e-7 &&
        abs(a.east - b.east) < 1e-7

private fun movePlaceCamera(
    zoom: Float,
    pan: Offset,
    focus: Offset,
    layout: SitPathPlot.Layout,
    viewport: SitPathPlot.Viewport?,
): SitPathPlot.Viewport? {
    if ((zoom == 1f || zoom <= 0f) && pan == Offset.Zero) return viewport
    var next = viewport ?: SitPathPlot.viewportOf(layout)
    if (zoom > 0f && zoom != 1f && focus != Offset.Unspecified) {
        val (lat, lon) = layout.unproject(focus.x, focus.y)
        next = next.zoomAbout(zoom, lat, lon)
    }
    if (pan != Offset.Zero) {
        next = next.panScreen(pan.x, pan.y, layout.metersPerPx, Double.NaN)
    }
    return next
}

/** Later pins step aside when two stops land on the same pixels, so both colors stay visible. */
private fun spreadPins(layout: SitPathPlot.Layout, pins: List<PlaceReport.Pin>): List<Offset> {
    val out = ArrayList<Offset>(pins.size)
    for (pin in pins) {
        val point = layout.project(pin.lat, pin.lon)
        var x = point.x
        var y = point.y
        var step = 0
        while (step < 8 && out.any { hypot((it.x - x).toDouble(), (it.y - y).toDouble()) < PIN_GAP }) {
            val ang = step * 0.9
            val reach = 16f + step * 3f
            x = point.x + cos(ang).toFloat() * reach
            y = point.y + sin(ang).toFloat() * reach
            step++
        }
        out += Offset(x, y)
    }
    return out
}
