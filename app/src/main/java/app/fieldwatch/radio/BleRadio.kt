package app.fieldwatch.radio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import app.fieldwatch.domain.Observation
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.ScanIntensity
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class BleRadio(
    private val context: Context,
    private val onObservation: (Observation) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var scanner: BluetoothLeScanner? = null
    private val running = AtomicBoolean(false)
    private var lastIntensity = ScanIntensity.PERFORMANCE
    private val startedAt = AtomicLong(0L)
    private val lastCallbackAt = AtomicLong(0L)
    private val nextRetryAt = AtomicLong(0L)
    private var failStreak = 0
    @Volatile private var lastError: String? = null
    @Volatile private var demoted = false
    @Volatile private var restMs = 2_500L
    @Volatile private var hint = ""

    // Empty filter matches every advertisement but is not an "unfiltered" list,
    // which Samsung refuses while the screen is off.
    private val matchAll = listOf(ScanFilter.Builder().build())

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            lastCallbackAt.set(System.currentTimeMillis())
            failStreak = 0
            lastError = null
            hint = ""
            emit(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            if (results.isNotEmpty()) {
                lastCallbackAt.set(System.currentTimeMillis())
                failStreak = 0
                lastError = null
                hint = ""
            }
            results.forEach { emit(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            if (errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
                running.set(true)
                return
            }
            running.set(false)
            failStreak = (failStreak + 1).coerceAtMost(5)
            val backoff = (4_000L * (1L shl (failStreak - 1))).coerceAtMost(30_000L)
            nextRetryAt.set(System.currentTimeMillis() + backoff)
            lastError = "BLE scan failed ($errorCode)"
            hint = "BLE retrying"
            demoted = true
            onError(lastError!!)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(intensity: ScanIntensity) {
        val now = System.currentTimeMillis()
        if (now < nextRetryAt.get()) return
        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) {
            running.set(false)
            lastError = "Bluetooth is off"
            hint = "Bluetooth is off"
            onError(lastError!!)
            nextRetryAt.set(now + 8_000L)
            return
        }
        val next = adapter.bluetoothLeScanner
        if (next == null) {
            running.set(false)
            lastError = "BLE scanner unavailable"
            hint = "BLE unavailable"
            onError(lastError!!)
            nextRetryAt.set(now + 8_000L)
            return
        }
        lastIntensity = intensity
        if (running.get()) {
            runCatching { scanner?.stopScan(callback) }
            running.set(false)
        }
        scanner = next
        startWith(next, settingsFor(intensity))
    }

    @SuppressLint("MissingPermission")
    private fun startWith(target: BluetoothLeScanner, settings: ScanSettings) {
        val ok = runCatching {
            target.startScan(matchAll, settings, callback)
        }
        if (ok.isSuccess) {
            startedAt.set(System.currentTimeMillis())
            lastCallbackAt.set(0L)
            running.set(true)
            lastError = null
            hint = ""
        } else {
            running.set(false)
            failStreak = (failStreak + 1).coerceAtMost(5)
            val backoff = (4_000L * (1L shl (failStreak - 1))).coerceAtMost(30_000L)
            nextRetryAt.set(System.currentTimeMillis() + backoff)
            lastError = ok.exceptionOrNull()?.message ?: "BLE start failed"
            hint = "BLE retrying"
            demoted = true
            onError(lastError!!)
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val active = scanner ?: return
        runCatching { active.stopScan(callback) }
        scanner = null
        running.set(false)
    }

    fun isRunning(): Boolean = running.get()

    fun holding(): Boolean = !running.get()

    fun statusHint(): String = hint

    fun restartBackoffMs(): Long = restMs

    fun needsRestart(): Boolean {
        val now = System.currentTimeMillis()
        if (now < nextRetryAt.get()) return false
        if (!running.get()) {
            restMs = if (demoted) 6_000L else 2_500L
            return true
        }
        val runFor = now - startedAt.get()
        val heardAt = lastCallbackAt.get()
        val quietFor = if (heardAt == 0L) runFor else now - heardAt
        val maxRun = when {
            demoted -> 150_000L
            lastIntensity == ScanIntensity.PERFORMANCE -> 70_000L
            lastIntensity == ScanIntensity.BALANCED -> 180_000L
            else -> 20 * 60_000L
        }
        if (runFor >= maxRun) {
            // Recycle before Samsung suspends a long LOW_LATENCY session.
            restMs = if (lastIntensity == ScanIntensity.PERFORMANCE) 2_500L else 1_200L
            hint = "BLE cycling"
            return true
        }
        if (runFor > 12_000L && quietFor > 18_000L) {
            // Registered but silent = OS suspended the client. Rest longer to clear quota.
            demoted = true
            restMs = if (heardAt == 0L) 12_000L else 8_000L
            hint = "BLE parked · restarting"
            return true
        }
        if (heardAt > 0L && runFor > 90_000L) {
            demoted = false
        }
        return false
    }

    private fun settingsFor(intensity: ScanIntensity): ScanSettings {
        val mode = when {
            demoted && intensity == ScanIntensity.PERFORMANCE -> ScanSettings.SCAN_MODE_BALANCED
            else -> scanMode(intensity)
        }
        return ScanSettings.Builder()
            .setScanMode(mode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .apply {
                if (Build.VERSION.SDK_INT >= 26) {
                    setLegacy(false)
                    setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                }
            }
            .build()
    }

    private fun scanMode(intensity: ScanIntensity): Int = when (intensity) {
        ScanIntensity.PERFORMANCE -> ScanSettings.SCAN_MODE_LOW_LATENCY
        ScanIntensity.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
        ScanIntensity.SAVER -> ScanSettings.SCAN_MODE_LOW_POWER
    }

    private fun emit(result: ScanResult) {
        try {
            onObservation(toObservation(result))
        } catch (t: Throwable) {
            Log.e("FieldwatchBle", "scan result failed", t)
        }
    }

    private fun toObservation(result: ScanResult): Observation {
        val record = result.scanRecord
        val parsed = BleAdParser.parse(record?.bytes)
        // Advertised name only. BluetoothDevice.getName() is a binder call to
        // this phone's paired cache, not what is on the air.
        val name = sequenceOf(
            parsed.localName,
            record?.deviceName,
        ).mapNotNull { it?.trim()?.trim('"')?.ifBlank { null } }.firstOrNull().orEmpty()
        val uuids = (record?.serviceUuids.orEmpty() + record?.serviceData?.keys.orEmpty())
            .mapNotNull { it?.toString()?.uppercase() }
            .plus(parsed.uuids)
            .distinct()
        val facts = BleAdParser.facts(result)
        val mfg = facts.mfgRecords.firstOrNull()
        val raw = record?.bytes?.toHex().orEmpty()
        return Observation(
            kind = RadioKind.BLE,
            mac = result.device?.address.orEmpty(),
            name = name,
            rssi = result.rssi,
            channel = 0,
            frequencyMhz = 2402,
            hiddenSsid = false,
            serviceUuids = uuids,
            manufacturerId = mfg?.companyId,
            manufacturerDataHex = mfg?.dataHex.orEmpty(),
            rawHex = raw.take(1024),
            extras = "",
            at = System.currentTimeMillis(),
            facts = facts,
        )
    }

}

private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
