package app.fieldwatch.domain

import java.time.ZoneId

/**
 * Radios already heard at two or more stops in the last 7 days.
 * The list does not wait for a sit, and it does not wait for the radio to be in earshot.
 * Each row keeps one color. The map uses that same color.
 * A stop is where this phone was.
 */
object PlaceReport {
    /**
     * Saturated enough to read on a street map and on the list, in day and night.
     * The ninth radio repeats the first color.
     */
    val COLORS: LongArray = longArrayOf(
        0xFF2563EB,
        0xFFDC2626,
        0xFF059669,
        0xFFD97706,
        0xFF7C3AED,
        0xFFDB2777,
        0xFF0891B2,
        0xFF4F46E5,
    )

    data class Stop(
        val lat: Double,
        val lon: Double,
        val at: Long,
    )

    data class Pin(
        val lat: Double,
        val lon: Double,
        val color: Long,
    )

    /** One stop, in the order this phone heard the radio. */
    data class Visit(
        val number: Int,
        val time: String,
        val name: String,
        val street: String,
        val coord: String,
    )

    data class Row(
        val id: String,
        val deviceKey: String,
        val color: Long,
        val title: String,
        val address: String,
        val count: Int,
        val visits: List<Visit>,
        /** A return after a quiet stretch. Blank when there is nothing to add. */
        val note: String,
        val stops: List<Stop>,
        /** True when this radio is still on the live list. The row opens either way. */
        val known: Boolean,
    )

    fun colorAt(index: Int): Long = COLORS[index % COLORS.size]

    fun rows(
        records: List<PlaceMemory.Record>,
        devices: List<Sighting>,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        now: Long,
        zone: ZoneId,
        demo: Boolean,
        streets: Map<String, String>,
    ): List<Row> {
        if (records.isEmpty()) return emptyList()
        val mine = RadioBookmarks.mineKeys(watchlist)
        val custom = RadioBookmarks.labels(watchlist)
        val fleetNames = HashMap<String, String>(fleets.size)
        for (fleet in fleets) fleetNames[fleet.id] = fleet.name
        val live = HashMap<String, String>(devices.size)
        val known = HashSet<String>(devices.size)
        for (device in devices) {
            known += device.key
            val sig = device.fleetIds.mapNotNull { fleetNames[it] }
            val title = displayTitle(
                device.listLineText(ListLine.NAME_AND_TYPE, sig, custom[device.key]),
                device.mac,
            )
            if (title.isNotEmpty()) live[device.key] = title
        }
        val ranked = records
            .filter { it.deviceKey !in mine && PlaceMemory.placeCount(it, now) >= PlaceMemory.MIN_PLACES }
            .sortedWith(
                compareByDescending<PlaceMemory.Record> { PlaceMemory.placeCount(it, now) }
                    .thenByDescending { it.lastHeardAt }
                    .thenBy { it.id },
            )
        return ranked.mapIndexed { index, record ->
            val count = PlaceMemory.placeCount(record, now)
            val address = addressOf(record.deviceKey, demo)
            val mac = RadioBookmarks.parseKey(record.deviceKey)?.second.orEmpty()
            val stored = displayTitle(record.label, mac)
            val named = displayTitle(custom[record.deviceKey].orEmpty(), mac)
            val title = live[record.deviceKey].orEmpty()
                .ifBlank { named }
                .ifBlank { stored }
                .ifBlank { address }
                .ifBlank { "—" }
            val places = PlaceMemory.placesInWindow(record, now).sortedBy { it.firstAt }
            val stops = places.mapNotNull { place ->
                if (!PayloadLocation.validCoord(place.lat, place.lon)) null
                else Stop(place.lat, place.lon, place.lastAt)
            }
            val visits = places.mapIndexed { placeIndex, place ->
                val parts = PlaceMemory.visitParts(place, now, zone, demo, streets)
                Visit(
                    number = placeIndex + 1,
                    time = parts.time,
                    name = parts.name,
                    street = parts.street,
                    coord = parts.coord,
                )
            }
            Row(
                id = record.id,
                deviceKey = record.deviceKey,
                color = colorAt(index),
                title = title,
                address = if (address.isNotEmpty() && !address.equals(title, ignoreCase = true)) address else "",
                count = count,
                visits = visits,
                note = PlaceMemory.returnLine(record, now),
                stops = stops,
                known = record.deviceKey in known,
            )
        }
    }

    fun pins(rows: List<Row>): List<Pin> = rows.flatMap { row ->
        row.stops.map { Pin(it.lat, it.lon, row.color) }
    }

    /** Latest stop on the list. The wide map opens here. */
    fun newest(rows: List<Row>): Stop? =
        rows.asSequence().flatMap { it.stops.asSequence() }.maxByOrNull { it.at }

    /**
     * A radio the live list has already dropped.
     * The name is the one saved with the places. The radio page still reads those places.
     */
    fun remembered(record: PlaceMemory.Record): Sighting? {
        val parsed = RadioBookmarks.parseKey(record.deviceKey) ?: return null
        val (kind, mac) = parsed
        val first = record.places.minOfOrNull { it.firstAt } ?: record.lastHeardAt
        val last = if (record.lastHeardAt > 0L) record.lastHeardAt else first
        return Sighting(
            key = record.deviceKey,
            kind = kind,
            mac = mac,
            name = displayTitle(record.label, mac),
            rssi = -100,
            rssiMin = -100,
            rssiMax = -100,
            channel = 0,
            frequencyMhz = 0,
            vendor = null,
            randomized = MacUtil.isRandomized(mac),
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = first,
            lastSeen = last,
            hitCount = record.places.sumOf { it.count.coerceAtLeast(0) },
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = listOf(PresenceSpan(first, last)),
            gone = true,
        )
    }

    /** Live Name + type, with a MAC or "unnamed LE" left blank so the row can fall through. */
    private fun displayTitle(raw: String, mac: String = ""): String {
        val title = raw.trim()
        if (title.isEmpty() || title.equals("unnamed LE", ignoreCase = true)) return ""
        if (mac.isNotEmpty() && title.equals(mac, ignoreCase = true)) return ""
        return title
    }

    private fun addressOf(deviceKey: String, demo: Boolean): String {
        val mac = RadioBookmarks.parseKey(deviceKey)?.second ?: return ""
        return MacUtil.screenMac(mac, demo)
    }
}
