package app.fieldwatch.domain

/** Copy for Tracker monitoring. English stays here. Packs carry the other languages. */
internal object TrackerLines {
    val all: List<EnglishLine> = listOf(
        en("settings.topic.tracker", "Tracker monitoring", LanguageSlot.CONTROL),
        en("tracker.follow", "Separated tag with you", LanguageSlot.CONTROL),
        en(
            "tracker.follow.note",
            "A tag that says it is away from its owner, heard for 30 minutes with no gap over 20 minutes, at 3 places at least 100 m apart. This needs Tag detections with GPS.",
            LanguageSlot.BODY,
        ),
        en("tracker.places", "Heard at two places", LanguageSlot.CONTROL),
        en(
            "tracker.places.note",
            "A finder with a stable address that this phone hears at two stops. This needs Places. The list stays either way.",
            LanguageSlot.BODY,
        ),
        en(
            "tracker.separated.for",
            "Separated for {minutes} minutes, {places} places.",
            LanguageSlot.BODY,
        ),
        en("tracker.seen.count", "Seen at {n} places.", LanguageSlot.BODY),
        en("tracker.seen.streets", "Seen at {places}.", LanguageSlot.BODY),
        en("tracker.also.places", "Also heard at {n} places.", LanguageSlot.BODY),
        en("tracker.open", "Open radio", LanguageSlot.CONTROL),
    )
}
