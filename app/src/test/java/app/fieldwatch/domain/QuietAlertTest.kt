package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietAlertTest {
    @Test
    fun muteSkipsTheQuietCueAndMineDoesNot() {
        val on = WatchTarget(
            id = "r",
            deviceKey = "BLE:AA:BB:CC:DD:EE:FF",
            label = "bag",
            alertOnGone = true,
            mine = true,
        )
        assertTrue(QuietAlert.armed(on))
        assertFalse(QuietAlert.armed(on.copy(mute = true)))
        assertFalse(QuietAlert.armed(on.copy(alertOnGone = false)))
        assertFalse(QuietAlert.armed(WatchTarget(id = "f", fleetId = "fleet-airtag", label = "tags", alertOnGone = true)))
    }

    @Test
    fun twoMinutesAndTheLateCaps() {
        assertFalse(QuietAlert.heardLongEnough(0L, 119_999L))
        assertTrue(QuietAlert.heardLongEnough(0L, 120_000L))
        assertTrue(QuietAlert.onTime(RadioKind.BLE, 0L, 15_000L, 15_000L))
        assertTrue(QuietAlert.onTime(RadioKind.BLE, 0L, 15_000L + 6_000L, 15_000L))
        assertFalse(QuietAlert.onTime(RadioKind.BLE, 0L, 15_000L + 6_001L, 15_000L))
        assertTrue(QuietAlert.onTime(RadioKind.WIFI, 0L, 15_000L + 45_000L, 15_000L))
        assertFalse(QuietAlert.onTime(RadioKind.WIFI, 0L, 15_000L + 45_001L, 15_000L))
        assertFalse(QuietAlert.onTime(RadioKind.BLE, 0L, 14_000L, 15_000L))
    }

    @Test
    fun cueNameUsesTheCustomNameAndMasksAMac() {
        val mac = "AA:BB:CC:DD:EE:FF"
        assertEquals("bag", QuietAlert.cueName("bag", mac, mac, demoMode = true))
        assertEquals("AA:BB:CC:**:**:**", QuietAlert.cueName("  ", mac, mac, demoMode = true))
        assertEquals("—", QuietAlert.rssiText(127))
        assertEquals("-62", QuietAlert.rssiText(-62))
    }

    @Test
    fun reportLineNamesTheRadioAndTheLastHear() {
        val mark = QuietMark(
            at = 1_700_000_000_000L,
            lastHeard = 1_700_000_000_000L - 90_000L,
            key = "BLE:AA:BB:CC:DD:EE:FF",
            name = "bag tag",
            rssi = -62,
        )
        val line = mark.reportLine()
        assertTrue(line.contains("bag tag went quiet"))
        assertTrue(line.contains("UTC"))
        assertTrue(line.contains(FloodBurst.clock(mark.at)))
        assertTrue(line.contains(FloodBurst.clock(mark.lastHeard)))
    }
}
