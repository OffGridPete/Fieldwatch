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
    fun aLineOfNearbyDetectionsStaysOneNumberWhenChained() {
        val piled = SitPathPlot.clusters(lineOfFour(), chain = true)
        assertEquals(1, piled.size)
        assertEquals(4, piled.single().members.size)
    }

    @Test
    fun aZoomedLineBreaksIntoSpotsUnlessTheyAreTheSamePlace() {
        val open = SitPathPlot.clusters(lineOfFour(), chain = false)
        assertEquals(2, open.size)
        assertTrue(open.all { it.members.size == 2 })
        val same = SitPathPlot.clusters(
            List(3) { i ->
                dot("$i") to SitPathPlot.Pt(40f, 40f)
            },
            chain = false,
        )
        assertEquals(1, same.size)
        assertEquals(3, same.single().members.size)
        val zoomed = SitPathPlot.clusters(
            listOf(0f, 40f, 80f).map { x -> dot("z$x") to SitPathPlot.Pt(x, 0f) },
            chain = false,
        )
        assertEquals(3, zoomed.size)
        assertTrue(zoomed.none { it.stacked })
    }

    private fun dot(key: String) = SitPathPlot.Dot(key, 0.0, 0.0, key, extraAttention = false, named = true)

    private fun lineOfFour() = listOf(0f, 20f, 40f, 60f).map { x ->
        dot("d$x") to SitPathPlot.Pt(x, 0f)
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

    @Test
    fun liveDotsUsesTheLouderHear() {
        val dots = SitPathPlot.liveDots(
            listOf(
                radioAt(
                    "loud",
                    trail = listOf(
                        GpsSample(1L, 28.780, -81.370, rssi = -45),
                        GpsSample(2L, 28.790, -81.370, rssi = -80),
                    ),
                ),
            ),
            emptyList(),
        )
        assertEquals(1, dots.size)
        assertEquals(28.780, dots[0].lat, 1e-9)
        assertEquals(-81.370, dots[0].lon, 1e-9)
        assertFalse(dots[0].advertised)
    }

    @Test
    fun liveDotsPrefersTheAdvertisedFix() {
        val dots = SitPathPlot.liveDots(
            listOf(
                radioAt(
                    "rid",
                    trail = listOf(GpsSample(1L, 28.780, -81.370, rssi = -40)),
                    payloadLat = 28.900,
                    payloadLon = -81.500,
                ),
            ),
            emptyList(),
        )
        assertEquals(28.900, dots.single().lat, 1e-6)
        assertEquals(-81.500, dots.single().lon, 1e-6)
        assertTrue(dots.single().advertised)
    }

    @Test
    fun liveDotsOmitsARadioWithNoFix() {
        val dots = SitPathPlot.liveDots(
            listOf(
                radioAt("none"),
                radioAt("here", latitude = 28.78, longitude = -81.37),
            ),
            emptyList(),
        )
        assertEquals(listOf("here"), dots.map { it.key })
        assertFalse(dots.single().advertised)
    }

    @Test
    fun zoomAboutHalvesTheWindowAndKeepsTheFocus() {
        val view = SitPathPlot.Viewport(28.7800, -81.3700, 400f, 800f)
        val focusLat = 28.7810
        val focusLon = -81.3690
        val zoomed = view.zoomAbout(2f, focusLat, focusLon)
        assertEquals(200f, zoomed.halfEastM, 0.01f)
        assertEquals(400f, zoomed.halfNorthM, 0.01f)
        assertEquals(eastFraction(view, focusLon), eastFraction(zoomed, focusLon), 0.001)
        assertEquals(northFraction(view, focusLat), northFraction(zoomed, focusLat), 0.001)
    }

    @Test
    fun coveredBoundsHoldTheCenterAndReachPastTheScreen() {
        val view = SitPathPlot.Viewport(28.78, -81.37, 400f, 800f)
        val box = view.coveredBounds()
        val south = box[0]
        val north = box[1]
        val west = box[2]
        val east = box[3]
        assertTrue(south < view.centerLat && view.centerLat < north)
        assertTrue(west < view.centerLon && view.centerLon < east)
        val northEdge = view.centerLat + view.halfNorthM / 110_540.0
        assertTrue(north > northEdge)
    }

    @Test
    fun panNorthMovesTheCenterNorth() {
        val view = SitPathPlot.Viewport(28.78, -81.37, 400f, 800f)
        val moved = view.panMeters(0f, 100f)
        assertTrue(moved.centerLat > view.centerLat)
        assertEquals(view.centerLon, moved.centerLon, 1e-12)
        assertEquals(view.halfEastM, moved.halfEastM, 0.01f)
    }

    @Test
    fun panScreenFollowsTheFingerWhenTravelIsUp() {
        val view = SitPathPlot.Viewport(36.0, -115.0, 400f, 400f, 90.0)
        val up = view.panScreen(0f, -10f, 1f, 90.0)
        assertTrue(up.centerLon < view.centerLon)
        assertEquals(view.centerLat, up.centerLat, 1e-8)
        assertEquals(90.0, up.heldCourseDeg, 0.0)
        val right = view.panScreen(10f, 0f, 1f, 90.0)
        assertTrue(right.centerLat > view.centerLat)
        assertEquals(view.centerLon, right.centerLon, 1e-8)
        val northUp = view.panScreen(0f, 10f, 1f, 0.0)
        val direct = view.panMeters(0f, 10f)
        assertEquals(direct.centerLat, northUp.centerLat, 1e-12)
        assertEquals(direct.centerLon, northUp.centerLon, 1e-12)
    }

    @Test
    fun heldViewportKeepsTheCenter() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.7800, -81.3700),
                GpsSample(2L, 28.7850, -81.3700),
            ),
            dots = emptyList(),
            lengthM = 550.0,
            spanM = 550.0,
            title = "tall",
        )
        val fitted = SitPathPlot.layout(model, 400f, 300f)!!
        val held = SitPathPlot.viewportOf(fitted)
        val again = SitPathPlot.layout(model, 400f, 300f, viewport = held)!!
        val midX = (again.plotLeft + again.plotRight) / 2f
        val midY = (again.plotTop + again.plotBottom) / 2f
        val center = again.project(held.centerLat, held.centerLon)
        assertTrue(kotlin.math.abs(center.x - midX) < 2f)
        assertTrue(kotlin.math.abs(center.y - midY) < 2f)
        val before = fitted.project(28.7850, -81.3700)
        val after = again.project(28.7850, -81.3700)
        assertTrue(kotlin.math.abs(before.x - after.x) < 2f)
        assertTrue(kotlin.math.abs(before.y - after.y) < 2f)
    }

    @Test
    fun liveDotsIgnoresALouderHearFromBeforeTheWindow() {
        val dots = SitPathPlot.liveDots(
            listOf(
                radioAt(
                    "loud",
                    trail = listOf(
                        GpsSample(1L, 28.780, -81.370, rssi = -45),
                        GpsSample(2L, 28.790, -81.370, rssi = -80),
                    ),
                ).copy(lastSeen = 2L),
            ),
            emptyList(),
            since = 2L,
        )
        assertEquals(28.790, dots.single().lat, 1e-9)
        assertEquals(-81.370, dots.single().lon, 1e-9)
    }

    @Test
    fun liveDotsUsesTheCurrentHearWhenTheTrailPointIsOlder() {
        val dots = SitPathPlot.liveDots(
            listOf(
                radioAt(
                    "same",
                    trail = listOf(GpsSample(1L, 28.780, -81.370, rssi = -40)),
                    latitude = 28.781,
                    longitude = -81.371,
                ).copy(lastSeen = 50L),
            ),
            emptyList(),
            since = 40L,
        )
        assertEquals(28.781, dots.single().lat, 1e-9)
        assertEquals(-81.371, dots.single().lon, 1e-9)
    }

    @Test
    fun livePathSinceUsesTheSitOrTheLaterOfArmAndFifteenMinutes() {
        val now = 1_000_000L
        val fifteen = DebriefPrompt.WINDOW_MS
        assertEquals(now - 1_000L, SitPathPlot.livePathSince(now, now - 1_000L, null))
        assertEquals(now - fifteen, SitPathPlot.livePathSince(now, now - fifteen - 5_000L, null))
        assertEquals(50L, SitPathPlot.livePathSince(now, now - 1_000L, 50L))
    }

    @Test
    fun livePathSamplesDropPointsBeforeTheWindow() {
        val path = listOf(
            GpsSample(1L, 28.78, -81.37),
            GpsSample(20L, 28.79, -81.37),
        )
        assertEquals(listOf(20L), SitPathPlot.livePathSamples(path, 10L).map { it.at })
    }

    @Test
    fun livePathSamplesDrawFromWhereTheSwitchTurnedOn() {
        val corner = GpsSample(1_000L, 28.7800, -81.3700)
        val tip = GpsSample(20_000L, 28.7810, -81.3700)
        val mark = GpsSample(15_000L, 28.7804, -81.3700)
        val line = SitPathPlot.livePathSamples(listOf(corner, tip), since = 15_000L, mark = mark)
        assertEquals(listOf(15_000L, 20_000L), line.map { it.at })
    }

    @Test
    fun livePathSamplesStayADotUntilThePhoneMoves() {
        val tip = GpsSample(20_000L, 28.7800, -81.3700)
        val mark = GpsSample(15_000L, 28.7800, -81.3700)
        val line = SitPathPlot.livePathSamples(listOf(tip), since = 15_000L, mark = mark)
        assertEquals(listOf(20_000L), line.map { it.at })
    }

    @Test
    fun livePathSamplesKeepTheLastStretchOfAStraightRoad() {
        val anchor = GpsSample(0L, 28.7800, -81.3700)
        val tip = GpsSample(30_000L, 28.7900, -81.3700)
        val mark = GpsSample(1_000L, 28.7801, -81.3700)
        val line = SitPathPlot.livePathSamples(listOf(anchor, tip), since = 15_000L, mark = mark)
        assertEquals(listOf(15_000L, 30_000L), line.map { it.at })
        assertEquals(28.7850, line.first().lat, 1e-9)
        assertEquals(28.7900, line.last().lat, 1e-9)
    }

    @Test
    fun livePathSamplesKeepATurnInsideTheWindow() {
        val anchor = GpsSample(0L, 28.7800, -81.3700)
        val corner = GpsSample(20_000L, 28.7860, -81.3700)
        val tip = GpsSample(30_000L, 28.7860, -81.3680)
        val mark = GpsSample(1_000L, 28.7802, -81.3700)
        val line = SitPathPlot.livePathSamples(listOf(anchor, corner, tip), since = 10_000L, mark = mark)
        assertEquals(listOf(10_000L, 20_000L, 30_000L), line.map { it.at })
    }

    @Test
    fun livePathRadiosKeepAWatchlistAlertHeardInTheWindow() {
        val heard = radioAt("BLE:AA").copy(lastSeen = 100L, fleetIds = setOf("fleet-a"))
        val old = radioAt("BLE:BB").copy(lastSeen = 10L, fleetIds = setOf("fleet-a"))
        val plain = radioAt("BLE:CC").copy(lastSeen = 100L)
        val hidden = radioAt("BLE:DD").copy(lastSeen = 100L, fleetIds = setOf("fleet-a"))
        val mac = radioAt("BLE:EE").copy(lastSeen = 100L)
        val kept = SitPathPlot.livePathRadios(
            listOf(heard, old, plain, hidden, mac),
            since = 50L,
            alertKeys = setOf("BLE:EE"),
            watchedFleetIds = setOf("fleet-a"),
            hiddenKeys = setOf("BLE:DD"),
        )
        assertEquals(listOf("BLE:AA", "BLE:EE"), kept.map { it.key })
    }

    @Test
    fun dotsAloneStillLayOut() {
        val dot = SitPathPlot.Dot(
            key = "BLE:AA",
            lat = 28.78,
            lon = -81.37,
            label = "AA",
            extraAttention = false,
            named = false,
        )
        val layout = SitPathPlot.layout(
            SitPathPlot.Model(emptyList(), listOf(dot), 0.0, 0.0, ""),
            400f,
            300f,
        )
        assertNotNull(layout)
        assertEquals(1, layout!!.dots.size)
    }

    @Test
    fun headingEastPutsEastAboveTheCenter() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 36.0, -115.0),
                GpsSample(2L, 36.0, -114.99),
            ),
            dots = emptyList(),
            lengthM = 900.0,
            spanM = 900.0,
            title = "east",
        )
        val lay = SitPathPlot.layout(model, 400f, 700f, courseDeg = 90.0)!!
        val here = lay.project(36.0, -115.0)
        val east = lay.project(36.0, -114.99)
        assertTrue(east.y < here.y - 20f)
        assertTrue(kotlin.math.abs(east.x - here.x) < kotlin.math.abs(here.y - east.y) / 4f)
        val north = lay.project(36.002, -115.0)
        assertTrue(north.x < here.x - 20f)
    }

    @Test
    fun aMissingCourseStaysNorthUp() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.7800, -81.3700),
                GpsSample(2L, 28.7810, -81.3700),
            ),
            dots = emptyList(),
            lengthM = 110.0,
            spanM = 110.0,
            title = "north",
        )
        val plain = SitPathPlot.layout(model, 400f, 300f)!!
        val missing = SitPathPlot.layout(model, 400f, 300f, courseDeg = Double.NaN)!!
        val zero = SitPathPlot.layout(model, 400f, 300f, courseDeg = 0.0)!!
        assertEquals(plain.path[1].x, missing.path[1].x, 0.05f)
        assertEquals(plain.path[1].y, missing.path[1].y, 0.05f)
        assertEquals(plain.path[1].x, zero.path[1].x, 0.05f)
        assertTrue(missing.path[1].y < missing.path[0].y)
    }

    @Test
    fun unprojectRoundTripsARotatedPoint() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 36.0, -115.0),
                GpsSample(2L, 36.0, -114.99),
            ),
            dots = emptyList(),
            lengthM = 900.0,
            spanM = 900.0,
            title = "east",
        )
        val lay = SitPathPlot.layout(model, 400f, 700f, courseDeg = 90.0)!!
        val pt = lay.project(36.001, -114.999)
        val (lat, lon) = lay.unproject(pt.x, pt.y)
        assertEquals(36.001, lat, 1e-6)
        assertEquals(-114.999, lon, 1e-6)
    }

    @Test
    fun aTurnedWideMapReachesPastThePathBox() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.90, -81.40),
                GpsSample(2L, 29.00, -81.30),
            ),
            dots = emptyList(),
            lengthM = 14_000.0,
            spanM = 14_000.0,
            title = "ne",
        )
        val lay = SitPathPlot.layout(model, 900f, 420f, courseDeg = 45.0)!!
        val window = lay.tileWindow()
        assertNotNull(window)
        val south = window!![0]
        val north = window[1]
        val west = window[2]
        val east = window[3]
        val corners = listOf(
            lay.plotLeft to lay.plotTop,
            lay.plotRight to lay.plotTop,
            lay.plotLeft to lay.plotBottom,
            lay.plotRight to lay.plotBottom,
        )
        var outside = false
        for ((x, y) in corners) {
            val (lat, lon) = lay.unproject(x, y)
            assertTrue(lat in south..north)
            assertTrue(lon in west..east)
            if (lat < 28.90 || lat > 29.00 || lon < -81.40 || lon > -81.30) outside = true
        }
        assertTrue(outside)
    }

    @Test
    fun aHeldViewKeepsDirectionOfTravelUp() {
        val model = SitPathPlot.Model(
            samples = listOf(GpsSample(1L, 36.0, -115.0)),
            dots = emptyList(),
            lengthM = 0.0,
            spanM = 0.0,
            title = "held",
        )
        val held = SitPathPlot.Viewport(36.0, -115.0, 400f, 400f, 90.0)
        val lay = SitPathPlot.layout(model, 400f, 400f, viewport = held, courseDeg = 90.0)!!
        val here = lay.project(36.0, -115.0)
        val cosLat = kotlin.math.cos(Math.toRadians(36.0))
        val east = lay.project(36.0, -115.0 + 40.0 / (111_320.0 * cosLat))
        assertTrue(east.y < here.y - 10f)
        assertTrue(kotlin.math.abs(east.x - here.x) < kotlin.math.abs(here.y - east.y) / 3f)
        val window = lay.tileWindow()
        assertNotNull(window)
        val (cornerLat, cornerLon) = lay.unproject(lay.plotLeft, lay.plotTop)
        assertTrue(cornerLat in window!![0]..window[1])
        assertTrue(cornerLon in window[2]..window[3])
    }

    @Test
    fun aHeldViewWithoutACourseStaysNorthUp() {
        val model = SitPathPlot.Model(
            samples = listOf(GpsSample(1L, 36.0, -115.0)),
            dots = emptyList(),
            lengthM = 0.0,
            spanM = 0.0,
            title = "held",
        )
        val held = SitPathPlot.Viewport(36.0, -115.0, 400f, 400f)
        val lay = SitPathPlot.layout(model, 400f, 400f, viewport = held, courseDeg = Double.NaN)!!
        val here = lay.project(36.0, -115.0)
        val north = lay.project(36.001, -115.0)
        assertTrue(north.y < here.y)
        assertTrue(kotlin.math.abs(north.x - here.x) < 3f)
    }

    @Test
    fun closeZoomMatchesASitThatHasNotMoved() {
        val here = GpsSample(1L, 36.0, -115.0)
        val still = SitPathPlot.Model(listOf(here), emptyList(), 0.0, 0.0, "still")
        val fitted = SitPathPlot.layout(still, 400f, 220f)!!
        val close = SitPathPlot.layout(
            still,
            400f,
            220f,
            followHalfM = MapFrame.CLOSE_HALF_M,
        )!!
        assertEquals(fitted.metersPerPx, close.metersPerPx, 0.01f)
    }

    @Test
    fun closeFollowSitsOnYouABitPastANearbyRadio() {
        assertEquals(SitPathPlot.MIN_HALF_SPAN_M * SitPathPlot.FIT_PAD, MapFrame.CLOSE_HALF_M, 0.01f)
        assertTrue(MapFrame.CLOSE_HALF_M > CoTravel.NEAR_M.toFloat())
        val start = GpsSample(1L, 36.0, -115.0)
        val here = GpsSample(2L, 36.01, -115.0)
        val model = SitPathPlot.Model(listOf(start, here), emptyList(), 1100.0, 1100.0, "close")
        val lay = SitPathPlot.layout(
            model,
            400f,
            220f,
            followHalfM = MapFrame.CLOSE_HALF_M,
            courseDeg = 0.0,
        )!!
        val you = lay.project(here.lat, here.lon)
        val cx = (lay.plotLeft + lay.plotRight) / 2f
        val cy = (lay.plotTop + lay.plotBottom) / 2f
        assertEquals(cx, you.x, 2f)
        assertEquals(cy, you.y, 2f)
        val edge = lay.project(here.lat + MapFrame.CLOSE_HALF_M / 110_540.0, here.lon)
        assertEquals(you.x, edge.x, 2f)
        assertEquals(lay.plotTop, edge.y, 3f)
        val tail = lay.project(start.lat, start.lon)
        assertTrue(tail.y > lay.plotBottom)
    }

    @Test
    fun closeFollowPutsTravelUp() {
        val here = GpsSample(1L, 36.0, -115.0)
        val model = SitPathPlot.Model(listOf(here), emptyList(), 0.0, 0.0, "east")
        val lay = SitPathPlot.layout(
            model,
            400f,
            300f,
            followHalfM = MapFrame.CLOSE_HALF_M,
            courseDeg = 90.0,
        )!!
        val you = lay.project(here.lat, here.lon)
        val cosLat = kotlin.math.cos(Math.toRadians(here.lat))
        val east = lay.project(here.lat, here.lon + 40.0 / (111_320.0 * cosLat))
        assertTrue(east.y < you.y - 10f)
        assertTrue(kotlin.math.abs(east.x - you.x) < kotlin.math.abs(you.y - east.y) / 3f)
    }

    @Test
    fun aHeldCloseViewKeepsDirectionOfTravelUp() {
        val here = GpsSample(1L, 36.0, -115.0)
        val model = SitPathPlot.Model(listOf(here), emptyList(), 0.0, 0.0, "held")
        val held = SitPathPlot.Viewport(36.0, -115.0, 400f, 400f, 90.0)
        val lay = SitPathPlot.layout(
            model,
            400f,
            400f,
            viewport = held,
            followHalfM = MapFrame.CLOSE_HALF_M,
            courseDeg = 90.0,
        )!!
        val you = lay.project(36.0, -115.0)
        val cx = (lay.plotLeft + lay.plotRight) / 2f
        val cy = (lay.plotTop + lay.plotBottom) / 2f
        assertEquals(cx, you.x, 2f)
        assertEquals(cy, you.y, 2f)
        val cosLat = kotlin.math.cos(Math.toRadians(36.0))
        val east = lay.project(36.0, -115.0 + 40.0 / (111_320.0 * cosLat))
        assertTrue(east.y < you.y - 10f)
    }

    @Test
    fun aHeldTurnedPinchKeepsTheFocus() {
        val here = GpsSample(1L, 36.0, -115.0)
        val model = SitPathPlot.Model(listOf(here), emptyList(), 0.0, 0.0, "pinch")
        val follow = SitPathPlot.layout(
            model,
            400f,
            300f,
            followHalfM = MapFrame.CLOSE_HALF_M,
            courseDeg = 90.0,
        )!!
        val held = SitPathPlot.viewportOf(follow, 90.0)
        assertEquals(90.0, held.heldCourseDeg, 0.0)
        val again = SitPathPlot.layout(
            model,
            400f,
            300f,
            viewport = held,
            courseDeg = held.heldCourseDeg,
        )!!
        val cosLat = kotlin.math.cos(Math.toRadians(36.0))
        val lon = here.lon + 40.0 / (111_320.0 * cosLat)
        val before = follow.project(here.lat, lon)
        val after = again.project(here.lat, lon)
        assertEquals(before.x, after.x, 2f)
        assertEquals(before.y, after.y, 2f)
        val zoomed = held.zoomAbout(2f, here.lat, lon)
        assertEquals(90.0, zoomed.heldCourseDeg, 0.0)
        val zoomLay = SitPathPlot.layout(
            model,
            400f,
            300f,
            viewport = zoomed,
            courseDeg = 90.0,
        )!!
        val focus = zoomLay.project(here.lat, lon)
        assertEquals(after.x, focus.x, 3f)
        assertEquals(after.y, focus.y, 3f)
    }

    @Test
    fun aHeldTravelCourseDoesNotFollowANewerFix() {
        assertEquals(90.0, MapFrame.drawnCourse(MapFrame.TRAVEL, 90.0, 10.0), 0.0)
        assertEquals(90.0, MapFrame.drawnCourse(MapFrame.CLOSE, 90.0, 10.0), 0.0)
        assertEquals(10.0, MapFrame.drawnCourse(MapFrame.TRAVEL, null, 10.0), 0.0)
        assertEquals(10.0, MapFrame.drawnCourse(MapFrame.CLOSE, Double.NaN, 10.0), 0.0)
        assertTrue(MapFrame.drawnCourse(MapFrame.NORTH, 90.0, 10.0).isNaN())
        assertTrue(MapFrame.drawnCourse(MapFrame.CLOSE_NORTH, 90.0, 10.0).isNaN())
        assertTrue(MapFrame.drawnCourse(MapFrame.TRAVEL, null, Double.NaN).isNaN())
        assertEquals(20.0, MapFrame.shortestTurn(350.0, 10.0), 0.0)
        assertEquals(-20.0, MapFrame.shortestTurn(10.0, 350.0), 0.0)
        assertEquals(0.0, MapFrame.shortestTurn(40.0, 40.0), 0.0)
        assertEquals(0.0, MapFrame.shortestTurn(Double.NaN, 10.0), 0.0)
        assertEquals(200, MapFrame.courseTurnMs(0L))
        assertEquals(200, MapFrame.courseTurnMs(50L))
        assertEquals(1_000, MapFrame.courseTurnMs(1_000L))
        assertEquals(1_000, MapFrame.courseTurnMs(4_000L))
        assertEquals(MapFrame.CLOSE, MapFrame.onScreen(MapFrame.CLOSE, MapFrame.TRAVEL))
        assertEquals(MapFrame.TRAVEL, MapFrame.onScreen(MapFrame.TRAVEL, MapFrame.TRAVEL))
        assertEquals(MapFrame.NORTH, MapFrame.onScreen(null, MapFrame.NORTH))
    }

    @Test
    fun theButtonSwitchesBearingAndADoubleTapSwitchesZoom() {
        assertEquals(MapFrame.NORTH, MapFrame.resolved(-1, false))
        assertEquals(MapFrame.TRAVEL, MapFrame.resolved(-1, true))
        assertEquals(MapFrame.CLOSE, MapFrame.resolved(MapFrame.CLOSE, false))
        assertEquals(MapFrame.CLOSE_NORTH, MapFrame.resolved(MapFrame.CLOSE_NORTH, true))
        assertEquals(MapFrame.TRAVEL, MapFrame.bearingToggled(MapFrame.NORTH))
        assertEquals(MapFrame.NORTH, MapFrame.bearingToggled(MapFrame.TRAVEL))
        assertEquals(MapFrame.CLOSE_NORTH, MapFrame.bearingToggled(MapFrame.CLOSE))
        assertEquals(MapFrame.CLOSE, MapFrame.bearingToggled(MapFrame.CLOSE_NORTH))
        assertEquals(MapFrame.CLOSE_NORTH, MapFrame.zoomToggled(MapFrame.NORTH))
        assertEquals(MapFrame.NORTH, MapFrame.zoomToggled(MapFrame.CLOSE_NORTH))
        assertEquals(MapFrame.CLOSE, MapFrame.zoomToggled(MapFrame.TRAVEL))
        assertEquals(MapFrame.TRAVEL, MapFrame.zoomToggled(MapFrame.CLOSE))
        assertTrue(MapFrame.zoomed(MapFrame.CLOSE))
        assertTrue(MapFrame.zoomed(MapFrame.CLOSE_NORTH))
        assertFalse(MapFrame.zoomed(MapFrame.NORTH))
        assertTrue(MapFrame.turns(MapFrame.CLOSE))
        assertFalse(MapFrame.turns(MapFrame.CLOSE_NORTH))
    }

    @Test
    fun livePathStaysOffOnRadar() {
        val stock = AppSettings()
        assertFalse(stock.showLivePath)
        assertFalse(stock.mapRotate)
        assertFalse(stock.copy(showLivePath = true, viewMode = ViewMode.RADAR).livePathOnScreen())
        assertTrue(stock.copy(showLivePath = true, viewMode = ViewMode.BY_CLASS).livePathOnScreen())
        assertFalse(stock.copy(showLivePath = false, viewMode = ViewMode.LIST).livePathOnScreen())
    }

    @Test
    fun displayControlsFollowThePicture() {
        assertFalse(ViewMode.RADAR.usesListFields())
        assertFalse(ViewMode.RADAR.usesRssiBars())
        assertTrue(ViewMode.TIMELINE.usesListFields())
        assertFalse(ViewMode.TIMELINE.usesRssiBars())
        listOf(ViewMode.LIST, ViewMode.HYBRID, ViewMode.BY_CLASS).forEach { mode ->
            assertTrue(mode.usesListFields())
            assertTrue(mode.usesRssiBars())
        }
    }

    private fun eastFraction(view: SitPathPlot.Viewport, lon: Double): Double {
        val cos0 = kotlin.math.cos(Math.toRadians(view.centerLat)).coerceAtLeast(0.2)
        val east = (lon - view.centerLon) * 111_320.0 * cos0
        return east / view.halfEastM
    }

    private fun northFraction(view: SitPathPlot.Viewport, lat: Double): Double {
        val north = (lat - view.centerLat) * 110_540.0
        return north / view.halfNorthM
    }

    private fun radioAt(
        key: String,
        trail: List<GpsSample> = emptyList(),
        latitude: Double? = null,
        longitude: Double? = null,
        payloadLat: Double? = null,
        payloadLon: Double? = null,
    ) = Sighting(
        key = key,
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:10",
        name = key,
        rssi = -40,
        rssiMin = -40,
        rssiMax = -40,
        channel = 0,
        frequencyMhz = 0,
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
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        latitude = latitude,
        longitude = longitude,
        gpsTrail = trail,
        payloadLat = payloadLat,
        payloadLon = payloadLon,
    )

    @Test
    fun aFarPointClipsToTheMapEdge() {
        val runs = SitPathPlot.clipPolyline(
            listOf(SitPathPlot.Pt(50f, 50f), SitPathPlot.Pt(1_000_000f, 50f)),
            left = 0f,
            top = 0f,
            right = 100f,
            bottom = 100f,
        )
        assertEquals(1, runs.size)
        assertEquals(50f, runs[0][0].x, 0.01f)
        assertEquals(100f, runs[0][1].x, 0.01f)
        assertEquals(50f, runs[0][1].y, 0.01f)
    }

    @Test
    fun aSegmentOffTheMapIsDropped() {
        val runs = SitPathPlot.clipPolyline(
            listOf(SitPathPlot.Pt(-50f, -50f), SitPathPlot.Pt(-10f, -10f)),
            0f, 0f, 100f, 100f,
        )
        assertTrue(runs.isEmpty())
    }

    @Test
    fun aPathThatLeavesAndReturnsIsTwoPieces() {
        val runs = SitPathPlot.clipPolyline(
            listOf(
                SitPathPlot.Pt(50f, 50f),
                SitPathPlot.Pt(150f, 50f),
                SitPathPlot.Pt(150f, -20f),
                SitPathPlot.Pt(50f, -20f),
                SitPathPlot.Pt(50f, 40f),
            ),
            0f, 0f, 100f, 100f,
        )
        assertEquals(2, runs.size)
        for (run in runs) {
            for (pt in run) {
                assertTrue(pt.x in 0f..100f)
                assertTrue(pt.y in 0f..100f)
            }
        }
    }

    @Test
    fun aZoomedLivePathIsCutToThePicture() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.0, -81.0),
                GpsSample(2L, 28.5, -81.0),
            ),
            dots = emptyList(),
            lengthM = 50_000.0,
            spanM = 50_000.0,
            title = "far",
            live = true,
        )
        val layout = SitPathPlot.layout(
            model,
            400f,
            800f,
            viewport = SitPathPlot.Viewport(28.0, -81.0, 200f, 200f),
        )!!
        assertTrue(layout.path.any { !layout.onPicture(it.x, it.y) })
        val runs = SitPathPlot.clipPolyline(
            layout.path,
            layout.pictureLeft,
            layout.pictureTop,
            layout.pictureRight,
            layout.pictureBottom,
        )
        assertTrue(runs.isNotEmpty())
        for (run in runs) {
            for (pt in run) assertTrue(layout.onPicture(pt.x, pt.y))
        }
    }

    @Test
    fun aCachedGroundMatchesTheLaidOutLine() {
        val samples = List(500) { i ->
            GpsSample(i * 1_000L, 36.0 + i * 0.0001, -115.0 + i * 0.00008)
        }
        val model = SitPathPlot.Model(
            samples = samples,
            dots = emptyList(),
            lengthM = 0.0,
            spanM = 0.0,
            title = "drive",
        )
        val view = SitPathPlot.Viewport(36.02, -114.98, 800f, 600f)
        val full = SitPathPlot.layout(model, 1080f, 1800f, viewport = view, courseDeg = 35.0)!!
        val ground = SitPathPlot.ground(model)
        val traced = SitPathPlot.prepare(
            ground,
            1080f,
            1800f,
            viewport = view,
            courseDeg = 35.0,
            trace = true,
        )!!
        assertEquals(full.path.size, traced.layout.path.size)
        for (i in full.path.indices) {
            assertEquals(full.path[i].x, traced.layout.path[i].x, 0.001f)
            assertEquals(full.path[i].y, traced.layout.path[i].y, 0.001f)
        }
        val light = SitPathPlot.prepare(
            ground,
            1080f,
            1800f,
            viewport = view,
            courseDeg = 35.0,
            trace = false,
        )!!
        assertTrue(light.layout.path.isEmpty())
        assertEquals(full.samples.size, light.layout.samples.size)
        for (i in light.layout.samples.indices) {
            assertEquals(full.samples[i].at, light.layout.samples[i].at)
            val sample = light.layout.samples[i]
            val pt = light.layout.project(sample.lat, sample.lon)
            assertEquals(full.path[i].x, pt.x, 0.001f)
            assertEquals(full.path[i].y, pt.y, 0.001f)
        }
    }

    @Test
    fun aCachedStrokeMatchesTheClippedLine() {
        val samples = ArrayList<GpsSample>()
        var at = 0L
        val stayLat = 36.02
        for (i in 0 until 20) {
            samples.add(GpsSample(at, stayLat - (20 - i) * 0.00015, -115.0))
            at += 1_000L
        }
        for (i in 0 until 45) {
            samples.add(GpsSample(at, stayLat, -115.0))
            at += 1_000L
        }
        for (i in 1..20) {
            samples.add(GpsSample(at, stayLat + i * 0.00015, -115.0))
            at += 1_000L
        }
        val model = SitPathPlot.Model(
            samples = samples,
            dots = emptyList(),
            lengthM = 0.0,
            spanM = 0.0,
            title = "stops",
        )
        val view = SitPathPlot.Viewport(stayLat, -115.0, 80f, 80f)
        val full = SitPathPlot.layout(model, 1080f, 1800f, viewport = view, courseDeg = 35.0)!!
        val light = SitPathPlot.prepare(
            SitPathPlot.ground(model),
            1080f,
            1800f,
            viewport = view,
            courseDeg = 35.0,
            trace = false,
        )!!
        data class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
        val blue = ArrayList<Seg>()
        val green = ArrayList<Seg>()
        light.stroke(
            { x1, y1, x2, y2, _ -> blue.add(Seg(x1, y1, x2, y2)) },
            { x1, y1, x2, y2, _ -> green.add(Seg(x1, y1, x2, y2)) },
        )
        val clipped = ArrayList<Seg>()
        for (run in SitPathPlot.clipPolyline(
            full.path,
            full.pictureLeft,
            full.pictureTop,
            full.pictureRight,
            full.pictureBottom,
        )) {
            for (i in 1 until run.size) {
                val a = run[i - 1]
                val b = run[i]
                clipped.add(Seg(a.x, a.y, b.x, b.y))
            }
        }
        assertTrue(blue.isNotEmpty())
        assertTrue(blue.size < full.path.size - 1)
        assertEquals(clipped.size, blue.size)
        for (i in blue.indices) {
            assertEquals(clipped[i].x1, blue[i].x1, 0.001f)
            assertEquals(clipped[i].y1, blue[i].y1, 0.001f)
            assertEquals(clipped[i].x2, blue[i].x2, 0.001f)
            assertEquals(clipped[i].y2, blue[i].y2, 0.001f)
        }
        val stays = Geo.realStops(full.samples)
        val expectGreen = ArrayList<Seg>()
        for (i in 1 until full.path.size) {
            if (!Geo.stopCovers(stays, full.samples[i - 1].at, full.samples[i].at)) continue
            val seg = SitPathPlot.clipSegment(
                full.path[i - 1],
                full.path[i],
                full.pictureLeft,
                full.pictureTop,
                full.pictureRight,
                full.pictureBottom,
            ) ?: continue
            expectGreen.add(Seg(seg.first.x, seg.first.y, seg.second.x, seg.second.y))
        }
        assertTrue(expectGreen.isNotEmpty())
        assertEquals(expectGreen.size, green.size)
        for (i in green.indices) {
            assertEquals(expectGreen[i].x1, green[i].x1, 0.001f)
            assertEquals(expectGreen[i].y1, green[i].y1, 0.001f)
            assertEquals(expectGreen[i].x2, green[i].x2, 0.001f)
            assertEquals(expectGreen[i].y2, green[i].y2, 0.001f)
        }
    }

    @Test
    fun aFingerPanLandsOnTheSamePixels() {
        val samples = listOf(
            GpsSample(0L, 36.0, -115.0),
            GpsSample(8_000L, 36.03, -115.01),
        )
        val model = SitPathPlot.Model(samples, emptyList(), 0.0, 0.0, "pan")
        val ground = SitPathPlot.ground(model)
        val course = 35.0
        val view = SitPathPlot.Viewport(36.015, -115.005, 600f, 900f, course)
        val before = SitPathPlot.prepare(
            ground, 1080f, 1800f, viewport = view, courseDeg = course, trace = false,
        )!!
        val panX = 72f
        val panY = -40f
        val moved = view.panScreen(panX, panY, before.layout.metersPerPx, course)
        val after = SitPathPlot.prepare(
            ground, 1080f, 1800f, viewport = moved, courseDeg = course, trace = false,
        )!!
        val spotLat = 36.016
        val spotLon = -115.006
        val from = before.layout.project(spotLat, spotLon)
        val to = after.layout.project(spotLat, spotLon)
        assertEquals(from.x + panX, to.x, 1.5f)
        assertEquals(from.y + panY, to.y, 1.5f)
        val focus = before.layout.project(spotLat, spotLon)
        val (focusLat, focusLon) = before.layout.unproject(focus.x, focus.y)
        val zoomed = view.zoomAbout(1.6f, focusLat, focusLon)
        val closer = SitPathPlot.prepare(
            ground, 1080f, 1800f, viewport = zoomed, courseDeg = course, trace = false,
        )!!
        val focusNow = closer.layout.project(focusLat, focusLon)
        assertEquals(focus.x, focusNow.x, 1.5f)
        assertEquals(focus.y, focusNow.y, 1.5f)
        val applied = view.halfEastM / zoomed.halfEastM
        val other = before.layout.project(36.02, -115.008)
        val otherNow = closer.layout.project(36.02, -115.008)
        assertEquals(focus.x + (other.x - focus.x) * applied, otherNow.x, 2f)
        assertEquals(focus.y + (other.y - focus.y) * applied, otherNow.y, 2f)
    }

    @Test
    fun aFollowTurnIsARotationAroundThePhone() {
        val here = GpsSample(2L, 36.0, -115.0)
        val start = GpsSample(1L, 36.0 - 80.0 / 110_540.0, -115.0)
        val model = SitPathPlot.Model(listOf(start, here), emptyList(), 80.0, 80.0, "turn")
        val northLat = 36.0 + 200.0 / 110_540.0
        val eastLon = -115.0 + 120.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(36.0)))
        val lay0 = SitPathPlot.layout(
            model,
            1080f,
            1800f,
            followHalfM = MapFrame.CLOSE_HALF_M,
            courseDeg = 0.0,
        )!!
        val lay20 = SitPathPlot.layout(
            model,
            1080f,
            1800f,
            followHalfM = MapFrame.CLOSE_HALF_M,
            courseDeg = 20.0,
        )!!
        val pivotX = (lay0.pictureLeft + lay0.pictureRight) / 2f
        val pivotY = (lay0.pictureTop + lay0.pictureBottom) / 2f
        val you0 = lay0.project(here.lat, here.lon)
        val you20 = lay20.project(here.lat, here.lon)
        assertEquals(pivotX, you0.x, 1.5f)
        assertEquals(pivotY, you0.y, 1.5f)
        assertEquals(you0.x, you20.x, 1.5f)
        assertEquals(you0.y, you20.y, 1.5f)
        val turn = (-MapFrame.shortestTurn(0.0, 20.0)).toFloat()
        val spot0 = lay0.project(northLat, eastLon)
        val spot20 = lay20.project(northLat, eastLon)
        val spun = SitPathPlot.picturePoint(
            spot0.x, spot0.y,
            pivotX, pivotY, pivotX, pivotY,
            1f, turn, pivotX, pivotY,
        )
        assertEquals(spot20.x, spun.x, 1.5f)
        assertEquals(spot20.y, spun.y, 1.5f)
        val back = SitPathPlot.pictureSource(
            spun.x, spun.y,
            pivotX, pivotY, pivotX, pivotY,
            1f, turn, pivotX, pivotY,
        )
        assertEquals(spot0.x, back.x, 0.05f)
        assertEquals(spot0.y, back.y, 0.05f)
        assertEquals(0f, SitPathPlot.rotationPadPx(0.4, 400f, 700f), 0.01f)
        assertTrue(SitPathPlot.rotationPadPx(20.0, 400f, 700f) > 12f)
    }

    @Test
    fun aPanShiftMatchesTheRepaintedPixels() {
        val samples = listOf(
            GpsSample(0L, 36.0, -115.0),
            GpsSample(8_000L, 36.03, -115.01),
        )
        val model = SitPathPlot.Model(samples, emptyList(), 0.0, 0.0, "shift")
        val ground = SitPathPlot.ground(model)
        val course = 35.0
        val view = SitPathPlot.Viewport(36.015, -115.005, 600f, 900f, course)
        val before = SitPathPlot.prepare(
            ground, 1080f, 1800f, viewport = view, courseDeg = course, trace = false,
        )!!
        val panX = 72f
        val panY = -40f
        val moved = view.panScreen(panX, panY, before.layout.metersPerPx, course)
        val after = SitPathPlot.prepare(
            ground, 1080f, 1800f, viewport = moved, courseDeg = course, trace = false,
        )!!
        val spotLat = 36.016
        val spotLon = -115.006
        val from = before.layout.project(spotLat, spotLon)
        val to = after.layout.project(spotLat, spotLon)
        val cx = (before.layout.pictureLeft + before.layout.pictureRight) / 2f
        val cy = (before.layout.pictureTop + before.layout.pictureBottom) / 2f
        val origin = before.layout.project(moved.centerLat, moved.centerLon)
        val k = view.halfEastM / moved.halfEastM
        val shifted = SitPathPlot.picturePoint(
            from.x, from.y, origin.x, origin.y, cx, cy, k, 0f, cx, cy,
        )
        assertEquals(to.x, shifted.x, 1.5f)
        assertEquals(to.y, shifted.y, 1.5f)
        assertEquals(from.x + panX, shifted.x, 1.5f)
        assertEquals(from.y + panY, shifted.y, 1.5f)
        val focus = before.layout.project(spotLat, spotLon)
        val (focusLat, focusLon) = before.layout.unproject(focus.x, focus.y)
        val zoomed = view.zoomAbout(1.6f, focusLat, focusLon)
        val closer = SitPathPlot.prepare(
            ground, 1080f, 1800f, viewport = zoomed, courseDeg = course, trace = false,
        )!!
        val originZ = before.layout.project(zoomed.centerLat, zoomed.centerLon)
        val kZ = view.halfEastM / zoomed.halfEastM
        val other = before.layout.project(36.02, -115.008)
        val otherNow = closer.layout.project(36.02, -115.008)
        val otherShift = SitPathPlot.picturePoint(
            other.x, other.y, originZ.x, originZ.y, cx, cy, kZ, 0f, cx, cy,
        )
        assertEquals(otherNow.x, otherShift.x, 2f)
        assertEquals(otherNow.y, otherShift.y, 2f)
        val focusShift = SitPathPlot.picturePoint(
            focus.x, focus.y, originZ.x, originZ.y, cx, cy, kZ, 0f, cx, cy,
        )
        assertEquals(focus.x, focusShift.x, 1.5f)
        assertEquals(focus.y, focusShift.y, 1.5f)
        val turned = SitPathPlot.picturePoint(
            other.x, other.y, originZ.x, originZ.y, cx, cy, kZ, 12f, cx, cy,
        )
        val turnedBack = SitPathPlot.pictureSource(
            turned.x, turned.y, originZ.x, originZ.y, cx, cy, kZ, 12f, cx, cy,
        )
        assertEquals(other.x, turnedBack.x, 0.05f)
        assertEquals(other.y, turnedBack.y, 0.05f)
    }
}
