package app.fieldwatch.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.domain.AircraftHistory
import app.fieldwatch.domain.AircraftTrail
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.OperatorLoc
import app.fieldwatch.domain.Sighting
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.component.SitPathCanvas
import androidx.compose.foundation.shape.RoundedCornerShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AircraftReportPage(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onOpen: (String) -> Unit,
) {
    val settings = state.settings
    val rows by remember { vm.aircraftRows() }.collectAsStateWithLifecycle()
    val streets by vm.placeStreetNames.collectAsStateWithLifecycle()
    LaunchedEffect(rows, settings.onlineLookup, settings.demoMode) {
        if (!settings.demoMode) vm.refreshAircraftStreets()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            LanguageText.text("reports.aircraft.blurb"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settings.demoMode) {
            Text(
                LanguageText.text("ui.privacy.mode.debrief.compare"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (rows.isEmpty()) {
            Text(
                LanguageText.text("reports.aircraft.empty"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val zone = ZoneId.systemDefault()
        rows.forEach { record ->
            AircraftReportRow(record, zone, settings.demoMode, streets) { onOpen(record.id) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftRecordScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onBack: () -> Unit,
    onOpenLive: (Sighting) -> Unit,
) {
    val id by vm.aircraftOpen.collectAsStateWithLifecycle()
    val rows by remember { vm.aircraftRows() }.collectAsStateWithLifecycle()
    val streets by vm.placeStreetNames.collectAsStateWithLifecycle()
    val mapTiles by vm.aircraftMapTiles.collectAsStateWithLifecycle()
    val settings = state.settings
    val record = rows.firstOrNull { it.id == id }
    val pictures = remember(record) { record?.let { AircraftHistory.pictures(it.passes) }.orEmpty() }
    LaunchedEffect(record, settings.onlineLookup, settings.demoMode) {
        if (!settings.demoMode) vm.refreshAircraftStreets()
    }
    LaunchedEffect(record, settings.onlineLookup, settings.mapBasemap, settings.mapOverlays) {
        val shown = record ?: return@LaunchedEffect
        vm.refreshAircraftPath(shown)
    }
    val title = if (settings.demoMode || id.isNullOrBlank()) "masked" else id.orEmpty()
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, LanguageText.text("chrome.back"))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (settings.demoMode) {
                Text(
                    LanguageText.text("ui.privacy.mode.debrief.compare"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (record == null) {
                Text(
                    LanguageText.text("reports.aircraft.empty"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            if (!settings.demoMode && record.label.isNotBlank()) {
                Text(record.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            if (!settings.demoMode && record.selfId.isNotBlank() && record.selfId != record.id) {
                Text(
                    record.selfId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val live = state.devices.firstOrNull { it.payloadUasId?.trim() == record.id && !it.gone }
            if (live != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenLive(live) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            LanguageText.text("device.aircraft.live"),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val zone = ZoneId.systemDefault()
            if (pictures.isNotEmpty()) {
                PathKey(record.passes.any { it.emergencies.isNotEmpty() })
            }
            pictures.forEachIndexed { index, picture ->
                if (pictures.size > 1) {
                    Text(
                        pictureDates(record.passes, picture.passes, zone),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val shot = mapTiles.getOrNull(index)
                SitPathCanvas(
                    picture.model,
                    tiles = shot?.tiles.orEmpty(),
                    zoomIn = shot?.zoomIn == true,
                    pictureHeight = 320.dp,
                )
            }
            record.passes.indices.reversed().forEach { index ->
                PassCard(
                    record.passes[index],
                    zone,
                    settings.demoMode,
                    streets,
                    AircraftHistory.colorAt(record.passes, index),
                )
            }
        }
    }
}

@Composable
private fun AircraftReportRow(
    record: AircraftHistory.Record,
    zone: ZoneId,
    demo: Boolean,
    streets: Map<String, String>,
    onOpen: () -> Unit,
) {
    val last = record.passes.lastOrNull()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (demo) "masked" else record.id,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = when {
                    demo -> ""
                    record.label.isNotBlank() -> record.label
                    record.selfId.isNotBlank() && record.selfId != record.id -> record.selfId
                    else -> ""
                }
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${countText(record.passes.map { AircraftHistory.localDate(it.start, zone).toEpochDay() }.distinct().size, "reports.aircraft.day", "reports.aircraft.days")} · ${countText(record.passes.size, "reports.aircraft.time", "reports.aircraft.times")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (last != null && !demo) {
                    val street = streetOf(last, streets)
                    val where = listOf(formatDate(last.end, zone), street).filter { it.isNotEmpty() }
                    if (where.isNotEmpty()) {
                        Text(
                            where.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (record.passes.any { it.emergencies.isNotEmpty() }) {
                    Text(
                        LanguageText.text("reports.aircraft.emergency"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = EmergencyInk,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PassCard(
    pass: AircraftHistory.Pass,
    zone: ZoneId,
    demo: Boolean,
    streets: Map<String, String>,
    color: Long,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (color != 0L) {
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(10.dp)
                            .background(Color(color.toInt()), CircleShape),
                    )
                }
                Text(formatDate(pass.start, zone), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${span(pass, zone)} · ${visitLength(pass)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            pass.emergencies.forEach { emergency ->
                Text(
                    LanguageText.text(
                        "reports.aircraft.emergency.when",
                        mapOf("when" to emergencyWhen(emergency, zone)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = EmergencyInk,
                )
                if (!demo && emergency.text.isNotBlank()) {
                    Text(
                        emergency.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!demo) {
                val street = streetOf(pass, streets)
                if (street.isNotEmpty()) {
                    Text(street, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (pass.hasPosition && pass.fixes.isEmpty()) {
                Text(
                    LanguageText.text("reports.aircraft.position"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AircraftTrail.motionSpanLines(pass.fixes).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val facts = listOfNotNull(
                pass.opKind?.let { OperatorLoc.label(it) },
                radioLine(pass).takeIf { it.isNotEmpty() },
            )
            if (facts.isNotEmpty()) {
                Text(
                    facts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun aircraftWhen(earlier: AircraftHistory.Earlier, zone: ZoneId): String = when (earlier) {
    is AircraftHistory.Earlier.Yesterday -> LanguageText.text("device.aircraft.yesterday")
    is AircraftHistory.Earlier.Clock -> formatClock(earlier.at, zone)
    is AircraftHistory.Earlier.Date -> formatDate(earlier.at, zone)
}

internal fun aircraftDaysLine(days: Int): String = if (days == 1) {
    LanguageText.text("device.aircraft.day")
} else {
    LanguageText.text("device.aircraft.days", mapOf("n" to days.toString()))
}

private fun countText(n: Int, one: String, many: String): String = if (n == 1) {
    LanguageText.text(one)
} else {
    LanguageText.text(many, mapOf("n" to n.toString()))
}

private fun pictureDates(passes: List<AircraftHistory.Pass>, indexes: List<Int>, zone: ZoneId): String {
    val dates = indexes.map { formatDate(passes[it].start, zone) }.distinct()
    return if (dates.size <= 1) dates.firstOrNull().orEmpty() else "${dates.first()}–${dates.last()}"
}

private val EmergencyInk = Color(0xFFDC2626.toInt())

@Composable
private fun PathKey(emergency: Boolean) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyMark(ink, arrow = false)
        Text(
            LanguageText.text("reports.aircraft.first"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        KeyMark(ink, arrow = true)
        Text(
            LanguageText.text("reports.aircraft.last"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (emergency) {
            EmergencyKeyMark()
            Text(
                LanguageText.text("reports.aircraft.emergency"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmergencyKeyMark() {
    Canvas(Modifier.size(14.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        fun diamond(size: Float) = Path().apply {
            moveTo(c.x, c.y - size)
            lineTo(c.x + size, c.y)
            lineTo(c.x, c.y + size)
            lineTo(c.x - size, c.y)
            close()
        }
        drawPath(diamond(5.4f), Color.White)
        drawPath(diamond(3.6f), EmergencyInk)
    }
}

@Composable
private fun KeyMark(ink: Color, arrow: Boolean) {
    Canvas(Modifier.size(14.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        if (!arrow) {
            drawCircle(Color.White, radius = 5.2f, center = c)
            drawCircle(ink, radius = 5.2f, center = c, style = Stroke(width = 1.6f))
            return@Canvas
        }
        val path = Path().apply {
            moveTo(c.x + 5.4f, c.y)
            lineTo(c.x - 3.6f, c.y - 3.6f)
            lineTo(c.x - 3.6f, c.y + 3.6f)
            close()
        }
        drawPath(path, Color.White)
        drawPath(
            Path().apply {
                moveTo(c.x + 4.2f, c.y)
                lineTo(c.x - 2.4f, c.y - 2.6f)
                lineTo(c.x - 2.4f, c.y + 2.6f)
                close()
            },
            ink,
        )
    }
}

private fun visitLength(pass: AircraftHistory.Pass): String {
    val mins = AircraftHistory.lengthMinutes(pass.start, pass.end)
    if (mins < 1) return LanguageText.text("reports.aircraft.under")
    if (mins < 60) {
        return LanguageText.text("reports.aircraft.minutes", mapOf("n" to mins.toString()))
    }
    val hours = mins / 60
    val rest = mins % 60
    if (rest == 0) return LanguageText.text("reports.aircraft.hour", mapOf("h" to hours.toString()))
    return LanguageText.text(
        "reports.aircraft.hours",
        mapOf("h" to hours.toString(), "m" to rest.toString()),
    )
}

private fun span(pass: AircraftHistory.Pass, zone: ZoneId): String {
    val start = formatClock(pass.start, zone)
    val end = formatClock(pass.end, zone)
    return if (start == end) start else "$start–$end"
}

private fun emergencyWhen(span: AircraftHistory.Emergency, zone: ZoneId): String {
    val start = formatClock(span.start, zone)
    val end = formatClock(span.end, zone)
    return if (start == end) start else "$start–$end"
}

private fun streetOf(pass: AircraftHistory.Pass, streets: Map<String, String>): String {
    val lat = pass.lat ?: return ""
    val lon = pass.lon ?: return ""
    return Geo.nameNear(lat, lon, streets, 100.0).orEmpty()
}

private fun radioLine(pass: AircraftHistory.Pass): String {
    val bits = ArrayList<String>(2)
    if (pass.ble) bits += LanguageText.text("ui.bluetooth.label")
    if (pass.wifi) bits += LanguageText.text("radio.wifi")
    return bits.joinToString(" · ")
}

private fun formatClock(at: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(at).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", LanguageText.dateLocale()))

private fun formatDate(at: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(at).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM", LanguageText.dateLocale()))
