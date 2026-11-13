package app.fieldwatch.domain

import app.fieldwatch.radio.BleAdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AdvPayloadDecoderTest {
    private fun bleSighting(mfgHex: String): Sighting = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:99",
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:99",
        name = "",
        rssi = -50,
        rssiMin = -50,
        rssiMax = -50,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = false,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = 0x004C,
        manufacturerDataHex = mfgHex,
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(mfgRecords = listOf(MfgRecord(0x004C, mfgHex))),
    )

    @Test
    fun appleIBeaconDecodesUuidMajorMinorTx() {
        // TLV 02/15 + 16-byte UUID + major + minor + calibrated TX.
        val mfg = "0215" + "E2C56DB5DFFB48D2B060D0F5A71096E0" + "0001" + "0002" + "C5"
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, mfg))
        val byLabel = fields.associate { it.label to it.value }
        assertEquals("0x02 · iBeacon", byLabel["Apple Continuity type"])
        assertEquals("e2c56db5-dffb-48d2-b060-d0f5a71096e0", byLabel["iBeacon UUID"])
        assertEquals("1 / 2", byLabel["iBeacon major / minor"])
        assertTrue(byLabel.getValue("iBeacon calibrated TX").startsWith("-59"))
    }

    @Test
    fun appleProximityPairingNamesAirPods() {
        // TLV 07, prefix 01, model 0F20 (AirPods 2nd gen), status 51, batt 98, case byte 11.
        val mfg = "07" + "07" + "010F2051" + "98" + "11" + "00"
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, mfg))
        val byLabel = fields.associate { it.label to it.value }
        assertEquals("AirPods (2nd generation)", byLabel["Product"])
        assertEquals("80% / 90%", byLabel["Battery (left / right)"])
        assertEquals("10%", byLabel["Case battery"])
        assertEquals("case", byLabel["Charging"])
    }

    @Test
    fun appleFindMyLongFormShowsTheKeyAndHidesBatteryUntilTheOwnerBit() {
        val separated = "12" + "19" + "00" + "11".repeat(24)
        val away = findMy(separated)
        assertTrue(away.contains("Broadcasting a public key"))
        assertTrue(away.contains("Owner not seen in the current key window"))
        assertTrue(away.contains("24-byte key fragment"))
        assertFalse(away.contains("Battery"))

        val near = findMy("12" + "19" + "44" + "11".repeat(24))
        assertTrue(near.contains("Owner seen recently"))
        assertTrue(near.contains("Battery medium"))
    }

    @Test
    fun appleFindMyShortFormDoesNotClaimAPublicKey() {
        val text = findMy("12" + "02" + "0000")
        assertTrue(text.contains("Short advertisement"))
        assertTrue(text.contains("Often a tag near its owner"))
        assertTrue(text.contains("This is not certain"))
        assertFalse(text.contains("public key"))
        assertFalse(text.contains("Battery"))
    }

    private fun findMy(mfg: String): String =
        AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, mfg))
            .single { it.label == "Find My / Offline Finding" }
            .value

    @Test
    fun unknownCompanyYieldsNoFields() {
        assertTrue(AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x9999, "AABB")).isEmpty())
        assertTrue(AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, "XYZ")).isEmpty())
    }

    @Test
    fun fastPairThreeByteModelIdIsPairing() {
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FE2C", "000006"))
        val byLabel = fields.associate { it.label to it.value }
        assertTrue(byLabel.getValue("Google Fast Pair").contains("pairing mode"))
        assertEquals("Google Pixel Buds  (0x000006)", byLabel["Model ID"])
    }

    @Test
    fun fastPairLongerPayloadIsAccountKeyBloom() {
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FE2C", "01020304050607"))
        assertTrue(fields.single().value.contains("Already paired"))
    }

    @Test
    fun eddystoneUidSplitsNamespaceAndInstance() {
        val bytes = "00" + "C5" + "00112233445566778899" + "AABBCCDDEEFF" + "0000"
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", bytes))
        val byLabel = fields.associate { it.label to it.value }
        assertEquals("00112233445566778899", byLabel["Eddystone-UID namespace"])
        assertEquals("AABBCCDDEEFF", byLabel["Eddystone-UID instance"])
    }

    @Test
    fun findHubFrameIsNotEddystone() {
        val eid = "11".repeat(20)
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", "41$eid" + "00"))
        assertEquals("separated (unwanted-tracking mode)", fields.first { it.label == "Find Hub" }.value)
        assertTrue(fields.none { it.label.startsWith("Eddystone") })
        val normal = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", "40$eid" + "00"))
        assertEquals(
            "normal mode, not flagged separated",
            normal.first { it.label == "Find Hub" }.value,
        )
        assertEquals("present. The byte is encrypted.", fields.first { it.label == "Hashed flags" }.value)
        assertEquals("11".repeat(20), fields.first { it.label == "Find Hub EID" }.value)
        val longKey = "33".repeat(32)
        val long = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", "41$longKey"))
        assertEquals("33".repeat(32), long.first { it.label == "Find Hub EID" }.value)
        assertEquals("not present", long.first { it.label == "Hashed flags" }.value)
        val flagged = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", "41$longKey" + "AB"))
        assertEquals("33".repeat(32), flagged.first { it.label == "Find Hub EID" }.value)
        assertEquals("present. The byte is encrypted.", flagged.first { it.label == "Hashed flags" }.value)
    }

    @Test
    fun eddystoneUrlExpandsSchemeAndSuffix() {
        // frame 10, tx C5, scheme 01 (https://www.), "example", 07 (.com)
        val bytes = "10" + "C5" + "01" + "example".toByteArray().toHexUpper() + "07"
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", bytes))
        assertEquals("https://www.example.com", fields.single().value)
    }

    @Test
    fun microsoftCdpNamesDeviceType() {
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x0006, "0109"))
        val field = fields.single()
        assertEquals("Microsoft Nearby Sharing", field.label)
        assertEquals("A Windows desktop is advertising.", field.value)
        assertFalse(field.label.contains("Swift Pair"))
        assertFalse(field.value.contains("Swift Pair"))
    }

    @Test
    fun microsoftCdpNamesNearbySharingMode() {
        val everyone = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x0006, "0109210A"))
        assertEquals(
            "A Windows desktop is advertising. Nearby sharing is set to Everyone nearby.",
            everyone.single().value,
        )
        val mine = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x0006, "01092002"))
        assertEquals(
            "A Windows desktop is advertising. Nearby sharing is set to My devices only.",
            mine.single().value,
        )
        val other = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x0006, "01092202"))
        assertEquals("A Windows desktop is advertising.", other.single().value)
    }

    @Test
    fun microsoftCdpShowsTheComputerNameWhenSharingIsEveryoneNearby() {
        val payload = "0109210A5CB21871EB504445534B544F502D494E5349434F46"
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x0006, payload))
        assertEquals(
            "A Windows desktop is advertising. Nearby sharing is set to Everyone nearby. " +
                "The name on the beacon is DESKTOP-INSICOF.",
            fields.single().value,
        )
        assertFalse(fields.single().value.contains("EB 50"))
        assertFalse(fields.single().value.contains("5C B2"))
    }

    @Test
    fun microsoftCdpHidesANameWhenSharingIsMyDevicesOnly() {
        val payload = "01092022DB200ED2461E4F868631CE27BCA3F88EE67F2250912FD9"
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x0006, payload))
        assertEquals(
            "A Windows desktop is advertising. Nearby sharing is set to My devices only.",
            fields.single().value,
        )
    }

    @Test
    fun microsoftSwiftPairStaysAByteCount() {
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x0006, "0300804C6170746F70"))
        assertEquals("Microsoft manufacturer data", fields.single().label)
        assertFalse(fields.single().value.contains("Laptop"))
    }

    @Test
    fun roleHintsFlagIBeaconAndTeslaKey() {
        val ibeacon = "0215" + "E2C56DB5DFFB48D2B060D0F5A71096E0" + "00010002" + "C5"
        val hints = AdvPayloadDecoder.roleHints(bleSighting(ibeacon))
        assertTrue(hints.any { it.label == "an iBeacon" })

        // Tesla prefix carries the "0215" TLV header; the body still needs
        // major/minor/tx (5 bytes) to reach the declared iBeacon length.
        val tesla = bleSighting(DefaultCatalog.TESLA_IBEACON_MFG_PREFIX + "00010002C5")
        assertTrue(AdvPayloadDecoder.roleHints(tesla).any { it.label.contains("Tesla") })
    }

    @Test
    fun homeKitCameraNamesTheCategoryAndMasksTheDeviceId() {
        val value = "01222022F38F011100FA920202"
        for (frame in listOf("060D$value", "062D$value")) {
            val facts = AdvPayloadDecoder.homekitFacts(listOf(MfgRecord(0x004C, frame)))!!
            assertFalse(facts.paired)
            assertEquals(17, facts.category)
            assertEquals("IP camera", facts.categoryName)
            assertEquals("22:20:22:F3:8F:01", facts.deviceId)
            assertEquals(37626, facts.stateNumber)
            assertEquals(2, facts.configNumber)
            assertEquals(2, facts.version)
            assertFalse(facts.setupHash)
            val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, frame))
            assertEquals("0x06 · HomeKit", fields.single().value)
            assertFalse(fields.any { it.label == "HomeKit" })
        }
        val sighting = homekitSighting("060D$value")
        val text = DeviceDetailText.build(sighting, emptyList())
        assertTrue(text.contains("HomeKit category: IP camera"))
        assertTrue(text.contains("HomeKit pairing: Not paired"))
        assertTrue(text.contains("HomeKit device ID: 22:20:22:F3:8F:01"))
        assertTrue(text.contains("HomeKit state number: 37626"))
        assertTrue(text.contains("HomeKit configuration number: 2"))
        assertTrue(text.contains("HomeKit version: 2"))
        assertFalse(text.contains("Setup hash present"))
        val privateText = DeviceDetailText.build(sighting, emptyList(), demo = true)
        assertTrue(privateText.contains("HomeKit device ID: 22:20:22:**:**:**"))
        assertFalse(privateText.contains("F3:8F:01"))
        val chip = sighting.withLiveDecode(emptyList()).liveDecode.single()
        assertEquals("IP camera, not paired", chip.text)
        assertTrue(chip.emphasis)
        val because = DeviceExplain.guess(sighting, emptyList()).because
        assertTrue(because.contains("A HomeKit IP camera is advertising as not paired."))
    }

    @Test
    fun homeKitLockAndSecuritySystemAndShortValue() {
        val lock = AdvPayloadDecoder.homekitFacts(
            listOf(MfgRecord(0x004C, "060D00AAAAAAAAAAAA060001000102")),
        )!!
        assertTrue(lock.paired)
        assertEquals("Lock", lock.categoryName)
        assertEquals("AA:AA:AA:AA:AA:AA", lock.deviceId)
        assertTrue(homekitSighting("060D00AAAAAAAAAAAA060001000102").withLiveDecode(emptyList()).liveDecode.isEmpty())

        val alarm = AdvPayloadDecoder.homekitFacts(
            listOf(MfgRecord(0x004C, "060D011111111111110B0000010102")),
        )!!
        assertFalse(alarm.paired)
        assertEquals(11, alarm.category)
        assertEquals("Security system", alarm.categoryName)

        val short = "06080102030405060708"
        assertNull(AdvPayloadDecoder.homekitFacts(listOf(MfgRecord(0x004C, short))))
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, short))
        assertEquals("8 bytes of HomeKit setup data", fields.single { it.label == "HomeKit" }.value)
        assertFalse(fields.any { it.value.contains("category") })
    }

    @Test
    fun homeKitAdkLengthAndSetupHashAndALaterTlv() {
        val camera = "01222022F38F011100FA920202"
        val withHash = AdvPayloadDecoder.homekitFacts(
            listOf(MfgRecord(0x004C, "0631" + camera + "DEADBEEF")),
        )!!
        assertTrue(withHash.setupHash)
        assertEquals("IP camera", withHash.categoryName)
        val text = DeviceDetailText.build(homekitSighting("0631" + camera + "DEADBEEF"), emptyList())
        assertTrue(text.contains("Setup hash present"))
        assertFalse(text.lines().any { it.startsWith("Setup hash:") })

        val both = "060D00AAAAAAAAAAAA060001000102" + "060D$camera"
        val later = AdvPayloadDecoder.homekitFacts(listOf(MfgRecord(0x004C, both)))!!
        assertFalse(later.paired)
        assertEquals("IP camera", later.categoryName)

        val kept = AdvPayloadDecoder.homekitFacts(
            listOf(MfgRecord(0x004C, "060D$camera" + "06080102030405060708")),
        )!!
        assertEquals("IP camera", kept.categoryName)

        assertNull(AdvPayloadDecoder.homekitFacts(listOf(MfgRecord(0x004C, "110401020304"))))
        assertEquals("category 18", Homekit.name(18))
        assertEquals("Range extender", Homekit.name(16))
        assertEquals("Bridge", Homekit.name(2))
    }

    @Test
    fun homeKitDetailReadsFactsWhenTheStoredHexIsAnOlderPairedLock() {
        val lock = "0611" + "00AAAAAAAAAAAA060001000102DEADBEEF"
        val camera = "060D" + "01222022F38F011100FA920202"
        val stored = RadioFacts(
            mfgRecords = listOf(MfgRecord(0x004C, lock)),
            homekit = AdvPayloadDecoder.homekitFacts(listOf(MfgRecord(0x004C, lock))),
        )
        val heard = RadioFacts(
            mfgRecords = listOf(MfgRecord(0x004C, camera)),
            homekit = AdvPayloadDecoder.homekitFacts(listOf(MfgRecord(0x004C, camera))),
        )
        val merged = stored.merge(heard)
        val text = DeviceDetailText.build(bleSighting(lock).copy(facts = merged), emptyList())
        assertTrue(text.contains("HomeKit category: IP camera"))
        assertTrue(text.contains("HomeKit pairing: Not paired"))
        assertTrue(text.contains("HomeKit device ID: 22:20:22:F3:8F:01"))
        assertFalse(text.contains("HomeKit category: Lock"))
        assertFalse(text.contains("HomeKit pairing: Paired"))
    }

    @Test
    fun matterCommissionableNamesVendorProductAndDiscriminator() {
        val hex = "00AB03F1FF008001"
        val facts = Matter.read(listOf(ServiceDataRecord("FFF6", hex)))!!
        assertTrue(facts.commissionable)
        assertEquals(0x00, facts.opcode)
        assertEquals(939, facts.discriminator)
        assertEquals(0, facts.advVersion)
        assertEquals(0xFFF1, facts.vendorId)
        assertEquals(0x8000, facts.productId)
        assertEquals(true, facts.additionalData)
        assertEquals(false, facts.extendedAnnouncement)
        val longUuid = Matter.read(
            listOf(ServiceDataRecord("0000FFF6-0000-1000-8000-00805F9B34FB", hex)),
        )!!
        assertEquals(939, longUuid.discriminator)

        val versioned = Matter.readValue(hexTo("00AB13F1FF008001"))!!
        assertEquals(1, versioned.advVersion)
        assertEquals(939, versioned.discriminator)

        val sighting = matterSighting(hex)
        val text = DeviceDetailText.build(sighting, emptyList())
        assertTrue(text.contains("## Matter"))
        assertTrue(text.contains("Commissionable (setup)"))
        assertTrue(text.contains("Matter vendor ID: 0xFFF1"))
        assertTrue(text.contains("Matter product ID: 0x8000"))
        assertTrue(text.contains("Discriminator: 939"))
        assertTrue(text.contains("Additional data present"))
        assertFalse(text.contains("Extended announcement"))
        assertFalse(text.contains("Advertisement"))
        assertTrue(text.contains("The vendor and product IDs are the device's own claim."))
        val privateText = DeviceDetailText.build(sighting, emptyList(), now = 1L, demo = true)
        assertTrue(privateText.contains("Matter vendor ID: 0xFFF1"))
        assertTrue(privateText.contains("Matter product ID: 0x8000"))
        assertFalse(privateText.contains("Discriminator"))
        assertFalse(privateText.contains("939"))
        val chip = sighting.withLiveDecode(emptyList()).liveDecode.single()
        assertEquals("Matter setup", chip.text)
        assertTrue(chip.emphasis)
        val because = DeviceExplain.guess(sighting, listOf("Fast Pair")).because
        assertTrue(because.contains("A Matter device is advertising as commissionable."))
        val headline = DeviceExplain.guess(sighting, listOf("Fast Pair")).headline
        assertTrue(headline, headline.contains("Fast Pair"))
        assertFalse(headline, headline.contains("commissionable"))
    }

    @Test
    fun matterExtendedAnnouncementAndAZeroVendorStayNumbers() {
        val facts = Matter.read(listOf(ServiceDataRecord("FFF6", "0000001012010002")))!!
        assertTrue(facts.commissionable)
        assertEquals(0, facts.discriminator)
        assertEquals(0x1210, facts.vendorId)
        assertEquals(1, facts.productId)
        assertEquals(false, facts.additionalData)
        assertEquals(true, facts.extendedAnnouncement)
        val text = DeviceDetailText.build(matterSighting("0000001012010002"), emptyList())
        assertTrue(text.contains("Matter vendor ID: 0x1210"))
        assertTrue(text.contains("Matter product ID: 0x0001"))
        assertTrue(text.contains("Discriminator: 0"))
        assertTrue(text.contains("Extended announcement"))
        assertFalse(text.contains("Additional data present"))

        val blank = Matter.readValue(hexTo("0000000000000002"))!!
        assertEquals(0, blank.vendorId)
        assertEquals(0, blank.productId)
        val blankText = DeviceDetailText.build(matterSighting("0000000000000002"), emptyList())
        assertTrue(blankText.contains("Matter vendor ID: 0x0000"))
        assertTrue(blankText.contains("Matter product ID: 0x0000"))
    }

    @Test
    fun matterRangingHidesTheIdAndAShortOpcodeDoesNotCrash() {
        val id = "DEADBEEFDEADBEEFDEADBEEFDEADBEEF"
        val ranging = "02" + "10" + "0001" + id + "C5"
        assertEquals(21, hexTo(ranging).size)
        val facts = Matter.readValue(hexTo(ranging))!!
        assertTrue(facts.ranging)
        assertFalse(facts.commissionable)
        assertNull(facts.vendorId)
        assertNull(facts.discriminator)
        val text = DeviceDetailText.build(matterSighting(ranging), emptyList(), demo = true)
        assertTrue(text.contains("Matter proximity ranging"))
        assertFalse(text.contains("DEADBEEF"))
        assertFalse(text.contains("DE AD BE EF"))
        assertFalse(text.contains("Commissionable (setup)"))
        assertFalse(text.contains("The vendor and product IDs"))
        assertTrue(matterSighting(ranging).withLiveDecode(emptyList()).liveDecode.isEmpty())

        val other = Matter.read(listOf(ServiceDataRecord("FFF6", "01")))!!
        assertFalse(other.commissionable)
        assertEquals(0x01, other.opcode)
        assertEquals(1, other.byteCount)
        val otherText = DeviceDetailText.build(matterSighting("01"), emptyList())
        assertTrue(otherText.contains("Matter BLE, opcode 0x01, 1 byte"))
        assertFalse(otherText.contains("1 bytes"))
        assertTrue(matterSighting("01").withLiveDecode(emptyList()).liveDecode.isEmpty())

        val short = Matter.readValue(hexTo("00010203040506"))!!
        assertFalse(short.commissionable)
        assertEquals(7, short.byteCount)
        assertTrue(
            DeviceDetailText.build(matterSighting("00010203040506"), emptyList())
                .contains("Matter BLE, opcode 0x00, 7 bytes"),
        )
        val notRanging = Matter.readValue(hexTo("02" + "00".repeat(7)))!!
        assertFalse(notRanging.ranging)
        assertEquals(8, notRanging.byteCount)
    }

    @Test
    fun matterDetailReadsFactsWhenTheStoredHexIsAnOlderCommissionable() {
        val setup = "00AB03F1FF008001"
        val stored = RadioFacts(
            serviceData = listOf(ServiceDataRecord("FFF6", setup)),
            matter = Matter.read(listOf(ServiceDataRecord("FFF6", setup))),
        )
        val heard = RadioFacts(
            serviceData = listOf(ServiceDataRecord("FFF6", "01")),
            matter = Matter.read(listOf(ServiceDataRecord("FFF6", "01"))),
        )
        val merged = stored.merge(heard)
        assertEquals(setup, merged.serviceData.single().dataHex)
        assertEquals(0x01, merged.matter!!.opcode)
        val text = DeviceDetailText.build(matterSighting(setup).copy(facts = merged), emptyList())
        assertTrue(text.contains("Matter BLE, opcode 0x01, 1 byte"))
        assertFalse(text.contains("Commissionable (setup)"))
        assertFalse(text.contains("Matter vendor ID"))
        assertEquals(0x01, merged.merge(RadioFacts()).matter!!.opcode)
    }

    @Test
    fun sidewalkRegistrationNamesTheBeaconAndHidesTheTail() {
        val facts = Sidewalk.read(listOf(MfgRecord(0x0171, "0433DEAD")), listOf("03FE"))!!
        assertEquals(0x04, facts.applicationId)
        assertEquals(SidewalkMode.REGISTRATION, facts.mode)
        assertEquals(SidewalkBattery.NORMAL, facts.battery)
        assertEquals(SidewalkBeacon.REGISTRATION, facts.beacon)
        assertTrue(facts.protocolBle)
        val version0 = Sidewalk.readValue(hexTo("0413"))!!
        assertEquals(SidewalkBeacon.REGISTRATION, version0.beacon)
        assertEquals(SidewalkBattery.NORMAL, version0.battery)

        val sighting = sidewalkSighting("0433DEAD")
        val text = DeviceDetailText.build(sighting, emptyList())
        assertTrue(text.contains("## Sidewalk beacon"))
        assertTrue(text.contains("Registration / provisioning"))
        assertTrue(text.contains("Registration mode"))
        assertTrue(text.contains("Sidewalk battery: Normal"))
        assertTrue(text.contains("not a proof of the retail model"))
        assertTrue(text.contains("offering itself to the Sidewalk network"))
        assertFalse(text.contains("Protocol bit is not BLE"))
        val privateText = DeviceDetailText.build(sighting, emptyList(), now = 1L, demo = true)
        assertFalse(privateText.contains("DEAD"))
        val chip = sighting.withLiveDecode(emptyList()).liveDecode.single()
        assertEquals("Sidewalk registration", chip.text)
        assertTrue(chip.emphasis)
        val named = DeviceExplain.guess(sighting, listOf("Amazon Sidewalk"))
        assertTrue(named.headline.contains("Amazon Sidewalk"))
        assertFalse(named.headline.contains("registration mode"))
        assertTrue(named.because.contains("advertising in registration mode"))
    }

    @Test
    fun sidewalkPresenceConnectionAndAMismatchedApplicationStayOnTheMode() {
        val presence = Sidewalk.readValue(hexTo("2121"))!!
        assertEquals(SidewalkBeacon.PRESENCE, presence.beacon)
        assertEquals(SidewalkMode.NORMAL, presence.mode)
        assertEquals(SidewalkBattery.CRITICAL, presence.battery)
        val low = Sidewalk.readValue(hexTo("2129"))!!
        assertEquals(SidewalkBattery.LOW, low.battery)
        val presenceText = DeviceDetailText.build(sidewalkSighting("2121"), emptyList())
        assertTrue(presenceText.contains("Presence / device state"))
        assertTrue(presenceText.contains("Normal mode"))
        assertTrue(presenceText.contains("Sidewalk battery: Critical"))
        assertFalse(presenceText.contains("offering itself"))
        assertTrue(sidewalkSighting("2121").withLiveDecode(emptyList()).liveDecode.isEmpty())

        val connect = Sidewalk.readValue(hexTo("0625"))!!
        assertEquals(SidewalkBeacon.CONNECTION_REQUEST, connect.beacon)
        assertEquals(SidewalkMode.CONNECTION_REQUEST, connect.mode)
        assertEquals(SidewalkBattery.CRITICAL, connect.battery)
        val connectNormal = Sidewalk.readValue(hexTo("0635"))!!
        assertEquals(SidewalkBattery.NORMAL, connectNormal.battery)
        val timeSync = Sidewalk.readValue(hexTo("0331"))!!
        assertEquals(SidewalkBeacon.TIME_SYNC, timeSync.beacon)
        assertEquals(SidewalkBattery.NORMAL, timeSync.battery)

        val other = Sidewalk.readValue(hexTo("0431"))!!
        assertNull(other.beacon)
        assertEquals(SidewalkMode.NORMAL, other.mode)
        val otherText = DeviceDetailText.build(sidewalkSighting("0431"), emptyList())
        assertTrue(otherText.contains("Sidewalk, application 0x04"))
        assertFalse(otherText.contains("Registration / provisioning"))

        val notBle = Sidewalk.readValue(hexTo("0432"))!!
        assertEquals(SidewalkMode.REGISTRATION, notBle.mode)
        assertFalse(notBle.protocolBle)
        assertTrue(DeviceDetailText.build(sidewalkSighting("0432"), emptyList()).contains("Protocol bit is not BLE"))

        assertNull(Sidewalk.readValue(hexTo("04")))
        assertNull(Sidewalk.read(listOf(MfgRecord(0x0171, "0433")), emptyList()))
        val oneByte = sidewalkSighting("04")
        assertNull(oneByte.facts.sidewalk)
        assertFalse(DeviceDetailText.build(oneByte, emptyList()).contains("Sidewalk beacon"))
    }

    @Test
    fun sidewalkDetailReadsFactsWhenTheStoredHexIsAnOlderRegistration() {
        val storedHex = "0433DEAD"
        val heardHex = "2121"
        val stored = RadioFacts(
            mfgRecords = listOf(MfgRecord(0x0171, storedHex)),
            sidewalk = Sidewalk.read(listOf(MfgRecord(0x0171, storedHex)), listOf("03FE")),
        )
        val heard = RadioFacts(
            mfgRecords = listOf(MfgRecord(0x0171, heardHex)),
            sidewalk = Sidewalk.read(listOf(MfgRecord(0x0171, heardHex)), listOf("03FE")),
        )
        val merged = stored.merge(heard)
        assertEquals(listOf(storedHex, heardHex), merged.mfgRecords.map { it.dataHex })
        assertEquals(SidewalkBeacon.PRESENCE, merged.sidewalk!!.beacon)
        val text = DeviceDetailText.build(sidewalkSighting(storedHex).copy(facts = merged), emptyList())
        assertTrue(text.contains("Presence / device state"))
        assertFalse(text.contains("Registration / provisioning"))
        val fragment = RadioFacts(
            mfgRecords = listOf(MfgRecord(0x0171, "04")),
            sidewalk = Sidewalk.read(listOf(MfgRecord(0x0171, "04")), listOf("03FE")),
        )
        assertEquals(SidewalkBeacon.PRESENCE, merged.merge(fragment).sidewalk!!.beacon)
        assertEquals(SidewalkBeacon.PRESENCE, merged.merge(RadioFacts()).sidewalk!!.beacon)
    }

    @Test
    fun bthomeReadsTheOfficialObjectsAndStopsAtAnUnknownId() {
        val ad = hexTo("0201060B094449592D73656E736F720A16D2FC4002C40903BF13")
        val parsed = BleAdParser.parse(ad)
        assertEquals("DIY-sensor", parsed.localName)
        val record = parsed.serviceData.single()
        assertEquals("FCD2", record.uuid)
        assertEquals("4002C40903BF13", record.dataHex)

        val sample = Bthome.read(listOf(record))!!
        assertEquals(2, sample.version)
        assertFalse(sample.encrypted)
        assertFalse(sample.triggerBased)
        assertEquals("25.00 °C", bthValue(sample, "Temperature"))
        assertEquals("50.55 %", bthValue(sample, "Humidity"))
        assertNull(sample.chip())

        val tableTemp = Bthome.readValue(hexTo("4002CA09"))!!
        assertEquals("25.06 °C", bthValue(tableTemp, "Temperature"))
        val coarse = Bthome.readValue(hexTo("402E23"))!!
        assertEquals("35 %", bthValue(coarse, "Humidity"))
        val pressure = Bthome.readValue(hexTo("4004138A01"))!!
        assertEquals("1008.83 hPa", bthValue(pressure, "Pressure"))
        val light = Bthome.readValue(hexTo("4005138A14"))!!
        assertEquals("13460.67 lx", bthValue(light, "Illuminance"))
        val battery = Bthome.readValue(hexTo("400161"))!!
        assertEquals("97 %", bthValue(battery, "Battery"))
        val signed = Bthome.readValue(hexTo("40630057D0FF"))!!
        assertEquals("-3.123456 m/s²", bthValue(signed, "Acceleration"))
        val factor = Bthome.readValue(hexTo("4058EA"))!!
        assertEquals("-7.7 °C", bthValue(factor, "Temperature"))

        val fw4 = Bthome.readValue(hexTo("40F100010204"))!!
        assertEquals("4.2.1.0", bthValue(fw4, "Firmware"))
        val fw3 = Bthome.readValue(hexTo("40F2000106"))!!
        assertEquals("6.1.0", bthValue(fw3, "Firmware"))
        val kind = Bthome.readValue(hexTo("40F00100"))!!
        assertEquals("1", bthValue(kind, "Device type"))
        assertFalse(bthValue(kind, "Device type").contains("Shelly"))

        val stamp = Bthome.readValue(hexTo("40505D396164"))!!
        assertEquals("2023-05-14 19:41:17 Z", bthValue(stamp, "Timestamp"))
        val epoch = Bthome.readValue(hexTo("405000000000"))!!
        assertEquals("1970-01-01 00:00:00 Z", bthValue(epoch, "Timestamp"))

        val text = Bthome.readValue(hexTo("40530C48656C6C6F20576F726C6421"))!!
        assertEquals("Hello World!", bthValue(text, "Text"))
        assertEquals("text, 12 bytes", text.objectRows(true, ZoneOffset.UTC).single().second)
        val raw = Bthome.readValue(hexTo("40540C48656C6C6F20576F726C6421"))!!
        assertEquals("48 65 6C 6C 6F 20 57 6F 72 6C 64 21", bthValue(raw, "Raw"))
        assertEquals("raw, 12 bytes", raw.objectRows(true, ZoneOffset.UTC).single().second)

        val stopped = Bthome.readValue(hexTo("4002C409FF00"))!!
        assertEquals("25.00 °C", bthValue(stopped, "Temperature"))
        assertEquals(1, stopped.objects.size)
        assertNull(Bthome.readValue(hexTo("4002C4"))!!.objects.singleOrNull())
        val reserved = Bthome.readValue(hexTo("4202C409"))!!
        assertEquals("25.00 °C", bthValue(reserved, "Temperature"))
        assertNull(reserved.triggerLine())
        val trigger = Bthome.readValue(hexTo("4402C409"))!!
        assertEquals("Trigger-based advertisements", trigger.triggerLine())
        val old = Bthome.readValue(hexTo("2002C409"))!!
        assertEquals(1, old.version)
        assertTrue(old.hideObjects)
        assertTrue(old.objects.isEmpty())
        assertNull(Bthome.readValue(hexTo("")))
        val encrypted = Bthome.readValue(hexTo("41"))!!
        assertTrue(encrypted.encrypted)
        assertEquals(2, encrypted.version)
        assertTrue(encrypted.objectRows(false).isEmpty())
        val page = DeviceDetailText.build(bthomeSighting("41"), emptyList())
        assertTrue(page.contains("BTHome payload encrypted"))
        assertTrue(page.contains("BTHome version: 2"))
        assertFalse(page.contains("Temperature"))
        assertFalse(page.contains("25.00"))
    }

    @Test
    fun bthomeKeepsButtonSlotsAndSkipsACommand() {
        val buttons = Bthome.readValue(hexTo("403A003A01"))!!
        assertEquals(listOf("None", "press"), buttons.objectRows(false).map { it.second })
        val dimmer = Bthome.readValue(hexTo("403C0103"))!!
        assertEquals("rotate left 3 steps", bthValue(dimmer, "Dimmer"))
        val unknown = Bthome.readValue(hexTo("403A07"))!!
        assertEquals("7", bthValue(unknown, "Button"))
        val command = Bthome.readValue(hexTo("403B010305F00100"))!!
        assertEquals(listOf("Command object present"), command.commandLines())
        assertEquals("1", bthValue(command, "Device type"))
        val text = DeviceDetailText.build(bthomeSighting("403B010305F00100"), emptyList())
        assertTrue(text.contains("Command object present"))
        assertFalse(text.contains("step up"))
        assertFalse(text.contains("5 steps"))
        val unordered = Bthome.readValue(hexTo("4003BF1302C409"))!!
        assertEquals(listOf("Temperature", "Humidity"), unordered.objectRows(false).map { it.first })
        val packet = Bthome.readValue(hexTo("40000902C409"))!!
        assertEquals(listOf(0x02), packet.objects.map { it.id })
        assertEquals("25.00 °C", bthValue(packet, "Temperature"))
    }

    @Test
    fun bthomeChipIsOpenThenMotionAndHidesWhileEncrypted() {
        val open = bthomeSighting("401A012101")
        val openChip = open.withLiveDecode(emptyList()).liveDecode.single()
        assertEquals("Open", openChip.text)
        assertTrue(openChip.emphasis)
        val because = DeviceExplain.guess(open, listOf("BTHome")).because
        assertTrue(because.contains("advertising open"))
        assertFalse(because.contains("Hello"))

        val window = bthomeSighting("402D01")
        assertEquals("Open", window.withLiveDecode(emptyList()).liveDecode.single().text)
        val moving = bthomeSighting("402201")
        val motion = moving.withLiveDecode(emptyList()).liveDecode.single()
        assertEquals("Motion", motion.text)
        assertFalse(motion.emphasis)
        assertTrue(DeviceExplain.guess(moving, listOf("BTHome")).because.contains("advertising motion"))

        assertTrue(bthomeSighting("402301").withLiveDecode(emptyList()).liveDecode.isEmpty())
        assertTrue(bthomeSighting("401501").withLiveDecode(emptyList()).liveDecode.isEmpty())
        assertTrue(bthomeSighting("402001").withLiveDecode(emptyList()).liveDecode.isEmpty())
        assertTrue(bthomeSighting("401F01").withLiveDecode(emptyList()).liveDecode.isEmpty())
        assertTrue(bthomeSighting("402501").withLiveDecode(emptyList()).liveDecode.isEmpty())
        assertEquals("Wet", bthValue(Bthome.readValue(hexTo("402001"))!!, "Moisture"))
        assertEquals("Low", bthValue(Bthome.readValue(hexTo("401501"))!!, "Battery"))
        assertEquals("Detected", bthValue(Bthome.readValue(hexTo("402901"))!!, "Smoke"))

        val named = bthomeSighting("40530C48656C6C6F20576F726C6421")
        val share = DeviceDetailText.build(named, emptyList(), demo = true)
        assertTrue(share.contains("text, 12 bytes"))
        assertFalse(share.contains("Hello"))
        assertTrue(named.withLiveDecode(emptyList()).liveDecode.isEmpty())
        val shown = DeviceDetailText.build(named, emptyList())
        assertTrue(shown.contains("Hello World!"))
        assertFalse(shown.contains("DIY-sensor"))
    }

    @Test
    fun exposureNotificationLabelsTwentyBytesAndHidesThePayload() {
        val payload = "CAFEBABECAFEBABECAFEBABECAFEBABE11223344"
        assertEquals(40, payload.length)
        val record = ServiceDataRecord("FD6F", payload)
        val fields = AdvPayloadDecoder.decodeService(record)
        assertEquals(
            listOf(
                "Rolling proximity identifier" to "16 bytes, not shown",
                "Associated metadata" to "Encrypted, 4 bytes",
            ),
            fields.map { it.label to it.value },
        )
        assertFalse(fields.any { it.label.contains("dBm") || it.label.contains("TX") })
        assertFalse(fields.any { it.value.contains("CAFE") || it.value.contains("11223344") })

        val eight = AdvPayloadDecoder.decodeService(ServiceDataRecord("0000FD6F", payload))
        assertEquals(fields, eight)
        val base = AdvPayloadDecoder.decodeService(
            ServiceDataRecord("0000FD6F-0000-1000-8000-00805F9B34FB", payload),
        )
        assertEquals(fields, base)

        val sighting = exposureSighting("FD6F", payload)
        assertTrue(sighting.withLiveDecode(emptyList()).liveDecode.isEmpty())
        val share = DeviceDetailText.build(sighting, listOf("Exposure Notification"), demo = true)
        assertEquals(1, Regex("Rolling proximity identifier").findAll(share).count())
        assertTrue(share.contains("16 bytes, not shown"))
        assertTrue(share.contains("Encrypted, 4 bytes"))
        assertTrue(share.contains("## Exposure Notification"))
        assertTrue(share.contains("It is not a tracker"))
        assertFalse(share.contains("CAFE"))
        assertFalse(share.contains("11223344"))
        assertFalse(share.contains("11 22 33 44"))
        val open = DeviceDetailText.build(sighting, emptyList())
        assertTrue(open.contains("CA FE BA BE"))
        assertEquals(1, Regex("Rolling proximity identifier").findAll(open).count())

        val short = ServiceDataRecord("FD6F", "AABBCCDD")
        assertTrue(AdvPayloadDecoder.decodeService(short).isEmpty())
        val four = ExposureNotification.label(short)!!
        assertEquals("Exposure Notification, 4 bytes", four.fallback)
        assertTrue(four.rows.isEmpty())
        val fourText = DeviceDetailText.build(exposureSighting("FD6F", "AABBCCDD"), emptyList())
        assertTrue(fourText.contains("Exposure Notification, 4 bytes"))
        assertFalse(fourText.contains("Rolling proximity identifier"))

        val sixteen = "00112233445566778899AABBCCDDEEFF"
        val mid = ExposureNotification.label(ServiceDataRecord("FD6F", sixteen))!!
        assertEquals("Exposure Notification, 16 bytes", mid.fallback)
        assertTrue(AdvPayloadDecoder.decodeService(ServiceDataRecord("FD6F", sixteen)).isEmpty())
        assertFalse(mid.fallback!!.contains("00112233"))

        val empty = ExposureNotification.label(ServiceDataRecord("FD6F", ""))!!
        assertEquals("Exposure Notification, 0 bytes", empty.fallback)
        assertNull(ExposureNotification.label(ServiceDataRecord("FD6F", "ABC")))
        assertTrue(AdvPayloadDecoder.decodeService(ServiceDataRecord("FD6F", "ABC")).isEmpty())
        val uuidOnly = bleSighting("").copy(
            manufacturerId = null,
            manufacturerDataHex = "",
            serviceUuids = listOf("FD6F"),
            facts = RadioFacts(),
        )
        val quiet = DeviceDetailText.build(uuidOnly, emptyList())
        assertFalse(quiet.contains("Rolling proximity identifier"))
        assertFalse(quiet.contains("Exposure Notification, "))
    }

    private fun exposureSighting(uuid: String, dataHex: String): Sighting {
        val rec = ServiceDataRecord(uuid, dataHex)
        return bleSighting("").copy(
            manufacturerId = null,
            manufacturerDataHex = "",
            serviceUuids = listOf(uuid),
            facts = RadioFacts(serviceData = listOf(rec)),
        )
    }

    private fun bthValue(facts: BthomeFacts, label: String): String =
        facts.objectRows(false, ZoneOffset.UTC).single { it.first == label }.second

    private fun bthomeSighting(dataHex: String): Sighting {
        val rec = ServiceDataRecord("FCD2", dataHex)
        return bleSighting("").copy(
            manufacturerId = null,
            manufacturerDataHex = "",
            serviceUuids = listOf("FCD2"),
            facts = RadioFacts(serviceData = listOf(rec), bthome = Bthome.read(listOf(rec))),
        )
    }

    private fun sidewalkSighting(payload: String): Sighting {
        val rec = MfgRecord(0x0171, payload)
        val uuids = listOf("03FE")
        return bleSighting("").copy(
            manufacturerId = 0x0171,
            manufacturerDataHex = payload,
            serviceUuids = uuids,
            facts = RadioFacts(
                mfgRecords = listOf(rec),
                sidewalk = Sidewalk.read(listOf(rec), uuids),
            ),
        )
    }

    private fun matterSighting(dataHex: String): Sighting {
        val rec = ServiceDataRecord("FFF6", dataHex)
        return bleSighting("").copy(
            manufacturerId = null,
            manufacturerDataHex = "",
            serviceUuids = listOf("FFF6"),
            facts = RadioFacts(
                serviceData = listOf(rec),
                matter = Matter.read(listOf(rec)),
            ),
        )
    }

    private fun hexTo(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun homekitSighting(continuityHex: String): Sighting {
        val rec = MfgRecord(0x004C, continuityHex)
        return bleSighting(continuityHex).copy(
            facts = RadioFacts(
                mfgRecords = listOf(rec),
                homekit = AdvPayloadDecoder.homekitFacts(listOf(rec)),
            ),
        )
    }
}
