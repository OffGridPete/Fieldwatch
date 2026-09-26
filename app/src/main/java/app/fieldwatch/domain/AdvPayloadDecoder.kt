package app.fieldwatch.domain

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
            0x00E0 -> listOf(Field("Google manufacturer data", "${bytes.size} bytes"))
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
                                "a Tesla vehicle or phone-as-key",
                                "Tesla phone-key iBeacon UUID (iOS background find).",
                                8,
                            )
                        } else if (hex.startsWith(targetPrefix) || hex.startsWith(targetPrefix.drop(4))) {
                            out += RoleHint(
                                "beacon",
                                "a Target Atrius basket tag",
                                "Target / Atrius iBeacon UUID (shopping-basket asset tag).",
                                8,
                            )
                        } else {
                            out += RoleHint("beacon", "an iBeacon", "Apple iBeacon payload.", 7)
                        }
                    }
                    0x05 -> out += RoleHint("phone", "an iPhone or iPad offering AirDrop", "Apple AirDrop advertisement.", 5)
                    0x07 -> {
                        val model = airPodsModel(tlv.data)
                        out += RoleHint(
                            "audio-personal",
                            model ?: "AirPods or Beats headphones",
                            if (model != null) "Apple Proximity Pairing: $model."
                            else "Apple Proximity Pairing (AirPods / Beats).",
                            8,
                        )
                    }
                    0x08 -> out += RoleHint("siri", "an Apple device that just heard “Hey Siri”", "Hey Siri advertisement.", 6)
                    0x09 -> out += RoleHint("audio-speaker", "an AirPlay speaker or Apple TV", "AirPlay advertisement.", 5)
                    0x0B -> out += RoleHint("phone", "an Apple device doing Handoff", "Handoff advertisement.", 4)
                    0x0C -> out += RoleHint("phone", "an Apple device looking for Instant Hotspot", "Tethering-target advertisement.", 5)
                    0x0D, 0x0E -> out += RoleHint("hotspot", "an iPhone/iPad offering Instant Hotspot", "Tethering-source advertisement.", 6)
                    0x0F -> out += RoleHint("phone", "an Apple device (Nearby Action)", nearbyActionReason(tlv.data), 4)
                    0x10 -> out += RoleHint("phone", "an iPhone / iPad / Mac (Nearby Info)", nearbyInfoReason(tlv.data), 5)
                    0x12 -> out += RoleHint(
                        "tag",
                        "a Find My network radio",
                        "Apple Offline Finding — AirTag, Find My accessory, or an Apple device locating itself.",
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
                            name ?: "a Fast Pair accessory (often earbuds or a speaker)",
                            if (name != null) "Google Fast Pair model $name (0x%06X), in pairing mode.".format(id)
                            else "Google Fast Pair model 0x%06X, in pairing mode.".format(id),
                            if (name != null) 8 else 6,
                        )
                    } else {
                        out += RoleHint(
                            "audio-personal",
                            "a Fast Pair accessory already paired to someone",
                            "Google Fast Pair account-key broadcast (not in pairing mode).",
                            4,
                        )
                    }
                }
                0xFEAA -> {
                    val frame = hexToBytes(sd.dataHex)?.firstOrNull()?.toInt()?.and(0xFF)
                    when (frame) {
                        0x40, 0x41 -> out += RoleHint(
                            "tag",
                            "a Google Find Hub tag",
                            if (frame == 0x41) "Find Hub separated (unwanted-tracking) frame."
                            else "Find Hub nearby frame.",
                            8,
                        )
                        else -> out += RoleHint("beacon", "an Eddystone beacon", "Eddystone service data.", 6)
                    }
                }
            }
        }
        return out
    }

    private data class Tlv(val type: Int, val data: ByteArray)

    private fun decodeApple(bytes: ByteArray): List<Field> {
        val tlvs = appleTlvs(bytes)
        if (tlvs.isEmpty()) return listOf(Field("Apple payload", "${bytes.size} bytes (unparsed)"))
        val out = ArrayList<Field>(8)
        for (tlv in tlvs) {
            out += Field("Apple Continuity type", "0x%02X · %s".format(tlv.type, appleTypeName(tlv.type)))
            out += when (tlv.type) {
                0x02 -> decodeIBeacon(tlv.data)
                0x05 -> decodeAirDrop(tlv.data)
                0x06 -> listOf(Field("HomeKit", "${tlv.data.size} bytes of HomeKit setup data"))
                0x07 -> decodeAirPods(tlv.data)
                0x08 -> decodeHeySiri(tlv.data)
                0x09 -> listOf(Field("AirPlay", "This device is advertising as an AirPlay source or target."))
                0x0A -> listOf(Field("Magic Switch", "Apple Watch wrist / unlock related."))
                0x0B -> decodeHandoff(tlv.data)
                0x0C -> decodeHandoffOrTetherTarget(tlv.data)
                0x0D, 0x0E -> decodeTetherSource(tlv.data)
                0x0F -> decodeNearbyAction(tlv.data)
                0x10 -> decodeNearbyInfo(tlv.data)
                0x12 -> decodeFindMy(tlv.data)
                else -> listOf(Field("Payload", "${tlv.data.size} bytes"))
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
        0x07 -> "Proximity Pairing (AirPods / Beats)"
        0x08 -> "Hey Siri"
        0x09 -> "AirPlay"
        0x0A -> "Magic Switch (Watch)"
        0x0B -> "Handoff"
        0x0C -> "Handoff or Instant Hotspot (target)"
        0x0D -> "Instant Hotspot (source)"
        0x0E -> "Instant Hotspot (source)"
        0x0F -> "Nearby Action"
        0x10 -> "Nearby Info"
        0x12 -> "Find My / Offline Finding"
        0x13 -> "Nearby Action (extended)"
        0x16 -> "Nearby Info"
        else -> "unlisted"
    }

    private fun decodeIBeacon(data: ByteArray): List<Field> {
        // TLV payload is length-byte already consumed; data is 0x15 + 21 bytes OR 21 bytes.
        val body = when {
            data.size >= 22 && data[0] == 0x15.toByte() -> data.copyOfRange(1, 22)
            data.size >= 21 -> data.copyOfRange(0, 21)
            else -> return listOf(Field("iBeacon", "truncated (${data.size} bytes)"))
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
                if (teslaKey) "$uuid — Tesla phone-as-key (iOS background find). Not a mall beacon." else uuid,
            ),
            Field("iBeacon major / minor", "$major / $minor"),
            Field("iBeacon calibrated TX", "$tx dBm at 1 m (used to estimate range)"),
        )
    }

    private fun decodeAirDrop(data: ByteArray): List<Field> {
        // 8 zeros, version, appleID hash(2), phone(2), email(2), email2(2), 0
        if (data.size < 18) return listOf(Field("AirDrop", "Someone nearby is offering AirDrop (${data.size} bytes)."))
        return listOf(
            Field("AirDrop", "Someone nearby has AirDrop receiving on. Hashes are truncated IDs, not names."),
            Field("Apple ID hash (2 bytes)", data.copyOfRange(9, 11).toHexUpper()),
        )
    }

    private fun decodeAirPods(data: ByteArray): List<Field> {
        // prefix 0x01, model u16be, status, batt nibble, charge+case, lid, color, 0x00, enc 16
        if (data.size < 5) return listOf(Field("AirPods", "Proximity Pairing, truncated."))
        val start = if (data[0] == 0x01.toByte()) 1 else 0
        if (data.size < start + 4) return listOf(Field("AirPods", "Proximity Pairing."))
        val model = ((data[start].toInt() and 0xFF) shl 8) or (data[start + 1].toInt() and 0xFF)
        val status = data[start + 2].toInt() and 0xFF
        val batt = data[start + 3].toInt() and 0xFF
        val left = batt and 0x0F
        val right = (batt shr 4) and 0x0F
        val out = ArrayList<Field>(6)
        out += Field("Product", airPodsModelName(model) ?: "Apple audio 0x%04X".format(model))
        out += Field("Pod position", airPodsStatus(status))
        out += Field("Battery (left / right)", "${nibblePct(left)} / ${nibblePct(right)}")
        if (data.size > start + 4) {
            val ch = data[start + 4].toInt() and 0xFF
            val caseBatt = ch and 0x0F
            val charging = buildList {
                if (ch and 0x10 != 0) add("case")
                if (ch and 0x20 != 0) add("right")
                if (ch and 0x40 != 0) add("left")
            }
            out += Field("Case battery", nibblePct(caseBatt))
            if (charging.isNotEmpty()) out += Field("Charging", charging.joinToString(", "))
        }
        if (data.size > start + 6) {
            out += Field("Color", airPodsColor(data[start + 6].toInt() and 0xFF))
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
        0x0220 -> "AirPods (1st generation)"
        0x0F20 -> "AirPods (2nd generation)"
        0x1320 -> "AirPods (3rd generation)"
        0x1920 -> "AirPods (4th generation)"
        0x1C20 -> "AirPods 4"
        0x0E20 -> "AirPods Pro"
        0x1420 -> "AirPods Pro (2nd generation)"
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
        0x01 -> "One or both out of the case"
        0x02 -> "Case open"
        0x03 -> "Taken out / in-ear transition"
        0x05 -> "One in ear"
        0x09 -> "Both out, not in ear"
        0x0B -> "In-ear activity"
        0x11, 0x13 -> "Both in ear"
        0x21 -> "One in ear (sharing?)"
        0x51 -> "Both in case, lid open"
        0x55 -> "Both in case, lid closed"
        0x75 -> "In case"
        else -> "Status 0x%02X".format(status)
    }

    private fun airPodsColor(v: Int): String = when (v) {
        0x00 -> "White"
        0x01 -> "Black"
        0x02 -> "Red"
        0x03 -> "Blue"
        0x04 -> "Pink"
        0x05 -> "Gray"
        0x06 -> "Silver"
        0x07 -> "Gold"
        0x08 -> "Rose gold"
        0x09 -> "Space gray"
        0x0A -> "Dark blue"
        0x0B -> "Light blue"
        0x0C -> "Yellow"
        else -> "0x%02X".format(v)
    }

    private fun nibblePct(n: Int): String = when (n) {
        in 0..9 -> "${n * 10}%"
        10, 11, 12, 13, 14 -> "100%"
        15 -> "unknown / not present"
        else -> "$n"
    }

    private fun decodeHeySiri(data: ByteArray): List<Field> {
        if (data.size < 6) return listOf(Field("Hey Siri", "Siri was just triggered on a nearby Apple device."))
        val klass = u16be(data, 4)
        val device = when (klass) {
            0x0002 -> "iPhone"
            0x0003 -> "iPad"
            0x0007 -> "HomePod"
            0x0009 -> "Mac"
            0x000A -> "Watch"
            else -> "class 0x%04X".format(klass)
        }
        return listOf(
            Field("Hey Siri", "A $device just heard a Siri trigger. The packet carries a short voice hash, not the words."),
        )
    }

    private fun decodeHandoff(data: ByteArray): List<Field> =
        listOf(Field("Handoff", "Continuity Handoff: a task can be continued on another Apple device. Payload is encrypted."))

    private fun decodeHandoffOrTetherTarget(data: ByteArray): List<Field> =
        if (data.size >= 14) decodeHandoff(data)
        else listOf(Field("Instant Hotspot (looking)", "This Apple device is searching for a paired phone’s hotspot."))

    private fun decodeTetherSource(data: ByteArray): List<Field> {
        if (data.size < 6) return listOf(Field("Instant Hotspot", "An iPhone/iPad is offering a personal hotspot."))
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
            else -> if (cell >= 0) "type $cell" else null
        }
        return listOf(
            Field(
                "Instant Hotspot (offering)",
                buildString {
                    append("Paired iPhone/iPad hotspot")
                    if (batt in 0..100) append(" · phone battery $batt%")
                    cellName?.let { append(" · $it") }
                    if (bars in 0..5) append(" · $bars/5 bars")
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
        val action = if (data.size >= 2) data[1].toInt() and 0xFF else return "Nearby Action advertisement."
        return "Nearby Action: ${nearbyActionName(action)}."
    }

    private fun nearbyActionName(action: Int): String = when (action) {
        0x01 -> "Apple TV setup"
        0x04 -> "Mobile backup"
        0x05 -> "Watch setup"
        0x06 -> "Apple TV pair"
        0x08 -> "Wi-Fi password sharing (prompting nearby iPhones)"
        0x09 -> "iOS setup"
        0x0A -> "Repair"
        0x0B -> "Speaker setup"
        0x0C -> "Apple Pay"
        0x0D -> "Whole-home audio setup"
        0x0F -> "Answered a call"
        0x10 -> "Ended a call"
        0x13 -> "Remote AutoFill"
        0x14 -> "Companion Link proximity"
        0x17 -> "Remote display"
        else -> "action 0x%02X".format(action)
    }

    private fun decodeNearbyInfo(data: ByteArray): List<Field> {
        if (data.isEmpty()) return listOf(Field("Nearby Info", "Apple device usage state."))
        val status = data[0].toInt() and 0xFF
        val action = status and 0x0F
        val flagsHi = (status shr 4) and 0x0F
        val dataFlags = if (data.size > 1) data[1].toInt() and 0xFF else 0
        val activity = when (action) {
            0x00 -> "activity unknown"
            0x01 -> "activity reporting off"
            0x03 -> "idle (screen locked)"
            0x05 -> "audio playing, screen locked"
            0x07 -> "active (screen on)"
            0x09 -> "screen on, video playing"
            0x0A -> "Watch on wrist and unlocked"
            0x0B -> "recent interaction"
            0x0D -> "user is driving"
            0x0E -> "phone or FaceTime call"
            else -> "activity 0x%X".format(action)
        }
        val extras = buildList {
            if (flagsHi and 0x1 != 0) add("primary iCloud device")
            if (flagsHi and 0x4 != 0) add("AirDrop receiving on")
            if (dataFlags and 0x04 != 0) add("Wi-Fi on")
            if (dataFlags and 0x01 != 0) add("AirPods connected")
            if (dataFlags and 0x20 != 0) add("Watch locked")
        }
        return listOf(
            Field(
                "What the Apple device is doing",
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
        if (data.isEmpty()) return "Nearby Info advertisement."
        val action = data[0].toInt() and 0x0F
        return when (action) {
            0x03 -> "Phone is idle / locked."
            0x05 -> "Audio playing with the screen locked."
            0x07 -> "Screen is on — someone is using it."
            0x0D -> "Device reports the user is driving."
            0x0E -> "In a phone or FaceTime call."
            else -> "Nearby Info advertisement."
        }
    }

    private fun decodeFindMy(data: ByteArray): List<Field> {
        if (data.isEmpty()) return listOf(Field("Find My", "Offline Finding advertisement."))
        val status = data[0].toInt() and 0xFF
        val maintained = status and 0x04 != 0
        val batt = (status shr 6) and 0x3
        val battName = when (batt) {
            0 -> "full"
            1 -> "medium"
            2 -> "low"
            else -> "critical"
        }
        val keyLen = (data.size - 1).coerceAtLeast(0)
        return listOf(
            Field(
                "Find My / Offline Finding",
                buildString {
                    append("Broadcasting a public key so the Find My network can report a location. ")
                    append("Used by AirTags, Find My accessories, and Apple devices locating themselves. ")
                    if (maintained) append("Owner seen recently. ")
                    else append("Owner not seen in the current key window. ")
                    if (maintained || batt in 0..3) append("Battery $battName. ")
                    append("($keyLen-byte key fragment — not a serial number.)")
                },
            ),
        )
    }

    private fun decodeFastPair(bytes: ByteArray): List<Field> {
        if (bytes.size == 3) {
            val id = modelId24(bytes)
            val name = FastPairModels.name(id)
            return listOf(
                Field("Google Fast Pair", "In pairing mode — Android will pop a tap-to-pair card."),
                Field(
                    "Model ID",
                    if (name != null) "$name  (0x%06X)".format(id) else "0x%06X (not in the local name list)".format(id),
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
                0x0 -> "wants to show a pairing card"
                0x2 -> "hiding the pairing card (e.g. buds back in the case)"
                else -> "filter type $type"
            }
        } else "account-key bloom filter"
        return listOf(
            Field(
                "Google Fast Pair",
                "Already paired to an account (not in pairing mode). $ui. Version $version.",
            ),
        )
    }

    private fun decodeEddystone(bytes: ByteArray): List<Field> {
        if (bytes.isEmpty()) return emptyList()
        return when (bytes[0].toInt() and 0xFF) {
            0x00 -> {
                if (bytes.size < 18) listOf(Field("Eddystone-UID", "truncated"))
                else listOf(
                    Field("Eddystone-UID namespace", bytes.copyOfRange(2, 12).toHexUpper()),
                    Field("Eddystone-UID instance", bytes.copyOfRange(12, 18).toHexUpper()),
                )
            }
            0x10 -> listOf(Field("Eddystone-URL", eddystoneUrl(bytes) ?: "${bytes.size} bytes"))
            0x20 -> listOf(Field("Eddystone-TLM", "telemetry (battery / temperature / advert count)"))
            0x30 -> listOf(Field("Eddystone-EID", "ephemeral ID (rotating)"))
            0x40, 0x41 -> {
                val mode = if (bytes[0].toInt() and 0xFF == 0x41) "separated (unwanted-tracking mode)" else "nearby / with owner"
                val eidLen = when {
                    bytes.size >= 33 -> 32
                    bytes.size >= 21 -> 20
                    else -> (bytes.size - 1).coerceAtLeast(0)
                }
                val eid = if (eidLen > 0) bytes.copyOfRange(1, 1 + eidLen).toHexUpper() else ""
                listOf(
                    Field("Find Hub", mode),
                    Field("Find Hub EID", eid.ifBlank { "${bytes.size} bytes" }),
                )
            }
            else -> listOf(Field("Eddystone", "frame 0x%02X".format(bytes[0])))
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
                9 -> "Windows desktop"
                11 -> "Windows phone"
                12 -> "Linux"
                13 -> "Windows IoT"
                14 -> "Surface Hub"
                15 -> "Windows laptop"
                16 -> "Windows tablet"
                else -> "type $type"
            }
            return listOf(Field("Microsoft Nearby Sharing / Swift Pair", "A $kind is advertising for quick pairing or sharing."))
        }
        return listOf(Field("Microsoft manufacturer data", "${bytes.size} bytes"))
    }

    private fun decodeAltBeacon(bytes: ByteArray): List<Field> {
        if (bytes.size >= 22 && bytes[0] == 0xBE.toByte() && bytes[1] == 0xAC.toByte()) {
            return listOf(
                Field("AltBeacon UUID", uuidFromBe(bytes, 2)),
                Field("AltBeacon major / minor", "${u16be(bytes, 18)} / ${u16be(bytes, 20)}"),
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
