package app.fieldwatch.domain

/** Exposure Notification rows. English stays here. Packs carry the other languages. */
internal object ExposureLines {
    val all: List<EnglishLine> = listOf(
        en("en.label", "Exposure Notification", LanguageSlot.CONTROL),
        en("en.rpi", "Rolling proximity identifier", LanguageSlot.CONTROL),
        en("en.rpi.value", "16 bytes, not shown", LanguageSlot.CONTROL),
        en("en.aem", "Associated metadata", LanguageSlot.CONTROL),
        en("en.aem.value", "Encrypted, 4 bytes", LanguageSlot.CONTROL),
        en(
            "en.note",
            "This is the Exposure Notification advertisement. It is not a tracker, and it does not name the phone. The identifier changes often.",
            LanguageSlot.BODY,
        ),
        en("en.bytes", "Exposure Notification, {n} bytes", LanguageSlot.CONTROL),
    )
}
