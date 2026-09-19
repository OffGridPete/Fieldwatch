package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TakPublishTest {
    private val remote = Fleet(
        id = "fleet-remote-id",
        name = "Remote ID",
        kind = SignatureClass.DRONE,
        attentionNote = "",
    )
    private val axon = Fleet(
        id = "fleet-axon",
        name = "Axon",
        kind = SignatureClass.LAW_ENFORCEMENT,
        attentionNote = "Body-worn, in-car, dock, or TASER.",
    )
    private val ruuvi = Fleet(
        id = "fleet-ruuvi",
        name = "Ruuvi",
        kind = SignatureClass.BEACON,
    )
    private val fleets = listOf(remote, axon, ruuvi)
    private val on = AppSettings(takEnabled = true, tagLocation = true)

    @Test
    fun payloadLocationRejectsZeroZeroAndOutOfRange() {
        assertFalse(PayloadLocation.validCoord(0.0, 0.0))
        assertFalse(PayloadLocation.validCoord(null, -74.0))
        assertFalse(PayloadLocation.validCoord(40.0, null))
        assertFalse(PayloadLocation.validCoord(Double.NaN, -74.0))
        assertFalse(PayloadLocation.validCoord(91.0, 0.1))
        assertFalse(PayloadLocation.validCoord(1.0, 181.0))
        assertTrue(PayloadLocation.validCoord(40.0, -74.0))
        assertTrue(PayloadLocation.validCoord(-33.9, 151.2))
    }

    @Test
    fun fromDecodedUsesLatitudeLongitudeNotOperator() {
        val loc = PayloadLocation.fromDecoded(
            listOf(
                field("latitude", 40.0),
                field("longitude", -74.0),
                field("alt_geo", 100.0),
                field("op_lat", 40.1),
                field("op_lon", -74.1),
            ),
        )
        assertEquals(40.0, loc.lat!!, 0.0)
        assertEquals(-74.0, loc.lon!!, 0.0)
        assertEquals(100.0, loc.alt!!, 0.0)
        assertEquals(40.1, loc.opLat!!, 0.0)
        assertEquals(-74.1, loc.opLon!!, 0.0)
        assertEquals(40.0 to -74.0, loc.pin())
    }

    @Test
    fun stickyKeepsLatWhenNextPacketHasNone() {
        val location = PayloadLocation(lat = 40.0, lon = -74.0, alt = 100.0)
        val basicId = PayloadLocation()
        val kept = basicId.mergeSticky(location)
        assertEquals(40.0, kept.lat!!, 0.0)
        assertEquals(-74.0, kept.lon!!, 0.0)
        assertEquals(100.0, kept.alt!!, 0.0)
    }

    @Test
    fun stickyZeroZeroDoesNotClobber() {
        val good = PayloadLocation(lat = 40.0, lon = -74.0)
        val unset = PayloadLocation(lat = 0.0, lon = 0.0)
        val kept = unset.mergeSticky(good)
        assertEquals(40.0, kept.lat!!, 0.0)
        assertEquals(-74.0, kept.lon!!, 0.0)
    }

    @Test
    fun remoteIdPinsWithoutExtraAttentionWhenPayloadChipOn() {
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
        )
        assertTrue(TakPublish.selected(drone, on, fleets, emptyList()))
        assertEquals(40.0 to -74.0, TakPublish.pin(drone, on))
        assertTrue(TakPublish.advertisedPin(drone))
        assertTrue(TakPublish.eligible(drone, on, fleets, emptyList()))
    }

    @Test
    fun extraAttentionUsesOperatorGpsWhenNoPayload() {
        val cam = radio(
            fleetIds = setOf("fleet-axon"),
            lat = 37.5,
            lon = -122.2,
        )
        assertTrue(TakPublish.selected(cam, on, fleets, emptyList()))
        assertEquals(37.5 to -122.2, TakPublish.pin(cam, on))
        assertFalse(TakPublish.advertisedPin(cam))
    }

    @Test
    fun gpsOffAndNoPayloadIsNotEligible() {
        val cam = radio(
            fleetIds = setOf("fleet-axon"),
            lat = 37.5,
            lon = -122.2,
        )
        val settings = on.copy(tagLocation = false)
        assertTrue(TakPublish.selected(cam, settings, fleets, emptyList()))
        assertNull(TakPublish.pin(cam, settings))
        assertFalse(TakPublish.eligible(cam, settings, fleets, emptyList()))
    }

    @Test
    fun gpsOffStillPublishesAdvertisedPayload() {
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
            lat = 37.5,
            lon = -122.2,
        )
        val settings = on.copy(tagLocation = false)
        assertEquals(40.0 to -74.0, TakPublish.pin(drone, settings))
        assertTrue(TakPublish.eligible(drone, settings, fleets, emptyList()))
    }

    @Test
    fun privacyModeNeverSelected() {
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
        )
        assertFalse(TakPublish.selected(drone, on.copy(demoMode = true), fleets, emptyList()))
    }

    @Test
    fun masterOffNeverSelected() {
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
        )
        assertFalse(TakPublish.selected(drone, AppSettings(), fleets, emptyList()))
    }

    @Test
    fun ruuviWithoutPayloadChipIsSkipped() {
        val tag = radio(fleetIds = setOf("fleet-ruuvi"), lat = 1.0, lon = 2.0)
        assertFalse(TakPublish.selected(tag, on, fleets, emptyList()))
        val all = on.copy(takAllSignatures = true)
        assertTrue(TakPublish.selected(tag, all, fleets, emptyList()))
    }

    @Test
    fun watchlistNamedRadioNeedsAlertOn() {
        val unmatched = radio(fleetIds = emptySet(), lat = 1.0, lon = 2.0)
        val quiet = WatchTarget(id = "n1", deviceKey = unmatched.key, label = "van", alert = false)
        val loud = quiet.copy(alert = true)
        val settings = on.copy(takWatchlist = true, takAttention = false, takPayloadFix = false)
        assertFalse(TakPublish.selected(unmatched, settings, fleets, listOf(quiet)))
        assertTrue(TakPublish.selected(unmatched, settings, fleets, listOf(loud)))
    }

    @Test
    fun shouldEmitFirstThenIntervalThenMove() {
        assertTrue(TakPublish.shouldEmit(null, null, null, 1_000L, 40.0, -74.0))
        assertFalse(TakPublish.shouldEmit(1_000L, 40.0, -74.0, 5_000L, 40.0, -74.0))
        assertTrue(TakPublish.shouldEmit(1_000L, 40.0, -74.0, 12_000L, 40.0, -74.0))
        assertTrue(TakPublish.shouldEmit(1_000L, 40.0, -74.0, 2_000L, 40.001, -74.0))
    }

    @Test
    fun heardHereMovesOnFirstAndLouderKeepsPeakOnRefresh() {
        assertEquals(TakHeardHere.MOVE, TakPublish.heardHereAction(null, null, 1_000L, -80))
        assertEquals(TakHeardHere.MOVE, TakPublish.heardHereAction(1_000L, Int.MIN_VALUE, 1_100L, -80))
        assertEquals(TakHeardHere.SKIP, TakPublish.heardHereAction(1_000L, -60, 5_000L, -70))
        assertEquals(TakHeardHere.SKIP, TakPublish.heardHereAction(1_000L, -60, 5_000L, -60))
        assertEquals(TakHeardHere.MOVE, TakPublish.heardHereAction(1_000L, -60, 2_000L, -50))
        assertEquals(TakHeardHere.REFRESH, TakPublish.heardHereAction(1_000L, -60, 12_000L, -90))
        assertEquals(TakHeardHere.REFRESH, TakPublish.heardHereAction(1_000L, -60, 12_000L, -60))
        assertEquals(TakHeardHere.MOVE, TakPublish.heardHereAction(1_000L, -60, 12_000L, -40))
    }

    @Test
    fun extraAttentionMarkerIsHeardHereNotAdvertised() {
        val cam = radio(fleetIds = setOf("fleet-axon"), lat = 37.5, lon = -122.2)
        val marks = TakPublish.markers(cam, on, fleets, emptyList())
        assertEquals(1, marks.size)
        assertFalse(marks[0].advertised)
        assertFalse(marks[0].pilot)
    }

    @Test
    fun watchlistHeardHereIsNotAdvertised() {
        val unmatched = radio(fleetIds = emptySet(), lat = 1.0, lon = 2.0)
        val loud = WatchTarget(id = "n1", deviceKey = unmatched.key, label = "van", alert = true)
        val settings = on.copy(takWatchlist = true, takAttention = false, takPayloadFix = false)
        val marks = TakPublish.markers(unmatched, settings, fleets, listOf(loud))
        assertEquals(1, marks.size)
        assertFalse(marks[0].advertised)
        assertFalse(marks[0].pilot)
    }

    @Test
    fun cotXmlUsesAdvertisedDroneTypeAndEscapes() {
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
            payloadAlt = 100.0,
            name = "RID <test>",
        )
        val xml = CotEvent.xml(
            device = drone,
            fleets = fleets,
            watchlist = emptyList(),
            lat = 40.0,
            lon = -74.0,
            advertised = true,
            now = 0L,
        )
        assertTrue(xml.contains("uid=\"FIELDWATCH-BLE-AABBCCDDEE01\""))
        assertTrue(xml.contains("type=\"a-u-A-M-H-Q\""))
        assertTrue(xml.contains("lat=\"40\""))
        assertTrue(xml.contains("lon=\"-74\""))
        assertTrue(xml.contains("hae=\"100\""))
        assertTrue(xml.contains("callsign=\"Remote ID\""))
        assertTrue(xml.contains("advertised position"))
        assertTrue(xml.contains("__group name=\"Yellow\""))
        assertTrue(xml.contains("stale="))
        assertFalse(xml.contains(" (here)"))
    }

    @Test
    fun xmlEscapeAmpersandAndTags() {
        assertEquals("A &amp; &lt;B&gt; &quot;c&quot;", CotEvent.xmlEscape("A & <B> \"c\""))
    }

    @Test
    fun cotHeardHereIsGroundUnknown() {
        val cam = radio(fleetIds = setOf("fleet-axon"), lat = 37.5, lon = -122.2)
        val xml = CotEvent.xml(
            device = cam,
            fleets = fleets,
            watchlist = emptyList(),
            lat = 37.5,
            lon = -122.2,
            advertised = false,
            now = 0L,
        )
        assertTrue(xml.contains("type=\"a-u-G\""))
        assertTrue(xml.contains("heard here (operator GPS)"))
        assertTrue(xml.contains("callsign=\"Axon (here)\""))
        assertTrue(xml.contains("__group name=\"Maroon\""))
        assertTrue(xml.contains("Extra attention"))
    }

    @Test
    fun uidUsesStickyUasIdNotMac() {
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
            payloadUasId = "TESTSERIAL1234567890",
        )
        assertEquals("FIELDWATCH-RID-TESTSERIAL1234567890", CotEvent.uid(drone))
        assertEquals("FIELDWATCH-PILOT-TESTSERIAL1234567890", CotEvent.pilotUid(drone))
        val xml = CotEvent.xml(
            device = drone,
            fleets = fleets,
            watchlist = emptyList(),
            lat = 40.0,
            lon = -74.0,
            advertised = true,
            now = 0L,
        )
        assertTrue(xml.contains("uid=\"FIELDWATCH-RID-TESTSERIAL1234567890\""))
        assertTrue(xml.contains("callsign=\"TESTSERIAL1234567890\""))
        assertFalse(xml.contains("FIELDWATCH-BLE-"))
    }

    @Test
    fun uidFallsBackToMacWithoutUasId() {
        val drone = radio(fleetIds = setOf("fleet-remote-id"), payloadLat = 40.0, payloadLon = -74.0)
        assertEquals("FIELDWATCH-BLE-AABBCCDDEE01", CotEvent.uid(drone))
    }

    @Test
    fun selfIdBeatsUasIdForCallsign() {
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
            payloadUasId = "TESTSERIAL1234567890",
            payloadSelfId = "N12345",
        )
        assertEquals("N12345", TakPublish.callsign(drone, fleets, emptyList()))
    }

    @Test
    fun pilotMarkerAndLink() {
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
            payloadOpLat = 40.1,
            payloadOpLon = -74.1,
            payloadUasId = "TESTSERIAL1234567890",
        )
        val marks = TakPublish.markers(drone, on, fleets, emptyList())
        assertEquals(2, marks.size)
        assertEquals("FIELDWATCH-RID-TESTSERIAL1234567890", marks[0].uid)
        assertEquals("FIELDWATCH-PILOT-TESTSERIAL1234567890", marks[1].uid)
        assertEquals(40.1, marks[1].lat, 0.0)
        val air = CotEvent.xml(
            device = drone,
            fleets = fleets,
            watchlist = emptyList(),
            lat = 40.0,
            lon = -74.0,
            advertised = true,
            now = 0L,
        )
        assertTrue(air.contains("link uid=\"FIELDWATCH-PILOT-TESTSERIAL1234567890\""))
        val pilot = CotEvent.xml(
            device = drone,
            fleets = fleets,
            watchlist = emptyList(),
            lat = 40.1,
            lon = -74.1,
            advertised = true,
            now = 0L,
            pilot = true,
        )
        assertTrue(pilot.contains("uid=\"FIELDWATCH-PILOT-TESTSERIAL1234567890\""))
        assertTrue(pilot.contains("callsign=\"Pilot · TESTSERIAL1234567890\""))
        assertTrue(pilot.contains("__group name=\"Orange\""))
        assertTrue(pilot.contains("operator (pilot) position"))
        assertTrue(pilot.contains("link uid=\"FIELDWATCH-RID-TESTSERIAL1234567890\""))
        assertFalse(pilot.contains(" (here)"))
    }

    @Test
    fun keepUidsHoldsGpsBlipAndDropsGone() {
        val cam = radio(fleetIds = setOf("fleet-axon"), lat = 37.5, lon = -122.2)
        val uid = CotEvent.uid(cam)
        val prev = listOf(TakSent(uid, cam.key, 1L, 37.5, -122.2))
        val blip = cam.copy(latitude = null, longitude = null)
        val keepBlip = TakPublish.keepUids(listOf(blip), on, fleets, emptyList(), prev)
        assertTrue(uid in keepBlip)
        val taggingOff = on.copy(tagLocation = false)
        val keepOff = TakPublish.keepUids(listOf(blip), taggingOff, fleets, emptyList(), prev)
        assertFalse(uid in keepOff)
        val gone = cam.copy(gone = true)
        val keepGone = TakPublish.keepUids(listOf(gone), on, fleets, emptyList(), prev)
        assertFalse(uid in keepGone)
    }

    @Test
    fun keepUidsDoesNotDropUnsentEligibleWhenCapped() {
        val cam = radio(fleetIds = setOf("fleet-axon"), lat = 37.5, lon = -122.2)
        val uid = CotEvent.uid(cam)
        val keep = TakPublish.keepUids(listOf(cam), on, fleets, emptyList(), emptyList())
        assertTrue(uid in keep)
    }

    @Test
    fun uasIdChangeTombstonesMacUid() {
        val macKeyed = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
        )
        val stable = macKeyed.copy(payloadUasId = "TESTSERIAL1234567890")
        val oldUid = CotEvent.uid(macKeyed)
        val newUid = CotEvent.uid(stable)
        assertEquals("FIELDWATCH-BLE-AABBCCDDEE01", oldUid)
        assertEquals("FIELDWATCH-RID-TESTSERIAL1234567890", newUid)
        val prev = listOf(TakSent(oldUid, macKeyed.key, 1L, 40.0, -74.0))
        val keep = TakPublish.keepUids(listOf(stable), on, fleets, emptyList(), prev)
        assertTrue(newUid in keep)
        assertFalse(oldUid in keep)
    }

    @Test
    fun tombstoneStaleEqualsNow() {
        val xml = CotEvent.tombstoneXml("FIELDWATCH-BLE-AABBCCDDEE01", 37.5, -122.2, 0L)
        assertTrue(xml.contains("uid=\"FIELDWATCH-BLE-AABBCCDDEE01\""))
        assertTrue(xml.contains("stale=\"1970-01-01T00:00:00.000Z\""))
        assertTrue(xml.contains("gone"))
    }

    @Test
    fun udpPresets() {
        assertEquals(TakUdpPreset.THIS_PHONE, TakPublish.udpPreset("127.0.0.1", 10011))
        assertEquals(TakUdpPreset.LAN_MULTICAST, TakPublish.udpPreset("239.2.3.1", 6969))
        assertEquals(TakUdpPreset.CUSTOM, TakPublish.udpPreset("239.2.3.1", 10011))
        assertEquals(TakUdpPreset.CUSTOM, TakPublish.udpPreset("192.168.0.9", 10011))
        assertEquals("127.0.0.1" to 10011, TakPublish.applyPreset(TakUdpPreset.THIS_PHONE))
        assertEquals("239.2.3.1" to 6969, TakPublish.applyPreset(TakUdpPreset.LAN_MULTICAST))
    }

    @Test
    fun stickyUasIdSurvivesLocationOnlyPacket() {
        val basic = PayloadLocation.fromDecoded(listOf(textField("uas_id", "TESTSERIAL1234567890")))
        assertEquals("TESTSERIAL1234567890", basic.uasId)
        val location = PayloadLocation.fromDecoded(
            listOf(field("latitude", 40.0), field("longitude", -74.0)),
        )
        val kept = location.mergeSticky(basic)
        assertEquals("TESTSERIAL1234567890", kept.uasId)
        assertEquals(40.0, kept.lat!!, 0.0)
    }

    private fun field(id: String, n: Double) = DecodedFieldValue(
        fleetId = "f",
        fleetName = "F",
        id = id,
        label = id,
        display = n.toString(),
        offset = 0,
        length = 4,
        number = n,
    )

    private fun radio(
        fleetIds: Set<String>,
        payloadLat: Double? = null,
        payloadLon: Double? = null,
        payloadAlt: Double? = null,
        payloadOpLat: Double? = null,
        payloadOpLon: Double? = null,
        payloadUasId: String? = null,
        payloadSelfId: String? = null,
        lat: Double? = null,
        lon: Double? = null,
        name: String = "",
    ) = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:01",
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:01",
        name = name,
        rssi = -60,
        rssiMin = -60,
        rssiMax = -60,
        channel = 0,
        frequencyMhz = 2402,
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
        fleetIds = fleetIds,
        rssiHistory = emptyList(),
        presence = emptyList(),
        latitude = lat,
        longitude = lon,
        payloadLat = payloadLat,
        payloadLon = payloadLon,
        payloadAlt = payloadAlt,
        payloadOpLat = payloadOpLat,
        payloadOpLon = payloadOpLon,
        payloadUasId = payloadUasId,
        payloadSelfId = payloadSelfId,
    )

    private fun textField(id: String, text: String) = DecodedFieldValue(
        fleetId = "f",
        fleetName = "F",
        id = id,
        label = id,
        display = text,
        offset = 0,
        length = text.length,
    )
}
