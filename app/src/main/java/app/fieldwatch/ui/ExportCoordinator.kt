package app.fieldwatch.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.fieldwatch.BuildConfig
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.data.CatalogRemote
import app.fieldwatch.data.DebriefPdf
import app.fieldwatch.data.PlaceLookup
import app.fieldwatch.domain.DebriefDoc
import app.fieldwatch.domain.DebriefPlaces
import app.fieldwatch.domain.DebriefPrompt
import app.fieldwatch.domain.DebriefReport
import app.fieldwatch.domain.DebriefWindow
import app.fieldwatch.domain.DeviceDetailPrompt
import app.fieldwatch.domain.DeviceDetailText
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.GeoExport
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.SettingsExchange
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureEngine
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.attentionNotes
import app.fieldwatch.domain.detectionPolicy
import app.fieldwatch.domain.signatureNotes
import app.fieldwatch.domain.toSighting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

/**
 * Everything that builds a file or a share Intent off the radio list:
 * log export/save/clear, debrief (text + PDF), GPX/KML/WiGLE, AI export,
 * device detail share, signature + settings packs, stock catalog update.
 * Owns the single [state] flow the UI consumes for progress and share sheets.
 */
class ExportCoordinator(
    private val app: FieldwatchApp,
    private val scope: CoroutineScope,
    private val signatures: SignatureEngine,
) {
    private val _state = MutableStateFlow(ExportUi())
    val state: StateFlow<ExportUi> = _state

    val active: Boolean get() = _state.value.active

    /** Drop-in share emission for coordinators that already built the Intent. */
    fun share(intent: Intent, title: String) {
        _state.value = ExportUi(share = intent, shareTitle = title)
    }

    fun notice(title: String, message: String) {
        _state.value = ExportUi(noticeTitle = title, noticeMessage = message)
    }

    private fun fleetName(id: String): String =
        app.config.fleets.firstOrNull { it.id == id }?.name ?: id

    private fun radioNoteFor(deviceKey: String): String =
        RadioBookmarks.noteFor(app.config.watchlist, deviceKey)

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
        scope.launch {
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
                _state.value = ExportUi(share = intent, shareTitle = "Fieldwatch signatures")
            }.onFailure { err ->
                _state.value = ExportUi(
                    error = err.message ?: "Could not export signatures",
                    errorTitle = "Could not export signatures",
                )
            }
        }
    }

    fun saveSignaturesToUri(uri: Uri) {
        scope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) { signaturePackJson() }
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not write to the location you picked.")
                }
            }.onSuccess {
                _state.value = ExportUi(
                    noticeTitle = "Signatures saved",
                    noticeMessage = "The pack was written to the folder you picked. Share it with another Fieldwatch or keep it as a backup before Restore defaults.",
                )
            }.onFailure { err ->
                _state.value = ExportUi(
                    error = err.message ?: "Could not save signatures",
                    errorTitle = "Could not save signatures",
                )
            }
        }
    }

    fun updateStockCatalogFromGitHub() {
        if (active) return
        scope.launch {
            if (!PlaceLookup.online(app)) {
                _state.value = ExportUi(
                    errorTitle = "No internet",
                    error = "No internet. Use Import signatures from a file.",
                )
                return@launch
            }
            _state.value = ExportUi(
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
                _state.value = if (result.alreadyLatest) {
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
                _state.value = if (access) {
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
        scope.launch {
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
                _state.value = ExportUi(
                    noticeTitle = "Signatures imported",
                    noticeMessage = summary,
                )
            }.onFailure { err ->
                _state.value = ExportUi(
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
        scope.launch {
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
                _state.value = ExportUi(share = intent, shareTitle = "Fieldwatch settings")
            }.onFailure { err ->
                _state.value = ExportUi(
                    error = err.message ?: "Could not export settings",
                    errorTitle = "Could not export settings",
                )
            }
        }
    }

    fun saveSettingsToUri(uri: Uri) {
        scope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) { settingsPackJson() }
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not write to the location you picked.")
                }
            }.onSuccess {
                _state.value = ExportUi(
                    noticeTitle = "Settings saved",
                    noticeMessage = "The pack was written to the folder you picked. Keep it for a factory reset or a new phone. Import settings on the new install. Signatures are a separate pack.",
                )
            }.onFailure { err ->
                _state.value = ExportUi(
                    error = err.message ?: "Could not save settings",
                    errorTitle = "Could not save settings",
                )
            }
        }
    }

    fun importSettingsFromUri(uri: Uri) {
        scope.launch {
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
                _state.value = ExportUi(
                    noticeTitle = "Settings imported",
                    noticeMessage = summary,
                )
            }.onFailure { err ->
                _state.value = ExportUi(
                    error = err.message ?: "Could not import settings",
                    errorTitle = "Could not import settings",
                )
            }
        }
    }

    fun suggestedExportName(): String = app.logs.suggestedExportName()

    fun exportMime(): String = app.logs.exportMime()

    fun startFieldDebriefPdf() {
        if (active) return
        scope.launch {
            _state.value = busy("Writing debrief PDF…")
            runCatching {
                val doc = fieldDebriefDoc { msg ->
                    _state.value = busy(msg)
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
                _state.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = "Debrief PDF",
                )
            }.onFailure { err ->
                _state.value = ExportUi(error = err.message ?: "Could not write debrief PDF")
            }
        }
    }

    fun startFieldDebrief() {
        if (active) return
        scope.launch {
            _state.value = busy("Writing debrief…")
            runCatching {
                val doc = fieldDebriefDoc { msg ->
                    _state.value = busy(msg)
                }
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, debriefSubject(doc))
                    putExtra(Intent.EXTRA_TEXT, doc.toPlainText())
                }
            }.onSuccess { intent ->
                _state.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = "Debrief",
                )
            }.onFailure { err ->
                _state.value = ExportUi(error = err.message ?: "Could not write debrief")
            }
        }
    }

    fun startGeoExport(format: GeoExport.Format) {
        if (active) return
        scope.launch {
            _state.value = busy("Reading log…")
            runCatching {
                val radios = app.logs.readRadios()
                if (radios.none { it.hasPosition }) {
                    _state.value = ExportUi(
                        noticeTitle = "No GPS points yet",
                        noticeMessage = "Map exports pin each radio where this phone heard it. Turn on Settings → Tag location, scan a while, then try again.",
                    )
                    return@launch
                }
                _state.value = busy("Matching signatures…")
                val fleets = app.config.fleets
                val names = withContext(Dispatchers.Default) {
                    signatures.match(radios.map { it.toSighting() }, fleets)
                        .mapValues { (_, ids) -> ids.map { id -> fleetName(id) }.sorted() }
                }
                _state.value = busy("Writing ${format.label}…")
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
                _state.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = "${format.label} export",
                )
            }.onFailure { err ->
                _state.value = ExportUi(error = err.message ?: "Could not write ${format.label} export")
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
        if (active) return
        scope.launch {
            _state.value = busy("Building AI export prompt…")
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
                    _state.value = busy("Looking up place names…")
                    val found = PlaceLookup.lookup(app, path, devices, now) { msg ->
                        _state.value = busy(msg)
                    }
                    _state.value = busy("Building AI export prompt…")
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
                _state.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = "AI Export",
                )
            }.onFailure { err ->
                _state.value = ExportUi(error = err.message ?: "Could not build AI export")
            }
        }
    }

    fun startDeviceDetailAiExport(device: Sighting) {
        if (active) return
        scope.launch {
            _state.value = busy("Building AI export prompt…")
            runCatching {
                val settings = app.config.settings
                val names = device.fleetIds.map { fleetName(it) }
                val attention = device.attentionNotes(app.config.fleets)
                val notes = device.signatureNotes(app.config.fleets)
                val places = if (settings.demoMode) {
                    DebriefPlaces.Off
                } else if (settings.onlineLookup && (settings.tagLocation || device.latitude != null)) {
                    _state.value = busy("Looking up place names…")
                    val found = PlaceLookup.lookup(app, app.operatorPathCopy(), listOf(device), System.currentTimeMillis()) { msg ->
                        _state.value = busy(msg)
                    }
                    _state.value = busy("Building AI export prompt…")
                    found
                } else {
                    DebriefPlaces.Off
                }
                val text = withContext(Dispatchers.Default) {
                    val raw = DeviceDetailPrompt.build(
                        device, names, settings, places, attentionNotes = attention,
                        signatureNotes = notes,
                        fleets = app.config.fleets,
                        operatorNote = radioNoteFor(device.key),
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
                _state.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = "AI Export",
                )
            }.onFailure { err ->
                _state.value = ExportUi(error = err.message ?: "Could not build AI export")
            }
        }
    }

    fun startDeviceDetailShare(device: Sighting) {
        if (active) return
        scope.launch {
            runCatching {
                val settings = app.config.settings
                val names = device.fleetIds.map { fleetName(it) }
                val attention = device.attentionNotes(app.config.fleets)
                val notes = device.signatureNotes(app.config.fleets)
                val text = withContext(Dispatchers.Default) {
                    val raw = DeviceDetailText.build(
                        device, names, attentionNotes = attention, signatureNotes = notes,
                        fleets = app.config.fleets,
                        operatorNote = radioNoteFor(device.key),
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
                _state.value = ExportUi(share = intent, shareTitle = "Device detail")
            }.onFailure { err ->
                _state.value = ExportUi(error = err.message ?: "Could not share device detail")
            }
        }
    }

    fun startExport() {
        if (active) return
        scope.launch {
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
                _state.value = ExportUi(active = false, progress = 1f, share = intent)
            }
        }
    }

    fun startSaveToUri(uri: Uri) {
        if (active) return
        scope.launch {
            runExport("Logging paused · saving to the location you picked…") {
                app.logs.exportToUri(app.contentResolver, uri, ::reportCopy)
            }.onSuccess {
                _state.value = ExportUi(active = false, progress = 1f, saved = true)
            }
        }
    }

    fun clearLogs() {
        if (active) return
        scope.launch {
            _state.value = ExportUi(active = true, progress = 0f, message = "Clearing log…")
            runCatching { app.logs.clear() }
                .onSuccess { count ->
                    app.devices.bumpLogs(count)
                    _state.value = ExportUi(cleared = true, message = "Log cleared")
                }
                .onFailure { err ->
                    _state.value = ExportUi(error = err.message ?: "Could not clear log")
                }
        }
    }

    fun consumeShare() {
        _state.value = _state.value.copy(share = null)
    }

    fun consumeExportNotice() {
        _state.value = ExportUi()
    }

    private fun reportCopy(copied: Long, total: Long) {
        val pct = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        _state.value = ExportUi(
            active = true,
            progress = pct,
            message = "Copying ${(copied / 1024)} KB of ${(total / 1024)} KB",
        )
    }

    private suspend fun <T> runExport(startMessage: String, block: suspend () -> T): Result<T> {
        _state.value = ExportUi(active = true, progress = 0f, message = startMessage)
        return runCatching { block() }.onFailure { err ->
            _state.value = ExportUi(active = false, error = err.message ?: "Export failed")
        }
    }

    fun logBytes(): Long = app.logs.totalBytes()
}
