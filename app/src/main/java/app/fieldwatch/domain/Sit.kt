package app.fieldwatch.domain

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

object Sit {
    const val NAME_MAX = 40
    const val RADIO_CAP = 6000
    const val PATH_CAP = 16_000
    const val CLOSED_CAP = 10
    const val TRAIL_CAP = 40
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
            h > 0L -> LanguageText.text("sit.hm", mapOf("h" to "$h", "m" to "$m"))
            m > 0L -> LanguageText.text("sit.m", mapOf("m" to "$m"))
            else -> LanguageText.text("sit.s", mapOf("s" to "$s"))
        }
    }

    fun newId(): String = UUID.randomUUID().toString()

    /** Closed list is newest-first. Null unless ending another sit would drop the oldest. */
    fun dropWarning(closed: List<SitSummary>): String? {
        if (closed.size < CLOSED_CAP) return null
        val oldest = closed.lastOrNull() ?: return null
        return LanguageText.text(
            "sit.drop",
            mapOf("n" to CLOSED_CAP.toString(), "name" to oldest.name),
        )
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
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadAircraft: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadAlt: Double? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadHeading: Double? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadSpeed: Double? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadOpLat: Double? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadOpLon: Double? = null,
    /** 0 takeoff, 1 live GPS, 2 fixed. Null on a sit saved before the type was stored. */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadOpKind: Int? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadOpAlt: Double? = null,
    /** ASTM Auth kept for a saved radio page. Null on a sit saved before this field. */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadAuth: RemoteIdAuth? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadTrail: List<PayloadFix> = emptyList(),
    val gpsTrail: List<GpsSample> = emptyList(),
    /** Live-row labels captured at hear-time. Saved sits do not keep the advertisement bytes. */
    val liveDecode: List<LiveDecodeChip> = emptyList(),
    /** Separated SmartTag privacy ID. Kept only as long as this sit keeps the radio. Not a device key. */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val smartTagId: String? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadAt: Long? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadPhoneLat: Double? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadPhoneLon: Double? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadOpAt: Long? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadOpPhoneLat: Double? = null,
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payloadOpPhoneLon: Double? = null,
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
        payloadAircraft = payloadAircraft,
        payloadAlt = payloadAlt,
        payloadHeading = payloadHeading,
        payloadSpeed = payloadSpeed,
        payloadOpLat = payloadOpLat,
        payloadOpLon = payloadOpLon,
        payloadOpKind = payloadOpKind,
        payloadOpAlt = payloadOpAlt,
        payloadAuth = payloadAuth,
        payloadTrail = payloadTrail,
        liveDecode = liveDecode,
        smartTagId = smartTagId,
        payloadAt = payloadAt,
        payloadPhoneLat = payloadPhoneLat,
        payloadPhoneLon = payloadPhoneLon,
        payloadOpAt = payloadOpAt,
        payloadOpPhoneLat = payloadOpPhoneLat,
        payloadOpPhoneLon = payloadOpPhoneLon,
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
            payloadAircraft = device.payloadAircraft,
            payloadAlt = device.payloadAlt,
            payloadHeading = device.payloadHeading,
            payloadSpeed = device.payloadSpeed,
            payloadOpLat = device.payloadOpLat,
            payloadOpLon = device.payloadOpLon,
            payloadOpKind = device.payloadOpKind,
            payloadOpAlt = device.payloadOpAlt,
            payloadAuth = device.payloadAuth,
            payloadTrail = AircraftTrail.append(emptyList(), device),
            gpsTrail = trailSample(device),
            liveDecode = device.liveDecode,
            smartTagId = SmartTag.separatedId(device),
            payloadAt = device.payloadAt,
            payloadPhoneLat = device.payloadPhoneLat,
            payloadPhoneLon = device.payloadPhoneLon,
            payloadOpAt = device.payloadOpAt,
            payloadOpPhoneLat = device.payloadOpPhoneLat,
            payloadOpPhoneLon = device.payloadOpPhoneLon,
        )

        fun trailSample(device: Sighting): List<GpsSample> {
            val fromDevice = Geo.capSpread(device.gpsTrail, Sit.TRAIL_CAP)
            val pin = if (device.latitude != null && device.longitude != null) {
                GpsSample(device.lastSeen, device.latitude, device.longitude, device.rssi)
            } else {
                null
            }
            if (pin == null) return fromDevice
            if (fromDevice.any { it.at == pin.at }) return fromDevice
            return Geo.capSpread(fromDevice + pin, Sit.TRAIL_CAP)
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
    val floods: List<FloodBurst> = emptyList(),
    val gnss: List<GnssMark> = emptyList(),
    val cells: List<CellMark> = emptyList(),
    /**
     * Cameras the path came near. Empty when none did, or when this file
     * was saved before that list was kept. [cameraKept] says which.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val pathCameras: List<FlockCamera> = emptyList(),
    /**
     * Last frozen step whose cameras are in [pathCameras].
     * Null means this sit never saved that list, so the report counts once from the files.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val cameraKept: GpsSample? = null,
)

data class SitUi(
    val open: SitSummary? = null,
    val radioCount: Int = 0,
    val atCap: Boolean = false,
    val memoryTight: Boolean = false,
    val closed: List<SitSummary> = emptyList(),
    val selectedId: String? = null,
    val compareId: String? = null,
    val notice: String? = null,
)

/** Open sit, for the live map. Radios are already limited to watchlist alerts heard since [startAt]. */
data class OpenLivePath(
    val startAt: Long,
    val samples: List<GpsSample>,
    val radios: List<Sighting>,
    /** Cameras this sit already saved along the path. Empty until the first fold. */
    val cameras: List<FlockCamera> = emptyList(),
)

data class SitDebrief(
    val name: String,
    val startAt: Long,
    val endAt: Long,
    val devices: List<Sighting>,
    val operatorPath: List<GpsSample>,
    val floods: List<FloodBurst> = emptyList(),
    val gnss: List<GnssMark> = emptyList(),
    val cells: List<CellMark> = emptyList(),
    /**
     * Cameras saved on this sit. Null when the sit has not saved that list yet,
     * including a sit written before it was kept. Empty means the path was checked
     * and no listed camera was along it.
     */
    val pathCameras: List<FlockCamera>? = null,
)

/** Path tail still to fold, and the cameras already frozen on that sit. */
data class CameraSlice(
    val anchor: GpsSample?,
    val points: List<GpsSample>,
    val committed: List<FlockCamera>,
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
    floods: List<FloodBurst> = emptyList(),
    gnss: List<GnssMark> = emptyList(),
    cells: List<CellMark> = emptyList(),
    pathCameras: List<FlockCamera> = emptyList(),
    cameraKept: GpsSample? = null,
) {
    var summary: SitSummary = summary
        private set
    private val radios = LinkedHashMap<String, SitRadio>(radios.size + 16).apply {
        radios.forEach { put(it.key, it) }
    }
    private val path = ArrayList<GpsSample>(path.size + 16).apply { addAll(path) }
    private val pathApex = Geo.DriveApex()
    /** Path length already checked for [onePlaceLabel]. A drive does not measure every pair again. */
    private var placeChecked = 0
    private var placeWide = false
    /** Cameras on frozen steps. The report also shows whatever is beside the current tip. */
    private val cameraCommitted = LinkedHashMap<Long, FlockCamera>()
    private var cameraShow: List<FlockCamera> = emptyList()
    private var cameraKept: GpsSample? = cameraKept
    private val floods = ArrayList<FloodBurst>(floods.size + 4).apply { addAll(floods) }
    private val gnss = ArrayList<GnssMark>(gnss.size + 4).apply { addAll(gnss) }
    private val cells = ArrayList<CellMark>(cells.size + 4).apply { addAll(cells) }
    var dirty: Boolean = false
        private set
    private var pathSnap: List<GpsSample> = emptyList()
    private var pathSnapN = -1
    private var pathSnapHeadAt = Long.MIN_VALUE
    private var pathSnapHeadLat = Double.NaN
    private var pathSnapHeadLon = Double.NaN
    private var pathSnapTipAt = Long.MIN_VALUE
    private var pathSnapTipLat = Double.NaN
    private var pathSnapTipLon = Double.NaN
    /** Bumps when an alert dot would move or appear. A louder hear does not. */
    private var alertGen = 0
    private var alertSnap: List<Sighting> = emptyList()
    private var alertSnapGen = -1
    private var alertSnapSince = Long.MIN_VALUE
    private var alertSnapKeys: Set<String> = emptySet()
    private var alertSnapFleets: Set<String> = emptySet()
    private var alertSnapHidden: Set<String> = emptySet()

    val open: Boolean get() = summary.open
    val radioCount: Int get() = radios.size
    val atCap: Boolean get() = radios.size >= Sit.RADIO_CAP
    val operatorPath: List<GpsSample>
        get() {
            val n = path.size
            val head = if (n > 0) path[0] else null
            val tip = if (n > 0) path[n - 1] else null
            if (n == pathSnapN &&
                samePathEnd(head, pathSnapHeadAt, pathSnapHeadLat, pathSnapHeadLon) &&
                samePathEnd(tip, pathSnapTipAt, pathSnapTipLat, pathSnapTipLon)
            ) {
                return pathSnap
            }
            val copy = path.toList()
            pathSnap = copy
            pathSnapN = n
            pathSnapHeadAt = head?.at ?: Long.MIN_VALUE
            pathSnapHeadLat = head?.lat ?: Double.NaN
            pathSnapHeadLon = head?.lon ?: Double.NaN
            pathSnapTipAt = tip?.at ?: Long.MIN_VALUE
            pathSnapTipLat = tip?.lat ?: Double.NaN
            pathSnapTipLon = tip?.lon ?: Double.NaN
            return copy
        }
    val pathCount: Int get() = path.size

    init {
        // The file stores the list the report was showing, tip included.
        // After a restart those tip cameras stay on the sit.
        if (cameraKept != null) {
            for (cam in pathCameras) {
                if (cam.kind == SensorKind.GUNSHOT) continue
                cameraCommitted[cam.key] = cam
            }
            cameraShow = cameraCommitted.values.sortedBy { it.key }
        }
    }

    /**
     * Sit name while the path is still one place, about [meters] across.
     * Null once some pair is at least that far. Adding points does not measure the drive again.
     */
    fun onePlaceLabel(meters: Double): String? {
        val name = summary.name.trim().ifBlank { null }
        if (placeWide) return null
        val count = path.size
        if (count < 2) return name
        if (count == placeChecked) return name
        placeWide = if (placeChecked == 0 || count < placeChecked) {
            Geo.widerThan(path, meters)
        } else {
            Geo.widerThanNew(path, placeChecked, meters)
        }
        placeChecked = count
        return if (placeWide) null else name
    }

    fun markClean() {
        dirty = false
    }

    /** Watchlist alerts whose last hear is at or after [since]. Hidden keys stay off. */
    fun alertsSince(
        since: Long,
        alertKeys: Set<String>,
        watchedFleetIds: Set<String>,
        hiddenKeys: Set<String>,
    ): List<Sighting> {
        if (alertGen == alertSnapGen &&
            since == alertSnapSince &&
            alertKeys == alertSnapKeys &&
            watchedFleetIds == alertSnapFleets &&
            hiddenKeys == alertSnapHidden
        ) {
            return alertSnap
        }
        val out = ArrayList<Sighting>()
        for (radio in radios.values) {
            if (radio.key in hiddenKeys) continue
            if (radio.lastSeen < since) continue
            if (radio.key !in alertKeys && radio.fleetIds.none { it in watchedFleetIds }) continue
            out += radio.toSighting()
        }
        alertSnap = out
        alertSnapGen = alertGen
        alertSnapSince = since
        alertSnapKeys = alertKeys
        alertSnapFleets = watchedFleetIds
        alertSnapHidden = hiddenKeys
        return out
    }

    fun snapshot(fleets: List<Fleet> = emptyList()): SitFile {
        val extra = radios.values.count { it.extraAttention }
        val sum = summary.copy(
            radioCount = radios.size,
            extraAttentionCount = extra,
        )
        summary = sum
        return SitFile(
            summary = sum,
            radios = radios.values.toList(),
            operatorPath = path.toList(),
            floods = floods.toList(),
            gnss = gnss.toList(),
            cells = cells.toList(),
            pathCameras = if (cameraKept != null) cameraShow else emptyList(),
            cameraKept = cameraKept,
        )
    }

    /** The part of the path whose cameras are not frozen yet. Null when there is no path. */
    fun cameraSlice(): CameraSlice? {
        if (path.isEmpty()) return null
        val committed = cameraCommitted.values.toList()
        val anchor = cameraKept
        if (anchor == null) return CameraSlice(null, path.toList(), committed)
        val idx = path.indexOfLast { it.at == anchor.at && it.lat == anchor.lat && it.lon == anchor.lon }
        if (idx < 0) {
            val head = path.first()
            return CameraSlice(head, path.toList(), committed)
        }
        return CameraSlice(anchor, ArrayList(path.subList(idx, path.size)), committed)
    }

    /** Cameras the report is showing for this path. Empty until a fold has run. */
    fun camerasAlongPath(): List<FlockCamera> = cameraShow

    /** Store a fold. The report reads [PathCameras.Fold.cameras] and does not search again. */
    fun applyCameraFold(fold: PathCameras.Fold) {
        val nextShow = fold.cameras
        val changed = cameraKept != fold.kept || cameraShow != nextShow
        cameraKept = fold.kept
        cameraCommitted.clear()
        for (cam in fold.committed) cameraCommitted[cam.key] = cam
        cameraShow = nextShow
        if (changed) dirty = true
    }

    /** Copy cell episodes that overlap this sit. The same start and kind update in place. */
    fun noteCell(incoming: List<CellMark>) {
        if (!open || incoming.isEmpty()) return
        val start = summary.startAt
        var changed = false
        for (mark in incoming) {
            if (mark.endedAt > 0L && mark.endedAt < start) continue
            val index = cells.indexOfFirst { it.at == mark.at && it.kind == mark.kind }
            if (index < 0) {
                cells += mark
                changed = true
            } else if (cells[index] != mark) {
                cells[index] = mark
                changed = true
            }
        }
        if (cells.size > 40) {
            val drop = cells.size - 40
            repeat(drop) { cells.removeAt(0) }
            changed = true
        }
        if (changed) dirty = true
    }

    /** Copy GNSS hits that started during this sit. The same start time updates in place. */
    fun noteGnss(incoming: List<GnssMark>) {
        if (!open || incoming.isEmpty()) return
        val start = summary.startAt
        var changed = false
        for (mark in incoming) {
            if (mark.at < start && (mark.endedAt <= 0L || mark.endedAt < start)) continue
            val index = gnss.indexOfFirst { it.at == mark.at && it.kind == mark.kind }
            if (index < 0) {
                gnss += mark
                changed = true
            } else if (gnss[index] != mark) {
                gnss[index] = mark
                changed = true
            }
        }
        if (gnss.size > 40) {
            val drop = gnss.size - 40
            repeat(drop) { gnss.removeAt(0) }
            changed = true
        }
        if (changed) dirty = true
    }

    /** Copy bursts that started during this sit. The same start time updates in place. */
    fun noteFloods(incoming: List<FloodBurst>) {
        if (!open || incoming.isEmpty()) return
        val start = summary.startAt
        var changed = false
        for (burst in incoming) {
            if (burst.at < start) continue
            val index = floods.indexOfFirst { it.at == burst.at && it.wifi == burst.wifi }
            if (index < 0) {
                floods += burst
                changed = true
            } else if (floods[index] != burst) {
                floods[index] = burst
                changed = true
            }
        }
        if (floods.size > 40) {
            val drop = floods.size - 40
            repeat(drop) { floods.removeAt(0) }
            changed = true
        }
        if (changed) dirty = true
    }

    fun radio(key: String): SitRadio? = radios[key]

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
            val merged = merge(existing, device, fleets)
            radios[device.key] = merged
            if (alertPlaceMoved(existing, merged)) alertGen++
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
        alertGen++
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
        if (last != null && !Geo.hopPlausible(last, lat, lon, at)) return false
        val trimmed = Geo.extendDrivePath(path, pathApex, lat, lon, at, cap = Sit.PATH_CAP)
        if (trimmed) {
            placeChecked = 0
            placeWide = false
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
        val before = radios.size
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
        if (radios.size != before) alertGen++
    }

    /** True when this hear would move a dot, add one, or drop one. Strength does not. */
    private fun alertPlaceMoved(old: SitRadio, merged: SitRadio): Boolean {
        val since = summary.startAt
        if ((old.lastSeen >= since) != (merged.lastSeen >= since)) return true
        if (old.fleetIds != merged.fleetIds) return true
        if (old.extraAttention != merged.extraAttention) return true
        if (old.payloadLat != merged.payloadLat || old.payloadLon != merged.payloadLon) return true
        val a = old.gpsTrail.lastOrNull()
        val b = merged.gpsTrail.lastOrNull()
        if (a == null || b == null) return a != null || b != null
        return a.lat != b.lat || a.lon != b.lon
    }

    private fun samePathEnd(sample: GpsSample?, at: Long, lat: Double, lon: Double): Boolean {
        if (sample == null) return at == Long.MIN_VALUE
        return sample.at == at && sample.lat == lat && sample.lon == lon
    }

    private fun merge(old: SitRadio, next: Sighting, fleets: List<Fleet>): SitRadio {
        val extra = old.extraAttention || next.attentionNotes(fleets).isNotEmpty()
        val trail = Geo.capSpread(
            (old.gpsTrail + SitRadio.trailSample(next))
                .distinctBy { it.at }
                .sortedBy { it.at },
            Sit.TRAIL_CAP,
        )
        return old.copy(
            name = next.name.ifBlank { old.name },
            fleetIds = old.fleetIds + next.fleetIds,
            firstSeen = min(old.firstSeen, next.firstSeen),
            lastSeen = max(old.lastSeen, next.lastSeen),
            hitCount = max(old.hitCount, next.hitCount),
            rssi = if (Rssi.measured(next.rssi)) next.rssi else old.rssi,
            rssiMin = if (Rssi.measured(next.rssi)) {
                if (Rssi.measured(old.rssiMin)) min(old.rssiMin, next.rssi) else next.rssi
            } else old.rssiMin,
            rssiMax = if (Rssi.measured(next.rssi)) {
                if (Rssi.measured(old.rssiMax)) max(old.rssiMax, next.rssi) else next.rssi
            } else old.rssiMax,
            channel = if (next.channel > 0) next.channel else old.channel,
            frequencyMhz = if (next.frequencyMhz > 0) next.frequencyMhz else old.frequencyMhz,
            randomized = old.randomized || next.randomized,
            hiddenSsid = old.hiddenSsid || next.hiddenSsid,
            gone = next.gone,
            extraAttention = extra,
            payloadLat = next.payloadLat ?: old.payloadLat,
            payloadLon = next.payloadLon ?: old.payloadLon,
            payloadUasId = next.payloadUasId ?: old.payloadUasId,
            payloadAircraft = if (next.payloadUasId != null && next.payloadUasId != old.payloadUasId) {
                next.payloadAircraft
            } else {
                next.payloadAircraft ?: old.payloadAircraft
            },
            payloadAlt = next.payloadAlt ?: old.payloadAlt,
            payloadHeading = next.payloadHeading ?: old.payloadHeading,
            payloadSpeed = next.payloadSpeed ?: old.payloadSpeed,
            payloadOpLat = next.payloadOpLat ?: old.payloadOpLat,
            payloadOpLon = next.payloadOpLon ?: old.payloadOpLon,
            payloadOpKind = next.payloadOpKind ?: old.payloadOpKind,
            payloadOpAlt = next.payloadOpAlt ?: old.payloadOpAlt,
            payloadAuth = next.payloadAuth ?: old.payloadAuth,
            payloadTrail = AircraftTrail.append(old.payloadTrail, next),
            gpsTrail = trail,
            liveDecode = next.liveDecode.ifEmpty { old.liveDecode },
            smartTagId = SmartTag.separatedId(next),
            payloadAt = next.payloadAt ?: old.payloadAt,
            payloadPhoneLat = if (next.payloadAt != null) next.payloadPhoneLat else old.payloadPhoneLat,
            payloadPhoneLon = if (next.payloadAt != null) next.payloadPhoneLon else old.payloadPhoneLon,
            payloadOpAt = next.payloadOpAt ?: old.payloadOpAt,
            payloadOpPhoneLat = if (next.payloadOpAt != null) next.payloadOpPhoneLat else old.payloadOpPhoneLat,
            payloadOpPhoneLon = if (next.payloadOpAt != null) next.payloadOpPhoneLon else old.payloadOpPhoneLon,
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
