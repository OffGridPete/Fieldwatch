package app.fieldwatch.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.ui.RadioClassBadge
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.domain.LogExportKind
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.Sit
import app.fieldwatch.domain.SitDiff
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.ui.component.AircraftAmber
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.SitPathCanvas
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.FieldwatchSwitch
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.theme.Cyan
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    exporting: Boolean,
    onSaveToStorage: () -> Unit,
    onSaveSitToStorage: () -> Unit,
    onSignatureCandidates: () -> Unit,
    onOpenPathRadio: (String) -> Unit = {},
) {
    val settings = state.settings
    var confirmClear by remember { mutableStateOf(false) }
    var startSit by remember { mutableStateOf(false) }
    var sitNameDraft by remember { mutableStateOf("") }
    var renameSitId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteSitId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar("Reports") },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (settings.demoMode) {
                Text(
                    "Privacy mode is on. Debrief, Compare, and AI Export hide the last half of each MAC, the coordinates, and a Remote ID id. The log and sit export still have the full addresses and coordinates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SectionCard("Sits") {
                Text(
                    "A sit is the radios heard during a named stretch of time. Path, Debrief, and Compare use the open sit, a saved sit you pick, or the last 15 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val open = state.sit.open
                if (open != null) {
                    val dur = Sit.fmtDuration(open.durationMs())
                    Text(
                        "This sit: ${open.name} · $dur · ${state.sit.radioCount} radios",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FieldwatchActionButton(
                        onClick = vm::endSit,
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("End sit") }
                } else {
                    FieldwatchActionButton(
                        onClick = {
                            sitNameDraft = vm.defaultSitName()
                            startSit = true
                        },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Start sit") }
                    Text(
                        if (state.sit.closed.isEmpty()) {
                            "No sit is running. Path and Debrief use the last 15 minutes until you start one."
                        } else {
                            "No sit is running. Path and Debrief use the sit you pick."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.sit.closed.isEmpty() && open == null) {
                    Text(
                        "No saved sits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.sit.closed.isNotEmpty()) {
                    val pickEnabled = open == null && !exporting
                    SitChoiceRow(
                        selected = state.sit.selectedId == null,
                        enabled = pickEnabled,
                        title = "Last 15 minutes",
                        subtitle = "Uses what is still in memory, not a saved sit.",
                        onSelect = { vm.selectSit(null) },
                    )
                    state.sit.closed.forEach { row ->
                        val dur = Sit.fmtDuration(row.durationMs())
                        val extra = if (row.extraAttentionCount > 0) {
                            " · Extra attention ${row.extraAttentionCount}"
                        } else {
                            ""
                        }
                        SitChoiceRow(
                            selected = state.sit.selectedId == row.id,
                            enabled = pickEnabled,
                            title = row.name,
                            subtitle = "${Sit.defaultName(row.startAt)} · $dur · ${row.radioCount} radios$extra",
                            onSelect = { vm.selectSit(row.id) },
                        )
                    }
                    if (open != null) {
                        Text(
                            "End this sit to pick a saved one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val picked = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FieldwatchActionButton(
                            onClick = {
                                if (picked != null) {
                                    renameSitId = picked.id
                                    renameDraft = picked.name
                                }
                            },
                            enabled = !exporting && picked != null,
                            modifier = Modifier.weight(1f),
                        ) { Text("Rename") }
                        FieldwatchActionButton(
                            onClick = { if (picked != null) deleteSitId = picked.id },
                            enabled = !exporting && picked != null,
                            modifier = Modifier.weight(1f),
                        ) { Text("Delete") }
                    }
                    FieldwatchActionButton(
                        onClick = { confirmDeleteAll = true },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Delete all sits") }
                }
            }

            val pathModel by vm.sitPath.collectAsStateWithLifecycle()
            LaunchedEffect(state.sit.selectedId, state.sit.open?.id) {
                while (true) {
                    vm.refreshSitPath()
                    kotlinx.coroutines.delay(3_000L)
                }
            }
            SectionCard("Path") {
                Text(
                    "North is up. The line is this phone, with times along it. Black is the start. Blue is you. One icon is one radio. A number is several radios in one spot. A thick green stretch is a stay. An advertised position is the last one that radio sent. A red diamond is where this phone was during a GNSS detection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val model = pathModel
                val showWalk = model != null && model.emptyHint == null
                val showAircraft = model != null && model.aircraftCards.isNotEmpty()
                if (model == null || (!showWalk && !showAircraft)) {
                    Text(
                        model?.emptyHint ?: "Turn on Tag detections with GPS and walk, or pick a sit that recorded a path.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val pathTiles by vm.pathTiles.collectAsStateWithLifecycle()
                    val aircraftTiles by vm.pathAircraftTiles.collectAsStateWithLifecycle()
                    if (!showWalk) {
                        Text(
                            model.emptyHint ?: "Turn on Tag detections with GPS and walk, or pick a sit that recorded a path.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showWalk) {
                    val stopN = model.dots.size
                    val gnssN = model.gnss.size
                    Text(
                        buildString {
                            append("${model.title} · ${model.lengthM.toInt()} m path · ${model.spanM.toInt()} m span")
                            if (stopN > 0) {
                                append(" · $stopN alert")
                                if (stopN != 1) append("s")
                            }
                            if (gnssN > 0) {
                                append(" · $gnssN GNSS")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SitPathCanvas(model, tiles = pathTiles, onOpenRadio = onOpenPathRadio)
                    Text(
                        "Tap a number for the radios there. Tap one icon for that radio. Tap a row to open it. Tap a red diamond for that detection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (model.craft.isNotEmpty() || model.pilots.isNotEmpty() || model.gnss.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (model.craft.isNotEmpty()) {
                                val multi = model.craft.any { it.samples.size >= 2 }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (multi) AdvertisedTrackSwatch() else AdvertisedRingSwatch()
                                    Text(
                                        if (multi) {
                                            "= advertised track within 2 km of this path"
                                        } else {
                                            "= one advertised position within 2 km of this path"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            if (model.pilots.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    PilotSwatch()
                                    Text(
                                        "= pilot",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (model.gnss.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    GnssSwatch()
                                    Text(
                                        "= where this phone was during a GNSS detection",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    val alertsOnACard = model.aircraftCards.any { it.dots.isNotEmpty() }
                    if (model.dots.isEmpty() && model.gnss.isEmpty() && !alertsOnACard) {
                        Text(
                            "No alerts with a GPS stamp on this path.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (model.dots.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            model.dots.forEachIndexed { i, dot ->
                                PathRadioRow(
                                    index = i + 1,
                                    dot = dot,
                                    demoMode = settings.demoMode,
                                    onOpen = { onOpenPathRadio(dot.key) },
                                )
                            }
                        }
                    }
                    if (model.gnss.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            model.gnss.forEach { pin ->
                                GnssPathNote(pin)
                            }
                        }
                    }
                    }
                    model.aircraftCards.forEachIndexed { index, card ->
                        val fixes = card.craft.sumOf { it.samples.size }
                        Text(
                            privacyAircraftHeading(card.title, settings.demoMode),
                            style = MaterialTheme.typography.titleSmall,
                            color = AircraftAmber,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            buildString {
                                append(
                                    if (fixes == 1) "1 advertised fix" else "$fixes advertised fixes",
                                )
                                if (card.lengthM >= 1.0) append(" · ${card.lengthM.toInt()} m")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (card.dots.isNotEmpty()) {
                            Text(
                                if (card.dots.size == 1) "1 alert" else "${card.dots.size} alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SitPathCanvas(
                            card,
                            tiles = aircraftTiles.getOrElse(index) { emptyList() },
                            onOpenRadio = onOpenPathRadio,
                        )
                        if (card.dots.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                card.dots.forEachIndexed { i, dot ->
                                    PathRadioRow(
                                        index = i + 1,
                                        dot = dot,
                                        demoMode = settings.demoMode,
                                        onOpen = { onOpenPathRadio(dot.key) },
                                    )
                                }
                            }
                        }
                        if (card.caption.isNotBlank()) {
                            Text(
                                privacyAircraftCaption(card.caption, card.title, settings.demoMode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (model.looseAdvertised > 0) {
                        Text(
                            if (model.looseAdvertised == 1) {
                                "An advertised position with no UAS id is in the sit report."
                            } else {
                                "Advertised positions with no UAS id are in the sit report."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionCard("Sit report") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldwatchActionButton(
                    onClick = vm::startFieldDebrief,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text("Debrief (text)") }
                FieldwatchActionButton(
                    onClick = vm::startFieldDebriefPdf,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text("Debrief (PDF)") }
            }
            Text(
                sitReportCaption(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Show unmatched rotating BLE",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FieldwatchSwitch(
                    settings.debriefShowUnmatchedRandomBle,
                    { on -> vm.updateSettings { it.copy(debriefShowUnmatchedRandomBle = on) } },
                )
            }
            Text(
                "Leaves unmatched Bluetooth radios that keep changing address out of the Debrief lists. The counts still include them. Extra attention, named signatures, bookmarks, and radios that sent a position stay. Sit export still has every radio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Show all radios",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FieldwatchSwitch(
                    settings.debriefShowAllRadios,
                    { on -> vm.updateSettings { it.copy(debriefShowAllRadios = on) } },
                )
            }
            Text(
                "Debrief and Compare open with counts. A radio is listed when it is Extra attention, has a custom name, is marked Mine, or is bookmarked. Compare also lists a decoded value that changed. Turn this on for the full list. The PDF draws the counts as bars. Sit export still has every radio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startAiExport,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("AI Export") }
            Text(
                "A short note for pasting into a chat: rates, signal bands, Extra attention, and tracking ids. It does not repeat the Debrief lists. A one-radio export is on that radio’s page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("Sit export") {
            val sitKind by vm.sitExportKind.collectAsStateWithLifecycle()
            val sitRadios by vm.sitExportRadios.collectAsStateWithLifecycle()
            ExportFormatBlock(
                kind = sitKind,
                radios = sitRadios,
                exporting = exporting,
                onKind = vm::setSitExportKind,
                onRadios = vm::setSitExportRadios,
                onShare = vm::startSitExport,
                onSave = onSaveSitToStorage,
                hint = "One row per radio in this sit, or the last 15 minutes. Not the log. CSV and JSON include the matched signatures. GPX and KML include this phone’s path and a point for each detection. Fieldwatch does not upload the file. Privacy mode does not hide addresses in it.",
            )
            }

            SectionCard("Compare sits") {
                Text(
                    compareThisCaption(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val thisSaved = SitDiff.thisSavedId(state.sit.open, state.sit.selectedId)
                val choices = SitDiff.secondSitChoices(state.sit.closed, thisSaved)
                if (choices.isEmpty()) {
                    Text(
                        "Save a second sit to compare. Start a sit, then end it. The last 15 minutes can be this sit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Second sit",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    choices.forEach { row ->
                        val dur = Sit.fmtDuration(row.durationMs())
                        SitChoiceRow(
                            selected = state.sit.compareId == row.id,
                            enabled = !exporting,
                            title = row.name,
                            subtitle = "${Sit.defaultName(row.startAt)} · $dur · ${row.radioCount} radios",
                            onSelect = { vm.selectCompareSit(row.id) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldwatchActionButton(
                        onClick = vm::startSitCompare,
                        enabled = !exporting && state.sit.compareId != null,
                        modifier = Modifier.weight(1f),
                    ) { Text("Compare (text)") }
                    FieldwatchActionButton(
                        onClick = vm::startSitComparePdf,
                        enabled = !exporting && state.sit.compareId != null,
                        modifier = Modifier.weight(1f),
                    ) { Text("Compare (PDF)") }
                }
                Text(
                    "The same report as text or PDF. Radios only in this sit, only in the second, and in both. Extra attention and named radios are marked. These points are where this phone was, not a location from the other radio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FieldwatchActionButton(
                    onClick = vm::startSitCompareAiExport,
                    enabled = !exporting && state.sit.compareId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("AI Export") }
                Text(
                    "A short note on what overlaps and what is only in one sit. It does not repeat the compare lists. The sit-report export stays on this window only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("Catalog") {
            FieldwatchActionButton(
                onClick = onSignatureCandidates,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Signature candidates") }
            Text(
                "Unmatched radios in the log that share an id. Not every unknown radio. Nothing is added until you save it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("Log export") {
            Text(
                "${state.logLines} lines this session  ·  ${vm.logBytes() / 1024} KB on disk" +
                    if (settings.loggingEnabled) "" else "  ·  logging off",
                style = MaterialTheme.typography.bodySmall,
            )
            val logKind by vm.logExportKind.collectAsStateWithLifecycle()
            val logRadios by vm.logExportRadios.collectAsStateWithLifecycle()
            ExportFormatBlock(
                kind = logKind,
                radios = logRadios,
                exporting = exporting,
                onKind = vm::setLogExportKind,
                onRadios = vm::setLogExportRadios,
                onShare = vm::startExport,
                onSave = onSaveToStorage,
                hint = "The file on disk is one detection per line. CSV is those rows in a spreadsheet. GPX, KML, and WiGLE are points where this phone was when it heard each radio, not a location from the other radio. Tag detections with GPS and logging both have to be on. Share uses the Android share sheet. Fieldwatch does not upload.",
            )
            FieldwatchActionButton(
                onClick = { confirmClear = true },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset / clear log")
            }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Clear the log?") },
                    text = {
                        Text("Deletes the log files on this phone. This cannot be undone. Scanning starts a new empty log.")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmClear = false
                            vm.clearLogs()
                        }) { Text("Clear log") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                    },
                )
            }
            }
        }
    }
    if (startSit) {
        AlertDialog(
            onDismissRequest = { startSit = false },
            title = { Text("Start sit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldwatchOutlinedField(
                        value = sitNameDraft,
                        onValueChange = { sitNameDraft = it.take(Sit.NAME_MAX) },
                        label = "Name",
                    )
                    Text(
                        "Debrief and AI Export use this window until you end it. The Live list is unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Sit.dropWarning(state.sit.closed)?.let { warn ->
                        Text(
                            warn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    startSit = false
                    vm.startSit(sitNameDraft)
                }) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { startSit = false }) { Text("Cancel") }
            },
        )
    }
    val renaming = renameSitId
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renameSitId = null },
            title = { Text("Rename sit") },
            text = {
                FieldwatchOutlinedField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(Sit.NAME_MAX) },
                    label = "Name",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameSitId = null
                    vm.renameSit(renaming, renameDraft)
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameSitId = null }) { Text("Cancel") }
            },
        )
    }
    val deleting = deleteSitId
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteSitId = null },
            title = { Text("Delete this sit?") },
            text = { Text("Removes the saved sit from this phone. The log is unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteSitId = null
                    vm.deleteSit(deleting)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteSitId = null }) { Text("Cancel") }
            },
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all sits?") },
            text = { Text("Removes saved sits from this phone. An open sit is not deleted. The log is unchanged.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    vm.deleteAllSits()
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SitChoiceRow(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Column(Modifier.padding(start = 8.dp).fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected && enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdvertisedTrackSwatch() {
    Canvas(Modifier.width(28.dp).height(10.dp)) {
        val dash = 3.dp.toPx()
        val gap = 4.5.dp.toPx()
        drawLine(
            Color.White,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), 0f),
        )
    }
}

@Composable
private fun AdvertisedRingSwatch() {
    Canvas(Modifier.size(12.dp)) {
        drawCircle(
            Color.White,
            radius = size.minDimension / 2f - 1.dp.toPx(),
            style = Stroke(width = 1.6.dp.toPx()),
        )
    }
}

@Composable
private fun GnssSwatch() {
    val ink = Color(0xFFB91C1C).nightIf(LocalNightMode.current)
    Canvas(Modifier.size(14.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        fun diamond(rad: Float) = Path().apply {
            moveTo(c.x, c.y - rad)
            lineTo(c.x + rad * 0.86f, c.y)
            lineTo(c.x, c.y + rad)
            lineTo(c.x - rad * 0.86f, c.y)
            close()
        }
        drawPath(diamond(r), Color.White)
        drawPath(diamond(r * 0.72f), ink)
    }
}

@Composable
private fun GnssPathNote(pin: SitPathPlot.GnssPin) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "${pin.title()} · ${pin.confidence}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            pin.whenLine(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            pin.detail.trim().ifBlank { "Signal changed." },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PilotSwatch() {
    val painter = rememberVectorPainter(Icons.Outlined.Person)
    Canvas(Modifier.size(18.dp)) {
        val radius = size.minDimension / 2f
        val disc = radius * 0.86f
        drawCircle(Color.White, radius = radius)
        drawCircle(Color(0xFFF4F7FB), radius = disc)
        drawCircle(Color(0xFF3D4A55), radius = disc, style = Stroke(width = 1.2.dp.toPx()))
        val icon = disc * 1.35f
        translate((size.width - icon) / 2f, (size.height - icon) / 2f) {
            with(painter) {
                draw(Size(icon, icon), colorFilter = ColorFilter.tint(Color(0xFF3D4A55)))
            }
        }
    }
}

/** A one-token serial is the UAS id. A name with a space stays. A MAC keeps its prefix. */
private fun privacyAircraftHeading(title: String, demo: Boolean): String {
    if (!demo || title.isBlank()) return title
    val serial = !title.contains(' ') && !title.contains(':') &&
        title.length >= 6 && title.any { it.isDigit() } && title.any { it.isLetter() }
    if (serial) return "Aircraft"
    return MacUtil.redactMacIn(title, title, true)
}

private fun privacyAircraftCaption(caption: String, title: String, demo: Boolean): String {
    if (!demo || caption.isBlank()) return caption
    val stripped = if (title.isBlank()) caption else caption.replace(title, "masked", ignoreCase = true)
    return Geo.redactCoordsIn(MacUtil.redactMacIn(stripped, title, true), true)
}

@Composable
private fun PathRadioRow(
    index: Int,
    dot: SitPathPlot.Dot,
    demoMode: Boolean,
    onOpen: () -> Unit,
) {
    val mac = MacUtil.screenMac(dot.mac, demoMode)
    val named = dot.label.isNotBlank() && !dot.label.equals(dot.mac, ignoreCase = true)
    val label = MacUtil.redactMacIn(dot.label, dot.mac, demoMode)
    val fleets = dot.fleetNames.joinToString(" · ")
    val note = dot.observerNotes.trim()
    val accent = (if (dot.accentArgb != 0) Color(dot.accentArgb) else MaterialTheme.colorScheme.onSurfaceVariant)
        .nightIf(LocalNightMode.current)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$index",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        RadioClassBadge(dot.classKind, accent, compact = true)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            if (named) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (mac.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioKindMark(dot.kind, size = 13.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        mac,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (fleets.isNotEmpty()) {
                Text(
                    fleets,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.isNotEmpty()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Cyan.nightIf(LocalNightMode.current),
                )
            }
        }
    }
}

private fun compareThisCaption(state: FieldwatchUi): String {
    val open = state.sit.open
    if (open != null) return "This sit: ${open.name}. Same window as Debrief."
    val selected = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
    if (selected != null) return "This sit: ${selected.name}. Same window as Debrief."
    return "This sit: the last 15 minutes still in memory, about 400 radios. Same window as Debrief."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatBlock(
    kind: LogExportKind,
    radios: LogExportRadios,
    exporting: Boolean,
    onKind: (LogExportKind) -> Unit,
    onRadios: (LogExportRadios) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    hint: String,
) {
    var openFormat by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = openFormat,
        onExpandedChange = { openFormat = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        FieldwatchDropdownField("Format", kind.label, openFormat)
        ExposedDropdownMenu(openFormat, { openFormat = false }) {
            LogExportKind.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        onKind(item)
                        openFormat = false
                    },
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LogExportRadios.entries.forEach { item ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = radios == item,
                        onClick = { onRadios(item) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = radios == item,
                    onClick = { onRadios(item) },
                    enabled = !exporting,
                )
                Text(item.label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    FieldwatchActionButton(
        onClick = onShare,
        enabled = !exporting,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Share") }
    FieldwatchActionButton(
        onClick = onSave,
        enabled = !exporting,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save to SD card / storage…") }
    Text(
        hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun sitReportCaption(state: FieldwatchUi): String {
    val open = state.sit.open
    val selected = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
    val window = when {
        open != null -> "This sit (${open.name})"
        selected != null -> "Sit: ${selected.name}"
        else -> "Last 15 minutes still in memory"
    }
    return "$window. Same window as Path. Moving with you runs when Tag detections with GPS is on and you have moved. This is not a legal finding."
}
