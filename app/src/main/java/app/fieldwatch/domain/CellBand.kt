package app.fieldwatch.domain

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Band and downlink frequency for a channel number. 3GPP TS 36.101,
 * TS 38.104, and the GSM 850 / 900 / 1800 / 1900 channel plans.
 * Several bands that share one frequency are named together. A 2G
 * channel that is 1800 in one plan and 1900 in the other lists both.
 */
object CellBand {
    fun label(gen: CellGen, channel: Int): String? {
        val hits = when (gen) {
            CellGen.GSM -> gsm(channel)
            CellGen.WCDMA, CellGen.TDSCDMA -> wcdma(channel)
            CellGen.LTE -> lte(channel)
            CellGen.NR -> nr(channel)
        }
        return format(hits)
    }

    private data class Hit(val name: String, val freq: Double)

    private fun format(hits: List<Hit>): String? {
        if (hits.isEmpty()) return null
        val freqs = hits.map { mhz(it.freq) }.distinct()
        return if (freqs.size == 1) {
            "${names(hits.map { it.name })} · ${freqs.single()} MHz"
        } else {
            names(hits.map { "${it.name} · ${mhz(it.freq)} MHz" })
        }
    }

    private fun names(parts: List<String>): String = when (parts.size) {
        1 -> parts[0]
        2 -> "${parts[0]} or ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + ", or " + parts.last()
    }

    private data class Lte(val band: Int, val start: Int, val end: Int, val low: Double, val offs: Int)

    private fun lte(channel: Int): List<Hit> {
        val row = LTE.firstOrNull { channel in it.start..it.end } ?: return emptyList()
        return listOf(Hit(row.band.toString(), row.low + 0.1 * (channel - row.offs)))
    }

    private fun nr(channel: Int): List<Hit> {
        val freq = nrMhz(channel) ?: return emptyList()
        return NR.filter { freq >= it.low && freq < it.high }.map { Hit("n${it.band}", freq) }
    }

    private fun nrMhz(channel: Int): Double? = when {
        channel < 0 || channel > 3_279_165 -> null
        channel < 600_000 -> channel * 0.005
        channel <= 2_016_666 -> 3000.0 + (channel - 600_000) * 0.015
        else -> 24250.08 + (channel - 2_016_667) * 0.060
    }

    private fun gsm(channel: Int): List<Hit> = when (channel) {
        in 128..251 -> listOf(Hit("850", 869.2 + 0.2 * (channel - 128)))
        in 1..124 -> listOf(Hit("900", 935.0 + 0.2 * channel))
        in 975..1023 -> listOf(Hit("900", 935.0 + 0.2 * (channel - 1024)))
        in 512..810 -> listOf(
            Hit("1800", 1805.2 + 0.2 * (channel - 512)),
            Hit("1900", 1930.2 + 0.2 * (channel - 512)),
        )
        in 811..885 -> listOf(Hit("1800", 1805.2 + 0.2 * (channel - 512)))
        else -> emptyList()
    }

    /** Bands whose downlink channel number is five times the frequency in MHz. */
    private fun wcdma(channel: Int): List<Hit> {
        if (channel <= 0) return emptyList()
        val freq = channel * 0.2
        return WCDMA.filter { channel in it.start..it.end && freq >= it.low && freq < it.high }
            .map { Hit(it.band.toString(), freq) }
    }

    private fun mhz(freq: Double): String {
        val tenths = (freq * 10.0).roundToInt()
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0) whole.toString() else String.format(Locale.US, "%d.%d", whole, frac)
    }

    private data class Span(val band: Int, val start: Int, val end: Int, val low: Double, val high: Double)

    private val WCDMA = listOf(
        Span(1, 10562, 10838, 2110.0, 2170.0),
        Span(2, 9662, 9938, 1930.0, 1990.0),
        Span(3, 1162, 1513, 1805.0, 1880.0),
        Span(5, 4357, 4458, 869.0, 894.0),
        Span(8, 2937, 3088, 925.0, 960.0),
    )

    private data class Nr(val band: Int, val low: Double, val high: Double)

    private val NR = listOf(
        Nr(1, 2110.0, 2170.0),
        Nr(2, 1930.0, 1990.0),
        Nr(3, 1805.0, 1880.0),
        Nr(5, 869.0, 894.0),
        Nr(7, 2620.0, 2690.0),
        Nr(8, 925.0, 960.0),
        Nr(12, 729.0, 746.0),
        Nr(13, 746.0, 756.0),
        Nr(14, 758.0, 768.0),
        Nr(18, 860.0, 875.0),
        Nr(20, 791.0, 821.0),
        Nr(25, 1930.0, 1995.0),
        Nr(26, 859.0, 894.0),
        Nr(28, 758.0, 803.0),
        Nr(30, 2350.0, 2360.0),
        Nr(34, 2010.0, 2025.0),
        Nr(38, 2570.0, 2620.0),
        Nr(39, 1880.0, 1920.0),
        Nr(40, 2300.0, 2400.0),
        Nr(41, 2496.0, 2690.0),
        Nr(48, 3550.0, 3700.0),
        Nr(66, 2110.0, 2200.0),
        Nr(70, 1995.0, 2020.0),
        Nr(71, 617.0, 652.0),
        Nr(77, 3300.0, 4200.0),
        Nr(78, 3300.0, 3800.0),
        Nr(79, 4400.0, 5000.0),
        Nr(257, 26500.0, 29500.0),
        Nr(258, 24250.0, 27500.0),
        Nr(260, 37000.0, 40000.0),
        Nr(261, 27500.0, 28350.0),
    )

    internal fun lteRangesAreDisjoint(): Boolean {
        val sorted = LTE.sortedBy { it.start }
        return sorted.zipWithNext().all { (left, right) -> left.end < right.start }
    }

    private val LTE = listOf(
        Lte(1, 0, 599, 2110.0, 0),
        Lte(2, 600, 1199, 1930.0, 600),
        Lte(3, 1200, 1949, 1805.0, 1200),
        Lte(4, 1950, 2399, 2110.0, 1950),
        Lte(5, 2400, 2649, 869.0, 2400),
        Lte(6, 2650, 2749, 875.0, 2650),
        Lte(7, 2750, 3449, 2620.0, 2750),
        Lte(8, 3450, 3799, 925.0, 3450),
        Lte(9, 3800, 4149, 1844.9, 3800),
        Lte(10, 4150, 4749, 2110.0, 4150),
        Lte(11, 4750, 4949, 1475.9, 4750),
        Lte(12, 5010, 5179, 729.0, 5010),
        Lte(13, 5180, 5279, 746.0, 5180),
        Lte(14, 5280, 5379, 758.0, 5280),
        Lte(17, 5730, 5849, 734.0, 5730),
        Lte(18, 5850, 5999, 860.0, 5850),
        Lte(19, 6000, 6149, 875.0, 6000),
        Lte(20, 6150, 6449, 791.0, 6150),
        Lte(21, 6450, 6599, 1495.9, 6450),
        Lte(22, 6600, 7399, 3510.0, 6600),
        Lte(23, 7500, 7699, 2180.0, 7500),
        Lte(24, 7700, 8039, 1525.0, 7700),
        Lte(25, 8040, 8689, 1930.0, 8040),
        Lte(26, 8690, 9039, 859.0, 8690),
        Lte(27, 9040, 9209, 852.0, 9040),
        Lte(28, 9210, 9659, 758.0, 9210),
        Lte(29, 9660, 9769, 717.0, 9660),
        Lte(30, 9770, 9869, 2350.0, 9770),
        Lte(31, 9870, 9919, 462.5, 9870),
        Lte(32, 9920, 10359, 1452.0, 9920),
        Lte(33, 36000, 36199, 1900.0, 36000),
        Lte(34, 36200, 36349, 2010.0, 36200),
        Lte(35, 36350, 36949, 1850.0, 36350),
        Lte(36, 36950, 37549, 1930.0, 36950),
        Lte(37, 37550, 37749, 1910.0, 37550),
        Lte(38, 37750, 38249, 2570.0, 37750),
        Lte(39, 38250, 38649, 1880.0, 38250),
        Lte(40, 38650, 39649, 2300.0, 38650),
        Lte(41, 39650, 41589, 2496.0, 39650),
        Lte(42, 41590, 43589, 3400.0, 41590),
        Lte(43, 43590, 45589, 3600.0, 43590),
        Lte(44, 45590, 46589, 703.0, 45590),
        Lte(45, 46590, 46789, 1447.0, 46590),
        Lte(46, 46790, 54539, 5150.0, 46790),
        Lte(47, 54540, 55239, 5855.0, 54540),
        Lte(48, 55240, 56739, 3550.0, 55240),
        Lte(49, 56740, 58239, 3550.0, 56740),
        Lte(50, 58240, 59089, 1432.0, 58240),
        Lte(51, 59090, 59139, 1427.0, 59090),
        Lte(52, 59140, 60139, 3300.0, 59140),
        Lte(53, 60140, 60254, 2483.5, 60140),
        Lte(65, 65536, 66435, 2110.0, 65536),
        Lte(66, 66436, 67335, 2110.0, 66436),
        Lte(67, 67336, 67535, 738.0, 67336),
        Lte(68, 67536, 67835, 753.0, 67536),
        Lte(69, 67836, 68335, 2570.0, 67836),
        Lte(70, 68336, 68585, 1995.0, 68336),
        Lte(71, 68586, 68935, 617.0, 68586),
        Lte(72, 68936, 68985, 461.0, 68936),
        Lte(73, 68986, 69035, 460.0, 68986),
        Lte(74, 69036, 69465, 1475.0, 69036),
        Lte(75, 69466, 70315, 1432.0, 69466),
        Lte(76, 70316, 70365, 1427.0, 70316),
        Lte(85, 70366, 70545, 728.0, 70366),
        Lte(87, 70546, 70595, 420.0, 70546),
        Lte(88, 70596, 70645, 422.0, 70596),
    )
}
