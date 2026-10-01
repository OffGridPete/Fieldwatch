package app.fieldwatch.wear.radio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import app.fieldwatch.wear.data.WearStateRepository
import app.fieldwatch.wear.model.RfSummaryPayload
import app.fieldwatch.wear.model.WearAlertPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class WatchBleScanner(private val context: Context, private val scope: CoroutineScope) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var scanner: BluetoothLeScanner? = null
    private var scanJob: Job? = null
    private val sighted = ConcurrentHashMap<String, Long>()
    private val alertDevices = ConcurrentHashMap<String, WearAlertPayload>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = device.address ?: return
            val rssi = result.rssi
            val record = result.scanRecord
            sighted[address] = System.currentTimeMillis()

            // Check for Apple AirTag / Find My or Samsung SmartTag
            val mfgData = record?.manufacturerSpecificData
            var isTracker = false
            var trackerType = "BLE Device"

            if (mfgData != null) {
                // Apple ID = 0x004C
                val appleBytes = mfgData.get(0x004C)
                if (appleBytes != null && appleBytes.isNotEmpty()) {
                    val typeByte = appleBytes[0].toInt() and 0xFF
                    if (typeByte == 0x12 || typeByte == 0x07 || typeByte == 0x10) {
                        isTracker = true
                        trackerType = "Apple AirTag / Find My"
                    }
                }
                // Samsung ID = 0x0075
                val samsungBytes = mfgData.get(0x0075)
                if (samsungBytes != null && samsungBytes.size >= 2) {
                    val sub = samsungBytes[0].toInt() and 0xFF
                    if (sub == 0x42 || sub == 0x01) {
                        isTracker = true
                        trackerType = "Samsung SmartTag"
                    }
                }
            }

            if (isTracker) {
                val name = device.name ?: trackerType
                alertDevices[address] = WearAlertPayload(
                    key = address,
                    name = name,
                    kind = if (trackerType.contains("Apple")) "AIRTAG" else "SMARTTAG",
                    rssi = rssi,
                    detail = "$trackerType detected locally",
                    timestamp = System.currentTimeMillis(),
                )
            }

            sightedInfo[address] = Pair(device.name ?: trackerType, rssi)
            updateRepo()
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "Watch BLE scan failed: $errorCode")
        }
    }

    private val sightedInfo = ConcurrentHashMap<String, Pair<String, Int>>()

    private fun updateRepo() {
        val contactList = sightedInfo.map { (addr, info) ->
            val isTrackerAlert = alertDevices[addr] != null
            app.fieldwatch.wear.model.WearContactPayload(
                key = addr,
                mac = addr,
                name = info.first,
                kind = if (isTrackerAlert) "TRACKER" else "BLE",
                rssi = info.second,
                isAlert = isTrackerAlert,
            )
        }.sortedByDescending { it.rssi }.take(35)

        WearStateRepository.updateSummary(
            RfSummaryPayload(
                bleCount = sighted.size,
                wifiCount = 0,
                trackerCount = alertDevices.size,
                scanning = true,
                contacts = contactList,
                timestamp = System.currentTimeMillis(),
            )
        )
        WearStateRepository.updateAlerts(alertDevices.values.toList())
    }

    @SuppressLint("MissingPermission")
    fun startBurstSweep(durationMs: Long = 10_000L) {
        val adapter = bluetoothManager.adapter ?: return
        if (!adapter.isEnabled) return
        scanner = adapter.bluetoothLeScanner ?: return

        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.Main) {
            try {
                WearStateRepository.setStandaloneScanning(true)
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                val filters = listOf(ScanFilter.Builder().build())

                scanner?.startScan(filters, settings, scanCallback)
                delay(durationMs)
            } finally {
                stopScan()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        runCatching {
            scanner?.stopScan(scanCallback)
        }
        WearStateRepository.setStandaloneScanning(false)
        scanJob?.cancel()
        scanJob = null
    }

    companion object {
        private const val TAG = "WatchBleScanner"
    }
}
