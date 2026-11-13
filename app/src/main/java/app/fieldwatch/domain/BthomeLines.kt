package app.fieldwatch.domain

/** BTHome rows. English stays here. Packs carry the other languages. */
internal object BthomeLines {
    val all: List<EnglishLine> = listOf(
        en("bth.version", "BTHome version", LanguageSlot.CONTROL),
        en("bth.encrypted", "BTHome payload encrypted", LanguageSlot.CONTROL),
        en("bth.trigger", "Trigger-based advertisements", LanguageSlot.CONTROL),
        en(
            "bth.note",
            "BTHome v2 service data. Values are advertised by the device.",
            LanguageSlot.BODY,
        ),
        en(
            "explain.bth.door",
            "A BTHome door or window sensor is advertising open.",
            LanguageSlot.BODY,
        ),
        en(
            "explain.bth.motion",
            "A BTHome motion sensor is advertising motion.",
            LanguageSlot.BODY,
        ),
    )
}
