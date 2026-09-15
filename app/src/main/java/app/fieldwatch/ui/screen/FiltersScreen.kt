package app.fieldwatch.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSlider
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.FilterLogic
import app.fieldwatch.domain.FilterPreset
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.ui.ClassGlyphs
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.spectreSectionFill
import app.fieldwatch.ui.component.spectreTileEdge
import app.fieldwatch.ui.component.spectreTileFill
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(state: FieldwatchUi, vm: FieldwatchViewModel) {
    var presetName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<FilterPreset?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val filter = state.filter
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar("Filters") },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Presets") {
            Text(
                "Tap to replace the whole filter. Long-press a chip to delete it. " +
                    "A short stock set ships; Save current as… adds your own (Cameras, plaza −80, …). " +
                    "Stock chips you delete come back with Settings → Restore default signatures & presets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.presets.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { preset ->
                                PresetChip(
                                    name = preset.name,
                                    selected = preset.filter == filter,
                                    onApply = { vm.applyPreset(preset) },
                                    onLongPress = { pendingDelete = preset },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    presetName,
                    { presetName = it },
                    label = { Text("Save current as…") },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    if (presetName.isNotBlank()) {
                        vm.savePreset(presetName.trim())
                        presetName = ""
                    }
                }) { Text("Save") }
            }
            }

            SectionCard("Radios") {
            Text(
                "These are include switches. Turn both on to see Wi-Fi and BLE together. A single device is never both.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.showWifi && filter.showBle,
                    onClick = { vm.updateFilter { it.copy(showWifi = true, showBle = true) } },
                    label = { Text("Both") },
                )
                FieldwatchFilterChip(
                    selected = filter.showWifi && !filter.showBle,
                    onClick = { vm.updateFilter { it.copy(showWifi = true, showBle = false) } },
                    label = { Text("Wi-Fi only") },
                )
                FieldwatchFilterChip(
                    selected = filter.showBle && !filter.showWifi,
                    onClick = { vm.updateFilter { it.copy(showWifi = false, showBle = true) } },
                    label = { Text("BLE only") },
                )
            }
            }

            SectionCard("Moving with you") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Moving with you", Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.movingWithYou,
                    { on ->
                        vm.updateFilter { current ->
                            if (!on) current.copy(movingWithYou = false)
                            else {
                                // Follow test is all radios. Leftover Trackers / Show only hides
                                // unmatched rows; AirTags rotate, so Live looks empty.
                                val hiding = current.useClassFilter && current.excludeClasses
                                current.copy(
                                    movingWithYou = true,
                                    namedOnly = false,
                                    customNamesOnly = false,
                                    watchedOnly = false,
                                    useClassFilter = hiding,
                                    excludeClasses = hiding,
                                    classes = if (hiding) current.classes else emptySet(),
                                    includeSignatures = false,
                                )
                            }
                        }
                    },
                )
            }
            Text(
                when {
                    !state.settings.tagLocation ->
                        "Turn on Settings → Tag detections with GPS, then walk or drive. " +
                            "Only loud radios that stay with you along the path. " +
                            "The switch starts a follow test on all radios (clears Signatures only / Show only / Named radios only / Watched only). " +
                            "Or tap the Moving with you preset at the top."
                    state.operatorSpanM < 45.0 ->
                        "GPS path so far ${state.operatorSpanM.toInt()} m. Keep moving (~50 m). " +
                            "If this stays 0 while you drive, Location is not giving a live fix " +
                            "(set Location to high accuracy). Last-known-only is not enough. " +
                            "A second iPhone usually will not match: BLE MAC rotation starts a new radio." +
                            when {
                                filter.customNamesOnly ->
                                    " Named radios only is also on — unlabeled radios stay hidden."
                                filter.watchedOnly ->
                                    " Watched only is also on — unwatched radios stay hidden."
                                filter.namedOnly || filter.namedOnlyImplied() ->
                                    " Signatures only / Show only is also on — unmatched radios stay hidden."
                                else -> ""
                            }
                    filter.customNamesOnly || filter.watchedOnly || filter.namedOnly || filter.namedOnlyImplied() ->
                        "GPS path ${state.operatorSpanM.toInt()} m. Signatures only, class Show only, " +
                            "Named radios only, or Watched only is also on, so only those radios can co-travel. " +
                            "Tap the Moving with you preset to test all radios. A tag in your bag or car should match; house APs should not."
                    else ->
                        "GPS path ${state.operatorSpanM.toInt()} m. Loud radios heard along that " +
                            "path at a fairly steady level — not ones that only appear when you " +
                            "arrive. A tag in your bag or car will match. House APs should not. " +
                            "A phone’s rotating BLE address will not stitch as one follower. " +
                            "Live → Start over clears the path and trails so you can test again."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("New detections") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.arrivalsLearning) "New detections only  ·  learning" else "New detections only",
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(
                    filter.arrivalsOnly,
                    { on -> vm.updateFilter { it.copy(arrivalsOnly = on) } },
                )
            }
            Text(
                if (filter.arrivalsOnly) {
                    "Mark seen and Reset seen sit on Live, above the tabs. " +
                        when {
                            state.arrivalsLearning ->
                                "Learning sitting Wi-Fi into already-seen."
                            state.hiddenKnown > 0 ->
                                "${state.hiddenKnown} already seen are hidden."
                            else ->
                                "Already seen is 0."
                        }
                } else {
                    "Hide radios already here so only new ones show on Live. " +
                        "Mark seen / Reset seen appear above the tabs on Live while this is on. " +
                        "Brief hold still sets how long a new radio stays after the last packet. " +
                        "Randomized BLE addresses look new."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("Who stays") {
            val namedImplied = filter.namedOnlyImplied()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Signatures only (hide unmatched)",
                    Modifier.weight(1f),
                    color = if (namedImplied) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                FieldwatchSwitch(
                    checked = filter.namedOnly || namedImplied,
                    onCheckedChange = { on ->
                        if (!namedImplied) vm.updateFilter { it.copy(namedOnly = on) }
                    },
                    enabled = !namedImplied,
                )
            }
            if (namedImplied) {
                Text(
                    "Show only already hides unmatched radios. Turn Show only (class or selected signatures) off to use this switch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Watched only", Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.watchedOnly,
                    { on -> vm.updateFilter { it.copy(watchedOnly = on) } },
                )
            }
            Text(
                "Only radios that match a bookmarked signature, or a Named radio with Alert on. " +
                    "Hide these still applies (Watched only + Hide Surveillance drops bookmarked cameras). " +
                    "Label-only names stay on Named radios only. Bookmark on Signatures; Alert on detail.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Named radios only", Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.customNamesOnly,
                    { on -> vm.updateFilter { it.copy(customNamesOnly = on) } },
                )
            }
            Text(
                "Only radios you gave a custom name. Alert can still be off. Settings → Named radios. " +
                    "A random / privacy MAC will not follow a rotation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hide Fast Pair account-key", Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.hideFastPairAccountKey,
                    { on -> vm.updateFilter { it.copy(hideFastPairAccountKey = on) } },
                )
            }
            Text(
                "Plaza noise: already-paired Fast Pair chips with no other signature. " +
                    "Keeps pairing-mode (tap-to-pair model ID). Hide selected Fast Pair still drops pairing-mode too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("Signature classes") {
            Text(
                "Live only — signatures still label, log, and can beep. " +
                    "Trackers / Hide trackers / Hide phones presets pick a class here. " +
                    "Cameras, Drones, and the rest are these chips — Show only, then Save current as… if you want a preset. " +
                    "Show only with no class picked leaves Live unchanged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.useClassFilter && !filter.excludeClasses,
                    onClick = {
                        vm.updateFilter {
                            val on = !(it.useClassFilter && !it.excludeClasses)
                            it.copy(useClassFilter = on, excludeClasses = false)
                        }
                    },
                    label = { Text("Show only") },
                )
                FieldwatchFilterChip(
                    selected = filter.useClassFilter && filter.excludeClasses,
                    onClick = {
                        vm.updateFilter {
                            val on = !(it.useClassFilter && it.excludeClasses)
                            it.copy(useClassFilter = on, excludeClasses = on)
                        }
                    },
                    label = { Text("Hide these") },
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SignatureClass.visible.sortedBy { it.label().lowercase() }.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { kind ->
                                val on = kind in filter.classes
                                FieldwatchFilterChip(
                                    selected = on,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = 32.dp),
                                    onClick = {
                                        vm.updateFilter { current ->
                                            val next = current.classes.toMutableSet()
                                            if (on) next.remove(kind) else next.add(kind)
                                            current.copy(classes = next)
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            ClassGlyphs.of(kind),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            kind.label(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            }

            SectionCard("Selected signatures") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show only selected signatures", Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.includeSignatures,
                    { on -> vm.updateFilter { it.copy(includeSignatures = on) } },
                )
            }
            if (filter.includeSignatures) {
                SignaturePickList(
                    fleets = state.fleets,
                    selected = filter.includeFleetIds,
                    help = "Tap a class to open its signatures. Only radios matching a signature you turn on below stay on Live. " +
                        "Empty list = no extra include (Live unchanged). Picks stay if you turn this off and on again.",
                    onToggle = { id, checked ->
                        vm.updateFilter { current ->
                            val next = current.includeFleetIds.toMutableSet()
                            if (checked) next.add(id) else next.remove(id)
                            current.copy(includeFleetIds = next)
                        }
                    },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hide selected signatures", Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.excludeSignatures,
                    { on -> vm.updateFilter { it.copy(excludeSignatures = on) } },
                )
            }
            if (filter.excludeSignatures) {
                SignaturePickList(
                    fleets = state.fleets,
                    selected = filter.fleetIds,
                    help = "Tap a class to open its signatures. Devices matching a signature you turn on below stay off the Live list. " +
                        "Your picks stay if you turn this off and on again.",
                    onToggle = { id, checked ->
                        vm.updateFilter { current ->
                            val next = current.fleetIds.toMutableSet()
                            if (checked) next.add(id) else next.remove(id)
                            current.copy(fleetIds = next)
                        }
                    },
                )
            }
            }

            SectionCard("Fine filter") {
            var rssiDrag by remember { mutableIntStateOf(filter.rssiMin) }
            var rssiDragging by remember { mutableStateOf(false) }
            LaunchedEffect(filter.rssiMin) {
                if (!rssiDragging) rssiDrag = filter.rssiMin
            }
            Text("Minimum RSSI  $rssiDrag dBm", style = MaterialTheme.typography.labelLarge)
            FieldwatchSlider(
                value = rssiDrag.toFloat(),
                onValueChange = { v ->
                    rssiDragging = true
                    rssiDrag = v.toInt()
                },
                onValueChangeFinished = {
                    vm.updateFilter { it.copy(rssiMin = rssiDrag) }
                    rssiDragging = false
                },
                valueRange = -100f..-30f,
            )

            OutlinedTextField(
                filter.nameQuery,
                { value -> vm.updateFilter { it.copy(nameQuery = value) } },
                label = { Text("Name / MAC contains") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                filter.ouiQuery,
                { value -> vm.updateFilter { it.copy(ouiQuery = value) } },
                label = { Text("OUI / vendor contains") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Extra filter logic", style = MaterialTheme.typography.labelLarge)
            Text(
                "AND/OR applies to name, OUI, RSSI, and class include — not to radios, Named radios only, Watched only, Hide Fast Pair account-key, or hide lists.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.logic == FilterLogic.AND,
                    onClick = { vm.updateFilter { it.copy(logic = FilterLogic.AND) } },
                    label = { Text("AND") },
                )
                FieldwatchFilterChip(
                    selected = filter.logic == FilterLogic.OR,
                    onClick = { vm.updateFilter { it.copy(logic = FilterLogic.OR) } },
                    label = { Text("OR") },
                )
            }

            FieldwatchActionButton(onClick = { confirmReset = true }) {
                Text("Reset filter")
            }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset filter?") },
            text = {
                Text(
                    "Clears every switch and pick on this tab (radios, classes, selected signatures, RSSI, name/OUI). " +
                        "Presets you saved stay. The Live display goes back to the unfiltered set. This is not undo.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        vm.updateFilter { app.fieldwatch.domain.FilterState() }
                    },
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete preset?") },
            text = {
                Text(
                    if (preset.isBuiltIn()) {
                        "Remove stock chip “${preset.name}” from this list? Catalog updates will not put it back. Settings → Restore default signatures & presets restores all stock chips. The filter on Live does not change until you apply another chip or Reset filter."
                    } else {
                        "Delete preset “${preset.name}”? This cannot be undone. The filter on Live does not change until you apply another chip or Reset filter."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePreset(preset.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SignaturePickList(
    fleets: List<Fleet>,
    selected: Set<String>,
    help: String,
    onToggle: (id: String, checked: Boolean) -> Unit,
) {
    val groups = remember(fleets) {
        fleets.groupBy { it.kind.folded() }
            .toList()
            .sortedBy { it.first.label().lowercase() }
            .map { (kind, rows) -> kind to rows.sortedBy { it.name.lowercase() } }
    }
    var open by remember {
        mutableStateOf(
            groups.filter { (_, rows) -> rows.any { it.id in selected } }
                .map { it.first.name }
                .toSet(),
        )
    }
    Column(
        modifier = Modifier.padding(start = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        groups.forEach { (kind, rows) ->
            val classId = kind.name
            val expanded = classId in open
            val picked = rows.count { it.id in selected }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        open = if (expanded) open - classId else open + classId
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    ClassGlyphs.of(kind),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    kind.label(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(if (expanded) "▾  " else "▸  ")
                        if (picked > 0) append("$picked/")
                        append(rows.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (picked > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (expanded) {
                rows.forEach { fleet ->
                    val on = fleet.id in selected
                    Row(
                        modifier = Modifier.padding(start = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(fleet.name, Modifier.weight(1f))
                        FieldwatchSwitch(on, { checked -> onToggle(fleet.id, checked) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(
    name: String,
    selected: Boolean = false,
    onApply: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = FilterChipDefaults.shape,
        color = if (selected) spectreSectionFill() else spectreTileFill(),
        border = BorderStroke(
            1.dp,
            if (selected) PhosphorActive.nightIf(LocalNightMode.current) else spectreTileEdge(),
        ),
        modifier = modifier
            .heightIn(max = 32.dp)
            .combinedClickable(
                onClick = onApply,
                onLongClick = onLongPress,
            ),
    ) {
        Text(
            name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
