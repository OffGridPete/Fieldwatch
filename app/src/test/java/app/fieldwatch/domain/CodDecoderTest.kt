package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodDecoderTest {
    @Test
    fun smartphoneIsPhoneSlashSmartphone() {
        // major 0x02 Phone, minor 0x03 Smartphone, format 0.
        val d = CodDecoder.decode(0x020C)
        assertEquals("Phone", d.major)
        assertEquals("Smartphone", d.minor)
        assertEquals("Phone / Smartphone", d.summary())
    }

    @Test
    fun serviceBitsAppendToSummary() {
        // Networking (bit 4) + Audio (bit 8) of the service field; A/V headphones.
        val cod = (1 shl 17) or (1 shl 21) or (0x04 shl 8) or (0x06 shl 2)
        val d = CodDecoder.decode(cod)
        assertEquals("Audio / Video", d.major)
        assertEquals("Headphones", d.minor)
        assertEquals("Audio / Video / Headphones · Networking, Audio", d.summary())
    }

    @Test
    fun peripheralKeyboard() {
        // major 0x05 Peripheral, minor 0x10 = keyboard HID bit alone.
        val d = CodDecoder.decode((0x05 shl 8) or (0x10 shl 2))
        assertEquals("Peripheral", d.major)
        assertEquals("Keyboard", d.minor)
    }

    @Test
    fun nonZeroFormatSuppressesMinorName() {
        val d = CodDecoder.decode((0x02 shl 8) or (0x03 shl 2) or 0x01)
        assertEquals("format 1", d.minor)
    }

    @Test
    fun zeroAndNullDecodeToNull() {
        assertNull(CodDecoder.decodeOrNull(null))
        assertNull(CodDecoder.decodeOrNull(0))
    }
}
