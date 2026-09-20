package app.fieldwatch.domain

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

object Sit {
    const val NAME_MAX = 40
    const val RADIO_CAP = 3000
    const val PATH_CAP = 2000
    const val CLOSED_CAP = 10
    const val TRAIL_CAP = 40
    const val PATH_MIN_M = 10.0
    const val PATH_MIN_MS = 5_000L
    const val FLUSH_MS = 10_000L
    const val FORMAT = "fieldwatch-sit"
    const val FORMAT_VERSION = 1

    fun clipName(raw: String): String =
        raw.trim().replace('\n', ' ').replace('\r', ' ').take(NAME_MAX)

    fun defaultName(at: Long, locale: Locale = Locale.US): String =
        SimpleDateFormat("d MMM HH:mm", locale).format(Date(at))

    fun resolveName(raw: String, at: Long, locale: Locale = Locale.US): String =
        clipName(raw).ifBlank { defaultName(at, locale) }

    fun fmtDuration(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        val h = s / 3600L
        val m = (s % 3600L) / 60L
        return when {
            h > 0L -> "$h h $m min"
            m > 0L -> "$m min"
            else -> "$s s"
        }
    }

    fun newId(): String = UUID.randomUUID().toString()

    /** Closed list is newest-first. Null unless ending another sit would drop the oldest. */
    fun dropWarning(closed: List<SitSummary>): String? {
        if (closed.size < CLOSED_CAP) return null
        val oldest = closed.lastOrNull() ?: return null
        return "You already have $CLOSED_CAP saved sits. When you end this one, the oldest (“${oldest.name}”) will be deleted."
    }

    fun pinned(
        key: String,
        fleetIds: Set<String>,
        payload: Boolean,
        extraAttention: Boolean,
        watchDeviceKeys: Set<String>,
        watchedFleetIds: Set<String>,
    ): Boolean {
        if (extraAttention) return true
        if (payload) return true
        if (key in watchDeviceKeys) return true
        if (fleetIds.any { it in watchedFleetIds }) return true
        return false
    }
}

@Serializable
data class SitSummary(
    val id: String,
    val name: String,
    val startAt: Long,
    val endAt: Long? = null,
    val radioCount: Int = 0,
    val extraAttentionCount: Int = 0,
) {
    val open: Boolean get() = endAt == null

    fun durationMs(now: Long = System.currentTimeMillis()): Long =
        ((endAt ?: now) - startAt).coerceAtLeast(0L)
}

@Serializable
data class SitRadio(
    val key: String,
    val kind: RadioKind,
    val mac: String,
    val name: String,
    val fleetIds: Set<String> = emptySet(),
    val firstSeen: Long,
    val lastSeen: Long,
    val hitCount: Int,
    val rssi: Int,
    val rssiMin: Int,
    val rssiMax: Int,
    val channel: Int = 0,
    val frequencyMhz: Int = 0,
    val randomized: Boolean = false,
    val hiddenSsid: Boolean = false,
    val gone: Boolean = false,
    val extraAttention: Boolean = false,
    val payloadLat: Double? = null,
    val payloadLon: Double? = null,
    val payloadUasId: String? = null,
    val gpsTrail: List<GpsSample> = emptyList(),
) {
    fun toSighting(): Sighting = Sighting(
        key = key,
        kind = kind,
        mac = mac,
        name = name,
        rssi = rssi,
        rssiMin = rssiMin,
        rssiMax = rssiMax,
        channel = channel,
        frequencyMhz = frequencyMhz,
        vendor = null,
        randomized = randomized,
        hiddenSsid = hiddenSsid,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        hitCount = hitCount,
        fleetIds = fleetIds,
        rssiHistory = emptyList(),
        presence = listOf(PresenceSpan(firstSeen, if (gone) lastSeen else null)),
        gone = gone,
        gpsTrail = gpsTrail,
        payloadLat = payloadLat,
        payloadLon = payloadLon,
        payloadUasId = payloadUasId,
    )

    companion object {
        fun from(device: Sighting, fleets: List<Fleet>): SitRadio = SitRadio(
            key = device.key,
            kind = device.kind,
            mac = device.mac,
            name = device.name,
            fleetIds = device.fleetIds,
            firstSeen = device.firstSeen,
            lastSeen = device.lastSeen,
            hitCount = device.hitCount,
            rssi = device.rssi,
            rssiMin = device.rssiMin,
            rssiMax = device.rssiMax,
            channel = device.channel,
            frequencyMhz = device.frequencyMhz,
            randomized = device.randomized,
            hiddenSsid = device.hiddenSsid,
            gone = device.gone,
            extraAttention = device.attentionNotes(fleets).isNotEmpty(),
            payloadLat = device.payloadLat,
            payloadLon = device.payloadLon,
            payloadUasId = device.payloadUasId,
            gpsTrail = trailSample(device),
        )

        fun trailSample(device: Sighting): List<GpsSample> {
            val fromDevice = device.gpsTrail.takeLast(Sit.TRAIL_CAP)
            val pin = if (device.latitude != null && device.longitude != null) {
                GpsSample(device.lastSeen, device.latitude, device.longitude, device.rssi)
            } else {
                null
            }
            if (pin == null) return fromDevice
            if (fromDevice.any { it.at == pin.at }) return fromDevice
            return (fromDevice + pin).takeLast(Sit.TRAIL_CAP)
        }
    }
}

@Serializable
data class SitFile(
    val format: String = Sit.FORMAT,
    val formatVersion: Int = Sit.FORMAT_VERSION,
    val summary: SitSummary,
    val radios: List<SitRadio> = emptyList(),
    val operatorPath: List<GpsSample> = emptyList(),
)

data class SitUi(
    val open: SitSummary? = null,
    val radioCount: Int = 0,
    val atCap: Boolean = false,
    val memoryTight: Boolean = false,
    val closed: List<SitSummary> = emptyList(),
    val selectedId: String? = null,
    val notice: String? = null,
)

data class SitDebrief(
    val name: String,
    val startAt: Long,
    val endAt: Long,
    val devices: List<Sighting>,
    val operatorPath: List<GpsSample>,
)

data class DebriefWindow(
    val startAt: Long,
    val endAt: Long,
    val sitName: String? = null,
) {
    val durationMs: Long get() = (endAt - startAt).coerceAtLeast(1L)
}

class SitSession(
    summary: SitSummary,
    radios: List<SitRadio> = emptyList(),
    path: List<GpsSample> = emptyList(),
) {
    var summary: SitSummary = summary
        private set
    private val radios = LinkedHashMap<String, SitRadio>(radios.size + 16).apply {
        radios.forEach { put(it.key, it) }
    }
    private val path = ArrayList<GpsSample>(path.size + 16).apply { addAll(path) }
    var dirty: Boolean = false
        private set

    val open: Boolean get() = summary.open
    val radioCount: Int get() = radios.size
    val atCap: Boolean get() = radios.size >= Sit.RADIO_CAP
    val operatorPath: List<GpsSample> get() = path.toList()

    fun markClean() {
        dirty = false
    }

    fun snapshot(fleets: List<Fleet> = emptyList()): SitFile {
        val extra = radios.values.count { it.extraAttention }
        val sum = summary.copy(
            radioCount = radios.size,
            extraAttentionCount = extra,
        )
        summary = sum
        return SitFile(summary = sum, radios = radios.values.toList(), operatorPath = path.toList())
    }

    fun sightings(): List<Sighting> = radios.values.map { it.toSighting() }

    fun rename(raw: String): Boolean {
        if (!open) return false
        val next = Sit.clipName(raw)
        if (next.isEmpty() || next == summary.name) return false
        summary = summary.copy(name = next)
        dirty = true
        return true
    }

    fun end(now: Long, fleets: List<Fleet>): SitFile {
        summary = summary.copy(endAt = now)
        dirty = true
        return snapshot(fleets)
    }

    fun ingest(
        device: Sighting,
        fleets: List<Fleet>,
        watchDeviceKeys: Set<String>,
        watchedFleetIds: Set<String>,
        tight: Boolean = false,
    ): Boolean {
        if (!open) return false
        val existing = radios[device.key]
        if (existing != null) {
            radios[device.key] = merge(existing, device, fleets)
            dirty = true
            return true
        }
        val pin = pinned(device, fleets, watchDeviceKeys, watchedFleetIds)
        val full = radios.size >= Sit.RADIO_CAP || tight
        if (full && !pin) {
            evict(fleets, watchDeviceKeys, watchedFleetIds)
            if (radios.size >= Sit.RADIO_CAP || tight) return false
        }
        radios[device.key] = SitRadio.from(device, fleets)
        dirty = true
        evict(fleets, watchDeviceKeys, watchedFleetIds)
        return true
    }

    fun seedHeard(
        heard: List<Sighting>,
        fleets: List<Fleet>,
        watchDeviceKeys: Set<String>,
        watchedFleetIds: Set<String>,
    ) {
        for (device in heard) {
            if (device.gone) continue
            ingest(device, fleets, watchDeviceKeys, watchedFleetIds)
        }
    }

    fun recordPath(lat: Double, lon: Double, at: Long): Boolean {
        if (!open) return false
        val last = path.lastOrNull()
        if (last != null) {
            val d = Geo.meters(last.lat, last.lon, lat, lon)
            val dt = at - last.at
            if (d < Sit.PATH_MIN_M && dt < Sit.PATH_MIN_MS) {
                path[path.lastIndex] = GpsSample(at, lat, lon)
                dirty = true
                return true
            }
        }
        path += GpsSample(at, lat, lon)
        if (path.size > Sit.PATH_CAP) {
            val drop = path.size - Sit.PATH_CAP
            repeat(drop) { path.removeAt(0) }
        }
        dirty = true
        return true
    }

    private fun pinned(
        device: Sighting,
        fleets: List<Fleet>,
        watchDeviceKeys: Set<String>,
        watchedFleetIds: Set<String>,
    ): Boolean = Sit.pinned(
        key = device.key,
        fleetIds = device.fleetIds,
        payload = device.payloadLat != null && device.payloadLon != null,
        extraAttention = device.attentionNotes(fleets).isNotEmpty(),
        watchDeviceKeys = watchDeviceKeys,
        watchedFleetIds = watchedFleetIds,
    )

    private fun pinnedRadio(
        row: SitRadio,
        watchDeviceKeys: Set<String>,
        watchedFleetIds: Set<String>,
    ): Boolean = Sit.pinned(
        key = row.key,
        fleetIds = row.fleetIds,
        payload = row.payloadLat != null && row.payloadLon != null,
        extraAttention = row.extraAttention,
        watchDeviceKeys = watchDeviceKeys,
        watchedFleetIds = watchedFleetIds,
    )

    private fun evict(
        fleets: List<Fleet>,
        watchDeviceKeys: Set<String>,
        watchedFleetIds: Set<String>,
    ) {
        if (radios.size <= Sit.RADIO_CAP) return
        fun drop(predicate: (SitRadio) -> Boolean) {
            if (radios.size <= Sit.RADIO_CAP) return
            val victims = radios.values.filter(predicate).sortedBy { it.lastSeen }
            val need = radios.size - Sit.RADIO_CAP
            victims.take(need).forEach { radios.remove(it.key) }
        }
        drop { row ->
            !pinnedRadio(row, watchDeviceKeys, watchedFleetIds) &&
                row.kind == RadioKind.BLE &&
                row.fleetIds.isEmpty()
        }
        drop { row -> !pinnedRadio(row, watchDeviceKeys, watchedFleetIds) }
    }

    private fun merge(old: SitRadio, next: Sighting, fleets: List<Fleet>): SitRadio {
        val extra = old.extraAttention || next.attentionNotes(fleets).isNotEmpty()
        val trail = (old.gpsTrail + SitRadio.trailSample(next))
            .distinctBy { it.at }
            .sortedBy { it.at }
            .takeLast(Sit.TRAIL_CAP)
        return old.copy(
            name = next.name.ifBlank { old.name },
            fleetIds = old.fleetIds + next.fleetIds,
            firstSeen = min(old.firstSeen, next.firstSeen),
            lastSeen = max(old.lastSeen, next.lastSeen),
            hitCount = max(old.hitCount, next.hitCount),
            rssi = next.rssi,
            rssiMin = min(old.rssiMin, next.rssi),
            rssiMax = max(old.rssiMax, next.rssi),
            channel = if (next.channel > 0) next.channel else old.channel,
            frequencyMhz = if (next.frequencyMhz > 0) next.frequencyMhz else old.frequencyMhz,
            randomized = old.randomized || next.randomized,
            hiddenSsid = old.hiddenSsid || next.hiddenSsid,
            gone = next.gone,
            extraAttention = extra,
            payloadLat = next.payloadLat ?: old.payloadLat,
            payloadLon = next.payloadLon ?: old.payloadLon,
            payloadUasId = next.payloadUasId ?: old.payloadUasId,
            gpsTrail = trail,
        )
    }

    companion object {
        fun start(
            name: String,
            now: Long,
            heard: List<Sighting>,
            fleets: List<Fleet>,
            watchDeviceKeys: Set<String>,
            watchedFleetIds: Set<String>,
            id: String = Sit.newId(),
        ): SitSession {
            val session = SitSession(
                SitSummary(id = id, name = Sit.resolveName(name, now), startAt = now),
            )
            session.seedHeard(heard, fleets, watchDeviceKeys, watchedFleetIds)
            session.dirty = true
            return session
        }
    }
}
