package app.fieldwatch.domain

import java.net.URLEncoder

/**
 * Radio-detail button that opens a GitHub issue draft. The page decides with
 * [eligible]. Nothing here contacts the network.
 */
internal object CatalogRequest {
    private const val ISSUE = "https://github.com/OffGridPete/Fieldwatch/issues/new"

    fun eligible(device: Sighting, peers: List<Sighting>): Boolean {
        if (device.kind != RadioKind.BLE) return false
        if (device.fleetIds.isNotEmpty()) return false
        if (!productName(device.name, device.mac)) return false
        return addresses(device.name, peers).size >= 2
    }

    fun issueUrl(device: Sighting, peers: List<Sighting>): String? {
        if (!eligible(device, peers)) return null
        val name = device.name.trim()
        val title = "Catalog request: $name"
        val body = body(name, peers)
        return ISSUE +
            "?title=" + encode(title) +
            "&body=" + encode(body)
    }

    internal fun productName(name: String, mac: String): Boolean {
        val n = name.trim()
        if (n.length !in 4..32) return false
        if (n.startsWith("<")) return false
        if (n.equals(mac, ignoreCase = true)) return false
        if (SignatureCandidates.isHouseLikeName(n)) return false
        if (SignatureCandidates.reservedNameHead(n)) return false
        if (hexTail(n) || hexBlob(n)) return false
        return modelShape(n)
    }

    private fun body(name: String, peers: List<Sighting>): String {
        val same = peers.filter { it.kind == RadioKind.BLE && it.name.trim().equals(name, ignoreCase = true) }
        val lines = ArrayList<String>(8)
        lines += "Catalog request from Fieldwatch. A factory-style Bluetooth name heard on ${addresses(name, peers).size} addresses. Not a finished signature."
        lines += ""
        lines += "Name: $name"
        lines += "Radio: Bluetooth"
        lines += "Addresses: ${addresses(name, peers).size}"
        val prefixes = same
            .filter { !it.randomized }
            .map { MacUtil.prefixBytes(it.mac) }
            .filter { it.count { ch -> ch == ':' } == 2 }
            .distinct()
        if (prefixes.isNotEmpty()) lines += "Vendor prefix: ${prefixes.joinToString(", ")}"
        val companies = same.mapNotNull { it.manufacturerId }.distinct().take(3)
        if (companies.isNotEmpty()) {
            lines += "Company: ${companies.joinToString(", ") { "0x%04X".format(it) }}"
        }
        val services = same
            .flatMap { it.serviceUuids }
            .mapNotNull { SignatureCandidates.usefulServiceShort(it) }
            .distinct()
            .take(4)
        if (services.isNotEmpty()) lines += "Services: ${services.joinToString(", ")}"
        lines += ""
        lines += "The address suffix, this phone's location, and the raw advertisement are not included."
        return lines.joinToString("\n")
    }

    private fun addresses(name: String, peers: List<Sighting>): Set<String> =
        peers
            .filter { it.kind == RadioKind.BLE && it.name.trim().equals(name, ignoreCase = true) }
            .map { MacUtil.normalize(it.mac) }
            .filter { it.isNotBlank() }
            .toSet()

    /** A module label such as ET25SE_BLE_ plus a long hex tail. */
    private fun hexTail(name: String): Boolean {
        val sep = maxOf(name.lastIndexOf('-'), name.lastIndexOf('_'))
        if (sep < 0 || sep >= name.lastIndex) return false
        val tail = name.substring(sep + 1).filter { it.isLetterOrDigit() }
        if (tail.length < 4) return false
        return tail.all { it.digitToIntOrNull(16) != null }
    }

    private fun hexBlob(name: String): Boolean {
        val compact = name.filter { it.isLetterOrDigit() }
        if (compact.length < 8) return false
        return compact.all { it.digitToIntOrNull(16) != null }
    }

    private fun modelShape(name: String): Boolean {
        val hasDigit = name.any { it.isDigit() }
        val hasSep = '-' in name || '_' in name
        if (!hasDigit && !hasSep) return false
        if (name.count { it.isWhitespace() } > 2) return false
        val compact = name.filter { it.isLetterOrDigit() }
        if (compact.length < 4) return false
        if (hasDigit) return true
        val letters = name.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        val upper = letters.count { it.isUpperCase() }
        return upper * 2 >= letters.length
    }

    private fun encode(text: String): String =
        URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
}
