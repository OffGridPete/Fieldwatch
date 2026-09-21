package app.fieldwatch.domain

/**
 * Plain-language decode of advertised identity. Guesses are what the radio
 * is broadcasting, not a visual identification.
 */
object DeviceExplain {
    data class Guess(
        val headline: String,
        val because: String,
        val confidence: Confidence,
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
        if (device.kind == RadioKind.WIFI) hints += wifiHints(device)

        if (hints.isEmpty()) {
            return Guess(
                headline = if (device.kind == RadioKind.WIFI) {
                    "Wi-Fi access point"
                } else {
                    "Bluetooth LE advertiser"
                },
                because = "It is on the air, but it did not advertise a product class " +
                    "(no Appearance, Class of Device, or well-known service that names a type).",
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
            Confidence.HIGH -> "Most likely"
            Confidence.MEDIUM -> "Probably"
            Confidence.LOW -> "Could be"
        }
        return Guess(
            headline = "$hedge ${best.label}",
            because = support.joinToString(" ") +
                " This is what the device is advertising, not a visual ID.",
            confidence = confidence,
        )
    }

    /**
     * Compact Live-row title from the same guess as detail. Null if we only
     * know it is an unnamed advertiser — caller may fall back to vendor.
     */
    fun listLabel(device: Sighting, signatureNames: List<String> = emptyList()): String? {
        val guess = guess(device, signatureNames)
        val generic = guess.headline.contains("Bluetooth LE advertiser", ignoreCase = true) ||
            guess.headline.contains("Wi-Fi access point", ignoreCase = true)
        val core = if (generic) null else tidyHeadline(guess.headline)
        val vendor = device.vendor?.trim()?.takeIf { it.isNotBlank() && it.length <= 24 }
        if (core != null) {
            return if (vendor != null && !core.contains(vendor, ignoreCase = true)) {
                "$vendor · $core"
            } else {
                core
            }
        }
        if (vendor != null) return "$vendor device"
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
            add("Limited-discoverable: briefly looking for a nearby connection.")
        }
        if (flags and 0x02 != 0) {
            add("Discoverable: other BLE devices can find it.")
        }
        if (flags and 0x04 != 0) {
            add("BLE-only: no classic Bluetooth (headsets/file-send radio).")
        } else {
            add("May also do classic Bluetooth (BR/EDR) as well as BLE.")
        }
        if (flags and 0x08 != 0 || flags and 0x10 != 0) {
            add("Dual-mode chip: BLE and classic can run together.")
        }
    }.joinToString(" ")

    fun phyExplain(label: String): String = when {
        label.contains("Coded") -> "$label — long-range BLE (slower, farther)"
        label.contains("2M") -> "$label — faster BLE (Bluetooth 5)"
        label.contains("1M") -> "$label — standard BLE radio"
        else -> label
    }

    fun addressExplain(device: Sighting): String {
        val type = device.facts.addressType
        return when {
            type.equals("Public", true) && !device.randomized ->
                "Public factory address (stable, IEEE-assigned)."
            type.equals("Random", true) || device.randomized ->
                "Random / privacy address. The MAC can change, so this is not a lasting identity."
            type.equals("Anonymous", true) ->
                "Anonymous: the stack hid the address."
            device.randomized ->
                "Locally administered address (not a global IEEE identity)."
            else ->
                listOfNotNull(type, "Universal IEEE address (stable OUI).").joinToString(" · ")
        }
    }

    fun rssiBand(rssi: Int): String = when {
        rssi >= -45 -> "very strong"
        rssi >= -60 -> "strong"
        rssi >= -75 -> "medium"
        rssi >= -88 -> "weak"
        else -> "very weak"
    }

    fun rssiExplain(rssi: Int): String = "%d dBm · %s".format(rssi, rssiBand(rssi))

    fun wifiSecurityExplain(raw: String): String {
        val bits = ArrayList<String>(4)
        val u = raw.uppercase()
        when {
            "SAE" in u || "WPA3" in u -> bits += "WPA3 password (SAE handshake)"
            "OWE" in u -> bits += "Enhanced Open (encrypted, no password)"
            "PSK" in u && "WPA2" in u -> bits += "WPA2 password (PSK)"
            "PSK" in u || "WPA" in u -> bits += "Wi-Fi password (WPA/PSK)"
            "802.1X" in u || "EAP" in u -> bits += "Enterprise login (802.1X)"
            "WEP" in u -> bits += "WEP (old, weak)"
            "ESS" in u && bits.isEmpty() -> bits += "Open or encryption not parsed"
        }
        when {
            "CCMP" in u || "GCMP" in u -> bits += "AES encryption"
            "TKIP" in u -> bits += "TKIP (older, weaker cipher)"
        }
        if ("WPS" in u) bits += "WPS setup is enabled"
        if ("MESH" in u) bits += "mesh node"
        if ("IBSS" in u) bits += "ad-hoc network"
        if ("ESS" in u) bits += "infrastructure access point"
        return if (bits.isEmpty()) raw else bits.distinct().joinToString(". ") + "."
    }

    fun uuidGloss(uuid: String): String? {
        val name = RadioDb.serviceUuid(uuid)
        val short = uuid16(uuid) ?: return name
        val extra = when (short) {
            0x1800 -> "connection basics"
            0x1801 -> "attribute protocol"
            0x180A -> "model / serial / firmware"
            0x180F -> "battery level"
            0x1812 -> "keyboard, mouse, or gamepad"
            0x180D -> "heart-rate sensor"
            0x1810 -> "blood-pressure sensor"
            0x181A -> "temperature / humidity style sensor"
            0x1844, 0x1845, 0x1846 -> "LE cycling power/speed"
            0x1850, 0x184E, 0x184F -> "LE Audio"
            0xFE2C -> "Google Fast Pair (often buds/speakers)"
            0xFD5A -> "Samsung SmartTag"
            0xFD44 -> "Apple Find My related"
            0xFEED, 0xFEDD -> "Tile tracker"
            0xFD50 -> "Tuya IoT"
            0xFEBE, 0xFE21 -> "Bose"
            0xFE78 -> "HP printer"
            0xFE07 -> "Sonos speaker"
            0xFEAF, 0xFEB0 -> "Nest Weave"
            0xFCBF -> "ASSA ABLOY Opening Solutions"
            0xFE24 -> "August Home lock"
            0xFCF4 -> "Allegion / Schlage"
            0xFCB2 -> "Apple (not ASSA ABLOY)"
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
    )

    private fun appearanceHint(name: String?): Hint? {
        if (name.isNullOrBlank() || name.equals("Unknown", true)) return null
        val n = name.lowercase()
        val (bucket, label, w) = when {
            "ear" in n || "headphone" in n || "headset" in n || "hearable" in n || "hearing" in n ->
                Triple("audio-personal", "earbuds or headphones", 7)
            "speaker" in n || "loudspeaker" in n || "hifi" in n ->
                Triple("audio-speaker", "a speaker", 7)
            "mouse" in n -> Triple("mouse", "a mouse", 8)
            "keyboard" in n -> Triple("keyboard", "a keyboard", 8)
            "gamepad" in n || "joystick" in n -> Triple("gamepad", "a game controller", 7)
            "watch" in n -> Triple("watch", "a watch or wrist wearable", 7)
            "phone" in n -> Triple("phone", "a phone", 6)
            "laptop" in n || "computer" in n || "desktop" in n || "tablet" in n ->
                Triple("computer", "a computer or tablet", 6)
            "tag" in n || "keyring" in n -> Triple("tag", "a finder tag / tracker", 6)
            "remote" in n -> Triple("remote", "a remote control", 6)
            "hid" in n -> Triple("hid", "an input device (keyboard, mouse, or similar)", 4)
            "heart" in n -> Triple("health", "a heart-rate monitor", 7)
            "glucose" in n || "oximeter" in n || "blood pressure" in n || "thermometer" in n ->
                Triple("health", "a health sensor", 6)
            "display" in n || "monitor" in n -> Triple("display", "a display or TV stick", 4)
            "clock" in n -> Triple("clock", "a clock", 5)
            "glasses" in n -> Triple("glasses", "smart glasses", 6)
            else -> Triple("other", name, 3)
        }
        return Hint(bucket, label, "It advertises Appearance as $name.", w)
    }

    private fun codHint(cod: CodDecoder.Decoded): Hint? {
        val minor = cod.minor.lowercase()
        val major = cod.major.lowercase()
        val (bucket, label, w) = when {
            "headphone" in minor || "headset" in minor || "hands-free" in minor ->
                Triple("audio-personal", "earbuds or a headset", 6)
            "loudspeaker" in minor || "portable audio" in minor || "hifi" in minor || "car audio" in minor ->
                Triple("audio-speaker", "a speaker", 6)
            "pointing" in minor || minor == "mouse" -> Triple("mouse", "a mouse", 7)
            "keyboard" in minor -> Triple("keyboard", "a keyboard", 7)
            "gamepad" in minor || "joystick" in minor -> Triple("gamepad", "a game controller", 6)
            "smartphone" in minor || (major == "phone" && "uncategorized" !in minor) ->
                Triple("phone", "a phone", 5)
            "laptop" in minor || "tablet" in minor || "desktop" in minor ->
                Triple("computer", "a computer", 5)
            "wristwatch" in minor -> Triple("watch", "a watch", 6)
            "heart" in minor || "pulse" in minor || "glucose" in minor || "oximeter" in minor ->
                Triple("health", "a health sensor", 6)
            "audio" in major -> Triple("audio-personal", "an audio device", 3)
            "peripheral" in major -> Triple("hid", "an input accessory", 3)
            "uncategorized" in major || "miscellaneous" in major -> return null
            else -> return null
        }
        val shown = if (cod.minor.isNotBlank() && cod.minor != "Uncategorized") {
            "${cod.major} / ${cod.minor}"
        } else {
            cod.major
        }
        return Hint(bucket, label, "Class of Device says $shown.", w)
    }

    private fun uuidHints(uuids: List<String>): List<Hint> {
        val out = ArrayList<Hint>(4)
        for (uuid in uuids) {
            val id = uuid16(uuid) ?: continue
            when (id) {
                0x1812 -> out += Hint("hid", "a keyboard, mouse, or gamepad", "It offers the HID (human-interface) service.", 5)
                0x1108, 0x1112, 0x111E, 0x110B, 0x110A, 0x1131, 0x1203 ->
                    out += Hint("audio-personal", "headphones, a headset, or a speaker", "It offers a classic audio / headset service.", 5)
                0x184E, 0x184F, 0x1850, 0x1851 ->
                    out += Hint("audio-personal", "LE Audio earbuds or a speaker", "It offers Bluetooth LE Audio services.", 6)
                0x180D -> out += Hint("health", "a heart-rate monitor", "It offers the Heart Rate service.", 6)
                0x1810 -> out += Hint("health", "a blood-pressure monitor", "It offers the Blood Pressure service.", 6)
                0x181A -> out += Hint("sensor", "an environmental sensor", "It offers Environmental Sensing.", 4)
                0xFE2C -> out += Hint("audio-personal", "earbuds or a speaker", "Google Fast Pair is present (common on buds and speakers).", 4)
                0xFD5A -> out += Hint("tag", "a Samsung SmartTag", "SmartTag service UUID.", 7)
                0xFD44 -> out += Hint("tag", "an Apple Find My accessory", "Find My related UUID.", 6)
                0xFEED, 0xFEDD -> out += Hint("tag", "a Tile tracker", "Tile service UUID.", 7)
            }
        }
        return out
    }

    private fun signatureHints(names: List<String>): List<Hint> {
        return names.mapNotNull { raw ->
            val n = raw.lowercase()
            when {
                "airtag" in n || "find my" in n ->
                    Hint("tag", "an Apple AirTag / Find My tag", "Matched signature $raw.", 8)
                "apple device" in n ->
                    Hint("phone", "an iPhone, iPad, or Mac", "Matched signature $raw.", 7)
                "apple audio" in n ->
                    Hint("audio-personal", "AirPods, Beats, or AirPlay", "Matched signature $raw.", 7)
                "microsoft" in n ->
                    Hint("computer", "a Windows / Surface / Xbox radio", "Matched signature $raw.", 6)
                n == "tesla tstpms" ->
                    Hint("vehicle", "a Tesla BLE tire sensor", "Matched signature $raw.", 7)
                n == "tesla" ->
                    Hint("vehicle", "a Tesla vehicle (including Cybertruck) or phone-as-key", "Matched signature $raw.", 7)
                n == "google" ->
                    Hint("phone", "a Pixel or other Google radio", "Matched signature $raw.", 6)
                n == "sony" ->
                    Hint("audio-personal", "Sony headphones, a TV, or a camera", "Matched signature $raw.", 6)
                n == "bose" ->
                    Hint("audio-personal", "Bose headphones or a speaker", "Matched signature $raw.", 7)
                n == "garmin" ->
                    Hint("watch", "a Garmin watch or inReach", "Matched signature $raw.", 7)
                n == "amazon" ->
                    Hint("speaker", "an Echo, Fire, or other Amazon radio", "Matched signature $raw.", 6)
                n == "fitbit" ->
                    Hint("watch", "a Fitbit", "Matched signature $raw.", 7)
                n == "oura" ->
                    Hint("wearable", "an Oura ring", "Matched signature $raw.", 7)
                n == "logitech" ->
                    Hint("hid", "a Logitech mouse, keyboard, or webcam", "Matched signature $raw.", 6)
                "jbl" in n || n == "harman" ->
                    Hint("audio-personal", "JBL or Harman audio", "Matched signature $raw.", 6)
                n == "sonos" ->
                    Hint("audio-speaker", "a Sonos speaker", "Matched signature $raw.", 7)
                n == "gopro" ->
                    Hint("camera", "a GoPro", "Matched signature $raw.", 7)
                n == "osmo" ->
                    Hint("camera", "a DJI Osmo action camera", "Matched signature $raw.", 7)
                n == "insta360" ->
                    Hint("camera", "an Insta360 camera", "Matched signature $raw.", 7)
                n == "dji" ->
                    Hint("drone", "a DJI drone or controller", "Matched signature $raw.", 7)
                n == "remote id" ->
                    Hint("drone", "a drone broadcasting ASTM Remote ID", "Matched signature $raw.", 8)
                n == "skydio" ->
                    Hint("drone", "a Skydio drone", "Matched signature $raw.", 7)
                n == "autel" ->
                    Hint("drone", "an Autel drone", "Matched signature $raw.", 7)
                n == "parrot" ->
                    Hint("drone", "a Parrot ANAFI or Bebop drone", "Matched signature $raw.", 7)
                n == "hoverair" ->
                    Hint("drone", "a HOVERAir flying camera", "Matched signature $raw.", 7)
                n == "netgear" || n == "orbi" ->
                    Hint("ap", "a NETGEAR or Orbi access point", "Matched signature $raw.", 6)
                n == "tp-link" ->
                    Hint("ap", "a TP-Link access point", "Matched signature $raw.", 6)
                n == "asus" ->
                    Hint("ap", "an ASUS access point", "Matched signature $raw.", 6)
                n == "linksys" ->
                    Hint("ap", "a Linksys or Velop access point", "Matched signature $raw.", 6)
                n == "eero" ->
                    Hint("ap", "an Eero mesh node", "Matched signature $raw.", 6)
                n == "google wifi" ->
                    Hint("ap", "a Google Wifi or Nest Wifi point", "Matched signature $raw.", 6)
                n == "d-link" ->
                    Hint("ap", "a D-Link access point", "Matched signature $raw.", 6)
                n == "belkin" ->
                    Hint("ap", "a Belkin access point", "Matched signature $raw.", 6)
                n == "xfinity" ->
                    Hint("ap", "an Xfinity gateway or hotspot", "Matched signature $raw.", 6)
                n == "spectrum" ->
                    Hint("ap", "a Spectrum gateway or Spectrum Mobile hotspot", "Matched signature $raw.", 6)
                n == "at&t" ->
                    Hint("ap", "an AT&T gateway or attwifi hotspot", "Matched signature $raw.", 6)
                n == "verizon" ->
                    Hint("ap", "a Verizon or Fios gateway", "Matched signature $raw.", 6)
                n == "starlink" ->
                    Hint("ap", "a Starlink router", "Matched signature $raw.", 7)
                n == "meraki" ->
                    Hint("ap", "a Cisco Meraki access point", "Matched signature $raw.", 7)
                n == "cisco" ->
                    Hint("ap", "a Cisco Aironet, Catalyst, Business, RV, or SPVTG access point", "Matched signature $raw.", 7)
                n == "mist" ->
                    Hint("ap", "a Juniper Mist access point", "Matched signature $raw.", 7)
                n == "t-mobile" ->
                    Hint("ap", "a T-Mobile Home Internet gateway or hotspot", "Matched signature $raw.", 6)
                n == "humax" ->
                    Hint("ap", "a HUMAX gateway (often T-Mobile Home Internet)", "Matched signature $raw.", 6)
                n == "sagemcom" ->
                    Hint("ap", "a Sagemcom ISP gateway", "Matched signature $raw.", 6)
                n == "arcadyan" ->
                    Hint("ap", "an Arcadyan ISP gateway", "Matched signature $raw.", 6)
                n == "askey" ->
                    Hint("ap", "an Askey ISP / 5G gateway", "Matched signature $raw.", 6)
                n == "calix" ->
                    Hint("ap", "a Calix fiber gateway", "Matched signature $raw.", 6)
                n == "nokia" ->
                    Hint("ap", "a Nokia Solutions and Networks gateway", "Matched signature $raw.", 6)
                n == "airties" ->
                    Hint("ap", "an AirTies ISP mesh node", "Matched signature $raw.", 6)
                n == "tenda" ->
                    Hint("ap", "a Tenda access point", "Matched signature $raw.", 6)
                n == "ruijie" ->
                    Hint("ap", "a Ruijie or Reyee access point", "Matched signature $raw.", 6)
                n == "dwnet" ->
                    Hint("ap", "a DWnet access point", "Matched signature $raw.", 6)
                n == "wavlink" ->
                    Hint("ap", "a WAVLINK access point", "Matched signature $raw.", 6)
                n == "sercomm" ->
                    Hint("ap", "a Sercomm ISP gateway", "Matched signature $raw.", 6)
                n == "luxul" ->
                    Hint("ap", "a Luxul access point", "Matched signature $raw.", 6)
                n == "sophos" ->
                    Hint("ap", "a Sophos firewall or access point", "Matched signature $raw.", 7)
                n == "aumovio" ->
                    Hint("hotspot", "an AUMOVIO / Continental vehicle Wi-Fi radio", "Matched signature $raw.", 6)
                n == "centurylink" ->
                    Hint("ap", "a CenturyLink gateway", "Matched signature $raw.", 6)
                n == "gm hotspot" ->
                    Hint("hotspot", "a GM in-car hotspot (Cadillac / GMC / Buick / Chevrolet)", "Matched signature $raw.", 6)
                n == "audi mmi" ->
                    Hint("hotspot", "an Audi MMI in-car hotspot", "Matched signature $raw.", 6)
                n == "extreme" ->
                    Hint("ap", "an Extreme Networks access point", "Matched signature $raw.", 7)
                n == "adtran" ->
                    Hint("ap", "an Adtran fiber gateway (often CenturyLink / Quantum Fiber OEM)", "Matched signature $raw.", 6)
                n == "cambium" ->
                    Hint("ap", "a Cambium or IgniteNet access point", "Matched signature $raw.", 6)
                n == "trendnet" ->
                    Hint("ap", "a TRENDnet access point", "Matched signature $raw.", 6)
                n == "cudy" ->
                    Hint("ap", "a Cudy travel or home router", "Matched signature $raw.", 6)
                n == "snapav" ->
                    Hint("ap", "a SnapAV / Control4 / Wattbox access point", "Matched signature $raw.", 6)
                n == "arlo" ->
                    Hint("camera", "an Arlo camera or VMB base station", "Matched signature $raw.", 6)
                n == "vantiva" ->
                    Hint("ap", "a Vantiva or Technicolor ISP gateway", "Matched signature $raw.", 6)
                n == "hitron" ->
                    Hint("ap", "a Hitron cable gateway (often Xfinity OEM)", "Matched signature $raw.", 6)
                n == "actiontec" ->
                    Hint("ap", "an Actiontec FiOS or Frontier gateway", "Matched signature $raw.", 6)
                n == "buffalo" ->
                    Hint("ap", "a Buffalo AirStation or router", "Matched signature $raw.", 6)
                n == "grandstream" ->
                    Hint("ap", "a Grandstream GWN access point", "Matched signature $raw.", 6)
                n == "edgecore" ->
                    Hint("ap", "an Edgecore access point", "Matched signature $raw.", 7)
                n == "watchguard ap" ->
                    Hint("ap", "a WatchGuard firewall or access point", "Matched signature $raw.", 7)
                n == "mojo" ->
                    Hint("ap", "a Mojo Networks / Arista Cognitive Wi-Fi access point", "Matched signature $raw.", 7)
                n == "winegard" ->
                    Hint("hotspot", "a Winegard RV or marine Wi-Fi radio", "Matched signature $raw.", 6)
                n == "inseego" ->
                    Hint("ap", "an Inseego 5G or MiFi hotspot", "Matched signature $raw.", 6)
                n == "franklin" ->
                    Hint("ap", "a Franklin Technology 5G home-internet gateway (RG3100 class)", "Matched signature $raw.", 6)
                n == "synology" ->
                    Hint("ap", "a Synology NAS or router access point", "Matched signature $raw.", 6)
                n == "aruba" ->
                    Hint("ap", "an HPE Aruba Instant or Instant On access point", "Matched signature $raw.", 7)
                n == "ruckus" ->
                    Hint("ap", "a RUCKUS access point", "Matched signature $raw.", 7)
                n == "fortinet" ->
                    Hint("ap", "a Fortinet FortiAP or FortiWiFi", "Matched signature $raw.", 7)
                n == "mikrotik" ->
                    Hint("ap", "a MikroTik router or access point", "Matched signature $raw.", 6)
                n == "engenius" ->
                    Hint("ap", "an EnGenius access point", "Matched signature $raw.", 6)
                n == "zyxel" ->
                    Hint("ap", "a Zyxel gateway or access point", "Matched signature $raw.", 6)
                n == "peplink" ->
                    Hint("ap", "a Peplink or Pepwave router", "Matched signature $raw.", 6)
                n == "openwrt" ->
                    Hint("ap", "an OpenWrt router", "Matched signature $raw.", 6)
                n == "arris" ->
                    Hint("ap", "an Arris or SURFboard cable gateway", "Matched signature $raw.", 6)
                n == "unifi ap" ->
                    Hint("ap", "a Ubiquiti UniFi access point", "Matched signature $raw.", 7)
                n == "unifi protect" ->
                    Hint("camera", "a UniFi Protect Instant camera", "Matched signature $raw.", 7)
                n == "unifi" ->
                    Hint("ap", "a UniFi / Ubiquiti name", "Matched signature $raw.", 5)
                n == "ecobee" ->
                    Hint("thermostat", "an ecobee thermostat", "Matched signature $raw.", 7)
                n == "sensi" ->
                    Hint("thermostat", "a Sensi thermostat", "Matched signature $raw.", 6)
                n == "honeywell home" ->
                    Hint("thermostat", "a Honeywell Home or Lyric thermostat", "Matched signature $raw.", 6)
                "honeywell xenon" in n ->
                    Hint("health", "a Honeywell Xenon healthcare barcode scanner", "Matched signature $raw.", 7)
                n == "omron" ->
                    Hint("health", "an Omron blood-pressure cuff or scale", "Matched signature $raw.", 7)
                n == "withings" ->
                    Hint("health", "a Withings scale or blood-pressure monitor", "Matched signature $raw.", 7)
                n == "dexcom" ->
                    Hint("health", "a Dexcom glucose sensor", "Matched signature $raw.", 7)
                n == "nest thermostat" ->
                    Hint("thermostat", "a Nest thermostat or Nest Labs BLE sensor", "Matched signature $raw.", 6)
                n == "nest weave" ->
                    Hint("sensor", "a Nest Protect, camera, or other Weave BLE device", "Matched signature $raw.", 7)
                n == "haiku fan" || n == "haiku" ->
                    Hint("fan", "a Haiku or Mammoth ceiling fan", "Matched signature $raw.", 7)
                n == "tuya" ->
                    Hint("iot", "a Tuya BLE gadget (plug, light, camera, sensor)", "Matched signature $raw.", 6)
                n == "seos" || n == "assa abloy" ->
                    Hint("access", "an ASSA ABLOY lock, Yale lock, HID reader, or Seos credential", "Matched signature $raw.", 7)
                n == "august" ->
                    Hint("lock", "an August smart lock", "Matched signature $raw.", 7)
                n == "schlage" ->
                    Hint("lock", "a Schlage or Allegion lock", "Matched signature $raw.", 7)
                n == "nuki" ->
                    Hint("lock", "a Nuki lock or opener", "Matched signature $raw.", 7)
                n == "salto" ->
                    Hint("access", "a SALTO access reader or lock", "Matched signature $raw.", 7)
                n == "dormakaba" ->
                    Hint("access", "a dormakaba, Saflok, or Oracode lock", "Matched signature $raw.", 7)
                n == "lockly" ->
                    Hint("lock", "a Lockly smart lock", "Matched signature $raw.", 6)
                n == "kevo" ->
                    Hint("lock", "a Kwikset Kevo or Unikey lock", "Matched signature $raw.", 7)
                n == "master lock" ->
                    Hint("lock", "a Master Lock padlock", "Matched signature $raw.", 7)
                n == "igloohome" ->
                    Hint("lock", "an igloohome lock or keybox", "Matched signature $raw.", 7)
                n == "tedee" ->
                    Hint("lock", "a Tedee smart lock", "Matched signature $raw.", 7)
                n == "paxton" ->
                    Hint("access", "a Paxton reader or Net2 access point", "Matched signature $raw.", 7)
                n == "kwikset" ->
                    Hint("lock", "a Kwikset lock", "Matched signature $raw.", 6)
                n == "myq" ->
                    Hint("garage", "a Chamberlain myQ garage hub", "Matched signature $raw.", 7)
                n == "chevrolet hotspot" ->
                    Hint("hotspot", "a Chevrolet in-car Wi-Fi hotspot", "Matched signature $raw.", 7)
                n == "rivian" ->
                    Hint("vehicle", "a Rivian vehicle, phone key, or sensor", "Matched signature $raw.", 7)
                n == "ford" ->
                    Hint("vehicle", "a Ford or Lincoln vehicle or phone-as-key", "Matched signature $raw.", 7)
                n == "honda" ->
                    Hint("vehicle", "a Honda or Acura vehicle or phone-as-key", "Matched signature $raw.", 7)
                n == "hyundai" ->
                    Hint("vehicle", "a Hyundai or Genesis vehicle or phone-as-key", "Matched signature $raw.", 7)
                n == "toyota" ->
                    Hint("vehicle", "a Toyota or Lexus vehicle or phone-as-key", "Matched signature $raw.", 7)
                n == "nissan" ->
                    Hint("vehicle", "a Nissan or Infiniti vehicle or phone-as-key", "Matched signature $raw.", 7)
                n == "subaru" ->
                    Hint("vehicle", "a Subaru vehicle or phone-as-key", "Matched signature $raw.", 7)
                n == "bmw" ->
                    Hint("vehicle", "a BMW vehicle, phone-as-key, or factory hotspot", "Matched signature $raw.", 7)
                n == "volkswagen" ->
                    Hint("vehicle", "a Volkswagen vehicle or phone-as-key", "Matched signature $raw.", 7)
                n == "porsche" ->
                    Hint("vehicle", "a Porsche vehicle or phone-as-key", "Matched signature $raw.", 7)
                n == "jaguar land rover" ->
                    Hint("vehicle", "a Jaguar, Land Rover, or Range Rover", "Matched signature $raw.", 7)
                n == "byd" ->
                    Hint("vehicle", "a BYD vehicle or phone-as-key", "Matched signature $raw.", 7)
                n == "govee" ->
                    Hint("light", "a Govee light or sensor", "Matched signature $raw.", 6)
                n == "hp" ->
                    Hint("printer", "an HP printer", "Matched signature $raw.", 6)
                n == "epson" ->
                    Hint("printer", "an Epson EcoTank or WorkForce printer", "Matched signature $raw.", 6)
                n == "lg webos tv" ->
                    Hint("tv", "an LG webOS TV", "Matched signature $raw.", 7)
                n == "roku" ->
                    Hint("tv", "a Roku streaming stick or Roku TV (often a hidden Wi-Fi Direct remote AP)", "Matched signature $raw.", 7)
                n == "samsung appliance" ->
                    Hint("iot", "a Samsung fridge, range, oven, or cooktop (setup AP)", "Matched signature $raw.", 6)
                n == "ecowater" ->
                    Hint("iot", "an EcoWater water softener (setup AP)", "Matched signature $raw.", 6)
                n == "nespresso" ->
                    Hint("iot", "a Nespresso coffee machine", "Matched signature $raw.", 7)
                n == "radiacode" ->
                    Hint("sensor", "a RadiaCode radiation detector", "Matched signature $raw.", 7)
                n == "shokz" ->
                    Hint("audio-personal", "Shokz OpenRun or OpenFit headphones", "Matched signature $raw.", 7)
                n == "mercedes mbux" ->
                    Hint("hotspot", "a Mercedes MBUX in-car hotspot", "Matched signature $raw.", 7)
                n == "motive" ->
                    Hint("hotspot", "a Motive / KeepTruckin fleet ELD hotspot", "Matched signature $raw.", 6)
                n == "peoplenet" ->
                    Hint("hotspot", "a PeopleNet fleet ELD hotspot", "Matched signature $raw.", 6)
                n == "uconnect" ->
                    Hint("hotspot", "a Uconnect in-car hotspot", "Matched signature $raw.", 6)
                n == "carplay" ->
                    Hint("hotspot", "a CarPlay in-car hotspot", "Matched signature $raw.", 6)
                n == "cradlepoint" ->
                    Hint("hotspot", "a Cradlepoint vehicle router (often public-safety / fleet)", "Matched signature $raw.", 7)
                n == "airlink" ->
                    Hint("hotspot", "a Sierra Wireless AirLink vehicle gateway", "Matched signature $raw.", 7)
                n == "compex" ->
                    Hint("hotspot", "a Compex access point (sometimes public-safety / fleet)", "Matched signature $raw.", 6)
                n == "novatel wireless" ->
                    Hint("hotspot", "a Novatel Wireless / Inseego vehicle radio", "Matched signature $raw.", 6)
                n == "utility inc" ->
                    Hint("hotspot", "a Utility, Inc vehicle or public-safety radio", "Matched signature $raw.", 6)
                "gl.inet" in n || n == "glinet" ->
                    Hint("ap", "a GL.iNet travel router", "Matched signature $raw.", 6)
                "smarttag" in n ->
                    Hint("tag", "a Samsung SmartTag", "Matched signature $raw.", 8)
                "tile" in n ->
                    Hint("tag", "a Tile tracker", "Matched signature $raw.", 8)
                n == "ibeacon" ->
                    Hint("beacon", "an iBeacon", "Matched signature $raw.", 7)
                "target atrius" in n ->
                    Hint("beacon", "a Target Atrius basket tag", "Matched signature $raw.", 8)
                n == "minew" ->
                    Hint("beacon", "a Minew BLE beacon or sensor", "Matched signature $raw.", 7)
                n == "estimote" ->
                    Hint("beacon", "an Estimote beacon", "Matched signature $raw.", 7)
                n == "kontakt.io" || n == "kontakt" ->
                    Hint("beacon", "a Kontakt.io beacon", "Matched signature $raw.", 7)
                "bluetoad" in n ->
                    Hint(
                        "roadside",
                        "an Iteris BlueTOAD / Vantage Velocity roadside Bluetooth travel-time reader",
                        "Matched signature $raw.",
                        7,
                    )
                "bliptrack" in n ->
                    Hint(
                        "roadside",
                        "a BLIP Systems BlipTrack roadside travel-time sensor",
                        "Matched signature $raw.",
                        7,
                    )
                "hanwha" in n || "wisenet" in n ->
                    Hint("camera", "a Hanwha Vision / Wisenet camera", "Matched signature $raw.", 7)
                n == "uniview" ->
                    Hint("camera", "a Uniview / UNV camera", "Matched signature $raw.", 7)
                n == "rhombus" ->
                    Hint("camera", "a Rhombus cloud camera", "Matched signature $raw.", 7)
                n == "meshcore" ->
                    Hint("mesh", "a MeshCore LoRa companion radio", "Matched signature $raw.", 7)
                "gotenna" in n ->
                    Hint("mesh", "a goTenna Mesh or Pro radio", "Matched signature $raw.", 7)
                n == "sensecap" ->
                    Hint("mesh", "a SenseCAP LoRaWAN / Helium gateway", "Matched signature $raw.", 7)
                "wisgate" in n || n == "rak wisgate" ->
                    Hint("mesh", "a RAK WisGate LoRaWAN gateway", "Matched signature $raw.", 7)
                n == "ghostesp" ->
                    Hint("pentest", "a GhostESP ESP32 audit board", "Matched signature $raw.", 7)
                n == "bruce" ->
                    Hint("pentest", "a Bruce ESP32 pentest board", "Matched signature $raw.", 7)
                "chipolo" in n || "pebblebee" in n || "moto tag" in n ->
                    Hint("tag", "a finder tag", "Matched signature $raw.", 7)
                "airpods" in n ->
                    Hint("audio-personal", "AirPods", "Matched signature $raw.", 8)
                else -> Hint("named", raw, "Matched signature $raw.", 3)
            }
        }
    }

    private fun wifiHints(device: Sighting): List<Hint> {
        val name = device.name
        val caps = (device.facts.capabilities ?: "").uppercase()
        val out = ArrayList<Hint>(2)
        when {
            name.startsWith("DIRECT-", true) ->
                out += Hint("wifi-direct", "a phone or TV using Wi-Fi Direct", "SSID starts with DIRECT-.", 6)
            name.startsWith("ANDROID-", true) || name.contains("hotspot", true) ->
                out += Hint("hotspot", "a phone hotspot", "SSID looks like a phone hotspot.", 6)
            "MESH" in caps ->
                out += Hint("mesh", "a mesh Wi-Fi node", "Capability list includes mesh.", 5)
            device.hiddenSsid ->
                out += Hint("ap", "a hidden Wi-Fi access point", "SSID is hidden; the radio is still beaconing.", 4)
            else ->
                out += Hint("ap", "a Wi-Fi access point", "Stock Android only reports beaconing APs.", 3)
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
