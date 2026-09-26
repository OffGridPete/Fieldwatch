package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenDroneIdTest {
    @Test
    fun bleLocationHeadingSpeedAndEw() {
        val loc = OpenDroneId.fromFacts(
            RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", bleWrap(locationMsg(dir = 90, ew = false, speed = 40))))),
        )
        assertEquals(40.0, loc.lat!!, 1e-6)
        assertEquals(-74.0, loc.lon!!, 1e-6)
        assertEquals(90.0, loc.headingDeg!!, 1e-6)
        assertEquals(10.0, loc.speedMps!!, 1e-6)
        assertEquals(2.0, loc.vspeedMps!!, 1e-6)
        val west = OpenDroneId.fromFacts(
            RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", bleWrap(locationMsg(dir = 90, ew = true, speed = 40))))),
        )
        assertEquals(270.0, west.headingDeg!!, 1e-6)
    }

    @Test
    fun wifiVendorIeLocation() {
        val ie = VendorIeRecord("FA:0B:BC", 0x0D, "00" + locationMsg(dir = 45, ew = false, speed = 8).toHexUpper())
        val loc = OpenDroneId.fromFacts(RadioFacts(vendorIes = listOf(ie)))
        assertEquals(40.0, loc.lat!!, 1e-6)
        assertEquals(45.0, loc.headingDeg!!, 1e-6)
        assertEquals(2.0, loc.speedMps!!, 1e-6)
    }

    @Test
    fun unknownDirectionIsNull() {
        val loc = OpenDroneId.parseMessages(listOf(locationMsg(dir = 255, ew = false, speed = 40)))
        assertNull(loc.headingDeg)
    }

    private fun bleWrap(msg: ByteArray) = ("0D00" + msg.toHexUpper())

    private fun locationMsg(dir: Int, ew: Boolean, speed: Int): ByteArray {
        val flags = 0x20 or (if (ew) 0x02 else 0)
        val lat = le32(400_000_000)
        val lon = le32(-740_000_000)
        val geo = le16(2200)
        val height = le16(2100)
        return byteArrayOf(
            0x12, flags.toByte(), dir.toByte(), speed.toByte(), 4,
        ) + lat + lon + le16(0) + geo + height + ByteArray(6)
    }

    private fun le32(n: Int) = byteArrayOf(
        (n and 0xFF).toByte(),
        ((n shr 8) and 0xFF).toByte(),
        ((n shr 16) and 0xFF).toByte(),
        ((n shr 24) and 0xFF).toByte(),
    )

    private fun le16(n: Int) = byteArrayOf((n and 0xFF).toByte(), ((n shr 8) and 0xFF).toByte())
}
