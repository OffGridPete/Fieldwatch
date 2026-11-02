package app.fieldwatch.domain

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Words said instead of one signature when the advertised network name matches.
 * One row covers every address. It is not a catalog change and not a named radio.
 */
@Serializable
data class SpokenName(
    val id: String,
    val fleetId: String,
    val networkName: String,
    val label: String,
)

object SpokenNames {
    const val MAX_LABEL = 32
    const val MAX_NETWORK = 64

    /**
     * Advertised SSID or BLE name. Blank, the radio's own address, and a name
     * longer than [MAX_NETWORK] cannot have a row. Matching stays exact.
     */
    fun networkOf(name: String, mac: String): String? {
        val net = name.trim()
        if (net.isEmpty() || net.length > MAX_NETWORK) return null
        if (net.equals(mac.trim(), ignoreCase = true)) return null
        return net
    }

    /** Exact network name, ignoring case. A longer or shorter name does not match. */
    fun find(rows: List<SpokenName>, fleetId: String, network: String): SpokenName? {
        val net = network.trim()
        if (fleetId.isBlank() || net.isEmpty()) return null
        return rows.firstOrNull { row ->
            row.fleetId == fleetId && row.networkName.trim().equals(net, ignoreCase = true)
        }
    }

    /** Words to say instead of the signature, or null when this pair has no row. */
    fun words(rows: List<SpokenName>, fleetId: String, name: String, mac: String): String? {
        val net = networkOf(name, mac) ?: return null
        val row = find(rows, fleetId, net) ?: return null
        val said = speakableWatchName(row.label.take(MAX_LABEL))
        if (said.isBlank() || said.equals("unmatched", ignoreCase = true)) return null
        return said
    }

    /**
     * Fleet a signature announcement would use: the first watched fleet on this
     * radio, otherwise the first matched fleet. A named radio is a different sentence.
     */
    fun voiceFleet(fleetIds: Set<String>, watchlist: List<WatchTarget>): String? {
        val watched = watchlist.asSequence()
            .filter { it.deviceKey == null }
            .mapNotNull { it.fleetId }
            .firstOrNull { it in fleetIds }
        return watched ?: fleetIds.firstOrNull()
    }

    /** A fleet that already has a row for this network, otherwise the first fleet. */
    fun preferredFleet(
        fleetIds: Iterable<String>,
        rows: List<SpokenName>,
        network: String?,
    ): String? {
        if (!network.isNullOrBlank()) {
            fleetIds.firstOrNull { find(rows, it, network) != null }?.let { return it }
        }
        return fleetIds.firstOrNull()
    }

    /**
     * Row the detail line shows. The fleet voice would use wins. Another saved
     * row on this radio is shown when that fleet has none.
     */
    fun announced(
        rows: List<SpokenName>,
        fleetIds: Iterable<String>,
        network: String?,
        voiceFleet: String?,
    ): SpokenName? {
        if (network.isNullOrBlank()) return null
        if (voiceFleet != null) find(rows, voiceFleet, network)?.let { return it }
        return fleetIds.firstNotNullOfOrNull { find(rows, it, network) }
    }

    fun save(
        rows: List<SpokenName>,
        fleetId: String,
        networkName: String,
        label: String,
        id: String? = null,
    ): List<SpokenName> {
        val net = networkName.trim()
        val words = label.trim().take(MAX_LABEL)
        if (fleetId.isBlank() || net.isEmpty() || net.length > MAX_NETWORK || words.isEmpty()) return rows
        val same = rows.firstOrNull { row ->
            row.fleetId == fleetId && row.networkName.trim().equals(net, ignoreCase = true)
        }
        val keptId = same?.id ?: id?.takeIf { candidate -> rows.any { it.id == candidate } } ?: newId()
        val next = SpokenName(keptId, fleetId, net, words)
        return rows.filterNot { row ->
            row.id == keptId ||
                row.id == id ||
                (row.fleetId == fleetId && row.networkName.trim().equals(net, ignoreCase = true))
        } + next
    }

    fun remove(rows: List<SpokenName>, fleetId: String, networkName: String): List<SpokenName> {
        val net = networkName.trim()
        if (fleetId.isBlank() || net.isEmpty()) return rows
        return rows.filterNot { row ->
            row.fleetId == fleetId && row.networkName.trim().equals(net, ignoreCase = true)
        }
    }

    fun delete(rows: List<SpokenName>, id: String): List<SpokenName> =
        rows.filterNot { it.id == id }

    private fun newId(): String = "sn-" + UUID.randomUUID()
}
