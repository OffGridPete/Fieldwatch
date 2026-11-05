package app.fieldwatch.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.FlockApproach
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.ui.RadioClassBadge
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.MapCameraMarks
import app.fieldwatch.domain.PathCameras
import app.fieldwatch.domain.LogExportKind
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.Sit
import app.fieldwatch.domain.SitDiff
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.ui.component.AircraftAmber
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.SitPathCanvas
import app.fieldwatch.ui.component.rememberMapFooter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.FieldwatchSwitch
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.spectreSectionFill
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
    onOpenPlaceRadio: (String) -> Unit = {},
) {
    val settings = state.settings
    var confirmClear by remember { mutableStateOf(false) }
    var startSit by remember { mutableStateOf(false) }
    var sitNameDraft by remember { mutableStateOf("") }
    var renameSitId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteSitId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var topicName by rememberSaveable { mutableStateOf<String?>(null) }
    val topic = topicName?.let { name ->
        ReportTopic.entries.firstOrNull { it.name == name }
    }
    BackHandler(enabled = topic != null) { topicName = null }
    val scroll = rememberScrollState()
    LaunchedEffect(topicName) { scroll.scrollTo(0) }
    val pathModel by vm.sitPath.collectAsStateWithLifecycle()
    val sitKey = "${state.sit.selectedId}:${state.sit.open?.id}"
    var pathViewport by remember(sitKey) { mutableStateOf<SitPathPlot.Viewport?>(null) }
    var pathTileWindow by remember(sitKey) { mutableStateOf<DoubleArray?>(null) }
    val pathTileWindowKey = pathTileWindow?.let { SitPathPlot.tileWindowKey(it) }.orEmpty()
    val pathHeld = pathViewport != null
    val mapKeyRev by vm.mapKeyRevision.collectAsStateWithLifecycle()
    LaunchedEffect(state.sit.selectedId, state.sit.open?.id) {
        while (true) {
            vm.refreshSitPath()
            kotlinx.coroutines.delay(3_000L)
        }
    }
    DisposableEffect(sitKey) {
        onDispose { vm.noteSitPathHold(false) }
    }
    LaunchedEffect(
        pathTileWindowKey,
        pathHeld,
        settings.onlineLookup,
        settings.mapBasemap,
        settings.mapOverlays,
        mapKeyRev,
    ) {
        if (!pathHeld) return@LaunchedEffect
        val window = pathTileWindow ?: return@LaunchedEffect
        vm.refreshSitPathWindow(window[0], window[1], window[2], window[3])
    }
    val sitKind by vm.sitExportKind.collectAsStateWithLifecycle()
    val logKind by vm.logExportKind.collectAsStateWithLifecycle()
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = topic?.title() ?: LanguageText.text("tab.reports"),
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
            if (settings.demoMode) {
                Text(
                    LanguageText.text("ui.privacy.mode.debrief.compare"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SectionCard(LanguageText.text("reports.sits")) {
                Text(
                    LanguageText.text("ui.radios.heard.during.named"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val open = state.sit.open
                if (open != null) {
                    val dur = Sit.fmtDuration(open.durationMs())
                    Text(
                        LanguageText.text(
                            "reports.this.sit",
                            mapOf(
                                "name" to open.name,
                                "duration" to dur,
                                "n" to state.sit.radioCount.toString(),
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FieldwatchActionButton(
                        onClick = vm::endSit,
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(LanguageText.text("ui.end.sit")) }
                } else {
                    FieldwatchActionButton(
                        onClick = {
                            sitNameDraft = vm.defaultSitName()
                            startSit = true
                        },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(LanguageText.text("ui.start.sit")) }
                    Text(
                        LanguageText.text("ui.select.sit.actions.below"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.sit.closed.isEmpty() && open == null) {
                    Text(
                        LanguageText.text("ui.no.saved.sits"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.sit.closed.isNotEmpty()) {
                    val pickEnabled = open == null && !exporting
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SitChoiceRow(
                            selected = state.sit.selectedId == null,
                            enabled = pickEnabled,
                            title = LanguageText.text("ui.last.15.minutes"),
                            subtitle = LanguageText.text("ui.uses.what.still.memory"),
                            onSelect = { vm.selectSit(null) },
                        )
                        state.sit.closed.forEach { row ->
                            val dur = Sit.fmtDuration(row.durationMs())
                            val extra = if (row.extraAttentionCount > 0) {
                                " · " + LanguageText.text(
                                    "reports.extra",
                                    mapOf(
                                        "what" to LanguageText.text("chrome.extra.attention"),
                                        "n" to row.extraAttentionCount.toString(),
                                    ),
                                )
                            } else {
                                ""
                            }
                            SitChoiceRow(
                                selected = state.sit.selectedId == row.id,
                                enabled = pickEnabled,
                                title = row.name,
                                subtitle = LanguageText.text(
                                    "reports.sit.row",
                                    mapOf(
                                        "when" to Sit.defaultName(row.startAt, LanguageText.dateLocale()),
                                        "duration" to dur,
                                        "n" to row.radioCount.toString(),
                                    ),
                                ) + extra,
                                onSelect = { vm.selectSit(row.id) },
                            )
                        }
                    }
                    if (open != null) {
                        Text(
                            LanguageText.text("ui.end.sit.pick.saved"),
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
                        ) { Text(LanguageText.text("ui.rename.label")) }
                        FieldwatchActionButton(
                            onClick = { if (picked != null) deleteSitId = picked.id },
                            enabled = !exporting && picked != null,
                            modifier = Modifier.weight(1f),
                        ) { Text(LanguageText.text("ui.delete.label")) }
                    }
                    FieldwatchActionButton(
                        onClick = { confirmDeleteAll = true },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(LanguageText.text("ui.delete.all.sits")) }
                }
            }
            ReportTopicList(
                using = usingSitLabel(state),
                onOpen = { topicName = it.name },
            )
            ReportTopicRow(
                title = LanguageText.text("ui.seen.more.than.one"),
                value = LanguageText.text("reports.places.blurb"),
                onClick = { topicName = ReportTopic.PLACES.name },
            )
            ReportTopicRow(
                title = LanguageText.text("reports.log.export"),
                value = LanguageText.text("ui.export.file.current.log"),
                onClick = { topicName = ReportTopic.LOG.name },
            )
            ReportTopicRow(
                title = LanguageText.text("reports.candidates"),
                value = LanguageText.text("ui.search.log.new.signatures"),
                onClick = { topicName = ReportTopic.CANDIDATES.name },
            )
            }

            if (topic == ReportTopic.PLACES) {
                PlaceReportPage(state, vm, onOpenPlaceRadio)
            }

            if (topic == ReportTopic.PATH) {
            SectionCard(LanguageText.text("reports.path"), showTitle = false) {
                val model = pathModel
                val showWalk = model != null && model.emptyHint == null
                val showAircraft = model != null && model.aircraftCards.isNotEmpty()
                if (model == null || (!showWalk && !showAircraft)) {
                    Text(
                        model?.emptyHint ?: LanguageText.text("ui.turn.tag.detections.gps"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val pathTiles by vm.pathTiles.collectAsStateWithLifecycle()
                    val pathZoomIn by vm.pathZoomIn.collectAsStateWithLifecycle()
                    val flockRevision by vm.flockRevision.collectAsStateWithLifecycle()
                    val pendingCameras by vm.shownMapCameras.collectAsStateWithLifecycle()
                    val cameraMode = MapCameraMarks.onScreen(pendingCameras, settings.mapCameras)
                    val aircraftTiles by vm.pathAircraftTiles.collectAsStateWithLifecycle()
                    val mapPosition = rememberMapFooter(
                        settings.mapFooter,
                        state.phoneLat,
                        state.phoneLon,
                        settings.demoMode,
                    )
                    if (!showWalk) {
                        Text(
                            model.emptyHint ?: LanguageText.text("ui.turn.tag.detections.gps"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showWalk) {
                    val pathShown = model.pathCameraTally?.let { tally ->
                        PathCameras.shownOnPath(tally, settings.flockLayer, settings.sensorLayer)
                    }
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
                    SitPathCanvas(
                        model,
                        tiles = pathTiles,
                        onOpenRadio = onOpenPathRadio,
                        position = if (model.live) mapPosition else null,
                        zoomIn = pathZoomIn,
                        cameraMode = cameraMode,
                        onCycleCameras = vm::cycleMapCameras,
                        camerasReady = (settings.flockLayer || settings.sensorLayer) &&
                            vm.flockIndex.hasCameras(),
                        flock = settings.flockLayer || settings.sensorLayer,
                        flockIndex = vm.flockIndex,
                        flockRevision = flockRevision,
                        viewport = pathViewport,
                        onViewport = { next ->
                            pathViewport = next
                            vm.noteSitPathHold(next != null)
                        },
                        fitButton = false,
                        splitStacked = true,
                        recordCameras = true,
                        onTileWindow = { next ->
                            if (SitPathPlot.tileWindowKey(next) != pathTileWindowKey) {
                                pathTileWindow = next
                            }
                        },
                    )
                    Text(
                        LanguageText.text("ui.tap.number.radios.there"),
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
                                            LanguageText.text("ui.advertised.track.within.2")
                                        } else {
                                            LanguageText.text("ui.one.advertised.position.within")
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
                                        LanguageText.text("ui.operator.label"),
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
                                        LanguageText.text("ui.where.phone.was.during"),
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
                            LanguageText.text("ui.no.alerts.gps.stamp"),
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
                    pathShown?.let { tally ->
                        PathCameraSection(tally)
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
                                    if (fixes == 1) LanguageText.text("ui.1.advertised.fix") else "$fixes advertised fixes",
                                )
                                if (card.lengthM >= 1.0) append(" · ${card.lengthM.toInt()} m")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (card.dots.isNotEmpty()) {
                            Text(
                                if (card.dots.size == 1) LanguageText.text("ui.1.alert") else "${card.dots.size} alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SitPathCanvas(
                            card,
                            tiles = aircraftTiles.getOrElse(index) { emptyList() },
                            onOpenRadio = onOpenPathRadio,
                            position = if (model.live) mapPosition else null,
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
                                LanguageText.text("ui.advertised.position.no.uas")
                            } else {
                                LanguageText.text("ui.advertised.positions.no.uas")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            }

            if (topic == ReportTopic.DEBRIEF) {
            SectionCard(LanguageText.text("reports.debrief"), showTitle = false) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldwatchActionButton(
                    onClick = vm::startFieldDebrief,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text(LanguageText.text("ui.debrief.text")) }
                FieldwatchActionButton(
                    onClick = vm::startFieldDebriefPdf,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) { Text(LanguageText.text("reports.debrief.pdf")) }
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
                    LanguageText.text("ui.show.unmatched.rotating.ble"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FieldwatchSwitch(
                    settings.debriefShowUnmatchedRandomBle,
                    { on -> vm.updateSettings { it.copy(debriefShowUnmatchedRandomBle = on) } },
                )
            }
            Text(
                LanguageText.text("ui.unmatched.bluetooth.changes.address"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    LanguageText.text("ui.show.all.radios"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FieldwatchSwitch(
                    settings.debriefShowAllRadios,
                    { on -> vm.updateSettings { it.copy(debriefShowAllRadios = on) } },
                )
            }
            Text(
                LanguageText.text("ui.lists.every.radio.off"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startAiExport,
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.ai.export")) }
            Text(
                LanguageText.text("ui.short.note.pasting.into"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            }

            if (topic == ReportTopic.EXPORT) {
            SectionCard(LanguageText.text("reports.sit.export"), showTitle = false) {
            val sitRadios by vm.sitExportRadios.collectAsStateWithLifecycle()
            ExportFormatBlock(
                kind = sitKind,
                radios = sitRadios,
                exporting = exporting,
                onKind = vm::setSitExportKind,
                onRadios = vm::setSitExportRadios,
                onShare = vm::startSitExport,
                onSave = onSaveSitToStorage,
                kinds = LogExportKind.entries,
                hint = LanguageText.text("ui.one.row.per.radio"),
            )
            }
            }

            if (topic == ReportTopic.COMPARE) {
            SectionCard(LanguageText.text("reports.compare"), showTitle = false) {
                Text(
                    compareThisCaption(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val thisSaved = SitDiff.thisSavedId(state.sit.open, state.sit.selectedId)
                val choices = SitDiff.secondSitChoices(state.sit.closed, thisSaved)
                if (choices.isEmpty()) {
                    Text(
                        LanguageText.text("ui.save.second.sit.compare"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        LanguageText.text("ui.second.sit"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldwatchActionButton(
                        onClick = vm::startSitCompare,
                        enabled = !exporting && state.sit.compareId != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(LanguageText.text("ui.compare.text")) }
                    FieldwatchActionButton(
                        onClick = vm::startSitComparePdf,
                        enabled = !exporting && state.sit.compareId != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(LanguageText.text("reports.compare.pdf")) }
                }
                Text(
                    LanguageText.text("ui.radios.only.sit.only"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FieldwatchActionButton(
                    onClick = vm::startSitCompareAiExport,
                    enabled = !exporting && state.sit.compareId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(LanguageText.text("ui.ai.export")) }
                Text(
                    LanguageText.text("ui.short.note.what.overlaps"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }

            if (topic == ReportTopic.LOG) {
            SectionCard(LanguageText.text("ui.log.label"), showTitle = false) {
            Text(
                LanguageText.text(
                    "reports.log",
                    mapOf(
                        "n" to state.logLines.toString(),
                        "kb" to (vm.logBytes() / 1024).toString(),
                    ),
                ) + if (settings.loggingEnabled) "" else ("  " + LanguageText.text("ui.logging.off")),
                style = MaterialTheme.typography.bodySmall,
            )
            val logRadios by vm.logExportRadios.collectAsStateWithLifecycle()
            ExportFormatBlock(
                kind = logKind,
                radios = logRadios,
                exporting = exporting,
                onKind = vm::setLogExportKind,
                onRadios = vm::setLogExportRadios,
                onShare = vm::startExport,
                onSave = onSaveToStorage,
                hint = LanguageText.text("ui.one.detection.per.line"),
            )
            FieldwatchActionButton(
                onClick = { confirmClear = true },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(LanguageText.text("ui.reset.clear.log"))
            }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text(LanguageText.text("ui.clear.log")) },
                    text = {
                        Text(LanguageText.text("ui.deletes.log.files.phone"))
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmClear = false
                            vm.clearLogs()
                        }) { Text(LanguageText.text("ui.clear.log.2")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClear = false }) { Text(LanguageText.text("language.cancel")) }
                    },
                )
            }
            }
            }

            if (topic == ReportTopic.CANDIDATES) {
            SectionCard(LanguageText.text("reports.candidates"), showTitle = false) {
                Text(
                    LanguageText.text("ui.unmatched.radios.log.share"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                candidates.report?.let { report ->
                    val n = report.families.size
                    Text(
                        LanguageText.text("reports.found", mapOf("n" to n.toString())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FieldwatchActionButton(
                    onClick = onSignatureCandidates,
                    enabled = !exporting && !candidates.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(LanguageText.text("ui.find.candidates")) }
            }
            }
        }
    }
    if (startSit) {
        AlertDialog(
            onDismissRequest = { startSit = false },
            title = { Text(LanguageText.text("ui.start.sit")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldwatchOutlinedField(
                        value = sitNameDraft,
                        onValueChange = { sitNameDraft = it.take(Sit.NAME_MAX) },
                        label = LanguageText.text("ui.name.label"),
                    )
                    Text(
                        LanguageText.text("ui.debrief.ai.export.use"),
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
                }) { Text(LanguageText.text("ui.start.label")) }
            },
            dismissButton = {
                TextButton(onClick = { startSit = false }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
    val renaming = renameSitId
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renameSitId = null },
            title = { Text(LanguageText.text("ui.rename.sit")) },
            text = {
                FieldwatchOutlinedField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(Sit.NAME_MAX) },
                    label = LanguageText.text("ui.name.label"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameSitId = null
                    vm.renameSit(renaming, renameDraft)
                }) { Text(LanguageText.text("ui.save.label")) }
            },
            dismissButton = {
                TextButton(onClick = { renameSitId = null }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
    val deleting = deleteSitId
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteSitId = null },
            title = { Text(LanguageText.text("ui.delete.sit")) },
            text = { Text(LanguageText.text("ui.removes.saved.sit.phone")) },
            confirmButton = {
                TextButton(onClick = {
                    deleteSitId = null
                    vm.deleteSit(deleting)
                }) { Text(LanguageText.text("ui.delete.label")) }
            },
            dismissButton = {
                TextButton(onClick = { deleteSitId = null }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(LanguageText.text("ui.delete.all.sits.2")) },
            text = { Text(LanguageText.text("ui.removes.saved.sits.phone")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    vm.deleteAllSits()
                }) { Text(LanguageText.text("ui.delete.all")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(LanguageText.text("language.cancel")) }
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
            .padding(vertical = 2.dp),
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
            pin.detail.trim().ifBlank { LanguageText.text("ui.signal.changed") },
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
    if (serial) return LanguageText.text("ui.aircraft.label")
    return MacUtil.redactMacIn(title, title, true)
}

private fun privacyAircraftCaption(caption: String, title: String, demo: Boolean): String {
    if (!demo || caption.isBlank()) return caption
    val stripped = if (title.isBlank()) caption else caption.replace(title, "masked", ignoreCase = true)
    return Geo.redactCoordsIn(MacUtil.redactMacIn(stripped, title, true), true)
}

@Composable
private fun PathCameraSection(tally: PathCameras.Tally) {
    if (!tally.plates && !tally.others) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            LanguageText.text("ui.cameras.on.path"),
            style = MaterialTheme.typography.titleSmall,
        )
        if (!tally.pathReady) {
            Text(
                LanguageText.text("ui.cameras.no.path"),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            return
        }
        Text(
            LanguageText.text(
                "ui.cameras.within",
                mapOf("meters" to FlockApproach.PATH_M.toInt().toString()),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        if (tally.counts.isEmpty()) {
            Text(
                LanguageText.text("ui.cameras.none.along"),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tally.counts.forEach { count -> PathCameraRow(count) }
            }
        }
    }
}

@Composable
private fun PathCameraRow(count: PathCameras.Count) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .background(cameraMarkColor(count.labelKey), CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                LanguageText.text(count.labelKey),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                LanguageText.text(
                    "ui.cameras.facing.count",
                    mapOf("count" to count.facing.toString()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                LanguageText.text(
                    "ui.cameras.near.count",
                    mapOf("count" to count.near.toString()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun cameraMarkColor(labelKey: String): Color = when (labelKey) {
    "ui.cameras.kind.flock" -> Color(0xFF3B82F6)
    "ui.cameras.kind.plate" -> Color(0xFFF59E0B)
    "ui.cameras.kind.speed" -> Color(0xFF14B8A6)
    "ui.cameras.kind.red" -> Color(0xFFEF4444)
    else -> Color(0xFF64748B)
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
    if (open != null) return LanguageText.text("reports.this.name", mapOf("name" to open.name))
    val selected = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
    if (selected != null) return LanguageText.text("reports.this.name", mapOf("name" to selected.name))
    return LanguageText.text("ui.sit.last.15.minutes")
}

private enum class ReportTopic(val titleKey: String) {
    PATH("reports.path"),
    DEBRIEF("reports.debrief"),
    EXPORT("reports.sit.export"),
    COMPARE("reports.compare"),
    LOG("reports.log.export"),
    CANDIDATES("reports.candidates"),
    PLACES("ui.seen.more.than.one"),
    ;

    fun title(): String = LanguageText.text(titleKey)
}

private fun usingSitLabel(state: FieldwatchUi): String {
    state.sit.open?.let { return it.name }
    val selected = state.sit.closed.firstOrNull { it.id == state.sit.selectedId }
    return selected?.name ?: LanguageText.text("ui.last.15.minutes.2")
}

@Composable
private fun ReportTopicList(
    using: String,
    onOpen: (ReportTopic) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            LanguageText.text("reports.using", mapOf("name" to using)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(spectreSectionFill(), RoundedCornerShape(2.dp)),
            )
            Column(
                Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportTopicRow(LanguageText.text("reports.path"), LanguageText.text("ui.view.path.selected.sit")) { onOpen(ReportTopic.PATH) }
                ReportTopicRow(LanguageText.text("reports.debrief"), LanguageText.text("ui.report.selected.sit")) { onOpen(ReportTopic.DEBRIEF) }
                ReportTopicRow(LanguageText.text("reports.sit.export"), LanguageText.text("ui.export.file.selected.sit")) { onOpen(ReportTopic.EXPORT) }
                ReportTopicRow(LanguageText.text("reports.compare"), LanguageText.text("ui.selected.sit.against.another")) { onOpen(ReportTopic.COMPARE) }
            }
        }
    }
}

@Composable
private fun ReportTopicRow(title: String, value: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (value != null) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
    kinds: List<LogExportKind> = LogExportKind.entries.filter { !it.sitOnly },
) {
    var openFormat by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = openFormat,
        onExpandedChange = { openFormat = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        FieldwatchDropdownField(LanguageText.text("ui.format.label"), kind.label(), openFormat)
        ExposedDropdownMenu(openFormat, { openFormat = false }) {
            kinds.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label()) },
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
                Text(item.label(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    FieldwatchActionButton(
        onClick = onShare,
        enabled = !exporting,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(LanguageText.text("ui.share.label")) }
    FieldwatchActionButton(
        onClick = onSave,
        enabled = !exporting,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(LanguageText.text("ui.save.sd.card.storage")) }
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
        open != null -> LanguageText.text("reports.this.window", mapOf("name" to open.name))
        selected != null -> LanguageText.text("reports.sit.name", mapOf("name" to selected.name))
        else -> LanguageText.text("ui.last.15.minutes.still")
    }
    return "$window."
}
