package app.fieldwatch.domain

import app.fieldwatch.i18n.localized

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
        val thisSit = thisSit.withoutFloodRadios()
        val second = second.withoutFloodRadios()
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
            if (row.mine) append(localized("sit_diff_prompt_marked_mine", "  Marked mine"))
            row.renderedFleetNames.filter { it.isNotBlank() }.forEach { append("  ").append(it) }
            if (row.extraAttention) append(localized("sit_diff_prompt_extra_attention", "  Extra attention"))
            val labels = row.renderedDecode.reportLabels()
            if (labels.isNotEmpty()) append("  ").append(labels.joinToString(", "))
            if (row.kind == RadioKind.BLE && row.randomized) append(localized("sit_diff_prompt_rand", "  RAND"))
        }
        fun exclusive(keys: Set<String>, where: String, pred: (SitDiff.Radio) -> Boolean) =
            keys.mapNotNull { byKey[it] }.filter(pred).map { "$where  ${line(it)}" }

        val extraRows =
            exclusive(onlyThis, localized("sit_diff_prompt_only_in_this_sit", "Only in this sit"), { it.extraAttention }) +
                exclusive(onlySecond, localized("sit_diff_prompt_only_in_second_sit", "Only in second sit"), { it.extraAttention })
        val namedRows =
            exclusive(onlyThis, localized("sit_diff_prompt_only_in_this_sit", "Only in this sit"), { it.named }) +
                exclusive(onlySecond, localized("sit_diff_prompt_only_in_second_sit", "Only in second sit"), { it.named })

        val body = buildString {
            append(FieldwatchDisclaimer.experimentalMarkdown())
            appendLine()
            appendLine(localized("ai_response_language", "Write your response in English. Preserve verbatim identifiers and observer notes."))
            appendLine(localized("sit_diff_prompt_you_are_a_field_rf_analyst_for", "You are a field RF analyst for the operator who compared two Fieldwatch sits. Fieldwatch is a stock-Android, receive-only Wi-Fi access-point + BLE-advertiser listener."))
            appendLine()
            appendLine(localized("sit_diff_prompt_the_onboard_compare_verbatim_below_already_split", "The **onboard Compare** (verbatim below) already split presence: only in this sit, only in the second, in both. **Do not rewrite that report. Do not reprint those lists.** Your job is an addendum the phone cannot write: what kind of change this is, and how much of it is real."))
            appendLine()
            appendLine(localized("sit_diff_prompt_constraints_you_must_respect", "Constraints you must respect:"))
            appendLine(localized("sit_diff_prompt_hear_only_kind_mac_ble_rotation_is", "- Hear-only. Kind + MAC. BLE rotation is a new row and will not stitch."))
            appendLine(localized("sit_diff_prompt_wi_fi_rows_are_access_points_only", "- Wi-Fi rows are access points only. Associated clients are invisible."))
            appendLine(localized("sit_diff_prompt_extra_attention_signature_matches_are_hypotheses_not", "- Extra attention / signature matches are hypotheses, not identity, not a person or vehicle."))
            appendLine(localized("sit_diff_prompt_gps_stamps_if_present_are_this_phone", "- GPS stamps (if present) are this phone at hear-time, not the other radio."))
            appendLine(localized("sit_diff_prompt_last_15_minutes_vs_a_named_sit", "- Last 15 minutes vs a named sit is not the same net (RAM about 400 vs sit %1\$s). Missing BLE on the RAM side can be eviction, not gone.", Sit.RADIO_CAP))
            appendLine(localized("sit_diff_prompt_presence_is_not_co_travel_do_not", "- Presence is not co-travel. Do not invent a tail, a follower, or a camera location."))
            appendLine(localized("sit_diff_prompt_a_radio_marked_mine_was_claimed_by", "- A radio marked mine was claimed by the operator. Do not treat it as an unexplained follower."))
            appendLine(localized("sit_diff_prompt_a_flood_note_is_a_burst_of", "- A flood note is a burst of new addresses, not a follower."))
            appendLine(localized("sit_diff_prompt_a_decoded_live_value_on_a_row", "- A decoded live value on a row is catalog text for that kind + MAC. If the onboard compare says that value changed, state the change. Do not stitch that value onto a different MAC."))
            appendLine(localized("sit_diff_prompt_an_aircraft_block_is_positions_the_radio", "- An aircraft block is positions the radio advertised, joined by UAS id. If the onboard compare says the status changed, state the change. That track is not this phone's GPS."))
            appendLine(localized("sit_diff_prompt_do_not_give_safety_advice_do_not", "- Do not give safety advice. Do not tell the operator they are safe or in danger."))
            appendLine(localized("sit_diff_prompt_treat_this_paste_as_operationally_sensitive", "- Treat this paste as operationally sensitive."))
            appendLine()
            appendLine(localized("sit_diff_prompt_your_output_required_this_is_the_addendum", "## Your output (required — this is the addendum the operator reads)"))
            appendLine(localized("sit_diff_prompt_write_complete_sentences_headings_as_below_short", "Write complete sentences. Headings as below. Short bullets only for exclusive Extra attention / Named radios. No markdown tables. No code fences. No dump of the onboard lists."))
            appendLine()
            appendLine(localized("sit_diff_prompt_1_disclaimer_repeat_the_experimental_use_disclaimer", "1. **Disclaimer** — Repeat the experimental-use disclaimer first."))
            appendLine(localized("sit_diff_prompt_2_what_the_onboard_compare_already_established", "2. **What the onboard compare already established** — 3–5 sentences. Window names, counts, Extra attention exclusives, Observer notes if any, Marked mine if any, Flood if any. Do not reprint inventories."))
            appendLine(localized("sit_diff_prompt_3_what_the_numbers_add_overlap_both", "3. **What the numbers add** — Overlap (both/union as a percent), Wi-Fi vs BLE in each bucket, how much exclusive BLE is RAND. Say whether this looks like fixtures, a different stall/hour, or a cap artifact. Confidence. Use the working table; do not invent rates."))
            appendLine(localized("sit_diff_prompt_4_exclusive_extra_attention_and_named_radios", "4. **Exclusive Extra attention and Named radios** — Full identifiers from the working table (complete MAC, name, signatures, which window). Pattern match, not identity. If none, say none."))
            appendLine(localized("sit_diff_prompt_5_what_another_sit_or_hunt_would", "5. **What another sit or Hunt would shrink** — Concrete in-app next steps only (a third sit at the same stall, Hunt on one exclusive Extra attention row, Filters). No safety advice. No “call the police.”"))
            appendLine()
            appendLine(localized("sit_diff_prompt_takeaway_required_last_line_one_sentence_starting", "**Takeaway (required, last line).** One sentence starting with `Takeaway:` that adds *one number the onboard takeaway does not already say* (overlap percent, exclusive Extra attention count, or RAND fraction of exclusive BLE). Not a moral. Not a threat level."))
            appendLine()
            appendLine(localized("sit_diff_prompt_onboard_compare_verbatim_already_shown_to_the", "## Onboard Compare (verbatim — already shown to the operator; do not rewrite)"))
            appendLine()
            appendLine(onboard.toPlainText().trimEnd())
            appendLine()
            appendLine(localized("sit_diff_prompt_working_data_for_the_addendum_do_not", "## Working data (for the addendum — do not copy rosters into the answer)"))
            appendLine()
            appendLine(localized("sit_diff_prompt_this_sit_radios", "This sit: %1\$s (%2\$s radios%3\$s)", thisSit.name, thisSit.radios.size, if (thisSit.ram) localized("sit_diff_prompt_ram_400", ", RAM ~400") else localized("sit_diff_prompt_named_sit_up_to", ", named sit up to %1\$s", Sit.RADIO_CAP)))
            appendLine(localized("sit_diff_prompt_second_sit_radios", "Second sit: %1\$s (%2\$s radios%3\$s)", second.name, second.radios.size, if (second.ram) localized("sit_diff_prompt_ram_400", ", RAM ~400") else localized("sit_diff_prompt_named_sit_up_to", ", named sit up to %1\$s", Sit.RADIO_CAP)))
            appendLine(localized("sit_diff_prompt_only_in_this_sit_only_in_second", "Only in this sit: %1\$s  Only in second: %2\$s  In both: %3\$s  Union: %4\$s  Overlap: %5\$s%%", onlyThis.size, onlySecond.size, both.size, union.size, overlapPct))
            appendLine(localized("sit_diff_prompt_only_in_this_sit_by_radio_wi", "Only in this sit by radio: Wi-Fi %1\$s  BLE %2\$s  RAND BLE %3\$s", onlyThisB.first, onlyThisB.second, onlyThisB.third))
            appendLine(localized("sit_diff_prompt_only_in_second_sit_by_radio_wi", "Only in second sit by radio: Wi-Fi %1\$s  BLE %2\$s  RAND BLE %3\$s", onlySecondB.first, onlySecondB.second, onlySecondB.third))
            appendLine(localized("sit_diff_prompt_in_both_by_radio_wi_fi_ble", "In both by radio: Wi-Fi %1\$s  BLE %2\$s  RAND BLE %3\$s", bothB.first, bothB.second, bothB.third))
            if (thisSit.ram || second.ram) {
                appendLine(localized("sit_diff_prompt_cap_note_last_15_minutes_is_live", "Cap note: last 15 minutes is Live RAM (about 400). A named sit keeps up to %1\$s. Counts are not the same net.", Sit.RADIO_CAP))
            }
            appendLine()
            appendLine(localized("sit_diff_prompt_exclusive_extra_attention", "Exclusive Extra attention:"))
            if (extraRows.isEmpty()) appendLine(localized("sit_diff_prompt_none", "- None."))
            else extraRows.forEach { appendLine("- $it") }
            appendLine()
            appendLine(localized("sit_diff_prompt_exclusive_named_radios", "Exclusive Named radios:"))
            if (namedRows.isEmpty()) appendLine(localized("sit_diff_prompt_none", "- None."))
            else namedRows.forEach { appendLine("- $it") }
            appendLine()
            appendLine(localized("sit_diff_prompt_observer_notes", "Observer notes:"))
            val observed = (thisSit.radios + second.radios)
                .distinctBy { it.key }
                .mapNotNull { r ->
                    val note = r.observerNotes.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    r to note
                }
            if (observed.isEmpty()) appendLine(localized("sit_diff_prompt_none", "- None."))
            else observed.forEach { (r, note) ->
                val where = when {
                    r.key in onlyThis -> localized("sit_diff_prompt_only_in_this_sit", "Only in this sit")
                    r.key in onlySecond -> localized("sit_diff_prompt_only_in_second_sit", "Only in second sit")
                    else -> localized("sit_diff_prompt_in_both", "In both")
                }
                appendLine("- $where  ${line(r)}")
                appendLine("  $note")
            }
            appendLine()
            appendLine(localized("sit_diff_prompt_end_of_working_data", "## End of working data"))
            appendLine(localized("sit_diff_prompt_write_the_addendum_now_following_your_output", "Write the addendum now, following **Your output** at the top. Do not rewrite the onboard Compare."))
        }
        val masked = MacUtil.redactMacsIn(body, macs, demoMode)
        val withPrivacy = if (demoMode) {
            localized("sit_diff_prompt_privacy_mode_mac_tails_are_gps_coordinates", "Privacy mode: MAC tails are **:**:**. GPS coordinates are masked. Logs on the phone are unchanged.\n\n%1\$s", masked)
        } else {
            masked
        }
        return if (withPrivacy.length <= MAX_CHARS) withPrivacy
        else withPrivacy.take(MAX_CHARS) + localized("sit_diff_prompt_n_n_truncated_for_share_sheet_size", "\n\n[truncated for share-sheet size]\n")
    }
}
