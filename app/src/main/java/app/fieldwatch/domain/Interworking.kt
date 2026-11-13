package app.fieldwatch.domain

/**
 * Wi-Fi Interworking element (ID 107). The access point sets these fields.
 * A missing element leaves a value already heard in place.
 */
data class Interworking(
    val accessType: Int,
    val internet: Boolean,
    val emergencyReachable: Boolean,
    val venueGroup: Int? = null,
    val venueType: Int? = null,
    val hessid: String? = null,
) {
    fun lines(demo: Boolean): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(5)
        out += LanguageText.text("ui.ap.net.type") to accessName(accessType)
        out += LanguageText.text("ui.ap.internet") to yesNo(internet)
        if (emergencyReachable) {
            out += LanguageText.text("ui.ap.emergency") to LanguageText.text("ui.ap.yes")
        }
        val group = venueGroup
        val type = venueType
        if (group != null && type != null && !(group == 0 && type == 0)) {
            out += LanguageText.text("ui.ap.venue") to LanguageText.text(
                "ui.ap.venue.value",
                mapOf("group" to groupName(group), "type" to typeName(group, type)),
            )
        }
        hessid?.let { id ->
            out += LanguageText.text("ui.ap.hessid") to MacUtil.screenMac(id, demo)
        }
        return out
    }

    /** Venue group 10. The type name stays the English registry word. */
    fun vehicleSentence(): String? {
        val group = venueGroup ?: return null
        val type = venueType ?: return null
        if (group != 10) return null
        return LanguageText.text(
            "explain.ap.vehicle",
            mapOf("type" to typeName(group, type)),
        )
    }

    companion object {
        fun parse(bytes: ByteArray): Interworking? {
            if (bytes.size != 1 && bytes.size != 3 && bytes.size != 7 && bytes.size != 9) return null
            return runCatching {
                val b0 = bytes[0].toInt() and 0xFF
                val venue = if (bytes.size == 3 || bytes.size == 9) {
                    (bytes[1].toInt() and 0xFF) to (bytes[2].toInt() and 0xFF)
                } else {
                    null
                }
                val hessid = if (bytes.size == 7 || bytes.size == 9) {
                    val start = bytes.size - 6
                    MacUtil.normalize(
                        (start until start + 6).joinToString(":") { i ->
                            "%02X".format(bytes[i].toInt() and 0xFF)
                        },
                    )
                } else {
                    null
                }
                Interworking(
                    accessType = b0 and 0x0F,
                    internet = b0 and 0x10 != 0,
                    emergencyReachable = b0 and 0x40 != 0,
                    venueGroup = venue?.first,
                    venueType = venue?.second,
                    hessid = hessid,
                )
            }.getOrNull()
        }

        /** A shorter later beacon keeps a venue or hotspot id already seen. */
        fun merge(older: Interworking?, newer: Interworking?): Interworking? {
            if (newer == null) return older
            if (older == null) return newer
            return newer.copy(
                venueGroup = newer.venueGroup ?: older.venueGroup,
                venueType = newer.venueType ?: older.venueType,
                hessid = newer.hessid ?: older.hessid,
            )
        }

        fun accessName(code: Int): String = when (code) {
            0 -> "Private network"
            1 -> "Private network with guest access"
            2 -> "Chargeable public network"
            3 -> "Free public network"
            4 -> "Personal device network"
            5 -> "Emergency services only network"
            14 -> "Test or experimental"
            15 -> "Wildcard"
            else -> "type $code"
        }

        fun groupName(code: Int): String = GROUPS[code] ?: "group $code"

        fun typeName(group: Int, type: Int): String {
            if (type == 0) return "Unspecified"
            return TYPES[group]?.get(type) ?: "type $type"
        }

        private fun yesNo(on: Boolean): String =
            LanguageText.text(if (on) "ui.ap.yes" else "ui.ap.no")

        private val GROUPS = mapOf(
            0 to "Unspecified",
            1 to "Assembly",
            2 to "Business",
            3 to "Educational",
            4 to "Factory and Industrial",
            5 to "Institutional",
            6 to "Mercantile",
            7 to "Residential",
            8 to "Storage",
            9 to "Utility and Miscellaneous",
            10 to "Vehicular",
            11 to "Outdoor",
        )

        private val TYPES = mapOf(
            1 to mapOf(
                1 to "Arena",
                2 to "Stadium",
                3 to "Passenger Terminal",
                4 to "Amphitheater",
                5 to "Amusement Park",
                6 to "Place of Worship",
                7 to "Convention Center",
                8 to "Library",
                9 to "Museum",
                10 to "Restaurant",
                11 to "Theater",
                12 to "Bar",
                13 to "Coffee Shop",
                14 to "Zoo or Aquarium",
                15 to "Emergency Coordination Center",
            ),
            2 to mapOf(
                1 to "Doctor or Dentist office",
                2 to "Bank",
                3 to "Fire Station",
                4 to "Police Station",
                6 to "Post Office",
                7 to "Professional Office",
                8 to "Research and Development Facility",
                9 to "Attorney Office",
            ),
            3 to mapOf(
                1 to "School, Primary",
                2 to "School, Secondary",
                3 to "University or College",
            ),
            4 to mapOf(1 to "Factory"),
            5 to mapOf(
                1 to "Hospital",
                2 to "Long-Term Care Facility",
                3 to "Alcohol and Drug Rehabilitation Center",
                4 to "Group Home",
                5 to "Prison or Jail",
            ),
            6 to mapOf(
                1 to "Retail Store",
                2 to "Grocery Market",
                3 to "Automotive Service Station",
                4 to "Shopping Mall",
                5 to "Gas Station",
            ),
            7 to mapOf(
                1 to "Private Residence",
                2 to "Hotel or Motel",
                3 to "Dormitory",
                4 to "Boarding House",
            ),
            10 to mapOf(
                1 to "Automobile or Truck",
                2 to "Airplane",
                3 to "Bus",
                4 to "Ferry",
                5 to "Ship or Boat",
                6 to "Train",
                7 to "Motor Bike",
            ),
            11 to mapOf(
                1 to "Muni-mesh Network",
                2 to "City Park",
                3 to "Rest Area",
                4 to "Traffic Control",
                5 to "Bus Stop",
                6 to "Kiosk",
            ),
        )
    }
}
