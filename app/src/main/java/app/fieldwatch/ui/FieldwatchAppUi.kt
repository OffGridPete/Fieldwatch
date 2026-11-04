package app.fieldwatch.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBarDefaults
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.ReleaseNotes
import app.fieldwatch.ui.component.FieldwatchDropdownField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fieldwatch.domain.AppUpdatePrompt
import app.fieldwatch.domain.HeaderTone
import app.fieldwatch.domain.MapKeyProblem
import app.fieldwatch.domain.ListLine
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.bleHeader
import app.fieldwatch.domain.gpsHeader
import app.fieldwatch.domain.wifiHeader
import app.fieldwatch.domain.ListSort
import app.fieldwatch.domain.StrengthSort
import app.fieldwatch.domain.ViewMode
import app.fieldwatch.domain.FieldwatchDisclaimer
import app.fieldwatch.domain.disclaimerOk
import app.fieldwatch.radio.RadioPermissions
import app.fieldwatch.ui.screen.LanguageDoor
import app.fieldwatch.ui.screen.LanguageOverlays
import app.fieldwatch.ui.screen.DeviceDetailScreen
import app.fieldwatch.ui.screen.HuntScreen
import app.fieldwatch.ui.screen.FiltersScreen
import app.fieldwatch.ui.screen.FleetsScreen
import app.fieldwatch.ui.screen.LivePane
import app.fieldwatch.ui.screen.CandidatesScreen
import app.fieldwatch.ui.screen.RadioBookmarksScreen
import app.fieldwatch.ui.screen.ReportsScreen
import app.fieldwatch.ui.screen.ScanStatusScreen
import app.fieldwatch.ui.screen.SettingsScreen
import app.fieldwatch.ui.screen.SpokenNamesScreen
import app.fieldwatch.ui.theme.FieldwatchTheme
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.Phosphor
import app.fieldwatch.ui.theme.nightIf

@Composable
fun FieldwatchRoot(vm: FieldwatchViewModel, onRequestPermissions: () -> Unit) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val languageRev by LanguageText.revision.collectAsStateWithLifecycle()
    val importLanguage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importLanguageFromUri) }
    FieldwatchTheme(
        darkTheme = true,
        nightMode = state.settings.nightMode,
    ) {
        if (languageRev >= 0) {
            if (language.snap.showDoor) {
                LanguageDoor(
                    onImport = { importLanguage.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onEnglish = vm::dismissLanguageDoor,
                )
            } else if (!state.settings.disclaimerOk()) {
                DisclaimerGate(onAccept = vm::acceptDisclaimer)
            } else if (!state.permissionsOk) {
                PermissionGate(onRequestPermissions)
            } else {
                FieldwatchShell(state, vm)
            }
            val context = LocalContext.current
            val export by vm.export.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(export.share) {
                export.share?.let { intent ->
                    context.startActivity(Intent.createChooser(intent, export.shareTitle))
                    vm.consumeShare()
                }
            }
            LanguageOverlays(
                state = language,
                onConfirmImport = vm::confirmLanguageImport,
                onCancelImport = vm::cancelLanguageImport,
                onSaveReport = vm::startLanguageReportShare,
                onDismissError = vm::dismissLanguageError,
                onDismissAi = vm::dismissLanguageAi,
            )
        }
    }
}

@Composable
private fun DisclaimerGate(onAccept: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    var agreed by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(
            LanguageText.text("ui.disclaimer.license"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
        Text(
            LanguageText.text("ui.disclaimer.label"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            FieldwatchDisclaimer.firstRunDisclaimer,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            FieldwatchDisclaimer.LICENSE_TITLE,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            FieldwatchDisclaimer.LICENSE_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            FieldwatchDisclaimer.ACCEPT,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.padding(top = 20.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .toggleable(
                    value = agreed,
                    onValueChange = { agreed = it },
                    role = Role.Checkbox,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = agreed, onCheckedChange = null)
            Text(
                LanguageText.text("ui.i.have.read.i"),
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Button(
            onClick = onAccept,
            enabled = agreed,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) { Text(LanguageText.text("ui.continue.label")) }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(LanguageText.text("ui.fieldwatch.needs.radios"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            LanguageText.text("ui.location.nearby.wi.fi"),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRequest) { Text(LanguageText.text("ui.grant.permissions")) }
    }
}

@Composable
private fun AppUpdateLayer(vm: FieldwatchViewModel) {
    val context = LocalContext.current
    val prompt by vm.appUpdate.collectAsStateWithLifecycle()
    val pending by vm.appUpdateIntent.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val intent = pending ?: return@LaunchedEffect
        val started = runCatching { context.startActivity(intent) }.isSuccess
        if (started) vm.consumeAppUpdateIntent() else vm.appUpdateIntentFailed()
    }
    when (val shown = prompt) {
        null -> Unit
        AppUpdatePrompt.Checking -> AppUpdateWait(LanguageText.text("update.checking"), vm::dismissAppUpdate)
        AppUpdatePrompt.Downloading -> AppUpdateWait(LanguageText.text("update.downloading"), vm::dismissAppUpdate)
        is AppUpdatePrompt.Offer -> AlertDialog(
            onDismissRequest = vm::laterAppUpdate,
            title = { Text("Fieldwatch ${shown.note.versionName}") },
            text = { ReleaseNotes(shown.note.notes) },
            confirmButton = {
                TextButton(onClick = vm::installOfferedUpdate) { Text(LanguageText.text("update.now")) }
            },
            dismissButton = {
                TextButton(onClick = vm::laterAppUpdate) { Text(LanguageText.text("update.later")) }
            },
        )
        is AppUpdatePrompt.Current -> AppUpdateNotice(
            title = LanguageText.text("update.current.title"),
            body = LanguageText.text("update.current.body", mapOf("version" to shown.versionName)),
            onClose = vm::dismissAppUpdate,
        )
        AppUpdatePrompt.Unreachable -> AppUpdateNotice(
            title = LanguageText.text("update.unreachable.title"),
            body = LanguageText.text("update.unreachable.body"),
            onClose = vm::dismissAppUpdate,
        )
        AppUpdatePrompt.Failed -> AppUpdateNotice(
            title = LanguageText.text("update.failed.title"),
            body = LanguageText.text("update.failed.body"),
            onClose = vm::dismissAppUpdate,
        )
        AppUpdatePrompt.Allow -> AlertDialog(
            onDismissRequest = vm::dismissAppUpdate,
            title = { Text(LanguageText.text("update.allow.title")) },
            text = { Text(LanguageText.text("update.allow.body")) },
            confirmButton = {
                TextButton(onClick = vm::openUnknownInstallSettings) {
                    Text(LanguageText.text("ui.open.android.settings"))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissAppUpdate) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
}

@Composable
private fun AppUpdateWait(title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
        confirmButton = { },
    )
}

@Composable
private fun AppUpdateNotice(title: String, body: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onClose) { Text(LanguageText.text("ui.ok.label")) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldwatchShell(state: FieldwatchUi, vm: FieldwatchViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: "live"
    val context = LocalContext.current
    AppUpdateLayer(vm)
    val export by vm.export.collectAsStateWithLifecycle()
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    val gnssStatus by vm.gnssStatus.collectAsStateWithLifecycle()
    val gnssNotice by vm.gnssNotice.collectAsStateWithLifecycle()
    val gnssProfile by vm.gnssProfile.collectAsStateWithLifecycle()
    val logKind by vm.logExportKind.collectAsStateWithLifecycle()
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(vm.exportMime()),
    ) { uri ->
        uri?.let(vm::startSaveToUri)
    }
    val sitSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(vm.sitExportMime()),
    ) { uri ->
        uri?.let(vm::startSitSaveToUri)
    }

    if (export.active) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                val m = export.message.lowercase()
                Text(
                    when {
                        LanguageText.text("ui.sit.compare") in m && "pdf" in m -> LanguageText.text("ui.sit.compare.pdf")
                        LanguageText.text("ui.sit.compare") in m && "ai" in m -> LanguageText.text("ui.sit.compare.ai.export")
                        LanguageText.text("ui.sit.compare") in m -> LanguageText.text("ui.sit.compare.2")
                        LanguageText.text("ui.ai.export.2") in m || LanguageText.text("ui.ai.export.2") in m -> LanguageText.text("ui.ai.export")
                        "pdf" in m -> LanguageText.text("ui.debrief.pdf")
                        "debrief" in m -> LanguageText.text("reports.debrief")
                        else -> LanguageText.text("ui.export.label")
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(export.message.ifBlank { LanguageText.text("ui.please.wait") })
                    if ("debrief" !in export.message.lowercase() &&
                        LanguageText.text("ui.ai.export.2") !in export.message.lowercase() &&
                        LanguageText.text("ui.sit.compare") !in export.message.lowercase()
                    ) {
                        Text(
                            LanguageText.text("ui.live.logging.paused.until"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { export.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${(export.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = { },
        )
    }
    if (candidates.loading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(LanguageText.text("reports.candidates")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(candidates.message.ifBlank { LanguageText.text("ui.reading.log") })
                    LinearProgressIndicator(
                        progress = { candidates.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${(candidates.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = { },
        )
    }
    if (export.error != null) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(export.errorTitle ?: LanguageText.text("ui.could.not.export")) },
            text = { Text(export.error ?: "") },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text(LanguageText.text("ui.ok.label")) }
            },
        )
    }
    if (export.saved) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(LanguageText.text("ui.log.saved")) },
            text = {
                Text(LanguageText.text("ui.file.was.written.folder"))
            },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text(LanguageText.text("ui.ok.label")) }
            },
        )
    }
    if (export.cleared) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(LanguageText.text("ui.log.cleared")) },
            text = { Text(LanguageText.text("ui.rotated.files.were.deleted")) },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text(LanguageText.text("ui.ok.label")) }
            },
        )
    }
    if (export.noticeTitle != null) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(export.noticeTitle ?: "") },
            text = { Text(export.noticeMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text(LanguageText.text("ui.ok.label")) }
            },
        )
    }
    val mapNotice by vm.mapNotice.collectAsStateWithLifecycle()
    val shownMapNotice = mapNotice
    if (shownMapNotice != null) {
        val problem = shownMapNotice.problem
        AlertDialog(
            onDismissRequest = vm::dismissMapNotice,
            title = {
                Text(
                    LanguageText.text(
                        when (problem) {
                            MapKeyProblem.MISSING -> "map.key.missing.title"
                            MapKeyProblem.DENIED -> "map.key.denied.title"
                            MapKeyProblem.REJECTED -> "map.key.rejected.title"
                            else -> "map.key.failed.title"
                        },
                    ),
                )
            },
            text = {
                Text(
                    LanguageText.text(
                        when (problem) {
                            MapKeyProblem.MISSING -> "map.key.missing.body"
                            MapKeyProblem.DENIED -> "map.key.denied.body"
                            MapKeyProblem.REJECTED -> "map.key.rejected.body"
                            else -> "map.key.failed.body"
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = vm::dismissMapNotice) { Text(LanguageText.text("ui.ok.label")) }
            },
        )
    }
    val view = LocalView.current
    var tourTargets by remember { mutableStateOf(LiveTourTargets()) }
    val showTour = !state.settings.liveTourDone && route == "live"
    LaunchedEffect(showTour) {
        if (showTour && state.settings.scanControlsExpanded) {
            vm.setScanControlsExpanded(false)
        }
    }
    val keepAwake = state.settings.keepScreenOn || route == "hunt"
    DisposableEffect(keepAwake) {
        val window = (view.context as? android.app.Activity)?.window
        if (keepAwake) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (route != "detail" && route != "hunt") {
                TopAppBar(
                    expandedHeight = 52.dp,
                    title = {
                        val screenW = LocalConfiguration.current.screenWidthDp.dp
                        val actionW = if (route == "live") 56.dp else 16.dp
                        Column(
                            modifier = Modifier
                                .widthIn(max = (screenW - 20.dp - actionW).coerceAtLeast(120.dp))
                                .fillMaxWidth()
                                .pointerInput(route) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (route != "live") {
                                                nav.navigate("live") { launchSingleTop = true }
                                            }
                                            vm.focusLiveList()
                                        },
                                    )
                                },
                        ) {
                            Text(
                                when {
                                    state.sit.open != null && state.displayPaused ->
                                        LanguageText.text("ui.fieldwatch.sit.paused")
                                    state.sit.open != null -> LanguageText.text("ui.fieldwatch.sit")
                                    state.displayPaused -> LanguageText.text("ui.fieldwatch.paused")
                                    else -> "FIELDWATCH"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                                val night = LocalNightMode.current
                                val wifiHead = wifiHeader(state.scanning, state.locationOn, state.scanRadio)
                                val bleHead = bleHeader(state.scanning, state.locationOn, state.scanRadio)
                                HeaderCount(
                                    state.wifiNow,
                                    Icons.Outlined.Wifi,
                                    wifiHead.second,
                                    headerTint(wifiHead.first, muted, night),
                                )
                                HeaderCount(
                                    state.bleNow,
                                    Icons.Outlined.Bluetooth,
                                    bleHead.second,
                                    headerTint(bleHead.first, muted, night),
                                )
                                HeaderCount(
                                    state.namedNow,
                                    Icons.Outlined.Hub,
                                    if (state.scanning) "signatures" else LanguageText.text("ui.signatures.idle"),
                                    headerTint(
                                        if (state.scanning) HeaderTone.HEALTHY else HeaderTone.IDLE,
                                        muted,
                                        night,
                                    ),
                                )
                                val notice = gnssNotice
                                val gnssAlert = notice != null && notice.line.isNotBlank() &&
                                    (notice.showDialog || !notice.ended)
                                val gps = gpsHeader(
                                    monitorOn = state.settings.gnssMonitor && gnssProfile != null,
                                    suppressAlerts = gnssProfile?.suppressAlerts == true,
                                    scanning = state.scanning,
                                    statusLine = gnssStatus,
                                    alertShowing = gnssAlert,
                                )
                                if (gps != null) {
                                    Icon(
                                        Icons.Outlined.GpsFixed,
                                        contentDescription = gps.second,
                                        modifier = Modifier.size(13.dp),
                                        tint = headerTint(gps.first, muted, night),
                                    )
                                }
                                if (state.throttleHint.isNotBlank()) {
                                    Text(
                                        "·  ${state.throttleHint}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = muted,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (route == "live") {
                            IconButton(
                                onClick = {
                                    vm.setScanControlsExpanded(!state.settings.scanControlsExpanded)
                                },
                                modifier = Modifier.onGloballyPositioned {
                                    tourTargets = tourTargets.copy(tune = it.boundsInRoot())
                                },
                            ) {
                                Icon(
                                    if (state.settings.scanControlsExpanded) {
                                        Icons.Outlined.ExpandLess
                                    } else {
                                        Icons.Outlined.Tune
                                    },
                                    if (state.settings.scanControlsExpanded) {
                                        LanguageText.text("ui.hide.scan.options")
                                    } else {
                                        LanguageText.text("ui.show.scan.options")
                                    },
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (route != "detail" && route != "hunt") {
                Column {
                    if (route == "live" && (state.filter.arrivalsOnly || state.filter.movingWithYou)) {
                        LiveSessionBar(
                            arrivalsOnly = state.filter.arrivalsOnly,
                            movingWithYou = state.filter.movingWithYou,
                            onMarkSeen = vm::markArrivalsSeen,
                            onResetSeen = vm::resetArrivalsSeen,
                            onStartOverFollow = vm::resetFollowSession,
                        )
                    }
                    Surface(
                        color = NavigationBarDefaults.containerColor,
                        tonalElevation = NavigationBarDefaults.Elevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 1.dp)
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        FieldwatchNavTab(
                            weight = 1f,
                            selected = route == "live",
                            onClick = {
                                if (route == "live") {
                                    vm.toggleLiveDisplay()
                                } else {
                                    nav.navigate("live") { launchSingleTop = true }
                                }
                            },
                            icon = {
                                Icon(
                                    when {
                                        route == "live" && !state.displayPaused -> Icons.Outlined.Pause
                                        route == "live" && state.displayPaused -> Icons.Outlined.PlayArrow
                                        else -> Icons.Outlined.CellTower
                                    },
                                    if (route == "live" && !state.displayPaused) {
                                        LanguageText.text("chrome.pause.display")
                                    } else {
                                        LanguageText.text("tab.live")
                                    },
                                )
                            },
                            label = if (route == "live" && !state.displayPaused) {
                                LanguageText.text("tab.pause")
                            } else {
                                LanguageText.text("tab.live")
                            },
                        )
                        FieldwatchNavTab(
                            weight = 1f,
                            selected = route == "filters",
                            onClick = { nav.navigate("filters") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.FilterAlt, null) },
                            label = LanguageText.text("tab.filters"),
                        )
                        FieldwatchNavTab(
                            weight = 1.45f,
                            selected = route == "fleets",
                            onClick = { nav.navigate("fleets") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Hub, null) },
                            label = LanguageText.text("tab.signatures"),
                        )
                        FieldwatchNavTab(
                            weight = 1f,
                            selected = route == "reports" || route == "candidates",
                            onClick = { nav.navigate("reports") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Description, null) },
                            label = LanguageText.text("tab.reports"),
                        )
                        FieldwatchNavTab(
                            weight = 1.05f,
                            selected = route == "settings" || route == "radio-bookmarks" || route == "scan-status" || route == "spoken-names",
                            onClick = { nav.navigate("settings") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Settings, null) },
                            label = LanguageText.text("tab.settings"),
                        )
                        }
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            nav,
            startDestination = "live",
            modifier = Modifier.padding(pad),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("live") {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    LivePane(
                        state = state,
                        vm = vm,
                        onOpen = {
                            vm.select(it)
                            nav.navigate("detail")
                        },
                    )
                    AnimatedVisibility(
                        visible = state.settings.scanControlsExpanded,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { vm.setScanControlsExpanded(false) },
                        )
                    }
                    AnimatedVisibility(
                        visible = state.settings.scanControlsExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .heightIn(max = maxHeight),
                    ) {
                        ViewPicker(
                            maxHeight = maxHeight,
                            mode = state.settings.viewMode,
                            sort = state.settings.strengthSort,
                            listSort = state.settings.listSort,
                            windowSec = state.settings.averageWindowSec,
                            decaySec = state.settings.decaySec,
                            showLivePath = state.settings.showLivePath,
                            showBar = state.settings.showRssiBar,
                            showFleet = state.settings.showFleetName,
                            showFrequency = state.settings.showFrequency,
                            showSeenTimes = state.settings.showSeenTimes,
                            titleLine = state.settings.listTitleLine,
                            subtitleLine = state.settings.listSubtitleLine,
                            onChangeView = vm::setViewMode,
                            onChangeSort = vm::setStrengthSort,
                            onChangeListSort = vm::setListSort,
                            onChangeDecay = vm::setDecaySec,
                            onToggleLivePath = vm::toggleLivePath,
                            onToggleBar = vm::toggleRssiBar,
                            onToggleFleet = vm::toggleFleetName,
                            onToggleFrequency = vm::toggleFrequency,
                            onToggleSeenTimes = vm::toggleSeenTimes,
                            onChangeTitleLine = vm::setListTitleLine,
                            onChangeSubtitleLine = vm::setListSubtitleLine,
                        )
                    }
                }
            }
            composable("fleets") {
                FleetsScreen(
                    state = state,
                    vm = vm,
                    onCandidateDraftClosed = {
                        if (!nav.popBackStack("candidates", false)) {
                            nav.navigate("candidates")
                        }
                    },
                )
            }
            composable("filters") { FiltersScreen(state, vm) }
            composable("reports") {
                ReportsScreen(
                    state = state,
                    vm = vm,
                    exporting = export.active,
                    onSaveToStorage = { saveLauncher.launch(vm.suggestedExportName()) },
                    onSaveSitToStorage = { sitSaveLauncher.launch(vm.suggestedSitExportName()) },
                    onSignatureCandidates = {
                        vm.startSignatureCandidates()
                        nav.navigate("candidates")
                    },
                    onOpenPathRadio = { key ->
                        if (vm.openPathRadio(key)) nav.navigate("detail")
                    },
                    onOpenPlaceRadio = { key ->
                        if (vm.openPlaceRadio(key)) nav.navigate("detail")
                    },
                )
            }
            composable("candidates") {
                CandidatesScreen(
                    vm = vm,
                    demoMode = state.settings.demoMode,
                    onBack = { nav.popBackStack() },
                    onCreate = { candidate ->
                        vm.beginCreateFromCandidate(candidate)
                        nav.navigate("fleets")
                    },
                )
            }
            composable("settings") {
                SettingsScreen(
                    state = state,
                    vm = vm,
                    onRadioBookmarks = { nav.navigate("radio-bookmarks") },
                    onSpokenNames = { nav.navigate("spoken-names") },
                    onScanStatus = { nav.navigate("scan-status") },
                    onShowLiveTour = {
                        vm.showLiveTour {
                            nav.navigate("live") { launchSingleTop = true }
                        }
                    },
                )
            }
            composable("scan-status") {
                ScanStatusScreen(state = state, onBack = { nav.popBackStack() })
            }
            composable("spoken-names") {
                SpokenNamesScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("radio-bookmarks") {
                RadioBookmarksScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpen = { key ->
                        vm.select(key)
                        nav.navigate("detail")
                    },
                )
            }
            composable("detail") {
                val device = state.selected
                val onBack = { nav.popBackStack(); vm.select(null); Unit }
                if (device == null) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(LanguageText.text("ui.detail.label")) },
                                navigationIcon = {
                                    IconButton(onClick = onBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, LanguageText.text("chrome.back"))
                                    }
                                },
                            )
                        },
                    ) { pad ->
                        Text(
                            LanguageText.text("ui.no.radio.selected"),
                            Modifier.padding(pad).padding(24.dp),
                        )
                    }
                } else {
                    DeviceDetailScreen(
                        device = device,
                        vm = vm,
                        watched = vm.isWatched(device.key),
                        onBack = onBack,
                        onCreateFleet = { vm.beginCreateFrom(device); nav.navigate("fleets") },
                        onHunt = {
                            vm.startHunt(device)
                            nav.navigate("hunt")
                        },
                        demoMode = state.settings.demoMode,
                        spokenNames = state.settings.spokenNames,
                        voiceWhat = state.settings.alertVoiceWhat,
                        watchlist = state.watchlist,
                    )
                }
            }
            composable("hunt") {
                HuntScreen(
                    vm = vm,
                    demoMode = state.settings.demoMode,
                    huntBeep = state.settings.huntBeep,
                    huntVibrate = state.settings.huntVibrate,
                    onBack = {
                        vm.stopHunt()
                        nav.popBackStack()
                    },
                )
            }
        }
    }
    if (showTour) {
        LiveChromeTour(
            tune = tourTargets.tune,
            onOpen = vm::openTuneFromTour,
            onSkip = vm::dismissLiveTour,
        )
    }
    }
}

@Composable
private fun headerTint(tone: HeaderTone, muted: Color, night: Boolean): Color {
    val color = when (tone) {
        HeaderTone.HEALTHY -> Phosphor
        HeaderTone.PROBLEM -> MaterialTheme.colorScheme.error
        HeaderTone.OFF -> muted.copy(alpha = 0.38f)
        HeaderTone.IDLE -> muted
    }
    return color.nightIf(night)
}

@Composable
private fun HeaderCount(n: Int, icon: ImageVector, desc: String, tint: Color) {
    val style = MaterialTheme.typography.labelSmall
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = desc,
            modifier = Modifier.size(13.dp),
            tint = tint,
        )
        Box {
            Text(
                HEADER_COUNT_SLOT,
                modifier = Modifier.clearAndSetSemantics {},
                style = style,
                fontFamily = FontFamily.Monospace,
                color = Color.Transparent,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                n.toString(),
                style = style,
                fontFamily = FontFamily.Monospace,
                color = tint,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Three digits. The header counts stay this wide so the line does not shift. */
private const val HEADER_COUNT_SLOT = "888"

@Composable
private fun RowScope.FieldwatchNavTab(
    weight: Float,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .weight(weight)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            Box(
                Modifier.height(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private val radarSweepGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "RadarSweep",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
        ) {
            moveTo(12f, 3f)
            arcTo(9f, 9f, 0f, false, true, 21f, 12f)
            arcTo(9f, 9f, 0f, false, true, 12f, 21f)
            arcTo(9f, 9f, 0f, false, true, 3f, 12f)
            arcTo(9f, 9f, 0f, false, true, 12f, 3f)
        }
        // Upper-right sector, from 12 o'clock clockwise, inside the ring.
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 12f)
            lineTo(12f, 4.8f)
            arcTo(7.2f, 7.2f, 0f, false, true, 18.53f, 8.96f)
            close()
        }
    }.build()
}

private val dashedLineGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "DashedLine",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
        ) {
            moveTo(3f, 12f)
            lineTo(6.2f, 12f)
            moveTo(8.2f, 12f)
            lineTo(11.4f, 12f)
            moveTo(13.4f, 12f)
            lineTo(16.6f, 12f)
            moveTo(18.6f, 12f)
            lineTo(21.8f, 12f)
        }
    }.build()
}

private val classBarsGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "ClassBars",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 5f)
            lineTo(16f, 5f)
            lineTo(16f, 8f)
            lineTo(3f, 8f)
            close()
            moveTo(8f, 10.5f)
            lineTo(21f, 10.5f)
            lineTo(21f, 13.5f)
            lineTo(8f, 13.5f)
            close()
            moveTo(3f, 16f)
            lineTo(16f, 16f)
            lineTo(16f, 19f)
            lineTo(3f, 19f)
            close()
        }
    }.build()
}

private fun ViewMode.glyph(): ImageVector = when (this) {
    ViewMode.RADAR -> radarSweepGlyph
    ViewMode.LIST -> Icons.AutoMirrored.Outlined.FormatListBulleted
    ViewMode.TIMELINE -> dashedLineGlyph
    ViewMode.HYBRID -> Icons.AutoMirrored.Outlined.ShowChart
    ViewMode.BY_CLASS -> classBarsGlyph
}

@Composable
private fun ViewModeGlyph(mode: ViewMode, compact: Boolean = false) {
    Icon(
        mode.glyph(),
        contentDescription = null,
        modifier = if (compact) Modifier.size(18.dp) else Modifier,
        tint = MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewPicker(
    maxHeight: Dp,
    mode: ViewMode,
    sort: StrengthSort,
    listSort: ListSort,
    windowSec: Int,
    decaySec: Int,
    showLivePath: Boolean,
    showBar: Boolean,
    showFleet: Boolean,
    showFrequency: Boolean,
    showSeenTimes: Boolean,
    titleLine: ListLine,
    subtitleLine: ListLine,
    onChangeView: (ViewMode) -> Unit,
    onChangeSort: (StrengthSort, Int?) -> Unit,
    onChangeListSort: (ListSort) -> Unit,
    onChangeDecay: (Int) -> Unit,
    onToggleLivePath: () -> Unit,
    onToggleBar: () -> Unit,
    onToggleFleet: () -> Unit,
    onToggleFrequency: () -> Unit,
    onToggleSeenTimes: () -> Unit,
    onChangeTitleLine: (ListLine) -> Unit,
    onChangeSubtitleLine: (ListLine) -> Unit,
) {
    var openView by remember { mutableStateOf(false) }
    var openSort by remember { mutableStateOf(false) }
    val viewLabel = mode.label()
    var openDecay by remember { mutableStateOf(false) }
    var openTitle by remember { mutableStateOf(false) }
    var openSubtitle by remember { mutableStateOf(false) }
    val listFields = mode.usesListFields()
    val rssiBars = mode.usesRssiBars()
    LaunchedEffect(listFields) {
        if (!listFields) {
            openTitle = false
            openSubtitle = false
        }
    }
    val sortLabel = when (listSort) {
        ListSort.STRENGTH -> if (sort == StrengthSort.AVERAGE) "Strongest · avg ${windowSec}s" else LanguageText.text("ui.strongest.label")
        ListSort.NEWEST -> LanguageText.text("ui.newest.heard")
        ListSort.NEWEST_ALERT -> LanguageText.text("ui.newest.alert")
        ListSort.FIRST_SEEN -> LanguageText.text("ui.newest.arrival")
        ListSort.ARRIVAL -> LanguageText.text("ui.new.bottom")
        ListSort.NAME -> LanguageText.text("sort.name")
        ListSort.SIGNATURES -> LanguageText.text("ui.signatures.first")
    }
    val decayLabel = if (decaySec <= 0) LanguageText.text("ui.off.label") else "Hold ${decaySec}s"
    val scroll = rememberScrollState()
    val panelMax = (maxHeight - 8.dp).coerceAtLeast(140.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .heightIn(max = panelMax),
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Box {
        Column(
            Modifier
                .verticalScroll(scroll)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .padding(bottom = if (scroll.canScrollForward) 20.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                LanguageText.text("ui.display.label"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val dropdownPad = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ExposedDropdownMenuBox(openView, { openView = it }, dropdownPad) {
                FieldwatchDropdownField(
                    LanguageText.text("ui.view.label"),
                    viewLabel,
                    openView,
                    leadingIcon = { ViewModeGlyph(mode, compact = true) },
                )
                ExposedDropdownMenu(openView, { openView = false }) {
                    ViewMode.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label()) },
                            leadingIcon = { ViewModeGlyph(item) },
                            onClick = { onChangeView(item); openView = false },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(openSort, { openSort = it }, dropdownPad) {
                FieldwatchDropdownField(LanguageText.text("ui.sort.label"), sortLabel, openSort)
                ExposedDropdownMenu(openSort, { openSort = false }) {
                    DropdownMenuItem(
                        text = { Text(LanguageText.text("ui.strongest.signal")) },
                        onClick = { onChangeSort(StrengthSort.INSTANT, null); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(LanguageText.text("ui.strongest.avg.30s")) },
                        onClick = { onChangeSort(StrengthSort.AVERAGE, 30); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(LanguageText.text("ui.newest.heard")) },
                        onClick = { onChangeListSort(ListSort.NEWEST); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(LanguageText.text("ui.newest.alert")) },
                        onClick = { onChangeListSort(ListSort.NEWEST_ALERT); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(LanguageText.text("ui.newest.arrival")) },
                        onClick = { onChangeListSort(ListSort.FIRST_SEEN); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(LanguageText.text("ui.new.bottom")) },
                        onClick = { onChangeListSort(ListSort.ARRIVAL); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(LanguageText.text("sort.name")) },
                        onClick = { onChangeListSort(ListSort.NAME); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text(LanguageText.text("ui.signatures.first")) },
                        onClick = { onChangeListSort(ListSort.SIGNATURES); openSort = false },
                    )
                }
            }
            ExposedDropdownMenuBox(openDecay, { openDecay = it }, dropdownPad) {
                FieldwatchDropdownField(LanguageText.text("ui.brief.hold"), decayLabel, openDecay)
                ExposedDropdownMenu(openDecay, { openDecay = false }) {
                    DropdownMenuItem(
                        text = { Text(LanguageText.text("ui.off.stale.after.only")) },
                        onClick = { onChangeDecay(0); openDecay = false },
                    )
                    listOf(10, 30, 60).forEach { sec ->
                        DropdownMenuItem(
                            text = { Text("Hold ${sec}s after last packet") },
                            onClick = { onChangeDecay(sec); openDecay = false },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(openTitle && listFields, { if (listFields) openTitle = it }, dropdownPad) {
                FieldwatchDropdownField(
                    LanguageText.text("ui.title.line"),
                    listLineLabel(titleLine),
                    openTitle && listFields,
                    enabled = listFields,
                )
                ExposedDropdownMenu(openTitle && listFields, { openTitle = false }) {
                    listOf(ListLine.ADVERTISED_NAME, ListLine.NAME_AND_TYPE, ListLine.MAC).forEach { item ->
                        DropdownMenuItem(
                            text = { Text(listLineLabel(item)) },
                            onClick = { onChangeTitleLine(item); openTitle = false },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(openSubtitle && listFields, { if (listFields) openSubtitle = it }, dropdownPad) {
                FieldwatchDropdownField(
                    LanguageText.text("ui.subtitle.line"),
                    listLineLabel(subtitleLine),
                    openSubtitle && listFields,
                    enabled = listFields,
                )
                ExposedDropdownMenu(openSubtitle && listFields, { openSubtitle = false }) {
                    listOf(
                        ListLine.ADVERTISED_NAME,
                        ListLine.NAME_AND_TYPE,
                        ListLine.MAC,
                        ListLine.NONE,
                    ).forEach { item ->
                        DropdownMenuItem(
                            text = { Text(listLineLabel(item)) },
                            onClick = { onChangeSubtitleLine(item); openSubtitle = false },
                        )
                    }
                }
            }
            OptionSwitch(
                LanguageText.text("display.live.path"),
                showLivePath,
                onToggleLivePath,
                enabled = listFields,
            )
            OptionSwitch(LanguageText.text("display.rssi"), showBar, onToggleBar, enabled = rssiBars)
            OptionSwitch(LanguageText.text("ui.signature.names"), showFleet, onToggleFleet)
            OptionSwitch(
                LanguageText.text("ui.frequency.label"),
                showFrequency,
                onToggleFrequency,
                enabled = listFields,
            )
            OptionSwitch(
                LanguageText.text("ui.first.last.seen"),
                showSeenTimes,
                onToggleSeenTimes,
                enabled = listFields,
            )
        }
        if (scroll.canScrollForward) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, surfaceColor),
                        ),
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = LanguageText.text("ui.more.display.options.below"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        }
    }
}

private fun listLineLabel(line: ListLine): String = when (line) {
    ListLine.ADVERTISED_NAME -> LanguageText.text("display.advertised")
    ListLine.NAME_AND_TYPE -> LanguageText.text("display.name.type")
    ListLine.MAC -> LanguageText.text("display.mac")
    ListLine.NONE -> LanguageText.text("display.none")
}

@Composable
private fun LiveSessionBar(
    arrivalsOnly: Boolean,
    movingWithYou: Boolean,
    onMarkSeen: () -> Unit,
    onResetSeen: () -> Unit,
    onStartOverFollow: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (arrivalsOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FieldwatchActionButton(
                        onClick = onMarkSeen,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text(LanguageText.text("ui.mark.seen")) }
                    FieldwatchActionButton(
                        onClick = onResetSeen,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text(LanguageText.text("ui.reset.seen")) }
                }
            }
            if (movingWithYou) {
                FieldwatchActionButton(
                    onClick = onStartOverFollow,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) { Text(LanguageText.text("ui.start.over")) }
            }
        }
    }
}

@Composable
private fun OptionSwitch(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
        FieldwatchSwitch(
            checked = checked,
            onCheckedChange = if (enabled) {
                { onToggle() }
            } else {
                null
            },
            enabled = enabled,
        )
    }
}

@Suppress("unused")
private val unusedPerms = RadioPermissions
