@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package app.fieldwatch.ui.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.GnssAlertFloor
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.LearnLeft
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.TrustedNet
import app.fieldwatch.domain.TrustedNets
import app.fieldwatch.domain.TwinHear
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import app.fieldwatch.ui.component.FieldwatchSwitch
import app.fieldwatch.ui.component.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
fun TrustedNetworksPage(vm: FieldwatchViewModel, settings: AppSettings) {
    val book by vm.trustedBook.collectAsStateWithLifecycle()
    val importProblem by vm.trustedImportProblem.collectAsStateWithLifecycle()
    var detail by remember { mutableStateOf<String?>(null) }
    var askForget by remember { mutableStateOf<String?>(null) }
    val open = book.nets.find { it.ssid == detail }
    val now = learnNow()
    BackHandler(enabled = open != null) { detail = null }
    val exportTrusted = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::exportTrustedToUri) }
    val importTrusted = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importTrustedFromUri) }
    if (open == null) {
        TrustedList(
            settings = settings,
            nets = book.nets,
            onChange = vm::updateSettings,
            onOpen = { detail = it },
            onForget = { askForget = it },
            onExport = { exportTrusted.launch("fieldwatch-trusted.json") },
            onImport = { importTrusted.launch(arrayOf("application/json", "application/*", "*/*")) },
            now = now,
        )
    } else {
        TrustedDetail(
            net = open,
            demo = settings.demoMode,
            learnOn = settings.trustedLearn,
            learnDays = settings.trustedLearnDays,
            now = now,
            onRename = { vm.renameTrusted(open.ssid, it) },
            onPaused = { vm.setTrustedPaused(open.ssid, it) },
            onTravels = { vm.setTrustedTravels(open.ssid, it) },
            onRemoveAp = { vm.removeTrustedAp(open.ssid, it) },
            onRemovePlace = { vm.removeTrustedPlace(open.ssid, it) },
            onStopIgnore = { vm.stopTrustedIgnore(open.ssid, it) },
            onForget = { askForget = open.ssid },
        )
    }
    val forgetting = askForget
    if (forgetting != null) {
        ForgetTrustedDialog(
            onDismiss = { askForget = null },
            onConfirm = {
                vm.forgetTrusted(forgetting)
                askForget = null
                if (detail == forgetting) detail = null
            },
        )
    }
    val problem = importProblem
    if (problem != null) {
        AlertDialog(
            onDismissRequest = vm::clearTrustedImportFailed,
            title = { Text(LanguageText.text("trusted.import")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(LanguageText.text(problem))
                    if (problem != "trusted.import.bad" && problem != "import.failed.save") {
                        Text(LanguageText.text("import.failed.kept"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::clearTrustedImportFailed) {
                    Text(LanguageText.text("ui.ok.label"))
                }
            },
        )
    }
}

@Composable
private fun TrustedList(
    settings: AppSettings,
    nets: List<TrustedNet>,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onOpen: (String) -> Unit,
    onForget: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    now: Long,
) {
    SectionCard(LanguageText.text("settings.topic.trusted"), showTitle = false) {
        Note(
            LanguageText.text(
                "trusted.help",
                mapOf("mine" to LanguageText.text("ui.mine.label")),
            ),
        )
        SwitchRow(LanguageText.text("trusted.master"), settings.trustedWatch) { on ->
            onChange { it.copy(trustedWatch = on) }
        }
        Note(LanguageText.text("trusted.master.note"))
    }
    SectionCard(LanguageText.text("trusted.warnings")) {
        Note(LanguageText.text("trusted.warnings.note"))
        SwitchRow(LanguageText.text("trusted.downgrade"), settings.trustedDowngrade) { on ->
            onChange { it.copy(trustedDowngrade = on) }
        }
        Note(LanguageText.text("trusted.downgrade.note"))
        SwitchRow(LanguageText.text("trusted.vendor"), settings.trustedVendor) { on ->
            onChange { it.copy(trustedVendor = on) }
        }
        Note(LanguageText.text("trusted.vendor.note"))
        SwitchRow(LanguageText.text("trusted.away"), settings.trustedAway) { on ->
            onChange { it.copy(trustedAway = on) }
        }
        Note(LanguageText.text("trusted.away.note"))
        Text(LanguageText.text("trusted.away.distance"), style = MaterialTheme.typography.labelLarge)
        ChipRow(
            listOf(
                500 to "trusted.m500",
                1_000 to "trusted.km1",
                2_000 to "trusted.km2",
                5_000 to "trusted.km5",
            ),
            settings.trustedAwayMeters,
        ) { meters -> onChange { it.copy(trustedAwayMeters = meters) } }
        Note(LanguageText.text("trusted.away.distance.note"))
        Note(LanguageText.text("trusted.copy.note"))
        Text(LanguageText.text("trusted.floor"), style = MaterialTheme.typography.labelLarge)
        ChipRow(
            listOf(-75 to "trusted.dbm75", -85 to "trusted.dbm85", -95 to "trusted.dbm95"),
            settings.trustedFloorDbm,
        ) { floor -> onChange { it.copy(trustedFloorDbm = floor) } }
        Note(LanguageText.text("trusted.floor.note"))
        Text(LanguageText.text("trusted.confidence"), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GnssAlertFloor.entries.forEach { item ->
                FieldwatchFilterChip(
                    selected = settings.trustedAlertFloor == item,
                    onClick = { onChange { it.copy(trustedAlertFloor = item) } },
                    label = { Text(item.label()) },
                )
            }
        }
        Note(LanguageText.text("trusted.confidence.note"))
    }
    SectionCard(LanguageText.text("trusted.reach")) {
        Note(
            LanguageText.text(
                "trusted.reach.note",
                mapOf(
                    "beep" to LanguageText.text("ui.beep.label"),
                    "voice" to LanguageText.text("ui.voice.label"),
                    "watchlist" to LanguageText.text("settings.topic.alerts"),
                ),
            ),
        )
        SwitchRow(LanguageText.text("ui.beep.label"), settings.trustedBeep) { on ->
            onChange { it.copy(trustedBeep = on) }
        }
        SwitchRow(LanguageText.text("ui.voice.label"), settings.trustedVoice) { on ->
            onChange { it.copy(trustedVoice = on) }
        }
        SwitchRow(LanguageText.text("ui.system.notification"), settings.trustedShade) { on ->
            onChange { it.copy(trustedShade = on) }
        }
    }
    SectionCard(LanguageText.text("trusted.learn.section")) {
        SwitchRow(LanguageText.text("trusted.learn"), settings.trustedLearn) { on ->
            onChange { it.copy(trustedLearn = on) }
        }
        Note(LanguageText.text("trusted.learn.note"))
        Text(LanguageText.text("trusted.learn.days"), style = MaterialTheme.typography.labelLarge)
        ChipRow(
            listOf(
                0 to "trusted.hour.1",
                1 to "trusted.day.1",
                3 to "trusted.day.3",
                7 to "trusted.day.7",
                14 to "trusted.day.14",
            ),
            settings.trustedLearnDays,
        ) { days -> onChange { it.copy(trustedLearnDays = days) } }
        Note(LanguageText.text("trusted.learn.days.note"))
    }
    if (nets.isEmpty()) {
        Text(
            LanguageText.text("trusted.empty"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    nets.forEach { net ->
        key(net.ssid) {
            TrustedRow(
                net,
                settings.demoMode,
                left = if (settings.trustedLearn) {
                    LearnLeft.label(
                        LearnLeft.remainingMs(net.trustedAt, LearnLeft.trustedWindowMs(settings.trustedLearnDays), now),
                    )
                } else {
                    null
                },
                onOpen = { onOpen(net.ssid) },
                onForget = { onForget(net.ssid) },
            )
        }
    }
    FieldwatchActionButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
        Text(LanguageText.text("trusted.export"))
    }
    Text(
        LanguageText.text("trusted.export.places"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FieldwatchActionButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
        Text(LanguageText.text("trusted.import"))
    }
}

@Composable
private fun TrustedRow(net: TrustedNet, demo: Boolean, left: String?, onOpen: () -> Unit, onForget: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onForget()
            false
        },
    )
    val seen = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    val last = net.aps.maxOfOrNull { it.lastSeen } ?: 0L
    val count = if (net.aps.size == 1) {
        LanguageText.text("trusted.one")
    } else {
        LanguageText.text("trusted.count", mapOf("n" to net.aps.size.toString()))
    }
    val bands = net.aps.map { it.band }.filter { it.isNotBlank() }.distinct().joinToString(", ")
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
                Text(LanguageText.text("trusted.forget"), color = MaterialTheme.colorScheme.onError)
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
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(TrustedNets.display(net), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (bands.isBlank()) count else "$count · $bands",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (last > 0L) {
                        Text(
                            LanguageText.text("trusted.last") + " " + seen.format(Date(last)),
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
                TextButton(onClick = onForget) { Text(LanguageText.text("trusted.forget")) }
            }
        }
    }
}

@Composable
private fun TrustedDetail(
    net: TrustedNet,
    demo: Boolean,
    onRename: (String) -> Unit,
    onPaused: (Boolean) -> Unit,
    onTravels: (Boolean) -> Unit,
    onRemoveAp: (String) -> Unit,
    onRemovePlace: (Int) -> Unit,
    onStopIgnore: (String) -> Unit,
    onForget: () -> Unit,
    learnOn: Boolean,
    learnDays: Int,
    now: Long,
) {
    var draft by remember(net.ssid) { mutableStateOf(net.label) }
    SectionCard(TrustedNets.display(net)) {
        FieldwatchOutlinedField(
            value = draft,
            onValueChange = { draft = it.take(64) },
            label = LanguageText.text("ui.rename.label"),
        )
        TextButton(onClick = { onRename(draft) }) { Text(LanguageText.text("ui.rename.label")) }
        SwitchRow(LanguageText.text("trusted.pause"), net.learningPaused, onPaused)
        Note(LanguageText.text("trusted.pause.note"))
        if (learnOn) {
            Note(
                LearnLeft.label(
                    LearnLeft.remainingMs(net.trustedAt, LearnLeft.trustedWindowMs(learnDays), now),
                ),
            )
        }
        SwitchRow(LanguageText.text("trusted.travels"), net.travels, onTravels)
        Note(LanguageText.text("trusted.travels.note"))
        net.aps.forEach { ap ->
            Column {
                Text(MacUtil.screenMac(ap.bssid, demo), style = MaterialTheme.typography.titleSmall)
                val bits = listOfNotNull(
                    ap.vendor?.takeIf { it.isNotBlank() },
                    ap.band.takeIf { it.isNotBlank() },
                    ap.securityText?.takeIf { it.isNotBlank() },
                )
                if (bits.isNotEmpty()) {
                    Text(
                        bits.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onRemoveAp(ap.bssid) }) { Text(LanguageText.text("trusted.remove.ap")) }
            }
        }
        if (net.places.isNotEmpty()) {
            Text(LanguageText.text("trusted.places"), style = MaterialTheme.typography.labelLarge)
            net.places.forEachIndexed { index, _ ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        LanguageText.text("trusted.place", mapOf("n" to (index + 1).toString())),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onRemovePlace(index) }) {
                        Text(LanguageText.text("trusted.remove.place"))
                    }
                }
            }
        }
        if (net.ignored.isNotEmpty()) {
            Text(LanguageText.text("trusted.ignored"), style = MaterialTheme.typography.labelLarge)
            net.ignored.forEach { mac ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(MacUtil.screenMac(mac, demo), modifier = Modifier.weight(1f))
                    TextButton(onClick = { onStopIgnore(mac) }) {
                        Text(LanguageText.text("trusted.stop.ignore"))
                    }
                }
            }
        }
        TextButton(onClick = onForget) { Text(LanguageText.text("trusted.forget")) }
    }
}

@Composable
fun TrustNetworkControl(device: Sighting, vm: FieldwatchViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val book by vm.trustedBook.collectAsStateWithLifecycle()
    val allowed = TrustedNets.canTrust(device.name, device.hiddenSsid)
    val trusted = allowed && book.nets.any { it.ssid == device.name }
    var picking by remember(device.key) { mutableStateOf(false) }
    var forgetting by remember(device.key) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    LanguageText.text("ui.trust.network"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    LanguageText.text(if (allowed) "ui.trust.exact" else "ui.trust.hidden"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FieldwatchSwitch(
                checked = trusted,
                onCheckedChange = { on ->
                    if (!allowed) return@FieldwatchSwitch
                    if (on) picking = true else forgetting = true
                },
                enabled = allowed,
            )
        }
    }
    if (forgetting) {
        ForgetTrustedDialog(
            onDismiss = { forgetting = false },
            onConfirm = {
                vm.forgetTrusted(device.name)
                forgetting = false
            },
        )
    }
    if (picking) {
        val wifi = ui.devices.filter { it.kind == RadioKind.WIFI && !it.hiddenSsid && TrustedNets.canTrust(it.name, false) }
        val peers = wifi.filter { it.name == device.name }.let { rows ->
            if (rows.any { it.mac.equals(device.mac, true) }) rows else listOf(device) + rows
        }
        TrustPickDialog(
            ssid = device.name,
            peers = peers,
            visible = wifi,
            demo = ui.settings.demoMode,
            onDismiss = { picking = false },
            onConfirm = { groups ->
                groups.forEach { (ssid, picks) -> vm.trustNetwork(ssid, picks) }
                picking = false
            },
        )
    }
}

@Composable
private fun TrustPickDialog(
    ssid: String,
    peers: List<Sighting>,
    visible: List<Sighting>,
    demo: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, List<TwinHear>>) -> Unit,
) {
    var checked by remember(ssid) { mutableStateOf(peers.map { MacUtil.normalize(it.mac) }.toSet()) }
    var alsoOn by remember(ssid) { mutableStateOf(emptySet<String>()) }
    val chosen = peers.filter { MacUtil.normalize(it.mac) in checked }.map { it.asTwinHear() }
    val hears = visible.map { it.asTwinHear() }
    val also = TrustedNets.alsoNames(hears, chosen, ssid)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LanguageText.text("ui.trust.network")) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    LanguageText.text("ui.trust.exact"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    LanguageText.text("ui.trust.which"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (TrustedNets.severalMakers(peers.map { it.asTwinHear() })) {
                    Text(LanguageText.text("ui.trust.several"), style = MaterialTheme.typography.bodyMedium)
                }
                peers.forEach { peer ->
                    val mac = MacUtil.normalize(peer.mac)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = mac in checked,
                            onCheckedChange = { on ->
                                checked = if (on) checked + mac else checked - mac
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(MacUtil.screenMac(peer.mac, demo), style = MaterialTheme.typography.titleSmall)
                            val bits = listOfNotNull(
                                peer.vendor?.takeIf { it.isNotBlank() },
                                TrustedNets.bandOf(peer.frequencyMhz).takeIf { it.isNotBlank() },
                                peer.facts.security?.takeIf { it.isNotBlank() },
                            )
                            if (bits.isNotEmpty()) {
                                Text(
                                    bits.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (also.isNotEmpty()) {
                    Text(LanguageText.text("ui.trust.also"), style = MaterialTheme.typography.labelLarge)
                    also.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = name in alsoOn,
                                onCheckedChange = { on ->
                                    alsoOn = if (on) alsoOn + name else alsoOn - name
                                },
                            )
                            Text(name, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val groups = LinkedHashMap<String, List<TwinHear>>()
                    groups[ssid] = chosen
                    for (name in also) {
                        if (name !in alsoOn) continue
                        val picks = TrustedNets.apsForName(hears, chosen, name)
                        if (picks.isNotEmpty()) groups[name] = picks
                    }
                    onConfirm(groups)
                },
                enabled = chosen.isNotEmpty(),
            ) { Text(LanguageText.text("ui.trust.network")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LanguageText.text("language.cancel")) }
        },
    )
}

@Composable
fun TrustedAlertDialog(vm: FieldwatchViewModel) {
    val alert by vm.trustedNotice.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val shown = alert ?: return
    AlertDialog(
        onDismissRequest = { vm.dismissTrusted(shown.ssid, shown.bssid) },
        title = { Text(TrustedNets.sentence(shown.ssid, shown.reasons)) },
        text = {
            Column {
                Text(MacUtil.screenMac(shown.bssid, ui.settings.demoMode))
                if (shown.ouiNote) {
                    Text(
                        LanguageText.text("trusted.oui.note", mapOf("oui" to "00:13:37")),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            // One column. The dialog dismiss slot sits beside this column and covers the last action.
            Column(horizontalAlignment = Alignment.End) {
                if (shown.offerLearn) {
                    TextButton(onClick = { vm.learnTrusted(shown.ssid, shown.bssid) }) {
                        Text(LanguageText.text("trusted.learn.ap"))
                    }
                }
                if (shown.offerIgnore) {
                    TextButton(onClick = { vm.ignoreTrusted(shown.ssid, shown.bssid) }) {
                        Text(LanguageText.text("trusted.ignore"))
                    }
                }
                if (shown.offerRemember) {
                    TextButton(onClick = { vm.rememberTrusted(shown.ssid, shown.bssid) }) {
                        Text(LanguageText.text("trusted.remember"))
                    }
                }
                TextButton(onClick = { vm.dismissTrusted(shown.ssid, shown.bssid) }) {
                    Text(LanguageText.text("trusted.dismiss"))
                }
            }
        },
    )
}

@Composable
private fun ForgetTrustedDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LanguageText.text("trusted.forget")) },
        text = { Text(LanguageText.text("trusted.forget.ask")) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(LanguageText.text("trusted.forget")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(LanguageText.text("language.cancel")) }
        },
    )
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
private fun SwitchRow(label: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        FieldwatchSwitch(on, set)
    }
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

private fun Sighting.asTwinHear() = TwinHear(
    ssid = name,
    bssid = mac,
    rssi = rssi,
    fresh = true,
    securityText = facts.security,
    channel = channel,
    frequencyMhz = frequencyMhz,
    vendor = vendor,
    lat = null,
    lon = null,
    accuracyM = null,
    locationPaused = false,
)
