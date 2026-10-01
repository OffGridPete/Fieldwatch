package app.fieldwatch.wear

import android.content.Context
import android.util.Log
import app.fieldwatch.alert.Alerter
import app.fieldwatch.data.DeviceStore
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Sighting
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

class PhoneWearBridge(
    private val context: Context,
    private val scope: CoroutineScope,
    private val devices: DeviceStore,
    private val alerter: Alerter,
    private val onStartScanRequested: () -> Unit = {},
    private val onStopScanRequested: () -> Unit = {},
) : MessageClient.OnMessageReceivedListener {

    private val json = Json { ignoreUnknownKeys = true }
    private var syncJob: Job? = null
    private var huntTargetKey: String? = null

    fun start() {
        Wearable.getMessageClient(context).addListener(this)
        startSyncLoop()
    }

    fun stop() {
        Wearable.getMessageClient(context).removeListener(this)
        syncJob?.cancel()
        syncJob = null
    }

    fun setHuntTarget(targetKey: String?) {
        huntTargetKey = targetKey
    }

    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    pushStatusToWatch()
                } catch (e: Exception) {
                    Log.w(TAG, "Error pushing status to Wear nodes: ${e.message}")
                }
                delay(1500)
            }
        }
    }

    private suspend fun pushStatusToWatch() {
        val nodeList: List<Node> = Wearable.getNodeClient(context).connectedNodes.await().toList()
        if (nodeList.isEmpty()) return

        val deviceList = devices.devices.value
        val stats = devices.stats.value
        val wifiCount = deviceList.count { it.kind == RadioKind.WIFI }
        val bleCount = deviceList.count { it.kind == RadioKind.BLE }

        // Find trackers (AirTags, SmartTags, Tile, Drones, Watchlist)
        val trackers = deviceList.filter { sighting ->
            sighting.payloadUasId != null ||
            sighting.fleetIds.any { id ->
                id.contains("airtag", true) ||
                id.contains("smarttag", true) ||
                id.contains("tile", true) ||
                id.contains("tracker", true) ||
                id.contains("findmy", true)
            } ||
            sighting.liveDecode.any { chip ->
                chip.text.contains("AirTag", true) ||
                chip.text.contains("Find My", true) ||
                chip.text.contains("SmartTag", true) ||
                chip.text.contains("Remote ID", true)
            }
        }

        val topContacts = deviceList
            .filter { !it.gone }
            .sortedByDescending { it.rssi }
            .take(35)
            .map { d ->
                val isTracker = d.payloadUasId != null ||
                    d.fleetIds.any { id -> id.contains("airtag", true) || id.contains("smarttag", true) || id.contains("tile", true) || id.contains("tracker", true) } ||
                    d.liveDecode.any { it.text.contains("AirTag", true) || it.text.contains("SmartTag", true) || it.text.contains("Remote ID", true) }
                val kindStr = when {
                    d.payloadUasId != null -> "DRONE"
                    d.liveDecode.any { it.text.contains("AirTag", true) } -> "AIRTAG"
                    d.liveDecode.any { it.text.contains("SmartTag", true) } -> "SMARTTAG"
                    d.fleetIds.any { it.contains("tile", true) } -> "TILE"
                    d.kind == RadioKind.WIFI -> "WIFI"
                    else -> "BLE"
                }
                WearContactPayload(
                    key = d.key,
                    mac = d.mac,
                    name = d.displayName.ifBlank { d.mac },
                    kind = kindStr,
                    rssi = d.rssi,
                    isAlert = isTracker,
                )
            }

        val summary = RfSummaryPayload(
            bleCount = bleCount,
            wifiCount = wifiCount,
            trackerCount = trackers.size,
            scanning = stats.scanning,
            contacts = topContacts,
            timestamp = System.currentTimeMillis(),
        )

        val summaryBytes = json.encodeToString(summary).toByteArray(StandardCharsets.UTF_8)
        for (node in nodeList) {
            Wearable.getMessageClient(context).sendMessage(node.id, WearPaths.SUMMARY, summaryBytes)
        }

        // Push alerts if any trackers found
        if (trackers.isNotEmpty()) {
            val alerts = trackers.take(10).map { t ->
                val kind = when {
                    t.payloadUasId != null -> "DRONE"
                    t.liveDecode.any { it.text.contains("AirTag", true) } -> "AIRTAG"
                    t.liveDecode.any { it.text.contains("SmartTag", true) } -> "SMARTTAG"
                    t.fleetIds.any { it.contains("tile", true) } -> "TILE"
                    else -> "TRACKER"
                }
                val detailStr = t.liveDecode.firstOrNull()?.text ?: t.extras.ifBlank { "Nearby tracker" }
                WearAlertPayload(
                    key = t.key,
                    name = t.displayName.ifBlank { t.mac },
                    kind = kind,
                    rssi = t.rssi,
                    detail = detailStr,
                    timestamp = t.lastSeen,
                )
            }
            val alertsBytes = json.encodeToString(alerts).toByteArray(StandardCharsets.UTF_8)
            for (node in nodeList) {
                Wearable.getMessageClient(context).sendMessage(node.id, WearPaths.ALERTS, alertsBytes)
            }
        }

        // Push active hunt target if one is selected
        huntTargetKey?.let { targetKey ->
            val target = deviceList.firstOrNull { it.key == targetKey }
            if (target != null) {
                val prox = when {
                    target.rssi >= -55 -> "IMMEDIATE"
                    target.rssi >= -70 -> "NEAR"
                    target.rssi >= -85 -> "MID"
                    else -> "FAR"
                }
                val huntPayload = HuntUpdatePayload(
                    targetKey = target.key,
                    targetName = target.displayName.ifBlank { target.mac },
                    rssi = target.rssi,
                    proximity = prox,
                    timestamp = target.lastSeen,
                )
                val huntBytes = json.encodeToString(huntPayload).toByteArray(StandardCharsets.UTF_8)
                for (node in nodeList) {
                    Wearable.getMessageClient(context).sendMessage(node.id, WearPaths.HUNT_UPDATE, huntBytes)
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearPaths.CMD_START_SCAN -> {
                Log.i(TAG, "Wear requested start scan")
                onStartScanRequested()
            }
            WearPaths.CMD_STOP_SCAN -> {
                Log.i(TAG, "Wear requested stop scan")
                onStopScanRequested()
            }
            WearPaths.CMD_START_HUNT -> {
                val targetKey = String(event.data, StandardCharsets.UTF_8)
                Log.i(TAG, "Wear requested hunt for target: $targetKey")
                setHuntTarget(targetKey)
            }
            WearPaths.CMD_STOP_HUNT -> {
                Log.i(TAG, "Wear requested stop hunt")
                setHuntTarget(null)
            }
        }
    }

    companion object {
        private const val TAG = "PhoneWearBridge"
    }
}
