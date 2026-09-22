package app.fieldwatch.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.MapPlot
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.component.FieldwatchActionButton

/**
 * Offline scatter map: each dot is where THIS PHONE was when it heard the
 * radio, not the radio's fix. Equirectangular meter grid, pinch-zoom + pan,
 * no network tiles.
 */
@Composable
fun MapScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    demoMode: Boolean,
    onOpen: (String) -> Unit,
) {
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar("Map") },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            MapCanvas(
                state = state,
                vm = vm,
                demoMode = demoMode,
                onOpen = onOpen,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MapCanvas(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    demoMode: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val warn = MaterialTheme.colorScheme.error
    val pathInk = MaterialTheme.colorScheme.tertiary

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var pickedKey by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val radios = state.devices.filter { it.latitude != null && it.longitude != null }
        val path = remember(state.devices) { vm.operatorPath() }
        val refLat = remember(radios, path) {
            val lats = radios.mapNotNull { it.latitude } + path.map { it.lat }
            if (lats.isEmpty()) 0.0 else lats.average()
        }
        val view = remember(radios, path, w, h) {
            val pts = radios.map { MapPlot.toMeters(it.latitude!!, it.longitude!!, refLat) } +
                path.map { MapPlot.toMeters(it.lat, it.lon, refLat) } +
                radios.flatMap { d ->
                    d.gpsTrail.map { MapPlot.toMeters(it.lat, it.lon, refLat) }
                }
            MapPlot.fit(pts, w, h)
        }
        fun proj(lat: Double, lon: Double): Offset {
            val p = MapPlot.toMeters(lat, lon, refLat)
            val (x, y) = MapPlot.project(p, view!!, w, h, zoom, pan.x, pan.y)
            return Offset(x, y)
        }
        val placed = if (view == null) emptyList() else radios.map { d ->
            Triple(d, proj(d.latitude!!, d.longitude!!), labelOf(d, state, demoMode))
        }
        val picked = pickedKey?.let { k -> radios.firstOrNull { it.key == k } }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(view) {
                    detectTransformGestures { _, panDelta, zoomDelta, _ ->
                        zoom = (zoom * zoomDelta).coerceIn(1f, 48f)
                        pan += panDelta
                    }
                }
                .pointerInput(placed) {
                    detectTapGestures { tap ->
                        val hit = placed.minByOrNull { (it.second - tap).getDistance() }
                            ?.takeIf { (it.second - tap).getDistance() < 28.dp.toPx() }
                        pickedKey = hit?.first?.key
                    }
                },
        ) {
            if (view == null) return@Canvas
            // Operator path — phone's own track while scanning.
            if (path.size >= 2) {
                val p = Path()
                path.forEachIndexed { i, s ->
                    val o = proj(s.lat, s.lon)
                    if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
                }
                drawPath(p, color = pathInk, style = Stroke(width = 2.dp.toPx()))
            }
            path.lastOrNull()?.let {
                drawCircle(pathInk, radius = 5.dp.toPx(), center = proj(it.lat, it.lon))
            }
            // Hear-trails: where the phone walked while each radio was on air.
            radios.forEach { d ->
                if (d.gpsTrail.size >= 2) {
                    val p = Path()
                    d.gpsTrail.forEachIndexed { i, s ->
                        val o = proj(s.lat, s.lon)
                        if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
                    }
                    drawPath(p, color = muted.copy(alpha = 0.45f), style = Stroke(width = 1.dp.toPx()))
                }
            }
            // Radio dots: named/signature = accent, extra attention = warn, else muted.
            placed.forEach { (d, o, label) ->
                val attention = vm.hasAttention(d)
                val color = when {
                    attention -> warn
                    d.fleetIds.isNotEmpty() -> accent
                    else -> muted
                }
                if (d.gone) {
                    drawCircle(color.copy(alpha = 0.6f), radius = 5.dp.toPx(), center = o, style = Stroke(1.dp.toPx()))
                } else {
                    drawCircle(color, radius = 4.dp.toPx(), center = o)
                }
                if (d.fleetIds.isNotEmpty() || d.key == pickedKey) {
                    drawText(
                        textMeasurer = measurer,
                        text = label,
                        topLeft = o + Offset(7.dp.toPx(), (-7).dp.toPx()),
                        style = TextStyle(
                            color = ink,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
            // Scale bar, bottom-left.
            MapPlot.scaleBar(MapPlot.metersPerPixel(view, zoom))?.let { (meters, px) ->
                val y = size.height - 14.dp.toPx()
                val x0 = 14.dp.toPx()
                drawLine(
                    color = muted,
                    start = Offset(x0, y),
                    end = Offset(x0 + px, y),
                    strokeWidth = 2.dp.toPx(),
                )
                drawText(
                    textMeasurer = measurer,
                    text = MapPlot.barLabel(meters),
                    topLeft = Offset(x0, y - 16.dp.toPx()),
                    style = TextStyle(
                        color = muted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }

        if (view == null) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No GPS-tagged radios yet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Dots mark where this phone heard each radio. Turn on Settings → Tag location, scan a while, then come back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${radios.size} pinned · ${if (state.settings.tagLocation) "GPS on" else "GPS off"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            picked?.let { d ->
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            labelOf(d, state, demoMode),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${MacUtil.screenMac(d.mac, demoMode)} · ${d.rssi} dBm · ${if (d.kind == RadioKind.WIFI) "Wi-Fi" else "BLE"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                        )
                    }
                    FieldwatchActionButton(onClick = { onOpen(d.key) }) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

private fun labelOf(d: Sighting, state: FieldwatchUi, demoMode: Boolean): String {
    val names = d.fleetIds.mapNotNull { id -> state.fleets.firstOrNull { it.id == id }?.name }
    val title = d.listTitle(names)
    return MacUtil.redactMacIn(title, d.mac, demoMode)
}
