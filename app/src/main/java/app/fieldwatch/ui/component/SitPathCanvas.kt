package app.fieldwatch.ui.component

import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.MapCameraMarks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.data.PathTiles
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.MapFooter
import app.fieldwatch.domain.MapFrame
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.domain.cameraAt
import app.fieldwatch.domain.cameraBrand
import app.fieldwatch.domain.cameraCard
import app.fieldwatch.domain.cameraId
import app.fieldwatch.domain.cameraWinsTap
import app.fieldwatch.domain.camerasOnSpot
import app.fieldwatch.domain.cardAnchor
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

/** Lower-left map line. Holds the last reading while this phone wanders in place. */
@Composable
fun rememberMapFooter(
    mode: MapFooter,
    lat: Double?,
    lon: Double?,
    demo: Boolean,
): String? {
    val hold = remember { arrayOfNulls<Geo.MapFooterHold>(1) }
    val next = Geo.settleMapFooter(hold[0], mode, lat, lon, demo)
    hold[0] = next
    return next?.text
}

@Composable
fun SitPathCanvas(
    model: SitPathPlot.Model,
    modifier: Modifier = Modifier,
    tiles: List<PathTiles.Tile> = emptyList(),
    onOpenRadio: (String) -> Unit = {},
    /** Live path fills its parent. Reports keep the fixed picture. */
    expand: Boolean = false,
    viewport: SitPathPlot.Viewport? = null,
    /** Pinch and drag. Null keeps the picture fitted and ignores those gestures. */
    onViewport: ((SitPathPlot.Viewport?) -> Unit)? = null,
    /** Where this phone is now. Blank stays off. The path figure does not pass this. */
    position: String? = null,
    /** Degrees clockwise from north. NaN keeps this frame north-up. */
    courseDeg: Double = Double.NaN,
    /** [MapFrame]. Reports leave this at north-up and leave the button off. */
    mapFrame: Int = MapFrame.NORTH,
    /** Null hides the button and keeps the drawn N. */
    onRotate: (() -> Unit)? = null,
    /** Double-tap after the held view is cleared. Reports leave this null. */
    onToggleClose: (() -> Unit)? = null,
    /** Recenter control while a view is held. Reports fit again on a double-tap. */
    fitButton: Boolean = true,
    /** Blank picture because this map starts at a closer zoom. */
    zoomIn: Boolean = false,
    /** Live layer on Show live path and Reports → Path. */
    flock: Boolean = false,
    flockIndex: app.fieldwatch.domain.FlockIndex? = null,
    flockRevision: Int = 0,
    /**
     * On draws every camera in this picture. Off draws none.
     * Null [onCycleCameras] draws the live layer when [flock] is on.
     */
    cameraMode: MapCameraMarks = MapCameraMarks.VIEW,
    /**
     * Tap cycles the marks. Null leaves the glyph off, and aircraft cards leave it null.
     * The saved mode still draws when this is set.
     */
    onCycleCameras: (() -> Unit)? = null,
    /** Under the compass. True only when a camera list is on and loaded. */
    camerasReady: Boolean = false,
    /** South, north, west, east of this picture, including a finger-held view. */
    onTileWindow: ((DoubleArray) -> Unit)? = null,
    /**
     * Reports → Path. A numbered group is one spot. Zooming in draws the
     * others on their own. Show live path keeps a chain of nearby marks.
     */
    splitStacked: Boolean = false,
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
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val labelStyle = TextStyle(fontSize = 10.sp, color = muted)
    val gnssInk = GnssDiamond.nightIf(night)
    val flockCountStyle = TextStyle(fontSize = 9.sp, color = Color.Black.nightIf(night))
    val marksMode = if (onCycleCameras != null) {
        cameraMode
    } else if (flock && flockIndex != null) {
        MapCameraMarks.VIEW
    } else {
        MapCameraMarks.OFF
    }
    val flockGen = if (marksMode == MapCameraMarks.VIEW && flock && flockIndex != null) flockRevision else -1
    val sensorCredit = flockGen >= 0
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedGnss by remember { mutableStateOf<String?>(null) }
    var selectedCamera by remember { mutableStateOf<String?>(null) }
    var placedCard by remember { mutableStateOf<MapCardBox?>(null) }
    var boxInWindow by remember { mutableStateOf(Offset.Zero) }
    val appliedCourse = if (onRotate == null) {
        Double.NaN
    } else {
        MapFrame.drawnCourse(
            mapFrame,
            if (viewport == null) null else viewport.heldCourseDeg,
            courseDeg,
        )
    }
    val followHalfM = if (
        onRotate != null && MapFrame.zoomed(mapFrame) && viewport == null
    ) {
        MapFrame.CLOSE_HALF_M
    } else {
        0f
    }
    // Drawn angle turns through the gap between GPS courses. Tiles stay on the heading reached.
    val drawnCourse = rememberEasedCourse(appliedCourse)
    val camerasShown = onCycleCameras != null && camerasReady
    val layout = remember(model, boxSize, viewport, drawnCourse, followHalfM) {
        if (boxSize.width < 8 || boxSize.height < 8) null
        else SitPathPlot.layout(
            model,
            boxSize.width.toFloat(),
            boxSize.height.toFloat(),
            viewport = viewport,
            followHalfM = followHalfM,
            courseDeg = drawnCourse,
        )
    }
    val tileLayout = remember(model, boxSize, viewport, appliedCourse, followHalfM) {
        if (boxSize.width < 8 || boxSize.height < 8) null
        else SitPathPlot.layout(
            model,
            boxSize.width.toFloat(),
            boxSize.height.toFloat(),
            viewport = viewport,
            followHalfM = followHalfM,
            courseDeg = appliedCourse,
        )
    }
    val onTileWindowRef = remember { mutableStateOf(onTileWindow) }
    onTileWindowRef.value = onTileWindow
    LaunchedEffect(tileLayout) {
        val window = tileLayout?.tileWindow() ?: return@LaunchedEffect
        onTileWindowRef.value?.invoke(window)
    }
    val cameraIndex = if (flockGen >= 0) flockIndex else null
    val cameraMarks = remember(layout, cameraIndex, flockGen) {
        val lay = layout
        val index = cameraIndex
        if (lay == null || index == null) emptyList() else flockMarksOn(lay, index)
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
            SitPathPlot.clusters(lay.dots, chain = !splitStacked, numberOf = numberOf)
        }
    }
    val selected = clusters.firstOrNull { it.id == selectedId }
    val markers = remember(layout, model.live, boxSize.width) {
        pathMarkers(layout, model.live, boxSize.width.toFloat(), measurer, labelStyle)
    }
    val layoutRef = remember { mutableStateOf(layout) }
    val viewportRef = remember { mutableStateOf(viewport) }
    val onViewportRef = remember { mutableStateOf(onViewport) }
    val courseRef = remember { mutableStateOf(Double.NaN) }
    val onToggleRef = remember { mutableStateOf(onToggleClose) }
    val tapRef = remember { mutableStateOf<(Offset) -> Unit>({ }) }
    val lastTapMs = remember { mutableLongStateOf(0L) }
    layoutRef.value = layout
    viewportRef.value = viewport
    onViewportRef.value = onViewport
    courseRef.value = drawnCourse
    onToggleRef.value = onToggleClose
    val showRecenter = fitButton && viewport != null && onViewport != null
    val compass = onRotate != null
    val compassLift = 56.dp
    fun cardTop(onRight: Boolean): Dp {
        val chrome = if (
            (onRight && compass) ||
            (onRight && showRecenter && !compass) ||
            (!onRight && showRecenter && compass)
        ) {
            compassLift
        } else {
            8.dp
        }
        return if (onRight && camerasShown) maxOf(chrome, cameraCardClearDp(compass).dp) else chrome
    }
    fun mapCard(key: String, minWidth: Dp = 0.dp): Modifier {
        return Modifier
            .widthIn(min = minWidth, max = 248.dp)
            .onGloballyPositioned { coords ->
                val origin = coords.positionInWindow() - boxInWindow
                val w = coords.size.width.toFloat()
                val h = coords.size.height.toFloat()
                placedCard = MapCardBox(key, origin.x, origin.y, origin.x + w, origin.y + h)
            }
            .background(surface, RoundedCornerShape(8.dp))
            .border(1.dp, outline, RoundedCornerShape(8.dp))
            .padding(8.dp)
    }
    fun handleTap(pos: Offset) {
        val lay = layout
        val gnssHit = if (lay == null) emptyList() else gnssAt(model.gnss, lay, pos.x, pos.y)
        val radio = SitPathPlot.clusterAt(clusters, pos.x, pos.y, markers)
        val cam = if (lay == null) {
            null
        } else {
            cameraAt(cameraMarks, pos.x, pos.y) { lat, lon ->
                val pt = lay.project(lat, lon)
                pt.x to pt.y
            }
        }
        val gnssD = gnssHit.minOfOrNull { pin ->
            val pt = lay!!.project(pin.lat, pin.lon)
            hypot(pt.x - pos.x, pt.y - pos.y)
        }
        val radioD = radio?.let { hypot(it.center.x - pos.x, it.center.y - pos.y) }
        val camD = if (cam == null || lay == null) {
            null
        } else {
            val pt = lay.project(cam.lat, cam.lon)
            hypot(pt.x - pos.x, pt.y - pos.y)
        }
        if (cam != null && camD != null && cameraWinsTap(camD, gnssD, radioD)) {
            val id = cameraId(cam)
            val open = selectedCamera
            val anchor = if (open == null) null else cameraMarks.firstOrNull { cameraId(it) == open }
            val openIds = if (anchor == null) {
                emptySet()
            } else {
                camerasOnSpot(cameraMarks, anchor) { lat, lon ->
                    val pt = lay!!.project(lat, lon)
                    pt.x to pt.y
                }.map { cameraId(it) }.toSet()
            }
            selectedCamera = if (id == open || id in openIds) null else id
            selectedGnss = null
            selectedId = null
            return
        }
        selectedCamera = null
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
    tapRef.value = { handleTap(it) }
    val frame = if (expand) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(240.dp)
    val gestures = if (onViewport == null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { tapRef.value(it) }
        }
    } else {
        Modifier.pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var armed = false
                var zoomAccum = 1f
                var panAccum = Offset.Zero
                var tapAt = down.position
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
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
                            moveLiveCamera(
                                zoomAccum, panAccum, focus, layoutRef, viewportRef, onViewportRef, courseRef,
                            )
                            event.changes.forEach { it.consume() }
                        }
                    } else {
                        moveLiveCamera(zoom, pan, focus, layoutRef, viewportRef, onViewportRef, courseRef)
                        event.changes.forEach { it.consume() }
                    }
                }
                if (!armed) {
                    val now = down.uptimeMillis
                    if (now - lastTapMs.longValue <= 280L) {
                        lastTapMs.longValue = 0L
                        viewportRef.value = null
                        onViewportRef.value?.invoke(null)
                        onToggleRef.value?.invoke()
                    } else {
                        lastTapMs.longValue = now
                        tapRef.value(tapAt)
                    }
                }
            }
        }
    }
    Box(
        modifier
            .then(frame)
            .onSizeChanged { boxSize = it }
            .onGloballyPositioned { boxInWindow = it.positionInWindow() },
    ) {
        Canvas(
            Modifier
                .matchParentSize()
                .clipToBounds()
                .then(gestures),
        ) {
            val lay = layout ?: return@Canvas
            fun onMap(x: Float, y: Float) = lay.onPicture(x, y)
            fun labelAt(x: Float, y: Float, textW: Int, textH: Int, dx: Float, dy: Float) = Offset(
                (x + dx).coerceIn(lay.pictureLeft, (lay.pictureRight - textW).coerceAtLeast(lay.pictureLeft)),
                (y + dy).coerceIn(lay.pictureTop, (lay.pictureBottom - textH).coerceAtLeast(lay.pictureTop)),
            )
            clipRect(lay.plotLeft, lay.plotTop, lay.plotRight, lay.plotBottom) {
                drawMapTiles(tiles, lay.project, alpha = 0.55f)
            }
            clipRect(lay.pictureLeft, lay.pictureTop, lay.pictureRight, lay.pictureBottom) {
                if (lay.path.size >= 2) {
                    strokeOnMap(
                        SitPathPlot.clipPolyline(
                            lay.path, lay.pictureLeft, lay.pictureTop, lay.pictureRight, lay.pictureBottom,
                        ),
                        RouteBlue,
                        ROUTE_PX,
                    )
                    val trace = if (lay.samples.size >= 2) lay.samples else model.samples
                    val stays = Geo.realStops(trace)
                    for (i in 1 until lay.path.size) {
                        val from = trace.getOrNull(i - 1)?.at ?: continue
                        val to = trace.getOrNull(i)?.at ?: continue
                        if (!Geo.stopCovers(stays, from, to)) continue
                        val seg = SitPathPlot.clipSegment(
                            lay.path[i - 1], lay.path[i],
                            lay.pictureLeft, lay.pictureTop, lay.pictureRight, lay.pictureBottom,
                        ) ?: continue
                        drawLine(
                            PhosphorActive.copy(alpha = 0.9f),
                            Offset(seg.first.x, seg.first.y),
                            Offset(seg.second.x, seg.second.y),
                            strokeWidth = ROUTE_PX,
                            cap = StrokeCap.Round,
                        )
                    }
                    stays.forEach { stay ->
                        val pt = lay.project(stay.lat, stay.lon)
                        if (!lay.nearPicture(pt.x, pt.y)) return@forEach
                        drawCircle(PhosphorActive.copy(alpha = 0.22f), radius = 16f, center = Offset(pt.x, pt.y))
                    }
                    val dur = (trace.last().at - trace.first().at).coerceAtLeast(1L)
                    listOf(0.25, 0.5, 0.75).forEach { frac ->
                        val want = trace.first().at + (dur * frac).toLong()
                        val idx = trace.indices.minByOrNull { abs(trace[it].at - want) } ?: return@forEach
                        if (idx == 0 || idx == trace.lastIndex) return@forEach
                        val pt = lay.path.getOrNull(idx) ?: return@forEach
                        if (!onMap(pt.x, pt.y)) return@forEach
                        val label = TIME_FMT.format(Date(trace[idx].at))
                        val measured = measurer.measure(label, labelStyle)
                        drawCircle(muted, radius = 3f, center = Offset(pt.x, pt.y))
                        drawText(
                            measured,
                            topLeft = labelAt(
                                pt.x, pt.y, measured.size.width, measured.size.height, 6f, -measured.size.height - 2f,
                            ),
                        )
                    }
                    val start = lay.path.first()
                    val end = lay.path.last()
                    if (onMap(start.x, start.y)) {
                        drawStartDot(start.x, start.y)
                        val startT = measurer.measure(LanguageText.text("ui.start.label"), labelStyle)
                        drawText(
                            startT,
                            topLeft = labelAt(start.x, start.y, startT.size.width, startT.size.height, 8f, -6f),
                        )
                    }
                    if (onMap(end.x, end.y)) {
                        val endLabel = if (model.live) LanguageText.text("ui.now.label") else LanguageText.text("ui.end.label")
                        val endT = measurer.measure(endLabel, labelStyle)
                        drawText(
                            endT,
                            topLeft = labelAt(end.x, end.y, endT.size.width, endT.size.height, 10f, -endT.size.height - 4f),
                        )
                    }
                }
                drawAdvertised(lay, model.craft, model.pilots, model.dots, pilotPainter)
            }
            if (cameraMarks.isNotEmpty() && cameraIndex != null) {
                clipRect(lay.plotLeft, lay.plotTop, lay.plotRight, lay.plotBottom) {
                    drawFlockMarks(
                        lay,
                        cameraIndex,
                        night,
                        measurer,
                        flockCountStyle,
                        bold = onCycleCameras != null || expand,
                        prepared = cameraMarks,
                    )
                }
            }
            clipRect(lay.pictureLeft, lay.pictureTop, lay.pictureRight, lay.pictureBottom) {
                clusters.forEach { cluster ->
                    if (!lay.nearPicture(cluster.center.x, cluster.center.y)) return@forEach
                    val pt = Offset(cluster.center.x, cluster.center.y)
                    if (expand) {
                        drawCircle(Color.White, 17f, pt)
                        drawCircle(Color(0xFF0F172A), 17f, pt, style = Stroke(width = 2.2f))
                    }
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
                    if (lay.nearPicture(end.x, end.y)) drawYouDot(end.x, end.y, 6.2f, you)
                }
                model.gnss.forEach { pin ->
                    val pt = lay.project(pin.lat, pin.lon)
                    if (!lay.nearPicture(pt.x, pt.y)) return@forEach
                    drawGnssDiamond(Offset(pt.x, pt.y), gnssInk)
                }
            }
            val camPick = if (selected == null && selectedGnss == null) {
                cameraMarks.firstOrNull { cameraId(it) == selectedCamera }
            } else {
                null
            }
            val leader = if (selected != null && onMap(selected.center.x, selected.center.y)) {
                Offset(selected.center.x, selected.center.y)
            } else if (camPick != null) {
                val pt = lay.project(camPick.lat, camPick.lon)
                if (onMap(pt.x, pt.y)) Offset(pt.x, pt.y) else null
            } else {
                null
            }
            val cardKey = when {
                selected != null -> "r:${selected.id}"
                selectedGnss != null -> "g:$selectedGnss"
                camPick != null -> "c:${cameraId(camPick)}"
                else -> null
            }
            val box = placedCard
            if (leader != null && box != null && cardKey != null && box.key == cardKey) {
                val (ex, ey) = cardAnchor(leader.x, leader.y, box.left, box.top, box.right, box.bottom)
                drawLine(outline, leader, Offset(ex, ey), strokeWidth = 2f)
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
            if (onRotate == null) {
                val n = measurer.measure("N", labelStyle)
                drawText(n, topLeft = Offset(size.width - n.size.width - 10f, 8f))
            }
        }
        val mapTile = tiles.firstOrNull()
        val layForCredit = layout
        if (layForCredit != null && boxSize.height > 0 && (mapTile != null || sensorCredit)) {
            val mapCredit = mapTile?.basemap
            val bottomPad = with(density) { (boxSize.height - layForCredit.plotBottom).toDp() + 2.dp }
            val endPad = with(density) { (boxSize.width - layForCredit.plotRight).toDp() + 2.dp }
            val half = with(density) {
                ((layForCredit.plotRight - layForCredit.plotLeft) / 2f).toDp() - 4.dp
            }.coerceAtLeast(0.dp)
            val lines = if (mapCredit != null) {
                mapCredit.shownCreditLines(
                    mapCredit.cautionKey?.let { LanguageText.text(it) },
                    mapTile.overlays,
                    sensorCredit,
                )
            } else {
                listOf(app.fieldwatch.domain.FlockCredit.LINE)
            }
            val link = mapCredit?.creditUrl ?: if (sensorCredit) app.fieldwatch.domain.FlockCredit.URL else null
            MapCreditChip(
                lines = lines,
                link = link,
                onOpen = { runCatching { uriHandler.openUri(it) } },
                color = track,
                textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                maxWidth = half,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = endPad, bottom = bottomPad),
            )
        }
        if (zoomIn && tiles.isEmpty()) {
            Text(
                LanguageText.text("map.zoom.in"),
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
        val here = position
        if (!here.isNullOrBlank() && layForCredit != null && boxSize.height > 0) {
            val bottomPad = with(density) { (boxSize.height - layForCredit.plotBottom).toDp() + 2.dp }
            val startPad = with(density) { layForCredit.plotLeft.toDp() + 2.dp }
            val half = with(density) {
                ((layForCredit.plotRight - layForCredit.plotLeft) / 2f).toDp() - 4.dp
            }.coerceAtLeast(0.dp)
            Text(
                here,
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
        val gnssCard = model.gnss.filter { gnssId(it) in selectedGnss.orEmpty().split("|") }
        if (gnssCard.isNotEmpty()) {
            val anchor = layout?.project(gnssCard.first().lat, gnssCard.first().lon)
            val onRight = (anchor?.x ?: 0f) < (boxSize.width / 2f)
            Column(
                Modifier
                    .align(if (onRight) Alignment.TopEnd else Alignment.TopStart)
                    .padding(start = 8.dp, top = cardTop(onRight), end = 8.dp, bottom = 8.dp)
                    .then(mapCard("g:$selectedGnss")),
            ) {
                Text(
                    if (gnssCard.size == 1) LanguageText.text("ui.gnss.detection") else "${gnssCard.size} GNSS detections",
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
                        pin.detail.trim().ifBlank { LanguageText.text("ui.signal.changed") },
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
                    .padding(start = 8.dp, top = cardTop(onRight), end = 8.dp, bottom = 8.dp)
                    .then(mapCard("r:${selected.id}")),
            ) {
                Text(
                    if (model.live) {
                        LanguageText.text(
                            "ui.radios.here",
                            mapOf("n" to selected.members.size.toString()),
                        )
                    } else if (selected.members.size == 1) {
                        LanguageText.text("ui.1.alert.here")
                    } else {
                        "${selected.members.size} alerts here"
                    },
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
        } else {
            val picked = cameraMarks.firstOrNull { cameraId(it) == selectedCamera }
            val index = cameraIndex
            val lay = layout
            if (picked != null && index != null && lay != null) {
                val group = camerasOnSpot(cameraMarks, picked) { lat, lon ->
                    val pt = lay.project(lat, lon)
                    pt.x to pt.y
                }
                val anchor = lay.project(picked.lat, picked.lon)
                val onRight = anchor.x < (boxSize.width / 2f)
                Column(
                    Modifier
                        .align(if (onRight) Alignment.TopEnd else Alignment.TopStart)
                        .padding(start = 8.dp, top = cardTop(onRight), end = 8.dp, bottom = 8.dp)
                        .then(mapCard("c:${cameraId(picked)}", minWidth = 168.dp)),
                ) {
                    group.forEachIndexed { i, mark ->
                        if (i > 0) Spacer(Modifier.height(8.dp))
                        val card = cameraCard(mark, cameraBrand(index, mark))
                        val kind = LanguageText.text(card.kindKey)
                        Text(kind, style = MaterialTheme.typography.labelSmall, color = muted)
                        if (card.count > 1) {
                            Text(
                                card.count.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = track,
                            )
                        }
                        if (card.brand.isNotBlank() && !card.brand.equals(kind, ignoreCase = true)) {
                            Text(
                                card.brand,
                                style = MaterialTheme.typography.bodySmall,
                                color = track,
                                maxLines = 2,
                            )
                        }
                        if (card.facing.isNotBlank()) {
                            Text(
                                LanguageText.text("ui.map.camera.facing", mapOf("dir" to card.facing)),
                                style = MaterialTheme.typography.bodySmall,
                                color = track,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
        if (showRecenter) {
            TextButton(
                onClick = { onViewport?.invoke(null) },
                modifier = Modifier
                    .align(if (compass) Alignment.TopStart else Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Text(LanguageText.text("display.recenter"))
            }
        }
        if (onRotate != null) {
            MapCompassButton(
                frame = mapFrame,
                northDeg = drawnCourse,
                onClick = onRotate,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }
        val cycle = onCycleCameras
        if (cycle != null && camerasShown && boxSize.width > 8) {
            val placed = cameraUnderCompass(
                boxSize.width.toFloat(),
                with(density) { 1.dp.toPx() },
                compass,
            )
            MapCameraButton(
                mode = cameraMode,
                onClick = cycle,
                modifier = Modifier.offset { IntOffset(placed.left.toInt(), placed.top.toInt()) },
            )
        }
    }
}

/** Matches MapCompassButton: 4 dp from the corner, 48 dp plate. */
internal const val COMPASS_PAD_DP = 4f
internal const val COMPASS_SIZE_DP = 48f

/** Matches MapCameraButton. */
internal const val CAMERA_BUTTON_DP = 36f

/** Space between the compass and the camera control. */
internal const val CAMERA_UNDER_GAP_DP = 8f

/** Right-side cards start below the camera control. */
internal fun cameraCardClearDp(compassShown: Boolean): Float {
    val top = if (compassShown) {
        COMPASS_PAD_DP + COMPASS_SIZE_DP + CAMERA_UNDER_GAP_DP
    } else {
        COMPASS_PAD_DP
    }
    return top + CAMERA_BUTTON_DP + 8f
}

internal class CameraControlBox(val left: Float, val top: Float, val size: Float)

/**
 * Right edge lines up with the compass. With the compass showing, the control sits
 * [CAMERA_UNDER_GAP_DP] below it. Without one, it takes that top corner.
 */
internal fun cameraUnderCompass(
    widthPx: Float,
    pxPerDp: Float,
    compassShown: Boolean,
): CameraControlBox {
    val pad = COMPASS_PAD_DP * pxPerDp
    val compass = COMPASS_SIZE_DP * pxPerDp
    val button = CAMERA_BUTTON_DP * pxPerDp
    val gap = CAMERA_UNDER_GAP_DP * pxPerDp
    val left = widthPx - pad - button
    val top = if (compassShown) pad + compass + gap else pad
    return CameraControlBox(left, top, button)
}

private fun moveLiveCamera(
    zoom: Float,
    pan: Offset,
    focus: Offset,
    layoutRef: MutableState<SitPathPlot.Layout?>,
    viewportRef: MutableState<SitPathPlot.Viewport?>,
    onViewportRef: MutableState<((SitPathPlot.Viewport?) -> Unit)?>,
    courseRef: MutableState<Double>,
) {
    val lay = layoutRef.value ?: return
    if ((zoom == 1f || zoom <= 0f) && pan == Offset.Zero) return
    var next = viewportRef.value ?: SitPathPlot.viewportOf(lay, courseRef.value)
    if (zoom > 0f && zoom != 1f && focus != Offset.Unspecified) {
        val (lat, lon) = lay.unproject(focus.x, focus.y)
        next = next.zoomAbout(zoom, lat, lon)
    }
    if (pan != Offset.Zero) {
        next = next.panScreen(pan.x, pan.y, lay.metersPerPx, courseRef.value)
    }
    viewportRef.value = next
    onViewportRef.value?.invoke(next)
}

private data class MapCardBox(
    val key: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal val AircraftAmber = Color(0xFFC47A00)

/** Route blue, a step darker than the old drive line. Flock marks stay their own blue. */
private val RouteBlue = Color(0xFF3E78F0)
private const val ROUTE_PX = 10f

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
    val startText = measurer.measure(LanguageText.text("ui.start.label"), labelStyle)
    val endText = measurer.measure(if (live) LanguageText.text("ui.now.label") else LanguageText.text("ui.end.label"), labelStyle)
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
            strokeOnMap(
                SitPathPlot.clipPolyline(
                    pts, lay.pictureLeft, lay.pictureTop, lay.pictureRight, lay.pictureBottom,
                ),
                AircraftInk,
                4.2f,
                AircraftDots,
            )
            val start = pts.first()
            if (lay.nearPicture(start.x, start.y)) {
                drawCircle(Color.White, radius = 4.6f, center = Offset(start.x, start.y))
                drawCircle(
                    AircraftInk,
                    radius = 4.6f,
                    center = Offset(start.x, start.y),
                    style = Stroke(width = 1.8f),
                )
            }
        }
        val end = pts.last()
        val covered = dots.any { SitPathPlot.sitsOnCraft(it, listOf(track)) }
        if (!covered && lay.nearPicture(end.x, end.y)) {
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
        if (!lay.nearPicture(pt.x, pt.y)) return@forEach
        drawPilotMark(Offset(pt.x, pt.y), pilotPainter)
    }
}

private fun DrawScope.strokeOnMap(
    runs: List<List<SitPathPlot.Pt>>,
    color: Color,
    width: Float,
    effect: PathEffect? = null,
) {
    val style = Stroke(
        width = width,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = effect,
    )
    for (run in runs) {
        if (run.size < 2) continue
        val path = Path().apply {
            moveTo(run[0].x, run[0].y)
            for (i in 1 until run.size) lineTo(run[i].x, run[i].y)
        }
        drawPath(path, color, style = style)
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
