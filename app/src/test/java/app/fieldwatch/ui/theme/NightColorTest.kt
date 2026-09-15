package app.fieldwatch.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.fieldwatch.domain.AppSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightColorTest {
    @Test
    fun nightModeDefaultsOff() {
        assertFalse(AppSettings().nightMode)
    }

    @Test
    fun oldSettingsJsonWithoutNightModeStaysOff() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(AppSettings.serializer(), "{}")
        assertFalse(decoded.nightMode)
    }

    @Test
    fun phosphorConstantsUnchanged() {
        assertEquals(0xFF3DFF9A.toInt(), Phosphor.toArgb())
        assertEquals(0xFF35D683.toInt(), PhosphorActive.toArgb())
        assertEquals(0xFFFFB020.toInt(), Amber.toArgb())
    }

    @Test
    fun nightIfFalseIsIdentity() {
        assertEquals(Phosphor, Phosphor.nightIf(false))
        assertEquals(PhosphorActive, PhosphorActive.nightIf(false))
        assertEquals(Amber, Amber.nightIf(false))
        val chip = Color(0xFF5BA3D9)
        assertEquals(chip, chip.nightIf(false))
        assertEquals(0xFF3DFF9A.toInt(), Color(0xFF3DFF9A).nightIf(false).toArgb())
    }

    @Test
    fun phosphorGreenBecomesRedDominant() {
        val out = nightForegroundArgb(0xFF3DFF9A.toInt())
        val r = (out shr 16) and 0xFF
        val g = (out shr 8) and 0xFF
        val b = out and 0xFF
        assertTrue(r > g)
        assertTrue(r > b)
    }

    @Test
    fun alreadyRedStaysRedDominant() {
        val out = nightForegroundArgb(0xFFFF3D5A.toInt())
        val r = (out shr 16) and 0xFF
        val g = (out shr 8) and 0xFF
        assertTrue(r > g)
    }

    @Test
    fun alphaPreserved() {
        val out = nightForegroundArgb(0x803DFF9A.toInt())
        assertEquals(0x80, (out ushr 24) and 0xFF)
    }
}
