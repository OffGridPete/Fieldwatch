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
    private val fleets = listOf(
        Fleet(id = "fleet-flock-cameras", name = "Flock Safety Cameras", attentionNote = "ALPR"),
        Fleet(id = "fleet-ring", name = "Ring"),
    )

    @Test
    fun csvOneRowPerRadioWithCustomNameAndNote() {
        val csv = SitExport.csv(
            listOf(wifi, ble),
            LogExportRadios.BOTH,
            mapOf(wifi.key to "porch AP"),
            mapOf(wifi.key to "lot B"),
            extra,
            fleets,
        )
        assertTrue(csv.startsWith(SitExport.CSV_HEADER))
        assertTrue(csv.contains("porch AP"))
        assertTrue(csv.contains("lot B"))
        assertTrue(csv.contains("37.441900"))
        assertTrue(csv.contains("true"))
        assertTrue(csv.contains("Tag"))
        assertTrue(csv.contains("Flock Safety Cameras; Ring"))
        assertTrue(csv.contains("Flock Safety Cameras"))
        val data = csv.lines().filter { it.isNotBlank() }.drop(1)
        assertEquals(2, data.size)
        val wifiLine = data.first { it.contains("CafeWiFi") }
        assertTrue(wifiLine, wifiLine.contains("Flock Safety Cameras; Ring"))
        val bleLine = data.first { it.contains("Tag") }
        assertTrue(bleLine.endsWith(",false"))
        assertTrue(SitExport.CSV_HEADER.endsWith(",mine"))
        val marked = SitExport.csv(
            listOf(wifi),
            LogExportRadios.BOTH,
            emptyMap(),
            emptyMap(),
            extra,
            fleets,
            mineKeys = setOf(wifi.key),
        )
        assertTrue(marked.lines().first { it.contains("CafeWiFi") }.endsWith(",true"))
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
        assertTrue(jsonl.contains("\"signatures\":\"\""))
        assertTrue(jsonl.contains("\"extra_attention_families\":\"\""))
    }

    @Test
    fun csvAndJsonlListSignaturesAndAttentionFamilies() {
        val jsonl = SitExport.jsonl(
            listOf(wifi),
            LogExportRadios.BOTH,
            emptyMap(),
            emptyMap(),
            extra,
            fleets,
        )
        assertTrue(jsonl.contains("\"signatures\":\"Flock Safety Cameras; Ring\""))
        assertTrue(jsonl.contains("\"extra_attention_families\":\"Flock Safety Cameras\""))
        assertTrue(jsonl.contains("\"extra_attention\":true"))
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
        fleetIds = if (key.startsWith("WIFI")) setOf("fleet-ring", "fleet-flock-cameras") else emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        gpsTrail = listOfNotNull(gps),
    )
}
