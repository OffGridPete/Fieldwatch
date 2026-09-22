package app.fieldwatch.ui.screen

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSlider
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fieldwatch.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fieldwatch.domain.AlertVoiceWhat
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.LogFormat
import app.fieldwatch.domain.ScanIntensity
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.TakUdpPreset
import app.fieldwatch.radio.WifiRadio
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.FieldwatchFilterChip
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
    onShowLiveTour: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = state.settings
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
    var confirmRestore by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar("Settings") },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Appearance") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Night mode", Modifier.weight(1f))
                FieldwatchSwitch(settings.nightMode, { on -> vm.updateSettings { it.copy(nightMode = on) } })
            }
            Text(
                "Off by default. Red-on-black field display so chips, text, and signal marks " +
                    "do not dump green or blue into a dark sit. Background stays dark. " +
                    "Phone brightness is unchanged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep screen on", Modifier.weight(1f))
                FieldwatchSwitch(settings.keepScreenOn, { on -> vm.updateSettings { it.copy(keepScreenOn = on) } })
            }
            Text(
                "On by default. Stops the display from sleeping while Fieldwatch is open so BLE is not parked when the phone blanks. Scanning still runs in the notification if you leave the app. Turn it off when you pocket the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Privacy mode", Modifier.weight(1f))
                FieldwatchSwitch(settings.demoMode, { on -> vm.updateSettings { it.copy(demoMode = on) } })
            }
            Text(
                "Hides the last three octets of every MAC on Live, radar, timeline, detail, Hunt, Named radios, and watchlist cards as **:**:** so the screen and sit reports do not show full addresses. GPS last-fix and Debrief / AI Export / detail Share coordinates become “masked”; street names are omitted from those sit reports. The first three octets (OUI / vendor prefix) stay. Off by default. Logs, matching, filters, Hunt math, Moving with you, and saved signatures still use the real MAC and GPS. A TAK / CoT feed, if you turned it on, is paused while this is on so full MACs and coordinates are not sent onto the LAN. Turn this off when you need the full address or coordinates on screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("Scanning") {
            val label = when (settings.intensity) {
                ScanIntensity.SAVER -> "Battery saver"
                ScanIntensity.BALANCED -> "Balanced"
                ScanIntensity.PERFORMANCE -> "High performance"
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
                "Wi-Fi is a batch radio: the phone grabs every AP at once, then must wait. High performance asks about every 30s — that is the fastest cadence that stays under the OS limit of four scans per two minutes. BLE still streams in between.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StableCaption(
                state.throttleHint.ifBlank { " " },
                "Wi-Fi waiting on OS",
                "Wi-Fi scanning",
                "Wi-Fi next 99s",
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
            val fastActive = settings.wifiFastScan && !osThrottled
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Faster Wi-Fi AP scans", Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = settings.wifiFastScan,
                    onCheckedChange = { on ->
                        if (!on) {
                            vm.updateSettings { it.copy(wifiFastScan = false) }
                        } else if (!osThrottled) {
                            vm.updateSettings { it.copy(wifiFastScan = true) }
                        } else {
                            needDevOptions = true
                        }
                    },
                )
            }
            StableCaption(
                when {
                    Build.VERSION.SDK_INT < 30 ->
                        "Needs Android 11+ so Fieldwatch can read whether the OS is still throttling scans. This phone cannot confirm that, so the switch stays off."
                    fastActive ->
                        "On. Fieldwatch asks for a new AP list about every 8 seconds. Uses more battery and heat. If the OS starts refusing scans, it backs off."
                    settings.wifiFastScan && osThrottled ->
                        "Saved on, but not in effect — Android Wi-Fi scan throttling is still on. Turn that off in Developer options, then return here."
                    else ->
                        "Stock Android allows about four AP scans per two minutes. Faster scans only run after you turn off Wi-Fi scan throttling in Developer options. Fieldwatch checks that OS switch before turning this on, and cannot change it for you."
                },
                "Needs Android 11+ so Fieldwatch can read whether the OS is still throttling scans. This phone cannot confirm that, so the switch stays off.",
                "On. Fieldwatch asks for a new AP list about every 8 seconds. Uses more battery and heat. If the OS starts refusing scans, it backs off.",
                "Saved on, but not in effect — Android Wi-Fi scan throttling is still on. Turn that off in Developer options, then return here.",
                "Stock Android allows about four AP scans per two minutes. Faster scans only run after you turn off Wi-Fi scan throttling in Developer options. Fieldwatch checks that OS switch before turning this on, and cannot change it for you.",
            )
            if (needDevOptions) {
                AlertDialog(
                    onDismissRequest = { needDevOptions = false },
                    title = { Text("Developer options required") },
                    text = {
                        Text(
                            if (Build.VERSION.SDK_INT < 30) {
                                "This phone is older than Android 11, so Fieldwatch cannot read the OS Wi-Fi scan-throttle switch. Faster AP scanning stays off."
                            } else {
                                "Android is still throttling Wi-Fi scans (about four per two minutes). Fieldwatch will not turn Faster Wi-Fi AP scans on until that is off.\n\n" +
                                    "Enable Developer options (tap Build number seven times in About phone), then Settings → Developer options → Wi-Fi scan throttling → Off. Come back and flip this switch again."
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
                            ) { Text("Open developer options") }
                        } else {
                            TextButton(onClick = { needDevOptions = false }) { Text("OK") }
                        }
                    },
                    dismissButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(onClick = { needDevOptions = false }) { Text("Not now") }
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow background usage", Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = backgroundAllowed,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.BACKGROUND },
                )
            }
            Text(
                "Mirrors Android Allow background usage. Tap to open Fieldwatch’s Battery page and " +
                    "use that switch. Fieldwatch updates when you return. Off: the OS can kill the scan " +
                    "as soon as you leave. Not Keep screen on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Unrestricted battery", Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = unrestricted,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.UNRESTRICTED },
                )
            }
            Text(
                "Mirrors Android Unrestricted (not Optimized). Some phones (Samsung among them) do not " +
                    "open onto that choice. If you only see Allow background usage, tap that row to " +
                    "click through and select Unrestricted. Fieldwatch updates when you return.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (batteryGate != null) {
                val background = batteryGate == BatteryAndroidGate.BACKGROUND
                AlertDialog(
                    onDismissRequest = { batteryGate = null },
                    title = {
                        Text(if (background) "Allow background usage" else "Unrestricted battery")
                    },
                    text = {
                        Text(
                            if (background) {
                                "The next screen is Fieldwatch’s Battery page. Use the Allow background usage switch. " +
                                    "Fieldwatch will match that setting when you return."
                            } else {
                                "Some phones (Samsung among them) do not open onto Unrestricted / " +
                                    "Optimized / Restricted. If you only see Allow background usage, " +
                                    "tap that row (the words, not the blue switch) to click through, " +
                                    "then select Unrestricted. Fieldwatch will match that when you return."
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
                        ) { Text("Open Android settings") }
                    },
                    dismissButton = {
                        TextButton(onClick = { batteryGate = null }) { Text("Not now") }
                    },
                )
            }
            }

            SectionCard("Watchlist") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Watchlist alerts", Modifier.weight(1f))
                FieldwatchSwitch(settings.alertsEnabled, { on -> vm.updateSettings { it.copy(alertsEnabled = on) } })
            }
            Text(
                "On by default. Master switch for bookmarked signatures and devices. Off: no beep, vibration, flash, jump, or shade card. Bookmarking still works — you just will not be told when that radio appears.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val radioWatchN = state.watchlist.count { it.deviceKey != null }
            FieldwatchActionButton(
                onClick = onRadioBookmarks,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Named radios ($radioWatchN)") }
            Text(
                "Custom names for one MAC. Alert is optional. Filters → Named radios only shows them on Live. Signature watches stay on Signatures.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Beep on watched signature", Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertBeep,
                    { on -> vm.updateSettings { it.copy(alertBeep = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                "The double pip on media volume when a bookmarked signature or device first appears, or returns after leaving. Sitting detections do not beep again. Independent of Voice — use beep, voice, or both. Raise media volume if you hear nothing, then tap Test alert.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice on watched signature", Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertVoice,
                    { on -> vm.updateSettings { it.copy(alertVoice = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                "On by default. Speaks on the same media volume as the pip. Independent of Beep: with Beep on, voice follows the pip; with Beep off, voice only. Not Hunt. If a phrase is already being spoken, a second hit is skipped. Phones with no text-to-speech still beep if Beep is on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("What to say", style = MaterialTheme.typography.labelLarge)
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
                "For signature watches: Class is the Live glyph bucket (finder tags, audio, …). Signature is the catalog row (Apple AirTags, Axon, …). Class + signature (default) says both. A named radio with Alert on always says its custom name, even if it has no class. Test alert plays the signature mix you have on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::testWatchBeep,
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
            ) { Text("Test alert") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Jump to new watched detection", Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.snapToBeep,
                    { on -> vm.updateSettings { it.copy(snapToBeep = on) } },
                    enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
                )
            }
            Text(
                "When a new watched signature or device appears, Live scrolls to that row so you can see the flash. Works with beep, voice, or both. Weak hits sit at the bottom of a strength-ranked list. Turn this off if you do not want the list to move.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("System notification", Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertShade,
                    { on -> vm.updateSettings { it.copy(alertShade = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                "Optional. Posts a silent shade card when a watched radio appears. Off by default — the beep and flash are enough, and skipping the card keeps the scan loop lighter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("Location") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tag detections with GPS", Modifier.weight(1f))
                FieldwatchSwitch(settings.tagLocation, { on -> vm.updateSettings { it.copy(tagLocation = on) } })
            }
            Text(
                "On by default. Requests live GPS/network updates and stamps each hear (Live detail, Moving with you, " +
                    "Debrief, and lat/lon on new log rows). Last-known-only is ignored if older than 30 s. " +
                    "That is your GPS at hear-time, not an independent fix on the other radio. " +
                    "Use high-accuracy Location or the path stays 0. Turn off if you do not want operator coordinates on logs. " +
                    "Heard-here TAK pins also need this; advertised payload coordinates (Remote ID) do not.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Online place names in Debrief", Modifier.weight(1f))
                FieldwatchSwitch(settings.onlineLookup, { on -> vm.updateSettings { it.copy(onlineLookup = on) } })
            }
            Text(
                "On by default. When the phone has internet, Debrief and AI Export reverse-geocode GPS stamps " +
                    "to street/city via the system geocoder (no Fieldwatch cloud, no API key). " +
                    "If you are offline, the report notes that and continues — no error dialog. " +
                    "Turn off if you do not want streets of your path in those files. " +
                    "Debrief, AI Export, Share log, Save log, and Reset / clear log are on the Reports tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("TAK / CoT") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TAK / CoT feed", Modifier.weight(1f))
                FieldwatchSwitch(settings.takEnabled, { on -> vm.updateSettings { it.copy(takEnabled = on) } })
            }
            Text(
                "Off by default. Sends Cursor-on-Target UDP markers to ATAK, WinTAK, or iTAK. " +
                    "This phone (${TakDefaults.LOOPBACK}:${TakDefaults.PORT}) is ATAK CIV on this handset. " +
                    "LAN multicast is ${TakDefaults.SA_HOST}:${TakDefaults.SA_PORT}. " +
                    "Custom is a unicast IPv4 or hostname. UDP only — a TAK server’s TCP 8087 is not this feed. " +
                    "Heard-here pins sit at this phone’s GPS at the loudest hear (closest approach) and are labeled (here). " +
                    "Walking away does not drag the pin; a louder hear moves it. Keep-alives refresh the same lat/lon every ~10 s so ATAK does not drop it. " +
                    "Advertised lat/lon (stock Remote ID) sit on the aircraft; the same Remote ID " +
                    "keeps one marker that moves (UAS ID, not the rotating BLE MAC). " +
                    "A decoded pilot location is a second pin. Gone radios are dropped on ATAK instead of sitting 120 s. " +
                    "Tap a marker in ATAK for remarks (name, MAC, RSSI, signatures). " +
                    "Not direction-finding. Not a Remote ID plugin. Privacy mode pauses the feed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.takEnabled && settings.demoMode) {
                Text(
                    "Privacy mode is on — the feed is paused so full MACs and coordinates are not sent. Turn Privacy mode off to publish.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (settings.takEnabled) {
                TakFeedSettings(settings, vm, state.takStatus)
            }
            }

            SectionCard("Logging") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Write detections to disk", Modifier.weight(1f))
                FieldwatchSwitch(settings.loggingEnabled, { on -> vm.updateSettings { it.copy(loggingEnabled = on) } })
            }
            StableCaption(
                if (settings.loggingEnabled) {
                    "Logging is on. New detections are appended to the rotating file."
                } else {
                    "Logging is off. Scanning still runs; nothing new is written until you turn this back on."
                },
                "Logging is on. New detections are appended to the rotating file.",
                "Logging is off. Scanning still runs; nothing new is written until you turn this back on.",
            )
            Text("File format", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = settings.logFormat == LogFormat.CSV,
                    onClick = { vm.updateSettings { it.copy(logFormat = LogFormat.CSV) } },
                    enabled = settings.loggingEnabled,
                    label = { Text("CSV") },
                )
                FieldwatchFilterChip(
                    selected = settings.logFormat == LogFormat.JSON,
                    onClick = { vm.updateSettings { it.copy(logFormat = LogFormat.JSON) } },
                    enabled = settings.loggingEnabled,
                    label = { Text("JSON lines") },
                )
            }
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
            var staleDrag by remember { mutableIntStateOf(settings.staleSec) }
            var staleDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.staleSec) {
                if (!staleDragging) staleDrag = settings.staleSec
            }
            Text("Stale after ${staleDrag}s")
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
            StickyHeight("log-stats") {
                Text(
                    "${state.logLines} lines this session  ·  ${vm.logBytes() / 1024} KB on disk. " +
                        "Share, Save, and Reset / clear log are on the Reports tab.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            }

            SectionCard("Signatures") {
            Text(
                "Export the catalog (stock plus any you added or edited) to share with another Fieldwatch or as a backup. Import adds new rows and extra rules; it does not delete anything. Same id or the same match rules are skipped so a pack can be imported twice. Update stock catalog from GitHub replaces stock rows (including Extra attention) from the repo; bookmarks, Settings, and signatures you added stay. Needs internet. Offline: Import signatures from a file. Restore defaults below still wipes customs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSignatureShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export signatures") }
            FieldwatchActionButton(
                onClick = { saveSignatures.launch(vm.suggestedSignaturesName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save signatures to SD card / storage…") }
            FieldwatchActionButton(
                onClick = {
                    importSignatures.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import signatures…") }
            FieldwatchActionButton(
                onClick = vm::updateStockCatalogFromGitHub,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Update stock catalog from GitHub") }

            FieldwatchActionButton(
                onClick = { confirmRestore = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Restore default signatures & presets")
            }
            }

            SectionCard("Settings backup") {
            Text(
                "Settings switches, the current filter, filter presets, named radios, and signature watches. " +
                    "Not the catalog — that is Export signatures. Not logs or GPS. " +
                    "Import replaces those on this phone; the catalog stays. " +
                    "Use this after a factory reset or on a new phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSettingsShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export settings") }
            FieldwatchActionButton(
                onClick = { saveSettings.launch(vm.suggestedSettingsName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save settings to SD card / storage…") }
            FieldwatchActionButton(
                onClick = {
                    importSettings.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import settings…") }
            }

            FieldwatchActionButton(
                onClick = onShowLiveTour,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Show Live tour") }
            Text(
                "Chrome overlay on Live: Tune is Display (Radar, list, By class), Pause, Filters, Signatures, Reports, Settings. First-run after the license; this button shows it again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Fieldwatch ${app.fieldwatch.BuildConfig.VERSION_NAME}  ·  Catalog ${state.catalogVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Passive Wi-Fi + BLE only. " +
                    "Stock Android cannot promiscuously capture Wi-Fi stations; access points and BLE advertisers are what the radios expose.",
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
                    "This phone’s IPv4  ·  none"
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
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore defaults?") },
            text = {
                Text(
                    "Rewrites the catalog (stock rows, class colors, Decode fields), stock bookmarks, " +
                        "stock filter chips, and default Settings switches. Custom signatures and chips you " +
                        "saved are wiped. Export signatures and Export settings first if you want a backup. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        vm.restoreDefaults()
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            },
        )
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
            "Copyright (c) 2026 Off Grid Pete LLC. All rights reserved.",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialChip(
                icon = R.drawable.ic_instagram,
                label = "@OffGridPete",
                tint = muted,
                onClick = { openUrl(context, "https://instagram.com/OffGridPete") },
            )
            SocialChip(
                icon = R.drawable.ic_x,
                label = "@OGridPete",
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
private fun TakFeedSettings(settings: AppSettings, vm: FieldwatchViewModel, status: TakFeedStatus) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var hostText by remember { mutableStateOf(settings.takHost) }
    var portText by remember { mutableStateOf(settings.takPort.toString()) }
    LaunchedEffect(settings.takHost) { hostText = settings.takHost }
    LaunchedEffect(settings.takPort) { portText = settings.takPort.toString() }
    val preset = TakPublish.udpPreset(settings.takHost, settings.takPort)
    Text("Destination", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.THIS_PHONE,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.THIS_PHONE)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text("This phone") },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.LAN_MULTICAST,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.LAN_MULTICAST)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text("LAN multicast") },
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
            label = { Text("Custom") },
        )
    }
    Text(
        "This phone: ${TakDefaults.LOOPBACK}:${TakDefaults.PORT} (ATAK CIV on this handset). " +
            "LAN multicast: ${TakDefaults.SA_HOST}:${TakDefaults.SA_PORT} (other ATAKs on this Wi-Fi). " +
            "Custom: type a unicast IPv4 or hostname. UDP only. A TAK server’s TCP 8087 is not this feed. " +
            "If This phone does not plot, use Custom with this phone’s Wi-Fi IPv4 from the footer and port ${TakDefaults.PORT}.",
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    OutlinedTextField(
        value = hostText,
        onValueChange = { value ->
            hostText = value
            val trimmed = value.trim()
            if (trimmed.isNotEmpty()) {
                vm.updateSettings { it.copy(takHost = trimmed) }
            }
        },
        label = { Text("Host") },
        placeholder = { Text(TakDefaults.HOST) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !settings.demoMode,
    )
    OutlinedTextField(
        value = portText,
        onValueChange = { value ->
            val filtered = value.filter { it.isDigit() }.take(5)
            portText = filtered
            val n = filtered.toIntOrNull() ?: return@OutlinedTextField
            if (n in 1..65_535) {
                vm.updateSettings { it.copy(takPort = n) }
            }
        },
        label = { Text("Port") },
        placeholder = { Text(TakDefaults.PORT.toString()) },
        supportingText = {
            Text("UDP. ATAK CIV ${TakDefaults.PORT}. SA multicast ${TakDefaults.SA_PORT}. Not TCP 8087.")
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        enabled = !settings.demoMode,
    )
    Text(takStatusLine(status), style = MaterialTheme.typography.bodySmall, color = muted)
    Text("What to send", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = settings.takAttention,
            onClick = { vm.updateSettings { it.copy(takAttention = !it.takAttention) } },
            enabled = !settings.demoMode,
            label = { Text("Extra attention") },
        )
        FieldwatchFilterChip(
            selected = settings.takPayloadFix,
            onClick = { vm.updateSettings { it.copy(takPayloadFix = !it.takPayloadFix) } },
            enabled = !settings.demoMode,
            label = { Text("Payload location") },
        )
        FieldwatchFilterChip(
            selected = settings.takWatchlist,
            onClick = { vm.updateSettings { it.copy(takWatchlist = !it.takWatchlist) } },
            enabled = !settings.demoMode,
            label = { Text("Watchlist") },
        )
        FieldwatchFilterChip(
            selected = settings.takAllSignatures,
            onClick = { vm.updateSettings { it.copy(takAllSignatures = !it.takAllSignatures) } },
            enabled = !settings.demoMode,
            label = { Text("All signatures") },
        )
    }
    Text(
        "Independent chips. Extra attention (on): body-cam, glasses, recording wearables, pentest, public-safety APs. " +
            "Payload location (on): advertised lat/lon from a decode map — required for stock Remote ID, which has no Extra attention mark. " +
            "Watchlist (off): bookmarked signatures and named radios with Alert on. " +
            "All signatures (off): every labeled radio — noisy in a plaza. Unmatched radios never go. " +
            "A pin still needs coordinates: advertised payload, or GPS tagging with a live fix. " +
            "Heard-here holds the loudest hear, not the last, and callsigns end in (here). " +
            "Remote ID keeps one aircraft marker (UAS ID) plus a pilot pin when that location decoded.",
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
}

private fun takStatusLine(status: TakFeedStatus): String {
    if (status.paused) return "Feed status  ·  paused (Privacy mode)"
    if (status.error != null) {
        val whenAt = takStatusWhen(status.at)
        return "Feed status  ·  error: ${status.error}" + if (whenAt.isNotEmpty()) "  ·  $whenAt" else ""
    }
    if (status.at <= 0L) {
        return "Feed status  ·  no send yet this session"
    }
    val bits = ArrayList<String>(5)
    bits += "on the feed ${status.onFeed}"
    bits += "sent ${status.sent}"
    if (status.gone > 0) {
        bits += if (status.gone == 1) "1 gone" else "${status.gone} gone"
    }
    if (status.dest.isNotBlank()) bits += status.dest
    val whenAt = takStatusWhen(status.at)
    if (whenAt.isNotEmpty()) bits += whenAt
    val head = "Feed status  ·  ${bits.joinToString("  ·  ")}"
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

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

private fun isBackgroundUsageAllowed(context: Context): Boolean =
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
