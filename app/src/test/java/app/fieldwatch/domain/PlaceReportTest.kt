package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class PlaceReportTest {
    private val zone = ZoneOffset.UTC
    private val t0 = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
    private val now = t0 + 6 * HOUR

    @Test
    fun twoPlacesSortByCountThenRecencyAndKeepTheirColor() {
        val older = record("ble:aa", "BLE:AA:00:00:00:00:01", "Porch", t0 + 2 * HOUR, stop(38.00, t0), stop(38.01, t0 + HOUR))
        val newer = record("ble:bb", "BLE:BB:00:00:00:00:02", "Keys", t0 + 4 * HOUR, stop(38.02, t0), stop(38.03, t0 + 3 * HOUR))
        val three = record(
            "ble:cc",
            "BLE:CC:00:00:00:00:03",
            "Bike",
            t0 + HOUR,
            stop(38.10, t0),
            stop(38.11, t0 + 20 * MINUTE),
            stop(38.12, t0 + 40 * MINUTE),
        )
        val one = record("ble:dd", "BLE:DD:00:00:00:00:04", "Once", now, stop(38.20, t0))
        val rows = PlaceReport.rows(
            records = listOf(older, one, newer, three),
            devices = emptyList(),
            fleets = emptyList(),
            watchlist = emptyList(),
            now = now,
            zone = zone,
            demo = false,
            streets = emptyMap(),
        )
        assertEquals(listOf("Bike", "Keys", "Porch"), rows.map { it.title })
        assertEquals(listOf(3, 2, 2), rows.map { it.count })
        assertEquals(PlaceReport.colorAt(0), rows[0].color)
        assertEquals(PlaceReport.colorAt(1), rows[1].color)
        assertEquals(PlaceReport.colorAt(2), rows[2].color)
        assertEquals(rows[0].color, PlaceReport.pins(rows).first { it.lat == 38.10 }.color)
        assertEquals(3, rows[0].stops.size)
        assertEquals(listOf(1, 2, 3), rows[0].visits.map { it.number })
        assertEquals("12:00", rows[0].visits[0].time)
        assertEquals("", rows[0].note)
        assertTrue(rows.none { it.known })
    }

    @Test
    fun theNinthRadioRepeatsTheFirstColor() {
        val records = (0 until 9).map { i ->
            record(
                "ble:$i",
                "BLE:AA:00:00:00:00:0$i",
                "Tag $i",
                t0 + i * MINUTE,
                stop(38.0 + i * 0.01, t0),
                stop(38.2 + i * 0.01, t0 + HOUR),
            )
        }
        val rows = PlaceReport.rows(records, emptyList(), emptyList(), emptyList(), now, zone, false, emptyMap())
        assertEquals(9, rows.size)
        assertEquals(PlaceReport.colorAt(1), rows[1].color)
        assertEquals(PlaceReport.colorAt(0), rows[8].color)
        assertTrue(rows[1].color != rows[8].color)
    }

    @Test
    fun privacyMasksTheAddressAndDropsTheMapCoordinatesFromTheLines() {
        val row = PlaceReport.rows(
            records = listOf(
                record("ble:aa", "BLE:00:11:22:33:44:55", "Keys", now, stop(38.0, t0), stop(38.01, t0 + HOUR)),
            ),
            devices = emptyList(),
            fleets = emptyList(),
            watchlist = emptyList(),
            now = now,
            zone = zone,
            demo = true,
            streets = mapOf(Geo.cellKey(38.0, -77.0) to "1 Main St"),
        ).single()
        assertEquals("Keys", row.title)
        assertEquals("00:11:22:**:**:**", row.address)
        assertTrue(row.visits.all { it.coord == "masked" && it.street.isEmpty() })
        assertTrue(row.visits.none { it.coord.contains("38.00000") || it.street.contains("Main") })
        assertEquals(2, row.stops.size)
    }

    @Test
    fun aBlankNameFallsBackToTheAddressAndACustomNameWins() {
        val key = "BLE:00:11:22:33:44:55"
        val saved = record("ble:aa", key, "", now, stop(38.0, t0), stop(38.01, t0 + HOUR))
        val plain = PlaceReport.rows(
            listOf(saved),
            emptyList(),
            emptyList(),
            emptyList(),
            now,
            zone,
            false,
            emptyMap(),
        ).single()
        assertEquals("00:11:22:33:44:55", plain.title)
        assertEquals("", plain.address)
        val named = PlaceReport.rows(
            listOf(saved.copy(label = "Tile")),
            devices = listOf(radio(key, "Front door")),
            fleets = emptyList(),
            watchlist = listOf(WatchTarget(id = "w", deviceKey = key, label = "Keys")),
            now = now,
            zone = zone,
            demo = false,
            streets = emptyMap(),
        ).single()
        assertEquals("Keys", named.title)
        assertEquals("00:11:22:33:44:55", named.address)
        assertTrue(named.known)
    }

    @Test
    fun aStopKeepsTheTimeStreetAndCoordinateApart() {
        val home = PlaceMemory.Place(
            lat = 38.0,
            lon = -77.0,
            firstAt = t0,
            lastAt = t0 + 4 * MINUTE,
            count = 2,
            name = "Home",
        )
        val row = PlaceReport.rows(
            records = listOf(
                record("ble:aa", "BLE:00:11:22:33:44:55", "Keys", now, home, stop(38.01, t0 + HOUR))
                    .copy(returnAt = now, returnGapMs = 25 * MINUTE),
            ),
            devices = emptyList(),
            fleets = emptyList(),
            watchlist = emptyList(),
            now = now,
            zone = zone,
            demo = false,
            streets = mapOf(Geo.cellKey(38.0, -77.0) to "1 Main St"),
        ).single()
        val first = row.visits[0]
        assertEquals(1, first.number)
        assertEquals("12:00 – 12:04", first.time)
        assertEquals("Home", first.name)
        assertEquals("1 Main St", first.street)
        assertEquals("38.00000, -77.00000", first.coord)
        assertEquals(2, row.visits[1].number)
        assertEquals("", row.visits[1].street)
        assertEquals("Heard again after 25 minutes", row.note)
    }

    @Test
    fun aLiveRadioUsesNameAndTypeAheadOfTheStoredLabel() {
        val key = "BLE:00:11:22:33:44:66"
        val saved = record("ble:aa", key, "old label", now, stop(38.0, t0), stop(38.01, t0 + HOUR))
        val named = PlaceReport.rows(
            listOf(saved),
            devices = listOf(radio(key, "Model Y")),
            fleets = emptyList(),
            watchlist = emptyList(),
            now = now,
            zone = zone,
            demo = false,
            streets = emptyMap(),
        ).single()
        assertEquals("Model Y", named.title)
        assertEquals("00:11:22:33:44:66", named.address)
        val tesla = radio(key, "").copy(fleetIds = setOf("tesla"))
        val typed = PlaceReport.rows(
            listOf(saved),
            devices = listOf(tesla),
            fleets = listOf(Fleet(id = "tesla", name = "Tesla")),
            watchlist = emptyList(),
            now = now,
            zone = zone,
            demo = false,
            streets = emptyMap(),
        ).single()
        assertEquals("Tesla vehicle or phone-as-key", typed.title)
        val unnamed = PlaceReport.rows(
            listOf(saved.copy(label = "Tile")),
            devices = listOf(radio(key, "")),
            fleets = emptyList(),
            watchlist = emptyList(),
            now = now,
            zone = zone,
            demo = false,
            streets = emptyMap(),
        ).single()
        assertEquals("Tile", unnamed.title)
    }

    @Test
    fun aRadioThatHasLeftKeepsTheSavedName() {
        val key = "BLE:F2:57:00:00:00:01"
        val saved = record("ble:f2", key, "Test radio 1", now, stop(38.0, t0), stop(38.01, t0 + HOUR))
        val row = PlaceReport.rows(
            listOf(saved),
            emptyList(),
            emptyList(),
            emptyList(),
            now,
            zone,
            false,
            emptyMap(),
        ).single()
        assertEquals("Test radio 1", row.title)
        val remembered = PlaceReport.remembered(saved)
        assertEquals("Test radio 1", remembered?.name)
        assertEquals(key, remembered?.key)
        assertTrue(remembered?.gone == true)
    }

    @Test
    fun mineStaysOffTheList() {
        val key = "BLE:00:11:22:33:44:55"
        val rows = PlaceReport.rows(
            records = listOf(record("ble:aa", key, "Mine", now, stop(38.0, t0), stop(38.01, t0 + HOUR))),
            devices = emptyList(),
            fleets = emptyList(),
            watchlist = listOf(WatchTarget(id = "w", deviceKey = key, label = "Mine", mine = true)),
            now = now,
            zone = zone,
            demo = false,
            streets = emptyMap(),
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun aNameStaysWhenTheNextHearHasNone() {
        val id = "ble:00:11:22:33:44:55"
        val first = PlaceMemory.note(null, hear(id, t0, "Tile"))
        val quiet = PlaceMemory.note(first, hear(id, t0 + 30 * MINUTE, ""))
        assertEquals("Tile", quiet.label)
        val unnamed = PlaceMemory.note(quiet, hear(id, t0 + HOUR, "unnamed LE"))
        assertEquals("Tile", unnamed.label)
        val renamed = PlaceMemory.note(unnamed, hear(id, t0 + 2 * HOUR, "Keys"))
        assertEquals("Keys", renamed.label)
    }

    @Test
    fun heardLabelSkipsAMacAndAnUnnamedAdvertiser() {
        val device = radio("BLE:00:11:22:33:44:55", "00:11:22:33:44:55")
        assertEquals("", PlaceMemory.heardLabel(device, emptyList(), null))
        assertEquals("", PlaceMemory.heardLabel(device.copy(name = ""), emptyList(), "unnamed LE"))
        assertEquals("Front door", PlaceMemory.heardLabel(device.copy(name = ""), emptyList(), "Front door"))
        assertEquals("Tile", PlaceMemory.heardLabel(device.copy(name = "Tile"), emptyList(), null))
    }

    private fun record(
        id: String,
        key: String,
        label: String,
        lastHeard: Long,
        vararg places: PlaceMemory.Place,
    ) = PlaceMemory.Record(
        id = id,
        deviceKey = key,
        places = places.toList(),
        lastHeardAt = lastHeard,
        label = label,
    )

    private fun stop(lat: Double, at: Long) = PlaceMemory.Place(
        lat = lat,
        lon = -77.0,
        firstAt = at,
        lastAt = at,
        count = 2,
    )

    private fun hear(id: String, at: Long, label: String) = PlaceMemory.Hear(
        id = id,
        deviceKey = "BLE:00:11:22:33:44:55",
        at = at,
        lat = 38.0,
        lon = -77.0,
        sitId = null,
        sitLabel = null,
        localDay = 0L,
        label = label,
    )

    private fun radio(key: String, name: String) = Sighting(
        key = key,
        kind = RadioKind.BLE,
        mac = key.substringAfter(':'),
        name = name,
        rssi = -60,
        rssiMin = -70,
        rssiMax = -50,
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
        firstSeen = t0,
        lastSeen = now,
        hitCount = 2,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
    )

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
