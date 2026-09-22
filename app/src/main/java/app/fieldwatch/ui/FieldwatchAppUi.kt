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
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
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
import app.fieldwatch.domain.ListLine
import app.fieldwatch.domain.ListSort
import app.fieldwatch.domain.StrengthSort
import app.fieldwatch.domain.ViewMode
import app.fieldwatch.domain.FieldwatchDisclaimer
import app.fieldwatch.domain.disclaimerOk
import app.fieldwatch.radio.RadioPermissions
import app.fieldwatch.ui.screen.DeviceDetailScreen
import app.fieldwatch.ui.screen.HuntScreen
import app.fieldwatch.ui.screen.MapScreen
import app.fieldwatch.ui.screen.FiltersScreen
import app.fieldwatch.ui.screen.FleetsScreen
import app.fieldwatch.ui.screen.LivePane
import app.fieldwatch.ui.screen.CandidatesScreen
import app.fieldwatch.ui.screen.RadioBookmarksScreen
import app.fieldwatch.ui.screen.ReportsScreen
import app.fieldwatch.ui.screen.SettingsScreen
import app.fieldwatch.ui.theme.FieldwatchTheme

@Composable
fun FieldwatchRoot(vm: FieldwatchViewModel, onRequestPermissions: () -> Unit) {
    val state by vm.ui.collectAsStateWithLifecycle()
    FieldwatchTheme(
        darkTheme = true,
        nightMode = state.settings.nightMode,
    ) {
        if (!state.settings.disclaimerOk()) {
            DisclaimerGate(onAccept = vm::acceptDisclaimer)
        } else if (!state.permissionsOk) {
            PermissionGate(onRequestPermissions)
        } else {
            FieldwatchShell(state, vm)
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
            "Disclaimer and license",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
        Text(
            "Disclaimer",
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
                "I have read this and I agree",
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
        ) { Text("Continue") }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Fieldwatch needs the radios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Location, nearby Wi-Fi, Bluetooth scan, and notifications let Fieldwatch passively watch advertised networks and BLE devices. Nothing is transmitted.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRequest) { Text("Grant permissions") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldwatchShell(state: FieldwatchUi, vm: FieldwatchViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: "live"
    val context = LocalContext.current
    val export by vm.export.collectAsStateWithLifecycle()
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(vm.exportMime()),
    ) { uri ->
        uri?.let(vm::startSaveToUri)
    }
    androidx.compose.runtime.LaunchedEffect(export.share) {
        export.share?.let { intent ->
            context.startActivity(Intent.createChooser(intent, export.shareTitle))
            vm.consumeShare()
        }
    }
    if (export.active) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(
                    when {
                        export.message.contains("AI export", ignoreCase = true) -> "AI Export"
                        export.message.contains("PDF", ignoreCase = true) -> "Debrief PDF"
                        export.message.contains("debrief", ignoreCase = true) -> "Debrief"
                        else -> "Working on log"
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(export.message.ifBlank { "Please wait…" })
                    if (!export.message.contains("debrief", ignoreCase = true) &&
                        !export.message.contains("AI export", ignoreCase = true)
                    ) {
                        Text(
                            "Live logging is paused until this finishes. Scanning continues.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (export.spinner) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.CenterHorizontally),
                            strokeWidth = 3.dp,
                        )
                    } else {
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
                }
            },
            confirmButton = { },
        )
    }
    if (export.error != null) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(export.errorTitle ?: "Could not export") },
            text = { Text(export.error ?: "") },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text("OK") }
            },
        )
    }
    if (export.saved) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text("Log saved") },
            text = {
                Text("The file was written to the folder you picked. In the system picker, use the menu to choose the SD card if you want it off internal storage.")
            },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text("OK") }
            },
        )
    }
    if (export.cleared) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text("Log cleared") },
            text = { Text("Rotated files were deleted. New detections will start a fresh log.") },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text("OK") }
            },
        )
    }
    if (export.noticeTitle != null) {
        AlertDialog(
            onDismissRequest = vm::consumeExportNotice,
            title = { Text(export.noticeTitle ?: "") },
            text = { Text(export.noticeMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = vm::consumeExportNotice) { Text("OK") }
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
        topBar = {
            if (route != "detail" && route != "hunt") {
                TopAppBar(
                    expandedHeight = 52.dp,
                    title = {
                        val screenW = LocalConfiguration.current.screenWidthDp.dp
                        val actionW = if (route == "live") 104.dp else 48.dp
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
                                        "FIELDWATCH  ·  SIT  ·  PAUSED"
                                    state.sit.open != null -> "FIELDWATCH  ·  SIT"
                                    state.displayPaused -> "FIELDWATCH  ·  PAUSED"
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
                                HeaderCount(state.wifiNow, Icons.Outlined.Wifi, "Wi-Fi")
                                HeaderCount(state.bleNow, Icons.Outlined.Bluetooth, "BLE")
                                HeaderCount(state.namedNow, Icons.Outlined.Hub, "signatures")
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
                        IconButton(onClick = { nav.navigate("map") { launchSingleTop = true } }) {
                            Icon(Icons.Outlined.Map, "Offline map")
                        }
                        if (route == "live") {
                            IconButton(
                                onClick = {
                                    vm.setScanControlsExpanded(!state.settings.scanControlsExpanded)
                                },
                            ) {
                                Icon(
                                    if (state.settings.scanControlsExpanded) {
                                        Icons.Outlined.ExpandLess
                                    } else {
                                        Icons.Outlined.Tune
                                    },
                                    if (state.settings.scanControlsExpanded) {
                                        "Hide scan options"
                                    } else {
                                        "Show scan options"
                                    },
                                    modifier = Modifier.onGloballyPositioned {
                                        tourTargets = tourTargets.copy(tune = it.boundsInRoot())
                                    },
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
                            onBounds = { tourTargets = tourTargets.copy(pause = it) },
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
                                    if (route == "live" && !state.displayPaused) "Pause display" else "Live",
                                )
                            },
                            label = if (route == "live" && !state.displayPaused) "Pause" else "Live",
                        )
                        FieldwatchNavTab(
                            weight = 1f,
                            selected = route == "filters",
                            onBounds = { tourTargets = tourTargets.copy(filters = it) },
                            onClick = { nav.navigate("filters") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.FilterAlt, null) },
                            label = "Filters",
                        )
                        FieldwatchNavTab(
                            weight = 1.45f,
                            selected = route == "fleets",
                            onBounds = { tourTargets = tourTargets.copy(signatures = it) },
                            onClick = { nav.navigate("fleets") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Hub, null) },
                            label = "Signatures",
                        )
                        FieldwatchNavTab(
                            weight = 1f,
                            selected = route == "reports" || route == "candidates",
                            onBounds = { tourTargets = tourTargets.copy(reports = it) },
                            onClick = { nav.navigate("reports") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Description, null) },
                            label = "Reports",
                        )
                        FieldwatchNavTab(
                            weight = 1.05f,
                            selected = route == "settings" || route == "radio-bookmarks",
                            onBounds = { tourTargets = tourTargets.copy(settings = it) },
                            onClick = { nav.navigate("settings") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Settings, null) },
                            label = "Settings",
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
            composable("map") {
                MapScreen(
                    state = state,
                    vm = vm,
                    demoMode = state.settings.demoMode,
                    onOpen = { key ->
                        vm.select(key)
                        nav.navigate("detail")
                    },
                )
            }
            composable("reports") {
                ReportsScreen(
                    state = state,
                    vm = vm,
                    exporting = export.active,
                    onSaveToStorage = { saveLauncher.launch(vm.suggestedExportName()) },
                    onSignatureCandidates = {
                        vm.startSignatureCandidates()
                        nav.navigate("candidates")
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
                    onShowLiveTour = {
                        vm.showLiveTour {
                            nav.navigate("live") { launchSingleTop = true }
                        }
                    },
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
                                title = { Text("Detail") },
                                navigationIcon = {
                                    IconButton(onClick = onBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                    }
                                },
                            )
                        },
                    ) { pad ->
                        Text(
                            "No radio selected.",
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
        LiveChromeTour(targets = tourTargets, onDismiss = vm::dismissLiveTour)
    }
    }
}

@Composable
private fun HeaderCount(n: Int, icon: ImageVector, desc: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = desc,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            n.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun RowScope.FieldwatchNavTab(
    weight: Float,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    onBounds: (Rect) -> Unit = {},
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
                Box(Modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) }) {
                    icon()
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewPicker(
    maxHeight: Dp,
    mode: ViewMode,
    sort: StrengthSort,
    listSort: ListSort,
    windowSec: Int,
    decaySec: Int,
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
    val sortLabel = when (listSort) {
        ListSort.STRENGTH -> if (sort == StrengthSort.AVERAGE) "Strongest · avg ${windowSec}s" else "Strongest"
        ListSort.NEWEST -> "Newest heard"
        ListSort.NEWEST_ALERT -> "Newest alert"
        ListSort.FIRST_SEEN -> "Newest arrival"
        ListSort.ARRIVAL -> "New at bottom"
        ListSort.NAME -> "Name A–Z"
        ListSort.SIGNATURES -> "Signatures first"
    }
    val decayLabel = if (decaySec <= 0) "Off" else "Hold ${decaySec}s"
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
                "Display",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val dropdownPad = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ExposedDropdownMenuBox(openView, { openView = it }, dropdownPad) {
                FieldwatchDropdownField("View", viewLabel, openView)
                ExposedDropdownMenu(openView, { openView = false }) {
                    ViewMode.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label()) },
                            onClick = { onChangeView(item); openView = false },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(openSort, { openSort = it }, dropdownPad) {
                FieldwatchDropdownField("Sort", sortLabel, openSort)
                ExposedDropdownMenu(openSort, { openSort = false }) {
                    DropdownMenuItem(
                        text = { Text("Strongest signal") },
                        onClick = { onChangeSort(StrengthSort.INSTANT, null); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Strongest (avg 30s)") },
                        onClick = { onChangeSort(StrengthSort.AVERAGE, 30); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Newest heard") },
                        onClick = { onChangeListSort(ListSort.NEWEST); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Newest alert") },
                        onClick = { onChangeListSort(ListSort.NEWEST_ALERT); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Newest arrival") },
                        onClick = { onChangeListSort(ListSort.FIRST_SEEN); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("New at bottom") },
                        onClick = { onChangeListSort(ListSort.ARRIVAL); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Name A–Z") },
                        onClick = { onChangeListSort(ListSort.NAME); openSort = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Signatures first") },
                        onClick = { onChangeListSort(ListSort.SIGNATURES); openSort = false },
                    )
                }
            }
            ExposedDropdownMenuBox(openDecay, { openDecay = it }, dropdownPad) {
                FieldwatchDropdownField("Brief hold", decayLabel, openDecay)
                ExposedDropdownMenu(openDecay, { openDecay = false }) {
                    DropdownMenuItem(
                        text = { Text("Off — Stale after only") },
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
            ExposedDropdownMenuBox(openTitle, { openTitle = it }, dropdownPad) {
                FieldwatchDropdownField("Title line", listLineLabel(titleLine), openTitle)
                ExposedDropdownMenu(openTitle, { openTitle = false }) {
                    listOf(ListLine.ADVERTISED_NAME, ListLine.NAME_AND_TYPE, ListLine.MAC).forEach { item ->
                        DropdownMenuItem(
                            text = { Text(listLineLabel(item)) },
                            onClick = { onChangeTitleLine(item); openTitle = false },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(openSubtitle, { openSubtitle = it }, dropdownPad) {
                FieldwatchDropdownField("Subtitle line", listLineLabel(subtitleLine), openSubtitle)
                ExposedDropdownMenu(openSubtitle, { openSubtitle = false }) {
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
            OptionSwitch("RSSI bars", showBar, onToggleBar)
            OptionSwitch("Signature names", showFleet, onToggleFleet)
            OptionSwitch("Frequency", showFrequency, onToggleFrequency)
            OptionSwitch("First / last seen", showSeenTimes, onToggleSeenTimes)
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
                    contentDescription = "More display options below",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        }
    }
}

private fun listLineLabel(line: ListLine): String = when (line) {
    ListLine.ADVERTISED_NAME -> "Advertised name"
    ListLine.NAME_AND_TYPE -> "Name + type"
    ListLine.MAC -> "MAC address"
    ListLine.NONE -> "None"
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
                    ) { Text("Mark seen") }
                    FieldwatchActionButton(
                        onClick = onResetSeen,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("Reset seen") }
                }
            }
            if (movingWithYou) {
                FieldwatchActionButton(
                    onClick = onStartOverFollow,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) { Text("Start over") }
            }
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FieldwatchSwitch(checked, { onToggle() })
    }
}

@Suppress("unused")
private val unusedPerms = RadioPermissions
