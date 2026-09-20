package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitTest {
    private val axon = Fleet(
        id = "fleet-axon",
        name = "Axon",
        kind = SignatureClass.LAW_ENFORCEMENT,
        attentionNote = "Body-worn, in-car, dock, or TASER.",
    )
    private val remote = Fleet(
        id = "fleet-remote-id",
        name = "Remote ID",
        kind = SignatureClass.DRONE,
    )
    private val fleets = listOf(axon, remote)

    @Test
    fun blankNameBecomesTimestamp() {
        val name = Sit.resolveName("  ", 0L, java.util.Locale.US)
        assertTrue(name.isNotBlank())
        assertFalse(name.isBlank())
        assertEquals("parking lot", Sit.resolveName(" parking lot ", 0L))
        assertEquals(Sit.NAME_MAX, Sit.clipName("x".repeat(80)).length)
    }

    @Test
    fun dropWarningNamesOldestWhenAtCap() {
        assertNull(Sit.dropWarning(emptyList()))
        val nine = (1..9).map { i ->
            SitSummary(id = "$i", name = "s$i", startAt = i.toLong())
        }
        assertNull(Sit.dropWarning(nine))
        val ten = listOf(SitSummary("new", "newest", 20L)) +
            nine +
            listOf(SitSummary("old", "oldest", 1L))
        val warn = Sit.dropWarning(ten)
        assertNotNull(warn)
        assertTrue(warn!!.contains("oldest"))
        assertTrue(warn.contains("10"))
    }

    @Test
    fun startCopiesCurrentlyHeardAndSkipsGone() {
        val cam = radio("BLE:AA:AA:AA:AA:AA:01", fleetIds = setOf("fleet-axon"), firstSeen = 1_000L)
        val gone = radio("BLE:AA:AA:AA:AA:AA:02", gone = true, lastSeen = 1_000L)
        val session = SitSession.start(
            name = "lot",
            now = 10_000L,
            heard = listOf(cam, gone),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        assertEquals("lot", session.summary.name)
        assertEquals(1, session.radioCount)
        val row = session.sightings().single()
        assertEquals(cam.key, row.key)
        assertEquals(1_000L, row.firstSeen)
        assertTrue(row.attentionNotes(fleets).isNotEmpty())
    }

    @Test
    fun arrivedAfterStartVsAlreadyHere() {
        val here = radio("BLE:AA:AA:AA:AA:AA:01", firstSeen = 1_000L, lastSeen = 10_000L)
        val session = SitSession.start(
            name = "walk",
            now = 10_000L,
            heard = listOf(here),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        val later = radio("BLE:BB:BB:BB:BB:BB:02", firstSeen = 11_000L, lastSeen = 11_000L)
        session.ingest(later, fleets, emptySet(), emptySet())
        val already = session.sightings().first { it.key == here.key }
        val arrived = session.sightings().first { it.key == later.key }
        assertTrue(already.firstSeen < session.summary.startAt)
        assertTrue(arrived.firstSeen >= session.summary.startAt)
    }

    @Test
    fun unnamedBleEvictsBeforeExtraAttention() {
        val session = SitSession.start(
            name = "plaza",
            now = 1L,
            heard = emptyList(),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        val cam = radio("BLE:CC:CC:CC:CC:CC:01", fleetIds = setOf("fleet-axon"), lastSeen = 1L)
        session.ingest(cam, fleets, emptySet(), emptySet())
        repeat(Sit.RADIO_CAP) { i ->
            val mac = "AA:AA:AA:AA:%02X:%02X".format(i / 256, i % 256)
            session.ingest(
                radio("BLE:$mac", mac = mac, lastSeen = i.toLong() + 2, fleetIds = emptySet()),
                fleets,
                emptySet(),
                emptySet(),
            )
        }
        assertTrue(session.radioCount <= Sit.RADIO_CAP)
        assertTrue(session.sightings().any { it.key == cam.key })
        assertTrue(session.atCap)
    }

    @Test
    fun tightMemoryRefusesNewUnpinnedKeepsPinned() {
        val session = SitSession.start(
            name = "tight",
            now = 1L,
            heard = emptyList(),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        val clutter = radio("BLE:AA:AA:AA:AA:AA:01", lastSeen = 1L)
        val later = radio("BLE:AA:AA:AA:AA:AA:01", lastSeen = 5L, firstSeen = 1L)
        val other = radio("BLE:BB:BB:BB:BB:BB:02", lastSeen = 2L)
        val cam = radio("BLE:CC:CC:CC:CC:CC:03", fleetIds = setOf("fleet-axon"), lastSeen = 3L)
        assertTrue(session.ingest(clutter, fleets, emptySet(), emptySet(), tight = false))
        assertTrue(session.ingest(later, fleets, emptySet(), emptySet(), tight = true))
        assertFalse(session.ingest(other, fleets, emptySet(), emptySet(), tight = true))
        assertTrue(session.ingest(cam, fleets, emptySet(), emptySet(), tight = true))
        val keys = session.sightings().map { it.key }.toSet()
        assertTrue(clutter.key in keys)
        assertEquals(5L, session.sightings().first { it.key == clutter.key }.lastSeen)
        assertFalse(other.key in keys)
        assertTrue(cam.key in keys)
    }

    @Test
    fun payloadAndWatchlistArePinned() {
        val session = SitSession.start(
            name = "air",
            now = 1L,
            heard = emptyList(),
            fleets = fleets,
            watchDeviceKeys = setOf("BLE:WATCH"),
            watchedFleetIds = emptySet(),
        )
        val drone = radio(
            "BLE:DD:DD:DD:DD:DD:01",
            fleetIds = setOf("fleet-remote-id"),
            lastSeen = 1L,
            payloadLat = 40.0,
            payloadLon = -74.0,
        )
        val named = radio("BLE:WATCH", lastSeen = 1L)
        session.ingest(drone, fleets, setOf("BLE:WATCH"), emptySet())
        session.ingest(named, fleets, setOf("BLE:WATCH"), emptySet())
        repeat(Sit.RADIO_CAP) { i ->
            val mac = "BB:BB:BB:BB:%02X:%02X".format(i / 256, i % 256)
            session.ingest(
                radio("BLE:$mac", mac = mac, lastSeen = i.toLong() + 2),
                fleets,
                setOf("BLE:WATCH"),
                emptySet(),
            )
        }
        val keys = session.sightings().map { it.key }.toSet()
        assertTrue(drone.key in keys)
        assertTrue(named.key in keys)
    }

    @Test
    fun debriefWindowUsesSitBounds() {
        val cam = radio("BLE:AA:AA:AA:AA:AA:01", fleetIds = setOf("fleet-axon"), firstSeen = 5_000L, lastSeen = 20_000L)
        val window = DebriefWindow(10_000L, 30_000L, "lot")
        val doc = DebriefReport.document(
            devices = listOf(cam),
            fleets = fleets,
            settings = AppSettings(tagLocation = false),
            operatorPath = emptyList(),
            now = 40_000L,
            window = window,
        )
        assertTrue(doc.heading.contains("lot"))
        assertTrue(doc.windowLine.contains("sit lot"))
        assertTrue(doc.disclaimer.contains("named sit"))
        assertFalse(doc.windowLine.contains("last 15 minutes"))
    }

    @Test
    fun rollingDebriefUnchangedWithoutSit() {
        val cam = radio("BLE:AA:AA:AA:AA:AA:01", firstSeen = 1L, lastSeen = System.currentTimeMillis())
        val doc = DebriefReport.document(
            devices = listOf(cam),
            fleets = fleets,
            settings = AppSettings(tagLocation = false),
            operatorPath = emptyList(),
        )
        assertEquals("FIELDWATCH FIELD DEBRIEF", doc.heading)
        assertTrue(doc.windowLine.contains("last 15 minutes"))
    }

    @Test
    fun endFreezesSummary() {
        val session = SitSession.start(
            name = "lot",
            now = 1_000L,
            heard = listOf(radio("BLE:AA:AA:AA:AA:AA:01")),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        val file = session.end(5_000L, fleets)
        assertEquals(5_000L, file.summary.endAt)
        assertFalse(file.summary.open)
        assertEquals(1, file.summary.radioCount)
        assertFalse(session.open)
    }

    @Test
    fun pathSubsamplesShortMoves() {
        val session = SitSession.start(
            name = "walk",
            now = 1_000L,
            heard = emptyList(),
            fleets = fleets,
            watchDeviceKeys = emptySet(),
            watchedFleetIds = emptySet(),
        )
        session.recordPath(40.0, -74.0, 1_000L)
        session.recordPath(40.00001, -74.0, 2_000L)
        assertEquals(1, session.operatorPath.size)
        session.recordPath(40.001, -74.0, 8_000L)
        assertEquals(2, session.operatorPath.size)
    }

    private fun radio(
        key: String,
        mac: String = key.substringAfter(':'),
        fleetIds: Set<String> = emptySet(),
        firstSeen: Long = 1L,
        lastSeen: Long = 1L,
        gone: Boolean = false,
        payloadLat: Double? = null,
        payloadLon: Double? = null,
    ) = Sighting(
        key = key,
        kind = RadioKind.BLE,
        mac = mac,
        name = "",
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
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        hitCount = 1,
        fleetIds = fleetIds,
        rssiHistory = emptyList(),
        presence = emptyList(),
        gone = gone,
        payloadLat = payloadLat,
        payloadLon = payloadLon,
    )
}
