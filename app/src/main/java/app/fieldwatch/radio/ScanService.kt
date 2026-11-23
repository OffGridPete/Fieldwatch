package app.fieldwatch.radio

import app.fieldwatch.domain.LanguageText

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.fieldwatch.MainActivity
import app.fieldwatch.R
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.Observation
import app.fieldwatch.domain.RadioDb
import app.fieldwatch.domain.TakFlood
import app.fieldwatch.domain.TakGnss
import app.fieldwatch.domain.TrustedNets
import app.fieldwatch.domain.TwinHear
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.TrackerWatch
import app.fieldwatch.domain.trustedOpt
import android.os.BatteryManager
import app.fieldwatch.domain.Automation
import app.fieldwatch.domain.BatteryFloor
import app.fieldwatch.domain.BatteryFloorLive
import app.fieldwatch.domain.BatteryFloorStep
import app.fieldwatch.domain.BatteryMark
import app.fieldwatch.domain.BatteryRestore
import app.fieldwatch.domain.BatterySample
import app.fieldwatch.domain.ScanIntensity
import app.fieldwatch.domain.detectionPolicy
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScanService : LifecycleService() {
    private lateinit var wifi: WifiRadio
    private lateinit var ble: BleRadio
    private var loop: Job? = null
    private var pump: Job? = null
    private var bleStartJob: Job? = null
    private var gnssStartJob: Job? = null
    private var gnssReady = false
    private val locationQuiet = LocationQuietWindow(nowMs = SystemClock::elapsedRealtime)
    private var locationWatching = false
    private var lastIntensity: ScanIntensity? = null
    @Volatile private var batterySample: BatterySample? = null
    private var batterySlowed = false
    private var batteryStopping = false
    private var batteryWatching = false
    private var lastNotifAt = 0L
    private var lastNotifText = ""
    private var locationTypeOn = false
    private var foregroundRefusal: SecurityException? = null
    private val inbound = Channel<Observation>(512, BufferOverflow.DROP_OLDEST)
    private val publishGate = Any()
    private var publishJob: Job? = null
    @Volatile private var lastPublishAt = 0L
    @Volatile private var wifiBatchPending = false

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val app = application as FieldwatchApp
        wifi = WifiRadio(
            this,
            onObservation = { offer(it) },
            onScanFinished = { _, _ -> },
            onFreshScan = { batch ->
                val now = System.currentTimeMillis()
                app.wifiFlood.scan(batch, now)
                app.sits.noteFloods(app.wifiFlood.bursts())
            },
        )
        ble = BleRadio(
            this,
            onObservation = { offer(it) },
            onError = { msg ->
                app.devices.setRadioHold(ble = true)
                app.devices.setScanning(true, msg)
            },
        )
        registerBatteryWatch()
        startAsForeground()
        val knownUas = app.devices.devices.value.mapNotNull { row ->
            row.payloadUasId?.trim()?.takeIf { it.isNotEmpty() }
        }
        app.remoteIdSwarm.beginSession(knownUas)
        app.alerter.clearSwarmNote()
        app.devices.setScanning(true, wifi.throttleHint())
        publishScanRadio()
        val locationOn = runCatching { app.systemLocationOn() }.getOrDefault(false)
        locationQuiet.seed(locationOn)
        registerLocationWatch()
        app.syncLocationUpdates()
        if (locationOn) restartRadios()
        gnssStartJob = lifecycleScope.launch {
            delay(GNSS_START_DELAY_MS)
            gnssReady = true
            if (isActive && !locationQuiet.locationHeld()) app.syncGnss()
        }
        pump = lifecycleScope.launch(Dispatchers.Default) { drainInbound() }
        loop = lifecycleScope.launch(Dispatchers.Default) {
            while (isActive) {
                val decision = applyBatteryFloor()
                if (decision == null) {
                    stopScanning()
                    break
                }
                val locationOn = runCatching { app.systemLocationOn() }.getOrDefault(false)
                noteLocation(locationOn)
                val hold = locationQuiet.radioHoldMs()
                if (hold > 0L) {
                    app.devices.noteQuietScan(false, false)
                    delay(hold.coerceAtMost(2_000L))
                    continue
                }
                var resumeRadios = false
                if (locationOn) {
                    resumeRadios = locationQuiet.takeRadioResume()
                    if (resumeRadios) restartRadios()
                    val settings = app.config.settings
                    val effective = decision.effective
                    if (lastIntensity != effective) restartRadios()
                    val unthrottled = !WifiRadio.osScanThrottled(this@ScanService)
                    val ownPace = effective == settings.intensity
                    val wifiMin = if (unthrottled && ownPace) {
                        WifiRadio.FAST_INTERVAL_MS
                    } else when (effective) {
                        ScanIntensity.PERFORMANCE -> 30_000L
                        ScanIntensity.BALANCED -> 40_000L
                        ScanIntensity.SAVER -> 55_000L
                    }
                    if (!locationQuiet.radiosHeld()) {
                        wifi.requestScan(wifiMin, unthrottled = unthrottled && ownPace)
                    }
                    if (!bleStartPending() && ble.needsRestart() && !locationQuiet.radiosHeld()) {
                        app.devices.setRadioHold(ble = true)
                        if (ble.isRunning()) {
                            ble.stop()
                            delay(ble.restartBackoffMs())
                        }
                        when {
                            locationQuiet.radiosHeld() -> Unit
                            locationQuiet.takeRadioResume() -> {
                                // The shade toggle landed during this backoff. Stagger BLE
                                // instead of starting it on the same pass as the GPS listener.
                                resumeRadios = true
                                restartRadios()
                            }
                            else -> ble.start(decision.effective)
                        }
                    }
                }
                app.devices.setRadioHold(
                    wifi = wifi.waitingOnOs(),
                    ble = ble.holding(),
                )
                val hint = listOf(
                    wifi.throttleHint(),
                    ble.statusHint(),
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                app.devices.setScanning(true, hint)
                publishScanRadio()
                val swarmNow = System.currentTimeMillis()
                app.pairingFlood.tick(swarmNow, app.floodSpeed())
                app.sits.noteFloods(app.pairingFlood.bursts())
                app.sits.noteFloods(app.wifiFlood.bursts())
                app.announceSwarm(app.passRemoteIdSwarm(swarmNow))
                val locationHeld = locationQuiet.locationHeld()
                val resumeLocation = !resumeRadios && locationQuiet.takeLocationResume()
                if (resumeLocation) {
                    app.resubscribeLocationUpdates()
                } else if (
                    !resumeRadios &&
                    !locationHeld &&
                    !app.locationUpdatesActive() &&
                    app.locationTagWanted() &&
                    locationOn
                ) {
                    app.syncLocationUpdates()
                }
                if (gnssReady && !locationHeld && !resumeLocation && !resumeRadios) {
                    app.syncGnss()
                    app.sits.noteGnss(app.gnss.marks())
                }
                app.tickCell(locationHeld)
                ageTrackers(app)
                publishNow()
                delay(2_000L)
            }
        }
    }

    private val locationWatch = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val app = application as? FieldwatchApp ?: return
            noteLocation(runCatching { app.systemLocationOn() }.getOrDefault(false))
        }
    }

    private fun registerLocationWatch() {
        if (locationWatching) return
        val filter = IntentFilter().apply {
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                locationWatch,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            locationWatching = true
        }
    }

    private fun unregisterLocationWatch() {
        if (!locationWatching) return
        runCatching { unregisterReceiver(locationWatch) }
        locationWatching = false
    }

    /** Stop both radios on the way down, and again for the quiet window on the way up. */
    private fun noteLocation(on: Boolean) {
        val rose = locationQuiet.observe(on)
        if (rose || !on) holdRadiosForLocation()
    }

    private fun holdRadiosForLocation() {
        bleStartJob?.cancel()
        bleStartJob = null
        if (::ble.isInitialized) ble.stop()
        if (::wifi.isInitialized) wifi.stop()
    }

    private fun restartRadios() {
        val app = application as FieldwatchApp
        if (locationQuiet.radiosHeld()) return
        if (!runCatching { app.systemLocationOn() }.getOrDefault(false)) return
        val intensity = effectiveIntensity()
        lastIntensity = intensity
        wifi.start()
        if (ble.isRunning()) {
            ble.start(intensity)
            return
        }
        if (bleStartJob?.isActive == true) return
        // Wi-Fi scan + GNSS + BLE startScan on the same frame has crashed some
        // OEM stacks at launch when Bluetooth and Location are both on.
        bleStartJob = lifecycleScope.launch(Dispatchers.Default) {
            delay(BLE_START_STAGGER_MS)
            if (!isActive || locationQuiet.radiosHeld()) return@launch
            val app = application as FieldwatchApp
            if (!runCatching { app.systemLocationOn() }.getOrDefault(false)) return@launch
            ble.start(effectiveIntensity())
            if (locationQuiet.radiosHeld() || !runCatching { app.systemLocationOn() }.getOrDefault(false)) {
                ble.stop()
            }
        }
    }

    private fun bleStartPending(): Boolean =
        bleStartJob?.isActive == true && !ble.isRunning()

    private fun publishScanRadio() {
        val app = application as FieldwatchApp
        app.devices.setScanRadio(
            wifiOn = wifi.radioOn(),
            wifiWaiting = wifi.waitingOnOs(),
            bleOn = ble.radioOn(),
            bleRunning = ble.isRunning(),
            bleParked = ble.statusHint() == "BLE parked · restarting",
            bleRetrying = ble.statusHint() == "BLE retrying" || ble.statusHint() == "BLE unavailable",
            bleStarting = bleStartPending() && !ble.isRunning(),
            bleHitsLastMin = ble.hitsLastMinute(System.currentTimeMillis()),
        )
    }

    private fun offer(observation: Observation) {
        if (observation.mac.isBlank()) return
        inbound.trySend(observation)
    }

    private suspend fun drainInbound() {
        val batch = ArrayList<Observation>(80)
        try {
        while (true) {
            batch.clear()
            batch.add(inbound.receive())
            while (batch.size < 80) {
                val next = inbound.tryReceive().getOrNull() ?: break
                batch.add(next)
            }
            try {
                val app = application as FieldwatchApp
                if (app.config.settings.tagLocation) app.refreshFix()
                val fix = if (app.config.settings.tagLocation) app.lastFix else null
                val tagged = if (fix == null) {
                    batch
                } else {
                    batch.map { it.copy(latitude = fix.first, longitude = fix.second) }
                }
                val fleets = app.config.fleets
                val settings = app.config.settings
                val seen = app.devices.ingestBatch(tagged, fleets, settings.staleSec)
                for (i in seen.indices) {
                    val row = seen[i]
                    if (row.kind != RadioKind.BLE || row.hitCount != 1) continue
                    app.pairingFlood.consider(tagged[i], row.lastSeen, app.floodSpeed())
                }
                app.sits.noteFloods(app.pairingFlood.bursts())
                app.sits.ingest(seen, fleets, app.config.watchlist)
                considerTrusted(app, tagged)
                if (settings.placeWatch && settings.tagLocation) {
                    app.places.hear(
                        seen,
                        fleets,
                        app.config.watchlist,
                        app.sits.openSitId(),
                        app.sits.clusterLabel(),
                    )
                }
                app.aircraft.hear(seen)
                considerTrackers(app, seen, tagged)
                if (settings.loggingEnabled) {
                    val toLog = seen.filter { it.hitCount <= 1 || it.hitCount % 25 == 0 }.take(16)
                    for (row in toLog) {
                        app.logs.append(row, fleets)
                    }
                    if (toLog.isNotEmpty()) app.devices.bumpLogs(app.logs.lineCount)
                }
                if (tagged.any { it.kind == RadioKind.WIFI && it.fresh }) {
                    wifiBatchPending = true
                }
                schedulePublish()
                updateNotification()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "ingest batch failed", e)
            }
        }
        } catch (_: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
            return
        }
    }

    /**
     * Separated-tag clock and the two-stop popup. The current advertisement decides
     * separated or near. Mine stays quiet. A switch that is off does not keep a clock.
     */
    private fun considerTrackers(app: FieldwatchApp, seen: List<Sighting>, tagged: List<Observation>) {
        val settings = app.config.settings
        val now = System.currentTimeMillis()
        val followOn = settings.trackerFollow && settings.tagLocation
        val placesOn = settings.trackerPlaces && settings.placeWatch && settings.tagLocation
        val placeFresh = app.places.trackerPlaces(placesOn, now, seen)
        val names = app.config.fleets.associate { it.id to it.name }
        val labels = RadioBookmarks.labels(app.config.watchlist)
        val hears = ArrayList<TrackerWatch.Hear>(seen.size)
        for (i in seen.indices) {
            val row = seen[i]
            if (row.kind != RadioKind.BLE) continue
            hears += TrackerWatch.Hear(
                device = row,
                packet = tagged.getOrNull(i)?.facts ?: row.facts,
                fleetNames = row.fleetIds.mapNotNull { id -> names[id] },
                custom = labels[row.key],
            )
        }
        val fix = if (settings.tagLocation) app.lastFix else null
        val before = app.tracker.notice.value?.id
        val raised = app.tracker.hear(
            hears = hears,
            mine = RadioBookmarks.mineKeys(app.config.watchlist),
            lat = fix?.first,
            lon = fix?.second,
            now = now,
            followOn = followOn,
            placesOn = placesOn,
            placeFresh = placeFresh,
        )
        val after = app.tracker.notice.value
        if (raised != null || (before != null && after?.id != before)) {
            app.syncTrackerShade(after)
        }
        if (raised != null) app.announceTracker(raised)
    }

    /** Lets a separated clock finish, or drop, between advertisements. */
    private fun ageTrackers(app: FieldwatchApp) {
        val settings = app.config.settings
        val followOn = settings.trackerFollow && settings.tagLocation
        val showing = app.tracker.notice.value
        if (!followOn && showing == null) return
        val before = showing?.id
        val placesOn = settings.trackerPlaces && settings.placeWatch && settings.tagLocation
        val raised = app.tracker.hear(
            hears = emptyList(),
            mine = RadioBookmarks.mineKeys(app.config.watchlist),
            lat = null,
            lon = null,
            now = System.currentTimeMillis(),
            followOn = followOn,
            placesOn = placesOn,
            placeFresh = emptyList(),
        )
        val after = app.tracker.notice.value
        if (raised != null || (before != null && after?.id != before)) {
            app.syncTrackerShade(after)
        }
        if (raised != null) app.announceTracker(raised)
    }

    /**
     * Trusted names only. Location comes from the GPS fix, not a network fix on the row.
     * A quiet or cached hear still goes in so a dismissed episode can keep its clock.
     */
    private fun considerTrusted(app: FieldwatchApp, batch: List<Observation>) {
        if (app.trusted.snapshot().nets.isEmpty()) return
        val settings = app.config.settings
        val opt = settings.trustedOpt()
        val now = System.currentTimeMillis()
        val fix = app.trustFix(now)
        val demo = settings.demoMode
        for (obs in batch) {
            if (obs.kind != RadioKind.WIFI || obs.hiddenSsid || obs.name.isBlank()) continue
            val alert = app.trusted.hear(
                TwinHear(
                    ssid = obs.name,
                    bssid = obs.mac,
                    rssi = obs.rssi,
                    fresh = obs.fresh,
                    securityText = obs.facts.security,
                    channel = obs.channel,
                    frequencyMhz = obs.frequencyMhz,
                    vendor = RadioDb.vendorForMac(obs.mac),
                    lat = fix.lat,
                    lon = fix.lon,
                    accuracyM = fix.accuracyM,
                    locationPaused = fix.paused,
                ),
                opt,
                now,
            ) ?: continue
            val sentence = TrustedNets.sentence(alert.ssid, alert.reasons)
            val body = buildString {
                append(TrustedNets.explain(alert.ouiNote))
                append('\n')
                append(MacUtil.screenMac(alert.bssid, demo))
            }
            app.alerter.trustedAlert(
                title = sentence,
                body = body,
                phrase = if (alert.voice) sentence else null,
                beep = alert.beep,
                shade = alert.shade,
            )
        }
    }

    private fun schedulePublish() {
        val now = SystemClock.elapsedRealtime()
        synchronized(publishGate) {
            if (lastPublishAt == 0L || now - lastPublishAt >= PUBLISH_MS) {
                lastPublishAt = now
                lifecycleScope.launch(Dispatchers.Default) { publishNow() }
                return
            }
            if (publishJob?.isActive == true) return
            val wait = PUBLISH_MS - (now - lastPublishAt)
            publishJob = lifecycleScope.launch(Dispatchers.Default) {
                delay(wait.coerceAtLeast(0L))
                lastPublishAt = SystemClock.elapsedRealtime()
                publishNow()
            }
        }
    }

    /** A running BLE scan that is not parked, retrying, or held for a location fix. */
    private fun quietBleHealthy(): Boolean {
        if (locationQuiet.radiosHeld()) return false
        if (!ble.isRunning() || ble.holding()) return false
        return when (ble.statusHint()) {
            "BLE parked · restarting", "BLE retrying", "BLE unavailable" -> false
            else -> true
        }
    }

    /** A failed Wi-Fi scan holds radios. A normal gap between scans does not. */
    private fun quietWifiHealthy(): Boolean {
        if (locationQuiet.radiosHeld()) return false
        return !wifi.waitingOnOs()
    }

    private suspend fun publishNow() {
        try {
            val app = application as FieldwatchApp
            val fleets = app.config.fleets
            val settings = app.config.settings
            app.devices.noteQuietScan(quietBleHealthy(), quietWifiHealthy())
            app.devices.refresh(
                fleets,
                settings.staleSec,
                policy = settings.detectionPolicy(),
                decaySec = settings.decaySec,
            )
            if (wifiBatchPending) {
                wifiBatchPending = false
                app.onFreshWifiBatch()
            }
            val live = app.devices.devices.value
            val liveKeys = live.mapTo(HashSet(live.size)) { it.key }
            app.pairingFlood.prune(liveKeys)
            app.wifiFlood.prune(liveKeys)
            val nowMs = System.currentTimeMillis()
            val swarm = app.passRemoteIdSwarm(nowMs)
            val near = app.passDroneNear(nowMs)
            val emergency = app.passRemoteIdEmergency()
            val quietFleets = if (swarm.quiet) app.droneFleetIds() else emptySet()
            if (settings.alertsEnabled && app.config.watchlist.isNotEmpty()) {
                app.alerter.checkLive(
                    live,
                    fleets,
                    app.config.watchlist,
                    alertsOn = true,
                    beepOn = settings.alertBeep,
                    voiceOn = settings.alertVoice,
                    voiceWhat = settings.alertVoiceWhat,
                    spokenNames = settings.spokenNames,
                    shadeOn = settings.alertShade,
                    visibleOnLive = app::wouldShowOnLive,
                    arrivalsOnly = app.config.filter.arrivalsOnly,
                    demoMode = settings.demoMode,
                    quietDroneFleets = quietFleets,
                    holdRemoteId = { device ->
                        app.droneNear.holdsBookmark(
                            device,
                            RadioBookmarks.mineKeys(app.config.watchlist),
                            nowMs,
                            settings.droneNearM,
                        )
                    },
                )
            }
            if (settings.alertsEnabled && app.devices.isScanLive()) {
                val marks = app.alerter.wentQuiet(
                    app.devices.takeQuietEdges(),
                    app.config.watchlist,
                    beepOn = settings.alertBeep,
                    voiceOn = settings.alertVoice,
                    shadeOn = settings.alertShade,
                    demoMode = settings.demoMode,
                )
                for (mark in marks) app.sits.noteQuiet(mark)
            } else {
                app.devices.takeQuietEdges()
            }
            app.announceSwarm(swarm)
            app.announceDroneNear(near)
            app.announceEmergency(emergency)
            val gnss = app.gnss.reading.value
            val floodNotice = app.pairingFlood.notice.value ?: app.wifiFlood.notice.value
            val phoneNote = listOfNotNull(
                TakGnss.line(settings.takGnss, gnss.notice, gnss.lives),
                TakFlood.line(settings.takFlood, floodNotice),
            ).joinToString("\n").ifBlank { null }
            val cfg = app.config.config.value
            val legacyRetired = app.tak.publish(
                live,
                fleets,
                settings,
                app.config.watchlist,
                selfFix = app.lastFix,
                gnssRemark = phoneNote,
                gnssNotice = gnss.notice,
                gnssLives = gnss.lives,
                installTag = cfg.takInstallTag,
                legacySelfRetired = cfg.takLegacySelfRetired,
                swarmNote = { device -> app.remoteIdSwarm.memberLine(device.payloadUasId) },
            )
            if (legacyRetired) {
                app.config.update { cur ->
                    if (cur.takLegacySelfRetired) cur else cur.copy(takLegacySelfRetired = true)
                }
            }
            val takOn = settings.takEnabled && !settings.demoMode
            val plan = app.trusted.planCot(settings.trustedOpt(), takOn, System.currentTimeMillis())
            if (plan.xml.isNotEmpty() && app.tak.sendNow(settings, plan.xml)) {
                app.trusted.commitCot(plan)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "publish failed", e)
        }
    }

    /**
     * API 34 and later refuse a location type until Fieldwatch is on screen.
     * The connected-device type still starts, so that refusal does not kill
     * the process. [includeLocationType] adds location once it is allowed.
     */
    private fun startAsForeground() {
        lastNotifText = LanguageText.text("ui.starting.radios")
        val notification = buildNotification(lastNotifText)
        when (chooseForeground(Build.VERSION.SDK_INT) { types -> postForeground(notification, types) }) {
            ForegroundChoice.UNTYPED -> {
                startForeground(NOTIF_ID, notification)
                locationTypeOn = false
            }
            ForegroundChoice.LOCATION -> locationTypeOn = true
            ForegroundChoice.RADIOS -> locationTypeOn = false
            ForegroundChoice.REFUSED -> throw foregroundRefusal
                ?: IllegalStateException("foreground start refused")
        }
    }

    private fun includeLocationType() {
        if (Build.VERSION.SDK_INT < 29 || locationTypeOn) return
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
        val text = lastNotifText.ifBlank { LanguageText.text("ui.starting.radios") }
        if (!postForeground(buildNotification(text), types)) return
        locationTypeOn = true
        val app = application as FieldwatchApp
        runCatching { app.syncLocationUpdates() }
        if (gnssReady) runCatching { app.syncGnss() }
    }

    private fun postForeground(notification: Notification, types: Int): Boolean =
        try {
            startForeground(NOTIF_ID, notification, types)
            foregroundRefusal = null
            true
        } catch (e: SecurityException) {
            foregroundRefusal = e
            Log.w(TAG, "foreground type $types refused", e)
            false
        }

    private fun updateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotifAt < 2_500L) return
        lastNotifAt = now
        val stats = (application as FieldwatchApp).devices.stats.value
        val text = "${stats.wifiNow} Wi-Fi · ${stats.bleNow} BLE · ${stats.namedNow} signatures"
        lastNotifText = text
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fieldwatch)
            .setContentTitle(LanguageText.text("ui.fieldwatch.scanning"))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launch)
            .addAction(0, "Stop", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopScanning()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_INCLUDE_LOCATION) includeLocationType()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopScanning()
    }

    /**
     * Returns null when this pass must stop the service.
     * The saved intensity is unchanged. [lastIntensity] follows the effective one.
     */
    private fun applyBatteryFloor(): app.fieldwatch.domain.BatteryFloorResult? {
        val app = application as FieldwatchApp
        val settings = app.config.settings
        val sample = batterySample
        val decision = BatteryFloor.evaluate(settings, sample, batterySlowed)
        app.publishBatteryFloor(
            BatteryFloorLive(
                percent = sample?.percent ?: -1,
                plugged = sample?.plugged == true,
                step = decision.step,
            ),
        )
        when (decision.step) {
            BatteryFloorStep.STOPPED -> {
                val percent = sample?.percent ?: -1
                app.sits.noteBattery(BatteryMark(System.currentTimeMillis(), "STOPPED", percent))
                postBatteryStop(percent)
                batteryStopping = true
                return null
            }
            BatteryFloorStep.SLOW -> {
                if (!batterySlowed) {
                    batterySlowed = true
                    app.sits.noteBattery(
                        BatteryMark(
                            System.currentTimeMillis(),
                            "SLOWED",
                            sample?.percent ?: -1,
                        ),
                    )
                }
            }
            BatteryFloorStep.NONE -> {
                if (batterySlowed) {
                    batterySlowed = false
                    val restored = decision.restored
                    if (restored != null) {
                        app.sits.noteBattery(
                            BatteryMark(
                                System.currentTimeMillis(),
                                "RESTORED",
                                sample?.percent ?: -1,
                                plugged = restored == BatteryRestore.CHARGING,
                                intensity = settings.intensity.name,
                            ),
                        )
                    }
                }
            }
        }
        return decision
    }

    private fun effectiveIntensity(): ScanIntensity {
        val app = application as FieldwatchApp
        return BatteryFloor.evaluate(app.config.settings, batterySample, batterySlowed).effective
    }

    private val batteryWatch = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            batterySample = readBattery(intent)
        }
    }

    private fun registerBatteryWatch() {
        if (batteryWatching) return
        val sticky = runCatching {
            registerReceiver(batteryWatch, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        batterySample = readBattery(sticky)
        batteryWatching = true
    }

    private fun unregisterBatteryWatch() {
        if (!batteryWatching) return
        runCatching { unregisterReceiver(batteryWatch) }
        batteryWatching = false
    }

    private fun readBattery(intent: Intent?): BatterySample? {
        if (intent == null) return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (level < 0 || scale <= 0) return BatterySample(-1, plugged)
        return BatterySample(((level * 100) / scale).coerceIn(0, 100), plugged)
    }

    private fun postBatteryStop(percent: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 24 && !nm.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    BATTERY_CHANNEL,
                    LanguageText.text("ui.battery.label"),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    setShowBadge(false)
                    setSound(null, null)
                },
            )
        }
        val launch = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val level = if (percent < 0) "—" else percent.toString()
        val note = NotificationCompat.Builder(this, BATTERY_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fieldwatch)
            .setContentTitle(LanguageText.text("ui.battery.stopped.title"))
            .setContentText(LanguageText.text("ui.battery.stopped.body", mapOf("n" to level)))
            .setAutoCancel(true)
            .setContentIntent(launch)
            .build()
        nm.notify(BATTERY_NOTE_ID, note)
    }

    override fun onDestroy() {
        unregisterLocationWatch()
        loop?.cancel()
        pump?.cancel()
        bleStartJob?.cancel()
        gnssStartJob?.cancel()
        gnssReady = false
        publishJob?.cancel()
        inbound.close()
        runCatching { wifi.stop() }
        runCatching { ble.stop() }
        runCatching { (application as FieldwatchApp).tak.close() }
        val app = application as FieldwatchApp
        app.remoteIdSwarm.endSession()
        app.alerter.clearSwarmNote()
        app.devices.setScanning(false)
        app.gnss.stop()
        unregisterBatteryWatch()
        if (!batteryStopping) {
            val appNow = application as? FieldwatchApp
            val live = appNow?.batteryFloor?.value
            if (appNow != null && live != null) {
                appNow.publishBatteryFloor(live.copy(step = BatteryFloorStep.NONE))
            }
        }
        app.stopLocationUpdates()
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
        super.onDestroy()
    }

    private fun stopScanning() {
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Scanning", NotificationManager.IMPORTANCE_LOW).apply {
                description = LanguageText.text("ui.passive.wi.fi.bluetooth")
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val TAG = "FieldwatchScan"
        const val CHANNEL = "fieldwatch_scan"
        const val ACTION_STOP = Automation.STOP_SCAN
        const val ACTION_INCLUDE_LOCATION = "app.fieldwatch.INCLUDE_LOCATION"

        fun includeLocation(context: Context) {
            val intent = Intent(context, ScanService::class.java).setAction(ACTION_INCLUDE_LOCATION)
            runCatching { context.startService(intent) }
        }
        private const val NOTIF_ID = 42
        private const val BATTERY_NOTE_ID = 43
        private const val BATTERY_CHANNEL = "fieldwatch_battery"
        private const val PUBLISH_MS = 200L
        private const val BLE_START_STAGGER_MS = 500L
        private const val GNSS_START_DELAY_MS = 1_500L
    }
}

internal enum class ForegroundChoice {
    /** Before API 29 the call has no type mask. */
    UNTYPED,
    /** Location type was accepted, with the connected-device type from API 34. */
    LOCATION,
    /** Location was refused. The connected-device type was accepted. */
    RADIOS,
    /** Every typed start was refused. */
    REFUSED,
}

/**
 * Try location first. From API 29, a refusal falls back to the connected-device
 * type so a screen-off start still comes up. Before API 29 there is no type mask.
 */
internal fun chooseForeground(sdkInt: Int, accept: (Int) -> Boolean): ForegroundChoice {
    if (sdkInt < 29) return ForegroundChoice.UNTYPED
    val location = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    val connected = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
    if (sdkInt >= 34) {
        if (accept(location or connected)) return ForegroundChoice.LOCATION
        if (accept(connected)) return ForegroundChoice.RADIOS
        return ForegroundChoice.REFUSED
    }
    if (accept(location)) return ForegroundChoice.LOCATION
    if (accept(connected)) return ForegroundChoice.RADIOS
    return ForegroundChoice.REFUSED
}
