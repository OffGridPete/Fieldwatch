package app.fieldwatch.domain

import java.util.UUID

object RadioBookmarks {
    const val MAX_NAME = 22

    fun radios(watchlist: List<WatchTarget>): List<WatchTarget> =
        watchlist.filter { it.deviceKey != null }

    fun namedKeys(watchlist: List<WatchTarget>): Set<String> =
        watchlist.mapNotNull { row ->
            val key = row.deviceKey ?: return@mapNotNull null
            if (row.label.isBlank()) null else key
        }.toSet()

    fun watchedFleetIds(watchlist: List<WatchTarget>): Set<String> =
        watchlist.mapNotNull { row ->
            if (row.deviceKey != null) null else row.fleetId
        }.toSet()

    fun alertDeviceKeys(watchlist: List<WatchTarget>): Set<String> =
        watchlist.mapNotNull { row ->
            val key = row.deviceKey ?: return@mapNotNull null
            if (row.alert) key else null
        }.toSet()

    fun withoutRadios(watchlist: List<WatchTarget>): List<WatchTarget> =
        watchlist.filter { it.deviceKey == null }

    fun parseKey(key: String): Pair<RadioKind, String>? {
        val kind = when {
            key.startsWith("WIFI:") -> RadioKind.WIFI
            key.startsWith("BLE:") -> RadioKind.BLE
            else -> return null
        }
        val mac = key.substringAfter(':').trim()
        if (mac.isEmpty()) return null
        return kind to mac
    }

    fun suggestLabel(device: Sighting, signatureNames: List<String> = emptyList()): String {
        val title = device.listTitle(signatureNames).trim()
        if (title.isNotEmpty() && !title.equals(device.mac, ignoreCase = true)) {
            return clip(title)
        }
        return if (device.kind == RadioKind.BLE) "unnamed LE" else clip(device.mac.takeLast(8))
    }

    fun clip(name: String): String = name.trim().take(MAX_NAME).ifBlank { "Radio" }

    fun rename(watchlist: List<WatchTarget>, id: String, name: String): List<WatchTarget> {
        val label = clip(name)
        return watchlist.map { row ->
            if (row.id == id && row.deviceKey != null) row.copy(label = label) else row
        }
    }

    fun remove(watchlist: List<WatchTarget>, id: String): List<WatchTarget> =
        watchlist.filterNot { it.id == id && it.deviceKey != null }

    fun setAlert(watchlist: List<WatchTarget>, id: String, on: Boolean): List<WatchTarget> =
        watchlist.map { row ->
            if (row.id == id && row.deviceKey != null) row.copy(alert = on) else row
        }

    fun toggleAlert(watchlist: List<WatchTarget>, deviceKey: String, suggest: String): List<WatchTarget> {
        val i = watchlist.indexOfFirst { it.deviceKey == deviceKey }
        if (i < 0) {
            return watchlist + WatchTarget(
                id = UUID.randomUUID().toString(),
                deviceKey = deviceKey,
                label = clip(suggest),
                alert = true,
            )
        }
        val row = watchlist[i]
        return watchlist.mapIndexed { idx, it ->
            if (idx == i) it.copy(alert = !row.alert) else it
        }
    }

    fun upsertName(
        watchlist: List<WatchTarget>,
        deviceKey: String,
        name: String,
        alertIfNew: Boolean = false,
    ): List<WatchTarget> {
        val label = clip(name)
        val i = watchlist.indexOfFirst { it.deviceKey == deviceKey }
        if (i >= 0) {
            return watchlist.mapIndexed { idx, row ->
                if (idx == i) row.copy(label = label) else row
            }
        }
        return watchlist + WatchTarget(
            id = UUID.randomUUID().toString(),
            deviceKey = deviceKey,
            label = label,
            alert = alertIfNew,
        )
    }
}
