package app.fieldwatch.domain

import java.util.Locale

/**
 * Maker line for a CTA-2063 serial already on an ASTM Basic ID.
 * Longest prefix wins. The caller only asks when the ID type is Serial.
 */
internal object Cta2063 {
    fun label(serial: String): String? {
        val id = serial.trim().uppercase(Locale.US)
        if (id.isEmpty()) return null
        return PREFIXES.firstOrNull { id.startsWith(it.first) }?.second
    }

    private val PREFIXES = listOf(
        "18179132" to "Freefly Alta X Gen2",
        "1817913" to "Freefly Astro",
        "181792" to "Freefly Alta X",
        "181793" to "Freefly Alta X broadcast kit",
        "1817" to "Freefly",
        "1914CL2D" to "BRINC Lemur 2",
        "1914CR1D" to "BRINC Responder",
        "1914" to "BRINC",
        "1839FTD" to "Teal 2",
        "1839" to "Teal",
    )
}
