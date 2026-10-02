package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StockCatalogFileTest {
    @Test
    fun distPackMatchesDefaultCatalog() {
        val dest = distPackFile()
        val encoded = SignatureExchange.encode(
            SignatureExchange.pack(
                fleets = DefaultCatalog.fleets(),
                catalogVersion = ConfigStore.CATALOG_VERSION,
                appVersion = "stock",
                exportedAt = "",
            ),
        )
        if (System.getenv("WRITE_STOCK_CATALOG") == "1") {
            dest.parentFile?.mkdirs()
            dest.writeText(encoded)
        }
        assertTrue("Missing ${dest.absolutePath}. Run tests with WRITE_STOCK_CATALOG=1.", dest.isFile)
        val pack = SignatureExchange.parse(dest.readText())
        assertEquals("fieldwatch-signatures", pack.format)
        assertEquals(ConfigStore.CATALOG_VERSION, pack.catalogVersion)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        assertEquals(stock.keys, pack.fleets.map { it.id }.toSet())
        for (row in pack.fleets) {
            val expect = stock.getValue(row.id)
            assertEquals(row.id, expect.name, row.name)
            assertEquals(row.id, expect.kind, row.kind)
            assertEquals(row.id, expect.attentionNote, row.attentionNote)
            assertEquals(row.id, expect.notes, row.notes)
            assertEquals(row.id, expect.matchAny, row.matchAny)
            assertTrue(row.builtIn)
        }
        val ray = pack.fleets.single { it.id == "fleet-rayneo" }
        assertFalse(ray.matchAny)
        assertEquals(0x0BC6, ray.rules.single { it.kind == RuleKind.MANUFACTURER_ID }.companyId)
        assertEquals("RayNeo*", ray.rules.single { it.kind == RuleKind.NAME_GLOB }.text)
        assertEquals(2, ray.rules.size)
        val even = pack.fleets.single { it.id == "fleet-even-g1" }
        assertTrue(even.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x10F9 })
        val lite = pack.fleets.single { it.id == "fleet-liteon-camera-radio" }
        val ouis = lite.rules.filter { it.kind == RuleKind.OUI }.map { it.text.uppercase() }.toSet()
        assertTrue(ouis.contains("E0:0A:F6"))
        assertTrue(ouis.contains("14:B5:CD"))
        assertTrue(lite.attentionNote.isBlank())
        val remote = pack.fleets.single { it.id == "fleet-remote-id" }
        assertTrue(remote.rules.any { it.kind == RuleKind.VENDOR_IE_OUI && it.text.equals("6A:5C:35", true) })
        assertTrue(remote.rules.any { it.kind == RuleKind.VENDOR_IE_OUI && it.text.equals("FA:0B:BC", true) })
        val tello = pack.fleets.single { it.id == "fleet-tello" }
        assertEquals(
            setOf("TELLO*", "RMTT*"),
            tello.rules.map { it.text }.toSet(),
        )
        val crazy = pack.fleets.single { it.id == "fleet-crazyflie" }
        assertFalse(crazy.rules.any { it.kind == RuleKind.MANUFACTURER_ID })
        assertTrue(pack.fleets.single { it.id == "fleet-parrot" }.rules.any {
            it.kind == RuleKind.NAME_GLOB && it.text == "Skycontroller*"
        })
    }

    private fun distPackFile(): File {
        val cwd = File(System.getProperty("user.dir")!!)
        val candidates = listOf(
            File(cwd, "dist/fieldwatch-signatures-v2.json"),
            File(cwd.parentFile, "dist/fieldwatch-signatures-v2.json"),
        )
        return candidates.first { it.parentFile?.exists() == true }
    }

    @Test
    fun remoteIdPinFieldsMatchWhenLiveKeysAreDropped() {
        val raw = distPackFile().readText()
        val full = SignatureExchange.parse(raw).fleets.single { it.id == "fleet-remote-id" }
        val older = SignatureExchange.parse(dropLiveKeys(raw)).fleets.single { it.id == "fleet-remote-id" }
        assertEquals(
            full.decode!!.fields.map { it.copy(live = false, liveEmphasis = emptyList(), enumNotes = null) },
            older.decode!!.fields,
        )
        val location = "0D0012200000000084D717007FE4D3000098083408000000000000"
        val ble = ridBle(location)
        val pinned = PayloadLocation.applySticky(ble.copy(fleetIds = setOf(full.id)), listOf(full))
        assertEquals(40.0, pinned.payloadLat!!, 1e-6)
        assertEquals(-74.0, pinned.payloadLon!!, 1e-6)
        assertEquals(0.0, pinned.payloadHeading!!, 1e-6)
        assertEquals(0.0, pinned.payloadSpeed!!, 1e-6)
        val fullRows = SignatureFieldDecoder.decodeSighting(ble.copy(fleetIds = setOf(full.id)), listOf(full))
        val oldRows = SignatureFieldDecoder.decodeSighting(ble.copy(fleetIds = setOf(older.id)), listOf(older))
        for (id in listOf("latitude", "longitude", "heading", "hspeed", "alt_geo", "status")) {
            assertEquals(id, row(fullRows, id).display, row(oldRows, id).display)
            assertEquals(id, row(fullRows, id).number, row(oldRows, id).number)
        }
        val pack = "D9F2190302123135383146335954444A3144303033315A353330000000" +
            "1220820A00864228110CFF80CF0000F508B2083A022E310A00" +
            "420176E42711B5FF81CF010000000000000005088B02900E00"
        val wifiPin = PayloadLocation.applySticky(ridWifi(pack).copy(fleetIds = setOf(full.id)), listOf(full))
        assertEquals("1581F3YTDJ1D0031Z530", wifiPin.payloadUasId)
        assertEquals(28.7851142, wifiPin.payloadLat!!, 1e-6)
        assertEquals(-81.3629684, wifiPin.payloadLon!!, 1e-6)
        assertEquals(130.0, wifiPin.payloadHeading!!, 1e-6)
        assertEquals(2.5, wifiPin.payloadSpeed!!, 1e-6)
        assertEquals(28.7827062, wifiPin.payloadOpLat!!, 1e-6)
        assertEquals(-81.3563979, wifiPin.payloadOpLon!!, 1e-6)
    }

    private fun row(rows: List<DecodedFieldValue>, id: String) = rows.first { it.id == id }

    private fun dropLiveKeys(raw: String): String {
        fun strip(el: JsonElement): JsonElement = when (el) {
            is JsonObject -> JsonObject(
                el.filterKeys { it != "live" && it != "liveEmphasis" && it != "enumNotes" }
                    .mapValues { (_, value) -> strip(value) },
            )
            is JsonArray -> JsonArray(el.map { strip(it) })
            else -> el
        }
        return strip(Json.parseToJsonElement(raw)).toString()
    }

    private fun ridBle(dataHex: String) = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:01",
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:01",
        name = "",
        rssi = -50,
        rssiMin = -50,
        rssiMax = -50,
        channel = 0,
        frequencyMhz = 2402,
        vendor = null,
        randomized = true,
        hiddenSsid = false,
        serviceUuids = listOf("FFFA"),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = setOf("fleet-remote-id"),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FFFA", dataHex))),
    )

    private fun ridWifi(dataHex: String) = Sighting(
        key = "WIFI:60:60:1F:06:31:08",
        kind = RadioKind.WIFI,
        mac = "60:60:1F:06:31:08",
        name = "RID",
        rssi = -80,
        rssiMin = -80,
        rssiMax = -80,
        channel = 6,
        frequencyMhz = 2437,
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
        fleetIds = setOf("fleet-remote-id"),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(vendorIes = listOf(VendorIeRecord("FA:0B:BC", 0x0D, dataHex))),
    )

    @Test
    fun legacyPackStaysOnCatalog77() {
        val cwd = File(System.getProperty("user.dir")!!)
        val legacy = listOf(
            File(cwd, "dist/fieldwatch-signatures.json"),
            File(cwd.parentFile, "dist/fieldwatch-signatures.json"),
        ).first { it.parentFile?.exists() == true }
        assertTrue("Keep dist/fieldwatch-signatures.json for 1.1.11 GitHub updates.", legacy.isFile)
        val pack = SignatureExchange.parse(legacy.readText())
        assertEquals(77, pack.catalogVersion)
        assertFalse(pack.fleets.any { fleet ->
            fleet.rules.any { it.kind == RuleKind.SERVICE_DATA }
        })
    }
}
