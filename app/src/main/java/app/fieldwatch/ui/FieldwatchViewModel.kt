package app.fieldwatch.ui

import app.fieldwatch.i18n.displayName
import app.fieldwatch.i18n.displayAttentionNotes
import app.fieldwatch.i18n.displaySignatureNotes
import app.fieldwatch.i18n.CatalogText

import app.fieldwatch.i18n.appText

import app.fieldwatch.R

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.lifecycle.viewModelScope
import app.fieldwatch.BuildConfig
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.domain.AircraftTrail
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.attentionNotes
import app.fieldwatch.domain.signatureNotes
import app.fieldwatch.domain.detectionPolicy
import app.fieldwatch.data.CatalogRemote
import app.fieldwatch.data.DebriefPdf
import app.fieldwatch.data.PathTiles
import app.fieldwatch.data.PlaceLookup
import app.fieldwatch.domain.DeviceDetailPrompt
import app.fieldwatch.domain.DeviceDetailText
import app.fieldwatch.domain.DebriefDoc
import app.fieldwatch.domain.DebriefPlaces
import app.fieldwatch.domain.DebriefPrompt
import app.fieldwatch.domain.DebriefReport
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.DISCLAIMER_REV
import app.fieldwatch.domain.disclaimerOk
import app.fieldwatch.domain.FilterEngine
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.GeoExport
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.LogExportKind
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.SitExport
import app.fieldwatch.domain.ClassOutline
import app.fieldwatch.domain.CoTravel
import app.fieldwatch.domain.FilterPreset
import app.fieldwatch.domain.FilterState
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.Hunt
import app.fieldwatch.domain.HuntCue
import app.fieldwatch.domain.FamilyVerdict
import app.fieldwatch.domain.LogRadio
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RssiSample
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureCandidate
import app.fieldwatch.domain.SignatureCandidates
import app.fieldwatch.domain.SignatureFamilyHint
import app.fieldwatch.domain.CandidateReport

import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureEngine
import app.fieldwatch.domain.SignatureListSort
import app.fieldwatch.domain.SettingsExchange
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.ListLine
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.PairingFlood
import app.fieldwatch.domain.ListSort
import app.fieldwatch.domain.StrengthSort
import app.fieldwatch.domain.ViewMode
import app.fieldwatch.domain.WatchTarget
import app.fieldwatch.domain.DebriefWindow
import app.fieldwatch.domain.Sit
import app.fieldwatch.domain.SitDiff
import app.fieldwatch.domain.SitDiffPrompt
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.domain.SitUi
import app.fieldwatch.radio.RadioPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class CandidatesUi(
    val loading: Boolean = false,
    val report: CandidateReport? = null,
    val error: String? = null,
)

private data class FamilyLogSnap(
    val radios: List<LogRadio> = emptyList(),
    val loaded: Boolean = false,
)

enum class ExportJob(val title: Int, val pausesLogging: Boolean = false) {
    LOG(R.string.fieldwatch_app_ui_export, true),
    SIT(R.string.reports_screen_sit_export),
    COMPARE(R.string.fieldwatch_app_ui_sit_compare),
    COMPARE_PDF(R.string.fieldwatch_app_ui_sit_compare_pdf),
    COMPARE_AI(R.string.fieldwatch_app_ui_sit_compare_ai_export),
    DEBRIEF(R.string.fieldwatch_app_ui_debrief),
    DEBRIEF_PDF(R.string.fieldwatch_app_ui_debrief_pdf),
    AI(R.string.fieldwatch_app_ui_ai_export),
    CATALOG(R.string.reports_screen_catalog),
}

data class ExportUi(
    val job: ExportJob = ExportJob.LOG,
    val active: Boolean = false,
    val progress: Float = 0f,
    /** True: spinning wait (Debrief / AI Export). False: determinate bar (log). */
    val spinner: Boolean = false,
    val message: String = "",
    val share: Intent? = null,
    val shareTitle: String = appText(R.string.fieldwatch_view_model_export_fieldwatch_logs),
    val error: String? = null,
    val errorTitle: String? = null,
    val cleared: Boolean = false,
    val saved: Boolean = false,
    val noticeTitle: String? = null,
    val noticeMessage: String? = null,
    /** Shown after the user dismisses [noticeTitle]. Catalog import skip-decode only. */
    val followUpTitle: String? = null,
    val followUpMessage: String? = null,
)

data class FieldwatchUi(
    val devices: List<Sighting> = emptyList(),
    val filtered: List<Sighting> = emptyList(),
    val fleets: List<Fleet> = emptyList(),
    val filter: FilterState = FilterState(),
    val presets: List<FilterPreset> = emptyList(),
    val watchlist: List<WatchTarget> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val selected: Sighting? = null,
    val draftFleet: Fleet? = null,
    val permissionsOk: Boolean = false,
    val scanning: Boolean = false,
    val wifiNow: Int = 0,
    val bleNow: Int = 0,
    val namedNow: Int = 0,
    val logLines: Long = 0,
    val throttleHint: String = "",
    val hiddenKnown: Int = 0,
    val arrivalsLearning: Boolean = false,
    val displayPaused: Boolean = false,
    val operatorSpanM: Double = 0.0,
    val takStatus: TakFeedStatus = TakFeedStatus(),
    val sit: SitUi = SitUi(),
    val catalogVersion: Int = 0,
)

class FieldwatchViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as FieldwatchApp
    private val filters = FilterEngine()
    private val signatures = SignatureEngine()
    private val selectedKey = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow(savedState.get<String>("fleetDraft")?.let {
        runCatching { Json.decodeFromString<Fleet>(it) }.getOrNull()
    })
    private var draftFromCandidates: Boolean
        get() = savedState["draftFromCandidates"] ?: false
        set(value) { savedState["draftFromCandidates"] = value }

    private fun setDraft(fleet: Fleet?) {
        // Restore the editor's existence after process recreation; editable fields have their own savers.
        savedState["fleetDraft"] = fleet?.let { Json.encodeToString(it) }
        draft.value = fleet
    }
    private val _export = MutableStateFlow(ExportUi())
    val export: StateFlow<ExportUi> = _export
    private val _candidates = MutableStateFlow(CandidatesUi())
    val candidates: StateFlow<CandidatesUi> = _candidates
    private val _logExportKind = MutableStateFlow(LogExportKind.LOG_CSV)
    val logExportKind: StateFlow<LogExportKind> = _logExportKind
    private val _logExportRadios = MutableStateFlow(LogExportRadios.BOTH)
    val logExportRadios: StateFlow<LogExportRadios> = _logExportRadios
    private val _sitExportKind = MutableStateFlow(LogExportKind.LOG_CSV)
    val sitExportKind: StateFlow<LogExportKind> = _sitExportKind
    private val _sitExportRadios = MutableStateFlow(LogExportRadios.BOTH)
    val sitExportRadios: StateFlow<LogExportRadios> = _sitExportRadios
    private val _sitPath = MutableStateFlow<SitPathPlot.Model?>(null)
    val sitPath: StateFlow<SitPathPlot.Model?> = _sitPath
    private val _pathTiles = MutableStateFlow<List<PathTiles.Tile>>(emptyList())
    val pathTiles: StateFlow<List<PathTiles.Tile>> = _pathTiles
    private val _pathAircraftTiles = MutableStateFlow<List<List<PathTiles.Tile>>>(emptyList())
    val pathAircraftTiles: StateFlow<List<List<PathTiles.Tile>>> = _pathAircraftTiles
    @Volatile private var pathRadios: List<Sighting> = emptyList()
    private val _liveFocus = MutableStateFlow(0)
    val liveFocus: StateFlow<Int> = _liveFocus
    private val flashUntil = HashMap<String, Long>()
    private val _flashKeys = MutableStateFlow<Set<String>>(emptySet())
    val flashKeys: StateFlow<Set<String>> = _flashKeys
    private val _beepSnap = MutableStateFlow(BeepSnap())
    val beepSnap: StateFlow<BeepSnap> = _beepSnap
    private val lastAlertAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _alertedKeys = MutableStateFlow<Set<String>>(emptySet())
    val alertedKeys: StateFlow<Set<String>> = _alertedKeys
    private val clock = MutableStateFlow(System.currentTimeMillis())
    private val displayPaused = MutableStateFlow(false)
    private val heldSelected = MutableStateFlow<Sighting?>(null)
    private val familyLog = MutableStateFlow(FamilyLogSnap())
    val familyHint: StateFlow<SignatureFamilyHint?> = combine(
        selectedKey,
        heldSelected,
        app.devices.devices,
        familyLog,
        app.config.config,
    ) { key, held, live, log, config ->
        if (key == null) return@combine null
        val device = live.firstOrNull { it.key == key } ?: held?.takeIf { it.key == key }
            ?: return@combine null
        val hint = SignatureCandidates.assessFamily(device, live, log.radios, config.fleets)
        if (!log.loaded && hint.verdict == FamilyVerdict.SINGLE) return@combine null
        hint
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val huntKey = MutableStateFlow<String?>(null)
    private val huntStartedAt = MutableStateFlow(0L)
    private val huntPeakRssi = MutableStateFlow(-127)
    private val huntSamples = MutableStateFlow<List<RssiSample>>(emptyList())
    private val _outlineOpenClasses = MutableStateFlow<Set<String>>(emptySet())
    val outlineOpenClasses: StateFlow<Set<String>> = _outlineOpenClasses
    private val _outlineOpenSigs = MutableStateFlow<Set<String>>(emptySet())
    val outlineOpenSigs: StateFlow<Set<String>> = _outlineOpenSigs
    private val _catalogOpenClasses = MutableStateFlow<Set<String>>(emptySet())
    val catalogOpenClasses: StateFlow<Set<String>> = _catalogOpenClasses
    @Volatile private var frozenUi: FieldwatchUi? = null

    val floodNotice: StateFlow<PairingFlood.Notice?> = combine(
        app.pairingFlood.notice,
        app.wifiFlood.notice,
    ) { ble, wifi -> ble ?: wifi }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val floodHide: StateFlow<PairingFlood.FloodHide> = combine(
        app.pairingFlood.hide,
        app.wifiFlood.hide,
    ) { ble, wifi ->
        PairingFlood.FloodHide(
            episodeOn = ble.episodeOn || wifi.episodeOn,
            keys = if (wifi.keys.isEmpty()) ble.keys else if (ble.keys.isEmpty()) wifi.keys else ble.keys + wifi.keys,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PairingFlood.FloodHide())

    private val liveUi: StateFlow<FieldwatchUi> = combine(
        combine(app.devices.devices, app.devices.stats, app.config.config) { devices, stats, config ->
            Triple(devices, stats, config)
        },
        combine(selectedKey, draft, app.arrivals, clock) { sel, fleetDraft, arr, now ->
            arrayOf(sel, fleetDraft, arr, now)
        },
        lastAlertAt,
        app.tak.status,
        floodHide,
    ) { tripleA, quad, alerts, takStatus, hide ->
        val (devices, stats, config) = tripleA
        val sel = quad[0] as String?
        val fleetDraft = quad[1] as Fleet?
        val arr = quad[2] as app.fieldwatch.ArrivalsState
        val now = quad[3] as Long
        val labeled = devices
        val fleetNames = config.fleets.associate { it.id to it.name }
        val watchNames = config.watchlist.mapNotNull { row ->
            val key = row.deviceKey ?: return@mapNotNull null
            val label = row.label.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            key to label
        }.toMap()
        val windowMs = config.settings.averageWindowSec.coerceIn(10, 180) * 1000L
        val persistMs = maxOf(
            config.settings.staleSec.coerceAtLeast(15) * 1000L,
            config.settings.decaySec.coerceAtLeast(0) * 1000L,
        )
        val arrivalsOn = config.filter.arrivalsOnly && arr.active
        val learning = arrivalsOn && now <= arr.learningUntil
        var hiddenKnown = 0
        val moveCtx = if (config.filter.movingWithYou) {
            CoTravel.Ctx.of(app.operatorPathCopy())
        } else {
            CoTravel.Ctx.None
        }
        val classById = config.fleets.associate { it.id to it.kind }
        val hiddenFlood = hide.keys
        val filtered = labeled.filter { device ->
            if (hiddenFlood.isNotEmpty() && device.key in hiddenFlood) return@filter false
            if (!filters.pass(
                    device,
                    config.filter,
                    moveCtx,
                    now,
                    classById,
                    watchNames.keys,
                    RadioBookmarks.watchedFleetIds(config.watchlist),
                    RadioBookmarks.alertDeviceKeys(config.watchlist),
                    RadioBookmarks.mineKeys(config.watchlist),
                )
            ) {
                return@filter false
            }
            val heardAgo = now - device.lastSeen
            val inWindow = when (config.settings.viewMode) {
                ViewMode.TIMELINE -> heardAgo <= 15 * 60_000L
                else -> heardAgo <= persistMs || !device.gone
            }
            if (!inWindow) return@filter false
            if (!arrivalsOn) return@filter true
            val absorbed = device.key in arr.knownKeys ||
                (learning && device.kind == RadioKind.WIFI)
            if (absorbed) {
                hiddenKnown++
                return@filter false
            }
            heardAgo <= persistMs || !device.gone
        }.sortedWith(
            when (config.settings.listSort) {
                ListSort.STRENGTH ->
                    compareByDescending { it.sortRssi(config.settings.strengthSort, windowMs, now) }
                ListSort.NEWEST ->
                    compareByDescending<Sighting> { it.lastSeen }
                        .thenByDescending { it.sortRssi(config.settings.strengthSort, windowMs, now) }
                ListSort.NEWEST_ALERT ->
                    compareByDescending<Sighting> { alerts[it.key] ?: 0L }
                        .thenByDescending { it.lastSeen }
                ListSort.FIRST_SEEN ->
                    compareByDescending<Sighting> { it.firstSeen }
                        .thenByDescending { it.lastSeen }
                ListSort.ARRIVAL ->
                    compareBy<Sighting> { it.firstSeen }.thenBy { it.mac }
                ListSort.NAME ->
                    compareBy<Sighting> { d ->
                        val names = d.fleetIds.map { fleetNames[it] ?: it }
                        d.listLineText(config.settings.listTitleLine, names, watchNames[d.key]).lowercase()
                    }.thenBy { it.mac }
                ListSort.SIGNATURES ->
                    compareByDescending<Sighting> { it.fleetIds.isNotEmpty() }
                        .thenByDescending { it.sortRssi(config.settings.strengthSort, windowMs, now) }
            },
        )
        FieldwatchUi(
            devices = labeled,
            filtered = filtered,
            fleets = config.fleets,
            filter = config.filter,
            presets = config.presets,
            watchlist = config.watchlist,
            settings = config.settings,
            selected = labeled.firstOrNull { it.key == sel },
            draftFleet = fleetDraft,
            permissionsOk = RadioPermissions.granted(app),
            scanning = stats.scanning,
            wifiNow = stats.wifiNow,
            bleNow = stats.bleNow,
            namedNow = stats.namedNow,
            logLines = stats.logLines,
            throttleHint = stats.throttleHint,
            hiddenKnown = hiddenKnown,
            arrivalsLearning = learning,
            operatorSpanM = if (moveCtx.ready || config.filter.movingWithYou) {
                moveCtx.pathLengthM
            } else {
                app.operatorPathLengthM()
            },
            takStatus = takStatus,
            catalogVersion = config.version,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FieldwatchUi(
                settings = app.config.settings,
                catalogVersion = app.config.config.value.version,
            ),
        )

    val ui: StateFlow<FieldwatchUi> = combine(liveUi, displayPaused, selectedKey, heldSelected, app.sits.ui) { live, paused, selKey, held, sit ->
        if (!paused) {
            frozenUi = null
            val selected = live.selected ?: held?.takeIf { selKey != null && it.key == selKey }?.let { snap ->
                if (snap.gone) snap else snap.copy(gone = true)
            }
            live.copy(displayPaused = false, selected = selected, sit = sit)
        } else {
            val hold = frozenUi ?: live
            frozenUi = hold
            val selected = held?.takeIf { selKey == null || it.key == selKey }
                ?: selKey?.let { key ->
                    hold.filtered.firstOrNull { it.key == key }
                        ?: hold.devices.firstOrNull { it.key == key }
                }
            live.copy(
                displayPaused = true,
                devices = hold.devices,
                filtered = hold.filtered,
                selected = selected,
                sit = sit,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FieldwatchUi(settings = app.config.settings),
    )

    val hunt: StateFlow<HuntUi> = combine(
        combine(huntKey, huntStartedAt, huntPeakRssi, huntSamples) { key, started, peak, samples ->
            arrayOf(key, started, peak, samples)
        },
        app.devices.devices,
        clock,
    ) { bits, devices, now ->
        val key = bits[0] as String?
        val started = bits[1] as Long
        val peak = bits[2] as Int
        @Suppress("UNCHECKED_CAST")
        val samples = bits[3] as List<RssiSample>
        if (key == null) return@combine HuntUi()
        val device = devices.firstOrNull { it.key == key }
        HuntUi(
            active = true,
            device = device,
            title = device?.listTitle() ?: appText(R.string.fieldwatch_view_model_hunt),
            cue = Hunt.cue(samples, now, device?.lastSeen, device == null && started > 0L),
            peakRssi = peak,
            samples = samples,
            lastSeen = device?.lastSeen ?: 0L,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HuntUi())

    init {
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val now = System.currentTimeMillis()
                clock.value = now
                val liveKeys = app.devices.devices.value.mapTo(HashSet()) { it.key }
                app.pairingFlood.tick(now)
                app.pairingFlood.prune(liveKeys)
                app.wifiFlood.prune(liveKeys)
                app.sits.noteFloods(app.pairingFlood.bursts())
                app.sits.noteFloods(app.wifiFlood.bursts())
            }
        }
        viewModelScope.launch {
            combine(selectedKey, app.devices.devices) { key, devices ->
                key to key?.let { k -> devices.firstOrNull { it.key == k } }
            }.collect { (key, device) ->
                when {
                    key == null -> heldSelected.value = null
                    device != null -> heldSelected.value = device
                }
            }
        }
        viewModelScope.launch {
            app.sits.ui.collect { refreshSitPath() }
        }
        viewModelScope.launch {
            selectedKey.collect { key ->
                if (key == null) return@collect
                runCatching { app.logs.readRadios() }
                    .onSuccess { radios -> familyLog.value = FamilyLogSnap(radios, loaded = true) }
                    .onFailure { familyLog.value = familyLog.value.copy(loaded = true) }
            }
        }
        viewModelScope.launch {
            combine(app.devices.devices, huntKey) { devices, key ->
                key to devices.firstOrNull { it.key == key }
            }.collect { (key, device) ->
                if (key == null || device == null) return@collect
                val last = huntSamples.value.lastOrNull()
                if (last != null && device.lastSeen <= last.at) return@collect
                val sample = RssiSample(device.lastSeen, device.rssi)
                huntSamples.update { (it + sample).takeLast(120) }
                if (device.rssi > huntPeakRssi.value) huntPeakRssi.value = device.rssi
            }
        }
        viewModelScope.launch {
            app.alerter.flashes.collect { key ->
                revealOutlineFor(key)
                lastAlertAt.update { it + (key to System.currentTimeMillis()) }
                _alertedKeys.update { it + key }
                val end = System.currentTimeMillis() + FLASH_MS
                flashUntil[key] = end
                _flashKeys.value = flashUntil.keys.toSet()
                if (app.config.settings.snapToBeep) {
                    _beepSnap.update { it.copy(seq = it.seq + 1, key = key) }
                }
                launch {
                    delay(FLASH_MS)
                    if (flashUntil[key] == end) {
                        flashUntil.remove(key)
                        _flashKeys.value = flashUntil.keys.toSet()
                    }
                }
            }
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch { app.config.update { it } }
    }

    fun startScan() {
        if (!app.config.settings.disclaimerOk()) return
        if (RadioPermissions.granted(app)) app.startScanning()
    }

    fun acceptDisclaimer() {
        viewModelScope.launch {
            app.config.update {
                it.copy(
                    settings = it.settings.copy(
                        disclaimerAccepted = true,
                        disclaimerRev = DISCLAIMER_REV,
                    ),
                )
            }
            startScan()
        }
    }

    fun dismissPairingFlood() {
        if (app.pairingFlood.notice.value != null) app.pairingFlood.dismiss()
        else app.wifiFlood.dismiss()
    }

    fun setHideBurst(on: Boolean) {
        if (app.pairingFlood.notice.value != null) app.pairingFlood.setHideBurst(on)
        else app.wifiFlood.setHideBurst(on)
    }

    fun hidePairingFlood() {
        if (app.pairingFlood.notice.value != null) {
            app.pairingFlood.setHideBurst(true)
            app.pairingFlood.dismiss()
        } else {
            app.wifiFlood.setHideBurst(true)
            app.wifiFlood.dismiss()
        }
    }

    fun clearHiddenFlood() {
        app.pairingFlood.clearHidden()
        app.wifiFlood.clearHidden()
    }

    fun dismissLiveTour() {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(liveTourDone = true))
            }
        }
    }

    fun showLiveTour(then: () -> Unit = {}) {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(liveTourDone = false))
            }
            then()
        }
    }

    fun stopScan() = app.stopScanning()

    fun select(key: String?) {
        selectedKey.value = key
        if (key == null) heldSelected.value = null
    }

    fun select(device: Sighting) {
        heldSelected.value = device
        selectedKey.value = device.key
    }

    fun startHunt(device: Sighting) {
        val now = System.currentTimeMillis()
        huntKey.value = device.key
        huntStartedAt.value = now
        huntPeakRssi.value = device.rssi
        huntSamples.value = listOf(RssiSample(now, device.rssi))
    }

    fun resetHunt() {
        val key = huntKey.value ?: return
        val live = app.devices.devices.value.firstOrNull { it.key == key } ?: return
        startHunt(live)
    }

    fun stopHunt() {
        huntKey.value = null
        huntSamples.value = emptyList()
        huntPeakRssi.value = -127
        huntStartedAt.value = 0L
    }

    fun huntTick(beepOn: Boolean, vibrateOn: Boolean) {
        if (!beepOn && !vibrateOn) return
        app.alerter.huntTick(beepOn, vibrateOn)
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(viewMode = mode)) }
        }
    }

    fun toggleOutlineClass(id: String) {
        val open = _outlineOpenClasses.value
        _outlineOpenClasses.value = if (id in open) {
            _outlineOpenSigs.value = _outlineOpenSigs.value.filterNot { it.startsWith("$id/") }.toSet()
            open - id
        } else {
            open + id
        }
    }

    fun toggleOutlineSignature(id: String) {
        val open = _outlineOpenSigs.value
        _outlineOpenSigs.value = if (id in open) open - id else open + id
    }

    fun toggleCatalogClass(id: String) {
        val open = _catalogOpenClasses.value
        _catalogOpenClasses.value = if (id in open) open - id else open + id
    }

    private fun revealOutlineFor(deviceKey: String) {
        val device = app.devices.devices.value.firstOrNull { it.key == deviceKey } ?: return
        val classBy = app.config.fleets.associate { it.id to it.kind }
        val reveal = ClassOutline.reveal(device, classBy)
        _outlineOpenClasses.update { it + reveal.classIds }
        _outlineOpenSigs.update { it + reveal.sigKeys }
    }

    fun setStrengthSort(sort: StrengthSort, windowSec: Int? = null) {
        viewModelScope.launch {
            app.config.update {
                it.copy(
                    settings = it.settings.copy(
                        listSort = ListSort.STRENGTH,
                        strengthSort = sort,
                        averageWindowSec = windowSec ?: it.settings.averageWindowSec,
                    ),
                )
            }
        }
    }

    fun setListSort(sort: ListSort) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(listSort = sort)) }
        }
    }

    fun setSignatureListSort(sort: SignatureListSort) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(signatureListSort = sort)) }
        }
    }

    fun setDecaySec(sec: Int) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(decaySec = sec.coerceIn(0, 60))) }
        }
    }

    fun setShowRssiBar(show: Boolean) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(showRssiBar = show)) }
        }
    }

    fun toggleRssiBar() {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(showRssiBar = !it.settings.showRssiBar))
            }
        }
    }

    fun toggleFleetName() {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(showFleetName = !it.settings.showFleetName))
            }
        }
    }

    fun toggleFrequency() {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(showFrequency = !it.settings.showFrequency))
            }
        }
    }

    fun toggleSeenTimes() {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(showSeenTimes = !it.settings.showSeenTimes))
            }
        }
    }

    fun setListTitleLine(line: ListLine) {
        if (line == ListLine.NONE) return
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(listTitleLine = line)) }
        }
    }

    fun setListSubtitleLine(line: ListLine) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(listSubtitleLine = line)) }
        }
    }

    fun markArrivalsSeen() {
        val keys = if (displayPaused.value) frozenUi?.filtered?.map { it.key } else null
        app.markArrivalsSeen(keys)
    }

    fun resetArrivalsSeen() {
        app.resetSeenBuffer()
    }

    fun resetFollowSession() {
        app.resetFollowSession()
    }

    fun setScanControlsExpanded(expanded: Boolean) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(scanControlsExpanded = expanded)) }
        }
    }

    fun focusLiveList() {
        _liveFocus.value = _liveFocus.value + 1
    }

    fun toggleLiveDisplay() {
        displayPaused.value = !displayPaused.value
    }

    fun defaultSitName(): String = Sit.defaultName(System.currentTimeMillis())

    fun startSit(name: String) {
        viewModelScope.launch {
            val heard = app.devices.devices.value.filter { !it.gone }
            app.sits.start(name, heard, app.config.fleets, app.config.watchlist)
            publishSitNotice()
        }
    }

    fun endSit() {
        viewModelScope.launch {
            app.sits.end(app.config.fleets)
            publishSitNotice()
        }
    }

    fun renameSit(id: String, name: String) {
        viewModelScope.launch { app.sits.rename(id, name) }
    }

    fun deleteSit(id: String) {
        viewModelScope.launch { app.sits.delete(id) }
    }

    fun deleteAllSits() {
        viewModelScope.launch { app.sits.deleteAllClosed() }
    }

    fun selectSit(id: String?) {
        app.sits.select(id)
    }

    fun selectCompareSit(id: String?) {
        app.sits.selectCompare(id)
    }

    fun startSitCompare() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.08f, appText(R.string.fieldwatch_view_model_writing_sit_compare), ExportJob.COMPARE)
            runCatching {
                val doc = sitCompareDoc()
                publishExport(0.85f, appText(R.string.fieldwatch_view_model_writing_sit_compare))
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, compareSubject(doc))
                    putExtra(Intent.EXTRA_TEXT, doc.toPlainText())
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = appText(R.string.fieldwatch_view_model_sit_compare),
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_write_sit_compare))
            }
        }
    }

    fun startSitComparePdf() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.06f, appText(R.string.fieldwatch_view_model_writing_sit_compare_pdf), ExportJob.COMPARE_PDF)
            runCatching {
                val doc = sitCompareDoc()
                publishExport(0.35f, appText(R.string.fieldwatch_view_model_laying_out_sit_compare_pdf))
                val dir = File(app.cacheDir, "debrief").apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val file = File(dir, "fieldwatch-sit-compare-$stamp.pdf")
                publishExport(0.32f, appText(R.string.fieldwatch_view_model_loading_map_tiles))
                val tiles = pathTilesForFigure(doc.pathFigure)
                val extraTiles = doc.extraFigures.map { pathTilesForFigure(it) }
                withContext(Dispatchers.Default) {
                    DebriefPdf.write(doc, file, tiles, extraTiles) { p ->
                        kotlinx.coroutines.runBlocking {
                            publishExport(0.38f + 0.55f * p, appText(R.string.fieldwatch_view_model_writing_sit_compare_pdf))
                        }
                    }
                }
                val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    clipData = ClipData.newRawUri("sit-compare", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, compareSubject(doc))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = appText(R.string.fieldwatch_view_model_sit_compare_pdf),
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_write_sit_compare_pdf))
            }
        }
    }

    private fun compareSubject(doc: DebriefDoc): String =
        if (doc.windowLine.isNotBlank()) appText(R.string.fieldwatch_view_model_fieldwatch_sit_compare, doc.windowLine)
        else appText(R.string.fieldwatch_view_model_fieldwatch_sit_compare_2)

    fun startSitCompareAiExport() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.08f, appText(R.string.fieldwatch_view_model_building_compare_ai_export), ExportJob.COMPARE_AI)
            runCatching {
                val (thisSide, second) = compareSides()
                publishExport(0.45f, appText(R.string.fieldwatch_view_model_building_compare_ai_export))
                val text = withContext(Dispatchers.Default) {
                    SitDiffPrompt.build(thisSide, second, app.config.settings.demoMode)
                }
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        appText(R.string.fieldwatch_view_model_fieldwatch_sit_compare_ai_export_vs, thisSide.name, second.name),
                    )
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = appText(R.string.fieldwatch_view_model_sit_compare_ai_export),
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_write_compare_ai_export))
            }
        }
    }

    private suspend fun sitCompareDoc(): DebriefDoc {
        val (thisSide, second) = compareSides()
        val macs = (thisSide.radios + second.radios).map { it.mac }
        return SitDiff.document(
            thisSide,
            second,
            RadioBookmarks.watchedFleetIds(app.config.watchlist),
            showAllRadios = app.config.settings.debriefShowAllRadios,
        )
            .withDemoMacs(macs, app.config.settings.demoMode)
    }

    private suspend fun compareSides(): Pair<SitDiff.Side, SitDiff.Side> {
        val sit = app.sits.ui.value
        val otherId = sit.compareId ?: error(appText(R.string.fieldwatch_view_model_pick_a_second_sit))
        val otherFile = withContext(Dispatchers.IO) { app.sits.sitFile(otherId) }
            ?: error(appText(R.string.fieldwatch_view_model_could_not_read_that_sit))
        val fleets = app.config.fleets
        val customNames = RadioBookmarks.labels(app.config.watchlist)
        val observerNotes = RadioBookmarks.notes(app.config.watchlist)
        val bookmarkedKeys = RadioBookmarks.alertDeviceKeys(app.config.watchlist)
        val mineKeys = RadioBookmarks.mineKeys(app.config.watchlist)
        val thisSide = compareThisSide(sit, fleets, customNames, observerNotes, bookmarkedKeys, mineKeys)
        val second = SitDiff.Side(
            name = otherFile.summary.name,
            ram = false,
            radios = otherFile.radios.map {
                SitDiff.fromSitRadio(it, fleets, customNames, observerNotes, bookmarkedKeys, mineKeys)
            },
            path = otherFile.operatorPath,
            floods = otherFile.floods,
        )
        return thisSide to second
    }

    private suspend fun compareThisSide(
        sit: SitUi,
        fleets: List<Fleet>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String>,
        bookmarkedKeys: Set<String>,
        mineKeys: Set<String>,
    ): SitDiff.Side {
        val open = sit.open
        if (open != null) {
            val source = app.sits.debriefSource()
            return SitDiff.Side(
                name = open.name,
                ram = false,
                radios = source?.devices.orEmpty().map {
                    SitDiff.fromSighting(it, fleets, customNames, observerNotes, bookmarkedKeys, mineKeys)
                },
                path = source?.operatorPath.orEmpty(),
                floods = source?.floods.orEmpty(),
            )
        }
        val selected = sit.closed.firstOrNull { it.id == sit.selectedId }
        if (selected != null) {
            val file = withContext(Dispatchers.IO) { app.sits.sitFile(selected.id) }
                ?: error(appText(R.string.fieldwatch_view_model_could_not_read_this_sit))
            return SitDiff.Side(
                name = selected.name,
                ram = false,
                radios = file.radios.map {
                    SitDiff.fromSitRadio(it, fleets, customNames, observerNotes, bookmarkedKeys, mineKeys)
                },
                path = file.operatorPath,
                floods = file.floods,
            )
        }
        val now = System.currentTimeMillis()
        val since = now - DebriefPrompt.WINDOW_MS
        return SitDiff.Side(
            name = appText(R.string.fieldwatch_view_model_last_15_minutes),
            ram = true,
            radios = app.devices.devices.value.map {
                SitDiff.fromSighting(it, fleets, customNames, observerNotes, bookmarkedKeys, mineKeys)
            },
            path = app.operatorPathCopy().filter { it.at >= since },
            floods = app.floodBursts().filter { it.at >= since },
        )
    }

    private fun publishSitNotice() {
        val notice = app.sits.ui.value.notice ?: return
        _export.value = ExportUi(noticeTitle = appText(R.string.fieldwatch_view_model_sits), noticeMessage = notice)
        app.sits.consumeNotice()
    }

    fun updateFilter(transform: (FilterState) -> FilterState) {
        viewModelScope.launch {
            var nextOn = app.config.filter.arrivalsOnly
            app.config.update {
                val next = transform(it.filter)
                nextOn = next.arrivalsOnly
                it.copy(filter = next)
            }
            app.syncArrivals(nextOn)
        }
    }

    fun applyPreset(preset: FilterPreset) {
        viewModelScope.launch {
            app.config.update { it.copy(filter = preset.filter) }
            app.syncArrivals(preset.filter.arrivalsOnly)
        }
    }

    fun savePreset(name: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val preset = FilterPreset(UUID.randomUUID().toString(), name, cfg.filter)
                cfg.copy(presets = cfg.presets + preset)
            }
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val removing = cfg.presets.firstOrNull { it.id == id }
                val hidden = if (removing?.isBuiltIn() == true) cfg.hiddenPresetIds + id else cfg.hiddenPresetIds
                cfg.copy(
                    presets = cfg.presets.filterNot { it.id == id },
                    hiddenPresetIds = hidden,
                )
            }
        }
    }

    fun upsertFleet(fleet: Fleet, after: (() -> Unit)? = null) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val existing = cfg.fleets.indexOfFirst { it.id == fleet.id }
                val next = cfg.fleets.toMutableList()
                if (existing >= 0) next[existing] = fleet else next += fleet
                cfg.copy(fleets = next)
            }
            setDraft(null)
            app.devices.refresh(
                app.config.fleets,
                app.config.settings.staleSec,
                policy = app.config.settings.detectionPolicy(),
                decaySec = app.config.settings.decaySec,
            )
            after?.invoke()
        }
    }

    fun deleteFleet(id: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(
                    fleets = cfg.fleets.filterNot { it.id == id },
                    watchlist = cfg.watchlist.filterNot { it.fleetId == id },
                    filter = cfg.filter.copy(
                        fleetIds = cfg.filter.fleetIds - id,
                        includeFleetIds = cfg.filter.includeFleetIds - id,
                    ),
                )
            }
            setDraft(null)
            app.devices.refresh(
                app.config.fleets,
                app.config.settings.staleSec,
                policy = app.config.settings.detectionPolicy(),
                decaySec = app.config.settings.decaySec,
            )
        }
    }

    fun beginCreateFrom(device: Sighting) {
        draftFromCandidates = false
        setDraft(signatures.suggestFleet(device))
    }

    fun beginCreateFromCandidate(candidate: SignatureCandidate) {
        draftFromCandidates = true
        setDraft(SignatureCandidates.suggestFleet(candidate))
    }

    fun takeDraftFromCandidates(): Boolean {
        val hit = draftFromCandidates
        draftFromCandidates = false
        return hit
    }

    fun startSignatureCandidates() {
        if (_candidates.value.loading) return
        viewModelScope.launch {
            _candidates.value = CandidatesUi(loading = true)
            runCatching {
                val radios = app.logs.readRadios()
                withContext(Dispatchers.Default) {
                    SignatureCandidates.analyze(radios, app.config.fleets)
                }
            }.onSuccess { report ->
                _candidates.value = CandidatesUi(report = report)
            }.onFailure { err ->
                _candidates.value = CandidatesUi(error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_read_the_log))
            }
        }
    }

    fun beginNewFleet() {
        draftFromCandidates = false
        setDraft(DefaultCatalog.newBlankFleet().copy(name = appText(R.string.new_signature_name)))
    }

    fun editFleet(fleet: Fleet) {
        draftFromCandidates = false
        setDraft(fleet)
    }

    fun saveFleetKeepDraft(fleet: Fleet) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val existing = cfg.fleets.indexOfFirst { it.id == fleet.id }
                val next = cfg.fleets.toMutableList()
                if (existing >= 0) next[existing] = fleet else next += fleet
                cfg.copy(fleets = next)
            }
            setDraft(fleet)
        }
    }

    fun cancelDraft() {
        setDraft(null)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val prev = app.config.settings
            app.config.update { it.copy(settings = transform(it.settings)) }
            val next = app.config.settings
            app.logs.configure(next.logFormat, next.logRotateKb, next.loggingEnabled)
            if (prev.tagLocation != next.tagLocation) app.syncLocationUpdates()
            if (prev.intensity != next.intensity && app.devices.stats.value.scanning) {
                app.startScanning()
            }
            if (prev.detectionPolicy() != next.detectionPolicy()) {
                app.devices.refresh(
                    app.config.fleets,
                    next.staleSec,
                    policy = next.detectionPolicy(),
                    decaySec = next.decaySec,
                )
            }
            if (next.alertVoice) app.alerter.prepareVoice()
        }
    }

    fun toggleWatchDevice(device: Sighting) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val names = device.fleetIds.mapNotNull { id -> cfg.fleets.firstOrNull { it.id == id }?.name }
                cfg.copy(
                    watchlist = RadioBookmarks.toggleAlert(
                        cfg.watchlist,
                        device.key,
                        RadioBookmarks.suggestLabel(device, names),
                    ),
                )
            }
        }
    }

    fun saveRadioName(device: Sighting, name: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.upsertName(cfg.watchlist, device.key, name))
            }
        }
    }

    fun setRadioAlert(id: String, on: Boolean) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.setAlert(cfg.watchlist, id, on))
            }
        }
    }

    fun isMine(deviceKey: String): Boolean =
        app.config.watchlist.any { it.deviceKey == deviceKey && it.mine }

    fun setRadioMine(device: Sighting, on: Boolean) {
        viewModelScope.launch {
            val suggest = RadioBookmarks.suggestLabel(device, device.fleetIds.map { fleetName(it) })
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.setMine(cfg.watchlist, device.key, on, suggest))
            }
        }
    }

    fun setNamedRadioMine(id: String, on: Boolean) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.setMineOnRow(cfg.watchlist, id, on))
            }
        }
    }

    fun renameRadioBookmark(id: String, name: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.rename(cfg.watchlist, id, name))
            }
        }
    }

    fun updateNamedRadio(id: String, name: String, notes: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.updateNamedRadio(cfg.watchlist, id, name, notes))
            }
        }
    }

    fun removeRadioBookmark(id: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.remove(cfg.watchlist, id))
            }
        }
    }

    fun clearRadioBookmarks() {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.withoutRadios(cfg.watchlist))
            }
        }
    }

    fun watchLabelFor(deviceKey: String): String? =
        app.config.watchlist.firstOrNull { it.deviceKey == deviceKey }?.label?.trim()?.takeIf { it.isNotEmpty() }

    fun watchObserverNoteFor(deviceKey: String): String? =
        app.config.watchlist.firstOrNull { it.deviceKey == deviceKey }?.observerNotes?.trim()?.takeIf { it.isNotEmpty() }

    fun saveRadioNotes(device: Sighting, notes: String) {
        viewModelScope.launch {
            val suggest = RadioBookmarks.suggestLabel(device, device.fleetIds.map { fleetName(it) })
            app.config.update { cfg ->
                cfg.copy(
                    watchlist = RadioBookmarks.upsertNotes(cfg.watchlist, device.key, notes, suggest),
                )
            }
        }
    }

    fun toggleWatchFleet(fleet: Fleet) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val exists = cfg.watchlist.any { it.fleetId == fleet.id }
                val next = if (exists) {
                    cfg.watchlist.filterNot { it.fleetId == fleet.id }
                } else {
                    cfg.watchlist + WatchTarget(
                        id = UUID.randomUUID().toString(),
                        fleetId = fleet.id,
                        label = fleet.name,
                    )
                }
                cfg.copy(watchlist = next)
            }
        }
    }

    fun restoreDefaults() {
        viewModelScope.launch {
            app.config.restoreDefaults()
            app.syncLocationUpdates()
            app.syncArrivals(false)
            app.devices.refresh(
                app.config.fleets,
                app.config.settings.staleSec,
                policy = app.config.settings.detectionPolicy(),
                decaySec = app.config.settings.decaySec,
            )
        }
    }

    fun suggestedSignaturesName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        return "fieldwatch-signatures-$stamp.json"
    }

    private fun signaturePackJson(): String {
        val cfg = app.config.config.value
        return SignatureExchange.encode(
            SignatureExchange.pack(
                fleets = cfg.fleets,
                catalogVersion = cfg.version,
                appVersion = BuildConfig.VERSION_NAME,
                exportedAt = java.time.Instant.now().toString(),
            ),
        )
    }

    fun startSignatureShare() {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) { signaturePackJson() }
                val dir = File(app.cacheDir, "signatures").apply { mkdirs() }
                val file = File(dir, suggestedSignaturesName())
                withContext(Dispatchers.IO) { file.writeText(json) }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    clipData = ClipData.newRawUri("signatures", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, appText(R.string.fieldwatch_view_model_fieldwatch_signatures))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(share = intent, shareTitle = appText(R.string.fieldwatch_view_model_fieldwatch_signatures))
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_export_signatures),
                    errorTitle = appText(R.string.fieldwatch_view_model_could_not_export_signatures),
                )
            }
        }
    }

    fun saveSignaturesToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) { signaturePackJson() }
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error(appText(R.string.fieldwatch_view_model_could_not_write_to_the_location_you))
                }
            }.onSuccess {
                _export.value = ExportUi(
                    noticeTitle = appText(R.string.fieldwatch_view_model_signatures_saved),
                    noticeMessage = appText(R.string.fieldwatch_view_model_the_pack_was_written_to_the_folder),
                )
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_save_signatures),
                    errorTitle = appText(R.string.fieldwatch_view_model_could_not_save_signatures),
                )
            }
        }
    }

    fun updateStockCatalogFromGitHub() {
        if (_export.value.active) return
        viewModelScope.launch {
            if (!PlaceLookup.online(app)) {
                _export.value = ExportUi(
                    errorTitle = appText(R.string.fieldwatch_view_model_no_internet),
                    error = appText(R.string.fieldwatch_view_model_no_internet_use_import_signatures_from_a),
                )
                return@launch
            }
            _export.value = ExportUi(
                active = true,
                spinner = true,
                job = ExportJob.CATALOG,
                message = appText(R.string.fieldwatch_view_model_updating_stock_catalog),
            )
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    CatalogRemote.fetch(
                        userAgent = "Fieldwatch/${BuildConfig.VERSION_NAME}",
                    )
                }
                val parsed = SignatureExchange.parsePack(text)
                val result = app.config.overlayStockCatalog(parsed.pack)
                    .copy(skippedDecode = parsed.skippedDecode)
                result.error?.let { throw IllegalStateException(it) }
                if (!result.alreadyLatest) {
                    app.devices.refresh(
                        app.config.fleets,
                        app.config.settings.staleSec,
                        policy = app.config.settings.detectionPolicy(),
                        decaySec = app.config.settings.decaySec,
                    )
                }
                result
            }.onSuccess { result ->
                _export.value = if (result.alreadyLatest) {
                    ExportUi(
                        noticeTitle = appText(R.string.fieldwatch_view_model_already_on_the_latest_catalog),
                        noticeMessage = appText(R.string.fieldwatch_view_model_already_on_catalog_nothing_to_update, result.catalogVersion),
                    )
                } else {
                    val bits = mutableListOf<String>()
                    if (result.updated > 0) bits += appText(R.string.fieldwatch_view_model_updated, result.updated)
                    if (result.added > 0) bits += appText(R.string.fieldwatch_view_model_added, result.added)
                    val change = if (bits.isEmpty()) appText(R.string.fieldwatch_view_model_no_stock_rows_changed)
                    else bits.joinToString(" · ").replaceFirstChar { it.uppercase() } + "."
                    val skip = result.skippedDecode > 0
                    ExportUi(
                        noticeTitle = appText(R.string.fieldwatch_view_model_catalog_updated),
                        noticeMessage = appText(R.string.fieldwatch_view_model_stock_catalog_is_now_bookmarks_and_settings, result.catalogVersion, change),
                        followUpTitle = if (skip) appText(R.string.fieldwatch_view_model_signature_decoding_skipped) else null,
                        followUpMessage = if (skip) {
                            appText(R.string.fieldwatch_view_model_some_signature_field_maps_in_this_catalog)
                        } else {
                            null
                        },
                    )
                }
            }.onFailure { err ->
                val msg = err.message.orEmpty()
                val access = msg.contains("HTTP", ignoreCase = true) ||
                    msg.contains("Unable to resolve", ignoreCase = true) ||
                    msg.contains("failed to connect", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("GitHub", ignoreCase = true)
                _export.value = if (access) {
                    ExportUi(
                        errorTitle = appText(R.string.fieldwatch_view_model_could_not_reach_github),
                        error = appText(R.string.fieldwatch_view_model_could_not_reach_the_catalog_on_github),
                    )
                } else {
                    ExportUi(
                        errorTitle = appText(R.string.fieldwatch_view_model_could_not_import_catalog),
                        error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_import_catalog_2),
                    )
                }
            }
        }
    }

    fun importSignaturesFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error(appText(R.string.fieldwatch_view_model_could_not_read_that_file))
                }
                val pack = SignatureExchange.parse(text)
                val result = app.config.importFleets(pack.fleets)
                if (result.error != null) error(result.error)
                app.devices.refresh(
                    app.config.fleets,
                    app.config.settings.staleSec,
                    policy = app.config.settings.detectionPolicy(),
                    decaySec = app.config.settings.decaySec,
                )
                result.summary()
            }.onSuccess { summary ->
                _export.value = ExportUi(
                    noticeTitle = appText(R.string.fieldwatch_view_model_signatures_imported),
                    noticeMessage = summary,
                )
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_import_signatures),
                    errorTitle = appText(R.string.fieldwatch_view_model_could_not_import_signatures),
                )
            }
        }
    }

    fun suggestedSettingsName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        return "fieldwatch-settings-$stamp.json"
    }

    private fun settingsPackJson(): String {
        val cfg = app.config.config.value
        return SettingsExchange.encode(
            SettingsExchange.pack(
                settings = cfg.settings,
                filter = cfg.filter,
                presets = cfg.presets,
                watchlist = cfg.watchlist,
                hiddenPresetIds = cfg.hiddenPresetIds,
                appVersion = BuildConfig.VERSION_NAME,
                exportedAt = java.time.Instant.now().toString(),
            ),
        )
    }

    fun startSettingsShare() {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) { settingsPackJson() }
                val dir = File(app.cacheDir, "settings").apply { mkdirs() }
                val file = File(dir, suggestedSettingsName())
                withContext(Dispatchers.IO) { file.writeText(json) }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    clipData = ClipData.newRawUri("settings", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, appText(R.string.fieldwatch_view_model_fieldwatch_settings))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(share = intent, shareTitle = appText(R.string.fieldwatch_view_model_fieldwatch_settings))
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_export_settings),
                    errorTitle = appText(R.string.fieldwatch_view_model_could_not_export_settings),
                )
            }
        }
    }

    fun saveSettingsToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) { settingsPackJson() }
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error(appText(R.string.fieldwatch_view_model_could_not_write_to_the_location_you))
                }
            }.onSuccess {
                _export.value = ExportUi(
                    noticeTitle = appText(R.string.fieldwatch_view_model_settings_saved),
                    noticeMessage = appText(R.string.fieldwatch_view_model_the_pack_was_written_to_the_folder_2),
                )
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_save_settings),
                    errorTitle = appText(R.string.fieldwatch_view_model_could_not_save_settings),
                )
            }
        }
    }

    fun importSettingsFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error(appText(R.string.fieldwatch_view_model_could_not_read_that_file))
                }
                val pack = SettingsExchange.parse(text)
                val prev = app.config.settings
                val result = app.config.importSettings(pack)
                if (result.error != null) error(result.error)
                val next = app.config.settings
                app.logs.configure(next.logFormat, next.logRotateKb, next.loggingEnabled)
                if (prev.tagLocation != next.tagLocation) app.syncLocationUpdates()
                if (prev.intensity != next.intensity && app.devices.stats.value.scanning) {
                    app.startScanning()
                }
                app.devices.refresh(
                    app.config.fleets,
                    next.staleSec,
                    policy = next.detectionPolicy(),
                    decaySec = next.decaySec,
                )
                if (next.alertVoice) app.alerter.prepareVoice()
                result.summary()
            }.onSuccess { summary ->
                _export.value = ExportUi(
                    noticeTitle = appText(R.string.fieldwatch_view_model_settings_imported),
                    noticeMessage = summary,
                )
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_import_settings),
                    errorTitle = appText(R.string.fieldwatch_view_model_could_not_import_settings),
                )
            }
        }
    }

    fun setLogExportKind(kind: LogExportKind) {
        _logExportKind.value = kind
    }

    fun setLogExportRadios(radios: LogExportRadios) {
        _logExportRadios.value = radios
    }

    fun setSitExportKind(kind: LogExportKind) {
        _sitExportKind.value = kind
    }

    fun setSitExportRadios(radios: LogExportRadios) {
        _sitExportRadios.value = radios
    }

    private suspend fun pathTilesForFigure(figure: SitPathPlot.Figure?): List<PathTiles.Tile> {
        if (figure == null || !figure.drawable) return emptyList()
        val phone = figure.tracks.filter { !it.aircraft }.flatMap { it.samples }
        val craft = figure.tracks.filter { it.aircraft }.flatMap { it.samples }
        val samples = Geo.despikePath(phone).let { if (it.size >= 2) it else phone } + craft
        if (samples.isEmpty()) return emptyList()
        val settings = app.config.settings
        return PathTiles.load(
            app, samples,
            onlineLookup = settings.onlineLookup,
        )
    }

    fun refreshSitPath() {
        viewModelScope.launch(Dispatchers.Default) {
            val model = buildSitPath()
            _sitPath.value = model
            val settings = app.config.settings
            if (!settings.onlineLookup) {
                _pathTiles.value = emptyList()
                _pathAircraftTiles.value = emptyList()
                return@launch
            }
            val phone = Geo.despikePath(model.samples).let { if (it.size >= 2) it else model.samples }
            val walk = phone + model.craft.flatMap { it.samples }
            _pathTiles.value = if (walk.isNotEmpty()) {
                runCatching {
                    PathTiles.load(
                        app, walk,
                        onlineLookup = settings.onlineLookup,
                    )
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _pathAircraftTiles.value = model.aircraftCards.map { card ->
                val samples = card.craft.flatMap { it.samples }
                if (samples.size < 2) {
                    emptyList()
                } else {
                    runCatching {
                        PathTiles.load(
                            app, samples,
                            onlineLookup = settings.onlineLookup,
                        )
                    }.getOrDefault(emptyList())
                }
            }
        }
    }

    private fun buildSitPath(): SitPathPlot.Model {
        val now = System.currentTimeMillis()
        val source = app.sits.debriefSource(now)
        val customNames = RadioBookmarks.labels(app.config.watchlist)
        val namedKeys = customNames.keys
        val bookmarkedKeys = RadioBookmarks.alertDeviceKeys(app.config.watchlist)
        val watchedFleets = RadioBookmarks.watchedFleetIds(app.config.watchlist)
        val fleets = app.config.fleets
        val tagging = app.config.settings.tagLocation
        if (source != null) {
            val raw = source.operatorPath
            val samples = Geo.despikePath(raw)
            val plot = SitPathPlot.dotsFrom(
                source.devices, fleets, namedKeys, customNames = customNames,
                observerNotes = RadioBookmarks.notes(app.config.watchlist),
                bookmarkedKeys = bookmarkedKeys,
                watchedFleetIds = watchedFleets,
                alertsOnly = true,
                mineKeys = RadioBookmarks.mineKeys(app.config.watchlist),
            )
            val empty = when {
                !tagging -> appText(R.string.fieldwatch_view_model_tag_detections_with_gps_settings_to_record)
                samples.isEmpty() -> appText(R.string.fieldwatch_view_model_walk_with_tagging_on_path_needs_a)
                else -> null
            }
            pathRadios = source.devices
            val pictures = AircraftTrail.pictures(
                source.devices.mapNotNull { device ->
                    AircraftTrail.source(
                        device,
                        device.payloadUasId?.trim().orEmpty().ifBlank { device.reportName(customNames) },
                    )
                },
                if (empty == null) raw else emptyList(),
            )
            return AircraftTrail.overlay(
                SitPathPlot.Model(
                    samples = samples,
                    dots = if (samples.isNotEmpty()) plot.points else emptyList(),
                    lengthM = Geo.pathLengthM(samples),
                    spanM = Geo.spanM(samples),
                    title = source.name,
                    emptyHint = empty,
                    live = app.sits.ui.value.open != null,
                ),
                pictures,
            )
        }
        val start = now - DebriefPrompt.WINDOW_MS
        val samples = Geo.despikePath(app.operatorPathCopy().filter { it.at >= start })
        val devices = app.devices.devices.value.filter { it.lastSeen >= start || it.firstSeen >= start }
        val empty = when {
            !tagging -> appText(R.string.fieldwatch_view_model_tag_detections_with_gps_settings_to_record)
            samples.isEmpty() -> appText(R.string.fieldwatch_view_model_last_15_minutes_walk_with_tagging_on)
            else -> null
        }
        pathRadios = devices
        val plot = if (samples.isNotEmpty()) {
            SitPathPlot.dotsFrom(
                devices, fleets, namedKeys, customNames = customNames,
                observerNotes = RadioBookmarks.notes(app.config.watchlist),
                bookmarkedKeys = bookmarkedKeys,
                watchedFleetIds = watchedFleets,
                alertsOnly = true,
                mineKeys = RadioBookmarks.mineKeys(app.config.watchlist),
            )
        } else {
            SitPathPlot.PlotRadios(emptyList())
        }
        val windowPath = app.operatorPathCopy().filter { it.at >= start }
        return AircraftTrail.overlay(
            SitPathPlot.Model(
                samples = samples,
                dots = plot.points,
                lengthM = Geo.pathLengthM(samples),
                spanM = Geo.spanM(samples),
                title = appText(R.string.fieldwatch_view_model_last_15_minutes),
                emptyHint = empty,
                live = true,
            ),
            AircraftTrail.pictures(
                devices.mapNotNull { device ->
                    AircraftTrail.source(
                        device,
                        device.payloadUasId?.trim().orEmpty().ifBlank { device.reportName(customNames) },
                    )
                },
                if (empty == null) windowPath else emptyList(),
            ),
        )
    }

    fun openPathRadio(key: String): Boolean {
        val device = pathRadios.firstOrNull { it.key == key } ?: return false
        select(device)
        return true
    }

    fun suggestedExportName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return when (val kind = _logExportKind.value) {
            LogExportKind.LOG_CSV -> app.logs.suggestedExportName(asJsonl = false)
            LogExportKind.LOG_JSONL -> app.logs.suggestedExportName(asJsonl = true)
            LogExportKind.GPX, LogExportKind.KML, LogExportKind.WIGLE ->
                GeoExport.suggestedName(GeoExport.formatOf(kind)!!, stamp)
        }
    }

    fun exportMime(): String = when (_logExportKind.value) {
        LogExportKind.LOG_CSV -> app.logs.exportMime(asJsonl = false)
        LogExportKind.LOG_JSONL -> app.logs.exportMime(asJsonl = true)
        LogExportKind.GPX, LogExportKind.KML, LogExportKind.WIGLE ->
            GeoExport.formatOf(_logExportKind.value)!!.mime
    }

    fun startFieldDebriefPdf() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.05f, appText(R.string.fieldwatch_view_model_writing_debrief_pdf), ExportJob.DEBRIEF_PDF)
            runCatching {
                val doc = fieldDebriefDoc()
                publishExport(0.4f, appText(R.string.fieldwatch_view_model_laying_out_debrief_pdf))
                val dir = File(app.cacheDir, "debrief").apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val file = File(dir, "fieldwatch-debrief-$stamp.pdf")
                publishExport(0.32f, appText(R.string.fieldwatch_view_model_loading_map_tiles))
                val tiles = pathTilesForFigure(doc.pathFigure)
                val extraTiles = doc.extraFigures.map { pathTilesForFigure(it) }
                withContext(Dispatchers.Default) {
                    DebriefPdf.write(doc, file, tiles, extraTiles) { p ->
                        kotlinx.coroutines.runBlocking {
                            publishExport(0.4f + 0.55f * p, appText(R.string.fieldwatch_view_model_writing_debrief_pdf))
                        }
                    }
                }
                val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    clipData = ClipData.newRawUri("debrief", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, debriefSubject(doc))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = appText(R.string.fieldwatch_view_model_debrief_pdf),
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_write_debrief_pdf))
            }
        }
    }

    fun startFieldDebrief() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.08f, appText(R.string.fieldwatch_view_model_writing_debrief), ExportJob.DEBRIEF)
            runCatching {
                val doc = fieldDebriefDoc()
                publishExport(0.9f, appText(R.string.fieldwatch_view_model_writing_debrief))
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, debriefSubject(doc))
                    putExtra(Intent.EXTRA_TEXT, doc.toPlainText())
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = appText(R.string.fieldwatch_view_model_debrief),
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_write_debrief))
            }
        }
    }

    private fun debriefSubject(doc: DebriefDoc): String =
        if (doc.heading.startsWith(appText(R.string.fieldwatch_view_model_fieldwatch_sit))) doc.heading else appText(R.string.fieldwatch_view_model_fieldwatch_field_debrief_last_15_minutes)

    private suspend fun fieldDebriefDoc(): DebriefDoc {
        publishExport(0.08f, appText(R.string.fieldwatch_view_model_gathering_sit))
        val settings = app.config.settings
        val fleets = app.config.fleets
        val now = System.currentTimeMillis()
        val source = app.sits.debriefSource(now)
        val devices = source?.devices ?: app.devices.devices.value
        val path = source?.operatorPath ?: app.operatorPathCopy()
        val window = source?.let { DebriefWindow(it.startAt, it.endAt, it.name) }
        val places = if (settings.demoMode) {
            DebriefPlaces.Off
        } else if (settings.onlineLookup) {
            publishExport(0.14f, appText(R.string.fieldwatch_view_model_looking_up_place_names))
            val found = PlaceLookup.lookup(app, path, devices, now, onProgress = { msg ->
                kotlinx.coroutines.runBlocking { publishExport(0.18f, msg) }
            })
            found
        } else {
            DebriefPlaces.Off
        }
        publishExport(0.32f, appText(R.string.fieldwatch_view_model_building_debrief))
        return withContext(Dispatchers.Default) {
            DebriefReport.document(
                devices = devices,
                fleets = fleets,
                settings = settings,
                operatorPath = path,
                now = now,
                places = places,
                window = window,
                customNames = RadioBookmarks.labels(app.config.watchlist),
                observerNotes = RadioBookmarks.notes(app.config.watchlist),
                bookmarkedKeys = RadioBookmarks.alertDeviceKeys(app.config.watchlist),
                watchedFleetIds = RadioBookmarks.watchedFleetIds(app.config.watchlist),
                mineKeys = RadioBookmarks.mineKeys(app.config.watchlist),
                floods = source?.floods ?: app.floodBursts(),
            ).withDemoMacs(devices.map { it.mac }, settings.demoMode)
        }
    }

    fun startAiExport() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.06f, appText(R.string.fieldwatch_view_model_building_ai_export_prompt), ExportJob.AI)
            runCatching {
                val settings = app.config.settings
                val fleets = app.config.fleets
                val now = System.currentTimeMillis()
                val source = app.sits.debriefSource(now)
                val devices = source?.devices ?: app.devices.devices.value
                val path = source?.operatorPath ?: app.operatorPathCopy()
                val window = source?.let { DebriefWindow(it.startAt, it.endAt, it.name) }
                val places = if (settings.demoMode) {
                    DebriefPlaces.Off
                } else if (settings.onlineLookup) {
                    publishExport(0.12f, appText(R.string.fieldwatch_view_model_looking_up_place_names))
                    val found = PlaceLookup.lookup(app, path, devices, now) { msg ->
                        kotlinx.coroutines.runBlocking { publishExport(0.16f, msg) }
                    }
                    publishExport(0.35f, appText(R.string.fieldwatch_view_model_building_ai_export_prompt))
                    found
                } else {
                    DebriefPlaces.Off
                }
                publishExport(0.4f, appText(R.string.fieldwatch_view_model_building_ai_export_prompt))
                val text = withContext(Dispatchers.Default) {
                    val raw = DebriefPrompt.build(
                        devices = devices,
                        fleets = fleets,
                        settings = settings,
                        now = now,
                        operatorPath = path,
                        places = places,
                        window = window,
                        customNames = RadioBookmarks.labels(app.config.watchlist),
                        observerNotes = RadioBookmarks.notes(app.config.watchlist),
                        bookmarkedKeys = RadioBookmarks.alertDeviceKeys(app.config.watchlist),
                        mineKeys = RadioBookmarks.mineKeys(app.config.watchlist),
                        floods = source?.floods ?: app.floodBursts(),
                    )
                    val masked = Geo.redactCoordsIn(
                        MacUtil.redactMacsIn(raw, devices.map { it.mac }, settings.demoMode),
                        settings.demoMode,
                    )
                    if (settings.demoMode) {
                        appText(R.string.fieldwatch_view_model_privacy_mode_mac_tails_are_gps_coordinates, masked)
                    } else {
                        masked
                    }
                }
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        if (window != null) appText(R.string.fieldwatch_view_model_fieldwatch_ai_export_sit, window.sitName)
                        else appText(R.string.fieldwatch_view_model_fieldwatch_ai_export_last_15_minutes),
                    )
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = appText(R.string.fieldwatch_view_model_ai_export),
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_build_ai_export))
            }
        }
    }

    fun startDeviceDetailAiExport(device: Sighting) {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.08f, appText(R.string.fieldwatch_view_model_building_ai_export_prompt), ExportJob.AI)
            runCatching {
                val settings = app.config.settings
                val names = device.fleetIds.map { fleetName(it) }
                val attention = attentionNotesFor(device)
                val notes = signatureNotesFor(device)
                val places = if (settings.demoMode) {
                    DebriefPlaces.Off
                } else if (settings.onlineLookup && (settings.tagLocation || device.latitude != null)) {
                    publishExport(0.15f, appText(R.string.fieldwatch_view_model_looking_up_place_names))
                    val found = PlaceLookup.lookup(app, app.operatorPathCopy(), listOf(device), System.currentTimeMillis()) { msg ->
                        kotlinx.coroutines.runBlocking { publishExport(0.2f, msg) }
                    }
                    publishExport(0.4f, appText(R.string.fieldwatch_view_model_building_ai_export_prompt))
                    found
                } else {
                    DebriefPlaces.Off
                }
                publishExport(0.45f, appText(R.string.fieldwatch_view_model_building_ai_export_prompt))
                val text = withContext(Dispatchers.Default) {
                    val raw = DeviceDetailPrompt.build(
                        device, names, settings, places, attentionNotes = attention,
                        signatureNotes = notes,
                        fleets = app.config.fleets,
                        mine = isMine(device.key),
                    )
                    val masked = Geo.redactCoordsIn(
                        MacUtil.redactMacIn(raw, device.mac, settings.demoMode),
                        settings.demoMode,
                    )
                    if (settings.demoMode) {
                        appText(R.string.fieldwatch_view_model_privacy_mode_mac_tails_are_gps_coordinates, masked)
                    } else {
                        masked
                    }
                }
                val title = MacUtil.redactMacIn(device.listTitle(names), device.mac, settings.demoMode)
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, appText(R.string.fieldwatch_view_model_fieldwatch_ai_export, title))
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = appText(R.string.fieldwatch_view_model_ai_export),
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_build_ai_export))
            }
        }
    }

    fun startDeviceDetailShare(device: Sighting) {
        if (_export.value.active) return
        viewModelScope.launch {
            runCatching {
                val settings = app.config.settings
                val names = device.fleetIds.map { fleetName(it) }
                val attention = attentionNotesFor(device)
                val notes = signatureNotesFor(device)
                val text = withContext(Dispatchers.Default) {
                    val raw = DeviceDetailText.build(
                        device, names, attentionNotes = attention, signatureNotes = notes,
                        fleets = app.config.fleets,
                        mine = isMine(device.key),
                    )
                    val masked = Geo.redactCoordsIn(
                        MacUtil.redactMacIn(raw, device.mac, settings.demoMode),
                        settings.demoMode,
                    )
                    if (settings.demoMode) {
                        appText(R.string.fieldwatch_view_model_privacy_mode_mac_tails_are_gps_coordinates, masked)
                    } else {
                        masked
                    }
                }
                val title = MacUtil.redactMacIn(device.listTitle(names), device.mac, settings.demoMode)
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, appText(R.string.fieldwatch_view_model_fieldwatch_device_detail, title))
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(share = intent, shareTitle = appText(R.string.fieldwatch_view_model_device_detail))
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_share_device_detail))
            }
        }
    }

    fun suggestedSitExportName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return SitExport.suggestedName(_sitExportKind.value, sitExportWindow().name, stamp)
    }

    fun sitExportMime(): String = SitExport.mime(_sitExportKind.value)

    fun startSitExport() {
        if (_export.value.active) return
        viewModelScope.launch {
            runExport(appText(R.string.fieldwatch_view_model_preparing_sit_export), ExportJob.SIT) {
                val kind = _sitExportKind.value
                val (file, sitName) = writeSitExport(kind)
                val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = SitExport.mime(kind)
                    clipData = ClipData.newRawUri("sit-export", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, SitExport.subject(kind, sitName))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(active = false, progress = 1f, share = intent, shareTitle = appText(R.string.fieldwatch_view_model_sit_export))
            }
        }
    }

    fun startSitSaveToUri(uri: Uri) {
        if (_export.value.active) return
        viewModelScope.launch {
            runExport(appText(R.string.fieldwatch_view_model_saving_sit_export), ExportJob.SIT) {
                val kind = _sitExportKind.value
                val text = sitExportText(kind)
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                        ?: error(appText(R.string.fieldwatch_view_model_could_not_open_the_selected_location))
                }
            }.onSuccess {
                _export.value = ExportUi(active = false, progress = 1f, saved = true)
            }
        }
    }

    private data class SitExportWindow(
        val name: String,
        val devices: List<Sighting>,
        val path: List<GpsSample>,
    )

    private fun sitExportWindow(now: Long = System.currentTimeMillis()): SitExportWindow {
        val source = app.sits.debriefSource(now)
        if (source != null) {
            return SitExportWindow(source.name, source.devices, source.operatorPath)
        }
        val start = now - DebriefPrompt.WINDOW_MS
        return SitExportWindow(
            appText(R.string.fieldwatch_view_model_last_15_minutes),
            app.devices.devices.value.filter { it.lastSeen >= start || it.firstSeen >= start },
            app.operatorPathCopy().filter { it.at >= start },
        )
    }

    private suspend fun writeSitExport(kind: LogExportKind): Pair<File, String> {
        val text = sitExportText(kind)
        val win = sitExportWindow()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val dir = File(app.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, SitExport.suggestedName(kind, win.name, stamp))
        withContext(Dispatchers.IO) {
            file.writeText(text)
            app.getExternalFilesDir(null)?.let { ext ->
                runCatching { file.copyTo(File(ext, file.name), overwrite = true) }
            }
        }
        return file to win.name
    }

    private suspend fun sitExportText(kind: LogExportKind): String {
        publishExport(0.08f, appText(R.string.fieldwatch_view_model_gathering_sit))
        val win = sitExportWindow()
        val radios = _sitExportRadios.value
        val custom = RadioBookmarks.labels(app.config.watchlist)
        val notes = RadioBookmarks.notes(app.config.watchlist)
        val mine = RadioBookmarks.mineKeys(app.config.watchlist)
        val fleets = app.config.fleets
        val extra = win.devices.filter { it.attentionNotes(fleets).isNotEmpty() }.map { it.key }.toSet()
        val rows = SitExport.rows(win.devices, radios)
        if (kind == LogExportKind.LOG_CSV || kind == LogExportKind.LOG_JSONL) {
            if (rows.isEmpty()) error(SitExport.emptyHint(kind, radios))
            publishExport(0.4f, appText(R.string.fieldwatch_view_model_writing_radios, kind.label, rows.size))
            return withContext(Dispatchers.Default) {
                if (kind == LogExportKind.LOG_CSV) {
                    SitExport.csv(win.devices, radios, custom, notes, extra, fleets, mine)
                } else {
                    SitExport.jsonl(win.devices, radios, custom, notes, extra, fleets, mine)
                }
            }
        }
        val pins = SitExport.mapRadios(win.devices, radios)
        if (pins.isEmpty()) error(SitExport.emptyHint(kind, radios))
        val fmt = GeoExport.formatOf(kind) ?: error(appText(R.string.fieldwatch_view_model_pick_a_map_format))
        val info = listOf(
            "model=${android.os.Build.MODEL}",
            "release=${android.os.Build.VERSION.RELEASE}",
            "device=${android.os.Build.DEVICE}",
        ).joinToString(",")
        publishExport(0.45f, appText(R.string.fieldwatch_view_model_writing_pins, kind.label, pins.size))
        return withContext(Dispatchers.Default) {
            GeoExport.render(
                fmt, pins, emptyMap(), BuildConfig.VERSION_NAME, info,
                customNames = custom,
                observerNotes = notes,
                track = win.path,
                onProgress = { done, total ->
                    if (total <= 0) return@render
                    if (done == total || done % 250 == 0) {
                        val pct = 0.50f + 0.45f * done.toFloat() / total.toFloat()
                        kotlinx.coroutines.runBlocking {
                            publishExport(pct, appText(R.string.fieldwatch_view_model_writing_of, kind.label, done, total))
                        }
                    }
                },
            )
        }
    }

    fun startExport() {
        if (_export.value.active) return
        viewModelScope.launch {
            runExport(appText(R.string.fieldwatch_view_model_logging_paused_preparing_file)) {
                val kind = _logExportKind.value
                val radios = _logExportRadios.value
                val file = when (kind) {
                    LogExportKind.LOG_CSV ->
                        app.logs.exportBundle(asJsonl = false, radios = radios, onProgress = ::reportCopy)
                    LogExportKind.LOG_JSONL ->
                        app.logs.exportBundle(asJsonl = true, radios = radios, onProgress = ::reportCopy)
                    LogExportKind.GPX, LogExportKind.KML, LogExportKind.WIGLE -> writeMapExport(kind, radios)
                }
                app.getExternalFilesDir(null)?.let { ext ->
                    runCatching { file.copyTo(File(ext, file.name), overwrite = true) }
                }
                val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = exportMime()
                    clipData = ClipData.newRawUri("log-export", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, exportSubject(kind))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(active = false, progress = 1f, share = intent)
            }
        }
    }

    fun startSaveToUri(uri: Uri) {
        if (_export.value.active) return
        viewModelScope.launch {
            runExport(appText(R.string.fieldwatch_view_model_logging_paused_saving_to_the_location_you)) {
                val kind = _logExportKind.value
                val radios = _logExportRadios.value
                when (kind) {
                    LogExportKind.LOG_CSV ->
                        app.logs.exportToUri(
                            app.contentResolver, uri, asJsonl = false, radios = radios, onProgress = ::reportCopy,
                        )
                    LogExportKind.LOG_JSONL ->
                        app.logs.exportToUri(
                            app.contentResolver, uri, asJsonl = true, radios = radios, onProgress = ::reportCopy,
                        )
                    LogExportKind.GPX, LogExportKind.KML, LogExportKind.WIGLE -> {
                        val text = mapExportText(kind, radios)
                        withContext(Dispatchers.IO) {
                            app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                                ?: error(appText(R.string.fieldwatch_view_model_could_not_open_the_selected_location))
                        }
                    }
                }
            }.onSuccess {
                _export.value = ExportUi(active = false, progress = 1f, saved = true)
            }
        }
    }

    private fun exportSubject(kind: LogExportKind): String = when (kind) {
        LogExportKind.LOG_CSV -> appText(R.string.fieldwatch_view_model_fieldwatch_log_csv)
        LogExportKind.LOG_JSONL -> appText(R.string.fieldwatch_view_model_fieldwatch_log_json_lines)
        LogExportKind.GPX -> "Fieldwatch GPX"
        LogExportKind.KML -> "Fieldwatch KML"
        LogExportKind.WIGLE -> "Fieldwatch WiGLE CSV"
    }

    private suspend fun writeMapExport(kind: LogExportKind, radios: LogExportRadios): File {
        val text = mapExportText(kind, radios)
        val fmt = GeoExport.formatOf(kind) ?: error(appText(R.string.fieldwatch_view_model_pick_a_map_format))
        val dir = File(app.cacheDir, "export").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val file = File(dir, GeoExport.suggestedName(fmt, stamp))
        withContext(Dispatchers.IO) {
            file.writeText(text)
            app.getExternalFilesDir(null)?.let { ext ->
                runCatching { file.copyTo(File(ext, file.name), overwrite = true) }
            }
        }
        return file
    }

    private suspend fun mapExportText(kind: LogExportKind, radios: LogExportRadios): String {
        val fmt = GeoExport.formatOf(kind) ?: error(appText(R.string.fieldwatch_view_model_pick_a_map_format))
        publishExport(0.02f, appText(R.string.fieldwatch_view_model_reading_log))
        val pins = app.logs.readRadios { copied, total ->
            val pct = 0.05f + 0.40f * copied.toFloat() / total.toFloat().coerceAtLeast(1f)
            publishExport(pct, appText(R.string.fieldwatch_view_model_reading_log_kb_of_kb, copied / 1024, total / 1024))
        }.filter { it.hasPosition && radios.matches(it.kind) }
        if (pins.isEmpty()) {
            val which = when (radios) {
                LogExportRadios.BOTH -> appText(R.string.fieldwatch_view_model_radios)
                LogExportRadios.WIFI -> appText(R.string.fieldwatch_view_model_wi_fi_radios)
                LogExportRadios.BLE -> appText(R.string.fieldwatch_view_model_ble_radios)
            }
            error(appText(R.string.fieldwatch_view_model_no_gps_tagged_settings_tag_detections_with, which))
        }
        val info = listOf(
            "model=${android.os.Build.MODEL}",
            "release=${android.os.Build.VERSION.RELEASE}",
            "device=${android.os.Build.DEVICE}",
            "display=${android.os.Build.DISPLAY}",
            "board=${android.os.Build.BOARD}",
            "brand=${android.os.Build.BRAND}",
        ).joinToString(",")
        publishExport(0.48f, appText(R.string.fieldwatch_view_model_writing_pins, kind.label, pins.size))
        return withContext(Dispatchers.Default) {
            GeoExport.render(
                fmt, pins, emptyMap(), BuildConfig.VERSION_NAME, info,
                customNames = RadioBookmarks.labels(app.config.watchlist),
                onProgress = { done, total ->
                    if (total <= 0) return@render
                    if (done == total || done % 250 == 0) {
                        val pct = 0.50f + 0.45f * done.toFloat() / total.toFloat()
                        kotlinx.coroutines.runBlocking {
                            publishExport(pct, appText(R.string.fieldwatch_view_model_writing_of, kind.label, done, total))
                        }
                    }
                },
            )
        }
    }

    fun clearLogs() {
        if (_export.value.active) return
        viewModelScope.launch {
            _export.value = ExportUi(active = true, progress = 0f, message = appText(R.string.fieldwatch_view_model_clearing_log))
            runCatching { app.logs.clear() }
                .onSuccess { count ->
                    app.devices.bumpLogs(count)
                    _export.value = ExportUi(cleared = true, message = appText(R.string.fieldwatch_view_model_log_cleared))
                }
                .onFailure { err ->
                    _export.value = ExportUi(error = err.message ?: appText(R.string.fieldwatch_view_model_could_not_clear_log))
                }
        }
    }

    fun consumeShare() {
        _export.value = _export.value.copy(share = null)
    }

    fun consumeExportNotice() {
        val cur = _export.value
        _export.value = if (cur.followUpTitle != null) {
            ExportUi(
                noticeTitle = cur.followUpTitle,
                noticeMessage = cur.followUpMessage,
            )
        } else {
            ExportUi()
        }
    }

    private suspend fun reportCopy(copied: Long, total: Long) {
        val pct = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        publishExport(pct, appText(R.string.fieldwatch_view_model_writing_kb_of_kb, (copied / 1024), (total / 1024)))
    }

    private suspend fun publishExport(progress: Float, message: String, job: ExportJob? = null) {
        withContext(Dispatchers.Main.immediate) {
            _export.update { it.copy(
                active = true,
                progress = progress.coerceIn(0f, 1f),
                message = message,
                job = job ?: it.job,
            ) }
        }
        yield()
    }

    private suspend fun <T> runExport(startMessage: String, job: ExportJob = ExportJob.LOG, block: suspend () -> T): Result<T> {
        _export.value = ExportUi(active = true, progress = 0f, message = startMessage, job = job)
        return runCatching { block() }.onFailure { err ->
            _export.value = ExportUi(active = false, error = err.message ?: appText(R.string.fieldwatch_view_model_export_failed))
        }
    }

    fun logBytes(): Long = app.logs.totalBytes()

    fun fleetName(id: String): String = app.config.fleets.firstOrNull { it.id == id }?.name ?: id

    fun fleetDisplayName(id: String): String = app.config.fleets.firstOrNull { it.id == id }?.displayName() ?: id

    fun fleetKind(id: String): SignatureClass? = app.config.fleets.firstOrNull { it.id == id }?.kind

    fun fleetColor(id: String): Int =
        app.config.fleets.firstOrNull { it.id == id }?.colorIndex ?: 0

    fun fleetAttentionNote(id: String): String =
        app.config.fleets.firstOrNull { it.id == id }?.attentionNote.orEmpty()

    fun hasAttention(device: Sighting): Boolean =
        device.fleetIds.any { fleetAttentionNote(it).isNotBlank() }

    fun hasObserverNote(device: Sighting): Boolean =
        watchObserverNoteFor(device.key) != null

    fun fleetHasDecode(id: String): Boolean =
        app.config.fleets.firstOrNull { it.id == id }?.decode != null

    fun attentionNotesFor(device: Sighting): List<Pair<String, String>> =
        device.displayAttentionNotes(app.config.fleets)

    fun signatureNotesFor(device: Sighting): List<Pair<String, String>> =
        device.displaySignatureNotes(app.config.fleets)

    fun isWatched(deviceKey: String): Boolean =
        app.config.watchlist.any { it.deviceKey == deviceKey && it.alert }
    fun isFleetWatched(id: String): Boolean = app.config.watchlist.any { it.fleetId == id }

    fun testWatchBeep() {
        val settings = app.config.settings
        app.alerter.playTestBeep(
            beepOn = settings.alertBeep,
            speakClass = settings.alertVoice,
            voiceWhat = settings.alertVoiceWhat,
        )
    }

    companion object {
        private const val FLASH_MS = 1_000L
    }
}

data class BeepSnap(
    val seq: Int = 0,
    val key: String = "",
)

data class HuntUi(
    val active: Boolean = false,
    val device: Sighting? = null,
    val title: String = "",
    val cue: HuntCue = HuntCue.WAITING,
    val peakRssi: Int = -127,
    val samples: List<RssiSample> = emptyList(),
    val lastSeen: Long = 0L,
)
