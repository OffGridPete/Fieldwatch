package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

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

    @Test
    fun wifiMessagePackDecodesIdLocationAndOperator() {
        // DJI RID-1581F3… FA:0B:BC type 13 pack from A54 28 Sep 2026.
        val hex = "D9F2190302123135383146335954444A3144303033315A353330000000" +
            "1220820A00864228110CFF80CF0000F508B2083A022E310A00" +
            "420176E42711B5FF81CF010000000000000005088B02900E00"
        val loc = OpenDroneId.fromFacts(
            RadioFacts(vendorIes = listOf(VendorIeRecord("FA:0B:BC", 0x0D, hex))),
        )
        assertEquals("1581F3YTDJ1D0031Z530", loc.uasId)
        assertEquals(28.7851142, loc.lat!!, 1e-6)
        assertEquals(-81.3629684, loc.lon!!, 1e-6)
        assertEquals(130.0, loc.headingDeg!!, 1e-6)
        assertEquals(2.5, loc.speedMps!!, 1e-6)
        assertEquals(28.7827062, loc.opLat!!, 1e-6)
        assertEquals(-81.3563979, loc.opLon!!, 1e-6)
    }

    @Test
    fun authPageZeroNamesTheTypeAndHidesTheBytes() {
        val msg = authMessage(type = 2, page = 0, lastPage = 0, length = 17, timestamp = 0, tail = hexBytes("DEADBEEF"))
        val auth = OpenDroneId.parseMessages(listOf(msg)).auth!!
        assertEquals(2, auth.type)
        assertEquals(0, auth.page)
        assertEquals(0, auth.lastPage)
        assertEquals(17, auth.length)
        assertEquals(0L, auth.timestamp)
        assertEquals("Operator ID signature", auth.typeName())
        assertEquals("0, last 0", auth.pageValue())
        assertEquals("Not set", auth.timeText(ZoneOffset.UTC))
        assertEquals("Auth data present, 17 bytes", auth.dataLine())
        assertNull(auth.pastLine())
        assertEquals("The plate claims this type. The signature is not checked.", auth.note())
        assertEquals("Operator ID signature", auth.chipLabel())

        val stuck = PayloadLocation.applySticky(ble(bleWrap(msg)), emptyList())
        assertEquals(auth, stuck.payloadAuth)
        assertNull(stuck.payloadLat)
        val row = stuck.withLiveDecode(emptyList())
        assertEquals("Operator ID signature", row.liveDecode.single().text)
        assertFalse(row.liveDecode.single().emphasis)

        val text = DeviceDetailText.build(stuck, emptyList())
        val section = authSection(text)
        assertTrue(section.contains("Operator ID signature"))
        assertTrue(section.contains("Auth data present, 17 bytes"))
        assertFalse(section.contains("DEAD"))
        assertFalse(DeviceDetailText.build(stuck, emptyList(), demo = true).contains("DEAD"))
        assertEquals(stuck.payloadAuth, SitRadio.from(stuck, emptyList()).toSighting().payloadAuth)
    }

    @Test
    fun laterAuthPageKeepsThePageZeroHeader() {
        val page0 = authMessage(type = 2, page = 0, lastPage = 0, length = 17, timestamp = 0, tail = hexBytes("DEADBEEF"))
        val page1 = authMessage(type = 0, page = 1, tail = hexBytes("0000"))
        val alone = OpenDroneId.parseMessages(listOf(page1)).auth!!
        assertEquals(0, alone.type)
        assertEquals(1, alone.page)
        assertNull(alone.lastPage)
        assertNull(alone.length)
        assertNull(alone.timestamp)
        assertEquals("1", alone.pageValue())
        assertNull(alone.dataLine())
        assertNull(alone.chipLabel())

        val kept = OpenDroneId.parseMessages(listOf(page0, page1)).auth!!
        assertEquals(0, kept.type)
        assertEquals(1, kept.page)
        assertEquals(0, kept.lastPage)
        assertEquals(17, kept.length)
        assertEquals(0L, kept.timestamp)
        assertEquals("None", kept.typeName())
        assertEquals("1, last 0", kept.pageValue())
        assertEquals("Not set", kept.timeText(ZoneOffset.UTC))
        assertEquals("Page is past the last page.", kept.pastLine())
        assertNull(kept.note())
        assertNull(kept.chipLabel())

        val replaced = OpenDroneId.parseMessages(
            listOf(page0, page1, authMessage(type = 4, page = 0, lastPage = 2, length = 0, timestamp = 1)),
        ).auth!!
        assertEquals(4, replaced.type)
        assertEquals(0, replaced.page)
        assertEquals(2, replaced.lastPage)
        assertEquals(0, replaced.length)
        assertEquals(1L, replaced.timestamp)
        assertNull(replaced.dataLine())
        assertEquals("Network Remote ID", replaced.chipLabel())
    }

    @Test
    fun authTypesAndTimestamp() {
        assertEquals("UAS ID signature", named(1).typeName())
        assertEquals("UAS ID signature", named(1).chipLabel())
        assertEquals("Message set signature", named(3).chipLabel())
        assertEquals("Network Remote ID", named(4).chipLabel())
        assertEquals("Specific authentication", named(5).typeName())
        assertNull(named(5).chipLabel())
        assertEquals("Reserved 6", named(6).typeName())
        assertNull(named(6).chipLabel())
        assertEquals("Private 10", named(10).typeName())
        assertNull(named(10).chipLabel())
        val timed = authMessage(type = 1, page = 0, lastPage = 0, length = 1, timestamp = 1)
        assertEquals(
            "2019-01-01 00:00:01 Z",
            OpenDroneId.parseMessages(listOf(timed)).auth!!.timeText(ZoneOffset.UTC),
        )
        val version0 = authMessage(type = 2, page = 0, lastPage = 0, length = 17, version = 0)
        assertEquals(0x20, version0[0].toInt() and 0xFF)
        assertEquals(2, OpenDroneId.parseMessages(listOf(version0)).auth!!.type)
    }

    @Test
    fun locationKeepsAuthAndFrenchPinDoesToo() {
        val page0 = authMessage(type = 3, page = 0, lastPage = 1, length = 20, timestamp = 5)
        val page1 = authMessage(type = 3, page = 1)
        val packed = OpenDroneId.fromFacts(
            RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", bleWrap(page0 + page1)))),
        ).auth!!
        assertEquals(3, packed.type)
        assertEquals(1, packed.page)
        assertEquals(1, packed.lastPage)
        assertEquals(20, packed.length)
        assertEquals(5L, packed.timestamp)
        assertNull(packed.pastLine())
        assertEquals("Message set signature", packed.chipLabel())

        val withLoc = OpenDroneId.parseMessages(
            listOf(page0, locationMsg(dir = 90, ew = false, speed = 40)),
        )
        assertEquals(40.0, withLoc.lat!!, 1e-6)
        assertEquals(20, withLoc.auth!!.length)
        assertEquals(5L, withLoc.auth.timestamp)

        val french = OpenDroneId.fromFacts(
            RadioFacts(
                serviceData = listOf(ServiceDataRecord("FFFA", bleWrap(page0))),
                vendorIes = listOf(VendorIeRecord("6A:5C:35", 1, "0404003D09000504FF8F15C0")),
            ),
        )
        assertEquals(40.0, french.lat!!, 1e-6)
        assertEquals(-74.0, french.lon!!, 1e-6)
        assertEquals(3, french.auth!!.type)
        assertEquals(20, french.auth.length)
    }

    @Test
    fun applyStickyKeepsAuthAcrossALocationPacket() {
        val authMsg = authMessage(type = 2, page = 0, lastPage = 0, length = 17, timestamp = 0)
        val locMsg = locationMsg(dir = 90, ew = false, speed = 40)
        val located = PayloadLocation.applySticky(ble(bleWrap(locMsg)), emptyList())
        assertEquals(40.0, located.payloadLat!!, 1e-6)
        assertNull(located.payloadAuth)
        val authed = PayloadLocation.applySticky(
            located.copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", bleWrap(authMsg))))),
            emptyList(),
        )
        assertEquals(40.0, authed.payloadLat!!, 1e-6)
        assertEquals(2, authed.payloadAuth!!.type)
        assertEquals(17, authed.payloadAuth.length)
        val again = PayloadLocation.applySticky(
            authed.copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", bleWrap(locMsg))))),
            emptyList(),
        )
        assertEquals(40.0, again.payloadLat!!, 1e-6)
        assertEquals(authed.payloadAuth, again.payloadAuth)
        assertFalse(again.withLiveDecode(emptyList()).liveDecode.single().emphasis)
    }

    @Test
    fun wifiVendorIeAuth() {
        val msg = authMessage(type = 4, page = 0, lastPage = 0, length = 8, timestamp = 0)
        val loc = OpenDroneId.fromFacts(
            RadioFacts(vendorIes = listOf(VendorIeRecord("FA:0B:BC", 0x0D, "00" + msg.toHexUpper()))),
        )
        assertEquals(4, loc.auth!!.type)
        assertEquals(8, loc.auth.length)
        assertEquals("Not set", loc.auth.timeText(ZoneOffset.UTC))
        assertEquals("Network Remote ID", loc.auth.chipLabel())
    }

    private fun named(type: Int): RemoteIdAuth =
        OpenDroneId.parseMessages(listOf(authMessage(type = type, page = 0, lastPage = 0, length = 0))).auth!!

    private fun authSection(text: String): String {
        val start = text.indexOf("## Authentication")
        assertTrue(start >= 0)
        val note = "The plate claims this type. The signature is not checked."
        val end = text.indexOf(note, start)
        assertTrue(end >= 0)
        return text.substring(start, end + note.length)
    }

    private fun authMessage(
        type: Int,
        page: Int,
        lastPage: Int = 0,
        length: Int = 0,
        timestamp: Long = 0,
        version: Int = 2,
        tail: ByteArray = ByteArray(0),
    ): ByteArray {
        val head = ((0x2 shl 4) or (version and 0x0F)).toByte()
        val flags = ((type shl 4) or (page and 0x0F)).toByte()
        val prefix = if (page == 0) {
            byteArrayOf(
                head,
                flags,
                (lastPage and 0xFF).toByte(),
                (length and 0xFF).toByte(),
                (timestamp and 0xFF).toByte(),
                ((timestamp shr 8) and 0xFF).toByte(),
                ((timestamp shr 16) and 0xFF).toByte(),
                ((timestamp shr 24) and 0xFF).toByte(),
            )
        } else {
            byteArrayOf(head, flags)
        }
        val data = ByteArray(25 - prefix.size)
        tail.copyInto(data, endIndex = minOf(tail.size, data.size))
        return prefix + data
    }

    private fun hexBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun ble(dataHex: String) = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:01",
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:01",
        name = "",
        rssi = -40,
        rssiMin = -40,
        rssiMax = -40,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = false,
        hiddenSsid = false,
        serviceUuids = listOf("FFFA"),
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
        facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", dataHex))),
    )

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
