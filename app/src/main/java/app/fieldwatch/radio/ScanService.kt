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
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.trustedOpt
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
    private var lastNotifAt = 0L
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
        startAsForeground()
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
                val locationOn = runCatching { app.systemLocationOn() }.getOrDefault(false)
                noteLocation(locationOn)
                val hold = locationQuiet.radioHoldMs()
                if (hold > 0L) {
                    delay(hold.coerceAtMost(2_000L))
                    continue
                }
                var resumeRadios = false
                if (locationOn) {
                    resumeRadios = locationQuiet.takeRadioResume()
                    if (resumeRadios) restartRadios()
                    val settings = app.config.settings
                    if (lastIntensity != settings.intensity) restartRadios()
                    val unthrottled = !WifiRadio.osScanThrottled(this@ScanService)
                    val wifiMin = if (unthrottled) {
                        WifiRadio.FAST_INTERVAL_MS
                    } else when (settings.intensity) {
                        ScanIntensity.PERFORMANCE -> 30_000L
                        ScanIntensity.BALANCED -> 40_000L
                        ScanIntensity.SAVER -> 55_000L
                    }
                    if (!locationQuiet.radiosHeld()) {
                        wifi.requestScan(wifiMin, unthrottled = unthrottled)
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
                            else -> ble.start(settings.intensity)
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
                app.pairingFlood.tick(System.currentTimeMillis(), app.floodSpeed())
                app.sits.noteFloods(app.pairingFlood.bursts())
                app.sits.noteFloods(app.wifiFlood.bursts())
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
        val intensity = app.config.settings.intensity
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
            ble.start(app.config.settings.intensity)
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
            val note = if (alert.ouiNote) {
                LanguageText.text("trusted.oui.note", mapOf("oui" to "00:13:37"))
            } else {
                null
            }
            val body = buildString {
                append(MacUtil.screenMac(alert.bssid, demo))
                if (note != null) {
                    append('\n')
                    append(note)
                }
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

    private suspend fun publishNow() {
        try {
            val app = application as FieldwatchApp
            val fleets = app.config.fleets
            val settings = app.config.settings
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
                )
            }
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

    private fun startAsForeground() {
        val notification = buildNotification(LanguageText.text("ui.starting.radios"))
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotifAt < 2_500L) return
        lastNotifAt = now
        val stats = (application as FieldwatchApp).devices.stats.value
        val text = "${stats.wifiNow} Wi-Fi · ${stats.bleNow} BLE · ${stats.namedNow} signatures"
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
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopScanning()
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
        app.devices.setScanning(false)
        app.gnss.stop()
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
        const val ACTION_STOP = "app.fieldwatch.STOP_SCAN"
        private const val NOTIF_ID = 42
        private const val PUBLISH_MS = 200L
        private const val BLE_START_STAGGER_MS = 500L
        private const val GNSS_START_DELAY_MS = 1_500L
    }
}
