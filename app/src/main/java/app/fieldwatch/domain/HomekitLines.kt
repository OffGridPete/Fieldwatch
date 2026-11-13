package app.fieldwatch.domain

/** HomeKit category and pairing rows. English stays here. Packs carry the other languages. */
internal object HomekitLines {
    val all: List<EnglishLine> = listOf(
        en("explain.hk.unpaired", "A HomeKit {category} is advertising as not paired.", LanguageSlot.BODY),
        en("hk.category", "HomeKit category", LanguageSlot.CONTROL),
        en("hk.category.n", "category {n}", LanguageSlot.CONTROL),
        en("hk.chip", "{category}, not paired", LanguageSlot.CONTROL),
        en("hk.config", "HomeKit configuration number", LanguageSlot.CONTROL),
        en("hk.deviceid", "HomeKit device ID", LanguageSlot.CONTROL),
        en(
            "hk.note",
            "Broadcast by the accessory. The category is its own claim. Not paired means it is accepting a Home app setup, not that it is unowned in law.",
            LanguageSlot.BODY,
        ),
        en("hk.paired", "HomeKit pairing", LanguageSlot.CONTROL),
        en("hk.paired.no", "Not paired", LanguageSlot.CONTROL),
        en("hk.paired.yes", "Paired", LanguageSlot.CONTROL),
        en("hk.setup", "Setup hash present", LanguageSlot.CONTROL),
        en("hk.state", "HomeKit state number", LanguageSlot.CONTROL),
        en("hk.version", "HomeKit version", LanguageSlot.CONTROL),
    )
}
