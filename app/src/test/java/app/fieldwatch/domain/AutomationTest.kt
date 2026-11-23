package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTest {
    @Test
    fun actionsMatchThePublicNames() {
        assertEquals(Automation.Action.CLOSE, Automation.action(Automation.CLOSE))
        assertNull(Automation.action(Automation.OPEN))
        assertEquals(Automation.Action.START_SCAN, Automation.action(Automation.START_SCAN))
        assertEquals(Automation.Action.STOP_SCAN, Automation.action(Automation.STOP_SCAN))
        assertEquals(Automation.Action.SCAN_STATUS, Automation.action(Automation.SCAN_STATUS))
        assertEquals(Automation.Action.START_SIT, Automation.action(Automation.START_SIT))
        assertEquals(Automation.Action.STOP_SIT, Automation.action(Automation.STOP_SIT))
        assertEquals(Automation.Action.EXPORT_SIT, Automation.action(Automation.EXPORT_SIT))
        assertEquals(Automation.Action.EXPORT_LOG, Automation.action(Automation.EXPORT_LOG))
        assertNull(Automation.action("app.fieldwatch.INCLUDE_LOCATION"))
        assertEquals("app.fieldwatch.STOP_SCAN", Automation.STOP_SCAN)
    }

    @Test
    fun blankFormatIsCsvAndUnknownIsRejected() {
        assertEquals(LogExportKind.LOG_CSV, Automation.format(null))
        assertEquals(LogExportKind.LOG_CSV, Automation.format("  "))
        assertEquals(LogExportKind.LOG_JSONL, Automation.format("JSON lines"))
        assertEquals(LogExportKind.ATAK, Automation.format("atak"))
        assertNull(Automation.format("pdf"))
        assertTrue(Automation.allowsLog(LogExportKind.GPX))
        assertTrue(!Automation.allowsLog(LogExportKind.GEOJSON))
        assertTrue(!Automation.allowsLog(LogExportKind.ATAK))
    }

    @Test
    fun radiosDefaultToBoth() {
        assertEquals(LogExportRadios.BOTH, Automation.radios(null))
        assertEquals(LogExportRadios.WIFI, Automation.radios("Wi-Fi"))
        assertEquals(LogExportRadios.BLE, Automation.radios("bluetooth"))
    }

    @Test
    fun newestSavedIsTheOneStoppedLast() {
        val older = sit("a", "Morning", start = 10L, end = 20L)
        val laterStart = sit("b", "Noon", start = 50L, end = 60L)
        val stoppedLast = sit("c", "Earlier start", start = 5L, end = 90L)
        val open = sit("d", "Open", start = 100L, end = null)
        val picked = Automation.newestSaved(listOf(laterStart, older, open, stoppedLast))
        assertEquals("c", picked?.id)
        assertNull(Automation.newestSaved(listOf(open)))
    }

    @Test
    fun statusStaysOneLinePerField() {
        val text = Automation.status(
            Automation.Status(
                ok = true,
                action = Automation.EXPORT_SIT,
                scanning = false,
                sit = "",
                saved = "Lot A",
                file = "/tmp/sit.csv",
                format = "csv",
                error = "",
            ),
        )
        assertTrue(text.contains("ok=true"))
        assertTrue(text.contains("saved=Lot A"))
        assertTrue(text.contains("file=/tmp/sit.csv"))
        assertTrue(text.endsWith("error=\n"))
    }

    private fun sit(id: String, name: String, start: Long, end: Long?) = SitSummary(
        id = id,
        name = name,
        startAt = start,
        endAt = end,
    )
}
