package app.fieldwatch.radio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import app.fieldwatch.domain.Observation
import app.fieldwatch.domain.RadioKind
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class WifiRadio(
    private val context: Context,
    private val onObservation: (Observation) -> Unit,
    private val onScanFinished: (Int, Boolean) -> Unit,
) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val main = Handler(Looper.getMainLooper())
    private var registered = false
    private val lastEmitAt = AtomicLong(0L)
    private val awaitingScan = AtomicBoolean(false)
    private val nextAllowedAt = AtomicLong(0L)
    private val failStreak = AtomicInteger(0)
    private val scanStarts = ArrayDeque<Long>()
    private val callbackExecutor = Executors.newSingleThreadExecutor()
    private val quotaLock = Any()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
            val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            emitResults(fresh = updated || awaitingScan.get())
        }
    }

    private val resultsCallback = if (Build.VERSION.SDK_INT >= 30) {
        object : WifiManager.ScanResultsCallback() {
            override fun onScanResultsAvailable() {
                // Only a scan we asked for is "fresh". Spontaneous callbacks are often a
                // partial or cached set; treating those as fresh ages every other AP off.
                emitResults(fresh = awaitingScan.get())
            }
        }
    } else null

    private val poll = Runnable { emitResults(fresh = awaitingScan.get()) }

    @SuppressLint("MissingPermission")
    fun start() {
        if (registered) return
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                wifi.registerScanResultsCallback(callbackExecutor, resultsCallback!!)
            }
        }
        registered = true
        emitResults(fresh = false)
        requestScan(minIntervalMs = 0L)
    }

    fun stop() {
        if (!registered) return
        main.removeCallbacks(poll)
        runCatching { context.unregisterReceiver(receiver) }
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { wifi.unregisterScanResultsCallback(resultsCallback!!) }
        }
        registered = false
        awaitingScan.set(false)
    }

    @SuppressLint("MissingPermission")
    fun requestScan(minIntervalMs: Long = 30_000L, unthrottled: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val quotaWait = if (unthrottled) 0L else synchronized(quotaLock) { pruneAndQuotaWait(now) }
        val gate = maxOf(nextAllowedAt.get(), quotaWait)
        if (now < gate) {
            if (quotaWait > nextAllowedAt.get()) nextAllowedAt.set(quotaWait)
            return false
        }
        val ok = runCatching { wifi.startScan() }.getOrDefault(false)
        if (ok) {
            failStreak.set(0)
            awaitingScan.set(true)
            synchronized(quotaLock) { scanStarts.addLast(now) }
            val floor = if (unthrottled) FAST_INTERVAL_MS else 28_000L
            nextAllowedAt.set(now + minIntervalMs.coerceAtLeast(floor))
            main.removeCallbacks(poll)
            main.postDelayed(poll, 2_400L)
        } else {
            val streak = failStreak.incrementAndGet().coerceAtMost(4)
            val backoff = (8_000L * (1L shl (streak - 1))).coerceAtMost(45_000L)
            nextAllowedAt.set(now + backoff)
            awaitingScan.set(false)
            main.removeCallbacks(poll)
            main.postDelayed({ emitResults(fresh = false) }, 250L)
        }
        return ok
    }

    fun waitingOnOs(): Boolean = failStreak.get() > 0

    fun throttleHint(): String {
        val now = System.currentTimeMillis()
        return when {
            failStreak.get() > 0 -> "Wi-Fi waiting on OS"
            awaitingScan.get() -> "Wi-Fi scanning"
            nextAllowedAt.get() - now > 1_500L -> {
                val sec = ((nextAllowedAt.get() - now + 999L) / 1000L).toInt().coerceAtLeast(1)
                "Wi-Fi next ${sec}s"
            }
            else -> ""
        }
    }

    private fun pruneAndQuotaWait(now: Long): Long {
        while (scanStarts.isNotEmpty() && now - scanStarts.first() > QUOTA_WINDOW_MS) {
            scanStarts.removeFirst()
        }
        if (scanStarts.size < QUOTA_SCANS) return 0L
        return scanStarts.first() + QUOTA_WINDOW_MS
    }

    @SuppressLint("MissingPermission")
    private fun emitResults(fresh: Boolean) {
        val nowWall = System.currentTimeMillis()
        val results = runCatching { wifi.scanResults }.getOrDefault(emptyList())
        if (results.isEmpty()) {
            if (awaitingScan.get()) {
                main.removeCallbacks(poll)
                main.postDelayed(poll, 1_200L)
            }
            return
        }
        if (nowWall - lastEmitAt.get() < 350L) return
        lastEmitAt.set(nowWall)
        val isFresh = fresh || awaitingScan.get()
        awaitingScan.set(false)
        var count = 0
        results.forEach { result ->
            if (result.BSSID.isNullOrBlank()) return@forEach
            count++
            onObservation(toObservation(result, nowWall, isFresh))
        }
        onScanFinished(count, isFresh)
    }

    private fun toObservation(result: ScanResult, nowWall: Long, fresh: Boolean): Observation {
        val ssid = result.ssidClean()
        val hidden = ssid.isBlank()
        val freq = result.frequency
        val ies = WifiIeParser.parse(result)
        val channel = ies.channelFromDs ?: channelOf(freq)
        val facts = app.fieldwatch.domain.RadioFacts(
            wifiStandard = WifiIeParser.wifiStandardLabel(result),
            channelWidth = WifiIeParser.channelWidthLabel(result),
            centerFreq0 = if (Build.VERSION.SDK_INT >= 23) result.centerFreq0.takeIf { it > 0 } else null,
            centerFreq1 = if (Build.VERSION.SDK_INT >= 23) result.centerFreq1.takeIf { it > 0 } else null,
            capabilities = result.capabilities?.ifBlank { null },
            supportedRates = ies.rates,
            security = ies.security,
            vendorIes = ies.vendorIes,
        )
        return Observation(
            kind = RadioKind.WIFI,
            mac = result.BSSID.orEmpty(),
            name = ssid,
            rssi = result.level,
            channel = channel,
            frequencyMhz = freq,
            hiddenSsid = hidden,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            at = nowWall,
            fresh = fresh,
            vendorIeOuis = ies.vendorIes.map { it.oui }.ifEmpty { vendorIeOuisOf(result) }.distinct(),
            facts = facts,
        )
    }

    companion object {
        private const val QUOTA_SCANS = 4
        private const val QUOTA_WINDOW_MS = 120_000L
        const val FAST_INTERVAL_MS = 8_000L

        /** Android 11+: true if the OS is still rate-limiting startScan(). Below 11 we cannot read it. */
        fun osScanThrottled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 30) return true
            val mgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return true
            return runCatching { mgr.isScanThrottleEnabled }.getOrDefault(true)
        }

        fun vendorIeOuisOf(result: ScanResult): List<String> {
            if (Build.VERSION.SDK_INT < 30) return emptyList()
            val ies = runCatching { result.informationElements }.getOrNull() ?: return emptyList()
            val found = LinkedHashSet<String>()
            ies.forEach { ie ->
                val id = runCatching { ie.id }.getOrDefault(-1)
                val raw = runCatching { ie.bytes }.getOrNull() ?: return@forEach
                val bytes = when (raw) {
                    is ByteArray -> raw
                    is java.nio.ByteBuffer -> {
                        val copy = ByteArray(raw.remaining())
                        raw.duplicate().get(copy)
                        copy
                    }
                    else -> return@forEach
                }
                if (id != 221 || bytes.size < 3) return@forEach
                found += "%02X:%02X:%02X".format(
                    bytes[0].toInt() and 0xFF,
                    bytes[1].toInt() and 0xFF,
                    bytes[2].toInt() and 0xFF,
                )
            }
            return found.toList()
        }

        fun channelOf(freq: Int): Int = when {
            freq in 2412..2484 -> (freq - 2407) / 5
            freq == 2484 -> 14
            freq in 5000..5895 -> (freq - 5000) / 5
            freq in 5955..7115 -> (freq - 5955) / 5 + 1
            else -> 0
        }
    }
}

private fun ScanResult.ssidClean(): String {
    val raw = if (Build.VERSION.SDK_INT >= 33) {
        wifiSsid?.toString()?.trim('"') ?: SSID
    } else {
        @Suppress("DEPRECATION")
        SSID
    }
    return raw?.trim()?.trim('"').orEmpty().let { if (it == "<unknown ssid>") "" else it }
}
