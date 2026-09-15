package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureExchangeTest {
    private val stock = DefaultCatalog.fleets()

    @Test
    fun sonyPlusIBeaconLayoutIsSonyNotIBeacon() {
        val ibeacon = "0215E2C56DB5DFFB48D2B060D0F5A71096E000010002C5"
        val tv = Sighting(
            key = "BLE:AA:BB:CC:DD:EE:02",
            kind = RadioKind.BLE,
            mac = "AA:BB:CC:DD:EE:02",
            name = "Sony TV",
            rssi = -45,
            rssiMin = -45,
            rssiMax = -45,
            channel = 0,
            frequencyMhz = 0,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = 0x012D,
            manufacturerDataHex = ibeacon,
            rawHex = "",
            extras = "",
            firstSeen = 1L,
            lastSeen = 1L,
            hitCount = 1,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            facts = RadioFacts(
                mfgRecords = listOf(
                    MfgRecord(0x012D, "0300"),
                    MfgRecord(0x004C, ibeacon),
                ),
            ),
        )
        val hits = SignatureEngine().match(listOf(tv), stock).getValue(tv.key)
        assertTrue("Sony", "fleet-sony" in hits)
        assertFalse("iBeacon is protocol noise on a Sony TV", "fleet-ibeacon" in hits)
    }

    @Test
    fun teslaPhoneKeyIBeaconIsTeslaNotIBeacon() {
        val data = DefaultCatalog.TESLA_IBEACON_MFG_PREFIX + "00015D"
        val car = Sighting(
            key = "BLE:AA:BB:CC:DD:EE:01",
            kind = RadioKind.BLE,
            mac = "AA:BB:CC:DD:EE:01",
            name = "S1a87a5a75f3df858C",
            rssi = -40,
            rssiMin = -40,
            rssiMax = -40,
            channel = 0,
            frequencyMhz = 0,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = 0x004C,
            manufacturerDataHex = data,
            rawHex = "",
            extras = "",
            firstSeen = 1L,
            lastSeen = 1L,
            hitCount = 1,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            facts = RadioFacts(mfgRecords = listOf(MfgRecord(0x004C, data))),
        )
        val hits = SignatureEngine().match(listOf(car), stock).getValue(car.key)
        assertTrue("Tesla", "fleet-tesla" in hits)
        assertFalse("not a mall iBeacon", "fleet-ibeacon" in hits)
        assertFalse("not Target Atrius", "fleet-target-atrius" in hits)
    }

    @Test
    fun targetAtriusIBeaconIsTargetAndIBeacon() {
        val data = DefaultCatalog.TARGET_ATRIUS_IBEACON_MFG_PREFIX + "6C42CC85C3"
        val tag = ble(
            name = "",
            manufacturerId = 0x004C,
            manufacturerDataHex = data,
        ).copy(
            serviceUuids = listOf("0000B1BB-0000-1000-8000-00805F9B34FB"),
        )
        val hits = SignatureEngine().match(listOf(tag), stock).getValue(tag.key)
        assertTrue("Target Atrius basket", "fleet-target-atrius" in hits)
        assertTrue("still dual-labels iBeacon", "fleet-ibeacon" in hits)
        val fleet = stock.first { it.id == "fleet-target-atrius" }
        assertEquals(SignatureClass.BEACON, fleet.kind)
        assertEquals("Retail beacons", fleet.kind.label())
    }

    @Test
    fun targetAtriusB1bbOnlyStillMatches() {
        val tag = ble(name = "").copy(
            serviceUuids = listOf("B1BB"),
        )
        val hits = SignatureEngine().match(listOf(tag), stock).getValue(tag.key)
        assertTrue("B1BB hits Target Atrius", "fleet-target-atrius" in hits)
    }

    @Test
    fun genericIBeaconIsNotTargetAtrius() {
        val other = ble(
            name = "",
            manufacturerId = 0x004C,
            manufacturerDataHex = "0215E2C56DB5DFFB48D2B060D0F5A71096E000010002C5",
        )
        val hits = SignatureEngine().match(listOf(other), stock).getValue(other.key)
        assertTrue("generic iBeacon", "fleet-ibeacon" in hits)
        assertFalse("not Target UUID", "fleet-target-atrius" in hits)
    }

    @Test
    fun iphoneOfflineFindingIsAppleDeviceNotAirTag() {
        val phone = ble(
            name = "",
            manufacturerId = 0x004C,
            manufacturerDataHex = "10AABBCCDDEE",
        ).copy(
            facts = RadioFacts(
                mfgRecords = listOf(
                    MfgRecord(0x004C, "10AABBCCDDEE"),
                    MfgRecord(0x004C, "12" + "00".repeat(24)),
                ),
            ),
        )
        val hits = SignatureEngine().match(listOf(phone), stock).getValue(phone.key)
        assertTrue("Apple Device", "fleet-apple-device" in hits)
        assertFalse("OF on an iPhone is not an AirTag", "fleet-airtag" in hits)
    }

    @Test
    fun namedIphoneWithOnlyFindMyIsAppleDeviceNotAirTag() {
        val phone = ble(
            name = "iPhone",
            manufacturerId = 0x004C,
            manufacturerDataHex = "12" + "00".repeat(24),
        )
        val hits = SignatureEngine().match(listOf(phone), stock).getValue(phone.key)
        assertTrue("Apple Device", "fleet-apple-device" in hits)
        assertFalse("named iPhone is not an AirTag", "fleet-airtag" in hits)
    }

    @Test
    fun airTagOfflineFindingStillAirTag() {
        val tag = ble(
            name = "",
            manufacturerId = 0x004C,
            manufacturerDataHex = "12" + "00".repeat(24),
        )
        val hits = SignatureEngine().match(listOf(tag), stock).getValue(tag.key)
        assertTrue("AirTag OF", "fleet-airtag" in hits)
        assertFalse("not Continuity", "fleet-apple-device" in hits)
    }

    @Test
    fun airTagNameKeepsAirTagEvenWithContinuity() {
        val tag = ble(
            name = "AirTag",
            manufacturerId = 0x004C,
            manufacturerDataHex = "10AABBCCDDEE",
        ).copy(
            facts = RadioFacts(
                mfgRecords = listOf(
                    MfgRecord(0x004C, "10AABBCCDDEE"),
                    MfgRecord(0x004C, "12" + "00".repeat(24)),
                ),
            ),
        )
        val hits = SignatureEngine().match(listOf(tag), stock).getValue(tag.key)
        assertTrue("name AirTag wins", "fleet-airtag" in hits)
        assertTrue("Continuity still labels Apple Device", "fleet-apple-device" in hits)
    }

    @Test
    fun findMyUuidKeepsAirTagEvenWithContinuity() {
        val accessory = ble(
            name = "",
            manufacturerId = 0x004C,
            manufacturerDataHex = "10AABBCCDDEE",
        ).copy(
            serviceUuids = listOf("0000FD44-0000-1000-8000-00805F9B34FB"),
            facts = RadioFacts(
                mfgRecords = listOf(
                    MfgRecord(0x004C, "10AABBCCDDEE"),
                    MfgRecord(0x004C, "12" + "00".repeat(24)),
                ),
            ),
        )
        val hits = SignatureEngine().match(listOf(accessory), stock).getValue(accessory.key)
        assertTrue("FD44 is a Find My accessory", "fleet-airtag" in hits)
        assertTrue("Continuity still labels Apple Device", "fleet-apple-device" in hits)
    }

    @Test
    fun continuityPlusFindMyIsNotFindMyPayloadAlone() {
        val phone = ble(
            name = "",
            manufacturerId = 0x004C,
            manufacturerDataHex = "10AABBCCDDEE",
        ).copy(
            facts = RadioFacts(
                mfgRecords = listOf(
                    MfgRecord(0x004C, "10AABBCCDDEE"),
                    MfgRecord(0x004C, "12" + "00".repeat(24)),
                ),
            ),
        )
        assertTrue(TrackerMatch.isAppleContinuity(phone))
        assertFalse(TrackerMatch.isFindMyPayload(phone))
        val tag = ble(
            name = "",
            manufacturerId = 0x004C,
            manufacturerDataHex = "12" + "00".repeat(24),
        )
        assertFalse(TrackerMatch.isAppleContinuity(tag))
        assertTrue(TrackerMatch.isFindMyPayload(tag))
    }

    @Test
    fun teslaMatchesVinPhoneKeyAndCybertruck() {
        val tesla = stock.first { it.id == "fleet-tesla" }
        assertTrue(tesla.rules.any { it.kind == RuleKind.NAME_CONTAINS && it.text.equals("Cybertruck", ignoreCase = true) })
        assertTrue(tesla.rules.any { it.kind == RuleKind.NAME_GLOB && it.text == "S????????????????C" })
        assertTrue(TextMatch.glob("S1a87a5a75f3df858C", "S????????????????C"))
        assertFalse(TextMatch.glob("Tesla", "S????????????????C"))
    }

    @Test
    fun stockPresetIdsAreBuiltInAndUnique() {
        val presets = FilterEngine().defaultPresets()
        assertEquals(presets.map { it.id }, presets.map { it.id }.distinct())
        presets.forEach { preset ->
            assertTrue(preset.id, preset.isBuiltIn())
        }
        assertEquals(8, presets.size)
        assertEquals("hide-phones", presets.first { it.name == "Hide phones" }.id)
        assertTrue(presets.none { it.id == "cameras" })
    }

    @Test
    fun consumerCamerasAreCameraClass() {
        val ids = stock.associateBy { it.id }
        for (id in listOf(
            "fleet-gopro", "fleet-osmo", "fleet-insta360",
            "fleet-wyze", "fleet-ring", "fleet-arlo", "fleet-eufy",
            "fleet-nest", "fleet-tapo", "fleet-reolink",
        )) {
            assertEquals(id, SignatureClass.CAMERA, ids.getValue(id).kind)
        }
        assertEquals(SignatureClass.GLASSES, ids.getValue("fleet-meta-glasses").kind)
        assertEquals(SignatureClass.ISP, ids.getValue("fleet-unifi-ap").kind)
        assertEquals(SignatureClass.ISP, ids.getValue("fleet-unifi").kind)
        assertEquals(SignatureClass.SURVEILLANCE, ids.getValue("fleet-unifi-protect").kind)
        for (id in listOf(
            "fleet-meraki", "fleet-cisco", "fleet-aruba", "fleet-ruckus",
            "fleet-fortinet", "fleet-mist", "fleet-sophos", "fleet-extreme",
            "fleet-edgecore", "fleet-watchguard-ap", "fleet-mojo",
        )) {
            assertEquals(id, SignatureClass.ISP, ids.getValue(id).kind)
        }
        assertEquals(SignatureClass.SURVEILLANCE, ids.getValue("fleet-flock-cameras").kind)
        assertEquals(SignatureClass.SURVEILLANCE, ids.getValue("fleet-fs-ext-battery").kind)
        val publicCameras = listOf(
            "fleet-flock-cameras", "fleet-fs-ext-battery", "fleet-penguin", "fleet-pigvision",
            "fleet-genetec", "fleet-rekor", "fleet-vigilant", "fleet-verkada",
            "fleet-avigilon", "fleet-axis", "fleet-hikvision", "fleet-dahua",
        )
        for (id in publicCameras) {
            assertTrue(id, ids.getValue(id).attentionNote.isNotBlank())
        }
        assertTrue(ids.getValue("fleet-unifi-protect").attentionNote.isBlank())
        assertTrue(ids.getValue("fleet-salto").attentionNote.isBlank())
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        for (id in publicCameras) {
            assertTrue(id, id in watched)
        }
        assertFalse("fleet-unifi-protect" in watched)
        assertEquals(SignatureClass.LAW_ENFORCEMENT, ids.getValue("fleet-axon").kind)
        assertEquals(SignatureClass.LAW_ENFORCEMENT, ids.getValue("fleet-watchguard").kind)
        assertEquals(SignatureClass.LAW_ENFORCEMENT, ids.getValue("fleet-cradlepoint").kind)
        assertEquals(SignatureClass.LAW_ENFORCEMENT, ids.getValue("fleet-airlink").kind)
        assertEquals(SignatureClass.LAW_ENFORCEMENT, ids.getValue("fleet-compex").kind)
        assertEquals(SignatureClass.LAW_ENFORCEMENT, ids.getValue("fleet-novatel").kind)
        assertEquals(SignatureClass.LAW_ENFORCEMENT, ids.getValue("fleet-utility-inc").kind)
        assertEquals(SignatureClass.VEHICLE, ids.getValue("fleet-tesla").kind)
        assertEquals(SignatureClass.ISP, ids.getValue("fleet-ruijie").kind)
        assertEquals(SignatureClass.ISP, ids.getValue("fleet-dwnet").kind)
        assertEquals(SignatureClass.ISP, ids.getValue("fleet-wavlink").kind)
        assertEquals(SignatureClass.VEHICLE, ids.getValue("fleet-peoplenet").kind)
        assertEquals(SignatureClass.VEHICLE, ids.getValue("fleet-uconnect").kind)
        assertEquals(SignatureClass.VEHICLE, ids.getValue("fleet-carplay").kind)
        assertEquals(SignatureClass.HOME, ids.getValue("fleet-roku").kind)
        assertEquals(SignatureClass.ISP, ids.getValue("fleet-franklin").kind)
        assertEquals(SignatureClass.ISP, ids.getValue("fleet-huawei").kind)
        assertEquals(SignatureClass.ISP, ids.getValue("fleet-plume").kind)
        assertEquals(SignatureClass.PHONE, ids.getValue("fleet-phone-hotspot").kind)
        assertEquals(SignatureClass.HOME, ids.getValue("fleet-samsung-appliance").kind)
        assertEquals(SignatureClass.HOME, ids.getValue("fleet-ecowater").kind)
        assertTrue(stock.none { it.kind == SignatureClass.BODYWORN })
        assertEquals(SignatureClass.THERMOSTAT, ids.getValue("fleet-nest-thermostat").kind)
        assertEquals(SignatureClass.DRONE, ids.getValue("fleet-dji").kind)
        assertEquals(SignatureClass.DRONE, ids.getValue("fleet-hoverair").kind)
        assertEquals(SignatureClass.HEALTH, ids.getValue("fleet-honeywell-xenon-hc").kind)
        assertEquals(SignatureClass.HEALTH, ids.getValue("fleet-omron").kind)
        assertEquals(SignatureClass.HEALTH, ids.getValue("fleet-withings").kind)
        assertEquals(SignatureClass.HEALTH, ids.getValue("fleet-dexcom").kind)
    }

    @Test
    fun honeywellXenonHealthcareNameHits() {
        val scan = ble(name = "Xenon_CCB-U00-HC_SN_25139B493", mac = "C4:EF:DA:40:63:56")
        val hits = SignatureEngine().match(listOf(scan), stock).getValue(scan.key)
        assertTrue("Xenon CCB-U00-HC", "fleet-honeywell-xenon-hc" in hits)
        val warehouse = ble(name = "Xenon_CCB-U00-G_SN_12345")
        val warehouseHits = SignatureEngine().match(listOf(warehouse), stock).getValue(warehouse.key)
        assertFalse("general-purpose Xenon is not HC", "fleet-honeywell-xenon-hc" in warehouseHits)
    }

    @Test
    fun omronHealthcareCompanyHits() {
        val cuff = ble(name = "BLESmart_0000025828FFB232E019", manufacturerId = 0x020E)
        val hits = SignatureEngine().match(listOf(cuff), stock).getValue(cuff.key)
        assertTrue("Omron 0x020E", "fleet-omron" in hits)
    }

    @Test
    fun osmoActionIsCameraNotDji() {
        val cam = ble(
            name = "OsmoAction5Pro",
            manufacturerId = 0x08AA,
            manufacturerDataHex = "150000AABBCCDDEE03",
        )
        val hits = SignatureEngine().match(listOf(cam), stock).getValue(cam.key)
        assertTrue("Osmo", "fleet-osmo" in hits)
        assertFalse("Osmo must not dual-chip DJI", "fleet-dji" in hits)
    }

    @Test
    fun osmoUnnamedModelIdIsCameraNotDji() {
        val cam = ble(
            name = "",
            manufacturerId = 0x08AA,
            manufacturerDataHex = "1200",
        )
        val hits = SignatureEngine().match(listOf(cam), stock).getValue(cam.key)
        assertTrue("Osmo Action 3 model id", "fleet-osmo" in hits)
        assertFalse("not DJI aircraft", "fleet-dji" in hits)
    }

    @Test
    fun djiAircraftStaysDjiNotOsmo() {
        val drone = ble(
            name = "DJI Mini 4",
            manufacturerId = 0x08AA,
            manufacturerDataHex = "7000",
        )
        val hits = SignatureEngine().match(listOf(drone), stock).getValue(drone.key)
        assertTrue("DJI", "fleet-dji" in hits)
        assertFalse("Mavic/Mini model id is not Osmo", "fleet-osmo" in hits)
    }

    @Test
    fun osmoMobileGimbalIsNotOsmoCamera() {
        val gimbal = ble(name = "Osmo Mobile 6")
        val hits = SignatureEngine().match(listOf(gimbal), stock).getValue(gimbal.key)
        assertFalse("Osmo Mobile is a gimbal", "fleet-osmo" in hits)
        assertFalse(TextMatch.glob("Osmo Mobile 6", "OsmoAction*"))
        assertFalse(TextMatch.glob("Osmo Mobile 6", "Osmo Pocket*"))
    }

    @Test
    fun insta360NameAndCompanyIdAreCamera() {
        val named = ble(name = "X3 11WC1A")
        val company = ble(name = "", manufacturerId = 0x10D7, manufacturerDataHex = "0102")
        val ap = sighting(kind = RadioKind.WIFI, mac = "AA:BB:CC:11:22:33", name = "GO 3 AABB")
        val engine = SignatureEngine()
        val namedHits = engine.match(listOf(named), stock).getValue(named.key)
        val companyHits = engine.match(listOf(company), stock).getValue(company.key)
        val apHits = engine.match(listOf(ap), stock).getValue(ap.key)
        assertTrue("X3 serial name", "fleet-insta360" in namedHits)
        assertTrue("Arashi 0x10D7", "fleet-insta360" in companyHits)
        assertTrue("GO 3 AP", "fleet-insta360" in apHits)
        assertFalse(TextMatch.glob("X300", "X3 *"))
        assertFalse(TextMatch.glob("Ace Hardware", "Ace Pro*"))
    }

    @Test
    fun oemVehicleCompanyIdsAreStock() {
        val ids = stock.associateBy { it.id }
        assertEquals(0x0723, ids.getValue("fleet-ford").rules.first { it.kind == RuleKind.MANUFACTURER_ID }.companyId)
        assertEquals(0x0915, ids.getValue("fleet-honda").rules.first { it.kind == RuleKind.MANUFACTURER_ID }.companyId)
        assertEquals(0x05EB, ids.getValue("fleet-bmw").rules.first { it.kind == RuleKind.MANUFACTURER_ID }.companyId)
    }

    @Test
    fun fastPairIsStockOnFe2c() {
        val row = stock.first { it.id == "fleet-fast-pair" }
        assertEquals("Fast Pair", row.name)
        assertTrue(row.enabled)
        assertTrue(row.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text.equals("FE2C", ignoreCase = true) })
    }

    @Test
    fun stockPackRoundTripsAndImportsAsNoOp() {
        val json = SignatureExchange.encode(
            SignatureExchange.pack(
                fleets = stock,
                catalogVersion = 37,
                appVersion = "1.0.0",
                exportedAt = "2026-08-27T00:00:00Z",
            ),
        )
        assertTrue(json.startsWith("{"))
        assertTrue(json.contains("\"format\": \"fieldwatch-signatures\""))
        val pack = SignatureExchange.parse(json)
        assertEquals(stock.size, pack.fleets.size)
        assertEquals("fieldwatch-signatures", pack.format)
        assertEquals(37, pack.catalogVersion)

        val (merged, result) = SignatureExchange.merge(stock, pack.fleets)
        assertEquals(stock.size, merged.size)
        assertEquals(0, result.added)
        assertEquals(0, result.merged)
        assertEquals(stock.size, result.skipped)
        assertEquals(null, result.error)
        assertEquals("Nothing new. ${stock.size} already on this phone.", result.summary())
    }

    @Test
    fun parseAcceptsLegacySpectreFormat() {
        val json = SignatureExchange.encode(
            SignatureExchange.pack(
                fleets = stock.take(1),
                catalogVersion = 1,
                appVersion = "1.2.14",
                exportedAt = "2026-09-15T00:00:00Z",
            ),
        ).replace("fieldwatch-signatures", "spectre-signatures")
        val pack = SignatureExchange.parse(json)
        assertEquals("spectre-signatures", pack.format)
        assertEquals(1, pack.fleets.size)
    }

    @Test
    fun customSignatureAddsAndNameCollisionRenames() {
        val custom = Fleet(
            id = "custom-pete-wifi",
            name = "Pete Test AP",
            rules = listOf(
                MatchRule(RuleKind.NAME_GLOB, text = "PeteTest*", radio = RadioKind.WIFI),
            ),
        )
        val (added, addResult) = SignatureExchange.merge(stock, listOf(custom))
        assertEquals(1, addResult.added)
        assertEquals(0, addResult.renamed)
        assertTrue(added.any { it.id == custom.id && it.name == "Pete Test AP" && !it.builtIn })

        val cloneName = custom.copy(id = "custom-pete-wifi-2", name = "Cisco")
        val (renamed, renameResult) = SignatureExchange.merge(stock, listOf(cloneName))
        assertEquals(1, renameResult.added)
        assertEquals(1, renameResult.renamed)
        assertTrue(renamed.any { it.id == "custom-pete-wifi-2" && it.name == "Cisco (imported)" })
    }

    @Test
    fun importedBodywornFoldsIntoWearables() {
        val custom = Fleet(
            id = "custom-old-bodyworn",
            name = "Old body cam",
            kind = SignatureClass.BODYWORN,
            rules = listOf(MatchRule(RuleKind.NAME_CONTAINS, text = "BodyCam")),
        )
        val (added, result) = SignatureExchange.merge(stock, listOf(custom))
        assertEquals(1, result.added)
        val row = added.first { it.id == custom.id }
        assertEquals(SignatureClass.WEARABLE, row.kind)
        assertTrue(added.none { it.kind == SignatureClass.BODYWORN })
    }

    @Test
    fun extraRuleOnStockRowMerges() {
        val cisco = stock.first { it.id == "fleet-cisco" }
        val extra = cisco.copy(
            rules = cisco.rules + MatchRule(
                RuleKind.NAME_GLOB,
                text = "PeteCiscoLab*",
                radio = RadioKind.WIFI,
            ),
        )
        val (merged, result) = SignatureExchange.merge(stock, listOf(extra))
        assertEquals(1, result.merged)
        assertEquals(0, result.added)
        val after = merged.first { it.id == "fleet-cisco" }
        assertEquals(cisco.rules.size + 1, after.rules.size)
        assertTrue(after.rules.any { it.text == "PeteCiscoLab*" })
    }

    @Test
    fun sameRulesDifferentIdAreSkipped() {
        val cisco = stock.first { it.id == "fleet-cisco" }
        val clone = cisco.copy(id = "not-cisco", name = "Cisco clone")
        val (_, result) = SignatureExchange.merge(stock, listOf(clone))
        assertEquals(0, result.added)
        assertEquals(1, result.skipped)
    }

    @Test
    fun parseRejectsLogsAndEmpty() {
        val log = "timestamp,iso,kind,mac,name\n1,x,WIFI,AA:BB:CC:DD:EE:FF,test\n"
        try {
            SignatureExchange.parse(log)
            throw AssertionError("expected parse to fail")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Not a Fieldwatch signature pack"))
        }
        try {
            SignatureExchange.parse("")
            throw AssertionError("expected parse to fail")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun matcherStillHitsAfterImportAndRespectsRadio() {
        val custom = Fleet(
            id = "custom-pete-wifi",
            name = "Pete Test AP",
            matchAny = true,
            rules = listOf(
                MatchRule(RuleKind.NAME_GLOB, text = "PeteTest*", radio = RadioKind.WIFI),
            ),
        )
        val (fleets, result) = SignatureExchange.merge(stock, listOf(custom))
        assertEquals(1, result.added)

        val engine = SignatureEngine()
        val ciscoAp = sighting(
            kind = RadioKind.WIFI,
            mac = "00:00:0C:11:22:33",
            name = "Campus",
        )
        val unifiVirtual = sighting(
            kind = RadioKind.WIFI,
            mac = "82:F9:2C:00:00:01",
            name = "Deep Learning",
            ies = listOf("00:50:F2", "00:0F:AC", "AC:8B:A9"),
        )
        val peteAp = sighting(
            kind = RadioKind.WIFI,
            mac = "AA:BB:CC:11:22:33",
            name = "PeteTest-lab",
        )
        val peteBle = sighting(
            kind = RadioKind.BLE,
            mac = "AA:BB:CC:11:22:33",
            name = "PeteTest-lab",
        )
        val airTag = sighting(
            kind = RadioKind.BLE,
            mac = "F4:5C:89:00:00:01",
            name = "AirTag",
        )
        val ciscoOnBle = sighting(
            kind = RadioKind.BLE,
            mac = "00:00:0C:11:22:33",
            name = "Campus",
        )

        val hits = engine.match(
            listOf(ciscoAp, unifiVirtual, peteAp, peteBle, airTag, ciscoOnBle),
            fleets,
        )
        assertTrue("Cisco OUI on Wi-Fi", "fleet-cisco" in hits.getValue(ciscoAp.key))
        assertTrue("UniFi vendor IE on virtual BSSID", "fleet-unifi-ap" in hits.getValue(unifiVirtual.key))
        assertTrue("imported Wi-Fi glob", "custom-pete-wifi" in hits.getValue(peteAp.key))
        assertFalse("imported Wi-Fi glob must not hit BLE", "custom-pete-wifi" in hits.getValue(peteBle.key))
        assertTrue("AirTag name on BLE", "fleet-airtag" in hits.getValue(airTag.key))
        assertFalse("Cisco must not label BLE", "fleet-cisco" in hits.getValue(ciscoOnBle.key))

        val again = engine.match(listOf(ciscoAp), fleets)
        assertTrue(again.getValue(ciscoAp.key).contains("fleet-cisco"))
    }

    @Test
    fun fieldUnmatchedWifiGetsUniqueApAndVehicleRows() {
        val engine = SignatureEngine()
        val ruijie = sighting(RadioKind.WIFI, "F0:74:8D:11:22:33", "MAB Family")
        val reyee = sighting(RadioKind.WIFI, "AA:BB:CC:11:22:33", "@Reyee-s3F2D")
        val dwnet = sighting(RadioKind.WIFI, "2C:67:BE:11:22:33", "MorrellHouse")
        val wavlink = sighting(RadioKind.WIFI, "80:3F:5D:11:22:33", "WAVLINK-N")
        val glinet = sighting(RadioKind.WIFI, "94:83:C4:11:22:33", "Academy - 6805")
        val keepTruckin = sighting(RadioKind.WIFI, "00:25:CA:11:22:33", "KeepTruckin Hotspot - HB492085")
        val pnet = sighting(RadioKind.WIFI, "98:5D:46:11:22:33", "PNet20267513")
        val toyota = sighting(RadioKind.WIFI, "E0:2D:F0:11:22:33", "TOYOTA Sienna_3dabe4e96405")
        val carPlay = sighting(RadioKind.WIFI, "FC:98:16:11:22:33", "BMW 51919 CarPlay")
        val uconnect = sighting(RadioKind.WIFI, "AA:BB:CC:00:00:01", "Uconnect-4326653b")
        val hpPrint = sighting(RadioKind.WIFI, "F0:92:1C:11:22:33", "HP-Print-34-ENVY 4500 series")
        val house = sighting(RadioKind.WIFI, "9C:4F:5F:11:22:33", "Jameson")
        val hits = engine.match(
            listOf(
                ruijie, reyee, dwnet, wavlink, glinet, keepTruckin, pnet,
                toyota, carPlay, uconnect, hpPrint, house,
            ),
            stock,
        )
        assertTrue("Ruijie OUI", "fleet-ruijie" in hits.getValue(ruijie.key))
        assertTrue("Reyee factory SSID", "fleet-ruijie" in hits.getValue(reyee.key))
        assertTrue("DWnet OUI", "fleet-dwnet" in hits.getValue(dwnet.key))
        assertTrue("WAVLINK SSID", "fleet-wavlink" in hits.getValue(wavlink.key))
        assertTrue("GL.iNet OUI", "fleet-glinet" in hits.getValue(glinet.key))
        assertTrue("KeepTruckin is Motive", "fleet-motive" in hits.getValue(keepTruckin.key))
        assertTrue("PeopleNet", "fleet-peoplenet" in hits.getValue(pnet.key))
        assertTrue("Toyota factory SSID", "fleet-toyota" in hits.getValue(toyota.key))
        assertTrue("CarPlay in-car", "fleet-carplay" in hits.getValue(carPlay.key))
        assertTrue("Uconnect", "fleet-uconnect" in hits.getValue(uconnect.key))
        assertTrue("HP-Print", "fleet-hp" in hits.getValue(hpPrint.key))
        assertTrue("house SSID stays unmatched", hits.getValue(house.key).isEmpty())
    }

    @Test
    fun wifiOui24UniversalClearsLocalBitOnly() {
        assertEquals("00095B", MacUtil.wifiOui24Universal("02:09:5B:11:22:33"))
        assertEquals("1C3BF3", MacUtil.wifiOui24Universal("1E:3B:F3:AA:BB:CC"))
        assertNull(MacUtil.wifiOui24Universal("00:09:5B:11:22:33"))
        assertNull(MacUtil.wifiOui24Universal("03:09:5B:11:22:33"))
        assertTrue(MacUtil.isRandomized("02:09:5B:11:22:33"))
    }

    @Test
    fun virtualBssidHitsCatalogOuiNotMacPinOrBle() {
        val engine = SignatureEngine()
        val netgearVirtual = sighting(RadioKind.WIFI, "02:09:5B:11:22:33", "Jameson")
        val tplinkVirtual = sighting(RadioKind.WIFI, "1E:3B:F3:AA:BB:CC", "SmithWifi")
        val netgearBle = sighting(RadioKind.BLE, "02:09:5B:11:22:33", "Jameson")
        val burned = sighting(RadioKind.WIFI, "00:09:5B:11:22:33", "Jameson")
        val pinFleet = Fleet(
            id = "custom-pin",
            name = "Pinned NETGEAR radio",
            matchAny = true,
            rules = listOf(
                MatchRule(RuleKind.MAC_PREFIX, text = "00:09:5B:11:22:33", radio = RadioKind.WIFI),
            ),
        )
        val hits = engine.match(
            listOf(netgearVirtual, tplinkVirtual, netgearBle, burned),
            stock + pinFleet,
        )
        assertTrue("NETGEAR virtual BSSID", "fleet-netgear" in hits.getValue(netgearVirtual.key))
        assertTrue("TP-Link virtual BSSID", "fleet-tplink" in hits.getValue(tplinkVirtual.key))
        assertFalse("BLE must not recover Wi-Fi OUI", "fleet-netgear" in hits.getValue(netgearBle.key))
        assertTrue("universal NETGEAR still hits", "fleet-netgear" in hits.getValue(burned.key))
        assertFalse("MAC pin does not follow virtual BSSID", "custom-pin" in hits.getValue(netgearVirtual.key))
        assertTrue("MAC pin still exact", "custom-pin" in hits.getValue(burned.key))
        assertTrue("virtual still marked randomized", netgearVirtual.randomized)
    }

    @Test
    fun phoneHotspotHuaweiAndPlumeCatchFactoryWifi() {
        val engine = SignatureEngine()
        val androidAp = sighting(RadioKind.WIFI, "C2:11:22:33:44:55", "AndroidAP_1234")
        val galaxy = sighting(RadioKind.WIFI, "C2:11:22:33:44:56", "Galaxy A54 5G")
        val galaxyDash = sighting(RadioKind.WIFI, "C2:11:22:33:44:57", "Galaxy-A54-XXXX")
        val pixel = sighting(RadioKind.WIFI, "C2:11:22:33:44:58", "Pixel 8")
        val iphone = sighting(RadioKind.WIFI, "C2:11:22:33:44:59", "Pete's iPhone")
        val custom = sighting(RadioKind.WIFI, "C2:11:22:33:44:5A", "PeteHotspot")
        val galaxyBle = sighting(RadioKind.BLE, "C2:11:22:33:44:5B", "Galaxy A54 5G")
        val huaweiName = sighting(RadioKind.WIFI, "C2:AA:BB:CC:DD:01", "HUAWEI-B535")
        val huaweiOui = sighting(RadioKind.WIFI, "00:18:82:11:22:33", "Jameson")
        val plumeOui = sighting(RadioKind.WIFI, "60:B4:F7:11:22:33", "SmithWifi")
        val superPod = sighting(RadioKind.WIFI, "C2:AA:BB:CC:DD:02", "SuperPod-setup")
        val hits = engine.match(
            listOf(
                androidAp, galaxy, galaxyDash, pixel, iphone, custom, galaxyBle,
                huaweiName, huaweiOui, plumeOui, superPod,
            ),
            stock,
        )
        assertTrue("AndroidAP", "fleet-phone-hotspot" in hits.getValue(androidAp.key))
        assertFalse("AndroidAP is not Unknown Signature", "fleet-unknown" in hits.getValue(androidAp.key))
        assertTrue("Galaxy space", "fleet-phone-hotspot" in hits.getValue(galaxy.key))
        assertTrue("Galaxy dash", "fleet-phone-hotspot" in hits.getValue(galaxyDash.key))
        assertTrue("Pixel", "fleet-phone-hotspot" in hits.getValue(pixel.key))
        assertTrue("iPhone stays Apple Device", "fleet-apple-device" in hits.getValue(iphone.key))
        assertFalse("iPhone is not Phone hotspot", "fleet-phone-hotspot" in hits.getValue(iphone.key))
        assertTrue("custom hotspot unmatched", hits.getValue(custom.key).isEmpty())
        assertFalse("Galaxy BLE is not Phone hotspot", "fleet-phone-hotspot" in hits.getValue(galaxyBle.key))
        assertTrue("Huawei factory SSID", "fleet-huawei" in hits.getValue(huaweiName.key))
        assertTrue("Huawei OUI renamed", "fleet-huawei" in hits.getValue(huaweiOui.key))
        assertTrue("Plume OUI renamed", "fleet-plume" in hits.getValue(plumeOui.key))
        assertTrue("SuperPod name", "fleet-plume" in hits.getValue(superPod.key))
    }

    @Test
    fun carlinkAdapterHitsGlobAndPanasonicOuiNotAlpsAlone() {
        val engine = SignatureEngine()
        val named = sighting(RadioKind.WIFI, "CC:57:63:11:22:33", "CARLINK-7D0AF4")
        val randNamed = sighting(RadioKind.WIFI, "C2:11:22:33:44:55", "CARLINK-B6789A")
        val panasonicRenamed = sighting(RadioKind.WIFI, "CC:57:63:AA:BB:CC", "MyCar")
        val zhuolian = sighting(RadioKind.WIFI, "68:8F:C9:11:22:33", "CARLINK-ABCDEF")
        val toyotaAlps = sighting(RadioKind.WIFI, "E0:2D:F0:11:22:33", "TOYOTA Sienna_3dabe4e96405")
        val alpsHouse = sighting(RadioKind.WIFI, "E0:2D:F0:44:55:66", "Jameson")
        val hits = engine.match(
            listOf(named, randNamed, panasonicRenamed, zhuolian, toyotaAlps, alpsHouse),
            stock,
        )
        assertTrue("Panasonic + glob", "fleet-carlink" in hits.getValue(named.key))
        assertTrue("randomized BSSID still hits glob", "fleet-carlink" in hits.getValue(randNamed.key))
        assertTrue("Panasonic OUI renamed SSID", "fleet-carlink" in hits.getValue(panasonicRenamed.key))
        assertTrue("Zhuolian + glob", "fleet-carlink" in hits.getValue(zhuolian.key))
        assertFalse("Toyota on Alps Alpine is not CARLINK", "fleet-carlink" in hits.getValue(toyotaAlps.key))
        assertTrue("Toyota factory SSID still Toyota", "fleet-toyota" in hits.getValue(toyotaAlps.key))
        assertFalse("Alps Alpine house SSID is not CARLINK", "fleet-carlink" in hits.getValue(alpsHouse.key))
    }

    @Test
    fun rokuHiddenWifiDirectHitsVendorIeNotWps() {
        val engine = SignatureEngine()
        val hidden = sighting(
            kind = RadioKind.WIFI,
            mac = "C2:D2:F3:E1:1D:02",
            name = "",
            ies = listOf("00:50:F2", "50:6F:9A", "C8:3A:6B"),
        )
        val wpsOnly = sighting(
            kind = RadioKind.WIFI,
            mac = "C2:D2:F3:E1:1D:03",
            name = "",
            ies = listOf("00:50:F2", "50:6F:9A"),
        )
        val factory = sighting(
            kind = RadioKind.WIFI,
            mac = "AA:BB:CC:00:00:02",
            name = "DIRECT-roku-abcd",
        )
        val burned = sighting(
            kind = RadioKind.WIFI,
            mac = "C8:3A:6B:11:22:33",
            name = "",
        )
        val onBle = sighting(
            kind = RadioKind.BLE,
            mac = "C8:3A:6B:11:22:33",
            name = "",
        )
        val hits = engine.match(listOf(hidden, wpsOnly, factory, burned, onBle), stock)
        assertTrue("Roku vendor IE on randomized BSSID", "fleet-roku" in hits.getValue(hidden.key))
        assertFalse("WPS/P2P IEs are not Roku", "fleet-roku" in hits.getValue(wpsOnly.key))
        assertTrue("DIRECT-roku factory SSID", "fleet-roku" in hits.getValue(factory.key))
        assertTrue("Roku burned-in OUI", "fleet-roku" in hits.getValue(burned.key))
        assertFalse("Roku must not label BLE", "fleet-roku" in hits.getValue(onBle.key))
    }

    @Test
    fun franklinRg3100HitsOuiAndFactorySsidNotQualcommIe() {
        val engine = SignatureEngine()
        val burned = sighting(
            kind = RadioKind.WIFI,
            mac = "50:FB:FF:02:CA:C8",
            name = "RG3100-8434 guest",
            ies = listOf("00:50:F2", "8C:FD:F0"),
        )
        val renamed = sighting(
            kind = RadioKind.WIFI,
            mac = "50:FB:FF:11:22:33",
            name = "HouseNet",
        )
        val factoryOnly = sighting(
            kind = RadioKind.WIFI,
            mac = "AA:BB:CC:00:00:03",
            name = "RG3100-8434 guest",
        )
        val qualcommOnly = sighting(
            kind = RadioKind.WIFI,
            mac = "AA:BB:CC:00:00:04",
            name = "HouseNet",
            ies = listOf("8C:FD:F0"),
        )
        val electric = sighting(
            kind = RadioKind.WIFI,
            mac = "00:12:27:11:22:33",
            name = "Pump",
        )
        val hits = engine.match(listOf(burned, renamed, factoryOnly, qualcommOnly, electric), stock)
        assertTrue("Franklin OUI + RG3100 guest", "fleet-franklin" in hits.getValue(burned.key))
        assertTrue("Franklin OUI renamed SSID", "fleet-franklin" in hits.getValue(renamed.key))
        assertTrue("RG3100 factory SSID", "fleet-franklin" in hits.getValue(factoryOnly.key))
        assertFalse("Qualcomm chip IE is not Franklin", "fleet-franklin" in hits.getValue(qualcommOnly.key))
        assertFalse("Franklin Electric is not the gateway row", "fleet-franklin" in hits.getValue(electric.key))
    }

    @Test
    fun samsungApplianceAndEcoWaterFactorySsids() {
        val engine = SignatureEngine()
        val fridge = sighting(RadioKind.WIFI, "1C:E8:9E:01:8F:92", "[fridge]_E30AJT5133207Z")
        val oven = sighting(RadioKind.WIFI, "34:FC:99:11:22:33", "[oven] Samsung")
        val h2o = sighting(RadioKind.WIFI, "04:7B:CB:D1:14:00", "H2O-047bcbd11400")
        val house = sighting(RadioKind.WIFI, "CE:BE:8F:24:DC:E3", "IonCannon")
        val guest = sighting(RadioKind.WIFI, "A2:53:22:C6:84:42", "LOB_Guest")
        val h2oBar = sighting(RadioKind.WIFI, "AA:BB:CC:00:00:05", "H2O-Lounge")
        val hits = engine.match(listOf(fridge, oven, h2o, house, guest, h2oBar), stock)
        assertTrue("[fridge] setup", "fleet-samsung-appliance" in hits.getValue(fridge.key))
        assertTrue("[oven] Samsung", "fleet-samsung-appliance" in hits.getValue(oven.key))
        assertTrue("H2O- + 12", "fleet-ecowater" in hits.getValue(h2o.key))
        assertTrue("house SSID stays unmatched", hits.getValue(house.key).isEmpty())
        assertTrue("guest SSID stays unmatched", hits.getValue(guest.key).isEmpty())
        assertTrue("short H2O- is not EcoWater", hits.getValue(h2oBar.key).isEmpty())
    }

    @Test
    fun merakiApDoesNotDualChipCiscoVendorIe() {
        val meraki = sighting(
            kind = RadioKind.WIFI,
            mac = "00:18:0A:11:22:33",
            name = "Campus",
            ies = listOf("00:50:F2", "00:0F:AC", "00:00:0C"),
        )
        val cisco = sighting(
            kind = RadioKind.WIFI,
            mac = "00:00:0C:11:22:33",
            name = "Campus",
            ies = listOf("00:00:0C"),
        )
        val hits = SignatureEngine().match(listOf(meraki, cisco), stock)
        assertTrue("Meraki OUI", "fleet-meraki" in hits.getValue(meraki.key))
        assertFalse("Meraki must not dual-chip Cisco vendor IE", "fleet-cisco" in hits.getValue(meraki.key))
        assertTrue("Cisco OUI still Cisco", "fleet-cisco" in hits.getValue(cisco.key))
        assertFalse("Cisco must not take Meraki", "fleet-meraki" in hits.getValue(cisco.key))
    }

    @Test
    fun createFromDeviceSkipsDirectGlob() {
        val printer = sighting(
            kind = RadioKind.WIFI,
            mac = "AC:F4:66:A4:BC:A8",
            name = "DIRECT-HP-DeskJet-A1B2",
        )
        val fleet = SignatureEngine().suggestFleet(printer)
        assertTrue(fleet.rules.any { it.kind == RuleKind.MAC_PREFIX && it.text == printer.mac })
        assertFalse(
            "DIRECT- must not become DIRECT*",
            fleet.rules.any { it.kind == RuleKind.NAME_GLOB && it.text.uppercase().startsWith("DIRECT") },
        )
        val nameRule = fleet.rules.single { it.kind == RuleKind.NAME_CONTAINS }
        assertEquals("DIRECT-HP-DeskJet-A1B2", nameRule.text)
        assertTrue(SignatureCandidates.isOverbroadCreateName("DIRECT*"))
        assertTrue(SignatureCandidates.isOverbroadCreateName("DIRECT-*"))
        assertFalse(SignatureCandidates.isOverbroadCreateName("DIRECT-HP-DeskJet-A1B2"))
        assertFalse(SignatureCandidates.isOverbroadCreateName("HP-Print*"))
    }

    @Test
    fun custom128BitUuidDoesNotAliasTo0000() {
        val epson = "802A0000-4EF4-4E59-B573-2BED4A4AC159"
        val sensorPush = "EF090000-11D6-42BA-93B8-9DD7EC090AA9"
        assertFalse(uuidAliases(epson).any { it in uuidAliases(sensorPush) })
        assertTrue("0000" !in uuidAliases(epson))
        assertTrue("0000" !in uuidAliases(sensorPush))
        assertTrue("FD44" in uuidAliases("FD44"))
        assertTrue("FD44" in uuidAliases("0000FD44-0000-1000-8000-00805F9B34FB"))
    }

    @Test
    fun epsonEt4800BleIsNotSensorPush() {
        val radio = sighting(
            kind = RadioKind.BLE,
            mac = "5A:05:D9:2A:CE:B0",
            name = "ET-4800 Series",
        ).copy(serviceUuids = listOf("802A0000-4EF4-4E59-B573-2BED4A4AC159"))
        val hits = SignatureEngine().match(listOf(radio), stock).getValue(radio.key)
        assertFalse("Epson printer UUID is not SensorPush", "fleet-sensorpush" in hits)
    }

    @Test
    fun createFromDeviceKeepsProductGlob() {
        val radio = sighting(
            kind = RadioKind.WIFI,
            mac = "00:11:22:33:44:55",
            name = "RG3100-7A21",
        )
        val fleet = SignatureEngine().suggestFleet(radio)
        assertTrue(fleet.rules.any { it.kind == RuleKind.NAME_GLOB && it.text == "RG3100*" })
        assertFalse(fleet.rules.any { it.kind == RuleKind.NAME_CONTAINS })
    }

    private fun ble(
        name: String,
        manufacturerId: Int? = null,
        manufacturerDataHex: String = "",
        mac: String = "AA:BB:CC:DD:EE:01",
    ) = sighting(
        kind = RadioKind.BLE,
        mac = mac,
        name = name,
    ).copy(
        manufacturerId = manufacturerId,
        manufacturerDataHex = manufacturerDataHex,
        facts = if (manufacturerId == null) RadioFacts()
        else RadioFacts(mfgRecords = listOf(MfgRecord(manufacturerId, manufacturerDataHex))),
    )

    private fun sighting(
        kind: RadioKind,
        mac: String,
        name: String = "",
        ies: List<String> = emptyList(),
    ) = Sighting(
        key = "${kind.name}:$mac",
        kind = kind,
        mac = mac,
        name = name,
        rssi = -40,
        rssiMin = -40,
        rssiMax = -40,
        channel = 1,
        frequencyMhz = 2412,
        vendor = null,
        randomized = kind == RadioKind.BLE || (mac.take(2).toInt(16) and 0x02) != 0,
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
        vendorIeOuis = ies,
    )
}
