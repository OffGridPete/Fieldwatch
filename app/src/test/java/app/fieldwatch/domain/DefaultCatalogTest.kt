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
        assertEquals(listOf("fleet-sidewalk", "fleet-rayneo"), stock.filter { !it.matchAny }.map { it.id })
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
        assertFalse(flock.rules.any { it.kind == RuleKind.NAME_CONTAINS && it.text == "Flock" })
        assertTrue(flock.rules.any { it.kind == RuleKind.NAME_GLOB && it.text == "Flock-*" })

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
        assertFalse("FlockCam is not Flock-", "fleet-flock-cameras" in hits.getValue(flockCam.key))
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

    @Test
    fun catalog99OlderDjiDroneIdLabelsDji() {
        val stock = DefaultCatalog.fleets()
        val dji = stock.single { it.id == "fleet-dji" }
        assertTrue(dji.rules.any { it.kind == RuleKind.VENDOR_IE_OUI && it.text.equals("26:37:12", true) })
        assertFalse(dji.rules.any { it.kind == RuleKind.OUI })
        val beacon = wifi("60:60:1F:11:22:33", "", "26:37:12")
        val macOnly = wifi("60:60:1F:44:55:66", "")
        val hits = SignatureEngine().match(listOf(beacon, macOnly), stock)
        assertTrue("older DroneID beacon", "fleet-dji" in hits.getValue(beacon.key))
        assertFalse("older DroneID is not Remote ID", "fleet-remote-id" in hits.getValue(beacon.key))
        assertFalse("DJI address alone", "fleet-dji" in hits.getValue(macOnly.key))
    }

    @Test
    fun catalog92DropsBadLiteOnOuisNamesMetaDisplayAndSoftensAxon() {
        val stock = DefaultCatalog.fleets()
        val engine = SignatureEngine()
        val lite = stock.single { it.id == "fleet-liteon-camera-radio" }
        val meta = stock.single { it.id == "fleet-meta-glasses" }
        val axon = stock.single { it.id == "fleet-axon" }
        val liteOuis = lite.rules.filter { it.kind == RuleKind.OUI }.map { it.text.uppercase() }.toSet()
        assertTrue(liteOuis.contains("F8:A2:D6"))
        assertTrue(liteOuis.contains("E0:0A:F6"))
        assertTrue(liteOuis.contains("14:B5:CD"))
        assertFalse(liteOuis.contains("48:27:EA"))
        assertFalse(liteOuis.contains("82:6B:F2"))
        assertTrue(lite.attentionNote.isBlank())
        assertEquals(SignatureClass.CAMERA, lite.kind)
        assertFalse("fleet-liteon-camera-radio" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })
        assertTrue(meta.rules.any { it.kind == RuleKind.NAME_GLOB && it.text == "Meta RB Display*" && it.radio == RadioKind.BLE })
        assertFalse(meta.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text.equals("FD5F", true) })
        assertTrue(meta.notes.contains("Meta Display"))
        assertTrue(axon.notes.contains("fixed ALPR"))
        assertTrue(axon.attentionNote.contains("fixed readers"))
        assertTrue(axon.attentionNote.contains("BWCDEVICE"))
        assertTrue(axon.rules.any { it.kind == RuleKind.OUI && it.text.equals("00:25:DF", true) })
        assertTrue(axon.rules.any {
            it.kind == RuleKind.SERVICE_DATA && it.dataPrefixHex.equals("425743444556494345", true)
        })
        assertFalse(stock.any { fleet ->
            fleet.rules.any { rule ->
                (rule.kind == RuleKind.MANUFACTURER_ID && rule.companyId == 0xFD5F) ||
                    (rule.kind == RuleKind.SERVICE_UUID && rule.text.equals("FD5F", true))
            }
        })

        val samsung = wifi("48:27:EA:11:22:33")
        val local = wifi("82:6B:F2:11:22:33")
        val kept = wifi("F8:A2:D6:11:22:33")
        val display = ble(name = "Meta RB Display 0053").copy(key = "BLE:21", mac = "AA:BB:CC:DD:EE:21")
        val short = ble(name = "Meta RB").copy(key = "BLE:22", mac = "AA:BB:CC:DD:EE:22")
        val mid = ble(name = "my Meta RB Display").copy(key = "BLE:23", mac = "AA:BB:CC:DD:EE:23")
        val fd5f = ble(name = "", serviceUuids = listOf("0000FD5F-0000-1000-8000-00805F9B34FB"))
            .copy(key = "BLE:24", mac = "AA:BB:CC:DD:EE:24")
        val hits = engine.match(listOf(samsung, local, kept, display, short, mid, fd5f), stock)
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertFalse("Samsung prefix", "fleet-liteon-camera-radio" in ids(samsung))
        assertFalse("local prefix", "fleet-liteon-camera-radio" in ids(local))
        assertTrue("LiteOn F8:A2:D6", "fleet-liteon-camera-radio" in ids(kept))
        assertTrue("Meta Display name", "fleet-meta-glasses" in ids(display))
        assertFalse("short Meta RB", "fleet-meta-glasses" in ids(short))
        assertFalse("Display not at the start", "fleet-meta-glasses" in ids(mid))
        assertFalse("FD5F alone", "fleet-meta-glasses" in ids(fd5f))
    }

    @Test
    fun catalog93TightensFlockPenguinFsExtAndLiteOn() {
        val stock = DefaultCatalog.fleets()
        val engine = SignatureEngine()
        val flock = stock.single { it.id == "fleet-flock-cameras" }
        val penguin = stock.single { it.id == "fleet-penguin" }
        val fs = stock.single { it.id == "fleet-fs-ext-battery" }
        val lite = stock.single { it.id == "fleet-liteon-camera-radio" }
        assertEquals(
            setOf("B4:1E:52"),
            flock.rules.filter { it.kind == RuleKind.OUI }.map { it.text.uppercase() }.toSet(),
        )
        assertEquals(listOf("Flock-*"), flock.rules.filter { it.kind == RuleKind.NAME_GLOB }.map { it.text })
        assertFalse(flock.rules.any { it.kind == RuleKind.NAME_CONTAINS })
        assertEquals(listOf("Penguin-*"), penguin.rules.filter { it.kind == RuleKind.NAME_GLOB }.map { it.text })
        assertFalse(penguin.rules.any { it.kind == RuleKind.NAME_CONTAINS })
        assertTrue(penguin.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x09C8 })
        assertTrue(fs.rules.none { it.kind == RuleKind.OUI })
        assertTrue(fs.rules.any { it.kind == RuleKind.NAME_CONTAINS && it.text == "FS Ext Battery" })
        assertEquals(listOf("FS Ext*"), fs.rules.filter { it.kind == RuleKind.NAME_GLOB }.map { it.text })
        val liteOuis = lite.rules.filter { it.kind == RuleKind.OUI }.map { it.text.uppercase() }.toSet()
        assertFalse(liteOuis.contains("B8:35:32"))
        assertTrue(liteOuis.contains("08:3A:88"))
        assertTrue(liteOuis.contains("C0:35:32"))
        assertTrue(lite.notes.contains("Universal Global Scientific"))
        assertTrue(lite.attentionNote.isBlank())

        val pole = wifi("B4:1E:52:00:00:01", "Home")
        val flockName = wifi("02:11:22:33:44:70", "Flock-ABCDEF")
        val flockLong = wifi("02:11:22:33:44:71", "Flock-ABCDEFGH")
        val falcons = wifi("02:11:22:33:44:72", "Atlanta-Falcons")
        val flck = wifi("02:11:22:33:44:73", "FLCK-9")
        val flockCam = wifi("02:11:22:33:44:74", "FlockCam-9")
        val penguinOld = wifi("02:11:22:33:44:80", "Penguin")
        val penguinName = wifi("02:11:22:33:44:81", "Penguin-12345")
        val penguinMid = wifi("02:11:22:33:44:82", "my Penguin-1")
        val digits = wifi("02:11:22:33:44:83", "7202302200")
        val xuntong = tagged("91", "7202302200", 0x09C8)
        val pack = wifi("02:11:22:33:44:90", "FS Ext Battery")
        val packTail = wifi("02:11:22:33:44:91", "FS Ext 2")
        val fsStar = wifi("02:11:22:33:44:92", "FS_1234")
        val silabs = wifi("04:0D:84:11:22:33", "Home")
        val bogus = wifi("B8:35:32:11:22:33", "Home")
        val ugsi = wifi("08:3A:88:11:22:33", "Home")
        val hits = engine.match(
            listOf(
                pole, flockName, flockLong, falcons, flck, flockCam,
                penguinOld, penguinName, penguinMid, digits, xuntong,
                pack, packTail, fsStar, silabs, bogus, ugsi,
            ),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("Flock OUI", "fleet-flock-cameras" in ids(pole))
        assertTrue("Flock- name", "fleet-flock-cameras" in ids(flockName))
        assertTrue("longer Flock- name", "fleet-flock-cameras" in ids(flockLong))
        assertFalse("Atlanta-Falcons", "fleet-flock-cameras" in ids(falcons))
        assertFalse("FLCK", "fleet-flock-cameras" in ids(flck))
        assertFalse("FlockCam", "fleet-flock-cameras" in ids(flockCam))
        assertFalse("bare Penguin", "fleet-penguin" in ids(penguinOld))
        assertTrue("Penguin- name", "fleet-penguin" in ids(penguinName))
        assertFalse("Penguin- in the middle", "fleet-penguin" in ids(penguinMid))
        assertFalse("10-digit name alone", "fleet-penguin" in ids(digits))
        assertTrue("XUNTONG id", "fleet-penguin" in ids(xuntong))
        assertTrue("FS Ext Battery name", "fleet-fs-ext-battery" in ids(pack))
        assertTrue("FS Ext prefix", "fleet-fs-ext-battery" in ids(packTail))
        assertFalse("FS_ wildcard", "fleet-fs-ext-battery" in ids(fsStar))
        assertFalse("Silicon Labs prefix", "fleet-fs-ext-battery" in ids(silabs))
        assertFalse("unassigned prefix", "fleet-liteon-camera-radio" in ids(bogus))
        assertTrue("UGSI prefix stays on the module row", "fleet-liteon-camera-radio" in ids(ugsi))
        assertFalse("UGSI is not Flock", "fleet-flock-cameras" in ids(ugsi))
    }

    @Test
    fun catalog94AddsFlipperServiceTilePreactivationAndPolar() {
        val stock = DefaultCatalog.fleets()
        val engine = SignatureEngine()
        val flipper = stock.single { it.id == "fleet-flipper" }
        val tile = stock.single { it.id == "fleet-tile" }
        val polar = stock.single { it.id == "fleet-polar" }
        assertEquals(SignatureClass.WEARABLE, polar.kind)
        assertTrue(polar.attentionNote.isBlank())
        assertFalse("fleet-polar" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })
        for (uuid in listOf("3080", "3081", "3082", "3083")) {
            assertTrue(uuid, flipper.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text.equals(uuid, true) })
        }
        assertTrue(tile.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text.equals("FEEC", true) })
        assertTrue(polar.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x006B })
        assertTrue(polar.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text.equals("FEEE", true) })
        assertTrue(polar.rules.any { it.kind == RuleKind.NAME_GLOB && it.text == "Polar*" && it.radio == RadioKind.BLE })
        assertTrue(DeviceExplain.uuidGloss("FEEC")!!.contains("Tile"))
        assertTrue(DeviceExplain.uuidGloss("FEEE")!!.contains("Polar"))

        val renamed = ble(name = "Widget", serviceUuids = listOf("00003082-0000-1000-8000-00805F9B34FB"))
            .copy(key = "BLE:f1", mac = "AA:BB:CC:DD:EE:31")
        val tileNew = ble(name = "", serviceUuids = listOf("0000FEEC-0000-1000-8000-00805F9B34FB"))
            .copy(key = "BLE:f2", mac = "AA:BB:CC:DD:EE:32")
        val h10 = ble(
            name = "Polar H10 E7BAC71A",
            manufacturerId = 0x006B,
            manufacturerDataHex = "37060000",
            serviceUuids = listOf(
                "0000180D-0000-1000-8000-00805F9B34FB",
                "0000FEEE-0000-1000-8000-00805F9B34FB",
                "0000FEA5-0000-1000-8000-00805F9B34FB",
            ),
        ).copy(key = "BLE:f3", mac = "AA:BB:CC:DD:EE:33")
        val gopro = ble(name = "GoPro 1234", serviceUuids = listOf("0000FEA5-0000-1000-8000-00805F9B34FB"))
            .copy(key = "BLE:f4", mac = "AA:BB:CC:DD:EE:34")
        val hrOnly = ble(name = "", serviceUuids = listOf("0000180D-0000-1000-8000-00805F9B34FB"))
            .copy(key = "BLE:f5", mac = "AA:BB:CC:DD:EE:35")
        val hits = engine.match(listOf(renamed, tileNew, h10, gopro, hrOnly), stock)
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("renamed Flipper service", "fleet-flipper" in ids(renamed))
        assertTrue("Tile FEEC", "fleet-tile" in ids(tileNew))
        assertTrue("Polar H10", "fleet-polar" in ids(h10))
        assertFalse("Polar strap is not a camera", "fleet-gopro" in ids(h10))
        assertTrue("GoPro still matches", "fleet-gopro" in ids(gopro))
        assertFalse("GoPro is not Polar", "fleet-polar" in ids(gopro))
        assertFalse("heart rate alone", "fleet-polar" in ids(hrOnly))
        assertEquals(
            TrackerMatch.Kind.WEARABLE,
            TrackerMatch.kind(h10.copy(fleetIds = setOf("fleet-polar")), mapOf("fleet-polar" to "Polar")),
        )
    }

    @Test
    fun bitchatMatchesMainnetServiceUuidOnly() {
        val stock = DefaultCatalog.fleets()
        val row = stock.single { it.id == "fleet-bitchat" }
        assertEquals(SignatureClass.MESH, row.kind)
        assertTrue(row.attentionNote.isBlank())
        assertFalse("fleet-bitchat" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })
        assertEquals(
            listOf("f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5c"),
            row.rules.map { it.text },
        )
        val mainnet = ble(
            name = "",
            serviceUuids = listOf("F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C"),
        ).copy(key = "BLE:b1", mac = "AA:BB:CC:DD:EE:51")
        val testnet = ble(
            name = "",
            serviceUuids = listOf("F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5A"),
        ).copy(key = "BLE:b2", mac = "AA:BB:CC:DD:EE:52")
        val characteristic = ble(
            name = "",
            serviceUuids = listOf("A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D"),
        ).copy(key = "BLE:b3", mac = "AA:BB:CC:DD:EE:53")
        val namedOnly = ble(name = "Bitchat").copy(key = "BLE:b4", mac = "AA:BB:CC:DD:EE:54")
        val hits = SignatureEngine().match(listOf(mainnet, testnet, characteristic, namedOnly), stock)
        assertTrue("mainnet service UUID", "fleet-bitchat" in hits.getValue(mainnet.key))
        assertFalse("testnet UUID", "fleet-bitchat" in hits.getValue(testnet.key))
        assertFalse("characteristic UUID", "fleet-bitchat" in hits.getValue(characteristic.key))
        assertFalse("name alone", "fleet-bitchat" in hits.getValue(namedOnly.key))
        assertTrue(DeviceExplain.guess(mainnet, listOf("Bitchat")).headline.contains("Bitchat BLE mesh"))
    }

    @Test
    fun chipoloMatchesServiceUuidNotNeighborUuids() {
        val stock = DefaultCatalog.fleets()
        val chipolo = stock.single { it.id == "fleet-chipolo" }
        assertTrue(chipolo.attentionNote.isBlank())
        assertFalse("fleet-chipolo" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })
        assertTrue(chipolo.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text.equals("FE33", true) })
        assertFalse(chipolo.rules.any { it.text.equals("FE65", true) || it.text.equals("FE34", true) })
        assertTrue(DeviceExplain.uuidGloss("FE33")!!.contains("Chipolo"))
        assertEquals(298, stock.size)

        val unnamed = ble(name = "", serviceUuids = listOf("FE33"))
            .copy(key = "BLE:c1", mac = "AA:BB:CC:DD:EE:41")
        val longForm = ble(name = "", serviceUuids = listOf("0000FE33-0000-1000-8000-00805F9B34FB"))
            .copy(key = "BLE:c2", mac = "AA:BB:CC:DD:EE:42")
        val serviceData = ble(name = "").copy(
            key = "BLE:c3",
            mac = "AA:BB:CC:DD:EE:43",
            facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FE33", "01"))),
        )
        val named = ble(name = "Chipolo")
            .copy(key = "BLE:c4", mac = "AA:BB:CC:DD:EE:44")
        val fe65 = ble(name = "", serviceUuids = listOf("FE65"))
            .copy(key = "BLE:c5", mac = "AA:BB:CC:DD:EE:45")
        val fe34 = ble(name = "", serviceUuids = listOf("FE34"))
            .copy(key = "BLE:c6", mac = "AA:BB:CC:DD:EE:46")
        val feaa = ble(name = "", serviceUuids = listOf("FEAA"))
            .copy(key = "BLE:c7", mac = "AA:BB:CC:DD:EE:47")
        val hits = SignatureEngine().match(
            listOf(unnamed, longForm, serviceData, named, fe65, fe34, feaa),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("unnamed FE33", "fleet-chipolo" in ids(unnamed))
        assertTrue("long-form FE33", "fleet-chipolo" in ids(longForm))
        assertTrue("service data FE33", "fleet-chipolo" in ids(serviceData))
        assertTrue("Chipolo name", "fleet-chipolo" in ids(named))
        assertFalse("FE65", "fleet-chipolo" in ids(fe65))
        assertFalse("FE34", "fleet-chipolo" in ids(fe34))
        assertFalse("FEAA is Find Hub, not Chipolo", "fleet-chipolo" in ids(feaa))
    }

    @Test
    fun pebblebeeMatchesServiceFa25AndKeepsTheName() {
        val stock = DefaultCatalog.fleets()
        val pebble = stock.single { it.id == "fleet-pebblebee" }
        assertTrue(pebble.attentionNote.isBlank())
        assertFalse("fleet-pebblebee" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })
        assertTrue(pebble.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text.equals("FA25", true) })
        assertTrue(pebble.notes.contains("FA25"))

        val unnamed = ble(name = "", serviceUuids = listOf("FA25"))
            .copy(key = "BLE:p1", mac = "AA:BB:CC:DD:EE:61")
        val longForm = ble(name = "", serviceUuids = listOf("0000FA25-0000-1000-8000-00805F9B34FB"))
            .copy(key = "BLE:p2", mac = "AA:BB:CC:DD:EE:62")
        val serviceData = ble(name = "").copy(
            key = "BLE:p3",
            mac = "AA:BB:CC:DD:EE:63",
            facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FA25", "01"))),
        )
        val named = ble(name = "Pebblebee")
            .copy(key = "BLE:p4", mac = "AA:BB:CC:DD:EE:64")
        val moto = ble(name = "moto tag")
            .copy(key = "BLE:p5", mac = "AA:BB:CC:DD:EE:65")
        val neighbor = ble(name = "", serviceUuids = listOf("FA26"))
            .copy(key = "BLE:p6", mac = "AA:BB:CC:DD:EE:66")
        val hits = SignatureEngine().match(
            listOf(unnamed, longForm, serviceData, named, moto, neighbor),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("unnamed FA25", "fleet-pebblebee" in ids(unnamed))
        assertTrue("long-form FA25", "fleet-pebblebee" in ids(longForm))
        assertTrue("service data FA25", "fleet-pebblebee" in ids(serviceData))
        assertTrue("Pebblebee name", "fleet-pebblebee" in ids(named))
        assertTrue("moto tag name", "fleet-pebblebee" in ids(moto))
        assertFalse("FA26", "fleet-pebblebee" in ids(neighbor))
        assertFalse("FA25 is not Find Hub", "fleet-find-hub" in ids(unnamed))
        assertFalse("FA25 is not DULT", "fleet-dult" in ids(unnamed))
    }

    @Test
    fun smartTagMatchesNameOrFd5aNotSamsungCompanyId() {
        val stock = DefaultCatalog.fleets()
        val fleet = stock.single { it.id == "fleet-smarttag" }
        assertTrue(fleet.matchAny)
        assertTrue(fleet.attentionNote.isBlank())
        assertFalse(fleet.rules.any { it.kind == RuleKind.MANUFACTURER_ID })
        assertTrue(fleet.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text.equals("FD5A", true) })
        val engine = SignatureEngine()
        val tv = ble(name = "", manufacturerId = 0x0075, manufacturerDataHex = "0102")
            .copy(key = "BLE:tv", mac = "AA:BB:CC:DD:EE:75")
        val named = ble(name = "SmartTag").copy(key = "BLE:st", mac = "AA:BB:CC:DD:EE:76")
        val spaced = ble(name = "Galaxy Smart Tag").copy(key = "BLE:sp", mac = "AA:BB:CC:DD:EE:77")
        val uuid = ble(name = "", serviceUuids = listOf("FD5A"))
            .copy(key = "BLE:fd", mac = "AA:BB:CC:DD:EE:78")
        val hits = engine.match(listOf(tv, named, spaced, uuid), stock)
        assertFalse("Samsung company ID", "fleet-smarttag" in hits.getValue(tv.key))
        assertFalse("company id is not a Samsung TV", "fleet-samsung-tv" in hits.getValue(tv.key))
        assertTrue("SmartTag name", "fleet-smarttag" in hits.getValue(named.key))
        assertTrue("Smart Tag name", "fleet-smarttag" in hits.getValue(spaced.key))
        assertTrue("FD5A", "fleet-smarttag" in hits.getValue(uuid.key))
    }

    @Test
    fun catalog112LabelsRepeatedSitFamiliesAndSkipsTheBroadKeys() {
        val stock = DefaultCatalog.fleets()
        val added = listOf(
            "fleet-samsung-tv", "fleet-google-cast", "fleet-levoit", "fleet-melk", "fleet-midea",
            "fleet-zebra", "fleet-jlab", "fleet-scentair", "fleet-escort", "fleet-gard-pro",
            "fleet-chb", "fleet-vizio", "fleet-owlet", "fleet-tandem", "fleet-sleeptracker",
            "fleet-whoop", "fleet-soundcore", "fleet-hume", "fleet-platco", "fleet-harpbt",
            "fleet-gt201", "fleet-neewer", "fleet-70mai", "fleet-blackvue", "fleet-galaxy-watch",
            "fleet-galaxy-buds",
        )
        assertEquals(added.toSet(), stock.map { it.id }.filter { it in added }.toSet())
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        added.forEach { id ->
            val row = stock.single { it.id == id }
            assertTrue(id, row.builtIn)
            assertTrue(id, row.attentionNote.isBlank())
            assertTrue(id, row.matchAny)
            assertFalse(id, id in watched)
            assertFalse(id, row.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x0075 })
            assertFalse(id, row.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x06D0 })
        }
        val tv = stock.single { it.id == "fleet-samsung-tv" }
        assertEquals(SignatureClass.HOME, tv.kind)
        assertTrue(tv.rules.none { it.kind == RuleKind.MANUFACTURER_ID })
        assertEquals(RadioKind.BLE, tv.rules.first().radio)

        val qled = ble(name = "43\" QLED").copy(key = "BLE:qled", mac = "B0:E4:5C:00:00:01")
        val frame = ble(name = "65\" The Frame").copy(key = "BLE:frame", mac = "28:07:08:00:00:01")
        val bracket = ble(name = "[TV] Samsung BE 65 TV").copy(key = "BLE:be", mac = "F8:4E:58:00:00:01")
        val bar = ble(name = "[AV] HW-Q800D").copy(key = "BLE:av", mac = "28:E6:A9:00:00:01")
        val crystal = ble(name = "55\" Crystal UHD").copy(key = "BLE:uhd", mac = "B8:B4:09:00:00:01")
        val cu = ble(name = "Samsung CU7000 55").copy(key = "BLE:cu", mac = "E0:03:6B:00:00:01")
        val watch = ble(name = "Galaxy Watch7 (DFHD)", manufacturerId = 0x0075)
            .copy(key = "BLE:watch", mac = "4F:71:D9:00:00:01")
        val buds = ble(name = "Buds FE", manufacturerId = 0x0075)
            .copy(key = "BLE:buds", mac = "E0:61:7B:00:00:01")
        val buds2 = ble(name = "Galaxy Buds2 Pro LE").copy(key = "BLE:buds2", mac = "C4:94:1F:00:00:01")
        val picture = ble(name = "Picture Frame").copy(key = "BLE:pic", mac = "AA:BB:CC:00:00:11")
        val wifiTv = wifi("AA:BB:CC:00:00:12", "[TV] Samsung BE 65 TV")
        val oven = ble(name = "[oven]Samsung(152Y)", manufacturerId = 0x0075)
            .copy(key = "BLE:oven", mac = "50:FD:D5:00:00:01")
        val cast = ble(name = "Kitchen Display", serviceUuids = listOf("FEA0"))
            .copy(key = "BLE:cast", mac = "4A:D8:DC:00:00:01")
        val castLong = ble(
            name = "",
            serviceUuids = listOf("0000FEA0-0000-1000-8000-00805F9B34FB"),
        ).copy(key = "BLE:cast2", mac = "5C:67:91:00:00:01")
        val roomOnly = ble(name = "Kitchen Display").copy(key = "BLE:room", mac = "AA:BB:CC:00:00:13")
        val lap = ble(name = "LAP-V102S-WUS", manufacturerId = 0x06D0)
            .copy(key = "BLE:lap", mac = "24:58:7C:00:00:01")
        val core = ble(name = "Core300s", manufacturerId = 0x06D0)
            .copy(key = "BLE:core", mac = "94:3C:C6:00:00:01")
        val espressif = ble(name = "CTO-R251S-KUS", manufacturerId = 0x06D0)
            .copy(key = "BLE:cto", mac = "08:D1:F9:00:00:01")
        val lamp = ble(name = "MELK-OB12").copy(key = "BLE:melk", mac = "BE:FF:A1:00:00:01")
        val midea = ble(name = "net", manufacturerId = 0x06A8)
            .copy(key = "BLE:midea", mac = "C4:39:60:00:00:01")
        val netOnly = ble(name = "net").copy(key = "BLE:net", mac = "AA:BB:CC:00:00:14")
        val zebra = ble(name = "D8N251305279", serviceUuids = listOf("FE79"))
            .copy(key = "BLE:zebra", mac = "60:95:32:00:00:01")
        val jlab = ble(name = "T21", manufacturerId = 0x05D6)
            .copy(key = "BLE:jlab", mac = "53:A6:6B:00:00:01")
        val t21 = ble(name = "T21").copy(key = "BLE:t21", mac = "AA:BB:CC:00:00:15")
        val scent = ble(name = "ScentAir", manufacturerId = 0x02E5, serviceUuids = listOf("1811"))
            .copy(key = "BLE:scent", mac = "AC:15:18:00:00:01")
        val alert = ble(name = "", serviceUuids = listOf("1811"))
            .copy(key = "BLE:alert", mac = "AA:BB:CC:00:00:16")
        val escort = ble(
            name = "ESCORT REDLINE 360c",
            serviceUuids = listOf("B5E22DE9-31EE-42AB-BE6A-9BE0837AA344"),
        ).copy(key = "BLE:escort", mac = "00:25:CA:00:00:01")
        val escortName = ble(name = "MAX 360c").copy(key = "BLE:max", mac = "00:25:CA:00:00:02")
        val gard = ble(name = "Gard Pro UItra 2+").copy(key = "BLE:gard", mac = "78:02:B7:00:00:01")
        val chb = ble(name = "CHB202044004360", serviceUuids = listOf("FFA0"))
            .copy(key = "BLE:chb", mac = "2F:27:98:00:00:01")
        val chbShort = ble(name = "CHB1").copy(key = "BLE:chb1", mac = "AA:BB:CC:00:00:17")
        val ffa0 = ble(name = "", serviceUuids = listOf("FFA0"))
            .copy(key = "BLE:ffa0", mac = "AA:BB:CC:00:00:18")
        val vizio = ble(name = "VIZIO Mobile App", manufacturerId = 0x0058)
            .copy(key = "BLE:vizio", mac = "F7:2B:6B:00:00:01")
        val owlet = ble(
            name = "OB",
            serviceUuids = listOf("C5163C4B-9B63-570D-A3A8-407716F04276"),
        ).copy(key = "BLE:owlet", mac = "F0:6E:73:00:00:01")
        val ob = ble(name = "OB").copy(key = "BLE:ob", mac = "AA:BB:CC:00:00:19")
        val pump = ble(name = "tslim X2 ***349", serviceUuids = listOf("FDFB"))
            .copy(key = "BLE:pump", mac = "E7:2D:F9:00:00:01")
        val sleep = ble(name = "SleepTracker").copy(key = "BLE:sleep", mac = "54:EF:FE:00:00:01")
        val whoop = ble(name = "WHOOP 5AM0005917").copy(key = "BLE:whoop", mac = "E3:98:8E:00:00:01")
        val sound = ble(name = "soundcore Sport X20").copy(key = "BLE:sound", mac = "70:2E:9B:00:00:01")
        val hume = ble(name = "Hume Band V2 B1D2").copy(key = "BLE:hume", mac = "FF:D4:77:00:00:01")
        val platco = ble(name = "Platco PR1", serviceUuids = listOf("1802"))
            .copy(key = "BLE:platco", mac = "B8:C0:65:00:00:01")
        val immediate = ble(name = "", serviceUuids = listOf("1802"))
            .copy(key = "BLE:1802", mac = "AA:BB:CC:00:00:1A")
        val harp = ble(name = "HarpBT194601559").copy(key = "BLE:harp", mac = "34:15:13:00:00:01")
        val gt = ble(name = "GT201_PV").copy(key = "BLE:gt", mac = "E0:46:E5:00:00:01")
        val light = ble(name = "NEEWER-RGB168").copy(key = "BLE:neewer", mac = "FB:43:CD:00:00:01")
        val mai = wifi("BC:FD:0C:00:00:01", "70mai_d06_258d")
        val vue = wifi("00:25:42:00:00:01", "Blackvue750XPlus-E4E73D")
        val et = ble(name = "ET-2800 Series").copy(key = "BLE:et", mac = "D6:80:8B:00:00:01")
        val osmo = wifi("54:AE:BC:00:00:01", "osmo_6e3b")
        val u6 = ble(name = "U6-LR").copy(key = "BLE:u6", mac = "60:22:32:00:00:01")
        val uxg = ble(name = "UXGLITE").copy(key = "BLE:uxg", mac = "9C:05:D6:00:00:01")

        val hits = SignatureEngine().match(
            listOf(
                qled, frame, bracket, bar, crystal, cu, watch, buds, buds2, picture, wifiTv, oven,
                cast, castLong, roomOnly, lap, core, espressif, lamp, midea, netOnly, zebra, jlab, t21,
                scent, alert, escort, escortName, gard, chb, chbShort, ffa0, vizio, owlet, ob, pump,
                sleep, whoop, sound, hume, platco, immediate, harp, gt, light, mai, vue, et, osmo, u6, uxg,
            ),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("QLED", "fleet-samsung-tv" in ids(qled))
        assertTrue("The Frame", "fleet-samsung-tv" in ids(frame))
        assertTrue("[TV]", "fleet-samsung-tv" in ids(bracket))
        assertTrue("[AV]", "fleet-samsung-tv" in ids(bar))
        assertTrue("Crystal UHD", "fleet-samsung-tv" in ids(crystal))
        assertTrue("CU7000", "fleet-samsung-tv" in ids(cu))
        assertFalse("watch name", "fleet-samsung-tv" in ids(watch))
        assertTrue("watch", "fleet-galaxy-watch" in ids(watch))
        assertFalse("buds are not a watch", "fleet-galaxy-watch" in ids(buds))
        assertTrue("Buds FE", "fleet-galaxy-buds" in ids(buds))
        assertTrue("Galaxy Buds", "fleet-galaxy-buds" in ids(buds2))
        assertFalse("Picture Frame", "fleet-samsung-tv" in ids(picture))
        assertTrue("Wi-Fi [TV] name stays off the TV row", ids(wifiTv).isEmpty())
        assertFalse("oven is not a TV", "fleet-samsung-tv" in ids(oven))
        assertTrue("Bluetooth oven", "fleet-samsung-appliance" in ids(oven))
        assertTrue("FEA0 display", "fleet-google-cast" in ids(cast))
        assertTrue("long FEA0", "fleet-google-cast" in ids(castLong))
        assertFalse("room name alone", "fleet-google-cast" in ids(roomOnly))
        assertTrue("LAP-", "fleet-levoit" in ids(lap))
        assertTrue("Core300", "fleet-levoit" in ids(core))
        assertFalse("Espressif company id", "fleet-levoit" in ids(espressif))
        assertTrue("MELK", "fleet-melk" in ids(lamp))
        assertTrue("Midea company", "fleet-midea" in ids(midea))
        assertFalse("the word net", "fleet-midea" in ids(netOnly))
        assertTrue("FE79", "fleet-zebra" in ids(zebra))
        assertTrue("JLab company on T21", "fleet-jlab" in ids(jlab))
        assertFalse("T21 name alone", "fleet-jlab" in ids(t21))
        assertTrue("ScentAir company", "fleet-scentair" in ids(scent))
        assertFalse("alert service", "fleet-scentair" in ids(alert))
        assertTrue("Escort UUID", "fleet-escort" in ids(escort))
        assertFalse("Escort name alone", "fleet-escort" in ids(escortName))
        assertTrue("Gard Pro", "fleet-gard-pro" in ids(gard))
        assertTrue("CHB2020", "fleet-chb" in ids(chb))
        assertFalse("short CHB", "fleet-chb" in ids(chbShort))
        assertFalse("FFA0", "fleet-chb" in ids(ffa0))
        assertTrue("Vizio company", "fleet-vizio" in ids(vizio))
        assertTrue("Owlet UUID", "fleet-owlet" in ids(owlet))
        assertFalse("name OB", "fleet-owlet" in ids(ob))
        assertTrue("t:slim", "fleet-tandem" in ids(pump))
        assertTrue("SleepTracker", "fleet-sleeptracker" in ids(sleep))
        assertTrue("WHOOP", "fleet-whoop" in ids(whoop))
        assertTrue("soundcore", "fleet-soundcore" in ids(sound))
        assertTrue("Hume", "fleet-hume" in ids(hume))
        assertTrue("Platco", "fleet-platco" in ids(platco))
        assertFalse("immediate alert", "fleet-platco" in ids(immediate))
        assertTrue("HarpBT", "fleet-harpbt" in ids(harp))
        assertTrue("GT201", "fleet-gt201" in ids(gt))
        assertTrue("NEEWER", "fleet-neewer" in ids(light))
        assertTrue("70mai", "fleet-70mai" in ids(mai))
        assertTrue("BlackVue", "fleet-blackvue" in ids(vue))
        assertTrue("ET- Series", "fleet-epson" in ids(et))
        assertTrue("osmo_", "fleet-osmo" in ids(osmo))
        assertFalse("osmo_ is not the aircraft row", "fleet-dji" in ids(osmo))
        assertTrue("U6-", "fleet-unifi" in ids(u6))
        assertTrue("UXG", "fleet-unifi" in ids(uxg))
    }

    @Test
    fun catalog100AddsExpressLrsXiaomiMiHomeAndEzviz() {
        val stock = DefaultCatalog.fleets()
        val elrs = stock.single { it.id == "fleet-elrs" }
        val xiaomi = stock.single { it.id == "fleet-xiaomi-mihome" }
        val ezviz = stock.single { it.id == "fleet-ezviz" }
        assertEquals(SignatureClass.DRONE, elrs.kind)
        assertEquals(SignatureClass.HOME, xiaomi.kind)
        assertEquals(SignatureClass.CAMERA, ezviz.kind)
        assertTrue(elrs.attentionNote.isBlank())
        assertTrue(xiaomi.attentionNote.isBlank())
        assertTrue(ezviz.attentionNote.isBlank())
        assertEquals(RadioKind.WIFI, elrs.rules.single().radio)
        assertEquals("ExpressLRS*", elrs.rules.single().text)
        assertEquals(RuleKind.SERVICE_UUID, xiaomi.rules.single().kind)
        assertEquals("FE95", xiaomi.rules.single().text)
        assertFalse(xiaomi.rules.any { it.kind == RuleKind.MANUFACTURER_ID })
        assertEquals(RadioKind.WIFI, ezviz.rules.single().radio)
        assertEquals("EZVIZ_*", ezviz.rules.single().text)
        assertFalse(ezviz.rules.any { it.kind == RuleKind.OUI })
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        assertTrue("ExpressLRS is bookmarked", "fleet-elrs" in watched)
        assertFalse("Xiaomi is not bookmarked", "fleet-xiaomi-mihome" in watched)
        assertFalse("EZVIZ is not bookmarked", "fleet-ezviz" in watched)

        val tx = wifi("02:00:00:00:10:01", "ExpressLRS TX")
        val rx = wifi("02:00:00:00:10:02", "ExpressLRS RX")
        val txPack = wifi("02:00:00:00:10:03", "ExpressLRS TX Backpack")
        val vrxPack = wifi("02:00:00:00:10:04", "ExpressLRS VRx Backpack")
        val aatPack = wifi("02:00:00:00:10:05", "ExpressLRS AAT Backpack")
        val lower = wifi("02:00:00:00:10:06", "expresslrs")
        val mine = wifi("02:00:00:00:10:07", "MyExpressLRS")
        val bleName = ble(name = "ExpressLRS TX").copy(key = "BLE:elrs", mac = "AA:BB:CC:DD:10:01")
        val ez = wifi("02:00:00:00:10:08", "EZVIZ_C123456")
        val ezLower = wifi("02:00:00:00:10:09", "ezviz_C654321")
        val ezBare = wifi("02:00:00:00:10:0A", "EZVIZ")
        val ezDash = wifi("02:00:00:00:10:0B", "EZVIZ-C123")
        val ezMid = wifi("02:00:00:00:10:0C", "my EZVIZ_1")
        val fe95 = ble(name = "", serviceUuids = listOf("FE95"))
            .copy(key = "BLE:fe95", mac = "AA:BB:CC:DD:10:02")
        val fe95Long = ble(name = "", serviceUuids = listOf("0000FE95-0000-1000-8000-00805F9B34FB"))
            .copy(key = "BLE:fe95l", mac = "AA:BB:CC:DD:10:03")
        val company = ble(name = "", manufacturerId = 0x038F, manufacturerDataHex = "0102")
            .copy(key = "BLE:mi", mac = "AA:BB:CC:DD:10:04")
        val djiMac = wifi("60:60:1F:99:88:77", "")
        val hits = SignatureEngine().match(
            listOf(
                tx, rx, txPack, vrxPack, aatPack, lower, mine, bleName,
                ez, ezLower, ezBare, ezDash, ezMid,
                fe95, fe95Long, company, djiMac,
            ),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("ExpressLRS TX", "fleet-elrs" in ids(tx))
        assertTrue("ExpressLRS RX", "fleet-elrs" in ids(rx))
        assertTrue("TX backpack", "fleet-elrs" in ids(txPack))
        assertTrue("VRx backpack", "fleet-elrs" in ids(vrxPack))
        assertTrue("AAT backpack", "fleet-elrs" in ids(aatPack))
        assertTrue("lowercase ExpressLRS", "fleet-elrs" in ids(lower))
        assertFalse("ExpressLRS in the middle", "fleet-elrs" in ids(mine))
        assertFalse("Bluetooth name", "fleet-elrs" in ids(bleName))
        assertTrue("EZVIZ setup name", "fleet-ezviz" in ids(ez))
        assertFalse("EZVIZ setup is not Hikvision", "fleet-hikvision" in ids(ez))
        assertTrue("lowercase EZVIZ_", "fleet-ezviz" in ids(ezLower))
        assertFalse("EZVIZ without the underscore", "fleet-ezviz" in ids(ezBare))
        assertFalse("EZVIZ with a dash", "fleet-ezviz" in ids(ezDash))
        assertFalse("EZVIZ_ in the middle", "fleet-ezviz" in ids(ezMid))
        assertTrue("FE95", "fleet-xiaomi-mihome" in ids(fe95))
        assertTrue("long-form FE95", "fleet-xiaomi-mihome" in ids(fe95Long))
        assertFalse("Xiaomi company id", "fleet-xiaomi-mihome" in ids(company))
        assertFalse("DJI address alone", "fleet-dji" in ids(djiMac))
        assertTrue(
            DeviceExplain.guess(tx, listOf("ExpressLRS")).headline
                .contains("an ExpressLRS radio in Wi-Fi setup"),
        )
        assertTrue(
            DeviceExplain.guess(fe95, listOf("Xiaomi Mi Home")).headline
                .contains("a Xiaomi Mi Home device"),
        )
        assertTrue(
            DeviceExplain.guess(ez, listOf("EZVIZ")).headline
                .contains("an EZVIZ camera on its setup network"),
        )
    }

    @Test
    fun catalog101KeepsInseegoAddressOffNovatelAndDropsTheHatchAddress() {
        val stock = DefaultCatalog.fleets()
        assertFalse(stock.any { it.id == "fleet-novatel" })
        val inseego = stock.single { it.id == "fleet-inseego" }
        assertTrue(inseego.attentionNote.isBlank())
        assertTrue(inseego.rules.any { it.kind == RuleKind.OUI && it.text.equals("28:80:A2", true) })
        val hatch = stock.single { it.id == "fleet-hatch" }
        assertFalse(hatch.rules.any { it.kind == RuleKind.OUI && it.text.equals("C8:FA:9C", true) })
        assertTrue(hatch.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x0434 })
        assertFalse("fleet-novatel" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })

        val address = wifi("28:80:A2:11:22:33", "")
        val hits = SignatureEngine().match(listOf(address), stock).getValue(address.key)
        assertTrue("Inseego", "fleet-inseego" in hits)
        assertFalse("Novatel", "fleet-novatel" in hits)
    }

    @Test
    fun catalog102AddsIcomId52ByItsBluetoothName() {
        val stock = DefaultCatalog.fleets()
        val icom = stock.single { it.id == "fleet-icom-id52" }
        assertEquals(SignatureClass.TWO_WAY, icom.kind)
        assertTrue(icom.attentionNote.isBlank())
        assertEquals(listOf("ICOM BT(ID-52)"), icom.rules.map { it.text })
        assertEquals(RadioKind.BLE, icom.rules.single().radio)
        assertFalse("fleet-icom-id52" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })
        assertTrue(stock.none { it.kind == SignatureClass.MILITARY })
        assertEquals(9, stock.count { it.kind == SignatureClass.TWO_WAY })

        val named = ble(name = "ICOM BT(ID-52)").copy(key = "BLE:i1", mac = "AA:BB:CC:DD:11:01")
        val lower = ble(name = "icom bt(id-52)").copy(key = "BLE:i2", mac = "AA:BB:CC:DD:11:02")
        val edited = ble(name = "ICOM BT(ID-52)A").copy(key = "BLE:i3", mac = "AA:BB:CC:DD:11:03")
        val other = ble(name = "ICOM BT(ID-5200)").copy(key = "BLE:i4", mac = "AA:BB:CC:DD:11:04")
        val classic = ble(name = "ICOM BT(IC-705)").copy(key = "BLE:i5", mac = "AA:BB:CC:DD:11:05")
        val wifiName = wifi("02:00:00:00:11:06", "ICOM BT(ID-52)")
        val hits = SignatureEngine().match(listOf(named, lower, edited, other, classic, wifiName), stock)
        assertTrue("factory name", "fleet-icom-id52" in hits.getValue(named.key))
        assertTrue("lowercase", "fleet-icom-id52" in hits.getValue(lower.key))
        assertFalse("edited name", "fleet-icom-id52" in hits.getValue(edited.key))
        assertFalse("ID-5200", "fleet-icom-id52" in hits.getValue(other.key))
        assertFalse("IC-705", "fleet-icom-id52" in hits.getValue(classic.key))
        assertFalse("Wi-Fi name", "fleet-icom-id52" in hits.getValue(wifiName.key))
        assertTrue(
            DeviceExplain.guess(named, listOf("Icom ID-52")).headline
                .contains("an Icom ID-52 handheld"),
        )
    }

    @Test
    fun catalog103QuestNameBeatsMetaGlasses() {
        val stock = DefaultCatalog.fleets()
        val quest = stock.single { it.id == "fleet-meta-quest" }
        assertEquals(SignatureClass.WEARABLE, quest.kind)
        assertTrue(quest.attentionNote.isBlank())
        assertEquals(
            listOf("Quest", "Quest *", "Quest-*", "Meta Quest*"),
            quest.rules.map { it.text },
        )
        assertTrue(quest.rules.all { it.radio == RadioKind.BLE && it.kind == RuleKind.NAME_GLOB })
        assertFalse("fleet-meta-quest" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })
        assertTrue("fleet-meta-glasses" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })

        val named = ble(
            name = "Quest 3S",
            manufacturerId = 0x058E,
            manufacturerDataHex = "01",
            serviceUuids = listOf("0000FEB8-0000-1000-8000-00805F9B34FB"),
        ).copy(key = "BLE:q1", mac = "46:2A:A1:00:00:01")
        val lower = ble(name = "quest 3s", manufacturerId = 0x058E)
            .copy(key = "BLE:q2", mac = "46:2A:A1:00:00:02")
        val metaName = ble(name = "Meta Quest 3", manufacturerId = 0x058E)
            .copy(key = "BLE:q3", mac = "46:2A:A1:00:00:03")
        val hyphen = ble(name = "Quest-3S", manufacturerId = 0x058E)
            .copy(key = "BLE:q4", mac = "46:2A:A1:00:00:04")
        val bare = ble(name = "Quest", manufacturerId = 0x058E)
            .copy(key = "BLE:q5", mac = "46:2A:A1:00:00:05")
        val question = ble(name = "Question", manufacturerId = 0x058E)
            .copy(key = "BLE:q6", mac = "46:2A:A1:00:00:06")
        val unnamed = ble(
            name = "",
            manufacturerId = 0x058E,
            serviceUuids = listOf("FEB8"),
        ).copy(key = "BLE:q7", mac = "46:2A:A1:00:00:07")
        val feb8Only = ble(name = "", serviceUuids = listOf("FEB8"))
            .copy(key = "BLE:q8", mac = "46:2A:A1:00:00:08")
        val ray = ble(name = "Ray-Ban Meta", manufacturerId = 0x058E)
            .copy(key = "BLE:q9", mac = "46:2A:A1:00:00:09")
        val wifiName = wifi("02:00:00:00:00:0A", "Quest 3S")
        val hits = SignatureEngine().match(
            listOf(named, lower, metaName, hyphen, bare, question, unnamed, feb8Only, ray, wifiName),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        listOf(named, lower, metaName, hyphen, bare).forEach { radio ->
            assertTrue(radio.name, "fleet-meta-quest" in ids(radio))
            assertFalse(radio.name, "fleet-meta-glasses" in ids(radio))
        }
        assertFalse("Question is not Quest", "fleet-meta-quest" in ids(question))
        assertTrue("Question still glasses by company id", "fleet-meta-glasses" in ids(question))
        assertFalse("company id alone", "fleet-meta-quest" in ids(unnamed))
        assertTrue("company id alone is glasses", "fleet-meta-glasses" in ids(unnamed))
        assertFalse("FEB8 alone is not Quest", "fleet-meta-quest" in ids(feb8Only))
        assertTrue("FEB8 alone is glasses", "fleet-meta-glasses" in ids(feb8Only))
        assertFalse("Ray-Ban", "fleet-meta-quest" in ids(ray))
        assertTrue("Ray-Ban stays glasses", "fleet-meta-glasses" in ids(ray))
        assertFalse("Wi-Fi name", "fleet-meta-quest" in ids(wifiName))
        assertTrue(
            DeviceExplain.guess(named, listOf("Meta Quest")).headline.contains("a Meta Quest headset"),
        )
    }

    @Test
    fun wirelessMicsMatchByTheirBluetoothNames() {
        val stock = DefaultCatalog.fleets()
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        val rows = listOf(
            MicRow("fleet-icom-vs3", "ICOM BT-002", "an Icom VS-3 headset"),
            MicRow("fleet-yaesu-ssm-bt10", "SSM-BT10", "a Yaesu SSM-BT10 headset"),
            MicRow("fleet-yaesu-ssm-bt20", "SSM-BT20", "a Yaesu SSM-BT20 headset"),
            MicRow("fleet-kenwood-khs-55bt", "KHS-55BT", "a Kenwood KHS-55BT headset"),
            MicRow("fleet-kenwood-khs-56bt", "KHS-56BT*", "a Kenwood KHS-56BT headset"),
            MicRow("fleet-vgc-bhm-88", "BHM-88", "a VGC BHM-88 headset"),
        )
        val radios = mutableListOf<Sighting>()
        rows.forEachIndexed { index, row ->
            val fleet = stock.single { it.id == row.id }
            assertEquals(SignatureClass.TWO_WAY, fleet.kind)
            assertTrue(fleet.attentionNote.isBlank())
            assertEquals(listOf(row.pattern), fleet.rules.map { it.text })
            assertEquals(RadioKind.BLE, fleet.rules.single().radio)
            assertTrue(fleet.notes.contains("how the microphone works"))
            assertFalse(row.id in watched)
            val tail = "%02X".format(index + 1)
            val factory = row.pattern.removeSuffix("*")
            radios += ble(name = factory).copy(key = "BLE:m$tail", mac = "AA:BB:CC:DD:22:$tail")
            radios += wifi("02:00:00:00:33:$tail", factory)
        }
        val coded = ble(name = "KHS-56BT 1234").copy(key = "BLE:m56", mac = "AA:BB:CC:DD:22:56")
        val otherMic = ble(name = "SSM-BT20").copy(key = "BLE:m20", mac = "AA:BB:CC:DD:22:20")
        val edited = ble(name = "ICOM BT-002A").copy(key = "BLE:mEA", mac = "AA:BB:CC:DD:22:EA")
        val longer = ble(name = "SSM-BT100").copy(key = "BLE:m100", mac = "AA:BB:CC:DD:22:64")
        val hits = SignatureEngine().match(radios + coded + otherMic + edited + longer, stock)
        assertTrue("VS-3", "fleet-icom-vs3" in hits.getValue("BLE:m01"))
        assertFalse("VS-3 is not the ID-52", "fleet-icom-id52" in hits.getValue("BLE:m01"))
        assertFalse("VS-3 wifi", "fleet-icom-vs3" in hits.getValue("WIFI:02:00:00:00:33:01"))
        assertTrue("SSM-BT10", "fleet-yaesu-ssm-bt10" in hits.getValue("BLE:m02"))
        assertFalse("SSM-BT10 does not take SSM-BT20", "fleet-yaesu-ssm-bt10" in hits.getValue(otherMic.key))
        assertTrue("SSM-BT20", "fleet-yaesu-ssm-bt20" in hits.getValue(otherMic.key))
        assertFalse("SSM-BT100", "fleet-yaesu-ssm-bt10" in hits.getValue(longer.key))
        assertTrue("KHS-55BT", "fleet-kenwood-khs-55bt" in hits.getValue("BLE:m04"))
        assertFalse("KHS-55BT is not KHS-56BT", "fleet-kenwood-khs-56bt" in hits.getValue("BLE:m04"))
        assertTrue("KHS-56BT", "fleet-kenwood-khs-56bt" in hits.getValue("BLE:m05"))
        assertTrue("KHS-56BT code", "fleet-kenwood-khs-56bt" in hits.getValue(coded.key))
        assertFalse("KHS-55BT is not the coded KHS-56BT", "fleet-kenwood-khs-55bt" in hits.getValue(coded.key))
        assertTrue("BHM-88", "fleet-vgc-bhm-88" in hits.getValue("BLE:m06"))
        assertFalse("edited VS-3", "fleet-icom-vs3" in hits.getValue(edited.key))
        rows.forEach { row ->
            val fleet = stock.single { it.id == row.id }
            val heard = radios.first { it.kind == RadioKind.BLE && it.name.equals(row.pattern.removeSuffix("*"), true) }
            assertTrue(
                row.headline,
                DeviceExplain.guess(heard, listOf(fleet.name)).headline.contains(row.headline),
            )
        }
    }

    @Test
    fun benshiRadiosAndPttButtonsMatchExactBluetoothNames() {
        val stock = DefaultCatalog.fleets()
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        val benshi = stock.single { it.id == "fleet-benshi-radio" }
        val ptt = stock.single { it.id == "fleet-ble-ptt-button" }
        assertEquals(SignatureClass.TWO_WAY, benshi.kind)
        assertEquals(SignatureClass.TWO_WAY, ptt.kind)
        assertTrue(benshi.attentionNote.isBlank())
        assertTrue(ptt.attentionNote.isBlank())
        assertFalse("fleet-benshi-radio" in watched)
        assertFalse("fleet-ble-ptt-button" in watched)
        assertEquals(
            listOf("UV-PRO", "UV-50PRO", "GA-5WB", "VR-N75", "VR-N76", "VR-N7500", "VR-N7600", "DB50-B"),
            benshi.rules.map { it.text },
        )
        assertEquals(listOf("PTT-Z", "PTT-AZ", "PTT-Z01"), ptt.rules.map { it.text })
        assertTrue(benshi.rules.all { it.radio == RadioKind.BLE && it.kind == RuleKind.NAME_GLOB })
        assertTrue(ptt.rules.all { it.radio == RadioKind.BLE && it.kind == RuleKind.NAME_GLOB })
        assertTrue(benshi.rules.none { it.kind == RuleKind.SERVICE_UUID || it.kind == RuleKind.MANUFACTURER_ID })
        assertTrue(ptt.rules.none { it.kind == RuleKind.SERVICE_UUID })

        val radios = mutableListOf<Sighting>()
        var n = 0
        fun add(name: String, kind: RadioKind = RadioKind.BLE): String {
            n += 1
            val key = "${kind.name}:q%02X".format(n)
            radios += ble(name = name).copy(
                key = key,
                kind = kind,
                mac = "AA:BB:CC:DD:44:%02X".format(n),
            )
            return key
        }
        val uvPro = add("UV-PRO")
        val uvProLower = add("uv-pro")
        val uv50 = add("UV-50PRO")
        val ga = add("GA-5WB")
        val vr75 = add("VR-N75")
        val vr76 = add("VR-N76")
        val vr7500 = add("VR-N7500")
        val vr7600 = add("VR-N7600")
        val db50 = add("DB50-B")
        val pttZ = add("PTT-Z")
        val pttAz = add("ptt-az")
        val pttZ01 = add("PTT-Z01")
        val wifiName = add("UV-PRO", RadioKind.WIFI)
        val missed = listOf("UV-P", "UV-PRO 2", "BTECH UV-50PRO", "VR-N750", "GMRS-PRO", "PTT-Z02", "XPTT-Z", "")
            .map { add(it) }
        val matched = SignatureEngine().match(radios, stock)
        listOf(uvPro, uvProLower, uv50, ga, vr75, vr76, vr7500, vr7600, db50).forEach { key ->
            assertTrue(key, "fleet-benshi-radio" in matched.getValue(key))
            assertFalse(key, "fleet-ble-ptt-button" in matched.getValue(key))
        }
        listOf(pttZ, pttAz, pttZ01).forEach { key ->
            assertTrue(key, "fleet-ble-ptt-button" in matched.getValue(key))
            assertFalse(key, "fleet-benshi-radio" in matched.getValue(key))
        }
        assertFalse("Wi-Fi name", "fleet-benshi-radio" in matched.getValue(wifiName))
        missed.forEach { key ->
            assertFalse(key, "fleet-benshi-radio" in matched.getValue(key))
            assertFalse(key, "fleet-ble-ptt-button" in matched.getValue(key))
        }
        assertTrue(
            DeviceExplain.guess(radios.first { it.key == uvPro }, listOf("Benshi radio")).headline
                .contains("a Benshi handheld or mobile radio"),
        )
        assertTrue(
            DeviceExplain.guess(radios.first { it.key == pttZ }, listOf("BLE PTT button")).headline
                .contains("a Bluetooth push-to-talk button"),
        )
    }

    private data class MicRow(val id: String, val pattern: String, val headline: String)

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
