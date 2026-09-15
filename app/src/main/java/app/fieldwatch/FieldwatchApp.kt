package app.fieldwatch

import android.app.Application
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import app.fieldwatch.alert.Alerter
import app.fieldwatch.data.ConfigStore
import app.fieldwatch.data.DeviceStore
import app.fieldwatch.data.LogStore
import app.fieldwatch.domain.CoTravel
import app.fieldwatch.domain.FilterEngine
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioDb
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import app.fieldwatch.radio.ScanService
import app.fieldwatch.radio.TakPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FieldwatchApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var config: ConfigStore
        private set
    lateinit var devices: DeviceStore
        private set
    lateinit var logs: LogStore
        private set
    lateinit var alerter: Alerter
        private set
    lateinit var tak: TakPublisher
        private set
    private val filters = FilterEngine()
    private val _arrivals = MutableStateFlow(ArrivalsState())
    val arrivals: StateFlow<ArrivalsState> = _arrivals.asStateFlow()
    @Volatile
    private var wifiLearnPending = false
    @Volatile
    private var persistArrivalsAt = 0L

    @Volatile
    var lastFix: Pair<Double, Double>? = null
    private val pathLock = Any()
    private val operatorPath = ArrayList<GpsSample>(64)
    private var pathLengthM = 0.0
    private var locating = false
    private val gpsListener = LocationListener { loc -> acceptFix(loc) }

    override fun onCreate() {
        super.onCreate()
        config = ConfigStore(this)
        RadioDb.init(this)
        devices = DeviceStore()
        logs = LogStore(this)
        alerter = Alerter(this)
        tak = TakPublisher()
        runBlocking {
            config.load()
            logs.configure(
                config.settings.logFormat,
                config.settings.logRotateKb,
                config.settings.loggingEnabled,
            )
        }
        syncLocationUpdates()
        if (config.settings.alertVoice) alerter.prepareVoice()
        if (config.filter.arrivalsOnly) {
            beginArrivals(keepRemembered = true)
        }
    }

    fun beginArrivals(keepRemembered: Boolean = false) {
        val remembered = if (keepRemembered) config.config.value.arrivalKnownKeys else emptySet()
        wifiLearnPending = true
        _arrivals.value = ArrivalsState(
            active = true,
            knownKeys = remembered + devices.devices.value.map { it.key },
            learningUntil = System.currentTimeMillis() + 90_000L,
            bufferStartedAt = System.currentTimeMillis(),
        )
        persistArrivalKeys()
    }

    fun resetSeenBuffer() {
        wifiLearnPending = false
        alerter.forgetAnnounced()
        _arrivals.value = ArrivalsState(
            active = true,
            knownKeys = emptySet(),
            learningUntil = 0L,
            bufferStartedAt = System.currentTimeMillis(),
        )
        persistArrivalKeys(emptySet())
    }

    fun clearArrivals() {
        wifiLearnPending = false
        _arrivals.value = ArrivalsState()
        persistArrivalKeys(emptySet())
    }

    fun markArrivalsSeen(keys: Collection<String>? = null) {
        val st = _arrivals.value
        if (!st.active) return
        rememberKeys(keys ?: devices.devices.value.map { it.key })
        wifiLearnPending = false
        _arrivals.value = _arrivals.value.copy(learningUntil = 0L)
    }

    fun onFreshWifiBatch() {
        if (!_arrivals.value.active || !wifiLearnPending) return
        val wifiKeys = devices.devices.value.filter { it.kind == RadioKind.WIFI }.map { it.key }
        rememberKeys(wifiKeys)
        wifiLearnPending = false
        _arrivals.value = _arrivals.value.copy(learningUntil = 0L)
    }

    private fun rememberKeys(keys: Collection<String>) {
        val st = _arrivals.value
        if (!st.active || keys.isEmpty()) return
        val merged = st.knownKeys + keys
        if (merged.size == st.knownKeys.size) return
        val trimmed = if (merged.size <= MAX_ARRIVAL_KEYS) {
            merged
        } else {
            merged.toList().takeLast(MAX_ARRIVAL_KEYS).toSet()
        }
        _arrivals.value = st.copy(knownKeys = trimmed)
        persistArrivalKeys(trimmed)
    }

    private fun persistArrivalKeys(keys: Set<String> = _arrivals.value.knownKeys) {
        persistArrivalsAt = System.currentTimeMillis()
        scope.launch {
            config.update { it.copy(arrivalKnownKeys = keys) }
        }
    }

    fun syncArrivals(on: Boolean) {
        if (on) {
            if (!_arrivals.value.active) beginArrivals(keepRemembered = false)
        } else if (_arrivals.value.active) {
            clearArrivals()
        }
    }

    fun isHiddenByArrivals(device: Sighting): Boolean {
        if (!config.filter.arrivalsOnly) return false
        val st = _arrivals.value
        if (!st.active) return false
        val learning = System.currentTimeMillis() <= st.learningUntil
        return device.key in st.knownKeys || (learning && device.kind == RadioKind.WIFI)
    }

    fun wouldShowOnLive(device: Sighting): Boolean {
        if (device.gone) return false
        val travel = if (config.filter.movingWithYou) {
            CoTravel.Ctx.of(operatorPathCopy())
        } else {
            CoTravel.Ctx.None
        }
        val classById = config.fleets.associate { it.id to it.kind }
        if (!filters.pass(
                device,
                config.filter,
                travel,
                classByFleetId = classById,
                namedRadioKeys = RadioBookmarks.namedKeys(config.watchlist),
                watchedFleetIds = RadioBookmarks.watchedFleetIds(config.watchlist),
                alertDeviceKeys = RadioBookmarks.alertDeviceKeys(config.watchlist),
            )
        ) return false
        if (isHiddenByArrivals(device)) return false
        return true
    }

    fun startScanning() {
        val intent = Intent(this, ScanService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    fun stopScanning() {
        stopService(Intent(this, ScanService::class.java))
    }

    fun hasFineLocation(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun syncLocationUpdates() {
        if (config.settings.tagLocation && hasFineLocation()) startLocationUpdates()
        else stopLocationUpdates()
    }

    fun startLocationUpdates() {
        if (locating || !hasFineLocation()) return
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        seedLastKnown(lm)
        val looper = Looper.getMainLooper()
        runCatching {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 8f, gpsListener, looper)
            }
        }
        runCatching {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4_000L, 15f, gpsListener, looper)
            }
        }
        locating = true
    }

    fun stopLocationUpdates() {
        if (!locating) return
        runCatching {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            lm.removeUpdates(gpsListener)
        }
        locating = false
    }

    fun refreshFix() {
        if (!hasFineLocation()) return
        runCatching {
            seedLastKnown(getSystemService(LOCATION_SERVICE) as LocationManager)
        }
    }

    private fun seedLastKnown(lm: LocationManager) {
        val now = System.currentTimeMillis()
        val cands = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time < 30_000L && (!it.hasAccuracy() || it.accuracy <= 75f) }
        val best = cands.minByOrNull { if (it.hasAccuracy()) it.accuracy else 75f } ?: return
        acceptFix(best)
    }

    private fun acceptFix(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > 75f) return
        val age = System.currentTimeMillis() - loc.time
        if (age > 30_000L) return
        lastFix = loc.latitude to loc.longitude
        recordOperatorFix(loc.latitude, loc.longitude, loc.time)
    }

    fun recordOperatorFix(lat: Double, lon: Double, at: Long = System.currentTimeMillis()) {
        synchronized(pathLock) {
            val last = operatorPath.lastOrNull()
            if (last != null && Geo.meters(last.lat, last.lon, lat, lon) < 15.0) {
                operatorPath[operatorPath.lastIndex] = GpsSample(at, lat, lon)
                return
            }
            if (last != null) {
                pathLengthM += Geo.meters(last.lat, last.lon, lat, lon)
            }
            operatorPath += GpsSample(at, lat, lon)
            if (operatorPath.size > 80) {
                operatorPath.removeAt(0)
                pathLengthM = Geo.pathLengthM(operatorPath)
            }
        }
    }

    fun operatorPathCopy(): List<GpsSample> = synchronized(pathLock) { operatorPath.toList() }

    fun operatorPathLengthM(): Double = synchronized(pathLock) { pathLengthM }

    fun resetFollowSession() {
        synchronized(pathLock) {
            operatorPath.clear()
            pathLengthM = 0.0
        }
        devices.clearGpsTrails()
    }

    companion object {
        private const val MAX_ARRIVAL_KEYS = 2_500
    }
}

data class ArrivalsState(
    val active: Boolean = false,
    val knownKeys: Set<String> = emptySet(),
    val learningUntil: Long = 0L,
    val bufferStartedAt: Long = 0L,
)
