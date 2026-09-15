package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioBookmarksTest {
    private val radio = WatchTarget(id = "r1", deviceKey = "BLE:AA:BB:CC:DD:EE:FF", label = "van tag")
    private val fleet = WatchTarget(id = "f1", fleetId = "fleet-airtag", label = "Apple AirTags")

    @Test
    fun radiosOnlyDropsSignatureWatches() {
        val list = listOf(fleet, radio)
        assertEquals(listOf(radio), RadioBookmarks.radios(list))
        assertEquals(listOf(fleet), RadioBookmarks.withoutRadios(list))
    }

    @Test
    fun clearRadiosKeepsSignatures() {
        assertEquals(listOf(fleet), RadioBookmarks.withoutRadios(listOf(fleet, radio)))
    }

    @Test
    fun renameOnlyTouchesThatRadio() {
        val next = RadioBookmarks.rename(listOf(fleet, radio), "r1", "  porch cam  ")
        assertEquals("porch cam", next.single { it.id == "r1" }.label)
        assertEquals("Apple AirTags", next.single { it.id == "f1" }.label)
    }

    @Test
    fun removeOneLeavesSignatures() {
        val next = RadioBookmarks.remove(listOf(fleet, radio), "r1")
        assertEquals(listOf(fleet), next)
    }

    @Test
    fun upsertNameWithoutAlertThenToggleKeepsName() {
        val named = RadioBookmarks.upsertName(listOf(fleet), "BLE:AA:BB:CC:DD:EE:FF", "Peter's Mesh Node")
        val row = named.single { it.deviceKey != null }
        assertEquals("Peter's Mesh Node", row.label)
        assertEquals(false, row.alert)
        val watched = RadioBookmarks.toggleAlert(named, row.deviceKey!!, "ignored")
        assertEquals(true, watched.single { it.deviceKey != null }.alert)
        assertEquals("Peter's Mesh Node", watched.single { it.deviceKey != null }.label)
        val quiet = RadioBookmarks.toggleAlert(watched, row.deviceKey!!, "ignored")
        assertEquals(false, quiet.single { it.deviceKey != null }.alert)
        assertEquals("Peter's Mesh Node", quiet.single { it.deviceKey != null }.label)
        assertEquals(1, RadioBookmarks.radios(quiet).size)
        assertEquals(fleet, quiet.single { it.fleetId != null })
    }

    @Test
    fun namedKeysSkipsSignaturesAndBlankLabels() {
        val blank = radio.copy(id = "r2", deviceKey = "WIFI:00:11:22:33:44:55", label = "  ")
        assertEquals(
            setOf("BLE:AA:BB:CC:DD:EE:FF"),
            RadioBookmarks.namedKeys(listOf(fleet, radio, blank)),
        )
    }

    @Test
    fun watchedOnlySetsAreBookmarksAndAlertRadios() {
        val quiet = radio.copy(alert = false)
        val alerted = radio.copy(id = "r2", deviceKey = "BLE:11:22:33:44:55:66", alert = true)
        val list = listOf(fleet, quiet, alerted)
        assertEquals(setOf("fleet-airtag"), RadioBookmarks.watchedFleetIds(list))
        assertEquals(setOf("BLE:11:22:33:44:55:66"), RadioBookmarks.alertDeviceKeys(list))
    }

    @Test
    fun parseKey() {
        assertEquals(RadioKind.BLE to "AA:BB:CC:DD:EE:FF", RadioBookmarks.parseKey("BLE:AA:BB:CC:DD:EE:FF"))
        assertEquals(RadioKind.WIFI to "00:11:22:33:44:55", RadioBookmarks.parseKey("WIFI:00:11:22:33:44:55"))
        assertNull(RadioBookmarks.parseKey("nope"))
    }

    @Test
    fun listLineUsesWatchNameForNameLines() {
        val named = ble(name = "Meshtastic_3480")
        assertEquals("Peter's Mesh Node", named.listLineText(ListLine.NAME_AND_TYPE, watchName = "Peter's Mesh Node"))
        assertEquals("Peter's Mesh Node", named.listLineText(ListLine.ADVERTISED_NAME, watchName = "Peter's Mesh Node"))
        assertEquals(named.mac, named.listLineText(ListLine.MAC, watchName = "Peter's Mesh Node"))
        assertEquals("Meshtastic_3480", named.listLineText(ListLine.NAME_AND_TYPE))
    }

    @Test
    fun suggestLabelPrefersAdvertisedName() {
        val named = ble(name = "Tile")
        assertEquals("Tile", RadioBookmarks.suggestLabel(named))
        val unnamed = ble(name = "")
        assertEquals("unnamed LE", RadioBookmarks.suggestLabel(unnamed))
    }

    private fun ble(name: String) = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:FF",
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:FF",
        name = name,
        rssi = -50,
        rssiMin = -50,
        rssiMax = -50,
        channel = 0,
        frequencyMhz = 0,
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
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
    )
}
