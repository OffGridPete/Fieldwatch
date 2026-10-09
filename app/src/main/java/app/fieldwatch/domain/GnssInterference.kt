package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Phone GNSS interference and spoofing hints.
 * Pure Kotlin. No Android types. The monitor feeds one epoch at a time.
 *
 * This notices a change. A hit already present at the start does not fire.
 * A Wi-Fi router can look like interference. Nothing here names a source,
 * a distance, or a direction.
 */
enum class GnssConfidence {
    LOW, MEDIUM, HIGH;

    fun label(): String = when (this) {
        LOW -> "Low"
        MEDIUM -> "Medium"
        HIGH -> "High"
    }

    fun meets(floor: GnssConfidence): Boolean = ordinal >= floor.ordinal

    fun lower(): GnssConfidence? = when (this) {
        HIGH -> MEDIUM
        MEDIUM -> LOW
        LOW -> null
    }
}

enum class GnssKind { INTERFERENCE, SPOOFING, MOCK }

@Serializable(with = GnssSensitivitySerializer::class)
enum class GnssSensitivity {
    LOW, MEDIUM, HIGH;

    fun label(): String = when (this) {
        LOW -> "Low"
        MEDIUM -> "Medium"
        HIGH -> "High"
    }
}

/** Reads the first field-test names (Less, Normal, More) so an already-saved setup still loads. */
object GnssSensitivitySerializer : KSerializer<GnssSensitivity> {
    override val descriptor = PrimitiveSerialDescriptor("GnssSensitivity", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: GnssSensitivity) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): GnssSensitivity = when (val raw = decoder.decodeString()) {
        "LOW", "LESS" -> GnssSensitivity.LOW
        "MEDIUM", "NORMAL" -> GnssSensitivity.MEDIUM
        "HIGH", "MORE" -> GnssSensitivity.HIGH
        else -> throw SerializationException("Unknown GNSS sensitivity: $raw")
    }
}

@Serializable
enum class GnssAlertFloor {
    LOW, MEDIUM, HIGH;

    fun label(): String = when (this) {
        LOW -> "Low"
        MEDIUM -> "Medium"
        HIGH -> "High"
    }

    fun toConfidence(): GnssConfidence = when (this) {
        LOW -> GnssConfidence.LOW
        MEDIUM -> GnssConfidence.MEDIUM
        HIGH -> GnssConfidence.HIGH
    }
}

enum class GnssBand { L1, B1I, G1, L2, L5, E5B, E6, UNKNOWN }

/** Android GnssStatus constellation ints. Kept as ints so this file stays Android-free. */
object GnssConstellation {
    const val UNKNOWN = 0
    const val GPS = 1
    const val SBAS = 2
    const val GLONASS = 3
    const val QZSS = 4
    const val BEIDOU = 5
    const val GALILEO = 6
    const val IRNSS = 7
}

data class BandKey(val constellation: Int, val band: GnssBand)

data class BandEpoch(
    val agcDb: Double?,
    val agcFromEventLevel: Boolean,
    val cn0Top3: Double?,
    val svTracked: Int,
    val cn0BySv: Map<Int, Double> = emptyMap(),
)

data class GnssEpoch(
    val elapsedMs: Long,
    val wallMs: Long,
    val clockDiscontinuity: Int,
    val bands: Map<BandKey, BandEpoch>,
)

/** Side checks the monitor already decided. No coordinates. */
data class GnssSide(
    val timeDisagree: Boolean = false,
    val positionDisagree: Boolean = false,
    val impossibleHop: Boolean = false,
    val mock: Boolean = false,
    val fixLost: Boolean = false,
)

/**
 * GPS fix against the network fix.
 * A network accuracy of 100 m or worse cannot vote. The centers have to sit
 * clearly past both accuracy circles, and the mismatch has to hold.
 */
object GnssPositionWitness {
    const val MAX_AGE_MS = 10_000L
    const val COARSE_ACC_M = 100.0
    const val CLEAR_M = 100.0
    const val HOLD_MS = 15_000L

    data class Hold(val sinceMs: Long, val on: Boolean)

    fun rawDisagree(
        gpsAccM: Double,
        netAccM: Double,
        apartM: Double,
        gpsAgeMs: Long,
        netAgeMs: Long,
    ): Boolean {
        if (gpsAgeMs < 0L || netAgeMs < 0L) return false
        if (gpsAgeMs > MAX_AGE_MS || netAgeMs > MAX_AGE_MS) return false
        if (!(gpsAccM > 0.0) || !(netAccM > 0.0) || netAccM >= COARSE_ACC_M) return false
        if (!apartM.isFinite()) return false
        return apartM > gpsAccM + netAccM + CLEAR_M
    }

    /** sinceMs is 0 while the raw check is clear. */
    fun hold(raw: Boolean, sinceMs: Long, nowMs: Long): Hold {
        if (!raw || nowMs < sinceMs) return Hold(0L, false)
        val since = if (sinceMs == 0L) nowMs else sinceMs
        return Hold(since, nowMs - since >= HOLD_MS)
    }
}

data class GnssConfig(
    val sensitivity: GnssSensitivity = GnssSensitivity.MEDIUM,
    val spoofChecks: Boolean = false,
    val correlation: Boolean = false,
    val invertAgc: Boolean = false,
    /** From this phone's calibration. 0 keeps the sensitivity bars. */
    val agcFloorDb: Double = 0.0,
    val cn0FloorDb: Double = 0.0,
    /** Measurement gaps are normal on this phone. Do not treat them as Medium. */
    val ignoreStopped: Boolean = false,
    /** Readings in this span after the check starts are not stored as the baseline. */
    val settleMs: Long = 60_000L,
)

data class GnssThresholds(
    val agcDropDb: Double,
    val cn0DropDb: Double,
    val cn0OnlyDropDb: Double,
)

fun GnssSensitivity.thresholds(): GnssThresholds = when (this) {
    GnssSensitivity.LOW -> GnssThresholds(agcDropDb = 8.0, cn0DropDb = 5.0, cn0OnlyDropDb = 8.0)
    GnssSensitivity.MEDIUM -> GnssThresholds(agcDropDb = 5.0, cn0DropDb = 3.0, cn0OnlyDropDb = 6.0)
    GnssSensitivity.HIGH -> GnssThresholds(agcDropDb = 3.0, cn0DropDb = 2.0, cn0OnlyDropDb = 4.0)
}

/** A calibrated phone raises a bar that sits inside its own quiet wander. It never lowers one. */
fun GnssThresholds.floored(agcFloorDb: Double, cn0FloorDb: Double): GnssThresholds {
    val agc = agcFloorDb.coerceAtLeast(0.0)
    val cn = cn0FloorDb.coerceAtLeast(0.0)
    return copy(
        agcDropDb = max(agcDropDb, agc),
        cn0DropDb = max(cn0DropDb, cn),
        cn0OnlyDropDb = max(cn0OnlyDropDb, cn),
    )
}

data class GnssLive(
    val kind: GnssKind,
    val confidence: GnssConfidence,
    val startedWall: Long,
    val detail: String,
    val adapted: Boolean,
    val rose: Boolean,
    /** Plain reason for the popup. Empty on older callers. */
    val why: String = "",
    /** Band numbers for the popup, after the reason. */
    val numbers: String = "",
)

data class GnssSnapshot(
    val statusLine: String,
    val bandLines: List<String>,
    val lives: List<GnssLive>,
    val marks: List<GnssMark>,
    val agcSeen: Boolean,
    val armed: Boolean,
    val learned: Int,
)

@Serializable
data class GnssMark(
    val at: Long,
    val endedAt: Long = 0L,
    val kind: String,
    val confidence: String,
    val detail: String,
) {
    fun reportLine(): String {
        val head = when (kind) {
            "spoofing" -> "Possible GNSS spoofing"
            "mock" -> "Mock location app"
            else -> "Possible GNSS interference"
        }
        val end = if (endedAt > 0L) " Ended ${GnssCopy.utcHm(endedAt)} UTC." else ""
        return "${GnssCopy.utcHm(at)} UTC. $head. $confidence. $detail.$end"
    }
}

data class GnssNotice(
    val showDialog: Boolean,
    val title: String,
    val body: String,
    val line: String,
    /** True for the red line after the hit has cleared. */
    val ended: Boolean = false,
    /** Receiver numbers. The popup shows these after the plain reason. */
    val details: String = "",
)

data class GnssCue(
    val title: String,
    val text: String,
    val phrase: String,
)

data class GnssPresentation(
    val notice: GnssNotice?,
    val cue: GnssCue?,
)

object GnssBands {
    fun ofHz(hz: Double?): GnssBand {
        if (hz == null || hz <= 0.0) return GnssBand.L1
        val mhz = hz / 1_000_000.0
        val centers = listOf(
            1575.42 to GnssBand.L1,
            1561.098 to GnssBand.B1I,
            1227.60 to GnssBand.L2,
            1176.45 to GnssBand.L5,
            1207.14 to GnssBand.E5B,
            1268.52 to GnssBand.E6,
            1278.75 to GnssBand.E6,
        )
        val nearest = centers.minByOrNull { abs(it.first - mhz) }
        if (nearest != null && abs(nearest.first - mhz) <= 10.0) return nearest.second
        if (mhz in 1588.0..1616.0) return GnssBand.G1
        return GnssBand.UNKNOWN
    }

    fun label(key: BandKey): String {
        val constellation = when (key.constellation) {
            GnssConstellation.GPS -> "GPS"
            GnssConstellation.SBAS -> "SBAS"
            GnssConstellation.GLONASS -> "GLONASS"
            GnssConstellation.QZSS -> "QZSS"
            GnssConstellation.BEIDOU -> "BeiDou"
            GnssConstellation.GALILEO -> "Galileo"
            GnssConstellation.IRNSS -> "IRNSS"
            else -> "GNSS"
        }
        val band = when (key.band) {
            GnssBand.L1 -> if (key.constellation == GnssConstellation.GALILEO) "E1" else "L1"
            GnssBand.B1I -> "B1I"
            GnssBand.G1 -> "G1"
            GnssBand.L2 -> "L2"
            GnssBand.L5 -> "L5"
            GnssBand.E5B -> "E5b"
            GnssBand.E6 -> "E6"
            GnssBand.UNKNOWN -> "band"
        }
        return "$constellation $band"
    }
}

object GnssCopy {
    const val INTRO =
        "Fieldwatch watches this phone's GPS receiver and notices a change. If this was already happening when the check started, it can be missed."
    const val ROUTER =
        "A Wi-Fi router or other electronics next to the phone can cause this. Fieldwatch cannot tell where it comes from or how far away it is."
    const val SPOOF_LIMIT =
        "Spoofing checks are partial. A careful spoofer can avoid them. A wrong position can also be a side effect of interference."
    const val MOCK =
        "A mock location app is on. That is the developer mock-location switch, not a radio spoofer."

    fun utcHm(ms: Long): String = stamp(ms, utc = true)

    fun localHm(ms: Long): String = stamp(ms, utc = false)

    fun title(kinds: Set<GnssKind>, confidence: GnssConfidence): String {
        val head = head(kinds)
        return "$head (${confidence.label()})"
    }

    fun banner(kinds: Set<GnssKind>, confidence: GnssConfidence): String =
        "${head(kinds)} · ${confidence.label()}"

    fun ended(kinds: Set<GnssKind>, wall: Long): String =
        "${head(kinds)} ended ${localHm(wall)}"

    fun body(detail: String, kinds: Set<GnssKind>, adapted: Boolean): String = buildString {
        append(detail.trim())
        if (adapted) {
            append("\n\nThe receiver may have adapted. The condition may still be present.")
        }
        append("\n\n")
        append(INTRO)
        append("\n\n")
        append(ROUTER)
        if (GnssKind.SPOOFING in kinds) {
            append("\n\n")
            append(SPOOF_LIMIT)
        }
        if (GnssKind.MOCK in kinds) {
            append("\n\n")
            append(MOCK)
        }
    }.trim()

    /** Dialog title. GPS, not GNSS, so the popup does not open on a jargon word. */
    fun popupTitle(kinds: Set<GnssKind>, confidence: GnssConfidence): String {
        if (GnssKind.MOCK in kinds && kinds.size == 1) return "Mock location app"
        return "${head(kinds).replace("GNSS", "GPS")} (${confidence.label()})"
    }

    /**
     * Plain popup. The reason comes first. Numbers stay in [details] on the notice.
     * A hand-built live with no reason keeps [body].
     */
    fun popup(lives: List<GnssLive>, kinds: Set<GnssKind>, adapted: Boolean): Pair<String, String>? {
        val reasons = lives.map { it.why.trim() }.filter { it.isNotEmpty() }
        if (reasons.isEmpty()) return null
        val text = buildString {
            if (lives.size == 1) {
                append(reasons.single())
            } else {
                lives.forEach { live ->
                    if (live.why.isBlank()) return@forEach
                    if (isNotEmpty()) append("\n\n")
                    append(head(setOf(live.kind)).replace("GNSS", "GPS"))
                    append(". ")
                    append(live.why.trim())
                }
            }
            if (adapted) {
                append("\n\nThe receiver may have adjusted. The condition may still be there.")
            }
            val note = popupNote(kinds)
            if (note.isNotEmpty()) {
                append("\n\n")
                append(note)
            }
        }.trim()
        val numbers = lives.map { it.numbers.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return text to numbers
    }

    fun interferenceWhy(
        bothDropped: Boolean,
        twoSystems: Boolean,
        lostSatellites: Boolean,
        paused: Boolean,
        signalOnly: Boolean,
    ): String = when {
        lostSatellites ->
            "The receiver turned its gain down and lost satellites."
        twoSystems && bothDropped ->
            "This phone's GPS got weaker on more than one satellite system. The receiver turned its gain down and the signals dropped."
        bothDropped ->
            "This phone's GPS got weaker. The receiver turned its gain down and the satellite signals dropped."
        paused ->
            "GPS readings paused. On this phone that can look like interference."
        signalOnly ->
            "The satellite signals got weaker. This phone is not reporting receiver gain, so this hint is lighter."
        else ->
            "The receiver turned its gain down more than usual. The satellite signals did not drop as much."
    }

    fun spoofWhy(
        gain: Boolean,
        clock: Boolean,
        position: Boolean,
        jumped: Boolean,
        faded: Boolean,
        fromInterference: Boolean,
    ): String {
        val parts = ArrayList<String>()
        if (gain) parts += "The receiver turned its gain down, and the satellite signals stayed up."
        if (clock) parts += "The GPS clock does not match network time."
        if (position) parts += "The GPS position does not match the network position."
        if (jumped) parts += "The GPS position jumped faster than this phone could have moved."
        if (faded && !gain) {
            parts += "Several satellite signals faded at the same time. A hand, a roof, or a pocket can do that too."
        }
        if (faded && gain) parts += "Several satellite signals also faded together."
        if (fromInterference) parts += "The interference on this phone can cause that too."
        return parts.joinToString(" ")
    }

    private fun popupNote(kinds: Set<GnssKind>): String = when {
        GnssKind.MOCK in kinds && kinds.size == 1 -> ""
        GnssKind.SPOOFING in kinds && GnssKind.INTERFERENCE in kinds ->
            "A router, a charger, or another phone against this one can do the same thing. A wrong position can come from that too. Fieldwatch cannot tell the source or how far away it is."
        GnssKind.SPOOFING in kinds ->
            "This is a hint. A careful fake GPS signal can get past these checks. Fieldwatch cannot tell the source or how far away it is."
        else ->
            "A router, a charger, or another phone against this one can do the same thing. Fieldwatch cannot tell the source or how far away it is."
    }

    fun phrase(kinds: Set<GnssKind>): String = when {
        GnssKind.MOCK in kinds && kinds.size == 1 -> "Mock location app is on"
        GnssKind.SPOOFING in kinds && GnssKind.INTERFERENCE !in kinds ->
            "Possible G P S spoofing. Position may be wrong."
        else -> "Possible G P S interference"
    }

    private fun head(kinds: Set<GnssKind>): String = when {
        GnssKind.MOCK in kinds && GnssKind.INTERFERENCE !in kinds && GnssKind.SPOOFING !in kinds ->
            "Mock location app"
        GnssKind.INTERFERENCE in kinds && GnssKind.SPOOFING in kinds ->
            "Possible GNSS interference and spoofing"
        GnssKind.SPOOFING in kinds -> "Possible GNSS spoofing"
        else -> "Possible GNSS interference"
    }

    private fun stamp(ms: Long, utc: Boolean): String {
        val fmt = SimpleDateFormat("HH:mm", if (utc) Locale.US else Locale.getDefault())
        if (utc) fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }
}

/**
 * Popup and red line. The alert floor gates the popup, the line, the beep, and voice.
 * A weaker hit still stays on the sit. Mock location always alerts.
 */
class GnssAlerts {
    private var showDialog = false
    private val popped = HashMap<String, GnssConfidence>()
    private var lastTitle = ""
    private var lastLine = ""
    private var lastKinds = setOf(GnssKind.INTERFERENCE)
    private var endedLine: String? = null
    private var endedUntil = 0L

    fun dismiss() {
        showDialog = false
    }

    fun reset() {
        showDialog = false
        popped.clear()
        endedLine = null
        endedUntil = 0L
    }

    fun onLives(lives: List<GnssLive>, floor: GnssConfidence, nowWall: Long): GnssPresentation {
        val hot = lives.filter { it.kind == GnssKind.MOCK || it.confidence.meets(floor) }
        if (hot.isEmpty()) {
            val had = popped.isNotEmpty()
            popped.clear()
            showDialog = false
            if (had && endedLine == null) {
                endedLine = GnssCopy.ended(lastKinds, nowWall)
                endedUntil = nowWall + ENDED_MS
            }
            val line = endedLine?.takeIf { nowWall <= endedUntil }
            if (line == null) endedLine = null
            val notice = line?.let {
                GnssNotice(showDialog = false, title = lastTitle, body = "", line = it, ended = true)
            }
            return GnssPresentation(notice, null)
        }
        endedLine = null
        val kinds = hot.map { it.kind }.toSet()
        val confidence = hot.maxOf { it.confidence }
        val detail = hot.joinToString("\n\n") { it.detail }
        val adapted = hot.any { it.adapted }
        val line = GnssCopy.banner(kinds, confidence)
        val plain = GnssCopy.popup(hot, kinds, adapted)
        val title = plain?.let { GnssCopy.popupTitle(kinds, confidence) } ?: GnssCopy.title(kinds, confidence)
        val body = plain?.first ?: GnssCopy.body(detail, kinds, adapted)
        val details = plain?.second.orEmpty()
        lastTitle = title
        lastLine = line
        lastKinds = kinds
        var cue: GnssCue? = null
        var open = false
        for (live in hot) {
            val key = "${live.kind}:${live.startedWall}"
            val prev = popped[key]
            if (prev == null || live.confidence > prev || live.rose) {
                popped[key] = maxOf(live.confidence, prev ?: live.confidence)
                open = true
            }
        }
        popped.keys.retainAll(hot.map { "${it.kind}:${it.startedWall}" }.toSet())
        if (open) {
            showDialog = true
            val shade = if (details.isBlank()) body else body + "\n\n" + details
            cue = GnssCue(
                title = title,
                text = shade.take(700),
                phrase = GnssCopy.phrase(kinds),
            )
        }
        return GnssPresentation(
            GnssNotice(
                showDialog = showDialog,
                title = title,
                body = body,
                line = line,
                details = details,
            ),
            cue,
        )
    }

    private companion object {
        const val ENDED_MS = 10 * 60_000L
    }
}

class GnssDetector(config: GnssConfig = GnssConfig()) {
    var config: GnssConfig = config

    fun reset() {
        bands.clear()
        marks.clear()
        svHist.clear()
        lastClock = Int.MIN_VALUE
        agcSeen = false
        interference = null
        spoofing = null
        mockEp = null
        interferenceEndedElapsed = 0L
        s1Since = 0L
        corrStreak = 0
        recover = 0
        settleStart = Long.MIN_VALUE
    }

    private val bands = LinkedHashMap<BandKey, Track>()
    private val marks = ArrayList<GnssMark>()
    private val svHist = HashMap<Int, ArrayDeque<Double>>()
    private var lastClock = Int.MIN_VALUE
    private var agcSeen = false
    private var interference: Episode? = null
    private var spoofing: Episode? = null
    private var mockEp: Episode? = null
    private var interferenceEndedElapsed = 0L
    private var s1Since = 0L
    private var corrStreak = 0
    private var recover = 0
    private var settleStart = Long.MIN_VALUE

    fun onEpoch(epoch: GnssEpoch, side: GnssSide = GnssSide()): GnssSnapshot {
        if (config.settleMs > 0L) {
            if (settleStart == Long.MIN_VALUE || epoch.elapsedMs < settleStart) {
                settleStart = epoch.elapsedMs
            }
            val waited = epoch.elapsedMs - settleStart
            if (waited < config.settleMs) {
                val left = ((config.settleMs - waited + 999L) / 1000L).coerceAtLeast(1L)
                return GnssSnapshot(
                    statusLine = "settling ${left}s",
                    bandLines = emptyList(),
                    lives = emptyList(),
                    marks = marks.toList(),
                    agcSeen = agcSeen,
                    armed = false,
                    learned = 0,
                )
            }
        }
        if (epoch.clockDiscontinuity != lastClock) {
            if (lastClock != Int.MIN_VALUE) {
                svHist.clear()
                corrStreak = 0
            }
            lastClock = epoch.clockDiscontinuity
        }
        val seen = HashSet<BandKey>()
        for ((key, raw) in epoch.bands) {
            val agc = cleanAgc(raw.agcDb)
            if (agc != null) agcSeen = true
            val cn0 = raw.cn0Top3?.takeIf { it.isFinite() && it > 0.0 }
            val track = bands.getOrPut(key) { Track() }
            track.push(Sample(agc, cn0, raw.svTracked.coerceAtLeast(0), raw.agcFromEventLevel && agc != null))
            track.missing = 0
            seen += key
        }
        for ((key, track) in bands) {
            if (key !in seen) track.missing++
        }
        pushSv(epoch)
        val metrics = bands.mapValues { it.value.metrics(config.invertAgc) }
        val learned = bands.values.maxOfOrNull { it.samples.size } ?: 0
        val armed = learned >= ARM
        val groups = groups()
        val limits = config.sensitivity.thresholds().floored(config.agcFloorDb, config.cn0FloorDb)
        var anyJ1 = false
        var anyJ1J2 = false
        var j3 = false
        var j4Groups = 0
        var stopped = false
        val j12Groups = HashSet<BandKey>()
        for ((key, track) in bands) {
            val m = metrics[key] ?: continue
            val j1Now = m.dAgc != null && m.dAgc <= -limits.agcDropDb
            val j2Now = m.dCn0 != null && m.dCn0 <= -limits.cn0DropDb
            val j1 = track.flag(track.j1, j1Now)
            val j2 = track.flag(track.j2, j2Now)
            if (j1Now) track.lastJ1 = epoch.elapsedMs
            if (j1) anyJ1 = true
            if (j1 && j2) {
                anyJ1J2 = true
                j12Groups += groups[key] ?: key
            }
            val baseSv = m.baseSv
            val lostSv = m.sv == 0 || (baseSv != null && baseSv > 0.0 && m.sv <= baseSv * 0.25)
            if (j1 && m.eventAgc && (lostSv || side.fixLost)) j3 = true
            if (!config.ignoreStopped &&
                track.missing in 1..10 &&
                epoch.elapsedMs - track.lastJ1 <= 10_000L
            ) {
                stopped = true
            }
        }
        j4Groups = j12Groups.size
        val cn0Only = cn0Only(metrics, limits, armed)
        val eventLevel = bands.values.any { it.samples.lastOrNull()?.eventAgc == true }
        val interferenceLevel = when {
            !agcSeen && cn0Only -> GnssConfidence.LOW
            agcSeen && ((anyJ1J2 && j4Groups >= 2) || (eventLevel && anyJ1J2 && j3)) -> GnssConfidence.HIGH
            agcSeen && (anyJ1J2 || stopped) -> GnssConfidence.MEDIUM
            agcSeen && (anyJ1 || cn0Only) -> GnssConfidence.LOW
            else -> null
        }
        val bandBits = bandBits(metrics)
        val bandDetail = bandBits.joinToString(" ")
        val bandNumbers = bandBits.joinToString("\n")
        val holding = interference != null
        if (interferenceLevel != null) recover = 0 else if (holding) recover++
        val interferenceWhy = if (interferenceLevel == null) {
            ""
        } else {
            GnssCopy.interferenceWhy(
                bothDropped = anyJ1J2,
                twoSystems = j4Groups >= 2,
                lostSatellites = j3 && anyJ1J2,
                paused = stopped && !anyJ1J2,
                signalOnly = cn0Only && !anyJ1,
            )
        }
        interference = updateEpisode(
            interference,
            interferenceLevel,
            bandDetail.ifBlank { "Signal changed." },
            epoch,
            adapted = false,
            why = interferenceWhy,
            numbers = bandNumbers,
        )?.let { if (interferenceLevel == null) it.copy(rose = false) else it }
        if (interference != null && epoch.elapsedMs - interference!!.startedElapsed > ADAPT_MS && interferenceLevel != null) {
            interference = interference!!.copy(
                endedWall = epoch.wallMs,
                adapted = true,
                detail = interference!!.detail,
            )
            interferenceEndedElapsed = epoch.elapsedMs
            upsert(interference!!, "interference")
            interference = null
            recover = 0
            clearFreeze()
        } else if (interference != null && interferenceLevel == null) {
            val age = epoch.elapsedMs - interference!!.startedElapsed
            val canClear = age >= HOLD_MS && recover >= RECOVER
            val adapted = age >= ADAPT_MS
            if (canClear || adapted) {
                val ep = interference!!.copy(endedWall = epoch.wallMs, adapted = adapted)
                interferenceEndedElapsed = epoch.elapsedMs
                upsert(ep, "interference")
                interference = null
                recover = 0
                clearFreeze()
            }
        }
        if (interference != null) {
            freeze(metrics)
            val shown = if (bandDetail.isNotBlank()) bandDetail else interference!!.detail
            val nums = if (bandNumbers.isNotBlank()) bandNumbers else interference!!.numbers
            upsert(interference!!.copy(detail = shown, numbers = nums), "interference")
            if (bandDetail.isNotBlank()) interference = interference!!.copy(detail = bandDetail, numbers = bandNumbers)
        }
        spoofing = spoofEpisode(epoch, side, metrics, limits, bandDetail, bandNumbers)
        if (spoofing != null) upsert(spoofing!!, "spoofing")
        mockEp = mockEpisode(epoch, side)
        if (mockEp != null) upsert(mockEp!!, "mock")
        val lives = ArrayList<GnssLive>()
        interference?.let { lives += it.toLive(GnssKind.INTERFERENCE) }
        spoofing?.let { lives += it.toLive(GnssKind.SPOOFING) }
        mockEp?.let { lives += it.toLive(GnssKind.MOCK) }
        val status = when {
            !armed -> "learning $learned/$ARM s"
            lives.isNotEmpty() -> {
                val top = lives.maxBy { it.confidence }
                val name = when (top.kind) {
                    GnssKind.SPOOFING -> "spoofing"
                    GnssKind.MOCK -> "mock location"
                    GnssKind.INTERFERENCE -> "interference"
                }
                "ALERT: $name (${top.confidence.label()})"
            }
            !agcSeen -> "AGC not available on this phone. Signal-strength only, low confidence."
            else -> "armed"
        }
        return GnssSnapshot(
            statusLine = status,
            bandLines = bandLines(metrics),
            lives = lives,
            marks = marks.toList(),
            agcSeen = agcSeen,
            armed = armed,
            learned = learned,
        )
    }

    private fun spoofEpisode(
        epoch: GnssEpoch,
        side: GnssSide,
        metrics: Map<BandKey, Metrics>,
        limits: GnssThresholds,
        bandDetail: String,
        bandNumbers: String,
    ): Episode? {
        if (!config.spoofChecks) {
            s1Since = 0L
            return endSpoof(epoch, spoofing)
        }
        val s1 = metrics.values.any { m ->
            m.dAgc != null && m.dAgc <= -limits.agcDropDb && m.dCn0 != null && m.dCn0 >= -1.0
        }
        if (s1) {
            if (s1Since == 0L) s1Since = epoch.elapsedMs
        } else {
            s1Since = 0L
        }
        val s1Hold = if (s1Since > 0L) epoch.elapsedMs - s1Since else 0L
        val s6 = config.correlation && correlated()
        val demote = interference != null ||
            (interferenceEndedElapsed > 0L && epoch.elapsedMs - interferenceEndedElapsed < DEMOTE_MS)
        val s2 = side.timeDisagree
        val s3 = side.positionDisagree
        val s4 = side.impossibleHop
        val sideCount = listOf(s2, s3, s4).count { it }
        // A clock, a position, or a hop is a witness. It does not open an episode alone.
        var level: GnssConfidence? = when {
            s1 && s1Hold >= S1_HIGH_MS && (s2 || s3) -> GnssConfidence.HIGH
            (s1 && s6) || sideCount >= 2 || (s1 && s1Hold >= S1_MED_MS) -> GnssConfidence.MEDIUM
            s1 || s6 -> GnssConfidence.LOW
            else -> null
        }
        val sideNote = demote && (s2 || s3 || s4)
        if (sideNote && level != null) level = level.lower()
        if (level == null) return endSpoof(epoch, spoofing)
        val notes = ArrayList<String>()
        if (bandDetail.isNotBlank()) notes += bandDetail
        if (s1) notes += "Gain is down and the signal is not."
        if (s2) notes += "GPS time and network time disagree."
        if (s3) notes += "GPS position and network position disagree."
        if (s4) notes += "The GPS position jumped faster than travel."
        if (s6) notes += "Satellite signals are fading together."
        if (sideNote) notes += "This may be a side effect of interference."
        val detail = notes.joinToString(" ")
        val why = GnssCopy.spoofWhy(
            gain = s1,
            clock = s2,
            position = s3,
            jumped = s4,
            faded = s6,
            fromInterference = sideNote,
        )
        return updateEpisode(spoofing, level, detail, epoch, adapted = false, why = why, numbers = bandNumbers)!!
    }

    private fun endSpoof(epoch: GnssEpoch, current: Episode?): Episode? {
        if (current == null) return null
        val age = epoch.elapsedMs - current.startedElapsed
        if (age < HOLD_MS) return if (current.rose) current.copy(rose = false) else current
        upsert(current.copy(endedWall = epoch.wallMs), "spoofing")
        return null
    }

    private fun mockEpisode(epoch: GnssEpoch, side: GnssSide): Episode? {
        if (!side.mock) {
            if (mockEp != null) upsert(mockEp!!.copy(endedWall = epoch.wallMs), "mock")
            return null
        }
        return updateEpisode(
            mockEp,
            GnssConfidence.LOW,
            GnssCopy.MOCK,
            epoch,
            adapted = false,
            why = GnssCopy.MOCK,
        )
    }

    private fun updateEpisode(
        current: Episode?,
        level: GnssConfidence?,
        detail: String,
        epoch: GnssEpoch,
        adapted: Boolean,
        why: String = "",
        numbers: String = "",
    ): Episode? {
        if (level == null) return current
        if (current == null) {
            return Episode(
                startedElapsed = epoch.elapsedMs,
                startedWall = epoch.wallMs,
                confidence = level,
                detail = detail,
                adapted = adapted,
                rose = true,
                why = why,
                numbers = numbers,
            )
        }
        val rose = level > current.confidence
        return current.copy(
            confidence = if (level > current.confidence) level else current.confidence,
            detail = detail.ifBlank { current.detail },
            adapted = current.adapted || adapted,
            rose = rose,
            why = why.ifBlank { current.why },
            numbers = numbers.ifBlank { current.numbers },
        )
    }

    private fun cn0Only(metrics: Map<BandKey, Metrics>, limits: GnssThresholds, armed: Boolean): Boolean {
        if (agcSeen || !armed) return false
        val rows = metrics.values.filter { it.dCn0 != null }
        if (rows.isEmpty()) return false
        if (rows.any { it.dCn0!! > -limits.cn0OnlyDropDb }) return false
        val sv = rows.sumOf { it.sv }
        val base = rows.mapNotNull { it.baseSv }.sum()
        return base > 0.0 && sv <= base * 0.5
    }

    private fun correlated(): Boolean {
        val full = svHist.filter { it.value.size >= CORR_WIN }
        if (full.size < 4) {
            corrStreak = 0
            return false
        }
        val series = full.values.map { diffs(it) }
        var sum = 0.0
        var n = 0
        for (i in series.indices) {
            for (j in i + 1 until series.size) {
                sum += pearson(series[i], series[j])
                n++
            }
        }
        val mean = if (n == 0) 0.0 else sum / n
        if (mean >= 0.5) corrStreak++ else corrStreak = 0
        return corrStreak >= 3
    }

    private fun pushSv(epoch: GnssEpoch) {
        val seen = HashSet<Int>()
        for ((key, band) in epoch.bands) {
            for ((svid, cn0) in band.cn0BySv) {
                if (cn0 <= 0.0 || !cn0.isFinite()) continue
                val id = key.constellation * 1000 + svid
                seen += id
                val q = svHist.getOrPut(id) { ArrayDeque() }
                q.addLast(cn0)
                while (q.size > CORR_WIN) q.removeFirst()
            }
        }
        svHist.keys.retainAll(seen)
    }

    private fun groups(): Map<BandKey, BandKey> {
        val keys = bands.keys.toList()
        val parent = keys.associateWith { it }.toMutableMap()
        fun find(k: BandKey): BandKey {
            var c = k
            while (parent[c] != c) c = parent[c] ?: c
            return c
        }
        for (i in keys.indices) {
            for (j in i + 1 until keys.size) {
                if (!shared(keys[i], keys[j])) continue
                parent[find(keys[j])] = find(keys[i])
            }
        }
        return keys.associateWith { find(it) }
    }

    private fun shared(a: BandKey, b: BandKey): Boolean {
        val left = bands[a]?.samples?.toList()?.takeLast(30) ?: return false
        val right = bands[b]?.samples?.toList()?.takeLast(30) ?: return false
        if (left.size < 30 || right.size < 30) return false
        for (i in left.indices) {
            val x = left[i].agc
            val y = right[i].agc
            if (x == null || y == null || abs(x - y) > 0.5) return false
        }
        return true
    }

    private fun freeze(metrics: Map<BandKey, Metrics>) {
        for ((key, track) in bands) {
            if (track.freezeSet) continue
            val m = metrics[key] ?: continue
            track.frozenAgc = m.baseAgc
            track.frozenCn0 = m.baseCn0
            track.frozenSv = m.baseSv
            track.freezeSet = true
        }
    }

    private fun clearFreeze() {
        for (track in bands.values) {
            track.freezeSet = false
            track.frozenAgc = null
            track.frozenCn0 = null
            track.frozenSv = null
        }
    }

    private fun bandBits(metrics: Map<BandKey, Metrics>): List<String> =
        metrics.entries
            .filter { it.value.dAgc != null || it.value.dCn0 != null }
            .sortedBy { it.value.dAgc ?: 0.0 }
            .take(3)
            .map { (key, m) -> bandText(key, m) }

    private fun bandLines(metrics: Map<BandKey, Metrics>): List<String> =
        metrics.entries.map { (key, m) ->
            val name = GnssBands.label(key)
            val agc = m.agc?.let { String.format(Locale.US, "%.1f dB", it) } ?: "n/a"
            val cn = m.cn0?.let { String.format(Locale.US, "%.1f", it) } ?: "n/a"
            "$name: AGC $agc, signal $cn, ${m.sv} satellites"
        }

    private fun bandText(key: BandKey, m: Metrics): String {
        val agc = m.dAgc?.let { String.format(Locale.US, "%+.1f dB", it) } ?: "n/a"
        val cn = m.dCn0?.let { String.format(Locale.US, "%+.1f dB", it) } ?: "n/a"
        val base = m.baseSv?.let { it.toInt().toString() } ?: "n/a"
        return "${GnssBands.label(key)}: AGC $agc, signal $cn, ${m.sv} satellites (baseline $base)"
    }

    private fun upsert(ep: Episode, kind: String) {
        val mark = GnssMark(
            at = ep.startedWall,
            endedAt = ep.endedWall ?: 0L,
            kind = kind,
            confidence = ep.confidence.label(),
            detail = ep.detail.take(400),
        )
        val index = marks.indexOfFirst { it.at == mark.at && it.kind == mark.kind }
        if (index < 0) marks += mark else marks[index] = mark
        while (marks.size > 40) marks.removeAt(0)
    }

    private fun cleanAgc(v: Double?): Double? {
        if (v == null || !v.isFinite() || v < -80.0) return null
        return v
    }

    private data class Sample(val agc: Double?, val cn0: Double?, val sv: Int, val eventAgc: Boolean)

    private data class Metrics(
        val dAgc: Double?,
        val dCn0: Double?,
        val baseAgc: Double?,
        val baseCn0: Double?,
        val baseSv: Double?,
        val agc: Double?,
        val cn0: Double?,
        val sv: Int,
        val eventAgc: Boolean,
    )

    private class Track {
        val samples = ArrayDeque<Sample>()
        val j1 = ArrayDeque<Boolean>()
        val j2 = ArrayDeque<Boolean>()
        var missing = 0
        var lastJ1 = Long.MIN_VALUE
        var freezeSet = false
        var frozenAgc: Double? = null
        var frozenCn0: Double? = null
        var frozenSv: Double? = null

        fun push(sample: Sample) {
            samples.addLast(sample)
            while (samples.size > ARM) samples.removeFirst()
        }

        fun flag(buf: ArrayDeque<Boolean>, now: Boolean): Boolean {
            buf.addLast(now)
            while (buf.size > 7) buf.removeFirst()
            return buf.count { it } >= 5
        }

        fun metrics(invert: Boolean): Metrics {
            val list = samples.toList()
            val last = list.lastOrNull()
            if (list.size < ARM || last == null) {
                return Metrics(null, null, null, null, null, last?.agc, last?.cn0, last?.sv ?: 0, last?.eventAgc == true)
            }
            val recent = list.takeLast(RECENT)
            val base = list.dropLast(RECENT)
            val baseAgc = frozenAgc ?: meanOf(base.map { it.agc })
            val baseCn0 = frozenCn0 ?: meanOf(base.map { it.cn0 })
            val baseSv = frozenSv ?: meanOf(base.map { it.sv.toDouble() })
            val recentAgc = meanOf(recent.map { it.agc })
            val recentCn0 = meanOf(recent.map { it.cn0 })
            val rawAgc = if (baseAgc != null && recentAgc != null) recentAgc - baseAgc else null
            val dAgc = if (invert && rawAgc != null) -rawAgc else rawAgc
            val dCn0 = if (baseCn0 != null && recentCn0 != null) recentCn0 - baseCn0 else null
            return Metrics(dAgc, dCn0, baseAgc, baseCn0, baseSv, last.agc, last.cn0, last.sv, last.eventAgc)
        }
    }

    private data class Episode(
        val startedElapsed: Long,
        val startedWall: Long,
        val confidence: GnssConfidence,
        val detail: String,
        val adapted: Boolean,
        val rose: Boolean,
        val endedWall: Long? = null,
        val why: String = "",
        val numbers: String = "",
    ) {
        fun toLive(kind: GnssKind) = GnssLive(
            kind = kind,
            confidence = confidence,
            startedWall = startedWall,
            detail = detail,
            adapted = adapted,
            rose = rose,
            why = why,
            numbers = numbers,
        )
    }

    private fun diffs(values: ArrayDeque<Double>): DoubleArray {
        val out = DoubleArray(values.size - 1)
        for (i in 1 until values.size) out[i - 1] = values.elementAt(i) - values.elementAt(i - 1)
        return out
    }

    private fun pearson(a: DoubleArray, b: DoubleArray): Double {
        val n = minOf(a.size, b.size)
        if (n < 4) return 0.0
        var ma = 0.0
        var mb = 0.0
        for (i in 0 until n) {
            ma += a[i]
            mb += b[i]
        }
        ma /= n
        mb /= n
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in 0 until n) {
            val xa = a[i] - ma
            val xb = b[i] - mb
            num += xa * xb
            da += xa * xa
            db += xb * xb
        }
        if (da <= 1e-9 || db <= 1e-9) return 0.0
        return num / sqrt(da * db)
    }

    private companion object {
        const val ARM = 60
        const val RECENT = 10
        const val HOLD_MS = 60_000L
        const val RECOVER = 20
        const val ADAPT_MS = 15 * 60_000L
        const val DEMOTE_MS = 120_000L
        const val S1_HIGH_MS = 10_000L
        const val S1_MED_MS = 30_000L
        const val CORR_WIN = 20
    }
}

private fun meanOf(values: Iterable<Double?>): Double? {
    var sum = 0.0
    var n = 0
    for (v in values) {
        if (v == null || !v.isFinite()) continue
        sum += v
        n++
    }
    return if (n == 0) null else sum / n
}
