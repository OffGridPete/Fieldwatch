package app.fieldwatch.wear

import kotlinx.serialization.Serializable

object WearPaths {
    const val SUMMARY = "/fieldwatch/summary"
    const val ALERTS = "/fieldwatch/alerts"
    const val HUNT_UPDATE = "/fieldwatch/hunt"
    const val CMD_START_SCAN = "/fieldwatch/cmd/start_scan"
    const val CMD_STOP_SCAN = "/fieldwatch/cmd/stop_scan"
    const val CMD_START_HUNT = "/fieldwatch/cmd/start_hunt"
    const val CMD_STOP_HUNT = "/fieldwatch/cmd/stop_hunt"
}

@Serializable
data class WearContactPayload(
    val key: String,
    val mac: String,
    val name: String,
    val kind: String, // "WIFI", "BLE", "AIRTAG", "SMARTTAG", "DRONE", "TILE", "TRACKER"
    val rssi: Int,
    val isAlert: Boolean = false,
)

@Serializable
data class RfSummaryPayload(
    val bleCount: Int = 0,
    val wifiCount: Int = 0,
    val trackerCount: Int = 0,
    val scanning: Boolean = false,
    val contacts: List<WearContactPayload> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class WearAlertPayload(
    val key: String,
    val name: String,
    val kind: String, // AIRTAG, SMARTTAG, TILE, DRONE, WATCHLIST, OTHER
    val rssi: Int,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class HuntUpdatePayload(
    val targetKey: String,
    val targetName: String,
    val rssi: Int,
    val distanceEstimatedM: Double = 0.0,
    val proximity: String = "UNKNOWN", // IMMEDIATE (<1m), NEAR (<5m), MID (<15m), FAR (>15m)
    val timestamp: Long = System.currentTimeMillis(),
)
