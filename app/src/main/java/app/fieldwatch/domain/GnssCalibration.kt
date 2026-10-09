package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

/**
 * One quiet minute of this phone's own GPS, saved on the phone until the next run.
 * The interference check stays off until a run is saved. Export settings does not include it.
 */
@Serializable
enum class GnssCalGrade {
    GOOD, FAIR, POOR;

    fun label(): String = when (this) {
        GOOD -> "Good"
        FAIR -> "Fair"
        POOR -> "Poor"
    }
}

@Serializable
data class GnssPhoneProfile(
    val at: Long,
    val grade: GnssCalGrade,
    val cn0Mean: Double,
    val cn0WanderDb: Double,
    val agcWanderDb: Double,
    val agcStepDb: Double,
    val svMean: Double,
    val gapFraction: Double,
    val agcSeen: Boolean,
    /** Drop the check must clear, under the user's Low / Medium / High. 0 keeps today's bars. */
    val cn0FloorDb: Double = 0.0,
    val agcFloorDb: Double = 0.0,
    /** Brief measurement gaps are normal here. They do not raise a Medium alert. */
    val ignoreStopped: Boolean = false,
    /** Popup, red line, beep, and voice stay off. The fix for a path still works. */
    val suppressAlerts: Boolean = false,
) {
    fun summary(): String = when (grade) {
        GnssCalGrade.GOOD ->
            "Calibration complete. This phone is Good. " +
                "It should work with a steady view of the sky, away from other electronics."
        GnssCalGrade.FAIR ->
            "Calibration complete. This phone is Fair. Usable. " +
                "Small drops will be hard to separate from this phone's own GPS. " +
                "Medium or Low will be steadier than High."
        GnssCalGrade.POOR ->
            "Calibration complete. This phone is Poor. " +
                "This phone's GPS is too coarse for an interference alert. " +
                "It can still tag a path. The popup stays off."
    }

    fun calibratedLabel(): String {
        val fmt = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
        return fmt.format(Date(at))
    }
}

/** One GPS fix during a calibration minute. Coordinates stay in this attempt and are not saved. */
data class GnssCalFix(
    val elapsedMs: Long,
    val accM: Float,
    val speedMps: Float,
    val lat: Double,
    val lon: Double,
)

data class GnssCalAttempt(
    val epochs: List<GnssEpoch>,
    val fixes: List<GnssCalFix>,
    val startedElapsedMs: Long,
    val endedElapsedMs: Long,
)

sealed class GnssCalOutcome {
    data class Saved(val profile: GnssPhoneProfile) : GnssCalOutcome()
    data class Rejected(val reason: String) : GnssCalOutcome()
}

/** What Settings shows while a calibration minute is running or after it is refused. */
data class GnssCalUi(
    val running: Boolean = false,
    val leftSec: Int = 0,
    val rejection: String = "",
)

object GnssCalibration {
    const val DURATION_MS = 60_000L

    fun grade(attempt: GnssCalAttempt, nowWall: Long): GnssCalOutcome {
        val window = attempt.endedElapsedMs - attempt.startedElapsedMs
        val epochs = attempt.epochs.sortedBy { it.elapsedMs }
        if (window < MIN_WINDOW_MS || epochs.size < MIN_EPOCHS) {
            return reject(FEW_READINGS)
        }
        val span = epochs.last().elapsedMs - epochs.first().elapsedMs
        if (span < MIN_SPAN_MS) return reject(FEW_READINGS)

        val goodFixes = attempt.fixes.filter { it.accM in 0f..MAX_ACC_M && it.lat.isFinite() && it.lon.isFinite() }
        if (goodFixes.size < MIN_FIXES) return reject(NO_FIX)
        val first = goodFixes.first()
        val last = goodFixes.last()
        val moved = Geo.meters(first.lat, first.lon, last.lat, last.lon)
        val fast = goodFixes.count { it.speedMps >= WALK_MPS }
        if (moved > MAX_MOVE_M || fast >= FAST_FIXES) return reject(MOVING)

        val cn0 = epochs.mapNotNull { epochCn0(it) }
        val agc = epochs.mapNotNull { epochAgc(it) }
        val sv = epochs.map { epochSv(it).toDouble() }
        if (cn0.size < MIN_EPOCHS / 2) return reject(WEAK)
        val cn0Mean = cn0.average()
        val svMean = sv.average()
        if (svMean < MIN_SV) return reject(FEW_SV)
        if (cn0Mean < MIN_CN0) return reject(WEAK)

        val cn0Wander = stdev(cn0)
        val agcWander = stdev(agc)
        val step = agcStep(agc)
        val gaps = gapFraction(epochs.map { it.elapsedMs })
        val agcSeen = agc.isNotEmpty()
        val weakPhone = cn0Mean < GOOD_CN0 || svMean < GOOD_SV
        val noisy = cn0Wander >= NOISY_DB || agcWander >= NOISY_DB || step >= COARSE_STEP_DB
        val veryNoisy = cn0Wander >= WILD_DB || agcWander >= WILD_DB || step >= WILD_STEP_DB || gaps >= WILD_GAPS
        val gappy = gaps >= FAIR_GAPS
        val grade = when {
            veryNoisy || (weakPhone && noisy) -> GnssCalGrade.POOR
            agcSeen && !weakPhone && !gappy &&
                cn0Wander <= QUIET_DB && agcWander <= QUIET_DB && step <= QUIET_STEP_DB &&
                cn0Mean >= GOOD_CN0 && svMean >= GOOD_SV -> GnssCalGrade.GOOD
            else -> GnssCalGrade.FAIR
        }
        val cn0Floor = if (grade == GnssCalGrade.GOOD) 0.0 else (cn0Wander * FLOOR_SCALE).coerceIn(0.0, FLOOR_CAP)
        val agcExtra = max(agcWander * FLOOR_SCALE, if (step >= QUIET_STEP_DB) step + 0.5 else 0.0)
        val agcFloor = if (grade == GnssCalGrade.GOOD) 0.0 else agcExtra.coerceIn(0.0, FLOOR_CAP)
        return GnssCalOutcome.Saved(
            GnssPhoneProfile(
                at = nowWall,
                grade = grade,
                cn0Mean = round1(cn0Mean),
                cn0WanderDb = round1(cn0Wander),
                agcWanderDb = round1(agcWander),
                agcStepDb = round1(step),
                svMean = round1(svMean),
                gapFraction = round1(gaps),
                agcSeen = agcSeen,
                cn0FloorDb = round1(cn0Floor),
                agcFloorDb = round1(agcFloor),
                ignoreStopped = grade != GnssCalGrade.GOOD,
                suppressAlerts = grade == GnssCalGrade.POOR,
            ),
        )
    }

    fun diagRows(profile: GnssPhoneProfile?): List<Pair<String, String>> {
        if (profile == null) return listOf("GNSS calibration" to "not run")
        val gain = if (profile.agcSeen) "${num(profile.agcWanderDb)} dB" else "not reported"
        return listOf(
            "GNSS calibration" to profile.grade.label(),
            "Calibrated" to profile.calibratedLabel(),
            "GPS signal at calibration" to "${num(profile.cn0Mean)} dB-Hz, wander ${num(profile.cn0WanderDb)} dB",
            "Gain wander" to gain,
        )
    }

    private fun reject(reason: String) = GnssCalOutcome.Rejected(reason)

    private fun epochCn0(epoch: GnssEpoch): Double? =
        epoch.bands.values.mapNotNull { band ->
            band.cn0Top3?.takeIf { it.isFinite() && it > 0.0 }
        }.maxOrNull()

    private fun epochAgc(epoch: GnssEpoch): Double? {
        val vals = epoch.bands.values.mapNotNull { band ->
            band.agcDb?.takeIf { it.isFinite() && it >= -80.0 }
        }
        if (vals.isEmpty()) return null
        return vals.sorted()[vals.size / 2]
    }

    private fun epochSv(epoch: GnssEpoch): Int =
        epoch.bands.values.sumOf { it.svTracked.coerceAtLeast(0) }

    private fun stdev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        var acc = 0.0
        for (v in values) {
            val d = v - mean
            acc += d * d
        }
        return sqrt(acc / (values.size - 1))
    }

    /** Typical jump between distinct gain readings. 0 when gain sits on one value. */
    private fun agcStep(values: List<Double>): Double {
        val uniq = values.map { (it * 2.0).roundToInt() / 2.0 }.distinct().sorted()
        if (uniq.size < 2) return 0.0
        val diffs = uniq.zipWithNext { a, b -> b - a }.filter { it > 0.05 }.sorted()
        if (diffs.isEmpty()) return 0.0
        return diffs[diffs.size / 2]
    }

    private fun gapFraction(times: List<Long>): Double {
        if (times.size < 3) return 1.0
        val dts = times.zipWithNext { a, b -> b - a }.filter { it > 0 }
        if (dts.isEmpty()) return 1.0
        val med = dts.sorted()[dts.size / 2].coerceAtLeast(1L)
        val limit = max(med * 5 / 2, 2_500L)
        return dts.count { it > limit }.toDouble() / dts.size
    }

    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0

    private fun num(v: Double): String = String.format(Locale.US, "%.1f", v)

    private const val MIN_WINDOW_MS = 50_000L
    private const val MIN_SPAN_MS = 45_000L
    private const val MIN_EPOCHS = 20
    private const val MIN_FIXES = 8
    private const val MAX_ACC_M = 25f
    private const val MAX_MOVE_M = 40.0
    private const val WALK_MPS = 2.5f
    private const val FAST_FIXES = 4
    private const val MIN_SV = 8.0
    private const val MIN_CN0 = 18.0
    private const val GOOD_CN0 = 32.0
    private const val GOOD_SV = 18.0
    private const val QUIET_DB = 1.2
    private const val QUIET_STEP_DB = 1.5
    private const val NOISY_DB = 2.0
    private const val COARSE_STEP_DB = 3.0
    private const val WILD_DB = 3.5
    private const val WILD_STEP_DB = 6.0
    private const val FAIR_GAPS = 0.08
    private const val WILD_GAPS = 0.20
    private const val FLOOR_SCALE = 2.5
    private const val FLOOR_CAP = 8.0

    const val FEW_READINGS = "Not enough GPS readings. Stay outside, hold still, and try again."
    const val NO_FIX = "No GPS fix. Stand outside, hold still, and try again."
    const val MOVING = "The phone was moving. Stand still and try again."
    const val FEW_SV = "Too few satellites. Stand in the open, away from a roof and a router, and try again."
    const val WEAK = "The GPS signal was too weak for a calibration. Stand in the open and try again."
}
