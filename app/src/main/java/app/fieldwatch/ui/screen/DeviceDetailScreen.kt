package app.fieldwatch.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import app.fieldwatch.ui.component.DecodeGlyph
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.domain.CodDecoder
import app.fieldwatch.domain.FamilyVerdict
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.DeviceExplain
import app.fieldwatch.domain.Palette
import app.fieldwatch.domain.RadioDb
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.ServiceDataRecord
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureFamilyHint
import app.fieldwatch.domain.SignatureFieldDecoder
import app.fieldwatch.domain.hexSpaced
import app.fieldwatch.domain.label
import app.fieldwatch.radio.BleAdParser
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.theme.Amber
import app.fieldwatch.ui.theme.Cyan
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf
import app.fieldwatch.ui.component.PresenceTrack
import app.fieldwatch.ui.component.Sparkline
import app.fieldwatch.ui.component.StickyHeight
import app.fieldwatch.ui.component.rssiColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: Sighting,
    vm: FieldwatchViewModel,
    watched: Boolean,
    onBack: () -> Unit,
    onCreateFleet: () -> Unit,
    onHunt: () -> Unit,
    demoMode: Boolean = false,
) {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val accent = (device.fleetIds.firstOrNull()
        ?.let { Color(Palette.color(vm.fleetColor(it))) }
        ?: rssiColor(device.rssi))
        .nightIf(LocalNightMode.current)
    val facts = device.facts
    val familyHint by vm.familyHint.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val custom = vm.watchLabelFor(device.key)
                    val title = custom?.takeIf { it.isNotBlank() }
                        ?: device.listTitle(device.fleetIds.map { vm.fleetName(it) })
                    Text(MacUtil.redactMacIn(title, device.mac, demoMode), maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.toggleWatchDevice(device) }) {
                        Icon(if (watched) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, "Watch")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(MacUtil.screenMac(device.mac, demoMode), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
            if (device.gone) {
                Text(
                    "Not on the air. This is the last detail we heard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var nameDraft by remember(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var lastSaved by remember(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var editingName by remember(device.key) { mutableStateOf(false) }
            val draftLabel = RadioBookmarks.clip(nameDraft)
            val nameIsSaved = lastSaved.isNotBlank() && draftLabel == lastSaved
            val canName = RadioBookmarks.canSetCustomName(device)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (lastSaved.isNotBlank()) {
                        Text(
                            "Custom name",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(lastSaved, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Advertised",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            device.name.ifBlank { "No advertised name" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (device.name.isNotBlank()) {
                        Text(
                            "Advertised name",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(device.name, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            "Name",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "No advertised name",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canName) {
                    IconButton(onClick = { editingName = !editingName }) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editingName) "Hide custom name" else "Custom name",
                        )
                    }
                }
            }
            if (editingName && canName) {
                FieldwatchOutlinedField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(RadioBookmarks.MAX_NAME) },
                    label = "Custom name",
                    supportingText = RadioBookmarks.customNameHint(device),
                )
                FieldwatchActionButton(
                    onClick = {
                        vm.saveRadioName(device, nameDraft)
                        nameDraft = draftLabel
                        lastSaved = draftLabel
                        scope.launch {
                            snackbarHostState.showSnackbar("Saved as $draftLabel")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nameDraft.isNotBlank() && !nameIsSaved,
                ) {
                    if (nameIsSaved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text("Saved")
                    } else {
                        Text("Save name")
                    }
                }
            }

            var notesDraft by remember(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var lastSavedNotes by remember(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var editingNotes by remember(device.key) { mutableStateOf(false) }
            val draftNotes = RadioBookmarks.clipNotes(notesDraft)
            val notesIsSaved = draftNotes == lastSavedNotes
            if (canName || lastSavedNotes.isNotBlank()) {
                ObserverNotesCard(
                    notes = lastSavedNotes,
                    canEdit = canName,
                    editing = editingNotes && canName,
                    draft = notesDraft,
                    onToggleEdit = { editingNotes = !editingNotes },
                    onDraftChange = { notesDraft = it.take(RadioBookmarks.MAX_NOTES) },
                    onSave = {
                        vm.saveRadioNotes(device, notesDraft)
                        notesDraft = draftNotes
                        lastSavedNotes = draftNotes
                        if (lastSaved.isBlank() && draftNotes.isNotBlank()) {
                            val suggest = RadioBookmarks.suggestLabel(
                                device,
                                device.fleetIds.map { vm.fleetName(it) },
                            )
                            lastSaved = suggest
                            nameDraft = suggest
                        }
                        editingNotes = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (draftNotes.isBlank()) "Observer notes cleared" else "Observer notes saved",
                            )
                        }
                    },
                    saveEnabled = !notesIsSaved,
                    saved = notesIsSaved && lastSavedNotes.isNotBlank(),
                )
            }

            val guess = DeviceExplain.guess(device, device.fleetIds.map { vm.fleetName(it) })
            StickyHeight(device.key to "guess") { GuessCard(guess) }
            val attention = vm.attentionNotesFor(device)
            if (attention.isNotEmpty()) {
                StickyHeight(device.key to "attention") { ExtraAttentionCard(attention) }
            }
            val notes = vm.signatureNotesFor(device)
            if (notes.isNotEmpty()) {
                StickyHeight(device.key to "notes") { SignatureNotesCard(notes) }
            }

            StickyHeight(device.key to "identity") {
                Section("Identity")
                Meta(
                    "Radio",
                    if (device.kind == RadioKind.WIFI) {
                        "Wi-Fi access point (beaconing a network)"
                    } else {
                        "Bluetooth Low Energy advertiser"
                    },
                )
                Meta("Address", DeviceExplain.addressExplain(device))
                vendorLine(device)?.let { Meta("Who made it", it) }
                    ?: Meta("OUI (vendor prefix)", "${device.oui} — no IEEE match; randomized addresses usually have none")
                if (device.hiddenSsid) {
                    Meta("Network name (SSID)", "Hidden — the AP is beaconing but not publishing a name")
                }
            }

            StickyHeight(device.key to "signal") {
                Section("Signal")
                if (device.gone) {
                    Meta("How loud here (RSSI)", "Not available")
                    Meta(
                        "Last heard",
                        "${fmt.format(Date(device.lastSeen))} at ${device.rssi} dBm",
                    )
                } else {
                    Meta("How loud here (RSSI)", DeviceExplain.rssiExplain(device.rssi))
                    Text(
                        "Closer to 0 dBm is louder here, not a distance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Meta("Heard range this session", "${device.rssiMin} to ${device.rssiMax} dBm")
                facts.txPowerDbm?.let {
                    Meta("Claimed transmit power", "$it dBm — how loud it says it transmits, not a distance")
                }
                if (device.channel != 0 || device.frequencyMhz != 0) {
                    Meta(
                        "Channel / frequency",
                        buildString {
                            if (device.channel != 0) append("channel ${device.channel}")
                            if (device.frequencyMhz != 0) {
                                if (isNotEmpty()) append("  ·  ")
                                append("${device.frequencyMhz} MHz")
                            }
                            facts.channelWidth?.let { append("  ·  $it wide") }
                        },
                    )
                }
                facts.wifiStandard?.let { Meta("Wi-Fi generation", it) }
                if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
                    Meta(
                        "Center frequencies",
                        listOfNotNull(
                            facts.centerFreq0?.let { "$it MHz" },
                            facts.centerFreq1?.let { "$it MHz" },
                        ).joinToString("  ·  "),
                    )
                }
            }

            if (device.kind == RadioKind.BLE) {
                StickyHeight(device.key to "ble") {
                Section("Bluetooth advertisement")
                facts.primaryPhy?.let {
                    val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                    Meta("Radio PHY", phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(phy) })
                }
                facts.connectable?.let {
                    Meta(
                        "Connectable",
                        if (it) "Yes — a phone could open a BLE connection"
                        else "No — broadcast-only (you can hear it, not join it from this scan)",
                    )
                }
                facts.advertisingIntervalMs?.let {
                    Meta(
                        "How often it advertises",
                        "%.0f ms between bursts (smaller = chattier on the air)".format(it),
                    )
                }
                facts.periodicIntervalMs?.let {
                    Meta("Periodic advertising", "%.0f ms".format(it))
                }
                facts.advFlags?.let { flags ->
                    Meta("Discoverability", DeviceExplain.flagsExplain(flags))
                    Meta("Flags (raw)", "0x%02X".format(flags), mono = true)
                }
                facts.appearance?.let { value ->
                    val name = RadioDb.appearance(value)
                    Meta(
                        "What it says it is (Appearance)",
                        name?.let { "$it\nThe device publishes this GAP Appearance code to describe itself." }
                            ?: "Unlisted Appearance 0x%04X".format(value),
                    )
                    Meta("Appearance code", "0x%04X".format(value), mono = true)
                }
                CodDecoder.decodeOrNull(facts.deviceClass)?.let { cod ->
                    Meta(
                        "Classic Bluetooth class",
                        buildString {
                            append(cod.major)
                            if (cod.minor.isNotBlank()) append(" / ").append(cod.minor)
                            append("\nThis is the Class of Device bitfield used by classic Bluetooth.")
                            if (cod.services.isNotEmpty()) {
                                append("\nAlso offers: ")
                                append(cod.services.joinToString(", "))
                            }
                        },
                    )
                }
                }
            }

            if (device.kind == RadioKind.WIFI) {
                StickyHeight(device.key to "wifi") {
                    Section("Wi-Fi access point")
                    facts.security?.let {
                        Meta("Encryption / login", DeviceExplain.wifiSecurityExplain(it))
                        if (it.isNotBlank()) Meta("Security string", it, mono = true)
                    }
                    facts.supportedRates?.let {
                        Meta("Supported rates", "$it Mbps  (* = required basic rate)")
                    }
                    facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                        Meta("Capability string", it, mono = true)
                    }
                }
            }

            if (device.serviceUuids.isNotEmpty() || facts.serviceData.isNotEmpty()) {
                StickyHeight(device.key to "services") {
                    if (device.serviceUuids.isNotEmpty()) {
                        Section("Services it offers")
                        Meta(
                            "Service IDs",
                            device.serviceUuids.joinToString("\n") { uuid ->
                                DeviceExplain.uuidGloss(uuid)?.let { "$uuid  ·  $it" } ?: uuid
                            },
                            mono = true,
                        )
                    }
                    facts.serviceData.forEach { sd ->
                        val decoded = app.fieldwatch.domain.AdvPayloadDecoder.decodeService(sd)
                        decoded.forEach { field -> Meta(field.label, field.value) }
                        Meta(
                            serviceDataHeading(sd),
                            sd.dataHex.hexSpaced().ifBlank { "(empty)" },
                            mono = true,
                        )
                    }
                }
            }

            if (device.kind == RadioKind.BLE) {
                val fleets = vm.ui.value.fleets
                val decoded = remember(device.key, device.facts, device.fleetIds) {
                    SignatureFieldDecoder.decodeSighting(device, fleets)
                }
                val mapped = device.fleetIds.mapNotNull { id -> fleets.find { it.id == id && it.decode != null } }
                val hasPayload = device.facts.mfgRecords.isNotEmpty() ||
                    device.manufacturerDataHex.isNotBlank() ||
                    device.facts.serviceData.isNotEmpty()
                if (decoded.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DecodeGlyph(
                            tint = MaterialTheme.colorScheme.primary,
                            size = 16.dp,
                        )
                        Text(
                            "Decoded fields",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val multi = decoded.map { it.fleetId }.distinct().size > 1
                    decoded.forEach { row ->
                        Meta(if (multi) "${row.fleetName} · ${row.label}" else row.label, row.display)
                    }
                } else if (mapped.isNotEmpty()) {
                    val govee = mapped.any { it.id == "fleet-govee" }
                    Text(
                        when {
                            hasPayload && govee ->
                                "Decode fields did not apply to this advertisement (short payload, a different company ID, or a different layout). Govee lights usually only send a name; hygrometers are H5074/H5075/H510x. Raw bytes are below."
                            hasPayload ->
                                "Decode fields did not apply to this advertisement (short payload, a different company ID, or a different layout). Raw bytes are below."
                            govee ->
                                "This signature has a decode map, but this advertisement has no manufacturer or service payload to parse. Many Govee lights only broadcast a name."
                            else ->
                                "This signature has a decode map, but this advertisement has no manufacturer or service payload to parse."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val mfg = facts.mfgRecords.ifEmpty {
                device.manufacturerId?.let {
                    listOf(app.fieldwatch.domain.MfgRecord(it, device.manufacturerDataHex))
                } ?: emptyList()
            }
            if (mfg.isNotEmpty()) {
                StickyHeight(device.key to "mfg") {
                    Section("Maker data inside the ad")
                    mfg.forEach { rec ->
                        val company = RadioDb.company(rec.companyId) ?: "Not in the Bluetooth company list"
                        Meta(
                            "Bluetooth company 0x%04X".format(rec.companyId),
                            "$company\nThis ID is assigned by the Bluetooth SIG and is carried in manufacturer-specific data.",
                        )
                        val decoded = BleAdParser.mfgDecodedFields(rec)
                        decoded.forEach { (k, v) -> Meta(k, v) }
                        if (rec.dataHex.isNotBlank()) {
                            Meta("Raw payload (${rec.dataHex.length / 2} bytes)", rec.dataHex.hexSpaced(), mono = true)
                        }
                    }
                }
            }

            if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
                StickyHeight(device.key to "ies") {
                    Section("Wi-Fi vendor tags")
                    val rows = facts.vendorIes.ifEmpty {
                        device.vendorIeOuis.map { app.fieldwatch.domain.VendorIeRecord(it, -1, "") }
                    }
                    rows.forEach { ie ->
                        val org = RadioDb.vendorForOui24(ie.oui)
                        val type = if (ie.type >= 0) " type %d".format(ie.type) else ""
                        Meta(
                            "Vendor OUI ${ie.oui}$type",
                            buildString {
                                append(org ?: "Unknown IEEE OUI")
                                append(" — extra AP information element, not the SSID.")
                                if (ie.dataHex.isNotBlank()) {
                                    append("\n")
                                    append(ie.dataHex.hexSpaced())
                                }
                            },
                        )
                    }
                }
            }

            StickyHeight(device.key to "session") {
                Section("Session")
                Meta("First seen", fmt.format(Date(device.firstSeen)))
                Meta("Last seen", fmt.format(Date(device.lastSeen)))
                Meta("Hits", device.hitCount.toString())
                Geo.screenCoord(device.latitude, device.longitude, demoMode)?.let { Meta("Last fix", it) }
                if (device.fleetIds.isNotEmpty()) {
                    Meta(
                        "Matched signatures",
                        device.fleetIds.joinToString("\n") { id ->
                            val name = vm.fleetName(id)
                            if (vm.fleetHasDecode(id)) "$name  ⬡" else name
                        },
                    )
                }
                if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
                    Meta("Raw advertisement", device.rawHex.hexSpaced())
                }
            }

            Text("Signal trend", style = MaterialTheme.typography.titleSmall)
            Sparkline(device.rssiHistory, accent, modifier = Modifier.fillMaxWidth().height(56.dp))
            Text("Presence (15 min)", style = MaterialTheme.typography.titleSmall)
            PresenceTrack(device, System.currentTimeMillis(), 15 * 60 * 1000L, accent)
            if (device.kind == RadioKind.BLE) {
                FieldwatchActionButton(
                    onClick = onHunt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.NearMe, null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Hunt")
                }
            } else {
                Text(
                    "Hunt is BLE only. Wi-Fi access points update too slowly on stock Android to walk toward.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            familyHint?.let { hint ->
                StickyHeight(device.key to "family") { FamilyCard(hint) }
            }
            FieldwatchActionButton(
                onClick = onCreateFleet,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.GroupAdd, null)
                Spacer(Modifier.padding(4.dp))
                Text("Create signature from device")
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailShare(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.padding(4.dp))
                Text("Share as text")
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailAiExport(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.padding(4.dp))
                Text("AI Export")
            }
            Text(
                "Opens a paste-ready prompt for a chat: decode this radio, look up OUI/company/UUIDs, and say what it most likely is. Same experimental disclaimer as Settings → AI Export. One device only — not identity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FamilyCard(hint: SignatureFamilyHint) {
    val scheme = MaterialTheme.colorScheme
    val container = when (hint.verdict) {
        FamilyVerdict.STRONG -> scheme.primaryContainer
        FamilyVerdict.POSSIBLE -> Amber.nightIf(LocalNightMode.current).copy(alpha = 0.22f)
        FamilyVerdict.SINGLE, FamilyVerdict.TAGGED -> scheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val onContainer = when (hint.verdict) {
        FamilyVerdict.STRONG -> scheme.onPrimaryContainer
        FamilyVerdict.POSSIBLE, FamilyVerdict.SINGLE, FamilyVerdict.TAGGED -> scheme.onSurface
    }
    val muted = onContainer.copy(alpha = 0.78f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Signature family",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    Text(hint.title, style = MaterialTheme.typography.titleMedium, color = onContainer)
                }
                if (hint.displayCount > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            hint.displayCount.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = onContainer,
                        )
                        RadioKindMark(hint.radioKind, size = 13.dp)
                    }
                }
            }
            hint.ruleLabel?.let { rule ->
                Text(
                    rule,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(hint.body, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@Composable
private fun SignatureNotesCard(notes: List<Pair<String, String>>) {
    if (notes.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Notes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            notes.forEach { (name, note) ->
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ObserverNotesCard(
    notes: String,
    canEdit: Boolean,
    editing: Boolean,
    draft: String,
    onToggleEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saved: Boolean,
) {
    val ink = Cyan.nightIf(LocalNightMode.current)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ink.copy(alpha = 0.18f),
        border = BorderStroke(1.5.dp, ink),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Observer notes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                if (canEdit) {
                    IconButton(onClick = onToggleEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editing) "Hide observer notes" else "Observer notes",
                        )
                    }
                }
            }
            if (!editing) {
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "No observer notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FieldwatchOutlinedField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = "Observer notes",
                    singleLine = false,
                    minLines = 3,
                    supportingText = "${draft.trim().length}/${RadioBookmarks.MAX_NOTES}. ${RadioBookmarks.observerNotesHint()}",
                )
                FieldwatchActionButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = saveEnabled,
                ) {
                    if (saved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text("Saved")
                    } else {
                        Text("Save notes")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraAttentionCard(notes: List<Pair<String, String>>) {
    if (notes.isEmpty()) return
    val warn = Amber.nightIf(LocalNightMode.current)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = warn.copy(alpha = 0.28f),
        border = BorderStroke(1.5.dp, warn),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = warn,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    "Extra attention",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = warn,
                )
            }
            notes.forEach { (name, note) ->
                Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "Pattern match, not identity. Not a safety finding.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuessCard(guess: DeviceExplain.Guess) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "What this looks like",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(guess.headline, style = MaterialTheme.typography.titleMedium)
            Text(guess.because, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Meta(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun vendorLine(device: Sighting): String? {
    val parts = ArrayList<String>(3)
    device.vendor?.let {
        parts += "IEEE board/chip vendor: $it (${device.oui}). This is who owns the MAC prefix, not always the product brand."
    }
    val mfgId = device.facts.mfgRecords.firstOrNull()?.companyId ?: device.manufacturerId
    if (mfgId != null) {
        val company = RadioDb.company(mfgId)
        parts += "Bluetooth company in the ad: ${company ?: "unlisted"} (0x%04X).".format(mfgId)
    }
    return parts.joinToString("\n").ifBlank { null }
}

private fun uuidShort(uuid: String): String {
    val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
    return if (hex.length >= 8 && hex.startsWith("0000")) hex.substring(4, 8) else uuid.take(8)
}

private fun serviceDataHeading(sd: ServiceDataRecord): String {
    val named = RadioDb.serviceUuid(sd.uuid)?.let { " ($it)" } ?: ""
    val frame = eddystoneFrameTag(sd)?.let { " · $it" } ?: ""
    return "Service data ${uuidShort(sd.uuid)}$named$frame"
}

private fun eddystoneFrameTag(sd: ServiceDataRecord): String? {
    val hex = sd.uuid.filter { it.isLetterOrDigit() }.uppercase()
    val short = when {
        hex.length == 4 -> hex
        hex.length >= 8 && hex.startsWith("0000") -> hex.substring(4, 8)
        else -> return null
    }
    if (short != "FEAA") return null
    return when (sd.dataHex.filter { it.isLetterOrDigit() }.uppercase().take(2)) {
        "00" -> "UID"
        "10" -> "URL"
        "20" -> "TLM"
        "30" -> "EID"
        else -> null
    }
}
