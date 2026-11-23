package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AircraftHistoryTest {
    private val zone = ZoneOffset.UTC
    private val day = AircraftHistory.DAY_MS
    private val quiet = AircraftHistory.QUIET_MS

    @Test
    fun aShortGapStaysOnePassAndAQuietGapStartsAnother() {
        val t0 = 1_700_000_000_000L
        val heardAgain = t0 + 5 * 60_000L
        var record = note(null, hear("ABC123", t0, ble = true))
        record = note(record, hear("ABC123", heardAgain, wifi = true, hasPosition = true))
        assertEquals(1, record.passes.size)
        assertTrue(record.passes[0].ble)
        assertTrue(record.passes[0].wifi)
        assertTrue(record.passes[0].hasPosition)
        val next = heardAgain + quiet
        record = note(record, hear("ABC123", next))
        assertEquals(2, record.passes.size)
        val live = AircraftHistory.live(record, next, next, zone)!!
        assertEquals(1, live.days)
        assertEquals(2, live.times)
        assertTrue(live.earlier is AircraftHistory.Earlier.Clock)
    }

    @Test
    fun theNextMorningIsAnotherDay() {
        val first = 1_763_800_800_000L
        val next = first + day
        val record = note(note(null, hear("SERIAL", first)), hear("SERIAL", next))
        val live = AircraftHistory.live(record, next, next, zone)!!
        assertEquals(2, live.days)
        assertEquals(2, live.times)
        assertEquals(AircraftHistory.Earlier.Yesterday, live.earlier)
    }

    @Test
    fun aPassOlderThanTheWindowDrops() {
        val now = 1_763_800_800_000L
        val old = hear("OLD", now - AircraftHistory.WINDOW_MS - 60_000L)
        assertNull(AircraftHistory.note(null, old, now))
        val kept = note(null, hear("KEEP", now - day))
        val live = AircraftHistory.live(kept, now - day, now, zone)!!
        assertEquals(1, live.times)
        assertNull(live.earlier)
    }

    @Test
    fun aBlankIdIsIgnoredAndALabelStays() {
        assertNull(AircraftHistory.idOf("  "))
        assertNull(AircraftHistory.idOf("x".repeat(81)))
        val t0 = 1_700_000_000_000L
        val named = note(null, hear("ID", t0, label = "DJI", selfId = "Yard"))
        val again = note(named, hear("ID", t0 + 1_000L))
        assertEquals("DJI", again.label)
        assertEquals("Yard", again.selfId)
    }

    @Test
    fun thePilotKindStaysWhenALaterHearHasNone() {
        val t0 = 1_700_000_000_000L
        val first = hear("ID", t0, opKind = OperatorLoc.LIVE)
        val second = hear("ID", t0 + 1_000L)
        val record = note(note(null, first), second)
        assertEquals(OperatorLoc.LIVE, record.passes.single().opKind)
    }

    @Test
    fun aVisitKeepsTheAltitudeAndSpeedItSent() {
        val t0 = 1_700_000_000_000L
        val moved = t0 + 2_000L
        val record = note(
            note(null, hear("ID", t0, fix = PayloadFix(t0, 38.90, -77.00, alt = 30.0, speed = 4.0))),
            hear("ID", moved, fix = PayloadFix(moved, 38.9003, -77.00, alt = 55.0, speed = 8.0)),
        )
        val fixes = record.passes.single().fixes
        assertEquals(30.0, fixes.first().alt)
        assertEquals(8.0, fixes.last().speed!!, 0.0)
        assertEquals(
            listOf("Altitude: 30–55 m", "Speed: 4–8 m/s"),
            AircraftTrail.motionSpanLines(fixes),
        )
    }

    @Test
    fun aPathKeepsARealMoveAndDropsAJump() {
        val t0 = 1_700_000_000_000L
        val movedAt = t0 + 2_000L
        val record = note(
            note(null, hear("ID", t0, fix = point(t0, 38.90, -77.00))),
            hear("ID", movedAt, fix = point(movedAt, 38.9003, -77.00)),
        )
        assertEquals(2, record.passes.single().fixes.size)
        val jumpAt = t0 + 4_000L
        val jumped = note(record, hear("ID", jumpAt, fix = point(jumpAt, 39.90, -77.00)))
        assertEquals(2, jumped.passes.single().fixes.size)
        assertEquals(0, AircraftHistory.lengthMinutes(t0, t0 + 20_000L))
        assertEquals(6, AircraftHistory.lengthMinutes(t0, t0 + 6 * 60_000L))
    }

    @Test
    fun aPointWithinEightMetersDoesNotGrowThePath() {
        val t0 = 1_700_000_000_000L
        val record = note(
            note(null, hear("ID", t0, fix = point(t0, 38.90, -77.00))),
            hear("ID", t0 + 1_000L, fix = point(t0 + 1_000L, 38.90001, -77.00)),
        )
        assertEquals(1, record.passes.single().fixes.size)
    }

    @Test
    fun closeVisitsShareAMapAndAFarPilotStaysOffIt() {
        val t0 = 1_700_000_000_000L
        var record = note(
            null,
            hear(
                "ID", t0,
                fix = point(t0, 38.90, -77.00),
                pilotLat = 38.9004,
                pilotLon = -77.00,
                opKind = OperatorLoc.LIVE,
            ),
        )
        record = note(record, hear("ID", t0 + quiet, fix = point(t0 + quiet, 38.901, -77.00)))
        val farAt = t0 + quiet * 2
        record = note(
            record,
            hear(
                "ID", farAt,
                fix = point(farAt, 38.95, -77.00),
                pilotLat = 39.00,
                pilotLon = -77.00,
                opKind = OperatorLoc.TAKEOFF,
            ),
        )
        val pictures = AircraftHistory.pictures(record.passes)
        assertEquals(2, pictures.size)
        assertEquals(1, pictures[0].model.craft.size)
        assertTrue(pictures[0].model.pilots.isEmpty())
        assertEquals(2, pictures[1].model.craft.size)
        assertEquals(1, pictures[1].model.pilots.size)
        assertTrue(pictures[1].model.craft.all { it.color != 0L })
        assertEquals(0L, AircraftHistory.colorAt(record.passes, -1))
        assertTrue(pictures.all { picture -> picture.model.craft.all { it.emergency.isEmpty() } })
    }

    @Test
    fun anEmergencyNotesTheTimeAndALaterLocationClearsIt() {
        val t0 = 1_700_000_000_000L
        var record = note(
            null,
            hear("ID", t0, fix = point(t0, 38.90, -77.00), emergency = true, emergencyText = "Low battery"),
        )
        record = note(record, hear("ID", t0 + 1_000L))
        val open = record.passes.single().emergencies.single()
        assertEquals(t0, open.start)
        assertEquals(t0, open.end)
        assertEquals("Low battery", open.text)
        assertTrue(open.open)
        val moved = t0 + 4_000L
        record = note(
            record,
            hear(
                "ID",
                moved,
                fix = point(moved, 38.9003, -77.00),
                emergency = true,
                emergencyText = "Motor",
            ),
        )
        val extended = record.passes.single().emergencies.single()
        assertEquals(t0, extended.start)
        assertEquals(moved, extended.end)
        assertEquals("Motor", extended.text)
        assertTrue(extended.open)
        val clearedAt = t0 + 8_000L
        record = note(
            record,
            hear("ID", clearedAt, fix = point(clearedAt, 38.9006, -77.00), emergency = false),
        )
        val closed = record.passes.single().emergencies.single()
        assertEquals(moved, closed.end)
        assertTrue(!closed.open)
        val again = t0 + 12_000L
        record = note(
            record,
            hear("ID", again, fix = point(again, 38.9009, -77.00), emergency = true),
        )
        val spans = record.passes.single().emergencies
        assertEquals(2, spans.size)
        assertEquals(t0, spans[0].start)
        assertTrue(!spans[0].open)
        assertEquals(again, spans[1].start)
        assertEquals(again, spans[1].end)
        assertTrue(spans[1].open)
        val indexes = AircraftHistory.emergencyIndexes(record.passes.single())
        assertEquals(listOf(0, 1, 3), indexes)
        assertEquals(indexes, AircraftHistory.pictures(record.passes).single().model.craft.single().emergency)
    }

    @Test
    fun aHoverStillRecordsWhenTheEmergencyWasDeclared() {
        val t0 = 1_700_000_000_000L
        val declared = t0 + 3_000L
        val record = note(
            note(null, hear("ID", t0, fix = point(t0, 38.90, -77.00))),
            hear(
                "ID",
                declared,
                fix = point(declared, 38.90001, -77.00),
                emergency = true,
                emergencyText = "Low battery",
            ),
        )
        val pass = record.passes.single()
        assertEquals(1, pass.fixes.size)
        assertTrue(pass.fixes.single().emergency)
        val span = pass.emergencies.single()
        assertEquals(declared, span.start)
        assertEquals(declared, span.end)
        assertEquals("Low battery", span.text)
        assertEquals(listOf(0), AircraftHistory.emergencyIndexes(pass))
    }

    private fun point(at: Long, lat: Double, lon: Double) = PayloadFix(at, lat, lon)

    private fun hear(
        id: String,
        at: Long,
        ble: Boolean = false,
        wifi: Boolean = false,
        hasPosition: Boolean = false,
        opKind: Int? = null,
        label: String = "",
        selfId: String = "",
        fix: PayloadFix? = null,
        pilotLat: Double? = null,
        pilotLon: Double? = null,
        emergency: Boolean? = null,
        emergencyText: String = "",
    ) = AircraftHistory.Hear(
        id = id,
        at = at,
        hasPosition = hasPosition || fix != null,
        opKind = opKind,
        ble = ble,
        wifi = wifi,
        label = label,
        selfId = selfId,
        fix = fix,
        pilotLat = pilotLat,
        pilotLon = pilotLon,
        emergency = emergency,
        emergencyText = emergencyText,
    )

    private fun note(prev: AircraftHistory.Record?, hear: AircraftHistory.Hear): AircraftHistory.Record =
        AircraftHistory.note(prev, hear, hear.at)!!
}
