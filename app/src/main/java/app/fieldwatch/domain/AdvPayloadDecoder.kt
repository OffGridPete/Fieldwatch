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
            0xFD6F -> ExposureNotification.decodedFields(bytes)
            else -> emptyList()
        }
    }

    fun roleHints(device: Sighting): List<RoleHint> {
        val out = ArrayList<RoleHint>(4)
        val mfg = device.facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let { listOf(MfgRecord(it, device.manufacturerDataHex)) } ?: emptyList()
        }
        val matter = device.facts.matter ?: Matter.read(device.facts.serviceData)
        if (matter != null && matter.commissionable) {
            // Under a named Fast Pair model (8) and under the signature name (7).
            out += RoleHint("home", tx("matter.mode"), tx("explain.matter.setup"), 4)
        }
        val sidewalk = device.facts.sidewalk ?: Sidewalk.read(mfg, device.serviceUuids)
        if (sidewalk?.mode == SidewalkMode.REGISTRATION) {
            // Under the signature name (7). The sentence stays in the because line.
            out += RoleHint("home", tx("sw.chip"), tx("explain.sw.reg"), 4)
        }
        val bthome = device.facts.bthome ?: Bthome.read(device.facts.serviceData)
        when (bthome?.chip()?.text) {
            "Open" -> out += RoleHint("home", "BTHome", tx("explain.bth.door"), 4)
            "Motion" -> out += RoleHint("home", "BTHome", tx("explain.bth.motion"), 4)
        }
        val hk = device.facts.homekit ?: homekitFacts(mfg)
        if (hk != null && !hk.paired) {
            val name = hk.categoryName
            // Under Nearby (weight 5) so a HomeKit TLV inside a phone ad does not rename the phone.
            out += RoleHint("home", name, tx("explain.hk.unpaired", mapOf("category" to name)), 4)
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
                            out += RoleHint("vehicle", tx("explain.tesla"), tx("explain.tesla.why"), 8)
                        } else if (hex.startsWith(targetPrefix) || hex.startsWith(targetPrefix.drop(4))) {
                            out += RoleHint("beacon", tx("explain.atrius"), tx("explain.atrius.why"), 8)
                        } else {
                            out += RoleHint("beacon", tx("explain.ibeacon"), tx("explain.ibeacon.why"), 7)
                        }
                    }
                    0x05 -> out += RoleHint("phone", tx("explain.airdrop"), tx("explain.airdrop.why"), 5)
                    0x07 -> {
                        val model = airPodsModel(tlv.data)
                        out += RoleHint(
                            "audio-personal",
                            model ?: tx("explain.airpods"),
                            if (model != null) tx("explain.airpods.model", mapOf("model" to model))
                            else tx("explain.airpods.why"),
                            8,
                        )
                    }
                    0x08 -> out += RoleHint("siri", tx("explain.siri"), tx("explain.siri.why"), 6)
                    0x09 -> out += RoleHint("audio-speaker", tx("explain.airplay"), tx("explain.airplay.why"), 5)
                    0x0B -> out += RoleHint("phone", tx("explain.handoff"), tx("explain.handoff.why"), 4)
                    0x0C -> out += RoleHint("phone", tx("explain.hotspot.look"), tx("explain.hotspot.look.why"), 5)
                    0x0D, 0x0E -> out += RoleHint("hotspot", tx("explain.hotspot.offer"), tx("explain.hotspot.offer.why"), 6)
                    0x0F -> out += RoleHint("phone", tx("explain.nearby.action"), nearbyActionReason(tlv.data), 4)
                    0x10 -> out += RoleHint("phone", tx("explain.nearby.info"), nearbyInfoReason(tlv.data), 5)
                    0x12 -> out += RoleHint("tag", tx("explain.findmy"), tx("explain.findmy.long"), 4)
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
                        val idHex = "0x%06X".format(id)
                        out += RoleHint(
                            "audio-personal",
                            name ?: tx("explain.fastpair"),
                            if (name != null) tx("explain.fastpair.named", mapOf("name" to name, "id" to idHex))
                            else tx("explain.fastpair.id", mapOf("id" to idHex)),
                            if (name != null) 8 else 6,
                        )
                    } else {
                        out += RoleHint(
                            "audio-personal",
                            tx("explain.fastpair.paired"),
                            tx("explain.fastpair.paired.why"),
                            4,
                        )
                    }
                }
                0xFEAA -> {
                    val frame = hexToBytes(sd.dataHex)?.firstOrNull()?.toInt()?.and(0xFF)
                    when (frame) {
                        0x40, 0x41 -> out += RoleHint(
                            "tag",
                            tx("explain.findhub"),
                            if (frame == 0x41) tx("explain.findhub.sep") else tx("explain.findhub.near"),
                            8,
                        )
                        else -> out += RoleHint("beacon", tx("explain.eddystone"), tx("explain.eddystone.why"), 6)
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
                0x06 -> if (Homekit.readValue(tlv.data) != null) {
                    emptyList()
                } else {
                    listOf(Field("HomeKit", "${tlv.data.size} bytes of HomeKit setup data"))
                }
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

    /**
     * Later successful type 0x06 in these records replaces an earlier one.
     * A value shorter than 9 bytes does not clear a previous good decode.
     */
    fun homekitFacts(records: List<MfgRecord>): HomekitFacts? {
        var found: HomekitFacts? = null
        for (rec in records) {
            if (rec.companyId != 0x004C) continue
            val bytes = hexToBytes(rec.dataHex) ?: continue
            for (tlv in appleTlvs(bytes)) {
                if (tlv.type != 0x06) continue
                Homekit.readValue(tlv.data)?.let { found = it }
            }
        }
        return found
    }

    private fun appleTlvs(bytes: ByteArray): List<Tlv> {
        val out = ArrayList<Tlv>(3)
        var i = 0
        while (i + 2 <= bytes.size) {
            val type = bytes[i].toInt() and 0xFF
            val len = bytes[i + 1].toInt() and 0xFF
            if (type == 0x06) {
                val value = homekitValue(bytes, i, len) ?: break
                out += Tlv(type, value)
                i += 2 + value.size
                continue
            }
            if (len <= 0 || i + 2 + len > bytes.size) break
            out += Tlv(type, bytes.copyOfRange(i + 2, i + 2 + len))
            i += 2 + len
        }
        return out
    }

    /**
     * HAP-ADK writes the second byte as 0x2D or 0x31, which is longer than the
     * element. A length that fits (0x0D, 0x11) is that many value bytes.
     * A length that does not fit takes the rest of this element.
     */
    private fun homekitValue(bytes: ByteArray, i: Int, len: Int): ByteArray? {
        val rest = bytes.size - (i + 2)
        if (rest <= 0) return null
        val take = if (len in 1..rest) len else rest
        return bytes.copyOfRange(i + 2, i + 2 + take)
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
        val action = if (data.size >= 2) data[1].toInt() and 0xFF else return tx("explain.near.ad")
        return tx("explain.near.reason", mapOf("name" to nearbyActionName(action)))
    }

    private fun nearbyActionName(action: Int): String = when (action) {
        0x01 -> tx("explain.near.tv.setup")
        0x04 -> tx("explain.near.backup")
        0x05 -> tx("explain.near.watch")
        0x06 -> tx("explain.near.tv.pair")
        0x08 -> tx("explain.near.wifi")
        0x09 -> tx("explain.near.ios")
        0x0A -> tx("explain.near.repair")
        0x0B -> tx("explain.near.speaker")
        0x0C -> tx("explain.near.pay")
        0x0D -> tx("explain.near.audio")
        0x0F -> tx("explain.near.answered")
        0x10 -> tx("explain.near.ended")
        0x13 -> tx("explain.near.autofill")
        0x14 -> tx("explain.near.companion")
        0x17 -> tx("explain.near.display")
        else -> tx("explain.near.code", mapOf("code" to "0x%02X".format(action)))
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
        if (data.isEmpty()) return tx("explain.info.ad")
        val action = data[0].toInt() and 0x0F
        return when (action) {
            0x03 -> tx("explain.info.idle")
            0x05 -> tx("explain.info.audio")
            0x07 -> tx("explain.info.screen")
            0x0D -> tx("explain.info.driving")
            0x0E -> tx("explain.info.call")
            else -> tx("explain.info.ad")
        }
    }

    private fun decodeFindMy(data: ByteArray): List<Field> {
        if (data.isEmpty()) return listOf(Field("Find My", "Offline Finding advertisement."))
        val status = data[0].toInt() and 0xFF
        val maintained = status and 0x04 != 0
        val battery = when ((status shr 6) and 0x3) {
            0 -> "full"
            1 -> "medium"
            2 -> "low"
            else -> "critical"
        }
        val text = when (data.size) {
            0x19 -> buildString {
                append("Broadcasting a public key so the Find My network can report a location. ")
                append("Used by AirTags, Find My accessories, and Apple devices locating themselves. ")
                if (maintained) {
                    append("Owner seen recently. ")
                    append("Battery $battery. ")
                } else {
                    append("Owner not seen in the current key window. ")
                }
                append("(${data.size - 1}-byte key fragment. Not a serial number.)")
            }
            0x02 ->
                "Short advertisement. Often a tag near its owner. This is not certain. " +
                    "Used by AirTags, Find My accessories, and Apple devices locating themselves."
            else -> buildString {
                append("Find My advertisement, ${data.size} bytes. ")
                append("Used by AirTags, Find My accessories, and Apple devices locating themselves. ")
                if (maintained) {
                    append("Owner seen recently. Battery $battery.")
                } else {
                    append("Owner not seen in the current key window.")
                }
            }
        }
        return listOf(Field("Find My / Offline Finding", text))
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
                val mode = if (bytes[0].toInt() and 0xFF == 0x41) {
                    "separated (unwanted-tracking mode)"
                } else {
                    "normal mode, not flagged separated"
                }
                val eidLen = when (bytes.size) {
                    21, 22 -> 20
                    33, 34 -> 32
                    else -> 0
                }
                if (eidLen == 0) {
                    listOf(Field("Find Hub", mode))
                } else {
                    val flags = if (bytes.size == 22 || bytes.size == 34) {
                        "present. The byte is encrypted."
                    } else {
                        "not present"
                    }
                    listOf(
                        Field("Find Hub", mode),
                        Field("Find Hub EID", bytes.copyOfRange(1, 1 + eidLen).toHexUpper()),
                        Field("Hashed flags", flags),
                    )
                }
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
            val sharing = if (bytes.size >= 3) {
                when (bytes[2].toInt() and 0x1F) {
                    0 -> " Nearby sharing is set to My devices only."
                    1 -> " Nearby sharing is set to Everyone nearby."
                    else -> ""
                }
            } else {
                ""
            }
            val named = microsoftComputerName(bytes)?.let { " The name on the beacon is $it." }.orEmpty()
            return listOf(
                Field(
                    "Microsoft Nearby Sharing",
                    "A $kind is advertising.$sharing$named",
                ),
            )
        }
        return listOf(Field("Microsoft manufacturer data", "${bytes.size} bytes"))
    }

    /**
     * Everyone nearby replaces the tail of the beacon with the computer name.
     * The name starts at byte 10. Bytes 8 and 9 stay off this line.
     * A name is shown only when every byte is printable and the whole name fits
     * in the 15 characters Windows uses.
     */
    private fun microsoftComputerName(bytes: ByteArray): String? {
        if (bytes.size !in 11..25 || bytes[0] != 0x01.toByte()) return null
        if ((bytes[2].toInt() and 0x1F) != 1) return null
        val raw = bytes.copyOfRange(10, bytes.size)
        if (raw.any { (it.toInt() and 0xFF) !in 0x20..0x7E }) return null
        val name = String(raw, Charsets.US_ASCII).trim()
        return name.ifBlank { null }
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
            // 32-bit form of a 16-bit UUID, as stored from AD type 0x20.
            hex.length == 8 && hex.startsWith("0000") -> hex.substring(4, 8).toIntOrNull(16)
            hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB") ->
                hex.substring(4, 8).toIntOrNull(16)
            else -> null
        }
    }

    private fun tx(key: String) = LanguageText.text(key)

    private fun tx(key: String, values: Map<String, String>) = LanguageText.text(key, values)

    private fun hexToBytes(hex: String): ByteArray? {
        val h = hex.filter { it.isLetterOrDigit() }
        if (h.isEmpty() || h.length % 2 != 0) return null
        return ByteArray(h.length / 2) { i ->
            h.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
