package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftTrailTest {
    @Test
    fun appendSkipsAShortMoveAndAGlitchAndKeepsTheEnds() {
        var trail = emptyList<PayloadFix>()
        trail = AircraftTrail.append(trail, fix(0L, 28.0, -81.0))
        trail = AircraftTrail.append(trail, fix(1_000L, 28.00002, -81.0))
        assertEquals(1, trail.size)
        trail = AircraftTrail.append(trail, fix(2_000L, 28.02, -81.0))
        assertEquals("a one-second jump of about 2 km is dropped", 1, trail.size)
        trail = AircraftTrail.append(trail, fix(30_000L, 28.001, -81.0))
        assertEquals(2, trail.size)
        repeat(50) { i ->
            trail = AircraftTrail.append(trail, fix(60_000L + i * 20_000L, 28.001 + (i + 1) * 0.001, -81.0))
        }
        assertTrue(trail.size <= AircraftTrail.CAP)
        assertEquals(28.0, trail.first().lat, 1e-6)
        assertEquals(28.001 + 50 * 0.001, trail.last().lat, 1e-4)
    }

    @Test
    fun sameUasIdJoinsAndAFarTrackGetsItsOwnFigure() {
        val path = listOf(
            GpsSample(0L, 28.0, -81.0, -50),
            GpsSample(60_000L, 28.001, -81.0, -50),
        )
        val nearA = source("ABC123", "near-a", 10_000L, 28.0004, -81.0, "Airborne")
        val nearB = source("ABC123", "near-b", 40_000L, 28.0008, -81.0, "Airborne")
        val far = source("ZZZ999", "far", 20_000L, 28.08, -81.0, "Ground")
        val anon = source("", "stray", 20_000L, 40.0, -74.0, "")
        val pictures = AircraftTrail.pictures(listOf(nearA, nearB, far, anon), path)
        val joined = pictures.single { it.uasId == "ABC123" }
        assertTrue(joined.onWalk)
        assertFalse(joined.ownFigure)
        assertTrue(joined.fixes.size >= 2)
        val away = pictures.single { it.uasId == "ZZZ999" }
        assertFalse(away.onWalk)
        assertTrue(away.ownFigure)
        val stray = pictures.single { it.uasId.isBlank() }
        assertFalse(stray.onWalk)
        assertFalse(stray.ownFigure)
        val own = AircraftTrail.ownFigures(pictures)
        assertEquals(1, own.size)
        assertEquals("AIRCRAFT", own.single().kicker)
        assertTrue(own.single().tracks.single().aircraft)
        assertEquals(90.0, own.single().tracks.single().headingDeg!!, 0.01)
        assertTrue(own.single().caption.contains("marker"))
        val over = AircraftTrail.overlay(
            SitPathPlot.Model(
                samples = path,
                dots = emptyList(),
                lengthM = 100.0,
                spanM = 100.0,
                title = "walk",
            ),
            pictures,
        )
        assertEquals(2, over.samples.size)
        assertEquals(1, over.craft.size)
        assertTrue(over.craft.single().aircraft)
        assertEquals("ABC123", over.craft.single().name)
        assertEquals(90.0, over.craft.single().headingDeg!!, 0.01)
        assertTrue(over.craft.single().samples.size >= 2)
        assertEquals(1, over.aircraftCards.size)
        assertEquals("ZZZ999", over.aircraftCards.single().title)
        assertEquals(140f, over.aircraftCards.single().minHalfSpanM, 0.01f)
        assertEquals(1, over.looseAdvertised)
        val walk = SitPathPlot.layout(over, 400f, 300f)!!
        assertEquals(2, walk.path.size)
        val nearPt = walk.project(28.0008, -81.0)
        assertTrue(nearPt.y > walk.plotTop && nearPt.y < walk.plotBottom)
        assertTrue(walk.project(28.08, -81.0).y < walk.plotTop)
        val card = SitPathPlot.layout(over.aircraftCards.single(), 400f, 300f)!!
        assertTrue(card.path.isEmpty())
        val farPt = card.project(28.08, -81.0)
        assertTrue(farPt.x > card.plotLeft && farPt.x < card.plotRight)
        assertTrue(farPt.y > card.plotTop && farPt.y < card.plotBottom)
    }

    @Test
    fun decodedAlertStaysOnANearTrackAndAFarOneLeavesTheWalk() {
        val path = listOf(
            GpsSample(0L, 28.0, -81.0, -50),
            GpsSample(60_000L, 28.001, -81.0, -50),
        )
        val near = source("ABC123", "near", 40_000L, 28.0008, -81.0, "Airborne", key = "near-key")
        val far = source("ZZZ999", "far", 20_000L, 28.08, -81.0, "Ground", key = "far-key")
        val pictures = AircraftTrail.pictures(listOf(near, far), path)
        val nearDot = alertDot("near-key", 28.0008, -81.0, advertised = true)
        val farDot = alertDot("far-key", 28.08, -81.0, advertised = true)
        val phoneDot = alertDot("phone", 28.0, -81.0, advertised = false)
        val over = AircraftTrail.overlay(
            SitPathPlot.Model(
                samples = path,
                dots = listOf(phoneDot, nearDot, farDot),
                lengthM = 100.0,
                spanM = 100.0,
                title = "walk",
            ),
            pictures,
        )
        assertEquals(listOf("phone", "near-key"), over.dots.map { it.key })
        assertEquals(listOf("far-key"), over.aircraftCards.single().dots.map { it.key })
        assertTrue(SitPathPlot.sitsOnCraft(nearDot, over.craft))
        assertFalse(SitPathPlot.sitsOnCraft(phoneDot, over.craft))
        assertTrue(SitPathPlot.sitsOnCraft(farDot, over.aircraftCards.single().craft))
        val walk = SitPathPlot.layout(over, 400f, 300f)!!
        assertTrue(walk.project(28.08, -81.0).y < walk.plotTop)
    }

    @Test
    fun sitKeepsTheAdvertisedTrailAfterTheWindowCloses() {
        val session = SitSession.start(
            name = "flight",
            now = 1_000L,
            heard = listOf(drone(1_000L, 28.0, -81.0)),
            fleets = emptyList(),
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        session.ingest(drone(20_000L, 28.002, -81.0), emptyList(), emptySet(), emptySet())
        session.ingest(drone(40_000L, 28.004, -81.0), emptyList(), emptySet(), emptySet())
        val saved = session.snapshot().radios.single()
        assertEquals(3, saved.payloadTrail.size)
        assertEquals(130.0, saved.payloadAlt!!, 1e-6)
        assertEquals("ABC123", saved.payloadUasId)
        val text = DebriefReport.document(
            devices = listOf(saved.toSighting()),
            fleets = emptyList(),
            settings = AppSettings(tagLocation = true),
            operatorPath = listOf(
                GpsSample(1_000L, 28.0, -81.0, -50),
                GpsSample(40_000L, 28.001, -81.0, -50),
            ),
            now = 40_000L,
            window = DebriefWindow(1_000L, 40_000L, "flight"),
            watchedFleetIds = setOf("fleet-remote-id"),
        )
        val plain = text.toPlainText()
        assertTrue(plain.contains("AIRCRAFT"))
        assertTrue(plain.contains("ABC123"))
        assertTrue(plain.contains("Airborne"))
        assertTrue(plain.contains("advertised aircraft positions"))
        assertTrue(text.pathFigure!!.tracks.any { it.aircraft })
        assertTrue(text.pathFigure!!.dots.isEmpty())
        val row = text.pathFigure!!.craftKeys.single()
        assertTrue(row.contains("Remote ID"))
        assertTrue(row.contains("AA:BB:CC:DD:EE:10"))
        assertTrue(row.contains("Airborne"))
        assertTrue(row.contains("UAS ABC123"))
        assertFalse(row.contains("Freefly"))
        assertTrue(row.contains("28.004000"))
        assertTrue(row.contains("130 m"))
        assertTrue(row.contains("course 90°"))
        assertTrue(text.extraFigures.isEmpty())
    }

    @Test
    fun privacyModeMasksRemoteIdInThePathKey() {
        val serial = "1581F6Z9D242C0018A7B"
        val mac = "60:60:1F:3B:84:2E"
        val heard = drone(40_000L, 28.004, -81.0).copy(
            key = "BLE:$mac",
            mac = mac,
            name = "",
            payloadUasId = serial,
            payloadOpLat = 28.787120,
            payloadOpLon = -81.360360,
        )
        val open = DebriefReport.document(
            devices = listOf(heard),
            fleets = emptyList(),
            settings = AppSettings(tagLocation = true),
            operatorPath = listOf(
                GpsSample(1_000L, 28.0, -81.0, -50),
                GpsSample(40_000L, 28.001, -81.0, -50),
            ),
            now = 40_000L,
            window = DebriefWindow(1_000L, 40_000L, "flight"),
            watchedFleetIds = setOf("fleet-remote-id"),
        )
        val row = open.pathFigure!!.craftKeys.single()
        assertTrue(row.contains(mac))
        assertTrue(row.contains(serial))
        assertTrue(row.contains("28.004000, -81.000000"))
        assertTrue(row.contains("28.787120, -81.360360"))
        val masked = open.withDemoMacs(listOf(mac), true, listOf(serial))
        val key = masked.pathFigure!!.craftKeys.single()
        assertTrue(key.contains("60:60:1F:**:**:**"))
        assertFalse(key.contains("3B:84:2E"))
        assertFalse(key.contains(serial))
        assertTrue(key.contains("UAS masked"))
        assertFalse(key.contains("28.004000"))
        assertFalse(key.contains("-81.000000"))
        assertFalse(key.contains("28.787120"))
        assertFalse(key.contains("-81.360360"))
        assertTrue(key.contains("last masked"))
        assertTrue(key.contains("pilot masked"))
        assertTrue(key.contains("130 m"))
        assertTrue(key.contains("course 90"))
        val plain = masked.toPlainText()
        assertFalse(plain.contains(serial))
        assertFalse(plain.contains(mac))
        assertFalse(plain.contains("28.004000"))
        assertFalse(plain.contains("28.787120"))
        assertTrue(plain.contains("Advertised position: masked"))
        val dot = SitPathPlot.Dot(
            key = "BLE:$mac",
            lat = 28.004,
            lon = -81.0,
            label = mac,
            extraAttention = false,
            named = false,
            mac = mac,
            advertisedNote = "Airborne · UAS $serial · last 28.787120, -81.365491 · pilot 28.787120, -81.360360",
        )
        val figure = SitPathPlot.Figure(
            kicker = "OPERATOR PATH",
            tracks = emptyList(),
            dots = listOf(dot),
            lengthM = 0.0,
            spanM = 0.0,
            caption = "advertised track for $serial",
            craftKeys = listOf(row),
        )
        val doc = masked.copy(pathFigure = figure).withDemoMacs(listOf(mac), true, listOf(serial))
        val kept = doc.pathFigure!!
        assertEquals("60:60:1F:**:**:**", kept.dots.single().mac)
        assertEquals("60:60:1F:**:**:**", kept.dots.single().label)
        assertFalse(kept.dots.single().advertisedNote.contains(serial))
        assertFalse(kept.dots.single().advertisedNote.contains("28.787120"))
        assertFalse(kept.caption.contains(serial))
        assertEquals(28.004, kept.dots.single().lat, 0.0)
    }

    @Test
    fun advertisedNoteLeadsWithTheSerialMaker() {
        val note = AircraftTrail.advertisedNote(
            status = "Airborne",
            uasId = "18179132000209",
            label = "Remote ID",
            lat = 28.0,
            lon = -81.0,
            alt = 100.0,
            heading = 90.0,
            speed = 5.0,
            pilotLat = null,
            pilotLon = null,
            aircraft = "Freefly Alta X Gen2",
        )
        assertTrue(note.startsWith("Freefly Alta X Gen2 · Airborne · UAS 18179132000209"))
        val text = AircraftTrail.body(
            listOf(
                AircraftTrail.Picture(
                    uasId = "18179200000001",
                    title = "18179200000001",
                    fixes = listOf(PayloadFix(1L, 28.0, -81.0)),
                    status = "Airborne",
                    alt = null,
                    heading = null,
                    speed = null,
                    pilotLat = null,
                    pilotLon = null,
                    pilotOnMap = false,
                    onWalk = true,
                    ownFigure = false,
                    aircraft = "Freefly Alta X",
                ),
            ),
        )
        assertEquals("  Freefly Alta X", text.lines()[1])
    }

    @Test
    fun sitKeepsSerialMakerUntilTheUasIdChanges() {
        val first = drone(1_000L, 28.0, -81.0).copy(payloadAircraft = "Freefly Alta X")
        val session = SitSession.start(
            name = "flight",
            now = 1_000L,
            heard = listOf(first),
            fleets = emptyList(),
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        session.ingest(drone(20_000L, 28.002, -81.0), emptyList(), emptySet(), emptySet())
        assertEquals("Freefly Alta X", session.snapshot().radios.single().payloadAircraft)
        session.ingest(
            drone(40_000L, 28.004, -81.0).copy(payloadUasId = "SESSION1", payloadAircraft = null),
            emptyList(),
            emptySet(),
            emptySet(),
        )
        val saved = session.snapshot().radios.single()
        assertEquals("SESSION1", saved.payloadUasId)
        assertNull(saved.payloadAircraft)
    }

    private fun fix(at: Long, lat: Double, lon: Double) = PayloadFix(at, lat, lon)

    private fun alertDot(key: String, lat: Double, lon: Double, advertised: Boolean) = SitPathPlot.Dot(
        key = key,
        lat = lat,
        lon = lon,
        label = key,
        extraAttention = false,
        named = false,
        advertised = advertised,
    )

    private fun source(
        uasId: String,
        title: String,
        at: Long,
        lat: Double,
        lon: Double,
        status: String,
        key: String = "",
    ) = AircraftTrail.Source(
        uasId = uasId,
        title = title,
        lastSeen = at,
        status = status,
        fixes = listOf(PayloadFix(at, lat, lon, alt = 100.0)),
        alt = 100.0,
        heading = 90.0,
        speed = 5.0,
        pilotLat = null,
        pilotLon = null,
        key = key,
    )

    private fun drone(at: Long, lat: Double, lon: Double) = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:10",
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:10",
        name = "Remote ID",
        rssi = -60,
        rssiMin = -60,
        rssiMax = -60,
        channel = 0,
        frequencyMhz = 2402,
        vendor = null,
        randomized = true,
        hiddenSsid = false,
        serviceUuids = listOf("FFFA"),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1_000L,
        lastSeen = at,
        hitCount = 3,
        fleetIds = setOf("fleet-remote-id"),
        rssiHistory = emptyList(),
        presence = emptyList(),
        payloadLat = lat,
        payloadLon = lon,
        payloadAlt = 130.0,
        payloadHeading = 90.0,
        payloadSpeed = 8.0,
        payloadUasId = "ABC123",
        liveDecode = listOf(LiveDecodeChip("Airborne", emphasis = false)),
    )
}
