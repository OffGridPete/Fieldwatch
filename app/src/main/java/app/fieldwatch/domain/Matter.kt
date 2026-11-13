package app.fieldwatch.domain

/**
 * Matter commissioning service data on UUID 0xFFF6.
 * Opcode 0x00 is the 8-byte commissionable block. Bytes after byte 7 are not product fields.
 * Opcode 0x02 is proximity ranging only when the payload is exactly 21 bytes.
 * The 16-byte ranging id is not stored. A short or unknown opcode is still a decode.
 */
data class MatterFacts(
    val opcode: Int,
    val commissionable: Boolean,
    val byteCount: Int,
    val discriminator: Int? = null,
    val advVersion: Int? = null,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val additionalData: Boolean? = null,
    val extendedAnnouncement: Boolean? = null,
    val ranging: Boolean = false,
) {
    fun headline(): String = when {
        commissionable -> LanguageText.text("matter.commissionable")
        ranging -> LanguageText.text("matter.ranging")
        byteCount == 1 -> LanguageText.text("matter.other.one", mapOf("opcode" to opcodeHex()))
        else -> LanguageText.text(
            "matter.other",
            mapOf("opcode" to opcodeHex(), "n" to byteCount.toString()),
        )
    }

    /** Vendor and product stay in privacy mode. The discriminator does not. */
    fun rows(demo: Boolean): List<Pair<String, String>> {
        if (!commissionable) return emptyList()
        val out = ArrayList<Pair<String, String>>(3)
        vendorId?.let { out += LanguageText.text("matter.vendor") to hex4(it) }
        productId?.let { out += LanguageText.text("matter.product") to hex4(it) }
        if (!demo) discriminator?.let { out += LanguageText.text("matter.discriminator") to it.toString() }
        return out
    }

    fun flagLines(): List<String> {
        if (!commissionable) return emptyList()
        val out = ArrayList<String>(2)
        if (additionalData == true) out += LanguageText.text("matter.flags.additional")
        if (extendedAnnouncement == true) out += LanguageText.text("matter.flags.extended")
        return out
    }

    /** The setup note does not belong on ranging or on an unknown opcode. */
    fun note(): String? = if (commissionable) LanguageText.text("matter.note") else null

    private fun opcodeHex(): String = "%02X".format(opcode)

    private fun hex4(value: Int): String = "0x%04X".format(value)
}

object Matter {
    fun read(records: List<ServiceDataRecord>): MatterFacts? {
        var found: MatterFacts? = null
        for (rec in records) {
            if (!isMatterUuid(rec.uuid)) continue
            val bytes = hexToBytes(rec.dataHex) ?: continue
            readValue(bytes)?.let { found = it }
        }
        return found
    }

    fun readValue(data: ByteArray): MatterFacts? {
        if (data.isEmpty()) return null
        val opcode = data[0].toInt() and 0xFF
        if (opcode == 0x00 && data.size >= 8) {
            val flags = data[7].toInt() and 0xFF
            return MatterFacts(
                opcode = opcode,
                commissionable = true,
                byteCount = data.size,
                discriminator = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0x0F) shl 8),
                advVersion = (data[2].toInt() and 0xF0) shr 4,
                vendorId = u16le(data, 3),
                productId = u16le(data, 5),
                additionalData = flags and 0x01 != 0,
                extendedAnnouncement = flags and 0x02 != 0,
            )
        }
        if (opcode == 0x02 && data.size == 21) {
            return MatterFacts(
                opcode = opcode,
                commissionable = false,
                byteCount = 21,
                ranging = true,
            )
        }
        return MatterFacts(
            opcode = opcode,
            commissionable = false,
            byteCount = data.size,
        )
    }

    fun isMatterUuid(uuid: String): Boolean {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex == "FFF6" || hex == "0000FFF6" -> true
            hex.length == 32 && hex.startsWith("0000FFF6") && hex.endsWith("00001000800000805F9B34FB") -> true
            else -> false
        }
    }

    private fun u16le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun hexToBytes(hex: String): ByteArray? {
        val h = hex.filter { it.isLetterOrDigit() }
        if (h.isEmpty() || h.length % 2 != 0) return null
        return ByteArray(h.length / 2) { i ->
            h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
