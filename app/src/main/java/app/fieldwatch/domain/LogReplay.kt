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
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val key: String get() = "${kind.name}:$mac"
    val hasPosition: Boolean get() = latitude != null && longitude != null
}

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
                latitude = obj.optDouble("lat").takeIf { obj.has("lat") && !obj.isNull("lat") },
                longitude = obj.optDouble("lon").takeIf { obj.has("lon") && !obj.isNull("lon") },
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
            latitude = row.latitude ?: prev.latitude,
            longitude = row.longitude ?: prev.longitude,
        )
    }

    internal val DEFAULT_CSV_HEADER = listOf(
        "timestamp", "iso", "kind", "mac", "name", "rssi", "channel", "freq",
        "oui", "vendor", "fleets", "mfg", "uuids", "flags", "raw", "lat", "lon", "vendor_ie",
    )

    fun lineKind(line: String, json: Boolean, header: List<String> = DEFAULT_CSV_HEADER): RadioKind? {
        if (json) {
            val obj = runCatching { org.json.JSONObject(line) }.getOrNull() ?: return null
            return runCatching { RadioKind.valueOf(obj.optString("kind")) }.getOrNull()
        }
        val cols = line.split(',')
        val i = header.indexOf("kind")
        if (i < 0 || i >= cols.size) return null
        return runCatching { RadioKind.valueOf(cols[i].trim()) }.getOrNull()
    }

    /** One CSV data line → JSONL object (no trailing newline). */
    fun csvRowToJson(line: String, header: List<String> = DEFAULT_CSV_HEADER): String? {
        val cols = line.split(',')
        if (cols.size < 5) return null
        fun col(name: String): String {
            val i = header.indexOf(name)
            if (i < 0 || i >= cols.size) return ""
            return cols[i]
        }
        val kind = col("kind").trim()
        if (kind != "WIFI" && kind != "BLE") return null
        val flags = col("flags").uppercase()
        val obj = org.json.JSONObject()
            .put("ts", col("timestamp").toLongOrNull() ?: 0L)
            .put("iso", col("iso"))
            .put("kind", kind)
            .put("mac", col("mac"))
            .put("name", col("name"))
            .put("rssi", col("rssi").toIntOrNull() ?: -100)
            .put("channel", col("channel").toIntOrNull() ?: 0)
            .put("freq", col("freq").toIntOrNull() ?: 0)
            .put("oui", col("oui"))
            .put("vendor", col("vendor").ifBlank { org.json.JSONObject.NULL })
            .put("fleets", col("fleets"))
        val mfgRaw = col("mfg").trim()
        val mfg = mfgRaw.toIntOrNull(16) ?: mfgRaw.toIntOrNull()
        if (mfg != null) obj.put("mfg", mfg) else obj.put("mfg", org.json.JSONObject.NULL)
        obj.put("uuids", col("uuids").replace('|', ','))
            .put("raw", col("raw"))
            .put("vendor_ie", col("vendor_ie"))
            .put("rand", flags.contains("RAND"))
            .put("hidden", flags.contains("HIDDEN"))
        val lat = col("lat").trim()
        val lon = col("lon").trim()
        if (lat.isNotEmpty()) obj.put("lat", lat.toDoubleOrNull() ?: lat) else obj.put("lat", org.json.JSONObject.NULL)
        if (lon.isNotEmpty()) obj.put("lon", lon.toDoubleOrNull() ?: lon) else obj.put("lon", org.json.JSONObject.NULL)
        return obj.toString()
    }

    /** One JSONL object → CSV data line (no trailing newline). */
    fun jsonRowToCsv(line: String): String? {
        val obj = runCatching { org.json.JSONObject(line) }.getOrNull() ?: return null
        val kind = obj.optString("kind").trim()
        if (kind != "WIFI" && kind != "BLE") return null
        fun str(key: String) = obj.optString(key, "")
        val flags = buildString {
            if (obj.optBoolean("rand", false)) append("RAND ")
            if (obj.optBoolean("hidden", false)) append("HIDDEN ")
        }.trim()
        val mfg = when {
            !obj.has("mfg") || obj.isNull("mfg") -> ""
            else -> {
                val n = obj.optInt("mfg", Int.MIN_VALUE)
                if (n != Int.MIN_VALUE) n.toString(16).uppercase() else str("mfg")
            }
        }
        fun coord(key: String): String {
            if (!obj.has(key) || obj.isNull(key)) return ""
            val d = obj.optDouble(key, Double.NaN)
            return if (d.isNaN()) str(key) else String.format(java.util.Locale.US, "%.6f", d)
        }
        val uuids = str("uuids").replace(',', '|')
        return listOf(
            obj.optLong("ts", 0L).toString(),
            str("iso"),
            kind,
            str("mac"),
            str("name").replace(',', ' '),
            obj.optInt("rssi", -100).toString(),
            obj.optInt("channel", 0).toString(),
            obj.optInt("freq", 0).toString(),
            str("oui"),
            str("vendor").replace(',', ' '),
            str("fleets").replace(',', ' '),
            mfg,
            uuids,
            flags,
            str("raw"),
            coord("lat"),
            coord("lon"),
            str("vendor_ie"),
        ).joinToString(",")
    }
}
