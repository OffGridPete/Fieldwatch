package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitDiffTest {
    private val axon = Fleet(
        id = "fleet-axon",
        name = "Axon",
        kind = SignatureClass.LAW_ENFORCEMENT,
        attentionNote = "Body-worn, in-car, dock, or TASER.",
    )
    private val fleets = listOf(axon)

    @Test
    fun secondSitSkipsThisSavedAndDefaultsToNewestOther() {
        val a = SitSummary("a", "plaza today", 30L)
        val b = SitSummary("b", "plaza last week", 20L)
        val c = SitSummary("c", "lot", 10L)
        val closed = listOf(a, b, c)
        assertEquals(listOf(b, c), SitDiff.secondSitChoices(closed, "a"))
        assertEquals("b", SitDiff.defaultSecondSitId(closed, "a"))
        assertEquals("a", SitDiff.defaultSecondSitId(closed, null))
        assertNull(SitDiff.defaultSecondSitId(emptyList(), null))
        assertNull(SitDiff.thisSavedId(SitSummary("open", "now", 1L), "a"))
        assertEquals("a", SitDiff.thisSavedId(null, "a"))
    }

    @Test
    fun presenceSplitsOnlyThisOnlySecondAndBoth() {
        val named = setOf("WIFI:AA:AA:AA:AA:AA:03")
        val thisSit = SitDiff.Side(
            name = "today",
            ram = false,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:01", "cam", extra = true),
                radio("WIFI:AA:AA:AA:AA:AA:02", "ap"),
                radio("WIFI:AA:AA:AA:AA:AA:03", "van", named = true),
            ),
        )
        val second = SitDiff.Side(
            name = "last week",
            ram = false,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:02", "ap"),
                radio("WIFI:AA:AA:AA:AA:AA:03", "van", named = true),
                radio("WIFI:AA:AA:AA:AA:AA:04", "new"),
            ),
        )
        val text = SitDiff.report(thisSit, second, demoMode = false)
        assertTrue(text.contains("ONLY IN THIS SIT (1)"))
        assertTrue(text.contains("AA:AA:AA:AA:AA:01"))
        assertTrue(text.contains("cam"))
        assertTrue(text.contains("Extra attention"))
        assertTrue(text.contains("ONLY IN SECOND SIT (1)"))
        assertTrue(text.contains("AA:AA:AA:AA:AA:04"))
        assertTrue(text.contains("IN BOTH (2)"))
        assertTrue(text.contains("van"))
        assertTrue(text.contains("This sit: today"))
        assertTrue(text.contains("Second sit: last week"))
        assertFalse(text.contains("Last 15 minutes is the Live RAM set"))
    }

    @Test
    fun observerNotesPrintOnExclusiveLine() {
        val thisSit = SitDiff.Side(
            name = "today",
            ram = false,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:01", "van", named = true, observer = "fleet van, lot B"),
            ),
        )
        val second = SitDiff.Side(name = "week", ram = false, radios = emptyList())
        val text = SitDiff.report(thisSit, second, demoMode = false)
        assertTrue(text.contains("OBSERVER NOTES"))
        assertTrue(text.contains("van"))
        assertTrue(text.contains("fleet van, lot B"))
        assertTrue(text.contains("this sit"))
        assertFalse(text.contains("Observer: fleet van, lot B"))
    }

    @Test
    fun ramCapNoteWhenThisSitIsLastFifteen() {
        val thisSit = SitDiff.Side(
            name = "Last 15 minutes",
            ram = true,
            radios = listOf(radio("BLE:AA:AA:AA:AA:AA:01", "")),
        )
        val second = SitDiff.Side(
            name = "plaza",
            ram = false,
            radios = emptyList(),
        )
        val text = SitDiff.report(thisSit, second, demoMode = false)
        assertTrue(text.contains("Last 15 minutes is the Live RAM set"))
        assertTrue(text.contains("${Sit.RADIO_CAP}"))
        assertTrue(text.contains("ONLY IN THIS SIT (1)"))
        assertTrue(text.contains("ONLY IN SECOND SIT (0)"))
        assertTrue(text.contains("(none)"))
    }

    @Test
    fun privacyModeMasksMacTails() {
        val thisSit = SitDiff.Side(
            name = "today",
            ram = false,
            radios = listOf(radio("WIFI:AA:BB:CC:11:22:33", "Cafe")),
        )
        val second = SitDiff.Side(name = "week", ram = false, radios = emptyList())
        val text = SitDiff.report(thisSit, second, demoMode = true)
        assertTrue(text.contains("Privacy"))
        assertTrue(text.contains("AA:BB:CC:**:**:**"))
        assertFalse(text.contains("11:22:33"))
        assertTrue(text.contains("FIELDWATCH SIT COMPARE"))
    }

    @Test
    fun extraAttentionFromFleetNote() {
        val device = Sighting(
            key = "BLE:AA:BB:CC:DD:EE:01",
            kind = RadioKind.BLE,
            mac = "AA:BB:CC:DD:EE:01",
            name = "dock",
            rssi = -50,
            rssiMin = -50,
            rssiMax = -50,
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
            firstSeen = 1L,
            lastSeen = 1L,
            hitCount = 1,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
        )
        val row = SitDiff.fromSighting(device, fleets, customNames = emptyMap())
        assertTrue(row.extraAttention)
        assertEquals(listOf("Axon"), row.fleetNames)
        assertFalse(row.named)
        assertTrue(row.randomized)
    }

    @Test
    fun aiExportIsAddendumNotASecondRoster() {
        val thisSit = SitDiff.Side(
            name = "today",
            ram = true,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:01", "cam", extra = true),
                radio("BLE:BB:BB:BB:BB:BB:02", "tag", named = true, ble = true, rand = true),
                radio("WIFI:AA:AA:AA:AA:AA:02", "ap"),
            ),
        )
        val second = SitDiff.Side(
            name = "last week",
            ram = false,
            radios = listOf(
                radio("WIFI:AA:AA:AA:AA:AA:02", "ap"),
                radio("WIFI:AA:AA:AA:AA:AA:04", "new"),
            ),
        )
        val text = SitDiffPrompt.build(thisSit, second, demoMode = false)
        assertTrue(text.contains("Do not rewrite that report"))
        assertTrue(text.contains("Overlap:"))
        assertTrue(text.contains("Exclusive Extra attention:"))
        assertTrue(text.contains("cam"))
        assertTrue(text.contains("Exclusive Named radios:"))
        assertTrue(text.contains("tag"))
        assertTrue(text.contains("RAND BLE"))
        assertTrue(text.contains("Cap note:"))
        assertTrue(text.contains("Takeaway:"))
        assertTrue(text.contains("FIELDWATCH SIT COMPARE"))
        assertFalse(text.contains("Full Wi-Fi inventory"))
    }

    @Test
    fun aiExportPrivacyMasksExclusiveMacs() {
        val thisSit = SitDiff.Side(
            name = "today",
            ram = false,
            radios = listOf(radio("WIFI:AA:BB:CC:11:22:33", "Cafe", extra = true)),
        )
        val second = SitDiff.Side(name = "week", ram = false, radios = emptyList())
        val text = SitDiffPrompt.build(thisSit, second, demoMode = true)
        assertTrue(text.contains("Privacy mode"))
        assertTrue(text.contains("AA:BB:CC:**:**:**"))
        assertFalse(text.contains("11:22:33"))
    }

    private fun radio(
        key: String,
        name: String,
        extra: Boolean = false,
        named: Boolean = false,
        ble: Boolean = false,
        rand: Boolean = false,
        observer: String = "",
    ) = SitDiff.Radio(
        key = key,
        kind = if (ble) RadioKind.BLE else RadioKind.WIFI,
        mac = key.substringAfter(':'),
        name = name,
        extraAttention = extra,
        named = named,
        fleetNames = if (extra) listOf("Axon") else emptyList(),
        randomized = rand,
        observerNotes = observer,
    )
}
