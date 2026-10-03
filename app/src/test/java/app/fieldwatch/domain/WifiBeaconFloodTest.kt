package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiBeaconFloodTest {
    @Test
    fun firstScanStaysQuietWhenTheCrowdLeaves() {
        val flood = WifiBeaconFlood()
        val crowd = listOf(anchor()) + spam(count = 20)
        flood.scan(crowd, at(0))
        assertNull(flood.notice.value)
        flood.scan(listOf(anchor()), at(1))
        assertNull(flood.notice.value)
        assertTrue(flood.bursts().isEmpty())
    }

    @Test
    fun meshExtenderGuestAndHiddenStayOut() {
        val flood = WifiBeaconFlood()
        val known = (1..15).map { ap(10 + it, "Net%02d".format(it), rssi = -60, channel = 1) }
        flood.scan(listOf(anchor()) + known, at(0))
        val suffixes = listOf(
            "Guest", "5G", "ext", "2.4", "5GHz", "extender", "guests",
            "6G", "2.4GHz", "6GHz", "guest", "5g", "EXT", "2.4g", "Guests",
        )
        val guests = suffixes.mapIndexed { i, suffix ->
            ap(200 + i, "Net%02d-$suffix".format(i + 1))
        }
        flood.scan(listOf(anchor()) + guests, at(1))
        flood.scan(listOf(anchor()), at(2))
        assertNull(flood.notice.value)

        val mesh = WifiBeaconFlood()
        mesh.scan(listOf(anchor()), at(0))
        mesh.scan(listOf(anchor()) + List(20) { ap(300 + it, "BuildingMesh") }, at(1))
        mesh.scan(listOf(anchor()), at(2))
        assertNull(mesh.notice.value)

        val hidden = WifiBeaconFlood()
        hidden.scan(listOf(anchor()), at(0))
        hidden.scan(listOf(anchor()) + List(20) { ap(400 + it, "") }, at(1))
        hidden.scan(listOf(anchor()), at(2))
        assertNull(hidden.notice.value)
        assertTrue(hidden.bursts().isEmpty())
    }

    @Test
    fun spreadRssiMixedChannelsAndAShortListStayOut() {
        val spread = WifiBeaconFlood()
        spread.scan(listOf(anchor()), at(0))
        val wide = (0 until 15).map { ap(100 + it, "Wide$it", rssi = -30 - it * 4) }
        spread.scan(listOf(anchor()) + wide, at(1))
        spread.scan(listOf(anchor()), at(2))
        assertNull(spread.notice.value)

        val mixed = WifiBeaconFlood()
        mixed.scan(listOf(anchor()), at(0))
        val bands = (0 until 8).map { ap(100 + it, "A$it", channel = 1) } +
            (0 until 8).map { ap(200 + it, "B$it", channel = 6) } +
            (0 until 8).map { ap(300 + it, "C$it", channel = 11) }
        mixed.scan(listOf(anchor()) + bands, at(1))
        mixed.scan(listOf(anchor()), at(2))
        assertNull(mixed.notice.value)

        val short = WifiBeaconFlood()
        short.scan(listOf(anchor()), at(0))
        short.scan(listOf(anchor()) + spam(count = 14), at(1))
        short.scan(listOf(anchor()), at(2))
        assertNull(short.notice.value)

        val unmeasured = WifiBeaconFlood()
        unmeasured.scan(listOf(anchor()), at(0))
        unmeasured.scan(listOf(anchor()) + (0 until 20).map { ap(100 + it, "Blank$it", rssi = 127) }, at(1))
        unmeasured.scan(listOf(anchor()), at(2))
        assertNull(unmeasured.notice.value)
        assertTrue(unmeasured.bursts().isEmpty())
    }

    @Test
    fun aClusterThatStaysIsNotAFlood() {
        val flood = WifiBeaconFlood()
        val batch = listOf(anchor()) + spam()
        flood.scan(listOf(anchor()), at(0))
        flood.scan(batch, at(1))
        flood.scan(batch, at(2))
        flood.scan(batch, at(3))
        assertNull(flood.notice.value)
        assertTrue(flood.bursts().isEmpty())
    }

    @Test
    fun oneSurvivorDropsThePendingSet() {
        val flood = WifiBeaconFlood()
        val names = spam()
        flood.scan(listOf(anchor()), at(0))
        flood.scan(listOf(anchor()) + names, at(1))
        flood.scan(listOf(anchor(), names.first()), at(2))
        flood.scan(listOf(anchor()), at(3))
        assertNull(flood.notice.value)
        assertTrue(flood.bursts().isEmpty())
    }

    @Test
    fun aNewPlaceDoesNotConfirmThePreviousCluster() {
        val flood = WifiBeaconFlood()
        val home = anchor()
        flood.scan(listOf(home), at(0))
        flood.scan(listOf(home) + spam(), at(1))
        val office = (1..5).map { ap(500 + it, "Office$it", rssi = -55, channel = 1) }
        flood.scan(office, at(2))
        assertNull(flood.notice.value)
        flood.scan(listOf(home), at(3))
        assertNull(flood.notice.value)
        assertTrue(flood.bursts().isEmpty())
    }

    @Test
    fun aCachedScanDoesNotCountOrClearAPendingSet() {
        val flood = WifiBeaconFlood()
        val crowd = listOf(anchor()) + spam(count = 20)
        flood.scan(crowd.map { it.copy(fresh = false) }, at(0))
        flood.scan(crowd, at(1))
        flood.scan(listOf(anchor()), at(2))
        assertNull(flood.notice.value)

        val pending = WifiBeaconFlood()
        val names = spam()
        pending.scan(listOf(anchor()), at(0))
        pending.scan(listOf(anchor()) + names, at(1))
        pending.scan(listOf(anchor()).map { it.copy(fresh = false) }, at(2))
        assertNull(pending.notice.value)
        pending.scan(listOf(anchor()), at(3))
        assertEquals(15, pending.notice.value!!.popupCount)
    }

    @Test
    fun aSecondCallbackInsideThreeSecondsDoesNotConfirm() {
        val flood = WifiBeaconFlood()
        val spamAt = at(1)
        flood.scan(listOf(anchor()), at(0))
        flood.scan(listOf(anchor()) + spam(), spamAt)
        flood.scan(listOf(anchor()), spamAt + 1_000L)
        assertNull(flood.notice.value)
        flood.scan(listOf(anchor()), spamAt + 30_000L)
        assertEquals(15, flood.notice.value!!.popupCount)
    }

    @Test
    fun goneTogetherOnOneChannelOpensOneDialog() {
        val flood = WifiBeaconFlood()
        val names = spam()
        val edge = (0 until 14).map { ap(100 + it, "Edge$it", rssi = -40) } +
            ap(114, "Edge14", rssi = -46)
        val outlier = ap(180, "Louder", rssi = -70)
        val otherChannel = ap(181, "Printer", rssi = -40, channel = 11)
        flood.scan(listOf(anchor()), at(0))
        flood.scan(listOf(anchor(), otherChannel, outlier) + names, at(1))
        flood.scan(listOf(anchor(), otherChannel), at(2))
        val notice = flood.notice.value!!
        assertTrue(notice.showDialog)
        assertTrue(notice.wifi)
        assertEquals("Wi-Fi beacon flood", notice.title())
        assertEquals(15, notice.popupCount)
        assertEquals(-34, notice.medianRssi)
        assertEquals("Wi-Fi beacon flood · 15 new names · about -34 dBm", notice.line())
        assertTrue(notice.body().contains("15 new Wi-Fi names showed up in one scan, about the same loudness, about -34 dBm,"))
        assertTrue(notice.body().contains("A repeated name, a mesh, an extender, or a guest network is not counted."))
        assertTrue(notice.body().contains("The advertisement does not name the tool."))
        assertFalse(notice.body().contains("Remote ID"))
        val burst = flood.bursts().single()
        assertTrue(burst.wifi)
        assertEquals(15, burst.popupCount)
        assertEquals(names.map { "WIFI:${macOf(it)}" }.toSet(), burst.keys.toSet())
        assertFalse(burst.keys.contains("WIFI:${mac(1)}"))
        assertFalse(burst.keys.contains("WIFI:${mac(180)}"))
        assertFalse(burst.keys.contains("WIFI:${mac(181)}"))
        assertEquals(
            "12:04 UTC. Wi-Fi beacon flood. 15 new names · about -34 dBm. 15 addresses from this burst are left out of the counts and lists below.",
            burst.reportLine("12:04"),
        )

        val band = WifiBeaconFlood()
        band.scan(listOf(anchor()), at(0))
        band.scan(listOf(anchor()) + edge, at(1))
        band.scan(listOf(anchor()), at(2))
        assertEquals(15, band.bursts().single().keys.size)
        assertTrue(band.bursts().single().keys.contains("WIFI:${mac(114)}"))
    }

    @Test
    fun aSecondRotatingBatchStaysOneEpisode() {
        val flood = WifiBeaconFlood()
        val first = spam(start = 100)
        val second = spam(start = 300)
        flood.scan(listOf(anchor()), at(0))
        flood.scan(listOf(anchor()) + first, at(1))
        flood.scan(listOf(anchor()) + second, at(2))
        val opened = flood.notice.value!!
        val firstAt = flood.bursts().single().at
        assertTrue(opened.showDialog)
        assertEquals(15, opened.popupCount)
        flood.dismiss()
        flood.setHideBurst(true)
        assertFalse(flood.notice.value!!.showDialog)
        assertEquals(15, flood.hide.value.keys.size)
        flood.scan(listOf(anchor()), at(3))
        val grown = flood.notice.value!!
        assertFalse(grown.showDialog)
        assertEquals(30, grown.popupCount)
        val burst = flood.bursts().single()
        assertEquals(firstAt, burst.at)
        assertEquals(30, burst.keys.size)
        assertEquals(30, flood.hide.value.keys.size)
        assertTrue(flood.hide.value.episodeOn)
        flood.scan(listOf(anchor()), at(4))
        assertNull(flood.notice.value)
        assertFalse(flood.hide.value.episodeOn)
        assertEquals(30, flood.hide.value.keys.size)

        val again = spam(start = 500)
        flood.scan(listOf(anchor()) + again, at(5))
        flood.scan(listOf(anchor()), at(6))
        flood.setHideBurst(false)
        assertEquals(2, flood.bursts().size)
        assertEquals(30, flood.hide.value.keys.size)
        assertTrue(flood.hide.value.keys.contains("WIFI:${mac(100)}"))
        assertFalse(flood.hide.value.keys.contains("WIFI:${mac(500)}"))
    }

    @Test
    fun theLowerChannelWinsATie() {
        val flood = WifiBeaconFlood()
        val low = (0 until 15).map { ap(100 + it, "Low$it", channel = 1) }
        val high = (0 until 15).map { ap(200 + it, "High$it", channel = 11) }
        flood.scan(listOf(anchor()), at(0))
        flood.scan(listOf(anchor()) + low + high, at(1))
        flood.scan(listOf(anchor()), at(2))
        val keys = flood.bursts().single().keys.toSet()
        assertEquals(low.map { "WIFI:${macOf(it)}" }.toSet(), keys)
        assertTrue(high.none { "WIFI:${macOf(it)}" in keys })
    }

    private fun at(step: Int) = 1_000_000L + step * 30_000L

    private fun anchor() = ap(1, "FrontDesk", rssi = -50, channel = 11)

    private fun spam(start: Int = 100, count: Int = 15) =
        (0 until count).map { ap(start + it, "Spam${start + it}") }

    private fun macOf(obs: Observation) = MacUtil.normalize(obs.mac)

    private fun mac(n: Int): String = macOf(ap(n, "x"))

    private fun ap(
        n: Int,
        name: String,
        rssi: Int = -34,
        channel: Int = 6,
        fresh: Boolean = true,
    ) = Observation(
        kind = RadioKind.WIFI,
        mac = "%02X:%02X:%02X:%02X:%02X:%02X".format(
            0x02,
            (n shr 24) and 0xFF,
            (n shr 16) and 0xFF,
            (n shr 8) and 0xFF,
            n and 0xFF,
            0x01,
        ),
        name = name,
        rssi = rssi,
        channel = channel,
        frequencyMhz = 2437,
        hiddenSsid = name.isBlank(),
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        at = 0L,
        fresh = fresh,
    )
}
