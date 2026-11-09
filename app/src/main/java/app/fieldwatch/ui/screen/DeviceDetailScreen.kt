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
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchSwitch
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
import androidx.compose.runtime.LaunchedEffect
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
import app.fieldwatch.domain.AlertVoiceWhat
import app.fieldwatch.domain.CodDecoder
import app.fieldwatch.domain.FamilyVerdict
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.OperatorLoc
import app.fieldwatch.domain.PayloadLocation
import app.fieldwatch.domain.DeviceExplain
import app.fieldwatch.domain.Palette
import app.fieldwatch.domain.RadioDb
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.SpokenName
import app.fieldwatch.domain.SpokenNames
import app.fieldwatch.domain.WatchTarget
import app.fieldwatch.domain.Rssi
import app.fieldwatch.domain.ServiceDataRecord
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureFamilyHint
import app.fieldwatch.domain.SignatureFieldDecoder
import app.fieldwatch.domain.SmartTag
import app.fieldwatch.domain.hexSpaced
import app.fieldwatch.domain.maskedRawBytes
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.VendorIeLabel
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
    spokenNames: List<SpokenName> = emptyList(),
    voiceWhat: AlertVoiceWhat = AlertVoiceWhat.BOTH,
    watchlist: List<WatchTarget> = emptyList(),
) {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val accent = (device.fleetIds.firstOrNull()
        ?.let { Color(Palette.color(vm.fleetColor(it))) }
        ?: rssiColor(device.rssi))
        .nightIf(LocalNightMode.current)
    val facts = device.facts
    val placeStreets by vm.placeStreetNames.collectAsStateWithLifecycle()
    LaunchedEffect(device.key) { vm.refreshPlaceStreets(device) }
    val familyHint by vm.familyHint.collectAsStateWithLifecycle()
    var catalogRequest by remember(device.key) { mutableStateOf(false) }
    LaunchedEffect(device.key, device.name, device.kind, device.fleetIds) {
        catalogRequest = vm.catalogRequestVisible(device)
    }
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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, LanguageText.text("chrome.back")) }
                },
                actions = {
                    IconButton(onClick = { vm.toggleWatchDevice(device) }) {
                        Icon(if (watched) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, LanguageText.text("ui.watch.label"))
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
                    LanguageText.text("ui.not.air.last.detail"),
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
            val spokenNetwork = SpokenNames.networkOf(device.name, device.mac)
            val spokenFleet = SpokenNames.voiceFleet(device.fleetIds, watchlist)
            val spokenRow = SpokenNames.announced(spokenNames, device.fleetIds, spokenNetwork, spokenFleet)
            val individualName = watchlist.firstOrNull { it.deviceKey == device.key }?.label?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals(device.mac, ignoreCase = true) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (lastSaved.isNotBlank()) {
                        Text(
                            LanguageText.text("ui.custom.name"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(lastSaved, style = MaterialTheme.typography.titleLarge)
                        Text(
                            LanguageText.text("ui.advertised.label"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            device.name.ifBlank { LanguageText.text("ui.no.advertised.name") },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (device.name.isNotBlank()) {
                        Text(
                            LanguageText.text("display.advertised"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(device.name, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            LanguageText.text("ui.name.label"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            LanguageText.text("ui.no.advertised.name"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (spokenRow != null && individualName == null) {
                        Text(
                            LanguageText.text("spoken.as", mapOf("name" to spokenRow.label)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                if (canName) {
                    IconButton(onClick = { editingName = !editingName }) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editingName) LanguageText.text("ui.hide.custom.name") else LanguageText.text("ui.custom.name"),
                        )
                    }
                }
            }
            if (editingName && canName) {
                FieldwatchOutlinedField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(RadioBookmarks.MAX_NAME) },
                    label = LanguageText.text("ui.custom.name"),
                    supportingText = RadioBookmarks.customNameHint(device),
                )
                FieldwatchActionButton(
                    onClick = {
                        vm.saveRadioName(device, nameDraft)
                        nameDraft = draftLabel
                        lastSaved = draftLabel
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                LanguageText.text("device.saved", mapOf("name" to draftLabel)),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nameDraft.isNotBlank() && !nameIsSaved,
                ) {
                    if (nameIsSaved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text(LanguageText.text("ui.saved.label"))
                    } else {
                        Text(LanguageText.text("ui.save.name"))
                    }
                }
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
                                if (draftNotes.isBlank()) LanguageText.text("ui.observer.notes.cleared") else LanguageText.text("ui.observer.notes.saved"),
                            )
                        }
                    },
                    saveEnabled = !notesIsSaved,
                    saved = notesIsSaved && lastSavedNotes.isNotBlank(),
                )
            }
            if (spokenNetwork != null && device.fleetIds.isNotEmpty()) {
                SpokenNameCard(
                    deviceKey = device.key,
                    network = spokenNetwork,
                    fleetIds = device.fleetIds.toList(),
                    spokenNames = spokenNames,
                    voiceWhat = voiceWhat,
                    individualName = individualName,
                    fleetName = vm::fleetName,
                    onSave = vm::saveSpokenName,
                    onRemove = vm::removeSpokenName,
                )
            }
            if (canName) {
                var mineOn by remember(device.key) { mutableStateOf(vm.isMine(device.key)) }
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
                            Text(LanguageText.text("ui.mine.label"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                LanguageText.text("ui.while.radio.stays.listed"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FieldwatchSwitch(
                            checked = mineOn,
                            onCheckedChange = { on ->
                                vm.setRadioMine(device, on)
                                mineOn = on
                                if (on && lastSaved.isBlank()) {
                                    val suggest = RadioBookmarks.suggestLabel(
                                        device,
                                        device.fleetIds.map { vm.fleetName(it) },
                                    )
                                    lastSaved = suggest
                                    nameDraft = suggest
                                }
                                if (on) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            LanguageText.text("ui.marked.yours.no.beep"),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
            if (device.kind == RadioKind.WIFI) {
                TrustNetworkControl(device, vm)
            }

            StickyHeight(device.key to "identity") {
                Section(LanguageText.text("ui.identity.label"))
                Meta(
                    LanguageText.text("ui.radio.label"),
                    if (device.kind == RadioKind.WIFI) {
                        LanguageText.text("ui.wi.fi.access.point")
                    } else {
                        LanguageText.text("ui.bluetooth.low.energy.advertiser")
                    },
                )
                Meta(LanguageText.text("ui.address.label"), DeviceExplain.addressExplain(device))
                vendorLine(device)?.let { Meta(LanguageText.text("ui.who.made"), it) }
                    ?: Meta(
                        LanguageText.text("ui.oui.vendor.prefix"),
                        LanguageText.text("device.oui.none", mapOf("oui" to device.oui)),
                    )
                if (device.hiddenSsid) {
                    Meta(LanguageText.text("ui.network.name.ssid"), LanguageText.text("ui.hidden.ap.beaconing.but"))
                }
            }

            StickyHeight(device.key to "signal") {
                Section(LanguageText.text("ui.signal.label"))
                if (device.gone) {
                    Meta(LanguageText.text("ui.how.loud.here.rssi"), LanguageText.text("ui.not.available"))
                    Meta(
                        LanguageText.text("ui.last.heard.2"),
                        buildString {
                            append(fmt.format(Date(device.lastSeen)))
                            Rssi.lastMeasured(device.rssi, device.rssiHistory)?.let {
                                append(" ")
                                append(LanguageText.text("device.at.dbm", mapOf("n" to it.toString())))
                            }
                        },
                    )
                } else {
                    Meta(LanguageText.text("ui.how.loud.here.rssi"), DeviceExplain.rssiExplain(device.rssi))
                    Text(
                        LanguageText.text("ui.closer.0.dbm.louder"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Meta(
                    LanguageText.text("ui.heard.range.session"),
                    Rssi.sessionRange(device.rssiMin, device.rssiMax, device.rssiHistory),
                )
                facts.txPowerDbm?.let {
                    Meta(
                        LanguageText.text("ui.claimed.transmit.power"),
                        LanguageText.text("device.tx", mapOf("n" to it.toString())),
                    )
                }
                if (device.channel != 0 || device.frequencyMhz != 0) {
                    Meta(
                        LanguageText.text("ui.channel.frequency"),
                        buildString {
                            if (device.channel != 0) {
                                append(LanguageText.text("device.channel", mapOf("n" to device.channel.toString())))
                            }
                            if (device.frequencyMhz != 0) {
                                if (isNotEmpty()) append("  ·  ")
                                append("${device.frequencyMhz} MHz")
                            }
                            facts.channelWidth?.let {
                                append("  ·  ")
                                append(LanguageText.text("device.wide", mapOf("width" to it)))
                            }
                        },
                    )
                }
                facts.wifiStandard?.let { Meta(LanguageText.text("ui.wi.fi.generation"), it) }
                if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
                    Meta(
                        LanguageText.text("ui.center.frequencies"),
                        listOfNotNull(
                            facts.centerFreq0?.let { "$it MHz" },
                            facts.centerFreq1?.let { "$it MHz" },
                        ).joinToString("  ·  "),
                    )
                }
            }

            if (device.kind == RadioKind.BLE) {
                StickyHeight(device.key to "ble") {
                Section(LanguageText.text("ui.bluetooth.advertisement"))
                facts.primaryPhy?.let {
                    val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                    Meta(LanguageText.text("ui.radio.phy"), phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(phy) })
                }
                facts.connectable?.let {
                    Meta(
                        LanguageText.text("ui.connectable.label"),
                        if (it) LanguageText.text("ui.yes.phone.could.open")
                        else LanguageText.text("ui.no.broadcast.only.can"),
                    )
                }
                facts.advertisingIntervalMs?.let {
                    Meta(
                        LanguageText.text("ui.how.often.advertises"),
                        LanguageText.text(
                            "device.burst",
                            mapOf("n" to "%.0f".format(Locale.US, it)),
                        ),
                    )
                }
                facts.periodicIntervalMs?.let {
                    Meta(LanguageText.text("ui.periodic.advertising"), "%.0f ms".format(it))
                }
                facts.advFlags?.let { flags ->
                    Meta(LanguageText.text("ui.discoverability.label"), DeviceExplain.flagsExplain(flags))
                    Meta(LanguageText.text("ui.flags.raw"), "0x%02X".format(flags), mono = true)
                }
                facts.appearance?.let { value ->
                    val name = RadioDb.appearance(value)
                    Meta(
                        LanguageText.text("ui.what.says.appearance"),
                        name?.let { "$it\n" + LanguageText.text("device.appearance") }
                            ?: LanguageText.text(
                                "device.appearance.unlisted",
                                mapOf("code" to "0x%04X".format(Locale.US, value)),
                            ),
                    )
                    Meta(LanguageText.text("ui.appearance.code"), "0x%04X".format(value), mono = true)
                }
                CodDecoder.decodeOrNull(facts.deviceClass)?.let { cod ->
                    Meta(
                        LanguageText.text("ui.classic.bluetooth.class"),
                        buildString {
                            append(cod.major)
                            if (cod.minor.isNotBlank()) append(" / ").append(cod.minor)
                            append(("\n" + LanguageText.text("ui.class.device.bitfield.used")))
                            if (cod.services.isNotEmpty()) {
                                append(("\n" + LanguageText.text("ui.also.offers") + " "))
                                append(cod.services.joinToString(", "))
                            }
                        },
                    )
                }
                }
            }

            if (device.kind == RadioKind.WIFI) {
                StickyHeight(device.key to "wifi") {
                    Section(LanguageText.text("ui.wi.fi.access.point.2"))
                    facts.security?.let {
                        Meta(LanguageText.text("ui.encryption.login"), DeviceExplain.wifiSecurityExplain(it))
                        if (it.isNotBlank()) Meta(LanguageText.text("ui.security.string"), it, mono = true)
                    }
                    facts.supportedRates?.let {
                        Meta(
                            LanguageText.text("ui.supported.rates"),
                            "$it " + LanguageText.text("device.rates.note"),
                        )
                    }
                    facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                        Meta(LanguageText.text("ui.capability.string"), it, mono = true)
                    }
                }
            }

            if (device.serviceUuids.isNotEmpty() || facts.serviceData.isNotEmpty()) {
                StickyHeight(device.key to "services") {
                    if (device.serviceUuids.isNotEmpty()) {
                        Section(LanguageText.text("ui.services.offers"))
                        Meta(
                            LanguageText.text("ui.service.ids"),
                            device.serviceUuids.joinToString("\n") { uuid ->
                                DeviceExplain.uuidGloss(uuid)?.let { "$uuid  ·  $it" } ?: uuid
                            },
                            mono = true,
                        )
                    }
                    facts.serviceData.forEach { sd ->
                        val decoded = app.fieldwatch.domain.AdvPayloadDecoder.decodeService(sd)
                        decoded.forEach { field -> Meta(field.label, field.value) }
                        if (SmartTag.isService(sd.uuid)) {
                            SmartTag.parse(sd.dataHex)?.let { tag ->
                                tag.state?.let { Meta(LanguageText.text("device.state"), it) }
                                Meta(LanguageText.text("device.aging"), tag.aging.toString())
                                Meta(LanguageText.text("device.battery"), tag.battery)
                                Meta("UWB", tag.uwb)
                                val idText = if (demoMode) "masked" else tag.privacyId.hexSpaced()
                                Meta(
                                    LanguageText.text("device.privacy"),
                                    idText + "\n" + LanguageText.text("device.not.serial"),
                                    mono = !demoMode,
                                )
                            }
                        }
                        Meta(
                            serviceDataHeading(sd),
                            if (demoMode && sd.dataHex.isNotBlank()) maskedRawBytes(sd.dataHex, true)
                            else sd.dataHex.hexSpaced().ifBlank { LanguageText.text("device.empty") },
                            mono = true,
                        )
                    }
                }
            }

            if (device.kind == RadioKind.BLE || device.kind == RadioKind.WIFI) {
                val aircraft = device.payloadAircraft?.trim().orEmpty()
                if (aircraft.isNotEmpty()) Meta(LanguageText.text("ui.aircraft.label"), aircraft)
                val phoneLat = vm.ui.value.phoneLat
                val phoneLon = vm.ui.value.phoneLon
                if (PayloadLocation.validCoord(device.payloadLat, device.payloadLon)) {
                    Geo.screenCoord(device.payloadLat, device.payloadLon, demoMode)?.let {
                        Meta(LanguageText.text("ui.aircraft.position"), it)
                    }
                    Geo.screenGrid(device.payloadLat, device.payloadLon, demoMode)?.let {
                        Meta(LanguageText.text("ui.grid"), it)
                    }
                    Geo.liveRange(phoneLat, phoneLon, device.payloadLat, device.payloadLon, demoMode)?.let { range ->
                        Text(range, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (PayloadLocation.validCoord(device.payloadOpLat, device.payloadOpLon)) {
                    Geo.screenCoord(device.payloadOpLat, device.payloadOpLon, demoMode)?.let {
                        Meta(OperatorLoc.label(device.payloadOpKind), it)
                    }
                    Geo.screenGrid(device.payloadOpLat, device.payloadOpLon, demoMode)?.let {
                        Meta(LanguageText.text("ui.grid"), it)
                    }
                    Geo.liveRange(phoneLat, phoneLon, device.payloadOpLat, device.payloadOpLon, demoMode)?.let { range ->
                        Text(range, style = MaterialTheme.typography.bodyMedium)
                    }
                }
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
                            LanguageText.text("ui.decoded.fields"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val multi = decoded.map { it.fleetId }.distinct().size > 1
                    decoded.forEach { row ->
                        Meta(if (multi) "${row.fleetName} · ${row.label}" else row.label, row.display)
                        if (row.note.isNotBlank()) {
                            Text(
                                row.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (mapped.isNotEmpty()) {
                    val govee = mapped.any { it.id == "fleet-govee" }
                    Text(
                        when {
                            hasPayload && govee ->
                                LanguageText.text("ui.decode.fields.did.not")
                            hasPayload ->
                                LanguageText.text("ui.decode.fields.did.not.2")
                            govee ->
                                LanguageText.text("ui.signature.has.decode.map")
                            else ->
                                LanguageText.text("ui.signature.has.decode.map.2")
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
                    Section(LanguageText.text("ui.maker.data.inside.ad"))
                    mfg.forEach { rec ->
                        val company = RadioDb.company(rec.companyId) ?: LanguageText.text("ui.not.bluetooth.company.list")
                        Meta(
                            LanguageText.text(
                                "device.company",
                                mapOf("id" to "0x%04X".format(Locale.US, rec.companyId)),
                            ),
                            "$company\n" + LanguageText.text("device.company.about"),
                        )
                        val decoded = BleAdParser.mfgDecodedFields(rec)
                        decoded.forEach { (k, v) -> Meta(k, v) }
                        if (rec.dataHex.isNotBlank()) {
                            Meta(
                                LanguageText.text(
                                    "device.raw",
                                    mapOf("n" to (rec.dataHex.length / 2).toString()),
                                ),
                                if (demoMode) maskedRawBytes(rec.dataHex, false) else rec.dataHex.hexSpaced(),
                                mono = true,
                            )
                        }
                    }
                }
            }

            if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
                StickyHeight(device.key to "ies") {
                    Section(LanguageText.text("ui.wi.fi.vendor.tags"))
                    val rows = facts.vendorIes.ifEmpty {
                        device.vendorIeOuis.map { app.fieldwatch.domain.VendorIeRecord(it, -1, "") }
                    }
                    rows.forEach { ie ->
                        val org = RadioDb.vendorForOui24(ie.oui)
                        val type = if (ie.type >= 0) {
                            " " + LanguageText.text("device.vendor.type", mapOf("n" to ie.type.toString()))
                        } else {
                            ""
                        }
                        Meta(
                            LanguageText.text("device.vendor", mapOf("oui" to ie.oui)) + type,
                            buildString {
                                append(
                                    VendorIeLabel.lead(
                                        ie,
                                        org,
                                        LanguageText.text("ui.unknown.ieee.oui"),
                                        LanguageText.text("ui.extra.ap.information.element"),
                                    ),
                                )
                                if (ie.dataHex.isNotBlank()) {
                                    append("\n")
                                    append(if (demoMode) maskedRawBytes(ie.dataHex, true) else ie.dataHex.hexSpaced())
                                }
                            },
                        )
                    }
                }
            }

            StickyHeight(device.key to "session") {
                Section(LanguageText.text("ui.session.label"))
                Meta(LanguageText.text("ui.first.seen"), fmt.format(Date(device.firstSeen)))
                Meta(LanguageText.text("ui.last.seen"), fmt.format(Date(device.lastSeen)))
                Meta(LanguageText.text("ui.hits.label"), device.hitCount.toString())
                vm.placeLines(device, placeStreets).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
                Geo.screenCoord(device.latitude, device.longitude, demoMode)?.let { Meta(LanguageText.text("ui.last.fix"), it) }
                Geo.screenGrid(device.latitude, device.longitude, demoMode)?.let { Meta(LanguageText.text("ui.grid"), it) }
                if (device.fleetIds.isNotEmpty()) {
                    Meta(
                        LanguageText.text("ui.matched.signatures"),
                        device.fleetIds.joinToString("\n") { id ->
                            val name = vm.fleetName(id)
                            if (vm.fleetHasDecode(id)) "$name  ⬡" else name
                        },
                    )
                }
                if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
                    Meta(
                        LanguageText.text("ui.raw.advertisement"),
                        if (demoMode) maskedRawBytes(device.rawHex, true) else device.rawHex.hexSpaced(),
                    )
                }
            }

            Text(LanguageText.text("ui.signal.trend"), style = MaterialTheme.typography.titleSmall)
            Sparkline(device.rssiHistory, accent, modifier = Modifier.fillMaxWidth().height(56.dp))
            Text(LanguageText.text("ui.presence.15.min"), style = MaterialTheme.typography.titleSmall)
            PresenceTrack(device, System.currentTimeMillis(), 15 * 60 * 1000L, accent)
            if (device.kind == RadioKind.BLE) {
                FieldwatchActionButton(
                    onClick = onHunt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.NearMe, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(LanguageText.text("ui.hunt.label"))
                }
            } else {
                Text(
                    LanguageText.text("ui.hunt.ble.only.wi"),
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
                Text(LanguageText.text("ui.create.signature.device"))
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailShare(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.padding(4.dp))
                Text(LanguageText.text("ui.share.text"))
            }
            if (catalogRequest) {
                FieldwatchActionButton(
                    onClick = { vm.openCatalogRequest(device) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Public, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(LanguageText.text("ui.catalog.request"))
                }
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailAiExport(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.padding(4.dp))
                Text(LanguageText.text("ui.ai.export"))
            }
            Text(
                LanguageText.text("ui.opens.paste.ready.prompt"),
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
                        LanguageText.text("ui.signature.family"),
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
                LanguageText.text("ui.notes.label"),
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
                    LanguageText.text("ui.observer.notes"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                if (canEdit) {
                    IconButton(onClick = onToggleEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editing) LanguageText.text("ui.hide.observer.notes") else LanguageText.text("ui.observer.notes"),
                        )
                    }
                }
            }
            if (!editing) {
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        LanguageText.text("ui.no.observer.notes"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FieldwatchOutlinedField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = LanguageText.text("ui.observer.notes"),
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
                        Text(LanguageText.text("ui.saved.label"))
                    } else {
                        Text(LanguageText.text("ui.save.notes"))
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
                    LanguageText.text("chrome.extra.attention"),
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
                LanguageText.text("ui.pattern.match.not.identity"),
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
                LanguageText.text("ui.what.looks.like"),
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
        parts += LanguageText.text(
            "device.vendor.line",
            mapOf("vendor" to it, "oui" to device.oui),
        )
    }
    val mfgId = device.facts.mfgRecords.firstOrNull()?.companyId ?: device.manufacturerId
    if (mfgId != null) {
        val company = RadioDb.company(mfgId)
        parts += LanguageText.text(
            "device.company.ad",
            mapOf(
                "company" to (company ?: LanguageText.text("device.unlisted")),
                "id" to "0x%04X".format(Locale.US, mfgId),
            ),
        )
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
    return LanguageText.text("device.service.data", mapOf("id" to uuidShort(sd.uuid))) + named + frame
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
