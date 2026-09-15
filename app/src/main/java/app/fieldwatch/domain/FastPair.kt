package app.fieldwatch.domain

/**
 * Google Fast Pair (UUID FE2C) has two Live-relevant shapes:
 * 3-byte model ID = pairing mode; longer = account-key plaza noise.
 */
object FastPair {
    const val FLEET_ID = "fleet-fast-pair"

    fun pairingAdvertised(facts: RadioFacts): Boolean =
        facts.serviceData.any { rec ->
            isFastPairUuid(rec.uuid) && hexLen(rec.dataHex) == 6
        }

    /** Account-key Fast Pair with no other signature — plaza chips. Pairing-mode stays. */
    fun isAccountKeyOnly(device: Sighting): Boolean {
        if (device.fastPairPairing) return false
        if (FLEET_ID !in device.fleetIds) return false
        return device.fleetIds.size == 1
    }

    fun liveLabel(pairing: Boolean): String =
        if (pairing) "Fast Pair pairing" else "Fast Pair"

    fun isFastPairUuid(uuid: String): Boolean {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return hex == "FE2C" ||
            (hex.length >= 8 && hex.substring(4, 8) == "FE2C")
    }

    private fun hexLen(raw: String): Int = raw.count { it.isLetterOrDigit() }
}
