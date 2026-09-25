package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoExportTest {
    private val cafe = LogRadio(
        kind = RadioKind.WIFI,
        mac = "AA:BB:CC:DD:EE:01",
        name = "CafeWiFi",
        vendor = "Acme",
        manufacturerId = null,
        manufacturerDataHex = "",
        serviceUuids = emptyList(),
        vendorIeOuis = emptyList(),
        randomized = false,
        hiddenSsid = false,
        rssi = -60,
        firstSeen = 1_700_000_000_000L,
        lastSeen = 1_700_000_000_000L,
        hits = 3,
        channel = 6,
        frequencyMhz = 2437,
        latitude = 37.4419,
        longitude = -122.1430,
    )
    private val tag = cafe.copy(
        kind = RadioKind.BLE,
        mac = "11:22:33:44:55:66",
        name = "Tag",
        channel = 0,
        frequencyMhz = 2402,
        latitude = 37.4420,
        longitude = -122.1431,
    )
    private val noGps = cafe.copy(mac = "00:00:00:00:00:99", latitude = null, longitude = null)

    @Test
    fun gpxDropsUntaggedAndMarksPhoneHear() {
        val xml = GeoExport.render(
            GeoExport.Format.GPX,
            listOf(cafe, noGps, tag),
            emptyMap(),
            "1.1.6",
            "",
        )
        assertTrue(xml.contains("<gpx version=\"1.1\""))
        assertTrue(xml.contains("lat=\"37.441900\""))
        assertTrue(xml.contains("lon=\"-122.143000\""))
        assertTrue(xml.contains("<name>CafeWiFi</name>"))
        assertTrue(xml.contains("Heard at this phone. Not a radio fix."))
        assertFalse(xml.contains("00:00:00:00:00:99"))
        assertEquals(2, xml.split("<wpt ").size - 1)
    }

    @Test
    fun kmlUsesLonLatOrder() {
        val xml = GeoExport.render(
            GeoExport.Format.KML,
            listOf(cafe),
            emptyMap(),
            "1.1.6",
            "",
        )
        assertTrue(xml.contains("<coordinates>-122.143000,37.441900</coordinates>"))
        assertTrue(xml.contains("Heard at this phone"))
    }

    @Test
    fun wigleHeaderAndWifiBleTypes() {
        val csv = GeoExport.render(
            GeoExport.Format.WIGLE,
            listOf(cafe, tag),
            emptyMap(),
            "1.1.6",
            "model=A54,release=16",
        )
        val lines = csv.lines().filter { it.isNotBlank() }
        assertTrue(lines[0].startsWith("WigleWifi-1.4,appRelease=Fieldwatch 1.1.6,model=A54"))
        assertTrue(lines[1].startsWith("MAC,SSID,AuthMode,"))
        assertTrue(lines[2].startsWith("AA:BB:CC:DD:EE:01,CafeWiFi,[ESS],"))
        assertTrue(lines[2].endsWith(",WIFI"))
        assertTrue(lines.any { it.startsWith("11:22:33:44:55:66,Tag,,") && it.endsWith(",BLE") })
    }
}
