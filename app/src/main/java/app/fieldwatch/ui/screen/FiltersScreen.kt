package app.fieldwatch.ui.screen

import app.fieldwatch.domain.LanguageText

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchOutlinedField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.FilterLogic
import app.fieldwatch.domain.FilterState
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
    var topicName by rememberSaveable { mutableStateOf<String?>(null) }
    val topic = topicName?.let { name ->
        FilterTopic.entries.firstOrNull { it.name == name && it.opensPage() }
    }
    BackHandler(enabled = topic != null) { topicName = null }
    val scroll = rememberScrollState()
    LaunchedEffect(topicName) { scroll.scrollTo(0) }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = topic?.title ?: LanguageText.text("tab.filters"),
                navigationIcon = {
                    if (topic != null) {
                        IconButton(onClick = { topicName = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, LanguageText.text("chrome.back"))
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (topic == null) {
            SectionCard(LanguageText.text("ui.presets.label")) {
            Text(
                (LanguageText.text("ui.tap.preset.load.long") + " ") +
                    LanguageText.text("ui.save.current.filters.settings"),
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
                FieldwatchOutlinedField(
                    presetName,
                    { presetName = it },
                    LanguageText.text("ui.save.current"),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    if (presetName.isNotBlank()) {
                        vm.savePreset(presetName.trim())
                        presetName = ""
                    }
                }) { Text(LanguageText.text("ui.save.label")) }
            }
            }
            SectionCard(LanguageText.text("ui.radios.label")) {
            Text(
                if (filter.movingWithYou) {
                    LanguageText.text("ui.moving.uses.ble.only")
                } else {
                    LanguageText.text("ui.pick.which.radios.live")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = !filter.movingWithYou && filter.showWifi && filter.showBle,
                    onClick = { vm.updateFilter { it.copy(showWifi = true, showBle = true) } },
                    enabled = !filter.movingWithYou,
                    label = { Text(LanguageText.text("ui.both.label")) },
                )
                FieldwatchFilterChip(
                    selected = !filter.movingWithYou && filter.showWifi && !filter.showBle,
                    onClick = { vm.updateFilter { it.copy(showWifi = true, showBle = false) } },
                    enabled = !filter.movingWithYou,
                    label = { Text(LanguageText.text("export.wifi.only")) },
                )
                FieldwatchFilterChip(
                    selected = filter.movingWithYou || (filter.showBle && !filter.showWifi),
                    onClick = { vm.updateFilter { it.copy(showWifi = false, showBle = true) } },
                    label = { Text(LanguageText.text("export.ble.only")) },
                )
            }
            }
            FilterTopicList(
                filter = filter,
                arrivalsLearning = state.arrivalsLearning,
                onOpen = { topicName = it.name },
                onChecked = { item, on -> vm.updateFilter { item.setEnabled(it, on) } },
            )
            FieldwatchActionButton(onClick = { confirmReset = true }) {
                Text(LanguageText.text("ui.reset.filter"))
            }
            }

            if (topic == FilterTopic.WHO) {
            SectionCard(LanguageText.text("ui.keep.hide"), showTitle = false) {
            val namedImplied = filter.namedOnlyImplied()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LanguageText.text("ui.keep.signature.matches.only"),
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
            Text(
                LanguageText.text("ui.live.shows.radios.match"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (namedImplied) {
                Text(
                    LanguageText.text("ui.show.only.already.hiding"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.keep.watched.only"), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.watchedOnly,
                    { on -> vm.updateFilter { it.copy(watchedOnly = on) } },
                )
            }
            Text(
                LanguageText.text("ui.live.shows.bookmarked.signatures"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.keep.named.only"), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.customNamesOnly,
                    { on -> vm.updateFilter { it.copy(customNamesOnly = on) } },
                )
            }
            Text(
                LanguageText.text("ui.live.shows.radios.named"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.hide.mine"), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.hideMine,
                    { on -> vm.updateFilter { it.copy(hideMine = on) } },
                )
            }
            Text(
                LanguageText.text("ui.radios.marked.mine.stay"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.hide.fast.pair.account"), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.hideFastPairAccountKey,
                    { on -> vm.updateFilter { it.copy(hideFastPairAccountKey = on) } },
                )
            }
            Text(
                LanguageText.text("ui.hides.already.paired.fast"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.hide.randomized.ble"), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.hideRandomizedBle,
                    { on -> vm.updateFilter { it.copy(hideRandomizedBle = on) } },
                )
            }
            Text(
                LanguageText.text("ui.hide.randomized.ble.note"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            }

            if (topic == FilterTopic.CLASSES) {
            SectionCard(LanguageText.text("ui.selected.signature.classes"), showTitle = false) {
            Text(
                LanguageText.text("ui.select.show.only.hide"),
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
                    label = { Text(LanguageText.text("ui.show.only")) },
                )
                FieldwatchFilterChip(
                    selected = filter.useClassFilter && filter.excludeClasses,
                    onClick = {
                        vm.updateFilter {
                            val on = !(it.useClassFilter && it.excludeClasses)
                            it.copy(useClassFilter = on, excludeClasses = on)
                        }
                    },
                    label = { Text(LanguageText.text("ui.hide.these")) },
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
                                            current.copy(
                                                classes = next,
                                                useClassFilter = if (on) current.useClassFilter else true,
                                            )
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
            }

            if (topic == FilterTopic.SIGNATURES) {
            SectionCard(LanguageText.text("ui.selected.signatures"), showTitle = false) {
            Text(
                LanguageText.text("ui.select.show.only.hide.2"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.signatureShowOn(),
                    onClick = { vm.updateFilter { it.toggleSignatureShow() } },
                    label = { Text(LanguageText.text("ui.show.only")) },
                )
                FieldwatchFilterChip(
                    selected = filter.signatureHideOn(),
                    onClick = { vm.updateFilter { it.toggleSignatureHide() } },
                    label = { Text(LanguageText.text("ui.hide.these")) },
                )
            }
            SignaturePickList(
                fleets = state.fleets,
                selected = filter.selectedSignatureIds(),
                help = LanguageText.text("ui.tap.class.open"),
                onToggle = { id, checked ->
                    vm.updateFilter { it.toggleSignaturePick(id, checked) }
                },
            )
            }
            }

            if (topic == FilterTopic.FINE) {
            SectionCard(LanguageText.text("ui.signal.name.mac.vendor"), showTitle = false) {
            var rssiDrag by remember { mutableIntStateOf(filter.rssiMin) }
            var rssiDragging by remember { mutableStateOf(false) }
            LaunchedEffect(filter.rssiMin) {
                if (!rssiDragging) rssiDrag = filter.rssiMin
            }
            Text(
                LanguageText.text("filters.rssi", mapOf("n" to rssiDrag.toString())),
                style = MaterialTheme.typography.labelLarge,
            )
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

            FieldwatchOutlinedField(
                filter.nameQuery,
                { value -> vm.updateFilter { it.copy(nameQuery = value) } },
                LanguageText.text("ui.name.mac.contains"),
            )
            FieldwatchOutlinedField(
                filter.ouiQuery,
                { value -> vm.updateFilter { it.copy(ouiQuery = value) } },
                LanguageText.text("ui.vendor.contains"),
            )

            Text(LanguageText.text("ui.name.vendor.signal.class"), style = MaterialTheme.typography.labelLarge)
            Text(
                LanguageText.text("ui.means.all.these.match"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.logic == FilterLogic.AND,
                    onClick = { vm.updateFilter { it.copy(logic = FilterLogic.AND) } },
                    label = { Text(LanguageText.text("filters.and")) },
                )
                FieldwatchFilterChip(
                    selected = filter.logic == FilterLogic.OR,
                    onClick = { vm.updateFilter { it.copy(logic = FilterLogic.OR) } },
                    label = { Text(LanguageText.text("filters.or")) },
                )
            }
            }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(LanguageText.text("ui.reset.filter.2")) },
            text = {
                Text(
                    (LanguageText.text("ui.clears.radios.classes.selected") + " ") +
                        LanguageText.text("ui.presets.saved.stay.live"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        vm.updateFilter { app.fieldwatch.domain.FilterState() }
                    },
                ) { Text(LanguageText.text("ui.reset.label")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(LanguageText.text("ui.delete.preset")) },
            text = {
                Text(
                    if (preset.isBuiltIn()) {
                        LanguageText.text("filters.delete.stock", mapOf("name" to preset.name))
                    } else {
                        LanguageText.text("filters.delete.custom", mapOf("name" to preset.name))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePreset(preset.id)
                    pendingDelete = null
                }) { Text(LanguageText.text("ui.delete.label")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
}

private enum class FilterTopic(val title: String) {
    MOVING(LanguageText.text("ui.moving.label")),
    PLACES(LanguageText.text("ui.seen.more.than.one")),
    ARRIVALS(LanguageText.text("ui.new.detections")),
    WHO(LanguageText.text("ui.keep.hide")),
    CLASSES(LanguageText.text("ui.selected.signature.classes")),
    SIGNATURES(LanguageText.text("ui.selected.signatures")),
    FINE(LanguageText.text("ui.signal.name.mac.vendor")),
    ;

    fun isOn(filter: FilterState): Boolean = when (this) {
        MOVING -> filter.movingWithYou
        PLACES -> filter.seenAtPlaces
        ARRIVALS -> filter.arrivalsOnly
        WHO -> filter.whoStaysOn()
        CLASSES -> filter.useClassFilter
        SIGNATURES -> filter.signaturesOn()
        FINE -> filter.fineOn()
    }

    /** A page of choices. One-switch rows stay on the list. Radios stays open above the list. */
    fun opensPage(): Boolean = this == WHO || this == CLASSES || this == SIGNATURES || this == FINE

    /** Row switch for the one-option rows. Moving with you uses the same clear as before. */
    fun setEnabled(filter: FilterState, on: Boolean): FilterState = when (this) {
        MOVING -> if (!on) {
            filter.copy(movingWithYou = false)
        } else {
            val hiding = filter.useClassFilter && filter.excludeClasses
            filter.copy(
                movingWithYou = true,
                showWifi = false,
                showBle = true,
                namedOnly = false,
                customNamesOnly = false,
                watchedOnly = false,
                useClassFilter = hiding,
                excludeClasses = hiding,
                classes = if (hiding) filter.classes else emptySet(),
                includeSignatures = false,
                excludeSignatures = filter.signatureHideOn(),
            )
        }
        PLACES -> filter.copy(seenAtPlaces = on)
        ARRIVALS -> filter.copy(arrivalsOnly = on)
        WHO -> if (on) filter else filter.clearWhoStays()
        CLASSES -> if (on) filter else filter.clearClasses()
        SIGNATURES -> if (on) filter else filter.clearSelectedSignatures()
        FINE -> if (on) filter else filter.clearFine()
    }
}

@Composable
private fun FilterTopicList(
    filter: FilterState,
    arrivalsLearning: Boolean,
    onOpen: (FilterTopic) -> Unit,
    onChecked: (FilterTopic, Boolean) -> Unit,
) {
    val rows = listOf(
        FilterTopic.MOVING to LanguageText.text("ui.loud.bluetooth.radios.stay"),
        FilterTopic.PLACES to LanguageText.text("ui.heard.another.place.last"),
        FilterTopic.ARRIVALS to if (filter.arrivalsOnly && arrivalsLearning) {
            LanguageText.text("ui.hides.radios.already.here")
        } else {
            LanguageText.text("ui.hides.radios.already.here.2")
        },
        FilterTopic.WHO to whoStaysValue(filter),
        FilterTopic.CLASSES to classesValue(filter),
        FilterTopic.SIGNATURES to signaturesValue(filter),
        FilterTopic.FINE to fineValue(filter),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (item, value) ->
            val page = item.opensPage()
            FilterTopicRow(
                title = item.title,
                value = value,
                checked = if (page) null else item.isOn(filter),
                onChecked = if (page) null else { on -> onChecked(item, on) },
                onClick = if (page) {
                    { onOpen(item) }
                } else {
                    { onChecked(item, !item.isOn(filter)) }
                },
                drillIn = page,
            )
        }
    }
}

@Composable
private fun FilterTopicRow(
    title: String,
    value: String?,
    checked: Boolean?,
    onChecked: ((Boolean) -> Unit)?,
    onClick: () -> Unit,
    drillIn: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!value.isNullOrBlank()) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (checked != null && onChecked != null) {
                FieldwatchSwitch(checked, onChecked)
                if (drillIn) Spacer(Modifier.width(4.dp))
            }
            if (drillIn) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onClick),
                )
            }
        }
    }
}

private fun whoStaysValue(filter: FilterState): String? {
    val parts = buildList {
        if (filter.namedOnly) add(LanguageText.text("ui.keep.signature.matches.only"))
        if (filter.watchedOnly) add(LanguageText.text("ui.keep.watched.only"))
        if (filter.customNamesOnly) add(LanguageText.text("ui.keep.named.only"))
        if (filter.hideMine) add(LanguageText.text("ui.hide.mine"))
        if (filter.hideFastPairAccountKey) add(LanguageText.text("ui.fast.pair"))
        if (filter.hideRandomizedBle) add(LanguageText.text("ui.hide.randomized.ble"))
    }
    return parts.joinToString(" · ").ifBlank { null }
}

private fun classesValue(filter: FilterState): String? {
    if (!filter.useClassFilter) return null
    val n = filter.classes.size
    if (n == 0) return null
    val mode = if (filter.excludeClasses) LanguageText.text("ui.hide.these") else LanguageText.text("ui.show.only")
    return "$mode · $n"
}

private fun signaturesValue(filter: FilterState): String? {
    val mode = when {
        filter.signatureShowOn() -> LanguageText.text("ui.show.only")
        filter.signatureHideOn() -> LanguageText.text("ui.hide.these")
        else -> return null
    }
    val n = filter.selectedSignatureIds().size
    if (n == 0) return null
    return "$mode · $n"
}

private fun fineValue(filter: FilterState): String? {
    val parts = buildList {
        if (filter.rssiMin > -100) add("${filter.rssiMin} dBm")
        if (filter.nameQuery.isNotBlank()) add(LanguageText.text("ui.name.label"))
        if (filter.ouiQuery.isNotBlank()) add(LanguageText.text("ui.vendor.label"))
        if (filter.logic == FilterLogic.OR) add("OR")
    }
    return parts.joinToString(" · ").ifBlank { null }
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
