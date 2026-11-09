package app.fieldwatch.domain

/**
 * A few vendor-tag OUIs carry a published Wi-Fi element, not a product.
 * 00:50:F2 type 2 is WMM. The IEEE owner of that OUI is Microsoft.
 */
object VendorIeLabel {
    private const val MICROSOFT =
        "Standard element under Microsoft's OUI, not a Microsoft device."
    private const val ALLIANCE =
        "Standard element from the Wi-Fi Alliance, not one product."

    /**
     * Lead sentence for the vendor-tag row.
     * A known element replaces the company name. Anything else keeps it.
     */
    fun lead(ie: VendorIeRecord, orgName: String?, unknown: String, extraPhrase: String): String {
        val named = element(ie)
        if (named != null) return named
        return (orgName ?: unknown) + " " + extraPhrase
    }

    fun element(ie: VendorIeRecord): String? {
        if (ie.type < 0) return null
        val oui = ie.oui.filter { it.isLetterOrDigit() }.uppercase()
        return when (oui) {
            "0050F2" -> microsoft(ie.type, firstByte(ie.dataHex))
            "506F9A" -> alliance(ie.type)
            else -> null
        }
    }

    private fun microsoft(type: Int, subtype: Int?): String? = when (type) {
        1 -> "WPA. $MICROSOFT"
        2 -> when (subtype) {
            0 -> "WMM information (Wi-Fi Multimedia). $MICROSOFT"
            1 -> "WMM parameters (Wi-Fi Multimedia). $MICROSOFT"
            else -> "WMM (Wi-Fi Multimedia). $MICROSOFT"
        }
        4 -> "WPS (Wi-Fi Protected Setup). $MICROSOFT"
        else -> null
    }

    private fun alliance(type: Int): String? = when (type) {
        0x09 -> "Wi-Fi Direct. $ALLIANCE"
        0x0A -> "Miracast (Wi-Fi Display). $ALLIANCE"
        0x10 -> "Hotspot 2.0. $ALLIANCE"
        0x16 -> "MBO-OCE. $ALLIANCE"
        0x1B -> "EasyMesh (Multi-AP). $ALLIANCE"
        0x1C -> "OWE transition. $ALLIANCE"
        else -> null
    }

    private fun firstByte(hex: String): Int? {
        val h = hex.filter { it.isLetterOrDigit() }
        if (h.length < 2) return null
        return h.substring(0, 2).toIntOrNull(16)
    }
}
