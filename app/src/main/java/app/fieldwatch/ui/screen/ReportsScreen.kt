package app.fieldwatch.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.LogExportKind
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.Sit
import app.fieldwatch.domain.SitDiff
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.SitPathCanvas
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
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
                    "Privacy mode is on. MAC tails in Debrief, sit compare, AI Export (sit or compare), and detail Share are **:**:**. GPS coordinates are masked. The log file, sit export, and GPX / KML / WiGLE files still have full addresses and lat/lon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SectionCard("Sits") {
                Text(
                    "A sit is a named window of radios heard here. The selection below drives Path, Debrief, and Compare’s this-sit side: open sit, a selected saved sit, or last 15 minutes if you never start one.",
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
                            "No sit running. Start sit here. Path and Debrief stay last 15 minutes until you do."
                        } else {
                            "No sit running. Start sit here. Path and Debrief use the selected sit."
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
                        subtitle = "Path and Debrief use RAM, not a saved sit.",
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
                            "End sit to pick a saved one for Path and Debrief.",
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
                    "North up. This phone. Extra attention and Named radios as dots — hear-points, not radio fixes. No map tiles; airplane mode is fine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val model = pathModel
                if (model == null || model.emptyHint != null) {
                    Text(
                        model?.emptyHint ?: "Tag detections with GPS and walk, or open a sit that recorded a path.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "${model.title} · ${model.lengthM.toInt()} m path · ${model.spanM.toInt()} m span",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SitPathCanvas(model, onOpenRadio = onOpenPathRadio)
                    Text(
                        "Stacked count: tap the number for names. Tap again to close. Isolated dots and the list still open the radio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Line = this phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Red = Extra attention",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "Blue = Named",
                                style = MaterialTheme.typography.bodySmall,
                                color = Cyan.nightIf(LocalNightMode.current),
                            )
                        }
                    }
                    if (model.dots.isEmpty()) {
                        Text(
                            "No Extra attention or Named radios with a GPS stamp on this path.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        model.dots.forEachIndexed { i, dot ->
                            val mac = MacUtil.screenMac(dot.mac, settings.demoMode)
                            val kind = if (dot.kind == RadioKind.WIFI) "WIFI" else "BLE"
                            val tag = if (dot.extraAttention) "Extra attention" else ""
                            val fleets = dot.fleetNames.joinToString(" · ")
                            val title = buildString {
                                append("${i + 1}. $kind  ${dot.label}")
                                if (dot.label != mac && mac.isNotBlank()) append("  $mac")
                            }
                            val sub = listOfNotNull(tag.ifBlank { null }, fleets.ifBlank { null }).joinToString(" · ")
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenPathRadio(dot.key) }
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(title, style = MaterialTheme.typography.bodyMedium)
                                if (sub.isNotEmpty()) {
                                    Text(
                                        sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (dot.extraAttention) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        val noted = model.dots.filter { it.observerNotes.trim().isNotEmpty() }
                        if (noted.isNotEmpty()) {
                            Text(
                                "Observer notes",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            noted.forEach { dot ->
                                val mac = MacUtil.screenMac(dot.mac, settings.demoMode)
                                val kind = if (dot.kind == RadioKind.WIFI) "WIFI" else "BLE"
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenPathRadio(dot.key) }
                                        .padding(vertical = 4.dp),
                                ) {
                                    Text(
                                        "$kind  ${dot.label}  $mac",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        dot.observerNotes.trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                        }
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
            FieldwatchActionButton(
                onClick = vm::startAiExport,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("AI Export") }
            Text(
                "Paste-ready addendum: rates, RSSI bands, Extra attention and tracking IDs. Does not reprint Debrief inventories. One-radio AI Export is on detail.",
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
                hint = "One row per unique radio in this sit (or last 15 minutes). Not the rotating log. GPX / KML include this phone’s path as a track plus hear-points. Fieldwatch does not upload. Privacy mode does not mask this file.",
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
                        "Save a second sit to compare. Start sit, then End sit. Last 15 minutes can be this sit.",
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
                    "Same report, two formats. Presence only — only in this sit, only in the second, in both. Kind + MAC. Extra attention and Named radios are marked. Not a radio fix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FieldwatchActionButton(
                    onClick = vm::startSitCompareAiExport,
                    enabled = !exporting && state.sit.compareId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("AI Export") }
                Text(
                    "Paste-ready addendum: overlap, exclusive Extra attention / Named radios, what another sit would shrink. Does not reprint the compare lists. Sit report AI Export stays this window only.",
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
                "Unmatched radios in the log that share a unique ID — not every unknown. You review; nothing is added until you Save.",
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
                hint = "The rotating file is JSON lines. CSV is the same rows as a spreadsheet. GPX — GPS Exchange, KML — Google Earth, and WiGLE CSV — wigle.net are hear-points: where this phone was when it heard each radio, not a radio fix. Tag detections with GPS and logging on. Share uses the Android share sheet — Fieldwatch does not upload.",
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
                        Text("This deletes all rotated CSV/JSON files on the phone. It cannot be undone. Live scanning will start a new empty log.")
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

private fun compareThisCaption(state: FieldwatchUi): String {
    val open = state.sit.open
    if (open != null) {
        return "This sit: ${open.name} — named window (up to ${Sit.RADIO_CAP}). Same as Debrief."
    }
    val selected = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
    if (selected != null) {
        return "This sit: ${selected.name} — named window (up to ${Sit.RADIO_CAP}). Same as Debrief."
    }
    return "This sit: last 15 minutes in memory (about 400 radios). Same as Debrief."
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
    if (open != null) {
        return "This sit (${open.name}) — same window as Path. GPS following test when tagging is on and you have moved. Not a legal finding."
    }
    val selected = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
    if (selected != null) {
        return "Sit: ${selected.name} — same window as Path. GPS following test when tagging is on and you have moved. Not a legal finding."
    }
    return "Last 15 minutes in memory — same window as Path. Two formats. GPS following test when tagging is on and you have moved. Not a legal finding."
}
