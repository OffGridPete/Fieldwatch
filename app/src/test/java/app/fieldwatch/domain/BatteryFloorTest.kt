package app.fieldwatch.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryFloorTest {
    private val high = AppSettings(
        batteryFloor = true,
        batterySlowPct = 20,
        batteryStopPct = 5,
        intensity = ScanIntensity.PERFORMANCE,
    )

    @Test
    fun missingJsonStaysOff() {
        val settings = Json.decodeFromString<AppSettings>("{}")
        assertEquals(false, settings.batteryFloor)
        assertEquals(20, settings.batterySlowPct)
        assertEquals(5, settings.batteryStopPct)
        val result = BatteryFloor.evaluate(settings, BatterySample(4, plugged = false), slowed = false)
        assertEquals(BatteryFloorStep.NONE, result.step)
        assertEquals(ScanIntensity.PERFORMANCE, result.effective)
    }

    @Test
    fun slowDownLeavesTheSavedIntensity() {
        val at = BatteryFloor.evaluate(high, BatterySample(20, plugged = false), slowed = false)
        assertEquals(BatteryFloorStep.SLOW, at.step)
        assertEquals(ScanIntensity.SAVER, at.effective)
        assertEquals(ScanIntensity.PERFORMANCE, high.intensity)
    }

    @Test
    fun hysteresisHoldsThrough23AndLiftsAt24() {
        val held = BatteryFloor.evaluate(high, BatterySample(23, plugged = false), slowed = true)
        assertEquals(BatteryFloorStep.SLOW, held.step)
        assertNull(held.restored)
        val lifted = BatteryFloor.evaluate(high, BatterySample(24, plugged = false), slowed = true)
        assertEquals(BatteryFloorStep.NONE, lifted.step)
        assertEquals(ScanIntensity.PERFORMANCE, lifted.effective)
        assertEquals(BatteryRestore.LEVEL, lifted.restored)
    }

    @Test
    fun chargingLiftsTheSlowDownImmediately() {
        val lifted = BatteryFloor.evaluate(high, BatterySample(12, plugged = true), slowed = true)
        assertEquals(BatteryFloorStep.NONE, lifted.step)
        assertEquals(BatteryRestore.CHARGING, lifted.restored)
        val idle = BatteryFloor.evaluate(high, BatterySample(2, plugged = true), slowed = false)
        assertEquals(BatteryFloorStep.NONE, idle.step)
        assertNull(idle.restored)
    }

    @Test
    fun stopBeatsTheSlowDown() {
        val stopped = BatteryFloor.evaluate(high, BatterySample(5, plugged = false), slowed = true)
        assertEquals(BatteryFloorStep.STOPPED, stopped.step)
        val tied = high.copy(batterySlowPct = 10, batteryStopPct = 10)
        val at10 = BatteryFloor.evaluate(tied, BatterySample(10, plugged = false), slowed = false)
        assertEquals(BatteryFloorStep.STOPPED, at10.step)
        val above = BatteryFloor.evaluate(tied, BatterySample(12, plugged = false), slowed = true)
        assertEquals(BatteryFloorStep.NONE, above.step)
        assertNull(above.restored)
    }

    @Test
    fun unknownLevelTakesNoStep() {
        val fresh = BatteryFloor.evaluate(high, BatterySample(-1, plugged = false), slowed = false)
        assertEquals(BatteryFloorStep.NONE, fresh.step)
        val held = BatteryFloor.evaluate(high, null, slowed = true)
        assertEquals(BatteryFloorStep.SLOW, held.step)
        assertNull(held.restored)
    }

    @Test
    fun switchOffDropsTheLatchWithoutARestore() {
        val off = high.copy(batteryFloor = false)
        val result = BatteryFloor.evaluate(off, BatterySample(4, plugged = false), slowed = true)
        assertEquals(BatteryFloorStep.NONE, result.step)
        assertEquals(ScanIntensity.PERFORMANCE, result.effective)
        assertNull(result.restored)
    }
}
