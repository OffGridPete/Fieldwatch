package app.fieldwatch.data

import android.content.Context
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.FilterEngine
import app.fieldwatch.domain.FilterPreset
import app.fieldwatch.domain.FilterState
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.PersistedConfig
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureCandidates
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SettingsExchange
import app.fieldwatch.domain.SettingsImportResult
import app.fieldwatch.domain.SettingsPack
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.SignatureImportResult
import app.fieldwatch.domain.WatchTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ConfigStore(context: Context) {
    private val file = File(context.filesDir, "config.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val _config = MutableStateFlow(seed())
    val config: StateFlow<PersistedConfig> = _config.asStateFlow()

    val fleets: List<Fleet> get() = _config.value.fleets
    val filter: FilterState get() = _config.value.filter
    val settings: AppSettings get() = _config.value.settings
    val presets: List<FilterPreset> get() = _config.value.presets
    val watchlist: List<WatchTarget> get() = _config.value.watchlist

    suspend fun load() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!file.exists()) {
                val seeded = seed()
                file.writeText(json.encodeToString(seeded))
                _config.value = seeded
                return@withContext
            }
            runCatching {
                json.decodeFromString<PersistedConfig>(file.readText())
            }.onSuccess { loaded ->
                if (loaded.fleets.isEmpty()) {
                    val seeded = seed()
                    file.writeText(json.encodeToString(seeded))
                    _config.value = seeded
                } else {
                    val patched = patchBuiltIn(loaded)
                    _config.value = patched
                    if (patched != loaded) {
                        file.writeText(json.encodeToString(patched))
                    }
                }
            }.onFailure {
                val seeded = seed()
                file.writeText(json.encodeToString(seeded))
                _config.value = seeded
            }
        }
    }

    suspend fun update(transform: (PersistedConfig) -> PersistedConfig) = mutex.withLock {
        val next = transform(_config.value)
        _config.value = next
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "config.tmp")
            tmp.writeText(json.encodeToString(next))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    suspend fun restoreDefaults() {
        val accepted = _config.value.settings.disclaimerAccepted
        val rev = _config.value.settings.disclaimerRev
        update {
            val fresh = seed()
            fresh.copy(
                settings = fresh.settings.copy(
                    disclaimerAccepted = accepted,
                    disclaimerRev = rev,
                ),
            )
        }
    }

    suspend fun importFleets(incoming: List<Fleet>): SignatureImportResult = mutex.withLock {
        val (merged, result) = SignatureExchange.merge(_config.value.fleets, incoming)
        if (result.error != null || (result.added == 0 && result.merged == 0)) {
            return@withLock result
        }
        val next = _config.value.copy(fleets = merged)
        _config.value = next
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "config.tmp")
            tmp.writeText(json.encodeToString(next))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
        result
    }

    suspend fun importSettings(pack: SettingsPack): SettingsImportResult {
        var result = SettingsImportResult()
        update { local ->
            val (next, applied) = SettingsExchange.apply(local, pack)
            result = applied
            if (applied.error != null) local else next
        }
        return result
    }

    private fun patchBuiltIn(cfg: PersistedConfig): PersistedConfig {
        val catalog = DefaultCatalog.fleets().associateBy { it.id }
        val next = cfg.fleets.map { fleet ->
            val stock = catalog[fleet.id] ?: return@map fleet
            if (!fleet.builtIn) return@map fleet
            val have = fleet.rules.map { ruleKey(it) }.toSet()
            val missing = stock.rules.filter { ruleKey(it) !in have }
            val renamed = when {
                fleet.id == "fleet-unknown" && fleet.name == "Unknown Fleet" -> stock.name
                fleet.id == "fleet-seos" && fleet.name == "Seos" -> stock.name
                else -> fleet.name
            }
            if (missing.isEmpty() && renamed == fleet.name) fleet
            else fleet.copy(name = renamed, rules = fleet.rules + missing)
        }
        var presets = cfg.presets.map { preset ->
            if (preset.id == "named" && preset.name == "Named only") {
                preset.copy(name = "Signatures only")
            } else {
                preset
            }
        }
        var fleets = next
        var version = cfg.version
        var filter = cfg.filter
        val hiddenPresetIds = cfg.hiddenPresetIds
        if (version < CATALOG_V2) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V2.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V2
        }
        if (version < CATALOG_V3) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V3.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V3
        }
        if (version < CATALOG_V4) {
            val s = cfg.settings
            fleets = fleets.map { fleet ->
                if (fleet.id !in POLICY_FLEET_IDS) return@map fleet
                fleet.copy(
                    rules = fleet.rules.map { rule ->
                        val on = when (rule.kind) {
                            RuleKind.NAME_CONTAINS, RuleKind.NAME_GLOB -> s.detectSsidKeywords
                            RuleKind.OUI, RuleKind.MAC_PREFIX -> s.detectKnownOuis
                            RuleKind.VENDOR_IE_OUI -> s.detectVendorIes
                            RuleKind.SERVICE_UUID, RuleKind.MANUFACTURER_ID, RuleKind.MANUFACTURER_DATA ->
                                if (fleet.id == "fleet-raven") s.detectBleRaven else true
                            else -> true
                        }
                        if (rule.enabled == on) rule else rule.copy(enabled = on)
                    },
                )
            }
            version = CATALOG_V4
        }
        if (version < CATALOG_V5) {
            if (cfg.settings.arrivalsOnly && !filter.arrivalsOnly) {
                filter = filter.copy(arrivalsOnly = true)
            }
            version = CATALOG_V5
        }
        if (version < CATALOG_V6) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V6.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V6
        }
        if (version < CATALOG_V7) {
            fleets = fleets.map { fleet ->
                if (fleet.id == "fleet-hobby-ble" && fleet.builtIn && !fleet.enabled) {
                    fleet.copy(enabled = true)
                } else {
                    fleet
                }
            }
            version = CATALOG_V7
        }
        if (version < CATALOG_V8) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) return@map fleet
                val enable = if (fleet.id == "fleet-axon") true else fleet.enabled
                val note = if (fleet.attentionNote.isBlank() && stock.attentionNote.isNotBlank()) {
                    stock.attentionNote
                } else {
                    fleet.attentionNote
                }
                if (enable == fleet.enabled && note == fleet.attentionNote) fleet
                else fleet.copy(enabled = enable, attentionNote = note)
            }
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V8.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V8
        }
        if (version < CATALOG_V9) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V9.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V9
        }
        if (version < CATALOG_V10) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V10.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V10
        }
        if (version < CATALOG_V11) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) return@map fleet
                if (fleet.colorIndex == stock.colorIndex) fleet
                else fleet.copy(colorIndex = stock.colorIndex)
            }
            version = CATALOG_V11
        }
        if (version < CATALOG_V12) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id]
                when {
                    fleet.id == "fleet-unknown" && fleet.builtIn && stock != null ->
                        fleet.copy(
                            enabled = false,
                            minPeers = 0,
                            clusterByOui = false,
                            sequentialMac = false,
                            notes = stock.notes,
                            rules = stock.rules,
                        )
                    fleet.id == "fleet-airtag" && fleet.builtIn ->
                        fleet.copy(
                            rules = fleet.rules.filterNot {
                                it.kind == RuleKind.MANUFACTURER_DATA &&
                                    it.companyId == 0x004C &&
                                    it.dataPrefixHex.filter { ch -> ch.isLetterOrDigit() }
                                        .equals("07", ignoreCase = true)
                            },
                            notes = stock?.notes ?: fleet.notes,
                        )
                    else -> fleet
                }
            }
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V12.mapNotNull { catalog[it] }.filter { it.id !in have }
            if (extras.isNotEmpty()) fleets = fleets + extras
            version = CATALOG_V12
        }
        if (version < CATALOG_V13) {
            fleets = fleets.sortedBy { it.name.lowercase() }
            version = CATALOG_V13
        }
        if (version < CATALOG_V14) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V14.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V14
        }
        if (version < CATALOG_V15) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V15.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V15
        }
        if (version < CATALOG_V16) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V16.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V16
        }
        if (version < CATALOG_V17) {
            fleets = fleets.map { fleet ->
                if (fleet.builtIn && !fleet.enabled) fleet.copy(enabled = true) else fleet
            }
            version = CATALOG_V17
        }
        var watchlist = cfg.watchlist
        if (version < CATALOG_V18) {
            val have = watchlist.mapNotNull { it.fleetId }.toSet()
            val extras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in have }
            if (extras.isNotEmpty()) watchlist = watchlist + extras
            version = CATALOG_V18
        }
        var settings = cfg.settings
        if (version < CATALOG_V19) {
            if (!settings.tagLocation) settings = settings.copy(tagLocation = true)
            version = CATALOG_V19
        }
        if (version < CATALOG_V20) {
            if (!settings.keepScreenOn || !settings.onlineLookup) {
                settings = settings.copy(keepScreenOn = true, onlineLookup = true)
            }
            version = CATALOG_V20
        }
        if (version < CATALOG_V21) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V21.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V21
        }
        if (version < CATALOG_V22) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V22.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V22
        }
        if (version < CATALOG_V23) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V23.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V23
        }
        if (version < CATALOG_V24) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V24.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V24
        }
        if (version < CATALOG_V25) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V25.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V25
        }
        if (version < CATALOG_V26) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) fleet else fleet.copy(kind = stock.kind)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V26
        }
        if (version < CATALOG_V27) {
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            if (filter.classIncludeActive() && filter.namedOnly) {
                filter = filter.copy(namedOnly = false)
            }
            version = CATALOG_V27
        }
        if (version < CATALOG_V28) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) fleet else fleet.copy(kind = stock.kind)
            }
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V28.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V28
        }
        if (version < CATALOG_V29) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) fleet else fleet.copy(kind = stock.kind)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V29
        }
        if (version < CATALOG_V30) {
            fleets = fleets.map { fleet ->
                if (fleet.enabled) fleet else fleet.copy(enabled = true)
            }
            if (filter.useFleetFilter && !filter.excludeSignatures && filter.fleetIds.isNotEmpty()) {
                filter = filter.copy(
                    includeSignatures = true,
                    includeFleetIds = filter.fleetIds,
                    useFleetFilter = false,
                    fleetIds = emptySet(),
                )
            }
            version = CATALOG_V30
        }
        if (version < CATALOG_V31) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V31.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V31
        }
        if (version < CATALOG_V32) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V32.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V32
        }
        if (version < CATALOG_V33) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V33.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V33
        }
        if (version < CATALOG_V34) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) fleet else fleet.copy(kind = stock.kind)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V34
        }
        if (version < CATALOG_V35) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V35.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V35
        }
        if (version < CATALOG_V36) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V36.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V36
        }
        if (version < CATALOG_V37) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V37.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V37
        }
        if (version < CATALOG_V38) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V38.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) return@map fleet
                val note = if (fleet.attentionNote.isBlank() && stock.attentionNote.isNotBlank()) {
                    stock.attentionNote
                } else {
                    fleet.attentionNote
                }
                if (note == fleet.attentionNote) fleet else fleet.copy(attentionNote = note)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V38
        }
        if (version < CATALOG_V39) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V39.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V39
        }
        if (version < CATALOG_V40) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V40.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val refresh = setOf("fleet-tesla", "fleet-mbux", "fleet-audi-mmi", "fleet-gm-hotspot")
            fleets = fleets.map { fleet ->
                if (fleet.id !in refresh || !fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                val keys = fleet.rules.map { ruleKey(it) }.toSet()
                val extraRules = stock.rules.filter { ruleKey(it) !in keys }
                fleet.copy(rules = fleet.rules + extraRules, notes = stock.notes)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V40
        }
        if (version < CATALOG_V41) {
            fleets = fleets.map { fleet ->
                if (fleet.id != "fleet-tesla" && fleet.id != "fleet-ibeacon") return@map fleet
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                val keys = fleet.rules.map { ruleKey(it) }.toSet()
                val extraRules = stock.rules.filter { ruleKey(it) !in keys }
                fleet.copy(rules = fleet.rules + extraRules, notes = stock.notes)
            }
            version = CATALOG_V41
        }
        if (version < CATALOG_V42) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) return@map fleet
                val color = if (fleet.id == "fleet-gopro") stock.colorIndex else fleet.colorIndex
                fleet.copy(kind = stock.kind, colorIndex = color)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V42
        }
        if (version < CATALOG_V43) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V43.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            fleets = fleets.map { fleet ->
                if (fleet.id != "fleet-dji" || !fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(notes = stock.notes)
            }
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V43
        }
        if (version < CATALOG_V44) {
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V44
        }
        if (version < CATALOG_V45) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                if (fleet.id != "fleet-airtag" && fleet.id != "fleet-apple-device") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(notes = stock.notes)
            }
            version = CATALOG_V45
        }
        if (version < CATALOG_V46) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                if (fleet.id != "fleet-unifi-ap" && fleet.id != "fleet-unifi") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(kind = stock.kind, colorIndex = stock.colorIndex, notes = stock.notes)
            }
            version = CATALOG_V46
        }
        if (version < CATALOG_V47) {
            val stockPresets = FilterEngine().defaultPresets(fleets)
            val custom = presets.filterNot { it.isBuiltIn() }
            presets = stockPresets + custom
            version = CATALOG_V47
        }
        if (version < CATALOG_V48) {
            val leIds = setOf(
                "fleet-axon",
                "fleet-watchguard",
                "fleet-cradlepoint",
                "fleet-airlink",
                "fleet-compex",
                "fleet-novatel",
                "fleet-utility-inc",
            )
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id !in leIds) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(kind = stock.kind, colorIndex = stock.colorIndex)
            }
            if (SignatureClass.BODYWORN in filter.classes) {
                filter = filter.copy(classes = filter.classes + SignatureClass.LAW_ENFORCEMENT)
            }
            version = CATALOG_V48
        }
        if (version < CATALOG_V49) {
            val campusApIds = setOf(
                "fleet-meraki",
                "fleet-cisco",
                "fleet-aruba",
                "fleet-ruckus",
                "fleet-fortinet",
                "fleet-mist",
                "fleet-sophos",
                "fleet-extreme",
                "fleet-edgecore",
                "fleet-watchguard-ap",
                "fleet-mojo",
            )
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id !in campusApIds) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(kind = stock.kind, colorIndex = stock.colorIndex, notes = stock.notes)
            }
            version = CATALOG_V49
        }
        if (version < CATALOG_V50) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V50.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            val mergeIds = setOf(
                "fleet-glinet",
                "fleet-toyota",
                "fleet-volkswagen",
                "fleet-porsche",
                "fleet-motive",
                "fleet-hp",
                "fleet-att",
            )
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn || fleet.id !in mergeIds) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                val keys = fleet.rules.map { ruleKey(it) }.toSet()
                val extraRules = stock.rules.filter { ruleKey(it) !in keys }
                fleet.copy(rules = fleet.rules + extraRules, notes = stock.notes)
            }
            version = CATALOG_V50
        }
        if (version < CATALOG_V51) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V51.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V51
        }
        if (version < CATALOG_V52) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V52.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V52
        }
        if (version < CATALOG_V53) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V53.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V53
        }
        if (version < CATALOG_V54) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V54.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V54
        }
        if (version < CATALOG_V55) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V55.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V55
        }
        if (version < CATALOG_V56) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn) return@map fleet
                if (stock.decode != null && fleet.decode == null) {
                    fleet.copy(decode = stock.decode, notes = stock.notes)
                } else {
                    fleet
                }
            }
            version = CATALOG_V56
        }
        if (version < CATALOG_V57) {
            fleets = fleets.map { fleet ->
                val stock = catalog[fleet.id] ?: return@map fleet
                if (!fleet.builtIn || stock.decode == null) return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V57
        }
        if (version < CATALOG_V58) {
            fleets = fleets.map { fleet ->
                if (fleet.kind != SignatureClass.BODYWORN) return@map fleet
                fleet.copy(kind = SignatureClass.WEARABLE)
            }
            fun foldBodyworn(state: FilterState): FilterState {
                if (SignatureClass.BODYWORN !in state.classes) return state
                return state.copy(classes = state.classes - SignatureClass.BODYWORN + SignatureClass.WEARABLE)
            }
            filter = foldBodyworn(filter)
            presets = presets.map { preset ->
                val next = foldBodyworn(preset.filter)
                if (next === preset.filter) preset else preset.copy(filter = next)
            }
            version = CATALOG_V58
        }
        if (version < CATALOG_V59) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V59.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V59
        }
        if (version < CATALOG_V60) {
            val have = fleets.map { it.id }.toSet()
            val extras = ADDED_IN_V60.mapNotNull { catalog[it] }.filter { it.id !in have }
            fleets = (fleets + extras).sortedBy { it.name.lowercase() }
            version = CATALOG_V60
        }
        if (version < CATALOG_V61) {
            fleets = fleets.map { fleet ->
                if (fleet.id != "fleet-govee" || !fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V61
        }
        if (version < CATALOG_V62) {
            fleets = fleets.map { fleet ->
                if (fleet.id != "fleet-nest-weave" || !fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                fleet.copy(decode = stock.decode, notes = stock.notes)
            }
            version = CATALOG_V62
        }
        if (version < CATALOG_V63) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                if (fleet.id != "fleet-fs-ext-battery" && fleet.id != "fleet-flock-cameras") return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                val note = if (fleet.attentionNote.isBlank() && stock.attentionNote.isNotBlank()) {
                    stock.attentionNote
                } else {
                    fleet.attentionNote
                }
                fleet.copy(
                    kind = stock.kind,
                    colorIndex = stock.colorIndex,
                    notes = stock.notes,
                    attentionNote = note,
                )
            }
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V63
        }
        if (version < CATALOG_V64) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                if (stock.attentionNote.isBlank()) return@map fleet
                val note = if (fleet.attentionNote.isBlank()) stock.attentionNote else fleet.attentionNote
                if (note == fleet.attentionNote) fleet else fleet.copy(attentionNote = note)
            }
            val watched = watchlist.mapNotNull { it.fleetId }.toSet()
            val watchExtras = DefaultCatalog.defaultWatchlist().filter { it.fleetId !in watched }
            if (watchExtras.isNotEmpty()) watchlist = watchlist + watchExtras
            version = CATALOG_V64
        }
        if (version < CATALOG_V65) {
            fleets = fleets.map { fleet ->
                if (fleet.builtIn) return@map fleet
                val rules = fleet.rules.map { rule ->
                    if (!rule.enabled) return@map rule
                    if (rule.kind != RuleKind.NAME_GLOB && rule.kind != RuleKind.NAME_CONTAINS) return@map rule
                    if (SignatureCandidates.isOverbroadCreateName(rule.text)) rule.copy(enabled = false)
                    else rule
                }
                if (rules == fleet.rules) fleet else fleet.copy(rules = rules)
            }
            version = CATALOG_V65
        }
        if (version < CATALOG_V66) {
            fleets = fleets.map { fleet ->
                if (fleet.builtIn) return@map fleet
                val rules = fleet.rules.map { rule ->
                    if (!rule.enabled || rule.kind != RuleKind.SERVICE_UUID) return@map rule
                    val hex = rule.text.filter { it.isLetterOrDigit() }.uppercase()
                    if (hex in GENERIC_GATT_UUIDS) rule.copy(enabled = false) else rule
                }
                if (rules == fleet.rules) fleet else fleet.copy(rules = rules)
            }
            version = CATALOG_V66
        }
        if (version < CATALOG_V67) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                if (stock.notes == fleet.notes) fleet else fleet.copy(notes = stock.notes)
            }
            version = CATALOG_V67
        }
        if (version < CATALOG_V68) {
            fleets = fleets.map { fleet ->
                if (!fleet.builtIn) return@map fleet
                val stock = catalog[fleet.id] ?: return@map fleet
                if (stock.notes == fleet.notes) fleet else fleet.copy(notes = stock.notes)
            }
            version = CATALOG_V68
        }
        presets = presets.filterNot { it.isBuiltIn() && it.id in hiddenPresetIds }
            .distinctBy { it.id }
        val fleetsChanged = fleets != cfg.fleets
        val presetsChanged = presets != cfg.presets
        val versionChanged = version != cfg.version
        val filterChanged = filter != cfg.filter
        val watchChanged = watchlist != cfg.watchlist
        val settingsChanged = settings != cfg.settings
        val hiddenChanged = hiddenPresetIds != cfg.hiddenPresetIds
        return when {
            !fleetsChanged && !presetsChanged && !versionChanged && !filterChanged &&
                !watchChanged && !settingsChanged && !hiddenChanged -> cfg
            else -> cfg.copy(
                version = version,
                fleets = fleets,
                presets = presets,
                filter = filter,
                watchlist = watchlist,
                settings = settings,
                hiddenPresetIds = hiddenPresetIds,
            )
        }
    }

    private fun ruleKey(rule: MatchRule): String =
        listOf(rule.kind.name, rule.text.uppercase(), rule.companyId, rule.dataPrefixHex.uppercase(), rule.radio?.name.orEmpty())
            .joinToString("|")

    private fun seed(): PersistedConfig {
        val fleets = DefaultCatalog.fleets()
        return PersistedConfig(
            version = CATALOG_V68,
            fleets = fleets,
            filter = FilterState(),
            presets = FilterEngine().defaultPresets(fleets),
            watchlist = DefaultCatalog.defaultWatchlist(),
            settings = AppSettings(),
        )
    }

    companion object {
        private const val CATALOG_V2 = 2
        private const val CATALOG_V3 = 3
        private const val CATALOG_V4 = 4
        private const val CATALOG_V5 = 5
        private const val CATALOG_V6 = 6
        private const val CATALOG_V7 = 7
        private const val CATALOG_V8 = 8
        private const val CATALOG_V9 = 9
        private const val CATALOG_V10 = 10
        private const val CATALOG_V11 = 11
        private const val CATALOG_V12 = 12
        private const val CATALOG_V13 = 13
        private const val CATALOG_V14 = 14
        private const val CATALOG_V15 = 15
        private const val CATALOG_V16 = 16
        private const val CATALOG_V17 = 17
        private const val CATALOG_V18 = 18
        private const val CATALOG_V19 = 19
        private const val CATALOG_V20 = 20
        private const val CATALOG_V21 = 21
        private const val CATALOG_V22 = 22
        private const val CATALOG_V23 = 23
        private const val CATALOG_V24 = 24
        private const val CATALOG_V25 = 25
        private const val CATALOG_V26 = 26
        private const val CATALOG_V27 = 27
        private const val CATALOG_V28 = 28
        private const val CATALOG_V29 = 29
        private const val CATALOG_V30 = 30
        private const val CATALOG_V31 = 31
        private const val CATALOG_V32 = 32
        private const val CATALOG_V33 = 33
        private const val CATALOG_V34 = 34
        private const val CATALOG_V35 = 35
        private const val CATALOG_V36 = 36
        private const val CATALOG_V37 = 37
        private const val CATALOG_V38 = 38
        private const val CATALOG_V39 = 39
        private const val CATALOG_V40 = 40
        private const val CATALOG_V41 = 41
        private const val CATALOG_V42 = 42
        private const val CATALOG_V43 = 43
        private const val CATALOG_V44 = 44
        private const val CATALOG_V45 = 45
        private const val CATALOG_V46 = 46
        private const val CATALOG_V47 = 47
        private const val CATALOG_V48 = 48
        private const val CATALOG_V49 = 49
        private const val CATALOG_V50 = 50
        private const val CATALOG_V51 = 51
        private const val CATALOG_V52 = 52
        private const val CATALOG_V53 = 53
        private const val CATALOG_V54 = 54
        private const val CATALOG_V55 = 55
        private const val CATALOG_V56 = 56
        private const val CATALOG_V57 = 57
        private const val CATALOG_V58 = 58
        private const val CATALOG_V59 = 59
        private const val CATALOG_V60 = 60
        private const val CATALOG_V61 = 61
        private const val CATALOG_V62 = 62
        private const val CATALOG_V63 = 63
        private const val CATALOG_V64 = 64
        private const val CATALOG_V65 = 65
        private const val CATALOG_V66 = 66
        private const val CATALOG_V67 = 67
        private const val CATALOG_V68 = 68
        private val GENERIC_GATT_UUIDS = setOf("180A", "180D", "180F")
        private val POLICY_FLEET_IDS = setOf(
            "fleet-flock-cameras",
            "fleet-raven",
            "fleet-fs-ext-battery",
            "fleet-unknown",
            "fleet-penguin",
            "fleet-pigvision",
        )
        private val ADDED_IN_V2 = listOf(
            "fleet-chipolo",
            "fleet-pebblebee",
            "fleet-verkada",
            "fleet-vigilant",
            "fleet-eufy",
            "fleet-wyze",
            "fleet-ring",
            "fleet-arlo",
            "fleet-nest",
            "fleet-tapo",
            "fleet-reolink",
            "fleet-hikvision",
            "fleet-dahua",
        )
        private val ADDED_IN_V3 = listOf(
            "fleet-meshtastic",
            "fleet-helium",
            "fleet-genetec",
            "fleet-rekor",
            "fleet-axon",
            "fleet-avigilon",
            "fleet-axis",
            "fleet-unifi",
        )
        private val ADDED_IN_V6 = listOf(
            "fleet-hobby-ble",
        )
        private val ADDED_IN_V8 = listOf(
            "fleet-watchguard",
            "fleet-meta-glasses",
            "fleet-snap-spectacles",
        )
        private val ADDED_IN_V9 = listOf(
            "fleet-hak5-pineapple",
            "fleet-flipper",
            "fleet-pwnagotchi",
            "fleet-marauder",
        )
        private val ADDED_IN_V10 = listOf(
            "fleet-porkchop",
        )
        private val ADDED_IN_V12 = listOf(
            "fleet-apple-device",
            "fleet-apple-audio",
            "fleet-microsoft",
        )
        private val ADDED_IN_V14 = listOf(
            "fleet-tesla",
            "fleet-google",
            "fleet-sony",
            "fleet-bose",
            "fleet-garmin",
            "fleet-amazon",
            "fleet-fitbit",
            "fleet-oura",
            "fleet-logitech",
            "fleet-jbl",
            "fleet-sonos",
            "fleet-gopro",
            "fleet-dji",
        )
        private val ADDED_IN_V15 = listOf(
            "fleet-netgear",
            "fleet-tplink",
            "fleet-asus",
            "fleet-linksys",
            "fleet-eero",
            "fleet-google-wifi",
            "fleet-dlink",
            "fleet-belkin",
            "fleet-xfinity",
            "fleet-spectrum",
            "fleet-att",
            "fleet-verizon",
            "fleet-starlink",
            "fleet-meraki",
            "fleet-glinet",
        )
        private val ADDED_IN_V16 = listOf(
            "fleet-unifi-ap",
        )
        private val ADDED_IN_V21 = listOf(
            "fleet-nest-thermostat",
            "fleet-ecobee",
            "fleet-sensi",
            "fleet-honeywell-home",
        )
        private val ADDED_IN_V22 = listOf(
            "fleet-nest-weave",
        )
        private val ADDED_IN_V23 = listOf(
            "fleet-haiku",
            "fleet-tuya",
            "fleet-seos",
            "fleet-myq",
            "fleet-chevrolet",
            "fleet-rivian",
            "fleet-govee",
            "fleet-hp",
            "fleet-mbux",
            "fleet-motive",
        )
        private val ADDED_IN_V24 = listOf(
            "fleet-august",
            "fleet-schlage",
            "fleet-nuki",
            "fleet-salto",
            "fleet-dormakaba",
            "fleet-lockly",
            "fleet-kevo",
            "fleet-master-lock",
            "fleet-igloohome",
            "fleet-tedee",
            "fleet-paxton",
            "fleet-kwikset",
        )
        private val ADDED_IN_V25 = listOf(
            "fleet-ibeacon",
            "fleet-minew",
            "fleet-estimote",
            "fleet-kontakt",
        )
        private val ADDED_IN_V28 = listOf(
            "fleet-goodyear",
            "fleet-schrader",
            "fleet-pacific-tpms",
            "fleet-huf",
            "fleet-fobo",
            "fleet-ruuvi",
            "fleet-bluemaestro",
            "fleet-sensorpush",
            "fleet-samsara",
        )
        private val ADDED_IN_V31 = listOf(
            "fleet-pokemon-go-plus",
            "fleet-hatch",
            "fleet-bhyve",
            "fleet-fieldy",
            "fleet-plaud",
            "fleet-retail-led-sign",
            "fleet-esl",
        )
        private val ADDED_IN_V32 = listOf(
            "fleet-unifi-protect",
            "fleet-tesla-tstpms",
            "fleet-radiacode",
            "fleet-lg-webos",
            "fleet-nespresso",
            "fleet-epson",
            "fleet-shokz",
        )
        private val ADDED_IN_V33 = listOf(
            "fleet-remote-id",
            "fleet-skydio",
            "fleet-autel",
            "fleet-parrot",
            "fleet-hoverair",
            "fleet-cradlepoint",
            "fleet-airlink",
        )
        private val ADDED_IN_V35 = listOf(
            "fleet-cisco",
            "fleet-aruba",
            "fleet-ruckus",
            "fleet-fortinet",
            "fleet-mikrotik",
            "fleet-engenius",
            "fleet-zyxel",
            "fleet-peplink",
            "fleet-openwrt",
            "fleet-arris",
        )
        private val ADDED_IN_V36 = listOf(
            "fleet-mist",
            "fleet-tmobile",
            "fleet-humax",
            "fleet-sagemcom",
            "fleet-arcadyan",
            "fleet-askey",
            "fleet-calix",
            "fleet-nokia",
            "fleet-airties",
            "fleet-tenda",
            "fleet-sercomm",
            "fleet-luxul",
            "fleet-sophos",
            "fleet-aumovio",
            "fleet-centurylink",
            "fleet-gm-hotspot",
            "fleet-audi-mmi",
        )
        private val ADDED_IN_V37 = listOf(
            "fleet-extreme",
            "fleet-adtran",
            "fleet-cambium",
            "fleet-trendnet",
            "fleet-cudy",
            "fleet-snapav",
            "fleet-vantiva",
            "fleet-hitron",
            "fleet-actiontec",
            "fleet-buffalo",
            "fleet-grandstream",
            "fleet-edgecore",
            "fleet-watchguard-ap",
            "fleet-mojo",
            "fleet-winegard",
            "fleet-inseego",
            "fleet-synology",
        )
        private val ADDED_IN_V38 = listOf(
            "fleet-compex",
            "fleet-novatel",
            "fleet-utility-inc",
        )
        private val ADDED_IN_V39 = listOf(
            "fleet-fast-pair",
        )
        private val ADDED_IN_V40 = listOf(
            "fleet-ford",
            "fleet-honda",
            "fleet-hyundai",
            "fleet-toyota",
            "fleet-nissan",
            "fleet-subaru",
            "fleet-bmw",
            "fleet-volkswagen",
            "fleet-porsche",
            "fleet-jlr",
            "fleet-byd",
        )
        private val ADDED_IN_V43 = listOf(
            "fleet-osmo",
            "fleet-insta360",
        )
        private val ADDED_IN_V50 = listOf(
            "fleet-ruijie",
            "fleet-dwnet",
            "fleet-wavlink",
            "fleet-peoplenet",
            "fleet-uconnect",
            "fleet-carplay",
        )
        private val ADDED_IN_V51 = listOf(
            "fleet-roku",
        )
        private val ADDED_IN_V52 = listOf(
            "fleet-franklin",
        )
        private val ADDED_IN_V53 = listOf(
            "fleet-samsung-appliance",
            "fleet-ecowater",
        )
        private val ADDED_IN_V54 = listOf(
            "fleet-carlink",
        )
        private val ADDED_IN_V55 = listOf(
            "fleet-target-atrius",
        )
        private val ADDED_IN_V59 = listOf(
            "fleet-phone-hotspot",
            "fleet-huawei",
            "fleet-plume",
        )
        private val ADDED_IN_V60 = listOf(
            "fleet-honeywell-xenon-hc",
            "fleet-omron",
            "fleet-withings",
            "fleet-dexcom",
        )
    }
}
