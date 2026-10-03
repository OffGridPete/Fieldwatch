package app.fieldwatch.domain

import java.util.UUID

object RadioBookmarks {
    const val MAX_NAME = 22
    const val MAX_NOTES = 280

    fun radios(watchlist: List<WatchTarget>): List<WatchTarget> =
        watchlist.filter { it.deviceKey != null }

    fun namedKeys(watchlist: List<WatchTarget>): Set<String> = labels(watchlist).keys

    /** Custom names keyed KIND:MAC. Blank labels omitted. */
    fun labels(watchlist: List<WatchTarget>): Map<String, String> =
        watchlist.mapNotNull { row ->
            val key = row.deviceKey ?: return@mapNotNull null
            val label = row.label.trim()
            if (label.isEmpty()) null else key to label
        }.toMap()

    fun notes(watchlist: List<WatchTarget>): Map<String, String> =
        watchlist.mapNotNull { row ->
            val key = row.deviceKey ?: return@mapNotNull null
            val note = row.observerNotes.trim()
            if (note.isEmpty()) null else key to note
        }.toMap()

    fun clipNotes(text: String): String = text.trim().take(MAX_NOTES)

    fun observerNotesHint(): String =
        "Pinned to this MAC. Shows on Debrief, Compare, Path, and AI Export. Does not turn Alert on."

    fun watchedFleetIds(watchlist: List<WatchTarget>): Set<String> =
        watchlist.mapNotNull { row ->
            if (row.deviceKey != null) null else row.fleetId
        }.toSet()

    fun alertDeviceKeys(watchlist: List<WatchTarget>): Set<String> =
        watchlist.mapNotNull { row ->
            val key = row.deviceKey ?: return@mapNotNull null
            if (row.alert) key else null
        }.toSet()

    /** Device keys marked mine. Fleet rows are not included. */
    fun mineKeys(watchlist: List<WatchTarget>): Set<String> =
        watchlist.mapNotNull { row ->
            val key = row.deviceKey ?: return@mapNotNull null
            if (row.mine) key else null
        }.toSet()

    /**
     * Path-list note for a radio that is already plotted.
     * Mine does not add a dot. A bookmark still gates the observer caption.
     */
    fun pathNote(bookmarked: Boolean, observerNotes: String, mine: Boolean): String {
        val obs = if (bookmarked) observerNotes.trim() else ""
        return when {
            obs.isNotEmpty() && mine -> "$obs\nMarked mine"
            mine -> "Marked mine"
            else -> obs
        }
    }

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

    /**
     * BLE privacy addresses rotate; a name would stick to a dead key.
     * Wi-Fi local-bit BSSIDs (guest / mesh / vehicle APs) usually stay put.
     */
    fun canSetCustomName(device: Sighting): Boolean {
        if (device.kind == RadioKind.WIFI) return true
        if (device.facts.addressType.equals("Random", ignoreCase = true)) return false
        return !device.randomized
    }

    fun customNameHint(device: Sighting): String {
        val base = "Shows on Live. Bookmark (top-right) is the alert; this does not turn it on."
        return if (device.kind == RadioKind.WIFI && device.randomized) {
            "$base Pinned to this BSSID. Vehicle, mesh, and guest APs often keep a locally administered address."
        } else {
            base
        }
    }

    fun rename(watchlist: List<WatchTarget>, id: String, name: String): List<WatchTarget> {
        val label = clip(name)
        return watchlist.map { row ->
            if (row.id == id && row.deviceKey != null) row.copy(label = label) else row
        }
    }

    fun setNotes(watchlist: List<WatchTarget>, id: String, notes: String): List<WatchTarget> {
        val note = clipNotes(notes)
        return watchlist.map { row ->
            if (row.id == id && row.deviceKey != null) row.copy(observerNotes = note) else row
        }
    }

    fun updateNamedRadio(
        watchlist: List<WatchTarget>,
        id: String,
        name: String,
        notes: String,
    ): List<WatchTarget> = setNotes(rename(watchlist, id, name), id, notes)

    fun remove(watchlist: List<WatchTarget>, id: String): List<WatchTarget> =
        watchlist.filterNot { it.id == id && it.deviceKey != null }

    fun setAlert(watchlist: List<WatchTarget>, id: String, on: Boolean): List<WatchTarget> =
        watchlist.map { row ->
            if (row.id == id && row.deviceKey != null) row.copy(alert = on) else row
        }

    /**
     * Mine on a new row writes the suggested name and leaves Alert off.
     * An existing name is kept. A blank name is filled. Turning Mine off keeps the name.
     */
    fun setMine(
        watchlist: List<WatchTarget>,
        deviceKey: String,
        on: Boolean,
        suggestLabel: String,
    ): List<WatchTarget> {
        val i = watchlist.indexOfFirst { it.deviceKey == deviceKey }
        if (!on) {
            if (i < 0) return watchlist
            return watchlist.mapIndexed { idx, row ->
                if (idx == i) row.copy(mine = false) else row
            }
        }
        if (i < 0) {
            return watchlist + WatchTarget(
                id = UUID.randomUUID().toString(),
                deviceKey = deviceKey,
                label = clip(suggestLabel),
                alert = false,
                mine = true,
            )
        }
        val row = watchlist[i]
        val label = if (row.label.trim().isEmpty()) clip(suggestLabel) else row.label
        return watchlist.mapIndexed { idx, it ->
            if (idx == i) it.copy(label = label, mine = true) else it
        }
    }

    /** Named-radios row. A blank label is filled from the key when Mine turns on. */
    fun setMineOnRow(watchlist: List<WatchTarget>, id: String, on: Boolean): List<WatchTarget> {
        val row = watchlist.firstOrNull { it.id == id && it.deviceKey != null } ?: return watchlist
        val key = row.deviceKey ?: return watchlist
        val suggest = row.label.trim().ifBlank {
            val mac = key.substringAfter(':')
            if (key.startsWith("BLE:")) "unnamed LE" else mac.takeLast(8)
        }
        return setMine(watchlist, key, on, suggest)
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

    fun upsertNotes(
        watchlist: List<WatchTarget>,
        deviceKey: String,
        notes: String,
        suggestLabel: String,
    ): List<WatchTarget> {
        val note = clipNotes(notes)
        val i = watchlist.indexOfFirst { it.deviceKey == deviceKey }
        if (i >= 0) {
            return watchlist.mapIndexed { idx, row ->
                if (idx == i) row.copy(observerNotes = note) else row
            }
        }
        if (note.isEmpty()) return watchlist
        return watchlist + WatchTarget(
            id = UUID.randomUUID().toString(),
            deviceKey = deviceKey,
            label = clip(suggestLabel),
            alert = false,
            observerNotes = note,
        )
    }
}
