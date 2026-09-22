package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Serializes GPS-tagged log radios to GPX / KML / WiGLE CSV. Pure string work so
 * unit tests pin the exact output. Points mark where THIS PHONE was when it
 * heard each radio — not fixes on the radios themselves.
 */
object GeoExport {

    enum class Format(val extension: String, val mime: String, val label: String) {
        GPX("gpx", "application/gpx+xml", "GPX"),
        KML("kml", "application/vnd.google-earth.kml+xml", "KML"),
        WIGLE("wigle.csv", "text/csv", "WiGLE CSV"),
    }

    /**
     * @param names signature names keyed by [LogRadio.key]; ignored by WiGLE (fixed schema)
     * @param deviceInfo WiGLE header tail, e.g. "model=Pixel 7,release=14" (Build.* on Android)
     */
    fun render(
        format: Format,
        radios: List<LogRadio>,
        names: Map<String, List<String>>,
        appVersion: String,
        deviceInfo: String,
    ): String {
        val pins = radios.filter { it.hasPosition }.sortedBy { it.firstSeen }
        return when (format) {
            Format.GPX -> gpx(pins, names, appVersion)
            Format.KML -> kml(pins, names, appVersion)
            Format.WIGLE -> wigleCsv(pins, appVersion, deviceInfo)
        }
    }

    fun suggestedName(format: Format, stamp: String): String =
        "fieldwatch-map-$stamp.${format.extension}"

    private fun gpx(
        radios: List<LogRadio>,
        names: Map<String, List<String>>,
        appVersion: String,
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="Fieldwatch ${xml(appVersion)}"""")
            .append(""" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        radios.forEach { r ->
            append("""  <wpt lat="${coord(r.latitude!!)}" lon="${coord(r.longitude!!)}">""").append('\n')
            append("    <time>${iso(r.lastSeen)}</time>\n")
            append("    <name>${xml(pinName(r))}</name>\n")
            append("    <desc>${xml(pinDesc(r, names))}</desc>\n")
            append("    <type>${r.kind.name}</type>\n")
            append("  </wpt>\n")
        }
        append("</gpx>\n")
    }

    private fun kml(
        radios: List<LogRadio>,
        names: Map<String, List<String>>,
        appVersion: String,
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""").append('\n')
        append("  <name>Fieldwatch ${xml(appVersion)} export</name>\n")
        radios.forEach { r ->
            append("  <Placemark>\n")
            append("    <name>${xml(pinName(r))}</name>\n")
            append("    <description>${xml(pinDesc(r, names))}</description>\n")
            append("    <TimeStamp><when>${iso(r.lastSeen)}</when></TimeStamp>\n")
            append("    <Point><coordinates>${coord(r.longitude!!)},${coord(r.latitude!!)}</coordinates></Point>\n")
            append("  </Placemark>\n")
        }
        append("</Document></kml>\n")
    }

    /**
     * WiGLE wardriving upload format (WigleWifi-1.4). Column order is fixed by
     * the spec; BLE rows use type BLE and channel 0 (BT channel has no meaning
     * in the WiGLE schema). AuthMode is rebuilt from the observed RSN/WPA
     * summary — "[WPA2-PSK-CCMP][ESS]" style, "[ESS]" when unknown.
     */
    private fun wigleCsv(radios: List<LogRadio>, appVersion: String, deviceInfo: String): String =
        buildString {
            append("WigleWifi-1.4,appRelease=Fieldwatch ${csvRaw(appVersion)}")
            // deviceInfo is a trusted "k=v,k=v" tail — commas are the field split.
            if (deviceInfo.isNotBlank()) append(',').append(deviceInfo.filter { it != '\n' && it != '\r' })
            append('\n')
            append("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,")
            append("CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type\n")
            radios.forEach { r ->
                val type = if (r.kind == RadioKind.WIFI) "WIFI" else "BLE"
                append(
                    listOf(
                        r.mac.uppercase(Locale.US),
                        csvField(if (r.hiddenSsid) "" else r.name),
                        if (r.kind == RadioKind.WIFI) wigleAuth(r.security) else "",
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
            }
        }

    /** "RSN PSK CCMP (group CCMP) · WPA CCMP" → "[WPA2-PSK-CCMP][WPA-CCMP][ESS]" */
    internal fun wigleAuth(security: String?): String {
        val raw = security?.trim().orEmpty()
        if (raw.isEmpty()) return "[ESS]"
        if (raw.startsWith("[")) return raw
        val parts = raw.split("·").map { part ->
            part.substringBefore("(").trim()
                .replace(Regex("^RSN\\b"), "WPA2")
                .replace(Regex("\\s+"), "-")
                .filter { it.isLetterOrDigit() || it == '-' || it == '.' }
        }.filter { it.isNotBlank() }
        return parts.joinToString("") { "[$it]" }.ifBlank { "" } + "[ESS]"
    }

    private fun pinName(r: LogRadio): String = when {
        r.hiddenSsid -> "<hidden>"
        r.name.isBlank() || r.name.equals(r.mac, ignoreCase = true) -> r.mac
        else -> r.name
    }

    private fun pinDesc(r: LogRadio, names: Map<String, List<String>>): String =
        buildList {
            add("${r.kind.name} ${r.mac}")
            r.vendor?.takeIf { it.isNotBlank() }?.let { add(it) }
            names[r.key]?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
            add("${r.rssi} dBm")
            if (r.channel != 0) add("ch ${r.channel}")
            r.security?.takeIf { it.isNotBlank() }?.let { add(it) }
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
