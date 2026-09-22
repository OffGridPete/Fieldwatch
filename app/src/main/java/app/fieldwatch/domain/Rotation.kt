package app.fieldwatch.domain

/**
 * Randomized-BLE rotation heuristic. When a new randomized address shows the
 * same structural payload fingerprint (manufacturer id + stable payload
 * prefix, service UUIDs, appearance, class, name) while its twin has gone
 * quiet, we mark the newcomer as a likely rotation of the old address.
 * A hint, not an identity — two identical units nearby can collide.
 */
object Rotation {

    /** The previous address must be silent this long before we link. */
    const val PREDECESSOR_SILENT_MS = 15_000L

    /** Structural fingerprint; null when the radio has nothing stable to key on. */
    fun fingerprint(d: Sighting): String? {
        if (d.kind != RadioKind.BLE || !d.randomized) return null
        val mfg = d.manufacturerId?.let {
            "%04X".format(it) + ":" + d.manufacturerDataHex.take(6).uppercase()
        }
        val uuids = d.serviceUuids.map { it.uppercase() }.sorted()
        val appearance = d.facts.appearance
        val deviceClass = d.facts.deviceClass
        if (mfg == null && uuids.isEmpty() && appearance == null && deviceClass == null &&
            d.name.isBlank()
        ) return null
        return buildString {
            append(mfg ?: "-")
            append('|')
            uuids.forEach { append(it); append(',') }
            append('|')
            append(appearance ?: -1)
            append('|')
            append(deviceClass ?: -1)
            append('|')
            append(d.name.trim().uppercase())
        }
    }
}
