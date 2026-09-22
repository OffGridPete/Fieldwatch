package app.fieldwatch.domain

/**
 * One unique radio collapsed from many log rows. Re-match with the current
 * catalog — do not trust the write-time fleets column.
 */
data class LogRadio(
    val kind: RadioKind,
    val mac: String,
    val name: String,
    val vendor: String?,
    val manufacturerId: Int?,
    val manufacturerDataHex: String,
    val serviceUuids: List<String>,
    val vendorIeOuis: List<String>,
    val randomized: Boolean,
    val hiddenSsid: Boolean,
    val rssi: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val hits: Int,
    val channel: Int = 0,
    val frequencyMhz: Int = 0,
    val security: String? = null,
    /** This phone's position when the radio was last heard — not the radio's fix. */
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val key: String get() = "${kind.name}:$mac"
    val hasPosition: Boolean get() = latitude != null && longitude != null
}

/** Live-free view of a logged radio for signature matching and decoders. */
fun LogRadio.toSighting(): Sighting = Sighting(
    key = key,
    kind = kind,
    mac = mac,
    name = name,
    rssi = rssi,
    rssiMin = rssi,
    rssiMax = rssi,
    channel = channel,
    frequencyMhz = frequencyMhz,
    vendor = vendor,
    randomized = randomized,
    hiddenSsid = hiddenSsid,
    serviceUuids = serviceUuids,
    manufacturerId = manufacturerId,
    manufacturerDataHex = manufacturerDataHex,
    rawHex = manufacturerDataHex,
    extras = "",
    firstSeen = firstSeen,
    lastSeen = lastSeen,
    hitCount = hits,
    fleetIds = emptySet(),
    rssiHistory = emptyList(),
    presence = emptyList(),
    latitude = latitude,
    longitude = longitude,
    vendorIeOuis = vendorIeOuis,
    facts = RadioFacts(
        mfgRecords = manufacturerId?.let { listOf(MfgRecord(it, manufacturerDataHex)) } ?: emptyList(),
        vendorIes = vendorIeOuis.map { VendorIeRecord(it, -1, "") },
        security = security,
    ),
)

/** Parse rotating / exported Fieldwatch logs into unique radios. */
object LogReplay {
    fun parse(text: String): List<LogRadio> {
        val acc = LinkedHashMap<String, LogRadio>()
        val trimmed = text.replace("\u0000", "")
        val json = trimmed.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.startsWith("{") == true
        ingest(trimmed.lineSequence(), json, acc)
        return acc.values.toList()
    }

    fun ingest(
        lines: Sequence<String>,
        json: Boolean,
        acc: MutableMap<String, LogRadio>,
    ) {
        var header: List<String>? = null
        lines.forEach { raw ->
            val line = raw.replace("\u0000", "").trimEnd('\r')
            if (line.isBlank()) return@forEach
            if (json) {
                if (!line.startsWith("{")) return@forEach
                ingestJson(line, acc)
                return@forEach
            }
            if (header == null) {
                if (line.startsWith("timestamp")) {
                    header = line.split(',')
                    return@forEach
                }
                header = DEFAULT_CSV_HEADER
            }
            ingestCsv(line, header!!, acc)
        }
    }

    private fun ingestCsv(line: String, header: List<String>, acc: MutableMap<String, LogRadio>) {
        val cols = line.split(',')
        fun col(name: String): String {
            val i = header.indexOf(name)
            if (i < 0 || i >= cols.size) return ""
            return cols[i]
        }
        val kind = runCatching { RadioKind.valueOf(col("kind").trim()) }.getOrNull() ?: return
        val mac = MacUtil.normalize(col("mac"))
        if (mac.isBlank()) return
        val flags = col("flags").uppercase()
        val uuids = col("uuids").split('|', ',').map { it.trim() }.filter { it.isNotEmpty() }
        val ies = col("vendor_ie").split('|').map { it.trim() }.filter { it.isNotEmpty() }
            .map { MacUtil.normalize(it) }
        val mfgRaw = col("mfg").trim()
        val mfg = mfgRaw.toIntOrNull(16) ?: mfgRaw.toIntOrNull()
        val ts = col("timestamp").toLongOrNull() ?: 0L
        merge(
            acc,
            LogRadio(
                kind = kind,
                mac = mac,
                name = col("name").trim(),
                vendor = col("vendor").trim().ifBlank { null },
                manufacturerId = mfg,
                manufacturerDataHex = col("raw").trim(),
                serviceUuids = uuids,
                vendorIeOuis = ies,
                randomized = flags.contains("RAND") || MacUtil.isRandomized(mac),
                hiddenSsid = flags.contains("HIDDEN"),
                rssi = col("rssi").toIntOrNull() ?: -100,
                firstSeen = ts,
                lastSeen = ts,
                hits = 1,
                channel = col("channel").toIntOrNull() ?: 0,
                frequencyMhz = col("freq").toIntOrNull() ?: 0,
                security = col("sec").trim().ifBlank { null },
                latitude = col("lat").toDoubleOrNull(),
                longitude = col("lon").toDoubleOrNull(),
            ),
        )
    }

    private fun ingestJson(line: String, acc: MutableMap<String, LogRadio>) {
        val obj = runCatching { org.json.JSONObject(line) }.getOrNull() ?: return
        fun str(key: String) = obj.optString(key, "")
        val kind = runCatching { RadioKind.valueOf(str("kind")) }.getOrNull() ?: return
        val mac = MacUtil.normalize(str("mac"))
        if (mac.isBlank()) return
        val uuids = str("uuids").split(',', '|').map { it.trim() }.filter { it.isNotEmpty() }
        val ies = str("vendor_ie").split('|').map { it.trim() }.filter { it.isNotEmpty() }
            .map { MacUtil.normalize(it) }
        val mfg = when {
            !obj.has("mfg") || obj.isNull("mfg") -> null
            else -> {
                val n = obj.optInt("mfg", Int.MIN_VALUE)
                if (n != Int.MIN_VALUE) n else str("mfg").toIntOrNull(16)
            }
        }
        val ts = obj.optLong("ts", 0L)
        val rand = obj.optBoolean("rand", MacUtil.isRandomized(mac))
        fun coord(key: String): Double? =
            if (obj.has(key) && !obj.isNull(key)) obj.optDouble(key).takeIf { it.isFinite() } else null
        merge(
            acc,
            LogRadio(
                kind = kind,
                mac = mac,
                name = str("name"),
                vendor = str("vendor").ifBlank { null },
                manufacturerId = mfg,
                manufacturerDataHex = str("raw"),
                serviceUuids = uuids,
                vendorIeOuis = ies,
                randomized = rand,
                hiddenSsid = obj.optBoolean("hidden", false),
                rssi = obj.optInt("rssi", -100),
                firstSeen = ts,
                lastSeen = ts,
                hits = 1,
                channel = obj.optInt("channel", 0),
                frequencyMhz = obj.optInt("freq", 0),
                security = str("sec").ifBlank { null },
                latitude = coord("lat"),
                longitude = coord("lon"),
            ),
        )
    }

    private fun merge(acc: MutableMap<String, LogRadio>, row: LogRadio) {
        val prev = acc[row.key]
        if (prev == null) {
            acc[row.key] = row
            return
        }
        acc[row.key] = prev.copy(
            name = row.name.ifBlank { prev.name },
            vendor = row.vendor ?: prev.vendor,
            manufacturerId = row.manufacturerId ?: prev.manufacturerId,
            manufacturerDataHex = row.manufacturerDataHex.ifBlank { prev.manufacturerDataHex },
            serviceUuids = (prev.serviceUuids + row.serviceUuids).distinct(),
            vendorIeOuis = (prev.vendorIeOuis + row.vendorIeOuis).distinct(),
            randomized = prev.randomized || row.randomized,
            hiddenSsid = prev.hiddenSsid || row.hiddenSsid,
            rssi = row.rssi,
            firstSeen = if (prev.firstSeen == 0L) row.firstSeen else minOf(prev.firstSeen, row.firstSeen),
            lastSeen = maxOf(prev.lastSeen, row.lastSeen),
            hits = prev.hits + row.hits,
            channel = if (row.channel != 0) row.channel else prev.channel,
            frequencyMhz = if (row.frequencyMhz != 0) row.frequencyMhz else prev.frequencyMhz,
            security = row.security ?: prev.security,
            latitude = row.latitude ?: prev.latitude,
            longitude = row.longitude ?: prev.longitude,
        )
    }

    private val DEFAULT_CSV_HEADER = listOf(
        "timestamp", "iso", "kind", "mac", "name", "rssi", "channel", "freq",
        "oui", "vendor", "fleets", "mfg", "uuids", "flags", "raw", "lat", "lon",
        "vendor_ie", "sec",
    )
}
