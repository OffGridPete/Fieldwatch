package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.WatchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

class Catalog101MigrationTest {
    @Test
    fun savedNovatelRowIsRemovedAndHatchDropsTheUnlistedAddress() {
        val novatel = Fleet(
            id = "fleet-novatel",
            name = "Novatel Wireless",
            builtIn = true,
            attentionNote = "Novatel Wireless / Inseego OUI 28:80:A2.",
            rules = listOf(MatchRule(RuleKind.OUI, text = "28:80:A2")),
        )
        val hatch = Fleet(
            id = "fleet-hatch",
            name = "Hatch",
            builtIn = true,
            rules = listOf(
                MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x0434),
                MatchRule(RuleKind.OUI, text = "C8:FA:9C"),
                MatchRule(RuleKind.NAME_GLOB, text = "Hatch Rest*"),
                MatchRule(RuleKind.OUI, text = "AA:BB:CC"),
            ),
        )
        val watch = listOf(
            WatchTarget(id = "watch-fleet-novatel", fleetId = "fleet-novatel", label = "Novatel Wireless"),
            WatchTarget(id = "watch-fleet-hatch", fleetId = "fleet-hatch", label = "Hatch"),
        )
        val (fleets, nextWatch) = catalog101Migrate(listOf(novatel, hatch), watch)
        assertFalse(fleets.any { it.id == "fleet-novatel" })
        assertFalse(nextWatch.any { it.fleetId == "fleet-novatel" })
        val kept = fleets.single { it.id == "fleet-hatch" }
        assertEquals(setOf("Hatch Rest*", "AA:BB:CC"), kept.rules.map { it.text }.filter { it.isNotBlank() }.toSet())
        assertTrue(kept.rules.any { it.companyId == 0x0434 })
        assertFalse(kept.rules.any { it.text.equals("C8:FA:9C", true) })
        assertTrue(nextWatch.any { it.fleetId == "fleet-hatch" })
    }

    @Test
    fun anAddedNovatelRuleStaysAndACustomCopyStays() {
        val novatel = Fleet(
            id = "fleet-novatel",
            name = "Novatel Wireless",
            builtIn = true,
            rules = listOf(
                MatchRule(RuleKind.OUI, text = "28:80:A2"),
                MatchRule(RuleKind.NAME_GLOB, text = "MiFi*"),
            ),
        )
        val custom = Fleet(
            id = "fleet-novatel",
            name = "Mine",
            builtIn = false,
            rules = listOf(MatchRule(RuleKind.OUI, text = "28:80:A2")),
        )
        val watch = listOf(
            WatchTarget(id = "watch-fleet-novatel", fleetId = "fleet-novatel", label = "Novatel Wireless"),
        )
        val (keptFleet, keptWatch) = catalog101Migrate(listOf(novatel), watch)
        assertEquals(listOf("MiFi*"), keptFleet.single().rules.map { it.text })
        assertEquals(1, keptWatch.size)
        val (customFleet, _) = catalog101Migrate(listOf(custom), watch)
        assertEquals("Mine", customFleet.single().name)
        assertEquals(listOf("28:80:A2"), customFleet.single().rules.map { it.text })
    }

    @Test
    fun pendingStaysTrueAfterACatalogUpdateLeftTheOldRows() {
        val novatel = Fleet(
            id = "fleet-novatel",
            name = "Novatel Wireless",
            builtIn = true,
            rules = listOf(MatchRule(RuleKind.OUI, text = "28:80:a2")),
        )
        val hatch = Fleet(
            id = "fleet-hatch",
            name = "Hatch",
            builtIn = true,
            rules = listOf(
                MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x0434),
                MatchRule(RuleKind.OUI, text = "c8:fa:9c"),
            ),
        )
        val empty = Fleet(id = "fleet-novatel", name = "Novatel Wireless", builtIn = true)
        val custom = Fleet(
            id = "fleet-novatel",
            name = "Mine",
            builtIn = false,
            rules = listOf(MatchRule(RuleKind.OUI, text = "28:80:A2")),
        )
        val added = Fleet(
            id = "fleet-novatel",
            name = "Novatel Wireless",
            builtIn = true,
            rules = listOf(MatchRule(RuleKind.NAME_GLOB, text = "MiFi*")),
        )
        assertTrue(catalog101Pending(listOf(novatel)))
        assertTrue(catalog101Pending(listOf(hatch)))
        assertTrue(catalog101Pending(listOf(empty)))
        assertFalse(catalog101Pending(listOf(custom, added)))
        val (cleaned, _) = catalog101Migrate(listOf(novatel, hatch), emptyList())
        assertFalse(catalog101Pending(cleaned))
    }
}

class Catalog102MigrationTest {
    @Test
    fun savedCatalogGainsTheTwoWayRowsAndDoesNotBookmarkThem() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val fleets = catalog102Migrate(emptyList(), stock)
        assertEquals(ADDED_IN_V102.toSet(), fleets.map { it.id }.toSet())
        assertTrue(fleets.all { it.builtIn })
        assertTrue(fleets.all { it.kind == SignatureClass.TWO_WAY })
        assertTrue(fleets.all { it.attentionNote.isBlank() })
    }

    @Test
    fun customCopyWithTheSameIdStays() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val custom = Fleet(
            id = "fleet-icom-id52",
            name = "Mine",
            builtIn = false,
            rules = listOf(MatchRule(RuleKind.NAME_GLOB, text = "Mine")),
        )
        val fleets = catalog102Migrate(listOf(custom), stock)
        val icom = fleets.single { it.id == "fleet-icom-id52" }
        assertFalse(icom.builtIn)
        assertEquals("Mine", icom.name)
        assertEquals(listOf("Mine"), icom.rules.map { it.text })
        assertEquals(ADDED_IN_V102.size, fleets.size)
        val yaesu = fleets.single { it.id == "fleet-yaesu-ssm-bt10" }
        assertTrue(yaesu.builtIn)
        assertEquals("SSM-BT10", yaesu.rules.single().text)
    }
}

class Catalog103MigrationTest {
    @Test
    fun savedCatalogGainsQuestAndRefreshesTheOldGlassesNote() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val glasses = stock.getValue("fleet-meta-glasses").copy(
            notes = "Ray-Ban Meta, Oakley Meta, or Meta Display smart glasses, or a Quest / other Meta wearable.",
        )
        val fleets = catalog103Migrate(listOf(glasses), stock)
        val quest = fleets.single { it.id == "fleet-meta-quest" }
        assertTrue(quest.builtIn)
        assertEquals(SignatureClass.WEARABLE, quest.kind)
        assertTrue(quest.attentionNote.isBlank())
        val refreshed = fleets.single { it.id == "fleet-meta-glasses" }
        assertEquals(stock.getValue("fleet-meta-glasses").notes, refreshed.notes)
        assertEquals(glasses.attentionNote, refreshed.attentionNote)
        val added = catalog103Migrate(emptyList(), stock)
        assertEquals(listOf("fleet-meta-quest"), added.map { it.id })
    }

    @Test
    fun customCopyAndEditedGlassesNotesStay() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val custom = Fleet(
            id = "fleet-meta-quest",
            name = "Mine",
            builtIn = false,
            rules = listOf(MatchRule(RuleKind.NAME_GLOB, text = "Mine")),
        )
        val edited = stock.getValue("fleet-meta-glasses").copy(notes = "Edited")
        val fleets = catalog103Migrate(listOf(custom, edited), stock)
        val quest = fleets.single { it.id == "fleet-meta-quest" }
        assertFalse(quest.builtIn)
        assertEquals("Mine", quest.name)
        assertEquals(listOf("Mine"), quest.rules.map { it.text })
        assertEquals("Edited", fleets.single { it.id == "fleet-meta-glasses" }.notes)
        assertEquals(2, fleets.size)
    }
}

class Catalog104MigrationTest {
    @Test
    fun builtInFindHubGainsTheNormalModeWording() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val old = stock.getValue("fleet-find-hub").copy(
            notes = "Google Find Hub / Find My Device network tag (Chipolo, Pebblebee, moto tag, " +
                "and other partners). Nearby vs separated frames. Separated mode can hold a MAC " +
                "about a day. Addresses otherwise rotate. Pattern match, not that bag.",
            decode = stock.getValue("fleet-find-hub").decode?.let { decode ->
                decode.copy(
                    fields = decode.fields.map { field ->
                        if (field.id != "mode") field
                        else field.copy(
                            enumLabels = field.enumLabels?.plus("64" to "nearby"),
                            enumNotes = field.enumNotes?.plus("64" to "Nearby its owner."),
                        )
                    },
                )
            },
        )
        val fleets = catalog104Migrate(listOf(old), stock)
        val hub = fleets.single()
        val mode = hub.decode?.fields?.single { it.id == "mode" }
        assertEquals("not separated", mode?.enumLabels?.get("64"))
        assertEquals("separated", mode?.enumLabels?.get("65"))
        assertTrue(mode?.enumNotes?.get("64")?.contains("not flagged separated") == true)
        assertEquals(stock.getValue("fleet-find-hub").notes, hub.notes)
    }

    @Test
    fun editedFindHubNoteStaysAndTheChipStillUpdates() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val edited = stock.getValue("fleet-find-hub").copy(notes = "Edited")
        val custom = stock.getValue("fleet-find-hub").copy(builtIn = false, notes = "Mine", decode = null)
        val fleets = catalog104Migrate(listOf(edited, custom), stock)
        assertEquals("Edited", fleets.single { it.builtIn }.notes)
        assertEquals("not separated", fleets.single { it.builtIn }.decode?.fields?.single { it.id == "mode" }?.enumLabels?.get("64"))
        assertEquals("Mine", fleets.single { !it.builtIn }.notes)
        assertNull(fleets.single { !it.builtIn }.decode)
    }
}

class Catalog105MigrationTest {
    @Test
    fun builtInFindHubGainsBothKeyLengths() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val old = stock.getValue("fleet-find-hub").let { fleet ->
            val decode = fleet.decode ?: error("find hub decode")
            fleet.copy(decode = decode.copy(fields = decode.fields.filter { it.id == "mode" }))
        }
        val hub = catalog105Migrate(listOf(old), stock).single()
        val lengths = hub.decode?.fields?.filter { it.id == "eid" }?.map { it.length }?.toSet()
        assertEquals(setOf(20, 32), lengths)
        assertTrue(hub.decode?.fields?.any { it.id == "hashed_flags" } == true)
        assertEquals(old.notes, hub.notes)
    }

    @Test
    fun customFindHubDecodeStays() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val custom = stock.getValue("fleet-find-hub").copy(
            builtIn = false,
            decode = stock.getValue("fleet-find-hub").decode?.copy(fields = emptyList()),
        )
        val kept = catalog105Migrate(listOf(custom), stock).single()
        assertTrue(kept.decode?.fields?.isEmpty() == true)
    }
}

class Catalog106MigrationTest {
    @Test
    fun builtInRemoteIdNoteSaysTheFrenchPlateCanFillPosition() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 106)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val old = stock.getValue("fleet-remote-id").copy(notes = OLD)
        val other = stock.getValue("fleet-dji")
        val fleets = catalog106Migrate(listOf(old, other), stock)
        val remote = fleets.single { it.id == "fleet-remote-id" }
        assertEquals(stock.getValue("fleet-remote-id").notes, remote.notes)
        assertFalse(remote.notes.contains("does not fill position"))
        assertTrue(remote.notes.contains("takeoff pin"))
        assertEquals(old.rules, remote.rules)
        assertEquals(other.notes, fleets.single { it.id == "fleet-dji" }.notes)
    }

    @Test
    fun editedRemoteIdNoteAndACustomCopyStay() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val edited = stock.getValue("fleet-remote-id").copy(notes = "Edited")
        val custom = stock.getValue("fleet-remote-id").copy(builtIn = false, notes = OLD)
        val fleets = catalog106Migrate(listOf(edited, custom), stock)
        assertEquals("Edited", fleets.single { it.builtIn }.notes)
        assertEquals(OLD, fleets.single { !it.builtIn }.notes)
    }

    private companion object {
        const val OLD =
            "In-flight drone digital license plate (ASTM / FAA Remote ID). Decoded fields can show ID, position, " +
                "heading, and operator location (takeoff, live GPS, or a fixed point). EU class shows only when the " +
                "plate says EU. The French Direct Remote ID vendor IE labels this row too. That plate does not fill " +
                "position or the operator pin. Pattern match, not a tail number. Wi-Fi Remote ID often misses on stock Android."
    }
}

class Catalog107MigrationTest {
    @Test
    fun addsHomeKitWhenMissingAndLeavesACustomCopy() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 107)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val added = catalog107Migrate(listOf(other), stock)
        val home = added.single { it.id == "fleet-homekit" }
        assertTrue(home.builtIn)
        assertEquals("HomeKit", home.name)
        assertTrue(home.attentionNote.isBlank())
        assertEquals(listOf("fleet-dji", "fleet-homekit"), added.map { it.id })

        val custom = home.copy(builtIn = false, name = "Mine")
        val kept = catalog107Migrate(listOf(custom, other), stock)
        assertEquals("Mine", kept.single { it.id == "fleet-homekit" }.name)
        assertFalse(kept.single { it.id == "fleet-homekit" }.builtIn)
        assertEquals(1, kept.count { it.id == "fleet-homekit" })
    }
}

class Catalog108MigrationTest {
    @Test
    fun addsMatterWhenMissingAndLeavesACustomCopy() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 108)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val added = catalog108Migrate(listOf(other), stock)
        val matter = added.single { it.id == "fleet-matter" }
        assertTrue(matter.builtIn)
        assertEquals("Matter", matter.name)
        assertTrue(matter.attentionNote.isBlank())
        assertNull(matter.decode)
        assertEquals(listOf("fleet-dji", "fleet-matter"), added.map { it.id })

        val custom = matter.copy(builtIn = false, name = "Mine")
        val kept = catalog108Migrate(listOf(custom, other), stock)
        assertEquals("Mine", kept.single { it.id == "fleet-matter" }.name)
        assertFalse(kept.single { it.id == "fleet-matter" }.builtIn)
        assertEquals(1, kept.count { it.id == "fleet-matter" })
    }
}

class Catalog109MigrationTest {
    @Test
    fun addsSidewalkWhenMissingAndKeepsAnEditedAmazonNote() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 109)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val added = catalog109Migrate(listOf(other), stock)
        val sidewalk = added.single { it.id == "fleet-sidewalk" }
        assertTrue(sidewalk.builtIn)
        assertEquals("Amazon Sidewalk", sidewalk.name)
        assertEquals(SignatureClass.HOME, sidewalk.kind)
        assertFalse(sidewalk.matchAny)
        assertTrue(sidewalk.attentionNote.isBlank())
        assertNull(sidewalk.decode)
        assertEquals(2, sidewalk.rules.size)
        assertEquals(listOf("fleet-sidewalk", "fleet-dji"), added.map { it.id })

        val custom = sidewalk.copy(builtIn = false, name = "Mine")
        val kept = catalog109Migrate(listOf(custom, other), stock)
        assertEquals("Mine", kept.single { it.id == "fleet-sidewalk" }.name)
        assertFalse(kept.single { it.id == "fleet-sidewalk" }.builtIn)
        assertEquals(1, kept.count { it.id == "fleet-sidewalk" })

        val amazon = stock.getValue("fleet-amazon").copy(notes = AMAZON_NOTES_BEFORE_V109)
        val noted = catalog109Migrate(listOf(amazon), stock).single { it.id == "fleet-amazon" }
        assertTrue(noted.notes.endsWith("is the Sidewalk row, not this one."))
        assertTrue(noted.notes.startsWith(AMAZON_NOTES_BEFORE_V109))

        val edited = amazon.copy(notes = "Mine")
        val left = catalog109Migrate(listOf(edited), stock).single { it.id == "fleet-amazon" }
        assertEquals("Mine", left.notes)

        val customAmazon = amazon.copy(builtIn = false)
        val customLeft = catalog109Migrate(listOf(customAmazon), stock).single { it.id == "fleet-amazon" }
        assertEquals(AMAZON_NOTES_BEFORE_V109, customLeft.notes)
        assertFalse(customLeft.builtIn)
    }
}

class Catalog110MigrationTest {
    @Test
    fun addsBthomeWhenMissingAndLeavesACustomCopy() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 110)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val added = catalog110Migrate(listOf(other), stock)
        val row = added.single { it.id == "fleet-bthome" }
        assertTrue(row.builtIn)
        assertEquals("BTHome", row.name)
        assertEquals(SignatureClass.HOME, row.kind)
        assertTrue(row.matchAny)
        assertTrue(row.attentionNote.isBlank())
        assertNull(row.decode)
        assertEquals(RadioKind.BLE, row.rules.single().radio)
        assertEquals("FCD2", row.rules.single().text)
        assertEquals(listOf("fleet-bthome", "fleet-dji"), added.map { it.id })

        val custom = row.copy(builtIn = false, name = "Mine")
        val kept = catalog110Migrate(listOf(custom, other), stock)
        assertEquals("Mine", kept.single { it.id == "fleet-bthome" }.name)
        assertFalse(kept.single { it.id == "fleet-bthome" }.builtIn)
        assertEquals(1, kept.count { it.id == "fleet-bthome" })
    }
}

class Catalog111MigrationTest {
    @Test
    fun addsExposureNotificationWhenMissingAndLeavesACustomCopy() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 111)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val added = catalog111Migrate(listOf(other), stock)
        val row = added.single { it.id == "fleet-exposure-notification" }
        assertTrue(row.builtIn)
        assertEquals("Exposure Notification", row.name)
        assertEquals(SignatureClass.PHONE, row.kind)
        assertTrue(row.matchAny)
        assertTrue(row.attentionNote.isBlank())
        assertNull(row.decode)
        assertEquals(RadioKind.BLE, row.rules.single().radio)
        assertEquals("FD6F", row.rules.single().text)
        assertEquals(listOf("fleet-dji", "fleet-exposure-notification"), added.map { it.id })

        val custom = row.copy(builtIn = false, name = "Mine")
        val kept = catalog111Migrate(listOf(custom, other), stock)
        assertEquals("Mine", kept.single { it.id == "fleet-exposure-notification" }.name)
        assertFalse(kept.single { it.id == "fleet-exposure-notification" }.builtIn)
        assertEquals(1, kept.count { it.id == "fleet-exposure-notification" })
    }
}

class Catalog112MigrationTest {
    @Test
    fun addsSitFamiliesWhenMissingAndLeavesACustomCopy() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 112)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val added = catalog112Migrate(listOf(other), stock)
        assertEquals(ADDED_IN_V112.size + 1, added.size)
        ADDED_IN_V112.forEach { id ->
            val row = added.single { it.id == id }
            assertTrue(id, row.builtIn)
            assertTrue(id, row.attentionNote.isBlank())
            assertTrue(id, row.matchAny)
        }
        assertEquals(
            (listOf("DJI") + ADDED_IN_V112.map { stock.getValue(it).name }).sortedBy { it.lowercase() },
            added.map { it.name },
        )
        val tv = added.single { it.id == "fleet-samsung-tv" }
        assertEquals("Samsung TV", tv.name)
        assertEquals(SignatureClass.HOME, tv.kind)
        assertTrue(tv.rules.none { it.kind == RuleKind.MANUFACTURER_ID })
        assertEquals("FEA0", added.single { it.id == "fleet-google-cast" }.rules.single().text)
        assertEquals(RadioKind.BLE, added.single { it.id == "fleet-google-cast" }.rules.single().radio)
        assertEquals(
            0x06A8,
            added.single { it.id == "fleet-midea" }.rules.single { it.kind == RuleKind.MANUFACTURER_ID }.companyId,
        )
        assertEquals(0x05D6, added.single { it.id == "fleet-jlab" }.rules.single().companyId)

        val custom = tv.copy(builtIn = false, name = "Mine")
        val kept = catalog112Migrate(listOf(custom, other), stock)
        assertEquals("Mine", kept.single { it.id == "fleet-samsung-tv" }.name)
        assertFalse(kept.single { it.id == "fleet-samsung-tv" }.builtIn)
        assertEquals(1, kept.count { it.id == "fleet-samsung-tv" })
    }

    @Test
    fun copiesApplianceOsmoAndUnifiNotesOnlyWhenUnedited() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val appliance = stock.getValue("fleet-samsung-appliance").copy(
            notes = SAMSUNG_APPLIANCE_NOTES_BEFORE_V112,
        )
        val osmo = stock.getValue("fleet-osmo").copy(notes = OSMO_NOTES_BEFORE_V112)
        val unifi = stock.getValue("fleet-unifi").copy(notes = UNIFI_NOTES_BEFORE_V112)
        val edited = appliance.copy(notes = "My fridge note")
        val updated = catalog112Migrate(listOf(appliance, osmo, unifi, edited), stock)
        assertEquals(stock.getValue("fleet-samsung-appliance").notes, updated.single { it.id == "fleet-samsung-appliance" && it.notes != "My fridge note" }.notes)
        assertEquals(stock.getValue("fleet-osmo").notes, updated.single { it.id == "fleet-osmo" }.notes)
        assertEquals(stock.getValue("fleet-unifi").notes, updated.single { it.id == "fleet-unifi" }.notes)
        assertEquals("My fridge note", updated.single { it.notes == "My fridge note" }.notes)
    }
}

class Catalog113MigrationTest {
    @Test
    fun addsTwoWayRadiosWhenMissingAndLeavesACustomCopy() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 113)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val added = catalog113Migrate(listOf(other), stock)
        assertEquals(ADDED_IN_V113.size + 1, added.size)
        ADDED_IN_V113.forEach { id ->
            val row = added.single { it.id == id }
            assertTrue(id, row.builtIn)
            assertTrue(id, row.attentionNote.isBlank())
            assertTrue(id, row.matchAny)
            assertEquals(SignatureClass.TWO_WAY, row.kind)
            assertNull(row.decode)
        }
        assertEquals(
            (listOf("DJI") + ADDED_IN_V113.map { stock.getValue(it).name }).sortedBy { it.lowercase() },
            added.map { it.name },
        )

        val custom = stock.getValue("fleet-ble-kiss-tnc").copy(builtIn = false, name = "Mine")
        val kept = catalog113Migrate(listOf(custom, other), stock)
        assertEquals("Mine", kept.single { it.id == "fleet-ble-kiss-tnc" }.name)
        assertFalse(kept.single { it.id == "fleet-ble-kiss-tnc" }.builtIn)
        assertEquals(1, kept.count { it.id == "fleet-ble-kiss-tnc" })
        assertEquals(ADDED_IN_V113.size, kept.count { it.id in ADDED_IN_V113 })
    }
}

class Catalog114MigrationTest {
    @Test
    fun addsSitFamiliesWhenMissingAndLeavesACustomCopy() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 114)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val added = catalog114Migrate(listOf(other), stock).first
        assertEquals(ADDED_IN_V114.size + 1, added.size)
        ADDED_IN_V114.forEach { id ->
            val row = added.single { it.id == id }
            assertTrue(id, row.builtIn)
            if (id != "fleet-mentra-live" && id != "fleet-steiner-lrf") {
                assertTrue(id, row.attentionNote.isBlank())
            }
            assertTrue(id, row.matchAny)
            assertNull(row.decode)
        }
        assertEquals(
            (listOf("DJI") + ADDED_IN_V114.map { stock.getValue(it).name }).sortedBy { it.lowercase() },
            added.map { it.name },
        )
        assertEquals(SignatureClass.VEHICLE, added.single { it.id == "fleet-cobra" }.kind)
        assertEquals(SignatureClass.SIGNAGE, added.single { it.id == "fleet-gli-open" }.kind)
        assertEquals(SignatureClass.HOME, added.single { it.id == "fleet-lg-appliance" }.kind)

        val custom = stock.getValue("fleet-cobra").copy(builtIn = false, name = "Mine")
        val kept = catalog114Migrate(listOf(custom, other), stock).first
        assertEquals("Mine", kept.single { it.id == "fleet-cobra" }.name)
        assertFalse(kept.single { it.id == "fleet-cobra" }.builtIn)
        assertEquals(1, kept.count { it.id == "fleet-cobra" })
        assertEquals(ADDED_IN_V114.size, kept.count { it.id in ADDED_IN_V114 })
    }

    @Test
    fun copiesNameRuleNotesOnlyWhenUnedited() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val base = DefaultCatalog.fleets().map { fleet ->
            when (fleet.id) {
                "fleet-bmw" -> fleet.copy(notes = BMW_NOTES_BEFORE_V114)
                "fleet-gm-hotspot" -> fleet.copy(notes = GM_HOTSPOT_NOTES_BEFORE_V114)
                "fleet-mbux" -> fleet.copy(notes = MBUX_NOTES_BEFORE_V114)
                "fleet-nissan" -> fleet.copy(notes = NISSAN_NOTES_BEFORE_V114)
                "fleet-hp" -> fleet.copy(notes = HP_NOTES_BEFORE_V114)
                "fleet-samsung-tv" -> fleet.copy(notes = SAMSUNG_TV_NOTES_BEFORE_V114)
                "fleet-carlink" -> fleet.copy(notes = CARLINK_NOTES_BEFORE_V114)
                else -> fleet
            }
        } + Fleet(
            id = "custom-printer",
            name = "Mine",
            builtIn = false,
            notes = "My printer",
            rules = listOf(MatchRule(RuleKind.NAME_CONTAINS, text = "Printer")),
        )
        val updated = catalog114Migrate(base, stock).first
        assertEquals(base.map { it.id }, updated.map { it.id })
        assertEquals(stock.getValue("fleet-bmw").notes, updated.single { it.id == "fleet-bmw" }.notes)
        assertEquals(stock.getValue("fleet-gm-hotspot").notes, updated.single { it.id == "fleet-gm-hotspot" }.notes)
        assertEquals(stock.getValue("fleet-mbux").notes, updated.single { it.id == "fleet-mbux" }.notes)
        assertEquals(stock.getValue("fleet-nissan").notes, updated.single { it.id == "fleet-nissan" }.notes)
        assertEquals(stock.getValue("fleet-hp").notes, updated.single { it.id == "fleet-hp" }.notes)
        assertEquals(stock.getValue("fleet-samsung-tv").notes, updated.single { it.id == "fleet-samsung-tv" }.notes)
        assertEquals(stock.getValue("fleet-carlink").notes, updated.single { it.id == "fleet-carlink" }.notes)
        assertEquals("My printer", updated.single { it.id == "custom-printer" }.notes)
    }

    @Test
    fun bookmarksMentraAndCrossfireAndRefreshesGlassesWhenUnedited() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val oldFrame = stock.getValue("fleet-brilliant-frame").copy(
            name = BRILLIANT_NAME_BEFORE_V114,
            notes = BRILLIANT_NOTES_BEFORE_V114,
            attentionNote = BRILLIANT_ATTENTION_BEFORE_V114,
            rules = BRILLIANT_RULES_BEFORE_V114,
        )
        val patchedFrame = oldFrame.copy(
            rules = BRILLIANT_RULES_BEFORE_V114 + stock.getValue("fleet-brilliant-frame").rules,
        )
        val oldEven = stock.getValue("fleet-even-g1").copy(
            name = EVEN_NAME_BEFORE_V114,
            notes = EVEN_NOTES_BEFORE_V114,
            attentionNote = EVEN_ATTENTION_BEFORE_V114,
            rules = EVEN_RULES_BEFORE_V114,
        )
        val customRule = MatchRule(RuleKind.NAME_CONTAINS, text = "Mine", radio = RadioKind.BLE)
        val editedFrame = oldFrame.copy(rules = listOf(customRule))
        val customEven = oldEven.copy(builtIn = false, name = "Mine")
        val (added, watches) = catalog114Migrate(
            listOf(other, oldFrame, oldEven),
            stock,
            listOf(WatchTarget(id = "watch-frame", fleetId = "fleet-brilliant-frame", label = "Brilliant Frame")),
        )
        assertEquals(stock.getValue("fleet-brilliant-frame").rules, added.single { it.id == "fleet-brilliant-frame" }.rules)
        assertEquals("Brilliant Labs glasses (Frame / Halo)", added.single { it.id == "fleet-brilliant-frame" }.name)
        assertEquals(stock.getValue("fleet-even-g1").rules, added.single { it.id == "fleet-even-g1" }.rules)
        assertEquals("Even Realities G1 / G2", added.single { it.id == "fleet-even-g1" }.name)
        assertEquals("Brilliant Labs glasses (Frame / Halo)", watches.single { it.fleetId == "fleet-brilliant-frame" }.label)
        assertTrue(watches.any { it.id == "watch-fleet-steiner-lrf" && it.fleetId == "fleet-steiner-lrf" })
        assertEquals(1, watches.count { it.fleetId == "fleet-steiner-lrf" })
        assertTrue(watches.any { it.fleetId == "fleet-mentra-live" })
        assertTrue(watches.any { it.fleetId == "fleet-tbs-crossfire" })
        assertFalse(watches.any { it.fleetId == "fleet-rnode" })
        assertFalse(watches.any { it.fleetId == "fleet-hikmicro-optics" })
        assertFalse(watches.any { it.fleetId == "fleet-pulsar-optics" })
        assertFalse(watches.any { it.fleetId == "fleet-steiner-burris-thermal" })
        assertFalse(watches.any { it.fleetId == "fleet-phillips-connect" })
        assertFalse(watches.any { it.fleetId == "fleet-elk-led" })
        assertEquals(1, watches.count { it.fleetId == "fleet-mentra-live" })

        val again = catalog114Migrate(added, stock, watches)
        assertEquals(watches.map { it.fleetId }, again.second.map { it.fleetId })
        assertFalse(catalog114Pending(again.first, stock))

        val (patched, _) = catalog114Migrate(listOf(patchedFrame), stock)
        assertEquals(
            stock.getValue("fleet-brilliant-frame").rules,
            patched.single { it.id == "fleet-brilliant-frame" }.rules,
        )

        val (keptRules, _) = catalog114Migrate(listOf(editedFrame), stock)
        val keptFrame = keptRules.single { it.id == "fleet-brilliant-frame" }
        assertEquals(listOf(customRule), keptFrame.rules)
        assertEquals("Brilliant Labs glasses (Frame / Halo)", keptFrame.name)

        val (keptCustom, _) = catalog114Migrate(listOf(customEven, other), stock)
        assertEquals("Mine", keptCustom.single { it.id == "fleet-even-g1" }.name)
        assertEquals(EVEN_RULES_BEFORE_V114, keptCustom.single { it.id == "fleet-even-g1" }.rules)
    }

    @Test
    fun keepsACustomSteinerLrfAndBookmarksTheStockRowOnce() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val custom = stock.getValue("fleet-steiner-lrf").copy(builtIn = false, name = "Mine", attentionNote = "")
        val other = stock.getValue("fleet-dji")
        val (kept, watches) = catalog114Migrate(listOf(custom, other), stock)
        assertEquals("Mine", kept.single { it.id == "fleet-steiner-lrf" }.name)
        assertFalse(kept.single { it.id == "fleet-steiner-lrf" }.builtIn)
        assertEquals(1, kept.count { it.id == "fleet-steiner-lrf" })
        assertEquals(1, watches.count { it.fleetId == "fleet-steiner-lrf" })
        val (again, againWatches) = catalog114Migrate(kept, stock, watches)
        assertEquals(watches.map { it.fleetId }, againWatches.map { it.fleetId })
        assertEquals("Mine", again.single { it.id == "fleet-steiner-lrf" }.name)
    }
}

class Catalog115MigrationTest {
    @Test
    fun replacesStockPorkchopRulesAndLeavesAnEditedRow() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 115)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val fresh = stock.getValue("fleet-porkchop")
        val old = fresh.copy(rules = PORKCHOP_RULES_BEFORE_V115)
        val appended = PORKCHOP_RULES_BEFORE_V115 + fresh.rules.filter { it !in PORKCHOP_RULES_BEFORE_V115 }
        val patched = old.copy(rules = appended)
        val customRule = MatchRule(RuleKind.NAME_CONTAINS, text = "Soup")
        val edited = old.copy(rules = PORKCHOP_RULES_BEFORE_V115 + customRule)
        val custom = old.copy(builtIn = false, name = "Mine")
        val other = stock.getValue("fleet-dji")

        val updated = catalog115Migrate(listOf(old, other), stock)
        assertEquals(fresh.rules, updated.single { it.id == "fleet-porkchop" }.rules)
        assertEquals(old.notes, updated.single { it.id == "fleet-porkchop" }.notes)
        assertEquals(old.attentionNote, updated.single { it.id == "fleet-porkchop" }.attentionNote)
        assertEquals(listOf("fleet-porkchop", "fleet-dji"), updated.map { it.id })

        val fromPatch = catalog115Migrate(listOf(patched), stock)
        assertEquals(fresh.rules, fromPatch.single().rules)

        val keptEdit = catalog115Migrate(listOf(edited), stock)
        assertEquals(PORKCHOP_RULES_BEFORE_V115 + customRule, keptEdit.single().rules)

        val keptCustom = catalog115Migrate(listOf(custom, other), stock)
        assertEquals("Mine", keptCustom.single { it.id == "fleet-porkchop" }.name)
        assertEquals(PORKCHOP_RULES_BEFORE_V115, keptCustom.single { it.id == "fleet-porkchop" }.rules)
        assertEquals(2, keptCustom.size)

        val already = catalog115Migrate(listOf(fresh), stock)
        assertEquals(fresh.rules, already.single().rules)
        assertFalse(catalog115Pending(already, stock))
        assertTrue(catalog115Pending(listOf(old), stock))
        assertTrue(catalog115Pending(listOf(patched), stock))
        assertFalse(catalog115Pending(listOf(edited), stock))
        assertFalse(catalog115Pending(listOf(custom), stock))
        assertFalse(catalog115Pending(listOf(other), stock))
    }
}

class Catalog116MigrationTest {
    @Test
    fun addsTheFourRowsWhenMissingAndLeavesACustomCopy() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 116)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val added = catalog116Migrate(listOf(other), stock)
        assertEquals(ADDED_IN_V116.size + 1, added.size)
        ADDED_IN_V116.forEach { id ->
            val row = added.single { it.id == id }
            assertTrue(id, row.builtIn)
            assertTrue(id, row.attentionNote.isBlank())
            assertTrue(id, row.matchAny)
            assertNull(row.decode)
            assertFalse(id, id in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })
        }
        assertEquals(
            (listOf("DJI") + ADDED_IN_V116.map { stock.getValue(it).name }).sortedBy { it.lowercase() },
            added.map { it.name },
        )
        assertEquals(SignatureClass.HOME, added.single { it.id == "fleet-lednetwf" }.kind)
        assertEquals(SignatureClass.VEHICLE, added.single { it.id == "fleet-ez-lynk" }.kind)
        assertEquals(SignatureClass.VEHICLE, added.single { it.id == "fleet-trackunit" }.kind)
        assertEquals(SignatureClass.AUDIO, added.single { it.id == "fleet-oontz" }.kind)

        val again = catalog116Migrate(added, stock)
        assertEquals(added.map { it.id }, again.map { it.id })
        assertEquals(ADDED_IN_V116.size, again.count { it.id in ADDED_IN_V116 })

        val custom = stock.getValue("fleet-lednetwf").copy(builtIn = false, name = "Mine")
        val kept = catalog116Migrate(listOf(custom, other), stock)
        assertEquals("Mine", kept.single { it.id == "fleet-lednetwf" }.name)
        assertFalse(kept.single { it.id == "fleet-lednetwf" }.builtIn)
        assertEquals(1, kept.count { it.id == "fleet-lednetwf" })
        assertEquals(ADDED_IN_V116.size, kept.count { it.id in ADDED_IN_V116 })
        val keptAgain = catalog116Migrate(kept, stock)
        assertEquals(kept.map { it.id }, keptAgain.map { it.id })
        assertEquals("Mine", keptAgain.single { it.id == "fleet-lednetwf" }.name)
    }
}

class Catalog117MigrationTest {
    @Test
    fun addsTheThreeRowsWhenMissingAndLeavesACustomCopy() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 117)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val added = catalog117Migrate(listOf(other), stock)
        assertEquals(ADDED_IN_V117.size + 1, added.size)
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        assertFalse("fleet-itag" in watched)
        assertFalse("fleet-viofo" in watched)
        assertTrue("fleet-ufo-drone" in watched)
        ADDED_IN_V117.forEach { id ->
            val row = added.single { it.id == id }
            assertTrue(id, row.builtIn)
            assertTrue(id, row.attentionNote.isBlank())
            assertTrue(id, row.matchAny)
            assertNull(row.decode)
        }
        assertEquals(
            (listOf("DJI") + ADDED_IN_V117.map { stock.getValue(it).name }).sortedBy { it.lowercase() },
            added.map { it.name },
        )
        assertEquals(SignatureClass.FINDER, added.single { it.id == "fleet-itag" }.kind)
        assertEquals(SignatureClass.DRONE, added.single { it.id == "fleet-ufo-drone" }.kind)
        assertEquals(SignatureClass.VEHICLE, added.single { it.id == "fleet-viofo" }.kind)

        val again = catalog117Migrate(added, stock)
        assertEquals(added.map { it.id }, again.map { it.id })
        assertEquals(ADDED_IN_V117.size, again.count { it.id in ADDED_IN_V117 })

        val custom = stock.getValue("fleet-itag").copy(builtIn = false, name = "Mine")
        val kept = catalog117Migrate(listOf(custom, other), stock)
        assertEquals("Mine", kept.single { it.id == "fleet-itag" }.name)
        assertFalse(kept.single { it.id == "fleet-itag" }.builtIn)
        assertEquals(1, kept.count { it.id == "fleet-itag" })
        assertEquals(ADDED_IN_V117.size, kept.count { it.id in ADDED_IN_V117 })
        val keptAgain = catalog117Migrate(kept, stock)
        assertEquals(kept.map { it.id }, keptAgain.map { it.id })
        assertEquals("Mine", keptAgain.single { it.id == "fleet-itag" }.name)
    }
}

class Catalog118MigrationTest {
    @Test
    fun copiesSetupNotesOnlyWhenUnedited() {
        assertTrue(ConfigStore.CATALOG_VERSION >= 118)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val tuya = stock.getValue("fleet-tuya").copy(notes = TUYA_NOTES_BEFORE_V118)
        val unifi = stock.getValue("fleet-unifi").copy(notes = UNIFI_NOTES_BEFORE_V118)
        val midea = stock.getValue("fleet-midea").copy(notes = MIDEA_NOTES_BEFORE_V118)
        val motive = stock.getValue("fleet-motive").copy(notes = MOTIVE_NOTES_BEFORE_V118)
        val edited = tuya.copy(notes = "My plug note")
        val custom = motive.copy(builtIn = false)
        val updated = catalog118Migrate(listOf(tuya, unifi, midea, motive, edited, custom), stock)
        assertEquals(
            stock.getValue("fleet-tuya").notes,
            updated.single { it.id == "fleet-tuya" && it.notes != "My plug note" }.notes,
        )
        assertEquals(stock.getValue("fleet-unifi").notes, updated.single { it.id == "fleet-unifi" }.notes)
        assertEquals(stock.getValue("fleet-midea").notes, updated.single { it.id == "fleet-midea" }.notes)
        assertEquals(
            stock.getValue("fleet-motive").notes,
            updated.single { it.id == "fleet-motive" && it.builtIn }.notes,
        )
        assertEquals("My plug note", updated.single { it.notes == "My plug note" }.notes)
        assertEquals(MOTIVE_NOTES_BEFORE_V118, updated.single { it.id == "fleet-motive" && !it.builtIn }.notes)
        val again = catalog118Migrate(updated, stock)
        assertEquals(updated.map { it.notes }, again.map { it.notes })
    }

    @Test
    fun addsProductRowsWhenMissingAndLeavesACustomCopy() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val other = stock.getValue("fleet-dji")
        val added = catalog118Migrate(listOf(other), stock)
        assertEquals(ADDED_IN_V118.size + 1, added.size)
        ADDED_IN_V118.forEach { id ->
            val row = added.single { it.id == id }
            assertTrue(id, row.builtIn)
            assertTrue(id, row.attentionNote.isBlank())
            assertFalse(id, id in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })
        }
        val custom = stock.getValue("fleet-kuna").copy(builtIn = false, name = "Mine")
        val kept = catalog118Migrate(listOf(custom, other), stock)
        assertEquals("Mine", kept.single { it.id == "fleet-kuna" }.name)
        assertEquals(1, kept.count { it.id == "fleet-kuna" })
        assertEquals(ADDED_IN_V118.size, kept.count { it.id in ADDED_IN_V118 })
    }
}
