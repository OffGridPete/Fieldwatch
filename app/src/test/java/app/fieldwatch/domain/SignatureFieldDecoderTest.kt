package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun packRoundTripKeepsIncludeCompanyId() {
        val fleet = Fleet(
            id = "fleet-sytpms",
            name = "SYTPMS",
            decode = FleetDecode(
                source = DecodeSource.MANUFACTURER_DATA,
                includeCompanyId = true,
                fields = listOf(DecodeField(id = "temp", label = "Temperature", offset = 2, type = DecodeType.U8)),
            ),
        )
        val json = SignatureExchange.encode(SignatureExchange.pack(listOf(fleet), 0, "test", "now"))
        val parsed = SignatureExchange.parse(json).fleets.single()
        assertTrue(parsed.decode!!.includeCompanyId)
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
    fun normalizedWhenTwoByteHexSetsLength() {
        val gate = normalizeGate(DecodeWhen(offset = 10, op = DecodeWhenOp.EQ, valueHex = "544E"))
        assertEquals("544E", gate!!.valueHex)
        assertEquals(2, gate.length)
        assertEquals(10, gate.offset)
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
    fun catalogPenguinTnSerial() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-penguin" }
        // Ryan O'Horo SCAN_RSP payload after company 0x09C8.
        val payload = "D8A0D89F4A5E2030502A544E3732303233303232303030373731"
        val rows = SignatureFieldDecoder.decodeSighting(ble(0x09C8, payload, fleet.id), listOf(fleet))
        assertEquals("D8:A0:D8:9F:4A:5E", rows.display("adv_mac"))
        assertEquals("TN72023022000771", rows.display("serial"))
    }

    @Test
    fun catalogPenguinWithoutTnSkipsDecode() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-penguin" }
        val payload = "D8A0D89F4A5E2030502A00003732303233303232303030373731"
        val rows = SignatureFieldDecoder.decodeSighting(ble(0x09C8, payload, fleet.id), listOf(fleet))
        assertTrue(rows.isEmpty())
    }

    @Test
    fun eqGateUsesHexByteLength() {
        val fleet = Fleet(
            id = "f",
            name = "P",
            decode = FleetDecode(
                source = DecodeSource.MANUFACTURER_DATA,
                fields = listOf(
                    DecodeField(
                        id = "tag",
                        label = "Tag",
                        offset = 0,
                        length = 2,
                        type = DecodeType.UTF8,
                        gate = DecodeWhen(offset = 0, op = DecodeWhenOp.EQ, valueHex = "544E"),
                    ),
                ),
            ),
        )
        val rows = SignatureFieldDecoder.decodeSighting(ble(1, "544E3132", fleet.id), listOf(fleet))
        assertEquals("TN", rows.display("tag"))
    }

    @Test
    fun catalogRemoteIdLocation() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-remote-id" }
        // 40° N, 74° W, HAE 100 m, height 50 m (OpenDroneID packed, proto v2).
        val payload = "0D0012200000000084D717007FE4D3000098083408000000000000"
        val rows = SignatureFieldDecoder.decodeSighting(bleService("FFFA", payload, fleet.id), listOf(fleet))
        assertEquals("Location", rows.display("msg_type"))
        assertEquals("Airborne", rows.display("status"))
        val airborne = SignatureFieldDecoder.liveChips(bleService("FFFA", payload, fleet.id), listOf(fleet))
        assertEquals("Airborne", airborne.single().text)
        assertFalse(airborne.single().emphasis)
        val emergency = "0D001230" + payload.removePrefix("0D001220")
        val strong = SignatureFieldDecoder.liveChips(bleService("FFFA", emergency, fleet.id), listOf(fleet))
        assertEquals("Emergency", strong.single().text)
        assertTrue(strong.single().emphasis)
        val basicId = "0D000220" + payload.removePrefix("0D001220")
        assertTrue(SignatureFieldDecoder.liveChips(bleService("FFFA", basicId, fleet.id), listOf(fleet)).isEmpty())
        assertEquals("40 °", rows.display("latitude"))
        assertEquals("-74 °", rows.display("longitude"))
        assertEquals("100 m", rows.display("alt_geo"))
        assertEquals("50 m", rows.display("height"))
        assertEquals("0 °", rows.display("heading"))
        assertEquals("0 m/s", rows.display("hspeed"))
        assertEquals(40.0, rows.number("latitude")!!, 1e-6)
        assertEquals(-74.0, rows.number("longitude")!!, 1e-6)
        assertEquals(100.0, rows.number("alt_geo")!!, 1e-6)
        assertEquals(0.0, rows.number("heading")!!, 1e-6)
        assertEquals(0.0, rows.number("hspeed")!!, 1e-6)
    }

    @Test
    fun catalogRemoteIdWifiMessagePackUsesBleMap() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-remote-id" }
        val hex = "D9F2190302123135383146335954444A3144303033315A353330000000" +
            "1220820A00864228110CFF80CF0000F508B2083A022E310A00" +
            "420176E42711B5FF81CF010000000000000005088B02900E00"
        val rows = SignatureFieldDecoder.decodeSighting(wifiRid(hex, fleet.id), listOf(fleet))
        assertEquals("1581F3YTDJ1D0031Z530", rows.display("uas_id"))
        assertEquals("Helicopter / multirotor", rows.display("ua_type"))
        assertEquals("Airborne", rows.display("status"))
        assertEquals(28.7851142, rows.number("latitude")!!, 1e-6)
        assertEquals(-81.3629684, rows.number("longitude")!!, 1e-6)
        assertEquals(130.0, rows.number("heading")!!, 1e-6)
        assertEquals(2.5, rows.number("hspeed")!!, 1e-6)
        assertEquals(28.7827062, rows.number("op_lat")!!, 1e-6)
        assertEquals(-81.3563979, rows.number("op_lon")!!, 1e-6)
    }

    @Test
    fun catalogRemoteIdLocationWestHeadingAndSpeed() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-remote-id" }
        // Airborne, EWDirection, direction 90 → 270°. Speed 40 × 0.25 = 10 m/s.
        val payload = "0D0012225A28000084D717007FE4D3000098083408000000000000"
        val rows = SignatureFieldDecoder.decodeSighting(bleService("FFFA", payload, fleet.id), listOf(fleet))
        assertEquals("Location", rows.display("msg_type"))
        assertEquals("270 °", rows.display("heading"))
        assertEquals("10 m/s", rows.display("hspeed"))
        assertEquals(270.0, rows.number("heading")!!, 1e-6)
        assertEquals(10.0, rows.number("hspeed")!!, 1e-6)
        assertEquals(1, rows.count { it.id == "heading" })
        assertEquals(1, rows.count { it.id == "hspeed" })
    }

    @Test
    fun catalogRemoteIdLocationHighSpeedMultiplier() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-remote-id" }
        // SpeedMult set, SpeedHorizontal 4 → 4×0.75 + 255×0.25 = 66.75 m/s.
        val payload = "0D0012210004000084D717007FE4D3000098083408000000000000"
        val rows = SignatureFieldDecoder.decodeSighting(bleService("FFFA", payload, fleet.id), listOf(fleet))
        assertEquals(66.75, rows.number("hspeed")!!, 1e-6)
        assertEquals(0.0, rows.number("heading")!!, 1e-6)
    }

    @Test
    fun catalogFindHubSeparatedMode() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-find-hub" }
        val eid = "11".repeat(20)
        val rows = SignatureFieldDecoder.decodeSighting(
            bleService("FEAA", "41$eid" + "00", fleet.id),
            listOf(fleet),
        )
        assertEquals("separated", rows.display("mode"))
        assertEquals("11 11 11 11 11 11 11 11 11 11 11 11 11 11 11 11 11 11 11 11", rows.display("eid"))
        val strong = SignatureFieldDecoder.liveChips(
            bleService("FEAA", "41$eid" + "00", fleet.id),
            listOf(fleet),
        )
        assertEquals("separated", strong.single().text)
        assertTrue(strong.single().emphasis)
        assertTrue(strong.single().note.contains("about a day"))
        val quiet = SignatureFieldDecoder.liveChips(
            bleService("FEAA", "40$eid" + "00", fleet.id),
            listOf(fleet),
        )
        assertEquals("nearby", quiet.single().text)
        assertFalse(quiet.single().emphasis)
        assertTrue(quiet.single().note.contains("own tag"))
        assertTrue(quiet.single().note.contains("joined"))
    }

    @Test
    fun catalogDultSeparatedAndNearOwner() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-dult" }
        val separated = SignatureFieldDecoder.decodeSighting(
            bleService("FCB2", "0100", fleet.id),
            listOf(fleet),
        )
        assertEquals("1", separated.display("network_id"))
        assertEquals("separated", separated.display("mode"))
        val separatedDevice = bleService("FCB2", "0100", fleet.id)
        val strong = SignatureFieldDecoder.liveChips(separatedDevice, listOf(fleet))
        assertEquals("separated", strong.single().text)
        assertTrue(strong.single().emphasis)
        assertTrue(strong.single().note.contains("about a day"))
        val near = SignatureFieldDecoder.decodeSighting(
            bleService("FCB2", "0201", fleet.id),
            listOf(fleet),
        )
        assertEquals("2", near.display("network_id"))
        assertEquals("near owner", near.display("mode"))
        val quiet = SignatureFieldDecoder.liveChips(bleService("FCB2", "0201", fleet.id), listOf(fleet))
        assertEquals("near owner", quiet.single().text)
        assertFalse(quiet.single().emphasis)
        assertTrue(quiet.single().note.contains("own tag"))
        assertTrue(quiet.single().note.contains("joined"))
    }

    @Test
    fun liveFlagRoundTripsAndOlderPacksOmitIt() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-dult" }
        val encoded = SignatureExchange.encode(
            SignatureExchange.pack(listOf(fleet), catalogVersion = 85, appVersion = "t", exportedAt = ""),
        )
        val mode = SignatureExchange.parse(encoded).fleets.single().decode!!.fields.single { it.id == "mode" }
        assertTrue(mode.live)
        assertEquals(listOf("0"), mode.liveEmphasis)
        assertTrue(mode.enumNotes?.get("0")?.contains("about a day") == true)
        assertTrue(mode.enumNotes?.get("1")?.contains("own tag") == true)
        val older = """
            {"format":"fieldwatch-signatures","formatVersion":1,"catalogVersion":1,"fleets":[
              {"id":"fleet-x","name":"X","rules":[{"kind":"SERVICE_DATA","text":"FCB2"}],
               "decode":{"source":"serviceData","serviceUuid":"FCB2","fields":[
                 {"id":"mode","label":"Mode","offset":1,"type":"bits","bitOffset":0,"bitWidth":1,
                  "enum":{"0":"separated","1":"near owner"}}
               ]}}
            ]}
        """.trimIndent()
        val parsed = SignatureExchange.parse(older).fleets.single().decode!!.fields.single()
        assertFalse(parsed.live)
        assertTrue(parsed.liveEmphasis.isEmpty())
        assertEquals(null, parsed.enumNotes)
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
    fun catalogDjiPower2000ModelLabel() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-dji-power" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x08AA, "941110E4B063D0AA76", fleet.id),
            listOf(fleet),
        )
        assertEquals("Power 2000", rows.display("model"))
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
        assertTrue(byId.getValue("fleet-dji-power").decode != null)
        assertTrue(byId.getValue("fleet-govee").decode != null)
        assertTrue(byId.getValue("fleet-kontakt").decode != null)
        assertTrue(byId.getValue("fleet-estimote").decode != null)
        assertTrue(byId.getValue("fleet-nest-weave").decode != null)
        assertTrue(byId.getValue("fleet-tuya").decode != null)
        assertTrue(byId.getValue("fleet-tile").decode != null)
        assertTrue(byId.getValue("fleet-fitbit").decode == null)
        assertTrue(byId.getValue("fleet-airtag").decode == null)
        assertTrue(byId.getValue("fleet-tpms-ble").decode != null)
        assertTrue(byId.getValue("fleet-sytpms").decode != null)
        assertTrue(byId.getValue("fleet-tesla-tstpms").decode != null)
        assertTrue(byId.getValue("fleet-goodyear").decode == null)
        assertTrue(byId.getValue("fleet-fobo").decode == null)
        assertTrue(byId.getValue("fleet-tirecheck").decode == null)
    }

    @Test
    fun catalogAftermarketTpmsPressureTempBattery() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-tpms-ble" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x0001, "80EACA108A78E36D0000E60A00005B00", fleet.id),
            listOf(fleet),
        )
        assertEquals("1", rows.display("wheel"))
        assertEquals("EA CA 10 8A 78", rows.display("sensor_id"))
        assertEquals("28.131 kPa", rows.display("pressure"))
        assertEquals("27.9 °C", rows.display("temperature"))
        assertEquals("91 %", rows.display("battery"))
        assertEquals("ok", rows.display("alarm"))
    }

    @Test
    fun catalogSytpmsIncludesCompanyIdBytes() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-sytpms" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x1E28, "1401558536", fleet.id),
            listOf(fleet),
        )
        assertEquals("ok", rows.display("alarm"))
        assertEquals("no", rows.display("rotating"))
        assertEquals("yes", rows.display("still"))
        assertEquals("3 V", rows.display("battery"))
        assertEquals("20 °C", rows.display("temperature"))
        assertEquals("19.6 psi", rows.display("pressure"))
    }

    @Test
    fun catalogTeslaTstpmsAwakePressure() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-tesla-tstpms" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x022B, "0000058A0147B80B", fleet.id),
            listOf(fleet),
        )
        assertEquals("5", rows.display("mode"))
        assertEquals("42 psi", rows.display("pressure"))
        assertEquals("70 °F", rows.display("temperature"))
        assertEquals("3000 mV", rows.display("battery"))
    }

    @Test
    fun catalogTeslaTstpmsSleepSkipsSensors() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-tesla-tstpms" }
        val rows = SignatureFieldDecoder.decodeSighting(
            ble(0x022B, "0000008A0147B80B", fleet.id),
            listOf(fleet),
        )
        assertEquals("sleep", rows.display("mode"))
        assertTrue(rows.none { it.id == "pressure" })
        assertTrue(rows.none { it.id == "temperature" })
        assertTrue(rows.none { it.id == "battery" })
    }

    private fun List<DecodedFieldValue>.display(id: String): String =
        first { it.id == id }.display

    private fun List<DecodedFieldValue>.number(id: String): Double? =
        first { it.id == id }.number

    private fun wifiRid(dataHex: String, fleetId: String) = Sighting(
        key = "WIFI:60:60:1F:06:31:08",
        kind = RadioKind.WIFI,
        mac = "60:60:1F:06:31:08",
        name = "RID-1581F3YTDJ1D0031Z530",
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
        fleetIds = setOf(fleetId),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(vendorIes = listOf(VendorIeRecord("FA:0B:BC", 0x0D, dataHex))),
    )

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
