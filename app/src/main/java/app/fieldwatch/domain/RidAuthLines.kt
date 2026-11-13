package app.fieldwatch.domain

/** ASTM Authentication rows. English stays here. Packs carry the other languages. */
internal object RidAuthLines {
    val all: List<EnglishLine> = listOf(
        en("rid.auth.network", "Network Remote ID", LanguageSlot.CONTROL),
        en("rid.auth.none", "None", LanguageSlot.CONTROL),
        en(
            "rid.auth.note",
            "The plate claims this type. The signature is not checked.",
            LanguageSlot.BODY,
        ),
        en("rid.auth.operator", "Operator ID signature", LanguageSlot.CONTROL),
        en("rid.auth.page", "Auth page", LanguageSlot.CONTROL),
        en("rid.auth.page.value", "{page}, last {last}", LanguageSlot.CONTROL),
        en("rid.auth.past", "Page is past the last page.", LanguageSlot.CONTROL),
        en("rid.auth.present", "Auth data present, {n} bytes", LanguageSlot.CONTROL),
        en("rid.auth.private", "Private {n}", LanguageSlot.CONTROL),
        en("rid.auth.reserved", "Reserved {n}", LanguageSlot.CONTROL),
        en("rid.auth.set", "Message set signature", LanguageSlot.CONTROL),
        en("rid.auth.specific", "Specific authentication", LanguageSlot.CONTROL),
        en("rid.auth.time", "Auth timestamp", LanguageSlot.CONTROL),
        en("rid.auth.time.unset", "Not set", LanguageSlot.CONTROL),
        en("rid.auth.type", "Authentication", LanguageSlot.CONTROL),
        en("rid.auth.uas", "UAS ID signature", LanguageSlot.CONTROL),
    )
}
