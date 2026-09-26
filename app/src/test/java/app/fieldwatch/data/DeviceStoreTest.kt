package app.fieldwatch.data

import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.Observation
import app.fieldwatch.domain.RadioFacts
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.ServiceDataRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceStoreTest {
    private val fleets = DefaultCatalog.fleets()

    @Test
    fun ciscoOuiLabelsOnIngestAndSurvivesRssiOnly() {
        val store = DeviceStore()
        val mac = "00:00:0C:11:22:33"
        store.ingestBatch(listOf(wifi(mac, name = "Campus", rssi = -60)), fleets, 30)
        val first = store.find("WIFI:$mac")!!
        assertTrue("Cisco OUI", "fleet-cisco" in first.fleetIds)

        store.ingestBatch(listOf(wifi(mac, name = "Campus", rssi = -42)), fleets, 30)
        val again = store.find("WIFI:$mac")!!
        assertTrue("RSSI-only keeps Cisco", "fleet-cisco" in again.fleetIds)
        assertTrue(again.rssi == -42)
    }

    @Test
    fun unifiVirtualBssidLabelsWhenVendorIeArrives() {
        val store = DeviceStore()
        val mac = "82:F9:2C:00:00:01"
        store.ingestBatch(listOf(wifi(mac, name = "Deep Learning")), fleets, 30)
        val before = store.find("WIFI:$mac")!!
        assertFalse("no Ubiquiti OUI on randomized BSSID", "fleet-unifi-ap" in before.fleetIds)

        store.ingestBatch(listOf(wifi(mac, name = "Deep Learning", rssi = -55)), fleets, 30)
        assertFalse("RSSI-only does not invent UniFi", "fleet-unifi-ap" in store.find("WIFI:$mac")!!.fleetIds)

        store.ingestBatch(
            listOf(wifi(mac, name = "Deep Learning", ies = listOf("00:50:F2", "00:0F:AC", "AC:8B:A9"))),
            fleets,
            30,
        )
        assertTrue(
            "Ubiquiti vendor IE labels virtual BSS",
            "fleet-unifi-ap" in store.find("WIFI:$mac")!!.fleetIds,
        )
    }

    @Test
    fun nameAppearingLaterCanLabel() {
        val store = DeviceStore()
        val mac = "DE:AD:00:11:22:33"
        store.ingestBatch(listOf(wifi(mac, name = "")), fleets, 30)
        assertFalse("fleet-netgear" in store.find("WIFI:$mac")!!.fleetIds)

        store.ingestBatch(listOf(wifi(mac, name = "NETGEAR-12AB")), fleets, 30)
        assertTrue("fleet-netgear" in store.find("WIFI:$mac")!!.fleetIds)
    }

    @Test
    fun bleCiscoOuiDoesNotTakeWifiSignature() {
        val store = DeviceStore()
        val mac = "00:00:0C:11:22:33"
        store.ingestBatch(listOf(ble(mac, name = "Campus")), fleets, 30)
        assertFalse("fleet-cisco" in store.find("BLE:$mac")!!.fleetIds)
    }

    @Test
    fun fastPairPairingSticksAfterAccountKeyPayload() {
        val store = DeviceStore()
        val mac = "AA:BB:CC:DD:EE:01"
        store.ingestBatch(
            listOf(
                ble(
                    mac,
                    name = "",
                    facts = RadioFacts(
                        serviceData = listOf(ServiceDataRecord("FE2C", "2A4139")),
                    ),
                ),
            ),
            fleets,
            30,
        )
        assertTrue(store.find("BLE:$mac")!!.fastPairPairing)
        store.ingestBatch(
            listOf(
                ble(
                    mac,
                    name = "",
                    facts = RadioFacts(
                        serviceData = listOf(ServiceDataRecord("FE2C", "00112233445566778899AABBCCDDEEFF")),
                    ),
                ),
            ),
            fleets,
            30,
        )
        val again = store.find("BLE:$mac")!!
        assertTrue("pairing-mode stays this session", again.fastPairPairing)
        assertTrue("fleet-fast-pair" in again.fleetIds)
    }

    @Test
    fun remoteIdLocationSticksAfterBasicIdPacket() {
        val store = DeviceStore()
        val mac = "AA:BB:CC:DD:EE:02"
        val location = "0D0012200000000084D717007FE4D3000098083408000000000000"
        val basic = "0D000212" + "5445535453455249414C31323334353637383930" + "000000"
        store.ingestBatch(
            listOf(ble(mac, name = "", facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", location))))),
            fleets,
            30,
        )
        val first = store.find("BLE:$mac")!!
        assertTrue("fleet-remote-id" in first.fleetIds)
        assertEquals(40.0, first.payloadLat!!, 1e-6)
        assertEquals(-74.0, first.payloadLon!!, 1e-6)
        assertEquals(100.0, first.payloadAlt!!, 1e-6)

        store.ingestBatch(
            listOf(ble(mac, name = "", facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", basic))))),
            fleets,
            30,
        )
        val again = store.find("BLE:$mac")!!
        assertTrue("fleet-remote-id" in again.fleetIds)
        assertEquals("Basic ID packet keeps last Location pin", 40.0, again.payloadLat!!, 1e-6)
        assertEquals(-74.0, again.payloadLon!!, 1e-6)
        assertEquals(100.0, again.payloadAlt!!, 1e-6)
        assertEquals("TESTSERIAL1234567890", again.payloadUasId)
    }

    @Test
    fun remoteIdUasIdSticksAfterLocationPacket() {
        val store = DeviceStore()
        val mac = "AA:BB:CC:DD:EE:03"
        val location = "0D0012200000000084D717007FE4D3000098083408000000000000"
        val basic = "0D000212" + "5445535453455249414C31323334353637383930" + "000000"
        store.ingestBatch(
            listOf(ble(mac, name = "", facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", basic))))),
            fleets,
            30,
        )
        val first = store.find("BLE:$mac")!!
        assertEquals("TESTSERIAL1234567890", first.payloadUasId)
        store.ingestBatch(
            listOf(ble(mac, name = "", facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", location))))),
            fleets,
            30,
        )
        val again = store.find("BLE:$mac")!!
        assertEquals("Location packet keeps Basic ID", "TESTSERIAL1234567890", again.payloadUasId)
        assertEquals(40.0, again.payloadLat!!, 1e-6)
        assertEquals(-74.0, again.payloadLon!!, 1e-6)
    }

    @Test
    fun radioHoldDoesNotResurrectAlreadyGone() {
        val now = 1_000_000L
        val linger = 15_000L
        val lastSeen = now - 120_000L
        assertFalse(
            "late Wi-Fi scan must not un-gone an aged AP",
            DeviceStore.stillHeard(
                kind = RadioKind.WIFI,
                lastSeen = lastSeen,
                alreadyGone = true,
                now = now,
                lingerMs = linger,
                wifiScanFresh = false,
                wifiHold = true,
                bleHold = false,
            ),
        )
        assertTrue(
            "a live AP still waits for the next Wi-Fi scan",
            DeviceStore.stillHeard(
                kind = RadioKind.WIFI,
                lastSeen = lastSeen,
                alreadyGone = false,
                now = now,
                lingerMs = linger,
                wifiScanFresh = false,
                wifiHold = false,
                bleHold = false,
            ),
        )
        assertFalse(
            "BLE restart must not un-gone an aged advertiser",
            DeviceStore.stillHeard(
                kind = RadioKind.BLE,
                lastSeen = lastSeen,
                alreadyGone = true,
                now = now,
                lingerMs = linger,
                wifiScanFresh = true,
                wifiHold = false,
                bleHold = true,
            ),
        )
        assertTrue(
            "heard inside linger stays",
            DeviceStore.stillHeard(
                kind = RadioKind.BLE,
                lastSeen = now - 1_000L,
                alreadyGone = false,
                now = now,
                lingerMs = linger,
                wifiScanFresh = true,
                wifiHold = false,
                bleHold = false,
            ),
        )
    }

    @Test
    fun rssi127DoesNotBecomeMaxOrHistory() {
        val store = DeviceStore()
        val mac = "E0:9D:13:6E:71:03"
        store.ingestBatch(listOf(ble(mac, name = "SmartTag", rssi = -92)), fleets, 30)
        store.ingestBatch(listOf(ble(mac, name = "SmartTag", rssi = 127)), fleets, 30)
        val device = store.find("BLE:$mac")!!
        assertEquals(-92, device.rssi)
        assertEquals(-92, device.rssiMin)
        assertEquals(-92, device.rssiMax)
        assertTrue(device.rssiHistory.none { it.rssi == 127 })
        store.ingestBatch(listOf(ble(mac, name = "SmartTag", rssi = -86)), fleets, 30)
        val louder = store.find("BLE:$mac")!!
        assertEquals(-86, louder.rssi)
        assertEquals(-92, louder.rssiMin)
        assertEquals(-86, louder.rssiMax)
    }

    @Test
    fun refreshEmitsWithoutDroppingTags() {
        val store = DeviceStore()
        val mac = "00:00:0C:11:22:33"
        store.ingestBatch(listOf(wifi(mac, name = "Campus")), fleets, 30)
        store.refresh(fleets, 30)
        val published = store.devices.value.first { it.mac == mac }
        assertTrue("fleet-cisco" in published.fleetIds)
    }

    private fun wifi(
        mac: String,
        name: String = "",
        rssi: Int = -50,
        ies: List<String> = emptyList(),
    ) = Observation(
        kind = RadioKind.WIFI,
        mac = mac,
        name = name,
        rssi = rssi,
        channel = 1,
        frequencyMhz = 2412,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        at = System.currentTimeMillis(),
        vendorIeOuis = ies,
    )

    private fun ble(
        mac: String,
        name: String,
        facts: RadioFacts = RadioFacts.Empty,
        rssi: Int = -50,
    ) = Observation(
        kind = RadioKind.BLE,
        mac = mac,
        name = name,
        rssi = rssi,
        channel = 0,
        frequencyMhz = 0,
        hiddenSsid = false,
        serviceUuids = if (facts.serviceData.isEmpty()) emptyList() else facts.serviceData.map { it.uuid },
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        at = System.currentTimeMillis(),
        facts = facts,
    )
}
