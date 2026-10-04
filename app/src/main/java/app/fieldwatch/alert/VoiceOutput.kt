package app.fieldwatch.alert

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal interface VoiceOutput {
    fun setLanguage(locale: Locale): Int
    fun speak(text: String, utteranceId: String): Boolean
}

internal enum class VoiceOutcome { ACCEPTED, UNAVAILABLE, FAILED }

/** Negative Android TTS language results require fallback; never queue text in another language. */
internal fun deliverVoice(output: VoiceOutput, locale: Locale, text: String, id: String): VoiceOutcome {
    val language = runCatching { output.setLanguage(locale) }.getOrDefault(-2)
    if (language < 0) return VoiceOutcome.UNAVAILABLE
    return if (runCatching { output.speak(text, id) }.getOrDefault(false)) VoiceOutcome.ACCEPTED else VoiceOutcome.FAILED
}

/** A queued callback may outlive the Activity which changed the app language. */
internal class VoiceEpoch {
    private val generation = AtomicLong()
    fun ticket(): Long = generation.get()
    fun isCurrent(ticket: Long): Boolean = ticket == generation.get()
    fun advance() { generation.incrementAndGet() }
}
