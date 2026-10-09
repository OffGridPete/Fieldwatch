package app.fieldwatch.radio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.GnssMeasurement
import android.location.GnssMeasurementRequest
import android.location.GnssMeasurementsEvent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.ExecutorCompat
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.domain.BandEpoch
import app.fieldwatch.domain.BandKey
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.GnssAlerts
import app.fieldwatch.domain.GnssBands
import app.fieldwatch.domain.GnssCalAttempt
import app.fieldwatch.domain.GnssCalFix
import app.fieldwatch.domain.GnssCalOutcome
import app.fieldwatch.domain.GnssCalUi
import app.fieldwatch.domain.GnssKind
import app.fieldwatch.domain.GnssCalibration
import app.fieldwatch.domain.GnssConfig
import app.fieldwatch.domain.GnssConfidence
import app.fieldwatch.domain.GnssCopy
import app.fieldwatch.domain.GnssCue
import app.fieldwatch.domain.GnssDetector
import app.fieldwatch.domain.GnssEpoch
import app.fieldwatch.domain.GnssLive
import app.fieldwatch.domain.GnssMark
import app.fieldwatch.domain.GnssNotice
import app.fieldwatch.domain.GnssSide
import app.fieldwatch.domain.GnssPhoneProfile
import app.fieldwatch.domain.GnssPositionWitness
import app.fieldwatch.domain.GnssSnapshot
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.AppSettings
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GnssReading(
    val notice: GnssNotice? = null,
    val diagRows: List<Pair<String, String>> = listOf("GNSS monitor" to "off"),
    val marks: List<GnssMark> = emptyList(),
    val lives: List<GnssLive> = emptyList(),
)

/**
 * Listens to the phone's own GNSS receiver while the scan service is up.
 * Starts about a second after the radios so it does not share their first frame.
 */
class GnssMonitor(private val app: FieldwatchApp) {
    private val detector = GnssDetector()
    private val alerts = GnssAlerts()
    private val _reading = MutableStateFlow(GnssReading())
    val reading: StateFlow<GnssReading> = _reading.asStateFlow()
    private val _calibration = MutableStateFlow(GnssCalUi())
    val calibration: StateFlow<GnssCalUi> = _calibration.asStateFlow()

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var executor: Executor? = null
    private var running = false
    @Volatile private var calibrating = false
    val isCalibrating: Boolean get() = calibrating
    private var calToken = 0
    private var calRun = 0
    private var calStart = 0L
    private var calGpsOn = false
    private val calEpochs = ArrayList<GnssEpoch>()
    private val calFixes = ArrayList<GnssCalFix>()
    private var callbackOn = false
    private var fullTracking = false
    private var ownLocation = false
    private var wantOwn = false
    private var receiverOn = false
    private var floor = GnssConfidence.MEDIUM
    private var suppressAlerts = false
    private var beepOn = true
    private var voiceOn = false
    private var shadeOn = false
    private var lastCue = ""
    private var lastAlert = "none"
    private var lastSnap: GnssSnapshot? = null
    private var gotMeas = false
    private val noData = Runnable {
        if (running && !gotMeas) {
            blocked = "no raw measurements"
            raw = "not supported"
            publish(lastSnap, _reading.value.notice)
        }
    }

    private var hardware = "unknown"
    private var year = "unknown"
    private var raw = "unknown"
    private var agcSource = "checking"
    private var fullLabel = "off"
    private var networkTime = "unavailable"
    private var blocked = ""
    private var events = 0
    private var sawEvent = false
    private var sawMeas = false

    private var timeDisagree = false
    private var positionDisagree = false
    private var positionSince = 0L
    private var hopLeft = 0
    private var mockOn = false
    private var lastGps: Fix? = null
    private var lastNet: Fix? = null

    private val main = Handler(Looper.getMainLooper())

    private val callback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
            gotMeas = true
            blocked = ""
            if (calibrating) {
                calEpochs += toEpoch(event)
                return
            }
            val snap = detector.onEpoch(toEpoch(event), side())
            deliver(snap)
        }

        override fun onStatusChanged(status: Int) {
            when (status) {
                STATUS_NOT_SUPPORTED -> {
                    raw = "not supported"
                    blocked = "raw measurements not supported"
                }
                STATUS_LOCATION_DISABLED -> blocked = "GPS provider off"
                else -> if (blocked == "GPS provider off") blocked = ""
            }
            publish(lastSnap, _reading.value.notice)
        }
    }

    private val providers = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handler?.post { refreshProviders() }
        }
    }

    private val ownGps = LocationListener { loc -> ingestFix(snapFix(loc, gps = true)) }
    private val ownNet = LocationListener { loc -> ingestFix(snapFix(loc, gps = false)) }

    fun sync(settings: AppSettings, profile: GnssPhoneProfile?) {
        if (calibrating) return
        if (!settings.gnssMonitor || profile == null) {
            stop()
            return
        }
        ensureThread()
        handler?.post {
            if (calibrating) return@post
            detector.config = GnssConfig(
                sensitivity = settings.gnssSensitivity,
                spoofChecks = settings.gnssSpoofChecks,
                correlation = settings.gnssCorrelation,
                invertAgc = settings.gnssInvertAgc,
                agcFloorDb = profile.agcFloorDb,
                cn0FloorDb = profile.cn0FloorDb,
                ignoreStopped = profile.ignoreStopped,
            )
            floor = settings.gnssAlertFloor.toConfidence()
            suppressAlerts = profile.suppressAlerts
            beepOn = settings.gnssBeep
            voiceOn = settings.gnssVoice
            shadeOn = settings.gnssShade
            if (!shadeOn) main.post { app.alerter.clearGnssNote() }
            fullLabel = if (settings.gnssFullTracking && Build.VERSION.SDK_INT >= 31) "on" else "off"
            wantOwn = !settings.tagLocation
            if (!running) startLocked(settings) else {
                val wantFull = settings.gnssFullTracking && Build.VERSION.SDK_INT >= 31
                if (wantFull != fullTracking && callbackOn) {
                    unregisterCallback()
                    registerCallback(wantFull)
                }
                syncOwnLocation()
                publish(lastSnap, _reading.value.notice)
            }
        }
    }

    fun stop() {
        if (calibrating) return
        val h = handler
        if (h == null) {
            _reading.value = _reading.value.copy(
                notice = null,
                lives = emptyList(),
                diagRows = listOf("GNSS monitor" to "off"),
            )
            return
        }
        h.post {
            teardown()
            _reading.value = _reading.value.copy(
                notice = null,
                lives = emptyList(),
                diagRows = listOf("GNSS monitor" to "off"),
            )
        }
    }

    fun dismiss() {
        handler?.post {
            alerts.dismiss()
            val snap = lastSnap ?: return@post
            val shown = alerts.onLives(snap.lives, floor, System.currentTimeMillis())
            publish(snap, shown.notice)
        }
    }

    /** Tagging fixes. Copied here so the location object can be reused by Android. */
    fun onTaggedFix(loc: Location, gps: Boolean) {
        val fix = snapFix(loc, gps)
        val h = handler ?: return
        if (Looper.myLooper() == h.looper) ingestFix(fix) else h.post { ingestFix(fix) }
    }

    fun marks(): List<GnssMark> = _reading.value.marks

    private fun startLocked(settings: AppSettings) {
        val lm = manager() ?: return
        if (!fine()) {
            blocked = "no location permission"
            running = false
            publish(null, null)
            return
        }
        probeHardware(lm)
        if (Build.VERSION.SDK_INT >= 31 && !lm.gnssCapabilities.hasMeasurements()) {
            raw = "not supported"
            blocked = "raw measurements not supported"
            running = true
            publish(null, null)
            return
        }
        if (Build.VERSION.SDK_INT < 31) raw = "unknown (API ${Build.VERSION.SDK_INT})"
        registerReceiver()
        val wantFull = settings.gnssFullTracking && Build.VERSION.SDK_INT >= 31
        registerCallback(wantFull)
        running = true
        gotMeas = false
        handler?.removeCallbacks(noData)
        handler?.postDelayed(noData, 30_000L)
        syncOwnLocation()
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) blocked = "GPS provider off"
        publish(null, null)
    }

    private fun teardown() {
        handler?.removeCallbacks(noData)
        unregisterCallback()
        unregisterReceiver()
        releaseOwnLocation()
        detector.reset()
        alerts.reset()
        lastSnap = null
        lastCue = ""
        timeDisagree = false
        positionDisagree = false
        positionSince = 0L
        hopLeft = 0
        mockOn = false
        running = false
        blocked = ""
        gotMeas = false
    }

    @SuppressLint("MissingPermission")
    private fun registerCallback(full: Boolean) {
        val lm = manager() ?: return
        val exec = executor ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 31 && full) {
                val request = GnssMeasurementRequest.Builder().setFullTracking(true).build()
                lm.registerGnssMeasurementsCallback(request, exec, callback)
            } else {
                LocationManagerCompat.registerGnssMeasurementsCallback(lm, exec, callback)
            }
            callbackOn = true
            fullTracking = full
        }
    }

    private fun unregisterCallback() {
        val lm = manager() ?: return
        if (!callbackOn) return
        runCatching { LocationManagerCompat.unregisterGnssMeasurementsCallback(lm, callback) }
        callbackOn = false
    }

    private fun registerReceiver() {
        if (receiverOn) return
        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        runCatching {
            ContextCompat.registerReceiver(app, providers, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverOn = true
        }
    }

    private fun unregisterReceiver() {
        if (!receiverOn) return
        runCatching { app.unregisterReceiver(providers) }
        receiverOn = false
    }

    @SuppressLint("MissingPermission")
    private fun syncOwnLocation() {
        val lm = manager() ?: return
        if (!wantOwn || !fine()) {
            releaseOwnLocation()
            return
        }
        if (ownLocation) return
        val looper = handler?.looper ?: return
        runCatching {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, ownGps, looper)
            }
        }
        runCatching {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4_000L, 0f, ownNet, looper)
            }
        }
        ownLocation = true
    }

    private fun releaseOwnLocation() {
        if (!ownLocation) return
        val lm = manager() ?: return
        runCatching { lm.removeUpdates(ownGps) }
        runCatching { lm.removeUpdates(ownNet) }
        ownLocation = false
    }

    private fun refreshProviders() {
        val lm = manager() ?: return
        blocked = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) "" else "GPS provider off"
        if (wantOwn) {
            releaseOwnLocation()
            syncOwnLocation()
        }
        publish(lastSnap, _reading.value.notice)
    }

    private fun deliver(snap: GnssSnapshot) {
        lastSnap = snap
        val alertLives = if (suppressAlerts) snap.lives.filter { it.kind == GnssKind.MOCK } else snap.lives
        val shown = alerts.onLives(alertLives, floor, System.currentTimeMillis())
        shown.cue?.let { fire(it) }
        publish(snap.copy(lives = alertLives), shown.notice)
    }

    /** About a minute, outside, standing still. The check stays off until a result is saved. */
    fun startCalibration() {
        if (calibrating) return
        calibrating = true
        ensureThread()
        val token = ++calToken
        handler?.post { beginCalibration(token) }
    }

    fun stopCalibration() {
        if (!calibrating) return
        calToken++
        handler?.post {
            endCalibration(GnssCalUi(rejection = "Calibration stopped. The last result is unchanged."))
        }
    }

    private val calTick: Runnable = Runnable {
        if (!calibrating || calRun != calToken) return@Runnable
        val elapsed = SystemClock.elapsedRealtime() - calStart
        if (elapsed >= GnssCalibration.DURATION_MS) {
            val outcome = GnssCalibration.grade(
                GnssCalAttempt(
                    epochs = calEpochs.toList(),
                    fixes = calFixes.toList(),
                    startedElapsedMs = calStart,
                    endedElapsedMs = SystemClock.elapsedRealtime(),
                ),
                System.currentTimeMillis(),
            )
            if (outcome is GnssCalOutcome.Saved) app.gnssProfile.save(outcome.profile)
            val rejection = (outcome as? GnssCalOutcome.Rejected)?.reason.orEmpty()
            endCalibration(GnssCalUi(rejection = rejection))
            return@Runnable
        }
        val left = ((GnssCalibration.DURATION_MS - elapsed + 999) / 1000L).toInt().coerceAtLeast(0)
        _calibration.value = GnssCalUi(running = true, leftSec = left)
        handler?.postDelayed(calTick, 1_000L)
    }

    private fun beginCalibration(token: Int) {
        if (token != calToken) return
        calRun = token
        handler?.removeCallbacks(noData)
        unregisterCallback()
        unregisterReceiver()
        releaseOwnLocation()
        detector.reset()
        alerts.reset()
        lastSnap = null
        running = false
        _reading.value = _reading.value.copy(notice = null, lives = emptyList())
        calEpochs.clear()
        calFixes.clear()
        calStart = SystemClock.elapsedRealtime()
        if (!fine()) {
            endCalibration(GnssCalUi(rejection = "Location permission is missing. Grant it, then try again."))
            return
        }
        val lm = manager()
        if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            endCalibration(GnssCalUi(rejection = "Turn Location on, stand outside, and try again."))
            return
        }
        probeHardware(lm)
        registerCallback(false)
        registerCalGps()
        _calibration.value = GnssCalUi(running = true, leftSec = 60)
        handler?.postDelayed(calTick, 1_000L)
    }

    private fun endCalibration(ui: GnssCalUi) {
        handler?.removeCallbacks(calTick)
        unregisterCallback()
        releaseCalGps()
        calEpochs.clear()
        calFixes.clear()
        calibrating = false
        _calibration.value = ui
        main.post { app.syncGnss() }
    }

    private val calGps = LocationListener { loc ->
        if (!calibrating || !loc.hasAccuracy()) return@LocationListener
        calFixes += GnssCalFix(
            elapsedMs = SystemClock.elapsedRealtime(),
            accM = loc.accuracy,
            speedMps = if (loc.hasSpeed()) loc.speed else 0f,
            lat = loc.latitude,
            lon = loc.longitude,
        )
    }

    @SuppressLint("MissingPermission")
    private fun registerCalGps() {
        val lm = manager() ?: return
        val looper = handler?.looper ?: return
        runCatching {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, calGps, looper)
                calGpsOn = true
            }
        }
    }

    private fun releaseCalGps() {
        if (!calGpsOn) return
        val lm = manager() ?: return
        runCatching { lm.removeUpdates(calGps) }
        calGpsOn = false
    }

    private fun fire(cue: GnssCue) {
        val key = cue.title + "\n" + cue.phrase
        if (key == lastCue) return
        lastCue = key
        lastAlert = GnssCopy.localHm(System.currentTimeMillis()) + ", " + cue.title
        val phrase = if (voiceOn) cue.phrase else null
        val beep = beepOn
        val shade = shadeOn
        main.post {
            app.alerter.gnssAlert(cue.title, cue.text, phrase, beep, shade)
        }
    }

    private fun side(): GnssSide {
        val hop = hopLeft > 0
        if (hopLeft > 0) hopLeft--
        val gps = lastGps
        val lost = gps != null && System.currentTimeMillis() - gps.wall > 15_000L
        return GnssSide(
            timeDisagree = timeDisagree,
            positionDisagree = positionDisagree,
            impossibleHop = hop,
            mock = mockOn,
            fixLost = lost,
        )
    }

    private fun ingestFix(fix: Fix) {
        mockOn = fix.mock
        if (fix.gps) {
            val prev = lastGps
            if (prev != null && !Geo.hopPlausible(GpsSample(prev.wall, prev.lat, prev.lon), fix.lat, fix.lon, fix.wall)) {
                hopLeft = 8
            }
            lastGps = fix
            timeDisagree = timeDelta(fix)
        } else {
            lastNet = fix
        }
        val now = System.currentTimeMillis()
        val next = GnssPositionWitness.hold(positionRaw(now), positionSince, now)
        positionSince = next.sinceMs
        positionDisagree = next.on
    }

    private fun positionRaw(now: Long): Boolean {
        val gps = lastGps ?: return false
        val net = lastNet ?: return false
        return GnssPositionWitness.rawDisagree(
            gpsAccM = gps.acc.toDouble(),
            netAccM = net.acc.toDouble(),
            apartM = Geo.meters(gps.lat, gps.lon, net.lat, net.lon),
            gpsAgeMs = now - gps.wall,
            netAgeMs = now - net.wall,
        )
    }

    private fun timeDelta(fix: Fix): Boolean {
        if (Build.VERSION.SDK_INT < 33) {
            networkTime = "unavailable"
            return false
        }
        return runCatching {
            val net = SystemClock.currentNetworkTimeClock().millis()
            val gnssNow = fix.time + (SystemClock.elapsedRealtimeNanos() - fix.elapsedNanos) / 1_000_000.0
            networkTime = "available"
            abs(net - gnssNow) > 1_000.0
        }.getOrElse {
            networkTime = "unavailable"
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun toEpoch(event: GnssMeasurementsEvent): GnssEpoch {
        val acc = LinkedHashMap<BandKey, Acc>()
        if (Build.VERSION.SDK_INT >= 33) {
            for (agc in event.gnssAutomaticGainControls) {
                val db = agc.levelDb
                if (!db.isFinite() || db < -80.0) continue
                sawEvent = true
                val key = BandKey(agc.constellationType, GnssBands.ofHz(agc.carrierFrequencyHz.toDouble()))
                acc.getOrPut(key) { Acc() }.eventAgc = db
            }
        }
        for (m in event.measurements) {
            val hz = if (m.hasCarrierFrequencyHz()) m.carrierFrequencyHz.toDouble() else null
            val key = BandKey(m.constellationType, GnssBands.ofHz(hz))
            val bucket = acc.getOrPut(key) { Acc() }
            val cn0 = m.cn0DbHz
            if (cn0.isFinite() && cn0 > 0.0 && (m.state and GnssMeasurement.STATE_CODE_LOCK) != 0) {
                bucket.cn0 += cn0
                bucket.sv += 1
                bucket.bySv[m.svid] = cn0
            }
            if (m.hasAutomaticGainControlLevelDb()) {
                val db = m.automaticGainControlLevelDb
                if (db.isFinite() && db >= -80.0) {
                    sawMeas = true
                    bucket.measAgc += db
                }
            }
        }
        events++
        agcSource = when {
            sawEvent && sawMeas -> "event-level and per-measurement"
            sawEvent -> "event-level"
            sawMeas -> "per-measurement"
            events >= 10 -> "none"
            else -> "checking"
        }
        val bands = acc.mapValues { (_, bucket) ->
            val eventAgc = bucket.eventAgc
            val top = bucket.cn0.sortedDescending().take(3)
            BandEpoch(
                agcDb = eventAgc ?: median(bucket.measAgc),
                agcFromEventLevel = eventAgc != null,
                cn0Top3 = if (top.isEmpty()) null else top.average(),
                svTracked = bucket.sv,
                cn0BySv = bucket.bySv.toMap(),
            )
        }
        return GnssEpoch(
            elapsedMs = SystemClock.elapsedRealtime(),
            wallMs = System.currentTimeMillis(),
            clockDiscontinuity = event.clock.hardwareClockDiscontinuityCount,
            bands = bands,
        )
    }

    private fun publish(snap: GnssSnapshot?, notice: GnssNotice?) {
        _reading.value = GnssReading(
            notice = notice,
            diagRows = diag(snap),
            marks = snap?.marks ?: _reading.value.marks,
            lives = snap?.lives ?: emptyList(),
        )
    }

    private fun diag(snap: GnssSnapshot?): List<Pair<String, String>> {
        val status = if (blocked.isNotBlank()) blocked else snap?.statusLine ?: "starting"
        val rows = ArrayList<Pair<String, String>>()
        rows += "GNSS hardware" to hardware
        rows += "GNSS hardware year" to year
        rows += "Raw measurements" to raw
        rows += "AGC source" to agcSource
        rows += "Full tracking" to fullLabel
        rows += "GNSS monitor" to status
        snap?.bandLines?.forEach { line ->
            val cut = line.indexOf(": ")
            if (cut > 0) rows += line.substring(0, cut) to line.substring(cut + 2)
        }
        rows += "Network time" to networkTime
        rows += "Last GNSS alert" to lastAlert
        return rows
    }

    private fun probeHardware(lm: LocationManager) {
        hardware = LocationManagerCompat.getGnssHardwareModelName(lm)?.ifBlank { null } ?: "unknown"
        val y = LocationManagerCompat.getGnssYearOfHardware(lm)
        year = if (y > 0) y.toString() else "unknown"
        if (Build.VERSION.SDK_INT >= 31) {
            raw = if (lm.gnssCapabilities.hasMeasurements()) "supported" else "not supported"
        }
    }

    private fun snapFix(loc: Location, gps: Boolean) = Fix(
        gps = gps,
        lat = loc.latitude,
        lon = loc.longitude,
        acc = if (loc.hasAccuracy()) loc.accuracy else 99_999f,
        time = loc.time,
        wall = System.currentTimeMillis(),
        elapsedNanos = loc.elapsedRealtimeNanos,
        mock = isMock(loc),
    )

    private fun isMock(loc: Location): Boolean =
        if (Build.VERSION.SDK_INT >= 31) loc.isMock else @Suppress("DEPRECATION") loc.isFromMockProvider

    private fun ensureThread() {
        if (thread?.isAlive == true && handler != null) return
        val next = HandlerThread("fw-gnss")
        next.start()
        thread = next
        val h = Handler(next.looper)
        handler = h
        executor = ExecutorCompat.create(h)
    }

    private fun manager(): LocationManager? =
        app.getSystemService(LocationManager::class.java)

    private fun fine(): Boolean =
        ContextCompat.checkSelfPermission(app, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private data class Fix(
        val gps: Boolean,
        val lat: Double,
        val lon: Double,
        val acc: Float,
        val time: Long,
        val wall: Long,
        val elapsedNanos: Long,
        val mock: Boolean,
    )

    private class Acc {
        var eventAgc: Double? = null
        val measAgc = ArrayList<Double>()
        val cn0 = ArrayList<Double>()
        val bySv = HashMap<Int, Double>()
        var sv = 0
    }
}
