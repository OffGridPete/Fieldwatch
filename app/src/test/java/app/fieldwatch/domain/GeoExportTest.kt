package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoExportTest {

    private fun radio(
        kind: RadioKind = RadioKind.WIFI,
        mac: String = "00:11:22:33:44:55",
        name: String = "CafeWiFi",
        lat: Double? = 48.8566,
        lon: Double? = 2.3522,
        firstSeen: Long = 1_700_000_000_000L,
        lastSeen: Long = 1_700_000_060_000L,
        rssi: Int = -62,
        channel: Int = 6,
        security: String? = "RSN PSK CCMP (group CCMP)",
        vendor: String? = "Cisco",
        hidden: Boolean = false,
    ) = LogRadio(
        kind = kind,
        mac = mac,
        name = name,
        vendor = vendor,
        manufacturerId = null,
        manufacturerDataHex = "",
        serviceUuids = emptyList(),
        vendorIeOuis = emptyList(),
        randomized = false,
        hiddenSsid = hidden,
        rssi = rssi,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        hits = 3,
        channel = channel,
        frequencyMhz = 2437,
        security = security,
        latitude = lat,
        longitude = lon,
    )

    @Test
    fun radiosWithoutPositionAreDropped() {
        val radios = listOf(radio(lat = null), radio())
        GeoExport.Format.entries.forEach { fmt ->
            val out = GeoExport.render(fmt, radios, emptyMap(), "9.9", "")
            // Exactly one pin/row, from the positioned radio.
            when (fmt) {
                GeoExport.Format.GPX -> assertEquals(1, Regex("<wpt ").findAll(out).count())
                GeoExport.Format.KML -> assertEquals(1, Regex("<Placemark>").findAll(out).count())
                GeoExport.Format.WIGLE -> assertEquals(3, out.lines().count { it.isNotBlank() })
            }
        }
    }

    @Test
    fun gpxWaypointsCarryNameTimeAndSignature() {
        val out = GeoExport.render(
            GeoExport.Format.GPX,
            listOf(radio()),
            mapOf("WIFI:00:11:22:33:44:55" to listOf("DJI drone")),
            "9.9",
            "",
        )
        assertTrue(out.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(out.contains("""<wpt lat="48.856600" lon="2.352200">"""))
        assertTrue(out.contains("<name>CafeWiFi</name>"))
        assertTrue(out.contains("<time>2023-11-14T22:14:20Z</time>"))
        assertTrue(out.contains("DJI drone"))
        assertTrue(out.contains("WIFI 00:11:22:33:44:55"))
        assertTrue(out.contains("<type>WIFI</type>"))
        assertTrue(out.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun gpxEscapesXmlInNames() {
        val out = GeoExport.render(
            GeoExport.Format.GPX,
            listOf(radio(name = "Joe's <Café> & \"Bar\"")),
            emptyMap(),
            "9.9",
            "",
        )
        assertTrue(out.contains("Joe&apos;s &lt;Café&gt; &amp; &quot;Bar&quot;"))
        assertFalse(out.contains("<Café>"))
    }

    @Test
    fun kmlPlacemarkUsesLonLatOrder() {
        val out = GeoExport.render(
            GeoExport.Format.KML,
            listOf(radio()),
            emptyMap(),
            "9.9",
            "",
        )
        assertTrue(out.contains("xmlns=\"http://www.opengis.net/kml/2.2\""))
        assertTrue(out.contains("<coordinates>2.352200,48.856600</coordinates>"))
        assertTrue(out.contains("<when>2023-11-14T22:14:20Z</when>"))
    }

    @Test
    fun wigleCsvFollowsTheSpec() {
        val ble = radio(
            kind = RadioKind.BLE,
            mac = "AA:BB:CC:DD:EE:01",
            name = "AirTag",
            channel = 0,
            security = null,
            firstSeen = 1_700_000_000_000L,
            lastSeen = 1_700_000_060_000L,
        )
        val out = GeoExport.render(
            GeoExport.Format.WIGLE,
            listOf(radio(), ble),
            emptyMap(),
            "9.9",
            "model=Pixel 7,release=14",
        )
        val lines = out.lines().filter { it.isNotBlank() }
        assertEquals(
            "WigleWifi-1.4,appRelease=Fieldwatch 9.9,model=Pixel 7,release=14",
            lines[0],
        )
        assertEquals(
            "MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude," +
                "CurrentLongitude,AltitudeMeters,AccuracyMeters,Type",
            lines[1],
        )
        // Sorted by firstSeen; both radios share the same firstSeen so order is stable.
        assertEquals(
            "00:11:22:33:44:55,CafeWiFi,[WPA2-PSK-CCMP][ESS],2023-11-14 22:13:20,6,-62,48.856600,2.352200,0,0,WIFI",
            lines[2],
        )
        assertEquals(
            "AA:BB:CC:DD:EE:01,AirTag,,2023-11-14 22:13:20,0,-62,48.856600,2.352200,0,0,BLE",
            lines[3],
        )
    }

    @Test
    fun wigleCsvQuotesCommasAndQuotes() {
        val out = GeoExport.render(
            GeoExport.Format.WIGLE,
            listOf(radio(name = "Joe's \"Wi-Fi\", Café")),
            emptyMap(),
            "9.9",
            "",
        )
        assertTrue(out.contains("\"Joe's \"\"Wi-Fi\"\", Café\""))
    }

    @Test
    fun wigleAuthMapsSummariesAndDefaults() {
        assertEquals("[ESS]", GeoExport.wigleAuth(null))
        assertEquals("[ESS]", GeoExport.wigleAuth("  "))
        assertEquals("[WPA2-PSK-CCMP][WPA-CCMP][ESS]",
            GeoExport.wigleAuth("RSN PSK CCMP (group CCMP) · WPA CCMP"))
        assertEquals("[WPA2-SAE-CCMP][ESS]",
            GeoExport.wigleAuth("RSN SAE CCMP (group CCMP)"))
        // Raw ScanResult capabilities pass through untouched.
        assertEquals("[WPA2-PSK-CCMP][ESS]",
            GeoExport.wigleAuth("[WPA2-PSK-CCMP][ESS]"))
    }

    @Test
    fun hiddenSsidExportsBlankSsidAndHiddenName() {
        val gpx = GeoExport.render(
            GeoExport.Format.GPX,
            listOf(radio(name = "", hidden = true)),
            emptyMap(),
            "9.9",
            "",
        )
        assertTrue(gpx.contains("<name>&lt;hidden&gt;</name>"))
        val wigle = GeoExport.render(
            GeoExport.Format.WIGLE,
            listOf(radio(name = "", hidden = true)),
            emptyMap(),
            "9.9",
            "",
        )
        val row = wigle.lines().filter { it.isNotBlank() }[2]
        assertTrue(row.startsWith("00:11:22:33:44:55,,"))
    }
}
