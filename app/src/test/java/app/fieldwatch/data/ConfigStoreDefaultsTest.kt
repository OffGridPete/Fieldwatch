package app.fieldwatch.data

import app.fieldwatch.domain.AppLanguage
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.PersistedConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigStoreDefaultsTest {
    @Test
    fun restoringDefaultsRetainsLanguageAndDisclaimerButResetsOtherSettings() {
        val current = PersistedConfig(
            settings = AppSettings(
                language = AppLanguage.SPANISH,
                disclaimerAccepted = true,
                disclaimerRev = 3,
                nightMode = true,
                keepScreenOn = false,
            ),
        )
        val defaults = PersistedConfig(
            settings = AppSettings(
                disclaimerAccepted = false,
                disclaimerRev = 0,
                nightMode = false,
                keepScreenOn = true,
            ),
        )

        val restored = restoreConfigDefaults(current, defaults)

        assertEquals(AppLanguage.SPANISH, restored.settings.language)
        assertTrue(restored.settings.disclaimerAccepted)
        assertEquals(3, restored.settings.disclaimerRev)
        assertFalse(restored.settings.nightMode)
        assertTrue(restored.settings.keepScreenOn)
    }
}
