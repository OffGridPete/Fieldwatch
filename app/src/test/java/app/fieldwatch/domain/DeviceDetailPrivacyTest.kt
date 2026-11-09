package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDetailPrivacyTest {
    private val wpsBody =
        "10470010AABBCCDDEEFF00112233445566778899" + "10420003313233"
    private val serviceBody = "0A0B0C0D"
    private val mfgBody = "01020304"
    private val rawBody = "DEADBEEF"

    @Test
    fun privacyModeMasksRawBytesAndKeepsTheCount() {
        val text = DeviceDetailText.build(radio(), emptyList(), now = 1L, demo = true)
        assertEquals(27, wpsBody.length / 2)
        val vendor = line(text, "Vendor OUI")
        assertTrue(vendor, vendor.contains("00:50:F2"))
        assertTrue(vendor, vendor.contains("type 4"))
        assertTrue(vendor, vendor.contains("WPS (Wi-Fi Protected Setup)."))
        assertFalse(vendor, vendor.contains("MICROSOFT"))
        assertTrue(vendor, vendor.contains("27 bytes, masked"))
        assertFalse(vendor, vendor.contains("AA BB"))
        assertFalse(vendor, vendor.contains("31 32 33"))
        assertFalse(text.contains(wpsBody))

        val service = line(text, "Service data")
        assertTrue(service, service.contains("4 bytes, masked"))
        assertFalse(service, service.contains("0A 0B"))

        val empty = text.lines().first { it.startsWith("Service data") && it.contains("EMPTY") }
        assertTrue(empty, empty.endsWith("(empty)"))
        assertFalse(empty, empty.contains("masked"))

        val payload = line(text, "Raw payload")
        assertTrue(payload, payload.contains("Raw payload (4 bytes)"))
        assertTrue(payload, payload.endsWith("masked"))
        assertFalse(payload, payload.contains("01 02 03 04"))

        val raw = line(text, "Raw advertisement")
        assertTrue(raw, raw.contains("4 bytes, masked"))
        assertFalse(raw, raw.contains("DE AD BE EF"))
    }

    @Test
    fun privacyModeOffKeepsTheHex() {
        val text = DeviceDetailText.build(radio(), emptyList(), now = 1L, demo = false)
        assertTrue(text.contains("AA BB CC DD EE FF 00 11 22 33 44 55 66 77 88 99"))
        assertTrue(text.contains("31 32 33"))
        assertTrue(text.contains("0A 0B 0C 0D"))
        assertTrue(text.contains("01 02 03 04"))
        assertTrue(text.contains("DE AD BE EF"))
        assertFalse(text.contains("bytes, masked"))
    }

    private fun line(text: String, prefix: String): String =
        text.lines().first { it.startsWith(prefix) }

    private fun radio() = Sighting(
        key = "WIFI:02:00:00:00:00:01",
        kind = RadioKind.BLE,
        mac = "02:00:00:00:00:01",
        name = "",
        rssi = -40,
        rssiMin = -40,
        rssiMax = -40,
        channel = 6,
        frequencyMhz = 2437,
        vendor = null,
        randomized = true,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = 0x004C,
        manufacturerDataHex = mfgBody,
        rawHex = rawBody,
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(
            mfgRecords = listOf(MfgRecord(0x004C, mfgBody)),
            vendorIes = listOf(VendorIeRecord("00:50:F2", 4, wpsBody)),
            serviceData = listOf(
                ServiceDataRecord("FE2C", serviceBody),
                ServiceDataRecord("EMPTY", ""),
            ),
        ),
    )
}
