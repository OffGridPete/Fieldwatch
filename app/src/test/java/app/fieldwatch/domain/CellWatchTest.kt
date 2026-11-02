package app.fieldwatch.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellWatchTest {
    private val DAY = 86_400_000L
    @Test
    fun aMapKeepsPlacesNearThePhoneAndDropsAFarOne() {
        val near = spot("near", 28.01, -81.0, 1_000L)
        val far = spot("far", 29.2, -81.0, 2_000L)
        val around = CellWatch.mapPlaces(listOf(near, far), 28.0, -81.0)
        assertEquals(listOf("near"), around.map { it.id })
        val newest = CellWatch.mapPlaces(listOf(near, far), null, null)
        assertEquals(listOf("far"), newest.map { it.id })
        val both = CellWatch.mapPlaces(
            listOf(near, spot("also", 28.02, -81.01, 3_000L)),
            28.0,
            -81.0,
        )
        assertEquals(setOf("near", "also"), both.map { it.id }.toSet())
        assertTrue(CellWatch.mapPlaces(listOf(spot("bad", Double.NaN, 0.0, 1L)), 28.0, -81.0).isEmpty())
    }

    @Test
    fun firstStillVisitStoresAndDoesNotWarn() {
        val step = tick(CellBook(), lte(), still(), 1_000L)
        assertTrue(step.fresh.isEmpty())
        assertTrue(step.book.episodes.isEmpty())
        val place = step.book.places.single()
        assertFalse(place.baselined)
        assertTrue(place.visiting)
        assertEquals(100L, place.cells.single().identity.cell)
    }

    @Test
    fun laterStillVisitWarnsOnceAndARepeatDoesNot() {
        val learned = home(leave(tick(CellBook(), lte(), still(), 1_000L).book, 2_000L), 2_500L)
        val first = tick(learned, lte(cell = 300), still(), 3_000L)
        assertEquals(CellKind.NEW, first.fresh.single().kind)
        val again = tick(first.book, lte(cell = 300), still(), 4_000L)
        assertTrue(again.fresh.isEmpty())
        assertEquals(1, again.book.episodes.count { it.open })
    }

    @Test
    fun newPlmnWarnsAndAPciChangeOnAStoredCellDoesNot() {
        val learned = home(leave(tick(CellBook(), lte(), still(), 1_000L).book, 2_000L), 2_500L)
        val plmn = tick(learned, lte(plmn = "311-480"), still(), 3_000L)
        assertEquals(CellKind.NEW, plmn.fresh.single().kind)
        assertFalse(plmn.book.places.single().cells.any { it.identity.plmn == "311-480" })
        val pci = tick(learned, lte(pci = 77), still(), 3_000L)
        assertTrue(pci.fresh.isEmpty())
        assertEquals(77, pci.book.places.single().cells.single().pci)
    }

    @Test
    fun areaChangeOnAKnownCellUpdatesAndDoesNotWarn() {
        val learned = home(leave(tick(CellBook(), lte(), still(), 1_000L).book, 2_000L), 2_500L)
        val step = tick(learned, lte(area = 2000), still(), 3_000L)
        assertTrue(step.fresh.isEmpty())
        val cell = step.book.places.single().cells.single()
        assertEquals(2000, cell.identity.area)
        assertEquals(100L, cell.identity.cell)
    }

    @Test
    fun unavailableIdentityAndAnEmptyListDoNotLearnOrWarn() {
        val empty = tick(CellBook(), CellReading(), still(), 1_000L)
        assertTrue(empty.book.places.isEmpty())
        assertTrue(empty.fresh.isEmpty())
        val missing = CellReading(registered = true, generation = CellGen.LTE, identity = null)
        val blank = tick(CellBook(), missing, still(), 1_000L)
        assertTrue(blank.book.places.isEmpty())
        val learned = leave(tick(CellBook(), lte(), still(), 1_000L).book, 2_000L)
        val quiet = tick(learned, missing, still(), 3_000L)
        assertTrue(quiet.fresh.isEmpty())
        assertEquals(1, quiet.book.places.single().cells.size)
    }

    @Test
    fun gsmDropWarnsWhileMovingInsideAHighPlaceOnly() {
        val home = learned(rec(CellGen.LTE, 1), rec(CellGen.NR, 2))
        assertTrue(Geo.meters(LAT, LON, north(400.0), LON) > CellWatch.NEAR_M)
        val moving = tick(home, gsm(), moving(), 5_000L)
        assertEquals(CellKind.DROP2, moving.fresh.single().kind)
        val again = tick(moving.book, gsm(), moving(), 6_000L)
        assertTrue(again.fresh.isEmpty())
        assertEquals(1, again.book.episodes.count { it.open })
        val wcdma = tick(home, wcdma(), moving(), 5_000L)
        assertEquals(CellKind.DROP3, wcdma.fresh.single().kind)
        val already = learned(rec(CellGen.LTE, 1), rec(CellGen.GSM, 9))
        assertTrue(tick(already, gsm(), moving(), 5_000L).fresh.isEmpty())
        val outside = tick(home, gsm(), moving(lat = north(400.0)), 5_000L)
        assertTrue(outside.fresh.isEmpty())
        assertEquals(1, outside.book.places.size)
        val off = tick(home, gsm(), moving(), 5_000L, warnDrop = false)
        assertTrue(off.fresh.isEmpty())
        assertFalse(off.book.places.single().cells.any { it.identity.gen == CellGen.GSM })
    }

    @Test
    fun learningWindowStoresALaterCellAndDoesNotWarn() {
        val opened = 1_000L
        val learned = home(
            leave(tick(CellBook(), lte(), still(), opened, learnDays = 3).book, opened + 1_000L, learnDays = 3),
            opened + 2_000L,
            learnDays = 3,
        )
        val step = tick(learned, lte(cell = 300), still(), opened + 3_000L, learnDays = 3)
        assertTrue(step.fresh.isEmpty())
        assertTrue(step.book.places.single().cells.any { it.identity.cell == 300L })
    }

    @Test
    fun learningWindowEndsOnTheClockAndANewCellWarns() {
        val opened = 1_000L
        val learned = home(
            leave(tick(CellBook(), lte(), still(), opened, learnDays = 3).book, opened + 1_000L, learnDays = 3),
            opened + 2_000L,
            learnDays = 3,
        )
        val quiet = tick(learned, lte(cell = 300), still(), opened + 3 * DAY - 1, learnDays = 3)
        assertTrue(quiet.fresh.isEmpty())
        assertTrue(quiet.book.places.single().cells.any { it.identity.cell == 300L })
        val warned = tick(learned, lte(cell = 301), still(), opened + 3 * DAY, learnDays = 3)
        assertEquals(CellKind.NEW, warned.fresh.single().kind)
        assertFalse(warned.book.places.single().cells.any { it.identity.cell == 301L })
    }

    @Test
    fun dropStillWarnsDuringTheLearningWindowAndIsNotStored() {
        val opened = 1_000L
        val learned = home(
            leave(tick(CellBook(), lte(), still(), opened, learnDays = 3).book, opened + 1_000L, learnDays = 3),
            opened + 2_000L,
            learnDays = 3,
        )
        val step = tick(learned, gsm(), still(), opened + 3_000L, learnDays = 3)
        assertEquals(CellKind.DROP2, step.fresh.single().kind)
        assertFalse(step.book.places.single().cells.any { it.identity.gen == CellGen.GSM })
        val episode = step.book.episodes.single { it.open }
        val dismissed = CellWatch.dismiss(step.book, episode.id, opened + 4_000L)
        val held = tick(dismissed, gsm(), still(), opened + 5_000L, learnDays = 3)
        assertTrue(held.fresh.isEmpty())
        assertFalse(held.book.places.single().cells.any { it.identity.gen == CellGen.GSM })
    }

    @Test
    fun newCellSwitchOffStoresAndDoesNotWarn() {
        val learned = home(leave(tick(CellBook(), lte(), still(), 1_000L).book, 2_000L), 2_500L)
        val step = tick(learned, lte(cell = 999), still(), 3_000L, warnNew = false)
        assertTrue(step.fresh.isEmpty())
        assertTrue(step.book.places.single().cells.any { it.identity.cell == 999L })
    }

    @Test
    fun movingPhoneDoesNotOpenAPlaceOrKeepAPassingCell() {
        val drive = tick(CellBook(), lte(), moving(), 1_000L)
        assertTrue(drive.book.places.isEmpty())
        assertTrue(drive.fresh.isEmpty())
        val home = learned(rec(CellGen.LTE, 1))
        val pass = tick(home, lte(cell = 999), moving(), 5_000L)
        assertTrue(pass.fresh.isEmpty())
        assertFalse(pass.book.places.single().cells.any { it.identity.cell == 999L })
    }

    @Test
    fun coarseFixDoesNotLearnOrWarn() {
        val coarse = tick(CellBook(), lte(), still(accuracy = 100f), 1_000L)
        assertTrue(coarse.book.places.isEmpty())
        val learned = leave(tick(CellBook(), lte(), still(), 1_000L).book, 2_000L)
        val step = tick(learned, lte(cell = 5), still(accuracy = 120f), 3_000L)
        assertTrue(step.fresh.isEmpty())
        assertFalse(step.book.places.single().cells.any { it.identity.cell == 5L })
        val paused = tick(CellBook(), lte(), still().copy(paused = true), 1_000L)
        assertTrue(paused.book.places.isEmpty())
    }

    @Test
    fun thirtyThirdCellReplacesTheLeastRecentlyHeard() {
        var book = CellBook()
        var now = 1_000L
        for (id in 1L..33L) {
            val step = tick(book, lte(cell = id), still(), now)
            assertTrue(step.fresh.isEmpty())
            book = step.book
            now += 1_000L
        }
        val cells = book.places.single().cells
        assertEquals(32, cells.size)
        assertFalse(cells.any { it.identity.cell == 1L })
        assertTrue(cells.any { it.identity.cell == 33L })
        assertTrue(cells.any { it.identity.cell == 2L })
    }

    @Test
    fun dismissDoesNotStoreAndDoesNotRefireUntilTheCellHasBeenGone() {
        val learned = home(leave(tick(CellBook(), lte(), still(), 1_000L).book, 2_000L), 2_500L)
        val warned = tick(learned, lte(cell = 300), still(), 3_000L)
        val id = warned.fresh.single().id
        val dismissed = CellWatch.dismiss(warned.book, id, 4_000L)
        assertTrue(dismissed.episodes.none { it.open })
        assertFalse(dismissed.places.single().cells.any { it.identity.cell == 300L })
        val held = tick(dismissed, lte(cell = 300), still(), 5_000L)
        assertTrue(held.fresh.isEmpty())
        val away = tick(dismissed, lte(), still(), 4_000L + CellWatch.GONE_MS + 1)
        assertTrue(away.book.holds.isEmpty())
        val again = tick(away.book, lte(cell = 300), still(), 4_000L + CellWatch.GONE_MS + 2)
        assertEquals(CellKind.NEW, again.fresh.single().kind)
    }

    @Test
    fun learnStoresTheCellAndEndsTheEpisode() {
        val learned = home(leave(tick(CellBook(), lte(), still(), 1_000L).book, 2_000L), 2_500L)
        val warned = tick(learned, lte(cell = 300), still(), 3_000L)
        val id = warned.fresh.single().id
        val stored = CellWatch.learn(warned.book, id, 4_000L)
        assertTrue(stored.episodes.none { it.open })
        assertTrue(stored.places.single().cells.any { it.identity.cell == 300L })
        val quiet = tick(stored, lte(cell = 300), still(), 5_000L)
        assertTrue(quiet.fresh.isEmpty())
    }

    @Test
    fun watchOffLearnsNothingAndALaterVisitCanWarn() {
        val off = tick(CellBook(), lte(), still(), 1_000L, watch = false)
        assertTrue(off.book.places.isEmpty())
        val first = tick(CellBook(), lte(), still(), 1_000L)
        val closed = tick(first.book, lte(), still(), 2_000L, watch = false)
        assertTrue(closed.book.places.single().baselined)
        assertFalse(closed.book.places.single().visiting)
        val later = tick(closed.book, lte(cell = 2), still(), 3_000L)
        assertEquals(CellKind.NEW, later.fresh.single().kind)
    }

    @Test
    fun privacyModeMasksTheCellIdAndAreaInTheSitBlock() {
        LanguageText.clear()
        val now = 1_700_000_000_000L
        val mark = CellMark(
            at = now - 60_000L,
            kind = "new",
            gen = "4G",
            plmn = "310-410",
            area = "445566",
            cell = "987654321",
            signal = -90,
            timingAdvance = 3,
        )
        val window = DebriefWindow(now - 15 * 60_000L, now, "lot")
        val demo = DebriefReport.document(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(demoMode = true),
            operatorPath = emptyList(),
            now = now,
            window = window,
            cells = listOf(mark),
        )
        val section = demo.sections.single { it.title == LanguageText.text("settings.topic.cell") }
        assertTrue(section.alert)
        assertTrue(section.body.contains("masked"))
        assertTrue(section.body.contains("4G"))
        assertTrue(section.body.contains("310-410"))
        assertTrue(section.body.contains("Country: United States"))
        assertTrue(section.body.contains("Operator: AT&T"))
        assertTrue(section.body.contains("Signal: -90 dBm"))
        assertTrue(section.body.contains("Timing advance: 3"))
        assertFalse(section.body.contains("987654321"))
        assertFalse(section.body.contains("445566"))
        assertFalse(section.body.contains("28.6"))
        val clear = DebriefReport.document(
            devices = emptyList(),
            fleets = emptyList(),
            settings = AppSettings(demoMode = false),
            operatorPath = emptyList(),
            now = now,
            window = window,
            cells = listOf(mark),
        )
        val shown = clear.sections.single { it.title == LanguageText.text("settings.topic.cell") }
        assertTrue(shown.body.contains("987654321"))
        assertTrue(shown.body.contains("445566"))
        assertTrue(shown.body.contains("Country: United States"))
        assertTrue(shown.body.contains("Operator: AT&T"))
        val privacy = clear.sections.indexOfFirst { it.title == LanguageText.text("ui.privacy.label") }
        val cellAt = clear.sections.indexOfFirst { it.title == LanguageText.text("settings.topic.cell") }
        assertTrue(cellAt in 0 until privacy)
    }

    @Test
    fun detailsAddsCountryAndOperatorWhenTheListHasThem() {
        LanguageText.clear()
        val known = CellEpisode(
            id = 1,
            kind = CellKind.NEW,
            placeId = "p",
            identity = CellIdentity(CellGen.LTE, "310-410", 1000, 100),
            channel = 5035,
        )
        val text = CellCopy.details(known)
        val networkAt = text.indexOf("Network: 310-410")
        val countryAt = text.indexOf("Country: United States")
        val operatorAt = text.indexOf("Operator: AT&T")
        val areaAt = text.indexOf("Area: 1000")
        val cellAt = text.indexOf("Cell: 100")
        val channelAt = text.indexOf("Channel: 5035")
        val bandAt = text.indexOf("Band: 12 · 731.5 MHz")
        assertTrue(networkAt in 0 until countryAt)
        assertTrue(countryAt < operatorAt && operatorAt < areaAt)
        assertTrue(areaAt < cellAt && cellAt < channelAt && channelAt < bandAt)
        assertFalse(text.contains("PCI"))
        val withPci = CellCopy.details(known.copy(pci = 50))
        assertTrue(withPci.contains("PCI: 50"))
        assertTrue(withPci.indexOf("Cell: 100") < withPci.indexOf("PCI: 50"))
        val shared = CellCopy.details(
            known.copy(
                identity = CellIdentity(CellGen.NR, "310-410", 1000, 100),
                channel = 392000,
            ),
        )
        assertTrue(shared.contains("Band: n2 or n25 · 1960 MHz"))
        val noChannel = CellCopy.details(known.copy(channel = null))
        assertFalse(noChannel.contains("Channel"))
        assertFalse(noChannel.contains("Band"))
        val countryOnly = CellCopy.details(
            known.copy(identity = CellIdentity(CellGen.LTE, "310-999", 1000, 100)),
        )
        assertTrue(countryOnly.contains("Country: United States"))
        assertFalse(countryOnly.contains("Operator"))
        val neither = CellCopy.details(
            known.copy(identity = CellIdentity(CellGen.LTE, "999-99", 1, 1)),
        )
        assertTrue(neither.contains("Network: 999-99"))
        assertFalse(neither.contains("Country"))
        assertFalse(neither.contains("Operator"))
    }

    @Test
    fun aCellCountsOneCampPerVisitAndRemembersTheLastTime() {
        val first = tick(CellBook(), lte(), still(), 1_000L).book
        val opened = first.places.single().cells.single()
        assertEquals(1, opened.camps)
        assertEquals(1_000L, opened.heardAt)
        assertEquals(1_000L, opened.firstAt)
        assertEquals(-90, opened.signal)
        var stayed = first
        repeat(50) { i ->
            stayed = tick(stayed, lte(), still(), 2_000L + i * 1_000L).book
        }
        val held = stayed.places.single().cells.single()
        assertEquals(1, held.camps)
        assertEquals(51_000L, held.heardAt)
        assertEquals(1_000L, held.firstAt)
        val quieter = tick(stayed, lte(signal = -70), still(), 52_000L).book
        val quiet = quieter.places.single().cells.single()
        assertEquals(1, quiet.camps)
        assertEquals(1_000L, quiet.firstAt)
        assertEquals(52_000L, quiet.heardAt)
        assertEquals(-70, quiet.signal)
        val hopped = tick(quieter, lte(cell = 200), still(), 60_000L).book
        val back = tick(hopped, lte(), still(), 61_000L).book
        val during = back.places.single().cells
        val homeCell = during.first { it.identity.cell == 100L }
        val other = during.first { it.identity.cell == 200L }
        assertEquals(1, homeCell.camps)
        assertEquals(1, other.camps)
        assertEquals(1_000L, homeCell.firstAt)
        assertEquals(60_000L, other.firstAt)
        assertEquals(-90, homeCell.signal)
        val area = tick(back, lte(area = 2000), still(), 62_000L).book
        val moved = area.places.single().cells.first { it.identity.cell == 100L }
        assertEquals(1, moved.camps)
        assertEquals(2000, moved.identity.area)
        assertEquals(1_000L, moved.firstAt)
        val away = leave(area, 70_000L)
        assertTrue(away.places.single().camped.isEmpty())
        val returned = tick(home(away, 79_000L), lte(), still(), 80_000L).book
        val cell = returned.places.single().cells.first { it.identity.cell == 100L }
        assertEquals(2, cell.camps)
        assertEquals(80_000L, cell.heardAt)
        assertEquals(1_000L, cell.firstAt)
        assertEquals(-90, cell.signal)
        val legacy = learned(rec(CellGen.LTE, 100).copy(camps = 0, heardAt = 500L))
        val seen = tick(legacy, lte(), still(), 5_000L).book
        val old = seen.places.single().cells.single()
        assertEquals(1, old.camps)
        assertEquals(5_000L, old.heardAt)
        assertEquals(500L, old.firstAt)
        assertEquals(-90, old.signal)
    }

    @Test
    fun diagnosticsNamesEmptyFieldsAndDoesNotPrintTheNetwork() {
        LanguageText.clear()
        val rows = CellWatch.diagRows(
            CellReading(
                registered = true,
                generation = CellGen.LTE,
                identity = null,
                emptyFields = listOf(CellField.NETWORK, CellField.CELL),
                neighborCount = 4,
                secondSimRegistered = null,
            ),
        )
        val values = rows.map { it.second }
        assertTrue(values.contains("on"))
        assertTrue(values.contains("4G"))
        assertTrue(values.contains("Network, Cell"))
        assertTrue(values.contains("4"))
        assertTrue(values.contains("not returned"))
        assertFalse(values.any { it.contains("310") })
    }

    @Test
    fun oldFilesLoadTheCellDefaults() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val settings = json.decodeFromString(AppSettings.serializer(), "{}")
        assertFalse(settings.cellWatch)
        assertTrue(settings.cellWarnNew)
        assertTrue(settings.cellWarnDrop)
        assertEquals(3, settings.cellLearnDays)
        assertTrue(settings.cellBeep)
        assertTrue(settings.cellVoice)
        assertFalse(settings.cellShade)
        val book = learned(rec(CellGen.LTE, 10))
        val back = json.decodeFromString(CellBook.serializer(), json.encodeToString(CellBook.serializer(), book))
        assertEquals(book, back)
        val old = json.decodeFromString(CellBook.serializer(), """{"format":"fieldwatch-cells"}""")
        assertTrue(old.places.isEmpty())
        assertFalse(old.dropHeld)
    }

    @Test
    fun distanceFromThisPhoneIsInsideThePlaceAndBlankWithoutAFix() {
        val place = learned(rec(CellGen.LTE, 1)).places.single()
        assertNull(CellWatch.metersFromPhone(place, null, null))
        assertNull(CellWatch.metersFromPhone(place, LAT, null))
        assertNull(CellWatch.metersFromPhone(place, Double.NaN, LON))
        val here = CellWatch.metersFromPhone(place, LAT, LON)
        assertNotNull(here)
        assertTrue(here!! <= CellWatch.NEAR_M)
        val away = CellWatch.metersFromPhone(place, north(400.0), LON)
        assertNotNull(away)
        assertTrue(away!! > CellWatch.NEAR_M)
        assertEquals(Geo.meters(LAT, LON, north(400.0), LON), away, 0.01)
    }

    @Test
    fun placeCardPinsHereAndGroupsTheRestByThePhoneCalendar() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val us = Locale.US
        fun at(day: Int, hour: Int): Long {
            val cal = Calendar.getInstance(zone, us)
            cal.set(2026, Calendar.JANUARY, day, hour, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
        val now = at(14, 15)
        val here = spot("here", at(3, 9), LAT)
        val today = spot("today", at(14, 9), north(400.0))
        val tomorrow = spot("tomorrow", at(15, 9), north(800.0))
        val tuesday = spot("tuesday", at(13, 18), north(1200.0))
        val sunday = spot("sunday", at(11, 8), north(1600.0))
        val saturday = spot("saturday", at(10, 20), north(2000.0))
        val roll = CellWatch.arrange(
            listOf(saturday, sunday, tuesday, today, tomorrow, here),
            LAT,
            LON,
            now,
            zone,
            us,
        )
        assertEquals(
            listOf("here", "tomorrow", "today", "tuesday", "sunday", "saturday"),
            roll.preview.map { it.id },
        )
        assertEquals(0, roll.more)
        assertEquals(
            listOf(CellPlaceBand.HERE, CellPlaceBand.TODAY, CellPlaceBand.WEEK, CellPlaceBand.OLDER),
            roll.sections.map { it.band },
        )
        assertEquals(listOf("here"), roll.sections[0].places.map { it.id })
        assertEquals(listOf("tomorrow", "today"), roll.sections[1].places.map { it.id })
        assertEquals(listOf("tuesday", "sunday"), roll.sections[2].places.map { it.id })
        assertEquals(listOf("saturday"), roll.sections[3].places.map { it.id })

        val monday = CellWatch.arrange(
            listOf(saturday, sunday, tuesday),
            null,
            null,
            now,
            zone,
            Locale.GERMANY,
        )
        assertEquals(listOf(CellPlaceBand.WEEK, CellPlaceBand.OLDER), monday.sections.map { it.band })
        assertEquals(listOf("tuesday"), monday.sections[0].places.map { it.id })
        assertEquals(listOf("sunday", "saturday"), monday.sections[1].places.map { it.id })

        val noFix = CellWatch.arrange(listOf(here, today), null, null, now, zone, us)
        assertFalse(noFix.sections.any { it.band == CellPlaceBand.HERE })
        assertEquals(listOf("today", "here"), noFix.preview.map { it.id })
    }

    @Test
    fun eleventhPlaceStaysOffTheCardAndThePlaceHereKeepsItsSeat() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val us = Locale.US
        val cal = Calendar.getInstance(zone, us)
        cal.set(2026, Calendar.JANUARY, 14, 15, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val now = cal.timeInMillis
        val away = (1..11).map { n ->
            spot("p$n", now - n * 3_600_000L, north(400.0 + n))
        }
        val here = spot("here", now - 20 * 86_400_000L, LAT)
        val roll = CellWatch.arrange(away + here, LAT, LON, now, zone, us)
        assertEquals(CellWatch.PREVIEW, roll.preview.size)
        assertEquals("here", roll.preview.first().id)
        assertEquals(2, roll.more)
        assertFalse(roll.preview.any { it.id == "p10" || it.id == "p11" })
        assertEquals(listOf("here"), roll.sections.first { it.band == CellPlaceBand.HERE }.places.map { it.id })
        val ten = CellWatch.arrange(away.take(CellWatch.PREVIEW), null, null, now, zone, us)
        assertEquals(0, ten.more)
        assertEquals(CellWatch.PREVIEW, ten.preview.size)
    }

    @Test
    fun placeTitleUsesTheStreetOnlyWhenOnlineNamesIsOn() {
        val place = spot("p", 1L, LAT).copy(name = "  Oak Street  ")
        assertEquals("masked", CellWatch.placeTitle(place, onlineNames = true, privacy = true))
        assertEquals("Oak Street", CellWatch.placeTitle(place, onlineNames = true, privacy = false))
        assertEquals(
            Geo.screenCoord(LAT, LON, false),
            CellWatch.placeTitle(place, onlineNames = false, privacy = false),
        )
        val waiting = place.copy(name = " ")
        assertEquals(
            Geo.screenCoord(LAT, LON, false),
            CellWatch.placeTitle(waiting, onlineNames = true, privacy = false),
        )
    }

    @Test
    fun aSavedStreetFillsABlankNameAndDoesNotReplaceOne() {
        val kept = spot("b", 2L, north(400.0)).copy(name = "Kept")
        val book = CellBook(places = listOf(spot("a", 1L, LAT), kept))
        val named = CellWatch.applyNames(book, mapOf(Geo.cellKey(LAT, LON) to "  Main Street  "))
        assertEquals("Main Street", named.places.first { it.id == "a" }.name)
        assertEquals("Kept", named.places.first { it.id == "b" }.name)
        val again = CellWatch.applyNames(named, mapOf(Geo.cellKey(LAT, LON) to "Other"))
        assertEquals("Main Street", again.places.first { it.id == "a" }.name)
        val long = "x".repeat(180)
        val capped = CellWatch.applyNames(book, mapOf(Geo.cellKey(LAT, LON) to long))
        assertEquals(160, capped.places.first { it.id == "a" }.name.length)
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = json.decodeFromString(CellPlace.serializer(), """{"id":"p9","lat":1.25,"lon":-2.5}""")
        assertEquals("", old.name)
        assertEquals("1.25000, -2.50000", CellWatch.placeTitle(old, onlineNames = true, privacy = false))
    }

    private fun spot(id: String, visit: Long, lat: Double) = CellPlace(
        id = id,
        lat = lat,
        lon = LON,
        openedAt = visit,
        lastVisitAt = visit,
        baselined = true,
    )

    private fun tick(
        book: CellBook,
        reading: CellReading,
        fix: CellFix,
        now: Long,
        watch: Boolean = true,
        warnNew: Boolean = true,
        warnDrop: Boolean = true,
        learnDays: Int = 0,
    ) = CellWatch.tick(book, reading, fix, CellSwitches(watch, warnNew, warnDrop, learnDays), now)

    /** Coming back from the drive is a step. The next still reading is the visit. */
    private fun home(book: CellBook, now: Long, learnDays: Int = 0): CellBook {
        val step = tick(book, lte(), still(), now, learnDays = learnDays)
        assertTrue(step.fresh.isEmpty())
        return step.book
    }

    private fun leave(book: CellBook, now: Long, learnDays: Int = 0): CellBook {
        val step = tick(book, lte(cell = 8), moving(lat = north(400.0)), now, learnDays = learnDays)
        assertTrue(step.fresh.isEmpty())
        assertEquals(1, step.book.places.size)
        assertTrue(step.book.places.single().baselined)
        assertFalse(step.book.places.single().cells.any { it.identity.cell == 8L })
        return step.book
    }

    private fun learned(vararg cells: CellRecord) = CellBook(
        places = listOf(
            CellPlace(
                id = "p1",
                lat = LAT,
                lon = LON,
                cells = cells.toList(),
                openedAt = 1L,
                lastVisitAt = 1L,
                baselined = true,
            ),
        ),
        nextPlace = 2,
    )

    private fun rec(gen: CellGen, cell: Long) = CellRecord(
        identity = CellIdentity(gen, "310-410", 1000, cell),
        heardAt = cell,
    )

    private fun spot(id: String, lat: Double, lon: Double, whenAt: Long) = CellPlace(
        id = id,
        lat = lat,
        lon = lon,
        lastVisitAt = whenAt,
    )

    private fun lte(
        cell: Long = 100L,
        plmn: String = "310-410",
        area: Int = 1000,
        pci: Int? = 50,
        signal: Int? = -90,
    ) = reading(CellGen.LTE, cell, plmn, area, pci, signal)

    private fun gsm(cell: Long = 40L) = reading(CellGen.GSM, cell, pci = 7)

    private fun wcdma(cell: Long = 41L) = reading(CellGen.WCDMA, cell, pci = 8)

    private fun reading(
        gen: CellGen,
        cell: Long,
        plmn: String = "310-410",
        area: Int = 1000,
        pci: Int? = null,
        signal: Int? = -90,
    ) = CellReading(
        registered = true,
        generation = gen,
        identity = CellIdentity(gen, plmn, area, cell),
        pci = pci,
        channel = 5200,
        signal = signal,
        timingAdvance = 3,
    )

    private fun still(accuracy: Float = 8f) = CellFix(LAT, LON, accuracy, 0f, paused = false)

    private fun moving(lat: Double = LAT) = CellFix(lat, LON, 8f, 5f, paused = false)

    private fun north(meters: Double) = LAT + meters / 111_320.0

    companion object {
        private const val LAT = 28.6
        private const val LON = -81.4
    }
}
