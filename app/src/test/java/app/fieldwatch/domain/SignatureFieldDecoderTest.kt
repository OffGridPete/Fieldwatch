package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SignatureFieldDecoderTest {
    @Test
    fun ruuviRawV2TemperatureHumidity() {
        // Data Format 5 after company ID 0x0499. Example from Ruuvi docs.
        val payload = "0512FC5394C37C0004FFFC040CAC364200CDCBB8334C884F"
        val fleet = Fleet(
            id = "fleet-ruuvi",
            name = "Ruuvi",
            decode = FleetDecode(
                source = DecodeSource.MANUFACTURER_DATA,
                companyId = 0x0499,
                fields = listOf(
                    DecodeField(
                        id = "format",
                        label = "Format",
                        offset = 0,
                        type = DecodeType.U8,
                        gate = DecodeWhen(offset = 0, op = DecodeWhenOp.EQ, valueHex = "05"),
                    ),
                    DecodeField(
                        id = "temperature",
                        label = "Temperature",
                        offset = 1,
                        type = DecodeType.I16,
                        endian = DecodeEndian.BE,
                        scale = 0.005,
                        unit = "°C",
                    ),
                    DecodeField(
                        id = "humidity",
                        label = "Humidity",
                        offset = 3,
                        type = DecodeType.U16,
                        endian = DecodeEndian.BE,
                        scale = 0.0025,
                        unit = "%",
                    ),
                ),
            ),
        )
        val device = ble(0x0499, payload, fleet.id)
        val rows = SignatureFieldDecoder.decodeSighting(device, listOf(fleet))
        assertEquals("Format", "5", rows.first { it.id == "format" }.display)
        assertEquals("Temperature", "24.3 °C", rows.first { it.id == "temperature" }.display)
        assertEquals("Humidity", "53.49 %", rows.first { it.id == "humidity" }.display)
    }

    @Test
    fun scaleThenOffsetAdd() {
        val fleet = Fleet(
            id = "f",
            name = "P",
            decode = FleetDecode(
                source = DecodeSource.MANUFACTURER_DATA,
                fields = listOf(
                    DecodeField(
                        id = "pressure",
                        label = "Pressure",
                        offset = 0,
                        type = DecodeType.U16,
                        endian = DecodeEndian.BE,
                        scale = 1.0,
                        offsetAdd = 50000.0,
                        unit = "Pa",
                    ),
                ),
            ),
        )
        val device = ble(1, "C37C", fleet.id)
        val rows = SignatureFieldDecoder.decodeSighting(device, listOf(fleet))
        // 0xC37C = 50044; + 50000 = 100044
        assertEquals("100044 Pa", rows.single().display)
    }

    @Test
    fun whenMismatchSkipsField() {
        val fleet = Fleet(
            id = "f",
            name = "P",
            decode = FleetDecode(
                source = DecodeSource.MANUFACTURER_DATA,
                fields = listOf(
                    DecodeField(
                        id = "temp",
                        label = "Temperature",
                        offset = 1,
                        type = DecodeType.U8,
                        gate = DecodeWhen(offset = 0, op = DecodeWhenOp.EQ, valueHex = "05"),
                    ),
                ),
            ),
        )
        val device = ble(1, "0312", fleet.id)
        assertTrue(SignatureFieldDecoder.decodeSighting(device, listOf(fleet)).isEmpty())
    }

    @Test
    fun shortPayloadSkipsWithoutThrowing() {
        val fleet = Fleet(
            id = "f",
            name = "P",
            decode = FleetDecode(
                source = DecodeSource.MANUFACTURER_DATA,
                fields = listOf(
                    DecodeField(id = "wide", label = "Wide", offset = 0, type = DecodeType.U32),
                ),
            ),
        )
        val device = ble(1, "01", fleet.id)
        assertTrue(SignatureFieldDecoder.decodeSighting(device, listOf(fleet)).isEmpty())
    }

    @Test
    fun serviceDataUuidMatch() {
        val fleet = Fleet(
            id = "f",
            name = "S",
            decode = FleetDecode(
                source = DecodeSource.SERVICE_DATA,
                serviceUuid = "FEAA",
                fields = listOf(
                    DecodeField(id = "frame", label = "Frame", offset = 0, type = DecodeType.U8),
                ),
            ),
        )
        val device = Sighting(
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
            serviceUuids = listOf("FEAA"),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 1L,
            lastSeen = 1L,
            hitCount = 1,
            fleetIds = setOf(fleet.id),
            rssiHistory = emptyList(),
            presence = emptyList(),
            facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FEAA", "20AB"))),
        )
        val rows = SignatureFieldDecoder.decodeSighting(device, listOf(fleet))
        assertEquals("32", rows.single().display)
    }

    @Test
    fun wifiIsIgnored() {
        val fleet = Fleet(
            id = "f",
            name = "P",
            decode = FleetDecode(
                source = DecodeSource.MANUFACTURER_DATA,
                fields = listOf(DecodeField(id = "x", label = "X", offset = 0, type = DecodeType.U8)),
            ),
        )
        val ap = ble(1, "01", fleet.id).copy(kind = RadioKind.WIFI)
        assertTrue(SignatureFieldDecoder.decodeSighting(ap, listOf(fleet)).isEmpty())
    }

    @Test
    fun packRoundTripKeepsDecode() {
        val fleet = Fleet(
            id = "fleet-ruuvi",
            name = "Ruuvi",
            rules = listOf(MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x0499)),
            decode = FleetDecode(
                source = DecodeSource.MANUFACTURER_DATA,
                companyId = 0x0499,
                fields = listOf(
                    DecodeField(id = "temperature", label = "Temperature", offset = 1, type = DecodeType.I16, endian = DecodeEndian.BE, scale = 0.005, unit = "°C"),
                ),
            ),
        )
        val json = SignatureExchange.encode(SignatureExchange.pack(listOf(fleet), 0, "test", "now"))
        val parsed = SignatureExchange.parse(json).fleets.single()
        assertEquals("temperature", parsed.decode!!.fields.single().id)
        assertEquals(DecodeEndian.BE, parsed.decode!!.fields.single().endian)
        assertEquals(0.005, parsed.decode!!.fields.single().scale)
    }

    @Test
    fun importBackupAppliesIncomingDecodeOnSameId() {
        val stock = Fleet(
            id = "fleet-fitbit",
            name = "Fitbit",
            rules = listOf(MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x018E)),
        )
        val backup = stock.copy(
            decode = FleetDecode(
                source = DecodeSource.MANUFACTURER_DATA,
                companyId = 0x018E,
                fields = listOf(DecodeField(id = "status", label = "Status", offset = 0, type = DecodeType.U8)),
            ),
        )
        val (_, result) = SignatureExchange.merge(listOf(stock), listOf(backup))
        assertEquals(1, result.merged)
        val merged = SignatureExchange.merge(listOf(stock), listOf(backup)).first.single()
        assertEquals("status", merged.decode!!.fields.single().id)
    }

    @Test
    fun wifiOnlySignatureHasNoDecodeEditor() {
        val ap = Fleet(
            id = "fleet-unifi",
            name = "UniFi AP",
            rules = listOf(MatchRule(RuleKind.NAME_CONTAINS, text = "UniFi", radio = RadioKind.WIFI)),
        )
        assertTrue(!ap.canHaveBleDecode())
        val ble = Fleet(
            id = "fleet-fitbit",
            name = "Fitbit",
            rules = listOf(MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x018E)),
        )
        assertTrue(ble.canHaveBleDecode())
    }

    @Test
    fun normalizeEnumKeysTreatHexAndDecimalAsSame() {
        assertEquals("5", normalizeEnumKey("0x05"))
        assertEquals("5", normalizeEnumKey("05"))
        assertEquals("5", normalizeEnumKey("5"))
        assertEquals("10", normalizeEnumKey("10"))
        val labels = normalizeEnumLabels(mapOf("0x02" to "Active", "1" to "Idle"))
        assertEquals("Active", labels!!["2"])
        assertEquals("Idle", labels["1"])
    }

    @Test
    fun normalizedWhenKeepsEvenHex() {
        val gate = normalizeGate(DecodeWhen(offset = 0, op = DecodeWhenOp.EQ, valueHex = "0x5"))
        assertEquals("05", gate!!.valueHex)
        assertEquals(1, gate.length)
    }

    @Test
    fun enumMapsRawValue() {
        val fleet = Fleet(
            id = "f",
            name = "P",
            decode = FleetDecode(
                source = DecodeSource.MANUFACTURER_DATA,
                fields = listOf(
                    DecodeField(
                        id = "mode",
                        label = "Mode",
                        offset = 0,
                        type = DecodeType.U8,
                        enumLabels = mapOf("1" to "idle", "2" to "active"),
                    ),
                ),
            ),
        )
        val device = ble(1, "02", fleet.id)
        assertEquals("active", SignatureFieldDecoder.decodeSighting(device, listOf(fleet)).single().display)
    }

    @Test
    fun catalogRuuviRawV2() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-ruuvi" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x0499, "0512FC5394C37C0004FFFC040CAC364200CDCBB8334C884F", fleet.id),
            listOf(fleet),
        )
        assertEquals("5", rows.display("format"))
        assertEquals("24.3 °C", rows.display("temperature"))
        assertEquals("53.49 %", rows.display("humidity"))
        assertEquals("1000.44 hPa", rows.display("pressure"))
        assertEquals("0.004 g", rows.display("acc_x"))
        assertEquals("-0.004 g", rows.display("acc_y"))
        assertEquals("1.036 g", rows.display("acc_z"))
        assertEquals("2977 mV", rows.display("battery"))
        assertEquals("4 dBm", rows.display("tx_power"))
        assertEquals("CB:B8:33:4C:88:4F", rows.display("mac"))
    }

    @Test
    fun catalogRemoteIdBasicId() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-remote-id" }
        val payload = "0D000212" + "5445535453455249414C31323334353637383930" + "000000"
        val rows = SignatureFieldDecoder.decodeSighting(bleService("FFFA", payload, fleet.id), listOf(fleet))
        assertEquals("Open Drone ID", rows.display("app"))
        assertEquals("Basic ID", rows.display("msg_type"))
        assertEquals("Serial (CTA-2063)", rows.display("id_type"))
        assertEquals("Helicopter / multirotor", rows.display("ua_type"))
        assertEquals("TESTSERIAL1234567890", rows.display("uas_id"))
    }

    @Test
    fun catalogRemoteIdLocation() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-remote-id" }
        // 40° N, 74° W, HAE 100 m, height 50 m (OpenDroneID packed, proto v2).
        val payload = "0D0012200000000084D717007FE4D3000098083408000000000000"
        val rows = SignatureFieldDecoder.decodeSighting(bleService("FFFA", payload, fleet.id), listOf(fleet))
        assertEquals("Location", rows.display("msg_type"))
        assertEquals("Airborne", rows.display("status"))
        assertEquals("40 °", rows.display("latitude"))
        assertEquals("-74 °", rows.display("longitude"))
        assertEquals("100 m", rows.display("alt_geo"))
        assertEquals("50 m", rows.display("height"))
        assertEquals(40.0, rows.number("latitude")!!, 1e-6)
        assertEquals(-74.0, rows.number("longitude")!!, 1e-6)
        assertEquals(100.0, rows.number("alt_geo")!!, 1e-6)
    }

    @Test
    fun catalogBlueMaestroV23() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-bluemaestro" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x0133, "1764000A000100E001F4", fleet.id),
            listOf(fleet),
        )
        assertEquals("23", rows.display("version"))
        assertEquals("100 %", rows.display("battery"))
        assertEquals("22.4 °C", rows.display("temperature"))
        assertEquals("50 %", rows.display("humidity"))
    }

    @Test
    fun catalogGoproHero12() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-gopro" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0xF202, "02073E000000000000000001", fleet.id),
            listOf(fleet),
        )
        assertEquals("2", rows.display("schema"))
        assertEquals("awake", rows.display("awake"))
        assertEquals("on", rows.display("wifi_ap"))
        assertEquals("yes", rows.display("pairing"))
        assertEquals("HERO12 Black", rows.display("model"))
    }

    @Test
    fun catalogOsmoAction3Model() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-osmo" }
        val rows = SignatureFieldDecoder.decodeSighting(ble(0x08AA, "1200", fleet.id), listOf(fleet))
        assertEquals("Osmo Action 3", rows.display("model"))
    }

    @Test
    fun catalogRuuviRawV1() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-ruuvi" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x0499, "03291A1ECE1EFC18F94202CA0B53", fleet.id),
            listOf(fleet),
        )
        assertEquals("3", rows.display("format"))
        assertEquals("20.5 %", rows.display("humidity"))
        assertEquals("1027.66 hPa", rows.display("pressure"))
        assertEquals("-1 g", rows.display("acc_x"))
        assertEquals("2899 mV", rows.display("battery"))
    }

    @Test
    fun catalogGoveeH5075Packed() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-govee" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0xEC88, "0003215D64", fleet.id),
            listOf(fleet),
        )
        assertEquals("20.5149 °C", rows.display("temperature"))
        assertEquals("14.9 %", rows.display("humidity"))
        assertEquals("100 %", rows.display("battery"))
    }

    @Test
    fun catalogGoveeH5074() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-govee" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0xEC88, "00580AE6116402", fleet.id),
            listOf(fleet),
        )
        assertEquals("26.48 °C", rows.display("temperature"))
        assertEquals("45.82 %", rows.display("humidity"))
        assertEquals("100 %", rows.display("battery"))
    }

    @Test
    fun decodedFractionsStayPeriodOnFrenchLocale() {
        val prev = Locale.getDefault()
        Locale.setDefault(Locale.FRANCE)
        try {
            catalogGoveeH5074()
        } finally {
            Locale.setDefault(prev)
        }
    }

    @Test
    fun catalogGoveeH5102Packed() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-govee" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x0001, "010103215D64", fleet.id),
            listOf(fleet),
        )
        assertEquals("20.5149 °C", rows.display("temperature"))
        assertEquals("14.9 %", rows.display("humidity"))
        assertEquals("100 %", rows.display("battery"))
    }

    @Test
    fun catalogGoveeH5102EightByte() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-govee" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x0001, "010103215D640000", fleet.id),
            listOf(fleet),
        )
        assertEquals("20.5149 °C", rows.display("temperature"))
        assertEquals("14.9 %", rows.display("humidity"))
        assertEquals("100 %", rows.display("battery"))
    }

    @Test
    fun catalogGoveeStripsIntelliRocksSuffix() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-govee" }
        val rocks = "INTELLI_ROCKS".encodeToByteArray().joinToString("") { "%02X".format(it) }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0xEC88, "0003215D64$rocks", fleet.id),
            listOf(fleet),
        )
        assertEquals("20.5149 °C", rows.display("temperature"))
        assertEquals("100 %", rows.display("battery"))
    }

    @Test
    fun catalogGoveeSkipsUnrelatedMakerRecord() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-govee" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x004C, "0215AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA01020304C5", fleet.id),
            listOf(fleet),
        )
        assertTrue(rows.isEmpty())
    }

    @Test
    fun catalogGoveePicksHygrometerAmongMakerRecords() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-govee" }
        val device = ble(0x004C, "0215AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA01020304C5", fleet.id).copy(
            facts = RadioFacts(
                mfgRecords = listOf(
                    MfgRecord(0x004C, "0215AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA01020304C5"),
                    MfgRecord(0xEC88, "0003215D64"),
                ),
            ),
        )
        val rows = SignatureFieldDecoder.decodeSighting(device, listOf(fleet))
        assertEquals("20.5149 °C", rows.display("temperature"))
    }

    @Test
    fun catalogKontaktLocation() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-kontakt" }
        val rows = SignatureFieldDecoder.decodeSighting(
            bleService("FE6A", "0764F4250A00", fleet.id),
            listOf(fleet),
        )
        assertEquals("Location", rows.display("kind"))
        assertEquals("100 %", rows.display("battery"))
        assertEquals("still", rows.display("moving"))
    }

    @Test
    fun catalogNestWeaveProtect2() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-nest-weave" }
        val rows = SignatureFieldDecoder.decodeSighting(
            bleService("FEAF", "0900", fleet.id),
            listOf(fleet),
        )
        assertEquals("Nest Protect (2nd gen)", rows.display("product"))
    }

    @Test
    fun catalogNestWeaveIdentificationBlock() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-nest-weave" }
        // OpenWeave WeaveBLEDeviceIdentificationInfo: len 0x10, type 0x01,
        // v0.1, vendor 0x235A, product 0x0009 Protect 2nd gen, device id, paired.
        val payload = "100100015A230900010203040506070801"
        val rows = SignatureFieldDecoder.decodeSighting(
            bleService("FEAF", payload, fleet.id),
            listOf(fleet),
        )
        assertEquals("Nest Labs", rows.display("vendor"))
        assertEquals("Nest Protect (2nd gen)", rows.display("product"))
        assertEquals("01 02 03 04 05 06 07 08", rows.display("device_id"))
        assertEquals("paired", rows.display("pairing"))
        assertTrue(rows.none { it.id == "product" && it.display.contains("272") })
    }

    @Test
    fun catalogTuyaBound() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-tuya" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x07D0, "8004", fleet.id),
            listOf(fleet),
        )
        assertEquals("bound", rows.display("bound"))
        assertEquals("4", rows.display("protocol"))
    }

    @Test
    fun catalogTilePrivateId() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-tile" }
        val rows = SignatureFieldDecoder.decodeSighting(
            bleService("FEED", "0102030405060708", fleet.id),
            listOf(fleet),
        )
        assertEquals("01 02 03 04 05 06 07 08", rows.display("private_id"))
    }

    @Test
    fun catalogEstimoteTelemetryFrame() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-estimote" }
        val rows = SignatureFieldDecoder.decodeSighting(ble(0x015D, "02ABCD", fleet.id), listOf(fleet))
        assertEquals("Telemetry", rows.display("frame"))
    }

    @Test
    fun catalogHasDecodeOnPublishedLayoutsOnly() {
        val byId = DefaultCatalog.fleets().associateBy { it.id }
        assertTrue(byId.getValue("fleet-ruuvi").decode != null)
        assertTrue(byId.getValue("fleet-remote-id").decode != null)
        assertTrue(byId.getValue("fleet-bluemaestro").decode != null)
        assertTrue(byId.getValue("fleet-gopro").decode != null)
        assertTrue(byId.getValue("fleet-osmo").decode != null)
        assertTrue(byId.getValue("fleet-dji").decode != null)
        assertTrue(byId.getValue("fleet-govee").decode != null)
        assertTrue(byId.getValue("fleet-kontakt").decode != null)
        assertTrue(byId.getValue("fleet-estimote").decode != null)
        assertTrue(byId.getValue("fleet-nest-weave").decode != null)
        assertTrue(byId.getValue("fleet-tuya").decode != null)
        assertTrue(byId.getValue("fleet-tile").decode != null)
        assertTrue(byId.getValue("fleet-fitbit").decode == null)
        assertTrue(byId.getValue("fleet-airtag").decode == null)
    }

    private fun List<DecodedFieldValue>.display(id: String): String =
        first { it.id == id }.display

    private fun List<DecodedFieldValue>.number(id: String): Double? =
        first { it.id == id }.number

    private fun bleService(uuid: String, dataHex: String, fleetId: String) = Sighting(
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
        serviceUuids = listOf(uuid),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = setOf(fleetId),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(serviceData = listOf(ServiceDataRecord(uuid, dataHex))),
    )

    private fun ble(companyId: Int, dataHex: String, fleetId: String) = Sighting(
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
        serviceUuids = emptyList(),
        manufacturerId = companyId,
        manufacturerDataHex = dataHex,
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = setOf(fleetId),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(mfgRecords = listOf(MfgRecord(companyId, dataHex))),
    )
}
