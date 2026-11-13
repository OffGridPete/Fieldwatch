package app.fieldwatch.domain

/**
 * Apple/Google Exposure Notification service data.
 * A 20-byte record is a 16-byte identifier plus 4 bytes of encrypted metadata.
 * Those bytes stay off the decoded rows.
 */
internal object ExposureNotification {
    fun matches(uuid: String): Boolean = shortUuid(uuid) == 0xFD6F

    /**
     * Null when the hex length is odd, so the page adds no row.
     * A blank record is 0 bytes. Exactly 20 bytes is the two labels.
     * Any other length is the one sentence.
     */
    fun label(record: ServiceDataRecord): Label? {
        if (!matches(record.uuid)) return null
        val hex = record.dataHex.filter { it.isLetterOrDigit() }
        if (hex.length % 2 != 0) return null
        val n = hex.length / 2
        if (n == 20) return Label(standardRows(), null)
        return Label(emptyList(), LanguageText.text("en.bytes", mapOf("n" to n.toString())))
    }

    /** Decoded rows for a parsed payload. Only the 20-byte layout has rows. */
    fun decodedFields(bytes: ByteArray): List<AdvPayloadDecoder.Field> {
        if (bytes.size != 20) return emptyList()
        return standardRows().map { (label, value) -> AdvPayloadDecoder.Field(label, value) }
    }

    data class Label(
        val rows: List<Pair<String, String>>,
        val fallback: String?,
    )

    private fun standardRows(): List<Pair<String, String>> = listOf(
        LanguageText.text("en.rpi") to LanguageText.text("en.rpi.value"),
        LanguageText.text("en.aem") to LanguageText.text("en.aem.value"),
    )

    private fun shortUuid(uuid: String): Int? {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex.length == 4 -> hex.toIntOrNull(16)
            hex.length == 8 && hex.startsWith("0000") -> hex.substring(4, 8).toIntOrNull(16)
            hex.length == 32 && hex.startsWith("0000") &&
                hex.endsWith("00001000800000805F9B34FB") -> hex.substring(4, 8).toIntOrNull(16)
            else -> null
        }
    }
}
