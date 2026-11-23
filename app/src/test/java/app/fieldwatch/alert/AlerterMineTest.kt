package app.fieldwatch.alert

import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.WatchTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlerterMineTest {
    @Test
    fun mineSkipsThatRadioAndLeavesTheRestOfTheFleet() {
        val fleet = WatchTarget(id = "w", fleetId = "fleet-dult", label = "DULT tracker")
        val mineRow = WatchTarget(
            id = "m",
            deviceKey = "BLE:11:22:33:44:55:66",
            label = "bag tag",
            alert = true,
            mine = true,
        )
        val mineKeys = setOf(mineRow.deviceKey!!)
        val mineDevice = ble("BLE:11:22:33:44:55:66", setOf("fleet-dult"))
        val other = ble("BLE:11:22:33:44:55:77", setOf("fleet-dult"))
        assertFalse(shouldRaiseWatch(fleet, mineDevice, mineKeys))
        assertFalse(shouldRaiseWatch(mineRow, mineDevice, mineKeys))
        assertTrue(shouldRaiseWatch(fleet, other, mineKeys))
        val quiet = mineRow.copy(id = "q", deviceKey = other.key, label = "other", alert = false, mine = false)
        assertFalse(shouldRaiseWatch(quiet, other, emptySet()))
    }

    @Test
    fun muteSkipsThatRadioAndLeavesTheRestOfTheFleet() {
        val fleet = WatchTarget(id = "w", fleetId = "fleet-porkchop", label = "Porkchop")
        val muteRow = WatchTarget(
            id = "m",
            deviceKey = "WIFI:11:22:33:44:55:66",
            label = "neighbor",
            alert = true,
            mute = true,
        )
        val muteKeys = setOf(muteRow.deviceKey!!)
        val muted = ble("WIFI:11:22:33:44:55:66", setOf("fleet-porkchop"))
        val other = ble("WIFI:AA:BB:CC:DD:EE:77", setOf("fleet-porkchop"))
        assertFalse(muteRow.mine)
        assertFalse(shouldRaiseWatch(fleet, muted, emptySet(), muteKeys))
        assertFalse(shouldRaiseWatch(muteRow, muted, emptySet(), muteKeys))
        assertTrue(shouldRaiseWatch(fleet, other, emptySet(), muteKeys))
        assertTrue(shouldRaiseWatch(fleet, other, emptySet()))
    }

    private fun ble(key: String, fleets: Set<String>) = Sighting(
        key = key,
        kind = RadioKind.BLE,
        mac = key.substringAfter(':'),
        name = "tag",
        rssi = -50,
        rssiMin = -50,
        rssiMax = -50,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = false,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = fleets,
        rssiHistory = emptyList(),
        presence = emptyList(),
    )
}
