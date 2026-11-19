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
    private val tag = "AB23"

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
        val marks = TakPublish.markers(cam, on, fleets, emptyList(), tag)
        assertEquals(1, marks.size)
        assertFalse(marks[0].advertised)
        assertFalse(marks[0].pilot)
    }

    @Test
    fun watchlistHeardHereIsNotAdvertised() {
        val unmatched = radio(fleetIds = emptySet(), lat = 1.0, lon = 2.0)
        val loud = WatchTarget(id = "n1", deviceKey = unmatched.key, label = "van", alert = true)
        val settings = on.copy(takWatchlist = true, takAttention = false, takPayloadFix = false)
        val marks = TakPublish.markers(unmatched, settings, fleets, listOf(loud), tag)
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
        
            tag = tag,
        )
        assertTrue(xml.contains("uid=\"FIELDWATCH-AB23-BLE-AABBCCDDEE01\""))
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
    fun cotXmlAddsTrackForAdvertisedAircraft() {
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
            payloadAlt = 100.0,
            payloadHeading = 270.0,
            payloadSpeed = 12.5,
        )
        val xml = CotEvent.xml(
            device = drone,
            fleets = fleets,
            watchlist = emptyList(),
            lat = 40.0,
            lon = -74.0,
            advertised = true,
            now = 0L,
        
            tag = tag,
        )
        assertTrue(xml.contains("<track course=\"270\" speed=\"12.5\"/>"))
        val here = CotEvent.xml(
            device = drone.copy(payloadHeading = 90.0, payloadSpeed = 5.0),
            fleets = fleets,
            watchlist = emptyList(),
            lat = 1.0,
            lon = 2.0,
            advertised = false,
            now = 0L,
        
            tag = tag,
        )
        assertFalse(here.contains("<track"))
    }

    @Test
    fun xmlEscapeAmpersandAndTags() {
        assertEquals("A &amp; &lt;B&gt; &quot;c&quot;", CotEvent.xmlEscape("A & <B> \"c\""))
        assertEquals("a&#10;b", CotEvent.xmlEscape("a\nb"))
    }

    @Test
    fun remarksAreAReadableCard() {
        val cam = radio(fleetIds = setOf("fleet-axon"), lat = 37.5, lon = -122.2)
        val text = CotEvent.remarks(cam, fleets, advertised = false)
        val lines = text.split('\n')
        assertEquals("Axon (here)", lines[0])
        assertEquals("BLE  AA:BB:CC:DD:EE:01  -60 dBm", lines[1])
        assertEquals("heard here (operator GPS)", lines[2])
        assertTrue(text.contains("Extra attention: "))
        assertTrue(text.contains("Body-worn"))
        assertFalse(text.contains("Extra attention (Axon)"))
        val xml = CotEvent.xml(
            device = cam,
            fleets = fleets,
            watchlist = emptyList(),
            lat = 37.5,
            lon = -122.2,
            advertised = false,
            now = 0L,
        
            tag = tag,
        )
        assertTrue(xml.contains("&#10;"))
        assertTrue(xml.contains("Axon (here)&#10;BLE  AA:BB:CC:DD:EE:01  -60 dBm"))
        assertFalse(xml.contains("Fieldwatch · "))
    }

    @Test
    fun advertisedRemarksKeepPayloadAndSkipRepeatName() {
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
            payloadUasId = "TESTSERIAL1234567890",
            payloadSelfId = "N12345",
        )
        val text = CotEvent.remarks(drone, fleets, advertised = true)
        val lines = text.split('\n')
        assertEquals("N12345", lines[0])
        assertEquals("BLE  AA:BB:CC:DD:EE:01  -60 dBm", lines[1])
        assertTrue(lines[2].startsWith("advertised position"))
        assertTrue(lines[2].contains("UAS ID TESTSERIAL1234567890"))
        assertTrue(text.contains("Remote ID"))
        val pilot = CotEvent.remarks(drone, fleets, advertised = true, pilot = true)
        assertEquals("Operator · TESTSERIAL1234567890", pilot.lineSequence().first())
        assertTrue(pilot.contains("operator position"))
        assertFalse(pilot.contains("operator (pilot) position"))
    }

    @Test
    fun swarmRemarkStaysOnTheAircraftCard() {
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
            payloadUasId = "1581F6Z3C24A00001001",
            payloadSelfId = "N12345",
        )
        val sentence = "Part of a drone swarm at 12:00 UTC"
        assertEquals("a-u-A-M-H-Q", CotEvent.type(drone, fleets, advertised = true))
        val text = CotEvent.remarks(drone, fleets, advertised = true, swarmNote = sentence)
        assertEquals(sentence, text.lineSequence().last())
        assertFalse(sentence.contains("1581F6Z3C24A00001001"))
        assertTrue(text.contains("UAS ID 1581F6Z3C24A00001001"))
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
        
            tag = tag,
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
        assertEquals("FIELDWATCH-RID-TESTSERIAL1234567890", CotEvent.uid(drone, tag))
        assertEquals("FIELDWATCH-PILOT-TESTSERIAL1234567890", CotEvent.pilotUid(drone, tag))
        val xml = CotEvent.xml(
            device = drone,
            fleets = fleets,
            watchlist = emptyList(),
            lat = 40.0,
            lon = -74.0,
            advertised = true,
            now = 0L,
        
            tag = tag,
        )
        assertTrue(xml.contains("uid=\"FIELDWATCH-RID-TESTSERIAL1234567890\""))
        assertTrue(xml.contains("callsign=\"TESTSERIAL1234567890\""))
        assertFalse(xml.contains("FIELDWATCH-BLE-"))
    }

    @Test
    fun uidFallsBackToMacWithoutUasId() {
        val drone = radio(fleetIds = setOf("fleet-remote-id"), payloadLat = 40.0, payloadLon = -74.0)
        assertEquals("FIELDWATCH-AB23-BLE-AABBCCDDEE01", CotEvent.uid(drone, tag))
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
        val marks = TakPublish.markers(drone, on, fleets, emptyList(), tag)
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
        
            tag = tag,
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
        
            tag = tag,
        )
        assertTrue(pilot.contains("uid=\"FIELDWATCH-PILOT-TESTSERIAL1234567890\""))
        assertTrue(pilot.contains("callsign=\"Operator · TESTSERIAL1234567890\""))
        assertTrue(pilot.contains("__group name=\"Orange\""))
        assertTrue(pilot.contains("operator position"))
        assertFalse(pilot.contains("operator (pilot) position"))
        assertTrue(pilot.contains("hae=\"9999999\""))
        assertTrue(pilot.contains("link uid=\"FIELDWATCH-RID-TESTSERIAL1234567890\""))
        assertFalse(pilot.contains(" (here)"))
    }

    @Test
    fun operatorPinFollowsLocationType() {
        val live = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
            payloadOpLat = 40.1,
            payloadOpLon = -74.1,
            payloadOpKind = OperatorLoc.LIVE,
            payloadOpAlt = 50.0,
            payloadUasId = "TESTSERIAL1234567890",
        )
        val liveXml = pilotXml(live)
        assertTrue(liveXml.contains("callsign=\"Pilot · TESTSERIAL1234567890\""))
        assertTrue(liveXml.contains("operator (pilot) position"))
        assertTrue(liveXml.contains("hae=\"50\""))
        val takeoff = live.copy(payloadOpKind = OperatorLoc.TAKEOFF, payloadOpAlt = null)
        val takeoffXml = pilotXml(takeoff)
        assertTrue(takeoffXml.contains("callsign=\"Takeoff · TESTSERIAL1234567890\""))
        assertTrue(takeoffXml.contains("takeoff position"))
        assertFalse(takeoffXml.contains("operator (pilot) position"))
        assertTrue(takeoffXml.contains("hae=\"9999999\""))
        val fixed = live.copy(payloadOpKind = OperatorLoc.FIXED, payloadOpAlt = -674.5)
        val fixedXml = pilotXml(fixed)
        assertTrue(fixedXml.contains("callsign=\"Fixed · TESTSERIAL1234567890\""))
        assertTrue(fixedXml.contains("fixed operator position"))
        assertTrue(fixedXml.contains("hae=\"-674.5\""))
        val unknownAlt = live.copy(payloadOpAlt = -1000.0)
        assertTrue(pilotXml(unknownAlt).contains("hae=\"9999999\""))
    }

    @Test
    fun keepUidsHoldsGpsBlipAndDropsGone() {
        val cam = radio(fleetIds = setOf("fleet-axon"), lat = 37.5, lon = -122.2)
        val uid = CotEvent.uid(cam, tag)
        val prev = listOf(TakSent(uid, cam.key, 1L, 37.5, -122.2))
        val blip = cam.copy(latitude = null, longitude = null)
        val keepBlip = TakPublish.keepUids(listOf(blip), on, fleets, emptyList(), prev, tag)
        assertTrue(uid in keepBlip)
        val taggingOff = on.copy(tagLocation = false)
        val keepOff = TakPublish.keepUids(listOf(blip), taggingOff, fleets, emptyList(), prev, tag)
        assertFalse(uid in keepOff)
        val gone = cam.copy(gone = true)
        val keepGone = TakPublish.keepUids(listOf(gone), on, fleets, emptyList(), prev, tag)
        assertFalse(uid in keepGone)
    }

    @Test
    fun keepUidsDoesNotDropUnsentEligibleWhenCapped() {
        val cam = radio(fleetIds = setOf("fleet-axon"), lat = 37.5, lon = -122.2)
        val uid = CotEvent.uid(cam, tag)
        val keep = TakPublish.keepUids(listOf(cam), on, fleets, emptyList(), emptyList(), tag)
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
        val oldUid = CotEvent.uid(macKeyed, tag)
        val newUid = CotEvent.uid(stable, tag)
        assertEquals("FIELDWATCH-AB23-BLE-AABBCCDDEE01", oldUid)
        assertEquals("FIELDWATCH-RID-TESTSERIAL1234567890", newUid)
        val prev = listOf(TakSent(oldUid, macKeyed.key, 1L, 40.0, -74.0))
        val keep = TakPublish.keepUids(listOf(stable), on, fleets, emptyList(), prev, tag)
        assertTrue(newUid in keep)
        assertFalse(oldUid in keep)
    }

    @Test
    fun gnssLineFollowsTheChosenWindow() {
        val active = GnssNotice(false, "Possible GNSS interference (Medium)", "", "Possible GNSS interference · Medium")
        val ended = active.copy(line = "Possible GNSS interference ended 15:04", ended = true)
        val low = GnssLive(
            kind = GnssKind.INTERFERENCE,
            confidence = GnssConfidence.LOW,
            startedWall = 1L,
            detail = "GPS L1",
            adapted = false,
            rose = false,
        )
        assertNull(TakGnss.line(TakGnssSend.OFF, active, listOf(low)))
        assertEquals(active.line, TakGnss.line(TakGnssSend.ALERT, active, emptyList()))
        assertNull(TakGnss.line(TakGnssSend.ALERT, ended, emptyList()))
        assertEquals(ended.line, TakGnss.line(TakGnssSend.RED_LINE, ended, emptyList()))
        assertNull(TakGnss.line(TakGnssSend.ANY, ended, emptyList()))
        assertEquals("Possible GNSS interference · Low", TakGnss.line(TakGnssSend.ANY, null, listOf(low)))
        assertEquals(TakGnssSend.OFF, AppSettings().takGnss)
        assertEquals(TakGnssHold.HOUR, AppSettings().takGnssHold)
        assertEquals(TakGnssHold.HOUR, AppSettings().takRadioHold)
    }

    @Test
    fun gnssMarkIsAPinThenAnOvalHalfAsWide() {
        val startLat = 40.0
        val startLon = -74.0
        val pin = TakGnss.plot(startLat, startLon, startLat, -74.0004)
        assertTrue(Geo.meters(startLat, startLon, startLat, -74.0004) < TakGnss.SPAN_PIN_M)
        assertTrue(pin.pin)
        assertEquals(startLat, pin.lat, 1e-9)
        assertEquals(startLon, pin.lon, 1e-9)

        val endLon = -73.998
        val span = Geo.meters(startLat, startLon, startLat, endLon)
        val oval = TakGnss.plot(startLat, startLon, startLat, endLon)
        assertTrue(span >= TakGnss.SPAN_PIN_M)
        assertFalse(oval.pin)
        assertEquals(span / 2.0, oval.semiMajorM, 0.01)
        assertEquals(span / 4.0, oval.semiMinorM, 0.01)
        assertEquals(oval.semiMajorM, oval.semiMinorM * 2.0, 0.01)
        assertEquals(Geo.bearingDegrees(startLat, startLon, startLat, endLon), oval.angleDeg, 0.05)
        val (midLat, midLon) = Geo.midpoint(startLat, startLon, startLat, endLon)
        assertEquals(midLat, oval.lat, 1e-6)
        assertEquals(midLon, oval.lon, 1e-6)
    }

    @Test
    fun gnssMarkFreezesWhenTheHitEndsAndClearsWhenOff() {
        val live = GnssLive(
            kind = GnssKind.INTERFERENCE,
            confidence = GnssConfidence.MEDIUM,
            startedWall = 50L,
            detail = "GPS L1",
            adapted = false,
            rose = false,
        )
        val active = GnssNotice(false, "Possible GNSS interference (Medium)", "", "Possible GNSS interference · Medium")
        val opened = TakGnss.plan(
            open = null,
            mode = TakGnssSend.ALERT,
            notice = active,
            lives = listOf(live),
            lat = 40.0,
            lon = -74.0,
            now = 1_000L,
            hold = TakGnssHold.HOUR,
            tag = tag,
        )
        assertTrue(opened.send)
        assertTrue(opened.plot!!.pin)
        assertEquals("FIELDWATCH-GNSS-AB23-50", opened.track!!.uid)
        assertTrue(opened.remarks.contains("Where this phone was"))
        assertFalse(opened.remarks.contains("40.0"))

        val quiet = TakGnss.plan(
            open = opened.track,
            mode = TakGnssSend.ALERT,
            notice = active,
            lives = listOf(live),
            lat = 40.0,
            lon = -74.0,
            now = 2_000L,
            hold = TakGnssHold.HOUR,
            tag = tag,
        )
        assertFalse(quiet.send)

        val stretched = TakGnss.plan(
            open = opened.track,
            mode = TakGnssSend.ALERT,
            notice = active,
            lives = listOf(live),
            lat = 40.0,
            lon = -73.998,
            now = 2_000L,
            hold = TakGnssHold.HOURS_6,
            tag = tag,
        )
        assertTrue(stretched.send)
        assertFalse(stretched.plot!!.pin)
        assertEquals(TakGnssHold.HOURS_6.staleMs(), stretched.staleMs)

        val ended = active.copy(line = "Possible GNSS interference ended 15:04", ended = true)
        val frozen = TakGnss.plan(
            open = stretched.track,
            mode = TakGnssSend.RED_LINE,
            notice = ended,
            lives = emptyList(),
            lat = 40.01,
            lon = -73.99,
            now = 30_000L,
            hold = TakGnssHold.HOUR,
            tag = tag,
        )
        assertTrue(frozen.send)
        assertFalse(frozen.track!!.open)
        assertEquals(stretched.plot!!.semiMajorM, frozen.plot!!.semiMajorM, 0.01)
        assertEquals(40.0, frozen.plot!!.lat, 1e-4)
        assertEquals(TakGnssHold.HOUR.staleMs(), frozen.staleMs)

        val off = TakGnss.plan(
            open = null,
            mode = TakGnssSend.OFF,
            notice = null,
            lives = emptyList(),
            lat = 40.0,
            lon = -74.0,
            now = 40_000L,
            hold = TakGnssHold.UNTIL_DELETED,
            tag = tag,
            hasMarks = true,
        )
        assertTrue(off.clearAll)
        assertFalse(TakGnss.growing(TakGnssSend.ALERT, ended, emptyList()))
    }

    @Test
    fun floodNoteStartsWhenTheFloodIsDetected() {
        val asking = PairingFlood.Notice(
            showDialog = true,
            popupCount = 12,
            nameCount = 0,
            families = listOf("Fast Pair"),
            medianRssi = -40,
        )
        val wifi = PairingFlood.Notice(
            showDialog = false,
            popupCount = 8,
            nameCount = 0,
            families = emptyList(),
            medianRssi = -55,
            wifi = true,
        )
        assertNull(TakFlood.line(TakFloodSend.OFF, asking))
        assertEquals("Pairing flood · 12 new addresses · about -40 dBm", TakFlood.line(TakFloodSend.ON, asking))
        assertEquals("Wi-Fi beacon flood · 8 new names · about -55 dBm", TakFlood.line(TakFloodSend.ON, wifi))
        assertEquals(TakFloodSend.OFF, AppSettings().takFlood)
        assertEquals("On", TakFloodSend.ON.label())
        val saved = SettingsExchange.json.decodeFromString(
            TakFloodSend.serializer(),
            "\"RED_LINE\"",
        )
        assertEquals(TakFloodSend.ON, saved)
        val xml = CotEvent.selfXml(40.0, -74.0, 0L, tag, "Fieldwatch-AB23", remarksExtra = TakFlood.line(TakFloodSend.ON, asking))
        assertTrue(xml.contains("Pairing flood"))
        assertFalse(xml.contains("Flipper"))
        assertFalse(xml.contains("latitude"))
    }

    @Test
    fun selfMarkerCarriesTheGnssLineAndNoCoordinatesInRemarks() {
        val plain = CotEvent.selfXml(40.0, -74.0, 0L, tag, "Fieldwatch-AB23")
        assertTrue(plain.contains("uid=\"FIELDWATCH-SELF-AB23\""))
        assertTrue(plain.contains("callsign=\"Fieldwatch-AB23\""))
        assertTrue(plain.contains("Fieldwatch TAK heartbeat (this phone)"))
        assertFalse(plain.contains("GNSS"))
        val xml = CotEvent.selfXml(40.0, -74.0, 0L, tag, "Fieldwatch-AB23", remarksExtra = "Possible GNSS interference · Medium")
        assertTrue(xml.contains("Possible GNSS interference · Medium"))
        assertTrue(xml.contains("&#10;"))
        assertFalse(xml.contains("latitude"))
        assertFalse(xml.contains("longitude"))
        assertTrue(xml.contains("lat=\"40\""))
    }

    @Test
    fun tombstoneStaleEqualsNow() {
        val xml = CotEvent.tombstoneXml("FIELDWATCH-AB23-BLE-AABBCCDDEE01", 37.5, -122.2, 0L)
        assertTrue(xml.contains("uid=\"FIELDWATCH-AB23-BLE-AABBCCDDEE01\""))
        assertTrue(xml.contains("stale=\"1970-01-01T00:00:00.000Z\""))
        assertTrue(xml.contains("gone"))
        val named = CotEvent.tombstoneXml(
            "FIELDWATCH-AB23-BLE-AABBCCDDEE01",
            37.5,
            -122.2,
            0L,
            callsign = "Axon (here)",
            remarks = "Axon (here)\nBLE",
        )
        assertTrue(named.contains("callsign=\"Axon (here)\""))
        assertFalse(named.contains("Fieldwatch"))
    }

    @Test
    fun departedRadioKeepsItsNameForTheHold() {
        val cam = radio(fleetIds = setOf("fleet-axon"), lat = 37.5, lon = -122.2)
        val xml = CotEvent.xml(
            device = cam,
            fleets = fleets,
            watchlist = emptyList(),
            lat = 37.5,
            lon = -122.2,
            advertised = false,
            now = 0L,
            staleMs = TakGnssHold.HOUR.staleMs(),
        
            tag = tag,
        )
        assertTrue(xml.contains("callsign=\"Axon (here)\""))
        assertTrue(xml.contains("stale=\"1970-01-01T01:00:00.000Z\""))
        assertFalse(xml.contains("Fieldwatch"))
        val face = CotEvent.face(cam, fleets, emptyList(), advertised = false, tag = tag)
        val again = CotEvent.render(
            uid = CotEvent.uid(cam, tag),
            face = face,
            lat = 37.5,
            lon = -122.2,
            now = 0L,
            staleMs = TakGnssHold.HOURS_6.staleMs(),
        )
        assertTrue(again.contains("callsign=\"Axon (here)\""))
        assertTrue(again.contains("stale=\"1970-01-01T06:00:00.000Z\""))
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
    fun twoInstallsAreDistinctAndShareARemoteId() {
        val cam = radio(fleetIds = setOf("fleet-axon"), lat = 37.5, lon = -122.2)
        val other = "CD34"
        assertEquals("FIELDWATCH-SELF-AB23", CotEvent.selfUid(tag))
        assertEquals("FIELDWATCH-SELF-CD34", CotEvent.selfUid(other))
        assertEquals("FIELDWATCH-AB23-BLE-AABBCCDDEE01", CotEvent.uid(cam, tag))
        assertEquals("FIELDWATCH-CD34-BLE-AABBCCDDEE01", CotEvent.uid(cam, other))
        assertEquals("FIELDWATCH-AB23-HERE-BLE-AABBCCDDEE01", CotEvent.hereUid(cam, tag))
        assertEquals("FIELDWATCH-CD34-HERE-BLE-AABBCCDDEE01", CotEvent.hereUid(cam, other))
        val live = GnssLive(
            kind = GnssKind.INTERFERENCE,
            confidence = GnssConfidence.MEDIUM,
            startedWall = 50L,
            detail = "GPS L1",
            adapted = false,
            rose = false,
        )
        val notice = GnssNotice(false, "Possible GNSS interference (Medium)", "", "Possible GNSS interference · Medium")
        val opened = TakGnss.plan(null, TakGnssSend.ALERT, notice, listOf(live), 40.0, -74.0, 1_000L, TakGnssHold.HOUR, tag)
        val otherOpen = TakGnss.plan(null, TakGnssSend.ALERT, notice, listOf(live), 40.0, -74.0, 1_000L, TakGnssHold.HOUR, other)
        assertEquals("FIELDWATCH-GNSS-AB23-50", opened.track!!.uid)
        assertEquals("FIELDWATCH-GNSS-CD34-50", otherOpen.track!!.uid)
        val drone = radio(
            fleetIds = setOf("fleet-remote-id"),
            payloadLat = 40.0,
            payloadLon = -74.0,
            payloadOpLat = 40.1,
            payloadOpLon = -74.1,
            payloadUasId = "TESTUAS1",
        )
        assertEquals("FIELDWATCH-RID-TESTUAS1", CotEvent.uid(drone, tag))
        assertEquals(CotEvent.uid(drone, tag), CotEvent.uid(drone, other))
        assertEquals("FIELDWATCH-PILOT-TESTUAS1", CotEvent.pilotUid(drone, tag))
        assertEquals(CotEvent.pilotUid(drone, tag), CotEvent.pilotUid(drone, other))
        val macPilot = drone.copy(payloadUasId = null)
        assertEquals("FIELDWATCH-AB23-PILOT-BLE-AABBCCDDEE01", CotEvent.pilotUid(macPilot, tag))
        assertEquals("FIELDWATCH-CD34-PILOT-BLE-AABBCCDDEE01", CotEvent.pilotUid(macPilot, other))
        val macXml = CotEvent.xml(
            device = cam,
            fleets = fleets,
            watchlist = emptyList(),
            lat = 37.5,
            lon = -122.2,
            advertised = false,
            now = 0L,
            tag = tag,
            viaCallsign = TakInstall.viaLine(CotEvent.uid(cam, tag), tag, "Fieldwatch-AB23"),
        )
        assertTrue(macXml.contains("via Fieldwatch-AB23"))
        val shared = CotEvent.xml(
            device = drone,
            fleets = fleets,
            watchlist = emptyList(),
            lat = 40.0,
            lon = -74.0,
            advertised = true,
            now = 0L,
            tag = tag,
            viaCallsign = TakInstall.viaLine(CotEvent.uid(drone, tag), tag, "Fieldwatch-AB23"),
        )
        assertFalse(shared.contains("via "))
        assertEquals("Scout 1", TakInstall.callsign("Scout 1", other))
        assertEquals("Fieldwatch-CD34", TakInstall.callsign("", other))
        assertEquals("Scout 1", TakInstall.callsign("Scout 1", tag))
    }

    @Test
    fun legacyDeleteNamesOnlyTheOldSelfMarker() {
        assertEquals("FIELDWATCH-SELF", TakInstall.legacySelfDelete(false, true, false))
        assertNull(TakInstall.legacySelfDelete(true, true, false))
        assertNull(TakInstall.legacySelfDelete(false, false, false))
        assertNull(TakInstall.legacySelfDelete(false, true, true))
        val xml = CotEvent.tombstoneXml("FIELDWATCH-SELF", 40.0, -74.0, 0L, callsign = "Fieldwatch")
        assertTrue(xml.contains("uid=\"FIELDWATCH-SELF\""))
        assertFalse(xml.contains("FIELDWATCH-SELF-"))
        assertTrue(xml.contains("stale=\"1970-01-01T00:00:00.000Z\""))
        assertEquals(1, Regex("""uid="FIELDWATCH-SELF"""").findAll(xml).count())
    }

    @Test
    fun phoneCallsignIsXmlEscaped() {
        val xml = CotEvent.selfXml(40.0, -74.0, 0L, tag, "A & <B> \"c\"")
        assertTrue(xml.contains("uid=\"FIELDWATCH-SELF-AB23\""))
        assertTrue(xml.contains("callsign=\"A &amp; &lt;B&gt; &quot;c&quot;\""))
        assertFalse(xml.contains("callsign=\"A & <B>"))
        val via = CotEvent.xml(
            device = radio(fleetIds = setOf("fleet-axon"), lat = 37.5, lon = -122.2),
            fleets = fleets,
            watchlist = emptyList(),
            lat = 37.5,
            lon = -122.2,
            advertised = false,
            now = 0L,
            tag = tag,
            viaCallsign = "A & B",
        )
        assertTrue(via.contains("via A &amp; B"))
    }

    @Test
    fun missingTagIsFilledWithoutRetiringTheLegacyDelete() {
        val raw = PersistedConfig()
        assertEquals("", raw.takInstallTag)
        assertFalse(raw.takLegacySelfRetired)
        val next = TakInstall.ensure(raw, java.util.Random(3))
        assertEquals(4, next.takInstallTag.length)
        assertTrue(next.takInstallTag.all { it in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" })
        assertFalse(next.takLegacySelfRetired)
        assertEquals(next.takInstallTag, TakInstall.ensure(next).takInstallTag)
        assertEquals("AB23", TakInstall.clean(" ab23 "))
        assertEquals("", TakInstall.clean("AB1I"))
        assertEquals("", TakInstall.storedCallsign("   "))
        assertEquals("Scout 1", TakInstall.storedCallsign("  Scout 1  "))
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

    private fun pilotXml(device: Sighting): String = CotEvent.xml(
        device = device,
        fleets = fleets,
        watchlist = emptyList(),
        lat = device.payloadOpLat ?: 0.0,
        lon = device.payloadOpLon ?: 0.0,
        advertised = true,
        now = 0L,
        pilot = true,
        tag = tag,
    )

    private fun radio(
        fleetIds: Set<String>,
        payloadLat: Double? = null,
        payloadLon: Double? = null,
        payloadAlt: Double? = null,
        payloadOpLat: Double? = null,
        payloadOpLon: Double? = null,
        payloadOpKind: Int? = null,
        payloadOpAlt: Double? = null,
        payloadUasId: String? = null,
        payloadSelfId: String? = null,
        payloadHeading: Double? = null,
        payloadSpeed: Double? = null,
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
        payloadOpKind = payloadOpKind,
        payloadOpAlt = payloadOpAlt,
        payloadUasId = payloadUasId,
        payloadSelfId = payloadSelfId,
        payloadHeading = payloadHeading,
        payloadSpeed = payloadSpeed,
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
