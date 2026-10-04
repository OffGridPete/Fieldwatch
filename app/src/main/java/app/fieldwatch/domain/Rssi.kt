package app.fieldwatch.domain

import app.fieldwatch.i18n.localized

/**
 * BLE/Wi-Fi RSSI as this phone received it (dBm). Bluetooth uses 127 for
 * “not available”; that is not transmit power and not a real hear.
 */
object Rssi {
    fun measured(rssi: Int): Boolean = rssi in -127..126

    fun sessionRange(min: Int, max: Int, history: List<RssiSample> = emptyList()): String {
        val vals = ArrayList<Int>(history.size + 2)
        if (measured(min)) vals += min
        if (measured(max)) vals += max
        for (s in history) if (measured(s.rssi)) vals += s.rssi
        if (vals.isEmpty()) return localized("device_detail_text_not_available", "Not available")
        val lo = vals.min()
        val hi = vals.max()
        return if (lo == hi) "$lo dBm" else localized("rssi_session_range", "%1\$s to %2\$s dBm", lo, hi)
    }

    fun lastMeasured(rssi: Int, history: List<RssiSample>): Int? {
        if (measured(rssi)) return rssi
        return history.asReversed().firstOrNull { measured(it.rssi) }?.rssi
    }
}
