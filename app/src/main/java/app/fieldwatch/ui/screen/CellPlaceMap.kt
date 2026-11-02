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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.data.PathTiles
import app.fieldwatch.domain.CellPlace
import app.fieldwatch.domain.CellWatch
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.MapBasemap
import app.fieldwatch.domain.MapKeyProblem
import app.fieldwatch.domain.MapOverlay
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.ui.component.MapCreditChip
import app.fieldwatch.ui.component.drawMapTiles
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Pin = Color(0xFF2563EB)
private const val PIN_R = 11f
private const val HERE_R = 15f
private const val PIN_RING = 3f
private const val PIN_HIT = 36.0

/** Wait until a pinch or drag settles before asking for a new set of pictures. */
private const val TILE_SETTLE_MS = 180L

/** Same gap the path map uses before a second tap counts as a double tap. */
private const val DOUBLE_TAP_MS = 280L

/**
 * One pin per place where this phone stood.
 * Saved pictures are drawn from the map cache as soon as they are read.
 * A missing picture is fetched only while this phone is online.
 * No saved picture, and no connection, leaves the map off.
 * The caller leaves it off when maps or privacy mode are off.
 */
@Composable
internal fun CellPlaceMap(
    places: List<CellPlace>,
    phoneLat: Double?,
    phoneLon: Double?,
    basemap: MapBasemap,
    mapKey: String,
    overlays: Set<MapOverlay>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPlaced: (LayoutCoordinates) -> Unit = {},
    onRemoved: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val removedRef = remember { mutableStateOf(onRemoved) }
    removedRef.value = onRemoved
    DisposableEffect(Unit) {
        onDispose { removedRef.value?.invoke() }
    }
    val shown = remember(places, phoneLat, phoneLon) { CellWatch.mapPlaces(places, phoneLat, phoneLon) }
    if (shown.isEmpty()) return
    val density = LocalDensity.current
    val hereInk = PhosphorActive.nightIf(LocalNightMode.current)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = with(density) { 200.dp.toPx() }
        if (widthPx < 40f) return@BoxWithConstraints
        var viewport by remember { mutableStateOf<SitPathPlot.Viewport?>(null) }
        val layout = SitPathPlot.layout(
            placeModel(shown),
            widthPx,
            heightPx,
            pad = 12f,
            scaleBarReserve = 8f,
            viewport = viewport,
        ) ?: return@BoxWithConstraints
        val window = layout.tileWindow() ?: return@BoxWithConstraints
        val windowKey = SitPathPlot.tileWindowKey(window)
        var tiles by remember { mutableStateOf<List<PathTiles.Tile>>(emptyList()) }
        val layoutRef = remember { mutableStateOf<SitPathPlot.Layout?>(null) }
        val shownRef = remember { mutableStateOf(shown) }
        val viewportRef = remember { mutableStateOf(viewport) }
        val onOpenRef = remember { mutableStateOf(onOpen) }
        layoutRef.value = layout
        shownRef.value = shown
        viewportRef.value = viewport
        onOpenRef.value = onOpen
        LaunchedEffect(windowKey, basemap, mapKey, overlays) {
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
            tiles = placeMapTiles(batch, tiles)
        }
        if (tiles.isEmpty()) return@BoxWithConstraints
        val uriHandler = LocalUriHandler.current
        val credit = tiles.first()
        val caution = credit.basemap.cautionKey?.let { LanguageText.text(it) }
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .onGloballyPositioned(onPlaced),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val slop = viewConfiguration.touchSlop
                        coroutineScope {
                        var lastTapMs = 0L
                        var openJob: Job? = null
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            down.consume()
                            var armed = false
                            var zoomAccum = 1f
                            var panAccum = Offset.Zero
                            var tapAt = down.position
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                event.changes.forEach { it.consume() }
                                tapAt = pressed.first().position
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
                                        val lay = layoutRef.value ?: continue
                                        viewportRef.value = movePlaceCamera(
                                            zoomAccum, panAccum, focus, lay, viewportRef.value,
                                        )
                                        viewport = viewportRef.value
                                    }
                                } else {
                                    val lay = layoutRef.value ?: continue
                                    viewportRef.value = movePlaceCamera(
                                        zoom, pan, focus, lay, viewportRef.value,
                                    )
                                    viewport = viewportRef.value
                                }
                            }
                            if (armed) {
                                openJob?.cancel()
                                openJob = null
                                lastTapMs = 0L
                            } else {
                                val now = down.uptimeMillis
                                if (now - lastTapMs <= DOUBLE_TAP_MS) {
                                    openJob?.cancel()
                                    openJob = null
                                    lastTapMs = 0L
                                    viewportRef.value = null
                                    viewport = null
                                } else {
                                    lastTapMs = now
                                    val lay = layoutRef.value
                                    val tap = tapAt
                                    val places = shownRef.value
                                    openJob?.cancel()
                                    openJob = launch {
                                        delay(DOUBLE_TAP_MS)
                                        if (lay == null) return@launch
                                        val id = hitPlace(places, lay, tap) ?: return@launch
                                        onOpenRef.value(id)
                                    }
                                }
                            }
                        }
                        }
                    },
            ) {
                drawMapTiles(tiles, layout.project, alpha = 1f)
                for (place in shown) {
                    val point = layout.project(place.lat, place.lon)
                    if (!layout.nearPicture(point.x, point.y)) continue
                    val here = standingHere(place, phoneLat, phoneLon)
                    val radius = if (here) HERE_R else PIN_R
                    drawCircle(Color.White, radius + PIN_RING, Offset(point.x, point.y))
                    drawCircle(if (here) hereInk else Pin, radius, Offset(point.x, point.y))
                }
            }
            MapCreditChip(
                lines = credit.basemap.shownCreditLines(caution, credit.overlays, false),
                link = credit.basemap.creditUrl,
                onOpen = { runCatching { uriHandler.openUri(it) } },
                color = MaterialTheme.colorScheme.onSurface,
                textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            )
        }
    }
}

private fun placeModel(places: List<CellPlace>): SitPathPlot.Model = SitPathPlot.Model(
    samples = emptyList(),
    dots = places.map { place ->
        SitPathPlot.Dot(
            key = place.id,
            lat = place.lat,
            lon = place.lon,
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

/**
 * A keyed failure leaves the map off.
 * A batch with pictures replaces what is on screen.
 * An empty batch, including a view too wide for this map, keeps the pictures already drawn.
 */
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

private fun hitPlace(shown: List<CellPlace>, layout: SitPathPlot.Layout, tap: Offset): String? {
    val hit = shown.minByOrNull { place ->
        val point = layout.project(place.lat, place.lon)
        hypot((point.x - tap.x).toDouble(), (point.y - tap.y).toDouble())
    } ?: return null
    val point = layout.project(hit.lat, hit.lon)
    return if (hypot((point.x - tap.x).toDouble(), (point.y - tap.y).toDouble()) <= PIN_HIT) hit.id else null
}

/** Same 300 m the place list uses for Here. No fix leaves every pin the ordinary color. */
private fun standingHere(place: CellPlace, phoneLat: Double?, phoneLon: Double?): Boolean {
    val lat = phoneLat ?: return false
    val lon = phoneLon ?: return false
    if (!lat.isFinite() || !lon.isFinite()) return false
    if (!place.lat.isFinite() || !place.lon.isFinite()) return false
    return Geo.meters(lat, lon, place.lat, place.lon) <= CellWatch.NEAR_M
}
