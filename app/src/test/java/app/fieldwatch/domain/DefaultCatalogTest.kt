package app.fieldwatch.domain

import app.fieldwatch.data.ADDED_IN_V118
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
        assertEquals(390, stock.size)

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
    fun catalog114AddsSitFamiliesAndNameRules() {
        val stock = DefaultCatalog.fleets()
        val added = listOf(
            "fleet-phillips-connect", "fleet-acuity", "fleet-cobra", "fleet-jlg-clearsky",
            "fleet-gm-peps", "fleet-lg-appliance", "fleet-gli-open", "fleet-iosix",
            "fleet-rove", "fleet-cardo", "fleet-mazda", "fleet-gree", "fleet-tozo",
            "fleet-irobot",
        )
        assertEquals(added.toSet(), stock.map { it.id }.filter { it in added }.toSet())
        assertEquals(390, stock.size)
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        added.forEach { id ->
            val row = stock.single { it.id == id }
            assertTrue(id, row.builtIn)
            assertTrue(id, row.enabled)
            assertTrue(id, row.attentionNote.isBlank())
            assertTrue(id, row.matchAny)
            assertTrue(id, row.decode == null)
            assertFalse(id, id in watched)
        }
        assertFalse(stock.single { it.id == "fleet-target-atrius" }.rules.any { it.companyId == 0x0346 })
        assertFalse(stock.any { fleet ->
            fleet.rules.any { it.text.equals("Hotspot*", true) || it.text.equals("Hotspot????", true) }
        })
        val cobra = stock.single { it.id == "fleet-cobra" }
        assertEquals(1, cobra.rules.size)
        assertFalse(cobra.rules.any { it.text.contains("C46B558B", true) })
        val peps = stock.single { it.id == "fleet-gm-peps" }
        assertFalse(peps.rules.any { it.text.equals("FE47", true) })
        val lg = stock.single { it.id == "fleet-lg-appliance" }
        assertFalse(lg.rules.any { it.companyId == 0x00C4 })
        assertFalse(stock.single { it.id == "fleet-gree" }.rules.any { it.companyId == 0x005D })
        assertFalse(stock.single { it.id == "fleet-tozo" }.rules.any { it.companyId == 35757 })
        val cardo = stock.single { it.id == "fleet-cardo" }
        assertFalse(cardo.rules.any { it.text.contains("CD007F80", true) || it.companyId == 0xA106 })
        assertFalse(stock.single { it.id == "fleet-gli-open" }.rules.any { it.text.contains("E7810A71", true) })
        assertFalse(stock.single { it.id == "fleet-irobot" }.rules.any { it.kind == RuleKind.SERVICE_UUID })
        assertEquals(SignatureClass.VEHICLE, stock.single { it.id == "fleet-rove" }.kind)
        assertEquals(SignatureClass.SIGNAGE, stock.single { it.id == "fleet-gli-open" }.kind)
        assertEquals(SignatureClass.AUDIO, cardo.kind)
        assertEquals(SignatureClass.HOME, lg.kind)

        val phillips = ble(name = "PCAM_1234", manufacturerId = 0x087F)
            .copy(key = "BLE:pc", mac = "AA:BB:CC:14:00:01")
        val pcam = ble(name = "PCAM_1234").copy(key = "BLE:pcam", mac = "AA:BB:CC:14:00:02")
        val acuityCo = ble(name = "A1B2C3", manufacturerId = 0x0346)
            .copy(key = "BLE:acu", mac = "AA:BB:CC:14:00:03")
        val acuityUuid = ble(
            name = "",
            serviceUuids = listOf("7fc4c6d5-8fab-452a-9871-8d707c939a6f"),
        ).copy(key = "BLE:acuu", mac = "AA:BB:CC:14:00:04")
        val cobraHit = ble(
            name = "RAD 480i",
            serviceUuids = listOf("2A668FA4-2902-4468-8568-3EBE69A930A0"),
        ).copy(key = "BLE:cobra", mac = "AA:BB:CC:14:00:05")
        val cobraOther = ble(name = "SC 200", serviceUuids = listOf("C46B558B"))
            .copy(key = "BLE:sc", mac = "AA:BB:CC:14:00:06")
        val escortName = ble(name = "MAX 360c").copy(key = "BLE:max2", mac = "AA:BB:CC:14:00:07")
        val jlg = ble(name = "serial", manufacturerId = 0x0753)
            .copy(key = "BLE:jlg", mac = "AA:BB:CC:14:00:08")
        val clearSky = ble(name = "ClearSky Smart Fleet").copy(key = "BLE:cs", mac = "AA:BB:CC:14:00:09")
        val pepsUuid = ble(
            name = "",
            serviceUuids = listOf("0000FE48-0000-1000-8000-00805F9B34FB"),
        ).copy(key = "BLE:fe48", mac = "AA:BB:CC:14:00:0A")
        val pepsName = ble(name = "GM_PEPS_VKM").copy(key = "BLE:vkm", mac = "AA:BB:CC:14:00:0B")
        val fe47 = ble(name = "", serviceUuids = listOf("FE47"))
            .copy(key = "BLE:fe47", mac = "AA:BB:CC:14:00:0C")
        val oven = wifi("AA:BB:CC:14:00:0D", "[LG_Oven]1234")
        val micro = wifi("AA:BB:CC:14:00:0E", "[LG_MicrowaveOven]1")
        val fridge = wifi("AA:BB:CC:14:00:0F", "[LG_Refrigerator]1")
        val washer = wifi("AA:BB:CC:14:00:10", "[LG_Washer]1")
        val wall = wifi("AA:BB:CC:14:00:11", "[LG_Wall-Mount A/C]1")
        val window = wifi("AA:BB:CC:14:00:12", "[LG_Window A/C]1")
        val speaker = ble(name = "LG_Speaker_SC9S_2129").copy(key = "BLE:spk", mac = "AA:BB:CC:14:00:13")
        val bar = ble(name = "LG S77S(37)").copy(key = "BLE:s77", mac = "AA:BB:CC:14:00:14")
        val webos = ble(name = "webOS TV").copy(key = "BLE:webos", mac = "AA:BB:CC:14:00:15")
        val lgCo = ble(name = "", manufacturerId = 0x00C4).copy(key = "BLE:lgc", mac = "AA:BB:CC:14:00:16")
        val gli = ble(name = "GLI1050.I", serviceUuids = listOf("E7810A71"))
            .copy(key = "BLE:gli", mac = "AA:BB:CC:14:00:17")
        val uart = ble(name = "", serviceUuids = listOf("E7810A71", "18F0"))
            .copy(key = "BLE:uart", mac = "AA:BB:CC:14:00:18")
        val gliShort = ble(name = "GLI").copy(key = "BLE:glishort", mac = "AA:BB:CC:14:00:19")
        val eld = wifi("AA:BB:CC:14:00:1A", "IOSiX ELD")
        val eldHex = wifi("AA:BB:CC:14:00:1B", "IOSiX ELD; AB12")
        val eldBare = wifi("AA:BB:CC:14:00:1C", "IOSiX")
        val eldUuid = ble(name = "", serviceUuids = listOf("FC32AE3C"))
            .copy(key = "BLE:fc32", mac = "AA:BB:CC:14:00:1D")
        val rovePro = wifi("AA:BB:CC:14:00:1E", "ROVE R2-PRO-4K_1")
        val roveDual = wifi("AA:BB:CC:14:00:1F", "ROVE_R2-4K-DUAL_1")
        val rove4k = wifi("AA:BB:CC:14:00:20", "ROVE_R2-4K_1")
        val freecom = ble(name = "FREECOM4X").copy(key = "BLE:free", mac = "AA:BB:CC:14:00:21")
        val cardoName = ble(name = "Cardo").copy(key = "BLE:cardo", mac = "AA:BB:CC:14:00:22")
        val cardboard = ble(name = "Cardboard").copy(key = "BLE:board", mac = "AA:BB:CC:14:00:23")
        val cardoUuid = ble(name = "", serviceUuids = listOf("CD007F80"), manufacturerId = 0xA106)
            .copy(key = "BLE:cdu", mac = "AA:BB:CC:14:00:24")
        val mazdaHit = wifi("AA:BB:CC:14:00:25", "Mazda_CX5_SPL")
        val greeHit = ble(name = "GR-AC_10001_09_1_SC", manufacturerId = 0x005D)
            .copy(key = "BLE:gree", mac = "AA:BB:CC:14:00:26")
        val realtek = ble(name = "RTL", manufacturerId = 0x005D)
            .copy(key = "BLE:rtl", mac = "AA:BB:CC:14:00:27")
        val tozoHit = ble(name = "TOZO NC20", manufacturerId = 35757)
            .copy(key = "BLE:tozo", mac = "AA:BB:CC:14:00:28")
        val tozoCo = ble(name = "", manufacturerId = 35757)
            .copy(key = "BLE:tozoc", mac = "AA:BB:CC:14:00:29")
        val roomba = ble(name = "G181020", manufacturerId = 0x0600, serviceUuids = listOf("C74EDD21"))
            .copy(key = "BLE:roomba", mac = "AA:BB:CC:14:00:2A")
        val roombaUuid = ble(name = "G181020", serviceUuids = listOf("C74EDD21"))
            .copy(key = "BLE:c74", mac = "AA:BB:CC:14:00:2B")
        val bmwDirect = wifi("AA:BB:CC:14:00:2C", "DIRECT-BMW1234")
        val bmwUnder = wifi("AA:BB:CC:14:00:2D", "BMW_1234")
        val gmc = wifi("AA:BB:CC:14:00:2E", "GMC3088")
        val gmcShort = wifi("AA:BB:CC:14:00:2F", "GMC308")
        val gmcLong = wifi("AA:BB:CC:14:00:30", "GMC30888")
        val myGmc = wifi("AA:BB:CC:14:00:31", "myGMC1234")
        val mb = wifi("AA:BB:CC:14:00:32", "MB Hotspot 1")
        val mbux = wifi("AA:BB:CC:14:00:33", "MBUX1234")
        val rogue = wifi("AA:BB:CC:14:00:34", "MY ROGUE")
        val rogueOnly = wifi("AA:BB:CC:14:00:35", "ROGUE")
        val laser = wifi("AA:BB:CC:14:00:36", "DIRECT-03-HP M283 LaserJet")
        val office = wifi("AA:BB:CC:14:00:37", "DIRECT-06-HP OfficeJet Pro 7740")
        val envy = wifi("AA:BB:CC:14:00:38", "DIRECT-6F-HP ENVY 7640 series")
        val canon = wifi("AA:BB:CC:14:00:39", "DIRECT-99-Canon")
        val business = ble(name = "Business TV BED-H").copy(key = "BLE:bed", mac = "AA:BB:CC:14:00:3A")
        val carUnder = wifi("AA:BB:CC:14:00:3B", "CARLINK_54BF42")
        val carDash = wifi("AA:BB:CC:14:00:3C", "CARLINK-54BF42")
        val carAp = wifi("AA:BB:CC:14:00:3D", "CarLinkAP276")
        val bareHotspot = wifi("AA:BB:CC:14:00:3E", "Hotspot1234")

        val hits = SignatureEngine().match(
            listOf(
                phillips, pcam, acuityCo, acuityUuid, cobraHit, cobraOther, escortName, jlg, clearSky,
                pepsUuid, pepsName, fe47, oven, micro, fridge, washer, wall, window, speaker, bar,
                webos, lgCo, gli, uart, gliShort, eld, eldHex, eldBare, eldUuid, rovePro, roveDual,
                rove4k, freecom, cardoName, cardboard, cardoUuid, mazdaHit, greeHit, realtek, tozoHit,
                tozoCo, roomba, roombaUuid, bmwDirect, bmwUnder, gmc, gmcShort, gmcLong, myGmc, mb,
                mbux, rogue, rogueOnly, laser, office, envy, canon, business, carUnder, carDash,
                carAp, bareHotspot,
            ),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("Phillips company", "fleet-phillips-connect" in ids(phillips))
        assertFalse("Phillips serial", "fleet-phillips-connect" in ids(pcam))
        assertTrue("Acuity company", "fleet-acuity" in ids(acuityCo))
        assertFalse("Acuity is not a cart tag", "fleet-target-atrius" in ids(acuityCo))
        assertTrue("Acuity service", "fleet-acuity" in ids(acuityUuid))
        assertTrue("Cobra service", "fleet-cobra" in ids(cobraHit))
        assertFalse("Cobra is not Escort", "fleet-escort" in ids(cobraHit))
        assertFalse("other Cobra service", "fleet-cobra" in ids(cobraOther))
        assertFalse("Escort name", "fleet-cobra" in ids(escortName))
        assertFalse("Escort name stays off Escort", "fleet-escort" in ids(escortName))
        assertTrue("JLG company", "fleet-jlg-clearsky" in ids(jlg))
        assertTrue("ClearSky name", "fleet-jlg-clearsky" in ids(clearSky))
        assertTrue("FE48", "fleet-gm-peps" in ids(pepsUuid))
        assertFalse("phone-as-key is not the hotspot row", "fleet-gm-hotspot" in ids(pepsUuid))
        assertTrue("GM_PEPS", "fleet-gm-peps" in ids(pepsName))
        assertFalse("FE47", "fleet-gm-peps" in ids(fe47))
        assertTrue("LG oven", "fleet-lg-appliance" in ids(oven))
        assertTrue("LG microwave", "fleet-lg-appliance" in ids(micro))
        assertTrue("LG fridge", "fleet-lg-appliance" in ids(fridge))
        assertTrue("LG washer", "fleet-lg-appliance" in ids(washer))
        assertTrue("LG wall A/C", "fleet-lg-appliance" in ids(wall))
        assertTrue("LG window A/C", "fleet-lg-appliance" in ids(window))
        assertTrue("LG speaker", "fleet-lg-appliance" in ids(speaker))
        assertTrue("LG soundbar", "fleet-lg-appliance" in ids(bar))
        assertFalse("webOS stays off the appliance row", "fleet-lg-appliance" in ids(webos))
        assertTrue("webOS TV", "fleet-lg-webos" in ids(webos))
        assertFalse("LG company id", "fleet-lg-appliance" in ids(lgCo))
        assertTrue("GLI1050", "fleet-gli-open" in ids(gli))
        assertFalse("Nordic UART", "fleet-gli-open" in ids(uart))
        assertFalse("short GLI", "fleet-gli-open" in ids(gliShort))
        assertTrue("IOSiX ELD", "fleet-iosix" in ids(eld))
        assertTrue("IOSiX ELD hex", "fleet-iosix" in ids(eldHex))
        assertFalse("IOSiX alone", "fleet-iosix" in ids(eldBare))
        assertFalse("IOSiX service", "fleet-iosix" in ids(eldUuid))
        assertTrue("ROVE R2-PRO", "fleet-rove" in ids(rovePro))
        assertTrue("ROVE dual", "fleet-rove" in ids(roveDual))
        assertTrue("ROVE 4K", "fleet-rove" in ids(rove4k))
        assertTrue("FREECOM", "fleet-cardo" in ids(freecom))
        assertTrue("Cardo", "fleet-cardo" in ids(cardoName))
        assertFalse("Cardboard", "fleet-cardo" in ids(cardboard))
        assertFalse("Cardo service and company", "fleet-cardo" in ids(cardoUuid))
        assertTrue("Mazda", "fleet-mazda" in ids(mazdaHit))
        assertTrue("Gree name", "fleet-gree" in ids(greeHit))
        assertFalse("Realtek company", "fleet-gree" in ids(realtek))
        assertTrue("TOZO name", "fleet-tozo" in ids(tozoHit))
        assertFalse("TOZO company", "fleet-tozo" in ids(tozoCo))
        assertTrue("iRobot company", "fleet-irobot" in ids(roomba))
        assertFalse("iRobot service", "fleet-irobot" in ids(roombaUuid))
        assertTrue("DIRECT-BMW", "fleet-bmw" in ids(bmwDirect))
        assertTrue("BMW_", "fleet-bmw" in ids(bmwUnder))
        assertTrue("GMC and four characters", "fleet-gm-hotspot" in ids(gmc))
        assertFalse("short GMC", "fleet-gm-hotspot" in ids(gmcShort))
        assertFalse("long GMC", "fleet-gm-hotspot" in ids(gmcLong))
        assertTrue("myGMC", "fleet-gm-hotspot" in ids(myGmc))
        assertTrue("MB Hotspot", "fleet-mbux" in ids(mb))
        assertTrue("MBUX", "fleet-mbux" in ids(mbux))
        assertTrue("MY ROGUE", "fleet-nissan" in ids(rogue))
        assertFalse("ROGUE alone", "fleet-nissan" in ids(rogueOnly))
        assertTrue("LaserJet", "fleet-hp" in ids(laser))
        assertTrue("OfficeJet", "fleet-hp" in ids(office))
        assertTrue("ENVY", "fleet-hp" in ids(envy))
        assertFalse("DIRECT Canon", "fleet-hp" in ids(canon))
        assertTrue("Business TV", "fleet-samsung-tv" in ids(business))
        assertTrue("Business TV needs no company id", business.manufacturerId == null)
        assertTrue("CARLINK_", "fleet-carlink" in ids(carUnder))
        assertTrue("CARLINK-", "fleet-carlink" in ids(carDash))
        assertFalse("CarLinkAP", "fleet-carlink" in ids(carAp))
        assertTrue("bare Hotspot stays unmatched", ids(bareHotspot).isEmpty())
        assertTrue(
            DeviceExplain.guess(phillips, listOf("Phillips Connect")).headline.contains("Phillips Connect"),
        )
        assertTrue(DeviceExplain.guess(cobraHit, listOf("Cobra")).headline.contains("Cobra"))
        assertTrue(DeviceExplain.guess(roomba, listOf("iRobot")).headline.contains("iRobot"))
    }

    @Test
    fun catalog114AddsGlassesMeshCrossfireAndOptics() {
        val stock = DefaultCatalog.fleets()
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        val frame = stock.single { it.id == "fleet-brilliant-frame" }
        val mentra = stock.single { it.id == "fleet-mentra-live" }
        val even = stock.single { it.id == "fleet-even-g1" }
        val rnode = stock.single { it.id == "fleet-rnode" }
        val tbs = stock.single { it.id == "fleet-tbs-crossfire" }
        val hik = stock.single { it.id == "fleet-hikmicro-optics" }
        val pulsar = stock.single { it.id == "fleet-pulsar-optics" }
        assertEquals(
            listOf("7A230001-5475-A6A4-654C-8431F6AD49C4"),
            frame.rules.map { it.text },
        )
        assertTrue(frame.attentionNote.isNotBlank())
        assertTrue("fleet-brilliant-frame" in watched)
        assertEquals(SignatureClass.GLASSES, mentra.kind)
        assertTrue(mentra.attentionNote.isNotBlank())
        assertTrue("fleet-mentra-live" in watched)
        assertTrue(even.rules.any { it.text == "Even G2_*" })
        assertFalse(even.rules.any { it.companyId == 0x4552 })
        assertEquals(SignatureClass.MESH, rnode.kind)
        assertTrue(rnode.attentionNote.isBlank())
        assertFalse("fleet-rnode" in watched)
        assertEquals(SignatureClass.DRONE, tbs.kind)
        assertTrue(tbs.attentionNote.isBlank())
        assertTrue("fleet-tbs-crossfire" in watched)
        assertEquals(SignatureClass.CAMERA, hik.kind)
        assertEquals(SignatureClass.CAMERA, pulsar.kind)
        assertEquals(17, pulsar.rules.size)
        assertFalse("fleet-hikmicro-optics" in watched)
        assertFalse("fleet-pulsar-optics" in watched)
        assertFalse(pulsar.rules.any { it.text.startsWith("Trail_") || it.text.startsWith("PROTON_") })
        assertEquals(listOf("fleet-steiner-lrf"), stock.filter { it.kind == SignatureClass.MILITARY }.map { it.id })

        val frameUuid = ble(
            name = "Frame 3C",
            serviceUuids = listOf("7a230001-5475-a6a4-654c-8431f6ad49c4"),
        ).copy(key = "BLE:fr", mac = "AA:BB:CC:14:01:01")
        val oldUuid = ble(
            name = "Brilliant Frame",
            serviceUuids = listOf("7A230001-5475-A6A4-654C-576174636800"),
        ).copy(key = "BLE:old", mac = "AA:BB:CC:14:01:02")
        val frameName = ble(name = "Frame 3C").copy(key = "BLE:fn", mac = "AA:BB:CC:14:01:03")
        val halo = ble(
            name = "Halo A1",
            serviceUuids = listOf("7A230001-5475-A6A4-654C-8431F6AD49C4"),
        ).copy(key = "BLE:halo", mac = "AA:BB:CC:14:01:04")
        val mentraName = ble(name = "Mentra_Live_01A4").copy(key = "BLE:ml", mac = "AA:BB:CC:14:01:05")
        val mentraUpper = ble(name = "MENTRA_LIVE_BLE").copy(key = "BLE:mu", mac = "AA:BB:CC:14:01:06")
        val mentraLower = ble(name = "mentra_live").copy(key = "BLE:mlo", mac = "AA:BB:CC:14:01:07")
        val xy = ble(name = "XyBLE_K900").copy(key = "BLE:xy", mac = "AA:BB:CC:14:01:08")
        val xyShort = ble(name = "Xy_A").copy(key = "BLE:xya", mac = "AA:BB:CC:14:01:09")
        val mentraWifi = wifi("AA:BB:CC:14:01:0A", "Mentra_Live_01A4")
        val g2 = ble(name = "Even G2_32_L_123456", manufacturerId = 0x4552)
            .copy(key = "BLE:g2", mac = "AA:BB:CC:14:01:0B")
        val g2Only = ble(name = "", manufacturerId = 0x4552)
            .copy(key = "BLE:er", mac = "AA:BB:CC:14:01:0C")
        val rnodeBle = ble(name = "RNode 3FA2").copy(key = "BLE:rn", mac = "AA:BB:CC:14:01:0D")
        val rnodeWifi = wifi("AA:BB:CC:14:01:0E", "rnode 3fa2")
        val rnodeShort = ble(name = "RNode 3FA").copy(key = "BLE:rns", mac = "AA:BB:CC:14:01:0F")
        val rnodeLong = ble(name = "RNode 3FA2X").copy(key = "BLE:rnl", mac = "AA:BB:CC:14:01:10")
        val tbsHit = wifi("AA:BB:CC:14:01:11", "TBS_XF_AP_3A2F1C")
        val tbsBle = ble(name = "TBS_XF_AP_3A2F1C").copy(key = "BLE:tbs", mac = "AA:BB:CC:14:01:12")
        val tbsShort = wifi("AA:BB:CC:14:01:13", "TBS_XF")
        val tbsMid = wifi("AA:BB:CC:14:01:14", "My TBS_XF_AP_1")
        val hikHit = wifi("AA:BB:CC:14:01:15", "HIKMICRO_FA1234567")
        val hikBare = wifi("AA:BB:CC:14:01:16", "HIKMICRO")
        val hikMine = wifi("AA:BB:CC:14:01:17", "MyHIKMICRO_1")
        val hikBle = ble(name = "HIKMICRO_FA1234567").copy(key = "BLE:hik", mac = "AA:BB:CC:14:01:18")
        val thermion2 = wifi("AA:BB:CC:14:01:19", "Thermion2_1234")
        val thermion = wifi("AA:BB:CC:14:01:1A", "Thermion_1234")
        val axion = wifi("AA:BB:CC:14:01:1B", "AXION2_1234")
        val helionLong = wifi("AA:BB:CC:14:01:1C", "Helion2_12345")
        val oryx = wifi("AA:BB:CC:14:01:1D", "Oryx_serial")
        val trail = wifi("AA:BB:CC:14:01:1E", "Trail_1234")
        val forward = wifi("AA:BB:CC:14:01:1F", "Forward F_12AB")
        val hits = SignatureEngine().match(
            listOf(
                frameUuid, oldUuid, frameName, halo, mentraName, mentraUpper, mentraLower, xy, xyShort,
                mentraWifi, g2, g2Only, rnodeBle, rnodeWifi, rnodeShort, rnodeLong, tbsHit, tbsBle, tbsShort,
                tbsMid, hikHit, hikBare, hikMine, hikBle, thermion2, thermion, axion, helionLong, oryx, trail,
                forward,
            ),
            stock,
        )
        fun ids(sighting: Sighting) = hits.getValue(sighting.key)
        assertTrue("Frame UUID", "fleet-brilliant-frame" in ids(frameUuid))
        assertFalse("old Frame UUID", "fleet-brilliant-frame" in ids(oldUuid))
        assertFalse("Frame name alone", "fleet-brilliant-frame" in ids(frameName))
        assertTrue("Halo UUID", "fleet-brilliant-frame" in ids(halo))
        assertTrue("Mentra_Live", "fleet-mentra-live" in ids(mentraName))
        assertTrue("MENTRA_LIVE_BLE", "fleet-mentra-live" in ids(mentraUpper))
        assertTrue("mentra_live", "fleet-mentra-live" in ids(mentraLower))
        assertTrue("XyBLE_", "fleet-mentra-live" in ids(xy))
        assertFalse("Xy_A", "fleet-mentra-live" in ids(xyShort))
        assertFalse("Mentra Wi-Fi", "fleet-mentra-live" in ids(mentraWifi))
        assertTrue("Even G2 name", "fleet-even-g1" in ids(g2))
        assertFalse("company 0x4552", "fleet-even-g1" in ids(g2Only))
        assertTrue("RNode BLE", "fleet-rnode" in ids(rnodeBle))
        assertTrue("RNode Wi-Fi", "fleet-rnode" in ids(rnodeWifi))
        assertFalse("short RNode", "fleet-rnode" in ids(rnodeShort))
        assertFalse("long RNode", "fleet-rnode" in ids(rnodeLong))
        assertTrue("Crossfire", "fleet-tbs-crossfire" in ids(tbsHit))
        assertFalse("Crossfire BLE", "fleet-tbs-crossfire" in ids(tbsBle))
        assertFalse("TBS_XF", "fleet-tbs-crossfire" in ids(tbsShort))
        assertFalse("TBS in the middle", "fleet-tbs-crossfire" in ids(tbsMid))
        assertTrue("HIKMICRO", "fleet-hikmicro-optics" in ids(hikHit))
        assertFalse("HIKMICRO is not Hikvision", "fleet-hikvision" in ids(hikHit))
        assertFalse("bare HIKMICRO", "fleet-hikmicro-optics" in ids(hikBare))
        assertFalse("MyHIKMICRO", "fleet-hikmicro-optics" in ids(hikMine))
        assertFalse("HIKMICRO BLE", "fleet-hikmicro-optics" in ids(hikBle))
        assertTrue("Thermion2", "fleet-pulsar-optics" in ids(thermion2))
        assertTrue("Thermion", "fleet-pulsar-optics" in ids(thermion))
        assertTrue("AXION2", "fleet-pulsar-optics" in ids(axion))
        assertFalse("Helion2 five digits", "fleet-pulsar-optics" in ids(helionLong))
        assertTrue("Oryx", "fleet-pulsar-optics" in ids(oryx))
        assertFalse("Trail", "fleet-pulsar-optics" in ids(trail))
        assertTrue("Forward F", "fleet-pulsar-optics" in ids(forward))

        val frameHint = DeviceExplain.guess(frameUuid, listOf(frame.name))
        assertTrue(frameHint.headline.contains("Brilliant Labs Frame"))
        val haloHint = DeviceExplain.guess(halo, listOf(frame.name))
        assertTrue(haloHint.headline.contains("Brilliant Labs Halo"))
        val bareFrame = DeviceExplain.guess(frameName, emptyList())
        assertFalse(bareFrame.headline.contains("Brilliant"))
        val g2Hint = DeviceExplain.guess(g2, listOf(even.name))
        assertTrue(g2Hint.headline.contains("Even Realities G2 glasses"))
        val g2Alone = DeviceExplain.guess(g2, emptyList())
        assertFalse(g2Alone.headline.contains("Even Realities G2"))
    }

    @Test
    fun catalog114AddsSteinerBurrisThermalNames() {
        val stock = DefaultCatalog.fleets()
        val row = stock.single { it.id == "fleet-steiner-burris-thermal" }
        assertEquals(SignatureClass.CAMERA, row.kind)
        assertTrue(row.attentionNote.isBlank())
        assertFalse(
            "fleet-steiner-burris-thermal" in
                DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId },
        )
        assertEquals(
            listOf(
                "STEINER Thermal Handheld*",
                "Nighthunter-*",
                "Nighthunter C35*",
                "Burris_BTH*",
            ),
            row.rules.map { it.text },
        )
        val handheld = wifi("AA:BB:CC:14:02:01", "STEINER Thermal Handheld-1A2B3C")
        val h35 = wifi("AA:BB:CC:14:02:02", "Nighthunter-H35-0042")
        val s35 = wifi("AA:BB:CC:14:02:03", "Nighthunter-S35X")
        val c35 = wifi("AA:BB:CC:14:02:04", "Nighthunter C35 12")
        val burris = wifi("AA:BB:CC:14:02:05", "Burris_BTH_9F21")
        val shortBth = wifi("AA:BB:CC:14:02:06", "BTH-A1B2")
        val shortBts = wifi("AA:BB:CC:14:02:07", "BTS-0001")
        val shortBtc = wifi("AA:BB:CC:14:02:08", "BTC-ZZ99")
        val noDash = wifi("AA:BB:CC:14:02:09", "BTHA1B2")
        val mid = wifi("AA:BB:CC:14:02:0A", "XBTS-0001")
        val bleName = ble(name = "STEINER Thermal Handheld-1")
            .copy(key = "BLE:st", mac = "AA:BB:CC:14:02:0B")
        val hik = wifi("AA:BB:CC:14:02:0C", "HIKMICRO_123")
        val thermion = wifi("AA:BB:CC:14:02:0D", "Thermion2_1234")
        val hits = SignatureEngine().match(
            listOf(handheld, h35, s35, c35, burris, shortBth, shortBts, shortBtc, noDash, mid, bleName, hik, thermion),
            stock,
        )
        fun ids(sighting: Sighting) = hits.getValue(sighting.key)
        assertTrue("handheld", "fleet-steiner-burris-thermal" in ids(handheld))
        assertTrue("H35", "fleet-steiner-burris-thermal" in ids(h35))
        assertTrue("S35", "fleet-steiner-burris-thermal" in ids(s35))
        assertTrue("C35", "fleet-steiner-burris-thermal" in ids(c35))
        assertTrue("Burris_BTH", "fleet-steiner-burris-thermal" in ids(burris))
        assertFalse("short BTH", "fleet-steiner-burris-thermal" in ids(shortBth))
        assertFalse("short BTS", "fleet-steiner-burris-thermal" in ids(shortBts))
        assertFalse("short BTC", "fleet-steiner-burris-thermal" in ids(shortBtc))
        assertFalse("no dash", "fleet-steiner-burris-thermal" in ids(noDash))
        assertFalse("in the middle", "fleet-steiner-burris-thermal" in ids(mid))
        assertFalse("BLE name", "fleet-steiner-burris-thermal" in ids(bleName))
        assertFalse("HIKMICRO", "fleet-steiner-burris-thermal" in ids(hik))
        assertTrue("HIKMICRO stays on its row", "fleet-hikmicro-optics" in ids(hik))
        assertFalse("Thermion", "fleet-steiner-burris-thermal" in ids(thermion))
        assertTrue("Thermion stays on Pulsar", "fleet-pulsar-optics" in ids(thermion))
    }

    @Test
    fun catalog114AddsSteinerM830LrfAsMilitary() {
        val stock = DefaultCatalog.fleets()
        val row = stock.single { it.id == "fleet-steiner-lrf" }
        assertEquals(SignatureClass.MILITARY, row.kind)
        assertEquals(7, row.colorIndex)
        assertTrue(row.attentionNote.isNotBlank())
        assertFalse(row.rules.any { it.companyId == 0x04CA || it.text.startsWith("Dongle") })
        assertEquals(
            listOf("watch-fleet-steiner-lrf"),
            DefaultCatalog.defaultWatchlist().filter { it.fleetId == row.id }.map { it.id },
        )
        val lrfX = ble(name = "LRF X 1234567").copy(key = "BLE:l1", mac = "AA:BB:CC:14:03:01")
        val steinerX = ble(name = "Steiner LRF X1234567").copy(key = "BLE:l2", mac = "AA:BB:CC:14:03:02")
        val lrf1535 = ble(name = "LRF1535 004711").copy(key = "BLE:l3", mac = "AA:BB:CC:14:03:03")
        val steiner1535 = ble(name = "Steiner LRF1535").copy(key = "BLE:l4", mac = "AA:BB:CC:14:03:04")
        val lower = ble(name = "lrf x 12").copy(key = "BLE:l5", mac = "AA:BB:CC:14:03:05")
        val dongle = ble(name = "Dongle 01").copy(key = "BLE:l6", mac = "AA:BB:CC:14:03:06")
        val noSpace = ble(name = "LRFX").copy(key = "BLE:l7", mac = "AA:BB:CC:14:03:07")
        val mine = ble(name = "MyLRF X").copy(key = "BLE:l8", mac = "AA:BB:CC:14:03:08")
        val accolade = wifi("AA:BB:CC:14:03:09", "Accolade 2 LRF_1234")
        val wifiName = wifi("AA:BB:CC:14:03:0A", "LRF X 1234567")
        val hits = SignatureEngine().match(
            listOf(lrfX, steinerX, lrf1535, steiner1535, lower, dongle, noSpace, mine, accolade, wifiName),
            stock,
        )
        fun ids(sighting: Sighting) = hits.getValue(sighting.key)
        assertTrue("LRF X", "fleet-steiner-lrf" in ids(lrfX))
        assertTrue("Steiner LRF X", "fleet-steiner-lrf" in ids(steinerX))
        assertTrue("LRF1535", "fleet-steiner-lrf" in ids(lrf1535))
        assertTrue("Steiner LRF1535", "fleet-steiner-lrf" in ids(steiner1535))
        assertTrue("lowercase", "fleet-steiner-lrf" in ids(lower))
        assertFalse("Dongle", "fleet-steiner-lrf" in ids(dongle))
        assertFalse("LRFX", "fleet-steiner-lrf" in ids(noSpace))
        assertFalse("MyLRF X", "fleet-steiner-lrf" in ids(mine))
        assertFalse("Accolade", "fleet-steiner-lrf" in ids(accolade))
        assertTrue("Accolade stays on Pulsar", "fleet-pulsar-optics" in ids(accolade))
        assertFalse("Wi-Fi name", "fleet-steiner-lrf" in ids(wifiName))
        assertFalse("thermal row", "fleet-steiner-burris-thermal" in ids(lrfX))
    }

    @Test
    fun catalog114AddsElkLedStrip() {
        val stock = DefaultCatalog.fleets()
        val row = stock.single { it.id == "fleet-elk-led" }
        assertEquals("ELK LED strip", row.name)
        assertEquals(SignatureClass.HOME, row.kind)
        assertTrue(row.attentionNote.isBlank())
        assertFalse("fleet-elk-led" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })
        assertEquals(listOf("ELK-BLEDO*"), row.rules.map { it.text })
        assertTrue(row.rules.all { it.kind == RuleKind.NAME_GLOB && it.radio == RadioKind.BLE })
        val dob = ble(name = "ELK-BLEDOB").copy(key = "BLE:e1", mac = "AA:BB:CC:14:04:01")
        val dom = ble(name = "ELK-BLEDOM").copy(key = "BLE:e2", mac = "AA:BB:CC:14:04:02")
        val suffix = ble(name = "ELK-BLEDOM0B").copy(key = "BLE:e3", mac = "AA:BB:CC:14:04:03")
        val lower = ble(name = "elk-bledob").copy(key = "BLE:e4", mac = "AA:BB:CC:14:04:04")
        val short = ble(name = "ELK-BLE").copy(key = "BLE:e5", mac = "AA:BB:CC:14:04:05")
        val bulb = ble(name = "ELK-BULB").copy(key = "BLE:e6", mac = "AA:BB:CC:14:04:06")
        val melk = ble(name = "MELK-1234").copy(key = "BLE:e7", mac = "AA:BB:CC:14:04:07")
        val hid = ble(name = "", serviceUuids = listOf("1812", "180F"), manufacturerId = 12849)
            .copy(key = "BLE:e8", mac = "AA:BB:CC:14:04:08")
        val wifiName = wifi("AA:BB:CC:14:04:09", "ELK-BLEDOB")
        val hits = SignatureEngine().match(
            listOf(dob, dom, suffix, lower, short, bulb, melk, hid, wifiName),
            stock,
        )
        fun ids(sighting: Sighting) = hits.getValue(sighting.key)
        assertTrue("ELK-BLEDOB", "fleet-elk-led" in ids(dob))
        assertTrue("ELK-BLEDOM", "fleet-elk-led" in ids(dom))
        assertTrue("ELK-BLEDOM0B", "fleet-elk-led" in ids(suffix))
        assertTrue("lowercase", "fleet-elk-led" in ids(lower))
        assertFalse("ELK-BLE", "fleet-elk-led" in ids(short))
        assertFalse("ELK-BULB", "fleet-elk-led" in ids(bulb))
        assertFalse("MELK name", "fleet-elk-led" in ids(melk))
        assertTrue("MELK stays on MELK", "fleet-melk" in ids(melk))
        assertFalse("HID and company field", "fleet-elk-led" in ids(hid))
        assertFalse("Wi-Fi name", "fleet-elk-led" in ids(wifiName))
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
        assertEquals(listOf("fleet-steiner-lrf"), stock.filter { it.kind == SignatureClass.MILITARY }.map { it.id })
        assertEquals(17, stock.count { it.kind == SignatureClass.TWO_WAY })

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

    @Test
    fun bleKissTncMatchesTheKissServiceUuid() {
        val stock = DefaultCatalog.fleets()
        val row = stock.single { it.id == "fleet-ble-kiss-tnc" }
        assertEquals(SignatureClass.TWO_WAY, row.kind)
        assertTrue(row.attentionNote.isBlank())
        assertTrue(row.matchAny)
        assertEquals(RuleKind.SERVICE_UUID, row.rules.single().kind)
        assertEquals(RadioKind.BLE, row.rules.single().radio)
        assertEquals("00000001-ba2a-46c9-ae49-01b0961f68bb", row.rules.single().text)
        assertFalse("fleet-ble-kiss-tnc" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })

        val kiss = "00000001-ba2a-46c9-ae49-01b0961f68bb"
        val blank = ble(name = "", serviceUuids = listOf(kiss)).copy(key = "BLE:k1", mac = "AA:BB:CC:DD:55:01")
        val upper = ble(name = "", serviceUuids = listOf(kiss.uppercase())).copy(key = "BLE:k2", mac = "AA:BB:CC:DD:55:02")
        val benshi = ble(name = "UV-PRO", serviceUuids = listOf(kiss)).copy(key = "BLE:k3", mac = "AA:BB:CC:DD:55:03")
        val tx = ble(name = "", serviceUuids = listOf("00000002-ba2a-46c9-ae49-01b0961f68bb"))
            .copy(key = "BLE:k4", mac = "AA:BB:CC:DD:55:04")
        val nordic = ble(name = "", serviceUuids = listOf("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
            .copy(key = "BLE:k5", mac = "AA:BB:CC:DD:55:05")
        val ffe0 = ble(name = "", serviceUuids = listOf("FFE0")).copy(key = "BLE:k6", mac = "AA:BB:CC:DD:55:06")
        val named = ble(name = "TNC4").copy(key = "BLE:k7", mac = "AA:BB:CC:DD:55:07")
        val wifiKiss = wifi("02:00:00:00:22:01").copy(serviceUuids = listOf(kiss))
        val hits = SignatureEngine().match(
            listOf(blank, upper, benshi, tx, nordic, ffe0, named, wifiKiss),
            stock,
        )
        assertTrue("blank", "fleet-ble-kiss-tnc" in hits.getValue(blank.key))
        assertTrue("upper case", "fleet-ble-kiss-tnc" in hits.getValue(upper.key))
        assertTrue("UV-PRO keeps KISS", "fleet-ble-kiss-tnc" in hits.getValue(benshi.key))
        assertTrue("UV-PRO keeps Benshi", "fleet-benshi-radio" in hits.getValue(benshi.key))
        assertFalse("TX characteristic", "fleet-ble-kiss-tnc" in hits.getValue(tx.key))
        assertFalse("Nordic UART", "fleet-ble-kiss-tnc" in hits.getValue(nordic.key))
        assertFalse("FFE0", "fleet-ble-kiss-tnc" in hits.getValue(ffe0.key))
        assertFalse("name TNC4", "fleet-ble-kiss-tnc" in hits.getValue(named.key))
        assertFalse("Wi-Fi", "fleet-ble-kiss-tnc" in hits.getValue(wifiKiss.key))
        assertTrue(
            DeviceExplain.guess(blank, listOf("BLE KISS TNC")).headline
                .contains("a Bluetooth packet-radio modem (KISS TNC)"),
        )
    }

    @Test
    fun icomIc705Id5200AndUt137MatchFactoryNames() {
        val stock = DefaultCatalog.fleets()
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        val ic705 = stock.single { it.id == "fleet-icom-ic705" }
        val id5200 = stock.single { it.id == "fleet-icom-id5200" }
        val ut137 = stock.single { it.id == "fleet-icom-ut137" }
        listOf(ic705, id5200, ut137).forEach { row ->
            assertEquals(SignatureClass.TWO_WAY, row.kind)
            assertTrue(row.attentionNote.isBlank())
            assertTrue(row.matchAny)
            assertTrue(row.rules.all { it.kind == RuleKind.NAME_GLOB && it.radio != null })
            assertTrue(
                row.rules.none {
                    it.kind == RuleKind.RADIO_KIND ||
                        it.kind == RuleKind.SERVICE_UUID ||
                        it.kind == RuleKind.MANUFACTURER_ID ||
                        it.kind == RuleKind.MANUFACTURER_DATA ||
                        it.kind == RuleKind.OUI
                },
            )
            assertFalse(row.id in watched)
        }
        assertEquals(listOf("ICOM BT(IC-705)", "IC-705"), ic705.rules.map { it.text })
        assertEquals(listOf(RadioKind.BLE, RadioKind.WIFI), ic705.rules.map { it.radio })
        assertEquals(listOf("ICOM BT(ID-5200)"), id5200.rules.map { it.text })
        assertEquals(RadioKind.BLE, id5200.rules.single().radio)
        assertEquals(listOf("ICOM BT-003"), ut137.rules.map { it.text })
        assertEquals(RadioKind.BLE, ut137.rules.single().radio)

        val ble705 = ble(name = "ICOM BT(IC-705)").copy(key = "BLE:c1", mac = "AA:BB:CC:DD:66:01")
        val ble705Lower = ble(name = "icom bt(ic-705)").copy(key = "BLE:c2", mac = "AA:BB:CC:DD:66:02")
        val wifi705 = wifi("02:00:00:00:66:03", "IC-705")
        val wifi705Lower = wifi("02:00:00:00:66:04", "ic-705")
        val bleBare = ble(name = "IC-705").copy(key = "BLE:c5", mac = "AA:BB:CC:DD:66:05")
        val wifiBleName = wifi("02:00:00:00:66:06", "ICOM BT(IC-705)")
        val wifi7050 = wifi("02:00:00:00:66:07", "IC-7050")
        val wifiHome = wifi("02:00:00:00:66:08", "IC-705-Home")
        val ble5200 = ble(name = "ICOM BT(ID-5200)").copy(key = "BLE:c9", mac = "AA:BB:CC:DD:66:09")
        val ble52 = ble(name = "ICOM BT(ID-52)").copy(key = "BLE:cA", mac = "AA:BB:CC:DD:66:0A")
        val ble003 = ble(name = "ICOM BT-003").copy(key = "BLE:cB", mac = "AA:BB:CC:DD:66:0B")
        val ble003Edited = ble(name = "ICOM BT-003 A").copy(key = "BLE:cC", mac = "AA:BB:CC:DD:66:0C")
        val ble002 = ble(name = "ICOM BT-002").copy(key = "BLE:cD", mac = "AA:BB:CC:DD:66:0D")
        val hits = SignatureEngine().match(
            listOf(
                ble705, ble705Lower, wifi705, wifi705Lower, bleBare, wifiBleName, wifi7050, wifiHome,
                ble5200, ble52, ble003, ble003Edited, ble002,
            ),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("IC-705 BLE", "fleet-icom-ic705" in ids(ble705))
        assertFalse("IC-705 BLE is not ID-52", "fleet-icom-id52" in ids(ble705))
        assertFalse("IC-705 BLE is not ID-5200", "fleet-icom-id5200" in ids(ble705))
        assertTrue("IC-705 lower", "fleet-icom-ic705" in ids(ble705Lower))
        assertTrue("IC-705 Wi-Fi", "fleet-icom-ic705" in ids(wifi705))
        assertTrue("ic-705 Wi-Fi", "fleet-icom-ic705" in ids(wifi705Lower))
        assertFalse("BLE name IC-705", "fleet-icom-ic705" in ids(bleBare))
        assertFalse("Wi-Fi Bluetooth name", "fleet-icom-ic705" in ids(wifiBleName))
        assertFalse("IC-7050", "fleet-icom-ic705" in ids(wifi7050))
        assertFalse("IC-705-Home", "fleet-icom-ic705" in ids(wifiHome))
        assertTrue("ID-5200", "fleet-icom-id5200" in ids(ble5200))
        assertFalse("ID-5200 is not ID-52", "fleet-icom-id52" in ids(ble5200))
        assertTrue("ID-52", "fleet-icom-id52" in ids(ble52))
        assertFalse("ID-52 is not ID-5200", "fleet-icom-id5200" in ids(ble52))
        assertTrue("UT-137", "fleet-icom-ut137" in ids(ble003))
        assertFalse("edited UT-137", "fleet-icom-ut137" in ids(ble003Edited))
        assertTrue("VS-3", "fleet-icom-vs3" in ids(ble002))
        assertFalse("VS-3 is not UT-137", "fleet-icom-ut137" in ids(ble002))
        assertTrue(
            DeviceExplain.guess(ble705, listOf("Icom IC-705")).headline
                .contains("an Icom IC-705 portable transceiver"),
        )
        assertTrue(
            DeviceExplain.guess(ble5200, listOf("Icom ID-5200")).headline
                .contains("an Icom ID-5200 mobile radio"),
        )
        assertTrue(
            DeviceExplain.guess(ble003, listOf("Icom UT-137")).headline
                .contains("an Icom ID-4100 mobile radio's UT-137 Bluetooth unit"),
        )
    }

    @Test
    fun codaPttButtonMatchesItsAdvertisedService() {
        val stock = DefaultCatalog.fleets()
        val row = stock.single { it.id == "fleet-coda-ptt-button" }
        assertEquals(SignatureClass.TWO_WAY, row.kind)
        assertTrue(row.attentionNote.isBlank())
        assertTrue(row.matchAny)
        assertEquals(RuleKind.SERVICE_UUID, row.rules.single().kind)
        assertEquals(RadioKind.BLE, row.rules.single().radio)
        assertEquals("2320ae58-8394-4652-95f7-0a872ac0958f", row.rules.single().text)
        assertFalse("fleet-coda-ptt-button" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })

        val uuid = "2320AE58-8394-4652-95F7-0A872AC0958F"
        val blank = ble(name = "", serviceUuids = listOf(uuid)).copy(key = "BLE:p1", mac = "AA:BB:CC:DD:77:01")
        val named = ble(name = "Blu-PTT", serviceUuids = listOf(uuid.lowercase()))
            .copy(key = "BLE:p2", mac = "AA:BB:CC:DD:77:02")
        val notify = ble(name = "", serviceUuids = listOf("89A8591D-BB19-485B-9F59-58492BC33E24"))
            .copy(key = "BLE:p3", mac = "AA:BB:CC:DD:77:03")
        val coda = ble(name = "CODA").copy(key = "BLE:p4", mac = "AA:BB:CC:DD:77:04")
        val pttZ = ble(name = "PTT-Z").copy(key = "BLE:p5", mac = "AA:BB:CC:DD:77:05")
        val ffe0 = ble(name = "", serviceUuids = listOf("FFE0")).copy(key = "BLE:p6", mac = "AA:BB:CC:DD:77:06")
        val wifiCoda = wifi("02:00:00:00:33:01").copy(serviceUuids = listOf(uuid))
        val hits = SignatureEngine().match(listOf(blank, named, notify, coda, pttZ, ffe0, wifiCoda), stock)
        assertTrue("blank", "fleet-coda-ptt-button" in hits.getValue(blank.key))
        assertTrue("named", "fleet-coda-ptt-button" in hits.getValue(named.key))
        assertFalse("notify service", "fleet-coda-ptt-button" in hits.getValue(notify.key))
        assertFalse("name CODA", "fleet-coda-ptt-button" in hits.getValue(coda.key))
        assertTrue("PTT-Z", "fleet-ble-ptt-button" in hits.getValue(pttZ.key))
        assertFalse("PTT-Z is not Blu-PTT", "fleet-coda-ptt-button" in hits.getValue(pttZ.key))
        assertFalse("FFE0", "fleet-coda-ptt-button" in hits.getValue(ffe0.key))
        assertFalse("Wi-Fi", "fleet-coda-ptt-button" in hits.getValue(wifiCoda.key))
        assertTrue(
            DeviceExplain.guess(blank, listOf("Blu-PTT button")).headline
                .contains("a Blu-PTT push-to-talk button"),
        )
    }

    @Test
    fun tidradioTdH9MatchesItsNamePrefix() {
        val stock = DefaultCatalog.fleets()
        val row = stock.single { it.id == "fleet-tidradio-td-h9" }
        assertEquals(SignatureClass.TWO_WAY, row.kind)
        assertTrue(row.attentionNote.isBlank())
        assertTrue(row.matchAny)
        assertEquals(RuleKind.NAME_GLOB, row.rules.single().kind)
        assertEquals("TD-H9*", row.rules.single().text)
        assertEquals(RadioKind.BLE, row.rules.single().radio)
        assertFalse("fleet-tidradio-td-h9" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })

        val coded = ble(name = "TD-H9-0536").copy(key = "BLE:t1", mac = "AA:BB:CC:DD:88:01")
        val lower = ble(name = "td-h9-0536").copy(key = "BLE:t2", mac = "AA:BB:CC:DD:88:02")
        val bare = ble(name = "TD-H9").copy(key = "BLE:t3", mac = "AA:BB:CC:DD:88:03")
        val h8 = ble(name = "TD-H8").copy(key = "BLE:t4", mac = "AA:BB:CC:DD:88:04")
        val h3 = ble(name = "TD-H3").copy(key = "BLE:t5", mac = "AA:BB:CC:DD:88:05")
        val prefixed = ble(name = "XTD-H9").copy(key = "BLE:t6", mac = "AA:BB:CC:DD:88:06")
        val ff00 = ble(name = "", serviceUuids = listOf("FF00")).copy(key = "BLE:t7", mac = "AA:BB:CC:DD:88:07")
        val wifiName = wifi("02:00:00:00:88:08", "TD-H9-0536")
        val hits = SignatureEngine().match(listOf(coded, lower, bare, h8, h3, prefixed, ff00, wifiName), stock)
        assertTrue("TD-H9-0536", "fleet-tidradio-td-h9" in hits.getValue(coded.key))
        assertTrue("lower", "fleet-tidradio-td-h9" in hits.getValue(lower.key))
        assertTrue("bare", "fleet-tidradio-td-h9" in hits.getValue(bare.key))
        assertFalse("TD-H8", "fleet-tidradio-td-h9" in hits.getValue(h8.key))
        assertFalse("TD-H3", "fleet-tidradio-td-h9" in hits.getValue(h3.key))
        assertFalse("XTD-H9", "fleet-tidradio-td-h9" in hits.getValue(prefixed.key))
        assertFalse("FF00", "fleet-tidradio-td-h9" in hits.getValue(ff00.key))
        assertFalse("Wi-Fi", "fleet-tidradio-td-h9" in hits.getValue(wifiName.key))
        assertTrue(
            DeviceExplain.guess(coded, listOf("TIDRADIO TD-H9")).headline
                .contains("a TIDRADIO TD-H9 handheld radio"),
        )
    }

    @Test
    fun ainaSmartButtonMatchesAsbPlusSixCharacters() {
        val stock = DefaultCatalog.fleets()
        val row = stock.single { it.id == "fleet-aina-smart-button" }
        assertEquals(SignatureClass.TWO_WAY, row.kind)
        assertTrue(row.attentionNote.isBlank())
        assertTrue(row.matchAny)
        assertEquals(RuleKind.NAME_GLOB, row.rules.single().kind)
        assertEquals("ASB??????", row.rules.single().text)
        assertEquals(RadioKind.BLE, row.rules.single().radio)
        assertFalse("fleet-aina-smart-button" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })

        val sample = ble(name = "ASB99C2F0").copy(key = "BLE:a1", mac = "AA:BB:CC:DD:99:01")
        val lower = ble(name = "asb99c2f0").copy(key = "BLE:a2", mac = "AA:BB:CC:DD:99:02")
        val zeros = ble(name = "ASB000001").copy(key = "BLE:a3", mac = "AA:BB:CC:DD:99:03")
        val short = ble(name = "ASB99C2F").copy(key = "BLE:a4", mac = "AA:BB:CC:DD:99:04")
        val long = ble(name = "ASB99C2F01").copy(key = "BLE:a5", mac = "AA:BB:CC:DD:99:05")
        val prefixed = ble(name = "XASB99C2F0").copy(key = "BLE:a6", mac = "AA:BB:CC:DD:99:06")
        val voice = ble(name = "APTT1234").copy(key = "BLE:a7", mac = "AA:BB:CC:DD:99:07")
        val pttZ = ble(name = "PTT-Z").copy(key = "BLE:a8", mac = "AA:BB:CC:DD:99:08")
        val wifiName = wifi("02:00:00:00:99:09", "ASB99C2F0")
        val hits = SignatureEngine().match(
            listOf(sample, lower, zeros, short, long, prefixed, voice, pttZ, wifiName),
            stock,
        )
        assertTrue("ASB99C2F0", "fleet-aina-smart-button" in hits.getValue(sample.key))
        assertTrue("lower", "fleet-aina-smart-button" in hits.getValue(lower.key))
        assertTrue("zeros", "fleet-aina-smart-button" in hits.getValue(zeros.key))
        assertFalse("five characters", "fleet-aina-smart-button" in hits.getValue(short.key))
        assertFalse("seven characters", "fleet-aina-smart-button" in hits.getValue(long.key))
        assertFalse("prefixed", "fleet-aina-smart-button" in hits.getValue(prefixed.key))
        assertFalse("Voice Responder", "fleet-aina-smart-button" in hits.getValue(voice.key))
        assertTrue("PTT-Z", "fleet-ble-ptt-button" in hits.getValue(pttZ.key))
        assertFalse("PTT-Z is not AINA", "fleet-aina-smart-button" in hits.getValue(pttZ.key))
        assertFalse("Wi-Fi", "fleet-aina-smart-button" in hits.getValue(wifiName.key))
        assertTrue(
            DeviceExplain.guess(sample, listOf("AINA PTT Smart Button")).headline
                .contains("an AINA push-to-talk button"),
        )
    }

    @Test
    fun wirelessCpsRadioMatchesExactWalkieTalkieName() {
        val stock = DefaultCatalog.fleets()
        val row = stock.single { it.id == "fleet-wireless-cps-radio" }
        assertEquals(SignatureClass.TWO_WAY, row.kind)
        assertTrue(row.attentionNote.isBlank())
        assertTrue(row.matchAny)
        assertEquals(RuleKind.NAME_GLOB, row.rules.single().kind)
        assertEquals("walkie-talkie", row.rules.single().text)
        assertEquals(RadioKind.BLE, row.rules.single().radio)
        assertFalse("fleet-wireless-cps-radio" in DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId })

        val lower = ble(name = "walkie-talkie").copy(key = "BLE:w1", mac = "AA:BB:CC:DD:AA:01")
        val mixed = ble(name = "Walkie-Talkie").copy(key = "BLE:w2", mac = "AA:BB:CC:DD:AA:02")
        val upper = ble(name = "WALKIE-TALKIE").copy(key = "BLE:w3", mac = "AA:BB:CC:DD:AA:03")
        val numbered = ble(name = "walkie-talkie 2").copy(key = "BLE:w4", mac = "AA:BB:CC:DD:AA:04")
        val mine = ble(name = "My walkie-talkie").copy(key = "BLE:w5", mac = "AA:BB:CC:DD:AA:05")
        val spaced = ble(name = "walkie talkie").copy(key = "BLE:w6", mac = "AA:BB:CC:DD:AA:06")
        val ffe0 = ble(name = "", serviceUuids = listOf("FFE0")).copy(key = "BLE:w7", mac = "AA:BB:CC:DD:AA:07")
        val wifiName = wifi("02:00:00:00:AA:08", "walkie-talkie")
        val hits = SignatureEngine().match(
            listOf(lower, mixed, upper, numbered, mine, spaced, ffe0, wifiName),
            stock,
        )
        assertTrue("lower", "fleet-wireless-cps-radio" in hits.getValue(lower.key))
        assertTrue("mixed", "fleet-wireless-cps-radio" in hits.getValue(mixed.key))
        assertTrue("upper", "fleet-wireless-cps-radio" in hits.getValue(upper.key))
        assertFalse("numbered", "fleet-wireless-cps-radio" in hits.getValue(numbered.key))
        assertFalse("prefixed", "fleet-wireless-cps-radio" in hits.getValue(mine.key))
        assertFalse("space", "fleet-wireless-cps-radio" in hits.getValue(spaced.key))
        assertFalse("FFE0", "fleet-wireless-cps-radio" in hits.getValue(ffe0.key))
        assertFalse("Wi-Fi", "fleet-wireless-cps-radio" in hits.getValue(wifiName.key))
        assertTrue(
            DeviceExplain.guess(lower, listOf("Walkie-talkie (wireless programming)")).headline
                .contains("a handheld radio with phone programming switched on"),
        )
    }

    @Test
    fun porkchopMatchesTheExactNameAndTheVendorMark() {
        val stock = DefaultCatalog.fleets()
        val pork = stock.single { it.id == "fleet-porkchop" }
        assertFalse(pork.rules.any { it.kind == RuleKind.NAME_CONTAINS })
        assertFalse(pork.rules.any { it.text == "PORKCHOP*" })
        val exact = wifi("02:11:22:33:44:55", "PORKCHOP")
        val lower = wifi("02:11:22:33:44:56", "porkchop")
        val soup = wifi("02:11:22:33:44:57", "PorkChopSoup")
        val guest = wifi("02:11:22:33:44:58", "PORKCHOP-Guest")
        val m5 = wifi("02:11:22:33:44:59", "M5PORKCHOP")
        val m5more = wifi("02:11:22:33:44:5A", "M5PORKCHOP-1")
        val marked = wifi("02:11:22:33:44:5B", "Cafe", vendorIe = "50:52:4B")
        val plain = wifi("02:11:22:33:44:5C", "Cafe")
        val bleName = ble(name = "PORKCHOP").copy(key = "BLE:AA:BB:CC:DD:EE:01", mac = "AA:BB:CC:DD:EE:01")
        val bleSoup = ble(name = "PorkChopSoup").copy(key = "BLE:AA:BB:CC:DD:EE:02", mac = "AA:BB:CC:DD:EE:02")
        val hits = SignatureEngine().match(
            listOf(exact, lower, soup, guest, m5, m5more, marked, plain, bleName, bleSoup),
            stock,
        )
        assertTrue(hits.getValue(exact.key).contains("fleet-porkchop"))
        assertTrue(hits.getValue(lower.key).contains("fleet-porkchop"))
        assertFalse(hits.getValue(soup.key).contains("fleet-porkchop"))
        assertFalse(hits.getValue(guest.key).contains("fleet-porkchop"))
        assertTrue(hits.getValue(m5.key).contains("fleet-porkchop"))
        assertTrue(hits.getValue(m5more.key).contains("fleet-porkchop"))
        assertTrue(hits.getValue(marked.key).contains("fleet-porkchop"))
        assertFalse(hits.getValue(plain.key).contains("fleet-porkchop"))
        assertTrue(hits.getValue(bleName.key).contains("fleet-porkchop"))
        assertFalse(hits.getValue(bleSoup.key).contains("fleet-porkchop"))
    }

    @Test
    fun catalog116AddsLednetwfEzLynkTrackunitAndOontz() {
        val stock = DefaultCatalog.fleets()
        val added = listOf("fleet-lednetwf", "fleet-ez-lynk", "fleet-trackunit", "fleet-oontz")
        assertEquals(390, stock.size)
        assertEquals(added.toSet(), stock.map { it.id }.filter { it in added }.toSet())
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        added.forEach { id ->
            val row = stock.single { it.id == id }
            assertTrue(id, row.builtIn)
            assertTrue(id, row.enabled)
            assertTrue(id, row.attentionNote.isBlank())
            assertTrue(id, row.matchAny)
            assertTrue(id, row.decode == null)
            assertEquals(id, 1, row.rules.size)
            assertFalse(id, id in watched)
        }
        val led = stock.single { it.id == "fleet-lednetwf" }
        val ez = stock.single { it.id == "fleet-ez-lynk" }
        val track = stock.single { it.id == "fleet-trackunit" }
        val speaker = stock.single { it.id == "fleet-oontz" }
        assertEquals(SignatureClass.HOME, led.kind)
        assertEquals(SignatureClass.VEHICLE, ez.kind)
        assertEquals(SignatureClass.VEHICLE, track.kind)
        assertEquals(SignatureClass.AUDIO, speaker.kind)
        assertEquals(RuleKind.NAME_GLOB, led.rules.single().kind)
        assertEquals("LEDnetWF*", led.rules.single().text)
        assertEquals(RadioKind.BLE, led.rules.single().radio)
        assertEquals(RuleKind.NAME_GLOB, ez.rules.single().kind)
        assertEquals("EZLYNK*", ez.rules.single().text)
        assertEquals(RadioKind.WIFI, ez.rules.single().radio)
        assertEquals(RuleKind.MANUFACTURER_ID, track.rules.single().kind)
        assertEquals(0x086F, track.rules.single().companyId)
        assertEquals(RadioKind.BLE, track.rules.single().radio)
        assertEquals(RuleKind.NAME_GLOB, speaker.rules.single().kind)
        assertEquals("OontZ*", speaker.rules.single().text)
        assertEquals(RadioKind.BLE, speaker.rules.single().radio)

        val ledOne = ble(name = "LEDnetWF02003544C420", manufacturerId = 0x5A00)
            .copy(key = "BLE:led1", mac = "E4:98:BB:00:00:01")
        val ledTwo = ble(name = "lednetwf02003544C622")
            .copy(key = "BLE:led2", mac = "E4:98:BB:00:00:02")
        val ledExact = ble(name = "LEDnetWF").copy(key = "BLE:led3", mac = "E4:98:BB:00:00:03")
        val ledPrefixed = ble(name = "xLEDnetWF0200").copy(key = "BLE:led4", mac = "E4:98:BB:00:00:04")
        val ledWifi = wifi("E4:98:BB:00:00:05", "LEDnetWF02003544C420")
        val ledCompany = ble(name = "", manufacturerId = 0x5A00)
            .copy(key = "BLE:led6", mac = "E4:98:BB:00:00:06")
        val ezExact = wifi("C2:EE:40:B5:63:F8", "EZLYNK")
        val ezOne = wifi("C2:EE:40:B5:63:F9", "EZLYNK_CCB0EFQ8")
        val ezTwo = wifi("50:33:8B:62:D1:9D", "EZLYNK_BEKK3AK7")
        val ezLower = wifi("50:33:8B:62:D1:9E", "ezlynk_abc")
        val ezShort = wifi("50:33:8B:62:D1:9F", "EZLYN")
        val ezPrefixed = wifi("50:33:8B:62:D1:A0", "XEZLYNK")
        val ezBle = ble(name = "EZLYNK_CCB0EFQ8").copy(key = "BLE:ez", mac = "C2:EE:40:B5:63:FA")
        val herc = ble(name = "Herc MAC", manufacturerId = 0x086F, serviceUuids = listOf("1802"))
            .copy(key = "BLE:herc", mac = "84:B4:DB:11:40:9A")
        val unit = ble(name = "TU3654367", manufacturerId = 0x086F)
            .copy(key = "BLE:tu", mac = "84:FD:27:EE:F8:F5")
        val unitBare = ble(name = "", manufacturerId = 0x086F)
            .copy(key = "BLE:tub", mac = "84:FD:27:EE:F8:F6")
        val immediate = ble(name = "Herc MAC", serviceUuids = listOf("1802"))
            .copy(key = "BLE:1802", mac = "84:B4:DB:11:40:9B")
        val trackWifi = wifi("84:B4:DB:11:40:9C", "Herc MAC").copy(manufacturerId = 0x086F)
        val oontz = ble(name = "OontZ Angle 3U App C17", manufacturerId = 0x03EE, serviceUuids = listOf("FFC0"))
            .copy(key = "BLE:oz1", mac = "AA:BB:CC:03:EE:01")
        val oontzTwo = ble(name = "OontZ Angle 3U App F92")
            .copy(key = "BLE:oz2", mac = "AA:BB:CC:03:EE:02")
        val oontzLower = ble(name = "oontz angle").copy(key = "BLE:oz3", mac = "AA:BB:CC:03:EE:03")
        val oontzPrefixed = ble(name = "My OontZ").copy(key = "BLE:oz4", mac = "AA:BB:CC:03:EE:04")
        val cube = ble(name = "", manufacturerId = 0x03EE).copy(key = "BLE:oz5", mac = "AA:BB:CC:03:EE:05")
        val ffc0 = ble(name = "", serviceUuids = listOf("FFC0")).copy(key = "BLE:oz6", mac = "AA:BB:CC:03:EE:06")
        val oontzWifi = wifi("AA:BB:CC:03:EE:07", "OontZ Angle 3U App C17")

        val hits = SignatureEngine().match(
            listOf(
                ledOne, ledTwo, ledExact, ledPrefixed, ledWifi, ledCompany,
                ezExact, ezOne, ezTwo, ezLower, ezShort, ezPrefixed, ezBle,
                herc, unit, unitBare, immediate, trackWifi,
                oontz, oontzTwo, oontzLower, oontzPrefixed, cube, ffc0, oontzWifi,
            ),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("LEDnetWF serial", "fleet-lednetwf" in ids(ledOne))
        assertTrue("lednetwf lower", "fleet-lednetwf" in ids(ledTwo))
        assertTrue("LEDnetWF exact", "fleet-lednetwf" in ids(ledExact))
        assertFalse("prefixed LEDnetWF", "fleet-lednetwf" in ids(ledPrefixed))
        assertFalse("LEDnetWF Wi-Fi", "fleet-lednetwf" in ids(ledWifi))
        assertFalse("LEDnetWF company id", "fleet-lednetwf" in ids(ledCompany))
        assertTrue("EZLYNK exact", "fleet-ez-lynk" in ids(ezExact))
        assertTrue("EZLYNK serial", "fleet-ez-lynk" in ids(ezOne))
        assertTrue("EZLYNK second", "fleet-ez-lynk" in ids(ezTwo))
        assertTrue("ezlynk lower", "fleet-ez-lynk" in ids(ezLower))
        assertFalse("short EZLYN", "fleet-ez-lynk" in ids(ezShort))
        assertFalse("prefixed EZLYNK", "fleet-ez-lynk" in ids(ezPrefixed))
        assertFalse("EZLYNK Bluetooth", "fleet-ez-lynk" in ids(ezBle))
        assertTrue("Trackunit Herc", "fleet-trackunit" in ids(herc))
        assertTrue("Trackunit serial", "fleet-trackunit" in ids(unit))
        assertTrue("Trackunit company id", "fleet-trackunit" in ids(unitBare))
        assertFalse("Immediate Alert", "fleet-trackunit" in ids(immediate))
        assertFalse("Trackunit Wi-Fi", "fleet-trackunit" in ids(trackWifi))
        assertTrue("OontZ name", "fleet-oontz" in ids(oontz))
        assertTrue("OontZ second", "fleet-oontz" in ids(oontzTwo))
        assertTrue("oontz lower", "fleet-oontz" in ids(oontzLower))
        assertFalse("prefixed OontZ", "fleet-oontz" in ids(oontzPrefixed))
        assertFalse("Cube company id", "fleet-oontz" in ids(cube))
        assertFalse("FFC0", "fleet-oontz" in ids(ffc0))
        assertFalse("OontZ Wi-Fi", "fleet-oontz" in ids(oontzWifi))
        assertTrue(
            DeviceExplain.guess(ledOne, listOf("LEDnetWF")).headline.contains("a Zengge LED controller"),
        )
        assertTrue(
            DeviceExplain.guess(ezOne, listOf("EZ Lynk")).headline.contains("an EZ Lynk vehicle tuner"),
        )
        assertTrue(
            DeviceExplain.guess(unit, listOf("Trackunit")).headline.contains("a Trackunit equipment tracker"),
        )
        assertTrue(
            DeviceExplain.guess(oontz, listOf("OontZ")).headline.contains("an OontZ speaker"),
        )
    }

    @Test
    fun catalog117AddsItagViofoAndTheUfoDrone() {
        val stock = DefaultCatalog.fleets()
        assertEquals(390, stock.size)
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        val tag = stock.single { it.id == "fleet-itag" }
        val cam = stock.single { it.id == "fleet-viofo" }
        val drone = stock.single { it.id == "fleet-ufo-drone" }
        listOf(tag, cam, drone).forEach { row ->
            assertTrue(row.id, row.builtIn)
            assertTrue(row.id, row.enabled)
            assertTrue(row.id, row.attentionNote.isBlank())
            assertTrue(row.id, row.matchAny)
            assertTrue(row.id, row.decode == null)
        }
        assertEquals(SignatureClass.FINDER, tag.kind)
        assertEquals(SignatureClass.VEHICLE, cam.kind)
        assertEquals(SignatureClass.DRONE, drone.kind)
        assertEquals(listOf(bleGlobText("iTAG")), tag.rules.map { it.text to it.radio })
        assertEquals(listOf("VIOFO*" to RadioKind.WIFI), cam.rules.map { it.text to it.radio })
        assertEquals(
            listOf("WIFI_UFO*" to RadioKind.WIFI, "FLY-UFO-*" to RadioKind.WIFI),
            drone.rules.map { it.text to it.radio },
        )
        assertFalse("fleet-itag" in watched)
        assertFalse("fleet-viofo" in watched)
        assertTrue("fleet-ufo-drone" in watched)

        val exact = ble(name = "iTAG").copy(key = "BLE:it1", mac = "AA:BB:CC:00:00:01")
        val lower = ble(name = "itag").copy(key = "BLE:it2", mac = "AA:BB:CC:00:00:02")
        val longer = ble(name = "iTAG-1").copy(key = "BLE:it3", mac = "AA:BB:CC:00:00:03")
        val prefixed = ble(name = "xiTAG").copy(key = "BLE:it4", mac = "AA:BB:CC:00:00:04")
        val tagWifi = wifi("AA:BB:CC:00:00:05", "iTAG")
        val viofo = wifi("02:11:22:33:44:01", "VIOFO_A329T_1234")
        val viofoDash = wifi("02:11:22:33:44:02", "VIOFO-A119M-1234")
        val viofoLower = wifi("02:11:22:33:44:03", "viofo")
        val viofoPrefixed = wifi("02:11:22:33:44:04", "XVIOFO")
        val viofoBle = ble(name = "VIOFO_A329T_1234").copy(key = "BLE:vf", mac = "02:11:22:33:44:05")
        val wifiUfo = wifi("02:11:22:33:44:11", "WIFI_UFO_40828c")
        val wifiUfoBare = wifi("02:11:22:33:44:12", "WiFi_UFO")
        val fly = wifi("02:11:22:33:44:13", "FLY-UFO-123456")
        val flow = wifi("02:11:22:33:44:14", "FLOW-UFO-123456")
        val flowShort = wifi("02:11:22:33:44:15", "FLOW_123456")
        val visuo = wifi("02:11:22:33:44:16", "WiFi-720P-1234")
        val ufoBle = ble(name = "WIFI_UFO_40828c").copy(key = "BLE:ufo", mac = "02:11:22:33:44:17")

        val hits = SignatureEngine().match(
            listOf(
                exact, lower, longer, prefixed, tagWifi,
                viofo, viofoDash, viofoLower, viofoPrefixed, viofoBle,
                wifiUfo, wifiUfoBare, fly, flow, flowShort, visuo, ufoBle,
            ),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("iTAG exact", "fleet-itag" in ids(exact))
        assertTrue("itag lower", "fleet-itag" in ids(lower))
        assertFalse("iTAG longer", "fleet-itag" in ids(longer))
        assertFalse("prefixed iTAG", "fleet-itag" in ids(prefixed))
        assertFalse("iTAG Wi-Fi", "fleet-itag" in ids(tagWifi))
        assertTrue("VIOFO serial", "fleet-viofo" in ids(viofo))
        assertTrue("VIOFO dash", "fleet-viofo" in ids(viofoDash))
        assertTrue("viofo lower", "fleet-viofo" in ids(viofoLower))
        assertFalse("prefixed VIOFO", "fleet-viofo" in ids(viofoPrefixed))
        assertFalse("VIOFO Bluetooth", "fleet-viofo" in ids(viofoBle))
        assertTrue("WIFI_UFO serial", "fleet-ufo-drone" in ids(wifiUfo))
        assertTrue("WiFi_UFO bare", "fleet-ufo-drone" in ids(wifiUfoBare))
        assertTrue("FLY-UFO", "fleet-ufo-drone" in ids(fly))
        assertFalse("FLOW-UFO", "fleet-ufo-drone" in ids(flow))
        assertFalse("FLOW_ short", "fleet-ufo-drone" in ids(flowShort))
        assertFalse("WiFi-720P", "fleet-ufo-drone" in ids(visuo))
        assertFalse("UFO Bluetooth", "fleet-ufo-drone" in ids(ufoBle))
    }

    @Test
    fun catalog118AddsSetupAndSerialNames() {
        val stock = DefaultCatalog.fleets()
        assertEquals(390, stock.size)
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        val tuya = stock.single { it.id == "fleet-tuya" }
        val unifi = stock.single { it.id == "fleet-unifi" }
        val midea = stock.single { it.id == "fleet-midea" }
        val motive = stock.single { it.id == "fleet-motive" }
        listOf(tuya, unifi, midea, motive).forEach { row ->
            assertTrue(row.id, row.attentionNote.isBlank())
            assertFalse(row.id, row.id in watched)
            assertFalse(row.id, row.rules.any { it.kind == RuleKind.OUI })
        }
        assertEquals(
            listOf("SmartLife-????" to RadioKind.WIFI, "SmartLife_????" to RadioKind.WIFI),
            tuya.rules.filter { it.text.startsWith("SmartLife") }.map { it.text to it.radio },
        )
        assertEquals(listOf("UDM Setup*" to RadioKind.WIFI), unifi.rules.filter { it.text.startsWith("UDM") }.map { it.text to it.radio })
        assertFalse(unifi.rules.any { it.text.equals("UDM", true) || it.text.startsWith("UDM-") })
        assertEquals(0x06A8, midea.rules.single { it.kind == RuleKind.MANUFACTURER_ID }.companyId)
        assertEquals(listOf("net_ac_*" to RadioKind.WIFI), midea.rules.filter { it.kind == RuleKind.NAME_GLOB }.map { it.text to it.radio })
        assertFalse(midea.rules.any { it.text.equals("net", true) || it.text.startsWith("midea_") })
        assertEquals(listOf("AABL*" to RadioKind.BLE), motive.rules.filter { it.text.startsWith("AABL") }.map { it.text to it.radio })
        assertFalse(motive.rules.any { it.text.equals("FC6D", true) })

        val hyphen = wifi("02:11:22:33:50:01", "SmartLife-EE19")
        val underscore = wifi("02:11:22:33:50:02", "smartlife_ee19")
        val shortLife = wifi("02:11:22:33:50:03", "SmartLife-EE1")
        val longLife = wifi("02:11:22:33:50:04", "SmartLife-EE190")
        val bareLife = wifi("02:11:22:33:50:05", "SmartLife")
        val lifeBle = ble(name = "SmartLife-EE19").copy(key = "BLE:life", mac = "02:11:22:33:50:06")
        val udm = wifi("02:11:22:33:50:11", "UDM Setup 1A2B")
        val udmBare = wifi("02:11:22:33:50:12", "UDM")
        val udmDash = wifi("02:11:22:33:50:13", "UDM-Pro")
        val udmBle = ble(name = "UDM").copy(key = "BLE:udm", mac = "02:11:22:33:50:14")
        val udmBleDash = ble(name = "UDM-1234").copy(key = "BLE:udm2", mac = "02:11:22:33:50:15")
        val ac = wifi("02:11:22:33:50:21", "net_ac_A1B2")
        val net = wifi("02:11:22:33:50:22", "net")
        val mideaName = wifi("02:11:22:33:50:23", "midea_ab_1234")
        val acShort = wifi("02:11:22:33:50:24", "net_ac")
        val acBle = ble(name = "net_ac_A1B2").copy(key = "BLE:ac", mac = "02:11:22:33:50:25")
        val gateway = ble(name = "AABL36Q1234567").copy(key = "BLE:aabl", mac = "02:11:22:33:50:31")
        val gatewayBare = ble(name = "aabl").copy(key = "BLE:aabl2", mac = "02:11:22:33:50:32")
        val gatewayWifi = wifi("02:11:22:33:50:33", "AABL36Q1234567")
        val prefixed = ble(name = "XAABL36").copy(key = "BLE:xaabl", mac = "02:11:22:33:50:34")

        val hits = SignatureEngine().match(
            listOf(
                hyphen, underscore, shortLife, longLife, bareLife, lifeBle,
                udm, udmBare, udmDash, udmBle, udmBleDash,
                ac, net, mideaName, acShort, acBle,
                gateway, gatewayBare, gatewayWifi, prefixed,
            ),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("SmartLife hyphen", "fleet-tuya" in ids(hyphen))
        assertTrue("SmartLife underscore", "fleet-tuya" in ids(underscore))
        assertFalse("SmartLife short", "fleet-tuya" in ids(shortLife))
        assertFalse("SmartLife long", "fleet-tuya" in ids(longLife))
        assertFalse("SmartLife bare", "fleet-tuya" in ids(bareLife))
        assertFalse("SmartLife Bluetooth", "fleet-tuya" in ids(lifeBle))
        assertTrue("UDM Setup", "fleet-unifi" in ids(udm))
        assertFalse("UDM bare", "fleet-unifi" in ids(udmBare))
        assertFalse("UDM dash", "fleet-unifi" in ids(udmDash))
        assertFalse("UDM Bluetooth", "fleet-unifi" in ids(udmBle))
        assertFalse("UDM- Bluetooth", "fleet-unifi" in ids(udmBleDash))
        assertTrue("net_ac_", "fleet-midea" in ids(ac))
        assertFalse("the word net", "fleet-midea" in ids(net))
        assertFalse("midea_ name", "fleet-midea" in ids(mideaName))
        assertFalse("net_ac short", "fleet-midea" in ids(acShort))
        assertFalse("net_ac Bluetooth", "fleet-midea" in ids(acBle))
        assertTrue("AABL serial", "fleet-motive" in ids(gateway))
        assertTrue("AABL bare", "fleet-motive" in ids(gatewayBare))
        assertFalse("AABL Wi-Fi", "fleet-motive" in ids(gatewayWifi))
        assertFalse("prefixed AABL", "fleet-motive" in ids(prefixed))
    }

    @Test
    fun catalog118AddsProductNames() {
        val stock = DefaultCatalog.fleets()
        assertEquals(390, stock.size)
        assertEquals(ADDED_IN_V118.size, 29)
        val watched = DefaultCatalog.defaultWatchlist().mapNotNull { it.fleetId }.toSet()
        ADDED_IN_V118.forEach { id ->
            val row = stock.single { it.id == id }
            assertTrue(id, row.builtIn)
            assertTrue(id, row.enabled)
            assertTrue(id, row.attentionNote.isBlank())
            assertTrue(id, row.matchAny)
            assertFalse(id, id in watched)
            assertFalse(id, row.rules.any { it.kind == RuleKind.OUI })
        }
        assertFalse(stock.any { it.id == "fleet-eddystone" || it.id == "fleet-ezlynk" })
        assertFalse(stock.any { fleet ->
            fleet.rules.any {
                it.companyId == 0x055D || it.companyId == 0x01F4 || it.companyId == 0x0794 || it.companyId == 0x05C7
            }
        })
        val gn = stock.single { it.id == "fleet-gn-hearing" }
        assertTrue(gn.rules.any { it.companyId == 0x0089 })
        assertTrue(gn.rules.any { it.text.equals("FEFE", true) })
        assertFalse(gn.rules.any { it.text.equals("FD20", true) })

        val air = ble(name = "WirelessONE-2").copy(key = "BLE:air", mac = "02:11:22:33:60:01")
        val dreo = ble(name = "DREO-FAN").copy(key = "BLE:dreo", mac = "02:11:22:33:60:02")
        val prep = ble(name = "PrepWizard-1").copy(key = "BLE:prep", mac = "02:11:22:33:60:03")
        val dash = wifi("02:11:22:33:60:04", "REDTIGER-A1")
        val dashLower = wifi("02:11:22:33:60:05", "Redtiger-A1")
        val dashBle = ble(name = "REDTIGER-A1").copy(key = "BLE:dash", mac = "02:11:22:33:60:06")
        val heater = ble(name = "Rinnai CX").copy(key = "BLE:rinnai", mac = "02:11:22:33:60:07")
        val heaterBare = ble(name = "Rinnai").copy(key = "BLE:rinnai2", mac = "02:11:22:33:60:08")
        val opt = wifi("02:11:22:33:60:09", "MyOptimum-123")
        val altice = wifi("02:11:22:33:60:0A", "optimumwifi")
        val kiaWifi = wifi("02:11:22:33:60:0B", "Kia_Soul")
        val kiaBle = ble(name = "Kia Soul").copy(key = "BLE:kia", mac = "02:11:22:33:60:0C")
        val nokia = ble(name = "Nokia").copy(key = "BLE:nokia", mac = "02:11:22:33:60:0D")
        val hughes = wifi("02:11:22:33:60:0E", "HughesNet-1")
        val sonic = wifi("02:11:22:33:60:0F", "SonicWall-TZ")
        val ubee = wifi("02:11:22:33:60:10", "Ubee-1234")
        val toast = wifi("02:11:22:33:60:11", "CAFE_TOAST")
        val toastMiss = wifi("02:11:22:33:60:12", "TOAST_BAR")
        val logger = ble(name = "DL-1234567890").copy(key = "BLE:dl", mac = "02:11:22:33:60:13")
        val loggerShort = ble(name = "DL-123456789").copy(key = "BLE:dl2", mac = "02:11:22:33:60:14")
        val parrot = ble(name = "BlueParrott B450").copy(key = "BLE:bp", mac = "02:11:22:33:60:15")
        val nimbus = ble(name = "ST-NIMBUS-1").copy(key = "BLE:nim", mac = "02:11:22:33:60:16")
        val table = ble(manufacturerId = 0x1006, manufacturerDataHex = "534F554E445441424C45")
            .copy(key = "BLE:table", mac = "02:11:22:33:60:17")
        val bareTable = ble(manufacturerId = 0x1006).copy(key = "BLE:table2", mac = "02:11:22:33:60:18")
        val solar = ble(manufacturerId = 0x02E1).copy(key = "BLE:vic", mac = "02:11:22:33:60:19")
        val cpap = ble(name = "ResMed 11").copy(key = "BLE:res", mac = "02:11:22:33:60:1A")
        val tank = ble(manufacturerId = 0x03B1).copy(key = "BLE:oto", mac = "02:11:22:33:60:1B")
        val scent = ble(name = "Pura-4").copy(key = "BLE:pura", mac = "02:11:22:33:60:1C")
        val arrow = ble(name = "ArrowQ1").copy(key = "BLE:arrow", mac = "02:11:22:33:60:1D")
        val beach = ble(manufacturerId = 0x00D9).copy(key = "BLE:tb", mac = "02:11:22:33:60:1E")
        val hearing = ble(manufacturerId = 0x0089).copy(key = "BLE:gn", mac = "02:11:22:33:60:1F")
        val treadmill = ble(manufacturerId = 0x01A5).copy(key = "BLE:ifit", mac = "02:11:22:33:60:20")
        val ambeo = ble(name = "AMBEO Soundbar").copy(key = "BLE:amb", mac = "02:11:22:33:60:21")
        val remote = ble(name = "LCIRemote1").copy(key = "BLE:lci", mac = "02:11:22:33:60:22")
        val hub = ble(name = "VIVINT-HUB-1").copy(key = "BLE:viv", mac = "02:11:22:33:60:23")
        val porch = ble(name = "Kuna Light").copy(key = "BLE:kuna", mac = "02:11:22:33:60:24")
        val pump = ble(name = "ALPHA HWR 15").copy(key = "BLE:pump2", mac = "02:11:22:33:60:25")
        val dei = ble(name = "DEI-DS4").copy(key = "BLE:dei", mac = "02:11:22:33:60:26")
        val base = ble(name = "LHB-2").copy(key = "BLE:lhb", mac = "02:11:22:33:60:27")
        val valve = ble(manufacturerId = 0x055D).copy(key = "BLE:valve", mac = "02:11:22:33:60:28")

        val hits = SignatureEngine().match(
            listOf(
                air, dreo, prep, dash, dashLower, dashBle, heater, heaterBare, opt, altice,
                kiaWifi, kiaBle, nokia, hughes, sonic, ubee, toast, toastMiss, logger, loggerShort,
                parrot, nimbus, table, bareTable, solar, cpap, tank, scent, arrow, beach, hearing,
                treadmill, ambeo, remote, hub, porch, pump, dei, base, valve,
            ),
            stock,
        )
        fun ids(radio: Sighting) = hits.getValue(radio.key)
        assertTrue("WirelessONE", "fleet-air-lift" in ids(air))
        assertTrue("Dreo", "fleet-dreo" in ids(dreo))
        assertTrue("PrepWizard", "fleet-prepwizard" in ids(prep))
        assertTrue("REDTIGER", "fleet-redtiger" in ids(dash))
        assertTrue("Redtiger", "fleet-redtiger" in ids(dashLower))
        assertFalse("Redtiger Bluetooth", "fleet-redtiger" in ids(dashBle))
        assertTrue("Rinnai space", "fleet-rinnai" in ids(heater))
        assertFalse("Rinnai bare", "fleet-rinnai" in ids(heaterBare))
        assertTrue("MyOptimum", "fleet-optimum" in ids(opt))
        assertTrue("optimumwifi", "fleet-optimum" in ids(altice))
        assertTrue("Kia Wi-Fi", "fleet-kia" in ids(kiaWifi))
        assertTrue("Kia Bluetooth", "fleet-kia" in ids(kiaBle))
        assertFalse("Nokia", "fleet-kia" in ids(nokia))
        assertTrue("HughesNet", "fleet-hughesnet" in ids(hughes))
        assertTrue("SonicWall", "fleet-sonicwall" in ids(sonic))
        assertTrue("Ubee", "fleet-ubee" in ids(ubee))
        assertTrue("Toast suffix", "fleet-toast" in ids(toast))
        assertFalse("Toast prefix", "fleet-toast" in ids(toastMiss))
        assertTrue("Danlaw ten", "fleet-danlaw" in ids(logger))
        assertFalse("Danlaw nine", "fleet-danlaw" in ids(loggerShort))
        assertTrue("BlueParrott", "fleet-blueparrott" in ids(parrot))
        assertTrue("Nimbus", "fleet-soundstream" in ids(nimbus))
        assertTrue("SOUNDTABLE", "fleet-soundstream" in ids(table))
        assertFalse("bare 0x1006", "fleet-soundstream" in ids(bareTable))
        assertTrue("Victron", "fleet-victron" in ids(solar))
        assertTrue("ResMed", "fleet-resmed" in ids(cpap))
        assertTrue("Otodata", "fleet-otodata" in ids(tank))
        assertTrue("Pura", "fleet-pura" in ids(scent))
        assertTrue("ArrowQ", "fleet-procon" in ids(arrow))
        assertTrue("Turtle Beach", "fleet-turtle-beach" in ids(beach))
        assertTrue("GN Hearing", "fleet-gn-hearing" in ids(hearing))
        assertTrue("iFIT", "fleet-ifit" in ids(treadmill))
        assertTrue("AMBEO", "fleet-sennheiser" in ids(ambeo))
        assertTrue("Lippert", "fleet-lippert" in ids(remote))
        assertTrue("Vivint", "fleet-vivint" in ids(hub))
        assertTrue("Kuna", "fleet-kuna" in ids(porch))
        assertTrue("Grundfos", "fleet-grundfos" in ids(pump))
        assertTrue("Directed", "fleet-directed" in ids(dei))
        assertTrue("Lighthouse", "fleet-lighthouse" in ids(base))
        assertFalse("Valve company id", "fleet-lighthouse" in ids(valve))
    }

    private fun bleGlobText(pattern: String) = pattern to RadioKind.BLE

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
