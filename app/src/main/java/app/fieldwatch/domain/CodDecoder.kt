package app.fieldwatch.domain

/** Bluetooth Class of Device (Assigned Numbers). 24-bit CoD. */
object CodDecoder {
    data class Decoded(
        val major: String,
        val minor: String,
        val services: List<String>,
        val raw: Int,
    ) {
        fun summary(): String = buildString {
            append(major)
            if (minor.isNotBlank() && minor != "Uncategorized") {
                append(" / ")
                append(minor)
            }
            if (services.isNotEmpty()) {
                append(" · ")
                append(services.joinToString(", "))
            }
        }
    }

    fun decode(cod: Int): Decoded {
        val format = cod and 0x3
        val minorBits = (cod shr 2) and 0x3F
        val majorBits = (cod shr 8) and 0x1F
        val serviceBits = (cod shr 13) and 0x7FF
        val major = majorName(majorBits)
        val minor = if (format != 0) "format $format" else minorName(majorBits, minorBits)
        return Decoded(major, minor, serviceNames(serviceBits), cod and 0xFFFFFF)
    }

    fun decodeOrNull(cod: Int?): Decoded? {
        if (cod == null || cod == 0) return null
        return decode(cod)
    }

    private fun majorName(major: Int): String = when (major) {
        0x00 -> "Miscellaneous"
        0x01 -> "Computer"
        0x02 -> "Phone"
        0x03 -> "LAN / Network AP"
        0x04 -> "Audio / Video"
        0x05 -> "Peripheral"
        0x06 -> "Imaging"
        0x07 -> "Wearable"
        0x08 -> "Toy"
        0x09 -> "Health"
        0x1F -> "Uncategorized"
        else -> "Major 0x%02X".format(major)
    }

    private fun minorName(major: Int, minor: Int): String = when (major) {
        0x01 -> when (minor) {
            0x00 -> "Uncategorized"
            0x01 -> "Desktop"
            0x02 -> "Server"
            0x03 -> "Laptop"
            0x04 -> "Handheld PC/PDA"
            0x05 -> "Palm-size PDA"
            0x06 -> "Wearable computer"
            0x07 -> "Tablet"
            else -> "Computer 0x%02X".format(minor)
        }
        0x02 -> when (minor) {
            0x00 -> "Uncategorized"
            0x01 -> "Cellular"
            0x02 -> "Cordless"
            0x03 -> "Smartphone"
            0x04 -> "Wired modem / voice gateway"
            0x05 -> "Common ISDN access"
            else -> "Phone 0x%02X".format(minor)
        }
        0x03 -> when ((minor shr 3) and 0x7) {
            0 -> "Fully available"
            1 -> "1–17% utilized"
            2 -> "17–33% utilized"
            3 -> "33–50% utilized"
            4 -> "50–67% utilized"
            5 -> "67–83% utilized"
            6 -> "83–99% utilized"
            else -> "No service available"
        }
        0x04 -> when (minor) {
            0x00 -> "Uncategorized"
            0x01 -> "Wearable headset"
            0x02 -> "Hands-free"
            0x04 -> "Microphone"
            0x05 -> "Loudspeaker"
            0x06 -> "Headphones"
            0x07 -> "Portable audio"
            0x08 -> "Car audio"
            0x09 -> "Set-top box"
            0x0A -> "HiFi audio"
            0x0B -> "VCR"
            0x0C -> "Video camera"
            0x0D -> "Camcorder"
            0x0E -> "Video monitor"
            0x0F -> "Video display and loudspeaker"
            0x10 -> "Video conferencing"
            0x12 -> "Gaming / toy"
            else -> "A/V 0x%02X".format(minor)
        }
        0x05 -> {
            val sense = minor and 0x0F
            val hid = (minor shr 4) and 0x3
            val kind = when (sense) {
                0x00 -> "Uncategorized"
                0x01 -> "Joystick"
                0x02 -> "Gamepad"
                0x03 -> "Remote control"
                0x04 -> "Sensing device"
                0x05 -> "Digitizer tablet"
                0x06 -> "Card reader"
                0x07 -> "Digital pen"
                0x08 -> "Handheld scanner"
                0x09 -> "Handheld gestural input"
                else -> "Peripheral 0x%X".format(sense)
            }
            val extra = when (hid) {
                1 -> "keyboard"
                2 -> "pointing"
                3 -> "keyboard/pointing"
                else -> null
            }
            if (extra == null) kind else if (sense == 0) extra.replaceFirstChar { it.uppercase() } else "$kind + $extra"
        }
        0x06 -> buildList {
            if (minor and 0x08 != 0) add("Display")
            if (minor and 0x04 != 0) add("Camera")
            if (minor and 0x02 != 0) add("Scanner")
            if (minor and 0x01 != 0) add("Printer")
        }.joinToString(" + ").ifBlank { "Uncategorized" }
        0x07 -> when (minor) {
            0x01 -> "Wristwatch"
            0x02 -> "Pager"
            0x03 -> "Jacket"
            0x04 -> "Helmet"
            0x05 -> "Glasses"
            else -> "Wearable 0x%02X".format(minor)
        }
        0x08 -> when (minor) {
            0x01 -> "Robot"
            0x02 -> "Vehicle"
            0x03 -> "Doll / action figure"
            0x04 -> "Controller"
            0x05 -> "Game"
            else -> "Toy 0x%02X".format(minor)
        }
        0x09 -> when (minor) {
            0x01 -> "Blood pressure monitor"
            0x02 -> "Thermometer"
            0x03 -> "Weighing scale"
            0x04 -> "Glucose meter"
            0x05 -> "Pulse oximeter"
            0x06 -> "Heart / pulse rate monitor"
            0x07 -> "Health data display"
            0x08 -> "Step counter"
            0x09 -> "Body composition analyzer"
            0x0A -> "Peak flow monitor"
            0x0B -> "Medication monitor"
            0x0C -> "Knee prosthesis"
            0x0D -> "Ankle prosthesis"
            0x0E -> "Generic health manager"
            0x0F -> "Personal mobility device"
            else -> "Health 0x%02X".format(minor)
        }
        else -> if (minor == 0) "" else "0x%02X".format(minor)
    }

    private fun serviceNames(bits: Int): List<String> = buildList {
        if (bits and 0x001 != 0) add("Limited Discoverable")
        if (bits and 0x008 != 0) add("Positioning")
        if (bits and 0x010 != 0) add("Networking")
        if (bits and 0x020 != 0) add("Rendering")
        if (bits and 0x040 != 0) add("Capturing")
        if (bits and 0x080 != 0) add("Object Transfer")
        if (bits and 0x100 != 0) add("Audio")
        if (bits and 0x200 != 0) add("Telephony")
        if (bits and 0x400 != 0) add("Information")
    }
}
