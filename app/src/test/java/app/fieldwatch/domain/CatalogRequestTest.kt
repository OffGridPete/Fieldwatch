package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class CatalogRequestTest {
    @Test
    fun twoBluetoothAddressesWithAFactoryNameCanOpenARequest() {
        val a = radio("AA:BB:CC:11:22:33", "UV-PRO")
        val b = radio("AA:BB:CC:44:55:66", "uv-pro", company = 0x1234, uuids = listOf("0000FD8E-0000-1000-8000-00805F9B34FB", "0000180A-0000-1000-8000-00805F9B34FB"))
        val peers = listOf(a, b)
        assertTrue(CatalogRequest.eligible(a, peers))
        val url = CatalogRequest.issueUrl(a, peers)!!
        val body = bodyOf(url)
        assertTrue(body.contains("Name: UV-PRO"))
        assertTrue(body.contains("Addresses: 2"))
        assertTrue(body.contains("Vendor prefix: AA:BB:CC"))
        assertTrue(body.contains("Company: 0x1234"))
        assertTrue(body.contains("FD8E"))
        assertFalse(body.contains("180A"))
        assertFalse(body.contains("11:22:33"))
        assertFalse(body.contains("44:55:66"))
        assertFalse(url.contains("11:22:33"))
    }

    @Test
    fun oneAddressStaysQuiet() {
        val a = radio("AA:BB:CC:11:22:33", "UV-PRO", hits = 40)
        assertFalse(CatalogRequest.eligible(a, listOf(a)))
        assertNull(CatalogRequest.issueUrl(a, listOf(a)))
    }

    @Test
    fun wifiAMatchedRadioAndAPersonalNameStayQuiet() {
        val named = radio("AA:BB:CC:11:22:33", "UV-PRO")
        val second = radio("AA:BB:CC:44:55:66", "UV-PRO")
        val wifi = named.copy(kind = RadioKind.WIFI, key = "WIFI:1")
        assertFalse(CatalogRequest.eligible(wifi, listOf(wifi, second.copy(kind = RadioKind.WIFI))))
        assertFalse(CatalogRequest.eligible(named.copy(fleetIds = setOf("fleet-icom-id52")), listOf(named, second)))
        val phone = radio("AA:BB:CC:11:22:33", "iPhone")
        val phone2 = radio("AA:BB:CC:44:55:66", "iPhone")
        assertFalse(CatalogRequest.eligible(phone, listOf(phone, phone2)))
        val desktop = radio("AA:BB:CC:11:22:33", "DESKTOP-AB12")
        val desktop2 = radio("AA:BB:CC:44:55:66", "DESKTOP-AB12")
        assertFalse(CatalogRequest.eligible(desktop, listOf(desktop, desktop2)))
        val module = radio("AA:BB:CC:11:22:33", "ET25SE_BLE_A1B2C3")
        val module2 = radio("AA:BB:CC:44:55:66", "ET25SE_BLE_A1B2C3")
        assertFalse(CatalogRequest.eligible(module, listOf(module, module2)))
    }

    @Test
    fun aRandomizedAddressLeavesThePrefixOut() {
        val a = radio("DE:AD:BE:11:22:33", "SSM-BT10", random = true)
        val b = radio("DE:AD:BE:44:55:66", "SSM-BT10", random = true)
        val body = bodyOf(CatalogRequest.issueUrl(a, listOf(a, b))!!)
        assertFalse(body.contains("Vendor prefix"))
        assertFalse(body.contains("11:22:33"))
        assertTrue(body.contains("Name: SSM-BT10"))
    }

    private fun bodyOf(url: String): String {
        val raw = url.substringAfter("body=")
        return URLDecoder.decode(raw, Charsets.UTF_8)
    }

    private fun radio(
        mac: String,
        name: String,
        hits: Int = 1,
        company: Int? = null,
        uuids: List<String> = emptyList(),
        random: Boolean = false,
    ) = Sighting(
        key = "BLE:$mac",
        kind = RadioKind.BLE,
        mac = mac,
        name = name,
        rssi = -50,
        rssiMin = -50,
        rssiMax = -50,
        channel = 0,
        frequencyMhz = 2402,
        vendor = null,
        randomized = random,
        hiddenSsid = false,
        serviceUuids = uuids,
        manufacturerId = company,
        manufacturerDataHex = "",
        rawHex = "001122",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = hits,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        latitude = 39.0,
        longitude = -104.0,
    )
}
