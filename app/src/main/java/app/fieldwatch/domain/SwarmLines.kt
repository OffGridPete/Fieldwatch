package app.fieldwatch.domain

/** Remote ID swarm warning. English is the source. Packs carry the translations. */
internal object SwarmLines {
    val all: List<EnglishLine> = listOf(
        en("ui.swarm.title", "Drone swarm detected", LanguageSlot.CONTROL),
        en("ui.swarm.body", "{n} new drones in {s} seconds.", LanguageSlot.BODY),
        en(
            "ui.swarm.details",
            "New drones: {n}\nTime: {s} seconds\nBluetooth: {b}\nWi-Fi: {w}",
            LanguageSlot.BODY,
        ),
        en("ui.swarm.unknown", "No maker named", LanguageSlot.BODY),
        en("ui.swarm.voice", "Says drone swarm detected, or broadcast within the distance.", LanguageSlot.BODY),
        en(
            "ui.swarm.part",
            "Part of a drone swarm at {time}",
            LanguageSlot.BODY,
        ),
        en("ui.swarm.warn", "Drone swarm detection", LanguageSlot.CONTROL),
        en(
            "ui.swarm.note",
            "Warns when Remote ID detects new drones in the time window below.",
            LanguageSlot.BODY,
        ),
        en("ui.swarm.trigger", "Swarm trigger", LanguageSlot.CONTROL),
        en("ui.swarm.min", "New drones needed ({n} drones)", LanguageSlot.BODY),
        en("ui.swarm.window", "Time window ({s} seconds)", LanguageSlot.BODY),
        en(
            "ui.swarm.quiet",
            "Cooldown before next warn ({min} min)",
            LanguageSlot.BODY,
        ),
        en(
            "ui.swarm.help",
            "Lower numbers warn sooner but fire more at drone shows and race meets.",
            LanguageSlot.BODY,
        ),
        en("ui.swarm.reset", "Reset to defaults", LanguageSlot.CONTROL),
        en(
            "ui.swarm.debrief",
            "{clock} UTC. Remote ID swarm. {n} new drones in {s} seconds.",
            LanguageSlot.BODY,
        ),
        en("ui.swarm.section", "Remote ID swarm", LanguageSlot.CONTROL),
        en("settings.topic.swarm", "Drone monitoring", LanguageSlot.CONTROL),
    )
}
