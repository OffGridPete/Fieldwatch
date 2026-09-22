package app.fieldwatch.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleAdParserTest {
    /** AD structure: [len][type][data]. len covers type + data. */
    private fun field(type: Int, vararg data: Int): ByteArray =
        byteArrayOf((data.size + 1).toByte(), type.toByte()) +
            data.map { it.toByte() }.toByteArray()

    private fun nameField(type: Int, s: String): ByteArray =
        byteArrayOf((s.length + 1).toByte(), type.toByte()) + s.toByteArray(Charsets.UTF_8)

    @Test
    fun nullAndEmptyGiveEmptyParsed() {
        val none = BleAdParser.parse(null)
        assertNull(none.localName)
        assertTrue(none.mfg.isEmpty())
        assertTrue(BleAdParser.parse(ByteArray(0)).uuids.isEmpty())
    }

    @Test
    fun flagsNameTxAppearanceInterval() {
        val bytes = field(0x01, 0x06) +
            nameField(0x09, "Pixel Buds") +
            field(0x0A, 0xF8) +
            field(0x19, 0x41, 0x03) +
            field(0x1A, 0xA0, 0x00)
        val p = BleAdParser.parse(bytes)
        assertEquals(0x06, p.flags)
        assertEquals("Pixel Buds", p.localName)
        assertEquals(-8, p.txPower)
        assertEquals(0x0341, p.appearance)
        assertEquals(100.0, p.advertisingIntervalMs!!, 0.001)
    }

    @Test
    fun completeNameBeatsShortenedEitherOrder() {
        val short = nameField(0x08, "FW")
        val full = nameField(0x09, "Fieldwatch")
        assertEquals("Fieldwatch", BleAdParser.parse(short + full).localName)
        assertEquals("Fieldwatch", BleAdParser.parse(full + short).localName)
        assertEquals("FW", BleAdParser.parse(short).localName)
    }

    @Test
    fun nameControlBytesBecomeSpaces() {
        // 0x07 (BEL) inside a local name must not inject control chars into the UI.
        val raw = byteArrayOf(4, 0x09, 'A'.code.toByte(), 0x07, 'B'.code.toByte())
        assertEquals("A B", BleAdParser.parse(raw).localName)
    }

    @Test
    fun uuid16ListIsBigEndian() {
        // On-air order is little-endian; parsed form is the SIG-assigned hex.
        val p = BleAdParser.parse(field(0x03, 0xAA, 0xFE, 0x0D, 0x18))
        assertEquals(listOf("FEAA", "180D"), p.uuids)
    }

    @Test
    fun uuid128IsByteReversed() {
        val le = (0x00..0x0F).map { it.toByte() }.toByteArray()
        val p = BleAdParser.parse(byteArrayOf(17, 0x07) + le)
        assertEquals(listOf("0F0E0D0C-0B0A-0908-0706-050403020100"), p.uuids)
    }

    @Test
    fun serviceData16AndManufacturerData() {
        val bytes = field(0x16, 0xAA, 0xFE, 0x01, 0x02) +
            field(0xFF, 0x4C, 0x00, 0x02, 0x15, 0xAB)
        val p = BleAdParser.parse(bytes)
        assertEquals(1, p.serviceData.size)
        assertEquals("FEAA", p.serviceData[0].uuid)
        assertEquals("0102", p.serviceData[0].dataHex)
        assertEquals(1, p.mfg.size)
        assertEquals(0x004C, p.mfg[0].companyId)
        assertEquals("0215AB", p.mfg[0].dataHex)
    }

    @Test
    fun deviceClassIsLittleEndian24() {
        val p = BleAdParser.parse(field(0x0D, 0x0C, 0x05, 0x1F))
        assertEquals(0x1F050C, p.deviceClass)
    }

    @Test
    fun truncatedFieldStopsButKeepsEarlier() {
        val good = field(0x01, 0x06)
        val bad = byteArrayOf(0x40, 0x09, 'X'.code.toByte()) // len says 64, only 2 bytes follow
        val p = BleAdParser.parse(good + bad)
        assertEquals(0x06, p.flags)
        assertNull(p.localName)
    }

    @Test
    fun zeroLengthFieldTerminates() {
        val p = BleAdParser.parse(field(0x01, 0x1E) + byteArrayOf(0x00) + nameField(0x09, "late"))
        assertEquals(0x1E, p.flags)
        assertNull(p.localName)
    }

    @Test
    fun flagsLabelText() {
        assertEquals("LE General Discoverable, BR/EDR not supported", BleAdParser.flagsLabel(0x06))
        assertEquals("0x00", BleAdParser.flagsLabel(0x00))
    }
}
