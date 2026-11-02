package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.RuleKind
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
