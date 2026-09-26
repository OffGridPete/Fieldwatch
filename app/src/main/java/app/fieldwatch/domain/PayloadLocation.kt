package app.fieldwatch.domain

/**
 * Advertised WGS84 from decode field ids, not the operator phone GPS.
 *
 * Canonical ids are [LAT] / [LON]. Stock Remote ID uses those. A custom map
 * with the same ids pins the same way — there is no Remote ID branch.
 * [OP_LAT] / [OP_LON] are the Remote ID *pilot* location and are never the pin.
 */
data class PayloadLocation(
    val lat: Double? = null,
    val lon: Double? = null,
    val alt: Double? = null,
    val opLat: Double? = null,
    val opLon: Double? = null,
    val uasId: String? = null,
    val selfId: String? = null,
    val headingDeg: Double? = null,
    val speedMps: Double? = null,
    val vspeedMps: Double? = null,
) {
    fun pin(): Pair<Double, Double>? =
        if (validCoord(lat, lon)) lat!! to lon!! else null

    /**
     * ASTM Remote ID rotates message types. A Basic ID packet has no lat/lon;
     * keep the last valid Location (and System operator) pair this session.
     * Basic ID `uas_id` / Self ID stick the same way so TAK can key one aircraft
     * across BLE MAC rotation.
     */
    fun mergeSticky(prev: PayloadLocation?): PayloadLocation {
        val p = prev ?: PayloadLocation()
        val nextPin = if (validCoord(lat, lon)) lat to lon else p.lat to p.lon
        val nextOp = if (validCoord(opLat, opLon)) opLat to opLon else p.opLat to p.opLon
        val nextAlt = alt?.takeIf { it.isFinite() } ?: p.alt
        return PayloadLocation(
            lat = nextPin.first,
            lon = nextPin.second,
            alt = nextAlt,
            opLat = nextOp.first,
            opLon = nextOp.second,
            uasId = uasId?.takeIf { it.isNotBlank() } ?: p.uasId,
            selfId = selfId?.takeIf { it.isNotBlank() } ?: p.selfId,
            headingDeg = headingDeg?.takeIf { it.isFinite() } ?: p.headingDeg,
            speedMps = speedMps?.takeIf { it.isFinite() } ?: p.speedMps,
            vspeedMps = vspeedMps?.takeIf { it.isFinite() } ?: p.vspeedMps,
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
            uasId = device.payloadUasId,
            selfId = device.payloadSelfId,
            headingDeg = device.payloadHeading,
            speedMps = device.payloadSpeed,
            vspeedMps = device.payloadVspeed,
        )

        fun applySticky(device: Sighting, fleets: List<Fleet>): Sighting {
            val decoded = if (device.fleetIds.isEmpty()) {
                emptyList()
            } else {
                SignatureFieldDecoder.decodeSighting(device, fleets)
            }
            val fromBytes = OpenDroneId.fromFacts(device.facts)
            if (decoded.isEmpty() &&
                fromBytes.lat == null &&
                fromBytes.opLat == null &&
                fromBytes.uasId == null &&
                device.payloadLat == null &&
                device.payloadOpLat == null &&
                device.payloadUasId == null
            ) {
                return device
            }
            val next = fromBytes.mergeSticky(fromDecoded(decoded)).mergeSticky(fromSighting(device))
            if (next.lat == device.payloadLat &&
                next.lon == device.payloadLon &&
                next.alt == device.payloadAlt &&
                next.opLat == device.payloadOpLat &&
                next.opLon == device.payloadOpLon &&
                next.uasId == device.payloadUasId &&
                next.selfId == device.payloadSelfId &&
                next.headingDeg == device.payloadHeading &&
                next.speedMps == device.payloadSpeed &&
                next.vspeedMps == device.payloadVspeed
            ) {
                return device
            }
            return device.copy(
                payloadLat = next.lat,
                payloadLon = next.lon,
                payloadAlt = next.alt,
                payloadOpLat = next.opLat,
                payloadOpLon = next.opLon,
                payloadUasId = next.uasId,
                payloadSelfId = next.selfId,
                payloadHeading = next.headingDeg,
                payloadSpeed = next.speedMps,
                payloadVspeed = next.vspeedMps,
            )
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

        private fun text(fields: List<DecodedFieldValue>, ids: Set<String>): String? {
            val hit = fields.firstOrNull { it.id.lowercase() in ids } ?: return null
            return hit.display.trim().trimEnd('\u0000').takeIf { it.isNotEmpty() }
        }
    }
}
