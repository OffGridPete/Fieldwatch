package app.fieldwatch.domain

/**
 * Amazon Sidewalk beacon after company 0x0171.
 * Byte 0 is the application ID. Byte 1 is the device state, least-significant bit first.
 * Bytes after that are identifiers and are not stored.
 * A payload shorter than 2 bytes is not a decode and does not clear a previous one.
 */
data class SidewalkFacts(
    val applicationId: Int,
    val mode: SidewalkMode,
    val battery: SidewalkBattery,
    val beacon: SidewalkBeacon?,
    val protocolBle: Boolean,
) {
    fun headline(): String = when (beacon) {
        SidewalkBeacon.REGISTRATION -> LanguageText.text("sw.type.registration")
        SidewalkBeacon.TIME_SYNC -> LanguageText.text("sw.type.timesync")
        SidewalkBeacon.CONNECTION_REQUEST -> LanguageText.text("sw.type.connect")
        SidewalkBeacon.PRESENCE -> LanguageText.text("sw.type.presence")
        null -> LanguageText.text("sw.app", mapOf("id" to "%02X".format(applicationId)))
    }

    fun modeLine(): String = when (mode) {
        SidewalkMode.NORMAL -> LanguageText.text("sw.mode.normal")
        SidewalkMode.REGISTRATION -> LanguageText.text("sw.mode.registration")
        SidewalkMode.CONNECTION_REQUEST -> LanguageText.text("sw.mode.connect")
        SidewalkMode.RESERVED -> LanguageText.text("sw.mode.reserved")
    }

    fun batteryRow(): Pair<String, String> =
        LanguageText.text("sw.battery") to when (battery) {
            SidewalkBattery.CRITICAL -> LanguageText.text("sw.battery.critical")
            SidewalkBattery.LOW -> LanguageText.text("sw.battery.low")
            SidewalkBattery.NORMAL -> LanguageText.text("sw.battery.normal")
            SidewalkBattery.RESERVED -> LanguageText.text("sw.battery.reserved")
        }

    /** Shown only when the protocol bit is not BLE. The other fields still decode. */
    fun protocolLine(): String? =
        if (protocolBle) null else LanguageText.text("sw.protocol")

    fun note(): String = LanguageText.text("sw.note")

    /** The registration sentence does not belong on a normal or presence beacon. */
    fun registrationNote(): String? =
        if (mode == SidewalkMode.REGISTRATION) LanguageText.text("sw.note.reg") else null
}

enum class SidewalkMode { NORMAL, REGISTRATION, CONNECTION_REQUEST, RESERVED }

enum class SidewalkBattery { CRITICAL, LOW, NORMAL, RESERVED }

enum class SidewalkBeacon { REGISTRATION, TIME_SYNC, CONNECTION_REQUEST, PRESENCE }

object Sidewalk {
    /**
     * Later successful 0171 payload wins. A short payload does not clear an earlier one.
     * No AMA UUID means this packet is not a Sidewalk beacon.
     */
    fun read(records: List<MfgRecord>, serviceUuids: List<String>): SidewalkFacts? {
        if (serviceUuids.none { isAmaUuid(it) }) return null
        var found: SidewalkFacts? = null
        for (rec in records) {
            if (rec.companyId != 0x0171) continue
            val bytes = hexToBytes(rec.dataHex) ?: continue
            readValue(bytes)?.let { found = it }
        }
        return found
    }

    fun readValue(data: ByteArray): SidewalkFacts? {
        if (data.size < 2) return null
        val applicationId = data[0].toInt() and 0xFF
        val state = data[1].toInt() and 0xFF
        val mode = when ((state shr 1) and 0x03) {
            0 -> SidewalkMode.NORMAL
            1 -> SidewalkMode.REGISTRATION
            2 -> SidewalkMode.CONNECTION_REQUEST
            else -> SidewalkMode.RESERVED
        }
        val battery = when ((state shr 3) and 0x03) {
            0 -> SidewalkBattery.CRITICAL
            1 -> SidewalkBattery.LOW
            2 -> SidewalkBattery.NORMAL
            else -> SidewalkBattery.RESERVED
        }
        val beacon = when {
            applicationId == 0x04 && mode == SidewalkMode.REGISTRATION -> SidewalkBeacon.REGISTRATION
            applicationId == 0x03 && mode == SidewalkMode.NORMAL -> SidewalkBeacon.TIME_SYNC
            applicationId == 0x06 && mode == SidewalkMode.CONNECTION_REQUEST ->
                SidewalkBeacon.CONNECTION_REQUEST
            applicationId == 0x21 && mode == SidewalkMode.NORMAL -> SidewalkBeacon.PRESENCE
            else -> null
        }
        return SidewalkFacts(
            applicationId = applicationId,
            mode = mode,
            battery = battery,
            beacon = beacon,
            protocolBle = (state and 0x01) == 1,
        )
    }

    fun isAmaUuid(uuid: String): Boolean = "03FE" in uuidAliases(uuid)

    private fun hexToBytes(hex: String): ByteArray? {
        val h = hex.filter { it.isLetterOrDigit() }
        if (h.isEmpty() || h.length % 2 != 0) return null
        return ByteArray(h.length / 2) { i ->
            h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
