package app.fieldwatch.domain

/**
 * Samsung SmartTag FD5A service data. One parser owns the layout.
 * The privacy ID is not a device key. A separated tag keeps it only on the sighting the sit already stores.
 */
object SmartTag {
    data class Read(
        /** Null for states 0 and 7. The frame still passed. */
        val state: String?,
        val separated: Boolean,
        val aging: Int,
        val battery: String,
        val uwb: String,
        /** Eight bytes, compact uppercase hex. */
        val privacyId: String,
    )

    fun isService(uuid: String): Boolean {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return hex == "FD5A" || (hex.length == 32 && hex.startsWith("0000FD5A"))
    }

    fun read(device: Sighting): Read? {
        for (rec in device.facts.serviceData) {
            if (!isService(rec.uuid)) continue
            parse(rec.dataHex)?.let { return it }
        }
        return null
    }

    /**
     * Privacy ID when the latest FD5A frame is separated.
     * A sighting with no FD5A bytes keeps the ID the sit already stored.
     * With owner, unlabeled, and rejected frames clear it.
     */
    fun separatedId(device: Sighting): String? {
        val records = device.facts.serviceData.filter { isService(it.uuid) }
        if (records.isEmpty()) return device.smartTagId
        val read = records.firstNotNullOfOrNull { parse(it.dataHex) }
        return read?.privacyId?.takeIf { read.separated }
    }

    fun parse(hex: String): Read? {
        val bytes = hexBytes(hex) ?: return null
        if (bytes.size < 13) return null
        val frame = bytes[0].toInt() and 0xFF
        if ((frame and 0xF8) != 0x10) return null
        val stateN = frame and 0x07
        val state = when (stateN) {
            1, 2 -> "Separated from owner"
            3 -> "Separated from owner, away more than 24 h"
            4, 5, 6 -> "With owner"
            else -> null
        }
        val aging = (bytes[1].toInt() and 0xFF) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            ((bytes[3].toInt() and 0xFF) shl 16)
        val id = buildString(16) {
            for (i in 4..11) append("%02X".format(bytes[i].toInt() and 0xFF))
        }
        val flags = bytes[12].toInt() and 0xFF
        val battery = when (flags and 0x03) {
            0 -> "Very low"
            1 -> "Low"
            2 -> "Medium"
            else -> "Full"
        }
        val uwb = if ((flags and 0x04) != 0) "UWB" else "No UWB"
        return Read(
            state = state,
            separated = stateN == 1 || stateN == 2 || stateN == 3,
            aging = aging,
            battery = battery,
            uwb = uwb,
            privacyId = id,
        )
    }

    /**
     * One finder subject per separated privacy ID.
     * The subject takes the earliest first-heard time, the latest last-heard time,
     * the loudest and quietest RSSI, and the combined GPS samples.
     * It exists only for this test. The current RSSI reading follows the loudest.
     */
    fun poolForFollow(trackers: List<Sighting>): List<Sighting> {
        if (trackers.none { !it.smartTagId.isNullOrEmpty() }) return trackers
        val byId = LinkedHashMap<String, MutableList<Sighting>>()
        for (d in trackers) {
            val id = d.smartTagId
            if (!id.isNullOrEmpty()) byId.getOrPut(id) { ArrayList() }.add(d)
        }
        val seen = HashSet<String>()
        val out = ArrayList<Sighting>(trackers.size)
        for (d in trackers) {
            val id = d.smartTagId
            if (id.isNullOrEmpty()) {
                out += d
                continue
            }
            if (!seen.add(id)) continue
            val group = byId.getValue(id)
            out += if (group.size == 1) group[0] else pooled(group)
        }
        return out
    }

    private fun pooled(group: List<Sighting>): Sighting {
        val base = group.minBy { it.firstSeen }
        val rssiMax = group.map { it.rssiMax }.filter { Rssi.measured(it) }.maxOrNull() ?: base.rssiMax
        val rssiMin = group.map { it.rssiMin }.filter { Rssi.measured(it) }.minOrNull() ?: base.rssiMin
        val trail = group
            .flatMap { it.gpsTrail }
            .distinctBy { Triple(it.at, it.lat, it.lon) }
            .sortedBy { it.at }
        return base.copy(
            firstSeen = group.minOf { it.firstSeen },
            lastSeen = group.maxOf { it.lastSeen },
            rssi = rssiMax,
            rssiMin = rssiMin,
            rssiMax = rssiMax,
            gpsTrail = trail,
        )
    }

    private fun hexBytes(hex: String): ByteArray? {
        val clean = hex.filter { it.isLetterOrDigit() }
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
