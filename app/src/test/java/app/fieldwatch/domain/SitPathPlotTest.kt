package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitPathPlotTest {
    @Test
    fun northIsUpAndEastIsRight() {
        val south = GpsSample(1L, 28.7800, -81.3700)
        val north = GpsSample(2L, 28.7810, -81.3700)
        val east = GpsSample(3L, 28.7810, -81.3690)
        val model = SitPathPlot.Model(
            samples = listOf(south, north, east),
            dots = emptyList(),
            lengthM = 200.0,
            spanM = 150.0,
            title = "test",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        assertEquals(3, layout.path.size)
        assertTrue(layout.path[1].y < layout.path[0].y)
        assertTrue(layout.path[2].x > layout.path[1].x)
    }

    @Test
    fun tallPathCentersHorizontally() {
        val south = GpsSample(1L, 28.7800, -81.3700)
        val north = GpsSample(2L, 28.7850, -81.3700)
        val model = SitPathPlot.Model(
            samples = listOf(south, north),
            dots = emptyList(),
            lengthM = 550.0,
            spanM = 550.0,
            title = "tall",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        val mid = 200f
        layout.path.forEach { pt ->
            assertTrue(kotlin.math.abs(pt.x - mid) < 20f)
        }
    }

    @Test
    fun oneAdvertisedFixStillFrames() {
        val model = SitPathPlot.Model(
            samples = emptyList(),
            dots = emptyList(),
            lengthM = 0.0,
            spanM = 0.0,
            title = "AIRCRAFT",
            frameSamples = listOf(GpsSample(1L, 28.78, -81.36)),
            minHalfSpanM = 140f,
        )
        val layout = SitPathPlot.layout(model, 400f, 300f, pad = 12f, scaleBarReserve = 0f)
        assertNotNull(layout)
        val pt = layout!!.project(28.78, -81.36)
        assertTrue(pt.x > layout.plotLeft && pt.x < layout.plotRight)
        assertTrue(pt.y > layout.plotTop && pt.y < layout.plotBottom)
        assertTrue(layout.path.isEmpty())
    }

    @Test
    fun advertisedFixDoesNotExtendThePhoneLine() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.78, -81.37),
                GpsSample(2L, 28.781, -81.37),
            ),
            dots = emptyList(),
            lengthM = 100.0,
            spanM = 100.0,
            title = "walk",
            frameSamples = listOf(GpsSample(3L, 28.80, -81.37)),
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        assertEquals(2, layout.path.size)
        val craft = layout.project(28.80, -81.37)
        assertTrue(craft.y < layout.path[0].y)
        assertTrue(craft.y > layout.plotTop)
    }

    @Test
    fun shortPathOpensEnoughGroundToReadTheStreets() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.78000, -81.37000),
                GpsSample(2L, 28.78002, -81.37000),
            ),
            dots = emptyList(),
            lengthM = 2.0,
            spanM = 2.0,
            title = "still",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        val here = layout.project(28.78000, -81.37000)
        val north = layout.project(28.78200, -81.37000)
        val meters = 0.002 * 110_540.0
        val pxPerM = (here.y - north.y) / meters
        val viewM = (layout.plotBottom - layout.plotTop) / pxPerM
        assertTrue(viewM in 350.0..600.0)
        assertTrue(here.y > layout.plotTop && here.y < layout.plotBottom)
    }

    @Test
    fun oneGpsFixStillFrames() {
        val model = SitPathPlot.Model(
            samples = listOf(GpsSample(1L, 28.78000, -81.37000)),
            dots = emptyList(),
            lengthM = 0.0,
            spanM = 0.0,
            title = "one",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)
        assertNotNull(layout)
        val pt = layout!!.project(28.78000, -81.37000)
        assertTrue(pt.x > layout.plotLeft && pt.x < layout.plotRight)
        assertTrue(pt.y > layout.plotTop && pt.y < layout.plotBottom)
    }

    @Test
    fun aLongWalkStillFillsThePlot() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.7800, -81.3700),
                GpsSample(2L, 28.8000, -81.3700),
            ),
            dots = emptyList(),
            lengthM = 2200.0,
            spanM = 2200.0,
            title = "drive",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        val south = layout.project(28.7800, -81.3700)
        val north = layout.project(28.8000, -81.3700)
        val used = (south.y - north.y) / (layout.plotBottom - layout.plotTop)
        assertTrue(used > 0.7f)
    }

    @Test
    fun scaleBarIsANiceMeterValue() {
        assertEquals(50.0, SitPathPlot.niceMeters(47.0), 0.01)
        assertEquals(100.0, SitPathPlot.niceMeters(80.0), 0.01)
        assertEquals(20.0, SitPathPlot.niceMeters(18.0), 0.01)
    }

    @Test
    fun nearbyDotsBecomeOneStackedCluster() {
        val a = SitPathPlot.Dot("a", 0.0, 0.0, "A", extraAttention = true, named = false)
        val b = SitPathPlot.Dot("b", 0.0, 0.0, "B", extraAttention = true, named = false)
        val c = SitPathPlot.Dot("c", 0.0, 0.0, "C", extraAttention = false, named = true)
        val stacked = SitPathPlot.clusters(
            listOf(
                a to SitPathPlot.Pt(10f, 10f),
                b to SitPathPlot.Pt(18f, 12f),
                c to SitPathPlot.Pt(200f, 180f),
            ),
            threshPx = 28f,
        )
        assertEquals(2, stacked.size)
        val pile = stacked.first { it.stacked }
        assertEquals(2, pile.members.size)
        assertTrue(stacked.any { !it.stacked && it.members.single().dot.key == "c" })
    }

    @Test
    fun startLabelOpensTheDetectionItCovers() {
        val dot = SitPathPlot.Dot("a", 0.0, 0.0, "A", extraAttention = false, named = true)
        val cluster = SitPathPlot.clusters(listOf(dot to SitPathPlot.Pt(100f, 100f))).single()
        val start = SitPathPlot.HitMarker(
            x = 100f,
            y = 100f,
            radius = 12f,
            labelLeft = 108f,
            labelTop = 94f,
            labelRight = 180f,
            labelBottom = 112f,
        )
        val onTheWord = SitPathPlot.clusterAt(listOf(cluster), 160f, 100f, listOf(start))
        assertEquals(cluster.id, onTheWord?.id)
        val onTheDot = SitPathPlot.clusterAt(listOf(cluster), 100f, 100f, listOf(start))
        assertEquals(cluster.id, onTheDot?.id)
        val elsewhere = SitPathPlot.clusterAt(listOf(cluster), 160f, 40f, listOf(start))
        assertEquals(null, elsewhere)
    }

    @Test
    fun extraAttentionDotsComeFirstAndNeedAFix() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:01",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:01",
            name = "cam",
            rssi = -50,
            rssiMin = -50,
            rssiMax = -50,
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
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(GpsSample(1L, 28.78, -81.37)),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertTrue(dots.points[0].extraAttention)
        assertNotNull(SitPathPlot.layout(
            SitPathPlot.Model(
                samples = listOf(
                    GpsSample(1L, 28.780, -81.370),
                    GpsSample(2L, 28.781, -81.371),
                ),
                dots = dots.points,
                lengthM = 120.0,
                spanM = 100.0,
                title = "plaza",
            ),
            300f, 200f,
        ))
    }

    @Test
    fun sameRadioPlotsOnceAtLoudestHear() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:01",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:01",
            name = "cam",
            rssi = -40,
            rssiMin = -80,
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
            lastSeen = 3L,
            hitCount = 3,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(1L, 28.7800, -81.3700, -80),
                GpsSample(2L, 28.7810, -81.3710, -42),
                GpsSample(3L, 28.7820, -81.3720, -70),
            ),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged, tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertEquals(28.7810, dots.points[0].lat, 0.00001)
        assertEquals(-81.3710, dots.points[0].lon, 0.00001)
    }

    @Test
    fun extraAttentionAlongTheWalkIsStillANumberedStop() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val path = listOf(
            GpsSample(0L, 28.7800, -81.3700),
            GpsSample(60_000L, 28.7850, -81.3700),
        )
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:01",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:01",
            name = "van",
            rssi = -50,
            rssiMin = -60,
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
            firstSeen = 0L,
            lastSeen = 60_000L,
            hitCount = 8,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(0L, 28.7800, -81.3700, -55),
                GpsSample(15_000L, 28.7812, -81.3700, -48),
                GpsSample(30_000L, 28.7825, -81.3700, -40),
                GpsSample(45_000L, 28.7837, -81.3700, -46),
                GpsSample(60_000L, 28.7850, -81.3700, -52),
            ),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertEquals("van", dots.points[0].label)
        assertTrue(dots.points[0].extraAttention)
    }

    @Test
    fun pathPlotsMacAlertsAndSignatureAlerts() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
            colorIndex = 7,
        )
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:01",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:01",
            name = "cam",
            rssi = -50,
            rssiMin = -50,
            rssiMax = -50,
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
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(GpsSample(1L, 28.78, -81.37)),
        )
        val report = SitPathPlot.dotsFrom(listOf(tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, report.points.size)
        val bare = SitPathPlot.dotsFrom(
            listOf(tagged), listOf(axon), namedKeys = emptySet(), alertsOnly = true,
        )
        assertTrue(bare.points.isEmpty())
        val signature = SitPathPlot.dotsFrom(
            listOf(tagged),
            listOf(axon),
            namedKeys = emptySet(),
            watchedFleetIds = setOf(axon.id),
            alertsOnly = true,
        )
        assertEquals(1, signature.points.size)
        assertEquals(SignatureClass.LAW_ENFORCEMENT, signature.points[0].classKind)
        assertEquals(Palette.color(7), signature.points[0].accentArgb)
        val mac = SitPathPlot.dotsFrom(
            listOf(tagged),
            listOf(axon),
            namedKeys = emptySet(),
            bookmarkedKeys = setOf(tagged.key),
            alertsOnly = true,
        )
        assertEquals(1, mac.points.size)
        assertTrue(mac.points[0].named)
        val decoded = tagged.copy(
            payloadLat = 29.5,
            payloadLon = -81.37,
            payloadTrail = listOf(
                PayloadFix(1L, 29.1, -81.37),
                PayloadFix(2L, 29.4, -81.37),
            ),
        )
        val pinned = SitPathPlot.dotsFrom(
            listOf(decoded),
            listOf(axon),
            namedKeys = emptySet(),
            watchedFleetIds = setOf(axon.id),
            alertsOnly = true,
        )
        assertEquals(29.4, pinned.points.single().lat, 1e-6)
        assertTrue(pinned.points.single().advertised)
        val letter = SitPathPlot.dotsFrom(listOf(decoded), listOf(axon), namedKeys = emptySet())
        assertEquals(28.78, letter.points.single().lat, 1e-6)
        assertFalse(letter.points.single().advertised)
    }

    @Test
    fun bookmarkedRadioIsANumberedStop() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val path = (0..20).map { i ->
            GpsSample(i * 60_000L, 28.7800 + i * 0.01, -81.3700)
        }
        val tagged = Sighting(
            key = "BLE:AA:AA:AA:AA:AA:02",
            kind = RadioKind.BLE,
            mac = "AA:AA:AA:AA:AA:02",
            name = "Cybertruck",
            rssi = -55,
            rssiMin = -70,
            rssiMax = -40,
            channel = 0,
            frequencyMhz = 2402,
            vendor = "Tesla, Inc.",
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 20 * 60_000L,
            hitCount = 80,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(0, 5, 10, 15, 20).map { i ->
                GpsSample(i * 60_000L, 28.7800 + i * 0.01, -81.3700, -50)
            },
        )
        val dots = SitPathPlot.dotsFrom(
            listOf(tagged), listOf(axon), namedKeys = setOf(tagged.key),
            bookmarkedKeys = setOf(tagged.key),
        )
        assertEquals(1, dots.points.size)
        assertEquals("Cybertruck", dots.points[0].label)
        assertTrue(dots.points[0].named)
    }

    @Test
    fun extraAttentionLeaveAndReturnIsAStop() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val path = (0..20).map { i ->
            GpsSample(i * 60_000L, 28.7800 + i * 0.01, -81.3700)
        }
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:03",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:03",
            name = "home AP",
            rssi = -40,
            rssiMin = -50,
            rssiMax = -35,
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
            firstSeen = 0L,
            lastSeen = 20 * 60_000L,
            hitCount = 12,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(0L, 28.7800, -81.3700, -40),
                GpsSample(20 * 60_000L, 28.7800, -81.3700, -42),
            ),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertEquals("home AP", dots.points[0].label)
        assertTrue(dots.points[0].extraAttention)
    }

    @Test
    fun namedWithoutBookmarkIsNotPlotted() {
        val path = listOf(
            GpsSample(0L, 28.7800, -81.3700),
            GpsSample(60_000L, 28.7850, -81.3700),
        )
        val tagged = Sighting(
            key = "BLE:AA:AA:AA:AA:AA:05",
            kind = RadioKind.BLE,
            mac = "AA:AA:AA:AA:AA:05",
            name = "tsTPMS",
            rssi = -55,
            rssiMin = -60,
            rssiMax = -50,
            channel = 0,
            frequencyMhz = 2402,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 60_000L,
            hitCount = 8,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(GpsSample(0L, 28.7800, -81.3700, -55)),
        )
        val dots = SitPathPlot.dotsFrom(
            listOf(tagged),
            fleets = emptyList(),
            namedKeys = setOf(tagged.key),
            observerNotes = mapOf(tagged.key to "Saw this at the gate."),
        )
        assertTrue(dots.points.isEmpty())
    }

    @Test
    fun observerNotesPlotAsNumberedBlueStop() {
        val path = listOf(
            GpsSample(0L, 28.7800, -81.3700),
            GpsSample(60_000L, 28.7850, -81.3700),
        )
        val tagged = Sighting(
            key = "BLE:AA:AA:AA:AA:AA:04",
            kind = RadioKind.BLE,
            mac = "AA:AA:AA:AA:AA:04",
            name = "iBeacon",
            rssi = -55,
            rssiMin = -60,
            rssiMax = -50,
            channel = 0,
            frequencyMhz = 2402,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 60_000L,
            hitCount = 8,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(0L, 28.7800, -81.3700, -55),
                GpsSample(30_000L, 28.7825, -81.3700, -50),
                GpsSample(60_000L, 28.7850, -81.3700, -58),
            ),
        )
        val dots = SitPathPlot.dotsFrom(
            listOf(tagged),
            fleets = emptyList(),
            namedKeys = emptySet(),
            observerNotes = mapOf(tagged.key to "Mall door, same as last week."),
            bookmarkedKeys = setOf(tagged.key),
        )
        assertEquals(1, dots.points.size)
        assertTrue(dots.points[0].named)
        assertFalse(dots.points[0].extraAttention)
        assertEquals("Mall door, same as last week.", dots.points[0].observerNotes)
    }

    @Test
    fun despikeDropsOutAndBackJump() {
        val a = GpsSample(0L, 28.7800, -81.3700)
        val spike = GpsSample(2_000L, 28.7950, -81.3700)
        val c = GpsSample(4_000L, 28.7802, -81.3700)
        val cleaned = Geo.despikePath(listOf(a, spike, c))
        assertEquals(2, cleaned.size)
        assertEquals(a.at, cleaned[0].at)
        assertEquals(c.at, cleaned[1].at)
    }

    @Test
    fun despikeKeepsHighwayHop() {
        val a = GpsSample(0L, 28.7800, -81.3700)
        val b = GpsSample(10_000L, 28.7827, -81.3700)
        val cleaned = Geo.despikePath(listOf(a, b))
        assertEquals(2, cleaned.size)
    }

    @Test
    fun capSpreadKeepsFirstAndLast() {
        val samples = (0 until 100).map { i ->
            GpsSample(i * 1_000L, 28.78 + i * 0.001, -81.37)
        }
        val kept = Geo.capSpread(samples, 40)
        assertEquals(40, kept.size)
        assertEquals(samples.first().at, kept.first().at)
        assertEquals(samples.last().at, kept.last().at)
        assertTrue(kept.any { it.at in 20_000L..30_000L })
    }

    @Test
    fun gnssPinSitsOnTheSampleNearestInTime() {
        val path = listOf(
            GpsSample(1_000L, 28.7800, -81.3700),
            GpsSample(10_000L, 28.7810, -81.3700),
            GpsSample(20_000L, 28.7820, -81.3700),
        )
        val mark = GnssMark(
            at = 11_000L,
            endedAt = 16_000L,
            kind = "interference",
            confidence = "Medium",
            detail = "GPS L1: AGC -9.0 dB, signal -7.0 dB, 8 satellites (baseline 12)",
        )
        val pin = SitPathPlot.gnssPins(listOf(mark), path).single()
        assertEquals(28.7810, pin.lat, 0.00001)
        assertEquals(-81.3700, pin.lon, 0.00001)
        val line = pin.keyLine()
        assertTrue(line.contains("Possible GNSS interference"))
        assertTrue(line.contains("Medium"))
        assertTrue(line.contains("AGC -9.0 dB"))
        assertFalse(line.contains("28.781"))
        assertFalse(line.contains("latitude", ignoreCase = true))
        assertFalse(line.contains("jammer", ignoreCase = true))
        assertFalse(line.contains("spoofer", ignoreCase = true))
    }

    @Test
    fun gnssPinNeedsAPathAndNamesSpoofingWithoutAPlace() {
        val mark = GnssMark(at = 5_000L, kind = "spoofing", confidence = "High", detail = "  ")
        assertTrue(SitPathPlot.gnssPins(listOf(mark), emptyList()).isEmpty())
        val pin = SitPathPlot.gnssPins(
            listOf(mark),
            listOf(GpsSample(1_000L, 28.7800, -81.3700)),
        ).single()
        assertEquals("Possible GNSS spoofing", pin.title())
        assertTrue(pin.keyLine().contains("Signal changed."))
        assertFalse(pin.keyLine().contains("28.7800"))
    }
}
