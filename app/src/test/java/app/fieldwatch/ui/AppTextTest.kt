package app.fieldwatch.ui

import app.fieldwatch.domain.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTextTest {
    @Test
    fun englishRemainsUnchangedAndSpanishTranslatesUiAndDynamicLabels() {
        assertEquals("Settings", translateAppText("Settings", AppLanguage.ENGLISH))
        assertEquals("Ajustes", translateAppText("Settings", AppLanguage.SPANISH))
        assertEquals("Idioma", translateAppText("Language", AppLanguage.SPANISH))
        assertEquals("Rotar en 2048 KB", translateAppText("Rotate at 2048 KB", AppLanguage.SPANISH))
        assertEquals("Radios con nombre (12)", translateAppText("Named radios (12)", AppLanguage.SPANISH))
    }
}
