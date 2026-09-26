package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvPayloadDecoderTest {
    private fun bleSighting(mfgHex: String): Sighting = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:99",
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:99",
        name = "",
        rssi = -50,
        rssiMin = -50,
        rssiMax = -50,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = false,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = 0x004C,
        manufacturerDataHex = mfgHex,
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(mfgRecords = listOf(MfgRecord(0x004C, mfgHex))),
    )

    @Test
    fun appleIBeaconDecodesUuidMajorMinorTx() {
        // TLV 02/15 + 16-byte UUID + major + minor + calibrated TX.
        val mfg = "0215" + "E2C56DB5DFFB48D2B060D0F5A71096E0" + "0001" + "0002" + "C5"
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, mfg))
        val byLabel = fields.associate { it.label to it.value }
        assertEquals("0x02 · iBeacon", byLabel["Apple Continuity type"])
        assertEquals("e2c56db5-dffb-48d2-b060-d0f5a71096e0", byLabel["iBeacon UUID"])
        assertEquals("1 / 2", byLabel["iBeacon major / minor"])
        assertTrue(byLabel.getValue("iBeacon calibrated TX").startsWith("-59"))
    }

    @Test
    fun appleProximityPairingNamesAirPods() {
        // TLV 07, prefix 01, model 0F20 (AirPods 2nd gen), status 51, batt 98, case byte 11.
        val mfg = "07" + "07" + "010F2051" + "98" + "11" + "00"
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, mfg))
        val byLabel = fields.associate { it.label to it.value }
        assertEquals("AirPods (2nd generation)", byLabel["Product"])
        assertEquals("80% / 90%", byLabel["Battery (left / right)"])
        assertEquals("10%", byLabel["Case battery"])
        assertEquals("case", byLabel["Charging"])
    }

    @Test
    fun appleFindMyLabelsOfflineFinding() {
        val mfg = "12" + "0A" + "00".repeat(10)
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, mfg))
        assertTrue(fields.any { it.label == "Find My / Offline Finding" })
    }

    @Test
    fun unknownCompanyYieldsNoFields() {
        assertTrue(AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x9999, "AABB")).isEmpty())
        assertTrue(AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, "XYZ")).isEmpty())
    }

    @Test
    fun fastPairThreeByteModelIdIsPairing() {
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FE2C", "000006"))
        val byLabel = fields.associate { it.label to it.value }
        assertTrue(byLabel.getValue("Google Fast Pair").contains("pairing mode"))
        assertEquals("Google Pixel Buds  (0x000006)", byLabel["Model ID"])
    }

    @Test
    fun fastPairLongerPayloadIsAccountKeyBloom() {
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FE2C", "01020304050607"))
        assertTrue(fields.single().value.contains("Already paired"))
    }

    @Test
    fun eddystoneUidSplitsNamespaceAndInstance() {
        val bytes = "00" + "C5" + "00112233445566778899" + "AABBCCDDEEFF" + "0000"
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", bytes))
        val byLabel = fields.associate { it.label to it.value }
        assertEquals("00112233445566778899", byLabel["Eddystone-UID namespace"])
        assertEquals("AABBCCDDEEFF", byLabel["Eddystone-UID instance"])
    }

    @Test
    fun findHubFrameIsNotEddystone() {
        val eid = "11".repeat(20)
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", "41$eid" + "00"))
        assertEquals("separated (unwanted-tracking mode)", fields.first { it.label == "Find Hub" }.value)
        assertTrue(fields.none { it.label.startsWith("Eddystone") })
    }

    @Test
    fun eddystoneUrlExpandsSchemeAndSuffix() {
        // frame 10, tx C5, scheme 01 (https://www.), "example", 07 (.com)
        val bytes = "10" + "C5" + "01" + "example".toByteArray().toHexUpper() + "07"
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", bytes))
        assertEquals("https://www.example.com", fields.single().value)
    }

    @Test
    fun microsoftCdpNamesDeviceType() {
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x0006, "0109"))
        assertTrue(fields.single().value.contains("Windows desktop"))
    }

    @Test
    fun roleHintsFlagIBeaconAndTeslaKey() {
        val ibeacon = "0215" + "E2C56DB5DFFB48D2B060D0F5A71096E0" + "00010002" + "C5"
        val hints = AdvPayloadDecoder.roleHints(bleSighting(ibeacon))
        assertTrue(hints.any { it.label == "an iBeacon" })

        // Tesla prefix carries the "0215" TLV header; the body still needs
        // major/minor/tx (5 bytes) to reach the declared iBeacon length.
        val tesla = bleSighting(DefaultCatalog.TESLA_IBEACON_MFG_PREFIX + "00010002C5")
        assertTrue(AdvPayloadDecoder.roleHints(tesla).any { it.label.contains("Tesla") })
    }
}
