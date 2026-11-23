package app.fieldwatch.domain

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Share/save format. [sitOnly] formats are Reports → Sit export.
 * The rotating log does not have a path, an advertised track, or a GNSS hit.
 */
enum class LogExportKind(val titleKey: String, val sitOnly: Boolean = false) {
    LOG_CSV("export.log.csv"),
    LOG_JSONL("export.log.jsonl"),
    GPX("export.gpx"),
    KML("export.kml"),
    GEOJSON("export.geojson", sitOnly = true),
    WIGLE("export.wigle"),
    ATAK("export.atak", sitOnly = true),
    ;

    fun label(): String = LanguageText.text(titleKey)

    companion object {
        /** Missing or unknown stays CSV. A sit-only name is CSV when the log cannot use it. */
        fun fromSaved(name: String?, allowSitOnly: Boolean): LogExportKind {
            val kind = entries.firstOrNull { it.name == name } ?: LOG_CSV
            return if (!allowSitOnly && kind.sitOnly) LOG_CSV else kind
        }
    }
}

enum class LogExportRadios(val titleKey: String) {
    BOTH("export.both"),
    WIFI("export.wifi.only"),
    BLE("export.ble.only"),
    ;

    fun label(): String = LanguageText.text(titleKey)

    fun matches(kind: RadioKind): Boolean = when (this) {
        BOTH -> true
        WIFI -> kind == RadioKind.WIFI
        BLE -> kind == RadioKind.BLE
    }
}

/**
 * GPX / KML / WiGLE of GPS-tagged log radios. Pins are where this phone heard
 * each radio, not a radio fix.
 */
object GeoExport {
    enum class Format(val extension: String, val mime: String) {
        GPX("gpx", "application/gpx+xml"),
        KML("kml", "application/vnd.google-earth.kml+xml"),
        GEOJSON("geojson", "application/geo+json"),
        WIGLE("csv", "text/csv"),
    }

    fun formatOf(kind: LogExportKind): Format? = when (kind) {
        LogExportKind.LOG_CSV, LogExportKind.LOG_JSONL, LogExportKind.ATAK -> null
        LogExportKind.GPX -> Format.GPX
        LogExportKind.KML -> Format.KML
        LogExportKind.GEOJSON -> Format.GEOJSON
        LogExportKind.WIGLE -> Format.WIGLE
    }

    fun render(
        format: Format,
        radios: List<LogRadio>,
        names: Map<String, List<String>>,
        appVersion: String,
        deviceInfo: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        track: List<GpsSample> = emptyList(),
    ): String {
        val pins = radios.filter { it.hasPosition }.sortedBy { it.firstSeen }
        return when (format) {
            Format.GPX -> gpx(pins, names, appVersion, onProgress, customNames, observerNotes, track)
            Format.KML -> kml(pins, names, appVersion, onProgress, customNames, observerNotes, track)
            Format.GEOJSON -> geoJson(pins, names, appVersion, onProgress, customNames, observerNotes, track)
            Format.WIGLE -> wigleCsv(pins, appVersion, deviceInfo, onProgress)
        }
    }

    fun suggestedName(format: Format, stamp: String): String =
        "fieldwatch-map-$stamp.${format.extension}"

    private fun gpx(
        radios: List<LogRadio>,
        names: Map<String, List<String>>,
        appVersion: String,
        onProgress: (Int, Int) -> Unit,
        customNames: Map<String, String>,
        observerNotes: Map<String, String>,
        track: List<GpsSample>,
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Fieldwatch ${xml(appVersion)}"""")
        append(""" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        val n = radios.size
        radios.forEachIndexed { i, r ->
            append("""  <wpt lat="${coord(r.latitude!!)}" lon="${coord(r.longitude!!)}">""").append('\n')
            append("    <time>${iso(r.lastSeen)}</time>\n")
            append("    <name>${xml(pinName(r, customNames))}</name>\n")
            append("    <desc>${xml(pinDesc(r, names, observerNotes))}</desc>\n")
            append("    <type>${r.kind.name}</type>\n")
            append("  </wpt>\n")
            val done = i + 1
            if (done == n || done % 250 == 0) onProgress(done, n)
        }
        if (n == 0) onProgress(0, 0)
        if (track.size >= 2) {
            append("  <trk>\n    <name>Operator path</name>\n    <trkseg>\n")
            track.forEach { s ->
                append("""      <trkpt lat="${coord(s.lat)}" lon="${coord(s.lon)}">""")
                append("<time>${iso(s.at)}</time></trkpt>\n")
            }
            append("    </trkseg>\n  </trk>\n")
        }
        append("</gpx>\n")
    }

    private fun kml(
        radios: List<LogRadio>,
        names: Map<String, List<String>>,
        appVersion: String,
        onProgress: (Int, Int) -> Unit,
        customNames: Map<String, String>,
        observerNotes: Map<String, String>,
        track: List<GpsSample>,
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""").append('\n')
        append("  <name>Fieldwatch ${xml(appVersion)} export</name>\n")
        val n = radios.size
        radios.forEachIndexed { i, r ->
            append("  <Placemark>\n")
            append("    <name>${xml(pinName(r, customNames))}</name>\n")
            append("    <description>${xml(pinDesc(r, names, observerNotes))}</description>\n")
            append("    <TimeStamp><when>${iso(r.lastSeen)}</when></TimeStamp>\n")
            append("    <Point><coordinates>${coord(r.longitude!!)},${coord(r.latitude!!)}</coordinates></Point>\n")
            append("  </Placemark>\n")
            val done = i + 1
            if (done == n || done % 250 == 0) onProgress(done, n)
        }
        if (n == 0) onProgress(0, 0)
        if (track.size >= 2) {
            append("  <Placemark>\n    <name>Operator path</name>\n    <LineString><coordinates>")
            append(track.joinToString(" ") { "${coord(it.lon)},${coord(it.lat)}" })
            append("</coordinates></LineString>\n  </Placemark>\n")
        }
        append("</Document></kml>\n")
    }

    /** Same hear-points and operator path as GPX and KML. Coordinates are lon, lat. */
    private fun geoJson(
        radios: List<LogRadio>,
        names: Map<String, List<String>>,
        appVersion: String,
        onProgress: (Int, Int) -> Unit,
        customNames: Map<String, String>,
        observerNotes: Map<String, String>,
        track: List<GpsSample>,
    ): String {
        val features = JSONArray()
        val n = radios.size
        radios.forEachIndexed { i, r ->
            val props = JSONObject()
            props.put("name", pinName(r, customNames))
            props.put("kind", r.kind.name)
            props.put("mac", r.mac)
            props.put("rssi", r.rssi)
            props.put("time", iso(r.lastSeen))
            props.put("description", pinDesc(r, names, observerNotes))
            features.put(feature("Point", JSONArray().put(round6(r.longitude!!)).put(round6(r.latitude!!)), props))
            val done = i + 1
            if (done == n || done % 250 == 0) onProgress(done, n)
        }
        if (n == 0) onProgress(0, 0)
        if (track.size >= 2) {
            val coords = JSONArray()
            track.forEach { s -> coords.put(JSONArray().put(round6(s.lon)).put(round6(s.lat))) }
            val props = JSONObject()
            props.put("name", "Operator path")
            features.put(feature("LineString", coords, props))
        }
        val root = JSONObject()
        root.put("type", "FeatureCollection")
        root.put("name", "Fieldwatch $appVersion export")
        root.put("features", features)
        return root.toString()
    }

    private fun feature(geometryType: String, coordinates: JSONArray, props: JSONObject): JSONObject {
        val geom = JSONObject()
        geom.put("type", geometryType)
        geom.put("coordinates", coordinates)
        val feature = JSONObject()
        feature.put("type", "Feature")
        feature.put("geometry", geom)
        feature.put("properties", props)
        return feature
    }

    private fun round6(v: Double): Double = String.format(Locale.US, "%.6f", v).toDouble()

    private fun wigleCsv(
        radios: List<LogRadio>,
        appVersion: String,
        deviceInfo: String,
        onProgress: (Int, Int) -> Unit,
    ): String = buildString {
        append("WigleWifi-1.4,appRelease=Fieldwatch ${csvRaw(appVersion)}")
        if (deviceInfo.isNotBlank()) append(',').append(deviceInfo.filter { it != '\n' && it != '\r' })
        append('\n')
        append("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,")
        append("CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type\n")
        val n = radios.size
        radios.forEachIndexed { i, r ->
            val type = if (r.kind == RadioKind.WIFI) "WIFI" else "BLE"
            append(
                listOf(
                    r.mac.uppercase(Locale.US),
                    CsvCells.quote(if (r.hiddenSsid) "" else r.name),
                    if (r.kind == RadioKind.WIFI) "[ESS]" else "",
                    wigleTime(r.firstSeen),
                    if (r.kind == RadioKind.WIFI) r.channel else 0,
                    r.rssi,
                    coord(r.latitude!!),
                    coord(r.longitude!!),
                    0,
                    0,
                    type,
                ).joinToString(","),
            ).append('\n')
            val done = i + 1
            if (done == n || done % 250 == 0) onProgress(done, n)
        }
        if (n == 0) onProgress(0, 0)
    }

    private fun pinName(r: LogRadio, customNames: Map<String, String> = emptyMap()): String {
        val custom = customNames[r.key]?.trim()
        if (!custom.isNullOrEmpty()) return custom
        return when {
            r.hiddenSsid -> "<hidden>"
            r.name.isBlank() || r.name.equals(r.mac, ignoreCase = true) -> r.mac
            else -> r.name
        }
    }

    private fun pinDesc(
        r: LogRadio,
        names: Map<String, List<String>>,
        observerNotes: Map<String, String> = emptyMap(),
    ): String =
        buildList {
            add("${r.kind.name} ${r.mac}")
            r.vendor?.takeIf { it.isNotBlank() }?.let { add(it) }
            names[r.key]?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
            add("${r.rssi} dBm")
            if (r.channel != 0) add("ch ${r.channel}")
            observerNotes[r.key]?.trim()?.takeIf { it.isNotEmpty() }?.let { add("Observer: $it") }
            add("Heard at this phone. Not a radio fix.")
        }.joinToString(" · ")

    private fun xml(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }

    private fun csvRaw(s: String): String = s.filter { it != ',' && it != '\n' && it != '\r' }

    private fun coord(v: Double): String = String.format(Locale.US, "%.6f", v)

    private val ISO_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val WIGLE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun iso(ms: Long): String = ISO_FMT.format(Date(ms))
    private fun wigleTime(ms: Long): String = WIGLE_FMT.format(Date(ms))
}
