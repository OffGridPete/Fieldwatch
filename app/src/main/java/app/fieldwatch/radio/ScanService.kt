package app.fieldwatch.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.fieldwatch.MainActivity
import app.fieldwatch.R
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.domain.Observation
import app.fieldwatch.domain.RadioKind
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
        app.syncLocationUpdates()
        restartRadios()
        pump = lifecycleScope.launch(Dispatchers.Default) { drainInbound() }
        loop = lifecycleScope.launch(Dispatchers.Default) {
            while (isActive) {
                val settings = app.config.settings
                if (lastIntensity != settings.intensity) restartRadios()
                val unthrottled = settings.wifiFastScan && !WifiRadio.osScanThrottled(this@ScanService)
                val wifiMin = if (unthrottled) {
                    WifiRadio.FAST_INTERVAL_MS
                } else when (settings.intensity) {
                    ScanIntensity.PERFORMANCE -> 30_000L
                    ScanIntensity.BALANCED -> 40_000L
                    ScanIntensity.SAVER -> 55_000L
                }
                wifi.requestScan(wifiMin, unthrottled = unthrottled)
                if (!bleStartPending() && ble.needsRestart()) {
                    app.devices.setRadioHold(ble = true)
                    if (ble.isRunning()) {
                        ble.stop()
                        delay(ble.restartBackoffMs())
                    }
                    ble.start(settings.intensity)
                }
                app.devices.setRadioHold(
                    wifi = wifi.waitingOnOs(),
                    ble = ble.holding(),
                )
                val fastBlocked = settings.wifiFastScan && !unthrottled
                val hint = listOf(
                    wifi.throttleHint(),
                    ble.statusHint(),
                    if (fastBlocked) "Wi-Fi fast scan needs Developer options" else "",
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                app.devices.setScanning(true, hint)
                publishNow()
                delay(2_000L)
            }
        }
    }

    private fun restartRadios() {
        val intensity = (application as FieldwatchApp).config.settings.intensity
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
            ble.start((application as FieldwatchApp).config.settings.intensity)
        }
    }

    private fun bleStartPending(): Boolean =
        bleStartJob?.isActive == true && !ble.isRunning()

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

    private fun publishNow() {
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
            if (settings.alertsEnabled && app.config.watchlist.isNotEmpty()) {
                app.alerter.checkLive(
                    live,
                    fleets,
                    app.config.watchlist,
                    alertsOn = true,
                    beepOn = settings.alertBeep,
                    voiceOn = settings.alertVoice,
                    voiceWhat = settings.alertVoiceWhat,
                    shadeOn = settings.alertShade,
                    visibleOnLive = app::wouldShowOnLive,
                    arrivalsOnly = app.config.filter.arrivalsOnly,
                    demoMode = settings.demoMode,
                )
            }
            if (settings.takEnabled) {
                app.tak.publish(
                    live,
                    fleets,
                    settings,
                    app.config.watchlist,
                    selfFix = app.lastFix,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "publish failed", e)
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification("Starting radios…")
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
            .setContentTitle("Fieldwatch scanning")
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
        loop?.cancel()
        pump?.cancel()
        bleStartJob?.cancel()
        publishJob?.cancel()
        inbound.close()
        runCatching { wifi.stop() }
        runCatching { ble.stop() }
        runCatching { (application as FieldwatchApp).tak.close() }
        val app = application as FieldwatchApp
        app.devices.setScanning(false)
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
                description = "Passive Wi-Fi and Bluetooth scan status"
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
    }
}
