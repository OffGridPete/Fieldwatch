package app.fieldwatch.domain

import app.fieldwatch.data.PlaceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class PlaceMemoryTest {
    private val zone = ZoneOffset.UTC
    private val day = Instant.parse("2026-06-15T12:00:00Z")
    private val t0 = day.toEpochMilli()
    private val localDay = day.atZone(zone).toLocalDate().toEpochDay()
    private val names = mapOf("tile" to "Tile")

    @Test
    fun trackerAtThreePlacesAlerts() {
        val record = visit(null, home, t0)
            .let { visit(it, store, t0 + 3 * HOUR) }
            .let { visit(it, park, t0 + 6 * HOUR) }
        val end = t0 + 6 * HOUR + MINUTE
        val lines = PlaceMemory.detailLines(record, end, zone)
        assertEquals("Seen at 3 places today", lines[0])
        assertEquals(5, lines.size)
        assertTrue(lines.any { it.contains("12:00") })
        assertTrue(lines.last().startsWith("Heard again after "))
        val heard = tile().copy(firstSeen = t0, lastSeen = end)
        val text = PlaceMemory.section(
            devices = listOf(heard),
            records = listOf(record),
            names = names,
            customNames = emptyMap(),
            mineKeys = emptySet(),
            now = end,
            zone = zone,
        )
        assertTrue(text!!.contains("Seen at 3 places today"))
        assertTrue(text.contains(heard.mac))
        val doc = DebriefReport.document(
            devices = listOf(heard),
            fleets = listOf(Fleet(id = "tile", name = "Tile")),
            settings = AppSettings(tagLocation = true),
            operatorPath = emptyList(),
            now = end,
            window = DebriefWindow(t0, end, "Day"),
            placeRecords = listOf(record),
        )
        val plain = doc.toPlainText()
        assertTrue(plain.contains("SEEN AT MORE THAN ONE PLACE"))
        assertTrue(plain.contains("Seen at 3 places today"))
        assertTrue(plain.contains("38.00000, -77.00000"))
        assertTrue(plain.contains("38.02000, -77.00000"))
        assertTrue(plain.contains(heard.mac))
    }

    @Test
    fun placeLineAddsStreetAndPrivacyDropsIt() {
        val record = visit(null, home, t0).let { visit(it, store, t0 + 3 * HOUR) }
        val end = t0 + 3 * HOUR + MINUTE
        val streets = mapOf(Geo.cellKey(home.first, home.second) to "1 Main St")
        val lines = PlaceMemory.detailLines(record, end, zone, streets = streets)
        assertTrue(lines.any { it.contains("1 Main St") && it.contains("38.00000, -77.00000") })
        val hidden = PlaceMemory.detailLines(record, end, zone, demo = true, streets = streets)
        assertTrue(hidden.any { it.endsWith(" · masked") })
        assertTrue(hidden.none { it.contains("Main") || it.contains("38.00000") })
        val heard = tile().copy(firstSeen = t0, lastSeen = end)
        val doc = DebriefReport.document(
            devices = listOf(heard),
            fleets = listOf(Fleet(id = "tile", name = "Tile")),
            settings = AppSettings(tagLocation = true),
            operatorPath = emptyList(),
            now = end,
            window = DebriefWindow(t0, end, "Day"),
            places = DebriefPlaces(attempted = true, available = true, note = "named", namesByCell = streets),
            placeRecords = listOf(record),
        )
        val plain = doc.toPlainText()
        assertTrue(plain.contains("1 Main St"))
        assertTrue(plain.contains("38.00000, -77.00000"))
        val privateDoc = DebriefReport.document(
            devices = listOf(heard),
            fleets = listOf(Fleet(id = "tile", name = "Tile")),
            settings = AppSettings(tagLocation = true, demoMode = true),
            operatorPath = emptyList(),
            now = end,
            window = DebriefWindow(t0, end, "Day"),
            places = DebriefPlaces.Off,
            placeRecords = listOf(record),
        )
        val masked = privateDoc.toPlainText()
        assertTrue(masked.contains("masked"))
        assertFalse(masked.contains("38.00000"))
        assertFalse(masked.contains("Main"))
    }

    @Test
    fun placesFromTheLastWeekStayListed() {
        val day = 24 * HOUR
        val record = visit(null, home, t0).let { visit(it, store, t0 + 3 * day) }
        val end = t0 + 3 * day + MINUTE
        val lines = PlaceMemory.detailLines(record, end, zone)
        assertEquals("Seen at 2 places", lines[0])
        assertTrue(lines[1].contains("Jun 15, 12:00"))
        assertTrue(lines[2].startsWith("Home · 12:00"))
        assertTrue(lines.last().startsWith("Heard again after 3 days"))
        val stale = visit(null, home, t0).let { visit(it, store, t0 + 8 * day) }
        val later = PlaceMemory.detailLines(stale, t0 + 8 * day + MINUTE, zone)
        assertTrue(later.none { it.startsWith("Seen at ") })
    }

    @Test
    fun onePlaceBssidDoesNotAlert() {
        val wifi = radio(RadioKind.WIFI, "00:11:22:33:44:66", fleetIds = emptySet())
        assertNull(PlaceMemory.accept(wifi, names, emptySet(), generic = false))
        val id = PlaceMemory.accept(wifi, names, emptySet(), generic = true)!!
        var record: PlaceMemory.Record? = null
        repeat(6) { i ->
            record = PlaceMemory.note(record, hear(id, t0 + i * MINUTE, home))
        }
        val lines = PlaceMemory.detailLines(record!!, t0 + 10 * MINUTE, zone)
        assertTrue(lines.none { it.startsWith("Seen at ") })
    }

    @Test
    fun rotatingBleDoesNotAlert() {
        val rotating = radio(RadioKind.BLE, "02:11:22:33:44:55", fleetIds = setOf("tile"))
        assertNull(PlaceMemory.accept(rotating, names, emptySet()))
    }

    @Test
    fun mineDoesNotAlert() {
        val device = tile()
        assertNull(PlaceMemory.accept(device, names, setOf(device.key)))
        assertEquals("ble:${device.mac}", PlaceMemory.accept(device, names, emptySet()))
    }

    @Test
    fun returnAfterTwentyFiveMinutesFlags() {
        val id = "ble:${tile().mac}"
        val record = PlaceMemory.note(
            PlaceMemory.note(null, hear(id, t0, home)),
            hear(id, t0 + 25 * MINUTE, home),
        )
        val lines = PlaceMemory.detailLines(record, t0 + 25 * MINUTE, zone)
        assertEquals(listOf("Heard again after 25 minutes"), lines)
    }

    @Test
    fun returnAfterTenMinutesDoesNotFlag() {
        val id = "ble:${tile().mac}"
        val record = PlaceMemory.note(
            PlaceMemory.note(null, hear(id, t0, home)),
            hear(id, t0 + 10 * MINUTE, home),
        )
        assertTrue(PlaceMemory.detailLines(record, t0 + 10 * MINUTE, zone).isEmpty())
    }

    @Test
    fun driveAlreadyWithYouDoesNotOpenASecondPlace() {
        val id = "ble:${tile().mac}"
        var record = visit(null, home, t0)
        record = PlaceMemory.note(record, hear(id, t0 + 5 * MINUTE, store))
        record = PlaceMemory.note(record, hear(id, t0 + 10 * MINUTE, park))
        assertEquals(1, PlaceMemory.placeCount(record, t0 + 10 * MINUTE))
        assertNull(
            PlaceMemory.section(
                devices = listOf(tile()),
                records = listOf(record),
                names = names,
                customNames = emptyMap(),
                mineKeys = emptySet(),
                now = t0 + 10 * MINUTE,
                zone = zone,
            ),
        )
    }

    @Test
    fun oneJumpedFixDoesNotCount() {
        val id = "ble:${tile().mac}"
        val record = PlaceMemory.note(visit(null, home, t0), hear(id, t0 + 30 * MINUTE, store))
        assertEquals(1, PlaceMemory.placeCount(record, t0 + 30 * MINUTE))
    }

    @Test
    fun placeCapStaysSmall() {
        val id = "ble:${tile().mac}"
        var record: PlaceMemory.Record? = null
        repeat(12) { i ->
            val at = t0 + i * 2 * HOUR
            record = visit(record, i * 0.02 to -77.0, at)
        }
        assertEquals(PlaceMemory.MAX_PLACES, record!!.places.size)
        assertTrue(record.places.any { it.firstAt == t0 + 11 * 2 * HOUR })
        assertTrue(record.places.none { it.firstAt == t0 })
    }

    @Test
    fun oneJumpAtTheCapDoesNotDropAnOlderStop() {
        val id = "ble:${tile().mac}"
        var record: PlaceMemory.Record? = null
        repeat(8) { i ->
            record = visit(record, i * 0.02 to -77.0, t0 + i * 2 * HOUR)
        }
        val oldest = t0
        val jumped = PlaceMemory.note(record, hear(id, t0 + 16 * HOUR, 0.20 to -77.0))
        assertTrue(jumped.places.any { it.firstAt == oldest })
        assertEquals(8, PlaceMemory.placeCount(jumped, t0 + 16 * HOUR))
        val confirmed = PlaceMemory.note(jumped, hear(id, t0 + 16 * HOUR + MINUTE, 0.20 to -77.0))
        assertTrue(confirmed.places.none { it.firstAt == oldest })
        assertTrue(confirmed.places.any { it.firstAt == t0 + 16 * HOUR })
        assertEquals(8, PlaceMemory.placeCount(confirmed, t0 + 16 * HOUR + MINUTE))
    }

    @Test
    fun trackerLatchSeedsAnOldFileAndAKnownEmptySetCanWarnAgain() {
        val file = File.createTempFile("places-tracker", ".json")
        try {
            val now = System.currentTimeMillis()
            val mac = "AA:BB:CC:DD:EE:01"
            val record = placeRecord(mac, now)
            file.writeText("""{"format":"fieldwatch-places","records":[$record,${placeRecord("rid:plane", now, id = "rid:plane")}]}""")
            val seeded = PlaceStore(file)
            seeded.load()
            val device = tile().copy(key = "BLE:$mac", mac = mac, rssi = -40)
            assertTrue(seeded.trackerPlaces(armed = true, now, listOf(device)).isEmpty())
            seeded.flush()
            val saved = file.readText()
            assertTrue(saved.contains("\"trackerSeenKnown\":true"))
            assertTrue(saved.contains("\"trackerSeen\":[\"ble:$mac\"]"))
            assertFalse(saved.contains("\"trackerSeen\":[\"rid:"))
            val again = PlaceStore(file)
            again.load()
            assertTrue(again.trackerPlaces(armed = true, now, listOf(device)).isEmpty())

            file.writeText("""{"format":"fieldwatch-places","trackerSeenKnown":true,"records":[$record]}""")
            val open = PlaceStore(file)
            open.load()
            val fresh = open.trackerPlaces(armed = true, now, listOf(device))
            assertEquals(1, fresh.size)
            assertEquals("places:ble:$mac", fresh[0].id)
            open.flush()
            val quiet = file.readText()
            assertTrue(quiet.contains("\"trackerSeenKnown\":true"))
            val held = PlaceStore(file)
            held.load()
            assertTrue(held.trackerPlaces(armed = true, now, listOf(device)).isEmpty())
        } finally {
            file.delete()
        }
    }

    private fun placeRecord(mac: String, now: Long, id: String = "ble:$mac"): String {
        val early = now - 3_600_000L
        return """{"id":"$id","deviceKey":"BLE:$mac","label":"Tile","lastHeardAt":$now,"places":[{"lat":38.0,"lon":-77.0,"firstAt":$early,"lastAt":$early,"count":2},{"lat":38.02,"lon":-77.0,"firstAt":$now,"lastAt":$now,"count":2}]}"""
    }

    @Test
    fun placeLinesStayAfterTheAdvertisementIsGone() {
        val file = File.createTempFile("places", ".json")
        try {
            val places = PlaceStore(file)
            val fleets = listOf(Fleet(id = "tile", name = "Tile", kind = SignatureClass.FINDER))
            val start = System.currentTimeMillis() - 40 * MINUTE
            val device = tile().copy(latitude = home.first, longitude = home.second)
            places.hear(listOf(device.copy(lastSeen = start)), fleets, emptyList(), null, "Home")
            places.hear(listOf(device.copy(lastSeen = start + 1_000L)), fleets, emptyList(), null, "Home")
            val away = device.copy(latitude = store.first, longitude = store.second)
            val later = start + 20 * MINUTE
            places.hear(listOf(away.copy(lastSeen = later)), fleets, emptyList(), null, "Store")
            places.hear(listOf(away.copy(lastSeen = later + 1_000L)), fleets, emptyList(), null, "Store")
            val bare = device.copy(
                name = "",
                fleetIds = emptySet(),
                lastSeen = later + 1_000L,
            )
            val lines = places.linesFor(bare, emptyList(), emptySet(), System.currentTimeMillis())
            assertTrue(lines.any { it.startsWith("Seen at ") })
            val other = bare.copy(key = "BLE:AA:BB:CC:DD:EE:FF", mac = "AA:BB:CC:DD:EE:FF")
            assertTrue(places.linesFor(other, emptyList(), emptySet(), System.currentTimeMillis()).isEmpty())
            assertTrue(places.linesFor(bare, emptyList(), setOf(bare.key), System.currentTimeMillis()).isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun storeRoundTripKeepsTheRecord() {
        val file = File.createTempFile("places", ".json")
        try {
            val store = PlaceStore(file)
            val device = tile().copy(
                lastSeen = System.currentTimeMillis(),
                latitude = home.first,
                longitude = home.second,
            )
            val fleets = listOf(Fleet(id = "tile", name = "Tile", kind = SignatureClass.FINDER))
            store.hear(listOf(device), fleets, emptyList(), null, "Home")
            store.hear(listOf(device.copy(lastSeen = device.lastSeen + 1_000L)), fleets, emptyList(), null, "Home")
            assertEquals(1, store.snapshot().size)
            store.flush()
            val loaded = PlaceStore(file)
            loaded.load()
            assertEquals(store.snapshot().single().id, loaded.snapshot().single().id)
            assertEquals(2, loaded.snapshot().single().places.single().count)
            assertEquals("Tile", loaded.snapshot().single().label)
        } finally {
            file.delete()
        }
    }

    @Test
    fun localDateFollowsThePhoneZone() {
        val east = ZoneId.of("America/New_York")
        val at = Instant.parse("2026-06-15T03:30:00Z")
        assertEquals(LocalDate.of(2026, 6, 14), PlaceMemory.localDate(at.toEpochMilli(), east))
        assertEquals(LocalDate.of(2026, 6, 15), PlaceMemory.localDate(at.toEpochMilli(), ZoneOffset.UTC))
    }

    private fun visit(prev: PlaceMemory.Record?, at: Pair<Double, Double>, whenAt: Long): PlaceMemory.Record {
        val id = "ble:${tile().mac}"
        val first = PlaceMemory.note(prev, hear(id, whenAt, at, "Home"))
        return PlaceMemory.note(first, hear(id, whenAt + MINUTE, at, "Home"))
    }

    private fun hear(
        id: String,
        at: Long,
        where: Pair<Double, Double>,
        label: String? = null,
    ) = PlaceMemory.Hear(
        id = id,
        deviceKey = tile().key,
        at = at,
        lat = where.first,
        lon = where.second,
        sitId = "sit",
        sitLabel = label,
        localDay = localDay,
    )

    private fun tile() = radio(RadioKind.BLE, "00:11:22:33:44:55", fleetIds = setOf("tile"))

    private fun radio(kind: RadioKind, mac: String, fleetIds: Set<String>) = Sighting(
        key = "${kind.name}:$mac",
        kind = kind,
        mac = mac,
        name = "Tile",
        rssi = -60,
        rssiMin = -70,
        rssiMax = -50,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = MacUtil.isRandomized(mac),
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = t0,
        lastSeen = t0,
        hitCount = 1,
        fleetIds = fleetIds,
        rssiHistory = emptyList(),
        presence = emptyList(),
    )

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        val home = 38.0 to -77.0
        val store = 38.01 to -77.0
        val park = 38.02 to -77.0
    }
}
