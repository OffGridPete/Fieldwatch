package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SitExportTest {
    private val wifi = radio(
        key = "WIFI:AA:BB:CC:DD:EE:01",
        kind = RadioKind.WIFI,
        name = "CafeWiFi",
        rssi = -50,
        channel = 6,
        gps = GpsSample(1_700_000_000_000L, 37.4419, -122.1430, -48),
    )
    private val ble = radio(
        key = "BLE:11:22:33:44:55:66",
        kind = RadioKind.BLE,
        name = "Tag",
        rssi = -70,
    )
    private val extra = setOf(wifi.key)

    @Test
    fun csvOneRowPerRadioWithCustomNameAndNote() {
        val csv = SitExport.csv(
            listOf(wifi, ble),
            LogExportRadios.BOTH,
            mapOf(wifi.key to "porch AP"),
            mapOf(wifi.key to "lot B"),
            extra,
        )
        assertTrue(csv.startsWith(SitExport.CSV_HEADER))
        assertTrue(csv.contains("porch AP"))
        assertTrue(csv.contains("lot B"))
        assertTrue(csv.contains("37.441900"))
        assertTrue(csv.contains("true"))
        assertTrue(csv.contains("Tag"))
        val data = csv.lines().filter { it.isNotBlank() }.drop(1)
        assertEquals(2, data.size)
    }

    @Test
    fun wifiOnlyDropsBle() {
        val csv = SitExport.csv(listOf(wifi, ble), LogExportRadios.WIFI, emptyMap(), emptyMap(), emptySet())
        assertTrue(csv.contains("CafeWiFi"))
        assertFalse(csv.contains("Tag"))
    }

    @Test
    fun jsonlIncludesNullLatWhenNoGps() {
        val jsonl = SitExport.jsonl(listOf(ble), LogExportRadios.BOTH, emptyMap(), emptyMap(), emptySet())
        assertTrue(jsonl.contains("\"mac\":\"11:22:33:44:55:66\""))
        assertTrue(jsonl.contains("\"lat\":null"))
        assertTrue(jsonl.contains("\"lon\":null"))
    }

    @Test
    fun mapRadiosUseLoudestTrailAndSkipUntagged() {
        val pins = SitExport.mapRadios(listOf(wifi, ble), LogExportRadios.BOTH)
        assertEquals(1, pins.size)
        assertEquals(37.4419, pins.single().latitude!!, 0.00001)
        assertEquals(-122.1430, pins.single().longitude!!, 0.00001)
    }

    @Test
    fun suggestedNameSlugsSitTitle() {
        val name = SitExport.suggestedName(LogExportKind.GPX, "Drive through Target!", "20260925-120000")
        assertEquals("fieldwatch-sit-drive-through-target-20260925-120000.gpx", name)
    }

    private fun radio(
        key: String,
        kind: RadioKind,
        name: String,
        rssi: Int,
        channel: Int = 0,
        gps: GpsSample? = null,
    ) = Sighting(
        key = key,
        kind = kind,
        mac = key.substringAfter(':'),
        name = name,
        rssi = rssi,
        rssiMin = rssi,
        rssiMax = rssi,
        channel = channel,
        frequencyMhz = if (kind == RadioKind.WIFI) 2437 else 2402,
        vendor = null,
        randomized = kind == RadioKind.BLE,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1_700_000_000_000L,
        lastSeen = 1_700_000_000_000L,
        hitCount = 4,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        gpsTrail = listOfNotNull(gps),
    )
}
