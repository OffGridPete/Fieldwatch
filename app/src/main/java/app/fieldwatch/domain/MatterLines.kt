package app.fieldwatch.domain

/** Matter commissioning rows. English stays here. Packs carry the other languages. */
internal object MatterLines {
    val all: List<EnglishLine> = listOf(
        en(
            "explain.matter.setup",
            "A Matter device is advertising as commissionable.",
            LanguageSlot.BODY,
        ),
        en("matter.chip", "Matter setup", LanguageSlot.CONTROL),
        en("matter.commissionable", "Commissionable (setup)", LanguageSlot.CONTROL),
        en("matter.discriminator", "Discriminator", LanguageSlot.CONTROL),
        en("matter.flags.additional", "Additional data present", LanguageSlot.CONTROL),
        en("matter.flags.extended", "Extended announcement", LanguageSlot.CONTROL),
        en("matter.mode", "Matter", LanguageSlot.CONTROL),
        en(
            "matter.note",
            "Broadcast while the device is offering Matter setup over Bluetooth. The vendor and product IDs are the device's own claim.",
            LanguageSlot.BODY,
        ),
        en("matter.other", "Matter BLE, opcode 0x{opcode}, {n} bytes", LanguageSlot.BODY),
        en("matter.other.one", "Matter BLE, opcode 0x{opcode}, 1 byte", LanguageSlot.BODY),
        en("matter.product", "Matter product ID", LanguageSlot.CONTROL),
        en("matter.ranging", "Matter proximity ranging", LanguageSlot.CONTROL),
        en("matter.vendor", "Matter vendor ID", LanguageSlot.CONTROL),
    )
}
