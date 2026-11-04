@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package app.fieldwatch.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import app.fieldwatch.ui.component.NoticeBody
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.CellBand
import app.fieldwatch.domain.CellCopy
import app.fieldwatch.domain.CellPlace
import app.fieldwatch.domain.CellPlaceBand
import app.fieldwatch.domain.CellPlaceRoll
import app.fieldwatch.domain.CellPlmn
import app.fieldwatch.domain.CellWatch
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.LearnLeft
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.FieldwatchSwitch
import app.fieldwatch.ui.component.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CellWatchPage(
    vm: FieldwatchViewModel,
    settings: AppSettings,
    phoneLat: Double?,
    phoneLon: Double?,
    onPlaceBack: ((() -> Unit)?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val book by vm.cellBook.collectAsStateWithLifecycle()
    var browse by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var askForget by remember { mutableStateOf<String?>(null) }
    val open = book.places.find { it.id == detail }
    val now = learnNow()
    val roll = remember(book.places, phoneLat, phoneLon, now) {
        CellWatch.arrange(book.places, phoneLat, phoneLon, now)
    }
    val listState = rememberLazyListState()
    val pageScroll = rememberScrollState()
    var columnCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var mapTop by remember { mutableIntStateOf(0) }
    var mapLaidOut by remember { mutableStateOf(false) }
    var returnToMap by remember { mutableStateOf(false) }
    fun openFromPin(id: String) {
        returnToMap = true
        browse = false
        detail = id
    }
    fun placeMap(coords: LayoutCoordinates) {
        val column = columnCoords ?: return
        if (!column.isAttached || !coords.isAttached) return
        // The scroll node reports the map inside the viewport, so the content offset adds the scroll.
        val y = column.localPositionOf(coords, Offset.Zero).y + pageScroll.value
        if (!y.isFinite()) return
        mapTop = y.toInt().coerceAtLeast(0)
        mapLaidOut = true
    }
    val nested = browse || detail != null
    BackHandler(enabled = nested) {
        if (detail != null) detail = null else browse = false
    }
    DisposableEffect(browse, detail) {
        onPlaceBack(
            if (browse || detail != null) {
                { if (detail != null) detail = null else browse = false }
            } else {
                null
            },
        )
        onDispose { onPlaceBack(null) }
    }
    LaunchedEffect(detail, returnToMap) {
        if (detail != null || !returnToMap) return@LaunchedEffect
        mapLaidOut = false
        val placed = withTimeoutOrNull(2000) {
            snapshotFlow { Triple(mapLaidOut, mapTop, pageScroll.maxValue) }
                .first { (laid, top, max) -> laid && max >= top }
        }
        if (detail == null && placed != null) {
            pageScroll.scrollTo(placed.second.coerceAtMost(pageScroll.maxValue))
        }
        returnToMap = false
    }
    LaunchedEffect(detail, book.places) {
        if (detail != null && book.places.none { it.id == detail }) detail = null
    }
    val unnamed = book.places.count { it.name.isBlank() }
    LaunchedEffect(settings.onlineLookup, settings.demoMode, unnamed) {
        if (settings.onlineLookup && !settings.demoMode && unnamed > 0) vm.refreshCellPlaceNames()
    }
    if (browse && open == null) {
        CellPlaceIndex(
            roll = roll,
            places = book.places,
            phoneLat = phoneLat,
            phoneLon = phoneLon,
            learnDays = settings.cellLearnDays,
            now = now,
            onlineNames = settings.onlineLookup,
            privacy = settings.demoMode,
            showMap = settings.onlineLookup && !settings.demoMode,
            basemap = settings.mapBasemap,
            mapKey = vm.mapKey(settings.mapBasemap),
            overlays = settings.mapOverlays,
            listState = listState,
            onOpen = { detail = it },
            onPin = ::openFromPin,
            onForget = { askForget = it },
            modifier = modifier,
        )
    } else {
        Column(
            modifier
                .onGloballyPositioned { columnCoords = it }
                .verticalScroll(pageScroll)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (open == null) {
                CellWatchCards(settings, vm::updateSettings)
                CellPlaces(
                    roll = roll,
                    places = book.places,
                    phoneLat = phoneLat,
                    phoneLon = phoneLon,
                    learnDays = settings.cellLearnDays,
                    now = now,
                    onlineNames = settings.onlineLookup,
                    privacy = settings.demoMode,
                    showMap = settings.onlineLookup && !settings.demoMode,
                    basemap = settings.mapBasemap,
                    mapKey = vm.mapKey(settings.mapBasemap),
                    overlays = settings.mapOverlays,
                    onOpen = { detail = it },
                    onPin = ::openFromPin,
                    onForget = { askForget = it },
                    onBrowse = { browse = true },
                    onMapPlaced = ::placeMap,
                    onMapRemoved = { mapLaidOut = false },
                )
            } else {
                CellPlaceCard(
                    place = open,
                    learnDays = settings.cellLearnDays,
                    now = now,
                    onlineNames = settings.onlineLookup,
                    privacy = settings.demoMode,
                    onRemove = { vm.removeCell(open.id, it) },
                    onForget = { askForget = open.id },
                )
            }
        }
    }
    val forgetting = askForget
    if (forgetting != null) {
        AlertDialog(
            onDismissRequest = { askForget = null },
            title = { Text(LanguageText.text("cell.forget")) },
            text = { Text(LanguageText.text("cell.forget.ask")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.forgetCellPlace(forgetting)
                        askForget = null
                        if (detail == forgetting) detail = null
                    },
                ) { Text(LanguageText.text("cell.forget")) }
            },
            dismissButton = {
                TextButton(onClick = { askForget = null }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
}

@Composable
fun CellAlertDialog(vm: FieldwatchViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val gnss by vm.gnssNotice.collectAsStateWithLifecycle()
    val trusted by vm.trustedNotice.collectAsStateWithLifecycle()
    val book by vm.cellBook.collectAsStateWithLifecycle()
    if (!ui.settings.cellWatch) return
    if (gnss?.showDialog == true) return
    if (trusted != null) return
    val episode = CellWatch.showing(book) ?: return
    val details = CellCopy.details(episode)
    AlertDialog(
        onDismissRequest = { vm.dismissCell(episode.id) },
        title = { Text(CellCopy.sentence(episode.kind)) },
        text = { NoticeBody(CellCopy.body(episode.kind), details) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { vm.learnCell(episode.id) }) {
                    Text(LanguageText.text("cell.learn"))
                }
                TextButton(onClick = { vm.dismissCell(episode.id) }) {
                    Text(LanguageText.text("trusted.dismiss"))
                }
            }
        },
    )
}

@Composable
private fun CellWatchCards(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    SectionCard(LanguageText.text("settings.topic.cell"), showTitle = false) {
        Note(LanguageText.text("cell.intro"))
        SwitchRow(LanguageText.text("cell.master"), settings.cellWatch) { on ->
            onChange { it.copy(cellWatch = on) }
        }
        Note(LanguageText.text("cell.master.note"))
    }
    SectionCard(LanguageText.text("trusted.warnings")) {
        Note(LanguageText.text("cell.warnings.note"))
        Text(LanguageText.text("trusted.learn.days"), style = MaterialTheme.typography.labelLarge)
        ChipRow(
            listOf(
                0 to "cell.learn.none",
                1 to "trusted.day.1",
                3 to "trusted.day.3",
                7 to "trusted.day.7",
                14 to "trusted.day.14",
            ),
            settings.cellLearnDays,
        ) { days -> onChange { it.copy(cellLearnDays = days) } }
        Note(LanguageText.text("cell.learn.days.note"))
        SwitchRow(LanguageText.text("cell.warn.new"), settings.cellWarnNew) { on ->
            onChange { it.copy(cellWarnNew = on) }
        }
        Note(LanguageText.text("cell.warn.new.note"))
        SwitchRow(LanguageText.text("cell.warn.drop"), settings.cellWarnDrop) { on ->
            onChange { it.copy(cellWarnDrop = on) }
        }
        Note(LanguageText.text("cell.warn.drop.note"))
    }
    SectionCard(LanguageText.text("trusted.reach")) {
        Note(LanguageText.text("cell.reach.note"))
        SwitchRow(LanguageText.text("ui.beep.label"), settings.cellBeep) { on ->
            onChange { it.copy(cellBeep = on) }
        }
        Note(LanguageText.text("cell.beep.note"))
        SwitchRow(LanguageText.text("ui.voice.label"), settings.cellVoice) { on ->
            onChange { it.copy(cellVoice = on) }
        }
        Note(LanguageText.text("cell.voice.note"))
        SwitchRow(LanguageText.text("ui.system.notification"), settings.cellShade) { on ->
            onChange { it.copy(cellShade = on) }
        }
        Note(LanguageText.text("cell.shade.note"))
    }
    SectionCard(LanguageText.text("cell.cannot")) {
        Note(LanguageText.text("cell.cannot.other"))
    }
}

@Composable
private fun CellPlaces(
    roll: CellPlaceRoll,
    places: List<CellPlace>,
    phoneLat: Double?,
    phoneLon: Double?,
    learnDays: Int,
    now: Long,
    onlineNames: Boolean,
    privacy: Boolean,
    showMap: Boolean,
    basemap: app.fieldwatch.domain.MapBasemap,
    mapKey: String,
    overlays: Set<app.fieldwatch.domain.MapOverlay>,
    onOpen: (String) -> Unit,
    onPin: (String) -> Unit,
    onForget: (String) -> Unit,
    onBrowse: () -> Unit,
    onMapPlaced: (LayoutCoordinates) -> Unit,
    onMapRemoved: () -> Unit,
) {
    val seen = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    SectionCard(LanguageText.text("cell.places")) {
        if (showMap && places.isNotEmpty()) {
            CellPlaceMap(
                places = places,
                phoneLat = phoneLat,
                phoneLon = phoneLon,
                basemap = basemap,
                mapKey = mapKey,
                overlays = overlays,
                onOpen = onPin,
                onPlaced = onMapPlaced,
                onRemoved = onMapRemoved,
            )
        }
        if (roll.preview.isEmpty()) {
            Note(LanguageText.text("cell.places.empty"))
        }
        roll.preview.forEach { place ->
            key(place.id) {
                CellPlaceRow(
                    place = place,
                    seen = seen,
                    away = awayLine(place, phoneLat, phoneLon),
                    left = cellLeft(place, learnDays, now),
                    onlineNames = onlineNames,
                    privacy = privacy,
                    onOpen = { onOpen(place.id) },
                    onForget = { onForget(place.id) },
                )
            }
        }
        if (roll.more > 0) {
            Text(
                moreLabel(roll.more),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBrowse)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CellPlaceIndex(
    roll: CellPlaceRoll,
    places: List<CellPlace>,
    phoneLat: Double?,
    phoneLon: Double?,
    learnDays: Int,
    now: Long,
    onlineNames: Boolean,
    privacy: Boolean,
    showMap: Boolean,
    basemap: app.fieldwatch.domain.MapBasemap,
    mapKey: String,
    overlays: Set<app.fieldwatch.domain.MapOverlay>,
    listState: LazyListState,
    onOpen: (String) -> Unit,
    onPin: (String) -> Unit,
    onForget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seen = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showMap && places.isNotEmpty()) {
            item(key = "map") {
                CellPlaceMap(
                    places = places,
                    phoneLat = phoneLat,
                    phoneLon = phoneLon,
                    basemap = basemap,
                    mapKey = mapKey,
                    overlays = overlays,
                    onOpen = onPin,
                )
            }
        }
        item(key = "places") {
            Text(
                LanguageText.text("cell.places"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (roll.sections.isEmpty()) {
            item(key = "empty") { Note(LanguageText.text("cell.places.empty")) }
        }
        roll.sections.forEach { section ->
            item(key = "band-${section.band}") {
                Text(
                    bandLabel(section.band),
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(section.places, key = { it.id }) { place ->
                CellPlaceRow(
                    place = place,
                    seen = seen,
                    away = awayLine(place, phoneLat, phoneLon),
                    left = cellLeft(place, learnDays, now),
                    onlineNames = onlineNames,
                    privacy = privacy,
                    onOpen = { onOpen(place.id) },
                    onForget = { onForget(place.id) },
                )
            }
        }
    }
}

private fun bandLabel(band: CellPlaceBand): String = LanguageText.text(
    when (band) {
        CellPlaceBand.HERE -> "cell.here"
        CellPlaceBand.TODAY -> "cell.group.today"
        CellPlaceBand.WEEK -> "cell.group.week"
        CellPlaceBand.OLDER -> "cell.group.older"
    },
)

private fun moreLabel(more: Int): String = if (more == 1) {
    LanguageText.text("cell.more.one")
} else {
    LanguageText.text("cell.more", mapOf("n" to more.toString()))
}

@Composable
private fun CellPlaceRow(
    place: CellPlace,
    seen: SimpleDateFormat,
    away: String?,
    left: String?,
    onlineNames: Boolean,
    privacy: Boolean,
    onOpen: () -> Unit,
    onForget: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onForget()
            false
        },
    )
    val count = if (place.cells.size == 1) {
        LanguageText.text("cell.one")
    } else {
        LanguageText.text("cell.count", mapOf("n" to place.cells.size.toString()))
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(LanguageText.text("cell.forget"), color = MaterialTheme.colorScheme.onError)
            }
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    CellWatch.placeTitle(place, onlineNames, privacy),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val kind = bands(place)
                Text(
                    if (kind.isBlank()) count else "$kind · $count",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    LanguageText.text("cell.last") + " " + seen.format(Date(place.lastVisitAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (away != null) {
                    Text(
                        away,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (left != null) {
                    Text(
                        left,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Here inside the place. A distance farther out. Nothing when this phone has no fix. */
private fun awayLine(place: CellPlace, phoneLat: Double?, phoneLon: Double?): String? {
    val meters = CellWatch.metersFromPhone(place, phoneLat, phoneLon) ?: return null
    return if (meters <= CellWatch.NEAR_M) {
        LanguageText.text("cell.here")
    } else {
        Geo.distanceText(meters)
    }
}

@Composable
private fun CellPlaceCard(
    place: CellPlace,
    learnDays: Int,
    now: Long,
    onlineNames: Boolean,
    privacy: Boolean,
    onRemove: (String) -> Unit,
    onForget: () -> Unit,
) {
    val left = cellLeft(place, learnDays, now)
    val seen = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            CellWatch.placeTitle(place, onlineNames, privacy),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (left != null) {
            Text(
                left,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        place.cells.forEach { record ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        record.identity.gen.band(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(
                        Modifier.padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        CellFact("cell.network", record.identity.plmn)
                        CellFact("cell.country", CellPlmn.country(record.identity.plmn))
                        CellFact("cell.operator", CellPlmn.operator(record.identity.plmn))
                        CellFact("cell.area", record.identity.area.toString())
                        CellFact("cell.cell", record.identity.cell.toString())
                        CellFact("cell.pci", record.pci?.toString())
                        CellFact("cell.channel", record.channel?.toString())
                        CellFact("cell.band", record.channel?.let { CellBand.label(record.identity.gen, it) })
                        CellFact("cell.camps", record.camps.coerceAtLeast(1).toString())
                        val firstCamp = when {
                            record.firstAt > 0L -> record.firstAt
                            else -> record.heardAt
                        }
                        CellFact(
                            "cell.camped.first",
                            if (firstCamp > 0L) seen.format(Date(firstCamp)) else null,
                        )
                        CellFact(
                            "cell.camped",
                            if (record.heardAt > 0L) seen.format(Date(record.heardAt)) else null,
                        )
                        CellFact("cell.signal", record.signal?.let { "$it dBm" })
                    }
                    Text(
                        LanguageText.text("cell.remove"),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp)
                            .clickable { onRemove(record.identity.key()) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        FieldwatchActionButton(onClick = onForget, modifier = Modifier.fillMaxWidth()) {
            Text(LanguageText.text("cell.forget"))
        }
    }
}

@Composable
private fun CellFact(labelKey: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            LanguageText.text(labelKey),
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun bands(place: CellPlace): String {
    val have = place.cells.map { it.identity.gen.band() }.toSet()
    return listOf("2G", "3G", "4G", "5G").filter { it in have }.joinToString(" ")
}

private fun cellLeft(place: CellPlace, learnDays: Int, now: Long): String? {
    val window = LearnLeft.cellWindowMs(learnDays) ?: return null
    if (place.openedAt <= 0L) return null
    return LearnLeft.label(LearnLeft.remainingMs(place.openedAt, window, now))
}

@Composable
private fun learnNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChipRow(choices: List<Pair<Int, String>>, selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (value, key) ->
            FieldwatchFilterChip(
                selected = selected == value,
                onClick = { onPick(value) },
                label = { Text(LanguageText.text(key)) },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        FieldwatchSwitch(on, set)
    }
}
