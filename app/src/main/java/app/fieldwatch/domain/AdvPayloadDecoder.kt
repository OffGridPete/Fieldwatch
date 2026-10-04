package app.fieldwatch.domain

import app.fieldwatch.i18n.localized

/**
 * Decode well-known BLE advertisement payloads: Apple Continuity / iBeacon,
 * Google Fast Pair, Eddystone, Microsoft CDP. After company ID / UUID the rest
 * is proprietary; only published or well-reverse-engineered layouts are named.
 */
object AdvPayloadDecoder {
    data class Field(val label: String, val value: String)

    data class RoleHint(
        val bucket: String,
        val label: String,
        val reason: String,
        val weight: Int,
    )

    fun decodeManufacturer(record: MfgRecord): List<Field> {
        val bytes = hexToBytes(record.dataHex) ?: return emptyList()
        return when (record.companyId) {
            0x004C -> decodeApple(bytes)
            0x0006 -> decodeMicrosoft(bytes)
            0x0157 -> decodeAltBeacon(bytes)
            0x00E0 -> listOf(Field(localized("adv_payload_decoder_google_manufacturer_data", "Google manufacturer data"), localized("adv_payload_decoder_bytes", "%1\$s bytes", bytes.size)))
            else -> emptyList()
        }
    }

    fun decodeService(record: ServiceDataRecord): List<Field> {
        val bytes = hexToBytes(record.dataHex) ?: return emptyList()
        val short = uuid16(record.uuid) ?: return emptyList()
        return when (short) {
            0xFE2C -> decodeFastPair(bytes)
            0xFEAA -> decodeEddystone(bytes)
            else -> emptyList()
        }
    }

    fun roleHints(device: Sighting): List<RoleHint> {
        val out = ArrayList<RoleHint>(4)
        val mfg = device.facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let { listOf(MfgRecord(it, device.manufacturerDataHex)) } ?: emptyList()
        }
        for (rec in mfg) {
            if (rec.companyId != 0x004C) continue
            val bytes = hexToBytes(rec.dataHex) ?: continue
            for (tlv in appleTlvs(bytes)) {
                when (tlv.type) {
                    0x02 -> if (tlv.data.size >= 20) {
                        val hex = tlv.data.toHexUpper()
                        val teslaPrefix = DefaultCatalog.TESLA_IBEACON_MFG_PREFIX
                        val targetPrefix = DefaultCatalog.TARGET_ATRIUS_IBEACON_MFG_PREFIX
                        if (hex.startsWith(teslaPrefix) || hex.startsWith(teslaPrefix.drop(4))) {
                            out += RoleHint(
                                "vehicle",
                                localized("adv_payload_decoder_a_tesla_vehicle_or_phone_as_key", "a Tesla vehicle or phone-as-key"),
                                localized("adv_payload_decoder_tesla_phone_key_ibeacon_uuid_ios_background", "Tesla phone-key iBeacon UUID (iOS background find)."),
                                8,
                            )
                        } else if (hex.startsWith(targetPrefix) || hex.startsWith(targetPrefix.drop(4))) {
                            out += RoleHint(
                                "beacon",
                                localized("adv_payload_decoder_a_target_atrius_basket_tag", "a Target Atrius basket tag"),
                                localized("adv_payload_decoder_target_atrius_ibeacon_uuid_shopping_basket_asset", "Target / Atrius iBeacon UUID (shopping-basket asset tag)."),
                                8,
                            )
                        } else {
                            out += RoleHint("beacon", localized("adv_payload_decoder_an_ibeacon", "an iBeacon"), localized("adv_payload_decoder_apple_ibeacon_payload", "Apple iBeacon payload."), 7)
                        }
                    }
                    0x05 -> out += RoleHint("phone", localized("adv_payload_decoder_an_iphone_or_ipad_offering_airdrop", "an iPhone or iPad offering AirDrop"), localized("adv_payload_decoder_apple_airdrop_advertisement", "Apple AirDrop advertisement."), 5)
                    0x07 -> {
                        val model = airPodsModel(tlv.data)
                        out += RoleHint(
                            "audio-personal",
                            model ?: localized("adv_payload_decoder_airpods_or_beats_headphones", "AirPods or Beats headphones"),
                            if (model != null) localized("adv_payload_decoder_apple_proximity_pairing", "Apple Proximity Pairing: %1\$s.", model)
                            else localized("adv_payload_decoder_apple_proximity_pairing_airpods_beats", "Apple Proximity Pairing (AirPods / Beats)."),
                            8,
                        )
                    }
                    0x08 -> out += RoleHint("siri", localized("adv_payload_decoder_an_apple_device_that_just_heard_hey", "an Apple device that just heard “Hey Siri”"), localized("adv_payload_decoder_hey_siri_advertisement", "Hey Siri advertisement."), 6)
                    0x09 -> out += RoleHint("audio-speaker", localized("adv_payload_decoder_an_airplay_speaker_or_apple_tv", "an AirPlay speaker or Apple TV"), localized("adv_payload_decoder_airplay_advertisement", "AirPlay advertisement."), 5)
                    0x0B -> out += RoleHint("phone", localized("adv_payload_decoder_an_apple_device_doing_handoff", "an Apple device doing Handoff"), localized("adv_payload_decoder_handoff_advertisement", "Handoff advertisement."), 4)
                    0x0C -> out += RoleHint("phone", localized("adv_payload_decoder_an_apple_device_looking_for_instant_hotspot", "an Apple device looking for Instant Hotspot"), localized("adv_payload_decoder_tethering_target_advertisement", "Tethering-target advertisement."), 5)
                    0x0D, 0x0E -> out += RoleHint("hotspot", localized("adv_payload_decoder_an_iphone_ipad_offering_instant_hotspot", "an iPhone/iPad offering Instant Hotspot"), localized("adv_payload_decoder_tethering_source_advertisement", "Tethering-source advertisement."), 6)
                    0x0F -> out += RoleHint("phone", localized("adv_payload_decoder_an_apple_device_nearby_action", "an Apple device (Nearby Action)"), nearbyActionReason(tlv.data), 4)
                    0x10 -> out += RoleHint("phone", localized("adv_payload_decoder_an_iphone_ipad_mac_nearby_info", "an iPhone / iPad / Mac (Nearby Info)"), nearbyInfoReason(tlv.data), 5)
                    0x12 -> out += RoleHint(
                        "tag",
                        localized("adv_payload_decoder_a_find_my_network_radio", "a Find My network radio"),
                        localized("adv_payload_decoder_apple_offline_finding_airtag_find_my_accessory", "Apple Offline Finding — AirTag, Find My accessory, or an Apple device locating itself."),
                        4,
                    )
                }
            }
        }
        for (sd in device.facts.serviceData) {
            when (uuid16(sd.uuid)) {
                0xFE2C -> {
                    val bytes = hexToBytes(sd.dataHex) ?: continue
                    if (bytes.size == 3) {
                        val id = modelId24(bytes)
                        val name = FastPairModels.name(id)
                        out += RoleHint(
                            "audio-personal",
                            name ?: localized("adv_payload_decoder_a_fast_pair_accessory_often_earbuds_or", "a Fast Pair accessory (often earbuds or a speaker)"),
                            if (name != null) localized("adv_payload_decoder_google_fast_pair_model_0x_06x_in", "Google Fast Pair model %1\$s (0x%%06X), in pairing mode.", name).format(id)
                            else localized("adv_payload_decoder_google_fast_pair_model_0x_06x_in_2", "Google Fast Pair model 0x%06X, in pairing mode.").format(id),
                            if (name != null) 8 else 6,
                        )
                    } else {
                        out += RoleHint(
                            "audio-personal",
                            localized("adv_payload_decoder_a_fast_pair_accessory_already_paired_to", "a Fast Pair accessory already paired to someone"),
                            localized("adv_payload_decoder_google_fast_pair_account_key_broadcast_not", "Google Fast Pair account-key broadcast (not in pairing mode)."),
                            4,
                        )
                    }
                }
                0xFEAA -> {
                    val frame = hexToBytes(sd.dataHex)?.firstOrNull()?.toInt()?.and(0xFF)
                    when (frame) {
                        0x40, 0x41 -> out += RoleHint(
                            "tag",
                            localized("adv_payload_decoder_a_google_find_hub_tag", "a Google Find Hub tag"),
                            if (frame == 0x41) localized("adv_payload_decoder_find_hub_separated_unwanted_tracking_frame", "Find Hub separated (unwanted-tracking) frame.")
                            else localized("adv_payload_decoder_find_hub_nearby_frame", "Find Hub nearby frame."),
                            8,
                        )
                        else -> out += RoleHint("beacon", localized("adv_payload_decoder_an_eddystone_beacon", "an Eddystone beacon"), localized("adv_payload_decoder_eddystone_service_data", "Eddystone service data."), 6)
                    }
                }
            }
        }
        return out
    }

    private data class Tlv(val type: Int, val data: ByteArray)

    private fun decodeApple(bytes: ByteArray): List<Field> {
        val tlvs = appleTlvs(bytes)
        if (tlvs.isEmpty()) return listOf(Field(localized("adv_payload_decoder_apple_payload", "Apple payload"), localized("adv_payload_decoder_bytes_unparsed", "%1\$s bytes (unparsed)", bytes.size)))
        val out = ArrayList<Field>(8)
        for (tlv in tlvs) {
            out += Field(localized("adv_payload_decoder_apple_continuity_type", "Apple Continuity type"), "0x%02X · %s".format(tlv.type, appleTypeName(tlv.type)))
            out += when (tlv.type) {
                0x02 -> decodeIBeacon(tlv.data)
                0x05 -> decodeAirDrop(tlv.data)
                0x06 -> listOf(Field("HomeKit", localized("adv_payload_decoder_bytes_of_homekit_setup_data", "%1\$s bytes of HomeKit setup data", tlv.data.size)))
                0x07 -> decodeAirPods(tlv.data)
                0x08 -> decodeHeySiri(tlv.data)
                0x09 -> listOf(Field("AirPlay", localized("adv_payload_decoder_this_device_is_advertising_as_an_airplay", "This device is advertising as an AirPlay source or target.")))
                0x0A -> listOf(Field("Magic Switch", localized("adv_payload_decoder_apple_watch_wrist_unlock_related", "Apple Watch wrist / unlock related.")))
                0x0B -> decodeHandoff(tlv.data)
                0x0C -> decodeHandoffOrTetherTarget(tlv.data)
                0x0D, 0x0E -> decodeTetherSource(tlv.data)
                0x0F -> decodeNearbyAction(tlv.data)
                0x10 -> decodeNearbyInfo(tlv.data)
                0x12 -> decodeFindMy(tlv.data)
                else -> listOf(Field(localized("adv_payload_decoder_payload", "Payload"), localized("adv_payload_decoder_bytes_2", "%1\$s bytes", tlv.data.size)))
            }
        }
        return out
    }

    private fun appleTlvs(bytes: ByteArray): List<Tlv> {
        val out = ArrayList<Tlv>(3)
        var i = 0
        while (i + 2 <= bytes.size) {
            val type = bytes[i].toInt() and 0xFF
            val len = bytes[i + 1].toInt() and 0xFF
            if (len <= 0 || i + 2 + len > bytes.size) break
            out += Tlv(type, bytes.copyOfRange(i + 2, i + 2 + len))
            i += 2 + len
        }
        return out
    }

    private fun appleTypeName(type: Int): String = when (type) {
        0x02 -> "iBeacon"
        0x03 -> "AirPrint"
        0x05 -> "AirDrop"
        0x06 -> "HomeKit"
        0x07 -> localized("adv_payload_decoder_proximity_pairing_airpods_beats", "Proximity Pairing (AirPods / Beats)")
        0x08 -> "Hey Siri"
        0x09 -> "AirPlay"
        0x0A -> "Magic Switch (Watch)"
        0x0B -> "Handoff"
        0x0C -> localized("adv_payload_decoder_handoff_or_instant_hotspot_target", "Handoff or Instant Hotspot (target)")
        0x0D -> localized("adv_payload_decoder_instant_hotspot_source", "Instant Hotspot (source)")
        0x0E -> localized("adv_payload_decoder_instant_hotspot_source", "Instant Hotspot (source)")
        0x0F -> "Nearby Action"
        0x10 -> "Nearby Info"
        0x12 -> localized("adv_payload_decoder_find_my_offline_finding", "Find My / Offline Finding")
        0x13 -> localized("adv_payload_decoder_nearby_action_extended", "Nearby Action (extended)")
        0x16 -> "Nearby Info"
        else -> localized("adv_payload_decoder_unlisted", "unlisted")
    }

    private fun decodeIBeacon(data: ByteArray): List<Field> {
        // TLV payload is length-byte already consumed; data is 0x15 + 21 bytes OR 21 bytes.
        val body = when {
            data.size >= 22 && data[0] == 0x15.toByte() -> data.copyOfRange(1, 22)
            data.size >= 21 -> data.copyOfRange(0, 21)
            else -> return listOf(Field("iBeacon", localized("adv_payload_decoder_truncated_bytes", "truncated (%1\$s bytes)", data.size)))
        }
        val uuid = uuidFromBe(body, 0)
        val major = u16be(body, 16)
        val minor = u16be(body, 18)
        val tx = body[20].toInt()
        val teslaKey = uuid.filter { it.isLetterOrDigit() }.equals(
            DefaultCatalog.TESLA_IBEACON_MFG_PREFIX.drop(4),
            ignoreCase = true,
        )
        return listOf(
            Field(
                "iBeacon UUID",
                if (teslaKey) localized("adv_payload_decoder_tesla_phone_as_key_ios_background_find", "%1\$s — Tesla phone-as-key (iOS background find). Not a mall beacon.", uuid) else uuid,
            ),
            Field(localized("adv_payload_decoder_ibeacon_major_minor", "iBeacon major / minor"), "$major / $minor"),
            Field(localized("adv_payload_decoder_ibeacon_calibrated_tx", "iBeacon calibrated TX"), localized("adv_payload_decoder_dbm_at_1_m_used_to_estimate", "%1\$s dBm at 1 m (used to estimate range)", tx)),
        )
    }

    private fun decodeAirDrop(data: ByteArray): List<Field> {
        // 8 zeros, version, appleID hash(2), phone(2), email(2), email2(2), 0
        if (data.size < 18) return listOf(Field("AirDrop", localized("adv_payload_decoder_someone_nearby_is_offering_airdrop_bytes", "Someone nearby is offering AirDrop (%1\$s bytes).", data.size)))
        return listOf(
            Field("AirDrop", localized("adv_payload_decoder_someone_nearby_has_airdrop_receiving_on_hashes", "Someone nearby has AirDrop receiving on. Hashes are truncated IDs, not names.")),
            Field(localized("adv_payload_decoder_apple_id_hash_2_bytes", "Apple ID hash (2 bytes)"), data.copyOfRange(9, 11).toHexUpper()),
        )
    }

    private fun decodeAirPods(data: ByteArray): List<Field> {
        // prefix 0x01, model u16be, status, batt nibble, charge+case, lid, color, 0x00, enc 16
        if (data.size < 5) return listOf(Field("AirPods", localized("adv_payload_decoder_proximity_pairing_truncated", "Proximity Pairing, truncated.")))
        val start = if (data[0] == 0x01.toByte()) 1 else 0
        if (data.size < start + 4) return listOf(Field("AirPods", localized("adv_payload_decoder_proximity_pairing", "Proximity Pairing.")))
        val model = ((data[start].toInt() and 0xFF) shl 8) or (data[start + 1].toInt() and 0xFF)
        val status = data[start + 2].toInt() and 0xFF
        val batt = data[start + 3].toInt() and 0xFF
        val left = batt and 0x0F
        val right = (batt shr 4) and 0x0F
        val out = ArrayList<Field>(6)
        out += Field(localized("adv_payload_decoder_product", "Product"), airPodsModelName(model) ?: localized("adv_payload_decoder_apple_audio_0x_04x", "Apple audio 0x%04X").format(model))
        out += Field(localized("adv_payload_decoder_pod_position", "Pod position"), airPodsStatus(status))
        out += Field(localized("adv_payload_decoder_battery_left_right", "Battery (left / right)"), "${nibblePct(left)} / ${nibblePct(right)}")
        if (data.size > start + 4) {
            val ch = data[start + 4].toInt() and 0xFF
            val caseBatt = ch and 0x0F
            val charging = buildList {
                if (ch and 0x10 != 0) add(localized("adv_payload_decoder_case", "case"))
                if (ch and 0x20 != 0) add(localized("adv_payload_decoder_right", "right"))
                if (ch and 0x40 != 0) add(localized("adv_payload_decoder_left", "left"))
            }
            out += Field(localized("adv_payload_decoder_case_battery", "Case battery"), nibblePct(caseBatt))
            if (charging.isNotEmpty()) out += Field(localized("adv_payload_decoder_charging", "Charging"), charging.joinToString(", "))
        }
        if (data.size > start + 6) {
            out += Field(localized("adv_payload_decoder_color", "Color"), airPodsColor(data[start + 6].toInt() and 0xFF))
        }
        return out
    }

    private fun airPodsModel(data: ByteArray): String? {
        if (data.size < 4) return null
        val start = if (data[0] == 0x01.toByte()) 1 else 0
        if (data.size < start + 2) return null
        val model = ((data[start].toInt() and 0xFF) shl 8) or (data[start + 1].toInt() and 0xFF)
        return airPodsModelName(model)
    }

    private fun airPodsModelName(id: Int): String? = when (id) {
        0x0220 -> localized("adv_payload_decoder_airpods_1st_generation", "AirPods (1st generation)")
        0x0F20 -> localized("adv_payload_decoder_airpods_2nd_generation", "AirPods (2nd generation)")
        0x1320 -> localized("adv_payload_decoder_airpods_3rd_generation", "AirPods (3rd generation)")
        0x1920 -> localized("adv_payload_decoder_airpods_4th_generation", "AirPods (4th generation)")
        0x1C20 -> "AirPods 4"
        0x0E20 -> "AirPods Pro"
        0x1420 -> localized("adv_payload_decoder_airpods_pro_2nd_generation", "AirPods Pro (2nd generation)")
        0x2420 -> "AirPods Pro 2 (USB-C)"
        0x1F20 -> "AirPods Max"
        0x0A20 -> "Beats Solo3"
        0x0B20 -> "Powerbeats 3"
        0x0C20 -> "Beats Studio Buds"
        0x0D20 -> "Beats Fit Pro"
        0x1020 -> "Powerbeats Pro"
        0x1120 -> "Beats Studio Buds +"
        0x1220 -> "Beats Solo Pro"
        0x1720 -> "Beats Flex"
        0x1A20 -> "Beats Studio Pro"
        0x1B20 -> "Beats Fit Pro"
        0x0520 -> "BeatsX"
        0x0920 -> "Beats Studio³ Wireless"
        0x1620 -> "Beats Studio Buds +"
        0x2520 -> "Beats Solo 4"
        0x2620 -> "Beats Solo Buds"
        0x2D20 -> "AirPods Max 2"
        0x3820 -> "Beats 360"
        0x038F -> "Beats Studio Buds"
        else -> null
    }

    private fun airPodsStatus(status: Int): String = when (status) {
        0x01 -> localized("adv_payload_decoder_one_or_both_out_of_the_case", "One or both out of the case")
        0x02 -> localized("adv_payload_decoder_case_open", "Case open")
        0x03 -> localized("adv_payload_decoder_taken_out_in_ear_transition", "Taken out / in-ear transition")
        0x05 -> localized("adv_payload_decoder_one_in_ear", "One in ear")
        0x09 -> localized("adv_payload_decoder_both_out_not_in_ear", "Both out, not in ear")
        0x0B -> localized("adv_payload_decoder_in_ear_activity", "In-ear activity")
        0x11, 0x13 -> localized("adv_payload_decoder_both_in_ear", "Both in ear")
        0x21 -> localized("adv_payload_decoder_one_in_ear_sharing", "One in ear (sharing?)")
        0x51 -> localized("adv_payload_decoder_both_in_case_lid_open", "Both in case, lid open")
        0x55 -> localized("adv_payload_decoder_both_in_case_lid_closed", "Both in case, lid closed")
        0x75 -> localized("adv_payload_decoder_in_case", "In case")
        else -> localized("adv_payload_decoder_status_0x_02x", "Status 0x%02X").format(status)
    }

    private fun airPodsColor(v: Int): String = when (v) {
        0x00 -> localized("adv_payload_decoder_white", "White")
        0x01 -> localized("adv_payload_decoder_black", "Black")
        0x02 -> localized("adv_payload_decoder_red", "Red")
        0x03 -> localized("adv_payload_decoder_blue", "Blue")
        0x04 -> localized("adv_payload_decoder_pink", "Pink")
        0x05 -> localized("adv_payload_decoder_gray", "Gray")
        0x06 -> localized("adv_payload_decoder_silver", "Silver")
        0x07 -> localized("adv_payload_decoder_gold", "Gold")
        0x08 -> localized("adv_payload_decoder_rose_gold", "Rose gold")
        0x09 -> localized("adv_payload_decoder_space_gray", "Space gray")
        0x0A -> localized("adv_payload_decoder_dark_blue", "Dark blue")
        0x0B -> localized("adv_payload_decoder_light_blue", "Light blue")
        0x0C -> localized("adv_payload_decoder_yellow", "Yellow")
        else -> "0x%02X".format(v)
    }

    private fun nibblePct(n: Int): String = when (n) {
        in 0..9 -> "${n * 10}%"
        10, 11, 12, 13, 14 -> "100%"
        15 -> localized("adv_payload_decoder_unknown_not_present", "unknown / not present")
        else -> "$n"
    }

    private fun decodeHeySiri(data: ByteArray): List<Field> {
        if (data.size < 6) return listOf(Field("Hey Siri", localized("adv_payload_decoder_siri_was_just_triggered_on_a_nearby", "Siri was just triggered on a nearby Apple device.")))
        val klass = u16be(data, 4)
        val device = when (klass) {
            0x0002 -> "iPhone"
            0x0003 -> "iPad"
            0x0007 -> "HomePod"
            0x0009 -> "Mac"
            0x000A -> localized("adv_payload_decoder_watch", "Watch")
            else -> localized("adv_payload_decoder_class_0x_04x", "class 0x%04X").format(klass)
        }
        return listOf(
            Field("Hey Siri", localized("adv_payload_decoder_a_just_heard_a_siri_trigger_the", "A %1\$s just heard a Siri trigger. The packet carries a short voice hash, not the words.", device)),
        )
    }

    private fun decodeHandoff(data: ByteArray): List<Field> =
        listOf(Field("Handoff", localized("adv_payload_decoder_continuity_handoff_a_task_can_be_continued", "Continuity Handoff: a task can be continued on another Apple device. Payload is encrypted.")))

    private fun decodeHandoffOrTetherTarget(data: ByteArray): List<Field> =
        if (data.size >= 14) decodeHandoff(data)
        else listOf(Field(localized("adv_payload_decoder_instant_hotspot_looking", "Instant Hotspot (looking)"), localized("adv_payload_decoder_this_apple_device_is_searching_for_a", "This Apple device is searching for a paired phone’s hotspot.")))

    private fun decodeTetherSource(data: ByteArray): List<Field> {
        if (data.size < 6) return listOf(Field("Instant Hotspot", localized("adv_payload_decoder_an_iphone_ipad_is_offering_a_personal", "An iPhone/iPad is offering a personal hotspot.")))
        val batt = data[2].toInt() and 0xFF
        val cell = if (data.size >= 5) u16be(data, 3) else -1
        val bars = if (data.size >= 6) data[5].toInt() and 0xFF else -1
        val cellName = when (cell) {
            0, 6 -> "4G"
            1 -> "1xRTT"
            2 -> "GPRS"
            3 -> "EDGE"
            4, 5 -> "3G"
            7 -> "LTE"
            8 -> "5G"
            else -> if (cell >= 0) localized("adv_unknown_type", "type %1\$s", cell) else null
        }
        return listOf(
            Field(
                localized("adv_payload_decoder_instant_hotspot_offering", "Instant Hotspot (offering)"),
                buildString {
                    append(localized("adv_payload_decoder_paired_iphone_ipad_hotspot", "Paired iPhone/iPad hotspot"))
                    if (batt in 0..100) append(localized("adv_payload_decoder_phone_battery", " · phone battery %1\$s%%", batt))
                    cellName?.let { append(" · $it") }
                    if (bars in 0..5) append(localized("adv_payload_decoder_5_bars", " · %1\$s/5 bars", bars))
                },
            ),
        )
    }

    private fun decodeNearbyAction(data: ByteArray): List<Field> {
        if (data.isEmpty()) return listOf(Field("Nearby Action", "Apple Nearby Action"))
        val action = if (data.size >= 2) data[1].toInt() and 0xFF else data[0].toInt() and 0xFF
        val name = nearbyActionName(action)
        return listOf(Field("Nearby Action", name))
    }

    private fun nearbyActionReason(data: ByteArray): String {
        val action = if (data.size >= 2) data[1].toInt() and 0xFF else return localized("adv_payload_decoder_nearby_action_advertisement", "Nearby Action advertisement.")
        return localized("adv_payload_decoder_nearby_action", "Nearby Action: %1\$s.", nearbyActionName(action))
    }

    private fun nearbyActionName(action: Int): String = when (action) {
        0x01 -> localized("adv_payload_decoder_apple_tv_setup", "Apple TV setup")
        0x04 -> localized("adv_payload_decoder_mobile_backup", "Mobile backup")
        0x05 -> localized("adv_payload_decoder_watch_setup", "Watch setup")
        0x06 -> localized("adv_payload_decoder_apple_tv_pair", "Apple TV pair")
        0x08 -> localized("adv_payload_decoder_wi_fi_password_sharing_prompting_nearby_iphones", "Wi-Fi password sharing (prompting nearby iPhones)")
        0x09 -> localized("adv_payload_decoder_ios_setup", "iOS setup")
        0x0A -> localized("adv_payload_decoder_repair", "Repair")
        0x0B -> localized("adv_payload_decoder_speaker_setup", "Speaker setup")
        0x0C -> "Apple Pay"
        0x0D -> localized("adv_payload_decoder_whole_home_audio_setup", "Whole-home audio setup")
        0x0F -> localized("adv_payload_decoder_answered_a_call", "Answered a call")
        0x10 -> localized("adv_payload_decoder_ended_a_call", "Ended a call")
        0x13 -> localized("adv_payload_decoder_remote_autofill", "Remote AutoFill")
        0x14 -> localized("adv_payload_decoder_companion_link_proximity", "Companion Link proximity")
        0x17 -> localized("adv_payload_decoder_remote_display", "Remote display")
        else -> localized("adv_payload_decoder_action_0x_02x", "action 0x%02X").format(action)
    }

    private fun decodeNearbyInfo(data: ByteArray): List<Field> {
        if (data.isEmpty()) return listOf(Field("Nearby Info", localized("adv_payload_decoder_apple_device_usage_state", "Apple device usage state.")))
        val status = data[0].toInt() and 0xFF
        val action = status and 0x0F
        val flagsHi = (status shr 4) and 0x0F
        val dataFlags = if (data.size > 1) data[1].toInt() and 0xFF else 0
        val activity = when (action) {
            0x00 -> localized("adv_payload_decoder_activity_unknown", "activity unknown")
            0x01 -> localized("adv_payload_decoder_activity_reporting_off", "activity reporting off")
            0x03 -> localized("adv_payload_decoder_idle_screen_locked", "idle (screen locked)")
            0x05 -> localized("adv_payload_decoder_audio_playing_screen_locked", "audio playing, screen locked")
            0x07 -> localized("adv_payload_decoder_active_screen_on", "active (screen on)")
            0x09 -> localized("adv_payload_decoder_screen_on_video_playing", "screen on, video playing")
            0x0A -> localized("adv_payload_decoder_watch_on_wrist_and_unlocked", "Watch on wrist and unlocked")
            0x0B -> localized("adv_payload_decoder_recent_interaction", "recent interaction")
            0x0D -> localized("adv_payload_decoder_user_is_driving", "user is driving")
            0x0E -> localized("adv_payload_decoder_phone_or_facetime_call", "phone or FaceTime call")
            else -> localized("adv_payload_decoder_activity_0x_x", "activity 0x%X").format(action)
        }
        val extras = buildList {
            if (flagsHi and 0x1 != 0) add(localized("adv_payload_decoder_primary_icloud_device", "primary iCloud device"))
            if (flagsHi and 0x4 != 0) add(localized("adv_payload_decoder_airdrop_receiving_on", "AirDrop receiving on"))
            if (dataFlags and 0x04 != 0) add(localized("adv_payload_decoder_wi_fi_on", "Wi-Fi on"))
            if (dataFlags and 0x01 != 0) add(localized("adv_payload_decoder_airpods_connected", "AirPods connected"))
            if (dataFlags and 0x20 != 0) add(localized("adv_payload_decoder_watch_locked", "Watch locked"))
        }
        return listOf(
            Field(
                localized("adv_payload_decoder_what_the_apple_device_is_doing", "What the Apple device is doing"),
                buildString {
                    append(activity.replaceFirstChar { it.uppercase() })
                    if (extras.isNotEmpty()) {
                        append(". ")
                        append(extras.joinToString("; "))
                    }
                    append(".")
                },
            ),
        )
    }

    private fun nearbyInfoReason(data: ByteArray): String {
        if (data.isEmpty()) return localized("adv_payload_decoder_nearby_info_advertisement", "Nearby Info advertisement.")
        val action = data[0].toInt() and 0x0F
        return when (action) {
            0x03 -> localized("adv_payload_decoder_phone_is_idle_locked", "Phone is idle / locked.")
            0x05 -> localized("adv_payload_decoder_audio_playing_with_the_screen_locked", "Audio playing with the screen locked.")
            0x07 -> localized("adv_payload_decoder_screen_is_on_someone_is_using_it", "Screen is on — someone is using it.")
            0x0D -> localized("adv_payload_decoder_device_reports_the_user_is_driving", "Device reports the user is driving.")
            0x0E -> localized("adv_payload_decoder_in_a_phone_or_facetime_call", "In a phone or FaceTime call.")
            else -> localized("adv_payload_decoder_nearby_info_advertisement", "Nearby Info advertisement.")
        }
    }

    private fun decodeFindMy(data: ByteArray): List<Field> {
        if (data.isEmpty()) return listOf(Field("Find My", localized("adv_payload_decoder_offline_finding_advertisement", "Offline Finding advertisement.")))
        val status = data[0].toInt() and 0xFF
        val maintained = status and 0x04 != 0
        val batt = (status shr 6) and 0x3
        val battName = when (batt) {
            0 -> localized("adv_payload_decoder_full", "full")
            1 -> localized("adv_payload_decoder_medium", "medium")
            2 -> localized("adv_payload_decoder_low", "low")
            else -> localized("adv_payload_decoder_critical", "critical")
        }
        val keyLen = (data.size - 1).coerceAtLeast(0)
        return listOf(
            Field(
                localized("adv_payload_decoder_find_my_offline_finding", "Find My / Offline Finding"),
                buildString {
                    append(localized("adv_payload_decoder_broadcasting_a_public_key_so_the_find", "Broadcasting a public key so the Find My network can report a location. "))
                    append(localized("adv_payload_decoder_used_by_airtags_find_my_accessories_and", "Used by AirTags, Find My accessories, and Apple devices locating themselves. "))
                    if (maintained) append(localized("adv_payload_decoder_owner_seen_recently", "Owner seen recently. "))
                    else append(localized("adv_payload_decoder_owner_not_seen_in_the_current_key", "Owner not seen in the current key window. "))
                    if (maintained || batt in 0..3) append(localized("adv_payload_decoder_battery", "Battery %1\$s. ", battName))
                    append(localized("adv_payload_decoder_byte_key_fragment_not_a_serial_number", "(%1\$s-byte key fragment — not a serial number.)", keyLen))
                },
            ),
        )
    }

    private fun decodeFastPair(bytes: ByteArray): List<Field> {
        if (bytes.size == 3) {
            val id = modelId24(bytes)
            val name = FastPairModels.name(id)
            return listOf(
                Field("Google Fast Pair", localized("adv_payload_decoder_in_pairing_mode_android_will_pop_a", "In pairing mode — Android will pop a tap-to-pair card.")),
                Field(
                    localized("adv_payload_decoder_model_id", "Model ID"),
                    if (name != null) "$name  (0x%06X)".format(id) else localized("adv_payload_decoder_0x_06x_not_in_the_local_name", "0x%06X (not in the local name list)").format(id),
                ),
            )
        }
        if (bytes.isEmpty()) return emptyList()
        val verFlags = bytes[0].toInt() and 0xFF
        val version = (verFlags shr 4) and 0x0F
        val ui = if (bytes.size > 1) {
            val lt = bytes[1].toInt() and 0xFF
            val type = lt and 0x0F
            when (type) {
                0x0 -> localized("adv_payload_decoder_wants_to_show_a_pairing_card", "wants to show a pairing card")
                0x2 -> localized("adv_payload_decoder_hiding_the_pairing_card_e_g_buds", "hiding the pairing card (e.g. buds back in the case)")
                else -> localized("adv_payload_decoder_filter_type", "filter type %1\$s", type)
            }
        } else localized("adv_payload_decoder_account_key_bloom_filter", "account-key bloom filter")
        return listOf(
            Field(
                "Google Fast Pair",
                localized("adv_payload_decoder_already_paired_to_an_account_not_in", "Already paired to an account (not in pairing mode). %1\$s. Version %2\$s.", ui, version),
            ),
        )
    }

    private fun decodeEddystone(bytes: ByteArray): List<Field> {
        if (bytes.isEmpty()) return emptyList()
        return when (bytes[0].toInt() and 0xFF) {
            0x00 -> {
                if (bytes.size < 18) listOf(Field("Eddystone-UID", localized("adv_payload_decoder_truncated", "truncated")))
                else listOf(
                    Field(localized("adv_payload_decoder_eddystone_uid_namespace", "Eddystone-UID namespace"), bytes.copyOfRange(2, 12).toHexUpper()),
                    Field(localized("adv_payload_decoder_eddystone_uid_instance", "Eddystone-UID instance"), bytes.copyOfRange(12, 18).toHexUpper()),
                )
            }
            0x10 -> listOf(Field("Eddystone-URL", eddystoneUrl(bytes) ?: localized("adv_payload_decoder_bytes", "%1\$s bytes", bytes.size)))
            0x20 -> listOf(Field("Eddystone-TLM", localized("adv_payload_decoder_telemetry_battery_temperature_advert_count", "telemetry (battery / temperature / advert count)")))
            0x30 -> listOf(Field("Eddystone-EID", localized("adv_payload_decoder_ephemeral_id_rotating", "ephemeral ID (rotating)")))
            0x40, 0x41 -> {
                val mode = if (bytes[0].toInt() and 0xFF == 0x41) localized("adv_payload_decoder_separated_unwanted_tracking_mode", "separated (unwanted-tracking mode)") else localized("adv_payload_decoder_nearby_with_owner", "nearby / with owner")
                val eidLen = when {
                    bytes.size >= 33 -> 32
                    bytes.size >= 21 -> 20
                    else -> (bytes.size - 1).coerceAtLeast(0)
                }
                val eid = if (eidLen > 0) bytes.copyOfRange(1, 1 + eidLen).toHexUpper() else ""
                listOf(
                    Field("Find Hub", mode),
                    Field("Find Hub EID", eid.ifBlank { localized("adv_payload_decoder_bytes", "%1\$s bytes", bytes.size) }),
                )
            }
            else -> listOf(Field("Eddystone", localized("adv_payload_decoder_frame_0x_02x", "frame 0x%02X").format(bytes[0])))
        }
    }

    private fun eddystoneUrl(bytes: ByteArray): String? {
        if (bytes.size < 3) return null
        val scheme = when (bytes[2].toInt() and 0xFF) {
            0 -> "http://www."
            1 -> "https://www."
            2 -> "http://"
            3 -> "https://"
            else -> return null
        }
        val expansions = arrayOf(
            ".com/", ".org/", ".edu/", ".net/", ".info/", ".biz/", ".gov/",
            ".com", ".org", ".edu", ".net", ".info", ".biz", ".gov",
        )
        val sb = StringBuilder(scheme)
        for (i in 3 until bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b < expansions.size) sb.append(expansions[b]) else if (b in 0x20..0x7E) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun decodeMicrosoft(bytes: ByteArray): List<Field> {
        if (bytes.isEmpty()) return emptyList()
        if (bytes[0] == 0x01.toByte() && bytes.size >= 2) {
            val type = bytes[1].toInt() and 0x1F
            val kind = when (type) {
                1 -> "Xbox"
                6 -> "iPhone"
                7 -> "iPad"
                8 -> "Android"
                9 -> localized("adv_payload_decoder_windows_desktop", "Windows desktop")
                11 -> localized("adv_payload_decoder_windows_phone", "Windows phone")
                12 -> "Linux"
                13 -> "Windows IoT"
                14 -> "Surface Hub"
                15 -> localized("adv_payload_decoder_windows_laptop", "Windows laptop")
                16 -> localized("adv_payload_decoder_windows_tablet", "Windows tablet")
                else -> "type $type"
            }
            return listOf(Field("Microsoft Nearby Sharing / Swift Pair", localized("adv_payload_decoder_a_is_advertising_for_quick_pairing_or", "A %1\$s is advertising for quick pairing or sharing.", kind)))
        }
        return listOf(Field(localized("adv_payload_decoder_microsoft_manufacturer_data", "Microsoft manufacturer data"), localized("adv_payload_decoder_bytes", "%1\$s bytes", bytes.size)))
    }

    private fun decodeAltBeacon(bytes: ByteArray): List<Field> {
        if (bytes.size >= 22 && bytes[0] == 0xBE.toByte() && bytes[1] == 0xAC.toByte()) {
            return listOf(
                Field("AltBeacon UUID", uuidFromBe(bytes, 2)),
                Field(localized("adv_payload_decoder_altbeacon_major_minor", "AltBeacon major / minor"), "${u16be(bytes, 18)} / ${u16be(bytes, 20)}"),
            )
        }
        return emptyList()
    }

    private fun modelId24(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xFF) shl 16) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            (bytes[2].toInt() and 0xFF)

    private fun u16be(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun uuidFromBe(data: ByteArray, offset: Int): String {
        fun h(i: Int) = "%02x".format(data[offset + i].toInt() and 0xFF)
        return "${h(0)}${h(1)}${h(2)}${h(3)}-${h(4)}${h(5)}-${h(6)}${h(7)}-${h(8)}${h(9)}-${h(10)}${h(11)}${h(12)}${h(13)}${h(14)}${h(15)}"
    }

    private fun uuid16(uuid: String): Int? {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex.length == 4 -> hex.toIntOrNull(16)
            hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB") ->
                hex.substring(4, 8).toIntOrNull(16)
            else -> null
        }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val h = hex.filter { it.isLetterOrDigit() }
        if (h.isEmpty() || h.length % 2 != 0) return null
        return ByteArray(h.length / 2) { i ->
            h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
