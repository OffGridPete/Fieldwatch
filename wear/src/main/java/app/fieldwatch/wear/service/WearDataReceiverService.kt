package app.fieldwatch.wear.service

import android.util.Log
import app.fieldwatch.wear.data.WearStateRepository
import app.fieldwatch.wear.haptics.WearHaptics
import app.fieldwatch.wear.model.HuntUpdatePayload
import app.fieldwatch.wear.model.RfSummaryPayload
import app.fieldwatch.wear.model.WearAlertPayload
import app.fieldwatch.wear.model.WearPaths
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

class WearDataReceiverService : WearableListenerService() {

    private val json = Json { ignoreUnknownKeys = true }
    private val haptics by lazy { WearHaptics(applicationContext) }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val dataStr = String(messageEvent.data, StandardCharsets.UTF_8)
        Log.d(TAG, "Message received on path $path")

        when (path) {
            WearPaths.SUMMARY -> {
                runCatching {
                    val summary = json.decodeFromString<RfSummaryPayload>(dataStr)
                    WearStateRepository.updateSummary(summary)
                }.onFailure { Log.e(TAG, "Failed decoding summary", it) }
            }
            WearPaths.ALERTS -> {
                runCatching {
                    val alerts = json.decodeFromString<List<WearAlertPayload>>(dataStr)
                    val prevCount = WearStateRepository.alerts.value.size
                    WearStateRepository.updateAlerts(alerts)
                    if (alerts.size > prevCount) {
                        // New alert arrived, buzz the wrist!
                        haptics.vibrateTrackerAlert()
                    }
                }.onFailure { Log.e(TAG, "Failed decoding alerts", it) }
            }
            WearPaths.HUNT_UPDATE -> {
                runCatching {
                    val hunt = json.decodeFromString<HuntUpdatePayload>(dataStr)
                    WearStateRepository.updateHunt(hunt)
                }.onFailure { Log.e(TAG, "Failed decoding hunt", it) }
            }
        }
    }

    override fun onPeerConnected(peer: Node) {
        super.onPeerConnected(peer)
        Log.i(TAG, "Phone node connected: ${peer.displayName}")
        WearStateRepository.setPhoneConnected(true)
    }

    override fun onPeerDisconnected(peer: Node) {
        super.onPeerDisconnected(peer)
        Log.i(TAG, "Phone node disconnected: ${peer.displayName}")
        WearStateRepository.setPhoneConnected(false)
    }

    companion object {
        private const val TAG = "WearDataReceiver"
    }
}
