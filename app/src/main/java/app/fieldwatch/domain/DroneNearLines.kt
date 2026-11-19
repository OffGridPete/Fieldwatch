package app.fieldwatch.domain

/** Broadcast-within-range warning. English is the source. Packs carry the translations. */
internal object DroneNearLines {
    val all: List<EnglishLine> = listOf(
        en("ui.near.title", "Broadcast within {distance}", LanguageSlot.BODY),
        en("ui.near.body", "{name} · {d} away", LanguageSlot.BODY),
        en("ui.near.chip", "Within {distance}", LanguageSlot.BODY),
        en("ui.near.row", "Drone perimeter detection", LanguageSlot.CONTROL),
        en(
            "ui.near.lead",
            "Detects a drone using Remote ID within the range selected below.",
            LanguageSlot.BODY,
        ),
        en(
            "ui.near.note",
            "Uses the position the drone broadcasts, on Bluetooth or Wi-Fi. Needs a GPS fix of 50 m or better on this phone. A drone can broadcast a wrong position.",
            LanguageSlot.BODY,
        ),
        en("ui.near.tag", "Turn on Tag detections with GPS to use this.", LanguageSlot.BODY),
        en("ui.near.voice", "Says broadcast within the distance.", LanguageSlot.BODY),
        en("ui.near.reach", "The swarm warning still opens, and the distance chip stays, if these are off.", LanguageSlot.BODY),
        en(
            "ui.near.sit",
            "{clock} UTC. {name} broadcast within {distance}. {d} away.",
            LanguageSlot.BODY,
        ),
        en("ui.near.closest", "{clock} UTC. {name} closest broadcast {d}.", LanguageSlot.BODY),
        en("ui.near.wait", "Drone distance alert is waiting for a GPS fix.", LanguageSlot.BODY),
        en(
            "ui.near.wide",
            "Drone distance alert is paused. GPS fix is {acc} m; it needs 50 m or better.",
            LanguageSlot.BODY,
        ),
        en("ui.near.m", "{n} m", LanguageSlot.BODY),
        en("ui.near.km", "{n} km", LanguageSlot.BODY),
        en("ui.near.section", "Drone distance", LanguageSlot.BODY),
    )
}
