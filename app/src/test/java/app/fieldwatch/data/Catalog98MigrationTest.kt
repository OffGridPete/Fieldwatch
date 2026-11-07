package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.WatchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Catalog98MigrationTest {
    @Test
    fun savedSmartTagDropsSamsungCompanyIdAndKeepsOtherRules() {
        val stock = DefaultCatalog.fleets().single { it.id == "fleet-smarttag" }
        val saved = Fleet(
            id = "fleet-smarttag",
            name = "Samsung SmartTags",
            builtIn = true,
            notes = "Samsung SmartTag / SmartTag+ item finder. Addresses often rotate.",
            rules = listOf(
                MatchRule(RuleKind.NAME_CONTAINS, text = "SmartTag"),
                MatchRule(RuleKind.SERVICE_UUID, text = "FD5A"),
                MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x0075),
                MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x0076),
                MatchRule(RuleKind.NAME_CONTAINS, text = "Lobby"),
            ),
        )
        val custom = Fleet(
            id = "fleet-smarttag",
            name = "Mine",
            builtIn = false,
            rules = listOf(MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x0075)),
        )
        val edited = saved.copy(notes = "My hotel note")

        val next = catalog98SmartTag(saved, stock)
        val nextCustom = catalog98SmartTag(custom, stock)
        val nextEdited = catalog98SmartTag(edited, stock)

        assertFalse(next.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x0075 })
        assertTrue(next.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x0076 })
        assertEquals(setOf("SmartTag", "FD5A", "Lobby"), next.rules.map { it.text }.filter { it.isNotBlank() }.toSet())
        assertEquals(stock.notes, next.notes)
        assertEquals(listOf(0x0075), nextCustom.rules.map { it.companyId })
        assertEquals("My hotel note", nextEdited.notes)
        assertFalse(nextEdited.rules.any { it.companyId == 0x0075 })
    }
}

class Catalog99MigrationTest {
    @Test
    fun savedDjiNoteUpdatesAndAnEditedNoteStays() {
        val stock = DefaultCatalog.fleets().single { it.id == "fleet-dji" }
        val saved = stock.copy(
            notes = "DJI aircraft, controller, or setup Wi-Fi. Handheld Osmo cameras are the Osmo row. " +
                "Power stations named Power2000 are the DJI Power row. " +
                "In-flight digital license plate is the Remote ID row.",
        )
        val custom = saved.copy(builtIn = false, name = "Mine")
        val edited = saved.copy(notes = "My note")

        assertEquals(stock.notes, catalog99Dji(saved, stock).notes)
        assertEquals(saved.notes, catalog99Dji(custom, stock).notes)
        assertEquals("My note", catalog99Dji(edited, stock).notes)
    }
}

class Catalog100MigrationTest {
    @Test
    fun savedCatalogGainsTheThreeFleetsAndBookmarksExpressLrsOnly() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val (fleets, watch) = catalog100Migrate(emptyList(), emptyList(), stock)
        assertEquals(
            setOf("fleet-elrs", "fleet-xiaomi-mihome", "fleet-ezviz"),
            fleets.map { it.id }.toSet(),
        )
        assertEquals(listOf("fleet-elrs"), watch.map { it.fleetId })
        assertTrue(fleets.single { it.id == "fleet-elrs" }.builtIn)
        assertTrue(fleets.single { it.id == "fleet-xiaomi-mihome" }.builtIn)
        assertTrue(fleets.single { it.id == "fleet-ezviz" }.builtIn)
    }

    @Test
    fun customCopyWithTheSameIdStaysAndAnExistingBookmarkIsNotDuplicated() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val custom = Fleet(
            id = "fleet-elrs",
            name = "Mine",
            builtIn = false,
            rules = listOf(MatchRule(RuleKind.NAME_GLOB, text = "Mine*")),
        )
        val already = WatchTarget(id = "watch-fleet-elrs", fleetId = "fleet-elrs", label = "ExpressLRS")
        val (fleets, watch) = catalog100Migrate(listOf(custom), listOf(already), stock)
        val elrs = fleets.single { it.id == "fleet-elrs" }
        assertFalse(elrs.builtIn)
        assertEquals("Mine", elrs.name)
        assertEquals(listOf("Mine*"), elrs.rules.map { it.text })
        assertEquals(1, watch.count { it.fleetId == "fleet-elrs" })
        assertTrue(fleets.any { it.id == "fleet-xiaomi-mihome" && it.builtIn })
        assertTrue(fleets.any { it.id == "fleet-ezviz" && it.builtIn })
        assertFalse(watch.any { it.fleetId == "fleet-xiaomi-mihome" || it.fleetId == "fleet-ezviz" })
    }
}
