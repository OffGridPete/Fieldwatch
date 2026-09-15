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
) {
    val key: String get() = "${kind.name}:$mac"
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
        )
    }

    private val DEFAULT_CSV_HEADER = listOf(
        "timestamp", "iso", "kind", "mac", "name", "rssi", "channel", "freq",
        "oui", "vendor", "fleets", "mfg", "uuids", "flags", "raw", "lat", "lon", "vendor_ie",
    )
}
