package app.fieldwatch.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diff between two saved sits: radios only in A (left since), only in B
 * (arrived), and in both with loudness change. Pure — feeds the Reports
 * dialog and the shareable text.
 */
object SitDiff {

    data class Result(
        val a: SitSummary,
        val b: SitSummary,
        /** Radios heard in A but not B (departed). */
        val onlyA: List<SitRadio>,
        /** Radios heard in B but not A (arrived). */
        val onlyB: List<SitRadio>,
        /** (in A, in B) pairs for radios heard in both sits. */
        val common: List<Pair<SitRadio, SitRadio>>,
    ) {
        val arrivedCount: Int get() = onlyB.size
        val departedCount: Int get() = onlyA.size
        val commonCount: Int get() = common.size
    }

    fun diff(a: SitFile, b: SitFile): Result {
        val ra = a.radios.associateBy { it.key }
        val rb = b.radios.associateBy { it.key }
        return Result(
            a = a.summary,
            b = b.summary,
            onlyA = (ra.keys - rb.keys).mapNotNull(ra::get).sortedWith(LOUD_FIRST),
            onlyB = (rb.keys - ra.keys).mapNotNull(rb::get).sortedWith(LOUD_FIRST),
            common = ra.keys.intersect(rb.keys)
                .map { ra.getValue(it) to rb.getValue(it) }
                .sortedWith(compareByDescending { (x, y) -> kotlin.math.abs(y.rssiMax - x.rssiMax) }),
        )
    }

    private val LOUD_FIRST =
        compareByDescending<SitRadio> { it.rssiMax }.thenBy { it.name }.thenBy { it.mac }

    fun label(radio: SitRadio): String {
        val name = radio.name.trim()
        return when {
            name.isNotEmpty() && !name.equals(radio.mac, ignoreCase = true) -> name
            radio.hiddenSsid -> "<hidden>"
            else -> radio.mac
        }
    }

    private fun fleetNames(radio: SitRadio, fleets: List<Fleet>): String =
        radio.fleetIds.mapNotNull { id -> fleets.firstOrNull { it.id == id }?.name }
            .joinToString(", ")

    private fun row(radio: SitRadio, fleets: List<Fleet>): String = buildString {
        append(label(radio))
        append("  ")
        append(radio.mac)
        append("  ").append(if (radio.kind == RadioKind.WIFI) "Wi-Fi" else "BLE")
        append("  ").append(radio.rssi).append(" dBm")
        val sig = fleetNames(radio, fleets)
        if (sig.isNotBlank()) append("  · ").append(sig)
    }

    /** Shareable plain-text report. Same disclaimer posture as Debrief. */
    fun toText(result: Result, fleets: List<Fleet>): String = buildString {
        val fmt = SimpleDateFormat("d MMM HH:mm", Locale.US)
        fun window(s: SitSummary) =
            "${fmt.format(Date(s.startAt))} → ${s.endAt?.let { fmt.format(Date(it)) } ?: "open"}"

        appendLine("Fieldwatch sit diff")
        appendLine("A: ${result.a.name}  (${window(result.a)}, ${result.a.radioCount} radios)")
        appendLine("B: ${result.b.name}  (${window(result.b)}, ${result.b.radioCount} radios)")
        appendLine()
        appendLine("Arrived in B: ${result.arrivedCount}  ·  Departed since A: ${result.departedCount}  ·  In both: ${result.commonCount}")
        appendLine()

        if (result.onlyB.isNotEmpty()) {
            appendLine("## Arrived in B (${result.arrivedCount})")
            result.onlyB.forEach { appendLine("+ ${row(it, fleets)}") }
            appendLine()
        }
        if (result.onlyA.isNotEmpty()) {
            appendLine("## Departed since A (${result.departedCount})")
            result.onlyA.forEach { appendLine("- ${row(it, fleets)}") }
            appendLine()
        }
        if (result.common.isNotEmpty()) {
            appendLine("## In both (${result.commonCount})")
            result.common.forEach { (x, y) ->
                val delta = y.rssiMax - x.rssiMax
                val arrow = when {
                    delta >= 6 -> "louder"
                    delta <= -6 -> "quieter"
                    else -> "similar"
                }
                appendLine("· ${row(y, fleets)}  (peak ${x.rssiMax} → ${y.rssiMax} dBm, $arrow)")
            }
            appendLine()
        }
        append("Same MAC across two windows, not proof of presence. Experimental.")
    }
}
