package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** What the low-battery floor is doing on this pass. */
enum class BatteryFloorStep { NONE, SLOW, STOPPED }

/** Why a slow-down step lifted. A settings change does not count. */
enum class BatteryRestore { CHARGING, LEVEL }

/** One sticky battery reading. [percent] is -1 when Android did not say. */
data class BatterySample(val percent: Int, val plugged: Boolean)

data class BatteryFloorLive(
    val percent: Int = -1,
    val plugged: Boolean = false,
    val step: BatteryFloorStep = BatteryFloorStep.NONE,
)

data class BatteryFloorResult(
    val step: BatteryFloorStep,
    val effective: ScanIntensity,
    val restored: BatteryRestore? = null,
)

/**
 * One battery event on an open sit.
 * [kind] is SLOWED, RESTORED, or STOPPED.
 * [intensity] is the [ScanIntensity] name after a restore.
 */
@kotlinx.serialization.Serializable
data class BatteryMark(
    val at: Long,
    val kind: String,
    val percent: Int = -1,
    val plugged: Boolean = false,
    val intensity: String = "",
)

/**
 * Slow the scan, then stop it, when the phone battery is low.
 * The saved intensity does not change. A slow-down stays through
 * three points above its line and lifts at the fourth, or on charge.
 */
object BatteryFloor {
    val SLOW_CHOICES = intArrayOf(0, 10, 15, 20, 30)
    val STOP_CHOICES = intArrayOf(0, 3, 5, 10)

    /** Stay slowed through threshold + 3. Lift at threshold + 4. */
    const val LIFT_POINTS = 4

    fun choiceLabel(percent: Int): String =
        if (percent <= 0) LanguageText.text("ui.off.label") else "$percent%"

    fun nearest(percent: Int, choices: IntArray): Int {
        var best = choices[0]
        var gap = Int.MAX_VALUE
        for (choice in choices) {
            val next = kotlin.math.abs(choice - percent)
            if (next < gap) {
                gap = next
                best = choice
            }
        }
        return best
    }

    /**
     * [slowed] is the latch from the previous pass.
     * A null or unknown sample does not start or lift a step.
     */
    fun evaluate(settings: AppSettings, sample: BatterySample?, slowed: Boolean): BatteryFloorResult {
        val saved = settings.intensity
        if (!settings.batteryFloor) return BatteryFloorResult(BatteryFloorStep.NONE, saved)
        if (sample == null || sample.percent < 0) {
            return if (slowed) {
                BatteryFloorResult(BatteryFloorStep.SLOW, ScanIntensity.SAVER)
            } else {
                BatteryFloorResult(BatteryFloorStep.NONE, saved)
            }
        }
        if (sample.plugged) {
            return BatteryFloorResult(
                BatteryFloorStep.NONE,
                saved,
                restored = if (slowed) BatteryRestore.CHARGING else null,
            )
        }
        val stop = settings.batteryStopPct
        val slow = settings.batterySlowPct
        if (stop > 0 && sample.percent <= stop) {
            return BatteryFloorResult(BatteryFloorStep.STOPPED, saved)
        }
        val slowApplies = slow > 0 && (stop <= 0 || stop < slow)
        if (!slowApplies) return BatteryFloorResult(BatteryFloorStep.NONE, saved)
        val inSlow = if (slowed) sample.percent < slow + LIFT_POINTS else sample.percent <= slow
        return if (inSlow) {
            BatteryFloorResult(BatteryFloorStep.SLOW, ScanIntensity.SAVER)
        } else {
            BatteryFloorResult(
                BatteryFloorStep.NONE,
                saved,
                restored = if (slowed) BatteryRestore.LEVEL else null,
            )
        }
    }

    fun diagValue(live: BatteryFloorLive): String {
        val level = if (live.percent < 0) "—" else "${live.percent}%"
        val charge = LanguageText.text(
            if (live.plugged) "ui.battery.charging" else "ui.battery.not.charging",
        )
        val step = LanguageText.text(
            when (live.step) {
                BatteryFloorStep.SLOW -> "ui.battery.step.slow"
                BatteryFloorStep.STOPPED -> "ui.battery.step.stopped"
                BatteryFloorStep.NONE -> "ui.battery.step.none"
            },
        )
        return LanguageText.text(
            "ui.battery.row",
            mapOf("n" to level, "charge" to charge, "step" to step),
        )
    }

    fun debriefLine(mark: BatteryMark): String {
        val clock = clockUtc(mark.at)
        val n = if (mark.percent < 0) "—" else mark.percent.toString()
        val intensity = intensityLabel(mark.intensity)
        return when (mark.kind) {
            "SLOWED" -> LanguageText.text(
                "ui.battery.debrief.slowed",
                mapOf("clock" to clock, "n" to n),
            )
            "RESTORED" -> if (mark.plugged) {
                LanguageText.text(
                    "ui.battery.debrief.restored.charge",
                    mapOf("clock" to clock, "intensity" to intensity),
                )
            } else {
                LanguageText.text(
                    "ui.battery.debrief.restored.level",
                    mapOf("clock" to clock, "n" to n, "intensity" to intensity),
                )
            }
            else -> LanguageText.text(
                "ui.battery.debrief.stopped",
                mapOf("clock" to clock, "n" to n),
            )
        }
    }

    fun intensityLabel(name: String): String = when (name) {
        ScanIntensity.SAVER.name -> LanguageText.text("ui.battery.saver")
        ScanIntensity.BALANCED.name -> LanguageText.text("ui.balanced.label")
        ScanIntensity.PERFORMANCE.name -> LanguageText.text("ui.high.performance")
        else -> name
    }

    private fun clockUtc(ms: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }
}
