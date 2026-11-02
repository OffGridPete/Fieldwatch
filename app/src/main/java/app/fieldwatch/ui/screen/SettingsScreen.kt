package app.fieldwatch.ui.screen

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import app.fieldwatch.ui.component.FieldwatchFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSlider
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fieldwatch.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fieldwatch.domain.AlertVoiceWhat
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.MapBasemap
import app.fieldwatch.domain.MapFooter
import app.fieldwatch.domain.MapOverlay
import app.fieldwatch.domain.MapKey
import app.fieldwatch.domain.GnssAlertFloor
import app.fieldwatch.domain.GnssSensitivity
import app.fieldwatch.domain.ScanIntensity
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakInstall
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.TakFloodSend
import app.fieldwatch.domain.TakGnssHold
import app.fieldwatch.domain.TakGnssSend
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.TakUdpPreset
import app.fieldwatch.radio.WifiRadio
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.FlockLayerUi
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.StableCaption
import app.fieldwatch.ui.component.StickyHeight
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onRadioBookmarks: () -> Unit,
    onSpokenNames: () -> Unit,
    onScanStatus: () -> Unit,
    onShowLiveTour: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = state.settings
    val flock by vm.flockLayerUi.collectAsStateWithLifecycle()
    val sensors by vm.sensorLayerUi.collectAsStateWithLifecycle()
    val saveSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSignaturesToUri) }
    val importSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSignaturesFromUri) }
    val saveSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSettingsToUri) }
    val importSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSettingsFromUri) }
    val importLanguage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importLanguageFromUri) }
    val language by vm.language.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf(false) }
    val pendingSettingsImport by vm.pendingSettingsImport.collectAsStateWithLifecycle()
    val gnssProfile by vm.gnssProfile.collectAsStateWithLifecycle()
    val gnssCal by vm.gnssCalibration.collectAsStateWithLifecycle()
    val gnssRecordings by vm.gnssRecordings.collectAsStateWithLifecycle()
    val gnssReady = gnssProfile != null
    var topicName by rememberSaveable { mutableStateOf<String?>(null) }
    var closeCellPlace by remember { mutableStateOf<(() -> Unit)?>(null) }
    var mapMenu by rememberSaveable { mutableStateOf(false) }
    var positionMenu by rememberSaveable { mutableStateOf(false) }
    var confirmClearMaps by rememberSaveable { mutableStateOf(false) }
    val topic = topicName?.let { name -> SettingsTopic.entries.firstOrNull { it.name == name } }
    val leaveTopic = {
        val close = closeCellPlace
        if (close != null) close() else topicName = null
    }
    BackHandler(enabled = topic != null, onBack = leaveTopic)
    val scroll = rememberScrollState()
    LaunchedEffect(topicName) { scroll.scrollTo(0) }
    LaunchedEffect(settings.onlineLookup) {
        if (!settings.onlineLookup) mapMenu = false
    }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = topic?.title() ?: LanguageText.text("tab.settings"),
                navigationIcon = {
                    if (topic != null) {
                        IconButton(onClick = leaveTopic) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, LanguageText.text("chrome.back"))
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (topic == SettingsTopic.CELL) {
            CellWatchPage(
                vm,
                settings,
                state.phoneLat,
                state.phoneLon,
                onPlaceBack = { closeCellPlace = it },
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize(),
            )
        } else Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (topic == null) {
                SettingsTopicList(
                    settings = settings,
                    catalogVersion = state.catalogVersion,
                    takInstallTag = state.takInstallTag,
                    gnssReady = gnssReady,
                    languageName = language.snap.activeName,
                    onOpen = { topicName = it.name },
                )
            }
            if (topic == SettingsTopic.APPEARANCE) {
            SectionCard(LanguageText.text("settings.topic.appearance"), showTitle = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.night.mode"), Modifier.weight(1f))
                FieldwatchSwitch(settings.nightMode, { on -> vm.updateSettings { it.copy(nightMode = on) } })
            }
            Text(
                LanguageText.text("ui.red.black.so.dark"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.keep.screen"), Modifier.weight(1f))
                FieldwatchSwitch(settings.keepScreenOn, { on -> vm.updateSettings { it.copy(keepScreenOn = on) } })
            }
            Text(
                LanguageText.text("ui.screen.stays.awake.while"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.privacy.mode"), Modifier.weight(1f))
                FieldwatchSwitch(settings.demoMode, { on -> vm.updateSettings { it.copy(demoMode = on) } })
            }
            Text(
                LanguageText.text("ui.hides.last.half.each"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            }

            if (topic == SettingsTopic.SCANNING) {
            SectionCard(LanguageText.text("settings.topic.scanning"), showTitle = false) {
            val label = when (settings.intensity) {
                ScanIntensity.SAVER -> LanguageText.text("ui.battery.saver")
                ScanIntensity.BALANCED -> LanguageText.text("ui.balanced.label")
                ScanIntensity.PERFORMANCE -> LanguageText.text("ui.high.performance")
            }
            Text("Scan intensity  ·  $label")
            FieldwatchSlider(
                value = settings.intensity.ordinal.toFloat(),
                onValueChange = { v ->
                    val next = ScanIntensity.entries[v.toInt().coerceIn(0, 2)]
                    vm.updateSettings { it.copy(intensity = next) }
                },
                valueRange = 0f..2f,
                steps = 1,
            )
            Text(
                LanguageText.text("ui.wi.fi.reads.every"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StableCaption(
                state.throttleHint.ifBlank { " " },
                LanguageText.text("ui.wi.fi.waiting.os"),
                LanguageText.text("ui.wi.fi.scanning"),
                LanguageText.text("ui.wi.fi.next.99s"),
                " ",
            )

            val lifecycleOwner = LocalLifecycleOwner.current
            var osThrottled by remember { mutableStateOf(WifiRadio.osScanThrottled(context)) }
            var backgroundAllowed by remember { mutableStateOf(isBackgroundUsageAllowed(context)) }
            var unrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
            var needDevOptions by remember { mutableStateOf(false) }
            var batteryGate by remember { mutableStateOf<BatteryAndroidGate?>(null) }
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        osThrottled = WifiRadio.osScanThrottled(context)
                        backgroundAllowed = isBackgroundUsageAllowed(context)
                        unrestricted = isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.faster.wi.fi.ap"), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = !osThrottled,
                    onCheckedChange = { needDevOptions = true },
                )
            }
            StableCaption(
                when {
                    Build.VERSION.SDK_INT < 30 ->
                        LanguageText.text("ui.phone.older.than.android")
                    !osThrottled ->
                        LanguageText.text("ui.new.access.point.list")
                    else ->
                        LanguageText.text("ui.off.android.limiting.wi")
                },
                LanguageText.text("ui.phone.older.than.android"),
                LanguageText.text("ui.new.access.point.list"),
                LanguageText.text("ui.off.android.limiting.wi"),
            )
            if (needDevOptions) {
                AlertDialog(
                    onDismissRequest = { needDevOptions = false },
                    title = { Text(LanguageText.text("ui.faster.wi.fi.ap")) },
                    text = {
                        Text(
                            if (Build.VERSION.SDK_INT < 30) {
                                LanguageText.text("ui.phone.older.than.android.2")
                            } else if (osThrottled) {
                                LanguageText.text("ui.opens.developer.options.turn")
                            } else {
                                LanguageText.text("ui.opens.developer.options.turn.2")
                            },
                        )
                    },
                    confirmButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(
                                onClick = {
                                    needDevOptions = false
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                    }
                                },
                            ) { Text(LanguageText.text("ui.open.developer.options")) }
                        } else {
                            TextButton(onClick = { needDevOptions = false }) { Text(LanguageText.text("ui.ok.label")) }
                        }
                    },
                    dismissButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(onClick = { needDevOptions = false }) { Text(LanguageText.text("ui.not.now")) }
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.allow.background.usage"), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = backgroundAllowed,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.BACKGROUND },
                )
            }
            Text(
                LanguageText.text("ui.opens.app.s.battery"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.unrestricted.battery"), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = unrestricted,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.UNRESTRICTED },
                )
            }
            Text(
                LanguageText.text("ui.opens.same.battery.page"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (batteryGate != null) {
                val background = batteryGate == BatteryAndroidGate.BACKGROUND
                AlertDialog(
                    onDismissRequest = { batteryGate = null },
                    title = {
                        Text(if (background) LanguageText.text("ui.allow.background.usage") else LanguageText.text("ui.unrestricted.battery"))
                    },
                    text = {
                        Text(
                            if (background) {
                                LanguageText.text("ui.opens.fieldwatch.s.battery")
                            } else {
                                LanguageText.text("ui.choose.unrestricted.some.phones")
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val gate = batteryGate
                                batteryGate = null
                                openAppBatteryPage(
                                    context,
                                    highlightBackground = gate == BatteryAndroidGate.BACKGROUND,
                                )
                            },
                        ) { Text(LanguageText.text("ui.open.android.settings")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { batteryGate = null }) { Text(LanguageText.text("ui.not.now")) }
                    },
                )
            }
            }
            }

            if (topic == SettingsTopic.WATCHLIST) {
            SectionCard(LanguageText.text("settings.topic.alerts"), showTitle = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.watchlist.alerts"), Modifier.weight(1f))
                FieldwatchSwitch(settings.alertsEnabled, { on -> vm.updateSettings { it.copy(alertsEnabled = on) } })
            }
            Text(
                LanguageText.text("ui.beep.voice.flash.jump"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.beep.watched.signature"), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertBeep,
                    { on -> vm.updateSettings { it.copy(alertBeep = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                LanguageText.text("ui.double.pip.media.volume"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.voice.watched.signature"), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertVoice,
                    { on -> vm.updateSettings { it.copy(alertVoice = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                LanguageText.text("ui.says.class.signature.same"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(LanguageText.text("ui.what.say"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertVoiceWhat.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.alertVoiceWhat == item,
                        onClick = { vm.updateSettings { it.copy(alertVoiceWhat = item) } },
                        enabled = settings.alertsEnabled && settings.alertVoice,
                        label = { Text(item.label()) },
                    )
                }
            }
            Text(
                LanguageText.text("ui.class.group.live.such"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            SettingsTopicRow(
                LanguageText.text("settings.named.radios"),
                namedRadiosValue(state.watchlist.count { it.deviceKey != null }),
                onRadioBookmarks,
            )
            Text(
                LanguageText.text("alerts.named.note"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            SettingsTopicRow(
                LanguageText.text("settings.spoken.names"),
                spokenNamesValue(settings),
                onSpokenNames,
            )
            Text(
                LanguageText.text("alerts.signature.note"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            SectionCard(LanguageText.text("settings.topic.alerts"), showTitle = false) {
            FieldwatchActionButton(
                onClick = vm::testWatchBeep,
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
            ) { Text(LanguageText.text("ui.test.alert")) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.jump.new.watched.detection"), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.snapToBeep,
                    { on -> vm.updateSettings { it.copy(snapToBeep = on) } },
                    enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
                )
            }
            Text(
                LanguageText.text("ui.live.scrolls.new.watched"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.system.notification"), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertShade,
                    { on -> vm.updateSettings { it.copy(alertShade = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                LanguageText.text("ui.silent.notification.when.watched"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            }

            if (topic == SettingsTopic.LOCATION) {
            SectionCard(LanguageText.text("settings.topic.location")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.tag.detections.gps"), Modifier.weight(1f))
                FieldwatchSwitch(settings.tagLocation, { on -> vm.updateSettings { it.copy(tagLocation = on) } })
            }
            Note(
                LanguageText.text(
                    "ui.stamps.phone.s.position",
                    mapOf(
                        "live" to LanguageText.text("tab.live"),
                        "moving" to LanguageText.text("ui.moving.label"),
                        "places" to LanguageText.text("ui.places.label"),
                        "debrief" to LanguageText.text("reports.debrief"),
                    ),
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.places.label"), Modifier.weight(1f))
                FieldwatchSwitch(settings.placeWatch, { on -> vm.updateSettings { it.copy(placeWatch = on) } })
            }
            Note(
                LanguageText.text(
                    "ui.finder.tag.keeps.its",
                    mapOf("gps" to LanguageText.text("ui.tag.detections.gps")),
                ),
            )
            }
            SectionCard(LanguageText.text("settings.topic.maps")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.online.place.names.maps"), Modifier.weight(1f))
                FieldwatchSwitch(settings.onlineLookup, { on -> vm.updateSettings { it.copy(onlineLookup = on) } })
            }
            Note(LanguageText.text("ui.internet.debrief.places.turn"))
            val mapsOn = settings.onlineLookup
            val open = mapMenu && mapsOn
            ExposedDropdownMenuBox(
                expanded = open,
                onExpandedChange = { want ->
                    if (mapsOn) {
                        mapMenu = want
                        if (want) positionMenu = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                FieldwatchDropdownField(
                    label = LanguageText.text("settings.map.choice"),
                    value = LanguageText.text(settings.mapBasemap.labelKey),
                    expanded = open,
                    enabled = mapsOn,
                )
                ExposedDropdownMenu(open, { mapMenu = false }) {
                    MapBasemap.entries.filter { it.inPicker }.forEach { choice ->
                        val selected = choice == settings.mapBasemap
                        val subtitle = choice.subtitleKeys
                            .joinToString(" · ") { LanguageText.text(it) }
                            .ifBlank { null }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 32.dp)
                                .clickable {
                                    mapMenu = false
                                    if (choice != settings.mapBasemap) vm.selectMap(choice)
                                }
                                .padding(horizontal = 12.dp, vertical = if (subtitle == null) 2.dp else 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    LanguageText.text(choice.labelKey),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                if (subtitle != null) {
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (choice.needsKey) {
                                Text(
                                    LanguageText.text("map.key.mark"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            if (selected) {
                                Icon(Icons.Outlined.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
            if (mapsOn && settings.mapBasemap.needsKey) {
                val service = settings.mapBasemap.service
                val stored = vm.mapKey(settings.mapBasemap)
                var draft by remember(service) { mutableStateOf(stored) }
                var skipFirstBlur by remember(service) { mutableStateOf(true) }
                FieldwatchOutlinedField(
                    value = draft,
                    onValueChange = { next ->
                        if (MapKey.verdict(next) == MapKey.Verdict.DENIED) {
                            vm.finishMapKey(settings.mapBasemap, next)
                        } else {
                            draft = next
                        }
                    },
                    label = LanguageText.text("map.key.label"),
                    supportingText = LanguageText.text("map.key.hint"),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { vm.finishMapKey(settings.mapBasemap, draft) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (focus.isFocused) {
                                skipFirstBlur = false
                            } else if (skipFirstBlur) {
                                skipFirstBlur = false
                            } else {
                                vm.finishMapKey(settings.mapBasemap, draft)
                            }
                        },
                )
            }
            Text(
                LanguageText.text("settings.map.about"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!mapsOn) {
                Text(
                    LanguageText.text("settings.map.needs.online"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                LanguageText.text("settings.map.overlays"),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            MapOverlay.entries.forEach { overlay ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(
                            LanguageText.text(overlay.basemap.labelKey),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            LanguageText.text("map.united.states"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FieldwatchSwitch(
                        overlay in settings.mapOverlays,
                        { on ->
                            vm.updateSettings { current ->
                                val next = if (on) current.mapOverlays + overlay else current.mapOverlays - overlay
                                current.copy(mapOverlays = next)
                            }
                        },
                        enabled = mapsOn,
                    )
                }
            }
            FieldwatchActionButton(onClick = { confirmClearMaps = true }) {
                Text(LanguageText.text("settings.map.clear"))
            }
            Note(LanguageText.text("settings.map.clear.body"))
            }
            SectionCard(LanguageText.text("settings.layer.heading")) {
            SensorLayerBlock(
                title = LanguageText.text("settings.layer.flock"),
                note = LanguageText.text("settings.layer.note"),
                state = flock,
                checked = settings.flockLayer,
                onChecked = vm::setFlockLayer,
                onUpdate = vm::updateFlockLayer,
                more = if (!flock.fullList && flock.savedAt > 0L && !flock.busy) {
                    LanguageText.text("settings.layer.more")
                } else {
                    null
                },
            ) {
                SensorAlertRow(
                    LanguageText.text("settings.layer.alert.plate"),
                    settings.flockAlert,
                    !flock.busy,
                ) { on -> vm.updateSettings { it.copy(flockAlert = on) } }
            }
            Spacer(Modifier.height(8.dp))
            SensorLayerBlock(
                title = LanguageText.text("settings.layer.other"),
                note = LanguageText.text("settings.layer.other.note"),
                state = sensors,
                checked = settings.sensorLayer,
                onChecked = vm::setSensorLayer,
                onUpdate = vm::updateSensorLayer,
            ) {
                SensorAlertRow(
                    LanguageText.text("settings.layer.alert.speed"),
                    settings.speedAlert,
                    !sensors.busy,
                ) { on -> vm.updateSettings { it.copy(speedAlert = on) } }
                SensorAlertRow(
                    LanguageText.text("settings.layer.alert.red"),
                    settings.redAlert,
                    !sensors.busy,
                ) { on -> vm.updateSettings { it.copy(redAlert = on) } }
                SensorAlertRow(
                    LanguageText.text("settings.layer.alert.gun"),
                    settings.gunshotAlert,
                    !sensors.busy,
                ) { on -> vm.updateSettings { it.copy(gunshotAlert = on) } }
                SensorAlertRow(
                    LanguageText.text("settings.layer.alert.camera"),
                    settings.cameraAlert,
                    !sensors.busy,
                ) { on -> vm.updateSettings { it.copy(cameraAlert = on) } }
            }
            }
            SectionCard(LanguageText.text("settings.map.display")) {
            ExposedDropdownMenuBox(
                expanded = positionMenu,
                onExpandedChange = { want ->
                    positionMenu = want
                    if (want) mapMenu = false
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                FieldwatchDropdownField(
                    label = LanguageText.text("settings.map.position"),
                    value = LanguageText.text(settings.mapFooter.labelKey),
                    expanded = positionMenu,
                )
                ExposedDropdownMenu(positionMenu, { positionMenu = false }) {
                    MapFooter.entries.forEach { choice ->
                        val selected = choice == settings.mapFooter
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 32.dp)
                                .clickable {
                                    positionMenu = false
                                    if (choice != settings.mapFooter) {
                                        vm.updateSettings { it.copy(mapFooter = choice) }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                LanguageText.text(choice.labelKey),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(Icons.Outlined.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
            Note(
                LanguageText.text(
                    "settings.map.position.about",
                    mapOf(
                        "gps" to LanguageText.text("map.position.gps"),
                        "grid" to LanguageText.text("ui.grid"),
                        "off" to LanguageText.text("ui.off.label"),
                    ),
                ),
            )
            }
            }

            if (topic == SettingsTopic.GNSS) {
            SectionCard(LanguageText.text("settings.topic.gnss"), showTitle = false) {
            Note(LanguageText.text("ui.detects.interference.spoofing.gnss"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.gnss.interference.check"), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = settings.gnssMonitor && gnssReady,
                    onCheckedChange = { on ->
                        if (gnssReady) vm.updateSettings { it.copy(gnssMonitor = on) }
                    },
                    enabled = gnssReady && !gnssCal.running,
                )
            }
            Note(LanguageText.text("ui.hit.opens.popup.after"))
            if (!gnssReady) {
                Text(
                    LanguageText.text("ui.calibrate.phone.before.turning"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            }
            SectionCard(LanguageText.text("gnss.section.calibration")) {
            FieldwatchActionButton(
                onClick = { if (gnssCal.running) vm.stopGnssCalibration() else vm.startGnssCalibration() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (gnssCal.running) LanguageText.text("ui.stop.calibration") else LanguageText.text("ui.calibrate.phone")) }
            Note(
                LanguageText.text(
                    "ui.stand.outside.hold.still",
                    mapOf("export" to LanguageText.text("ui.export.settings")),
                ),
            )
            if (gnssCal.running) {
                Text(
                    LanguageText.text("gnss.calibrating", mapOf("n" to gnssCal.leftSec.toString())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (gnssCal.rejection.isNotBlank()) {
                Text(
                    gnssCal.rejection,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            gnssProfile?.let { profile ->
                Text(
                    profile.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Note(LanguageText.text("gnss.calibrated", mapOf("when" to profile.calibratedLabel())))
            }
            }
            SectionCard(LanguageText.text("trusted.warnings")) {
            Text(LanguageText.text("ui.sensitivity.label"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GnssSensitivity.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.gnssSensitivity == item,
                        onClick = { vm.updateSettings { it.copy(gnssSensitivity = item) } },
                        label = { Text(item.label()) },
                    )
                }
            }
            Note(LanguageText.text("ui.low.waits.deeper.drop"))
            Text(LanguageText.text("ui.alert.label.2"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GnssAlertFloor.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.gnssAlertFloor == item,
                        onClick = { vm.updateSettings { it.copy(gnssAlertFloor = item) } },
                        label = { Text(item.label()) },
                    )
                }
            }
            Note(
                LanguageText.text(
                    "ui.popup.red.line.beep",
                    mapOf("diag" to LanguageText.text("ui.diagnostics.label")),
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.spoofing.checks"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssSpoofChecks, { on -> vm.updateSettings { it.copy(gnssSpoofChecks = on) } })
            }
            Note(LanguageText.text("ui.warns.when.gain.falls"))
            }
            SectionCard(LanguageText.text("trusted.reach")) {
            Note(
                LanguageText.text(
                    "gnss.reach.note",
                    mapOf(
                        "beep" to LanguageText.text("ui.beep.label"),
                        "voice" to LanguageText.text("ui.voice.label"),
                        "watchlist" to LanguageText.text("settings.topic.alerts"),
                    ),
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.beep.label"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssBeep, { on -> vm.updateSettings { it.copy(gnssBeep = on) } })
            }
            Note(LanguageText.text("ui.same.pip.watchlist.hit"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.voice.label"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssVoice, { on -> vm.updateSettings { it.copy(gnssVoice = on) } })
            }
            Note(LanguageText.text("ui.says.possible.gps.interference"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.system.notification"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssShade, { on -> vm.updateSettings { it.copy(gnssShade = on) } })
            }
            Note(LanguageText.text("ui.silent.notification.warning.popup"))
            }
            SectionCard(LanguageText.text("gnss.section.recording")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.record.raw.gnss.during"), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = settings.gnssRawLog,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(gnssRawLog = on) } },
                    enabled = gnssReady && settings.gnssMonitor && !settings.demoMode && !gnssCal.running,
                )
            }
            Note(
                LanguageText.text(
                    "ui.saves.phone.s.raw",
                    mapOf("privacy" to LanguageText.text("ui.privacy.mode")),
                ),
            )
            FieldwatchActionButton(
                onClick = { gnssRecordings.firstOrNull()?.let { vm.shareGnssRecording(it.name) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = gnssRecordings.isNotEmpty(),
            ) { Text(LanguageText.text("ui.share.last.recording")) }
            if (gnssRecordings.size > 1) {
                gnssRecordings.drop(1).forEach { recording ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            recording.label(),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { vm.shareGnssRecording(recording.name) }) { Text(LanguageText.text("ui.share.label")) }
                    }
                }
            }
            }
            SectionCard(LanguageText.text("gnss.section.receiver")) {
            Note(LanguageText.text("gnss.receiver.note"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.full.tracking"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssFullTracking, { on -> vm.updateSettings { it.copy(gnssFullTracking = on) } })
            }
            Note(LanguageText.text("ui.keeps.gps.receiver.awake"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.fading.together.check"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssCorrelation, { on -> vm.updateSettings { it.copy(gnssCorrelation = on) } })
            }
            Note(LanguageText.text("ui.watches.satellites.fading.same"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.invert.gain"), Modifier.weight(1f))
                FieldwatchSwitch(settings.gnssInvertAgc, { on -> vm.updateSettings { it.copy(gnssInvertAgc = on) } })
            }
            Note(
                LanguageText.text(
                    "ui.few.phones.report.gain",
                    mapOf("diag" to LanguageText.text("ui.diagnostics.label")),
                ),
            )
            }
            }

            if (topic == SettingsTopic.TRUSTED) {
                TrustedNetworksPage(vm, settings)
            }

            if (topic == SettingsTopic.TAK) {
            SectionCard(LanguageText.text("settings.topic.tak"), showTitle = false) {
            Note(
                LanguageText.text(
                    "ui.radios.hear.pinned.phone",
                    mapOf("privacy" to LanguageText.text("ui.privacy.mode")),
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.tak.cot.feed"), Modifier.weight(1f))
                FieldwatchSwitch(settings.takEnabled, { on -> vm.updateSettings { it.copy(takEnabled = on) } })
            }
            if (settings.takEnabled && settings.demoMode) {
                Text(
                    LanguageText.text("ui.privacy.mode.so.feed"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            }
            if (settings.takEnabled) {
                TakFeedSettings(settings, vm, state.takStatus, state.takInstallTag)
            }
            }

            if (topic == SettingsTopic.LOGGING) {
            SectionCard(LanguageText.text("settings.topic.logging"), showTitle = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LanguageText.text("ui.write.detections.disk"), Modifier.weight(1f))
                FieldwatchSwitch(settings.loggingEnabled, { on -> vm.updateSettings { it.copy(loggingEnabled = on) } })
            }
            StableCaption(
                if (settings.loggingEnabled) {
                    LanguageText.text("ui.logging.new.detections.added")
                } else {
                    LanguageText.text("ui.logging.off.scanning.still")
                },
                LanguageText.text("ui.logging.new.detections.added"),
                LanguageText.text("ui.logging.off.scanning.still"),
            )
            Text(
                LanguageText.text("ui.file.disk.one.detection"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var rotateDrag by remember { mutableIntStateOf(settings.logRotateKb) }
            var rotateDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.logRotateKb) {
                if (!rotateDragging) rotateDrag = settings.logRotateKb
            }
            Text("Rotate at $rotateDrag KB")
            FieldwatchSlider(
                value = rotateDrag.toFloat(),
                onValueChange = {
                    rotateDragging = true
                    rotateDrag = it.toInt().coerceIn(128, 4096)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(logRotateKb = rotateDrag) }
                    rotateDragging = false
                },
                valueRange = 128f..4096f,
            )
            Text(
                LanguageText.text("ui.when.log.file.reaches"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var staleDrag by remember { mutableIntStateOf(settings.staleSec) }
            var staleDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.staleSec) {
                if (!staleDragging) staleDrag = settings.staleSec
            }
            Text(LanguageText.text("settings.stale", mapOf("n" to staleDrag.toString())))
            FieldwatchSlider(
                value = staleDrag.toFloat(),
                onValueChange = {
                    staleDragging = true
                    staleDrag = it.toInt().coerceIn(15, 180)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(staleSec = staleDrag) }
                    staleDragging = false
                },
                valueRange = 15f..180f,
            )
            Text(
                LanguageText.text("ui.radio.marked.gone.live"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StickyHeight("log-stats") {
                Text(
                    LanguageText.text(
                        "reports.log",
                        mapOf(
                            "n" to state.logLines.toString(),
                            "kb" to (vm.logBytes() / 1024).toString(),
                        ),
                    ) + ". " + LanguageText.text("ui.share.save.reset.clear"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            }
            }

            if (topic == SettingsTopic.SIGNATURES) {
            SectionCard(LanguageText.text("settings.topic.signatures"), showTitle = false) {
            Text(
                LanguageText.text("ui.export.shares.catalog.including"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSignatureShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.export.signatures")) }
            FieldwatchActionButton(
                onClick = { saveSignatures.launch(vm.suggestedSignaturesName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.save.signatures.sd.card")) }
            FieldwatchActionButton(
                onClick = {
                    importSignatures.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.import.signatures")) }
            FieldwatchActionButton(
                onClick = vm::updateStockCatalogFromGitHub,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.update.stock.catalog.github")) }

            FieldwatchActionButton(
                onClick = { confirmRestore = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(LanguageText.text("ui.restore.default.signatures.presets"))
            }
            }
            }

            if (topic == SettingsTopic.LANGUAGE) {
                LanguagePage(
                    state = language,
                    onChoose = vm::chooseLanguage,
                    onImport = { importLanguage.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onExport = vm::startLanguageTemplateShare,
                    onRemove = vm::removeLanguagePack,
                )
            }
            if (topic == SettingsTopic.BACKUP) {
            SectionCard(LanguageText.text("settings.topic.backup"), showTitle = false) {
            Text(
                (LanguageText.text("ui.saves.switches.filters.presets") + " ") +
                    (LanguageText.text("ui.catalog.signatures.import.replaces") + " ") +
                    LanguageText.text("ui.fieldwatch.asks.before.file"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSettingsShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.export.settings")) }
            FieldwatchActionButton(
                onClick = { saveSettings.launch(vm.suggestedSettingsName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.save.settings.sd.card")) }
            FieldwatchActionButton(
                onClick = {
                    importSettings.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.import.settings")) }
            }
            }

            if (topic == null) {
            FieldwatchActionButton(
                onClick = onShowLiveTour,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.show.live.tour")) }
            Text(
                LanguageText.text("ui.shows.live.tour.again"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = onScanStatus,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.diagnostics.label")) }
            Text(
                LanguageText.text("ui.shows.whether.scanning.running"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Fieldwatch ${app.fieldwatch.BuildConfig.VERSION_NAME}  ·  Catalog ${state.catalogVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val footerLifecycle = LocalLifecycleOwner.current
            var ipv4 by remember { mutableStateOf(localIpv4Addresses()) }
            DisposableEffect(footerLifecycle) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) ipv4 = localIpv4Addresses()
                }
                footerLifecycle.lifecycle.addObserver(obs)
                onDispose { footerLifecycle.lifecycle.removeObserver(obs) }
            }
            Text(
                if (ipv4.isEmpty()) {
                    LanguageText.text("ui.phone.s.ipv4.none")
                } else {
                    "This phone’s IPv4  ·  ${ipv4.joinToString("  ·  ")}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            CreditFooter()
            }
        }
    }
    pendingSettingsImport?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::dismissPendingSettingsImport,
            title = { Text(LanguageText.text("ui.import.settings.2")) },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(onClick = vm::confirmPendingSettingsImport) { Text(LanguageText.text("language.import.action")) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissPendingSettingsImport) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
    if (confirmClearMaps) {
        AlertDialog(
            onDismissRequest = { confirmClearMaps = false },
            title = { Text(LanguageText.text("settings.map.clear")) },
            text = { Text(LanguageText.text("settings.map.clear.body")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearMaps = false
                        vm.clearSavedMaps()
                    },
                ) { Text(LanguageText.text("settings.map.clear")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearMaps = false }) {
                    Text(LanguageText.text("language.cancel"))
                }
            },
        )
    }
    if (flock.confirm) {
        AlertDialog(
            onDismissRequest = vm::dismissFlockDownload,
            title = { Text(LanguageText.text("settings.layer.download.title")) },
            text = { Text(LanguageText.text("settings.layer.download.body")) },
            confirmButton = {
                TextButton(onClick = vm::confirmFlockDownload) {
                    Text(LanguageText.text("settings.layer.download.action"))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissFlockDownload) {
                    Text(LanguageText.text("language.cancel"))
                }
            },
        )
    }
    if (sensors.confirm) {
        AlertDialog(
            onDismissRequest = vm::dismissSensorDownload,
            title = { Text(LanguageText.text("settings.layer.other.download.title")) },
            text = { Text(LanguageText.text("settings.layer.other.download.body")) },
            confirmButton = {
                TextButton(onClick = vm::confirmSensorDownload) {
                    Text(LanguageText.text("settings.layer.download.action"))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissSensorDownload) {
                    Text(LanguageText.text("language.cancel"))
                }
            },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(LanguageText.text("ui.restore.defaults")) },
            text = {
                Text(
                    (LanguageText.text("ui.replaces.stock.catalog.colors") + " ") +
                        (LanguageText.text("ui.signatures.filters.added.deleted") + " ") +
                        LanguageText.text("ui.cannot.undone"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        vm.restoreDefaults()
                    },
                ) { Text(LanguageText.text("ui.restore.label")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(LanguageText.text("language.cancel")) }
            },
        )
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

@Composable
private fun SensorLayerBlock(
    title: String,
    note: String,
    state: FlockLayerUi,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    more: String? = null,
    alerts: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val status = when {
                state.busy && state.processing -> LanguageText.text("settings.layer.processing")
                state.busy -> LanguageText.text(
                    "settings.layer.downloading",
                    mapOf("n" to state.percent.coerceIn(0, 99).toString()),
                )
                state.savedAt > 0L -> {
                    val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                        .format(java.util.Date(state.savedAt))
                    LanguageText.text("settings.layer.saved", mapOf("date" to date))
                }
                else -> null
            }
            if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (more != null) {
                Text(
                    more,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val notice = state.noticeKey
            if (notice != null) {
                val line = if (state.noticeCode.isEmpty()) {
                    LanguageText.text(notice)
                } else {
                    LanguageText.text(notice, mapOf("code" to state.noticeCode))
                }
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.noticeKeep) {
                    Text(
                        LanguageText.text("settings.layer.failed.unchanged"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FieldwatchSwitch(checked, onChecked, enabled = !state.busy)
    }
    if (checked && state.savedAt > 0L) {
        FieldwatchActionButton(onClick = onUpdate, enabled = !state.busy) {
            Text(LanguageText.text("settings.layer.update"))
        }
    }
    if (checked) {
        Text(
            LanguageText.text("settings.layer.alerts"),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        alerts()
    }
}

@Composable
private fun SensorAlertRow(
    label: String,
    on: Boolean,
    enabled: Boolean,
    set: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
        FieldwatchSwitch(on, set, enabled = enabled)
    }
}

private enum class SettingsTopic(val titleKey: String) {
    APPEARANCE("settings.topic.appearance"),
    SCANNING("settings.topic.scanning"),
    WATCHLIST("settings.topic.alerts"),
    LOCATION("settings.topic.location.maps"),
    GNSS("settings.topic.gnss"),
    CELL("settings.topic.cell"),
    TRUSTED("settings.topic.trusted"),
    TAK("settings.topic.tak"),
    LOGGING("settings.topic.logging"),
    SIGNATURES("settings.topic.signatures"),
    LANGUAGE("settings.topic.language"),
    BACKUP("settings.topic.backup"),
    ;

    fun title(): String = LanguageText.text(titleKey)
}

@Composable
private fun SettingsTopicList(
    settings: AppSettings,
    catalogVersion: Int,
    takInstallTag: String,
    gnssReady: Boolean,
    languageName: String,
    onOpen: (SettingsTopic) -> Unit,
) {
    val scanLabel = when (settings.intensity) {
        ScanIntensity.SAVER -> LanguageText.text("ui.battery.saver")
        ScanIntensity.BALANCED -> LanguageText.text("ui.balanced.label")
        ScanIntensity.PERFORMANCE -> LanguageText.text("ui.high.performance")
    }
    fun onOff(on: Boolean) = if (on) "on" else "off"
    val rows: List<Pair<SettingsTopic, String>> = listOf(
        SettingsTopic.APPEARANCE to "Night ${onOff(settings.nightMode)} · Privacy ${onOff(settings.demoMode)}",
        SettingsTopic.LANGUAGE to languageName,
        SettingsTopic.SCANNING to scanLabel,
        SettingsTopic.WATCHLIST to "Alerts ${onOff(settings.alertsEnabled)}",
        SettingsTopic.LOCATION to "GPS ${onOff(settings.tagLocation)} · Places ${onOff(settings.placeWatch)}",
        SettingsTopic.GNSS to when {
            !gnssReady -> LanguageText.text("ui.needs.calibration")
            settings.gnssMonitor -> LanguageText.text("ui.on.label")
            else -> LanguageText.text("ui.off.label")
        },
        SettingsTopic.CELL to if (settings.cellWatch) {
            LanguageText.text("ui.on.label")
        } else {
            LanguageText.text("ui.off.label")
        },
        SettingsTopic.TRUSTED to if (settings.trustedWatch) {
            LanguageText.text("ui.on.label")
        } else {
            LanguageText.text("ui.off.label")
        },
        SettingsTopic.TAK to when {
            settings.takEnabled && settings.demoMode -> LanguageText.text("ui.paused.label")
            settings.takEnabled -> "On · ${TakInstall.callsign(settings.takCallsign, takInstallTag)}"
            else -> LanguageText.text("ui.off.label")
        },
        SettingsTopic.LOGGING to if (settings.loggingEnabled) LanguageText.text("ui.on.label") else LanguageText.text("ui.off.label"),
        SettingsTopic.SIGNATURES to "Catalog $catalogVersion",
        SettingsTopic.BACKUP to LanguageText.text("ui.export.import"),
    )
    rows.forEach { (item, value) ->
        SettingsTopicRow(item.title(), value) { onOpen(item) }
    }
}

@Composable
private fun namedRadiosValue(count: Int): String = when (count) {
    0 -> LanguageText.text("display.none")
    1 -> LanguageText.text("ui.1.radio")
    else -> "$count radios"
}

@Composable
private fun spokenNamesValue(settings: AppSettings): String {
    val count = settings.spokenNames.size
    return buildString {
        append(
            when (count) {
                0 -> LanguageText.text("display.none")
                1 -> LanguageText.text("spoken.one")
                else -> LanguageText.text("spoken.count", mapOf("n" to count.toString()))
            },
        )
        if (count > 0 && settings.alertVoiceWhat == AlertVoiceWhat.CLASS) {
            append(" · ")
            append(LanguageText.text("spoken.held"))
        }
    }
}

@Composable
private fun SettingsTopicRow(title: String, value: String, onClick: () -> Unit) {
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
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun CreditFooter() {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            LanguageText.text("ui.copyright.c.2026.off"),
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialChip(
                icon = R.drawable.ic_instagram,
                label = LanguageText.text("ui.offgridpete.label"),
                tint = muted,
                onClick = { openUrl(context, "https://instagram.com/OffGridPete") },
            )
            SocialChip(
                icon = R.drawable.ic_x,
                label = LanguageText.text("ui.ogridpete.label"),
                tint = muted,
                onClick = { openUrl(context, "https://x.com/OGridPete") },
            )
        }
    }
}

@Composable
private fun SocialChip(
    icon: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TakFeedSettings(
    settings: AppSettings,
    vm: FieldwatchViewModel,
    status: TakFeedStatus,
    installTag: String,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var hostText by remember { mutableStateOf(settings.takHost) }
    var portText by remember { mutableStateOf(settings.takPort.toString()) }
    LaunchedEffect(settings.takHost) { hostText = settings.takHost }
    LaunchedEffect(settings.takPort) { portText = settings.takPort.toString() }
    val preset = TakPublish.udpPreset(settings.takHost, settings.takPort)
    val phoneName = TakInstall.callsign(settings.takCallsign, installTag)
    var callsignText by remember { mutableStateOf(settings.takCallsign) }
    LaunchedEffect(settings.takCallsign) { callsignText = settings.takCallsign }
    SectionCard(LanguageText.text("ui.phone.label")) {
    FieldwatchOutlinedField(
        value = callsignText,
        onValueChange = { value ->
            val next = TakInstall.storedCallsign(value)
            callsignText = next
            vm.updateSettings { it.copy(takCallsign = next) }
        },
        label = LanguageText.text("ui.tak.callsign"),
        placeholder = phoneName,
        supportingText = LanguageText.text("ui.tak.callsign.note", mapOf("name" to phoneName)),
        enabled = !settings.demoMode,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(LanguageText.text("ui.tak.tag") + "  " + installTag, Modifier.weight(1f))
        TextButton(
            onClick = vm::regenerateTakTag,
            enabled = !settings.demoMode && installTag.isNotEmpty(),
        ) { Text(LanguageText.text("ui.new.tag")) }
    }
    Note(LanguageText.text("ui.each.phone.has.its"))
    }
    SectionCard(LanguageText.text("ui.destination.label")) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.THIS_PHONE,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.THIS_PHONE)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("ui.phone.label")) },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.LAN_MULTICAST,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.LAN_MULTICAST)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("ui.lan.multicast")) },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.CUSTOM,
            onClick = {
                if (preset != TakUdpPreset.CUSTOM) {
                    val (host, port) = TakPublish.applyPreset(TakUdpPreset.CUSTOM)
                    vm.updateSettings { it.copy(takHost = host, takPort = port) }
                }
            },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("ui.custom.label")) },
        )
    }
    Note(
        LanguageText.text(
            "ui.custom.one.address.udp",
            mapOf(
                "loop" to TakDefaults.LOOPBACK,
                "port" to TakDefaults.PORT.toString(),
                "multi" to TakDefaults.SA_HOST,
                "mport" to TakDefaults.SA_PORT.toString(),
            ),
        ),
    )
    FieldwatchOutlinedField(
        value = hostText,
        onValueChange = { value ->
            hostText = value
            val trimmed = value.trim()
            if (trimmed.isNotEmpty()) {
                vm.updateSettings { it.copy(takHost = trimmed) }
            }
        },
        label = LanguageText.text("ui.host.label"),
        placeholder = TakDefaults.HOST,
        enabled = !settings.demoMode,
    )
    FieldwatchOutlinedField(
        value = portText,
        onValueChange = { value ->
            val filtered = value.filter { it.isDigit() }.take(5)
            portText = filtered
            filtered.toIntOrNull()?.let { n ->
                if (n in 1..65_535) {
                    vm.updateSettings { it.copy(takPort = n) }
                }
            }
        },
        label = LanguageText.text("ui.port.label"),
        placeholder = TakDefaults.PORT.toString(),
        supportingText = LanguageText.text(
            "ui.tak.port.note",
            mapOf(
                "port" to TakDefaults.PORT.toString(),
                "mport" to TakDefaults.SA_PORT.toString(),
            ),
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !settings.demoMode,
    )
    Text(takStatusLine(status), style = MaterialTheme.typography.bodySmall, color = muted)
    }
    SectionCard(LanguageText.text("ui.what.send")) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = settings.takAttention,
            onClick = { vm.updateSettings { it.copy(takAttention = !it.takAttention) } },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("chrome.extra.attention")) },
        )
        FieldwatchFilterChip(
            selected = settings.takPayloadFix,
            onClick = { vm.updateSettings { it.copy(takPayloadFix = !it.takPayloadFix) } },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("ui.payload.location")) },
        )
        FieldwatchFilterChip(
            selected = settings.takWatchlist,
            onClick = { vm.updateSettings { it.copy(takWatchlist = !it.takWatchlist) } },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("settings.topic.watchlist")) },
        )
        FieldwatchFilterChip(
            selected = settings.takAllSignatures,
            onClick = { vm.updateSettings { it.copy(takAllSignatures = !it.takAllSignatures) } },
            enabled = !settings.demoMode,
            label = { Text(LanguageText.text("ui.all.signatures")) },
        )
    }
    Note(
        LanguageText.text(
            "ui.turn.any.combination.extra",
            mapOf("gps" to LanguageText.text("ui.tag.detections.gps")),
        ),
    )
    Note(LanguageText.text("ui.remote.id.aircraft.use"))
    }
    SectionCard(LanguageText.text("ui.how.long.spot.stays"), showTitle = false) {
    Text(LanguageText.text("ui.how.long.spot.stays"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakGnssHold.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takRadioHold == item,
                onClick = { vm.updateSettings { it.copy(takRadioHold = item) } },
                enabled = !settings.demoMode,
                label = { Text(item.label()) },
            )
        }
    }
    Note(LanguageText.text("ui.after.radio.leaves.spot"))
    }
    SectionCard(LanguageText.text("ui.gnss.warnings")) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakGnssSend.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takGnss == item,
                onClick = { vm.updateSettings { it.copy(takGnss = item) } },
                enabled = !settings.demoMode,
                label = { Text(item.label()) },
            )
        }
    }
    Note(
        LanguageText.text(
            "ui.mark.map.where.phone",
            mapOf("level" to LanguageText.text("ui.alert.label.2")),
        ),
    )
    Text(LanguageText.text("ui.how.long.mark.stays"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakGnssHold.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takGnssHold == item,
                onClick = { vm.updateSettings { it.copy(takGnssHold = item) } },
                enabled = !settings.demoMode && settings.takGnss != TakGnssSend.OFF,
                label = { Text(item.label()) },
            )
        }
    }
    Note(LanguageText.text("ui.time.starts.last.update"))
    }
    val trustedOn = settings.trustedWatch && !settings.demoMode
    SectionCard(LanguageText.text("trusted.tak")) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(LanguageText.text("trusted.tak"), Modifier.weight(1f))
        FieldwatchSwitch(
            settings.trustedTak,
            { on -> vm.updateSettings { it.copy(trustedTak = on) } },
            enabled = trustedOn,
        )
    }
    Note(LanguageText.text("ui.tak.trusted.note"))
    Text(LanguageText.text("trusted.stale"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakGnssHold.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.trustedTakHold == item,
                onClick = { vm.updateSettings { it.copy(trustedTakHold = item) } },
                enabled = trustedOn,
                label = { Text(item.label()) },
            )
        }
    }
    Note(
        LanguageText.text(
            "ui.tak.trusted.hold",
            mapOf("pin" to LanguageText.text("trusted.tak")),
        ),
    )
    }
    SectionCard(LanguageText.text("ui.floods.label")) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TakFloodSend.entries.forEach { item ->
            FieldwatchFilterChip(
                selected = settings.takFlood == item,
                onClick = { vm.updateSettings { it.copy(takFlood = item) } },
                enabled = !settings.demoMode,
                label = { Text(item.label()) },
            )
        }
    }
    Note(LanguageText.text("ui.adds.note.phone.s"))
    }
}

private fun takStatusLine(status: TakFeedStatus): String {
    if (status.paused) return LanguageText.text("ui.feed.status.paused.privacy")
    if (status.error != null) {
        val whenAt = takStatusWhen(status.at)
        return LanguageText.text("ui.feed.status") + "  ·  error: ${status.error}" +
            if (whenAt.isNotEmpty()) "  ·  $whenAt" else ""
    }
    if (status.at <= 0L) {
        return LanguageText.text("ui.feed.status.no.send")
    }
    val bits = ArrayList<String>(5)
    bits += LanguageText.text("ui.feed.on", mapOf("n" to status.onFeed.toString()))
    bits += LanguageText.text("ui.feed.sent", mapOf("n" to status.sent.toString()))
    if (status.gone > 0) {
        bits += LanguageText.text("ui.n.gone", mapOf("n" to status.gone.toString()))
    }
    if (status.dest.isNotBlank()) bits += status.dest
    val whenAt = takStatusWhen(status.at)
    if (whenAt.isNotEmpty()) bits += whenAt
    val head = LanguageText.text("ui.feed.status") + "  ·  ${bits.joinToString("  ·  ")}"
    return if (status.detail.isNotBlank() && status.sent == 0 && status.gone == 0) {
        "$head  ·  ${status.detail}"
    } else {
        head
    }
}

private fun takStatusWhen(at: Long): String {
    if (at <= 0L) return ""
    return java.time.Instant.ofEpochMilli(at)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}

private fun localIpv4Addresses(): List<String> {
    val found = LinkedHashSet<String>()
    val nifs = runCatching {
        java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
    }.getOrDefault(emptyList())
    for (nif in nifs) {
        if (!nif.isUp || nif.isLoopback) continue
        for (addr in java.util.Collections.list(nif.inetAddresses)) {
            if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                addr.hostAddress?.let { found += it }
            }
        }
    }
    return found.toList()
}

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

internal fun isBackgroundUsageAllowed(context: Context): Boolean =
    context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted != true

private enum class BatteryAndroidGate { BACKGROUND, UNRESTRICTED }

/**
 * Fieldwatch’s per-app Battery page. Samsung keeps Allow background usage and
 * Unrestricted on this same screen. [highlightBackground] asks Settings to
 * focus the background-usage switch when the OEM supports it.
 */
private fun openAppBatteryPage(context: Context, highlightBackground: Boolean) {
    val pkgUri = Uri.fromParts("package", context.packageName, null)
    val attempts = listOf(
        Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").apply {
            data = pkgUri
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("request_ignore_background_restriction", highlightBackground)
            if (!highlightBackground) {
                putExtra(":settings:fragment_args_key", "unrestricted_pref")
            }
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = pkgUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    for (intent in attempts) {
        if (intent.resolveActivity(context.packageManager) == null) continue
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
