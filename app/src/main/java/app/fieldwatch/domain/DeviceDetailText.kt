package app.fieldwatch.domain

import app.fieldwatch.i18n.codLabel
import app.fieldwatch.i18n.TextRuntime

import app.fieldwatch.i18n.forDisplay

import app.fieldwatch.i18n.localized

import app.fieldwatch.radio.BleAdParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-text dump of the device-detail screen. Same fields, no sparkline/presence art.
 * Not a legal identity.
 */
object DeviceDetailText {
    fun build(
        device: Sighting,
        signatureNames: List<String>,
        now: Long = System.currentTimeMillis(),
        attentionNotes: List<Pair<String, String>> = emptyList(),
        signatureNotes: List<Pair<String, String>> = emptyList(),
        fleets: List<Fleet> = emptyList(),
        mine: Boolean = false,
    ): String {
        val fmt = SimpleDateFormat("HH:mm:ss", TextRuntime.localeProvider())
        val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", TextRuntime.localeProvider())
        val facts = device.facts
        val title = device.listTitle(signatureNames)
        val guess = DeviceExplain.guess(device, signatureNames)
        val out = StringBuilder()

        fun line(label: String, value: String) {
            out.append(label).append(": ").append(value.trim()).append('\n')
        }
        fun section(title: String) {
            out.append('\n').append("## ").append(title).append('\n')
        }

        out.append(localized("device_detail_text_fieldwatch_device_detail_n", "Fieldwatch device detail\n"))
        out.append(iso.format(Date(now))).append('\n')
        out.append(
            localized("device_detail_text_experimental_not_a_legal_identity_stock_android", "Experimental. Not a legal identity. Stock Android radios — this is what the OS exposed, not a guarantee a tracker or camera is present.\n"),
        )
        out.append('\n')
        out.append(title).append('\n')
        line("MAC", device.mac)
        if (device.name.isNotBlank()) line(localized("device_detail_text_advertised_name", "Advertised name"), device.name)
        if (mine) line(localized("device_detail_text_marked_mine", "Marked mine"), localized("device_detail_text_no_beep_while_this_is_on_still", "No beep while this is on. Still listed."))

        out.append('\n')
        out.append(localized("device_detail_text_what_this_looks_like", "What this looks like: ")).append(guess.headline).append('\n')
        out.append(guess.because).append('\n')
        if (attentionNotes.isNotEmpty()) {
            section(localized("device_detail_text_extra_attention", "Extra attention"))
            attentionNotes.forEach { (name, note) ->
                out.append(localized("device_detail_text_extra_attention_2", "EXTRA ATTENTION (%1\$s): ", name)).append(note.trim()).append('\n')
            }
            out.append(localized("device_detail_text_pattern_match_not_identity_not_a_safety", "Pattern match, not identity. Not a safety finding.\n"))
        }
        if (signatureNotes.isNotEmpty()) {
            section(localized("device_detail_text_notes", "Notes"))
            signatureNotes.forEach { (name, note) ->
                out.append(name).append(": ").append(note.trim()).append('\n')
            }
        }

        section(localized("device_detail_text_identity", "Identity"))
        line(
            localized("device_detail_text_radio", "Radio"),
            if (device.kind == RadioKind.WIFI) {
                localized("device_detail_text_wi_fi_access_point_beaconing_a_network", "Wi-Fi access point (beaconing a network)")
            } else {
                localized("device_detail_text_bluetooth_low_energy_advertiser", "Bluetooth Low Energy advertiser")
            },
        )
        line(localized("device_detail_text_address", "Address"), DeviceExplain.addressExplain(device))
        vendorLine(device)?.let { line(localized("device_detail_text_who_made_it", "Who made it"), it.replace('\n', ' ')) }
            ?: line(localized("device_detail_text_oui_vendor_prefix", "OUI (vendor prefix)"), localized("device_detail_text_no_ieee_match_randomized_addresses_usually_have", "%1\$s — no IEEE match; randomized addresses usually have none", device.oui))
        if (device.hiddenSsid) {
            line(localized("device_detail_text_network_name_ssid", "Network name (SSID)"), localized("device_detail_text_hidden_the_ap_is_beaconing_but_not", "Hidden — the AP is beaconing but not publishing a name"))
        }

        section(localized("device_detail_text_signal", "Signal"))
        if (device.gone) {
            line(localized("device_detail_text_how_loud_here_rssi", "How loud here (RSSI)"), localized("device_detail_text_not_available", "Not available"))
            val last = Rssi.lastMeasured(device.rssi, device.rssiHistory)
            line(localized("device_detail_text_last_heard", "Last heard"), last?.let { "$it dBm" } ?: localized("device_detail_text_not_available", "Not available"))
        } else {
            line(localized("device_detail_text_how_loud_here_rssi", "How loud here (RSSI)"), DeviceExplain.rssiExplain(device.rssi))
            out.append(localized("device_detail_text_closer_to_0_dbm_is_louder_here", "Closer to 0 dBm is louder here, not a distance.\n"))
        }
        line(localized("device_detail_text_heard_range_this_session", "Heard range this session"), Rssi.sessionRange(device.rssiMin, device.rssiMax, device.rssiHistory))
        facts.txPowerDbm?.let {
            line(localized("device_detail_text_claimed_transmit_power", "Claimed transmit power"), localized("device_detail_text_dbm_how_loud_it_says_it_transmits", "%1\$s dBm — how loud it says it transmits, not a distance", it))
        }
        if (device.channel != 0 || device.frequencyMhz != 0) {
            line(
                localized("device_detail_text_channel_frequency", "Channel / frequency"),
                buildString {
                    if (device.channel != 0) append(localized("device_detail_text_channel", "channel %1\$s", device.channel))
                    if (device.frequencyMhz != 0) {
                        if (isNotEmpty()) append("  ·  ")
                        append("${device.frequencyMhz} MHz")
                    }
                    facts.channelWidth?.let { append(localized("device_detail_text_wide", "  ·  %1\$s wide", it)) }
                },
            )
        }
        facts.wifiStandard?.let { line(localized("device_detail_text_wi_fi_generation", "Wi-Fi generation"), it) }
        if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
            line(
                localized("device_detail_text_center_frequencies", "Center frequencies"),
                listOfNotNull(
                    facts.centerFreq0?.let { "$it MHz" },
                    facts.centerFreq1?.let { "$it MHz" },
                ).joinToString("  ·  "),
            )
        }
        val rssiTail = device.rssiHistory.filter { Rssi.measured(it.rssi) }.takeLast(24)
        if (rssiTail.isNotEmpty()) {
            line(
                localized("device_detail_text_recent_rssi_oldest_newest", "Recent RSSI (oldest → newest)"),
                rssiTail.joinToString(", ") { it.rssi.toString() },
            )
        }

        if (device.kind == RadioKind.BLE) {
            section(localized("device_detail_text_bluetooth_advertisement", "Bluetooth advertisement"))
            facts.primaryPhy?.let {
                val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                line(localized("device_detail_text_radio_phy", "Radio PHY"), phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(phy) })
            }
            facts.connectable?.let {
                line(
                    localized("device_detail_text_connectable", "Connectable"),
                    if (it) localized("device_detail_text_yes_a_phone_could_open_a_ble", "Yes — a phone could open a BLE connection")
                    else localized("device_detail_text_no_broadcast_only_you_can_hear_it", "No — broadcast-only (you can hear it, not join it from this scan)"),
                )
            }
            facts.advertisingIntervalMs?.let {
                line(localized("device_detail_text_how_often_it_advertises", "How often it advertises"), localized("device_detail_text_0f_ms_between_bursts_smaller_chattier_on", "%.0f ms between bursts (smaller = chattier on the air)").format(it))
            }
            facts.periodicIntervalMs?.let { line(localized("device_detail_text_periodic_advertising", "Periodic advertising"), "%.0f ms".format(it)) }
            facts.advFlags?.let { flags ->
                line(localized("device_detail_text_discoverability", "Discoverability"), DeviceExplain.flagsExplain(flags))
                line(localized("device_detail_text_flags_raw", "Flags (raw)"), "0x%02X".format(flags))
            }
            facts.appearance?.let { value ->
                val name = RadioDb.appearance(value)
                line(
                    localized("device_detail_text_what_it_says_it_is_appearance", "What it says it is (Appearance)"),
                    name ?: localized("device_detail_text_unlisted_appearance_0x_04x", "Unlisted Appearance 0x%04X").format(value),
                )
                line(localized("device_detail_text_appearance_code", "Appearance code"), "0x%04X".format(value))
            }
            CodDecoder.decodeOrNull(facts.deviceClass)?.let { cod ->
                line(
                    localized("device_detail_text_classic_bluetooth_class", "Classic Bluetooth class"),
                    buildString {
                        append(codLabel(cod.major))
                        if (cod.minor.isNotBlank()) append(" / ").append(codLabel(cod.minor))
                        if (cod.services.isNotEmpty()) {
                            append(localized("device_detail_text_also_offers", ". Also offers: "))
                            append(cod.services.joinToString(", ") { codLabel(it) })
                        }
                    },
                )
            }
        }

        if (device.kind == RadioKind.WIFI) {
            section(localized("device_detail_text_wi_fi_access_point", "Wi-Fi access point"))
            facts.security?.let {
                line(localized("device_detail_text_encryption_login", "Encryption / login"), DeviceExplain.wifiSecurityExplain(it))
                if (it.isNotBlank()) line(localized("device_detail_text_security_string", "Security string"), it)
            }
            facts.supportedRates?.let { line(localized("device_detail_text_supported_rates", "Supported rates"), localized("device_detail_text_mbps_required_basic_rate", "%1\$s Mbps  (* = required basic rate)", it)) }
            facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                line(localized("device_detail_text_capability_string", "Capability string"), it)
            }
        }

        device.payloadAircraft?.trim()?.takeIf { it.isNotEmpty() }?.let { line(localized("device_detail_text_aircraft", "Aircraft"), it) }

        if (fleets.isNotEmpty() && (device.kind == RadioKind.BLE || device.kind == RadioKind.WIFI)) {
            val decoded = SignatureFieldDecoder.decodeSighting(device, fleets).forDisplay(fleets)
            if (decoded.isNotEmpty()) {
                section(localized("device_detail_text_decoded_fields", "Decoded fields"))
                decoded.forEach { row ->
                    line(row.label, row.display)
                    if (row.note.isNotBlank()) line(localized("device_detail_text_note", "Note"), row.note)
                }
            }
        }

        if (device.serviceUuids.isNotEmpty()) {
            section(localized("device_detail_text_services_it_offers", "Services it offers"))
            line(
                localized("device_detail_text_service_ids", "Service IDs"),
                device.serviceUuids.joinToString("; ") { uuid ->
                    DeviceExplain.uuidGloss(uuid)?.let { "$uuid  ·  $it" } ?: uuid
                },
            )
        }
        if (facts.serviceData.isNotEmpty()) {
            facts.serviceData.forEach { sd ->
                val named = RadioDb.serviceUuid(sd.uuid)?.let { " ($it)" } ?: ""
                AdvPayloadDecoder.decodeService(sd).forEach { field -> line(field.label, field.value) }
                line(
                    localized("device_detail_text_service_data", "Service data %1\$s%2\$s", uuidShort(sd.uuid), named),
                    sd.dataHex.hexSpaced().ifBlank { localized("device_detail_text_empty", "(empty)") },
                )
            }
        }

        val mfg = facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let {
                listOf(MfgRecord(it, device.manufacturerDataHex))
            } ?: emptyList()
        }
        if (mfg.isNotEmpty()) {
            section(localized("device_detail_text_maker_data_inside_the_ad", "Maker data inside the ad"))
            mfg.forEach { rec ->
                val company = RadioDb.company(rec.companyId) ?: localized("device_detail_text_not_in_the_bluetooth_company_list", "Not in the Bluetooth company list")
                line(localized("device_detail_text_bluetooth_company_0x_04x", "Bluetooth company 0x%04X").format(rec.companyId), company)
                BleAdParser.mfgDecodedFields(rec).forEach { (k, v) -> line(k, v) }
                if (rec.dataHex.isNotBlank()) {
                    line(localized("device_detail_text_raw_payload_bytes", "Raw payload (%1\$s bytes)", rec.dataHex.length / 2), rec.dataHex.hexSpaced())
                }
            }
        }

        if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
            section(localized("device_detail_text_wi_fi_vendor_tags", "Wi-Fi vendor tags"))
            val rows = facts.vendorIes.ifEmpty {
                device.vendorIeOuis.map { VendorIeRecord(it, -1, "") }
            }
            rows.forEach { ie ->
                val org = RadioDb.vendorForOui24(ie.oui)
                val type = if (ie.type >= 0) localized("device_detail_text_type_d", " type %d").format(ie.type) else ""
                line(
                    localized("device_detail_text_vendor_oui", "Vendor OUI %1\$s%2\$s", ie.oui, type),
                    buildString {
                        append(org ?: localized("device_detail_text_unknown_ieee_oui", "Unknown IEEE OUI"))
                        append(localized("device_detail_text_extra_ap_information_element_not_the_ssid", " — extra AP information element, not the SSID."))
                        if (ie.dataHex.isNotBlank()) {
                            append(" ")
                            append(ie.dataHex.hexSpaced())
                        }
                    },
                )
            }
        }

        section(localized("device_detail_text_session", "Session"))
        line(localized("device_detail_text_first_seen", "First seen"), fmt.format(Date(device.firstSeen)))
        line(localized("device_detail_text_last_seen", "Last seen"), fmt.format(Date(device.lastSeen)))
        line(localized("device_detail_text_hits", "Hits"), device.hitCount.toString())
        Geo.screenCoord(device.latitude, device.longitude, false)?.let {
            line(localized("device_detail_text_last_fix", "Last fix"), it)
            out.append(localized("device_detail_text_last_fix_is_the_phone_s_gps", "Last fix is the phone’s GPS at hear-time, not a fix on this radio.\n"))
        }
        if (device.fleetIds.isNotEmpty()) {
            line(localized("device_detail_text_matched_signatures", "Matched signatures"), signatureNames.joinToString("; ").ifBlank {
                device.fleetIds.joinToString("; ")
            })
        }
        if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
            line(localized("device_detail_text_raw_advertisement", "Raw advertisement"), device.rawHex.hexSpaced())
        }
        presenceLine(device, now, fmt)?.let { line(localized("device_detail_text_presence_15_min", "Presence (15 min)"), it) }
        return out.toString().trimEnd() + "\n"
    }

    private fun vendorLine(device: Sighting): String? {
        val parts = ArrayList<String>(3)
        device.vendor?.let {
            parts += localized("device_detail_text_ieee_board_chip_vendor_this_is_who", "IEEE board/chip vendor: %1\$s (%2\$s). This is who owns the MAC prefix, not always the product brand.", it, device.oui)
        }
        val mfgId = device.facts.mfgRecords.firstOrNull()?.companyId ?: device.manufacturerId
        if (mfgId != null) {
            val company = RadioDb.company(mfgId)
            parts += localized("device_detail_text_bluetooth_company_in_the_ad_0x_04x", "Bluetooth company in the ad: %1\$s (0x%%04X).", company ?: localized("device_detail_text_unlisted", "unlisted")).format(mfgId)
        }
        return parts.joinToString(" ").ifBlank { null }
    }

    private fun uuidShort(uuid: String): String {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return if (hex.length >= 8 && hex.startsWith("0000")) hex.substring(4, 8) else uuid.take(8)
    }

    private fun presenceLine(device: Sighting, now: Long, fmt: SimpleDateFormat): String? {
        if (device.presence.isEmpty()) return null
        val from = now - 15 * 60 * 1000L
        val spans = device.presence.filter { (it.end ?: now) >= from }
        if (spans.isEmpty()) return null
        return spans.joinToString("; ") { span ->
            val start = fmt.format(Date(span.start.coerceAtLeast(from)))
            val end = span.end?.let { fmt.format(Date(it)) } ?: localized("device_detail_text_now", "now")
            "$start–$end"
        }
    }
}
