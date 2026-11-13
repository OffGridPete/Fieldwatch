package app.fieldwatch.data

import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.PlaceMemory
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.Sit
import app.fieldwatch.domain.TrackerWatch
import app.fieldwatch.domain.WatchTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.ZoneId

/**
 * Local place file. One lookup per eligible hear, flushed on the sit cadence.
 * Coordinates stay in this file. Settings export does not include it.
 */
class PlaceStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val lock = Any()
    private val records = HashMap<String, PlaceMemory.Record>()
    private var trackerSeen = emptySet<String>()
    private var trackerSeenKnown = false
    private var dirty = false
    private var generation = 0
    private var cacheMinute = Long.MIN_VALUE
    private var cacheGen = -1
    private var cacheMine = 0
    private var cacheKeys: Set<String> = emptySet()

    fun load() {
        val text = runCatching { file.readText() }.getOrNull() ?: return
        val parsed = runCatching { json.decodeFromString<PlaceFile>(text) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            records.clear()
            for (record in parsed.records) {
                val kept = PlaceMemory.prune(record, now) ?: continue
                records[kept.id] = kept
            }
            trimLocked(now)
            if (parsed.trackerSeenKnown) {
                trackerSeen = parsed.trackerSeen
                trackerSeenKnown = true
            } else {
                trackerSeen = records.values.mapNotNull { record ->
                    if (!record.id.startsWith("ble:")) return@mapNotNull null
                    if (PlaceMemory.placeCount(record, now) < PlaceMemory.MIN_PLACES) return@mapNotNull null
                    record.id
                }.toSet()
                trackerSeenKnown = true
                dirty = true
            }
            generation++
        }
    }

    fun startFlusher(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                delay(Sit.FLUSH_MS)
                runCatching { flush() }
            }
        }
    }

    fun hear(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        sitId: String?,
        sitLabel: String?,
    ) {
        if (devices.isEmpty()) return
        val names = HashMap<String, String>(fleets.size)
        for (fleet in fleets) names[fleet.id] = fleet.name
        val custom = RadioBookmarks.labels(watchlist)
        val mine = RadioBookmarks.mineKeys(watchlist)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        synchronized(lock) {
            var changed = false
            for (device in devices) {
                val id = PlaceMemory.accept(device, names, mine) ?: continue
                val at = if (device.lastSeen > 0L) device.lastSeen else now
                val day = PlaceMemory.localDate(at, zone).toEpochDay()
                val sigNames = device.fleetIds.mapNotNull { names[it] }
                val prev = records[id]
                val next = PlaceMemory.prune(
                    PlaceMemory.note(
                        prev,
                        PlaceMemory.Hear(
                            id = id,
                            deviceKey = device.key,
                            at = at,
                            lat = device.latitude,
                            lon = device.longitude,
                            sitId = sitId,
                            sitLabel = sitLabel,
                            localDay = day,
                            label = PlaceMemory.heardLabel(device, sigNames, custom[device.key]),
                        ),
                    ),
                    now,
                )
                if (next == null) {
                    if (prev != null) {
                        records.remove(id)
                        changed = true
                    }
                    continue
                }
                if (next != prev) {
                    records[next.id] = next
                    changed = true
                }
            }
            if (changed) {
                trimLocked(now)
                dirty = true
                generation++
            }
        }
    }

    fun linesFor(
        device: Sighting,
        fleets: List<Fleet>,
        mineKeys: Set<String>,
        now: Long,
        demo: Boolean = false,
        streets: Map<String, String> = emptyMap(),
    ): List<String> {
        val record = recordFor(device, fleets, mineKeys) ?: return emptyList()
        return PlaceMemory.detailLines(record, now, ZoneId.systemDefault(), demo, streets)
    }

    /** Confirmed places in the last 7 days, when this radio has at least two. */
    fun placePoints(
        device: Sighting,
        fleets: List<Fleet>,
        mineKeys: Set<String>,
        now: Long,
    ): List<Pair<Double, Double>> {
        val record = recordFor(device, fleets, mineKeys) ?: return emptyList()
        val places = PlaceMemory.placesInWindow(record, now)
        if (places.size < PlaceMemory.MIN_PLACES) return emptyList()
        return places.map { it.lat to it.lon }
    }

    private fun recordFor(device: Sighting, fleets: List<Fleet>, mineKeys: Set<String>): PlaceMemory.Record? {
        if (device.key in mineKeys) return null
        val names = HashMap<String, String>(fleets.size)
        for (fleet in fleets) names[fleet.id] = fleet.name
        val id = PlaceMemory.accept(device, names, mineKeys)
        synchronized(lock) {
            if (id != null) records[id]?.let { return it }
            return records.values.firstOrNull { it.deviceKey == device.key }
        }
    }

    fun multiPlaceKeys(now: Long, mineKeys: Set<String>): Set<String> {
        val minute = now / 60_000L
        val mineStamp = mineKeys.hashCode()
        synchronized(lock) {
            if (cacheGen == generation && cacheMinute == minute && cacheMine == mineStamp) {
                return cacheKeys
            }
            val keys = HashSet<String>()
            for (record in records.values) {
                if (record.deviceKey in mineKeys) continue
                if (PlaceMemory.placeCount(record, now) >= PlaceMemory.MIN_PLACES) {
                    keys += record.deviceKey
                }
            }
            cacheGen = generation
            cacheMinute = minute
            cacheMine = mineStamp
            cacheKeys = keys
            return keys
        }
    }

    fun snapshot(): List<PlaceMemory.Record> = synchronized(lock) { records.values.toList() }

    /**
     * Finders newly at two stops. While [armed] is false, radios already at two
     * stops are remembered and do not warn later just because the switch was turned on.
     */
    fun trackerPlaces(armed: Boolean, now: Long, devices: List<Sighting>): List<TrackerWatch.Notice> {
        synchronized(lock) {
            val byKey = HashMap<String, Sighting>(devices.size)
            for (device in devices) byKey[device.key] = device
            val rows = ArrayList<TrackerWatch.PlaceRow>(records.size)
            for (record in records.values) {
                if (!record.id.startsWith("ble:")) continue
                val device = byKey[record.deviceKey]
                rows += TrackerWatch.PlaceRow(
                    id = record.id,
                    deviceKey = record.deviceKey,
                    name = record.label,
                    mac = record.id.removePrefix("ble:"),
                    rssi = device?.rssi ?: Int.MIN_VALUE,
                    count = PlaceMemory.placeCount(record, now),
                    stops = PlaceMemory.placesInWindow(record, now).map {
                        TrackerWatch.Spot(it.lat, it.lon)
                    },
                )
            }
            val result = TrackerWatch.places(trackerSeen, rows, armed)
            if (!trackerSeenKnown || result.seen != trackerSeen) {
                trackerSeen = result.seen
                trackerSeenKnown = true
                dirty = true
            }
            return result.fresh
        }
    }

    fun flush() {
        val copy = synchronized(lock) {
            if (!dirty) return
            dirty = false
            Triple(records.values.toList(), trackerSeen, trackerSeenKnown)
        }
        try {
            val text = json.encodeToString(
                PlaceFile(
                    records = copy.first,
                    trackerSeen = copy.second,
                    trackerSeenKnown = copy.third,
                ),
            )
            val dir = file.parentFile
            if (dir != null && !dir.isDirectory) dir.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (err: Exception) {
            synchronized(lock) { dirty = true }
            throw err
        }
    }

    private fun trimLocked(now: Long) {
        if (records.size <= PlaceMemory.MAX_RECORDS) return
        val drop = records.values.sortedBy { it.lastHeardAt }
            .take(records.size - PlaceMemory.MAX_RECORDS)
        for (record in drop) {
            if (now - record.lastHeardAt >= 0L) records.remove(record.id)
        }
    }

    @Serializable
    private data class PlaceFile(
        val format: String = PlaceMemory.FORMAT,
        val records: List<PlaceMemory.Record> = emptyList(),
        val trackerSeen: Set<String> = emptySet(),
        val trackerSeenKnown: Boolean = false,
    )
}
