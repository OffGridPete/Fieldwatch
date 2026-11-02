package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearnLeftTest {
    @Test
    fun windowsMatchTheLearningClocks() {
        assertEquals(LearnLeft.HOUR_MS, LearnLeft.trustedWindowMs(0))
        assertEquals(3 * LearnLeft.DAY_MS, LearnLeft.trustedWindowMs(3))
        assertEquals(LearnLeft.DAY_MS, LearnLeft.trustedWindowMs(-4))
        assertNull(LearnLeft.cellWindowMs(0))
        assertEquals(14 * LearnLeft.DAY_MS, LearnLeft.cellWindowMs(14))
    }

    @Test
    fun remainingEndsOnTheSameInstantTheWindowEnds() {
        val start = 5_000L
        val window = 3 * LearnLeft.DAY_MS
        assertEquals(1L, LearnLeft.remainingMs(start, window, start + window - 1))
        assertEquals(0L, LearnLeft.remainingMs(start, window, start + window))
        assertEquals(0L, LearnLeft.remainingMs(0L, window, start))
    }

    @Test
    fun labelUsesDaysThenHoursThenMinutes() {
        assertEquals("Learning has 3 days left", LearnLeft.label(3 * LearnLeft.DAY_MS))
        assertEquals("Learning has 47 hours left", LearnLeft.label(47 * LearnLeft.HOUR_MS))
        assertEquals("Learning has 1 hour left", LearnLeft.label(LearnLeft.HOUR_MS))
        assertEquals("Learning has 2 minutes left", LearnLeft.label(2 * 60_000L))
        assertEquals("Learning has 1 minute left", LearnLeft.label(60_000L))
        assertEquals("Learning has under a minute left", LearnLeft.label(59_000L))
        assertEquals("Learning has ended", LearnLeft.label(0L))
    }
}
