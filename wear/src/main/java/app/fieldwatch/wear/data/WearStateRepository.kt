package app.fieldwatch.wear.data

import app.fieldwatch.wear.model.HuntUpdatePayload
import app.fieldwatch.wear.model.RfSummaryPayload
import app.fieldwatch.wear.model.WearAlertPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WearStateRepository {
    private val _summary = MutableStateFlow(RfSummaryPayload())
    val summary: StateFlow<RfSummaryPayload> = _summary.asStateFlow()

    private val _alerts = MutableStateFlow<List<WearAlertPayload>>(emptyList())
    val alerts: StateFlow<List<WearAlertPayload>> = _alerts.asStateFlow()

    private val _hunt = MutableStateFlow<HuntUpdatePayload?>(null)
    val hunt: StateFlow<HuntUpdatePayload?> = _hunt.asStateFlow()

    private val _phoneConnected = MutableStateFlow(false)
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()

    private val _standaloneScanning = MutableStateFlow(false)
    val standaloneScanning: StateFlow<Boolean> = _standaloneScanning.asStateFlow()

    fun updateSummary(summary: RfSummaryPayload) {
        _summary.value = summary
        _phoneConnected.value = true
    }

    fun updateAlerts(alerts: List<WearAlertPayload>) {
        _alerts.value = alerts
    }

    fun updateHunt(hunt: HuntUpdatePayload?) {
        _hunt.value = hunt
    }

    fun setPhoneConnected(connected: Boolean) {
        _phoneConnected.value = connected
    }

    fun setStandaloneScanning(scanning: Boolean) {
        _standaloneScanning.value = scanning
    }
}
