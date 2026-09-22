package app.fieldwatch.ui

import app.fieldwatch.FieldwatchApp
import app.fieldwatch.domain.Hunt
import app.fieldwatch.domain.HuntCue
import app.fieldwatch.domain.RssiSample
import app.fieldwatch.domain.Sighting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HuntUi(
    val active: Boolean = false,
    val device: Sighting? = null,
    val title: String = "",
    val cue: HuntCue = HuntCue.WAITING,
    val peakRssi: Int = -127,
    val samples: List<RssiSample> = emptyList(),
    val lastSeen: Long = 0L,
)

/**
 * Locate-a-radio session: tracks the target key, records an RSSI trail
 * (last 120 samples), and exposes the hot/cold cue the Hunt screen draws.
 */
class HuntController(
    private val app: FieldwatchApp,
    private val clock: StateFlow<Long>,
    private val scope: CoroutineScope,
) {
    private val huntKey = MutableStateFlow<String?>(null)
    private val huntStartedAt = MutableStateFlow(0L)
    private val huntPeakRssi = MutableStateFlow(-127)
    private val huntSamples = MutableStateFlow<List<RssiSample>>(emptyList())

    val hunt: StateFlow<HuntUi> = combine(
        combine(huntKey, huntStartedAt, huntPeakRssi, huntSamples) { key, started, peak, samples ->
            arrayOf(key, started, peak, samples)
        },
        app.devices.devices,
        clock,
    ) { bits, devices, now ->
        val key = bits[0] as String?
        val started = bits[1] as Long
        val peak = bits[2] as Int
        @Suppress("UNCHECKED_CAST")
        val samples = bits[3] as List<RssiSample>
        if (key == null) return@combine HuntUi()
        val device = devices.firstOrNull { it.key == key }
        HuntUi(
            active = true,
            device = device,
            title = device?.listTitle() ?: "Hunt",
            cue = Hunt.cue(samples, now, device?.lastSeen, device == null && started > 0L),
            peakRssi = peak,
            samples = samples,
            lastSeen = device?.lastSeen ?: 0L,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), HuntUi())

    init {
        scope.launch {
            combine(app.devices.devices, huntKey) { devices, key ->
                key to devices.firstOrNull { it.key == key }
            }.collect { (key, device) ->
                if (key == null || device == null) return@collect
                val last = huntSamples.value.lastOrNull()
                if (last != null && device.lastSeen <= last.at) return@collect
                val sample = RssiSample(device.lastSeen, device.rssi)
                huntSamples.update { (it + sample).takeLast(120) }
                if (device.rssi > huntPeakRssi.value) huntPeakRssi.value = device.rssi
            }
        }
    }

    fun startHunt(device: Sighting) {
        val now = System.currentTimeMillis()
        huntKey.value = device.key
        huntStartedAt.value = now
        huntPeakRssi.value = device.rssi
        huntSamples.value = listOf(RssiSample(now, device.rssi))
    }

    fun resetHunt() {
        val key = huntKey.value ?: return
        val live = app.devices.devices.value.firstOrNull { it.key == key } ?: return
        startHunt(live)
    }

    fun stopHunt() {
        huntKey.value = null
        huntSamples.value = emptyList()
        huntPeakRssi.value = -127
        huntStartedAt.value = 0L
    }

    fun huntTick(beepOn: Boolean, vibrateOn: Boolean) {
        if (!beepOn && !vibrateOn) return
        app.alerter.huntTick(beepOn, vibrateOn)
    }
}
