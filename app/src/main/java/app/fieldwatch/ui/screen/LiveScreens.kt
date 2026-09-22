package app.fieldwatch.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.fieldwatch.ui.component.DecodeGlyph
import app.fieldwatch.ui.component.FieldwatchFilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.fieldwatch.domain.Sit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.ClassOutline
import app.fieldwatch.domain.ClassSlice
import app.fieldwatch.domain.FastPair
import app.fieldwatch.domain.OutlineSnap
import app.fieldwatch.domain.ListLine
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.ListSort
import app.fieldwatch.domain.Palette
import app.fieldwatch.domain.RadarPlot
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.ui.ClassGlyphs
import app.fieldwatch.ui.RadioClassBadge
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.domain.StrengthSort
import app.fieldwatch.domain.ViewMode
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.ui.component.PresenceTrack
import app.fieldwatch.ui.component.RssiBar
import app.fieldwatch.ui.component.Sparkline
import app.fieldwatch.ui.component.TrendMark
import app.fieldwatch.ui.component.rssiColor
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LivePane(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onOpen: (Sighting) -> Unit,
) {
    val live = state.filtered
    val sort = state.settings.strengthSort
    val pinEnd = state.settings.listSort == ListSort.ARRIVAL
    val windowMs = state.settings.averageWindowSec.coerceIn(10, 180) * 1000L
    val showBar = state.settings.showRssiBar
    val showFleet = state.settings.showFleetName
    val showFrequency = state.settings.showFrequency
    val showSeenTimes = state.settings.showSeenTimes
    val titleLine = state.settings.listTitleLine
    val subtitleLine = state.settings.listSubtitleLine
    val demoMode = state.settings.demoMode
    val flashKeys by vm.flashKeys.collectAsStateWithLifecycle()
    val alertedKeys by vm.alertedKeys.collectAsStateWithLifecycle()
    var renameSit by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        if (state.displayPaused) {
            Text(
                "Display paused · radios still scanning and logging. Filters still apply when you run again. Tap Live to run the list again.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (state.filter.arrivalsOnly) {
            Text(
                when {
                    state.arrivalsLearning -> "New only · learning sitting Wi-Fi"
                    state.hiddenKnown > 0 -> "New only · ${state.hiddenKnown} hidden"
                    else -> "New only"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (state.filter.movingWithYou) {
            Text(
                "Follow · path ${state.operatorSpanM.toInt()} m · Start over clears the path",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (state.filter.watchedOnly) {
            Text(
                "Watched only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (state.filter.customNamesOnly) {
            Text(
                "Named radios only",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        val openSit = state.sit.open
        if (openSit != null) {
            val now = System.currentTimeMillis()
            val dur = Sit.fmtDuration(openSit.durationMs(now))
            val cap = when {
                state.sit.memoryTight -> " · memory cap"
                state.sit.atCap -> " · ${Sit.RADIO_CAP} cap"
                else -> ""
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sit · ${openSit.name} · $dur · ${state.sit.radioCount} radios$cap",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            renameDraft = openSit.name
                            renameSit = true
                        },
                )
            }
        }
        if (renameSit && openSit != null) {
            AlertDialog(
                onDismissRequest = { renameSit = false },
                title = { Text("Rename sit") },
                text = {
                    FieldwatchOutlinedField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it.take(Sit.NAME_MAX) },
                        label = "Name",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            renameSit = false
                            vm.renameSit(openSit.id, renameDraft)
                        },
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { renameSit = false }) { Text("Cancel") }
                },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.settings.viewMode) {
                ViewMode.RADAR -> RadarView(live, vm, sort, windowMs, onOpen, emptyHint = arrivalsEmpty(state), showFleet = showFleet, demoMode = demoMode, flashKeys = flashKeys, alertedKeys = alertedKeys)
                ViewMode.LIST -> RankedList(live, vm, onOpen, sparklines = false, sort = sort, windowMs = windowMs, showBar = showBar, layoutEpoch = state.settings.scanControlsExpanded, emptyHint = arrivalsEmpty(state), showNewAge = state.filter.arrivalsOnly, showFleet = showFleet, showFrequency = showFrequency, showSeenTimes = showSeenTimes, flashKeys = flashKeys, alertedKeys = alertedKeys, pinEnd = pinEnd, titleLine = titleLine, subtitleLine = subtitleLine, demoMode = demoMode)
                ViewMode.TIMELINE -> TimelineView(live, vm, onOpen, showFleet = showFleet, showFrequency = showFrequency, showSeenTimes = showSeenTimes, flashKeys = flashKeys, alertedKeys = alertedKeys, titleLine = titleLine, subtitleLine = subtitleLine, demoMode = demoMode)
                ViewMode.HYBRID -> RankedList(live, vm, onOpen, sparklines = true, sort = sort, windowMs = windowMs, showBar = showBar, layoutEpoch = state.settings.scanControlsExpanded, emptyHint = arrivalsEmpty(state), showNewAge = state.filter.arrivalsOnly, showFleet = showFleet, showFrequency = showFrequency, showSeenTimes = showSeenTimes, flashKeys = flashKeys, alertedKeys = alertedKeys, pinEnd = pinEnd, titleLine = titleLine, subtitleLine = subtitleLine, demoMode = demoMode)
                ViewMode.BY_CLASS -> ClassOutlineView(
                    live, vm, state, onOpen,
                    sort = sort, windowMs = windowMs, showBar = showBar,
                    emptyHint = arrivalsEmpty(state),
                    showNewAge = state.filter.arrivalsOnly, showFleet = showFleet,
                    showFrequency = showFrequency, showSeenTimes = showSeenTimes,
                    flashKeys = flashKeys, alertedKeys = alertedKeys, titleLine = titleLine, subtitleLine = subtitleLine,
                    demoMode = demoMode,
                )
            }
        }
    }
}

private fun arrivalsEmpty(state: FieldwatchUi): String? {
    if (state.filter.movingWithYou) {
        return when {
            !state.settings.tagLocation ->
                "Moving with you needs Settings → Tag detections with GPS, then a walk or drive."
            state.operatorSpanM < 45.0 ->
                "GPS path ${state.operatorSpanM.toInt()} m — too short. Keep moving. " +
                    "If this stays 0, Location is not updating (use high accuracy)."
            state.filter.customNamesOnly || state.filter.watchedOnly ||
                state.filter.namedOnly || state.filter.namedOnlyImplied() ->
                "No loud BLE has stayed with you among the radios still allowed. " +
                    "Tap the Moving with you preset to test BLE, or turn off Signatures only / Show only / Named radios only / Watched only."
            else ->
                "No loud BLE has stayed with you along this path. Wi-Fi access points stay hidden. A tag in your bag or car should show. Find My MAC rotation will not stitch."
        }
    }
    if (state.filter.arrivalsOnly) {
        return when {
            state.arrivalsLearning -> "Hiding sitting access points until the next Wi-Fi scan. New Bluetooth still shows right away."
            state.hiddenKnown > 0 ->
                "${state.hiddenKnown} already seen are hidden. A new radio stays while we hear it, then at least as long as Brief hold after the last packet."
            else ->
                "Waiting for a new Wi-Fi or BLE radio. It stays while we hear it, then at least as long as Brief hold after the last packet."
        }
    }
    if (state.filter.watchedOnly) {
        return "No watched radios on the air. Bookmark a signature, turn Alert on a Named radio, or turn off Filters → Watched only."
    }
    if (state.filter.customNamesOnly) {
        return "No named radios on the air. Set a custom name on detail, or turn off Filters → Named radios only."
    }
    return null
}

private fun Sighting.rowTitle(vm: FieldwatchViewModel): String {
    val watch = vm.watchLabelFor(key)
    if (!watch.isNullOrBlank()) return watch
    return listTitle(fleetIds.map { vm.fleetName(it) })
}

@Composable
private fun ClassOutlineView(
    devices: List<Sighting>,
    vm: FieldwatchViewModel,
    state: FieldwatchUi,
    onOpen: (Sighting) -> Unit,
    sort: StrengthSort,
    windowMs: Long,
    showBar: Boolean,
    emptyHint: String?,
    showNewAge: Boolean,
    showFleet: Boolean,
    showFrequency: Boolean,
    showSeenTimes: Boolean,
    flashKeys: Set<String>,
    alertedKeys: Set<String> = emptySet(),
    titleLine: ListLine,
    subtitleLine: ListLine,
    demoMode: Boolean,
) {
    val classById = remember(state.fleets) { state.fleets.associate { it.id to it.kind } }
    val nameById = remember(state.fleets) { state.fleets.associate { it.id to it.name } }
    val slices = remember(devices, classById, nameById) {
        ClassOutline.of(devices, classById, nameById)
    }
    val hideEmpty = state.settings.outlineHideEmpty
    val visible = remember(slices, hideEmpty) {
        if (hideEmpty) slices.filter { it.radios.isNotEmpty() } else slices
    }
    val multi = remember(devices, classById) { ClassOutline.multiClassCount(devices, classById) }
    val openClasses by vm.outlineOpenClasses.collectAsStateWithLifecycle()
    val openSigs by vm.outlineOpenSigs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val liveFocus by vm.liveFocus.collectAsStateWithLifecycle()
    val beepSnap by vm.beepSnap.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(liveFocus) {
        if (liveFocus > 0) listState.scrollQuicklyToTop()
    }
    LaunchedEffect(beepSnap.seq) {
        if (beepSnap.seq <= 0 || beepSnap.key.isBlank()) return@LaunchedEffect
        withFrameNanos { }
        val snap = ClassOutline.outlineSnap(
            visible, openClasses, openSigs, beepSnap.key, leadingItems = 2,
        ) ?: return@LaunchedEffect
        listState.scrollOutlineToRadio(snap)
    }
    val unmatched = slices.firstOrNull { it.kind == null }?.radios?.size ?: 0
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        item(key = "outline-mode") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldwatchFilterChip(
                    selected = !hideEmpty,
                    onClick = { vm.updateSettings { it.copy(outlineHideEmpty = false) } },
                    label = { Text("Show all") },
                    modifier = Modifier.weight(1f),
                )
                FieldwatchFilterChip(
                    selected = hideEmpty,
                    onClick = { vm.updateSettings { it.copy(outlineHideEmpty = true) } },
                    label = { Text("Collapse empty") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (devices.isEmpty()) {
            item {
                Text(
                    emptyHint
                        ?: "No live emitters match the current filter. If this just emptied, the OS may be between scan windows — the last set is held and should return without a burst.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@LazyColumn
        }
        item(key = "outline-summary") {
            val hidden = slices.count { it.radios.isEmpty() }
            val summary = buildString {
                append("${devices.size} radios")
                if (unmatched > 0) append(" · $unmatched unmatched")
                if (multi > 0) append(" · $multi in more than one class")
                if (hideEmpty && hidden > 0) append(" · $hidden empty hidden")
            }
            Text(
                summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        visible.forEach { slice ->
            item(key = "class-${slice.id}") {
                OutlineGroupRow(
                    title = slice.label(),
                    count = slice.radios.size,
                    subtitle = when {
                        slice.radios.isEmpty() -> null
                        slice.kind == null -> "no signature"
                        slice.signatures.size == 1 -> vm.fleetName(slice.signatures.first().fleetId)
                        else -> "${slice.signatures.size} signatures"
                    },
                    accent = slice.accent(vm).nightIf(LocalNightMode.current),
                    expanded = slice.id in openClasses,
                    empty = slice.radios.isEmpty(),
                    glyph = ClassGlyphs.of(slice.kind),
                    onToggle = { vm.toggleOutlineClass(slice.id) },
                )
            }
            if (slice.id !in openClasses) return@forEach
            if (slice.kind == null) {
                items(slice.radios, key = { "u-${it.key}" }) { device ->
                    Box(Modifier.padding(start = 16.dp)) {
                        outlineRadioRow(
                            device, vm, onOpen, sort, windowMs,
                            showBar, showNewAge, showFleet, showFrequency, showSeenTimes,
                            now, flashKeys, titleLine, subtitleLine, demoMode,
                            alertedKeys = alertedKeys,
                        )
                    }
                }
            } else {
                slice.signatures.forEach { sig ->
                    val sigKey = "${slice.id}/${sig.fleetId}"
                    item(key = "sig-$sigKey") {
                        OutlineGroupRow(
                            title = vm.fleetName(sig.fleetId),
                            count = sig.radios.size,
                            subtitle = null,
                            accent = Color(Palette.color(vm.fleetColor(sig.fleetId)))
                                .nightIf(LocalNightMode.current),
                            expanded = sigKey in openSigs,
                            indent = true,
                            mapped = vm.fleetHasDecode(sig.fleetId),
                            onToggle = { vm.toggleOutlineSignature(sigKey) },
                        )
                    }
                    if (sigKey in openSigs) {
                        items(sig.radios, key = { "r-$sigKey-${it.key}" }) { device ->
                            Box(Modifier.padding(start = 28.dp)) {
                                outlineRadioRow(
                                    device, vm, onOpen, sort, windowMs,
                                    showBar, showNewAge, showFleet, showFrequency, showSeenTimes,
                                    now, flashKeys, titleLine, subtitleLine, demoMode,
                                    alertedKeys = alertedKeys,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineGroupRow(
    title: String,
    count: Int,
    subtitle: String?,
    accent: Color,
    expanded: Boolean,
    indent: Boolean = false,
    empty: Boolean = false,
    glyph: ImageVector? = null,
    mapped: Boolean = false,
    onToggle: () -> Unit,
) {
    val mute = MaterialTheme.colorScheme.onSurfaceVariant
    val mark = if (empty) mute else accent
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (indent) 0.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 16.dp else 0.dp)
            .clickable(onClick = onToggle),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                Surface(
                    shape = CircleShape,
                    color = mark.copy(alpha = if (empty) 0.12f else 0.18f),
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            glyph,
                            contentDescription = title,
                            modifier = Modifier.size(16.dp),
                            tint = mark.copy(alpha = if (empty) 0.55f else 1f),
                        )
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = mark.copy(alpha = if (empty) 0.12f else 0.22f),
                    modifier = Modifier.size(10.dp),
                ) {}
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        title,
                        style = compactLine(16.sp, 18.sp, FontWeight.SemiBold),
                        color = if (empty) mute else Color.Unspecified,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (mapped) {
                        DecodeGlyph(
                            tint = if (empty) mute else accent,
                            size = 14.dp,
                        )
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = compactLine(11.sp, 13.sp),
                        color = mute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                if (expanded) "▾  $count" else "▸  $count",
                style = compactLine(14.sp, 16.sp, FontWeight.Bold).copy(fontFamily = FontFamily.Monospace),
                color = if (empty) mute else accent,
            )
        }
    }
}

@Composable
private fun outlineRadioRow(
    device: Sighting,
    vm: FieldwatchViewModel,
    onOpen: (Sighting) -> Unit,
    sort: StrengthSort,
    windowMs: Long,
    showBar: Boolean,
    showNewAge: Boolean,
    showFleet: Boolean,
    showFrequency: Boolean,
    showSeenTimes: Boolean,
    now: Long,
    flashKeys: Set<String>,
    titleLine: ListLine,
    subtitleLine: ListLine,
    demoMode: Boolean,
    alertedKeys: Set<String> = emptySet(),
) {
    DeviceRow(
        device, vm, sparklines = false, compact = false,
        onOpen, sort, windowMs, showBar, showNewAge,
        showFleet, showFrequency, showSeenTimes, now,
        highlighted = device.key in flashKeys,
        titleLine = titleLine, subtitleLine = subtitleLine, demoMode = demoMode,
        alerted = device.key in alertedKeys,
    )
}

private fun ClassSlice.accent(vm: FieldwatchViewModel): Color {
    val id = signatures.firstOrNull()?.fleetId ?: radios.firstOrNull()?.fleetIds?.firstOrNull()
    return if (id != null) Color(Palette.color(vm.fleetColor(id))) else rssiColor(-80)
}

private fun radarRadius(rssi: Int, maxR: Float, zoom: Float = 1f): Float =
    RadarPlot.radius(rssi, maxR, zoom)

private fun radarRadius(rssi: Double, maxR: Float, zoom: Float = 1f): Float =
    radarRadius(rssi.toInt(), maxR, zoom)

private fun radarAngle(mac: String): Double {
    val hash = mac.hashCode()
    return ((hash ushr 1) % 360) * Math.PI / 180.0
}

private fun radarPoint(
    device: Sighting,
    center: Offset,
    maxR: Float,
    rssi: Double = device.rssi.toDouble(),
    zoom: Float = 1f,
): Offset {
    val angle = radarAngle(device.mac)
    val dist = radarRadius(rssi, maxR, zoom)
    return Offset(
        center.x + (cos(angle) * dist).toFloat(),
        center.y + (sin(angle) * dist).toFloat(),
    )
}

private fun sweepBehindDegrees(sweepDeg: Float, mac: String): Float {
    val blip = ((mac.hashCode() ushr 1) % 360).toFloat()
    var beam = (270f + sweepDeg) % 360f
    if (beam < 0f) beam += 360f
    var behind = beam - blip
    while (behind < 0f) behind += 360f
    return behind
}

/** 1 at the beam, falling off through the trail, dim between paints. */
private fun sweepPaint(behindDeg: Float): Float = when {
    behindDeg <= 8f -> 1f
    behindDeg < 120f -> {
        val u = (behindDeg - 8f) / 112f
        (1f - u) * (1f - u)
    }
    else -> 0.20f
}

private fun DrawScope.drawRadarSweep(center: Offset, maxR: Float, beam: Color, night: Boolean) {
    val trailDeg = 58f
    val steps = 24
    val slice = trailDeg / steps
    val box = androidx.compose.ui.geometry.Size(maxR * 2, maxR * 2)
    val origin = Offset(center.x - maxR, center.y - maxR)
    for (i in 0 until steps) {
        val t = i / (steps - 1f).coerceAtLeast(1f)
        val fade = 1f - t
        drawArc(
            color = beam.copy(alpha = 0.32f * fade * fade * fade),
            startAngle = -90f - i * slice,
            sweepAngle = -(slice + 1.1f),
            useCenter = true,
            topLeft = origin,
            size = box,
        )
    }
    val tip = Offset(center.x, center.y - maxR)
    drawLine(beam.copy(alpha = 0.16f), center, tip, 15f)
    drawLine(beam.copy(alpha = 0.42f), center, tip, 7f)
    drawLine(beam.copy(alpha = if (night) 0.95f else 0.88f), center, tip, 2.2f)
}

private fun DrawScope.drawRadarContact(
    pos: Offset,
    color: Color,
    named: Boolean,
    gone: Boolean,
    flashElapsedMs: Long?,
    alertedRing: Color? = null,
) {
    val rad = if (named) 11f else 7f
    if (named && !gone) drawCircle(color.copy(alpha = 0.28f), radius = 20f, center = pos)
    if (flashElapsedMs == null) {
        drawCircle(color, radius = rad, center = pos)
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = rad, center = pos, style = Stroke(1.4f))
        if (alertedRing != null) {
            drawCircle(
                alertedRing,
                radius = rad + 3.8f,
                center = pos,
                style = Stroke(width = 2.3f),
            )
        }
        return
    }
    val elapsed = flashElapsedMs.coerceAtLeast(0L)
    fun ping(delayMs: Long, travel: Float) {
        val age = elapsed - delayMs
        if (age < 0L) return
        val u = (age / 720f).coerceIn(0f, 1f)
        val ringR = rad + 6f + u * travel
        val a = (1f - u) * 0.92f
        drawCircle(
            color.copy(alpha = a),
            radius = ringR,
            center = pos,
            style = Stroke(width = (3.8f * (1f - 0.55f * u)).coerceAtLeast(1.2f)),
        )
    }
    ping(0, 56f)
    ping(220, 42f)
    val beat = (0.5f + 0.5f * sin(elapsed / 1000.0 * 4.0 * Math.PI).toFloat()).coerceIn(0f, 1f)
    drawCircle(Color.White.copy(alpha = 0.18f + 0.32f * beat), radius = rad + 16f + 10f * beat, center = pos)
    drawCircle(color.copy(alpha = 0.40f + 0.25f * beat), radius = rad + 8f + 6f * beat, center = pos)
    drawCircle(Color.White.copy(alpha = 0.80f), radius = rad + 2.5f + 2.5f * beat, center = pos)
    drawCircle(color, radius = rad + 1.2f, center = pos)
    drawCircle(Color.White.copy(alpha = 0.70f), radius = 3.4f, center = pos)
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = rad + 1.2f, center = pos, style = Stroke(1.4f))
}

/**
 * Vsync sweep so the beam still moves when Developer options
 * Animator duration scale is off (tween infinite animations freeze).
 */
@Composable
private fun rememberRadarSweepDegrees(periodMs: Int = 4200): MutableFloatState {
    val sweep = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(periodMs) {
        var last = 0L
        val periodNs = periodMs * 1_000_000L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val add = (now - last).toDouble() / periodNs * 360.0
                    var next = sweep.floatValue + add.toFloat()
                    while (next >= 360f) next -= 360f
                    sweep.floatValue = next
                }
                last = now
            }
        }
    }
    return sweep
}

@Composable
private fun RadarView(
    devices: List<Sighting>,
    vm: FieldwatchViewModel,
    sort: StrengthSort,
    windowMs: Long,
    onOpen: (Sighting) -> Unit,
    emptyHint: String? = null,
    showFleet: Boolean = true,
    demoMode: Boolean = false,
    flashKeys: Set<String> = emptySet(),
    alertedKeys: Set<String> = emptySet(),
) {
    val sweep = rememberRadarSweepDegrees()
    val night = LocalNightMode.current
    val ring = MaterialTheme.colorScheme.outline
    val beam = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val youColor = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    val onAir = devices.count { !it.gone }
    val ringStyle = TextStyle(
        color = labelColor,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
    )
    val nameStyle = TextStyle(
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
    )
    val flashAt = remember { mutableStateMapOf<String, Long>() }
    val zoomState = remember { mutableFloatStateOf(1f) }
    var zoom by zoomState
    val liveRef = remember { mutableStateOf(devices) }
    liveRef.value = devices
    LaunchedEffect(flashKeys) {
        val t = System.currentTimeMillis()
        flashKeys.forEach { key -> if (key !in flashAt) flashAt[key] = t }
        flashAt.keys.filter { it !in flashKeys }.forEach { flashAt.remove(it) }
    }

    fun hitBlip(tap: Offset, canvas: androidx.compose.ui.unit.IntSize): Sighting? {
        val c = Offset(canvas.width / 2f, canvas.height / 2f)
        val maxR = minOf(canvas.width, canvas.height) / 2f - 18f
        val z = zoomState.floatValue
        val now = System.currentTimeMillis()
        return liveRef.value
            .map {
                val rssi = it.sortRssi(sort, windowMs, now)
                it to rssi
            }
            .filter { RadarPlot.onDisc(it.second.toInt(), maxR, z) }
            .map { (device, rssi) ->
                device to (radarPoint(device, c, maxR, rssi, z) - tap).getDistance()
            }
            .minByOrNull { it.second }
            ?.takeIf { it.second < 48f }
            ?.first
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2) {
                                val factor = event.calculateZoom()
                                if (factor != 1f) {
                                    zoomState.floatValue =
                                        RadarPlot.clampZoom(zoomState.floatValue * factor)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { zoomState.floatValue = RadarPlot.MIN_ZOOM },
                        onTap = { tap -> hitBlip(tap, size)?.let(onOpen) },
                    )
                },
        ) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f - 18f
            val z = zoom

            drawCircle(Color.Black.copy(alpha = 0.18f), radius = maxR, center = c)
            listOf(-40, -60, -80, -100).forEach { dbm ->
                val rr = radarRadius(dbm, maxR, z)
                if (rr > maxR + 0.5f) return@forEach
                drawCircle(ring.copy(alpha = 0.55f), radius = rr, center = c, style = Stroke(2.2f))
                val layout = measurer.measure("$dbm", ringStyle)
                drawText(
                    layout,
                    topLeft = Offset(c.x + 6f, c.y - rr - layout.size.height),
                )
            }
            drawLine(ring.copy(alpha = 0.4f), Offset(c.x - maxR, c.y), Offset(c.x + maxR, c.y), 2f)
            drawLine(ring.copy(alpha = 0.4f), Offset(c.x, c.y - maxR), Offset(c.x, c.y + maxR), 2f)

            rotate(sweep.floatValue, c) {
                drawRadarSweep(c, maxR, beam, night)
            }

            val now = System.currentTimeMillis()
            val phosphor = PhosphorActive.nightIf(night)
            fun contact(device: Sighting, persist: Boolean = true): Triple<Offset, Color, Boolean>? {
                val plotRssi = device.sortRssi(sort, windowMs, now)
                if (!RadarPlot.onDisc(plotRssi.toInt(), maxR, z)) return null
                val pos = radarPoint(device, c, maxR, plotRssi, z)
                val named = device.fleetIds.isNotEmpty()
                val paint = if (persist) sweepPaint(sweepBehindDegrees(sweep.floatValue, device.mac)) else 1f
                val alpha = (if (device.gone) 0.45f else 1f) * (0.35f + 0.65f * paint)
                val color = (device.fleetIds.firstOrNull()
                    ?.let { Color(Palette.color(vm.fleetColor(it))) }
                    ?: rssiColor(plotRssi.toInt()))
                    .nightIf(night)
                    .copy(alpha = alpha)
                return Triple(pos, color, named)
            }
            devices.forEach { device ->
                val (pos, color, named) = contact(device) ?: return@forEach
                if (device.key !in flashKeys && device.key !in alertedKeys) {
                    drawRadarContact(pos, color, named, device.gone, null)
                }
                val rad = if (named) 11f else 7f
                val label = when {
                    showFleet && named -> device.fleetIds.firstOrNull()?.let { vm.fleetName(it) }?.take(14)
                        ?: MacUtil.redactMacIn(device.rowTitle(vm), device.mac, demoMode).take(14)
                    named -> MacUtil.redactMacIn(device.rowTitle(vm), device.mac, demoMode).take(14)
                    devices.size <= 24 -> MacUtil.redactMacIn(device.rowTitle(vm), device.mac, demoMode).take(12)
                    else -> null
                }
                if (label != null) {
                    val layout = measurer.measure(label, nameStyle)
                    drawText(
                        layout,
                        topLeft = Offset(
                            (pos.x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width),
                            (pos.y + rad + 4f).coerceAtMost(size.height - layout.size.height),
                        ),
                    )
                }
            }
            devices.forEach { device ->
                if (device.key in flashKeys || device.key !in alertedKeys) return@forEach
                val (pos, color, named) = contact(device, persist = false) ?: return@forEach
                val ring = phosphor.copy(alpha = if (device.gone) 0.45f else 1f)
                drawRadarContact(pos, color, named, device.gone, null, alertedRing = ring)
            }
            devices.forEach { device ->
                if (device.key !in flashKeys) return@forEach
                val (pos, color, named) = contact(device, persist = false) ?: return@forEach
                val started = flashAt[device.key] ?: now
                drawRadarContact(pos, color, named, device.gone, now - started)
            }

            drawCircle(youColor, radius = 7f, center = c)
            drawCircle(youColor.copy(alpha = 0.2f), radius = 16f, center = c)
            val you = measurer.measure("YOU", ringStyle.copy(color = youColor, fontWeight = FontWeight.Bold))
            drawText(you, topLeft = Offset(c.x - you.size.width / 2f, c.y + 12f))
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                if (devices.isEmpty()) {
                    emptyHint ?: "No devices match the current filter"
                } else {
                    val zoomBit = if (zoom > 1.04f) {
                        " · ×${"%.1f".format(zoom)} · double-tap reset"
                    } else {
                        " · pinch to zoom"
                    }
                    "$onAir on-air · ${devices.size} in filter · dim = gone · tap a blip$zoomBit"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun LazyListState.indexVisible(index: Int): Boolean {
    val vis = layoutInfo.visibleItemsInfo
    if (vis.isEmpty()) return false
    return index in vis.first().index..vis.last().index
}

/**
 * Pin the class header (then the signature header) if the radio still
 * fits on screen. Far-down radios fall back to the row itself.
 */
private suspend fun LazyListState.scrollOutlineToRadio(snap: OutlineSnap) {
    scrollToItem(snap.classIndex)
    withFrameNanos { }
    if (indexVisible(snap.radioIndex)) return
    val sig = snap.signatureIndex
    if (sig != null) {
        scrollToItem(sig)
        withFrameNanos { }
        if (indexVisible(snap.radioIndex)) return
    }
    scrollToItem(snap.radioIndex)
}

@OptIn(ExperimentalFoundationApi::class)
private suspend fun LazyListState.scrollQuicklyToTop() {
    if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset < 4) return
    val avg = layoutInfo.visibleItemsInfo.map { it.size }.average().toFloat()
    val distance = if (avg.isFinite() && avg > 0f) {
        firstVisibleItemIndex * avg + firstVisibleItemScrollOffset
    } else {
        firstVisibleItemScrollOffset.toFloat()
    }
    if (distance > 4f) {
        animateScrollBy(-distance, tween(durationMillis = 140, easing = LinearEasing))
    }
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) {
        scrollToItem(0)
    }
}

@Composable
private fun RankedList(
    devices: List<Sighting>,
    vm: FieldwatchViewModel,
    onOpen: (Sighting) -> Unit,
    sparklines: Boolean = false,
    compact: Boolean = false,
    sort: StrengthSort = StrengthSort.AVERAGE,
    windowMs: Long = 30_000L,
    showBar: Boolean = true,
    layoutEpoch: Boolean = true,
    emptyHint: String? = null,
    showNewAge: Boolean = false,
    showFleet: Boolean = true,
    showFrequency: Boolean = false,
    showSeenTimes: Boolean = false,
    flashKeys: Set<String> = emptySet(),
    alertedKeys: Set<String> = emptySet(),
    pinEnd: Boolean = false,
    titleLine: ListLine = ListLine.MAC,
    subtitleLine: ListLine = ListLine.NAME_AND_TYPE,
    demoMode: Boolean = false,
) {
    val listState = rememberLazyListState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val stickToTop = remember { mutableStateOf(true) }
    val stickToEnd = remember { mutableStateOf(true) }
    val liveFocus by vm.liveFocus.collectAsStateWithLifecycle()
    val beepSnap by vm.beepSnap.collectAsStateWithLifecycle()
    LaunchedEffect(pinEnd) {
        if (pinEnd) stickToEnd.value = true else stickToTop.value = true
    }
    LaunchedEffect(liveFocus) {
        if (liveFocus > 0) {
            if (devices.isNotEmpty()) listState.scrollQuicklyToTop()
            stickToTop.value = !pinEnd
            stickToEnd.value = false
        }
    }
    LaunchedEffect(beepSnap.seq) {
        if (beepSnap.seq <= 0 || beepSnap.key.isBlank()) return@LaunchedEffect
        val idx = devices.indexOfFirst { it.key == beepSnap.key }
        if (idx < 0) return@LaunchedEffect
        stickToTop.value = false
        stickToEnd.value = false
        listState.scrollToItem(idx)
    }
    LaunchedEffect(listState, pinEnd) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24
            val atEnd = last != null && info.totalItemsCount > 0 &&
                last.index >= info.totalItemsCount - 1
            Triple(listState.isScrollInProgress, atTop, atEnd)
        }.distinctUntilChanged().collect { (scrolling, atTop, atEnd) ->
            if (pinEnd) {
                if (scrolling) stickToEnd.value = atEnd else if (atEnd) stickToEnd.value = true
            } else {
                if (scrolling) stickToTop.value = atTop else if (atTop) stickToTop.value = true
            }
        }
    }
    LaunchedEffect(devices.lastOrNull()?.key, devices.size, pinEnd) {
        if (devices.isEmpty()) return@LaunchedEffect
        if (pinEnd && stickToEnd.value) {
            listState.scrollToItem(devices.lastIndex)
        } else if (!pinEnd && stickToTop.value) {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(showBar, showFleet, showFrequency, showSeenTimes, layoutEpoch, devices.size, pinEnd) {
        val count = devices.size
        if (count == 0) return@LaunchedEffect
        if (pinEnd) {
            if (stickToEnd.value) listState.scrollToItem(count - 1)
            return@LaunchedEffect
        }
        val idx = listState.firstVisibleItemIndex
        if (stickToTop.value || idx == 0 || idx >= count) {
            listState.scrollToItem(0)
            stickToTop.value = true
        } else {
            listState.scrollToItem(idx.coerceAtMost(count - 1))
        }
    }
    LaunchedEffect(listState, pinEnd) {
        snapshotFlow { listState.layoutInfo }
            .collect { info ->
                if (info.totalItemsCount > 0 && info.visibleItemsInfo.isEmpty()) {
                    if (pinEnd) {
                        listState.scrollToItem((info.totalItemsCount - 1).coerceAtLeast(0))
                        stickToEnd.value = true
                    } else {
                        listState.scrollToItem(0)
                        stickToTop.value = true
                    }
                }
            }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (devices.isEmpty()) {
            item {
                Text(
                    emptyHint
                        ?: "No live emitters match the current filter. If this just emptied, the OS may be between scan windows — the last set is held and should return without a burst. Use Timeline for recent disappearances.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        items(devices, key = { it.key }) { device ->
            DeviceRow(device, vm, sparklines, compact, onOpen, sort, windowMs, showBar, showNewAge, showFleet, showFrequency, showSeenTimes, now, highlighted = device.key in flashKeys, titleLine = titleLine, subtitleLine = subtitleLine, demoMode = demoMode, alerted = device.key in alertedKeys)
        }
    }
}

@Composable
fun DeviceRow(
    device: Sighting,
    vm: FieldwatchViewModel,
    sparklines: Boolean,
    compact: Boolean,
    onOpen: (Sighting) -> Unit,
    sort: StrengthSort = StrengthSort.AVERAGE,
    windowMs: Long = 30_000L,
    showBar: Boolean = true,
    showNewAge: Boolean = false,
    showFleet: Boolean = true,
    showFrequency: Boolean = false,
    showSeenTimes: Boolean = false,
    now: Long = System.currentTimeMillis(),
    highlighted: Boolean = false,
    titleLine: ListLine = ListLine.MAC,
    subtitleLine: ListLine = ListLine.NAME_AND_TYPE,
    demoMode: Boolean = false,
    alerted: Boolean = false,
) {
    val heardRssi = device.heardRssi(sort, windowMs, now).toInt()
    val rankRssi = device.sortRssi(sort, windowMs, now).toInt()
    val accent = (device.fleetIds.firstOrNull()
        ?.let { Color(Palette.color(vm.fleetColor(it))) }
        ?: rssiColor(heardRssi))
        .nightIf(LocalNightMode.current)
    val named = showFleet && device.fleetIds.isNotEmpty()
    val roomy = showBar || sparklines || showSeenTimes
    val flash = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    val rowColor by animateColorAsState(
        targetValue = if (highlighted) flash else MaterialTheme.colorScheme.surface,
        animationSpec = tween(if (highlighted) 90 else 280),
        label = "alertFlash",
    )
    Surface(
        shape = RoundedCornerShape(if (roomy) 12.dp else 8.dp),
        color = rowColor,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(device) },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = if (roomy) 6.dp else 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                RadioClassBadge(
                    classKind = device.fleetIds.firstOrNull()?.let { vm.fleetKind(it) },
                    accent = accent,
                    compact = compact,
                )
                Spacer(Modifier.width(8.dp))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    val names = device.fleetIds.map { vm.fleetName(it) }
                    val watch = vm.watchLabelFor(device.key)
                    val crumbs = device.statusCrumbs()
                    val showSub = subtitleLine != ListLine.NONE
                    val titleCore = MacUtil.redactMacIn(device.listLineText(titleLine, names, watch), device.mac, demoMode)
                    val titleText = if (!showSub && crumbs.isNotEmpty()) {
                        if (titleCore.isNotEmpty()) "$titleCore  $crumbs" else crumbs
                    } else {
                        titleCore
                    }
                    Text(
                        titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = compactLine(16.sp, 18.sp, FontWeight.SemiBold),
                    )
                    val attention = vm.hasAttention(device)
                    if (attention || named || alerted) {
                        FleetNameChips(device, vm, attention, showNames = named, alerted = alerted)
                    }
                    if (showSub) {
                        val sub = listSubtitle(device, subtitleLine, names, demoMode, watch)
                        RadioKindSubtitle(device.kind, sub)
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrendMark(device.rssiTrend())
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${device.rssi}",
                            style = compactLine(16.sp, 18.sp, FontWeight.Bold).copy(fontFamily = FontFamily.Monospace),
                            color = accent,
                        )
                    }
                    if (showFrequency) {
                        val fact = radioFactLine(device)
                        if (fact != null) {
                            Text(
                                fact,
                                style = compactLine(10.sp, 11.sp).copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (showNewAge) {
                        val ageSec = ((now - device.firstSeen) / 1000L).coerceAtLeast(0L)
                        Text(
                            if (ageSec < 60L) "new ${ageSec}s" else "new ${ageSec / 60L}m",
                            style = compactLine(10.sp, 11.sp).copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (sort == StrengthSort.AVERAGE) {
                        Text(
                            "avg $rankRssi",
                            style = compactLine(10.sp, 11.sp).copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!compact && showBar) {
                Spacer(Modifier.height(4.dp))
                RssiBar(heardRssi, accent, Modifier.fillMaxWidth())
            }
            if (!compact && sparklines) {
                Spacer(Modifier.height(6.dp))
                Sparkline(device.rssiHistory, accent, Modifier.fillMaxWidth().height(56.dp))
            }
            if (showSeenTimes) {
                Spacer(Modifier.height(4.dp))
                Text(
                    seenTimesLabel(device, now),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FleetNameChips(
    device: Sighting,
    vm: FieldwatchViewModel,
    attention: Boolean = false,
    showNames: Boolean = true,
    alerted: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (attention) {
            val warn = MaterialTheme.colorScheme.error
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = warn.copy(alpha = 0.22f),
            ) {
                Text(
                    "!",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp),
                    maxLines = 1,
                    style = TextStyle(
                        color = warn,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
        }
        if (alerted) {
            val mark = PhosphorActive.nightIf(LocalNightMode.current)
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = mark.copy(alpha = 0.22f),
            ) {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = "Alerted this session",
                    modifier = Modifier
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                        .size(11.dp),
                    tint = mark,
                )
            }
        }
        if (showNames) device.fleetIds.take(3).forEach { id ->
            val color = Color(Palette.color(vm.fleetColor(id)))
                .nightIf(LocalNightMode.current)
            val mapped = vm.fleetHasDecode(id)
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = color.copy(alpha = 0.18f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        if (id == FastPair.FLEET_ID) FastPair.liveLabel(device.fastPairPairing)
                        else vm.fleetName(id),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = color,
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        ),
                    )
                    if (mapped) {
                        DecodeGlyph(tint = color, size = 10.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineView(
    devices: List<Sighting>,
    vm: FieldwatchViewModel,
    onOpen: (Sighting) -> Unit,
    showFleet: Boolean = true,
    showFrequency: Boolean = false,
    showSeenTimes: Boolean = false,
    flashKeys: Set<String> = emptySet(),
    alertedKeys: Set<String> = emptySet(),
    titleLine: ListLine = ListLine.MAC,
    subtitleLine: ListLine = ListLine.NAME_AND_TYPE,
    demoMode: Boolean = false,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val window = 15 * 60 * 1000L
    val listState = rememberLazyListState()
    val liveFocus by vm.liveFocus.collectAsStateWithLifecycle()
    val beepSnap by vm.beepSnap.collectAsStateWithLifecycle()
    LaunchedEffect(liveFocus) {
        if (liveFocus > 0) listState.scrollQuicklyToTop()
    }
    LaunchedEffect(beepSnap.seq) {
        if (beepSnap.seq <= 0 || beepSnap.key.isBlank()) return@LaunchedEffect
        val idx = devices.indexOfFirst { it.key == beepSnap.key }
        if (idx < 0) return@LaunchedEffect
        listState.scrollToItem(idx + 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        item {
            Text(
                "Last 15 minutes · solid bars are on-air windows",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(devices, key = { it.key }) { device ->
            val color = (device.fleetIds.firstOrNull()
                ?.let { Color(Palette.color(vm.fleetColor(it))) }
                ?: rssiColor(device.rssi))
                .nightIf(LocalNightMode.current)
            val flash = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            val rowColor by animateColorAsState(
                targetValue = if (device.key in flashKeys) flash else MaterialTheme.colorScheme.surface,
                animationSpec = tween(if (device.key in flashKeys) 90 else 280),
                label = "alertFlash",
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = rowColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(device) },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioClassBadge(
                            classKind = device.fleetIds.firstOrNull()?.let { vm.fleetKind(it) },
                            accent = color,
                        )
                        Spacer(Modifier.width(8.dp))
                        val names = device.fleetIds.map { vm.fleetName(it) }
                        val watch = vm.watchLabelFor(device.key)
                        val crumbs = device.statusCrumbs()
                        val showSub = subtitleLine != ListLine.NONE
                        val titleCore = MacUtil.redactMacIn(device.listLineText(titleLine, names, watch), device.mac, demoMode)
                        val titleText = if (!showSub && crumbs.isNotEmpty()) {
                            if (titleCore.isNotEmpty()) "$titleCore  $crumbs" else crumbs
                        } else {
                            titleCore
                        }
                        Text(titleText, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TrendMark(device.rssiTrend())
                        Spacer(Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${device.rssi} dBm", fontFamily = FontFamily.Monospace, color = color)
                            if (showFrequency) {
                                val fact = radioFactLine(device)
                                if (fact != null) {
                                    Text(
                                        fact,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (subtitleLine != ListLine.NONE) {
                        val names = device.fleetIds.map { vm.fleetName(it) }
                        val watch = vm.watchLabelFor(device.key)
                        val sub = listSubtitle(device, subtitleLine, names, demoMode, watch)
                        RadioKindSubtitle(device.kind, sub)
                    }
                    if (showSeenTimes) {
                        Text(
                            seenTimesLabel(device, now),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val attention = vm.hasAttention(device)
                    val showNames = showFleet && device.fleetIds.isNotEmpty()
                    val alerted = device.key in alertedKeys
                    if (attention || showNames || alerted) {
                        Spacer(Modifier.height(3.dp))
                        FleetNameChips(device, vm, attention, showNames = showNames, alerted = alerted)
                    }
                    Spacer(Modifier.height(6.dp))
                    PresenceTrack(device, now, window, color)
                }
            }
        }
    }
}

private fun compactLine(
    size: androidx.compose.ui.unit.TextUnit,
    line: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontSize = size,
    lineHeight = line,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

@Composable
private fun RadioKindSubtitle(kind: RadioKind, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioKindMark(kind, size = 11.dp)
        if (text.isNotEmpty()) {
            Text(
                text,
                style = compactLine(11.sp, 13.sp).copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private fun listSubtitle(
    device: Sighting,
    subtitleLine: ListLine,
    names: List<String>,
    demoMode: Boolean,
    watchName: String? = null,
): String {
    val raw = MacUtil.redactMacIn(device.listLineText(subtitleLine, names, watchName), device.mac, demoMode)
    val body = if (raw.equals("unnamed LE", ignoreCase = true)) "unnamed" else raw
    val crumbs = device.statusCrumbs()
    return buildString {
        if (body.isNotEmpty()) append(body)
        if (crumbs.isNotEmpty()) {
            if (isNotEmpty()) append("  ")
            append(crumbs)
        }
    }
}

private fun radioFactLine(device: Sighting): String? {
    val ch = if (device.channel != 0) "ch${device.channel}" else null
    val mhz = if (device.frequencyMhz != 0) "${device.frequencyMhz}MHz" else null
    return listOfNotNull(ch, mhz).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun seenTimesLabel(device: Sighting, now: Long): String {
    val first = formatAge(now - device.firstSeen)
    val lastMs = now - device.lastSeen
    val last = if (lastMs < 1_000L) "now" else formatAge(lastMs)
    return "first $first  ·  last $last"
}

private fun formatAge(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    return when {
        sec < 60L -> "${sec}s"
        sec < 3600L -> {
            val m = sec / 60L
            val s = sec % 60L
            if (s == 0L) "${m}m" else "${m}m ${s}s"
        }
        else -> {
            val h = sec / 3600L
            val m = (sec % 3600L) / 60L
            if (m == 0L) "${h}h" else "${h}h ${m}m"
        }
    }
}
