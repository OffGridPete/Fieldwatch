package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerWatchTest {
    @Test
    fun separatedTagWarnsOnceAfterThirtyMinutesAndThreePlaces() {
        val monitor = TrackerMonitor()
        val first = step(monitor, 0, P1, dult("00"))
        assertNull(first)
        val second = step(monitor, 15 * MINUTE, P2, dult("00"))
        assertNull(second)
        val third = step(monitor, 30 * MINUTE, P3, dult("00"))
        val notice = third!!
        assertEquals(TrackerWatch.Kind.FOLLOW, notice.kind)
        assertEquals(30, notice.minutes)
        assertEquals(3, notice.places)
        assertEquals("DULT tracker", notice.name)
        val held = step(monitor, 31 * MINUTE, P3, dult("00"))
        assertNull(held)
        assertEquals(notice.id, monitor.notice.value?.id)
    }

    @Test
    fun dismissStaysQuietUntilTheTagIsGoneAndTheClockStartsOver() {
        val monitor = TrackerMonitor()
        step(monitor, 0, P1, dult("00"))
        step(monitor, 15 * MINUTE, P2, dult("00"))
        val notice = step(monitor, 30 * MINUTE, P3, dult("00"))!!
        assertNull(monitor.dismissCurrent())
        assertNull(step(monitor, 40 * MINUTE, P3, dult("00")))
        assertNull(step(monitor, 40 * MINUTE + TrackerWatch.GAP_MS + 1, P1, dult("00")))
        assertNull(step(monitor, 55 * MINUTE + TrackerWatch.GAP_MS + 1, P2, dult("00")))
        val again = step(monitor, 70 * MINUTE + TrackerWatch.GAP_MS + 1, P3, dult("00"))
        assertEquals(notice.deviceKey, again!!.deviceKey)
        assertEquals(TrackerWatch.Kind.FOLLOW, again.kind)
    }

    @Test
    fun nearOwnerClearsTheClock() {
        val monitor = TrackerMonitor()
        step(monitor, 0, P1, dult("00"))
        step(monitor, 15 * MINUTE, P2, dult("00"))
        assertNull(step(monitor, 20 * MINUTE, P2, dult("01")))
        assertNull(step(monitor, 50 * MINUTE, P3, dult("00")))
    }

    @Test
    fun shortAndMaintainedFindMyStayOutAndALostLongFormCounts() {
        val monitor = TrackerMonitor()
        assertNull(walk(monitor, findMy("02", "0000")))
        assertNull(walk(monitor, findMy("19", "44" + "11".repeat(24))))
        assertNull(walk(monitor, findMy("19", "00" + "11".repeat(24)), name = "", fleets = setOf("fleet-apple-device")))
        val phone = findMy("19", "00" + "11".repeat(24))
        val withContinuity = RadioFacts(
            mfgRecords = listOf(MfgRecord(0x004C, "100100" + "12" + "19" + "00" + "11".repeat(24))),
        )
        assertNull(walk(monitor, withContinuity))
        val tag = walk(monitor, phone, name = "AirTag")
        assertEquals(TrackerWatch.Kind.FOLLOW, tag!!.kind)
        assertTrue(tag.name.contains("AirTag"))
    }

    @Test
    fun findMyAccessoryUuidStillCountsBesideContinuity() {
        val monitor = TrackerMonitor()
        val packet = RadioFacts(
            mfgRecords = listOf(MfgRecord(0x004C, "100100" + "12" + "19" + "00" + "11".repeat(24))),
        )
        val notice = walk(monitor, packet, serviceUuids = listOf("FD44"))
        assertEquals(TrackerWatch.Kind.FOLLOW, notice!!.kind)
    }

    @Test
    fun nearWinsWhenTheSamePacketAlsoSaysSeparated() {
        val monitor = TrackerMonitor()
        val both = RadioFacts(
            serviceData = listOf(
                ServiceDataRecord("FCB2", "0000"),
                ServiceDataRecord("FEAA", "40" + "11".repeat(20)),
            ),
        )
        assertNull(walk(monitor, both))
    }

    @Test
    fun smartTagPrivacyIdStitchesANewAddress() {
        val monitor = TrackerMonitor()
        val id = "AABBCCDDEEFF0011"
        step(monitor, 0, P1, fd5a(1, id), mac = "02:00:00:00:00:01")
        step(monitor, 15 * MINUTE, P2, fd5a(1, id), mac = "02:00:00:00:00:02")
        val notice = step(monitor, 30 * MINUTE, P3, fd5a(1, id), mac = "02:00:00:00:00:03")
        assertEquals("smarttag:$id", notice!!.id)
        assertEquals("BLE:02:00:00:00:00:03", notice.deviceKey)
        assertNull(step(monitor, 31 * MINUTE, P3, fd5a(4, id), mac = "02:00:00:00:00:03"))
        assertNull(monitor.notice.value)
    }

    @Test
    fun mineAndANameOnlyTagDoNotWarn() {
        val monitor = TrackerMonitor()
        val mineKey = "BLE:$MAC"
        assertNull(
            step(monitor, 0, P1, dult("00"), mine = setOf(mineKey)),
        )
        assertNull(step(monitor, 15 * MINUTE, P2, dult("00"), mine = setOf(mineKey)))
        assertNull(step(monitor, 30 * MINUTE, P3, dult("00"), mine = setOf(mineKey)))
        val named = TrackerMonitor()
        assertNull(walk(named, RadioFacts.Empty, name = "Tile"))
    }

    @Test
    fun placesSwitchOffRemembersHistoryAndADropCanWarnAgain() {
        val row = placeRow(count = 2)
        val off = TrackerWatch.places(emptySet(), listOf(row), armed = false)
        assertTrue(off.fresh.isEmpty())
        assertEquals(setOf(row.id), off.seen)
        val held = TrackerWatch.places(off.seen, listOf(row), armed = true)
        assertTrue(held.fresh.isEmpty())
        val dropped = TrackerWatch.places(held.seen, listOf(row.copy(count = 1)), armed = true)
        assertTrue(dropped.seen.isEmpty())
        val back = TrackerWatch.places(dropped.seen, listOf(row), armed = true)
        assertEquals(1, back.fresh.size)
        assertEquals(TrackerWatch.Kind.PLACES, back.fresh.single().kind)
        val aircraft = TrackerWatch.places(
            emptySet(),
            listOf(row.copy(id = "rid:SERIAL", count = 2)),
            armed = true,
        )
        assertTrue(aircraft.fresh.isEmpty())
    }

    @Test
    fun oneRadioThatTripsBothGetsTheSeparatedTitle() {
        val follow = TrackerWatch.Notice(
            id = "ble:$MAC",
            kind = TrackerWatch.Kind.FOLLOW,
            deviceKey = "BLE:$MAC",
            name = "Keys",
            mac = MAC,
            rssi = -50,
            minutes = 30,
            places = 3,
        )
        val places = TrackerWatch.Notice(
            id = "places:ble:$MAC",
            kind = TrackerWatch.Kind.PLACES,
            deviceKey = "BLE:$MAC",
            name = "Keys",
            mac = MAC,
            rssi = -50,
            places = 2,
        )
        val other = places.copy(id = "places:ble:00:00:00:00:00:02", deviceKey = "BLE:00:00:00:00:00:02")
        val merged = TrackerWatch.merge(listOf(follow), listOf(places, other))
        assertEquals(2, merged.size)
        assertEquals(TrackerWatch.Kind.FOLLOW, merged[0].kind)
        assertEquals(2, merged[0].alsoPlaces)
        assertEquals(TrackerWatch.Kind.PLACES, merged[1].kind)
    }

    @Test
    fun aSecondRadioWaitsUntilTheFirstIsDismissed() {
        val monitor = TrackerMonitor()
        step(monitor, 0, P1, dult("00"), mac = MAC)
        step(monitor, 15 * MINUTE, P2, dult("00"), mac = MAC)
        val first = step(monitor, 30 * MINUTE, P3, dult("00"), mac = MAC)!!
        val otherMac = "00:11:22:33:44:66"
        val findMy = findMy("19", "00" + "11".repeat(24))
        assertNull(step(monitor, 30 * MINUTE, P1, findMy, mac = otherMac))
        assertEquals(first.deviceKey, monitor.notice.value?.deviceKey)
        step(monitor, 35 * MINUTE, P3, dult("00"), mac = MAC)
        step(monitor, 45 * MINUTE, P2, findMy, mac = otherMac)
        step(monitor, 50 * MINUTE, P3, dult("00"), mac = MAC)
        val waiting = step(monitor, 60 * MINUTE, P3, findMy, mac = otherMac)
        assertNull(waiting)
        assertEquals(first.deviceKey, monitor.notice.value?.deviceKey)
        val next = monitor.dismissCurrent()
        assertEquals("BLE:$otherMac", next!!.deviceKey)
    }

    @Test
    fun openingTheWarnedRadioCountsAsSeeingIt() {
        val monitor = TrackerMonitor()
        val notice = walk(monitor, dult("00"))!!
        assertNull(monitor.seenDevice("BLE:someone-else"))
        assertEquals(notice.id, monitor.notice.value?.id)
        assertNull(monitor.seenDevice(notice.deviceKey))
        assertNull(monitor.notice.value)
        assertNull(step(monitor, 31 * MINUTE, P3, dult("00")))
    }

    private fun walk(
        monitor: TrackerMonitor,
        packet: RadioFacts,
        name: String = "",
        mac: String = MAC,
        fleets: Set<String> = emptySet(),
        fleetNames: List<String> = listOf("DULT tracker"),
        serviceUuids: List<String> = emptyList(),
    ): TrackerWatch.Notice? {
        var shown: TrackerWatch.Notice? = null
        val points = listOf(P1, P2, P3)
        for (i in points.indices) {
            shown = step(
                monitor,
                i * 15 * MINUTE,
                points[i],
                packet,
                name = name,
                mac = mac,
                fleets = fleets,
                fleetNames = fleetNames,
                serviceUuids = serviceUuids,
            ) ?: shown
        }
        return shown
    }

    private fun step(
        monitor: TrackerMonitor,
        at: Long,
        where: Pair<Double, Double>,
        packet: RadioFacts,
        name: String = "",
        mac: String = MAC,
        fleets: Set<String> = emptySet(),
        fleetNames: List<String> = listOf("DULT tracker"),
        serviceUuids: List<String> = emptyList(),
        mine: Set<String> = emptySet(),
    ): TrackerWatch.Notice? = monitor.hear(
        hears = listOf(
            TrackerWatch.Hear(
                device = radio(mac, name, fleets, serviceUuids),
                packet = packet,
                fleetNames = fleetNames,
                custom = null,
            ),
        ),
        mine = mine,
        lat = where.first,
        lon = where.second,
        now = T0 + at,
        followOn = true,
        placesOn = false,
        placeFresh = emptyList(),
    )

    private fun placeRow(count: Int) = TrackerWatch.PlaceRow(
        id = "ble:$MAC",
        deviceKey = "BLE:$MAC",
        name = "Tile",
        mac = MAC,
        rssi = -55,
        count = count,
        stops = listOf(TrackerWatch.Spot(P1.first, P1.second), TrackerWatch.Spot(P2.first, P2.second)),
    )

    private fun dult(mode: String) = RadioFacts(
        serviceData = listOf(ServiceDataRecord("FCB2", "00$mode")),
    )

    private fun findMy(length: String, payload: String) = RadioFacts(
        mfgRecords = listOf(MfgRecord(0x004C, "12$length$payload")),
    )

    private fun fd5a(state: Int, id: String) = RadioFacts(
        serviceData = listOf(
            ServiceDataRecord("FD5A", "%02X010000%s07".format(0x10 or state, id)),
        ),
    )

    private fun radio(
        mac: String,
        name: String,
        fleets: Set<String>,
        serviceUuids: List<String>,
    ) = Sighting(
        key = "BLE:$mac",
        kind = RadioKind.BLE,
        mac = mac,
        name = name,
        rssi = -48,
        rssiMin = -60,
        rssiMax = -48,
        channel = 0,
        frequencyMhz = 2402,
        vendor = null,
        randomized = true,
        hiddenSsid = false,
        serviceUuids = serviceUuids,
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = T0,
        lastSeen = T0,
        hitCount = 1,
        fleetIds = fleets,
        rssiHistory = emptyList(),
        presence = emptyList(),
    )

    private companion object {
        const val MAC = "00:11:22:33:44:55"
        const val T0 = 1_700_000_000_000L
        const val MINUTE = 60_000L
        val P1 = 38.0 to -77.0
        val P2 = 38.001 to -77.0
        val P3 = 38.002 to -77.0
    }
}
