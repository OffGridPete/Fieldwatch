package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrenchRidTest {
    @Test
    fun officialExampleFillsAircraftAndTakeoff() {
        val serial = "0".repeat(23) + "1"
        val plate = plate(example(serial))
        assertEquals(1, plate.version)
        assertEquals("ABC", plate.trigram)
        assertEquals("DEF", plate.model)
        assertEquals(serial, plate.serial)
        assertEquals("ABCDEF$serial", plate.frenchId)
        val loc = plate.location
        assertEquals("ABCDEF$serial", loc.uasId)
        assertNull(loc.aircraft)
        assertFalse(loc.basicId)
        assertEquals(48.15278, loc.lat!!, 1e-5)
        assertEquals(179.12345, loc.lon!!, 1e-5)
        assertEquals(100.0, loc.alt!!, 0.0)
        assertEquals(48.15278, loc.opLat!!, 1e-5)
        assertEquals(179.12345, loc.opLon!!, 1e-5)
        assertEquals(OperatorLoc.TAKEOFF, loc.opKind)
        assertEquals(10.0, loc.speedMps!!, 0.0)
        assertEquals(90.0, loc.headingDeg!!, 0.0)
        assertNull(plate.heightM)
        val hidden = plate.identifier(demo = true)!!
        assertTrue(hidden.contains("ABC"))
        assertTrue(hidden.contains("DEF"))
        assertTrue(hidden.contains("masked"))
        assertFalse(hidden.contains(serial))
        assertTrue(plate.identifier(demo = false)!!.contains(serial))
        assertEquals(48.15278, loc.pin()!!.first, 1e-5)
        assertTrue(plate.takeoffHere())
        assertEquals("Takeoff position (French plate)", plate.operatorLabel(OperatorLoc.LIVE))
    }

    @Test
    fun negativeLongitudeAndBounds() {
        val west = plate(tlv(0x05, "FEEEADE7") + tlv(0x04, "004979AE")).location
        assertEquals(-179.12345, west.lon!!, 1e-5)
        assertEquals(48.15278, west.lat!!, 1e-5)
        val edge = plate(tlv(0x04, be32(90 * SCALE)) + tlv(0x05, be32(-180 * SCALE))).location
        assertEquals(-180.0, edge.lon!!, 1e-6)
        assertTrue(PayloadLocation.validCoord(edge.lat, edge.lon))
        val tooFar = plate(tlv(0x04, be32(0)) + tlv(0x05, be32(18000001))).location
        assertNull(tooFar.pin())
    }

    @Test
    fun zeroZeroDoesNotPinOrRelabel() {
        val plate = plate(tlv(0x04, "00000000") + tlv(0x05, "00000000") + tlv(0x08, "00000000") + tlv(0x09, "00000000"))
        assertNull(plate.location.pin())
        assertNull(plate.location.opKind)
        assertNull(plate.location.opLat)
        val pilot = PayloadLocation(opLat = 10.0, opLon = 20.0, opKind = OperatorLoc.LIVE, lat = 12.0, lon = 34.0)
        val kept = plate.location.mergeSticky(pilot)
        assertEquals(OperatorLoc.LIVE, kept.opKind)
        assertEquals(10.0, kept.opLat!!, 0.0)
        assertEquals(12.0, kept.lat!!, 0.0)
        assertEquals("Pilot", plate.operatorLabel(OperatorLoc.LIVE))
    }

    @Test
    fun ctaOnlyNamesTheMakerAndDoesNotPin() {
        val plate = plate(tlv(0x01, "01") + tlv(0x03, ascii("1668B......")))
        assertTrue(plate.location.uasId!!.startsWith("1668B"))
        assertEquals("Skydio", plate.location.aircraft)
        assertNull(plate.location.pin())
        assertNull(plate.identifier(false))
        assertFalse(plate.location.basicId)
    }

    @Test
    fun bothIdsKeepTheFrenchIdAndUseCtaOnlyForTheMaker() {
        val serial = "0".repeat(23) + "1"
        val plate = plate(tlv(0x02, ascii("ABC" + "DEF" + serial)) + tlv(0x03, ascii("1668B......")))
        assertEquals("ABCDEF$serial", plate.location.uasId)
        assertEquals("Skydio", plate.location.aircraft)
        assertFalse(plate.location.uasId!!.contains("1668"))
        assertFalse(plate.location.basicId)
        assertNull(Cta2063.label("ABC"))
    }

    @Test
    fun heightIsNotAltitudeAndZeroSpeedAndAltitudeStay() {
        val height = plate(tlv(0x07, "0014"))
        assertEquals(20, height.heightM)
        assertNull(height.location.alt)
        assertTrue(height.lines(false).any { it.second == "20 m" })
        val zeros = plate(tlv(0x06, "0000") + tlv(0x07, "0000") + tlv(0x0A, "00") + tlv(0x0B, "0000"))
        assertEquals(0.0, zeros.location.alt!!, 0.0)
        assertEquals(0, zeros.heightM)
        assertEquals(0.0, zeros.location.speedMps!!, 0.0)
        assertEquals(0.0, zeros.location.headingDeg!!, 0.0)
        val below = plate(tlv(0x06, "FFFF") + tlv(0x0B, "0167") + tlv(0x0B, "0168") + tlv(0x01, "07"))
        assertEquals(-1.0, below.location.alt!!, 0.0)
        assertEquals(359.0, below.location.headingDeg!!, 0.0)
        assertEquals(7, below.version)
        assertTrue(below.lines(false).any { it.second == "7" })
    }

    @Test
    fun unknownTypesAreSkippedAndAShortValueIsNotDecoded() {
        val skipped = plate(
            tlv(0x00, "FF") + tlv(0x0C, "AA") + tlv(0xC9, "00") +
                tlv(0x04, "004979AE") + tlv(0x05, "01115219"),
        ).location
        assertEquals(48.15278, skipped.lat!!, 1e-5)
        assertEquals(179.12345, skipped.lon!!, 1e-5)
        val wrong = plate(tlv(0x04, "0001") + tlv(0x05, "01115219")).location
        assertNull(wrong.lat)
        assertEquals(179.12345, wrong.lon!!, 1e-5)
    }

    @Test
    fun overrunKeepsEarlierFields() {
        val plate = plate("01010204040049")
        assertEquals(2, plate.version)
        assertNull(plate.location.lat)
        assertTrue(plate.present)
        val lone = plate("04")
        assertFalse(lone.present)
    }

    @Test
    fun otherTypeAndOtherOuiDoNotDecode() {
        val bytes = tlv(0x04, "004979AE") + tlv(0x05, "01115219")
        assertFalse(plate(bytes, type = 0).present)
        assertFalse(plate(bytes, type = 2).present)
        assertFalse(plate(bytes, oui = "FA:0B:BC").present)
        val lower = FrenchRid.fromFacts(
            RadioFacts(vendorIes = listOf(VendorIeRecord("6a:5c:35", 1, bytes))),
        )
        assertEquals(48.15278, lower.location.lat!!, 1e-5)
    }

    @Test
    fun shortFrenchIdShowsTrimmedAscii() {
        val four = plate(tlv(0x02, ascii("ABCD")))
        assertEquals("ABCD", four.frenchId)
        assertFalse(four.splitId)
        assertEquals("ABCD", four.identifier(true))
        val six = plate(tlv(0x02, ascii("ABCDEF")))
        assertEquals("ABC", six.trigram)
        assertEquals("DEF", six.model)
        assertEquals("", six.serial)
        assertEquals("ABC DEF", six.identifier(false))
    }

    @Test
    fun frenchIdDoesNotClearAnAstmPinOrSelfId() {
        val astm = VendorIeRecord(
            "FA:0B:BC",
            0x0D,
            "00" + locationMsg().toHexUpper() + selfId("HELLO").toHexUpper() + basicId("1668BTEST").toHexUpper(),
        )
        val french = VendorIeRecord("6A:5C:35", 1, tlv(0x02, ascii("ABCDEF")))
        val loc = OpenDroneId.fromFacts(RadioFacts(vendorIes = listOf(french, astm)))
        assertEquals(40.0, loc.lat!!, 1e-6)
        assertEquals(-74.0, loc.lon!!, 1e-6)
        assertEquals("HELLO", loc.selfId)
        assertEquals("ABCDEF", loc.uasId)
        assertEquals("Skydio", loc.aircraft)
        assertTrue(loc.basicId)
        assertEquals(2.0, loc.vspeedMps!!, 1e-6)
    }

    @Test
    fun frenchCoordinateWinsInsideTheSameBeacon() {
        val serial = "0".repeat(23) + "1"
        val french = VendorIeRecord("6A:5C:35", 1, example(serial))
        val astm = VendorIeRecord(
            "FA:0B:BC",
            0x0D,
            "00" + locationMsg().toHexUpper() + selfId("KEEP").toHexUpper(),
        )
        val loc = OpenDroneId.fromFacts(RadioFacts(vendorIes = listOf(astm, french)))
        assertEquals(48.15278, loc.lat!!, 1e-5)
        assertEquals(179.12345, loc.lon!!, 1e-5)
        assertEquals("KEEP", loc.selfId)
        assertEquals(2.0, loc.vspeedMps!!, 1e-6)
        assertEquals(10.0, loc.speedMps!!, 0.0)
        assertEquals(OperatorLoc.TAKEOFF, loc.opKind)
    }

    @Test
    fun laterAstmSystemReplacesFrenchTakeoff() {
        val french = OpenDroneId.fromFacts(
            RadioFacts(vendorIes = listOf(VendorIeRecord("6A:5C:35", 1, example("0".repeat(23) + "1")))),
        )
        val system = OpenDroneId.fromFacts(
            RadioFacts(vendorIes = listOf(VendorIeRecord("FA:0B:BC", 0x0D, "00" + systemMsg().toHexUpper()))),
        )
        val next = system.mergeSticky(french)
        assertEquals(OperatorLoc.LIVE, next.opKind)
        assertEquals(41.0, next.opLat!!, 1e-6)
        assertEquals(-73.0, next.opLon!!, 1e-6)
        assertEquals(48.15278, next.lat!!, 1e-5)
    }

    @Test
    fun newerFrenchPlateReplacesTheStoredOne() {
        val serial = "0".repeat(23) + "1"
        val first = example(serial)
        val moved = tlv(0x01, "01") +
            tlv(0x02, ascii("ABC" + "DEF" + serial)) +
            tlv(0x04, be32(40 * SCALE)) +
            tlv(0x05, be32(-74 * SCALE)) +
            tlv(0x0A, "03")
        val stored = RadioFacts(
            vendorIes = listOf(
                VendorIeRecord("FA:0B:BC", 0x0D, "00112233445566778899AAAA"),
                VendorIeRecord("6A:5C:35", 1, first),
            ),
        ).merge(
            RadioFacts(
                vendorIes = listOf(
                    VendorIeRecord("6a:5c:35", 1, moved),
                    VendorIeRecord("FA:0B:BC", 0x0D, "0011223344556677BBBB"),
                ),
            ),
        )
        val french = stored.vendorIes.filter { FrenchRid.isPlate(it) }
        assertEquals(1, french.size)
        assertEquals(moved, french.single().dataHex)
        val loc = FrenchRid.fromFacts(stored).location
        assertEquals(40.0, loc.lat!!, 1e-5)
        assertEquals(-74.0, loc.lon!!, 1e-5)
        assertEquals(3.0, loc.speedMps!!, 0.0)
        val astm = stored.vendorIes.filter { it.oui == "FA:0B:BC" }
        assertEquals(1, astm.size)
        assertTrue(astm.single().dataHex.endsWith("AAAA"))
        val held = PayloadLocation.applySticky(
            sighting(RadioFacts(vendorIes = listOf(VendorIeRecord("6A:5C:35", 1, first)))),
            emptyList(),
        )
        val next = PayloadLocation.applySticky(held.copy(facts = stored), emptyList())
        assertEquals(40.0, next.payloadLat!!, 1e-5)
        assertEquals(-74.0, next.payloadLon!!, 1e-5)
    }

    @Test
    fun otherVendorIesStillKeepTheFirstMatchingPrefix() {
        val same = "0102030405060708"
        val merged = RadioFacts(
            vendorIes = listOf(
                VendorIeRecord("FA:0B:BC", 0x0D, "AA00112233445566"),
                VendorIeRecord("00:50:F2", 1, same),
                VendorIeRecord("6A:5C:35", 2, same),
            ),
        ).merge(
            RadioFacts(
                vendorIes = listOf(
                    VendorIeRecord("FA:0B:BC", 0x0D, "BB00112233445566"),
                    VendorIeRecord("00:50:F2", 1, same + "FFFF"),
                    VendorIeRecord("6A:5C:35", 2, same + "FFFF"),
                ),
            ),
        )
        assertEquals("AA00112233445566", merged.vendorIes[0].dataHex)
        assertEquals(same, merged.vendorIes[1].dataHex)
        assertEquals(same, merged.vendorIes[2].dataHex)
        assertEquals("BB00112233445566", merged.vendorIes[3].dataHex)
        assertEquals(4, merged.vendorIes.size)
    }

    @Test
    fun altitudeAndSpeedStickWithoutAFix() {
        val facts = RadioFacts(vendorIes = listOf(VendorIeRecord("6A:5C:35", 1, tlv(0x06, "FFFB") + tlv(0x0A, "00"))))
        val saved = PayloadLocation.applySticky(sighting(facts), emptyList())
        assertEquals(-5.0, saved.payloadAlt!!, 0.0)
        assertEquals(0.0, saved.payloadSpeed!!, 0.0)
        assertNull(saved.payloadLat)
    }

    private fun example(serial: String): String =
        tlv(0x01, "01") +
            tlv(0x02, ascii("ABC" + "DEF" + serial)) +
            tlv(0x04, "004979AE") +
            tlv(0x05, "01115219") +
            tlv(0x06, "0064") +
            tlv(0x08, "004979AE") +
            tlv(0x09, "01115219") +
            tlv(0x0A, "0A") +
            tlv(0x0B, "005A")

    private fun plate(payload: String, oui: String = "6A:5C:35", type: Int = 1): FrenchRid.Plate =
        FrenchRid.fromFacts(RadioFacts(vendorIes = listOf(VendorIeRecord(oui, type, payload))))

    private fun tlv(type: Int, hexValue: String): String =
        "%02X%02X%s".format(type, hexValue.length / 2, hexValue)

    private fun ascii(text: String): String =
        text.toByteArray(Charsets.US_ASCII).joinToString("") { "%02X".format(it) }

    private fun be32(n: Int): String =
        "%02X%02X%02X%02X".format((n shr 24) and 0xFF, (n shr 16) and 0xFF, (n shr 8) and 0xFF, n and 0xFF)

    private fun locationMsg(): ByteArray {
        val lat = le32(400_000_000)
        val lon = le32(-740_000_000)
        return byteArrayOf(0x12, 0x20, 90, 40, 4) + lat + lon + le16(0) + le16(2200) + le16(2100) + ByteArray(6)
    }

    private fun selfId(text: String): ByteArray {
        val msg = ByteArray(25)
        msg[0] = 0x30
        text.toByteArray(Charsets.US_ASCII).copyInto(msg, 2)
        return msg
    }

    private fun basicId(serial: String): ByteArray {
        val msg = ByteArray(25)
        msg[0] = 0x00
        msg[1] = 0x10
        serial.toByteArray(Charsets.US_ASCII).copyInto(msg, 2)
        return msg
    }

    private fun systemMsg(): ByteArray {
        val msg = ByteArray(25)
        msg[0] = 0x40
        msg[1] = OperatorLoc.LIVE.toByte()
        le32(410_000_000).copyInto(msg, 2)
        le32(-730_000_000).copyInto(msg, 6)
        return msg
    }

    private fun sighting(facts: RadioFacts) = Sighting(
        key = "WIFI:aa",
        kind = RadioKind.WIFI,
        mac = "AA:BB:CC:DD:EE:01",
        name = "",
        rssi = -40,
        rssiMin = -40,
        rssiMax = -40,
        channel = 6,
        frequencyMhz = 2437,
        vendor = null,
        randomized = false,
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
        facts = facts,
    )

    private fun le32(n: Int) = byteArrayOf(
        (n and 0xFF).toByte(),
        ((n shr 8) and 0xFF).toByte(),
        ((n shr 16) and 0xFF).toByte(),
        ((n shr 24) and 0xFF).toByte(),
    )

    private fun le16(n: Int) = byteArrayOf((n and 0xFF).toByte(), ((n shr 8) and 0xFF).toByte())

    private companion object {
        const val SCALE = 100_000
    }
}
