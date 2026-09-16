package app.fieldwatch.domain

class FilterEngine {
    fun pass(
        device: Sighting,
        filter: FilterState,
        travel: CoTravel.Ctx = CoTravel.Ctx.None,
        now: Long = System.currentTimeMillis(),
        classByFleetId: Map<String, SignatureClass> = emptyMap(),
        namedRadioKeys: Set<String> = emptySet(),
        watchedFleetIds: Set<String> = emptySet(),
        alertDeviceKeys: Set<String> = emptySet(),
    ): Boolean {
        val named = device.fleetIds.isNotEmpty()
        val namedOk = if (filter.namedOnly) named else true
        val customNamedOk = if (filter.customNamesOnly) device.key in namedRadioKeys else true
        val watchedOk = if (!filter.watchedOnly) true else {
            device.key in alertDeviceKeys || device.fleetIds.any { it in watchedFleetIds }
        }
        val typeOk = when (device.kind) {
            RadioKind.WIFI -> filter.showWifi
            RadioKind.BLE -> filter.showBle
        }
        val hideOk = if (filter.excludeSignatures && filter.fleetIds.isNotEmpty()) {
            device.fleetIds.none { it in filter.fleetIds }
        } else true
        val includeOk = if (!filter.includeSignatures || filter.includeFleetIds.isEmpty()) true
        else device.fleetIds.any { it in filter.includeFleetIds }
        val deviceClasses = device.fleetIds.mapNotNull { classByFleetId[it] }.toSet()
        val hideClassOk = if (filter.excludeClasses && filter.classes.isNotEmpty()) {
            deviceClasses.none { it in filter.classes }
        } else true
        val includeClassOk = if (!filter.useClassFilter || filter.excludeClasses || filter.classes.isEmpty()) true
        else deviceClasses.any { it in filter.classes }
        val rssiOk = device.rssi >= filter.rssiMin
        val nameOk = filter.nameQuery.isBlank() ||
            TextMatch.contains(device.name, filter.nameQuery) ||
            TextMatch.contains(device.mac, filter.nameQuery)
        val ouiOk = filter.ouiQuery.isBlank() ||
            TextMatch.contains(device.mac, filter.ouiQuery) ||
            (device.vendor != null && TextMatch.contains(device.vendor, filter.ouiQuery))

        val gates = if (filter.logic == FilterLogic.AND) {
            namedOk && customNamedOk && watchedOk && typeOk && hideOk && includeOk && hideClassOk && includeClassOk &&
                rssiOk && nameOk && ouiOk
        } else {
            val optional = mutableListOf<Boolean>()
            if (filter.includeSignatures) optional += includeOk
            if (filter.useClassFilter && !filter.excludeClasses) optional += includeClassOk
            if (filter.nameQuery.isNotBlank()) optional += nameOk
            if (filter.ouiQuery.isNotBlank()) optional += ouiOk
            if (filter.rssiMin > -100) optional += rssiOk
            val any = if (optional.isEmpty()) true else optional.any { it }
            namedOk && customNamedOk && watchedOk && typeOk && hideOk && hideClassOk && any
        }
        if (!gates) return false
        if (filter.hideFastPairAccountKey && FastPair.isAccountKeyOnly(device)) return false
        if (!filter.movingWithYou) return true
        return CoTravel.withYou(device, travel, now)
    }

    fun defaultPresets(@Suppress("UNUSED_PARAMETER") fleets: List<Fleet> = emptyList()): List<FilterPreset> {
        fun only(kind: SignatureClass, bleOnly: Boolean = false) = FilterState(
            useClassFilter = true,
            classes = setOf(kind),
            showWifi = !bleOnly,
            showBle = true,
        )
        return listOf(
            FilterPreset("all", "All traffic", FilterState()),
            FilterPreset("wifi", "Wi-Fi only", FilterState(showBle = false)),
            FilterPreset("ble", "BLE only", FilterState(showWifi = false)),
            FilterPreset("strong", "Strong signal", FilterState(rssiMin = -70)),
            FilterPreset(
                "with-you",
                "Moving with you",
                FilterState(movingWithYou = true, showWifi = false),
            ),
            FilterPreset("trackers", "Trackers", only(SignatureClass.FINDER, bleOnly = true)),
            FilterPreset(
                "hide-trackers",
                "Hide trackers",
                FilterState(useClassFilter = true, excludeClasses = true, classes = setOf(SignatureClass.FINDER)),
            ),
            FilterPreset(
                "hide-phones",
                "Hide phones",
                FilterState(useClassFilter = true, excludeClasses = true, classes = setOf(SignatureClass.PHONE)),
            ),
        )
    }
}
