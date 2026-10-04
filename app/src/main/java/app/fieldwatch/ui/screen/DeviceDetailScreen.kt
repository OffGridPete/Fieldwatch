package app.fieldwatch.ui.screen

import app.fieldwatch.i18n.codLabel

import app.fieldwatch.i18n.forDisplay

import app.fieldwatch.i18n.appText

import app.fieldwatch.R

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
import app.fieldwatch.domain.Rssi
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
    val currentUi by vm.ui.collectAsStateWithLifecycle()
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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, appText(R.string.device_detail_screen_back)) }
                },
                actions = {
                    IconButton(onClick = { vm.toggleWatchDevice(device) }) {
                        Icon(if (watched) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, appText(R.string.device_detail_screen_watch))
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
                    appText(R.string.device_detail_screen_not_on_the_air_this_is_the),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var nameDraft by androidx.compose.runtime.saveable.rememberSaveable(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var lastSaved by androidx.compose.runtime.saveable.rememberSaveable(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var editingName by androidx.compose.runtime.saveable.rememberSaveable(device.key) { mutableStateOf(false) }
            val draftLabel = RadioBookmarks.clip(nameDraft)
            val nameIsSaved = lastSaved.isNotBlank() && draftLabel == lastSaved
            val canName = RadioBookmarks.canSetCustomName(device)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (lastSaved.isNotBlank()) {
                        Text(
                            appText(R.string.device_detail_screen_custom_name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(lastSaved, style = MaterialTheme.typography.titleLarge)
                        Text(
                            appText(R.string.device_detail_screen_advertised),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            device.name.ifBlank { appText(R.string.device_detail_screen_no_advertised_name) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (device.name.isNotBlank()) {
                        Text(
                            appText(R.string.device_detail_screen_advertised_name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(device.name, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            appText(R.string.device_detail_screen_name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            appText(R.string.device_detail_screen_no_advertised_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canName) {
                    IconButton(onClick = { editingName = !editingName }) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editingName) appText(R.string.device_detail_screen_hide_custom_name) else appText(R.string.device_detail_screen_custom_name),
                        )
                    }
                }
            }
            if (editingName && canName) {
                FieldwatchOutlinedField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(RadioBookmarks.MAX_NAME) },
                    label = appText(R.string.device_detail_screen_custom_name),
                    supportingText = RadioBookmarks.customNameHint(device),
                )
                FieldwatchActionButton(
                    onClick = {
                        vm.saveRadioName(device, nameDraft)
                        nameDraft = draftLabel
                        lastSaved = draftLabel
                        scope.launch {
                            snackbarHostState.showSnackbar(appText(R.string.device_detail_screen_saved_as, draftLabel))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nameDraft.isNotBlank() && !nameIsSaved,
                ) {
                    if (nameIsSaved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text(appText(R.string.device_detail_screen_saved))
                    } else {
                        Text(appText(R.string.device_detail_screen_save_name))
                    }
                }
            }

            var notesDraft by androidx.compose.runtime.saveable.rememberSaveable(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var lastSavedNotes by androidx.compose.runtime.saveable.rememberSaveable(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var editingNotes by androidx.compose.runtime.saveable.rememberSaveable(device.key) { mutableStateOf(false) }
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
                                if (draftNotes.isBlank()) appText(R.string.device_detail_screen_observer_notes_cleared) else appText(R.string.device_detail_screen_observer_notes_saved),
                            )
                        }
                    },
                    saveEnabled = !notesIsSaved,
                    saved = notesIsSaved && lastSavedNotes.isNotBlank(),
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
                            Text(appText(R.string.device_detail_screen_mine), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                appText(R.string.device_detail_screen_while_on_this_radio_stays_listed_and),
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
                                            appText(R.string.device_detail_screen_marked_as_yours_no_beep_while_this),
                                        )
                                    }
                                }
                            },
                        )
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

            StickyHeight(device.key to "identity") {
                Section(appText(R.string.device_detail_screen_identity))
                Meta(
                    appText(R.string.device_detail_screen_radio),
                    if (device.kind == RadioKind.WIFI) {
                        appText(R.string.device_detail_screen_wi_fi_access_point_beaconing_a_network)
                    } else {
                        appText(R.string.device_detail_screen_bluetooth_low_energy_advertiser)
                    },
                )
                Meta(appText(R.string.device_detail_screen_address), DeviceExplain.addressExplain(device))
                vendorLine(device)?.let { Meta(appText(R.string.device_detail_screen_who_made_it), it) }
                    ?: Meta(appText(R.string.device_detail_screen_oui_vendor_prefix), appText(R.string.device_detail_screen_no_ieee_match_randomized_addresses_usually_have, device.oui))
                if (device.hiddenSsid) {
                    Meta(appText(R.string.device_detail_screen_network_name_ssid), appText(R.string.device_detail_screen_hidden_the_ap_is_beaconing_but_not))
                }
            }

            StickyHeight(device.key to "signal") {
                Section(appText(R.string.device_detail_screen_signal))
                if (device.gone) {
                    Meta(appText(R.string.device_detail_screen_how_loud_here_rssi), appText(R.string.device_detail_screen_not_available))
                    Meta(
                        appText(R.string.device_detail_screen_last_heard),
                        buildString {
                            append(fmt.format(Date(device.lastSeen)))
                            Rssi.lastMeasured(device.rssi, device.rssiHistory)?.let {
                                append(appText(R.string.device_detail_screen_at_dbm, it))
                            }
                        },
                    )
                } else {
                    Meta(appText(R.string.device_detail_screen_how_loud_here_rssi), DeviceExplain.rssiExplain(device.rssi))
                    Text(
                        appText(R.string.device_detail_screen_closer_to_0_dbm_is_louder_here),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Meta(
                    appText(R.string.device_detail_screen_heard_range_this_session),
                    Rssi.sessionRange(device.rssiMin, device.rssiMax, device.rssiHistory),
                )
                facts.txPowerDbm?.let {
                    Meta(appText(R.string.device_detail_screen_claimed_transmit_power), appText(R.string.device_detail_screen_dbm_how_loud_it_says_it_transmits, it))
                }
                if (device.channel != 0 || device.frequencyMhz != 0) {
                    Meta(
                        appText(R.string.device_detail_screen_channel_frequency),
                        buildString {
                            if (device.channel != 0) append(appText(R.string.device_detail_screen_channel, device.channel))
                            if (device.frequencyMhz != 0) {
                                if (isNotEmpty()) append("  ·  ")
                                append("${device.frequencyMhz} MHz")
                            }
                            facts.channelWidth?.let { append(appText(R.string.device_detail_screen_wide, it)) }
                        },
                    )
                }
                facts.wifiStandard?.let { Meta(appText(R.string.device_detail_screen_wi_fi_generation), it) }
                if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
                    Meta(
                        appText(R.string.device_detail_screen_center_frequencies),
                        listOfNotNull(
                            facts.centerFreq0?.let { "$it MHz" },
                            facts.centerFreq1?.let { "$it MHz" },
                        ).joinToString("  ·  "),
                    )
                }
            }

            if (device.kind == RadioKind.BLE) {
                StickyHeight(device.key to "ble") {
                Section(appText(R.string.device_detail_screen_bluetooth_advertisement))
                facts.primaryPhy?.let {
                    val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                    Meta(appText(R.string.device_detail_screen_radio_phy), phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(phy) })
                }
                facts.connectable?.let {
                    Meta(
                        appText(R.string.device_detail_screen_connectable),
                        if (it) appText(R.string.device_detail_screen_yes_a_phone_could_open_a_ble)
                        else appText(R.string.device_detail_screen_no_broadcast_only_you_can_hear_it),
                    )
                }
                facts.advertisingIntervalMs?.let {
                    Meta(
                        appText(R.string.device_detail_screen_how_often_it_advertises),
                        appText(R.string.device_detail_screen_0f_ms_between_bursts_smaller_chattier_on).format(it),
                    )
                }
                facts.periodicIntervalMs?.let {
                    Meta(appText(R.string.device_detail_screen_periodic_advertising), "%.0f ms".format(it))
                }
                facts.advFlags?.let { flags ->
                    Meta(appText(R.string.device_detail_screen_discoverability), DeviceExplain.flagsExplain(flags))
                    Meta(appText(R.string.device_detail_screen_flags_raw), "0x%02X".format(flags), mono = true)
                }
                facts.appearance?.let { value ->
                    val name = RadioDb.appearance(value)
                    Meta(
                        appText(R.string.device_detail_screen_what_it_says_it_is_appearance),
                        name?.let { appText(R.string.device_detail_screen_nthe_device_publishes_this_gap_appearance_code, it) }
                            ?: appText(R.string.device_detail_screen_unlisted_appearance_0x_04x).format(value),
                    )
                    Meta(appText(R.string.device_detail_screen_appearance_code), "0x%04X".format(value), mono = true)
                }
                CodDecoder.decodeOrNull(facts.deviceClass)?.let { cod ->
                    Meta(
                        appText(R.string.device_detail_screen_classic_bluetooth_class),
                        buildString {
                            append(codLabel(cod.major))
                            if (cod.minor.isNotBlank()) append(" / ").append(codLabel(cod.minor))
                            append(appText(R.string.device_detail_screen_nthis_is_the_class_of_device_bitfield))
                            if (cod.services.isNotEmpty()) {
                                append(appText(R.string.device_detail_screen_nalso_offers))
                                append(cod.services.joinToString(", ") { codLabel(it) })
                            }
                        },
                    )
                }
                }
            }

            if (device.kind == RadioKind.WIFI) {
                StickyHeight(device.key to "wifi") {
                    Section(appText(R.string.device_detail_screen_wi_fi_access_point))
                    facts.security?.let {
                        Meta(appText(R.string.device_detail_screen_encryption_login), DeviceExplain.wifiSecurityExplain(it))
                        if (it.isNotBlank()) Meta(appText(R.string.device_detail_screen_security_string), it, mono = true)
                    }
                    facts.supportedRates?.let {
                        Meta(appText(R.string.device_detail_screen_supported_rates), appText(R.string.device_detail_screen_mbps_required_basic_rate, it))
                    }
                    facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                        Meta(appText(R.string.device_detail_screen_capability_string), it, mono = true)
                    }
                }
            }

            if (device.serviceUuids.isNotEmpty() || facts.serviceData.isNotEmpty()) {
                StickyHeight(device.key to "services") {
                    if (device.serviceUuids.isNotEmpty()) {
                        Section(appText(R.string.device_detail_screen_services_it_offers))
                        Meta(
                            appText(R.string.device_detail_screen_service_ids),
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
                            sd.dataHex.hexSpaced().ifBlank { appText(R.string.device_detail_screen_empty) },
                            mono = true,
                        )
                    }
                }
            }

            if (device.kind == RadioKind.BLE || device.kind == RadioKind.WIFI) {
                val aircraft = device.payloadAircraft?.trim().orEmpty()
                if (aircraft.isNotEmpty()) Meta(appText(R.string.device_detail_screen_aircraft), aircraft)
                val fleets = currentUi.fleets
                val decoded = remember(device.key, device.facts, device.fleetIds, fleets) {
                    SignatureFieldDecoder.decodeSighting(device, fleets).forDisplay(fleets)
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
                            appText(R.string.device_detail_screen_decoded_fields),
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
                                appText(R.string.device_detail_screen_decode_fields_did_not_apply_to_this)
                            hasPayload ->
                                appText(R.string.device_detail_screen_decode_fields_did_not_apply_to_this_2)
                            govee ->
                                appText(R.string.device_detail_screen_this_signature_has_a_decode_map_but)
                            else ->
                                appText(R.string.device_detail_screen_this_signature_has_a_decode_map_but_2)
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
                    Section(appText(R.string.device_detail_screen_maker_data_inside_the_ad))
                    mfg.forEach { rec ->
                        val company = RadioDb.company(rec.companyId) ?: appText(R.string.device_detail_screen_not_in_the_bluetooth_company_list)
                        Meta(
                            appText(R.string.device_detail_screen_bluetooth_company_0x_04x).format(rec.companyId),
                            appText(R.string.device_detail_screen_nthis_id_is_assigned_by_the_bluetooth, company),
                        )
                        val decoded = BleAdParser.mfgDecodedFields(rec)
                        decoded.forEach { (k, v) -> Meta(k, v) }
                        if (rec.dataHex.isNotBlank()) {
                            Meta(appText(R.string.device_detail_screen_raw_payload_bytes, rec.dataHex.length / 2), rec.dataHex.hexSpaced(), mono = true)
                        }
                    }
                }
            }

            if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
                StickyHeight(device.key to "ies") {
                    Section(appText(R.string.device_detail_screen_wi_fi_vendor_tags))
                    val rows = facts.vendorIes.ifEmpty {
                        device.vendorIeOuis.map { app.fieldwatch.domain.VendorIeRecord(it, -1, "") }
                    }
                    rows.forEach { ie ->
                        val org = RadioDb.vendorForOui24(ie.oui)
                        val type = if (ie.type >= 0) appText(R.string.device_detail_screen_type_d).format(ie.type) else ""
                        Meta(
                            appText(R.string.device_detail_screen_vendor_oui, ie.oui, type),
                            buildString {
                                append(org ?: appText(R.string.device_detail_screen_unknown_ieee_oui))
                                append(appText(R.string.device_detail_screen_extra_ap_information_element_not_the_ssid))
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
                Section(appText(R.string.device_detail_screen_session))
                Meta(appText(R.string.device_detail_screen_first_seen), fmt.format(Date(device.firstSeen)))
                Meta(appText(R.string.device_detail_screen_last_seen), fmt.format(Date(device.lastSeen)))
                Meta(appText(R.string.device_detail_screen_hits), device.hitCount.toString())
                Geo.screenCoord(device.latitude, device.longitude, demoMode)?.let { Meta(appText(R.string.device_detail_screen_last_fix), it) }
                if (device.fleetIds.isNotEmpty()) {
                    Meta(
                        appText(R.string.device_detail_screen_matched_signatures),
                        device.fleetIds.joinToString("\n") { id ->
                            val name = vm.fleetDisplayName(id)
                            if (vm.fleetHasDecode(id)) "$name  ⬡" else name
                        },
                    )
                }
                if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
                    Meta(appText(R.string.device_detail_screen_raw_advertisement), device.rawHex.hexSpaced())
                }
            }

            Text(appText(R.string.device_detail_screen_signal_trend), style = MaterialTheme.typography.titleSmall)
            Sparkline(device.rssiHistory, accent, modifier = Modifier.fillMaxWidth().height(56.dp))
            Text(appText(R.string.device_detail_screen_presence_15_min), style = MaterialTheme.typography.titleSmall)
            PresenceTrack(device, System.currentTimeMillis(), 15 * 60 * 1000L, accent)
            if (device.kind == RadioKind.BLE) {
                FieldwatchActionButton(
                    onClick = onHunt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.NearMe, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(appText(R.string.device_detail_screen_hunt))
                }
            } else {
                Text(
                    appText(R.string.device_detail_screen_hunt_is_ble_only_wi_fi_access),
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
                Text(appText(R.string.device_detail_screen_create_signature_from_device))
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailShare(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.padding(4.dp))
                Text(appText(R.string.device_detail_screen_share_as_text))
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailAiExport(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.padding(4.dp))
                Text(appText(R.string.device_detail_screen_ai_export))
            }
            Text(
                appText(R.string.device_detail_screen_opens_a_paste_ready_prompt_for_a),
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
                        appText(R.string.device_detail_screen_signature_family),
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
                appText(R.string.device_detail_screen_notes),
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
                    appText(R.string.device_detail_screen_observer_notes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                if (canEdit) {
                    IconButton(onClick = onToggleEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editing) appText(R.string.device_detail_screen_hide_observer_notes) else appText(R.string.device_detail_screen_observer_notes),
                        )
                    }
                }
            }
            if (!editing) {
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        appText(R.string.device_detail_screen_no_observer_notes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FieldwatchOutlinedField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = appText(R.string.device_detail_screen_observer_notes),
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
                        Text(appText(R.string.device_detail_screen_saved))
                    } else {
                        Text(appText(R.string.device_detail_screen_save_notes))
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
                    appText(R.string.device_detail_screen_extra_attention),
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
                appText(R.string.device_detail_screen_pattern_match_not_identity_not_a_safety),
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
                appText(R.string.device_detail_screen_what_this_looks_like),
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
        parts += appText(R.string.device_detail_screen_ieee_board_chip_vendor_this_is_who, it, device.oui)
    }
    val mfgId = device.facts.mfgRecords.firstOrNull()?.companyId ?: device.manufacturerId
    if (mfgId != null) {
        val company = RadioDb.company(mfgId)
        parts += appText(R.string.device_detail_screen_bluetooth_company_in_the_ad_0x_04x, company ?: appText(R.string.device_detail_screen_unlisted)).format(mfgId)
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
    return appText(R.string.device_detail_screen_service_data, uuidShort(sd.uuid), named, frame)
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
