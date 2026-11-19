package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DroneNearTest {
    private val names = mapOf("fleet-remote-id" to "Remote ID", "fleet-dji" to "DJI")
    private val phoneLat = 40.0
    private val phoneLon = -74.0

    @Test
    fun offDoesNothingAndABadDistanceClampsOff() {
        val near = DroneNear()
        val drone = drone("A", meters = 100.0)
        val phone = phone(10f)
        val outcome = near.pass(listOf(drone), names, emptySet(), NOW, 0, phone)
        assertTrue(outcome.fires.isEmpty())
        assertFalse(near.holdsBookmark(drone, emptySet(), NOW, 0))
        val bad = DroneNear.clamp(AppSettings(droneNearM = 80))
        assertEquals(0, bad.droneNearM)
        assertEquals(250, bad.droneNearPick)
        val kept = DroneNear.clamp(AppSettings(droneNearM = 250))
        assertEquals(250, kept.droneNearM)
        assertEquals(250, kept.droneNearPick)
        val legacy = DroneNear.clamp(AppSettings(droneNearM = 100))
        assertEquals(100, legacy.droneNearM)
        assertEquals(100, legacy.droneNearPick)
        val fresh = AppSettings()
        assertEquals(0, fresh.droneNearM)
        assertEquals(250, fresh.droneNearPick)
        val on = DroneNear.armed(fresh, true)
        assertEquals(250, on.droneNearM)
        val farther = DroneNear.pick(on, 1000)
        val off = DroneNear.armed(farther, false)
        assertEquals(0, off.droneNearM)
        assertEquals(1000, off.droneNearPick)
        assertEquals(1000, DroneNear.armed(off, true).droneNearM)
    }

    @Test
    fun firstFreshPositionInsideFiresIncludingADroneAlreadyInside() {
        val near = DroneNear()
        val drone = drone("A", meters = 200.0, fleets = setOf("fleet-remote-id", "fleet-dji"))
        val outcome = near.pass(listOf(drone), names, emptySet(), NOW, 250, phone(10f))
        assertEquals(1, outcome.fires.size)
        assertEquals("Broadcast within 250 m", outcome.fires[0].title)
        assertEquals("DJI · 200 m away", outcome.fires[0].body)
        assertEquals("Within 250 m", near.chips.value["A"])
        assertTrue(near.holdsBookmark(drone, emptySet(), NOW, 250))
        val again = near.pass(listOf(drone), names, emptySet(), NOW + 1_000, 250, phone(10f))
        assertTrue(again.fires.isEmpty())
    }

    @Test
    fun outsideThenInsideFiresOnceAndRearmsPastOneAndAHalf() {
        val near = DroneNear()
        val far = drone("A", meters = 400.0)
        val first = near.pass(listOf(far), names, emptySet(), NOW, 250, phone(10f))
        assertTrue(first.fires.isEmpty())
        assertFalse(near.holdsBookmark(far, emptySet(), NOW, 250))
        val inside = drone("A", meters = 180.0)
        val second = near.pass(listOf(inside), names, emptySet(), NOW + 1_000, 250, phone(10f))
        assertEquals(1, second.fires.size)
        val drift = drone("A", meters = 300.0, at = NOW + 2_000)
        val held = near.pass(listOf(drift), names, emptySet(), NOW + 2_000, 250, phone(10f))
        assertTrue(held.fires.isEmpty())
        assertNull(near.chips.value["A"])
        val left = drone("A", meters = 400.0, at = NOW + 3_000)
        val rearm = near.pass(listOf(left), names, emptySet(), NOW + 3_000, 250, phone(10f))
        assertTrue(rearm.fires.isEmpty())
        assertTrue(rearm.marks.any { it.closest })
        val back = drone("A", meters = 100.0, at = NOW + 4_000)
        val third = near.pass(listOf(back), names, emptySet(), NOW + 4_000, 250, phone(10f))
        assertEquals(1, third.fires.size)
    }

    @Test
    fun aStalePositionAndAWideFixChangeNothing() {
        val near = DroneNear()
        val stale = drone("A", meters = 50.0, at = NOW - 11_000)
        val waiting = near.pass(listOf(stale), names, emptySet(), NOW, 250, phone(10f))
        assertTrue(waiting.fires.isEmpty())
        assertTrue(near.holdsBookmark(stale, emptySet(), NOW, 250))
        val wide = near.pass(listOf(drone("A", meters = 50.0)), names, emptySet(), NOW, 250, phone(60f))
        assertTrue(wide.fires.isEmpty())
        assertTrue(near.chips.value.isEmpty())
        val aged = near.pass(
            listOf(drone("A", meters = 50.0)),
            names,
            emptySet(),
            NOW,
            250,
            phone(10f, at = NOW - 31_000),
        )
        assertTrue(aged.fires.isEmpty())
    }

    @Test
    fun blankIdInvalidCoordAndMineStayOut() {
        val near = DroneNear()
        val blank = drone("A", meters = 20.0, uas = "  ")
        val zero = drone("B", meters = 20.0).copy(payloadLat = 0.0, payloadLon = 0.0)
        val mine = drone("C", meters = 20.0, mac = "02:11:25:4A:00:0C")
        val outcome = near.pass(
            listOf(blank, zero, mine),
            names,
            setOf(mine.key),
            NOW,
            250,
            phone(10f),
        )
        assertTrue(outcome.fires.isEmpty())
        assertFalse(near.holdsBookmark(mine, setOf(mine.key), NOW, 250))
    }

    @Test
    fun theSameUasOnANewAddressDoesNotFireAgainUntilEverySightingIsGone() {
        val near = DroneNear()
        val ble = drone("A", meters = 80.0, mac = "02:11:25:4A:00:01")
        assertEquals(1, near.pass(listOf(ble), names, emptySet(), NOW, 250, phone(10f)).fires.size)
        val wifi = drone("A", meters = 90.0, mac = "02:11:25:4A:00:02", kind = RadioKind.WIFI, at = NOW + 500)
        val both = near.pass(listOf(ble, wifi), names, emptySet(), NOW + 500, 250, phone(10f))
        assertTrue(both.fires.isEmpty())
        val onlyWifi = near.pass(
            listOf(ble.copy(gone = true), wifi),
            names,
            emptySet(),
            NOW + 1_000,
            250,
            phone(10f),
        )
        assertTrue(onlyWifi.fires.isEmpty())
        assertTrue(onlyWifi.marks.none { it.closest })
        val gone = near.pass(
            listOf(ble.copy(gone = true), wifi.copy(gone = true)),
            names,
            emptySet(),
            NOW + 2_000,
            250,
            phone(10f),
        )
        assertTrue(gone.marks.any { it.closest })
        val returned = drone("A", meters = 70.0, mac = "02:11:25:4A:00:03", at = NOW + 3_000)
        assertEquals(1, near.pass(listOf(returned), names, emptySet(), NOW + 3_000, 250, phone(10f)).fires.size)
    }

    @Test
    fun holdExpiresAtTenSecondsAndASitEndWritesTheClosestLine() {
        val near = DroneNear()
        val quiet = drone("A", meters = 20.0, at = null, firstSeen = NOW - 10_000)
        assertFalse(near.holdsBookmark(quiet, emptySet(), NOW, 250))
        val fresh = drone("A", meters = 40.0, firstSeen = NOW)
        near.pass(listOf(fresh), names, emptySet(), NOW, 250, phone(50f))
        val closing = near.closeOpen(NOW + 5_000)
        assertEquals(1, closing.size)
        assertTrue(closing[0].closest)
        assertTrue(closing[0].reportLine().contains("closest broadcast"))
        assertTrue(near.closeOpen(NOW + 6_000).isEmpty())
    }

    @Test
    fun diagnosticsNameTheWaitingFixAndTheWideFix() {
        assertNull(DroneNear.diag(0, phone(10f), NOW))
        assertEquals(
            "Drone distance alert is waiting for a GPS fix.",
            DroneNear.diag(250, null, NOW),
        )
        assertEquals(
            "Drone distance alert is waiting for a GPS fix.",
            DroneNear.diag(250, phone(10f, at = NOW - 31_000), NOW),
        )
        assertEquals(
            "Drone distance alert is paused. GPS fix is 60 m; it needs 50 m or better.",
            DroneNear.diag(250, phone(60f), NOW),
        )
        assertNull(DroneNear.diag(250, phone(50f), NOW))
    }

    private fun phone(accuracy: Float, at: Long = NOW) = GpsAccFix(phoneLat, phoneLon, accuracy, at)

    private fun drone(
        id: String,
        meters: Double,
        mac: String = "02:11:25:4A:00:01",
        kind: RadioKind = RadioKind.BLE,
        uas: String? = id,
        at: Long? = NOW,
        firstSeen: Long = NOW,
        fleets: Set<String> = setOf("fleet-remote-id"),
    ): Sighting {
        // A pure degree round trip lands a hair under the asked meter, and distanceText truncates.
        val lat = phoneLat + Math.toDegrees((meters + 0.05) / 6_371_000.0)
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
            firstSeen = firstSeen,
            lastSeen = firstSeen,
            hitCount = 1,
            fleetIds = fleets,
            rssiHistory = emptyList(),
            presence = emptyList(),
            payloadLat = lat,
            payloadLon = phoneLon,
            payloadUasId = uas,
            payloadAt = at,
        )
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
