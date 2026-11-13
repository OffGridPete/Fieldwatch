package app.fieldwatch.domain

/**
 * Advertised WGS84 from decode field ids, not the operator phone GPS.
 *
 * Canonical ids are [LAT] / [LON]. Stock Remote ID uses those. A custom map
 * with the same ids pins the same way — there is no Remote ID branch.
 * [OP_LAT] / [OP_LON] are the Remote ID operator location and are never the pin.
 * [opKind] is takeoff, live GPS, or a fixed point. Null means this packet did not say.
 */
/**
 * One advertisement, not the facts kept from earlier packets.
 * Phone coordinates are this hear only. A missing fix stays null.
 */
data class HeardFix(
    val facts: RadioFacts,
    val phoneLat: Double? = null,
    val phoneLon: Double? = null,
)

data class PayloadLocation(
    val lat: Double? = null,
    val lon: Double? = null,
    val alt: Double? = null,
    val opLat: Double? = null,
    val opLon: Double? = null,
    /** 0 takeoff, 1 live GPS, 2 fixed. Null when this packet is not a System message. */
    val opKind: Int? = null,
    /** Operator altitude in meters. Null when this packet did not send a known value. */
    val opAlt: Double? = null,
    val uasId: String? = null,
    val selfId: String? = null,
    val headingDeg: Double? = null,
    val speedMps: Double? = null,
    val vspeedMps: Double? = null,
    /** Maker from a CTA-2063 serial. Null when this message did not name one. */
    val aircraft: String? = null,
    /** This message is a Basic ID, including one that clears [aircraft]. */
    val basicId: Boolean = false,
    /** ASTM Auth. Null when this packet is not an Auth message. */
    val auth: RemoteIdAuth? = null,
) {
    fun pin(): Pair<Double, Double>? =
        if (validCoord(lat, lon)) lat!! to lon!! else null

    /**
     * ASTM Remote ID rotates message types. A Basic ID packet has no lat/lon;
     * keep the last valid Location (and System operator) pair this session.
     * Basic ID `uas_id` / Self ID stick the same way so TAK can key one aircraft
     * across BLE MAC rotation.
     * A Serial Basic ID can name the maker in [aircraft]. That name sticks across
     * Location and System. A later Basic ID with no maker clears it. [basicId]
     * is only for this merge. It is not stored on the radio.
     */
    fun mergeSticky(prev: PayloadLocation?): PayloadLocation {
        val p = prev ?: PayloadLocation()
        val nextPin = if (validCoord(lat, lon)) lat to lon else p.lat to p.lon
        val nextOp = if (validCoord(opLat, opLon)) opLat to opLon else p.opLat to p.opLon
        val nextAlt = alt?.takeIf { it.isFinite() } ?: p.alt
        val nextAircraft = when {
            basicId -> aircraft?.takeIf { it.isNotBlank() }
            else -> aircraft?.takeIf { it.isNotBlank() } ?: p.aircraft
        }
        return PayloadLocation(
            lat = nextPin.first,
            lon = nextPin.second,
            alt = nextAlt,
            opLat = nextOp.first,
            opLon = nextOp.second,
            opKind = opKind ?: p.opKind,
            opAlt = opAlt?.takeIf { OperatorLoc.knownAlt(it) } ?: p.opAlt,
            uasId = uasId?.takeIf { it.isNotBlank() } ?: p.uasId,
            selfId = selfId?.takeIf { it.isNotBlank() } ?: p.selfId,
            headingDeg = headingDeg?.takeIf { it.isFinite() } ?: p.headingDeg,
            speedMps = speedMps?.takeIf { it.isFinite() } ?: p.speedMps,
            vspeedMps = vspeedMps?.takeIf { it.isFinite() } ?: p.vspeedMps,
            aircraft = nextAircraft,
            basicId = basicId || p.basicId,
            auth = auth?.onto(p.auth) ?: p.auth,
        )
    }

    companion object {
        const val LAT = "latitude"
        const val LON = "longitude"
        const val OP_LAT = "op_lat"
        const val OP_LON = "op_lon"

        private val LAT_IDS = setOf("latitude", "lat")
        private val LON_IDS = setOf("longitude", "lon", "lng")
        private val ALT_IDS = setOf("alt_geo", "altitude", "alt", "hae")
        private val OP_LAT_IDS = setOf("op_lat", "operator_lat")
        private val OP_LON_IDS = setOf("op_lon", "operator_lon")
        private val OP_KIND_IDS = setOf("op_loc_type")
        private val OP_ALT_IDS = setOf("op_alt", "operator_alt")
        private val UAS_IDS = setOf("uas_id", "uasid", "serial")
        private val SELF_IDS = setOf("self_id", "selfid")
        private val HEADING_IDS = setOf("heading", "course")
        private val SPEED_IDS = setOf("speed", "hspeed")
        private val VSPEED_IDS = setOf("vspeed", "vert_speed")

        fun fromDecoded(fields: List<DecodedFieldValue>): PayloadLocation {
            if (fields.isEmpty()) return PayloadLocation()
            return PayloadLocation(
                lat = num(fields, LAT_IDS),
                lon = num(fields, LON_IDS),
                alt = num(fields, ALT_IDS),
                opLat = num(fields, OP_LAT_IDS),
                opLon = num(fields, OP_LON_IDS),
                opKind = kind(fields),
                opAlt = opAltitude(fields),
                uasId = text(fields, UAS_IDS),
                selfId = text(fields, SELF_IDS),
                headingDeg = num(fields, HEADING_IDS),
                speedMps = num(fields, SPEED_IDS),
                vspeedMps = num(fields, VSPEED_IDS),
            )
        }

        fun fromSighting(device: Sighting): PayloadLocation = PayloadLocation(
            lat = device.payloadLat,
            lon = device.payloadLon,
            alt = device.payloadAlt,
            opLat = device.payloadOpLat,
            opLon = device.payloadOpLon,
            opKind = device.payloadOpKind,
            opAlt = device.payloadOpAlt,
            uasId = device.payloadUasId,
            selfId = device.payloadSelfId,
            headingDeg = device.payloadHeading,
            speedMps = device.payloadSpeed,
            vspeedMps = device.payloadVspeed,
            aircraft = device.payloadAircraft,
            auth = device.payloadAuth,
        )

        fun applySticky(device: Sighting, fleets: List<Fleet>, heard: HeardFix? = null): Sighting {
            val decoded = if (device.fleetIds.isEmpty()) {
                emptyList()
            } else {
                SignatureFieldDecoder.decodeSighting(device, fleets)
            }
            val fromBytes = OpenDroneId.fromFacts(device.facts)
            if (decoded.isEmpty() &&
                fromBytes.lat == null &&
                fromBytes.opLat == null &&
                fromBytes.opKind == null &&
                fromBytes.opAlt == null &&
                fromBytes.alt == null &&
                fromBytes.headingDeg == null &&
                fromBytes.speedMps == null &&
                fromBytes.vspeedMps == null &&
                fromBytes.uasId == null &&
                !fromBytes.basicId &&
                fromBytes.auth == null &&
                device.payloadLat == null &&
                device.payloadOpLat == null &&
                device.payloadOpKind == null &&
                device.payloadOpAlt == null &&
                device.payloadUasId == null &&
                device.payloadAuth == null
            ) {
                return withReadStamp(device, fleets, heard)
            }
            val next = fromBytes.mergeSticky(fromDecoded(decoded)).mergeSticky(fromSighting(device))
            val merged = if (next.lat == device.payloadLat &&
                next.lon == device.payloadLon &&
                next.alt == device.payloadAlt &&
                next.opLat == device.payloadOpLat &&
                next.opLon == device.payloadOpLon &&
                next.opKind == device.payloadOpKind &&
                next.opAlt == device.payloadOpAlt &&
                next.uasId == device.payloadUasId &&
                next.selfId == device.payloadSelfId &&
                next.headingDeg == device.payloadHeading &&
                next.speedMps == device.payloadSpeed &&
                next.vspeedMps == device.payloadVspeed &&
                next.aircraft == device.payloadAircraft &&
                next.auth == device.payloadAuth
            ) {
                device
            } else {
                device.copy(
                    payloadLat = next.lat,
                    payloadLon = next.lon,
                    payloadAlt = next.alt,
                    payloadOpLat = next.opLat,
                    payloadOpLon = next.opLon,
                    payloadOpKind = next.opKind,
                    payloadOpAlt = next.opAlt,
                    payloadUasId = next.uasId,
                    payloadSelfId = next.selfId,
                    payloadHeading = next.headingDeg,
                    payloadSpeed = next.speedMps,
                    payloadVspeed = next.vspeedMps,
                    payloadAircraft = next.aircraft,
                    payloadAuth = next.auth,
                )
            }
            return withReadStamp(merged, fleets, heard)
        }

        /**
         * Stamp the read time from this advertisement only.
         * A later Basic ID keeps the previous stamp. A repeat of the same
         * spot still moves the stamp, so a report can use where the phone was
         * at the last reading.
         */
        private fun withReadStamp(device: Sighting, fleets: List<Fleet>, heard: HeardFix?): Sighting {
            if (heard == null) return device
            val packet = packetLocation(device, fleets, heard)
            val pin = validCoord(packet.lat, packet.lon)
            val op = validCoord(packet.opLat, packet.opLon)
            if (!pin && !op) return device
            val phoneOk = validCoord(heard.phoneLat, heard.phoneLon)
            val phoneLat = if (phoneOk) heard.phoneLat else null
            val phoneLon = if (phoneOk) heard.phoneLon else null
            val at = device.lastSeen
            val nextAt = if (pin) at else device.payloadAt
            val nextPhoneLat = if (pin) phoneLat else device.payloadPhoneLat
            val nextPhoneLon = if (pin) phoneLon else device.payloadPhoneLon
            val nextOpAt = if (op) at else device.payloadOpAt
            val nextOpPhoneLat = if (op) phoneLat else device.payloadOpPhoneLat
            val nextOpPhoneLon = if (op) phoneLon else device.payloadOpPhoneLon
            if (nextAt == device.payloadAt &&
                nextPhoneLat == device.payloadPhoneLat &&
                nextPhoneLon == device.payloadPhoneLon &&
                nextOpAt == device.payloadOpAt &&
                nextOpPhoneLat == device.payloadOpPhoneLat &&
                nextOpPhoneLon == device.payloadOpPhoneLon
            ) {
                return device
            }
            return device.copy(
                payloadAt = nextAt,
                payloadPhoneLat = nextPhoneLat,
                payloadPhoneLon = nextPhoneLon,
                payloadOpAt = nextOpAt,
                payloadOpPhoneLat = nextOpPhoneLat,
                payloadOpPhoneLon = nextOpPhoneLon,
            )
        }

        /** Location carried by this packet. Sticky facts from earlier packets are not included. */
        private fun packetLocation(device: Sighting, fleets: List<Fleet>, heard: HeardFix): PayloadLocation {
            val decoded = if (device.fleetIds.isEmpty()) {
                emptyList()
            } else {
                SignatureFieldDecoder.decodeSighting(device.copy(facts = heard.facts), fleets)
            }
            return OpenDroneId.fromFacts(heard.facts).mergeSticky(fromDecoded(decoded))
        }

        fun validCoord(lat: Double?, lon: Double?): Boolean {
            if (lat == null || lon == null) return false
            if (!lat.isFinite() || !lon.isFinite()) return false
            if (lat == 0.0 && lon == 0.0) return false
            if (lat !in -90.0..90.0) return false
            if (lon !in -180.0..180.0) return false
            return true
        }

        private fun num(fields: List<DecodedFieldValue>, ids: Set<String>): Double? {
            val hit = fields.firstOrNull { it.id.lowercase() in ids } ?: return null
            return hit.number?.takeIf { it.isFinite() }
        }

        private fun kind(fields: List<DecodedFieldValue>): Int? {
            val n = num(fields, OP_KIND_IDS) ?: return null
            val k = n.toInt()
            return if (k in 0..2 && k.toDouble() == n) k else null
        }

        private fun opAltitude(fields: List<DecodedFieldValue>): Double? {
            val n = num(fields, OP_ALT_IDS) ?: return null
            return n.takeIf { OperatorLoc.knownAlt(it) }
        }

        private fun text(fields: List<DecodedFieldValue>, ids: Set<String>): String? {
            val hit = fields.firstOrNull { it.id.lowercase() in ids } ?: return null
            return hit.display.trim().trimEnd('\u0000').takeIf { it.isNotEmpty() }
        }
    }
}

/**
 * ASTM F3411 operator location type. 0 is takeoff, 1 is the live pilot, 2 is a fixed point.
 * Null is an older sit, or a plate that has not sent a System message yet.
 */
object OperatorLoc {
    const val TAKEOFF = 0
    const val LIVE = 1
    const val FIXED = 2

    fun label(kind: Int?): String = when (kind) {
        TAKEOFF -> "Takeoff"
        LIVE -> "Pilot"
        FIXED -> "Fixed"
        else -> "Operator"
    }

    /** Remarks line. Live GPS keeps the existing pilot phrase. */
    fun where(kind: Int?): String = when (kind) {
        TAKEOFF -> "takeoff position"
        LIVE -> "operator (pilot) position"
        FIXED -> "fixed operator position"
        else -> "operator position"
    }

    /** Encoded 0 is the unknown sentinel (−1000 m). A real altitude is above that. */
    fun knownAlt(alt: Double?): Boolean = alt != null && alt.isFinite() && alt > -1000.0

    /** CoT HAE. Unknown stays the CoT unknown height. */
    fun hae(alt: Double?): Double = if (knownAlt(alt)) alt!! else 9999999.0
}
