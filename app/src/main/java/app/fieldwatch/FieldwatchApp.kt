package app.fieldwatch

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import app.fieldwatch.alert.Alerter
import app.fieldwatch.crash.CrashNotes
import app.fieldwatch.radio.CellRadio
import app.fieldwatch.radio.GnssMonitor
import app.fieldwatch.data.AssetLanguageFiles
import app.fieldwatch.data.CellStore
import app.fieldwatch.data.ConfigStore
import app.fieldwatch.data.FlockFiles
import app.fieldwatch.data.SensorList
import app.fieldwatch.data.LanguageStore
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.data.DeviceStore
import app.fieldwatch.data.GnssProfileStore
import app.fieldwatch.data.GnssRawFiles
import app.fieldwatch.data.LogStore
import app.fieldwatch.data.MapKeyStore
import app.fieldwatch.data.PlaceStore
import app.fieldwatch.data.SitStore
import app.fieldwatch.data.TrustedNetworkStore
import app.fieldwatch.domain.CellCopy
import app.fieldwatch.domain.TrackerCopy
import app.fieldwatch.domain.TrackerMonitor
import app.fieldwatch.domain.TrackerWatch
import java.util.concurrent.atomic.AtomicInteger
import app.fieldwatch.domain.CellFix
import app.fieldwatch.domain.CellReading
import app.fieldwatch.domain.CellSwitches
import app.fieldwatch.domain.CoTravel
import app.fieldwatch.domain.FilterEngine
import app.fieldwatch.domain.FlockApproach
import app.fieldwatch.domain.FlockCamera
import app.fieldwatch.domain.FlockIndex
import app.fieldwatch.domain.FlockLatch
import app.fieldwatch.domain.PathCameras
import app.fieldwatch.domain.alertsFor
import app.fieldwatch.domain.anySensorAlert
import app.fieldwatch.domain.sensorNotice
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.Hunt
import app.fieldwatch.domain.HuntHeat
import app.fieldwatch.domain.HuntPlain
import app.fieldwatch.domain.HuntPose
import app.fieldwatch.domain.HuntStepDetector
import app.fieldwatch.domain.HuntTrack
import app.fieldwatch.domain.huntAccelStepCounts
import app.fieldwatch.domain.FloodBurst
import app.fieldwatch.domain.PairingFlood
import app.fieldwatch.domain.WifiBeaconFlood
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioDb
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import app.fieldwatch.radio.ScanService
import app.fieldwatch.radio.TakPublisher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale

data class TrustFix(
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Float?,
    val paused: Boolean,
)

class FieldwatchApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var config: ConfigStore
        private set
    lateinit var mapKeys: MapKeyStore
        private set
    lateinit var devices: DeviceStore
        private set
    lateinit var logs: LogStore
        private set
    lateinit var sits: SitStore
        private set
    lateinit var places: PlaceStore
        private set
    val tracker = TrackerMonitor()
    private val uiDepth = AtomicInteger(0)
    lateinit var trusted: TrustedNetworkStore
        private set
    lateinit var cells: CellStore
        private set
    private val cellVoiceLock = Any()
    private val cellVoiced = HashSet<Long>()
    lateinit var alerter: Alerter
        private set
    val flockIndex = FlockIndex()
    private val cameraReportLock = Any()
    private var cameraReportStamp = Long.MIN_VALUE
    @Volatile private var cameraReportCache: PathCameras.Report? = null
    private val flockLatch = FlockLatch()
    private val _flockRevision = MutableStateFlow(0)
    val flockRevision: StateFlow<Int> = _flockRevision.asStateFlow()
    private var flockLoadGen = 0
    lateinit var tak: TakPublisher
        private set
    val pairingFlood = PairingFlood()
    val wifiFlood = WifiBeaconFlood()
    lateinit var gnss: GnssMonitor
        private set
    lateinit var gnssRaw: GnssRawFiles
        private set
    lateinit var gnssProfile: GnssProfileStore
        private set
    lateinit var language: LanguageStore
        private set

    /** Null until onCreate has loaded the language file. */
    fun loadedLanguage(): LanguageStore? = if (::language.isInitialized) language else null

    /** Bluetooth bursts, Wi-Fi bursts, or both in time order. */
    fun floodBursts(): List<FloodBurst> {
        val ble = pairingFlood.bursts()
        val wifi = wifiFlood.bursts()
        if (wifi.isEmpty()) return ble
        if (ble.isEmpty()) return wifi
        return (ble + wifi).sortedBy { it.at }
    }

    private val filters = FilterEngine()
    private val _arrivals = MutableStateFlow(ArrivalsState())
    val arrivals: StateFlow<ArrivalsState> = _arrivals.asStateFlow()
    @Volatile
    private var wifiLearnPending = false
    @Volatile
    private var persistArrivalsAt = 0L

    @Volatile
    var lastFix: Pair<Double, Double>? = null
    @Volatile
    var lastFixAt: Long = 0L
    /** GPS fix good enough for the Hunt cloud. Network fixes stay out. */
    @Volatile
    private var lastGpsFix: Pair<Double, Double>? = null
    @Volatile
    private var lastGpsFixAt: Long = 0L
    /** Meters. NaN when that fix did not say how wide it was. */
    @Volatile
    private var lastGpsAcc = Float.NaN

    /** A fresh GPS fix for trusted-network places. Coarse and paused fixes stay out. */
    fun trustFix(now: Long = System.currentTimeMillis()): TrustFix {
        if (!::config.isInitialized || !config.settings.tagLocation) {
            return TrustFix(null, null, null, paused = true)
        }
        val fix = lastGpsFix
        val acc = lastGpsAcc
        val at = lastGpsFixAt
        if (fix == null || !acc.isFinite() || at <= 0L || now - at > 30_000L) {
            return TrustFix(null, null, null, paused = false)
        }
        return TrustFix(fix.first, fix.second, acc, paused = false)
    }

    /** The activity is on screen. A tracker warning uses the popup here, and a quiet note when it is not. */
    fun uiStarted() {
        uiDepth.incrementAndGet()
    }

    fun uiStopped() {
        uiDepth.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    fun inFront(): Boolean = uiDepth.get() > 0

    /** Quiet shade card for the tracker warning on screen. None while the app is in front. */
    fun syncTrackerShade(notice: TrackerWatch.Notice?) {
        if (!::alerter.isInitialized) return
        if (notice == null || inFront()) {
            alerter.clearTrackerNote()
            return
        }
        alerter.trackerNote(
            title = TrackerCopy.title(notice.kind),
            text = TrackerCopy.notification(notice, config.settings.demoMode),
            deviceKey = notice.deviceKey,
        )
    }

    /** GPS fix for this phone's cell. A held receiver is paused even when the fix is fresh. */
    private fun cellFix(now: Long, paused: Boolean): CellFix {
        val trust = trustFix(now)
        return CellFix(
            lat = trust.lat,
            lon = trust.lon,
            accuracyM = trust.accuracyM,
            speedMps = floodSpeed(now)?.toFloat(),
            paused = paused || trust.paused,
        )
    }
    /** Course for the map button. NaN until GPS has shown a direction. */
    @Volatile
    var travelDeg: Double = Double.NaN
        private set
    private val travelCourse = Geo.TravelCourse()

    /**
     * Latest phone fix still young enough to call current. Older than 30 s is left off.
     * A timestamp a few seconds ahead of [now] still counts. The map reads this between
     * clock ticks, and a newer Location.time was blanking the corner on every pass.
     */
    fun currentFix(now: Long = System.currentTimeMillis()): Pair<Double, Double>? {
        val fix = lastFix ?: return null
        if (!phoneFixIsCurrent(lastFixAt, now)) return null
        return fix
    }

    /** Where the hunt dot is. A local meter picture until the walk is on a fix. Not a place to publish. */
    fun huntFix(now: Long = System.currentTimeMillis()): Pair<Double, Double>? {
        synchronized(motionLock) {
            if (huntMotion) return huntTrack.display()
        }
        val fix = lastGpsFix ?: return null
        if (!phoneFixIsCurrent(lastGpsFixAt, now)) return null
        return fix
    }

    /**
     * Remember a measured reading so the next steps can paint it where the phone is.
     * Does not add a spot. [at] is the time this phone still heard the radio.
     */
    fun armHuntRssi(rssi: Int, at: Long) {
        synchronized(motionLock) {
            if (!huntMotion) return
            if (HuntHeat.level(rssi) == null) return
            if (at < lastHuntRssiAt && HuntHeat.level(lastHuntRssi) != null) return
            lastHuntRssi = rssi
            lastHuntRssiAt = at
            if (huntTrack.raiseHere(rssi, at)) publishHuntLocked()
        }
    }

    /** One hear at the current step position. Later steps keep painting until the radio goes quiet. */
    fun noteHunt(rssi: Int, at: Long) {
        synchronized(motionLock) {
            if (!huntMotion) return
            if (HuntHeat.level(rssi) != null) {
                val had = HuntHeat.level(lastHuntRssi) != null
                if (!had || at >= lastHuntRssiAt) lastHuntRssiAt = at
                lastHuntRssi = rssi
            }
            huntTrack.note(rssi, at)
            publishHuntLocked()
        }
    }

    private val motionLock = Any()
    private val huntTrack = HuntTrack()
    private val stepDetector = HuntStepDetector()
    private var huntMotion = false
    private var huntSensors = false
    /** True while the step detector is registered. */
    private var hardwareSteps = false
    /** The step sensor stayed quiet, so the walking motion counts for the rest of this hunt. */
    private var accelSteps = false
    /** A step-detector event arrived during this hunt. */
    private var hardwareHeard = false
    private var accelStreak = 0
    /** When the step sensor was asked to start reporting. */
    private var stepWatchAt = 0L
    private var magSensorPresent = false
    private var sawMag = false
    private var lastMagUt = Double.NaN
    private var lastMagAcc = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var lastCompass = Double.NaN
    private var lastGame = Double.NaN
    private var lastHuntRssi = 127
    private var lastHuntRssiAt = 0L
    private val rotScratch = FloatArray(9)
    private val oriScratch = FloatArray(3)
    private val _huntPose = MutableStateFlow<HuntPose?>(null)
    /** Null when Hunt is closed. The dot reads this. */
    val huntPose: StateFlow<HuntPose?> = _huntPose.asStateFlow()
    private val _huntCloud = MutableStateFlow<List<GpsSample>>(emptyList())
    /**
     * Hears for this session, in the same coordinates as the dot.
     * The map reads this list. Hears start with the walk, before GPS places it.
     */
    val huntCloud: StateFlow<List<GpsSample>> = _huntCloud.asStateFlow()
    private var publishedCloudGen = -1

    private val huntSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            synchronized(motionLock) {
                if (!huntMotion) return@synchronized
                val now = System.currentTimeMillis()
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val ori = orientationOf(event.values) ?: return@synchronized
                        lastCompass = ori[0]
                        val wasFlat = huntTrack.holdingFlat()
                        huntTrack.heading(ori[0], gameOrNull(), magHealthyLocked(), now)
                        huntTrack.tilt(ori[1], now)
                        if (huntTrack.holdingFlat() != wasFlat) publishHuntLocked()
                    }
                    Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                        val ori = orientationOf(event.values) ?: return@synchronized
                        lastGame = ori[0]
                        if (lastCompass.isFinite()) {
                            huntTrack.heading(lastCompass, lastGame, magHealthyLocked(), now)
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val v = event.values
                        if (v.size < 3) return@synchronized
                        lastMagUt = kotlin.math.sqrt(
                            (v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble(),
                        )
                        lastMagAcc = event.accuracy
                        sawMag = true
                        if (lastCompass.isFinite()) {
                            huntTrack.heading(lastCompass, gameOrNull(), magHealthyLocked(), now)
                        }
                    }
                    Sensor.TYPE_STEP_DETECTOR -> {
                        if (accelSteps) return@synchronized
                        hardwareHeard = true
                        accelStreak = 0
                        if (huntTrack.step(now)) {
                            paintHeardLocked(now)
                            publishHuntLocked()
                        }
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        val v = event.values
                        val mag = kotlin.math.sqrt(
                            (v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble(),
                        ).toFloat()
                        val stepped = stepDetector.sample(mag, event.timestamp / 1_000_000L)
                        if (!stepped) return@synchronized
                        if (!hardwareSteps || accelSteps) {
                            if (huntTrack.step(now)) {
                                paintHeardLocked(now)
                                publishHuntLocked()
                            }
                            return@synchronized
                        }
                        val count = huntAccelStepCounts(
                            hardwarePresent = true,
                            fellBack = false,
                            hardwareHeard = hardwareHeard,
                            watchedAt = stepWatchAt,
                            now = now,
                        )
                        if (!count) {
                            accelStreak = 0
                            return@synchronized
                        }
                        accelStreak += 1
                        if (accelStreak < ACCEL_TAKEOVER_STEPS) return@synchronized
                        accelSteps = true
                        if (huntTrack.step(now)) {
                            paintHeardLocked(now)
                            publishHuntLocked()
                        }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            if (sensor?.type != Sensor.TYPE_MAGNETIC_FIELD) return
            synchronized(motionLock) {
                lastMagAcc = accuracy
                if (!huntMotion || !sawMag || !lastCompass.isFinite()) return@synchronized
                huntTrack.heading(lastCompass, gameOrNull(), magHealthyLocked(), System.currentTimeMillis())
            }
        }
    }

    /**
     * The plain walk still on screen, when this hunt is not on a fix.
     * Null once that walk has been placed, or when Hunt is closed.
     */
    fun plainSnapshot(): HuntPlain? {
        synchronized(motionLock) {
            if (!huntMotion) return null
            return huntTrack.plainPicture()
        }
    }

    /**
     * Steps and phone heading, only while Hunt is open.
     * [seed] places a resumed walk that was already on a fix.
     * [plain] continues a walk that was not on a map. [plainSamples] are its hears.
     * A fresh hunt stays a local picture until two fresh fixes agree and the walk is still short.
     */
    fun beginHuntMotion(
        seed: Pair<Double, Double>? = null,
        plain: HuntPlain? = null,
        plainSamples: List<GpsSample> = emptyList(),
    ) {
        synchronized(motionLock) {
            huntTrack.clear()
            val saved = readHuntStride()
            huntTrack.setStride(saved.first, learnedAlready = saved.second)
            stepDetector.reset()
            sawMag = false
            lastMagUt = Double.NaN
            lastMagAcc = SensorManager.SENSOR_STATUS_UNRELIABLE
            lastCompass = Double.NaN
            lastGame = Double.NaN
            lastHuntRssi = 127
            lastHuntRssiAt = 0L
            accelSteps = false
            hardwareHeard = false
            accelStreak = 0
            huntMotion = true
            val now = System.currentTimeMillis()
            stepWatchAt = now
            if (plain != null) huntTrack.resumePlain(plain, plainSamples)
            val start = huntStartFix(if (plain != null) null else seed, lastGpsFix, lastGpsFixAt, lastGpsAcc, now)
            if (start != null) {
                if (seed != null && plain == null) {
                    huntTrack.gps(start.first, start.second, start.third, now, fresh = true, commit = true)
                } else {
                    val age = now - lastGpsFixAt
                    val fresh = age in -5_000L..5_000L
                    huntTrack.gps(start.first, start.second, start.third, lastGpsFixAt, fresh = fresh)
                }
            }
            publishHuntLocked()
        }
        if (huntSensors) return
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val game = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val magnetic = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        magSensorPresent = magnetic != null
        var armed = false
        for (sensor in listOf(rotation, game, magnetic)) {
            if (listen(sm, sensor)) armed = true
        }
        // The step sensor counts when it is reporting. The accelerometer counts
        // when that sensor is missing, or after it has been quiet.
        val stepSensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        hardwareSteps = listen(sm, stepSensor)
        val accel = listen(sm, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER))
        huntSensors = armed || hardwareSteps || accel
    }

    /** The step sensor exists, Hunt is open, and this phone has not allowed it yet. */
    fun huntNeedsStepPermission(): Boolean {
        if (!huntMotion || hardwareSteps || stepRecognitionGranted()) return false
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        return sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    }

    /**
     * The step sensor just became allowed. Give it a moment to report.
     * The accelerometer stays registered and takes over if the sensor stays quiet.
     * The walk already on the map stays where it is.
     */
    fun useHardwareSteps() {
        if (!huntMotion || hardwareSteps || !stepRecognitionGranted()) return
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val step = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: return
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null) listen(sm, accel)
        if (!listen(sm, step)) return
        hardwareSteps = true
        accelSteps = false
        hardwareHeard = false
        accelStreak = 0
        stepWatchAt = System.currentTimeMillis()
        synchronized(motionLock) { stepDetector.reset() }
    }

    private fun stepRecognitionGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun listen(sm: SensorManager, sensor: Sensor?): Boolean {
        if (sensor == null) return false
        return runCatching {
            sm.registerListener(huntSensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }.getOrDefault(false)
    }

    fun endHuntMotion() {
        if (!huntMotion && !huntSensors) return
        if (huntSensors) {
            val sm = getSystemService(SENSOR_SERVICE) as SensorManager
            sm.unregisterListener(huntSensorListener)
            huntSensors = false
        }
        hardwareSteps = false
        accelSteps = false
        hardwareHeard = false
        accelStreak = 0
        synchronized(motionLock) {
            huntMotion = false
            huntTrack.clear()
            stepDetector.reset()
            publishHuntLocked()
        }
    }

    /** Azimuth in degrees, and degrees off flat. Null when the vector cannot be read. */
    private fun orientationOf(values: FloatArray): DoubleArray? {
        if (values.size < 3) return null
        val ok = runCatching {
            SensorManager.getRotationMatrixFromVector(rotScratch, values)
            SensorManager.getOrientation(rotScratch, oriScratch)
        }.isSuccess
        if (!ok) return null
        val azimuth = Math.toDegrees(oriScratch[0].toDouble())
        val pitch = oriScratch[1].toDouble()
        val roll = oriScratch[2].toDouble()
        val face = (kotlin.math.cos(pitch) * kotlin.math.cos(roll)).coerceIn(-1.0, 1.0)
        val tilt = Math.toDegrees(kotlin.math.acos(face))
        return doubleArrayOf(azimuth, tilt)
    }

    private fun gameOrNull(): Double? = lastGame.takeIf { it.isFinite() }

    /** A missing magnetometer, or no reading yet, stays trusted. A weak or wild field does not. */
    private fun magHealthyLocked(): Boolean {
        if (!magSensorPresent || !sawMag) return true
        if (lastMagAcc < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) return false
        return lastMagUt in HuntTrack.MAG_MIN_UT..HuntTrack.MAG_MAX_UT
    }

    /**
     * Color the dot's current place when this phone heard the radio recently.
     * A quiet radio leaves that ground uncolored. The dot still moves.
     */
    private fun paintHeardLocked(now: Long) {
        if (!HuntHeat.paintable(lastHuntRssi, lastHuntRssiAt, now)) return
        huntTrack.note(lastHuntRssi, now)
    }

    private fun publishHuntLocked() {
        if (!huntMotion) {
            _huntPose.value = null
            if (_huntCloud.value.isNotEmpty()) _huntCloud.value = emptyList()
            publishedCloudGen = -1
            return
        }
        val (lat, lon) = huntTrack.display()
        val gen = huntTrack.hearGen()
        val marks = if (gen == publishedCloudGen) {
            _huntPose.value?.marks ?: emptyList()
        } else {
            val fresh = huntTrack.marks()
            publishedCloudGen = gen
            _huntCloud.value = fresh
            fresh
        }
        _huntPose.value = HuntPose(
            lat = lat,
            lon = lon,
            onGlobe = huntTrack.onGlobe(),
            uncertaintyM = huntTrack.uncertaintyM(),
            holdFlat = huntTrack.holdingFlat(),
            session = huntTrack.session(),
            attachGen = huntTrack.attachGen(),
            marks = marks,
        )
    }

    /** Meters, and whether that length was learned on a walk. Missing file keeps the default. */
    private fun readHuntStride(): Pair<Double, Boolean> {
        val text = runCatching { File(filesDir, HUNT_STRIDE_FILE).readText() }.getOrNull()?.trim()
            ?: return HuntTrack.STRIDE_M to false
        val meters = text.toDoubleOrNull() ?: return HuntTrack.STRIDE_M to false
        return meters.coerceIn(HuntTrack.STRIDE_MIN_M, HuntTrack.STRIDE_MAX_M) to true
    }

    private fun writeHuntStride(meters: Double) {
        runCatching {
            File(filesDir, HUNT_STRIDE_FILE).writeText(meters.toString())
        }
    }

    @Volatile
    private var speedMps: Double? = null
    @Volatile
    private var speedAt: Long = 0L
    private val pathLock = Any()
    private val operatorPath = ArrayList<GpsSample>(64)
    private val driveApex = Geo.DriveApex()
    private var pathLengthM = 0.0
    private var pathSnap: List<GpsSample> = emptyList()
    private var pathSnapN = -1
    private var pathSnapHeadAt = Long.MIN_VALUE
    private var pathSnapHeadLat = Double.NaN
    private var pathSnapHeadLon = Double.NaN
    private var pathSnapTipAt = Long.MIN_VALUE
    private var pathSnapTipLat = Double.NaN
    private var pathSnapTipLon = Double.NaN
    @Volatile
    private var locating = false
    @Volatile
    var pendingCrash: CrashNotes.Pending? = null
    private var started = false
    private val gpsListener = LocationListener { loc -> onProviderFix(loc, gps = true) }
    private val netListener = LocationListener { loc -> onProviderFix(loc, gps = false) }

    override fun onCreate() {
        super.onCreate()
        mapKeys = MapKeyStore(File(filesDir, "map-keys.json"))
        CrashNotes.install(this)
        pendingCrash = runCatching { CrashNotes.pending(this) }.getOrNull()
        if (pendingCrash != null) return
        startUp()
    }

    /** Opens the rest of the app after a crash note is dismissed. A normal launch calls this immediately. */
    fun dismissCrashNote() {
        val exitAt = pendingCrash?.exitAt ?: 0L
        pendingCrash = null
        runCatching { CrashNotes.dismiss(this, exitAt) }
        startUp()
    }

    private fun startUp() {
        if (started) return
        started = true
        config = ConfigStore(this)
        RadioDb.init(this)
        devices = DeviceStore()
        logs = LogStore(this)
        sits = SitStore(this, scope)
        sits.watchPathCameras { cameraReport()?.near }
        places = PlaceStore(File(filesDir, "place-memory.json"))
        trusted = TrustedNetworkStore(File(filesDir, "trusted-networks.json"), scope)
        cells = CellStore(File(filesDir, "cell-places.json"), scope)
        alerter = Alerter(this)
        tak = TakPublisher(this)
        gnssProfile = GnssProfileStore(this)
        gnssRaw = GnssRawFiles(File(filesDir, "gnss-raw"))
        gnss = GnssMonitor(this)
        language = LanguageStore(
            File(filesDir, "language.json"),
            AssetLanguageFiles(assets),
        )
        runBlocking {
            config.load()
            val languageSnap = language.load(Locale.getDefault())
            LanguageText.apply(languageSnap.overlay, languageSnap.activeTag)
            if (gnssProfile.profile.value == null && config.settings.gnssMonitor) {
                config.update { it.copy(settings = it.settings.copy(gnssMonitor = false)) }
            }
            logs.configure(
                config.settings.logFormat,
                config.settings.logRotateKb,
                config.settings.loggingEnabled,
            )
            sits.load()
            places.load()
            trusted.load()
            cells.load()
            val sitOpen = sits.ui.value.open != null
            pairingFlood.setSitOpen(sitOpen)
            wifiFlood.setSitOpen(sitOpen)
        }
        sits.startFlusher()
        places.startFlusher(scope)
        trusted.startFlusher()
        cells.startFlusher()
        syncLocationUpdates()
        syncFlock()
        if (config.settings.alertVoice || config.settings.gnssVoice || config.settings.trustedVoice || config.settings.cellVoice || config.settings.anySensorAlert()) {
            alerter.prepareVoice()
        }
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
        val bleHidden = pairingFlood.hide.value.keys
        val wifiHidden = wifiFlood.hide.value.keys
        if ((bleHidden.isNotEmpty() && device.key in bleHidden) ||
            (wifiHidden.isNotEmpty() && device.key in wifiHidden)
        ) return false
        val travel = if (config.filter.movingWithYou) {
            CoTravel.Ctx.of(operatorPathCopy())
        } else {
            CoTravel.Ctx.None
        }
        val classById = config.fleets.associate { it.id to it.kind }
        val mineKeys = RadioBookmarks.mineKeys(config.watchlist)
        val placeKeys = if (config.filter.seenAtPlaces && config.settings.placeWatch) {
            places.multiPlaceKeys(System.currentTimeMillis(), mineKeys)
        } else {
            emptySet()
        }
        if (!filters.pass(
                device,
                config.filter,
                travel,
                classByFleetId = classById,
                namedRadioKeys = RadioBookmarks.namedKeys(config.watchlist),
                watchedFleetIds = RadioBookmarks.watchedFleetIds(config.watchlist),
                alertDeviceKeys = RadioBookmarks.alertDeviceKeys(config.watchlist),
                mineKeys = mineKeys,
                placeKeys = placeKeys,
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

    fun systemLocationOn(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return if (android.os.Build.VERSION.SDK_INT >= 28) {
            runCatching { lm.isLocationEnabled }.getOrDefault(false)
        } else {
            runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ||
                runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        }
    }

    fun locationTagWanted(): Boolean = config.settings.tagLocation && hasFineLocation()

    fun locationUpdatesActive(): Boolean = locating

    fun gpsFixAgeMs(now: Long): Long? {
        val at = lastFixAt
        return if (at <= 0L) null else (now - at).coerceAtLeast(0L)
    }

    fun hasFineLocation(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun syncLocationUpdates() {
        if (locationTagWanted()) startLocationUpdates()
        else stopLocationUpdates()
    }

    /**
     * Drop any listener registered while Location was off, then attach again.
     * [startLocationUpdates] leaves [locating] false when both providers were off,
     * and a later shade toggle used to return early and never tag a fix.
     */
    fun resubscribeLocationUpdates() {
        stopLocationUpdates()
        syncLocationUpdates()
    }

    fun startLocationUpdates() {
        if (locating || !hasFineLocation()) return
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        seedLastKnown(lm)
        val looper = Looper.getMainLooper()
        var requested = false
        runCatching {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                // Once a second, with no distance floor. A 2 s / 8 m request
                // left the live path as straight chords between sparse fixes.
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, gpsListener, looper)
                requested = true
            }
        }
        runCatching {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4_000L, 15f, netListener, looper)
                requested = true
            }
        }
        locating = requested
    }

    fun stopLocationUpdates() {
        if (!locating) return
        runCatching {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            lm.removeUpdates(gpsListener)
            lm.removeUpdates(netListener)
        }
        locating = false
    }

    /**
     * One cached serving-cell read on the scan cadence.
     * A telephony failure stays in this call. It does not stop the scan loop.
     */
    fun tickCell(paused: Boolean) {
        if (!::cells.isInitialized || !::config.isInitialized) return
        try {
            val now = System.currentTimeMillis()
            val reading = runCatching { CellRadio.read(this) }.getOrDefault(CellReading())
            val settings = config.settings
            cells.tick(
                reading,
                cellFix(now, paused),
                CellSwitches(
                    watch = settings.cellWatch,
                    warnNew = settings.cellWarnNew,
                    warnDrop = settings.cellWarnDrop,
                    learnDays = settings.cellLearnDays,
                ),
                now,
            )
            if (::sits.isInitialized) sits.noteCell(cells.marks())
            announceCell()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Fieldwatch", "cell watch failed", e)
        }
    }

    /** Beep and speak only when an episode becomes the dialog. A shade repost stays quiet. */
    fun announceCell() {
        if (!::cells.isInitialized || !::config.isInitialized || !::alerter.isInitialized) return
        val settings = config.settings
        if (!settings.cellWatch) {
            alerter.clearCellNote()
            return
        }
        if (cellDialogBlocked()) return
        val shown = cells.showing()
        if (shown == null) {
            alerter.clearCellNote()
            return
        }
        val first = synchronized(cellVoiceLock) {
            val open = cells.bookFlow.value.episodes.filter { it.open }.map { it.id }.toSet()
            cellVoiced.retainAll(open)
            cellVoiced.add(shown.id)
        }
        val sentence = CellCopy.sentence(shown.kind)
        alerter.cellAlert(
            title = sentence,
            body = CellCopy.body(shown.kind),
            phrase = if (first && settings.cellVoice) sentence else null,
            beep = first && settings.cellBeep,
            shade = settings.cellShade,
        )
    }

    private fun cellDialogBlocked(): Boolean {
        val gnssUp = ::gnss.isInitialized && gnss.reading.value.notice?.showDialog == true
        val trustedUp = ::trusted.isInitialized && trusted.notice.value != null
        return gnssUp || trustedUp
    }

    /** Start or stop the GNSS check. Safe to call often. The scan service waits past radio start. */
    fun syncGnss() {
        if (!::gnss.isInitialized || !::gnssProfile.isInitialized) return
        if (gnss.isCalibrating) return
        val ready = config.settings.gnssMonitor && gnssProfile.profile.value != null
        if (!ready || !devices.stats.value.scanning) {
            gnss.stop()
            return
        }
        gnss.sync(config.settings, gnssProfile.profile.value)
    }

    private fun onProviderFix(loc: android.location.Location, gps: Boolean) {
        acceptFix(loc, gps)
        if (::gnss.isInitialized) gnss.onTaggedFix(loc, gps)
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
        acceptFix(best, gps = best.provider == LocationManager.GPS_PROVIDER)
    }

    private fun acceptFix(loc: Location, gps: Boolean) {
        if (loc.hasAccuracy() && loc.accuracy > 75f) return
        val age = System.currentTimeMillis() - loc.time
        if (age > 30_000L) return
        lastFix = loc.latitude to loc.longitude
        lastFixAt = loc.time
        if (loc.hasSpeed() && loc.speed >= 0f && loc.speed.isFinite()) {
            val loose = loc.hasSpeedAccuracy() && loc.speedAccuracyMetersPerSecond > 4f
            if (!loose) {
                speedMps = loc.speed.toDouble()
                speedAt = System.currentTimeMillis()
            }
        }
        if (gps) {
            val looseSpeed = loc.hasSpeedAccuracy() && loc.speedAccuracyMetersPerSecond > 4f
            val speed = if (loc.hasSpeed() && loc.speed >= 0f && loc.speed.isFinite() && !looseSpeed) {
                loc.speed
            } else {
                null
            }
            val bearing = if (loc.hasBearing() && loc.bearing.isFinite()) loc.bearing else null
            val bearingAcc = if (loc.hasBearingAccuracy() && loc.bearingAccuracyDegrees.isFinite()) {
                loc.bearingAccuracyDegrees
            } else {
                null
            }
            travelDeg = travelCourse.note(loc.latitude, loc.longitude, speed, bearing, bearingAcc)
        }
        val accuracy = if (loc.hasAccuracy()) loc.accuracy else null
        if (!Geo.acceptDriveFix(gps, accuracy)) return
        val lat = loc.latitude
        val lon = loc.longitude
        val at = loc.time
        val plausible = synchronized(pathLock) {
            val last = operatorPath.lastOrNull()
            last == null || Geo.hopPlausible(last, lat, lon, at)
        }
        if (!plausible) return
        lastGpsFix = lat to lon
        lastGpsFixAt = at
        lastGpsAcc = accuracy ?: Float.NaN
        val learned = synchronized(motionLock) {
            if (!huntMotion) null
            else {
                val nowMs = System.currentTimeMillis()
                val fixAt = if (at > 0L) at else nowMs
                val age = nowMs - fixAt
                val fresh = age in -5_000L..5_000L
                val stride = huntTrack.gps(lat, lon, accuracy ?: 12f, fixAt, fresh = fresh)
                paintHeardLocked(nowMs)
                publishHuntLocked()
                stride
            }
        }
        if (learned != null) writeHuntStride(learned)
        recordOperatorFix(lat, lon, at)
        noteFlock(lat, lon)
    }

    /**
     * Both saved lists, for the sit report. Null when neither file is on the phone,
     * or a file is there and could not be read. A miss leaves the sit's saved step
     * where it is, so a later download can still be counted along the path so far.
     * Call this off the main thread. The parsed lists stay until a file changes.
     */
    fun cameraReport(): PathCameras.Report? {
        val stamp = cameraFileStamp()
        synchronized(cameraReportLock) {
            val held = cameraReportCache
            if (held != null && stamp == cameraReportStamp) return held
        }
        val platesHeader = FlockFiles.peek(filesDir, SensorList.PLATES)
        val otherHeader = FlockFiles.peek(filesDir, SensorList.OTHER)
        if (platesHeader == null && otherHeader == null) return null
        val plates = if (platesHeader != null) FlockFiles.read(filesDir, SensorList.PLATES) else null
        val others = if (otherHeader != null) FlockFiles.read(filesDir, SensorList.OTHER) else null
        if (platesHeader != null && plates == null) return null
        if (otherHeader != null && others == null) return null
        val index = FlockIndex()
        val all = ArrayList<FlockCamera>((plates?.cameras?.size ?: 0) + (others?.cameras?.size ?: 0))
        if (plates != null) all.addAll(plates.cameras)
        if (others != null) all.addAll(others.cameras)
        if (all.isEmpty()) index.markMissing() else index.replace(all)
        val report = PathCameras.Report(plates != null, others != null, index::near)
        synchronized(cameraReportLock) {
            cameraReportCache = report
            cameraReportStamp = stamp
        }
        return report
    }

    /** True when that list's file can be read. Does not load the cameras. */
    fun cameraListsOnDisk(): Pair<Boolean, Boolean> =
        (FlockFiles.peek(filesDir, SensorList.PLATES) != null) to
            (FlockFiles.peek(filesDir, SensorList.OTHER) != null)

    private fun cameraFileStamp(): Long {
        fun part(list: SensorList): Long {
            val file = FlockFiles.file(filesDir, list)
            if (!file.isFile) return 0L
            return file.lastModified() xor file.length()
        }
        val plates = part(SensorList.PLATES)
        val other = part(SensorList.OTHER)
        return plates xor other.rotateLeft(1) xor if (plates == 0L && other == 0L) 1L else 0L
    }

    /**
     * Reload the saved camera list when the layer is on.
     * [force] replaces a list already in memory after a download.
     */
    fun syncFlock(force: Boolean = false) {
        if (!::config.isInitialized) return
        val settings = config.settings
        val showPlates = settings.flockLayer
        val showOther = settings.sensorLayer
        if (!settings.anySensorAlert()) {
            flockLatch.clear()
            if (::alerter.isInitialized) alerter.clearFlockNote()
        }
        if (!showPlates && !showOther) {
            flockIndex.clear()
            _flockRevision.value = flockIndex.revision
            return
        }
        if (!force && flockIndex.loaded) return
        val gen = ++flockLoadGen
        scope.launch(Dispatchers.IO) {
            try {
                val cameras = ArrayList<FlockCamera>()
                if (showPlates) FlockFiles.read(filesDir, SensorList.PLATES)?.cameras?.let { cameras.addAll(it) }
                if (showOther) FlockFiles.read(filesDir, SensorList.OTHER)?.cameras?.let { cameras.addAll(it) }
                if (gen != flockLoadGen) return@launch
                val now = config.settings
                if (now.flockLayer != showPlates || now.sensorLayer != showOther) return@launch
                if (cameras.isEmpty()) flockIndex.markMissing() else flockIndex.replace(cameras)
                _flockRevision.value = flockIndex.revision
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (gen != flockLoadGen) return@launch
                if (!flockIndex.loaded) flockIndex.markMissing()
                _flockRevision.value = flockIndex.revision
            }
        }
    }

    private fun noteFlock(lat: Double, lon: Double) {
        if (!::config.isInitialized || !::alerter.isInitialized) return
        val settings = config.settings
        if (!settings.anySensorAlert()) return
        val hit = runCatching {
            val nearby = flockIndex.near(lat, lon, FlockApproach.REARM_M + 40.0)
                .filter { settings.alertsFor(it.kind) }
            flockLatch.consider(lat, lon, travelDeg, floodSpeed(), nearby)
        }.getOrNull() ?: return
        val (key, values) = sensorNotice(hit)
        alerter.flockNote(if (values.isEmpty()) LanguageText.text(key) else LanguageText.text(key, values))
    }

    /** GPS speed in m/s, or null when the latest fix has no usable speed. */
    fun floodSpeed(now: Long = System.currentTimeMillis()): Double? {
        val speed = speedMps ?: return null
        if (now - speedAt > 12_000L) return null
        return speed
    }

    fun recordOperatorFix(lat: Double, lon: Double, at: Long = System.currentTimeMillis()) {
        synchronized(pathLock) {
            val last = operatorPath.lastOrNull()
            if (last != null && !Geo.hopPlausible(last, lat, lon, at)) {
                return
            }
            val before = operatorPath.size
            val frozen = if (before >= 2) operatorPath[before - 2] else null
            val tip = if (before >= 1) operatorPath[before - 1] else null
            val trimmed = Geo.extendDrivePath(operatorPath, driveApex, lat, lon, at, cap = Geo.TRACK_CAP)
            pathLengthM = if (trimmed) {
                Geo.pathLengthM(operatorPath)
            } else {
                Geo.advanceDriveLength(pathLengthM, before, frozen, tip, operatorPath)
            }
        }
        if (::sits.isInitialized) sits.recordPath(lat, lon, at)
    }

    fun operatorPathCopy(): List<GpsSample> = synchronized(pathLock) {
        val n = operatorPath.size
        val head = if (n > 0) operatorPath[0] else null
        val tip = if (n > 0) operatorPath[n - 1] else null
        if (n == pathSnapN &&
            samePathEnd(head, pathSnapHeadAt, pathSnapHeadLat, pathSnapHeadLon) &&
            samePathEnd(tip, pathSnapTipAt, pathSnapTipLat, pathSnapTipLon)
        ) {
            return pathSnap
        }
        val copy = operatorPath.toList()
        pathSnap = copy
        pathSnapN = n
        pathSnapHeadAt = head?.at ?: Long.MIN_VALUE
        pathSnapHeadLat = head?.lat ?: Double.NaN
        pathSnapHeadLon = head?.lon ?: Double.NaN
        pathSnapTipAt = tip?.at ?: Long.MIN_VALUE
        pathSnapTipLat = tip?.lat ?: Double.NaN
        pathSnapTipLon = tip?.lon ?: Double.NaN
        copy
    }

    private fun samePathEnd(sample: GpsSample?, at: Long, lat: Double, lon: Double): Boolean {
        if (sample == null) return at == Long.MIN_VALUE
        return sample.at == at && sample.lat == lat && sample.lon == lon
    }

    fun operatorPathLengthM(): Double = synchronized(pathLock) { pathLengthM }

    fun resetFollowSession() {
        synchronized(pathLock) {
            operatorPath.clear()
            driveApex.clear()
            pathLengthM = 0.0
            pathSnap = emptyList()
            pathSnapN = -1
            pathSnapHeadAt = Long.MIN_VALUE
            pathSnapHeadLat = Double.NaN
            pathSnapHeadLon = Double.NaN
            pathSnapTipAt = Long.MIN_VALUE
            pathSnapTipLat = Double.NaN
            pathSnapTipLon = Double.NaN
        }
        devices.clearGpsTrails()
    }

    companion object {
        private const val MAX_ARRIVAL_KEYS = 2_500
        private const val HUNT_STRIDE_FILE = "hunt-stride.txt"
        /** Walking-motion steps with a silent step sensor before that sensor is left behind. */
        private const val ACCEL_TAKEOVER_STEPS = 3
    }
}

data class ArrivalsState(
    val active: Boolean = false,
    val knownKeys: Set<String> = emptySet(),
    val learningUntil: Long = 0L,
    val bufferStartedAt: Long = 0L,
)

/** Location.time may lead the clock that painted the last frame. Five seconds covers that. */
internal fun phoneFixIsCurrent(fixAt: Long, now: Long): Boolean {
    val age = now - fixAt
    return age >= -5_000L && age <= 30_000L
}

/**
 * Where a hunt may pin its start. A resumed walk already has a place, so it
 * attaches. A fresh hunt passes the saved fix and the accuracy it reported.
 * No accuracy is not a tight fix. The track still refuses anything wider than
 * about 12 m.
 */
internal fun huntStartFix(
    seed: Pair<Double, Double>?,
    fix: Pair<Double, Double>?,
    fixAt: Long,
    accuracyM: Float,
    now: Long,
): Triple<Double, Double, Float>? {
    if (seed != null) return Triple(seed.first, seed.second, 8f)
    val start = fix?.takeIf { phoneFixIsCurrent(fixAt, now) } ?: return null
    if (!accuracyM.isFinite()) return null
    return Triple(start.first, start.second, accuracyM)
}
