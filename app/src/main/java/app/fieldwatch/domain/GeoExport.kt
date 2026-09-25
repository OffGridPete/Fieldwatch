package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Share/save format for Reports → Log. Disk is always CSV; JSON lines is an export. */
enum class LogExportKind(val label: String) {
    LOG_CSV("Log file — CSV"),
    LOG_JSONL("Log file — JSON lines"),
    GPX("GPX — GPS Exchange"),
    KML("KML — Google Earth"),
    WIGLE("WiGLE CSV — wigle.net"),
}

enum class LogExportRadios(val label: String) {
    BOTH("Both radios"),
    WIFI("Wi-Fi only"),
    BLE("BLE only"),
    ;

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
        WIGLE("csv", "text/csv"),
    }

    fun formatOf(kind: LogExportKind): Format? = when (kind) {
        LogExportKind.LOG_CSV, LogExportKind.LOG_JSONL -> null
        LogExportKind.GPX -> Format.GPX
        LogExportKind.KML -> Format.KML
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
    ): String {
        val pins = radios.filter { it.hasPosition }.sortedBy { it.firstSeen }
        return when (format) {
            Format.GPX -> gpx(pins, names, appVersion, onProgress, customNames)
            Format.KML -> kml(pins, names, appVersion, onProgress, customNames)
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
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Fieldwatch ${xml(appVersion)}"""")
        append(""" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        val n = radios.size
        radios.forEachIndexed { i, r ->
            append("""  <wpt lat="${coord(r.latitude!!)}" lon="${coord(r.longitude!!)}">""").append('\n')
            append("    <time>${iso(r.lastSeen)}</time>\n")
            append("    <name>${xml(pinName(r, customNames))}</name>\n")
            append("    <desc>${xml(pinDesc(r, names))}</desc>\n")
            append("    <type>${r.kind.name}</type>\n")
            append("  </wpt>\n")
            val done = i + 1
            if (done == n || done % 250 == 0) onProgress(done, n)
        }
        if (n == 0) onProgress(0, 0)
        append("</gpx>\n")
    }

    private fun kml(
        radios: List<LogRadio>,
        names: Map<String, List<String>>,
        appVersion: String,
        onProgress: (Int, Int) -> Unit,
        customNames: Map<String, String>,
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""").append('\n')
        append("  <name>Fieldwatch ${xml(appVersion)} export</name>\n")
        val n = radios.size
        radios.forEachIndexed { i, r ->
            append("  <Placemark>\n")
            append("    <name>${xml(pinName(r, customNames))}</name>\n")
            append("    <description>${xml(pinDesc(r, names))}</description>\n")
            append("    <TimeStamp><when>${iso(r.lastSeen)}</when></TimeStamp>\n")
            append("    <Point><coordinates>${coord(r.longitude!!)},${coord(r.latitude!!)}</coordinates></Point>\n")
            append("  </Placemark>\n")
            val done = i + 1
            if (done == n || done % 250 == 0) onProgress(done, n)
        }
        if (n == 0) onProgress(0, 0)
        append("</Document></kml>\n")
    }

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
                    csvField(if (r.hiddenSsid) "" else r.name),
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

    private fun pinDesc(r: LogRadio, names: Map<String, List<String>>): String =
        buildList {
            add("${r.kind.name} ${r.mac}")
            r.vendor?.takeIf { it.isNotBlank() }?.let { add(it) }
            names[r.key]?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
            add("${r.rssi} dBm")
            if (r.channel != 0) add("ch ${r.channel}")
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

    private fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) {
            "\"${s.replace("\"", "\"\"")}\""
        } else s

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
