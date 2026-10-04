package app.fieldwatch.domain

import app.fieldwatch.i18n.displayLabel

import app.fieldwatch.i18n.localized

/**
 * Paste-ready analyst prompt for **one** radio from the device-detail screen.
 * One-tap share; no Fieldwatch cloud.
 */
object DeviceDetailPrompt {
    fun build(
        device: Sighting,
        signatureNames: List<String>,
        settings: AppSettings,
        places: DebriefPlaces = DebriefPlaces.Off,
        now: Long = System.currentTimeMillis(),
        attentionNotes: List<Pair<String, String>> = emptyList(),
        signatureNotes: List<Pair<String, String>> = emptyList(),
        fleets: List<Fleet> = emptyList(),
        mine: Boolean = false,
    ): String {
        val title = device.listTitle(signatureNames)
        val kind = if (device.kind == RadioKind.WIFI) localized("device_detail_prompt_wi_fi_access_point", "Wi-Fi access point") else localized("device_detail_prompt_bluetooth_le_advertiser", "Bluetooth LE advertiser")
        return buildString {
            append(DebriefPrompt.experimentalDisclaimerMarkdown())
            appendLine()
            appendLine(localized("ai_response_language", "Write your response in English. Preserve verbatim identifiers and observer notes."))
            appendLine(localized("device_detail_prompt_you_are_a_field_rf_privacy_analyst", "You are a field RF / privacy analyst with deep knowledge of IEEE OUI, Bluetooth SIG assigned numbers, GAP Appearance, known advertisement formats (iBeacon, Eddystone, Apple Continuity / Find My, Google Fast Pair, Microsoft), and common consumer products."))
            appendLine()
            appendLine(localized("device_detail_prompt_the_user_wants_as_much_information_as", "The user wants **as much information as possible** about **this one radio** from a Fieldwatch observation. Fieldwatch is a stock-Android, receive-only Wi-Fi + BLE listener. Use the dump below **and** your public knowledge of registries and formats. Cite which field or byte pattern supports each claim."))
            appendLine()
            appendLine(localized("device_detail_prompt_constraints_you_must_respect", "Constraints you must respect:"))
            appendLine(localized("device_detail_prompt_this_is_one_advertised_radio_not_a", "- This is **one** advertised radio, not a person, vehicle, or legal identity."))
            appendLine(localized("device_detail_prompt_wi_fi_rows_are_access_points_only", "- Wi-Fi rows are **access points only**. Associated clients are invisible. Stock Android cannot promiscuously capture stations or probe requests."))
            appendLine(localized("device_detail_prompt_ble_rows_are_advertisers_randomized_macs_are", "- BLE rows are advertisers. Randomized MACs are not stable identities and will not stitch across rotations."))
            appendLine(localized("device_detail_prompt_signature_oui_company_uuid_matches_are_hypotheses", "- Signature / OUI / company / UUID matches are **hypotheses**, not proof of a serial, owner, or that a tracker is present."))
            appendLine(localized("device_detail_prompt_gps_stamps_if_present_are_the_operator", "- GPS stamps (if present) are the **operator phone** at hear-time, not this radio’s location."))
            appendLine(localized("device_detail_prompt_place_names_if_present_are_system_reverse", "- Place names (if present) are system reverse-geocode of those stamps. Approximate."))
            appendLine(localized("device_detail_prompt_rssi_is_loudness_at_the_phone_not", "- RSSI is loudness at the phone, not a measured distance."))
            appendLine(localized("device_detail_prompt_do_not_invent_fields_that_are_not", "- Do not invent fields that are not in the dump. If data is thin, say so and say what would help."))
            appendLine(localized("device_detail_prompt_do_not_claim_this_radio_is_following", "- Do not claim this radio is following anyone. Do not give safety advice."))
            appendLine(localized("device_detail_prompt_treat_this_paste_as_operationally_sensitive_mac", "- Treat this paste as operationally sensitive (MAC, SSID, payload, GPS)."))
            appendLine()
            appendLine(localized("device_detail_prompt_collection_context", "## Collection context"))
            appendLine(localized("device_detail_prompt_tool_fieldwatch_app_fieldwatch_receive_only_no", "- Tool: Fieldwatch (app.fieldwatch), receive-only, no association / injection / cloud."))
            appendLine(localized("device_detail_prompt_subject_titled", "- Subject: %1\$s titled “%2\$s”.", kind, title))
            appendLine(localized("device_detail_prompt_scan_intensity_stale_after_s_brief_hold", "- Scan intensity: %1\$s. Stale after %2\$ss. Brief hold %3\$ss.", settings.intensity.displayLabel(), settings.staleSec, settings.decaySec))
            appendLine(localized("device_detail_prompt_location_tags", "- Location tags: %1\$s.", if (settings.tagLocation) localized("device_detail_prompt_on", "on") else localized("device_detail_prompt_off", "off")))
            appendLine(localized("device_detail_prompt_online_place_names", "- Online place names: %1\$s.", if (settings.onlineLookup) localized("device_detail_prompt_on", "on") else localized("device_detail_prompt_off", "off")))
            appendLine(localized("device_detail_prompt_randomized_mac_flag", "- Randomized MAC flag: %1\$s.", if (device.randomized) localized("decode_boolean_yes", "yes") else localized("decode_boolean_no", "no")))
            appendLine(localized("device_detail_prompt_hits_this_session_gone", "- Hits this session: %1\$s. Gone: %2\$s.", device.hitCount, if (device.gone) localized("decode_boolean_yes", "yes") else localized("decode_boolean_no", "no")))
            if (device.gpsTrail.isNotEmpty()) {
                appendLine(localized("device_detail_prompt_operator_gps_trail_samples_on_this_radio", "- Operator GPS trail samples on this radio: %1\$s (phone path while it was heard).", device.gpsTrail.size))
            }
            appendLine()
            if (places.attempted) {
                appendLine(localized("device_detail_prompt_places_operator_gps_optional", "## Places (operator GPS, optional)"))
                appendLine(places.note)
                places.lines.forEach { appendLine(it) }
                appendLine()
            }
            appendLine(localized("device_detail_prompt_observation_dump_verbatim_from_the_detail_page", "## Observation dump (verbatim from the detail page)"))
            appendLine()
            append(DeviceDetailText.build(device, signatureNames, now, attentionNotes, signatureNotes, fleets, mine).trimEnd())
            appendLine()
            appendLine()
            appendLine(localized("device_detail_prompt_your_analysis_required_sections", "## Your analysis (required sections)"))
            appendLine(localized("device_detail_prompt_1_what_it_likely_is_product_class", "1. **What it likely is** — Product class, likely brand/family, possible model. Confidence 0–100. Hedge (Most likely / Probably / Could be). List the evidence (name, OUI, company ID, Appearance, services, payload). Competing hypotheses if the data fits more than one product."))
            appendLine(localized("device_detail_prompt_2_registry_format_decode_ieee_oui_or", "2. **Registry / format decode** — IEEE OUI or CID; Bluetooth SIG company; GAP Appearance; 16-bit UUIDs; iBeacon UUID/major/minor if present; Fast Pair model ID if present; Apple Continuity type if present. Quote the hex you used. If you recognize a well-known UUID or company from public lists, say so and say the list."))
            appendLine(localized("device_detail_prompt_3_what_that_product_typically_does_phone", "3. **What that product typically does** — Phone, tag, speaker, car, AP, camera, mesh node, accessory, etc. Typical radio behavior (always-on beacon vs intermittent)."))
            appendLine(localized("device_detail_prompt_4_what_fieldwatch_actually_saw_vs_what", "4. **What Fieldwatch actually saw vs what it cannot see** — Stock Android limits (no station/probe capture, no cellular, no DF). Randomized address implications."))
            appendLine(localized("device_detail_prompt_5_signal_and_presence_loud_quiet_here", "5. **Signal and presence** — Loud/quiet here; RSSI range this session; on-air windows. Do not convert RSSI to meters."))
            appendLine(localized("device_detail_prompt_6_signature_match_if_fieldwatch_matched_a", "6. **Signature match** — If Fieldwatch matched a signature, treat it as a filter hit, not identity. Say whether the payload also supports that family. If Notes are in the dump, use them as catalog context for that family. If Extra attention is in the dump, quote it and treat it as an operator caution on a pattern, not proof — separate from Notes."))
            appendLine(localized("device_detail_prompt_7_open_questions_what_extra_observation_another", "7. **Open questions** — What extra observation (another packet, name, GPS path, a second radio) would raise or lower confidence."))
            appendLine(localized("device_detail_prompt_8_must_not_conclude_one_short_list", "8. **Must not conclude** — One short list of claims the dump does **not** support (owner, following, legal ID, distance)."))
            appendLine()
            appendLine(localized("device_detail_prompt_end_with_a_single_one_line_takeaway", "End with a single one-line **takeaway** (what this radio most likely is, and one thing to check next). No safety advice."))
        }
    }
}
