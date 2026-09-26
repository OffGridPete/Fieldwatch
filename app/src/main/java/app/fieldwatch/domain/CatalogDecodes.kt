package app.fieldwatch.domain

/**
 * Stock [Fleet.decode] maps for catalog BLE signatures whose advertisement
 * layout is published (vendor docs or ASTM/OpenDroneID). Identity match stays
 * on [Fleet.rules]; this is parse-only on detail and reports.
 *
 * Encrypted or connect/GATT-only payloads (modern Fitbit GATT, Find My tags)
 * are intentionally omitted. Older Fitbit ads still do not carry steps.
 */
internal object CatalogDecodes {
    private val GOPRO_MODELS = mapOf(
        "55" to "HERO9 Black",
        "57" to "HERO10 Black",
        "58" to "HERO11 Black",
        "60" to "HERO11 Black Mini",
        "62" to "HERO12 Black",
        "64" to "MAX 2",
        "65" to "HERO13 Black",
        "66" to "HERO (2024)",
        "69" to "MISSION 1 Pro",
        "70" to "LIT HERO",
        "71" to "MISSION 1",
    )

    private val DJI_MODELS = mapOf(
        "6" to "Osmo Action 1",
        "16" to "Osmo Action 2",
        "18" to "Osmo Action 3",
        "20" to "Osmo Action 4",
        "21" to "Osmo Action 5 Pro",
        "23" to "Osmo 360",
        "24" to "Osmo Action 6",
        "25" to "Osmo Nano",
        "32" to "Osmo Pocket 3",
        "33" to "Osmo Pocket 4",
        "34" to "Osmo Pocket 4 Pro",
        "112" to "Mavic 3",
        "126" to "Neo 2",
    )

    /**
     * OpenWeave NestWeaveProductId (16-bit). Retail names where the
     * codename is published; otherwise the Nest internal name.
     */
    private val NEST_WEAVE_PRODUCTS = mapOf(
        "1" to "Nest Learning Thermostat (1st/2nd)",
        "2" to "Nest Learning Thermostat (1st/2nd) backplate",
        "3" to "Nest Learning Thermostat (3rd)",
        "4" to "Nest Learning Thermostat (3rd) backplate",
        "5" to "Nest Protect (1st gen)",
        "6" to "Nest Heat Link backplate",
        "7" to "Nest Heat Link",
        "8" to "Nest Pinna",
        "9" to "Nest Protect (2nd gen)",
        "10" to "Nest Learning Thermostat (3rd, EU)",
        "11" to "Nest Learning Thermostat (3rd, EU) backplate",
        "12" to "Nest Flintstone",
        "13" to "Nest Cam IQ indoor",
        "14" to "Nest Hello",
        "15" to "Nest Heat Link (2nd)",
        "16" to "Nest Cam IQ outdoor",
        "17" to "Nest Quartz2",
        "18" to "Nest Black Quartz",
        "19" to "Nest Detect",
        "20" to "Nest Guard",
        "21" to "Nest Guard backplate",
        "22" to "Nest Antigua",
        "23" to "Nest Rose Quartz",
        "24" to "Nest Moonstone",
        "26" to "Nest Kryptonite",
    )

    private val NEST_WEAVE_VENDORS = mapOf(
        "9050" to "Nest Labs",
        "59175" to "Yale",
    )

    /** Ruuvi Innovations 0x0499 Data Format 5 (RAWv2). Official docs. */
    val ruuvi: FleetDecode = FleetDecode(
        source = DecodeSource.MANUFACTURER_DATA,
        companyId = 0x0499,
        fields = listOf(
            u8("format", "Format", 0),
            i16be("temperature", "Temperature", 1, scale = 0.005, unit = "°C", gate = eq(0, "05")),
            u16be("humidity", "Humidity", 3, scale = 0.0025, unit = "%", gate = eq(0, "05")),
            u16be("pressure", "Pressure", 5, scale = 0.01, offsetAdd = 500.0, unit = "hPa", gate = eq(0, "05")),
            i16be("acc_x", "Accel X", 7, scale = 0.001, unit = "g", gate = eq(0, "05")),
            i16be("acc_y", "Accel Y", 9, scale = 0.001, unit = "g", gate = eq(0, "05")),
            i16be("acc_z", "Accel Z", 11, scale = 0.001, unit = "g", gate = eq(0, "05")),
            bits(
                "battery", "Battery", 13, length = 2, bitOffset = 5, bitWidth = 11,
                endian = DecodeEndian.BE, scale = 1.0, offsetAdd = 1600.0, unit = "mV",
                gate = eq(0, "05"),
            ),
            bits(
                "tx_power", "TX power", 13, length = 2, bitOffset = 0, bitWidth = 5,
                endian = DecodeEndian.BE, scale = 2.0, offsetAdd = -40.0, unit = "dBm",
                gate = eq(0, "05"),
            ),
            u8("movement", "Movement", 15, gate = eq(0, "05")),
            u16be("sequence", "Sequence", 16, gate = eq(0, "05")),
            mac("mac", "MAC in payload", 18, gate = eq(0, "05")),
        ) + ruuviRawV1(),
    )

    /**
     * ASTM F3411 / OpenDroneID on UUID 0xFFFA.
     * Service data: 0x0D app code, counter, then a 25-byte message.
     * Type-specific fields are gated on protocol versions 0–2 (F3411).
     * Older protocol bytes still show app code, counter, and message type.
     */
    val remoteId: FleetDecode = FleetDecode(
        source = DecodeSource.SERVICE_DATA,
        serviceUuid = "FFFA",
        fields = buildList {
            add(u8("app", "App code", 0, enumLabels = mapOf("13" to "Open Drone ID")))
            add(u8("counter", "Counter", 1))
            add(
                bits(
                    "msg_type", "Message", 2, bitOffset = 4, bitWidth = 4,
                    enumLabels = mapOf(
                        "0" to "Basic ID",
                        "1" to "Location",
                        "2" to "Auth",
                        "3" to "Self ID",
                        "4" to "System",
                        "5" to "Operator ID",
                        "15" to "Message pack",
                    ),
                ),
            )
            add(bits("proto", "Protocol", 2, bitOffset = 0, bitWidth = 4))
            addAll(forMessageType(0, basicIdFields()))
            addAll(forMessageType(1, locationFields()))
            addAll(forMessageType(3, selfIdFields()))
            addAll(forMessageType(4, systemFields()))
            addAll(forMessageType(5, operatorIdFields()))
        },
    )

    /** Blue Maestro Tempo Disc / Disc Mini-Maxi. Official advertisement API. */
    val blueMaestro: FleetDecode = FleetDecode(
        source = DecodeSource.MANUFACTURER_DATA,
        companyId = 0x0133,
        fields = listOf(
            u8("version", "Version", 0),
            u8("battery", "Battery", 1, unit = "%"),
            u16be("interval", "Log interval", 2, unit = "s"),
            u16be("logs", "Stored logs", 4),
            i16be("temperature", "Temperature", 6, scale = 0.1, unit = "°C"),
            u16be("humidity", "Humidity", 8, scale = 0.1, unit = "%", gate = eq(0, "17")),
        ),
    )

    /** Open GoPro BLE advertisement, company 0xF202 (not a SIG member ID). */
    val gopro: FleetDecode = FleetDecode(
        source = DecodeSource.MANUFACTURER_DATA,
        companyId = 0xF202,
        fields = listOf(
            u8("schema", "Schema", 0),
            bits("awake", "Processor", 1, bitOffset = 0, bitWidth = 1, enumLabels = onOff("asleep", "awake")),
            bits("wifi_ap", "Wi-Fi AP", 1, bitOffset = 1, bitWidth = 1, enumLabels = onOff("off", "on")),
            bits("pairing", "Pairing", 1, bitOffset = 2, bitWidth = 1, enumLabels = onOff("no", "yes")),
            bits("new_media", "New media", 1, bitOffset = 4, bitWidth = 1, enumLabels = onOff("no", "yes")),
            u8("model", "Model", 2, enumLabels = GOPRO_MODELS),
            bits("offload", "Media offload", 11, bitOffset = 0, bitWidth = 1, enumLabels = onOff("no", "available")),
        ),
    )

    /**
     * Govee hygrometers. Company ID is empty so both 0xEC88 (H5074/H5075)
     * and 0x0001 (H5100/H5102/H5174/…) records are tried. Lights usually
     * have no matching length. H5075 starts 00; H510x packed ads start 01.
     */
    val govee: FleetDecode = FleetDecode(
        source = DecodeSource.MANUFACTURER_DATA,
        companyId = null,
        fields = buildList {
            addAll(goveeH5074())
            addAll(goveeH5075(lenEq(5, eq(0, "00"))))
            addAll(goveeH5075(lenEq(6, eq(0, "00"))))
            addAll(goveeH510x(lenEq(6, eq(0, "01"))))
            addAll(goveeH510x(lenEq(8, eq(0, "01"))))
        },
    )

    /** Kontakt.io Location packet on member UUID 0xFE6A. */
    val kontakt: FleetDecode = FleetDecode(
        source = DecodeSource.SERVICE_DATA,
        serviceUuid = "FE6A",
        fields = listOf(
            u8("kind", "Packet", 0, enumLabels = mapOf("7" to "Location"), gate = eq(0, "07")),
            u8("battery", "Battery", 1, unit = "%", gate = eq(0, "07")),
            i8("tx_power", "TX power", 2, unit = "dBm", gate = eq(0, "07")),
            u8("channel", "Channel", 3, gate = eq(0, "07")),
            u8("model", "Model id", 4, gate = eq(0, "07")),
            bits("moving", "Moving", 5, bitOffset = 0, bitWidth = 1, enumLabels = onOff("still", "moving"), gate = eq(0, "07")),
        ),
    )

    /** Estimote 0x015D frame type (Nearable vs Telemetry). Sensor bytes are packed. */
    val estimote: FleetDecode = FleetDecode(
        source = DecodeSource.MANUFACTURER_DATA,
        companyId = 0x015D,
        fields = listOf(
            u8(
                "frame", "Frame", 0,
                enumLabels = mapOf("1" to "Nearable", "2" to "Telemetry"),
            ),
        ),
    )

    /**
     * Nest Weave FEAF service data is a WeaveBLEDeviceIdentificationInfo block
     * (OpenWeave WeaveBleServiceData.h), not a bare product u16.
     * Byte 0 = block length 0x10, byte 1 = type 0x01 (device identification).
     * Reading those two bytes as LE looks like product 272.
     * Truncated 2-byte ads still carry the product id at offset 0.
     */
    val nestWeave: FleetDecode = FleetDecode(
        source = DecodeSource.SERVICE_DATA,
        serviceUuid = "FEAF",
        fields = buildList {
            val idBlock = lenEq(17, eq(1, "01"))
            add(
                u16le(
                    "product", "Product", 0,
                    enumLabels = NEST_WEAVE_PRODUCTS,
                    gate = lenEq(2),
                ),
            )
            add(u16le("vendor", "Vendor", 4, enumLabels = NEST_WEAVE_VENDORS, gate = idBlock))
            add(u16le("product", "Product", 6, enumLabels = NEST_WEAVE_PRODUCTS, gate = idBlock))
            add(hex("device_id", "Weave device id", 8, length = 8, gate = idBlock))
            add(
                u8(
                    "pairing", "Pairing", 16,
                    enumLabels = onOff("unpaired", "paired"),
                    gate = idBlock,
                ),
            )
        },
    )

    /** Tuya 0x07D0: bound flag and protocol version. UUID bytes are encrypted. */
    val tuya: FleetDecode = FleetDecode(
        source = DecodeSource.MANUFACTURER_DATA,
        companyId = 0x07D0,
        fields = listOf(
            bits("bound", "Bound", 0, bitOffset = 7, bitWidth = 1, enumLabels = onOff("unbound", "bound")),
            u8("protocol", "Protocol", 1),
        ),
    )

    /** Tile FEED rotating private id (not a serial). */
    val tile: FleetDecode = FleetDecode(
        source = DecodeSource.SERVICE_DATA,
        serviceUuid = "FEED",
        fields = listOf(
            hex("private_id", "Private ID", 0, length = 8),
        ),
    )

    /** DJI 0x08AA manufacturer-data model id (u16 LE). Osmo 0x0006–0x0022; some aircraft known. */
    val djiModel: FleetDecode = FleetDecode(
        source = DecodeSource.MANUFACTURER_DATA,
        companyId = 0x08AA,
        fields = listOf(
            u16le("model", "Model", 0, enumLabels = DJI_MODELS),
        ),
    )

    /**
     * Cheap valve-cap BLE TPMS (TPMS1 / FBB0 / TomTom 0x0001).
     * 16 bytes after company ID: sensor, id, pressure kPa, temperature °C, battery, alarm.
     * ra6070/BLE-TPMS and Theengs TPMS. Identity is name / FBB0 / data prefix 80–83,
     * not a bare Nokia 0x0001 match.
     */
    val tpmsAftermarket: FleetDecode = FleetDecode(
        source = DecodeSource.MANUFACTURER_DATA,
        companyId = 0x0001,
        fields = listOf(
            u8(
                "wheel", "Wheel", 0,
                enumLabels = mapOf(
                    "128" to "1",
                    "129" to "2",
                    "130" to "3",
                    "131" to "4",
                ),
                gate = lenEq(16),
            ),
            hex("sensor_id", "Sensor id", 1, length = 5, gate = lenEq(16)),
            u32le("pressure", "Pressure", 6, scale = 0.001, unit = "kPa", gate = lenEq(16)),
            i32le("temperature", "Temperature", 10, scale = 0.01, unit = "°C", gate = lenEq(16)),
            u8("battery", "Battery", 14, unit = "%", gate = lenEq(16)),
            u8(
                "alarm", "Alarm", 15,
                enumLabels = mapOf("0" to "ok", "1" to "no pressure"),
                gate = lenEq(16),
            ),
        ),
    )

    /**
     * SYTPMS / BR bicycle-scooter sensors. 7-byte manufacturer blob
     * SS BB TT PPPP CCCC — company ID is status+battery, so the map
     * prepends those two bytes. andi38/TPMS, Theengs TPMSBR.
     */
    val sytpms: FleetDecode = FleetDecode(
        source = DecodeSource.MANUFACTURER_DATA,
        includeCompanyId = true,
        fields = listOf(
            bits(
                "alarm", "Alarm", 0, bitOffset = 7, bitWidth = 1,
                enumLabels = onOff("ok", "zero pressure"),
                gate = lenEq(7),
            ),
            bits(
                "rotating", "Rotating", 0, bitOffset = 6, bitWidth = 1,
                enumLabels = onOff("no", "yes"),
                gate = lenEq(7),
            ),
            bits(
                "still", "Standing still", 0, bitOffset = 5, bitWidth = 1,
                enumLabels = onOff("no", "yes"),
                gate = lenEq(7),
            ),
            u8("battery", "Battery", 1, scale = 0.1, unit = "V", gate = lenEq(7)),
            u8("temperature", "Temperature", 2, unit = "°C", gate = lenEq(7)),
            u16be("pressure", "Pressure", 3, scale = 0.1, offsetAdd = -14.5, unit = "psi", gate = lenEq(7)),
        ),
    )

    /**
     * Tesla tsTPMS manufacturer 0x022B after the name match.
     * Reverse-engineered (cunzulatu/Tesla_BLE_TPMS): type < 5 is sleep.
     * Pressure (raw−100)/7 psi; temperature is °F minus 1.
     */
    val teslaTstpms: FleetDecode = FleetDecode(
        source = DecodeSource.MANUFACTURER_DATA,
        companyId = 0x022B,
        fields = listOf(
            u8(
                "mode", "Mode", 2,
                enumLabels = mapOf(
                    "0" to "sleep",
                    "1" to "sleep",
                    "2" to "sleep",
                    "3" to "sleep",
                    "4" to "sleep",
                ),
            ),
            u16le(
                "pressure", "Pressure", 3,
                scale = 1.0 / 7.0, offsetAdd = -100.0 / 7.0, unit = "psi",
                gate = teslaAwake(),
            ),
            u8("temperature", "Temperature", 5, offsetAdd = -1.0, unit = "°F", gate = teslaAwake()),
            u16le("battery", "Battery", 6, unit = "mV", gate = teslaAwake()),
        ),
    )

    private fun basicIdFields(): List<DecodeField> = listOf(
        bits(
            "id_type", "ID type", 3, bitOffset = 4, bitWidth = 4,
            enumLabels = mapOf(
                "0" to "None",
                "1" to "Serial (CTA-2063)",
                "2" to "CAA registration",
                "3" to "UTM UUID",
                "4" to "Session ID",
            ),
        ),
        bits(
            "ua_type", "UA type", 3, bitOffset = 0, bitWidth = 4,
            enumLabels = mapOf(
                "0" to "None",
                "1" to "Aeroplane",
                "2" to "Helicopter / multirotor",
                "3" to "Gyroplane",
                "4" to "Hybrid lift",
                "6" to "Glider",
                "10" to "Airship",
                "15" to "Other",
            ),
        ),
        utf8("uas_id", "UAS ID", 4, length = 20),
    )

    private fun locationFields(): List<DecodeField> = listOf(
        bits(
            "status", "Status", 3, bitOffset = 4, bitWidth = 4,
            enumLabels = mapOf(
                "0" to "Undeclared",
                "1" to "Ground",
                "2" to "Airborne",
                "3" to "Emergency",
                "4" to "RID failure",
            ),
        ),
        u8("heading", "Heading", 4, scale = 2.0, unit = "°", gate = neq(4, "FF")),
        i8("vspeed", "Vertical speed", 6, scale = 0.5, unit = "m/s"),
        i32le("latitude", "Latitude", 7, scale = 1e-7, unit = "°"),
        i32le("longitude", "Longitude", 11, scale = 1e-7, unit = "°"),
        u16le("alt_baro", "Altitude (baro)", 15, scale = 0.5, offsetAdd = -1000.0, unit = "m"),
        u16le("alt_geo", "Altitude (HAE)", 17, scale = 0.5, offsetAdd = -1000.0, unit = "m"),
        u16le("height", "Height", 19, scale = 0.5, offsetAdd = -1000.0, unit = "m"),
    )

    private fun selfIdFields(): List<DecodeField> = listOf(
        utf8("self_id", "Self ID", 4, length = 23),
    )

    private fun systemFields(): List<DecodeField> = listOf(
        i32le("op_lat", "Operator lat", 4, scale = 1e-7, unit = "°"),
        i32le("op_lon", "Operator lon", 8, scale = 1e-7, unit = "°"),
    )

    private fun operatorIdFields(): List<DecodeField> = listOf(
        utf8("operator_id", "Operator ID", 4, length = 20),
    )

    /** Gate on message type for protocol versions 0–2 (header = type<<4 | version). */
    private fun forMessageType(type: Int, fields: List<DecodeField>): List<DecodeField> {
        return (0..2).flatMap { ver ->
            val headerGate = eq(2, "%02X".format((type shl 4) or ver))
            fields.map { field ->
                field.copy(gate = field.gate?.let { headerGate.copy(and = it) } ?: headerGate)
            }
        }
    }

    private fun ruuviRawV1(): List<DecodeField> = listOf(
        u8("humidity", "Humidity", 1, scale = 0.5, unit = "%", gate = eq(0, "03")),
        u16be("pressure", "Pressure", 4, scale = 0.01, offsetAdd = 500.0, unit = "hPa", gate = eq(0, "03")),
        i16be("acc_x", "Accel X", 6, scale = 0.001, unit = "g", gate = eq(0, "03")),
        i16be("acc_y", "Accel Y", 8, scale = 0.001, unit = "g", gate = eq(0, "03")),
        i16be("acc_z", "Accel Z", 10, scale = 0.001, unit = "g", gate = eq(0, "03")),
        u16be("battery", "Battery", 12, unit = "mV", gate = eq(0, "03")),
    )

    private fun goveeH5074(): List<DecodeField> {
        val gate = lenEq(7)
        return listOf(
            i16le("temperature", "Temperature", 1, scale = 0.01, unit = "°C", gate = gate),
            u16le("humidity", "Humidity", 3, scale = 0.01, unit = "%", gate = gate),
            u8("battery", "Battery", 5, unit = "%", gate = gate),
        )
    }

    private fun goveeH5075(gate: DecodeWhen): List<DecodeField> = listOf(
        u24be("temperature", "Temperature", 1, scale = 0.0001, unit = "°C", gate = gate),
        u24be("humidity", "Humidity", 1, modulo = 1000.0, scale = 0.1, unit = "%", gate = gate),
        u8("battery", "Battery", 4, unit = "%", gate = gate),
    )

    private fun goveeH510x(gate: DecodeWhen): List<DecodeField> = listOf(
        u24be("temperature", "Temperature", 2, scale = 0.0001, unit = "°C", gate = gate),
        u24be("humidity", "Humidity", 2, modulo = 1000.0, scale = 0.1, unit = "%", gate = gate),
        u8("battery", "Battery", 5, unit = "%", gate = gate),
    )

    private fun onOff(off: String, on: String) = mapOf("0" to off, "1" to on)

    private fun eq(offset: Int, hex: String) =
        DecodeWhen(offset = offset, op = DecodeWhenOp.EQ, valueHex = hex)

    private fun neq(offset: Int, hex: String, and: DecodeWhen? = null) =
        DecodeWhen(offset = offset, op = DecodeWhenOp.NEQ, valueHex = hex, and = and)

    /** Tesla tsTPMS type byte at offset 2: 0–4 sleep, 5+ live. */
    private fun teslaAwake(): DecodeWhen =
        neq(2, "00", neq(2, "01", neq(2, "02", neq(2, "03", neq(2, "04")))))

    private fun lenEq(n: Int, and: DecodeWhen? = null) =
        DecodeWhen(offset = 0, length = n, op = DecodeWhenOp.LEN, valueHex = "", and = and)

    private fun u8(
        id: String, label: String, offset: Int,
        scale: Double? = null, offsetAdd: Double? = null, unit: String? = null,
        enumLabels: Map<String, String>? = null, gate: DecodeWhen? = null,
    ) = DecodeField(
        id, label, offset, type = DecodeType.U8, scale = scale, offsetAdd = offsetAdd,
        unit = unit, enumLabels = enumLabels, gate = gate,
    )

    private fun u16be(
        id: String, label: String, offset: Int,
        scale: Double? = null, offsetAdd: Double? = null, unit: String? = null, gate: DecodeWhen? = null,
    ) = DecodeField(
        id, label, offset, type = DecodeType.U16, endian = DecodeEndian.BE,
        scale = scale, offsetAdd = offsetAdd, unit = unit, gate = gate,
    )

    private fun u24be(
        id: String, label: String, offset: Int,
        scale: Double? = null, modulo: Double? = null, unit: String? = null, gate: DecodeWhen? = null,
    ) = DecodeField(
        id, label, offset, type = DecodeType.U24, endian = DecodeEndian.BE,
        scale = scale, modulo = modulo, unit = unit, gate = gate,
    )

    private fun i16le(
        id: String, label: String, offset: Int,
        scale: Double? = null, unit: String? = null, gate: DecodeWhen? = null,
    ) = DecodeField(
        id, label, offset, type = DecodeType.I16, endian = DecodeEndian.LE,
        scale = scale, unit = unit, gate = gate,
    )

    private fun i8(
        id: String, label: String, offset: Int,
        scale: Double? = null, unit: String? = null, gate: DecodeWhen? = null,
    ) = DecodeField(id, label, offset, type = DecodeType.I8, scale = scale, unit = unit, gate = gate)

    private fun hex(
        id: String, label: String, offset: Int, length: Int,
        gate: DecodeWhen? = null,
    ) = DecodeField(id, label, offset, length = length, type = DecodeType.HEX, gate = gate)

    private fun u16le(
        id: String, label: String, offset: Int,
        scale: Double? = null, offsetAdd: Double? = null, unit: String? = null,
        enumLabels: Map<String, String>? = null, gate: DecodeWhen? = null,
    ) = DecodeField(
        id, label, offset, type = DecodeType.U16, endian = DecodeEndian.LE,
        scale = scale, offsetAdd = offsetAdd, unit = unit, enumLabels = enumLabels, gate = gate,
    )

    private fun i16be(
        id: String, label: String, offset: Int,
        scale: Double? = null, unit: String? = null, gate: DecodeWhen? = null,
    ) = DecodeField(
        id, label, offset, type = DecodeType.I16, endian = DecodeEndian.BE,
        scale = scale, unit = unit, gate = gate,
    )

    private fun i32le(
        id: String, label: String, offset: Int,
        scale: Double? = null, unit: String? = null, gate: DecodeWhen? = null,
    ) = DecodeField(
        id, label, offset, type = DecodeType.I32, endian = DecodeEndian.LE,
        scale = scale, unit = unit, gate = gate,
    )

    private fun u32le(
        id: String, label: String, offset: Int,
        scale: Double? = null, unit: String? = null, gate: DecodeWhen? = null,
    ) = DecodeField(
        id, label, offset, type = DecodeType.U32, endian = DecodeEndian.LE,
        scale = scale, unit = unit, gate = gate,
    )

    private fun utf8(id: String, label: String, offset: Int, length: Int) =
        DecodeField(id, label, offset, length = length, type = DecodeType.UTF8)

    private fun mac(id: String, label: String, offset: Int, gate: DecodeWhen? = null) =
        DecodeField(id, label, offset, type = DecodeType.MAC, gate = gate)

    private fun bits(
        id: String, label: String, offset: Int,
        length: Int? = null, bitOffset: Int, bitWidth: Int,
        endian: DecodeEndian = DecodeEndian.LE,
        scale: Double? = null, offsetAdd: Double? = null, unit: String? = null,
        enumLabels: Map<String, String>? = null, gate: DecodeWhen? = null,
    ) = DecodeField(
        id, label, offset, length = length, type = DecodeType.BITS, endian = endian,
        bitOffset = bitOffset, bitWidth = bitWidth, scale = scale, offsetAdd = offsetAdd,
        unit = unit, enumLabels = enumLabels, gate = gate,
    )
}
