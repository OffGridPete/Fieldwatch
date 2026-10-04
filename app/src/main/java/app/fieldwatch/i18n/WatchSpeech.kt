package app.fieldwatch.i18n

import app.fieldwatch.domain.*

fun localizedTestWatchPhrase(what: AlertVoiceWhat): String = when (what) {
    AlertVoiceWhat.CLASS -> SignatureClass.FINDER.displayLabel()
    AlertVoiceWhat.SIGNATURE -> DefaultCatalog.fleets().first { it.id == "fleet-airtag" }.displayName()
    AlertVoiceWhat.BOTH -> "${SignatureClass.FINDER.displayLabel()}, ${localizedTestWatchPhrase(AlertVoiceWhat.SIGNATURE)}"
}

fun localizedWatchPhrase(device: Sighting, fleets: List<Fleet>, what: AlertVoiceWhat, target: WatchTarget?): String {
    if (target?.deviceKey != null) return spokenWatchPhrase(device, fleets, what, target)
    val id = target?.fleetId?.takeIf { it in device.fleetIds } ?: device.fleetIds.firstOrNull()
    val fleet = fleets.firstOrNull { it.id == id } ?: return localized("label_unmatched", "Unmatched")
    val cls = fleet.kind.displayLabel()
    val name = speakableWatchName(fleet.displayName())
    return when (what) {
        AlertVoiceWhat.CLASS -> cls
        AlertVoiceWhat.SIGNATURE -> name
        AlertVoiceWhat.BOTH -> if (cls.equals(name, true)) cls else "$cls, $name"
    }
}
