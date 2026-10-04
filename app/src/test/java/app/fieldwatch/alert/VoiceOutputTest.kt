package app.fieldwatch.alert

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class VoiceOutputTest {
    private class FakeVoice(val support: Int, val accepted: Boolean = true) : VoiceOutput {
        var requestedLocale: Locale? = null
        val phrases = mutableListOf<String>()
        override fun setLanguage(locale: Locale): Int { requestedLocale = locale; return support }
        override fun speak(text: String, utteranceId: String): Boolean { phrases += text; return accepted }
    }

    @Test fun missingAndUnsupportedChineseVoiceNeverSpeaksInFallbackLanguage() {
        for (status in listOf(-1, -2)) {
            val engine = FakeVoice(status)
            assertEquals(VoiceOutcome.UNAVAILABLE, deliverVoice(engine, Locale.SIMPLIFIED_CHINESE, "定位标签", "qa"))
            assertEquals(Locale.SIMPLIFIED_CHINESE, engine.requestedLocale)
            assertTrue(engine.phrases.isEmpty())
        }
    }

    @Test fun supportedVoiceAndSpeakFailureAreDistinguished() {
        val supported = FakeVoice(2)
        assertEquals(VoiceOutcome.ACCEPTED, deliverVoice(supported, Locale.SIMPLIFIED_CHINESE, "定位标签", "qa"))
        assertEquals(listOf("定位标签"), supported.phrases)
        assertEquals(VoiceOutcome.FAILED, deliverVoice(FakeVoice(0, false), Locale.US, "Finder tags", "qa"))
        val throwing = object : VoiceOutput {
            override fun setLanguage(locale: Locale): Int = error("unavailable engine")
            override fun speak(text: String, utteranceId: String): Boolean = error("must not be called")
        }
        assertEquals(VoiceOutcome.UNAVAILABLE, deliverVoice(throwing, Locale.US, "Finder tags", "qa"))
    }

    @Test fun changingLanguageInvalidatesDelayedSpeechButAllowsNewRequests() {
        val epoch = VoiceEpoch()
        val old = epoch.ticket()
        val spoken = mutableListOf<String>()
        val oldRetry = { if (epoch.isCurrent(old)) spoken += "Finder tags" }
        epoch.advance()
        val current = epoch.ticket()
        oldRetry()
        if (epoch.isCurrent(current)) spoken += "定位标签"
        assertEquals(listOf("定位标签"), spoken)
    }
}
