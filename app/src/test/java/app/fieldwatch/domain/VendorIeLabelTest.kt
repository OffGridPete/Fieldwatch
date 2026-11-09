package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorIeLabelTest {
    @Test
    fun wmmParametersAreNotAMicrosoftDevice() {
        val ie = VendorIeRecord(
            "00:50:F2",
            2,
            "0101810003A4000027A4000042435E0062322F00",
        )
        val text = VendorIeLabel.element(ie)!!
        assertTrue(text, text.startsWith("WMM parameters (Wi-Fi Multimedia)."))
        assertTrue(text, text.contains("not a Microsoft device"))
        assertFalse(text, text.contains("MICROSOFT"))
    }

    @Test
    fun wmmInformationAndBareWmm() {
        assertTrue(
            VendorIeLabel.element(VendorIeRecord("00:50:f2", 2, "00"))!!
                .startsWith("WMM information"),
        )
        assertTrue(
            VendorIeLabel.element(VendorIeRecord("0050F2", 2, ""))!!
                .startsWith("WMM (Wi-Fi Multimedia)."),
        )
    }

    @Test
    fun wpaAndWpsStayStandardElements() {
        assertTrue(VendorIeLabel.element(VendorIeRecord("00:50:F2", 1, "0100"))!!.startsWith("WPA."))
        assertTrue(
            VendorIeLabel.element(VendorIeRecord("00:50:F2", 4, "1047"))!!
                .startsWith("WPS (Wi-Fi Protected Setup)."),
        )
    }

    @Test
    fun wifiAllianceTypes() {
        assertEquals(
            "Wi-Fi Direct. Standard element from the Wi-Fi Alliance, not one product.",
            VendorIeLabel.element(VendorIeRecord("50:6F:9A", 0x09, "")),
        )
        assertTrue(VendorIeLabel.element(VendorIeRecord("50:6F:9A", 0x0A, ""))!!.startsWith("Miracast"))
        assertTrue(VendorIeLabel.element(VendorIeRecord("50:6F:9A", 0x10, ""))!!.startsWith("Hotspot 2.0"))
        assertTrue(VendorIeLabel.element(VendorIeRecord("50:6F:9A", 0x16, ""))!!.startsWith("MBO-OCE"))
        assertTrue(VendorIeLabel.element(VendorIeRecord("50:6F:9A", 0x1B, ""))!!.startsWith("EasyMesh"))
        assertTrue(VendorIeLabel.element(VendorIeRecord("50:6F:9A", 0x1C, ""))!!.startsWith("OWE transition"))
    }

    @Test
    fun otherOuiAndUnknownTypeKeepTheCompanyLine() {
        assertNull(VendorIeLabel.element(VendorIeRecord("00:10:18", 2, "01")))
        assertNull(VendorIeLabel.element(VendorIeRecord("00:50:F2", 8, "01")))
        assertNull(VendorIeLabel.element(VendorIeRecord("50:6F:9A", 0x13, "")))
        assertNull(VendorIeLabel.element(VendorIeRecord("00:50:F2", -1, "")))
        val lead = VendorIeLabel.lead(
            VendorIeRecord("00:10:18", 2, "01"),
            "Broadcom",
            "Unknown IEEE OUI",
            "— extra AP information element, not the SSID.",
        )
        assertEquals("Broadcom — extra AP information element, not the SSID.", lead)
    }

    @Test
    fun shareTextNamesWmmParameters() {
        val wmm = "0101810003A4000027A4000042435E0062322F00"
        val text = DeviceDetailText.build(
            Sighting(
                key = "WIFI:02:00:00:00:00:02",
                kind = RadioKind.WIFI,
                mac = "02:00:00:00:00:02",
                name = "Cafe",
                rssi = -40,
                rssiMin = -40,
                rssiMax = -40,
                channel = 6,
                frequencyMhz = 2437,
                vendor = null,
                randomized = true,
                hiddenSsid = false,
                serviceUuids = emptyList(),
                manufacturerId = null,
                manufacturerDataHex = "",
                rawHex = "",
                extras = "",
                firstSeen = 1L,
                lastSeen = 1L,
                hitCount = 1,
                fleetIds = emptySet(),
                rssiHistory = emptyList(),
                presence = emptyList(),
                facts = RadioFacts(vendorIes = listOf(VendorIeRecord("00:50:F2", 2, wmm))),
            ),
            emptyList(),
            now = 1L,
        )
        val line = text.lines().first { it.startsWith("Vendor OUI") }
        assertTrue(line, line.contains("type 2"))
        assertTrue(line, line.contains("WMM parameters (Wi-Fi Multimedia)."))
        assertFalse(line, line.contains("MICROSOFT"))
        assertTrue(line, line.contains("01 01 81 00"))
    }
}
