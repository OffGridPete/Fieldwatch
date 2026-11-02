package app.fieldwatch.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.PlaceReport
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import java.time.ZoneId

/**
 * Radios heard at two or more places in the last 7 days.
 * The map is where this phone was. Each radio keeps one color on the map and in the list.
 */
@Composable
internal fun PlaceReportPage(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onOpen: (String) -> Unit,
) {
    val settings = state.settings
    if (!settings.placeWatch) {
        Note(LanguageText.text("reports.places.off"))
        return
    }
    val streets by vm.placeStreetNames.collectAsStateWithLifecycle()
    val mapKeyRev by vm.mapKeyRevision.collectAsStateWithLifecycle()
    LaunchedEffect(settings.onlineLookup, settings.demoMode) {
        vm.refreshPlaceReportStreets()
    }
    val rows = PlaceReport.rows(
        records = vm.placeRecords(),
        devices = state.devices,
        fleets = state.fleets,
        watchlist = state.watchlist,
        now = System.currentTimeMillis(),
        zone = ZoneId.systemDefault(),
        demo = settings.demoMode,
        streets = if (settings.demoMode || !settings.onlineLookup) emptyMap() else streets,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Note(LanguageText.text("reports.places.blurb"))
        if (settings.demoMode) {
            Text(
                LanguageText.text("reports.places.private"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (rows.isEmpty()) {
            Note(LanguageText.text("reports.places.empty"))
            return@Column
        }
        val focus = PlaceReport.newest(rows)
        val pins = PlaceReport.pins(rows)
        val mapKey = remember(settings.mapBasemap, mapKeyRev) { vm.mapKey(settings.mapBasemap) }
        if (focus != null && pins.isNotEmpty() && settings.onlineLookup && !settings.demoMode) {
            PlaceStopsMap(
                pins = pins,
                focusLat = focus.lat,
                focusLon = focus.lon,
                basemap = settings.mapBasemap,
                mapKey = mapKey,
                overlays = settings.mapOverlays,
            )
        }
        rows.forEach { row ->
            PlaceReportRow(row) { onOpen(row.deviceKey) }
        }
    }
}

@Composable
private fun PlaceReportRow(row: PlaceReport.Row, onOpen: () -> Unit) {
    val color = Color(row.color)
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
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(12.dp)
                    .background(color, CircleShape),
            )
            Column(
                Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        LanguageText.text("reports.places.count", mapOf("n" to row.count.toString())),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                }
                if (row.address.isNotEmpty()) {
                    Text(
                        row.address,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (row.visits.isNotEmpty()) {
                    PlaceVisits(row.visits)
                }
                if (row.note.isNotEmpty()) {
                    Text(
                        row.note,
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceVisits(visits: List<PlaceReport.Visit>) {
    Column(Modifier.padding(top = 8.dp)) {
        visits.forEachIndexed { index, visit ->
            if (index > 0) {
                HorizontalDivider(
                    Modifier.padding(start = 28.dp, top = 7.dp, bottom = 7.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    visit.number.toString(),
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        visit.time,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val where = visit.street.ifBlank { visit.name }
                    if (where.isNotEmpty()) {
                        Text(
                            where,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (visit.street.isNotEmpty() && visit.name.isNotEmpty()) {
                        Text(
                            visit.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (visit.coord.isNotEmpty()) {
                        Text(
                            visit.coord,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
