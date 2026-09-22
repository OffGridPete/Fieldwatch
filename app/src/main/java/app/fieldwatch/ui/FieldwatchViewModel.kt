package app.fieldwatch.ui

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fieldwatch.BuildConfig
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.attentionNotes
import app.fieldwatch.domain.signatureNotes
import app.fieldwatch.domain.detectionPolicy
import app.fieldwatch.data.CatalogRemote
import app.fieldwatch.data.DebriefPdf
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
import app.fieldwatch.domain.ClassOutline
import app.fieldwatch.domain.CoTravel
import app.fieldwatch.domain.FilterPreset
import app.fieldwatch.domain.FilterState
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.GeoExport
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
import app.fieldwatch.domain.ListSort
import app.fieldwatch.domain.StrengthSort
import app.fieldwatch.domain.ViewMode
import app.fieldwatch.domain.WatchTarget
import app.fieldwatch.domain.toSighting
import app.fieldwatch.domain.DebriefWindow
import app.fieldwatch.domain.Sit
import app.fieldwatch.domain.SitUi
import app.fieldwatch.radio.RadioPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

data class ExportUi(
    val active: Boolean = false,
    val progress: Float = 0f,
    /** True: spinning wait (Debrief / AI Export). False: determinate bar (log). */
    val spinner: Boolean = false,
    val message: String = "",
    val share: Intent? = null,
    val shareTitle: String = "Export Fieldwatch logs",
    val error: String? = null,
    val errorTitle: String? = null,
    val cleared: Boolean = false,
    val saved: Boolean = false,
    val noticeTitle: String? = null,
    val noticeMessage: String? = null,
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

class FieldwatchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FieldwatchApp
    private val filters = FilterEngine()
    private val signatures = SignatureEngine()
    private val selectedKey = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow<Fleet?>(null)
    private var draftFromCandidates = false
    private val _export = MutableStateFlow(ExportUi())
    val export: StateFlow<ExportUi> = _export
    private val _candidates = MutableStateFlow(CandidatesUi())
    val candidates: StateFlow<CandidatesUi> = _candidates
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

    private val liveUi: StateFlow<FieldwatchUi> = combine(
        combine(app.devices.devices, app.devices.stats, app.config.config) { devices, stats, config ->
            Triple(devices, stats, config)
        },
        combine(selectedKey, draft, app.arrivals, clock) { sel, fleetDraft, arr, now ->
            arrayOf(sel, fleetDraft, arr, now)
        },
        lastAlertAt,
        app.tak.status,
    ) { tripleA, quad, alerts, takStatus ->
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
        val filtered = labeled.filter { device ->
            if (!filters.pass(
                    device,
                    config.filter,
                    moveCtx,
                    now,
                    classById,
                    watchNames.keys,
                    RadioBookmarks.watchedFleetIds(config.watchlist),
                    RadioBookmarks.alertDeviceKeys(config.watchlist),
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
            title = device?.listTitle() ?: "Hunt",
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
                clock.value = System.currentTimeMillis()
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

    private fun publishSitNotice() {
        val notice = app.sits.ui.value.notice ?: return
        _export.value = ExportUi(noticeTitle = "Sits", noticeMessage = notice)
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
            draft.value = null
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
            draft.value = null
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
        draft.value = signatures.suggestFleet(device)
    }

    fun beginCreateFromCandidate(candidate: SignatureCandidate) {
        draftFromCandidates = true
        draft.value = SignatureCandidates.suggestFleet(candidate)
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
                _candidates.value = CandidatesUi(error = err.message ?: "Could not read the log")
            }
        }
    }

    fun beginNewFleet() {
        draftFromCandidates = false
        draft.value = DefaultCatalog.newBlankFleet()
    }

    fun editFleet(fleet: Fleet) {
        draftFromCandidates = false
        draft.value = fleet
    }

    fun saveFleetKeepDraft(fleet: Fleet) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val existing = cfg.fleets.indexOfFirst { it.id == fleet.id }
                val next = cfg.fleets.toMutableList()
                if (existing >= 0) next[existing] = fleet else next += fleet
                cfg.copy(fleets = next)
            }
            draft.value = fleet
        }
    }

    fun cancelDraft() {
        draft.value = null
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

    fun renameRadioBookmark(id: String, name: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.rename(cfg.watchlist, id, name))
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
                    putExtra(Intent.EXTRA_SUBJECT, "Fieldwatch signatures")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(share = intent, shareTitle = "Fieldwatch signatures")
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: "Could not export signatures",
                    errorTitle = "Could not export signatures",
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
                    } ?: error("Could not write to the location you picked.")
                }
            }.onSuccess {
                _export.value = ExportUi(
                    noticeTitle = "Signatures saved",
                    noticeMessage = "The pack was written to the folder you picked. Share it with another Fieldwatch or keep it as a backup before Restore defaults.",
                )
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: "Could not save signatures",
                    errorTitle = "Could not save signatures",
                )
            }
        }
    }

    fun updateStockCatalogFromGitHub() {
        if (_export.value.active) return
        viewModelScope.launch {
            if (!PlaceLookup.online(app)) {
                _export.value = ExportUi(
                    errorTitle = "No internet",
                    error = "No internet. Use Import signatures from a file.",
                )
                return@launch
            }
            _export.value = ExportUi(
                active = true,
                spinner = true,
                message = "Updating stock catalog…",
            )
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    CatalogRemote.fetch(
                        userAgent = "Fieldwatch/${BuildConfig.VERSION_NAME}",
                    )
                }
                val pack = SignatureExchange.parse(text)
                val result = app.config.overlayStockCatalog(pack)
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
                        noticeTitle = "Already on the latest catalog",
                        noticeMessage = "Already on catalog ${result.catalogVersion}. Nothing to update.",
                    )
                } else {
                    val bits = mutableListOf<String>()
                    if (result.updated > 0) bits += "updated ${result.updated}"
                    if (result.added > 0) bits += "added ${result.added}"
                    val change = if (bits.isEmpty()) "No stock rows changed."
                    else bits.joinToString(" · ").replaceFirstChar { it.uppercase() } + "."
                    ExportUi(
                        noticeTitle = "Catalog updated",
                        noticeMessage = "Stock catalog is now ${result.catalogVersion}. $change " +
                            "Bookmarks and Settings were not changed.",
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
                        errorTitle = "Could not reach GitHub",
                        error = "Could not reach the catalog on GitHub. Try again later, or use Import signatures from a file.",
                    )
                } else {
                    ExportUi(
                        errorTitle = "Could not import catalog",
                        error = err.message ?: "Could not import catalog.",
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
                    } ?: error("Could not read that file.")
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
                    noticeTitle = "Signatures imported",
                    noticeMessage = summary,
                )
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: "Could not import signatures",
                    errorTitle = "Could not import signatures",
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
                    putExtra(Intent.EXTRA_SUBJECT, "Fieldwatch settings")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(share = intent, shareTitle = "Fieldwatch settings")
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: "Could not export settings",
                    errorTitle = "Could not export settings",
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
                    } ?: error("Could not write to the location you picked.")
                }
            }.onSuccess {
                _export.value = ExportUi(
                    noticeTitle = "Settings saved",
                    noticeMessage = "The pack was written to the folder you picked. Keep it for a factory reset or a new phone. Import settings on the new install. Signatures are a separate pack.",
                )
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: "Could not save settings",
                    errorTitle = "Could not save settings",
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
                    } ?: error("Could not read that file.")
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
                    noticeTitle = "Settings imported",
                    noticeMessage = summary,
                )
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: "Could not import settings",
                    errorTitle = "Could not import settings",
                )
            }
        }
    }

    fun suggestedExportName(): String = app.logs.suggestedExportName()

    fun exportMime(): String = app.logs.exportMime()

    fun startFieldDebriefPdf() {
        if (_export.value.active) return
        viewModelScope.launch {
            _export.value = busy("Writing debrief PDF…")
            runCatching {
                val doc = fieldDebriefDoc { msg ->
                    _export.value = busy(msg)
                }
                val dir = File(app.cacheDir, "debrief").apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val file = File(dir, "fieldwatch-debrief-$stamp.pdf")
                withContext(Dispatchers.Default) { DebriefPdf.write(doc, file) }
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
                    shareTitle = "Debrief PDF",
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: "Could not write debrief PDF")
            }
        }
    }

    fun startFieldDebrief() {
        if (_export.value.active) return
        viewModelScope.launch {
            _export.value = busy("Writing debrief…")
            runCatching {
                val doc = fieldDebriefDoc { msg ->
                    _export.value = busy(msg)
                }
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
                    shareTitle = "Debrief",
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: "Could not write debrief")
            }
        }
    }

    fun startGeoExport(format: GeoExport.Format) {
        if (_export.value.active) return
        viewModelScope.launch {
            _export.value = busy("Reading log…")
            runCatching {
                val radios = app.logs.readRadios()
                if (radios.none { it.hasPosition }) {
                    _export.value = ExportUi(
                        noticeTitle = "No GPS points yet",
                        noticeMessage = "Map exports pin each radio where this phone heard it. Turn on Settings → Tag location, scan a while, then try again.",
                    )
                    return@launch
                }
                _export.value = busy("Matching signatures…")
                val fleets = app.config.fleets
                val names = withContext(Dispatchers.Default) {
                    signatures.match(radios.map { it.toSighting() }, fleets)
                        .mapValues { (_, ids) -> ids.map { id -> fleetName(id) }.sorted() }
                }
                _export.value = busy("Writing ${format.label}…")
                val text = withContext(Dispatchers.Default) {
                    GeoExport.render(
                        format = format,
                        radios = radios,
                        names = names,
                        appVersion = BuildConfig.VERSION_NAME,
                        deviceInfo = "model=${Build.MODEL},release=${Build.VERSION.RELEASE}," +
                            "device=${Build.DEVICE},display=${Build.DISPLAY}," +
                            "board=${Build.BOARD},brand=${Build.BRAND}",
                    )
                }
                val dir = File(app.cacheDir, "export").apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val file = File(dir, GeoExport.suggestedName(format, stamp))
                withContext(Dispatchers.IO) { file.writeText(text) }
                val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = format.mime
                    clipData = ClipData.newRawUri(format.label, uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Fieldwatch ${format.label} export")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = "${format.label} export",
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: "Could not write ${format.label} export")
            }
        }
    }

    private fun busy(message: String) = ExportUi(active = true, spinner = true, message = message)

    private fun debriefSubject(doc: DebriefDoc): String =
        if (doc.heading.startsWith("FIELDWATCH SIT")) doc.heading else "Fieldwatch field debrief — last 15 minutes"

    private suspend fun fieldDebriefDoc(onLookup: (String) -> Unit): DebriefDoc {
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
            onLookup("Looking up place names…")
            val found = PlaceLookup.lookup(app, path, devices, now, onProgress = onLookup)
            onLookup("Writing debrief…")
            found
        } else {
            DebriefPlaces.Off
        }
        return withContext(Dispatchers.Default) {
            DebriefReport.document(
                devices = devices,
                fleets = fleets,
                settings = settings,
                operatorPath = path,
                now = now,
                places = places,
                window = window,
            ).withDemoMacs(devices.map { it.mac }, settings.demoMode)
        }
    }

    fun startAiExport() {
        if (_export.value.active) return
        viewModelScope.launch {
            _export.value = busy("Building AI export prompt…")
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
                    _export.value = busy("Looking up place names…")
                    val found = PlaceLookup.lookup(app, path, devices, now) { msg ->
                        _export.value = busy(msg)
                    }
                    _export.value = busy("Building AI export prompt…")
                    found
                } else {
                    DebriefPlaces.Off
                }
                val text = withContext(Dispatchers.Default) {
                    val raw = DebriefPrompt.build(
                        devices = devices,
                        fleets = fleets,
                        settings = settings,
                        now = now,
                        operatorPath = path,
                        places = places,
                        window = window,
                    )
                    val masked = Geo.redactCoordsIn(
                        MacUtil.redactMacsIn(raw, devices.map { it.mac }, settings.demoMode),
                        settings.demoMode,
                    )
                    if (settings.demoMode) {
                        "Privacy mode: MAC tails are **:**:**. GPS coordinates are masked. Logs on the phone are unchanged.\n\n$masked"
                    } else {
                        masked
                    }
                }
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        if (window != null) "Fieldwatch AI export — sit ${window.sitName}"
                        else "Fieldwatch AI export — last 15 minutes",
                    )
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = "AI Export",
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: "Could not build AI export")
            }
        }
    }

    fun startDeviceDetailAiExport(device: Sighting) {
        if (_export.value.active) return
        viewModelScope.launch {
            _export.value = busy("Building AI export prompt…")
            runCatching {
                val settings = app.config.settings
                val names = device.fleetIds.map { fleetName(it) }
                val attention = attentionNotesFor(device)
                val notes = signatureNotesFor(device)
                val places = if (settings.demoMode) {
                    DebriefPlaces.Off
                } else if (settings.onlineLookup && (settings.tagLocation || device.latitude != null)) {
                    _export.value = busy("Looking up place names…")
                    val found = PlaceLookup.lookup(app, app.operatorPathCopy(), listOf(device), System.currentTimeMillis()) { msg ->
                        _export.value = busy(msg)
                    }
                    _export.value = busy("Building AI export prompt…")
                    found
                } else {
                    DebriefPlaces.Off
                }
                val text = withContext(Dispatchers.Default) {
                    val raw = DeviceDetailPrompt.build(
                        device, names, settings, places, attentionNotes = attention,
                        signatureNotes = notes,
                        fleets = app.config.fleets,
                    )
                    val masked = Geo.redactCoordsIn(
                        MacUtil.redactMacIn(raw, device.mac, settings.demoMode),
                        settings.demoMode,
                    )
                    if (settings.demoMode) {
                        "Privacy mode: MAC tails are **:**:**. GPS coordinates are masked. Logs on the phone are unchanged.\n\n$masked"
                    } else {
                        masked
                    }
                }
                val title = MacUtil.redactMacIn(device.listTitle(names), device.mac, settings.demoMode)
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Fieldwatch AI export — $title")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = "AI Export",
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: "Could not build AI export")
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
                    )
                    val masked = Geo.redactCoordsIn(
                        MacUtil.redactMacIn(raw, device.mac, settings.demoMode),
                        settings.demoMode,
                    )
                    if (settings.demoMode) {
                        "Privacy mode: MAC tails are **:**:**. GPS coordinates are masked. Logs on the phone are unchanged.\n\n$masked"
                    } else {
                        masked
                    }
                }
                val title = MacUtil.redactMacIn(device.listTitle(names), device.mac, settings.demoMode)
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Fieldwatch device detail — $title")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(share = intent, shareTitle = "Device detail")
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: "Could not share device detail")
            }
        }
    }

    fun startExport() {
        if (_export.value.active) return
        viewModelScope.launch {
            runExport("Logging paused · preparing file…") {
                val file = app.logs.exportBundle(::reportCopy)
                val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = app.logs.exportMime()
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Fieldwatch log export")
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
            runExport("Logging paused · saving to the location you picked…") {
                app.logs.exportToUri(app.contentResolver, uri, ::reportCopy)
            }.onSuccess {
                _export.value = ExportUi(active = false, progress = 1f, saved = true)
            }
        }
    }

    fun clearLogs() {
        if (_export.value.active) return
        viewModelScope.launch {
            _export.value = ExportUi(active = true, progress = 0f, message = "Clearing log…")
            runCatching { app.logs.clear() }
                .onSuccess { count ->
                    app.devices.bumpLogs(count)
                    _export.value = ExportUi(cleared = true, message = "Log cleared")
                }
                .onFailure { err ->
                    _export.value = ExportUi(error = err.message ?: "Could not clear log")
                }
        }
    }

    fun consumeShare() {
        _export.value = _export.value.copy(share = null)
    }

    fun consumeExportNotice() {
        _export.value = ExportUi()
    }

    private fun reportCopy(copied: Long, total: Long) {
        val pct = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        _export.value = ExportUi(
            active = true,
            progress = pct,
            message = "Copying ${(copied / 1024)} KB of ${(total / 1024)} KB",
        )
    }

    private suspend fun <T> runExport(startMessage: String, block: suspend () -> T): Result<T> {
        _export.value = ExportUi(active = true, progress = 0f, message = startMessage)
        return runCatching { block() }.onFailure { err ->
            _export.value = ExportUi(active = false, error = err.message ?: "Export failed")
        }
    }

    fun logBytes(): Long = app.logs.totalBytes()

    fun fleetName(id: String): String = app.config.fleets.firstOrNull { it.id == id }?.name ?: id

    fun fleetKind(id: String): SignatureClass? = app.config.fleets.firstOrNull { it.id == id }?.kind

    fun fleetColor(id: String): Int =
        app.config.fleets.firstOrNull { it.id == id }?.colorIndex ?: 0

    fun fleetAttentionNote(id: String): String =
        app.config.fleets.firstOrNull { it.id == id }?.attentionNote.orEmpty()

    fun hasAttention(device: Sighting): Boolean =
        device.fleetIds.any { fleetAttentionNote(it).isNotBlank() }

    fun fleetHasDecode(id: String): Boolean =
        app.config.fleets.firstOrNull { it.id == id }?.decode != null

    fun attentionNotesFor(device: Sighting): List<Pair<String, String>> =
        device.attentionNotes(app.config.fleets)

    fun signatureNotesFor(device: Sighting): List<Pair<String, String>> =
        device.signatureNotes(app.config.fleets)

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
