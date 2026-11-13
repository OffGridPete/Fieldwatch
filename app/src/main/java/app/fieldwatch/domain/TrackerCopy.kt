package app.fieldwatch.domain

/** Popup and notification lines. The title is the warning. The radio sits under Details. */
object TrackerCopy {
    fun title(kind: TrackerWatch.Kind): String = when (kind) {
        TrackerWatch.Kind.FOLLOW -> LanguageText.text("tracker.follow")
        TrackerWatch.Kind.PLACES -> LanguageText.text("tracker.places")
    }

    fun prose(notice: TrackerWatch.Notice, streets: List<String>): String {
        val main = when (notice.kind) {
            TrackerWatch.Kind.FOLLOW -> LanguageText.text(
                "tracker.separated.for",
                mapOf(
                    "minutes" to notice.minutes.toString(),
                    "places" to notice.places.toString(),
                ),
            )
            TrackerWatch.Kind.PLACES -> if (streets.size == notice.stops.size && streets.isNotEmpty()) {
                LanguageText.text("tracker.seen.streets", mapOf("places" to streets.joinToString(", ")))
            } else {
                LanguageText.text("tracker.seen.count", mapOf("n" to notice.places.toString()))
            }
        }
        if (notice.alsoPlaces < PlaceMemory.MIN_PLACES) return main
        return main + "\n\n" + LanguageText.text(
            "tracker.also.places",
            mapOf("n" to notice.alsoPlaces.toString()),
        )
    }

    fun details(notice: TrackerWatch.Notice, demo: Boolean): String = buildString {
        if (notice.name.isNotBlank()) appendLine(notice.name)
        if (Rssi.measured(notice.rssi)) appendLine("${notice.rssi} dBm")
        append(MacUtil.screenMac(notice.mac, demo))
    }

    fun notification(notice: TrackerWatch.Notice, demo: Boolean): String {
        val lines = ArrayList<String>(4)
        lines += prose(notice, emptyList())
        if (notice.name.isNotBlank()) lines += notice.name
        if (Rssi.measured(notice.rssi)) lines += "${notice.rssi} dBm"
        lines += MacUtil.screenMac(notice.mac, demo)
        return lines.joinToString("\n")
    }

    /** Street names already on the phone. A missing name keeps the count sentence. */
    fun streets(notice: TrackerWatch.Notice, namesByCell: Map<String, String>, demo: Boolean): List<String> {
        if (demo || notice.kind != TrackerWatch.Kind.PLACES || namesByCell.isEmpty()) return emptyList()
        return notice.stops.map { spot ->
            Geo.nameNear(spot.lat, spot.lon, namesByCell, TrackerWatch.APART_M) ?: return emptyList()
        }
    }
}
