package app.fieldwatch.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.attentionNotes
import app.fieldwatch.domain.signatureNotes
import app.fieldwatch.domain.detectionPolicy
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.DISCLAIMER_REV
import app.fieldwatch.domain.disclaimerOk
import app.fieldwatch.domain.FilterEngine
import app.fieldwatch.domain.ClassOutline
import app.fieldwatch.domain.CoTravel
import app.fieldwatch.domain.FilterPreset
import app.fieldwatch.domain.FilterState
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.GeoExport
import app.fieldwatch.domain.FamilyVerdict
import app.fieldwatch.domain.LogRadio
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureCandidate
import app.fieldwatch.domain.SignatureCandidates
import app.fieldwatch.domain.SignatureFamilyHint

import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureEngine
import app.fieldwatch.domain.SignatureListSort
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.ListLine
import app.fieldwatch.domain.ListSort
import app.fieldwatch.domain.StrengthSort
import app.fieldwatch.domain.ViewMode
import app.fieldwatch.domain.WatchTarget
import app.fieldwatch.domain.SitUi
import app.fieldwatch.radio.RadioPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private data class FamilyLogSnap(
    val radios: List<LogRadio> = emptyList(),
    val loaded: Boolean = false,
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
    private val exports = ExportCoordinator(app, viewModelScope, signatures)
    private val reports = ReportsCoordinator(app, viewModelScope, exports)
    private val huntCtl = HuntController(app, clock, viewModelScope)
    val export: StateFlow<ExportUi> = exports.state
    val candidates: StateFlow<CandidatesUi> = reports.candidates
    val sitDiff: StateFlow<SitDiffUi> = reports.sitDiff
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

    val hunt: StateFlow<HuntUi> = huntCtl.hunt

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

    fun startHunt(device: Sighting) = huntCtl.startHunt(device)

    fun resetHunt() = huntCtl.resetHunt()

    fun stopHunt() = huntCtl.stopHunt()

    fun huntTick(beepOn: Boolean, vibrateOn: Boolean) = huntCtl.huntTick(beepOn, vibrateOn)

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

    fun defaultSitName(): String = reports.defaultSitName()

    fun startSit(name: String) = reports.startSit(name)

    fun endSit() = reports.endSit()

    fun renameSit(id: String, name: String) = reports.renameSit(id, name)

    fun deleteSit(id: String) = reports.deleteSit(id)

    fun deleteAllSits() = reports.deleteAllSits()

    fun selectSit(id: String?) = reports.selectSit(id)

    fun startSitDiff(aId: String, bId: String) = reports.startSitDiff(aId, bId)

    fun closeSitDiff() = reports.closeSitDiff()

    fun shareSitDiff() = reports.shareSitDiff()

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

    fun startSignatureCandidates() = reports.startSignatureCandidates()

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

    fun radioNoteFor(deviceKey: String): String =
        RadioBookmarks.noteFor(app.config.watchlist, deviceKey)

    fun saveRadioNote(device: Sighting, note: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.setNote(cfg.watchlist, device.key, note))
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

    fun suggestedSignaturesName(): String = exports.suggestedSignaturesName()

    fun startSignatureShare() = exports.startSignatureShare()

    fun saveSignaturesToUri(uri: Uri) = exports.saveSignaturesToUri(uri)

    fun updateStockCatalogFromGitHub() = exports.updateStockCatalogFromGitHub()

    fun importSignaturesFromUri(uri: Uri) = exports.importSignaturesFromUri(uri)

    fun suggestedSettingsName(): String = exports.suggestedSettingsName()

    fun startSettingsShare() = exports.startSettingsShare()

    fun saveSettingsToUri(uri: Uri) = exports.saveSettingsToUri(uri)

    fun importSettingsFromUri(uri: Uri) = exports.importSettingsFromUri(uri)

    fun suggestedExportName(): String = exports.suggestedExportName()

    fun exportMime(): String = exports.exportMime()

    fun startFieldDebriefPdf() = exports.startFieldDebriefPdf()

    fun startFieldDebrief() = exports.startFieldDebrief()

    fun startGeoExport(format: GeoExport.Format) = exports.startGeoExport(format)

    fun startAiExport() = exports.startAiExport()

    fun startDeviceDetailAiExport(device: Sighting) = exports.startDeviceDetailAiExport(device)

    fun startDeviceDetailShare(device: Sighting) = exports.startDeviceDetailShare(device)

    fun startExport() = exports.startExport()

    fun startSaveToUri(uri: Uri) = exports.startSaveToUri(uri)

    fun clearLogs() = exports.clearLogs()

    fun consumeShare() = exports.consumeShare()

    fun consumeExportNotice() = exports.consumeExportNotice()

    fun logBytes(): Long = exports.logBytes()

    fun fleetName(id: String): String = app.config.fleets.firstOrNull { it.id == id }?.name ?: id

    fun fleetKind(id: String): SignatureClass? = app.config.fleets.firstOrNull { it.id == id }?.kind

    fun fleetColor(id: String): Int =
        app.config.fleets.firstOrNull { it.id == id }?.colorIndex ?: 0

    fun fleetAttentionNote(id: String): String =
        app.config.fleets.firstOrNull { it.id == id }?.attentionNote.orEmpty()

    fun hasAttention(device: Sighting): Boolean =
        device.fleetIds.any { fleetAttentionNote(it).isNotBlank() }

    /** Operator GPS track while scanning. Snapshot — the map re-reads on recomposition. */
    fun operatorPath(): List<GpsSample> = app.operatorPathCopy()

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
