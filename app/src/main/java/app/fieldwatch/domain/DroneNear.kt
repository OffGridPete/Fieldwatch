package app.fieldwatch.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** This phone's latest GPS fix, with the accuracy Android reported. Network points stay out. */
data class GpsAccFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val at: Long,
)

/** One broadcast-within-range line on a sit. [settingM] is 0 on a closest line. */
@Serializable
data class DroneNearMark(
    val at: Long,
    val name: String,
    val settingM: Int,
    val measuredM: Int,
    val closest: Boolean = false,
) {
    fun reportLine(): String = if (closest) {
        LanguageText.text(
            "ui.near.closest",
            mapOf(
                "clock" to FloodBurst.clock(at),
                "name" to name,
                "d" to Geo.distanceText(measuredM.toDouble()),
            ),
        )
    } else {
        LanguageText.text(
            "ui.near.sit",
            mapOf(
                "clock" to FloodBurst.clock(at),
                "name" to name,
                "distance" to DroneNear.settingLabel(settingM),
                "d" to Geo.distanceText(measuredM.toDouble()),
            ),
        )
    }
}

/** One drone that just broadcast inside the chosen distance. */
data class DroneNearFire(
    val uasId: String,
    val title: String,
    val body: String,
    val noteId: Int,
)

data class DroneNearOutcome(
    val fires: List<DroneNearFire> = emptyList(),
    val marks: List<DroneNearMark> = emptyList(),
)

/**
 * Warn when a Remote ID drone broadcasts inside a chosen horizontal distance.
 * The latch only decides whether it may fire again. The live chip follows the
 * latest fresh position. A blank UAS ID does not count.
 */
class DroneNear {
    private val lock = Any()
    private val saved = ArrayList<DroneNearMark>()
    private val latches = HashMap<String, Latch>()
    /** UAS IDs whose first fresh position this visit was past the setting. */
    private val outside = HashSet<String>()
    private val _chips = MutableStateFlow<Map<String, String>>(emptyMap())
    val chips: StateFlow<Map<String, String>> = _chips.asStateFlow()

    fun pass(
        devices: List<Sighting>,
        names: Map<String, String>,
        mineKeys: Set<String>,
        now: Long,
        distanceM: Int,
        phone: GpsAccFix?,
    ): DroneNearOutcome = synchronized(lock) {
        if (distanceM !in CHOICES || distanceM == 0) {
            latches.clear()
            outside.clear()
            _chips.value = emptyMap()
            return DroneNearOutcome()
        }
        val phoneOk = measurable(phone, now)
        val present = LinkedHashSet<String>()
        val fresh = HashMap<String, Sample>()
        for (device in devices) {
            if (device.gone || device.key in mineKeys) continue
            if (REMOTE_ID_FLEET !in device.fleetIds) continue
            val id = device.payloadUasId?.trim().orEmpty()
            if (id.isEmpty()) continue
            present.add(id)
            val at = device.payloadAt ?: continue
            if (now - at > FRESH_MS || at > now + 5_000L) continue
            if (!PayloadLocation.validCoord(device.payloadLat, device.payloadLon)) continue
            val prev = fresh[id]
            if (prev == null || at >= prev.at) {
                fresh[id] = Sample(
                    at = at,
                    lat = device.payloadLat!!,
                    lon = device.payloadLon!!,
                    name = nameOf(device, names),
                )
            }
        }
        val marks = ArrayList<DroneNearMark>()
        val fires = ArrayList<DroneNearFire>()
        val gone = latches.keys.filter { it !in present }
        for (id in gone) {
            val latch = latches.remove(id) ?: continue
            outside.remove(id)
            if (!latch.closestWritten) marks += closestMark(latch)
        }
        outside.retainAll(present)
        val chips = HashMap<String, String>()
        val label = settingLabel(distanceM)
        for (id in present) {
            val sample = fresh[id] ?: continue
            if (!phoneOk || phone == null) continue
            val meters = Geo.meters(phone.lat, phone.lon, sample.lat, sample.lon)
            if (meters <= distanceM) {
                chips[id] = LanguageText.text("ui.near.chip", mapOf("distance" to label))
            }
            val latch = latches[id]
            if (latch == null) {
                if (meters <= distanceM) {
                    latches[id] = Latch(distanceM, sample.name, meters, sample.at, closestWritten = false)
                    outside.remove(id)
                    fires += fire(id, sample.name, label, meters)
                    marks += DroneNearMark(
                        at = now,
                        name = sample.name,
                        settingM = distanceM,
                        measuredM = meters.roundToInt().coerceAtLeast(0),
                        closest = false,
                    )
                } else {
                    outside.add(id)
                }
            } else if (meters > distanceM * REARM) {
                if (!latch.closestWritten) marks += closestMark(latch)
                latches.remove(id)
                outside.add(id)
            } else if (meters < latch.closestM) {
                latch.closestM = meters
                latch.closestAt = sample.at
            }
        }
        _chips.value = chips
        keep(marks)
        DroneNearOutcome(fires, marks)
    }

    fun marks(): List<DroneNearMark> = synchronized(lock) { saved.toList() }

    /**
     * True while the stock Remote ID bookmark should stay quiet for this sighting.
     * A named radio is not this function's job. The caller leaves those alone.
     */
    fun holdsBookmark(device: Sighting, mineKeys: Set<String>, now: Long, distanceM: Int): Boolean =
        synchronized(lock) {
            if (distanceM == 0 || device.key in mineKeys) return false
            if (REMOTE_ID_FLEET !in device.fleetIds) return false
            val id = device.payloadUasId?.trim().orEmpty()
            if (id.isNotEmpty() && id in latches) return true
            if (id.isNotEmpty() && id in outside) return false
            val start = device.presence.lastOrNull()?.start ?: device.firstSeen
            if (start <= 0L && now > HOLD_MS) return false
            now - start < HOLD_MS
        }

    /** Closest line for a firing that is still open when the sit ends. The latch stays. */
    fun closeOpen(now: Long): List<DroneNearMark> = synchronized(lock) {
        val marks = ArrayList<DroneNearMark>()
        for (latch in latches.values) {
            if (latch.closestWritten) continue
            marks += closestMark(latch, at = if (latch.closestAt > 0L) latch.closestAt else now)
            latch.closestWritten = true
        }
        keep(marks)
        marks
    }

    private fun keep(incoming: List<DroneNearMark>) {
        if (incoming.isEmpty()) return
        saved.addAll(incoming)
        while (saved.size > 40) saved.removeAt(0)
    }

    private fun fire(id: String, name: String, label: String, meters: Double): DroneNearFire {
        val shown = Geo.distanceText(meters)
        return DroneNearFire(
            uasId = id,
            title = LanguageText.text("ui.near.title", mapOf("distance" to label)),
            body = LanguageText.text("ui.near.body", mapOf("name" to name, "d" to shown)),
            noteId = 0x4E00_0000 or (id.hashCode() and 0x00FF_FFFF),
        )
    }

    private fun closestMark(latch: Latch, at: Long = latch.closestAt): DroneNearMark {
        latch.closestWritten = true
        return DroneNearMark(
            at = at,
            name = latch.name,
            settingM = 0,
            measuredM = latch.closestM.roundToInt().coerceAtLeast(0),
            closest = true,
        )
    }

    private class Latch(
        val settingM: Int,
        val name: String,
        var closestM: Double,
        var closestAt: Long,
        var closestWritten: Boolean,
    )

    private data class Sample(
        val at: Long,
        val lat: Double,
        val lon: Double,
        val name: String,
    )

    companion object {
        const val REMOTE_ID_FLEET = "fleet-remote-id"
        const val FRESH_MS = 10_000L
        const val HOLD_MS = 10_000L
        const val PHONE_MAX_AGE_MS = 30_000L
        const val PHONE_ACC_M = 50f
        private const val REARM = 1.5
        val DISTANCES = setOf(100, 250, 500, 1000)
        const val DEFAULT_M = 250
        val CHOICES = DISTANCES + 0

        fun clamp(settings: AppSettings): AppSettings {
            val meters = if (settings.droneNearM in DISTANCES) settings.droneNearM else 0
            val pick = when {
                meters != 0 -> meters
                settings.droneNearPick in DISTANCES -> settings.droneNearPick
                else -> DEFAULT_M
            }
            if (settings.droneNearM == meters && settings.droneNearPick == pick) return settings
            return settings.copy(droneNearM = meters, droneNearPick = pick)
        }

        /** Switch. Turning it on uses the distance chip that was left under it. */
        fun armed(settings: AppSettings, on: Boolean): AppSettings {
            val clamped = clamp(settings)
            return if (on) clamped.copy(droneNearM = clamped.droneNearPick)
            else clamped.copy(droneNearM = 0)
        }

        /** Distance chip. A choice while the switch is off is kept for the next time it is on. */
        fun pick(settings: AppSettings, meters: Int): AppSettings {
            if (meters !in DISTANCES) return clamp(settings)
            val clamped = clamp(settings)
            return if (clamped.droneNearM == 0) clamped.copy(droneNearPick = meters)
            else clamped.copy(droneNearM = meters, droneNearPick = meters)
        }

        fun settingLabel(meters: Int): String = if (meters >= 1000) {
            LanguageText.text("ui.near.km", mapOf("n" to (meters / 1000).toString()))
        } else {
            LanguageText.text("ui.near.m", mapOf("n" to meters.toString()))
        }

        /** Waiting, too wide, or nothing when the fix is good enough or the setting is off. */
        fun diag(distanceM: Int, fix: GpsAccFix?, now: Long): String? {
            if (distanceM == 0) return null
            if (fix == null || !fix.accuracyM.isFinite()) return LanguageText.text("ui.near.wait")
            val age = now - fix.at
            if (age > PHONE_MAX_AGE_MS || age < -5_000L) return LanguageText.text("ui.near.wait")
            if (fix.accuracyM > PHONE_ACC_M) {
                val shown = fix.accuracyM.roundToInt().coerceAtLeast(51)
                return LanguageText.text("ui.near.wide", mapOf("acc" to shown.toString()))
            }
            return null
        }

        private fun measurable(phone: GpsAccFix?, now: Long): Boolean {
            if (phone == null || !phone.accuracyM.isFinite() || phone.accuracyM > PHONE_ACC_M) return false
            val age = now - phone.at
            return age <= PHONE_MAX_AGE_MS && age >= -5_000L
        }

        private fun nameOf(device: Sighting, names: Map<String, String>): String {
            val aircraft = device.payloadAircraft?.trim().orEmpty()
            if (aircraft.isNotEmpty()) return aircraft
            val hint = device.fleetIds.asSequence()
                .filter { it != REMOTE_ID_FLEET }
                .sorted()
                .mapNotNull { names[it]?.trim()?.takeIf { name -> name.isNotEmpty() } }
                .firstOrNull()
            if (hint != null) return hint
            return names[REMOTE_ID_FLEET]?.trim().orEmpty().ifEmpty { "Remote ID" }
        }
    }
}
