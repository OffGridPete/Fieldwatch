package app.fieldwatch.domain

import app.fieldwatch.i18n.codLabel

import app.fieldwatch.i18n.localized

/**
 * Plain-language decode of advertised identity. Guesses are what the radio
 * is broadcasting, not a visual identification.
 */
object DeviceExplain {
    data class Guess(
        val headline: String,
        val because: String,
        val confidence: Confidence,
        /** Semantic row label; never derive classification from a translated headline. */
        val listCore: String? = null,
    )

    enum class Confidence { HIGH, MEDIUM, LOW }

    fun guess(device: Sighting, signatureNames: List<String>): Guess {
        val hints = ArrayList<Hint>(8)
        val appearance = device.facts.appearance?.let { RadioDb.appearance(it) }
        appearanceHint(appearance)?.let { hints += it }
        CodDecoder.decodeOrNull(device.facts.deviceClass)?.let { codHint(it)?.let { h -> hints += h } }
        hints += uuidHints(device.serviceUuids + device.facts.serviceData.map { it.uuid })
        hints += AdvPayloadDecoder.roleHints(device).map {
            Hint(it.bucket, it.label, it.reason, it.weight)
        }
        hints += signatureHints(signatureNames)
        if (device.kind == RadioKind.WIFI) hints += wifiHints(device, signatureNames)

        if (hints.isEmpty()) {
            return Guess(
                headline = if (device.kind == RadioKind.WIFI) {
                    localized("device_explain_wi_fi_access_point", "Wi-Fi access point")
                } else {
                    localized("device_explain_bluetooth_le_advertiser", "Bluetooth LE advertiser")
                },
                because = localized("device_explain_it_is_on_the_air_but_it", "It is on the air, but it did not advertise a product class (no Appearance, Class of Device, or well-known service that names a type)."),
                confidence = Confidence.LOW,
            )
        }
        val grouped = LinkedHashMap<String, Hint>()
        for (hint in hints.sortedByDescending { it.weight }) {
            val key = hint.bucket
            val prev = grouped[key]
            if (prev == null || hint.weight > prev.weight) grouped[key] = hint
        }
        val best = grouped.values.maxBy { it.weight }
        val support = grouped.values
            .filter { it.bucket == best.bucket || it.weight >= 3 }
            .map { it.reason }
            .distinct()
        val confidence = when {
            best.weight >= 6 -> Confidence.HIGH
            best.weight >= 3 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val hedge = when (confidence) {
            Confidence.HIGH -> localized("device_explain_most_likely", "Most likely")
            Confidence.MEDIUM -> localized("device_explain_probably", "Probably")
            Confidence.LOW -> localized("device_explain_could_be", "Could be")
        }
        return Guess(
            headline = localized("device_explain_", "%1\$s %2\$s", hedge, best.label),
            because = support.joinToString(" ") +
                localized("device_explain_this_is_what_the_device_is_advertising", " This is what the device is advertising, not a visual ID."),
            confidence = confidence,
            listCore = if (best.generic) null else tidyHeadline(best.label),
        )
    }

    /**
     * Compact Live-row title from the same guess as detail. Null if we only
     * know it is an unnamed advertiser — caller may fall back to vendor.
     */
    fun listLabel(device: Sighting, signatureNames: List<String> = emptyList()): String? {
        val guess = guess(device, signatureNames)
        val core = guess.listCore
        val vendor = device.vendor?.trim()?.takeIf { it.isNotBlank() && it.length <= 24 }
        if (core != null) {
            return if (vendor != null && !core.contains(vendor, ignoreCase = true)) {
                "$vendor · $core"
            } else {
                core
            }
        }
        if (vendor != null) return localized("device_explain_device", "%1\$s device", vendor)
        return null
    }

    private fun tidyHeadline(headline: String): String {
        var s = headline
            .removePrefix("Most likely ")
            .removePrefix("Probably ")
            .removePrefix("Could be ")
            .trim()
        s = s.replace(Regex("""\s*\([^)]*\)"""), "").trim()
        s = s.removePrefix("an ").removePrefix("a ").trim()
        if (s.isEmpty()) return headline
        return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun flagsExplain(flags: Int): String = buildList {
        if (flags and 0x01 != 0) {
            add(localized("device_explain_limited_discoverable_briefly_looking_for_a_nearby", "Limited-discoverable: briefly looking for a nearby connection."))
        }
        if (flags and 0x02 != 0) {
            add(localized("device_explain_discoverable_other_ble_devices_can_find_it", "Discoverable: other BLE devices can find it."))
        }
        if (flags and 0x04 != 0) {
            add(localized("device_explain_ble_only_no_classic_bluetooth_headsets_file", "BLE-only: no classic Bluetooth (headsets/file-send radio)."))
        } else {
            add(localized("device_explain_may_also_do_classic_bluetooth_br_edr", "May also do classic Bluetooth (BR/EDR) as well as BLE."))
        }
        if (flags and 0x08 != 0 || flags and 0x10 != 0) {
            add(localized("device_explain_dual_mode_chip_ble_and_classic_can", "Dual-mode chip: BLE and classic can run together."))
        }
    }.joinToString(" ")

    fun phyExplain(label: String): String = when {
        label.contains("Coded") -> localized("device_explain_long_range_ble_slower_farther", "%1\$s — long-range BLE (slower, farther)", label)
        label.contains("2M") -> localized("device_explain_faster_ble_bluetooth_5", "%1\$s — faster BLE (Bluetooth 5)", label)
        label.contains("1M") -> localized("device_explain_standard_ble_radio", "%1\$s — standard BLE radio", label)
        else -> label
    }

    fun addressExplain(device: Sighting): String {
        val type = device.facts.addressType
        return when {
            device.kind == RadioKind.WIFI && device.randomized ->
                localized("device_explain_locally_administered_bssid_vehicle_mesh_and_guest", "Locally administered BSSID. Vehicle, mesh, and guest APs often keep this address. Not a rotating phone MAC.")
            MacUtil.isLocallyAdministered(device.mac) && !device.randomized ->
                localized("device_explain_locally_administered_address_the_local_bit_is", "Locally administered address. The local bit is set, so this is not an IEEE factory assignment.")
            type.equals("Public", true) && !device.randomized ->
                localized("device_explain_public_factory_address_stable_ieee_assigned", "Public factory address (stable, IEEE-assigned).")
            type.equals("Random", true) || device.randomized ->
                localized("device_explain_random_privacy_address_the_mac_can_change", "Random / privacy address. The MAC can change, so this is not a lasting identity.")
            type.equals("Anonymous", true) ->
                localized("device_explain_anonymous_the_stack_hid_the_address", "Anonymous: the stack hid the address.")
            else ->
                listOfNotNull(type, localized("device_explain_universal_ieee_address_stable_oui", "Universal IEEE address (stable OUI).")).joinToString(" · ")
        }
    }

    fun rssiBand(rssi: Int): String = when {
        !Rssi.measured(rssi) -> localized("device_explain_not_available_2", "not available")
        rssi >= -45 -> localized("device_explain_very_strong", "very strong")
        rssi >= -60 -> localized("device_explain_strong", "strong")
        rssi >= -75 -> localized("device_explain_medium", "medium")
        rssi >= -88 -> localized("device_explain_weak", "weak")
        else -> localized("device_explain_very_weak", "very weak")
    }

    fun rssiExplain(rssi: Int): String =
        if (!Rssi.measured(rssi)) localized("device_explain_not_available", "Not available")
        else "%d dBm · %s".format(rssi, rssiBand(rssi))

    fun wifiSecurityExplain(raw: String): String {
        val bits = ArrayList<String>(4)
        val u = raw.uppercase()
        when {
            "SAE" in u || "WPA3" in u -> bits += localized("device_explain_wpa3_password_sae_handshake", "WPA3 password (SAE handshake)")
            "OWE" in u -> bits += localized("device_explain_enhanced_open_encrypted_no_password", "Enhanced Open (encrypted, no password)")
            "PSK" in u && "WPA2" in u -> bits += localized("device_explain_wpa2_password_psk", "WPA2 password (PSK)")
            "PSK" in u || "WPA" in u -> bits += localized("device_explain_wi_fi_password_wpa_psk", "Wi-Fi password (WPA/PSK)")
            "802.1X" in u || "EAP" in u -> bits += localized("device_explain_enterprise_login_802_1x", "Enterprise login (802.1X)")
            "WEP" in u -> bits += localized("device_explain_wep_old_weak", "WEP (old, weak)")
            "ESS" in u && bits.isEmpty() -> bits += localized("device_explain_open_or_encryption_not_parsed", "Open or encryption not parsed")
        }
        when {
            "CCMP" in u || "GCMP" in u -> bits += localized("device_explain_aes_encryption", "AES encryption")
            "TKIP" in u -> bits += localized("device_explain_tkip_older_weaker_cipher", "TKIP (older, weaker cipher)")
        }
        if ("WPS" in u) bits += localized("device_explain_wps_setup_is_enabled", "WPS setup is enabled")
        if ("MESH" in u) bits += localized("device_explain_mesh_node", "mesh node")
        if ("IBSS" in u) bits += localized("device_explain_ad_hoc_network", "ad-hoc network")
        if ("ESS" in u) bits += localized("device_explain_infrastructure_access_point", "infrastructure access point")
        return if (bits.isEmpty()) raw else bits.distinct().joinToString(". ") + "."
    }

    fun uuidGloss(uuid: String): String? {
        val name = RadioDb.serviceUuid(uuid)
        val short = uuid16(uuid) ?: return name
        val extra = when (short) {
            0x1800 -> localized("device_explain_connection_basics", "connection basics")
            0x1801 -> localized("device_explain_attribute_protocol", "attribute protocol")
            0x180A -> localized("device_explain_model_serial_firmware", "model / serial / firmware")
            0x180F -> localized("device_explain_battery_level", "battery level")
            0x1812 -> localized("device_explain_keyboard_mouse_or_gamepad", "keyboard, mouse, or gamepad")
            0x180D -> localized("device_explain_heart_rate_sensor", "heart-rate sensor")
            0x1810 -> localized("device_explain_blood_pressure_sensor", "blood-pressure sensor")
            0x181A -> localized("device_explain_temperature_humidity_style_sensor", "temperature / humidity style sensor")
            0x1844, 0x1845, 0x1846 -> localized("device_explain_le_cycling_power_speed", "LE cycling power/speed")
            0x1850, 0x184E, 0x184F -> "LE Audio"
            0xFE2C -> localized("device_explain_google_fast_pair_often_buds_speakers", "Google Fast Pair (often buds/speakers)")
            0xFD5A -> "Samsung SmartTag"
            0xFD44 -> localized("device_explain_apple_find_my_related", "Apple Find My related")
            0xFEED, 0xFEDD -> localized("device_explain_tile_tracker", "Tile tracker")
            0xFD50 -> "Tuya IoT"
            0xFEBE, 0xFE21 -> "Bose"
            0xFE78 -> localized("device_explain_hp_printer", "HP printer")
            0xFE07 -> localized("device_explain_sonos_speaker", "Sonos speaker")
            0xFEAF, 0xFEB0 -> "Nest Weave"
            0xFCBF -> "ASSA ABLOY Opening Solutions"
            0xFE24 -> localized("device_explain_august_home_lock", "August Home lock")
            0xFCF4 -> "Allegion / Schlage"
            0xFCB2 -> localized("device_explain_apple_not_assa_abloy", "Apple (not ASSA ABLOY)")
            else -> null
        }
        return when {
            name != null && extra != null -> "$name — $extra"
            name != null -> name
            extra != null -> extra
            else -> null
        }
    }

    private data class Hint(
        val bucket: String,
        val label: String,
        val reason: String,
        val weight: Int,
        val generic: Boolean = false,
    )

    private fun appearanceHint(name: String?): Hint? {
        if (name.isNullOrBlank() || name.equals("Unknown", true)) return null
        val n = name.lowercase()
        val (bucket, label, w) = when {
            "ear" in n || "headphone" in n || "headset" in n || "hearable" in n || "hearing" in n ->
                Triple("audio-personal", localized("device_explain_earbuds_or_headphones", "earbuds or headphones"), 7)
            "speaker" in n || "loudspeaker" in n || "hifi" in n ->
                Triple("audio-speaker", localized("device_explain_a_speaker", "a speaker"), 7)
            "mouse" in n -> Triple("mouse", localized("device_explain_a_mouse", "a mouse"), 8)
            "keyboard" in n -> Triple("keyboard", localized("device_explain_a_keyboard", "a keyboard"), 8)
            "gamepad" in n || "joystick" in n -> Triple("gamepad", localized("device_explain_a_game_controller", "a game controller"), 7)
            "watch" in n -> Triple("watch", localized("device_explain_a_watch_or_wrist_wearable", "a watch or wrist wearable"), 7)
            "phone" in n -> Triple("phone", localized("device_explain_a_phone", "a phone"), 6)
            "laptop" in n || "computer" in n || "desktop" in n || "tablet" in n ->
                Triple("computer", localized("device_explain_a_computer_or_tablet", "a computer or tablet"), 6)
            "tag" in n || "keyring" in n -> Triple("tag", localized("device_explain_a_finder_tag_tracker", "a finder tag / tracker"), 6)
            "remote" in n -> Triple("remote", localized("device_explain_a_remote_control", "a remote control"), 6)
            "hid" in n -> Triple("hid", localized("device_explain_an_input_device_keyboard_mouse_or_similar", "an input device (keyboard, mouse, or similar)"), 4)
            "heart" in n -> Triple("health", localized("device_explain_a_heart_rate_monitor", "a heart-rate monitor"), 7)
            "glucose" in n || "oximeter" in n || "blood pressure" in n || "thermometer" in n ->
                Triple("health", localized("device_explain_a_health_sensor", "a health sensor"), 6)
            "display" in n || "monitor" in n -> Triple("display", localized("device_explain_a_display_or_tv_stick", "a display or TV stick"), 4)
            "clock" in n -> Triple("clock", localized("device_explain_a_clock", "a clock"), 5)
            "glasses" in n -> Triple("glasses", localized("device_explain_smart_glasses", "smart glasses"), 6)
            else -> Triple("other", name, 3)
        }
        return Hint(bucket, label, localized("device_explain_it_advertises_appearance_as", "It advertises Appearance as %1\$s.", name), w)
    }

    private fun codHint(cod: CodDecoder.Decoded): Hint? {
        val minor = cod.minor.lowercase()
        val major = cod.major.lowercase()
        val (bucket, label, w) = when {
            "headphone" in minor || "headset" in minor || "hands-free" in minor ->
                Triple("audio-personal", localized("device_explain_earbuds_or_a_headset", "earbuds or a headset"), 6)
            "loudspeaker" in minor || "portable audio" in minor || "hifi" in minor || "car audio" in minor ->
                Triple("audio-speaker", localized("device_explain_a_speaker", "a speaker"), 6)
            "pointing" in minor || minor == "mouse" -> Triple("mouse", localized("device_explain_a_mouse", "a mouse"), 7)
            "keyboard" in minor -> Triple("keyboard", localized("device_explain_a_keyboard", "a keyboard"), 7)
            "gamepad" in minor || "joystick" in minor -> Triple("gamepad", localized("device_explain_a_game_controller", "a game controller"), 6)
            "smartphone" in minor || (major == "phone" && "uncategorized" !in minor) ->
                Triple("phone", localized("device_explain_a_phone", "a phone"), 5)
            "laptop" in minor || "tablet" in minor || "desktop" in minor ->
                Triple("computer", localized("device_explain_a_computer", "a computer"), 5)
            "wristwatch" in minor -> Triple("watch", localized("device_explain_a_watch", "a watch"), 6)
            "heart" in minor || "pulse" in minor || "glucose" in minor || "oximeter" in minor ->
                Triple("health", localized("device_explain_a_health_sensor", "a health sensor"), 6)
            "audio" in major -> Triple("audio-personal", localized("device_explain_an_audio_device", "an audio device"), 3)
            "peripheral" in major -> Triple("hid", localized("device_explain_an_input_accessory", "an input accessory"), 3)
            "uncategorized" in major || "miscellaneous" in major -> return null
            else -> return null
        }
        val shown = if (cod.minor.isNotBlank() && cod.minor != "Uncategorized") {
            "${codLabel(cod.major)} / ${codLabel(cod.minor)}"
        } else {
            codLabel(cod.major)
        }
        return Hint(bucket, label, localized("device_explain_class_of_device_says", "Class of Device says %1\$s.", shown), w)
    }

    private fun uuidHints(uuids: List<String>): List<Hint> {
        val out = ArrayList<Hint>(4)
        for (uuid in uuids) {
            val id = uuid16(uuid) ?: continue
            when (id) {
                0x1812 -> out += Hint("hid", localized("device_explain_a_keyboard_mouse_or_gamepad", "a keyboard, mouse, or gamepad"), localized("device_explain_it_offers_the_hid_human_interface_service", "It offers the HID (human-interface) service."), 5)
                0x1108, 0x1112, 0x111E, 0x110B, 0x110A, 0x1131, 0x1203 ->
                    out += Hint("audio-personal", localized("device_explain_headphones_a_headset_or_a_speaker", "headphones, a headset, or a speaker"), localized("device_explain_it_offers_a_classic_audio_headset_service", "It offers a classic audio / headset service."), 5)
                0x184E, 0x184F, 0x1850, 0x1851 ->
                    out += Hint("audio-personal", localized("device_explain_le_audio_earbuds_or_a_speaker", "LE Audio earbuds or a speaker"), localized("device_explain_it_offers_bluetooth_le_audio_services", "It offers Bluetooth LE Audio services."), 6)
                0x180D -> out += Hint("health", localized("device_explain_a_heart_rate_monitor", "a heart-rate monitor"), localized("device_explain_it_offers_the_heart_rate_service", "It offers the Heart Rate service."), 6)
                0x1810 -> out += Hint("health", localized("device_explain_a_blood_pressure_monitor", "a blood-pressure monitor"), localized("device_explain_it_offers_the_blood_pressure_service", "It offers the Blood Pressure service."), 6)
                0x181A -> out += Hint("sensor", localized("device_explain_an_environmental_sensor", "an environmental sensor"), localized("device_explain_it_offers_environmental_sensing", "It offers Environmental Sensing."), 4)
                0xFE2C -> out += Hint("audio-personal", localized("device_explain_earbuds_or_a_speaker", "earbuds or a speaker"), localized("device_explain_google_fast_pair_is_present_common_on", "Google Fast Pair is present (common on buds and speakers)."), 4)
                0xFD5A -> out += Hint("tag", localized("device_explain_a_samsung_smarttag", "a Samsung SmartTag"), localized("device_explain_smarttag_service_uuid", "SmartTag service UUID."), 7)
                0xFD44 -> out += Hint("tag", localized("device_explain_an_apple_find_my_accessory", "an Apple Find My accessory"), localized("device_explain_find_my_related_uuid", "Find My related UUID."), 6)
                0xFEED, 0xFEDD -> out += Hint("tag", localized("device_explain_a_tile_tracker", "a Tile tracker"), localized("device_explain_tile_service_uuid", "Tile service UUID."), 7)
            }
        }
        return out
    }

    private fun signatureHints(names: List<String>): List<Hint> {
        return names.mapNotNull { raw ->
            if (isGenericSignatureName(raw)) return@mapNotNull null
            val n = raw.lowercase()
            when {
                "airtag" in n || n == "find my" || "find hub" in n || "dult" in n ->
                    Hint(
                        "tag",
                        when {
                            "dult" in n -> localized("device_explain_a_dult_finder_tag", "a DULT finder tag")
                            "find hub" in n -> localized("device_explain_a_google_find_hub_tag", "a Google Find Hub tag")
                            else -> localized("device_explain_an_apple_airtag_find_my_tag", "an Apple AirTag / Find My tag")
                        },
                        localized("device_explain_matched_signature", "Matched signature %1\$s.", raw),
                        8,
                    )
                "apple device" in n ->
                    Hint("phone", localized("device_explain_an_iphone_ipad_or_mac", "an iPhone, iPad, or Mac"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                "apple audio" in n ->
                    Hint("audio-personal", localized("device_explain_airpods_beats_or_airplay", "AirPods, Beats, or AirPlay"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                "microsoft" in n ->
                    Hint("computer", localized("device_explain_a_windows_surface_xbox_radio", "a Windows / Surface / Xbox radio"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "tesla tstpms" ->
                    Hint("vehicle", localized("device_explain_a_tesla_ble_tire_sensor", "a Tesla BLE tire sensor"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                "tpms" in n || n == "tirecheck" || n == "sytpms" ->
                    Hint("vehicle", localized("device_explain_a_ble_tire_pressure_sensor", "a BLE tire-pressure sensor"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "vuzix" ->
                    Hint("glasses", localized("device_explain_vuzix_smart_glasses", "Vuzix smart glasses"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "tesla" ->
                    Hint("vehicle", localized("device_explain_a_tesla_vehicle_including_cybertruck_or_phone", "a Tesla vehicle (including Cybertruck) or phone-as-key"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "google" ->
                    Hint("phone", localized("device_explain_a_pixel_or_other_google_radio", "a Pixel or other Google radio"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "sony" ->
                    Hint("audio-personal", localized("device_explain_sony_headphones_a_tv_or_a_camera", "Sony headphones, a TV, or a camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "bose" ->
                    Hint("audio-personal", localized("device_explain_bose_headphones_or_a_speaker", "Bose headphones or a speaker"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "garmin" ->
                    Hint("watch", localized("device_explain_a_garmin_watch_or_inreach", "a Garmin watch or inReach"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "amazon" ->
                    Hint("speaker", localized("device_explain_an_echo_fire_or_other_amazon_radio", "an Echo, Fire, or other Amazon radio"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "fitbit" ->
                    Hint("watch", localized("device_explain_a_fitbit", "a Fitbit"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "oura" ->
                    Hint("wearable", localized("device_explain_an_oura_ring", "an Oura ring"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "logitech" ->
                    Hint("hid", localized("device_explain_a_logitech_mouse_keyboard_or_webcam", "a Logitech mouse, keyboard, or webcam"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                "jbl" in n || n == "harman" ->
                    Hint("audio-personal", localized("device_explain_jbl_or_harman_audio", "JBL or Harman audio"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "sonos" ->
                    Hint("audio-speaker", localized("device_explain_a_sonos_speaker", "a Sonos speaker"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "gopro" ->
                    Hint("camera", localized("device_explain_a_gopro", "a GoPro"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "osmo" ->
                    Hint("camera", localized("device_explain_a_dji_osmo_action_camera", "a DJI Osmo action camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "insta360" ->
                    Hint("camera", localized("device_explain_an_insta360_camera", "an Insta360 camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "dji" ->
                    Hint("drone", localized("device_explain_a_dji_drone_or_controller", "a DJI drone or controller"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "remote id" ->
                    Hint("drone", localized("device_explain_a_drone_broadcasting_astm_remote_id", "a drone broadcasting ASTM Remote ID"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                n == "skydio" ->
                    Hint("drone", localized("device_explain_a_skydio_drone", "a Skydio drone"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "autel" ->
                    Hint("drone", localized("device_explain_an_autel_drone", "an Autel drone"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "parrot" ->
                    Hint("drone", localized("device_explain_a_parrot_anafi_or_bebop_drone", "a Parrot ANAFI or Bebop drone"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "hoverair" ->
                    Hint("drone", localized("device_explain_a_hoverair_flying_camera", "a HOVERAir flying camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "netgear" || n == "orbi" ->
                    Hint("ap", localized("device_explain_a_netgear_or_orbi_access_point", "a NETGEAR or Orbi access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "tp-link" ->
                    Hint("ap", localized("device_explain_a_tp_link_access_point", "a TP-Link access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "asus" ->
                    Hint("ap", localized("device_explain_an_asus_access_point", "an ASUS access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "linksys" ->
                    Hint("ap", localized("device_explain_a_linksys_or_velop_access_point", "a Linksys or Velop access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "eero" ->
                    Hint("ap", localized("device_explain_an_eero_mesh_node", "an Eero mesh node"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "google wifi" ->
                    Hint("ap", localized("device_explain_a_google_wifi_or_nest_wifi_point", "a Google Wifi or Nest Wifi point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "d-link" ->
                    Hint("ap", localized("device_explain_a_d_link_access_point", "a D-Link access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "belkin" ->
                    Hint("ap", localized("device_explain_a_belkin_access_point", "a Belkin access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "xfinity" ->
                    Hint("ap", localized("device_explain_an_xfinity_gateway_or_hotspot", "an Xfinity gateway or hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "spectrum" ->
                    Hint("ap", localized("device_explain_a_spectrum_gateway_or_spectrum_mobile_hotspot", "a Spectrum gateway or Spectrum Mobile hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "at&t" ->
                    Hint("ap", localized("device_explain_an_at_t_gateway_or_attwifi_hotspot", "an AT&T gateway or attwifi hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "verizon" ->
                    Hint("ap", localized("device_explain_a_verizon_or_fios_gateway", "a Verizon or Fios gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "starlink" ->
                    Hint("ap", localized("device_explain_a_starlink_router", "a Starlink router"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "meraki" ->
                    Hint("ap", localized("device_explain_a_cisco_meraki_access_point", "a Cisco Meraki access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "cisco" ->
                    Hint("ap", localized("device_explain_a_cisco_aironet_catalyst_business_rv_or", "a Cisco Aironet, Catalyst, Business, RV, or SPVTG access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "mist" ->
                    Hint("ap", localized("device_explain_a_juniper_mist_access_point", "a Juniper Mist access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "t-mobile" ->
                    Hint("ap", localized("device_explain_a_t_mobile_home_internet_gateway_or", "a T-Mobile Home Internet gateway or hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "humax" ->
                    Hint("ap", localized("device_explain_a_humax_gateway_often_t_mobile_home", "a HUMAX gateway (often T-Mobile Home Internet)"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "sagemcom" ->
                    Hint("ap", localized("device_explain_a_sagemcom_isp_gateway", "a Sagemcom ISP gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "arcadyan" ->
                    Hint("ap", localized("device_explain_an_arcadyan_isp_gateway", "an Arcadyan ISP gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "askey" ->
                    Hint("ap", localized("device_explain_an_askey_isp_5g_gateway", "an Askey ISP / 5G gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "calix" ->
                    Hint("ap", localized("device_explain_a_calix_fiber_gateway", "a Calix fiber gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "nokia" ->
                    Hint("ap", localized("device_explain_a_nokia_solutions_and_networks_gateway", "a Nokia Solutions and Networks gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "airties" ->
                    Hint("ap", localized("device_explain_an_airties_isp_mesh_node", "an AirTies ISP mesh node"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "tenda" ->
                    Hint("ap", localized("device_explain_a_tenda_access_point", "a Tenda access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "ruijie" ->
                    Hint("ap", localized("device_explain_a_ruijie_or_reyee_access_point", "a Ruijie or Reyee access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "dwnet" ->
                    Hint("ap", localized("device_explain_a_dwnet_access_point", "a DWnet access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "wavlink" ->
                    Hint("ap", localized("device_explain_a_wavlink_access_point", "a WAVLINK access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "sercomm" ->
                    Hint("ap", localized("device_explain_a_sercomm_isp_gateway", "a Sercomm ISP gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "luxul" ->
                    Hint("ap", localized("device_explain_a_luxul_access_point", "a Luxul access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "sophos" ->
                    Hint("ap", localized("device_explain_a_sophos_firewall_or_access_point", "a Sophos firewall or access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "aumovio" ->
                    Hint("hotspot", localized("device_explain_an_aumovio_continental_vehicle_wi_fi_radio", "an AUMOVIO / Continental vehicle Wi-Fi radio"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "centurylink" ->
                    Hint("ap", localized("device_explain_a_centurylink_gateway", "a CenturyLink gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "gm hotspot" ->
                    Hint("hotspot", localized("device_explain_a_gm_in_car_hotspot_cadillac_gmc", "a GM in-car hotspot (Cadillac / GMC / Buick / Chevrolet)"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "audi mmi" ->
                    Hint("hotspot", localized("device_explain_an_audi_mmi_in_car_hotspot", "an Audi MMI in-car hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "extreme" ->
                    Hint("ap", localized("device_explain_an_extreme_networks_access_point", "an Extreme Networks access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "adtran" ->
                    Hint("ap", localized("device_explain_an_adtran_fiber_gateway_often_centurylink_quantum", "an Adtran fiber gateway (often CenturyLink / Quantum Fiber OEM)"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "cambium" ->
                    Hint("ap", localized("device_explain_a_cambium_or_ignitenet_access_point", "a Cambium or IgniteNet access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "trendnet" ->
                    Hint("ap", localized("device_explain_a_trendnet_access_point", "a TRENDnet access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "cudy" ->
                    Hint("ap", localized("device_explain_a_cudy_travel_or_home_router", "a Cudy travel or home router"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "snapav" ->
                    Hint("ap", localized("device_explain_a_snapav_control4_wattbox_access_point", "a SnapAV / Control4 / Wattbox access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "arlo" ->
                    Hint("camera", localized("device_explain_an_arlo_camera_or_vmb_base_station", "an Arlo camera or VMB base station"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "vantiva" ->
                    Hint("ap", localized("device_explain_a_vantiva_or_technicolor_isp_gateway", "a Vantiva or Technicolor ISP gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "hitron" ->
                    Hint("ap", localized("device_explain_a_hitron_cable_gateway_often_xfinity_oem", "a Hitron cable gateway (often Xfinity OEM)"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "actiontec" ->
                    Hint("ap", localized("device_explain_an_actiontec_fios_or_frontier_gateway", "an Actiontec FiOS or Frontier gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "buffalo" ->
                    Hint("ap", localized("device_explain_a_buffalo_airstation_or_router", "a Buffalo AirStation or router"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "grandstream" ->
                    Hint("ap", localized("device_explain_a_grandstream_gwn_access_point", "a Grandstream GWN access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "edgecore" ->
                    Hint("ap", localized("device_explain_an_edgecore_access_point", "an Edgecore access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "watchguard ap" ->
                    Hint("ap", localized("device_explain_a_watchguard_firewall_or_access_point", "a WatchGuard firewall or access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "mojo" ->
                    Hint("ap", localized("device_explain_a_mojo_networks_arista_cognitive_wi_fi", "a Mojo Networks / Arista Cognitive Wi-Fi access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "winegard" ->
                    Hint("hotspot", localized("device_explain_a_winegard_rv_or_marine_wi_fi", "a Winegard RV or marine Wi-Fi radio"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "inseego" ->
                    Hint("ap", localized("device_explain_an_inseego_5g_or_mifi_hotspot", "an Inseego 5G or MiFi hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "franklin" ->
                    Hint("ap", localized("device_explain_a_franklin_technology_5g_home_internet_gateway", "a Franklin Technology 5G home-internet gateway (RG3100 class)"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "synology" ->
                    Hint("ap", localized("device_explain_a_synology_nas_or_router_access_point", "a Synology NAS or router access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "aruba" ->
                    Hint("ap", localized("device_explain_an_hpe_aruba_instant_or_instant_on", "an HPE Aruba Instant or Instant On access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "ruckus" ->
                    Hint("ap", localized("device_explain_a_ruckus_access_point", "a RUCKUS access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "fortinet" ->
                    Hint("ap", localized("device_explain_a_fortinet_fortiap_or_fortiwifi", "a Fortinet FortiAP or FortiWiFi"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "mikrotik" ->
                    Hint("ap", localized("device_explain_a_mikrotik_router_or_access_point", "a MikroTik router or access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "engenius" ->
                    Hint("ap", localized("device_explain_an_engenius_access_point", "an EnGenius access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "zyxel" ->
                    Hint("ap", localized("device_explain_a_zyxel_gateway_or_access_point", "a Zyxel gateway or access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "peplink" ->
                    Hint("ap", localized("device_explain_a_peplink_or_pepwave_router", "a Peplink or Pepwave router"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "openwrt" ->
                    Hint("ap", localized("device_explain_an_openwrt_router", "an OpenWrt router"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "arris" ->
                    Hint("ap", localized("device_explain_an_arris_or_surfboard_cable_gateway", "an Arris or SURFboard cable gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "unifi ap" ->
                    Hint("ap", localized("device_explain_a_ubiquiti_unifi_access_point", "a Ubiquiti UniFi access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "unifi protect" ->
                    Hint("camera", localized("device_explain_a_unifi_protect_instant_camera", "a UniFi Protect Instant camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "unifi" ->
                    Hint("ap", localized("device_explain_a_unifi_ubiquiti_name", "a UniFi / Ubiquiti name"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 5)
                n == "ecobee" ->
                    Hint("thermostat", localized("device_explain_an_ecobee_thermostat", "an ecobee thermostat"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "sensi" ->
                    Hint("thermostat", localized("device_explain_a_sensi_thermostat", "a Sensi thermostat"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "honeywell home" ->
                    Hint("thermostat", localized("device_explain_a_honeywell_home_or_lyric_thermostat", "a Honeywell Home or Lyric thermostat"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                "honeywell xenon" in n ->
                    Hint("health", localized("device_explain_a_honeywell_xenon_healthcare_barcode_scanner", "a Honeywell Xenon healthcare barcode scanner"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "omron" ->
                    Hint("health", localized("device_explain_an_omron_blood_pressure_cuff_or_scale", "an Omron blood-pressure cuff or scale"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "withings" ->
                    Hint("health", localized("device_explain_a_withings_scale_or_blood_pressure_monitor", "a Withings scale or blood-pressure monitor"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "dexcom" ->
                    Hint("health", localized("device_explain_a_dexcom_glucose_sensor", "a Dexcom glucose sensor"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "nest thermostat" ->
                    Hint("thermostat", localized("device_explain_a_nest_thermostat_or_nest_labs_ble", "a Nest thermostat or Nest Labs BLE sensor"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "nest weave" ->
                    Hint("sensor", localized("device_explain_a_nest_protect_camera_or_other_weave", "a Nest Protect, camera, or other Weave BLE device"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "haiku fan" || n == "haiku" ->
                    Hint("fan", localized("device_explain_a_haiku_or_mammoth_ceiling_fan", "a Haiku or Mammoth ceiling fan"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "tuya" ->
                    Hint("iot", localized("device_explain_a_tuya_ble_gadget_plug_light_camera", "a Tuya BLE gadget (plug, light, camera, sensor)"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "seos" || n == "assa abloy" ->
                    Hint("access", localized("device_explain_an_assa_abloy_lock_yale_lock_hid", "an ASSA ABLOY lock, Yale lock, HID reader, or Seos credential"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "august" ->
                    Hint("lock", localized("device_explain_an_august_smart_lock", "an August smart lock"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "schlage" ->
                    Hint("lock", localized("device_explain_a_schlage_or_allegion_lock", "a Schlage or Allegion lock"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "nuki" ->
                    Hint("lock", localized("device_explain_a_nuki_lock_or_opener", "a Nuki lock or opener"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "salto" ->
                    Hint("access", localized("device_explain_a_salto_access_reader_or_lock", "a SALTO access reader or lock"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "dormakaba" ->
                    Hint("access", localized("device_explain_a_dormakaba_saflok_or_oracode_lock", "a dormakaba, Saflok, or Oracode lock"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "lockly" ->
                    Hint("lock", localized("device_explain_a_lockly_smart_lock", "a Lockly smart lock"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "kevo" ->
                    Hint("lock", localized("device_explain_a_kwikset_kevo_or_unikey_lock", "a Kwikset Kevo or Unikey lock"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "master lock" ->
                    Hint("lock", localized("device_explain_a_master_lock_padlock", "a Master Lock padlock"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "igloohome" ->
                    Hint("lock", localized("device_explain_an_igloohome_lock_or_keybox", "an igloohome lock or keybox"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "tedee" ->
                    Hint("lock", localized("device_explain_a_tedee_smart_lock", "a Tedee smart lock"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "paxton" ->
                    Hint("access", localized("device_explain_a_paxton_reader_or_net2_access_point", "a Paxton reader or Net2 access point"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "kwikset" ->
                    Hint("lock", localized("device_explain_a_kwikset_lock", "a Kwikset lock"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "myq" ->
                    Hint("garage", localized("device_explain_a_chamberlain_myq_garage_hub", "a Chamberlain myQ garage hub"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "chevrolet hotspot" ->
                    Hint("hotspot", localized("device_explain_a_chevrolet_in_car_wi_fi_hotspot", "a Chevrolet in-car Wi-Fi hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "rivian" ->
                    Hint("vehicle", localized("device_explain_a_rivian_vehicle_phone_key_or_sensor", "a Rivian vehicle, phone key, or sensor"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "ford" ->
                    Hint("vehicle", localized("device_explain_a_ford_or_lincoln_vehicle_or_phone", "a Ford or Lincoln vehicle or phone-as-key"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "honda" ->
                    Hint("vehicle", localized("device_explain_a_honda_or_acura_vehicle_or_phone", "a Honda or Acura vehicle or phone-as-key"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "hyundai" ->
                    Hint("vehicle", localized("device_explain_a_hyundai_or_genesis_vehicle_or_phone", "a Hyundai or Genesis vehicle or phone-as-key"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "toyota" ->
                    Hint("vehicle", localized("device_explain_a_toyota_or_lexus_vehicle_or_phone", "a Toyota or Lexus vehicle or phone-as-key"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "nissan" ->
                    Hint("vehicle", localized("device_explain_a_nissan_or_infiniti_vehicle_or_phone", "a Nissan or Infiniti vehicle or phone-as-key"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "subaru" ->
                    Hint("vehicle", localized("device_explain_a_subaru_vehicle_or_phone_as_key", "a Subaru vehicle or phone-as-key"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "bmw" ->
                    Hint("vehicle", localized("device_explain_a_bmw_vehicle_phone_as_key_or", "a BMW vehicle, phone-as-key, or factory hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "volkswagen" ->
                    Hint("vehicle", localized("device_explain_a_volkswagen_vehicle_or_phone_as_key", "a Volkswagen vehicle or phone-as-key"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "porsche" ->
                    Hint("vehicle", localized("device_explain_a_porsche_vehicle_or_phone_as_key", "a Porsche vehicle or phone-as-key"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "jaguar land rover" ->
                    Hint("vehicle", localized("device_explain_a_jaguar_land_rover_or_range_rover", "a Jaguar, Land Rover, or Range Rover"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "byd" ->
                    Hint("vehicle", localized("device_explain_a_byd_vehicle_or_phone_as_key", "a BYD vehicle or phone-as-key"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "govee" ->
                    Hint("light", localized("device_explain_a_govee_light_or_sensor", "a Govee light or sensor"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "hp" ->
                    Hint("printer", localized("device_explain_an_hp_printer", "an HP printer"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "epson" ->
                    Hint("printer", localized("device_explain_an_epson_ecotank_or_workforce_printer", "an Epson EcoTank or WorkForce printer"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "lg webos tv" ->
                    Hint("tv", localized("device_explain_an_lg_webos_tv", "an LG webOS TV"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "roku" ->
                    Hint("tv", localized("device_explain_a_roku_streaming_stick_or_roku_tv", "a Roku streaming stick or Roku TV (often a hidden Wi-Fi Direct remote AP)"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "samsung appliance" ->
                    Hint("iot", localized("device_explain_a_samsung_fridge_range_oven_or_cooktop", "a Samsung fridge, range, oven, or cooktop (setup AP)"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "ecowater" ->
                    Hint("iot", localized("device_explain_an_ecowater_water_softener_setup_ap", "an EcoWater water softener (setup AP)"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "nespresso" ->
                    Hint("iot", localized("device_explain_a_nespresso_coffee_machine", "a Nespresso coffee machine"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "radiacode" ->
                    Hint("sensor", localized("device_explain_a_radiacode_radiation_detector", "a RadiaCode radiation detector"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "shokz" ->
                    Hint("audio-personal", localized("device_explain_shokz_openrun_or_openfit_headphones", "Shokz OpenRun or OpenFit headphones"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "mercedes mbux" ->
                    Hint("hotspot", localized("device_explain_a_mercedes_mbux_in_car_hotspot", "a Mercedes MBUX in-car hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "motive" ->
                    Hint("hotspot", localized("device_explain_a_motive_keeptruckin_fleet_eld_hotspot", "a Motive / KeepTruckin fleet ELD hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "peoplenet" ->
                    Hint("hotspot", localized("device_explain_a_peoplenet_fleet_eld_hotspot", "a PeopleNet fleet ELD hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "uconnect" ->
                    Hint("hotspot", localized("device_explain_a_uconnect_in_car_hotspot", "a Uconnect in-car hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "carplay" ->
                    Hint("hotspot", localized("device_explain_a_carplay_in_car_hotspot", "a CarPlay in-car hotspot"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "cradlepoint" ->
                    Hint("hotspot", localized("device_explain_a_cradlepoint_vehicle_router_often_public_safety", "a Cradlepoint vehicle router (often public-safety / fleet)"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "airlink" ->
                    Hint("hotspot", localized("device_explain_a_sierra_wireless_airlink_vehicle_gateway", "a Sierra Wireless AirLink vehicle gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "compex" ->
                    Hint("hotspot", localized("device_explain_a_compex_access_point_sometimes_public_safety", "a Compex access point (sometimes public-safety / fleet)"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "novatel wireless" ->
                    Hint("hotspot", localized("device_explain_a_novatel_wireless_inseego_vehicle_radio", "a Novatel Wireless / Inseego vehicle radio"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                n == "utility inc" ->
                    Hint("hotspot", localized("device_explain_a_utility_inc_vehicle_or_public_safety", "a Utility, Inc vehicle or public-safety radio"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                "gl.inet" in n || n == "glinet" ->
                    Hint("ap", localized("device_explain_a_gl_inet_travel_router", "a GL.iNet travel router"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 6)
                "smarttag" in n ->
                    Hint("tag", localized("device_explain_a_samsung_smarttag", "a Samsung SmartTag"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                "tile" in n ->
                    Hint("tag", localized("device_explain_a_tile_tracker", "a Tile tracker"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                n == "ibeacon" ->
                    Hint("beacon", localized("device_explain_an_ibeacon", "an iBeacon"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                "target atrius" in n ->
                    Hint("beacon", localized("device_explain_a_target_atrius_basket_tag", "a Target Atrius basket tag"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                n == "minew" ->
                    Hint("beacon", localized("device_explain_a_minew_ble_beacon_or_sensor", "a Minew BLE beacon or sensor"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "estimote" ->
                    Hint("beacon", localized("device_explain_an_estimote_beacon", "an Estimote beacon"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "kontakt.io" || n == "kontakt" ->
                    Hint("beacon", localized("device_explain_a_kontakt_io_beacon", "a Kontakt.io beacon"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                "bluetoad" in n ->
                    Hint(
                        "roadside",
                        localized("device_explain_an_iteris_bluetoad_vantage_velocity_roadside_bluetooth", "an Iteris BlueTOAD / Vantage Velocity roadside Bluetooth travel-time reader"),
                        localized("device_explain_matched_signature", "Matched signature %1\$s.", raw),
                        7,
                    )
                "bliptrack" in n ->
                    Hint(
                        "roadside",
                        localized("device_explain_a_blip_systems_bliptrack_roadside_travel_time", "a BLIP Systems BlipTrack roadside travel-time sensor"),
                        localized("device_explain_matched_signature", "Matched signature %1\$s.", raw),
                        7,
                    )
                "raven" in n || "shotspotter" in n || "soundthinking" in n ->
                    Hint(
                        "acoustic",
                        localized("device_explain_a_flock_raven_or_shotspotter_acoustic_gunshot", "a Flock Raven or ShotSpotter acoustic gunshot sensor"),
                        localized("device_explain_matched_signature", "Matched signature %1\$s.", raw),
                        8,
                    )
                "digital ally" in n ->
                    Hint("camera", localized("device_explain_a_digital_ally_body_worn_or_in", "a Digital Ally body-worn or in-car camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                "reveal media" in n || "bodyworn" in n ->
                    Hint("camera", localized("device_explain_a_reveal_media_body_worn_camera", "a Reveal Media body-worn camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                n == "wolfcom" ->
                    Hint("camera", localized("device_explain_a_wolfcom_body_worn_or_in_car", "a Wolfcom body-worn or in-car camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                "i-pro" in n || "arbitrator" in n ->
                    Hint("camera", localized("device_explain_a_panasonic_i_pro_camera_or_arbitrator", "a Panasonic i-PRO camera or Arbitrator in-car system"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                "limitless" in n ->
                    Hint("wearable", localized("device_explain_a_limitless_pendant_conversation_recorder", "a Limitless Pendant conversation recorder"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                n == "bee pendant" || "bee pioneer" in n ->
                    Hint("wearable", localized("device_explain_a_bee_pioneer_wearable_recorder", "a Bee Pioneer wearable recorder"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                n == "omi" || "openglass" in n ->
                    Hint("wearable", localized("device_explain_an_omi_pendant_or_openglass_camera_glasses", "an Omi pendant or OpenGlass camera glasses"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                "friend pendant" in n ->
                    Hint("wearable", localized("device_explain_a_friend_pendant_necklace", "a Friend Pendant necklace"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                "brilliant frame" in n ->
                    Hint("glasses", localized("device_explain_brilliant_labs_frame_ar_glasses", "Brilliant Labs Frame AR glasses"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                n == "even g1" ->
                    Hint("glasses", localized("device_explain_even_realities_g1_glasses", "Even Realities G1 glasses"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                "hayden" in n ->
                    Hint("camera", localized("device_explain_a_hayden_ai_bus_or_vehicle_mounted", "a Hayden AI bus- or vehicle-mounted camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                "miovision" in n ->
                    Hint("camera", localized("device_explain_a_miovision_intersection_traffic_camera", "a Miovision intersection traffic camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                n == "tattile" ->
                    Hint("camera", localized("device_explain_a_tattile_plate_reader", "a Tattile plate reader"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                "lvt" in n || "liveview" in n ->
                    Hint("camera", localized("device_explain_an_lvt_liveview_solar_surveillance_trailer", "an LVT / LiveView solar surveillance trailer"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                "hanwha" in n || "wisenet" in n ->
                    Hint("camera", localized("device_explain_a_hanwha_vision_wisenet_camera", "a Hanwha Vision / Wisenet camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "uniview" ->
                    Hint("camera", localized("device_explain_a_uniview_unv_camera", "a Uniview / UNV camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "rhombus" ->
                    Hint("camera", localized("device_explain_a_rhombus_cloud_camera", "a Rhombus cloud camera"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "meshcore" ->
                    Hint("mesh", localized("device_explain_a_meshcore_lora_companion_radio", "a MeshCore LoRa companion radio"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                "gotenna" in n ->
                    Hint("mesh", localized("device_explain_a_gotenna_mesh_or_pro_radio", "a goTenna Mesh or Pro radio"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "sensecap" ->
                    Hint("mesh", localized("device_explain_a_sensecap_lorawan_helium_gateway", "a SenseCAP LoRaWAN / Helium gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                "wisgate" in n || n == "rak wisgate" ->
                    Hint("mesh", localized("device_explain_a_rak_wisgate_lorawan_gateway", "a RAK WisGate LoRaWAN gateway"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "ghostesp" ->
                    Hint("pentest", localized("device_explain_a_ghostesp_esp32_audit_board", "a GhostESP ESP32 audit board"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "bruce" ->
                    Hint("pentest", localized("device_explain_a_bruce_esp32_pentest_board", "a Bruce ESP32 pentest board"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                n == "liteon camera radio" ->
                    Hint(
                        "module",
                        localized("device_explain_a_camera_module_radio_liteon_or_similar", "a camera-module radio (LiteOn or similar)"),
                        localized("device_explain_matched_signature", "Matched signature %1\$s.", raw),
                        3,
                    )
                "chipolo" in n || "pebblebee" in n || "moto tag" in n ->
                    Hint("tag", localized("device_explain_a_finder_tag", "a finder tag"), localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
                "airpods" in n ->
                    Hint("audio-personal", "AirPods", localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 8)
                else -> Hint("named", raw, localized("device_explain_matched_signature", "Matched signature %1\$s.", raw), 7)
            }
        }
    }

    private fun isGenericSignatureName(name: String): Boolean {
        val n = name.trim()
        return n.equals("Unknown Signature", ignoreCase = true) ||
            n.equals("Unknown Fleet", ignoreCase = true)
    }

    private fun wifiHints(device: Sighting, signatureNames: List<String>): List<Hint> {
        val name = device.name
        val caps = (device.facts.capabilities ?: "").uppercase()
        val specific = signatureNames.any { !isGenericSignatureName(it) }
        val out = ArrayList<Hint>(2)
        when {
            name.startsWith("DIRECT-", true) ->
                out += if (specific) {
                    Hint("wifi-direct", localized("device_explain_a_wi_fi_direct_access_point", "a Wi-Fi Direct access point"), localized("device_explain_ssid_starts_with_direct", "SSID starts with DIRECT-."), 4)
                } else {
                    Hint("wifi-direct", localized("device_explain_a_phone_or_tv_using_wi_fi", "a phone or TV using Wi-Fi Direct"), localized("device_explain_ssid_starts_with_direct", "SSID starts with DIRECT-."), 6)
                }
            name.startsWith("ANDROID-", true) || name.contains("hotspot", true) ->
                if (!specific) {
                    out += Hint("hotspot", localized("device_explain_a_phone_hotspot", "a phone hotspot"), localized("device_explain_ssid_looks_like_a_phone_hotspot", "SSID looks like a phone hotspot."), 6)
                }
            "MESH" in caps ->
                out += Hint("mesh", localized("device_explain_a_mesh_wi_fi_node", "a mesh Wi-Fi node"), localized("device_explain_capability_list_includes_mesh", "Capability list includes mesh."), 5)
            device.hiddenSsid ->
                out += Hint("ap", localized("device_explain_a_hidden_wi_fi_access_point", "a hidden Wi-Fi access point"), localized("device_explain_ssid_is_hidden_the_radio_is_still", "SSID is hidden; the radio is still beaconing."), 4, generic = true)
            else ->
                if (!specific) {
                    out += Hint("ap", localized("device_explain_a_wi_fi_access_point", "a Wi-Fi access point"), localized("device_explain_stock_android_only_reports_beaconing_aps", "Stock Android only reports beaconing APs."), 3, generic = true)
                }
        }
        return out
    }

    private fun uuid16(uuid: String): Int? {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex.length == 4 -> hex.toIntOrNull(16)
            hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB") ->
                hex.substring(4, 8).toIntOrNull(16)
            hex.length == 8 -> hex.takeLast(4).toIntOrNull(16)
            else -> null
        }
    }
}
