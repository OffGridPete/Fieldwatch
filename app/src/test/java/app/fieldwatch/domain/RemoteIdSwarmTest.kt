package app.fieldwatch.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteIdSwarmTest {
    private val serial = "1581F6Z3C24A00001001"

    @Test
    fun sixteenIdsInsideTwentySecondsAreOneWarning() {
        val swarm = swarm()
        var beeps = 0
        for (i in 0 until 16) {
            val out = pass(swarm, listOf(drone("U$i")), now = i * 1_000L)
            if (out.beep) beeps++
        }
        assertEquals(1, beeps)
        assertEquals(1, swarm.bursts().size)
        assertEquals(16, swarm.bursts().single().idCount)
        assertEquals(16, swarm.notice.value!!.count)
        assertTrue(swarm.notice.value!!.showDialog)
    }

    @Test
    fun spreadAndLoudnessDoNotMatter() {
        val swarm = swarm()
        val devices = (0 until 16).map { i ->
            drone(
                id = "S$i",
                rssi = -40 - (i * 50 / 15),
                opLat = 40.0 + i * 0.01,
                opLon = -74.0 + i * 0.01,
            )
        }
        var beeps = 0
        devices.forEachIndexed { i, device ->
            if (pass(swarm, listOf(device), now = i * 1_000L).beep) beeps++
        }
        assertEquals(1, beeps)
        assertEquals(16, swarm.bursts().single().idCount)
    }

    @Test
    fun fourDronesDoNotWarn() {
        val swarm = swarm()
        for (i in 0 until 4) pass(swarm, listOf(drone("F$i")), now = i * 4_000L)
        assertNull(swarm.notice.value)
        assertTrue(swarm.bursts().isEmpty())
    }

    @Test
    fun oneUasIdOnManyMacsIsOneDrone() {
        val swarm = swarm()
        val devices = (0 until 16).map { i ->
            drone("ONLY", mac = "02:11:25:4A:10:%02X".format(i))
        }
        val out = pass(swarm, devices, now = 1_000L)
        assertFalse(out.beep)
        assertTrue(swarm.bursts().isEmpty())
    }

    @Test
    fun fiveSpreadPastTheWindowDoNotWarn() {
        val swarm = swarm()
        pass(swarm, listOf(drone("A"), drone("B")), now = 0L)
        pass(swarm, listOf(drone("C"), drone("D")), now = 20_000L)
        val out = pass(swarm, listOf(drone("E")), now = 45_000L)
        assertFalse(out.beep)
        assertNull(swarm.notice.value)
        assertTrue(swarm.bursts().isEmpty())
    }

    @Test
    fun droneClassRowsGoQuietAndANamedRadioDoesNot() {
        val swarm = swarm()
        val first = pass(swarm, ids("Q", 5), now = 0L)
        assertTrue(first.beep)
        assertTrue(first.notify)
        assertTrue(first.quiet)
        val same = pass(swarm, ids("Q", 5), now = 1_000L)
        assertFalse(same.beep)
        assertFalse(same.notify)
        assertTrue(same.quiet)
        val more = pass(swarm, listOf(drone("Q5")), now = 2_000L)
        assertFalse(more.beep)
        assertTrue(more.notify)
        assertEquals(6, more.count)
        val drones = setOf("fleet-remote-id")
        assertTrue(swarmQuietsFleetWatch(null, "fleet-remote-id", drones))
        assertFalse(swarmQuietsFleetWatch("BLE:02:11:25:4A:00:01", "fleet-remote-id", drones))
        assertFalse(swarmQuietsFleetWatch(null, "fleet-home", drones))
        assertFalse(swarmQuietsFleetWatch(null, "fleet-remote-id", emptySet()))
        assertFalse(swarmQuietsFleetWatch(null, null, drones))
    }

    @Test
    fun sentencesLeaveTheUasIdInTheSitFileOnly() {
        val swarm = swarm()
        val devices = (1..4).map { drone("P$it") } + drone(serial)
        pass(swarm, devices, now = 1_000L)
        val notice = swarm.notice.value!!
        val burst = swarm.bursts().single()
        assertTrue(burst.uasIds.contains(serial))
        val shown = listOf(
            notice.title(),
            notice.body(),
            notice.details(),
            burst.reportLine(),
            swarm.memberLine(serial)!!,
        )
        for (line in shown) assertFalse(line, line.contains(serial))
        assertTrue(notice.body().contains("5"))
        assertTrue(burst.reportLine().contains("5"))
        assertFalse(notice.body().contains("rarely"))
        assertFalse(notice.details().contains("·"))
    }

    @Test
    fun warningNamesMakersAndPutsEachCountOnItsOwnLine() {
        val swarm = swarm()
        val devices = listOf(
            drone("D1", payloadAircraft = "DJI"),
            drone("D2", payloadAircraft = "DJI"),
            drone("D3", payloadAircraft = "DJI"),
            drone("D4", payloadAircraft = "DJI"),
            drone("D5", payloadAircraft = "DJI"),
            drone("A1", payloadAircraft = "Autel"),
            drone("A2", payloadAircraft = "Autel"),
            drone("S1", fleet = "fleet-skydio", kind = RadioKind.WIFI),
        )
        pass(
            swarm,
            devices,
            now = 1_000L,
            windowMs = 19_000L,
            fleets = setOf("fleet-remote-id", "fleet-skydio"),
            typeNames = mapOf("fleet-skydio" to "Skydio", "fleet-dji" to "DJI"),
        )
        val notice = swarm.notice.value!!
        assertEquals("Drone swarm detected", notice.title())
        val body = notice.body()
        assertTrue(body.contains("8 new drones in 19 seconds."))
        assertTrue(body.contains("5 DJI"))
        assertTrue(body.contains("2 Autel"))
        assertTrue(body.contains("1 Skydio"))
        assertFalse(body.contains("Remote ID"))
        assertFalse(body.contains("D1"))
        val rows = NoticeText.rows(notice.details())
        assertEquals(listOf("New drones", "Time", "Bluetooth", "Wi-Fi"), rows.map { it.label })
        assertEquals(listOf("8", "19 seconds", "7", "1"), rows.map { it.value })
    }

    @Test
    fun aPlateMakerReplacesASignatureNameAndRemoteIdAloneIsNotAType() {
        val swarm = swarm()
        val named = drone(
            "P1",
            fleet = "fleet-dji",
            payloadAircraft = "Freefly Alta X",
            fleetIds = setOf("fleet-remote-id", "fleet-dji"),
        )
        pass(
            swarm,
            listOf(named) + ids("Q", 4),
            now = 1_000L,
            fleets = setOf("fleet-remote-id", "fleet-dji"),
            typeNames = mapOf("fleet-dji" to "DJI"),
        )
        val body = swarm.notice.value!!.body()
        assertTrue(body.contains("1 Freefly Alta X"))
        assertTrue(body.contains("4 No maker named"))
        assertFalse(body.contains("DJI"))
        assertFalse(body.contains("Remote ID"))
        val later = drone("P1", payloadAircraft = "DJI", fleet = "fleet-dji")
        pass(
            swarm,
            listOf(later),
            now = 2_000L,
            fleets = setOf("fleet-remote-id", "fleet-dji"),
            typeNames = mapOf("fleet-dji" to "DJI"),
        )
        val updated = swarm.notice.value!!.body()
        assertTrue(updated.contains("1 DJI"))
        assertFalse(updated.contains("Freefly"))
    }

    @Test
    fun quietThenANewGroupCanWarnAndOldIdsStaySpent() {
        val swarm = swarm()
        val first = ids("OLD", 5)
        pass(swarm, first, now = 0L)
        val ended = pass(swarm, emptyList(), now = 120_000L)
        assertTrue(ended.clearNote)
        assertNull(swarm.notice.value)
        assertFalse(swarm.quietsDroneClass())
        assertEquals(0L, swarm.memberAt("OLD0"))
        assertNotNull(swarm.memberLine("OLD0"))
        val again = pass(swarm, first, now = 130_000L)
        assertFalse(again.beep)
        assertEquals(1, swarm.bursts().size)
        val next = pass(swarm, ids("NEW", 5), now = 130_000L)
        assertTrue(next.beep)
        assertEquals(2, swarm.bursts().size)
    }

    @Test
    fun switchOffEndsTheEpisodeAndKeepsSpentIds() {
        val swarm = swarm()
        pass(swarm, ids("ON", 5), now = 0L)
        val off = pass(swarm, ids("ON", 5), now = 1_000L, enabled = false)
        assertTrue(off.clearNote)
        assertNull(swarm.notice.value)
        assertFalse(swarm.quietsDroneClass())
        val same = pass(swarm, ids("ON", 5), now = 2_000L, enabled = true)
        assertFalse(same.beep)
        assertNull(swarm.notice.value)
        val fresh = pass(swarm, ids("NEXT", 5), now = 2_000L, enabled = true)
        assertTrue(fresh.beep)
        assertTrue(swarm.quietsDroneClass())
    }

    @Test
    fun missingJsonLeavesTheSwitchOff() {
        val json = Json { ignoreUnknownKeys = true }
        val fresh = json.decodeFromString<AppSettings>("{}")
        val upgraded = json.decodeFromString<AppSettings>("""{"alertsEnabled":true}""")
        assertFalse(fresh.remoteIdSwarm)
        assertFalse(upgraded.remoteIdSwarm)
        assertEquals(5, fresh.swarmMinIds)
        assertEquals(30, fresh.swarmWindowSec)
        assertEquals(2, fresh.swarmQuietMin)
        assertTrue(fresh.swarmBeep)
        assertTrue(fresh.swarmVoice)
        assertFalse(fresh.swarmShade)
    }

    @Test
    fun tenNeededWarnsAtSixteenAndNotAtEight() {
        assertFalse(fired(8, minIds = 10))
        assertTrue(fired(16, minIds = 10))
    }

    @Test
    fun resetNumbersMatchAFreshInstall() {
        val fresh = AppSettings()
        assertEquals(5, RemoteIdSwarm.DEFAULT_MIN_IDS)
        assertEquals(30, RemoteIdSwarm.DEFAULT_WINDOW_SEC)
        assertEquals(2, RemoteIdSwarm.DEFAULT_QUIET_MIN)
        assertEquals(RemoteIdSwarm.DEFAULT_MIN_IDS, fresh.swarmMinIds)
        assertEquals(RemoteIdSwarm.DEFAULT_WINDOW_SEC, fresh.swarmWindowSec)
        assertEquals(RemoteIdSwarm.DEFAULT_QUIET_MIN, fresh.swarmQuietMin)
        assertFalse(fresh.remoteIdSwarm)
    }

    @Test
    fun outOfRangeTriggerValuesClampAndTheSwitchStays() {
        val low = RemoteIdSwarm.clamp(
            AppSettings(remoteIdSwarm = true, swarmMinIds = 1, swarmWindowSec = 5, swarmQuietMin = 0),
        )
        assertTrue(low.remoteIdSwarm)
        assertEquals(3, low.swarmMinIds)
        assertEquals(10, low.swarmWindowSec)
        assertEquals(1, low.swarmQuietMin)
        val high = RemoteIdSwarm.clamp(
            AppSettings(remoteIdSwarm = true, swarmMinIds = 50, swarmWindowSec = 200, swarmQuietMin = 99),
        )
        assertTrue(high.remoteIdSwarm)
        assertEquals(20, high.swarmMinIds)
        assertEquals(120, high.swarmWindowSec)
        assertEquals(10, high.swarmQuietMin)
        val step = AppSettings(remoteIdSwarm = true, swarmMinIds = 7, swarmWindowSec = 12, swarmQuietMin = 4)
        assertSame(step, RemoteIdSwarm.clamp(step))
    }

    @Test
    fun blankUasIdWaitsUntilAnIdSticks() {
        val swarm = swarm()
        val blank = (0 until 5).map { drone("B$it", uas = null, firstSeen = 0L) }
        pass(swarm, blank, now = 0L)
        pass(swarm, blank.map { it.copy(payloadUasId = "  ") }, now = 50_000L)
        assertNull(swarm.notice.value)
        val stuck = blank.mapIndexed { i, row -> row.copy(payloadUasId = "LATE$i") }
        val out = pass(swarm, stuck, now = 100_000L, windowMs = 30_000L)
        assertTrue(out.beep)
        assertEquals(5, swarm.bursts().single().idCount)
    }

    @Test
    fun dismissHidesTheDialogAndLeavesTheQuietOn() {
        val swarm = swarm()
        pass(swarm, ids("D", 5), now = 0L)
        assertTrue(swarm.notice.value!!.showDialog)
        swarm.dismiss()
        assertFalse(swarm.notice.value!!.showDialog)
        assertTrue(swarm.quietsDroneClass())
        assertEquals(1, swarm.bursts().size)
    }

    @Test
    fun mintingIdsStayOneEpisodeUntilAFullQuietGap() {
        val swarm = swarm()
        var beeps = 0
        if (pass(swarm, ids("M", 5), now = 0L).beep) beeps++
        for (i in 1..5) {
            val out = pass(swarm, listOf(drone("MORE$i")), now = i * 60_000L)
            if (out.beep) beeps++
            assertTrue(out.quiet)
        }
        assertEquals(1, beeps)
        assertEquals(1, swarm.bursts().size)
        assertEquals(10, swarm.bursts().single().idCount)
        val ended = pass(swarm, emptyList(), now = 300_000L + 120_000L)
        assertTrue(ended.clearNote)
        assertNull(swarm.notice.value)
        val again = pass(swarm, ids("GAP", 5), now = 420_000L)
        assertTrue(again.beep)
        assertEquals(2, swarm.bursts().size)
    }

    @Test
    fun bleAndWifiWithTheSameIdCountOnce() {
        val swarm = swarm()
        val devices = (1..5).map { drone("BOTH$it") } +
            drone("BOTH1", mac = "02:11:25:4A:00:99", kind = RadioKind.WIFI)
        val out = pass(swarm, devices, now = 1_000L)
        assertTrue(out.beep)
        assertEquals(5, out.count)
        assertEquals(5, out.ble)
        assertEquals(1, out.wifi)
        assertEquals(5, swarm.bursts().single().ble)
        assertEquals(1, swarm.bursts().single().wifi)
    }

    @Test
    fun customDroneFleetCountsAndAnotherClassDoesNot() {
        val custom = swarm()
        val heard = (0 until 5).map { drone("C$it", fleet = "custom-drone") }
        val out = pass(custom, heard, now = 0L, fleets = setOf("custom-drone"))
        assertTrue(out.beep)
        val other = swarm()
        val home = (0 until 8).map { drone("H$it", fleet = "fleet-home") }
        val skipped = pass(other, home, now = 0L, fleets = setOf("fleet-remote-id"))
        assertFalse(skipped.beep)
        assertTrue(other.bursts().isEmpty())
    }

    @Test
    fun sessionStartDropsIdsAlreadyOnTheAir() {
        val known = (1..5).map { "KNOWN$it" }
        val swarm = swarm()
        swarm.beginSession(known)
        assertFalse(pass(swarm, known.map { drone(it) }, now = 0L).beep)
        val fresh = swarm()
        fresh.beginSession(emptyList())
        val out = pass(fresh, ids("NOW", 5), now = 5_000L)
        assertTrue(out.beep)
        assertEquals(1, fresh.bursts().size)
    }

    @Test
    fun savedBurstExistsBeforeASitCopiesIt() {
        val swarm = swarm()
        pass(swarm, ids("B", 5), now = 8_000L)
        val burst = swarm.bursts().single()
        assertEquals(8_000L, burst.at)
        assertEquals(5, burst.idCount)
        assertEquals(5, burst.uasIds.size)
    }

    @Test
    fun triggerChangesDoNotRestartTheRunningEpisode() {
        val swarm = swarm()
        assertTrue(pass(swarm, ids("E", 5), now = 0L).beep)
        val changed = pass(
            swarm,
            emptyList(),
            now = 10_000L,
            minIds = 20,
            windowMs = 10_000L,
            quietMs = 60_000L,
        )
        assertFalse(changed.beep)
        assertTrue(changed.quiet)
        assertTrue(swarm.notice.value!!.showDialog)
        assertEquals(30_000L, swarm.bursts().single().windowMs)
        assertEquals(60_000L, swarm.bursts().single().quietMs)
        val joined = pass(
            swarm,
            listOf(drone("LATE")),
            now = 40_000L,
            minIds = 20,
            windowMs = 10_000L,
            quietMs = 60_000L,
        )
        assertFalse(joined.beep)
        assertTrue(joined.notify)
        assertEquals(6, joined.count)
        assertEquals(1, swarm.bursts().size)
        assertEquals(30_000L, swarm.bursts().single().windowMs)
        assertTrue(swarm.bursts().single().reportLine().contains("30"))
        val still = pass(swarm, emptyList(), now = 99_000L, quietMs = 60_000L)
        assertTrue(still.quiet)
        val done = pass(swarm, emptyList(), now = 100_000L, quietMs = 60_000L)
        assertTrue(done.clearNote)
        assertNull(swarm.notice.value)
    }

    private fun swarm(): RemoteIdSwarm = RemoteIdSwarm().also { it.beginSession(emptyList()) }

    private fun fired(count: Int, minIds: Int): Boolean {
        val swarm = swarm()
        return pass(swarm, ids("N", count), now = 1_000L, minIds = minIds).beep
    }

    private fun ids(prefix: String, count: Int): List<Sighting> =
        (0 until count).map { drone("$prefix$it") }

    private fun pass(
        swarm: RemoteIdSwarm,
        devices: List<Sighting>,
        now: Long,
        enabled: Boolean = true,
        minIds: Int = 5,
        windowMs: Long = 30_000L,
        quietMs: Long = 120_000L,
        fleets: Set<String> = setOf("fleet-remote-id"),
        typeNames: Map<String, String> = emptyMap(),
    ) = swarm.pass(devices, fleets, now, enabled, minIds, windowMs, quietMs, typeNames)

    private fun drone(
        id: String,
        mac: String = "02:11:25:4A:00:%02X".format(id.hashCode() and 0xFF),
        kind: RadioKind = RadioKind.BLE,
        fleet: String = "fleet-remote-id",
        rssi: Int = -60,
        uas: String? = id,
        firstSeen: Long = 0L,
        opLat: Double? = null,
        opLon: Double? = null,
        payloadAircraft: String? = null,
        fleetIds: Set<String>? = null,
    ): Sighting {
        val prefix = if (kind == RadioKind.WIFI) "WIFI" else "BLE"
        return Sighting(
            key = "$prefix:$mac",
            kind = kind,
            mac = mac,
            name = "Remote ID",
            rssi = rssi,
            rssiMin = rssi,
            rssiMax = rssi,
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
            firstSeen = firstSeen,
            lastSeen = firstSeen,
            hitCount = 1,
            fleetIds = fleetIds ?: setOf(fleet),
            rssiHistory = emptyList(),
            presence = emptyList(),
            payloadOpLat = opLat,
            payloadOpLon = opLon,
            payloadUasId = uas,
            payloadAircraft = payloadAircraft,
        )
    }
}
