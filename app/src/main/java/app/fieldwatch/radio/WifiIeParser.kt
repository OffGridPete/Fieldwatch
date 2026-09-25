package app.fieldwatch.radio

import android.net.wifi.ScanResult
import android.os.Build
import app.fieldwatch.domain.VendorIeRecord
import app.fieldwatch.domain.toHexUpper

object WifiIeParser {
    data class Parsed(
        val rates: String? = null,
        val security: String? = null,
        val vendorIes: List<VendorIeRecord> = emptyList(),
        val channelFromDs: Int? = null,
    )

    /** One raw information element: element id + payload bytes. */
    class Ie(val id: Int, val bytes: ByteArray)

    fun parse(result: ScanResult): Parsed {
        if (Build.VERSION.SDK_INT < 30) {
            return Parsed(security = result.capabilities?.ifBlank { null })
        }
        val ies = runCatching { result.informationElements }.getOrNull() ?: return Parsed(
            security = result.capabilities?.ifBlank { null },
        )
        val raw = ies.mapNotNull { ie ->
            val id = runCatching { ie.id }.getOrDefault(-1)
            val bytes = ieBytes(ie) ?: return@mapNotNull null
            Ie(id, bytes)
        }
        return parseIes(raw, result.capabilities)
    }

    /** Pure decode of a captured IE set — unit tests feed frames without a ScanResult. */
    fun parseIes(ies: List<Ie>, capabilities: String?): Parsed {
        val rates = ArrayList<String>(16)
        val vendor = ArrayList<VendorIeRecord>(4)
        val sec = ArrayList<String>(4)
        var ds: Int? = null
        ies.forEach { ie ->
            val id = ie.id
            val bytes = ie.bytes
            when (id) {
                1, 50 -> rates += decodeRates(bytes)
                3 -> if (bytes.isNotEmpty()) ds = bytes[0].toInt() and 0xFF
                48 -> rsnSummary(bytes)?.let { sec += it }
                221 -> if (bytes.size >= 3) {
                    val oui = "%02X:%02X:%02X".format(
                        bytes[0].toInt() and 0xFF,
                        bytes[1].toInt() and 0xFF,
                        bytes[2].toInt() and 0xFF,
                    )
                    val type = if (bytes.size > 3) bytes[3].toInt() and 0xFF else 0
                    val payload = if (bytes.size > 4) bytes.copyOfRange(4, bytes.size.coerceAtMost(4 + 24)) else ByteArray(0)
                    vendor += VendorIeRecord(oui, type, payload.toHexUpper())
                    if (oui == "00:50:F2" && type == 1) {
                        wpaSummary(bytes)?.let { sec += it }
                    }
                }
            }
        }
        val cap = capabilities.orEmpty()
        if (sec.isEmpty() && cap.isNotBlank()) sec += cap
        return Parsed(
            rates = rates.distinct().joinToString(" ").ifBlank { null },
            security = sec.distinct().joinToString(" · ").ifBlank { cap.ifBlank { null } },
            vendorIes = vendor.distinctBy { it.oui to it.type }.take(12),
            channelFromDs = ds,
        )
    }

    fun wifiStandardLabel(result: ScanResult): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return when (result.wifiStandard) {
            ScanResult.WIFI_STANDARD_UNKNOWN -> null
            ScanResult.WIFI_STANDARD_LEGACY -> "802.11a/b/g"
            ScanResult.WIFI_STANDARD_11N -> "802.11n"
            ScanResult.WIFI_STANDARD_11AC -> "802.11ac"
            ScanResult.WIFI_STANDARD_11AX -> "802.11ax"
            ScanResult.WIFI_STANDARD_11AD -> "802.11ad"
            else -> if (Build.VERSION.SDK_INT >= 33 && result.wifiStandard == ScanResult.WIFI_STANDARD_11BE) {
                "802.11be"
            } else null
        }
    }

    fun channelWidthLabel(result: ScanResult): String? {
        if (Build.VERSION.SDK_INT < 23) return null
        return when (result.channelWidth) {
            ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
            ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
            ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
            ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
            ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
            else -> if (Build.VERSION.SDK_INT >= 33 && result.channelWidth == ScanResult.CHANNEL_WIDTH_320MHZ) {
                "320 MHz"
            } else null
        }
    }

    private fun decodeRates(bytes: ByteArray): List<String> = bytes.map { b ->
        val raw = b.toInt() and 0xFF
        val basic = raw and 0x80 != 0
        val mbps = (raw and 0x7F) / 2.0
        val label = if (mbps == mbps.toInt().toDouble()) "${mbps.toInt()}" else mbps.toString()
        if (basic) "$label*" else label
    }

    private fun rsnSummary(bytes: ByteArray): String? {
        if (bytes.size < 8) return null
        var o = 0
        val ver = u16(bytes, o); o += 2
        if (ver != 1) return "RSN v$ver"
        val group = cipherSuite(bytes, o); o += 4
        if (o + 2 > bytes.size) return "RSN $group"
        val pc = u16(bytes, o); o += 2
        val pairwise = ArrayList<String>(pc)
        repeat(pc) {
            if (o + 4 > bytes.size) return@repeat
            pairwise += cipherSuite(bytes, o); o += 4
        }
        if (o + 2 > bytes.size) return "RSN $group / ${pairwise.joinToString(",")}"
        val ac = u16(bytes, o); o += 2
        val akm = ArrayList<String>(ac)
        repeat(ac) {
            if (o + 4 > bytes.size) return@repeat
            akm += akmSuite(bytes, o); o += 4
        }
        return buildString {
            append("RSN ")
            append(akm.joinToString("/").ifBlank { "AKM?" })
            append(" ")
            append(pairwise.joinToString("/").ifBlank { group })
            if (group.isNotBlank()) append(" (group $group)")
        }
    }

    private fun wpaSummary(bytes: ByteArray): String? {
        if (bytes.size < 10) return "WPA"
        var o = 4
        val ver = u16(bytes, o); o += 2
        if (ver != 1) return "WPA v$ver"
        val group = cipherSuite(bytes, o); o += 4
        if (o + 2 > bytes.size) return "WPA $group"
        val pc = u16(bytes, o); o += 2
        val pairwise = ArrayList<String>(pc)
        repeat(pc) {
            if (o + 4 > bytes.size) return@repeat
            pairwise += cipherSuite(bytes, o); o += 4
        }
        return "WPA ${pairwise.joinToString("/").ifBlank { group }}"
    }

    private fun cipherSuite(bytes: ByteArray, offset: Int): String {
        val (oui, type) = ouiType(bytes, offset) ?: return "?"
        if (oui == "000FAC" || oui == "0050F2") {
            return when (type) {
                0 -> "Group"
                1 -> "WEP-40"
                2 -> "TKIP"
                4 -> "CCMP"
                5 -> "WEP-104"
                6 -> "BIP"
                8 -> "GCMP"
                9 -> "GCMP-256"
                10 -> "CCMP-256"
                else -> "cipher $type"
            }
        }
        return "$oui/$type"
    }

    private fun akmSuite(bytes: ByteArray, offset: Int): String {
        val (oui, type) = ouiType(bytes, offset) ?: return "?"
        if (oui == "000FAC") {
            return when (type) {
                1 -> "802.1X"
                2 -> "PSK"
                3 -> "FT-802.1X"
                4 -> "FT-PSK"
                5 -> "802.1X-SHA256"
                6 -> "PSK-SHA256"
                8 -> "SAE"
                9 -> "FT-SAE"
                11 -> "SUITE-B"
                12 -> "SUITE-B-192"
                18 -> "OWE"
                else -> "AKM $type"
            }
        }
        return "$oui/$type"
    }

    private fun ouiType(bytes: ByteArray, offset: Int): Pair<String, Int>? {
        if (offset + 4 > bytes.size) return null
        val oui = "%02X%02X%02X".format(
            bytes[offset].toInt() and 0xFF,
            bytes[offset + 1].toInt() and 0xFF,
            bytes[offset + 2].toInt() and 0xFF,
        )
        return oui to (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun u16(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ieBytes(ie: ScanResult.InformationElement): ByteArray? {
        val raw = runCatching { ie.bytes }.getOrNull() ?: return null
        val copy = ByteArray(raw.remaining())
        raw.duplicate().get(copy)
        return copy
    }
}
