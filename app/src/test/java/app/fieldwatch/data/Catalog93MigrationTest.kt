package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.RuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Catalog93MigrationTest {
    @Test
    fun savedBuiltInRowsDropLooseRulesAndKeepOperatorRules() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val flock = old(
            "fleet-flock-cameras",
            MatchRule(RuleKind.OUI, text = "B4:1E:52"),
            MatchRule(RuleKind.NAME_CONTAINS, text = "Flock"),
            MatchRule(RuleKind.NAME_CONTAINS, text = "flck"),
            MatchRule(RuleKind.NAME_CONTAINS, text = "CONDOR"),
            MatchRule(RuleKind.NAME_CONTAINS, text = "Falcon"),
            MatchRule(RuleKind.NAME_CONTAINS, text = "SPARROW"),
            MatchRule(RuleKind.NAME_GLOB, text = "Flock-*"),
            MatchRule(RuleKind.NAME_GLOB, text = "Flock-??????"),
            MatchRule(RuleKind.NAME_CONTAINS, text = "MyPole"),
        )
        val penguin = old(
            "fleet-penguin",
            MatchRule(RuleKind.NAME_CONTAINS, text = "Penguin"),
            MatchRule(RuleKind.NAME_CONTAINS, text = "PENGUIN"),
            MatchRule(RuleKind.NAME_GLOB, text = "Penguin*"),
            MatchRule(RuleKind.NAME_GLOB, text = "Penguin-*"),
            MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x09C8),
            MatchRule(RuleKind.NAME_GLOB, text = "KeepMe*"),
        )
        val fs = old(
            "fleet-fs-ext-battery",
            MatchRule(RuleKind.NAME_CONTAINS, text = "FS Ext Battery"),
            MatchRule(RuleKind.NAME_GLOB, text = "FS_*"),
            MatchRule(RuleKind.NAME_GLOB, text = "FS Ext*"),
            MatchRule(RuleKind.OUI, text = "04:0d:84"),
            MatchRule(RuleKind.OUI, text = "F0:82:C0"),
            MatchRule(RuleKind.OUI, text = "AA:BB:CC"),
        )
        val lite = old(
            "fleet-liteon-camera-radio",
            MatchRule(RuleKind.OUI, text = "B8:35:32"),
            MatchRule(RuleKind.OUI, text = "08:3A:88"),
            MatchRule(RuleKind.OUI, text = "C0:35:32"),
        )
        val custom = Fleet(id = "fleet-flock-cameras", name = "Mine", builtIn = false, rules = listOf(
            MatchRule(RuleKind.NAME_CONTAINS, text = "Flock"),
        ))

        val nextFlock = catalog93Fleet(flock, stock.getValue(flock.id))
        val nextPenguin = catalog93Fleet(penguin, stock.getValue(penguin.id))
        val nextFs = catalog93Fleet(fs, stock.getValue(fs.id))
        val nextLite = catalog93Fleet(lite, stock.getValue(lite.id))
        val nextCustom = catalog93Fleet(custom, stock.getValue("fleet-flock-cameras"))

        assertEquals(setOf("B4:1E:52", "Flock-*", "MyPole"), texts(nextFlock))
        assertEquals(stock.getValue(flock.id).notes, nextFlock.notes)
        assertEquals(setOf("Penguin-*", "KeepMe*"), texts(nextPenguin).filter { it.isNotBlank() }.toSet())
        assertTrue(nextPenguin.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x09C8 })
        assertEquals(stock.getValue(penguin.id).attentionNote, nextPenguin.attentionNote)
        assertEquals(setOf("FS Ext Battery", "FS Ext*", "AA:BB:CC"), texts(nextFs))
        assertFalse(texts(nextFs).any { it.equals("FS_*", true) })
        assertEquals(setOf("08:3A:88", "C0:35:32"), texts(nextLite))
        assertTrue(nextLite.notes.contains("Universal Global Scientific"))
        assertEquals(listOf("Flock"), nextCustom.rules.map { it.text })
    }

    private fun old(id: String, vararg rules: MatchRule) = Fleet(
        id = id,
        name = id,
        builtIn = true,
        rules = rules.toList(),
        notes = "old notes",
        attentionNote = "old attention",
    )

    private fun texts(fleet: Fleet) = fleet.rules.map { it.text }.toSet()
}
