package app.fieldwatch.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One Remote ID that just declared an emergency. */
data class RemoteIdEmergencyFire(
    val id: String,
    val title: String,
    val body: String,
    val noteId: Int,
)

data class RemoteIdEmergencyOutcome(
    val fires: List<RemoteIdEmergencyFire> = emptyList(),
)

/**
 * Warn when a Remote ID on the live list declares an emergency.
 * The same UAS ID on Bluetooth and Wi-Fi is one drone. A blank UAS ID stays
 * on its own radio, so two blanks do not merge. A radio marked Mine still alerts.
 * The latch follows this advertisement. A Basic ID neither starts nor ends it.
 * While the switch is off the latch still moves, so turning it on does not
 * speak for a declaration that was already open.
 */
class RemoteIdEmergency {
    data class Notice(
        val showDialog: Boolean,
        val body: String,
    ) {
        fun title(): String = LanguageText.text("ui.rid.emergency.row")
    }

    private class Heard {
        var declared: Boolean = false
        var ended: Boolean = false
        var aircraft: String? = null
        var uas: String? = null
    }

    private val lock = Any()
    private val latched = HashSet<String>()
    private val pending = ArrayDeque<String>()
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    /**
     * [devices] is null when the scan is not running, so a paused list is not
     * treated as every drone leaving. An empty list while scanning drops the latches.
     */
    fun pass(devices: List<Sighting>?, enabled: Boolean): RemoteIdEmergencyOutcome = synchronized(lock) {
        if (devices == null) {
            if (!enabled) clearDialogs()
            return RemoteIdEmergencyOutcome()
        }
        val present = LinkedHashSet<String>()
        val groups = LinkedHashMap<String, Heard>()
        for (device in devices) {
            val id = identity(device) ?: continue
            present.add(id)
            val group = groups.getOrPut(id) { Heard() }
            if (AircraftTrail.declared(device)) group.declared = true
            else if (ended(device)) group.ended = true
            val craft = device.payloadAircraft?.trim().orEmpty()
            if (group.aircraft == null && craft.isNotEmpty()) group.aircraft = craft
            val uas = device.payloadUasId?.trim().orEmpty()
            if (group.uas == null && uas.isNotEmpty()) group.uas = uas
        }
        latched.retainAll(present)
        val fires = ArrayList<RemoteIdEmergencyFire>()
        for ((id, group) in groups) {
            if (group.declared) {
                if (latched.add(id) && enabled) fires += fire(id, group)
            } else if (group.ended) {
                latched.remove(id)
            }
        }
        if (!enabled) {
            clearDialogs()
            return RemoteIdEmergencyOutcome()
        }
        for (fire in fires) offer(fire.body)
        RemoteIdEmergencyOutcome(fires)
    }

    /** Hide the dialog. The next queued declaration opens. The notification stays. */
    fun dismiss() = synchronized(lock) {
        val next = pending.removeFirstOrNull()
        _notice.value = next?.let { Notice(showDialog = true, body = it) }
    }

    private fun offer(body: String) {
        val current = _notice.value
        if (current == null || !current.showDialog) {
            _notice.value = Notice(showDialog = true, body = body)
        } else {
            pending.addLast(body)
        }
    }

    private fun clearDialogs() {
        pending.clear()
        _notice.value = null
    }

    private fun fire(id: String, group: Heard): RemoteIdEmergencyFire {
        val name = group.aircraft ?: group.uas
        val body = if (name.isNullOrBlank()) {
            LanguageText.text("ui.rid.emergency.plain")
        } else {
            LanguageText.text("ui.rid.emergency.voice", mapOf("name" to name))
        }
        return RemoteIdEmergencyFire(
            id = id,
            title = LanguageText.text("ui.rid.emergency.row"),
            body = body,
            // Above 0x4EFFFFFF, which the distance warning fills.
            noteId = 0x4F00_0000 or (id.hashCode() and 0x00FF_FFFF),
        )
    }

    private fun identity(device: Sighting): String? {
        if (device.gone) return null
        if (DroneNear.REMOTE_ID_FLEET !in device.fleetIds) return null
        val uas = device.payloadUasId?.trim().orEmpty()
        return if (uas.isNotEmpty()) "uas:$uas" else "key:${device.key}"
    }

    /** A location that is present and is not an emergency, with no emergency self-ID. */
    private fun ended(device: Sighting): Boolean =
        device.payloadStatus != null &&
            device.payloadStatus != OpenDroneId.STATUS_EMERGENCY &&
            device.payloadEmergencyText == null
}
