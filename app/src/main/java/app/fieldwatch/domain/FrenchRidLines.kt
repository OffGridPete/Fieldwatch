package app.fieldwatch.domain

/** French Direct Remote ID rows. English stays here. Packs carry the other languages. */
internal object FrenchRidLines {
    val all: List<EnglishLine> = listOf(
        en("rid.french.plate", "French Direct Remote ID", LanguageSlot.CONTROL),
        en("rid.french.version", "French RID protocol version", LanguageSlot.CONTROL),
        en("rid.french.id", "French identifier", LanguageSlot.CONTROL),
        en("rid.french.id.value", "{trigram} {model} {serial}", LanguageSlot.CONTROL),
        en("rid.french.height", "Height above takeoff", LanguageSlot.CONTROL),
        en("rid.french.takeoff", "Takeoff position (French plate)", LanguageSlot.CONTROL),
        en(
            "rid.french.note",
            "Advertised by the drone on the French legal plate. Not checked against a map.",
            LanguageSlot.BODY,
        ),
    )
}
