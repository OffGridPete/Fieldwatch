package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntTest {
    private val now = 100_000L

    @Test
    fun veryCloseBeatsCloserWhenScreamingLoud() {
        val samples = listOf(
            RssiSample(now - 6_000, -70),
            RssiSample(now - 5_000, -68),
            RssiSample(now - 1_200, -38),
            RssiSample(now - 400, -36),
        )
        assertEquals(HuntCue.VERY_CLOSE, Hunt.cue(samples, now, now - 400, missing = false))
    }

    @Test
    fun belowVeryCloseStillUsesRelativeCue() {
        val samples = listOf(
            RssiSample(now - 6_000, -72),
            RssiSample(now - 5_000, -70),
            RssiSample(now - 1_200, -60),
            RssiSample(now - 400, -58),
        )
        assertEquals(HuntCue.CLOSER, Hunt.cue(samples, now, now - 400, missing = false))
    }

    @Test
    fun quietWinsOverALoudLastPacket() {
        val samples = listOf(RssiSample(now - 9_000, -30))
        assertEquals(HuntCue.QUIET, Hunt.cue(samples, now, now - 9_000, missing = false))
    }

    @Test
    fun goneWins() {
        assertEquals(HuntCue.GONE, Hunt.cue(emptyList(), now, now, missing = true))
    }

    @Test
    fun tickSilentWhenQuietGoneOrNoRssi() {
        assertEquals(null, Hunt.tickIntervalMs(-40, HuntCue.QUIET))
        assertEquals(null, Hunt.tickIntervalMs(-40, HuntCue.GONE))
        assertEquals(null, Hunt.tickIntervalMs(null, HuntCue.CLOSER))
    }

    @Test
    fun tickFasterWhenLouder() {
        val slow = Hunt.tickIntervalMs(-90, HuntCue.SAME)!!
        val mid = Hunt.tickIntervalMs(-65, HuntCue.CLOSER)!!
        val fast = Hunt.tickIntervalMs(-40, HuntCue.VERY_CLOSE)!!
        assertEquals(Hunt.TICK_SLOW_MS, slow)
        assertEquals(Hunt.TICK_FAST_MS, fast)
        assertTrue(mid in (fast + 1) until slow)
        val veryClose = Hunt.tickIntervalMs(-45, HuntCue.VERY_CLOSE)!!
        assertTrue(veryClose < mid)
        assertTrue(veryClose > fast)
    }
}
