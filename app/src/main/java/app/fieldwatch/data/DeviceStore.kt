package app.fieldwatch.data

import app.fieldwatch.domain.Observation
import app.fieldwatch.domain.OuiLookup
import app.fieldwatch.domain.PresenceSpan
import app.fieldwatch.domain.Rotation
import app.fieldwatch.domain.RssiSample
import app.fieldwatch.domain.ScanStats
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureEngine
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.DetectionPolicy
import app.fieldwatch.domain.FastPair
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.PayloadLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

class DeviceStore(
    private val engine: SignatureEngine = SignatureEngine(),
) {
    private val live = ConcurrentHashMap<String, Sighting>()
    private val _devices = MutableStateFlow<List<Sighting>>(emptyList())
    val devices: StateFlow<List<Sighting>> = _devices.asStateFlow()

    private val _stats = MutableStateFlow(ScanStats())
    val stats: StateFlow<ScanStats> = _stats.asStateFlow()

    private val historyLimit = 40
    private val evictAfterMs = 15 * 60 * 1000L
    private val evictAnonMs = 3 * 60 * 1000L
    /** Steady-state list size. Fresh radios inside brief hold/stale may exceed this. */
    private val maxLive = 400
    /** Absolute ceiling so a plaza of rotating BLE MACs cannot grow without bound. */
    private val hardLive = 900
    @Volatile private var lastWifiBatchAt = 0L
    @Volatile private var wifiHold = false
    @Volatile private var bleHold = false
    private val lock = Any()
    /** Identity fingerprint → skip signature search while name / IEs / UUIDs / mfg stay the same. */
    private val matchStamp = HashMap<String, String>(512)
    private var catalogRef: List<Fleet>? = null

    fun keyOf(kind: app.fieldwatch.domain.RadioKind, mac: String) =
        "${kind.name}:${MacUtil.normalize(mac)}"

    fun ingest(observation: Observation, fleets: List<Fleet>, staleSec: Int): Sighting =
        ingestBatch(listOf(observation), fleets, staleSec).first()

    fun ingestBatch(
        observations: List<Observation>,
        fleets: List<Fleet>,
        staleSec: Int,
    ): List<Sighting> = synchronized(lock) {
        if (observations.isEmpty()) return emptyList()
        val out = ArrayList<Sighting>(observations.size)
        for (observation in observations) {
            out += upsert(observation)
        }
        relabel(out, fleets)
        for (i in out.indices) {
            out[i] = live[out[i].key] ?: out[i]
        }
        out
    }

    private fun upsert(observation: Observation): Sighting {
        val mac = MacUtil.normalize(observation.mac)
        val key = keyOf(observation.kind, mac)
        val now = if (observation.at > 0L) observation.at else System.currentTimeMillis()
        val existing = live[key]
        if (observation.kind == app.fieldwatch.domain.RadioKind.WIFI && !observation.fresh && existing != null) {
            val next = existing.copy(
                name = observation.name.ifBlank { existing.name },
                rssi = observation.rssi,
                vendorIeOuis = mergeIes(existing.vendorIeOuis, observation.vendorIeOuis),
                facts = existing.facts.merge(observation.facts),
                fastPairPairing = existing.fastPairPairing || FastPair.pairingAdvertised(observation.facts),
            )
            live[key] = next
            return next
        }
        val sample = RssiSample(now, observation.rssi)
        val merged = if (existing == null) {
            Sighting(
                key = key,
                kind = observation.kind,
                mac = mac,
                name = observation.name,
                rssi = observation.rssi,
                rssiMin = observation.rssi,
                rssiMax = observation.rssi,
                channel = observation.channel,
                frequencyMhz = observation.frequencyMhz,
                vendor = OuiLookup.vendor(mac)
                    ?: observation.manufacturerId?.let { app.fieldwatch.domain.RadioDb.company(it) },
                randomized = MacUtil.isRandomized(mac),
                hiddenSsid = observation.hiddenSsid,
                serviceUuids = observation.serviceUuids,
                manufacturerId = observation.manufacturerId,
                manufacturerDataHex = observation.manufacturerDataHex.take(512),
                rawHex = observation.rawHex.take(1024),
                extras = observation.extras.take(160),
                firstSeen = now,
                lastSeen = now,
                hitCount = 1,
                fleetIds = emptySet(),
                rssiHistory = listOf(sample),
                presence = listOf(PresenceSpan(now, null)),
                latitude = observation.latitude,
                longitude = observation.longitude,
                vendorIeOuis = observation.vendorIeOuis,
                facts = observation.facts,
                gpsTrail = gpsStart(observation),
                fastPairPairing = FastPair.pairingAdvertised(observation.facts),
            )
        } else {
            val history = if (existing.rssiHistory.size >= historyLimit) {
                existing.rssiHistory.drop(existing.rssiHistory.size - historyLimit + 1) + sample
            } else {
                existing.rssiHistory + sample
            }
            val last = existing.presence.lastOrNull()
            val presence = if (last == null || last.end != null) {
                existing.presence + PresenceSpan(now, null)
            } else {
                existing.presence
            }
            val uuids = if (observation.serviceUuids.isEmpty()) {
                existing.serviceUuids
            } else {
                (existing.serviceUuids + observation.serviceUuids).distinct()
            }
            existing.copy(
                name = observation.name.ifBlank { existing.name },
                rssi = observation.rssi,
                vendor = existing.vendor
                    ?: OuiLookup.vendor(mac)
                    ?: observation.manufacturerId?.let { app.fieldwatch.domain.RadioDb.company(it) },
                rssiMin = minOf(existing.rssiMin, observation.rssi),
                rssiMax = maxOf(existing.rssiMax, observation.rssi),
                channel = if (observation.channel != 0) observation.channel else existing.channel,
                frequencyMhz = if (observation.frequencyMhz != 0) observation.frequencyMhz else existing.frequencyMhz,
                hiddenSsid = existing.hiddenSsid || observation.hiddenSsid,
                serviceUuids = uuids,
                manufacturerId = existing.manufacturerId ?: observation.manufacturerId,
                manufacturerDataHex = mergeMfgHex(
                    existing.manufacturerDataHex,
                    observation.manufacturerDataHex,
                ),
                rawHex = when {
                    observation.rawHex.length >= existing.rawHex.length -> observation.rawHex.take(1024)
                    else -> existing.rawHex
                },
                lastSeen = now,
                hitCount = existing.hitCount + 1,
                rssiHistory = history,
                presence = presence,
                latitude = observation.latitude ?: existing.latitude,
                longitude = observation.longitude ?: existing.longitude,
                gpsTrail = gpsAppend(existing.gpsTrail, observation),
                gone = false,
                vendorIeOuis = mergeIes(existing.vendorIeOuis, observation.vendorIeOuis),
                facts = existing.facts.merge(observation.facts),
                fastPairPairing = existing.fastPairPairing || FastPair.pairingAdvertised(observation.facts),
            )
        }
        val stamped = if (existing == null) {
            merged.copy(rotationOf = rotationPredecessor(merged, now))
        } else merged
        live[key] = stamped
        if (observation.kind == app.fieldwatch.domain.RadioKind.WIFI && observation.fresh) {
            lastWifiBatchAt = System.currentTimeMillis()
        }
        return stamped
    }

    /**
     * A new randomized BLE address whose payload fingerprint matches a quiet
     * twin is probably the same radio after a MAC rotation.
     */
    private fun rotationPredecessor(device: Sighting, now: Long): String? {
        val fp = Rotation.fingerprint(device) ?: return null
        return live.values.asSequence()
            .filter { it.key != device.key && Rotation.fingerprint(it) == fp }
            .filter { now - it.lastSeen >= Rotation.PREDECESSOR_SILENT_MS }
            .maxByOrNull { it.lastSeen }
            ?.mac
    }

    private fun relabel(touched: Collection<Sighting>, fleets: List<Fleet>) {
        if (fleets !== catalogRef) {
            catalogRef = fleets
            matchStamp.clear()
        }
        val cluster = fleets.any { it.minPeers > 0 || it.clusterByOui || it.sequentialMac }
        val dirty = if (cluster || matchStamp.isEmpty()) {
            ArrayList(live.values)
        } else {
            val out = ArrayList<Sighting>(touched.size)
            for (d in touched) {
                val cur = live[d.key] ?: d
                if (matchStamp[cur.key] != matchIdentity(cur)) out += cur
            }
            out
        }
        if (dirty.isEmpty()) return
        val pool: Collection<Sighting> = if (cluster) live.values else dirty
        val matches = engine.match(pool, fleets)
        val update = if (cluster) ArrayList(live.values) else dirty
        for (d in update) {
            val cur = live[d.key] ?: continue
            val ids = matches[cur.key] ?: emptySet()
            val labeled = if (ids == cur.fleetIds) cur else cur.copy(fleetIds = ids)
            val next = PayloadLocation.applySticky(labeled, fleets)
            if (next !== cur) live[cur.key] = next
            matchStamp[cur.key] = matchIdentity(next)
        }
    }

    /** Name, vendor IEs, UUIDs, manufacturer payloads — not RSSI / GPS / gone. */
    private fun matchIdentity(device: Sighting): String {
        val ies = device.vendorIeOuis
        val uuids = device.serviceUuids
        val mfg = device.facts.mfgRecords
        val sd = device.facts.serviceData
        return buildString(64 + device.name.length + ies.size * 10) {
            append(device.name)
            append('\u0001')
            append(if (device.hiddenSsid) 'H' else '-')
            append('\u0001')
            ies.forEach { append(it); append(',') }
            append('\u0001')
            uuids.forEach { append(it); append(',') }
            append('\u0001')
            append(device.manufacturerId ?: -1)
            append('\u0001')
            append(device.manufacturerDataHex)
            append('\u0001')
            mfg.forEach { append(it.companyId); append('='); append(it.dataHex); append(',') }
            append('\u0001')
            sd.forEach { append(it.uuid); append('='); append(it.dataHex); append(',') }
        }
    }

    /** GPS-trailed radios still being heard stay through a plaza flood. */
    private fun crowdPinned(device: Sighting, now: Long, lingerMs: Long): Boolean =
        now - device.lastSeen <= lingerMs && device.gpsTrail.size >= 2

    /** Drop unnamed past linger first. Fresh hold-window radios stay until [hardLive]. */
    private fun evictOverflow(now: Long, lingerMs: Long) {
        if (live.size <= maxLive) return
        fun drop(rows: List<Sighting>, count: Int) {
            if (count <= 0) return
            rows.take(count).forEach {
                live.remove(it.key)
                matchStamp.remove(it.key)
            }
        }
        val unnamed = live.values.filter { it.fleetIds.isEmpty() }
        val expired = unnamed.filter { now - it.lastSeen > lingerMs }.sortedBy { it.lastSeen }
        drop(expired, live.size - maxLive)
        if (live.size <= maxLive) return
        if (live.size <= hardLive) return
        val stillUnnamed = live.values.filter {
            it.fleetIds.isEmpty() && !crowdPinned(it, now, lingerMs)
        }.sortedBy { it.lastSeen }
        drop(stillUnnamed, live.size - hardLive)
        if (live.size <= hardLive) return
        val unpinned = live.values.filter { !crowdPinned(it, now, lingerMs) }.sortedBy { it.lastSeen }
        drop(unpinned, live.size - hardLive)
        if (live.size <= hardLive) return
        drop(live.values.sortedBy { it.lastSeen }, live.size - hardLive)
    }

    fun refresh(
        fleets: List<Fleet>,
        staleSec: Int,
        tick: Observation? = null,
        policy: DetectionPolicy = DetectionPolicy(),
        decaySec: Int = 0,
    ) {
        synchronized(lock) { refreshLocked(fleets, staleSec, tick, policy, decaySec) }
    }

    private fun refreshLocked(
        fleets: List<Fleet>,
        staleSec: Int,
        tick: Observation? = null,
        policy: DetectionPolicy = DetectionPolicy(),
        decaySec: Int = 0,
    ) {
        val now = System.currentTimeMillis()
        val staleMs = staleSec.coerceAtLeast(15) * 1000L
        val lingerMs = maxOf(staleMs, decaySec.coerceAtLeast(0) * 1000L)
        val wifiScanFresh = lastWifiBatchAt != 0L && now - lastWifiBatchAt <= lingerMs
        live.replaceAll { _, device ->
            val gone = !stillHeard(
                kind = device.kind,
                lastSeen = device.lastSeen,
                alreadyGone = device.gone,
                now = now,
                lingerMs = lingerMs,
                wifiScanFresh = wifiScanFresh,
                wifiHold = wifiHold,
                bleHold = bleHold,
            )
            val last = device.presence.lastOrNull()
            when {
                gone && last != null && last.end == null -> last.end = device.lastSeen
                !gone && last != null && last.end != null -> last.end = null
            }
            if (device.gone == gone) device else device.copy(gone = gone)
        }
        relabel(live.values, fleets)
        live.values.filter { device ->
            val cut = if (device.fleetIds.isEmpty()) evictAnonMs else evictAfterMs
            device.lastSeen < now - cut
        }.forEach {
            live.remove(it.key)
            matchStamp.remove(it.key)
        }
        evictOverflow(now, lingerMs)
        val published = live.values.toList()
        _devices.value = published
        _stats.update { prev ->
            prev.copy(
                wifiFrames = prev.wifiFrames + if (tick?.kind == app.fieldwatch.domain.RadioKind.WIFI) 1 else 0,
                bleAdvs = prev.bleAdvs + if (tick?.kind == app.fieldwatch.domain.RadioKind.BLE) 1 else 0,
                devicesSeen = published.size,
                namedNow = published.count { !it.gone && it.fleetIds.isNotEmpty() },
                wifiNow = published.count {
                    it.kind == app.fieldwatch.domain.RadioKind.WIFI &&
                        (now - it.lastSeen <= staleMs || !wifiScanFresh || wifiHold)
                },
                bleNow = published.count {
                    it.kind == app.fieldwatch.domain.RadioKind.BLE && (!it.gone || bleHold)
                },
                lastWifiScanAt = if (tick?.kind == app.fieldwatch.domain.RadioKind.WIFI && tick.fresh) {
                    now
                } else {
                    prev.lastWifiScanAt
                },
            )
        }
    }

    fun setScanning(on: Boolean, throttleHint: String = "") {
        _stats.update { it.copy(scanning = on, throttleHint = throttleHint) }
    }

    fun setRadioHold(wifi: Boolean = wifiHold, ble: Boolean = bleHold) {
        wifiHold = wifi
        bleHold = ble
    }

    fun bumpLogs(lines: Long) {
        _stats.update { it.copy(logLines = lines) }
    }

    fun clearGpsTrails() = synchronized(lock) {
        live.replaceAll { _, device ->
            if (device.gpsTrail.isEmpty() && device.latitude == null && device.longitude == null) {
                device
            } else {
                device.copy(gpsTrail = emptyList(), latitude = null, longitude = null)
            }
        }
        _devices.value = live.values.toList()
    }

    fun find(key: String): Sighting? = live[key]

    private fun gpsStart(observation: Observation): List<app.fieldwatch.domain.GpsSample> {
        val lat = observation.latitude ?: return emptyList()
        val lon = observation.longitude ?: return emptyList()
        return listOf(app.fieldwatch.domain.GpsSample(observation.at, lat, lon, observation.rssi))
    }

    private fun gpsAppend(
        trail: List<app.fieldwatch.domain.GpsSample>,
        observation: Observation,
    ): List<app.fieldwatch.domain.GpsSample> {
        val lat = observation.latitude ?: return trail
        val lon = observation.longitude ?: return trail
        return app.fieldwatch.domain.Geo.append(trail, observation.at, lat, lon, observation.rssi)
    }

    private fun mergeMfgHex(old: String, extra: String): String {
        if (extra.isBlank()) return old
        if (old.isBlank()) return extra.take(512)
        if (extra.take(2).equals(old.take(2), ignoreCase = true) && extra.length >= old.length) {
            return extra.take(512)
        }
        return old
    }

    private fun mergeIes(old: List<String>, extra: List<String>): List<String> {
        if (extra.isEmpty()) return old
        if (old.isEmpty()) return extra
        return (old + extra).distinct()
    }

    companion object {
        /**
         * A late Wi-Fi scan or a BLE restart must not un-gone radios that already
         * aged out. Hold only keeps currently-live rows from flipping gone.
         */
        fun stillHeard(
            kind: app.fieldwatch.domain.RadioKind,
            lastSeen: Long,
            alreadyGone: Boolean,
            now: Long,
            lingerMs: Long,
            wifiScanFresh: Boolean,
            wifiHold: Boolean,
            bleHold: Boolean,
        ): Boolean {
            if (now - lastSeen <= lingerMs) return true
            return when (kind) {
                app.fieldwatch.domain.RadioKind.WIFI ->
                    if (wifiHold || !wifiScanFresh) !alreadyGone else false
                app.fieldwatch.domain.RadioKind.BLE ->
                    if (bleHold) !alreadyGone else false
            }
        }
    }
}
