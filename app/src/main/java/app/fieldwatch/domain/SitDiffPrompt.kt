package app.fieldwatch.domain

/**
 * Paste-ready addendum prompt for Reports → Compare sits → AI Export.
 * Onboard compare is verbatim. Working data is overlap + exclusive Extra attention /
 * Named radios — not a second inventory.
 */
object SitDiffPrompt {
    private const val MAX_CHARS = 90_000

    fun build(
        thisSit: SitDiff.Side,
        second: SitDiff.Side,
        demoMode: Boolean,
    ): String {
        val macs = (thisSit.radios + second.radios).map { it.mac }
        val onboard = SitDiff.document(thisSit, second).withDemoMacs(macs, demoMode)
        val thisKeys = thisSit.keys
        val secondKeys = second.keys
        val byKey = (thisSit.radios + second.radios).associateBy { it.key }
        val onlyThis = thisKeys.minus(secondKeys)
        val onlySecond = secondKeys.minus(thisKeys)
        val both = thisKeys.intersect(secondKeys)
        val union = thisKeys.union(secondKeys)
        val overlapPct = if (union.isEmpty()) 0 else (both.size * 100) / union.size
        fun bucket(keys: Set<String>): Triple<Int, Int, Int> {
            val rows = keys.mapNotNull { byKey[it] }
            val wifi = rows.count { it.kind == RadioKind.WIFI }
            val ble = rows.count { it.kind == RadioKind.BLE }
            val randBle = rows.count { it.kind == RadioKind.BLE && it.randomized }
            return Triple(wifi, ble, randBle)
        }
        val onlyThisB = bucket(onlyThis)
        val onlySecondB = bucket(onlySecond)
        val bothB = bucket(both)
        fun line(row: SitDiff.Radio): String = buildString {
            append(if (row.kind == RadioKind.WIFI) "WIFI" else "BLE")
            append("  ").append(row.mac)
            val label = row.name.trim()
            if (label.isNotEmpty() && !label.equals(row.mac, ignoreCase = true)) {
                append("  ").append(label)
            }
            row.fleetNames.filter { it.isNotBlank() }.forEach { append("  ").append(it) }
            if (row.extraAttention) append("  Extra attention")
            if (row.kind == RadioKind.BLE && row.randomized) append("  RAND")
        }
        fun exclusive(keys: Set<String>, where: String, pred: (SitDiff.Radio) -> Boolean) =
            keys.mapNotNull { byKey[it] }.filter(pred).map { "$where  ${line(it)}" }

        val extraRows =
            exclusive(onlyThis, "Only in this sit", { it.extraAttention }) +
                exclusive(onlySecond, "Only in second sit", { it.extraAttention })
        val namedRows =
            exclusive(onlyThis, "Only in this sit", { it.named }) +
                exclusive(onlySecond, "Only in second sit", { it.named })

        val body = buildString {
            append(FieldwatchDisclaimer.experimentalMarkdown())
            appendLine()
            appendLine("You are a field RF analyst for the operator who compared two Fieldwatch sits. Fieldwatch is a stock-Android, receive-only Wi-Fi access-point + BLE-advertiser listener.")
            appendLine()
            appendLine("The **onboard Compare** (verbatim below) already split presence: only in this sit, only in the second, in both. **Do not rewrite that report. Do not reprint those lists.** Your job is an addendum the phone cannot write: what kind of change this is, and how much of it is real.")
            appendLine()
            appendLine("Constraints you must respect:")
            appendLine("- Hear-only. Kind + MAC. BLE rotation is a new row and will not stitch.")
            appendLine("- Wi-Fi rows are access points only. Associated clients are invisible.")
            appendLine("- Extra attention / signature matches are hypotheses, not identity, not a person or vehicle.")
            appendLine("- GPS stamps (if present) are this phone at hear-time, not the other radio.")
            appendLine("- Last 15 minutes vs a named sit is not the same net (RAM about 400 vs sit ${Sit.RADIO_CAP}). Missing BLE on the RAM side can be eviction, not gone.")
            appendLine("- Presence is not co-travel. Do not invent a tail, a follower, or a camera location.")
            appendLine("- Do not give safety advice. Do not tell the operator they are safe or in danger.")
            appendLine("- Treat this paste as operationally sensitive.")
            appendLine()
            appendLine("## Your output (required — this is the addendum the operator reads)")
            appendLine("Write complete sentences. Headings as below. Short bullets only for exclusive Extra attention / Named radios. No markdown tables. No code fences. No dump of the onboard lists.")
            appendLine()
            appendLine("1. **Disclaimer** — Repeat the experimental-use disclaimer first.")
            appendLine("2. **What the onboard compare already established** — 3–5 sentences. Window names, counts, Extra attention exclusives, Observer notes if any. Do not reprint inventories.")
            appendLine("3. **What the numbers add** — Overlap (both/union as a percent), Wi-Fi vs BLE in each bucket, how much exclusive BLE is RAND. Say whether this looks like fixtures, a different stall/hour, or a cap artifact. Confidence. Use the working table; do not invent rates.")
            appendLine("4. **Exclusive Extra attention and Named radios** — Full identifiers from the working table (complete MAC, name, signatures, which window). Pattern match, not identity. If none, say none.")
            appendLine("5. **What another sit or Hunt would shrink** — Concrete in-app next steps only (a third sit at the same stall, Hunt on one exclusive Extra attention row, Filters). No safety advice. No “call the police.”")
            appendLine()
            appendLine("**Takeaway (required, last line).** One sentence starting with `Takeaway:` that adds *one number the onboard takeaway does not already say* (overlap percent, exclusive Extra attention count, or RAND fraction of exclusive BLE). Not a moral. Not a threat level.")
            appendLine()
            appendLine("## Onboard Compare (verbatim — already shown to the operator; do not rewrite)")
            appendLine()
            appendLine(onboard.toPlainText().trimEnd())
            appendLine()
            appendLine("## Working data (for the addendum — do not copy rosters into the answer)")
            appendLine()
            appendLine("This sit: ${thisSit.name} (${thisSit.radios.size} radios${if (thisSit.ram) ", RAM ~400" else ", named sit up to ${Sit.RADIO_CAP}"})")
            appendLine("Second sit: ${second.name} (${second.radios.size} radios${if (second.ram) ", RAM ~400" else ", named sit up to ${Sit.RADIO_CAP}"})")
            appendLine("Only in this sit: ${onlyThis.size}  Only in second: ${onlySecond.size}  In both: ${both.size}  Union: ${union.size}  Overlap: $overlapPct%")
            appendLine("Only in this sit by radio: Wi-Fi ${onlyThisB.first}  BLE ${onlyThisB.second}  RAND BLE ${onlyThisB.third}")
            appendLine("Only in second sit by radio: Wi-Fi ${onlySecondB.first}  BLE ${onlySecondB.second}  RAND BLE ${onlySecondB.third}")
            appendLine("In both by radio: Wi-Fi ${bothB.first}  BLE ${bothB.second}  RAND BLE ${bothB.third}")
            if (thisSit.ram || second.ram) {
                appendLine("Cap note: last 15 minutes is Live RAM (about 400). A named sit keeps up to ${Sit.RADIO_CAP}. Counts are not the same net.")
            }
            appendLine()
            appendLine("Exclusive Extra attention:")
            if (extraRows.isEmpty()) appendLine("- None.")
            else extraRows.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Exclusive Named radios:")
            if (namedRows.isEmpty()) appendLine("- None.")
            else namedRows.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Observer notes:")
            val observed = (thisSit.radios + second.radios)
                .distinctBy { it.key }
                .mapNotNull { r ->
                    val note = r.observerNotes.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    r to note
                }
            if (observed.isEmpty()) appendLine("- None.")
            else observed.forEach { (r, note) ->
                val where = when {
                    r.key in onlyThis -> "Only in this sit"
                    r.key in onlySecond -> "Only in second sit"
                    else -> "In both"
                }
                appendLine("- $where  ${line(r)}")
                appendLine("  $note")
            }
            appendLine()
            appendLine("## End of working data")
            appendLine("Write the addendum now, following **Your output** at the top. Do not rewrite the onboard Compare.")
        }
        val masked = MacUtil.redactMacsIn(body, macs, demoMode)
        val withPrivacy = if (demoMode) {
            "Privacy mode: MAC tails are **:**:**. GPS coordinates are masked. Logs on the phone are unchanged.\n\n$masked"
        } else {
            masked
        }
        return if (withPrivacy.length <= MAX_CHARS) withPrivacy
        else withPrivacy.take(MAX_CHARS) + "\n\n[truncated for share-sheet size]\n"
    }
}
