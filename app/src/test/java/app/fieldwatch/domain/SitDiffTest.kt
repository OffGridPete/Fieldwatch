package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SitDiffTest {

    private fun radio(
        key: String,
        name: String = "",
        rssi: Int = -60,
        rssiMax: Int = rssi,
        fleetIds: Set<String> = emptySet(),
    ) = SitRadio(
        key = key,
        kind = if (key.startsWith("WIFI:")) RadioKind.WIFI else RadioKind.BLE,
        mac = key.substringAfter(':'),
        name = name,
        fleetIds = fleetIds,
        firstSeen = 0L,
        lastSeen = 1_000L,
        hitCount = 1,
        rssi = rssi,
        rssiMin = rssi - 10,
        rssiMax = rssiMax,
    )

    private fun sit(id: String, name: String, radios: List<SitRadio>) = SitFile(
        summary = SitSummary(id = id, name = name, startAt = 1_000L, endAt = 61_000L),
        radios = radios,
    )

    private val fleets = listOf(
        Fleet(id = "f-airtag", name = "Apple AirTags", rules = emptyList()),
    )

    @Test
    fun diffSplitsArrivedDepartedAndCommon() {
        val a = sit("a", "Morning", listOf(
            radio("WIFI:00:11:22:33:44:55", "HomeAP", -50, -48),
            radio("BLE:AA:BB:CC:DD:EE:01", "Tag", -70, -65),
        ))
        val b = sit("b", "Evening", listOf(
            radio("WIFI:00:11:22:33:44:55", "HomeAP", -55, -52),
            radio("BLE:AA:BB:CC:DD:EE:02", "New Beacon", -40, -38),
        ))
        val r = SitDiff.diff(a, b)
        assertEquals(1, r.arrivedCount)
        assertEquals("BLE:AA:BB:CC:DD:EE:02", r.onlyB.single().key)
        assertEquals(1, r.departedCount)
        assertEquals("BLE:AA:BB:CC:DD:EE:01", r.onlyA.single().key)
        assertEquals(1, r.commonCount)
        val (inA, inB) = r.common.single()
        assertEquals(-48, inA.rssiMax)
        assertEquals(-52, inB.rssiMax)
    }

    @Test
    fun identicalSitsHaveEmptyDiff() {
        val a = sit("a", "One", listOf(radio("WIFI:00:11:22:33:44:55")))
        val b = sit("b", "Two", listOf(radio("WIFI:00:11:22:33:44:55")))
        val r = SitDiff.diff(a, b)
        assertEquals(0, r.arrivedCount)
        assertEquals(0, r.departedCount)
        assertEquals(1, r.commonCount)
    }

    @Test
    fun arrivedSortsLoudestFirst() {
        val a = sit("a", "A", emptyList())
        val b = sit("b", "B", listOf(
            radio("BLE:AA:BB:CC:DD:EE:01", "quiet", -90, -85),
            radio("BLE:AA:BB:CC:DD:EE:02", "loud", -40, -35),
        ))
        val r = SitDiff.diff(a, b)
        assertEquals(listOf("loud", "quiet"), r.onlyB.map { it.name })
    }

    @Test
    fun textReportHasSectionsAndSignatureNames() {
        val a = sit("a", "Site A", listOf(radio("BLE:AA:BB:CC:DD:EE:01", "Old", -70)))
        val b = sit("b", "Site B", listOf(
            radio("BLE:AA:BB:CC:DD:EE:02", "Tag", -50, fleetIds = setOf("f-airtag")),
        ))
        val text = SitDiff.toText(SitDiff.diff(a, b), fleets)
        assertTrue(text.contains("## Arrived in B (1)"))
        assertTrue(text.contains("## Departed since A (1)"))
        assertTrue(text.contains("+ Tag  AA:BB:CC:DD:EE:02  BLE  -50 dBm  · Apple AirTags"))
        assertTrue(text.contains("- Old  AA:BB:CC:DD:EE:01"))
        assertTrue(text.contains("not proof of presence"))
    }

    @Test
    fun commonRowsReportLoudnessChange() {
        val a = sit("a", "A", listOf(radio("WIFI:00:11:22:33:44:55", "AP", -60, -58)))
        val b = sit("b", "B", listOf(radio("WIFI:00:11:22:33:44:55", "AP", -45, -42)))
        val text = SitDiff.toText(SitDiff.diff(a, b), emptyList())
        assertTrue(text.contains("peak -58 → -42 dBm, louder"))
    }
}
