package app.fieldwatch.domain

import app.fieldwatch.i18n.displayLabel

import app.fieldwatch.i18n.localized

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Paste-ready addendum prompt for Reports → AI Export.
 * Onboard Debrief is verbatim. Working data is rates, RSSI bands, Extra attention,
 * and finder-tag rows for a tracking stress-test — not a second inventory.
 */
object DebriefPrompt {
    const val WINDOW_SHORT_MS = 5 * 60_000L
    const val WINDOW_MS = 15 * 60_000L
    private const val MAX_CHARS = 90_000

    fun build(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        settings: AppSettings,
        now: Long = System.currentTimeMillis(),
        operatorPath: List<GpsSample> = emptyList(),
        places: DebriefPlaces = DebriefPlaces.Off,
        window: DebriefWindow? = null,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
        bookmarkedKeys: Set<String> = emptySet(),
        mineKeys: Set<String> = emptySet(),
        floods: List<FloodBurst> = emptyList(),
    ): String {
        val names = fleets.associate { it.id to it.name }
        val win = window ?: DebriefWindow(now - WINDOW_MS, now)
        val windowStart = win.startAt
        val windowEnd = win.endAt
        val aside = FloodBurst.keysOf(floods, windowStart, windowEnd)
        val in15 = devices.filter {
            (it.lastSeen >= windowStart || it.firstSeen >= windowStart) && it.key !in aside
        }
        val shortStart = maxOf(windowStart, windowEnd - WINDOW_SHORT_MS)
        val in5 = in15.filter { it.lastSeen >= shortStart || it.firstSeen >= shortStart }
        val wifi = in15.filter { it.kind == RadioKind.WIFI }
        val ble = in15.filter { it.kind == RadioKind.BLE }
        val signed = in15.filter { it.fleetIds.isNotEmpty() }
        val randomized = ble.count { it.randomized }
        val arrived = in15.filter { it.firstSeen >= windowStart }
        val departed = in15.filter { it.gone || it.lastSeen < windowEnd - 45_000L }
        val persistent = in15.filter { dwellMs(it, windowStart, windowEnd) >= win.durationMs * 2 / 3 }
        val path = operatorPath.filter { it.at in windowStart..windowEnd }
        val pathSpan = Geo.spanM(path)
        val pathLen = Geo.pathLengthM(path)
        val extraHits = in15.flatMap { d ->
            d.attentionNotes(fleets).map { (sig, note) -> Triple(d, sig, note) }
        }
        val finders = in15.filter {
            it.key !in mineKeys && TrackerMatch.kind(it, names) == TrackerMatch.Kind.FINDER
        }
        val sigFamilies = signed.groupBy { d ->
            d.fleetIds.joinToString("+") { names[it] ?: it }
        }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
        val bleRssi = ble.map { it.rssi }
        val wifiRssi = wifi.map { it.rssi }
        val onboard = DebriefReport.build(
            devices, fleets, settings, operatorPath, now, places, win,
            customNames, observerNotes, bookmarkedKeys, mineKeys = mineKeys,
            floods = floods,
        )
        val iso = utc(windowEnd)
        val start = utc(windowStart)

        val body = buildString {
            append(experimentalDisclaimerMarkdown())
            appendLine()
            appendLine(localized("ai_response_language", "Write your response in English. Preserve verbatim identifiers and observer notes."))
            appendLine(localized("debrief_prompt_you_are_a_field_rf_analyst_for", "You are a field RF analyst for the operator who collected this sit. Fieldwatch is a stock-Android, receive-only Wi-Fi access-point + BLE-advertiser listener."))
            appendLine()
            appendLine(localized("debrief_prompt_the_onboard_debrief_verbatim_below_already_tabulated", "The **onboard Debrief** (verbatim below) already tabulated the sit: counts, Where you were, tracking callouts, inventories, Extra attention, takeaway. **Do not rewrite that report. Do not reprint inventories or stay lists.** Your job is an addendum the phone cannot write: rates, competing hypotheses, and a stress-test of the onboard tracking callouts."))
            appendLine()
            appendLine(localized("debrief_prompt_constraints_you_must_respect", "Constraints you must respect:"))
            appendLine(localized("debrief_prompt_hear_only_wi_fi_rows_are_access", "- Hear-only. Wi-Fi rows are access points only. BLE rows are advertisers. Kind + MAC. BLE rotation is a new row and will not stitch."))
            appendLine(localized("debrief_prompt_signature_oui_company_matches_are_hypotheses_not", "- Signature / OUI / company matches are hypotheses, not identity, not a person or vehicle."))
            appendLine(localized("debrief_prompt_gps_stamps_if_present_are_this_phone", "- GPS stamps (if present) are this phone at hear-time, not the other radio. Do not place a camera or tag at the GPS pin."))
            appendLine(localized("debrief_prompt_place_names_if_present_are_system_reverse", "- Place names (if present) are system reverse-geocode of those stamps."))
            appendLine(localized("debrief_prompt_rssi_is_loudness_at_the_phone_not", "- RSSI is loudness at the phone, not meters."))
            appendLine(localized("debrief_prompt_live_ram_cap_is_about_400_radios", "- Live RAM cap is about 400 radios; unnamed BLE evicts after ~3 min. A named sit keeps more. This is not a complete capture."))
            appendLine(localized("debrief_prompt_do_not_claim_a_tracker_is_following", "- Do not claim a tracker is following unless the onboard GPS co-travel section supports it. A radio with you the whole sit is not automatically yours — it may be planted. Do not dismiss it. Do not invent a tail the onboard test did not flag. Do not treat a retail beacon as a Find My tail."))
            appendLine(localized("debrief_prompt_a_radio_marked_mine_was_claimed_by", "- A radio marked mine was claimed by the operator. Do not treat it as an unexplained follower."))
            appendLine(localized("debrief_prompt_a_flood_note_is_a_burst_of", "- A flood note is a burst of new addresses, not a follower."))
            appendLine(localized("debrief_prompt_a_decoded_live_value_on_a_tracking", "- A decoded live value on a tracking row is catalog text for that advertisement. Quote the catalog sentence when the onboard report includes one. Do not stitch that value onto a different MAC."))
            appendLine(localized("debrief_prompt_an_aircraft_block_and_an_amber_track", "- An aircraft block and an amber track are positions the radio advertised. Trails with the same UAS id are one aircraft. They are not this phone's GPS and they are not a finding that the aircraft followed the operator."))
            appendLine(localized("debrief_prompt_do_not_give_safety_advice_do_not", "- Do not give safety advice. Do not tell the operator they are safe or in danger."))
            appendLine(localized("debrief_prompt_treat_this_paste_as_operationally_sensitive", "- Treat this paste as operationally sensitive."))
            appendLine()
            appendLine(localized("debrief_prompt_your_output_required_this_is_the_addendum", "## Your output (required — this is the addendum the operator reads)"))
            appendLine(localized("debrief_prompt_write_complete_sentences_headings_as_below_short", "Write complete sentences. Headings as below. Short bullets only for Extra attention and tracking rows from the working table. No markdown tables. No code fences. No dump of the onboard inventories."))
            appendLine()
            appendLine(localized("debrief_prompt_1_disclaimer_repeat_the_experimental_use_disclaimer", "1. **Disclaimer** — Repeat the experimental-use disclaimer first."))
            appendLine(localized("debrief_prompt_2_what_the_onboard_debrief_already_established", "2. **What the onboard Debrief already established** — 3–5 sentences. Counts, distance, tracking callouts, Extra attention hits, Observer notes if any, Marked mine if any, Flood if any. Do not reprint inventories."))
            appendLine(localized("debrief_prompt_3_what_the_numbers_add_5_vs", "3. **What the numbers add** — 5- vs 15-minute counts, RSSI bands, RAND BLE percent, arrivals per minute, persistent vs gone, signature-family mix. Say street vs dwelling vs retail vs vehicle, and 5-minute vs 15-minute change (denser, quieter, stable). Confidence. If GPS ran, path length/span from the working table — do not pin a radio to a stay."))
            appendLine(localized("debrief_prompt_4_extra_attention_and_tracking_callouts_full", "4. **Extra attention and tracking callouts** — Full identifiers from the working table (complete MAC, name, RSSI min/max, signatures, dwell). Stress-test onboard Possible trackers with you / Possible tail / Retail beacons / Wearables. Agree, qualify, or say the data are too thin. Pattern match, not identity. If none, say none."))
            appendLine(localized("debrief_prompt_5_what_another_sit_or_hunt_would", "5. **What another sit or Hunt would shrink** — Concrete in-app next steps only (Hunt on one Extra attention row, a longer GPS path, Compare sits, Filters). No safety advice. No “call the police.”"))
            appendLine()
            appendLine(localized("debrief_prompt_takeaway_required_last_line_one_sentence_starting", "**Takeaway (required, last line).** One sentence starting with `Takeaway:` that adds *one number the onboard takeaway does not already say* (a rate, RAND percent, 5- vs 15-minute change, path span). Not a moral. Not a threat level."))
            appendLine()
            appendLine(localized("debrief_prompt_collection_context", "## Collection context"))
            appendLine(localized("debrief_prompt_tool_fieldwatch_app_fieldwatch_receive_only_no", "- Tool: Fieldwatch (app.fieldwatch), receive-only, no association / injection / cloud."))
            appendLine(
                if (win.sitName != null) {
                    localized("debrief_prompt_window_sit_utc_with_a_5_minute", "- Window: sit **%1\$s** (%2\$s → %3\$s UTC), with a 5-minute recent slice.", win.sitName, start, iso)
                } else {
                    localized("debrief_prompt_window_last_15_minutes_utc_with_a", "- Window: last **15 minutes** (%1\$s → %2\$s UTC), with a **5-minute** recent slice.", start, iso)
                },
            )
            appendLine(localized("debrief_prompt_scan_intensity_stale_after_s", "- Scan intensity: %1\$s. Stale after %2\$ss.", settings.intensity.displayLabel(), settings.staleSec))
            appendLine(localized("debrief_prompt_location_tags_online_place_names", "- Location tags: %1\$s. Online place names: %2\$s.", if (settings.tagLocation) localized("debrief_prompt_on", "on") else localized("debrief_prompt_off", "off"), if (settings.onlineLookup) localized("debrief_prompt_on", "on") else localized("debrief_prompt_off", "off")))
            appendLine()
            appendLine(localized("debrief_prompt_onboard_debrief_verbatim_already_shown_to_the", "## Onboard Debrief (verbatim — already shown to the operator; do not rewrite)"))
            appendLine()
            appendLine(onboard.trimEnd())
            appendLine()
            appendLine(localized("debrief_prompt_working_data_for_the_addendum_do_not", "## Working data (for the addendum — do not copy rosters into the answer)"))
            appendLine()
            appendLine(
                localized("debrief_prompt_15_min_wi_fi_ble_signed_hidden", "15 min: Wi-Fi %1\$s  BLE %2\$s  signed %3\$s  hidden SSIDs %4\$s  ", wifi.size, ble.size, signed.size, wifi.count { it.hiddenSsid }) +
                    localized("debrief_prompt_rand_ble", "RAND BLE %1\$s/%2\$s (%3\$s%%)  ", randomized, ble.size, pct(randomized, ble.size)) +
                    localized("debrief_prompt_first_seen_min_persistent_gone_quiet", "first-seen %1\$s (%2\$s/min)  persistent %3\$s  gone/quiet %4\$s", arrived.size, perMin(arrived.size), persistent.size, departed.size),
            )
            appendLine(
                localized("debrief_prompt_5_min_wi_fi_ble", "5 min: Wi-Fi %1\$s  BLE %2\$s  ", in5.count { it.kind == RadioKind.WIFI }, in5.count { it.kind == RadioKind.BLE }) +
                    localized("debrief_prompt_signed_first_seen", "signed %1\$s  first-seen %2\$s", in5.count { it.fleetIds.isNotEmpty() }, in5.count { it.firstSeen >= shortStart }),
            )
            appendLine(
                "BLE RSSI (n=${ble.size}): ≥−50 ${bandGe(bleRssi, -50)}  −51..−70 ${band(bleRssi, -70, -51)}  " +
                    "−71..−85 ${band(bleRssi, -85, -71)}  <−85 ${bandLt(bleRssi, -85)}",
            )
            appendLine(
                "Wi-Fi RSSI (n=${wifi.size}): ≥−50 ${bandGe(wifiRssi, -50)}  −51..−70 ${band(wifiRssi, -70, -51)}  " +
                    "−71..−85 ${band(wifiRssi, -85, -71)}  <−85 ${bandLt(wifiRssi, -85)}",
            )
            if (sigFamilies.isEmpty()) {
                appendLine(localized("debrief_prompt_signature_families_none", "Signature families: none."))
            } else {
                appendLine(localized("debrief_prompt_signature_families_count", "Signature families (count): ") + sigFamilies.take(12).joinToString { "${it.first}=${it.second}" })
            }
            appendLine(
                localized("debrief_prompt_gps_path_tagging", "GPS path: tagging %1\$s  ", if (settings.tagLocation) localized("debrief_prompt_on", "on") else localized("debrief_prompt_off", "off")) +
                    localized("debrief_prompt_fixes_length_m_span_m", "fixes %1\$s  length %2\$s m  span %3\$s m  ", path.size, pathLen.toInt(), pathSpan.toInt()) +
                    localized("debrief_prompt_places", "places %1\$s", if (places.attempted) places.note else localized("debrief_prompt_off", "off")),
            )
            appendLine()
            appendLine(localized("debrief_prompt_extra_attention", "Extra attention:"))
            if (extraHits.isEmpty()) {
                appendLine(localized("debrief_prompt_none", "- None."))
            } else {
                extraHits.forEach { (d, sig, note) ->
                    append("- ").append(row(d, names, now, windowStart, customNames, observerNotes))
                    if (d.key in mineKeys) append(localized("debrief_prompt_marked_mine", "  Marked mine"))
                    append(" | ").append(sig).append(": ").append(note)
                    appendLine()
                }
            }
            appendLine()
            appendLine(localized("debrief_prompt_observer_notes", "Observer notes:"))
            val observed = in15.mapNotNull { d ->
                val note = observerNotes[d.key]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                d to note
            }
            if (observed.isEmpty()) {
                appendLine(localized("debrief_prompt_none", "- None."))
            } else {
                observed.sortedByDescending { it.first.rssi }.forEach { (d, note) ->
                    append("- ").append(row(d, names, now, windowStart, customNames, emptyMap()))
                    appendLine()
                    appendLine("  $note")
                }
            }
            appendLine()
            appendLine(localized("debrief_prompt_finder_tag_like_radios_for_stress_test", "Finder-tag-like radios (for stress-test of onboard tracking; not a tail list):"))
            if (finders.isEmpty()) {
                appendLine(localized("debrief_prompt_none", "- None."))
            } else {
                finders.sortedByDescending { it.rssi }.take(20).forEach { d ->
                    append("- ").append(row(d, names, now, windowStart, customNames, observerNotes))
                    append(" rssiMin=").append(d.rssiMin).append(" rssiMax=").append(d.rssiMax)
                    appendLine()
                }
            }
            appendLine()
            appendLine(localized("debrief_prompt_end_of_working_data", "## End of working data"))
            appendLine(localized("debrief_prompt_write_the_addendum_now_following_your_output", "Write the addendum now, following **Your output** at the top. Do not rewrite the onboard Debrief."))
        }
        return if (body.length <= MAX_CHARS) body
        else body.take(MAX_CHARS) + localized("debrief_prompt_n_n_truncated_for_share_sheet_size", "\n\n[truncated for share-sheet size]\n")
    }

    fun experimentalDisclaimerMarkdown(): String = FieldwatchDisclaimer.experimentalMarkdown()

    private fun row(
        d: Sighting,
        names: Map<String, String>,
        now: Long,
        windowStart: Long,
        customNames: Map<String, String> = emptyMap(),
        observerNotes: Map<String, String> = emptyMap(),
    ): String = buildString {
        append(if (d.kind == RadioKind.WIFI) "WIFI" else "BLE")
        append(" ").append(d.mac)
        val label = d.reportName(customNames).trim()
        if (label.isNotEmpty() && !label.equals(d.mac, ignoreCase = true)) {
            append("  ").append(label.take(32))
        }
        observerNotes[d.key]?.let { append(localized("debrief_prompt_observer", "  Observer: ")).append(it.take(80)) }
        append(" rssi=").append(d.rssi).append("dBm")
        if (d.randomized) append(" RAND")
        if (d.fleetIds.isNotEmpty()) {
            append(" sig=").append(d.fleetIds.joinToString("+") { names[it] ?: it })
        }
        val labels = d.liveDecode.reportLabels()
        if (labels.isNotEmpty()) append(" decoded=").append(labels.joinToString(","))
        val notes = d.liveDecode.map { it.note.trim() }.filter { it.isNotEmpty() }.distinct()
        if (notes.isNotEmpty()) append(" decodeNote=").append(notes.joinToString(" "))
        append(" dwell=").append(fmtDur(dwellMs(d, windowStart, now)))
    }

    private fun dwellMs(d: Sighting, from: Long, to: Long): Long {
        var sum = 0L
        val spans = d.presence.ifEmpty { listOf(PresenceSpan(d.firstSeen, if (d.gone) d.lastSeen else null)) }
        for (span in spans) {
            val a = maxOf(span.start, from)
            val b = minOf(span.end ?: to, to)
            if (b > a) sum += b - a
        }
        return sum
    }

    private fun utc(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    private fun fmtDur(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val m = s / 60
        val r = s % 60
        return if (m >= 60) "${m / 60}h${m % 60}m" else if (m > 0) "${m}m${r}s" else "${r}s"
    }

    private fun pct(n: Int, d: Int): Int = if (d <= 0) 0 else (n * 100) / d

    private fun perMin(n: Int): String {
        val rate = n / 15.0
        return if (rate >= 10) rate.toInt().toString() else "%.1f".format(Locale.US, rate)
    }

    private fun band(list: List<Int>, lo: Int, hi: Int) = list.count { it in lo..hi }
    private fun bandGe(list: List<Int>, lo: Int) = list.count { it >= lo }
    private fun bandLt(list: List<Int>, hi: Int) = list.count { it < hi }
}
