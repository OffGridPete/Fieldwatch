package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCatalogTest {
    @Test
    fun stockCatalogDoesNotShipUnknownSignature() {
        assertFalse(DefaultCatalog.fleets().any { it.id == "fleet-unknown" })
        assertFalse(DefaultCatalog.fleets().any { it.name.equals("Unknown Signature", ignoreCase = true) })
    }

    @Test
    fun stockNotesDoNotSayShipsOn() {
        DefaultCatalog.fleets().forEach { fleet ->
            assertFalse(
                "${fleet.name} notes still mention Ships on: ${fleet.notes}",
                fleet.notes.contains("Ships on", ignoreCase = true),
            )
        }
    }

    @Test
    fun ibeaconNoteKeepsMallAdvice() {
        val note = DefaultCatalog.fleets().single { it.id == "fleet-ibeacon" }.notes
        assertTrue(note.contains("Mute in a dense mall."))
        assertFalse(note.contains("Ships on"))
        assertTrue(note.contains("proximity beacon", ignoreCase = true))
    }

    @Test
    fun stockNotesExplainTheFamilyNotTheMatcher() {
        DefaultCatalog.fleets().forEach { fleet ->
            assertTrue("${fleet.name} has empty notes", fleet.notes.isNotBlank())
            assertFalse(
                "${fleet.name} notes still look like matcher copy: ${fleet.notes}",
                Regex("""0x[0-9A-Fa-f]{2,}""").containsMatchIn(fleet.notes),
            )
        }
        val oura = DefaultCatalog.fleets().single { it.id == "fleet-oura" }.notes
        assertTrue(oura, oura.contains("ring", ignoreCase = true))
    }

    @Test
    fun flockAndFsExtDropEspressifAndSilabsOuis() {
        val flock = DefaultCatalog.fleets().single { it.id == "fleet-flock-cameras" }
        val fs = DefaultCatalog.fleets().single { it.id == "fleet-fs-ext-battery" }
        val flockOuis = flock.rules.filter { it.kind == RuleKind.OUI }.map { it.text.uppercase() }.toSet()
        val fsOuis = fs.rules.filter { it.kind == RuleKind.OUI }.map { it.text.uppercase() }.toSet()
        assertFalse(flockOuis.contains("A4:CF:12"))
        assertFalse(flockOuis.contains("3C:71:BF"))
        assertFalse(flockOuis.contains("E0:4F:43"))
        assertFalse(flockOuis.contains("70:C9:4E"))
        assertTrue(flockOuis.contains("B4:1E:52"))
        val lite = DefaultCatalog.fleets().single { it.id == "fleet-liteon-camera-radio" }
        val liteOuis = lite.rules.filter { it.kind == RuleKind.OUI }.map { it.text.uppercase() }.toSet()
        assertTrue(liteOuis.contains("70:C9:4E"))
        assertFalse(liteOuis.contains("E0:4F:43"))
        assertFalse(liteOuis.contains("B4:1E:52"))
        assertTrue(lite.attentionNote.isBlank())
        assertEquals(SignatureClass.CAMERA, lite.kind)
        assertFalse(fsOuis.contains("90:35:EA"))
        assertFalse(fsOuis.contains("58:8E:81"))
        assertFalse(fsOuis.contains("EC:1B:BD"))
    }

    @Test
    fun catalogV77AddsGlassesAndAxonUuids() {
        val axon = DefaultCatalog.fleets().single { it.id == "fleet-axon" }
        val meta = DefaultCatalog.fleets().single { it.id == "fleet-meta-glasses" }
        val snap = DefaultCatalog.fleets().single { it.id == "fleet-snap-spectacles" }
        val vuzix = DefaultCatalog.fleets().single { it.id == "fleet-vuzix" }
        val rid = DefaultCatalog.fleets().single { it.id == "fleet-remote-id" }
        assertTrue(axon.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text.equals("FC81", true) })
        assertTrue(axon.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x034D })
        assertTrue(meta.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text.equals("FEB7", true) })
        assertTrue(snap.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text.equals("FE45", true) })
        assertTrue(vuzix.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x060C })
        assertTrue(rid.rules.any { it.kind == RuleKind.VENDOR_IE_OUI && it.text.equals("FA:0B:BC", true) })
    }

    @Test
    fun findHubMatchesSeparatedFrameNotEddystoneUid() {
        val stock = DefaultCatalog.fleets()
        val engine = SignatureEngine()
        val eid = "11".repeat(20)
        val hub = ble(
            name = "",
            serviceUuids = listOf("FEAA"),
        ).copy(
            facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FEAA", "41$eid" + "00"))),
        )
        val uid = ble(
            name = "",
            serviceUuids = listOf("FEAA"),
        ).copy(
            facts = RadioFacts(
                serviceData = listOf(ServiceDataRecord("FEAA", "00C5" + "11".repeat(10) + "22".repeat(6))),
            ),
        )
        assertTrue("Find Hub", "fleet-find-hub" in engine.match(listOf(hub), stock).getValue(hub.key))
        assertFalse("Eddystone-UID is not Find Hub", "fleet-find-hub" in engine.match(listOf(uid), stock).getValue(uid.key))
    }

    @Test
    fun dultMatchesFcb2ServiceDataNotUuidList() {
        val stock = DefaultCatalog.fleets()
        val engine = SignatureEngine()
        val tagged = ble(name = "", serviceUuids = listOf("FCB2")).copy(
            facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FCB2", "0100"))),
        )
        val uuidOnly = ble(name = "", serviceUuids = listOf("FCB2"))
        val hub = ble(name = "", serviceUuids = listOf("FEAA")).copy(
            facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FEAA", "41" + "11".repeat(20)))),
        )
        assertTrue("DULT", "fleet-dult" in engine.match(listOf(tagged), stock).getValue(tagged.key))
        assertFalse("UUID list is not DULT", "fleet-dult" in engine.match(listOf(uuidOnly), stock).getValue(uuidOnly.key))
        assertFalse("Find Hub is not DULT", "fleet-dult" in engine.match(listOf(hub), stock).getValue(hub.key))
        assertTrue("Find Hub still matches", "fleet-find-hub" in engine.match(listOf(hub), stock).getValue(hub.key))
        val fleet = stock.single { it.id == "fleet-dult" }
        assertEquals(SignatureClass.FINDER, fleet.kind)
        assertTrue(fleet.attentionNote.isBlank())
    }

    @Test
    fun aftermarketTpmsMatchesPrefixAndNameNotBareNokia() {
        val stock = DefaultCatalog.fleets()
        val engine = SignatureEngine()
        val cap = ble(
            name = "TPMS1_A1B2",
            manufacturerId = 0x0001,
            manufacturerDataHex = "80EACA108A78E36D0000E60A00005B00",
            serviceUuids = listOf("FBB0"),
        )
        val hits = engine.match(listOf(cap), stock).getValue(cap.key)
        assertTrue("aftermarket TPMS", "fleet-tpms-ble" in hits)
        assertFalse("not Tesla tsTPMS", "fleet-tesla-tstpms" in hits)

        val nokia = ble(
            name = "",
            manufacturerId = 0x0001,
            manufacturerDataHex = "010103215D64",
        )
        val nokiaHits = engine.match(listOf(nokia), stock).getValue(nokia.key)
        assertFalse("bare Nokia 0x0001 is not aftermarket TPMS", "fleet-tpms-ble" in nokiaHits)

        val teslaTire = ble(name = "tsTPMS")
        val teslaHits = engine.match(listOf(teslaTire), stock).getValue(teslaTire.key)
        assertTrue("Tesla tsTPMS", "fleet-tesla-tstpms" in teslaHits)
        assertFalse("tsTPMS is not Aftermarket TPMS", "fleet-tpms-ble" in teslaHits)
    }

    @Test
    fun sytpmsMatchesBrNameAndPressureUuidNotBrother() {
        val stock = DefaultCatalog.fleets()
        val engine = SignatureEngine()
        val sensor = ble(name = "BR", serviceUuids = listOf("27A5"))
        val hits = engine.match(listOf(sensor), stock).getValue(sensor.key)
        assertTrue("SYTPMS", "fleet-sytpms" in hits)

        val printer = ble(name = "Brother Printer")
        val printerHits = engine.match(listOf(printer), stock).getValue(printer.key)
        assertFalse("BR contains is not used", "fleet-sytpms" in printerHits)
    }

    @Test
    fun foboMatchesServiceUuid() {
        val stock = DefaultCatalog.fleets()
        val sensor = ble(name = "", serviceUuids = listOf("00EE"))
        val hits = SignatureEngine().match(listOf(sensor), stock).getValue(sensor.key)
        assertTrue("FOBO", "fleet-fobo" in hits)
    }

    @Test
    fun aftermarketTpmsDoesNotUseBareNokiaCompanyId() {
        val fleet = DefaultCatalog.fleets().single { it.id == "fleet-tpms-ble" }
        assertFalse(fleet.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x0001 })
        assertTrue(fleet.rules.any { it.kind == RuleKind.MANUFACTURER_DATA && it.companyId == 0x0001 })
        assertTrue(fleet.decode != null)
    }

    @Test
    fun signatureNotesAreSeparateFromExtraAttention() {
        val oura = DefaultCatalog.fleets().single { it.id == "fleet-oura" }
        val axon = DefaultCatalog.fleets().single { it.id == "fleet-axon" }
        val device = ble().copy(fleetIds = setOf(oura.id, axon.id))
        val notes = device.signatureNotes(listOf(oura, axon))
        val attention = device.attentionNotes(listOf(oura, axon))
        assertTrue(oura.notes.isNotBlank())
        assertTrue(notes.any { it.first == oura.name && it.second == oura.notes })
        assertTrue(notes.any { it.first == axon.name && it.second == axon.notes })
        assertEquals(listOf(axon.name), attention.map { it.first })
        assertFalse(attention.any { it.first == oura.name })
        val dump = DeviceDetailText.build(
            device,
            listOf(oura.name, axon.name),
            now = 1L,
            attentionNotes = attention,
            signatureNotes = notes,
            fleets = listOf(oura, axon),
        )
        assertTrue(dump.contains("## Notes"))
        assertTrue(dump.contains(oura.notes))
        assertTrue(dump.contains("EXTRA ATTENTION (${axon.name})"))
        assertFalse(dump.contains("EXTRA ATTENTION (${oura.name})"))
    }

    @Test
    fun catalog89RayNeoRequiresNameAndTclEvenOrsCompanyIdLiteOnStaysQuiet() {
        val stock = DefaultCatalog.fleets()
        val engine = SignatureEngine()
        val ray = stock.single { it.id == "fleet-rayneo" }
        val even = stock.single { it.id == "fleet-even-g1" }
        val lite = stock.single { it.id == "fleet-liteon-camera-radio" }
        val flock = stock.single { it.id == "fleet-flock-cameras" }

        assertFalse(ray.matchAny)
        assertEquals(listOf("fleet-rayneo"), stock.filter { !it.matchAny }.map { it.id })
        assertEquals(SignatureClass.GLASSES, ray.kind)
        assertTrue(ray.attentionNote.isNotBlank())
        assertEquals(0x0BC6, ray.rules.single { it.kind == RuleKind.MANUFACTURER_ID }.companyId)
        assertEquals("RayNeo*", ray.rules.single { it.kind == RuleKind.NAME_GLOB }.text)
        assertEquals(2, ray.rules.size)
        assertTrue(even.matchAny)
        assertTrue(even.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x10F9 })
        assertTrue(even.rules.any { it.kind == RuleKind.NAME_CONTAINS && it.text == "Even G1" })
        assertFalse(even.notes.contains("Name-only", ignoreCase = true))
        val liteOuis = lite.rules.filter { it.kind == RuleKind.OUI }.map { it.text.uppercase() }.toSet()
        assertTrue(liteOuis.contains("E0:0A:F6"))
        assertTrue(liteOuis.contains("14:B5:CD"))
        assertTrue(liteOuis.contains("08:3A:88"))
        assertTrue(lite.attentionNote.isBlank())
        assertEquals(SignatureClass.CAMERA, lite.kind)
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        assertTrue("fleet-rayneo" in watched)
        assertTrue("fleet-even-g1" in watched)
        assertFalse("fleet-liteon-camera-radio" in watched)
        assertFalse(stock.any { fleet ->
            fleet.rules.any { rule ->
                rule.kind == RuleKind.MANUFACTURER_ID &&
                    rule.companyId in setOf(0x07D7, 0x0BA7, 0xFD5F)
            }
        })
        assertFalse(stock.any { fleet ->
            fleet.rules.any { rule ->
                rule.text.contains("FlockCam", ignoreCase = true) ||
                    rule.text.contains("RWLS", ignoreCase = true) ||
                    rule.text.contains("META_RB", ignoreCase = true) ||
                    rule.text.equals("Pico", ignoreCase = true)
            }
        })
        assertTrue(flock.rules.any { it.kind == RuleKind.NAME_CONTAINS && it.text == "Flock" })

        val both = tagged("01", "RayNeo Air 2", 0x0BC6)
        val bare = tagged("02", "RayNeo", 0x0BC6)
        val lower = tagged("03", "rayneo", 0x0BC6)
        val nameOnly = tagged("04", "RayNeo Air 2", null)
        val tclOnly = tagged("05", "TCL 50", 0x0BC6)
        val midName = tagged("06", "My RayNeo", 0x0BC6)
        val qinheng = tagged("07", "RayNeo", 0x07D7)
        val hearx = tagged("08", "RayNeo", 0x0BA7)
        val evenId = tagged("09", "", 0x10F9)
        val evenName = tagged("0A", "Even G1", null)
        val uart = ble(name = "", serviceUuids = listOf("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
            .copy(key = "BLE:0B", mac = "AA:BB:CC:DD:EE:0B")
        val liteA = wifi("E0:0A:F6:11:22:33")
        val liteB = wifi("14:B5:CD:11:22:33")
        val flockCam = wifi("02:11:22:33:44:55", "FlockCam-9")
        val rwls = wifi("02:11:22:33:44:56", "RWLS-1")
        val hits = engine.match(
            listOf(
                both, bare, lower, nameOnly, tclOnly, midName, qinheng, hearx,
                evenId, evenName, uart, liteA, liteB, flockCam, rwls,
            ),
            stock,
        )
        assertTrue("name and TCL id", "fleet-rayneo" in hits.getValue(both.key))
        assertTrue("bare RayNeo", "fleet-rayneo" in hits.getValue(bare.key))
        assertTrue("lowercase rayneo", "fleet-rayneo" in hits.getValue(lower.key))
        assertFalse("name only", "fleet-rayneo" in hits.getValue(nameOnly.key))
        assertFalse("TCL phone", "fleet-rayneo" in hits.getValue(tclOnly.key))
        assertFalse("name not at the start", "fleet-rayneo" in hits.getValue(midName.key))
        assertFalse("Qinheng id", "fleet-rayneo" in hits.getValue(qinheng.key))
        assertFalse("hearX id", "fleet-rayneo" in hits.getValue(hearx.key))
        assertTrue("Even company id", "fleet-even-g1" in hits.getValue(evenId.key))
        assertFalse("Even id is not RayNeo", "fleet-rayneo" in hits.getValue(evenId.key))
        assertTrue("Even name", "fleet-even-g1" in hits.getValue(evenName.key))
        assertFalse("Nordic UART", "fleet-even-g1" in hits.getValue(uart.key))
        assertTrue("E0:0A:F6", "fleet-liteon-camera-radio" in hits.getValue(liteA.key))
        assertFalse("E0:0A:F6 is not Flock", "fleet-flock-cameras" in hits.getValue(liteA.key))
        assertTrue("14:B5:CD", "fleet-liteon-camera-radio" in hits.getValue(liteB.key))
        assertFalse("14:B5:CD is not Flock", "fleet-flock-cameras" in hits.getValue(liteB.key))
        assertTrue("Flock name already covers FlockCam", "fleet-flock-cameras" in hits.getValue(flockCam.key))
        assertFalse("RWLS is not Flock", "fleet-flock-cameras" in hits.getValue(rwls.key))
        assertTrue(liteA.copy(fleetIds = hits.getValue(liteA.key)).attentionNotes(stock).isEmpty())
    }

    @Test
    fun catalog90FrenchPlateAndNamedDronesStayNarrow() {
        val stock = DefaultCatalog.fleets()
        val engine = SignatureEngine()
        val added = listOf(
            "fleet-tello", "fleet-potensic", "fleet-holystone", "fleet-hubsan",
            "fleet-yuneec", "fleet-swellpro", "fleet-crazyflie",
        )
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        for (id in added) {
            val fleet = stock.single { it.id == id }
            assertEquals(id, SignatureClass.DRONE, fleet.kind)
            assertTrue(fleet.matchAny)
            assertTrue(fleet.attentionNote.isBlank())
            assertTrue(id in watched)
        }
        val remote = stock.single { it.id == "fleet-remote-id" }
        assertTrue(remote.rules.any { it.kind == RuleKind.VENDOR_IE_OUI && it.text.equals("6A:5C:35", true) })
        assertTrue(remote.rules.any { it.kind == RuleKind.VENDOR_IE_OUI && it.text.equals("FA:0B:BC", true) })
        val crazy = stock.single { it.id == "fleet-crazyflie" }
        assertFalse(crazy.rules.any { it.kind == RuleKind.MANUFACTURER_ID })
        assertTrue(stock.single { it.id == "fleet-parrot" }.rules.any {
            it.kind == RuleKind.NAME_GLOB && it.text == "Skycontroller*"
        })

        val french = wifi("02:00:00:00:00:01", "RID", "6A:5C:35")
        val astm = wifi("02:00:00:00:00:02", "RID", "FA:0B:BC")
        val nan = wifi("02:00:00:00:00:03", "RID", "50:6F:9A")
        val parrotOui = wifi("90:3A:E6:11:22:33", "Home")
        val tello = wifi("02:00:00:00:00:11", "TELLO-ABCDEF")
        val talent = wifi("02:00:00:00:00:12", "RMTT-9AFF2A")
        val telloMid = wifi("02:00:00:00:00:13", "my TELLO")
        val dji = wifi("02:00:00:00:00:14", "DJI-Mini")
        val potensic = wifi("02:00:00:00:00:21", "Potensic-ATOM-1")
        val atom = wifi("02:00:00:00:00:22", "ATOM-1")
        val holy = wifi("02:00:00:00:00:31", "HolyStoneFPV-1")
        val holySpaced = wifi("02:00:00:00:00:32", "Holy Stone HS720")
        val fpv = wifi("02:00:00:00:00:33", "FPV_WIFI")
        val hubsan = wifi("02:00:00:00:00:41", "HUBSAN-Zino")
        val exo = wifi("02:00:00:00:00:42", "EXO-1234")
        val yuneec = wifi("02:00:00:00:00:51", "Yuneec-H520")
        val typhoon = wifi("02:00:00:00:00:52", "Typhoon")
        val swell = wifi("02:00:00:00:00:61", "SwellPro-Splash")
        val crazyName = wifi("02:00:00:00:00:71", "Crazyflie")
        val bitcraze = tagged("81", "", 0x01C5)
        val sky = wifi("02:00:00:00:00:91", "Skycontroller 3")
        val autelSsid = wifi("02:00:00:00:00:A1", "default-ssid")
        val elrs = wifi("02:00:00:00:00:A2", "ExpressLRS")
        val radios = listOf(
            french, astm, nan, parrotOui, tello, talent, telloMid, dji,
            potensic, atom, holy, holySpaced, fpv, hubsan, exo, yuneec, typhoon,
            swell, crazyName, bitcraze, sky, autelSsid, elrs,
        )
        val hits = engine.match(radios, stock)
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("French plate", "fleet-remote-id" in ids(french))
        assertTrue("ASTM plate", "fleet-remote-id" in ids(astm))
        assertFalse("NAN is not Remote ID", "fleet-remote-id" in ids(nan))
        assertFalse("Parrot OUI alone", "fleet-parrot" in ids(parrotOui))
        assertTrue("Tello", "fleet-tello" in ids(tello))
        assertFalse("Tello is not DJI", "fleet-dji" in ids(tello))
        assertTrue("Tello Talent", "fleet-tello" in ids(talent))
        assertFalse("TELLO in the middle", "fleet-tello" in ids(telloMid))
        assertTrue("DJI name", "fleet-dji" in ids(dji))
        assertFalse("DJI name is not Tello", "fleet-tello" in ids(dji))
        assertTrue("Potensic", "fleet-potensic" in ids(potensic))
        assertFalse("bare ATOM", "fleet-potensic" in ids(atom))
        assertTrue("HolyStone", "fleet-holystone" in ids(holy))
        assertTrue("Holy Stone", "fleet-holystone" in ids(holySpaced))
        assertFalse("generic FPV", "fleet-holystone" in ids(fpv))
        assertTrue("Hubsan", "fleet-hubsan" in ids(hubsan))
        assertFalse("bare EXO", "fleet-hubsan" in ids(exo))
        assertTrue("Yuneec", "fleet-yuneec" in ids(yuneec))
        assertFalse("Typhoon", "fleet-yuneec" in ids(typhoon))
        assertTrue("SwellPro", "fleet-swellpro" in ids(swell))
        assertTrue("Crazyflie name", "fleet-crazyflie" in ids(crazyName))
        assertFalse("Bitcraze company id", "fleet-crazyflie" in ids(bitcraze))
        assertTrue("Skycontroller", "fleet-parrot" in ids(sky))
        assertFalse("default-ssid", "fleet-autel" in ids(autelSsid))
        assertTrue(added.none { id -> id in ids(elrs) })
        for (id in added) {
            assertTrue(wifi("02:11:22:33:44:55", "Home").let { quiet ->
                engine.match(listOf(quiet), stock).getValue(quiet.key).contains(id).not()
            })
        }
    }

    @Test
    fun catalog91DjiPowerIsHomeNotDrone() {
        val stock = DefaultCatalog.fleets()
        val engine = SignatureEngine()
        val power = stock.single { it.id == "fleet-dji-power" }
        assertEquals(SignatureClass.HOME, power.kind)
        assertTrue(power.matchAny)
        assertTrue(power.attentionNote.isBlank())
        assertEquals(RadioKind.BLE, power.rules.single().radio)
        assertEquals("Power2000*", power.rules.single().text)
        assertFalse(
            "DJI Power is not bookmarked",
            "fleet-dji-power" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId },
        )
        assertTrue(stock.single { it.id == "fleet-dji" }.notes.contains("DJI Power"))

        val named = tagged("01", "Power2000-1006HZ", 0x08AA)
            .copy(manufacturerDataHex = "941110E4B063D0AA76")
        val lower = tagged("02", "power2000-1006hz", 0x08AA)
            .copy(manufacturerDataHex = "9411")
        val nameOnly = tagged("03", "Power2000", null)
        val mid = tagged("04", "my Power2000", 0x08AA)
            .copy(manufacturerDataHex = "9411")
        val bare = tagged("05", "", 0x08AA)
            .copy(manufacturerDataHex = "9411")
        val djiName = tagged("06", "DJI Mini 4", 0x08AA)
            .copy(manufacturerDataHex = "7000")
        val wifiPower = wifi("02:00:00:00:00:77", "Power2000-1006HZ")
        val hits = engine.match(listOf(named, lower, nameOnly, mid, bare, djiName, wifiPower), stock)
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("named power", "fleet-dji-power" in ids(named))
        assertFalse("named power is not the drone row", "fleet-dji" in ids(named))
        assertTrue("lowercase name", "fleet-dji-power" in ids(lower))
        assertFalse("lowercase name is not the drone row", "fleet-dji" in ids(lower))
        assertTrue("name only", "fleet-dji-power" in ids(nameOnly))
        assertFalse("name only is not the drone row", "fleet-dji" in ids(nameOnly))
        assertFalse("Power2000 in the middle", "fleet-dji-power" in ids(mid))
        assertTrue("middle name with company stays DJI", "fleet-dji" in ids(mid))
        assertFalse("bare company is not Power", "fleet-dji-power" in ids(bare))
        assertTrue("bare company stays DJI", "fleet-dji" in ids(bare))
        assertTrue("DJI name", "fleet-dji" in ids(djiName))
        assertFalse("DJI name is not Power", "fleet-dji-power" in ids(djiName))
        assertFalse("Wi-Fi Power2000", "fleet-dji-power" in ids(wifiPower))
        val guess = DeviceExplain.guess(named, listOf("DJI Power"))
        assertTrue(guess.headline, guess.headline.contains("Power station", ignoreCase = true))
        assertFalse(guess.headline, guess.headline.contains("drone", ignoreCase = true))
    }

    private fun tagged(tail: String, name: String, manufacturerId: Int?) =
        ble(name = name, manufacturerId = manufacturerId)
            .copy(key = "BLE:$tail", mac = "AA:BB:CC:DD:EE:$tail")

    private fun wifi(mac: String, name: String = "Home", vendorIe: String? = null) = ble(name = name).copy(
        key = "WIFI:$mac",
        kind = RadioKind.WIFI,
        mac = mac,
        randomized = false,
        vendorIeOuis = if (vendorIe == null) emptyList() else listOf(vendorIe),
    )

    private fun ble(
        name: String = "",
        manufacturerId: Int? = null,
        manufacturerDataHex: String = "",
        serviceUuids: List<String> = emptyList(),
    ) = Sighting(
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
        serviceUuids = serviceUuids,
        manufacturerId = manufacturerId,
        manufacturerDataHex = manufacturerDataHex,
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(
            mfgRecords = if (manufacturerId != null) {
                listOf(MfgRecord(manufacturerId, manufacturerDataHex))
            } else {
                emptyList()
            },
        ),
    )
}
