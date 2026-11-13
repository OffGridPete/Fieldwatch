package app.fieldwatch.domain

/**
 * Two quiet warnings. A separated tag that stays with this phone, and a finder
 * heard at two stops. Neither uses Moving with you. Mine stays out.
 *
 * The separated clock is this phone's GPS, about 100 m from the last place,
 * with no quiet gap. Places uses the stop list that already exists.
 */
object TrackerWatch {
    const val FOLLOW_MS = 30 * 60_000L
    const val GAP_MS = 20 * 60_000L
    const val FOLLOW_PLACES = 3
    const val APART_M = 100.0

    enum class Kind { FOLLOW, PLACES }

    data class Spot(val lat: Double, val lon: Double)

    data class Notice(
        val id: String,
        val kind: Kind,
        val deviceKey: String,
        val name: String,
        val mac: String,
        val rssi: Int,
        val minutes: Int = 0,
        val places: Int = 0,
        val alsoPlaces: Int = 0,
        val stops: List<Spot> = emptyList(),
    )

    data class Episode(
        val id: String,
        val deviceKey: String,
        val name: String,
        val mac: String,
        val rssi: Int,
        val startedAt: Long,
        val lastHeard: Long,
        val spots: List<Spot> = emptyList(),
        val seen: Boolean = false,
        val raised: Boolean = false,
    )

    data class Hear(
        val device: Sighting,
        val packet: RadioFacts,
        val fleetNames: List<String> = emptyList(),
        val custom: String? = null,
    )

    data class FollowResult(
        val episodes: List<Episode>,
        val ready: List<Notice>,
    )

    data class PlaceRow(
        val id: String,
        val deviceKey: String,
        val name: String,
        val mac: String,
        val rssi: Int,
        val count: Int,
        val stops: List<Spot> = emptyList(),
    )

    data class PlaceResult(
        val seen: Set<String>,
        val fresh: List<Notice>,
    )

    fun follow(
        episodes: List<Episode>,
        hears: List<Hear>,
        mine: Set<String>,
        lat: Double?,
        lon: Double?,
        now: Long,
        enabled: Boolean,
    ): FollowResult {
        if (!enabled) return FollowResult(emptyList(), emptyList())
        val open = episodes.filter { now - it.lastHeard <= GAP_MS }.associateBy { it.id }.toMutableMap()
        for (hear in hears) {
            if (hear.device.kind != RadioKind.BLE) continue
            if (hear.device.key in mine) {
                val mac = hear.device.mac
                open.entries.removeAll { it.value.deviceKey == hear.device.key || it.value.mac == mac }
                continue
            }
            val read = read(hear)
            when (read?.state) {
                Sep.NEAR -> open.remove(read.id)
                Sep.SEPARATED -> {
                    val prev = open[read.id]
                    open[read.id] = Episode(
                        id = read.id,
                        deviceKey = hear.device.key,
                        name = label(hear),
                        mac = hear.device.mac,
                        rssi = hear.device.rssi,
                        startedAt = prev?.startedAt ?: now,
                        lastHeard = now,
                        spots = addSpot(prev?.spots.orEmpty(), lat, lon),
                        seen = prev?.seen == true,
                        raised = prev?.raised == true,
                    )
                }
                else -> {
                    val mac = hear.device.mac
                    for ((id, episode) in open) {
                        if (episode.mac != mac && episode.deviceKey != hear.device.key) continue
                        open[id] = episode.copy(
                            deviceKey = hear.device.key,
                            name = label(hear).ifBlank { episode.name },
                            rssi = hear.device.rssi,
                            lastHeard = now,
                        )
                    }
                }
            }
        }
        val ready = ArrayList<Notice>()
        val next = open.values.map { episode ->
            val due = !episode.seen && !episode.raised &&
                now - episode.startedAt >= FOLLOW_MS &&
                episode.spots.size >= FOLLOW_PLACES
            if (!due) {
                episode
            } else {
                ready += Notice(
                    id = episode.id,
                    kind = Kind.FOLLOW,
                    deviceKey = episode.deviceKey,
                    name = episode.name,
                    mac = episode.mac,
                    rssi = episode.rssi,
                    minutes = ((now - episode.startedAt) / 60_000L).toInt().coerceAtLeast(30),
                    places = episode.spots.size,
                    stops = episode.spots,
                )
                episode.copy(raised = true)
            }
        }
        return FollowResult(next, ready)
    }

    /**
     * [armed] false records every finder already at two stops and raises nothing.
     * A later crossing, after the count drops below two, can raise again.
     */
    fun places(seen: Set<String>, rows: List<PlaceRow>, armed: Boolean): PlaceResult {
        val qualified = rows.filter { it.id.startsWith("ble:") && it.count >= PlaceMemory.MIN_PLACES }
        val ids = qualified.map { it.id }.toSet()
        if (!armed) return PlaceResult(ids, emptyList())
        val fresh = qualified.filter { it.id !in seen }.map { row ->
            Notice(
                id = "places:${row.id}",
                kind = Kind.PLACES,
                deviceKey = row.deviceKey,
                name = row.name,
                mac = row.mac,
                rssi = row.rssi,
                places = row.count,
                stops = row.stops,
            )
        }
        return PlaceResult(ids, fresh)
    }

    /** One warning per radio. The separated sentence wins, and keeps the place count. */
    fun merge(followReady: List<Notice>, placeFresh: List<Notice>): List<Notice> {
        val byDevice = placeFresh.associateBy { it.deviceKey }
        val followKeys = followReady.map { it.deviceKey }.toSet()
        val follow = followReady.map { notice ->
            val extra = byDevice[notice.deviceKey] ?: return@map notice
            notice.copy(alsoPlaces = extra.places)
        }
        return follow + placeFresh.filter { it.deviceKey !in followKeys }
    }

    private enum class Sep { SEPARATED, NEAR }

    private data class Read(val id: String, val state: Sep)

    private fun read(hear: Hear): Read? {
        val device = hear.device
        val smart = smartRead(hear.packet)
        if (smart != null) {
            val state = when {
                smart.separated -> Sep.SEPARATED
                smart.state != null -> Sep.NEAR
                else -> return null
            }
            return Read("smarttag:${smart.privacyId}", state)
        }
        var near = false
        var separated = false
        when (dult(hear.packet)) {
            Sep.NEAR -> near = true
            Sep.SEPARATED -> separated = true
            null -> Unit
        }
        when (findHub(hear.packet)) {
            Sep.NEAR -> near = true
            Sep.SEPARATED -> separated = true
            null -> Unit
        }
        when (findMy(device, hear.packet)) {
            Sep.NEAR -> near = true
            Sep.SEPARATED -> separated = true
            null -> Unit
        }
        val state = when {
            near -> Sep.NEAR
            separated -> Sep.SEPARATED
            else -> return null
        }
        return Read("ble:${device.mac}", state)
    }

    private fun label(hear: Hear): String {
        val custom = hear.custom?.trim().orEmpty()
        if (custom.isNotEmpty() && !custom.equals(hear.device.mac, ignoreCase = true)) return custom
        val advertised = hear.device.name.trim()
        if (advertised.isNotEmpty() && !advertised.equals(hear.device.mac, ignoreCase = true)) {
            return advertised
        }
        return hear.fleetNames.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun addSpot(spots: List<Spot>, lat: Double?, lon: Double?): List<Spot> {
        if (lat == null || lon == null) return spots
        if (spots.any { Geo.meters(it.lat, it.lon, lat, lon) < APART_M }) return spots
        return spots + Spot(lat, lon)
    }

    private fun smartRead(packet: RadioFacts): SmartTag.Read? {
        for (rec in packet.serviceData) {
            if (!SmartTag.isService(rec.uuid)) continue
            SmartTag.parse(rec.dataHex)?.let { return it }
        }
        return null
    }

    private fun dult(packet: RadioFacts): Sep? {
        for (rec in packet.serviceData) {
            if (uuidShort(rec.uuid) != "FCB2") continue
            val bytes = hexBytes(rec.dataHex) ?: continue
            if (bytes.size < 2) continue
            return if (bytes[1].toInt() and 0x01 == 0) Sep.SEPARATED else Sep.NEAR
        }
        return null
    }

    private fun findHub(packet: RadioFacts): Sep? {
        for (rec in packet.serviceData) {
            if (uuidShort(rec.uuid) != "FEAA") continue
            val bytes = hexBytes(rec.dataHex) ?: continue
            if (bytes.isEmpty()) continue
            return when (bytes[0].toInt() and 0xFF) {
                0x41 -> Sep.SEPARATED
                0x40 -> Sep.NEAR
                else -> null
            }
        }
        return null
    }

    /** Long form with the recent-owner bit clear. A phone, a short form, or a maintained tag stays out. */
    private fun findMy(device: Sighting, packet: RadioFacts): Sep? {
        if (applePhone(device, packet)) return null
        var near = false
        var separated = false
        for (rec in packet.mfgRecords) {
            if (rec.companyId != 0x004C) continue
            val bytes = hexBytes(rec.dataHex) ?: continue
            for (tlv in appleTlvs(bytes)) {
                if (tlv.type != 0x12) continue
                when {
                    tlv.data.size == 0x02 -> near = true
                    tlv.data.size == 0x19 && tlv.data[0].toInt() and 0x04 != 0 -> near = true
                    tlv.data.size == 0x19 -> separated = true
                }
            }
        }
        return when {
            near -> Sep.NEAR
            separated -> Sep.SEPARATED
            else -> null
        }
    }

    private fun applePhone(device: Sighting, packet: RadioFacts): Boolean {
        if (device.name.contains("AirTag", ignoreCase = true)) return false
        if (hasFd44(device) || hasFd44(packet)) return false
        if ("fleet-apple-device" in device.fleetIds || "fleet-apple-audio" in device.fleetIds) return true
        for (rec in packet.mfgRecords) {
            if (rec.companyId != 0x004C) continue
            val bytes = hexBytes(rec.dataHex) ?: continue
            if (appleTlvs(bytes).any { it.type in CONTINUITY }) return true
        }
        return false
    }

    private fun hasFd44(device: Sighting): Boolean =
        device.serviceUuids.any { uuidShort(it) == "FD44" } ||
            device.facts.serviceData.any { uuidShort(it.uuid) == "FD44" }

    private fun hasFd44(packet: RadioFacts): Boolean =
        packet.serviceData.any { uuidShort(it.uuid) == "FD44" }

    private data class Tlv(val type: Int, val data: ByteArray)

    private fun appleTlvs(bytes: ByteArray): List<Tlv> {
        val out = ArrayList<Tlv>(3)
        var i = 0
        while (i + 2 <= bytes.size) {
            val type = bytes[i].toInt() and 0xFF
            val len = bytes[i + 1].toInt() and 0xFF
            if (len <= 0 || i + 2 + len > bytes.size) break
            out += Tlv(type, bytes.copyOfRange(i + 2, i + 2 + len))
            i += 2 + len
        }
        return out
    }

    private fun uuidShort(uuid: String): String {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex.length == 4 -> hex
            hex.length == 32 && hex.startsWith("0000") -> hex.substring(4, 8)
            else -> hex
        }
    }

    private fun hexBytes(hex: String): ByteArray? {
        val clean = hex.filter { it.isLetterOrDigit() }
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private val CONTINUITY = setOf(
        0x05, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
    )
}
