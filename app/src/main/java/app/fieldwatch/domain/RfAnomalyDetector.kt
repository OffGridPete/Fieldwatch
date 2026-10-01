package app.fieldwatch.domain

/**
 * Tactical RF anomaly detection engine.
 *
 * Detects adversarial radio phenomena in real-time, such as:
 * - High-rate BLE advertising spam floods (Flipper Zero, ESP32 Marauder, Bruce, SourApple).
 *
 * These attacks broadcast rapid bursts of spoofed pairing frames (AirPods popups, FastPair,
 * SwiftPair) with rotating randomized MAC addresses, designed to cause notification spam,
 * denial-of-service, or confusion.
 */
object RfAnomalyDetector {

    data class SpamAssessment(
        val isSpamAttackActive: Boolean,
        val burstCount: Int,
        val primaryVector: String? = null,
        val details: String? = null,
    )

    /**
     * Assesses a collection of sightings for active BLE advertising spam attacks.
     *
     * @param sightings The active list of sightings.
     * @param now Current timestamp in milliseconds.
     * @param windowMs Observation window (default 5 seconds).
     * @param thresholdPerWindow Minimum count of ephemeral pairing frames in the window to trigger alert.
     */
    fun assessBleSpam(
        sightings: Collection<Sighting>,
        now: Long = System.currentTimeMillis(),
        windowMs: Long = 5_000L,
        thresholdPerWindow: Int = 12,
    ): SpamAssessment {
        val windowStart = now - windowMs

        // Filter sightings first heard in this window with low hitCount (ephemeral transient MACs)
        val recentEphemerals = sightings.filter { s ->
            s.kind == RadioKind.BLE &&
                s.firstSeen >= windowStart &&
                s.hitCount <= 3
        }

        if (recentEphemerals.size < thresholdPerWindow) {
            return SpamAssessment(isSpamAttackActive = false, burstCount = recentEphemerals.size)
        }

        var applePairingCount = 0
        var fastPairCount = 0
        var microsoftCount = 0

        for (s in recentEphemerals) {
            val mfg = s.facts.mfgRecords.ifEmpty {
                s.manufacturerId?.let { listOf(MfgRecord(it, s.manufacturerDataHex)) } ?: emptyList()
            }
            if (mfg.any { it.companyId == 0x004C }) {
                applePairingCount++
            }
            if (mfg.any { it.companyId == 0x0006 }) {
                microsoftCount++
            }
            if (s.facts.serviceData.any { sd -> uuid16(sd.uuid) == 0xFE2C }) {
                fastPairCount++
            }
        }

        val maxVectorCount = maxOf(applePairingCount, fastPairCount, microsoftCount)
        if (maxVectorCount < 6 && recentEphemerals.size < thresholdPerWindow * 2) {
            return SpamAssessment(isSpamAttackActive = false, burstCount = recentEphemerals.size)
        }

        val primaryVector = when {
            applePairingCount >= fastPairCount && applePairingCount >= microsoftCount ->
                "Apple Proximity Pairing / AirDrop Flood"
            fastPairCount >= applePairingCount && fastPairCount >= microsoftCount ->
                "Google FastPair Flood"
            microsoftCount >= 6 ->
                "Microsoft SwiftPair Flood"
            else -> "Multi-Vector BLE Pairing Flood"
        }

        val rateSec = recentEphemerals.size.toDouble() / (windowMs.toDouble() / 1000.0)
        val details = "High burst rate (%.1f ads/sec) of ephemeral pairing beacons. Characteristic signature of Flipper Zero or ESP32 Marauder tools."
            .format(rateSec)

        return SpamAssessment(
            isSpamAttackActive = true,
            burstCount = recentEphemerals.size,
            primaryVector = primaryVector,
            details = details,
        )
    }

    private fun uuid16(uuid: String): Int? {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex.length == 4 -> hex.toIntOrNull(16)
            hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB") ->
                hex.substring(4, 8).toIntOrNull(16)
            else -> null
        }
    }
}
