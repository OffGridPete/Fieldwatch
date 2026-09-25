package app.fieldwatch.domain

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Share/Save of the selected sit (or last 15 minutes). Not the rotating log. */
object SitExport {
    const val CSV_HEADER =
        "kind,mac,name,custom_name,observer_notes,rssi,rssi_min,rssi_max,channel,frequency_mhz," +
            "randomized,hidden,first_seen,last_seen,hits,lat,lon,extra_attention"

    fun csv(
        devices: List<Sighting>,
        radios: LogExportRadios,
        customNames: Map<String, String>,
        observerNotes: Map<String, String>,
        extraKeys: Set<String>,
    ): String = buildString {
        append(CSV_HEADER).append('\n')
        rows(devices, radios).forEach { d ->
            val pin = hearPoint(d)
            append(
                listOf(
                    csv(d.kind.name),
                    csv(d.mac),
                    csv(d.name),
                    csv(customNames[d.key].orEmpty()),
                    csv(observerNotes[d.key].orEmpty()),
                    d.rssi.toString(),
                    d.rssiMin.toString(),
                    d.rssiMax.toString(),
                    d.channel.toString(),
                    d.frequencyMhz.toString(),
                    d.randomized.toString(),
                    d.hiddenSsid.toString(),
                    iso(d.firstSeen),
                    iso(d.lastSeen),
                    d.hitCount.toString(),
                    pin?.lat?.let { coord(it) }.orEmpty(),
                    pin?.lon?.let { coord(it) }.orEmpty(),
                    (d.key in extraKeys).toString(),
                ).joinToString(","),
            ).append('\n')
        }
    }

    fun jsonl(
        devices: List<Sighting>,
        radios: LogExportRadios,
        customNames: Map<String, String>,
        observerNotes: Map<String, String>,
        extraKeys: Set<String>,
    ): String = buildString {
        rows(devices, radios).forEach { d ->
            val pin = hearPoint(d)
            val obj = JSONObject()
            obj.put("kind", d.kind.name)
            obj.put("mac", d.mac)
            obj.put("name", d.name)
            obj.put("custom_name", customNames[d.key].orEmpty())
            obj.put("observer_notes", observerNotes[d.key].orEmpty())
            obj.put("rssi", d.rssi)
            obj.put("rssi_min", d.rssiMin)
            obj.put("rssi_max", d.rssiMax)
            obj.put("channel", d.channel)
            obj.put("frequency_mhz", d.frequencyMhz)
            obj.put("randomized", d.randomized)
            obj.put("hidden", d.hiddenSsid)
            obj.put("first_seen", iso(d.firstSeen))
            obj.put("last_seen", iso(d.lastSeen))
            obj.put("hits", d.hitCount)
            if (pin != null) {
                obj.put("lat", pin.lat)
                obj.put("lon", pin.lon)
            } else {
                obj.put("lat", JSONObject.NULL)
                obj.put("lon", JSONObject.NULL)
            }
            obj.put("extra_attention", d.key in extraKeys)
            append(obj.toString()).append('\n')
        }
    }

    fun mapRadios(
        devices: List<Sighting>,
        radios: LogExportRadios,
    ): List<LogRadio> = rows(devices, radios).mapNotNull { d ->
        val pin = hearPoint(d) ?: return@mapNotNull null
        LogRadio(
            kind = d.kind,
            mac = d.mac,
            name = d.name,
            vendor = d.vendor,
            manufacturerId = d.manufacturerId,
            manufacturerDataHex = d.manufacturerDataHex,
            serviceUuids = d.serviceUuids,
            vendorIeOuis = emptyList(),
            randomized = d.randomized,
            hiddenSsid = d.hiddenSsid,
            rssi = d.rssi,
            firstSeen = d.firstSeen,
            lastSeen = d.lastSeen,
            hits = d.hitCount,
            channel = d.channel,
            frequencyMhz = d.frequencyMhz,
            latitude = pin.lat,
            longitude = pin.lon,
        )
    }

    fun rows(devices: List<Sighting>, radios: LogExportRadios): List<Sighting> =
        devices.filter { radios.matches(it.kind) }
            .sortedWith(compareByDescending<Sighting> { it.rssi }.thenBy { it.mac })

    fun hearPoint(d: Sighting): GpsSample? {
        val loudest = d.gpsTrail.maxByOrNull { it.rssi }
        if (loudest != null) return loudest
        val lat = d.latitude
        val lon = d.longitude
        if (lat != null && lon != null) return GpsSample(d.lastSeen, lat, lon, d.rssi)
        return null
    }

    fun suggestedName(kind: LogExportKind, sitName: String, stamp: String): String {
        val slug = sitName.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(24)
            .ifBlank { "sit" }
        val prefix = if (kind == LogExportKind.WIGLE) "fieldwatch-sit-wigle" else "fieldwatch-sit"
        val ext = when (kind) {
            LogExportKind.LOG_CSV, LogExportKind.WIGLE -> "csv"
            LogExportKind.LOG_JSONL -> "jsonl"
            LogExportKind.GPX -> "gpx"
            LogExportKind.KML -> "kml"
        }
        return "$prefix-$slug-$stamp.$ext"
    }

    fun mime(kind: LogExportKind): String = when (kind) {
        LogExportKind.LOG_CSV, LogExportKind.WIGLE -> "text/csv"
        LogExportKind.LOG_JSONL -> "application/x-ndjson"
        LogExportKind.GPX, LogExportKind.KML -> GeoExport.formatOf(kind)!!.mime
    }

    fun subject(kind: LogExportKind, sitName: String): String = when (kind) {
        LogExportKind.LOG_CSV -> "Fieldwatch sit $sitName (CSV)"
        LogExportKind.LOG_JSONL -> "Fieldwatch sit $sitName (JSON lines)"
        LogExportKind.GPX -> "Fieldwatch sit $sitName (GPX)"
        LogExportKind.KML -> "Fieldwatch sit $sitName (KML)"
        LogExportKind.WIGLE -> "Fieldwatch sit $sitName (WiGLE CSV)"
    }

    fun emptyHint(kind: LogExportKind, radios: LogExportRadios): String {
        val which = when (radios) {
            LogExportRadios.BOTH -> "radios"
            LogExportRadios.WIFI -> "Wi-Fi radios"
            LogExportRadios.BLE -> "BLE radios"
        }
        return if (kind == LogExportKind.LOG_CSV || kind == LogExportKind.LOG_JSONL) {
            "No $which in this sit."
        } else {
            "No GPS-tagged $which in this sit. Settings → Tag detections with GPS."
        }
    }

    private fun csv(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${s.replace("\"", "\"\"")}\""
        } else s

    private fun coord(v: Double): String = String.format(Locale.US, "%.6f", v)

    private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun iso(ms: Long): String = ISO.format(Date(ms))
}
