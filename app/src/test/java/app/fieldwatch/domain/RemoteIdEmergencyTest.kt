package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteIdEmergencyTest {
    @Test
    fun switchOffLatchesAndTurningOnDoesNotSpeak() {
        val watch = RemoteIdEmergency()
        val drone = drone(status = OpenDroneId.STATUS_EMERGENCY)
        val off = watch.pass(listOf(drone), enabled = false)
        assertTrue(off.fires.isEmpty())
        assertNull(watch.notice.value)
        val on = watch.pass(listOf(drone), enabled = true)
        assertTrue(on.fires.isEmpty())
        assertNull(watch.notice.value)
    }

    @Test
    fun firstHearAlreadyDeclaredFiresOnce() {
        val watch = RemoteIdEmergency()
        val drone = drone(status = OpenDroneId.STATUS_EMERGENCY, aircraft = "Mini 3")
        val first = watch.pass(listOf(drone), enabled = true)
        assertEquals(1, first.fires.size)
        assertEquals("Remote ID Mini 3 declared an emergency.", first.fires.single().body)
        assertEquals("Remote ID emergency", first.fires.single().title)
        assertTrue(watch.notice.value!!.showDialog)
        val again = watch.pass(listOf(drone), enabled = true)
        assertTrue(again.fires.isEmpty())
        assertEquals("Remote ID Mini 3 declared an emergency.", watch.notice.value!!.body)
    }

    @Test
    fun aNonEmergencyLocationClearsAndALaterDeclarationFires() {
        val watch = RemoteIdEmergency()
        val declared = drone(status = OpenDroneId.STATUS_EMERGENCY)
        assertEquals(1, watch.pass(listOf(declared), enabled = true).fires.size)
        val airborne = drone(status = 2)
        assertTrue(watch.pass(listOf(airborne), enabled = true).fires.isEmpty())
        val next = watch.pass(listOf(declared), enabled = true)
        assertEquals(1, next.fires.size)
    }

    @Test
    fun aBasicIdNeitherStartsNorEnds() {
        val watch = RemoteIdEmergency()
        val basic = drone()
        assertTrue(watch.pass(listOf(basic), enabled = true).fires.isEmpty())
        assertNull(watch.notice.value)
        val declared = drone(status = OpenDroneId.STATUS_EMERGENCY)
        assertEquals(1, watch.pass(listOf(declared), enabled = true).fires.size)
        assertTrue(watch.pass(listOf(basic), enabled = true).fires.isEmpty())
        assertTrue(watch.pass(listOf(declared), enabled = true).fires.isEmpty())
    }

    @Test
    fun bluetoothAndWifiOfTheSameUasAreOneWarning() {
        val watch = RemoteIdEmergency()
        val ble = drone(status = OpenDroneId.STATUS_EMERGENCY, kind = RadioKind.BLE, mac = "02:11:25:4A:00:01")
        val wifi = drone(
            status = 2,
            kind = RadioKind.WIFI,
            mac = "02:11:25:4A:00:02",
            aircraft = "Mini 3",
        )
        val out = watch.pass(listOf(ble, wifi), enabled = true)
        assertEquals(1, out.fires.size)
        assertEquals("Remote ID Mini 3 declared an emergency.", out.fires.single().body)
        assertTrue(watch.pass(listOf(ble, wifi), enabled = true).fires.isEmpty())
    }

    @Test
    fun aBlankUasIdUsesTheRadioKey() {
        val watch = RemoteIdEmergency()
        val one = drone(uas = null, status = OpenDroneId.STATUS_EMERGENCY, mac = "02:11:25:4A:00:01")
        val two = drone(uas = "  ", status = OpenDroneId.STATUS_EMERGENCY, mac = "02:11:25:4A:00:02")
        val out = watch.pass(listOf(one, two), enabled = true)
        assertEquals(2, out.fires.size)
        assertEquals("Remote ID declared an emergency.", out.fires[0].body)
        assertEquals("Remote ID declared an emergency.", out.fires[1].body)
        assertEquals(out.fires[0].body, watch.notice.value!!.body)
        watch.dismiss()
        assertEquals(out.fires[1].body, watch.notice.value!!.body)
    }

    @Test
    fun aMarkedMineRadioStillFires() {
        val watch = RemoteIdEmergency()
        val mine = drone(status = OpenDroneId.STATUS_EMERGENCY, aircraft = "Mine bird")
        val out = watch.pass(listOf(mine), enabled = true)
        assertEquals("Remote ID Mine bird declared an emergency.", out.fires.single().body)
    }

    @Test
    fun theNameIsTheAircraftThenTheUasIdAndTheWordsStayOffTheLine() {
        val watch = RemoteIdEmergency()
        val named = drone(
            status = OpenDroneId.STATUS_EMERGENCY,
            aircraft = "Mini 3",
            emergency = "Low battery",
            selfId = "Yard loop",
        )
        val namedLine = watch.pass(listOf(named), enabled = true).fires.single().body
        assertEquals("Remote ID Mini 3 declared an emergency.", namedLine)
        assertFalse(namedLine.contains("Low battery"))
        assertFalse(namedLine.contains("Yard loop"))
        assertFalse(namedLine.contains("!"))

        val idOnly = RemoteIdEmergency()
        val plate = drone(status = OpenDroneId.STATUS_EMERGENCY, emergency = "Low battery", selfId = "Yard loop")
        val plateLine = idOnly.pass(listOf(plate), enabled = true).fires.single().body
        assertEquals("Remote ID $UAS declared an emergency.", plateLine)
        assertFalse(plateLine.contains("Low battery"))

        val bare = RemoteIdEmergency()
        val unnamed = drone(uas = null, emergency = "")
        assertEquals(
            "Remote ID declared an emergency.",
            bare.pass(listOf(unnamed), enabled = true).fires.single().body,
        )
    }

    @Test
    fun ridFailureDoesNotDeclare() {
        val watch = RemoteIdEmergency()
        val failed = drone(status = 4)
        assertTrue(watch.pass(listOf(failed), enabled = true).fires.isEmpty())
        val declared = drone(status = OpenDroneId.STATUS_EMERGENCY)
        assertEquals(1, watch.pass(listOf(declared), enabled = true).fires.size)
        assertTrue(watch.pass(listOf(failed), enabled = true).fires.isEmpty())
        assertEquals(1, watch.pass(listOf(declared), enabled = true).fires.size)
    }

    @Test
    fun anEmptyEmergencyDescriptionStillDeclares() {
        val watch = RemoteIdEmergency()
        val out = watch.pass(listOf(drone(emergency = "")), enabled = true)
        assertEquals("Remote ID $UAS declared an emergency.", out.fires.single().body)
    }

    @Test
    fun twoDronesAreTwoWarnings() {
        val watch = RemoteIdEmergency()
        val a = drone(uas = "ONE", status = OpenDroneId.STATUS_EMERGENCY, aircraft = "One")
        val b = drone(uas = "TWO", status = OpenDroneId.STATUS_EMERGENCY, aircraft = "Two", mac = "02:11:25:4A:00:09")
        val out = watch.pass(listOf(a, b), enabled = true)
        assertEquals(2, out.fires.size)
        assertEquals("Remote ID One declared an emergency.", out.fires[0].body)
        assertEquals("Remote ID Two declared an emergency.", out.fires[1].body)
        assertEquals(0x4F, out.fires[0].noteId ushr 24)
        assertEquals(0x4F, out.fires[1].noteId ushr 24)
        assertTrue(out.fires[0].noteId != out.fires[1].noteId)
    }

    @Test
    fun aDeclaredRadioWinsOverASiblingThatHasEnded() {
        val watch = RemoteIdEmergency()
        val ble = drone(status = OpenDroneId.STATUS_EMERGENCY, kind = RadioKind.BLE)
        val wifi = drone(status = 1, kind = RadioKind.WIFI, mac = "02:11:25:4A:00:08")
        assertEquals(1, watch.pass(listOf(ble, wifi), enabled = true).fires.size)
        assertTrue(watch.pass(listOf(ble, wifi), enabled = true).fires.isEmpty())
    }

    @Test
    fun anEmptyLiveListDropsTheLatch() {
        val watch = RemoteIdEmergency()
        val drone = drone(status = OpenDroneId.STATUS_EMERGENCY)
        assertEquals(1, watch.pass(listOf(drone), enabled = true).fires.size)
        assertTrue(watch.pass(emptyList(), enabled = true).fires.isEmpty())
        assertEquals(1, watch.pass(listOf(drone), enabled = true).fires.size)
    }

    @Test
    fun aGoneRadioDoesNotCountAndAReturnFires() {
        val watch = RemoteIdEmergency()
        val live = drone(status = OpenDroneId.STATUS_EMERGENCY)
        assertEquals(1, watch.pass(listOf(live), enabled = true).fires.size)
        assertTrue(watch.pass(listOf(live.copy(gone = true)), enabled = true).fires.isEmpty())
        assertEquals(1, watch.pass(listOf(live), enabled = true).fires.size)
    }

    @Test
    fun aPausedScanDoesNotResetAndSwitchOffClearsTheQueue() {
        val watch = RemoteIdEmergency()
        val a = drone(uas = "ONE", status = OpenDroneId.STATUS_EMERGENCY, aircraft = "One")
        val b = drone(uas = "TWO", status = OpenDroneId.STATUS_EMERGENCY, aircraft = "Two", mac = "02:11:25:4A:00:09")
        watch.pass(listOf(a, b), enabled = true)
        assertEquals("Remote ID One declared an emergency.", watch.notice.value!!.body)
        assertTrue(watch.pass(null, enabled = true).fires.isEmpty())
        assertEquals("Remote ID One declared an emergency.", watch.notice.value!!.body)
        assertTrue(watch.pass(listOf(a, b), enabled = true).fires.isEmpty())
        watch.pass(null, enabled = false)
        assertNull(watch.notice.value)
        assertTrue(watch.pass(listOf(a, b), enabled = true).fires.isEmpty())
    }

    @Test
    fun aRadioThatIsNotRemoteIdDoesNotFire() {
        val watch = RemoteIdEmergency()
        val other = drone(status = OpenDroneId.STATUS_EMERGENCY, fleets = setOf("fleet-dji"))
        assertTrue(watch.pass(listOf(other), enabled = true).fires.isEmpty())
    }

    private fun drone(
        uas: String? = UAS,
        status: Int? = null,
        emergency: String? = null,
        aircraft: String? = null,
        selfId: String? = null,
        mac: String = "02:11:25:4A:00:01",
        kind: RadioKind = RadioKind.BLE,
        fleets: Set<String> = setOf(DroneNear.REMOTE_ID_FLEET),
    ): Sighting {
        val prefix = if (kind == RadioKind.WIFI) "WIFI" else "BLE"
        return Sighting(
            key = "$prefix:$mac",
            kind = kind,
            mac = mac,
            name = "Remote ID",
            rssi = -60,
            rssiMin = -60,
            rssiMax = -60,
            channel = 0,
            frequencyMhz = 2402,
            vendor = null,
            randomized = true,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 1_700_000_000_000L,
            lastSeen = 1_700_000_000_000L,
            hitCount = 1,
            fleetIds = fleets,
            rssiHistory = emptyList(),
            presence = emptyList(),
            payloadUasId = uas,
            payloadAircraft = aircraft,
            payloadSelfId = selfId,
            payloadStatus = status,
            payloadEmergencyText = emergency,
        )
    }

    private companion object {
        const val UAS = "1581F4XF0123ABCD"
    }
}
