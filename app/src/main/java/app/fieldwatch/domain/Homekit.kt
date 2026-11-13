package app.fieldwatch.domain

/**
 * Apple Continuity type 0x06, the bytes after the type and the length byte.
 * Bit 0 set means the accessory says it is not paired. Other flag bits are ignored.
 * Category names are the HAP Accessory Development Kit names. Value 16 is obsolete
 * in HAP R10; the on-screen name stays Range extender. Unknown values, including
 * 18 and 24–27, stay "category N".
 */
data class HomekitFacts(
    val paired: Boolean,
    val category: Int,
    val deviceId: String,
    val stateNumber: Int?,
    val configNumber: Int?,
    val version: Int?,
    val setupHash: Boolean,
) {
    val categoryName: String get() = Homekit.name(category)

    fun rows(demo: Boolean): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(6)
        out += LanguageText.text("hk.category") to categoryName
        out += LanguageText.text("hk.paired") to LanguageText.text(
            if (paired) "hk.paired.yes" else "hk.paired.no",
        )
        out += LanguageText.text("hk.deviceid") to MacUtil.screenMac(deviceId, demo)
        stateNumber?.let { out += LanguageText.text("hk.state") to it.toString() }
        configNumber?.let { out += LanguageText.text("hk.config") to it.toString() }
        version?.let { out += LanguageText.text("hk.version") to it.toString() }
        return out
    }

    /** The four hash bytes are not shown. This line only says they were in the advertisement. */
    fun setupLine(): String? = if (setupHash) LanguageText.text("hk.setup") else null

    fun note(): String = LanguageText.text("hk.note")
}

object Homekit {
    fun readValue(data: ByteArray): HomekitFacts? {
        if (data.size < 9) return null
        val paired = (data[0].toInt() and 0x01) == 0
        val deviceId = (1..6).joinToString(":") { i ->
            "%02X".format(data[i].toInt() and 0xFF)
        }
        val category = u16le(data, 7)
        val state = if (data.size >= 11) u16le(data, 9) else null
        val config = if (data.size >= 12) data[11].toInt() and 0xFF else null
        val version = if (data.size >= 13) data[12].toInt() and 0xFF else null
        return HomekitFacts(
            paired = paired,
            category = category,
            deviceId = deviceId,
            stateNumber = state,
            configNumber = config,
            version = version,
            setupHash = data.size >= 17,
        )
    }

    fun name(category: Int): String = when (category) {
        0 -> "Bridged accessory"
        1 -> "Other"
        2 -> "Bridge"
        3 -> "Fan"
        4 -> "Garage door opener"
        5 -> "Lighting"
        6 -> "Lock"
        7 -> "Outlet"
        8 -> "Switch"
        9 -> "Thermostat"
        10 -> "Sensor"
        11 -> "Security system"
        12 -> "Door"
        13 -> "Window"
        14 -> "Window covering"
        15 -> "Programmable switch"
        16 -> "Range extender"
        17 -> "IP camera"
        19 -> "Air purifier"
        20 -> "Heater"
        21 -> "Air conditioner"
        22 -> "Humidifier"
        23 -> "Dehumidifier"
        28 -> "Sprinkler"
        29 -> "Faucet"
        30 -> "Shower system"
        else -> LanguageText.text("hk.category.n", mapOf("n" to category.toString()))
    }

    private fun u16le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
}
