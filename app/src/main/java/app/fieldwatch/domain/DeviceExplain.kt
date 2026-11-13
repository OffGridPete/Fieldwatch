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
        val generic: Boolean = false,
        val bare: String = "",
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
            val bare = if (device.kind == RadioKind.WIFI) {
                tx("ui.wi.fi.access.point.2")
            } else {
                tx("explain.ble.adv")
            }
            return Guess(
                headline = bare,
                because = tx("explain.no.class"),
                confidence = Confidence.LOW,
                generic = true,
                bare = bare,
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
            Confidence.HIGH -> "explain.likely"
            Confidence.MEDIUM -> "explain.probably"
            Confidence.LOW -> "explain.could"
        }
        return Guess(
            headline = tx(hedge, mapOf("what" to best.label)),
            because = support.joinToString(" ") + " " + tx("explain.advertised"),
            confidence = confidence,
            generic = best.generic,
            bare = best.label,
        )
    }

    /**
     * Compact Live-row title from the same guess as detail. Null if we only
     * know it is an unnamed advertiser — caller may fall back to vendor.
     */
    fun listLabel(device: Sighting, signatureNames: List<String> = emptyList()): String? {
        val guess = guess(device, signatureNames)
        val core = if (guess.generic) null else tidyHeadline(guess.bare.ifBlank { guess.headline })
        val vendor = device.vendor?.trim()?.takeIf { it.isNotBlank() && it.length <= 24 }
        if (core != null) {
            return if (vendor != null && !core.contains(vendor, ignoreCase = true)) {
                "$vendor · $core"
            } else {
                core
            }
        }
        if (vendor != null) return tx("explain.vendor.device", mapOf("vendor" to vendor))
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
            add(tx("explain.flag.limited"))
        }
        if (flags and 0x02 != 0) {
            add(tx("explain.flag.discoverable"))
        }
        if (flags and 0x04 != 0) {
            add(tx("explain.flag.ble.only"))
        } else {
            add(tx("explain.flag.classic"))
        }
        if (flags and 0x08 != 0 || flags and 0x10 != 0) {
            add(tx("explain.flag.dual"))
        }
    }.joinToString(" ")

    fun phyExplain(label: String): String = when {
        label.contains("Coded") -> tx("explain.phy.coded", mapOf("label" to label))
        label.contains("2M") -> tx("explain.phy.2m", mapOf("label" to label))
        label.contains("1M") -> tx("explain.phy.1m", mapOf("label" to label))
        else -> label
    }

    fun addressExplain(device: Sighting): String {
        val type = device.facts.addressType
        return when {
            device.kind == RadioKind.WIFI && device.randomized ->
                tx("explain.addr.bssid")
            MacUtil.isLocallyAdministered(device.mac) && !device.randomized ->
                tx("explain.addr.local")
            type.equals("Public", true) && !device.randomized ->
                tx("explain.addr.public")
            type.equals("Random", true) || device.randomized ->
                tx("explain.addr.random")
            type.equals("Anonymous", true) ->
                tx("explain.addr.anon")
            else ->
                listOfNotNull(type, tx("explain.addr.universal")).joinToString(" · ")
        }
    }

    fun rssiBand(rssi: Int): String = when {
        !Rssi.measured(rssi) -> LanguageText.text("ui.rssi.band.unavailable")
        rssi >= -45 -> LanguageText.text("ui.rssi.very.strong")
        rssi >= -60 -> LanguageText.text("ui.rssi.strong")
        rssi >= -75 -> LanguageText.text("ui.rssi.medium")
        rssi >= -88 -> LanguageText.text("ui.rssi.weak")
        else -> LanguageText.text("ui.rssi.very.weak")
    }

    fun rssiExplain(rssi: Int): String =
        if (!Rssi.measured(rssi)) LanguageText.text("ui.rssi.not.available")
        else "${rssi} dBm · ${rssiBand(rssi)}"

    fun wifiSecurityExplain(raw: String): String {
        val bits = ArrayList<String>(4)
        val u = raw.uppercase()
        when {
            "SAE" in u || "WPA3" in u -> bits += tx("explain.sec.wpa3")
            "OWE" in u -> bits += tx("explain.sec.owe")
            "PSK" in u && "WPA2" in u -> bits += tx("explain.sec.wpa2")
            "PSK" in u || "WPA" in u -> bits += tx("explain.sec.wpa")
            "802.1X" in u || "EAP" in u -> bits += tx("explain.sec.enterprise")
            "WEP" in u -> bits += tx("explain.sec.wep")
            "ESS" in u && bits.isEmpty() -> bits += tx("explain.sec.open")
        }
        when {
            "CCMP" in u || "GCMP" in u -> bits += tx("explain.sec.aes")
            "TKIP" in u -> bits += tx("explain.sec.tkip")
        }
        if ("WPS" in u) bits += tx("explain.sec.wps")
        if ("MESH" in u) bits += tx("explain.sec.mesh")
        if ("IBSS" in u) bits += tx("explain.sec.ibss")
        if ("ESS" in u) bits += tx("explain.sec.ess")
        return if (bits.isEmpty()) raw else bits.distinct().joinToString(". ") + "."
    }

    fun uuidGloss(uuid: String): String? {
        val name = RadioDb.serviceUuid(uuid)
        val short = uuid16(uuid) ?: return name
        val extra = when (short) {
            0x1800 -> tx("explain.uuid.1800")
            0x1801 -> tx("explain.uuid.1801")
            0x180A -> tx("explain.uuid.180a")
            0x180F -> tx("explain.uuid.180f")
            0x1812 -> tx("explain.uuid.1812")
            0x180D -> tx("explain.uuid.180d")
            0x1810 -> tx("explain.uuid.1810")
            0x181A -> tx("explain.uuid.181a")
            0x1844, 0x1845, 0x1846 -> tx("explain.uuid.cycle")
            0x1850, 0x184E, 0x184F -> tx("explain.uuid.le.audio")
            0xFE2C -> tx("explain.uuid.fastpair")
            0xFD5A -> tx("explain.uuid.smarttag")
            0xFD44 -> tx("explain.uuid.findmy")
            0xFEED, 0xFEDD, 0xFEEC -> tx("explain.uuid.tile")
            0xFEEE -> tx("explain.uuid.polar")
            0xFE33 -> tx("explain.uuid.chipolo")
            0xFD50 -> tx("explain.uuid.tuya")
            0xFEBE, 0xFE21 -> tx("explain.uuid.bose")
            0xFE78 -> tx("explain.uuid.hp")
            0xFE07 -> tx("explain.uuid.sonos")
            0xFEAF, 0xFEB0 -> tx("explain.uuid.nest")
            0xFCBF -> tx("explain.uuid.assa")
            0xFE24 -> tx("explain.uuid.august")
            0xFCF4 -> tx("explain.uuid.allegion")
            0xFCB2 -> tx("explain.uuid.apple")
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
                Triple("audio-personal", tx("explain.earbuds"), 7)
            "speaker" in n || "loudspeaker" in n || "hifi" in n ->
                Triple("audio-speaker", tx("explain.speaker"), 7)
            "mouse" in n -> Triple("mouse", tx("explain.mouse"), 8)
            "keyboard" in n -> Triple("keyboard", tx("explain.keyboard"), 8)
            "gamepad" in n || "joystick" in n -> Triple("gamepad", tx("explain.gamepad"), 7)
            "watch" in n -> Triple("watch", tx("explain.watch"), 7)
            "phone" in n -> Triple("phone", tx("explain.phone"), 6)
            "laptop" in n || "computer" in n || "desktop" in n || "tablet" in n ->
                Triple("computer", tx("explain.computer.tablet"), 6)
            "tag" in n || "keyring" in n -> Triple("tag", tx("explain.finder"), 6)
            "remote" in n -> Triple("remote", tx("explain.remote"), 6)
            "hid" in n -> Triple("hid", tx("explain.hid"), 4)
            "heart" in n -> Triple("health", tx("explain.heart"), 7)
            "glucose" in n || "oximeter" in n || "blood pressure" in n || "thermometer" in n ->
                Triple("health", tx("explain.health"), 6)
            "display" in n || "monitor" in n -> Triple("display", tx("explain.display"), 4)
            "clock" in n -> Triple("clock", tx("explain.clock"), 5)
            "glasses" in n -> Triple("glasses", tx("explain.glasses"), 6)
            else -> Triple("other", name, 3)
        }
        return Hint(bucket, label, tx("explain.appearance.as", mapOf("name" to name)), w)
    }

    private fun codHint(cod: CodDecoder.Decoded): Hint? {
        val minor = cod.minor.lowercase()
        val major = cod.major.lowercase()
        val (bucket, label, w) = when {
            "headphone" in minor || "headset" in minor || "hands-free" in minor ->
                Triple("audio-personal", tx("explain.headset"), 6)
            "loudspeaker" in minor || "portable audio" in minor || "hifi" in minor || "car audio" in minor ->
                Triple("audio-speaker", tx("explain.speaker"), 6)
            "pointing" in minor || minor == "mouse" -> Triple("mouse", tx("explain.mouse"), 7)
            "keyboard" in minor -> Triple("keyboard", tx("explain.keyboard"), 7)
            "gamepad" in minor || "joystick" in minor -> Triple("gamepad", tx("explain.gamepad"), 6)
            "smartphone" in minor || (major == "phone" && "uncategorized" !in minor) ->
                Triple("phone", tx("explain.phone"), 5)
            "laptop" in minor || "tablet" in minor || "desktop" in minor ->
                Triple("computer", tx("explain.computer"), 5)
            "wristwatch" in minor -> Triple("watch", tx("explain.watch.short"), 6)
            "heart" in minor || "pulse" in minor || "glucose" in minor || "oximeter" in minor ->
                Triple("health", tx("explain.health"), 6)
            "audio" in major -> Triple("audio-personal", tx("explain.audio"), 3)
            "peripheral" in major -> Triple("hid", tx("explain.input"), 3)
            "uncategorized" in major || "miscellaneous" in major -> return null
            else -> return null
        }
        val shown = if (cod.minor.isNotBlank() && cod.minor != "Uncategorized") {
            "${cod.major} / ${cod.minor}"
        } else {
            cod.major
        }
        return Hint(bucket, label, tx("explain.cod.says", mapOf("name" to shown)), w)
    }

    private fun uuidHints(uuids: List<String>): List<Hint> {
        val out = ArrayList<Hint>(4)
        for (uuid in uuids) {
            val id = uuid16(uuid) ?: continue
            when (id) {
                0x1812 -> out += Hint("hid", tx("explain.hid.service"), tx("explain.hid.why"), 5)
                0x1108, 0x1112, 0x111E, 0x110B, 0x110A, 0x1131, 0x1203 ->
                    out += Hint("audio-personal", tx("explain.classic.audio"), tx("explain.classic.audio.why"), 5)
                0x184E, 0x184F, 0x1850, 0x1851 ->
                    out += Hint("audio-personal", tx("explain.le.audio"), tx("explain.le.audio.why"), 6)
                0x180D -> out += Hint("health", tx("explain.heart"), tx("explain.heart.why"), 6)
                0x1810 -> out += Hint("health", tx("explain.bp"), tx("explain.bp.why"), 6)
                0x181A -> out += Hint("sensor", tx("explain.env"), tx("explain.env.why"), 4)
                0xFE2C -> out += Hint("audio-personal", tx("explain.buds"), tx("explain.fastpair.why"), 4)
                0xFD5A -> out += Hint("tag", tx("explain.smarttag"), tx("explain.smarttag.why"), 7)
                0xFD44 -> out += Hint("tag", tx("explain.findmy.acc"), tx("explain.findmy.why"), 6)
                0xFEED, 0xFEDD, 0xFEEC -> out += Hint("tag", tx("explain.tile"), tx("explain.tile.why"), 7)
                0xFEEE -> out += Hint("wearable", tx("explain.polar"), tx("explain.polar.why"), 7)
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
                            "dult" in n -> "a DULT finder tag"
                            "find hub" in n -> "a Google Find Hub tag"
                            else -> "an Apple AirTag / Find My tag"
                        },
                        matched(raw),
                        8,
                    )
                "apple device" in n ->
                    Hint("phone", "an iPhone, iPad, or Mac", matched(raw), 7)
                "apple audio" in n ->
                    Hint("audio-personal", "AirPods, Beats, or AirPlay", matched(raw), 7)
                "microsoft" in n ->
                    Hint("computer", "a Windows / Surface / Xbox radio", matched(raw), 6)
                n == "tesla tstpms" ->
                    Hint("vehicle", "a Tesla BLE tire sensor", matched(raw), 7)
                "tpms" in n || n == "tirecheck" || n == "sytpms" ->
                    Hint("vehicle", "a BLE tire-pressure sensor", matched(raw), 7)
                n == "vuzix" ->
                    Hint("glasses", "Vuzix smart glasses", matched(raw), 7)
                n == "tesla" ->
                    Hint("vehicle", "a Tesla vehicle (including Cybertruck) or phone-as-key", matched(raw), 7)
                n == "google" ->
                    Hint("phone", "a Pixel or other Google radio", matched(raw), 6)
                n == "sony" ->
                    Hint("audio-personal", "Sony headphones, a TV, or a camera", matched(raw), 6)
                n == "bose" ->
                    Hint("audio-personal", "Bose headphones or a speaker", matched(raw), 7)
                n == "garmin" ->
                    Hint("watch", "a Garmin watch or inReach", matched(raw), 7)
                n == "amazon" ->
                    Hint("speaker", "an Echo, Fire, or other Amazon radio", matched(raw), 6)
                n == "fitbit" ->
                    Hint("watch", "a Fitbit", matched(raw), 7)
                n == "oura" ->
                    Hint("wearable", "an Oura ring", matched(raw), 7)
                n == "logitech" ->
                    Hint("hid", "a Logitech mouse, keyboard, or webcam", matched(raw), 6)
                "jbl" in n || n == "harman" ->
                    Hint("audio-personal", "JBL or Harman audio", matched(raw), 6)
                n == "sonos" ->
                    Hint("audio-speaker", "a Sonos speaker", matched(raw), 7)
                n == "gopro" ->
                    Hint("camera", "a GoPro", matched(raw), 7)
                n == "osmo" ->
                    Hint("camera", "a DJI Osmo action camera", matched(raw), 7)
                n == "ezviz" ->
                    Hint("camera", "an EZVIZ camera on its setup network", matched(raw), 7)
                n == "insta360" ->
                    Hint("camera", "an Insta360 camera", matched(raw), 7)
                n == "dji power" ->
                    Hint("iot", "a DJI Power station", matched(raw), 7)
                n == "xiaomi mi home" ->
                    Hint("iot", "a Xiaomi Mi Home device", matched(raw), 7)
                n == "dji" ->
                    Hint("drone", "a DJI drone or controller", matched(raw), 7)
                n == "remote id" ->
                    Hint("drone", "a drone broadcasting ASTM Remote ID", matched(raw), 8)
                n == "skydio" ->
                    Hint("drone", "a Skydio drone", matched(raw), 7)
                n == "autel" ->
                    Hint("drone", "an Autel drone", matched(raw), 7)
                n == "parrot" ->
                    Hint("drone", "a Parrot ANAFI or Bebop drone", matched(raw), 7)
                n == "hoverair" ->
                    Hint("drone", "a HOVERAir flying camera", matched(raw), 7)
                n == "expresslrs" ->
                    Hint("drone", "an ExpressLRS radio in Wi-Fi setup", matched(raw), 7)
                n == "icom id-52" ->
                    Hint("radio", "an Icom ID-52 handheld", matched(raw), 7)
                n == "icom vs-3" ->
                    Hint("radio", "an Icom VS-3 headset", matched(raw), 7)
                n == "yaesu ssm-bt10" ->
                    Hint("radio", "a Yaesu SSM-BT10 headset", matched(raw), 7)
                n == "yaesu ssm-bt20" ->
                    Hint("radio", "a Yaesu SSM-BT20 headset", matched(raw), 7)
                n == "kenwood khs-55bt" ->
                    Hint("radio", "a Kenwood KHS-55BT headset", matched(raw), 7)
                n == "kenwood khs-56bt" ->
                    Hint("radio", "a Kenwood KHS-56BT headset", matched(raw), 7)
                n == "vgc bhm-88" ->
                    Hint("radio", "a VGC BHM-88 headset", matched(raw), 7)
                n == "benshi radio" ->
                    Hint("radio", "a Benshi handheld or mobile radio", matched(raw), 7)
                n == "ble ptt button" ->
                    Hint("radio", "a Bluetooth push-to-talk button", matched(raw), 7)
                n == "netgear" || n == "orbi" ->
                    Hint("ap", "a NETGEAR or Orbi access point", matched(raw), 6)
                n == "tp-link" ->
                    Hint("ap", "a TP-Link access point", matched(raw), 6)
                n == "asus" ->
                    Hint("ap", "an ASUS access point", matched(raw), 6)
                n == "linksys" ->
                    Hint("ap", "a Linksys or Velop access point", matched(raw), 6)
                n == "eero" ->
                    Hint("ap", "an Eero mesh node", matched(raw), 6)
                n == "google wifi" ->
                    Hint("ap", "a Google Wifi or Nest Wifi point", matched(raw), 6)
                n == "d-link" ->
                    Hint("ap", "a D-Link access point", matched(raw), 6)
                n == "belkin" ->
                    Hint("ap", "a Belkin access point", matched(raw), 6)
                n == "xfinity" ->
                    Hint("ap", "an Xfinity gateway or hotspot", matched(raw), 6)
                n == "spectrum" ->
                    Hint("ap", "a Spectrum gateway or Spectrum Mobile hotspot", matched(raw), 6)
                n == "at&t" ->
                    Hint("ap", "an AT&T gateway or attwifi hotspot", matched(raw), 6)
                n == "verizon" ->
                    Hint("ap", "a Verizon or Fios gateway", matched(raw), 6)
                n == "starlink" ->
                    Hint("ap", "a Starlink router", matched(raw), 7)
                n == "meraki" ->
                    Hint("ap", "a Cisco Meraki access point", matched(raw), 7)
                n == "cisco" ->
                    Hint("ap", "a Cisco Aironet, Catalyst, Business, RV, or SPVTG access point", matched(raw), 7)
                n == "mist" ->
                    Hint("ap", "a Juniper Mist access point", matched(raw), 7)
                n == "t-mobile" ->
                    Hint("ap", "a T-Mobile Home Internet gateway or hotspot", matched(raw), 6)
                n == "humax" ->
                    Hint("ap", "a HUMAX gateway (often T-Mobile Home Internet)", matched(raw), 6)
                n == "sagemcom" ->
                    Hint("ap", "a Sagemcom ISP gateway", matched(raw), 6)
                n == "arcadyan" ->
                    Hint("ap", "an Arcadyan ISP gateway", matched(raw), 6)
                n == "askey" ->
                    Hint("ap", "an Askey ISP / 5G gateway", matched(raw), 6)
                n == "calix" ->
                    Hint("ap", "a Calix fiber gateway", matched(raw), 6)
                n == "nokia" ->
                    Hint("ap", "a Nokia Solutions and Networks gateway", matched(raw), 6)
                n == "airties" ->
                    Hint("ap", "an AirTies ISP mesh node", matched(raw), 6)
                n == "tenda" ->
                    Hint("ap", "a Tenda access point", matched(raw), 6)
                n == "ruijie" ->
                    Hint("ap", "a Ruijie or Reyee access point", matched(raw), 6)
                n == "dwnet" ->
                    Hint("ap", "a DWnet access point", matched(raw), 6)
                n == "wavlink" ->
                    Hint("ap", "a WAVLINK access point", matched(raw), 6)
                n == "sercomm" ->
                    Hint("ap", "a Sercomm ISP gateway", matched(raw), 6)
                n == "luxul" ->
                    Hint("ap", "a Luxul access point", matched(raw), 6)
                n == "sophos" ->
                    Hint("ap", "a Sophos firewall or access point", matched(raw), 7)
                n == "aumovio" ->
                    Hint("hotspot", "an AUMOVIO / Continental vehicle Wi-Fi radio", matched(raw), 6)
                n == "centurylink" ->
                    Hint("ap", "a CenturyLink gateway", matched(raw), 6)
                n == "gm hotspot" ->
                    Hint("hotspot", "a GM in-car hotspot (Cadillac / GMC / Buick / Chevrolet)", matched(raw), 6)
                n == "audi mmi" ->
                    Hint("hotspot", "an Audi MMI in-car hotspot", matched(raw), 6)
                n == "extreme" ->
                    Hint("ap", "an Extreme Networks access point", matched(raw), 7)
                n == "adtran" ->
                    Hint("ap", "an Adtran fiber gateway (often CenturyLink / Quantum Fiber OEM)", matched(raw), 6)
                n == "cambium" ->
                    Hint("ap", "a Cambium or IgniteNet access point", matched(raw), 6)
                n == "trendnet" ->
                    Hint("ap", "a TRENDnet access point", matched(raw), 6)
                n == "cudy" ->
                    Hint("ap", "a Cudy travel or home router", matched(raw), 6)
                n == "snapav" ->
                    Hint("ap", "a SnapAV / Control4 / Wattbox access point", matched(raw), 6)
                n == "arlo" ->
                    Hint("camera", "an Arlo camera or VMB base station", matched(raw), 6)
                n == "vantiva" ->
                    Hint("ap", "a Vantiva or Technicolor ISP gateway", matched(raw), 6)
                n == "hitron" ->
                    Hint("ap", "a Hitron cable gateway (often Xfinity OEM)", matched(raw), 6)
                n == "actiontec" ->
                    Hint("ap", "an Actiontec FiOS or Frontier gateway", matched(raw), 6)
                n == "buffalo" ->
                    Hint("ap", "a Buffalo AirStation or router", matched(raw), 6)
                n == "grandstream" ->
                    Hint("ap", "a Grandstream GWN access point", matched(raw), 6)
                n == "edgecore" ->
                    Hint("ap", "an Edgecore access point", matched(raw), 7)
                n == "watchguard ap" ->
                    Hint("ap", "a WatchGuard firewall or access point", matched(raw), 7)
                n == "mojo" ->
                    Hint("ap", "a Mojo Networks / Arista Cognitive Wi-Fi access point", matched(raw), 7)
                n == "winegard" ->
                    Hint("hotspot", "a Winegard RV or marine Wi-Fi radio", matched(raw), 6)
                n == "inseego" ->
                    Hint("ap", "an Inseego 5G or MiFi hotspot", matched(raw), 6)
                n == "franklin" ->
                    Hint("ap", "a Franklin Technology 5G home-internet gateway (RG3100 class)", matched(raw), 6)
                n == "synology" ->
                    Hint("ap", "a Synology NAS or router access point", matched(raw), 6)
                n == "aruba" ->
                    Hint("ap", "an HPE Aruba Instant or Instant On access point", matched(raw), 7)
                n == "ruckus" ->
                    Hint("ap", "a RUCKUS access point", matched(raw), 7)
                n == "fortinet" ->
                    Hint("ap", "a Fortinet FortiAP or FortiWiFi", matched(raw), 7)
                n == "mikrotik" ->
                    Hint("ap", "a MikroTik router or access point", matched(raw), 6)
                n == "engenius" ->
                    Hint("ap", "an EnGenius access point", matched(raw), 6)
                n == "zyxel" ->
                    Hint("ap", "a Zyxel gateway or access point", matched(raw), 6)
                n == "peplink" ->
                    Hint("ap", "a Peplink or Pepwave router", matched(raw), 6)
                n == "openwrt" ->
                    Hint("ap", "an OpenWrt router", matched(raw), 6)
                n == "arris" ->
                    Hint("ap", "an Arris or SURFboard cable gateway", matched(raw), 6)
                n == "unifi ap" ->
                    Hint("ap", "a Ubiquiti UniFi access point", matched(raw), 7)
                n == "unifi protect" ->
                    Hint("camera", "a UniFi Protect Instant camera", matched(raw), 7)
                n == "unifi" ->
                    Hint("ap", "a UniFi / Ubiquiti name", matched(raw), 5)
                n == "ecobee" ->
                    Hint("thermostat", "an ecobee thermostat", matched(raw), 7)
                n == "sensi" ->
                    Hint("thermostat", "a Sensi thermostat", matched(raw), 6)
                n == "honeywell home" ->
                    Hint("thermostat", "a Honeywell Home or Lyric thermostat", matched(raw), 6)
                "honeywell xenon" in n ->
                    Hint("health", "a Honeywell Xenon healthcare barcode scanner", matched(raw), 7)
                n == "omron" ->
                    Hint("health", "an Omron blood-pressure cuff or scale", matched(raw), 7)
                n == "withings" ->
                    Hint("health", "a Withings scale or blood-pressure monitor", matched(raw), 7)
                n == "dexcom" ->
                    Hint("health", "a Dexcom glucose sensor", matched(raw), 7)
                n == "nest thermostat" ->
                    Hint("thermostat", "a Nest thermostat or Nest Labs BLE sensor", matched(raw), 6)
                n == "nest weave" ->
                    Hint("sensor", "a Nest Protect, camera, or other Weave BLE device", matched(raw), 7)
                n == "haiku fan" || n == "haiku" ->
                    Hint("fan", "a Haiku or Mammoth ceiling fan", matched(raw), 7)
                n == "tuya" ->
                    Hint("iot", "a Tuya BLE gadget (plug, light, camera, sensor)", matched(raw), 6)
                n == "seos" || n == "assa abloy" ->
                    Hint("access", "an ASSA ABLOY lock, Yale lock, HID reader, or Seos credential", matched(raw), 7)
                n == "august" ->
                    Hint("lock", "an August smart lock", matched(raw), 7)
                n == "schlage" ->
                    Hint("lock", "a Schlage or Allegion lock", matched(raw), 7)
                n == "nuki" ->
                    Hint("lock", "a Nuki lock or opener", matched(raw), 7)
                n == "salto" ->
                    Hint("access", "a SALTO access reader or lock", matched(raw), 7)
                n == "dormakaba" ->
                    Hint("access", "a dormakaba, Saflok, or Oracode lock", matched(raw), 7)
                n == "lockly" ->
                    Hint("lock", "a Lockly smart lock", matched(raw), 6)
                n == "kevo" ->
                    Hint("lock", "a Kwikset Kevo or Unikey lock", matched(raw), 7)
                n == "master lock" ->
                    Hint("lock", "a Master Lock padlock", matched(raw), 7)
                n == "igloohome" ->
                    Hint("lock", "an igloohome lock or keybox", matched(raw), 7)
                n == "tedee" ->
                    Hint("lock", "a Tedee smart lock", matched(raw), 7)
                n == "paxton" ->
                    Hint("access", "a Paxton reader or Net2 access point", matched(raw), 7)
                n == "kwikset" ->
                    Hint("lock", "a Kwikset lock", matched(raw), 6)
                n == "myq" ->
                    Hint("garage", "a Chamberlain myQ garage hub", matched(raw), 7)
                n == "chevrolet hotspot" ->
                    Hint("hotspot", "a Chevrolet in-car Wi-Fi hotspot", matched(raw), 7)
                n == "rivian" ->
                    Hint("vehicle", "a Rivian vehicle, phone key, or sensor", matched(raw), 7)
                n == "ford" ->
                    Hint("vehicle", "a Ford or Lincoln vehicle or phone-as-key", matched(raw), 7)
                n == "honda" ->
                    Hint("vehicle", "a Honda or Acura vehicle or phone-as-key", matched(raw), 7)
                n == "hyundai" ->
                    Hint("vehicle", "a Hyundai or Genesis vehicle or phone-as-key", matched(raw), 7)
                n == "toyota" ->
                    Hint("vehicle", "a Toyota or Lexus vehicle or phone-as-key", matched(raw), 7)
                n == "nissan" ->
                    Hint("vehicle", "a Nissan or Infiniti vehicle or phone-as-key", matched(raw), 7)
                n == "subaru" ->
                    Hint("vehicle", "a Subaru vehicle or phone-as-key", matched(raw), 7)
                n == "bmw" ->
                    Hint("vehicle", "a BMW vehicle, phone-as-key, or factory hotspot", matched(raw), 7)
                n == "volkswagen" ->
                    Hint("vehicle", "a Volkswagen vehicle or phone-as-key", matched(raw), 7)
                n == "porsche" ->
                    Hint("vehicle", "a Porsche vehicle or phone-as-key", matched(raw), 7)
                n == "jaguar land rover" ->
                    Hint("vehicle", "a Jaguar, Land Rover, or Range Rover", matched(raw), 7)
                n == "byd" ->
                    Hint("vehicle", "a BYD vehicle or phone-as-key", matched(raw), 7)
                n == "govee" ->
                    Hint("light", "a Govee light or sensor", matched(raw), 6)
                n == "hp" ->
                    Hint("printer", "an HP printer", matched(raw), 6)
                n == "epson" ->
                    Hint("printer", "an Epson EcoTank or WorkForce printer", matched(raw), 6)
                n == "lg webos tv" ->
                    Hint("tv", "an LG webOS TV", matched(raw), 7)
                n == "roku" ->
                    Hint("tv", "a Roku streaming stick or Roku TV (often a hidden Wi-Fi Direct remote AP)", matched(raw), 7)
                n == "samsung appliance" ->
                    Hint("iot", "a Samsung fridge, range, oven, or cooktop (setup AP)", matched(raw), 6)
                n == "ecowater" ->
                    Hint("iot", "an EcoWater water softener (setup AP)", matched(raw), 6)
                n == "nespresso" ->
                    Hint("iot", "a Nespresso coffee machine", matched(raw), 7)
                n == "radiacode" ->
                    Hint("sensor", "a RadiaCode radiation detector", matched(raw), 7)
                n == "shokz" ->
                    Hint("audio-personal", "Shokz OpenRun or OpenFit headphones", matched(raw), 7)
                n == "mercedes mbux" ->
                    Hint("hotspot", "a Mercedes MBUX in-car hotspot", matched(raw), 7)
                n == "motive" ->
                    Hint("hotspot", "a Motive / KeepTruckin fleet ELD hotspot", matched(raw), 6)
                n == "peoplenet" ->
                    Hint("hotspot", "a PeopleNet fleet ELD hotspot", matched(raw), 6)
                n == "uconnect" ->
                    Hint("hotspot", "a Uconnect in-car hotspot", matched(raw), 6)
                n == "carplay" ->
                    Hint("hotspot", "a CarPlay in-car hotspot", matched(raw), 6)
                n == "cradlepoint" ->
                    Hint("hotspot", "a Cradlepoint vehicle router (often public-safety / fleet)", matched(raw), 7)
                n == "airlink" ->
                    Hint("hotspot", "a Sierra Wireless AirLink vehicle gateway", matched(raw), 7)
                n == "compex" ->
                    Hint("hotspot", "a Compex access point (sometimes public-safety / fleet)", matched(raw), 6)
                n == "utility inc" ->
                    Hint("hotspot", "a Utility, Inc vehicle or public-safety radio", matched(raw), 6)
                "gl.inet" in n || n == "glinet" ->
                    Hint("ap", "a GL.iNet travel router", matched(raw), 6)
                "smarttag" in n ->
                    Hint("tag", "a Samsung SmartTag", matched(raw), 8)
                "tile" in n ->
                    Hint("tag", "a Tile tracker", matched(raw), 8)
                n == "ibeacon" ->
                    Hint("beacon", "an iBeacon", matched(raw), 7)
                "atrius" in n ->
                    Hint("beacon", "an Atrius cart tag", matched(raw), 8)
                n == "minew" ->
                    Hint("beacon", "a Minew BLE beacon or sensor", matched(raw), 7)
                n == "estimote" ->
                    Hint("beacon", "an Estimote beacon", matched(raw), 7)
                n == "kontakt.io" || n == "kontakt" ->
                    Hint("beacon", "a Kontakt.io beacon", matched(raw), 7)
                "bluetoad" in n ->
                    Hint(
                        "roadside",
                        "an Iteris BlueTOAD / Vantage Velocity roadside Bluetooth travel-time reader",
                        matched(raw),
                        7,
                    )
                "bliptrack" in n ->
                    Hint(
                        "roadside",
                        "a BLIP Systems BlipTrack roadside travel-time sensor",
                        matched(raw),
                        7,
                    )
                "raven" in n || "shotspotter" in n || "soundthinking" in n ->
                    Hint(
                        "acoustic",
                        "a Flock Raven or ShotSpotter acoustic gunshot sensor",
                        matched(raw),
                        8,
                    )
                "digital ally" in n ->
                    Hint("camera", "a Digital Ally body-worn or in-car camera", matched(raw), 8)
                "reveal media" in n || "bodyworn" in n ->
                    Hint("camera", "a Reveal Media body-worn camera", matched(raw), 8)
                n == "wolfcom" ->
                    Hint("camera", "a Wolfcom body-worn or in-car camera", matched(raw), 8)
                "i-pro" in n || "arbitrator" in n ->
                    Hint("camera", "a Panasonic i-PRO camera or Arbitrator in-car system", matched(raw), 8)
                "limitless" in n ->
                    Hint("wearable", "a Limitless Pendant conversation recorder", matched(raw), 8)
                n == "bee pendant" || "bee pioneer" in n ->
                    Hint("wearable", "a Bee Pioneer wearable recorder", matched(raw), 8)
                n == "omi" || "openglass" in n ->
                    Hint("wearable", "an Omi pendant or OpenGlass camera glasses", matched(raw), 8)
                "friend pendant" in n ->
                    Hint("wearable", "a Friend Pendant necklace", matched(raw), 8)
                "brilliant frame" in n ->
                    Hint("glasses", "Brilliant Labs Frame AR glasses", matched(raw), 8)
                n == "even g1" ->
                    Hint("glasses", "Even Realities G1 glasses", matched(raw), 8)
                n == "meta quest" ->
                    Hint("wearable", "a Meta Quest headset", matched(raw), 8)
                "hayden" in n ->
                    Hint("camera", "a Hayden AI bus- or vehicle-mounted camera", matched(raw), 8)
                "miovision" in n ->
                    Hint("camera", "a Miovision intersection traffic camera", matched(raw), 8)
                n == "tattile" ->
                    Hint("camera", "a Tattile plate reader", matched(raw), 8)
                "lvt" in n || "liveview" in n ->
                    Hint("camera", "an LVT / LiveView solar surveillance trailer", matched(raw), 8)
                "hanwha" in n || "wisenet" in n ->
                    Hint("camera", "a Hanwha Vision / Wisenet camera", matched(raw), 7)
                n == "uniview" ->
                    Hint("camera", "a Uniview / UNV camera", matched(raw), 7)
                n == "rhombus" ->
                    Hint("camera", "a Rhombus cloud camera", matched(raw), 7)
                n == "meshcore" ->
                    Hint("mesh", "a MeshCore LoRa companion radio", matched(raw), 7)
                n == "bitchat" ->
                    Hint("mesh", "a Bitchat BLE mesh chat", matched(raw), 7)
                "gotenna" in n ->
                    Hint("mesh", "a goTenna Mesh or Pro radio", matched(raw), 7)
                n == "sensecap" ->
                    Hint("mesh", "a SenseCAP LoRaWAN / Helium gateway", matched(raw), 7)
                "wisgate" in n || n == "rak wisgate" ->
                    Hint("mesh", "a RAK WisGate LoRaWAN gateway", matched(raw), 7)
                n == "ghostesp" ->
                    Hint("pentest", "a GhostESP ESP32 audit board", matched(raw), 7)
                n == "bruce" ->
                    Hint("pentest", "a Bruce ESP32 pentest board", matched(raw), 7)
                n == "liteon camera radio" ->
                    Hint(
                        "module",
                        "a camera-module radio (LiteOn or similar)",
                        matched(raw),
                        3,
                    )
                "chipolo" in n || "pebblebee" in n || "moto tag" in n ->
                    Hint("tag", "a finder tag", matched(raw), 7)
                "airpods" in n ->
                    Hint("audio-personal", "AirPods", matched(raw), 8)
                else -> Hint("named", raw, matched(raw), 7)
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
                    Hint("wifi-direct", tx("explain.wifi.direct.ap"), tx("explain.wifi.direct.why"), 4)
                } else {
                    Hint("wifi-direct", tx("explain.wifi.direct.phone"), tx("explain.wifi.direct.why"), 6)
                }
            name.startsWith("ANDROID-", true) || name.contains("hotspot", true) ->
                if (!specific) {
                    out += Hint("hotspot", tx("explain.hotspot"), tx("explain.hotspot.why"), 6)
                }
            "MESH" in caps ->
                out += Hint("mesh", tx("explain.mesh.node"), tx("explain.mesh.why"), 5)
            device.hiddenSsid ->
                out += Hint("ap", tx("explain.hidden.ap"), tx("explain.hidden.why"), 4, generic = true)
            else ->
                if (!specific) {
                    out += Hint("ap", tx("explain.ap"), tx("explain.ap.why"), 3, generic = true)
                }
        }
        // Weight 4 stays under a named signature and still prints in the because line.
        device.facts.interworking?.vehicleSentence()?.let { sentence ->
            out += Hint("venue", tx("explain.ap"), sentence, 4, generic = true)
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

    private fun tx(key: String) = LanguageText.text(key)

    private fun tx(key: String, values: Map<String, String>) = LanguageText.text(key, values)

    private fun matched(raw: String) = tx("explain.matched", mapOf("name" to raw))
}
