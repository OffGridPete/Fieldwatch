package app.fieldwatch.domain

data class SignatureSlice(
    val fleetId: String,
    val radios: List<Sighting>,
)

data class ClassSlice(
    val kind: SignatureClass?,
    val radios: List<Sighting>,
    val signatures: List<SignatureSlice>,
) {
    val id: String get() = kind?.name ?: "unmatched"
    fun label(): String = kind?.label() ?: "Unmatched"
}

data class OutlineReveal(
    val classIds: Set<String>,
    val sigKeys: Set<String>,
)

/** LazyColumn indices for a By class watchlist jump. */
data class OutlineSnap(
    val classIndex: Int,
    val signatureIndex: Int?,
    val radioIndex: Int,
)

object ClassOutline {
    /**
     * Group the current Live set by signature class, then signature.
     * Every class is present, even at 0, so the list does not jump as radios
     * appear and drop. Classes are A–Z by label. Signatures under a class
     * are A–Z by name. Dual-chip radios sit in every class they matched.
     * Unmatched is always last. Radio order inside a bucket follows
     * [devices] (Live sort).
     */
    fun of(
        devices: List<Sighting>,
        classByFleetId: Map<String, SignatureClass>,
        nameByFleetId: Map<String, String> = emptyMap(),
    ): List<ClassSlice> {
        data class Acc(
            val keys: LinkedHashSet<String> = LinkedHashSet(),
            val byFleet: LinkedHashMap<String, LinkedHashSet<String>> = LinkedHashMap(),
        )
        val byClass = LinkedHashMap<SignatureClass, Acc>()
        val unmatched = ArrayList<Sighting>()
        val byKey = LinkedHashMap<String, Sighting>(devices.size)
        devices.forEach { device -> byKey[device.key] = device }
        devices.forEach { device ->
            if (device.fleetIds.isEmpty()) {
                unmatched += device
                return@forEach
            }
            device.fleetIds.forEach { id ->
                val kind = (classByFleetId[id] ?: SignatureClass.OTHER).folded()
                val acc = byClass.getOrPut(kind) { Acc() }
                acc.keys += device.key
                acc.byFleet.getOrPut(id) { LinkedHashSet() }.add(device.key)
            }
        }
        fun radiosOf(keys: Collection<String>): List<Sighting> = keys.mapNotNull { byKey[it] }
        fun slice(kind: SignatureClass, acc: Acc?): ClassSlice {
            if (acc == null) return ClassSlice(kind, emptyList(), emptyList())
            val sigs = acc.byFleet.entries
                .sortedBy { (fleetId, _) ->
                    (nameByFleetId[fleetId] ?: fleetId).lowercase()
                }
                .map { (fleetId, keys) -> SignatureSlice(fleetId, radiosOf(keys)) }
            return ClassSlice(kind, radiosOf(acc.keys), sigs)
        }
        return SignatureClass.visible
            .sortedBy { it.label().lowercase() }
            .map { kind -> slice(kind, byClass[kind]) } +
            ClassSlice(null, unmatched, emptyList())
    }

    fun multiClassCount(
        devices: List<Sighting>,
        classByFleetId: Map<String, SignatureClass>,
    ): Int = devices.count { device ->
        device.fleetIds.mapNotNull { classByFleetId[it] }.toSet().size > 1
    }

    /** Class / signature ids to open so [device] is composed on By class. */
    fun reveal(
        device: Sighting,
        classByFleetId: Map<String, SignatureClass>,
    ): OutlineReveal {
        if (device.fleetIds.isEmpty()) {
            return OutlineReveal(setOf("unmatched"), emptySet())
        }
        val classIds = LinkedHashSet<String>()
        val sigKeys = LinkedHashSet<String>()
        device.fleetIds.forEach { id ->
            val kind = (classByFleetId[id] ?: SignatureClass.OTHER).folded()
            classIds += kind.name
            sigKeys += "${kind.name}/$id"
        }
        return OutlineReveal(classIds, sigKeys)
    }

    /**
     * LazyColumn index of the first radio row for [deviceKey], or null if that
     * branch is closed. [leadingItems] is the count of rows before the first
     * class header (chips + summary).
     */
    fun radioItemIndex(
        slices: List<ClassSlice>,
        openClasses: Set<String>,
        openSigs: Set<String>,
        deviceKey: String,
        leadingItems: Int = 1,
    ): Int? = outlineSnap(slices, openClasses, openSigs, deviceKey, leadingItems)?.radioIndex

    /**
     * Class header, signature header (null for Unmatched), and radio row
     * indices so a watchlist jump can keep the tree in view when it fits.
     */
    fun outlineSnap(
        slices: List<ClassSlice>,
        openClasses: Set<String>,
        openSigs: Set<String>,
        deviceKey: String,
        leadingItems: Int = 1,
    ): OutlineSnap? {
        var idx = leadingItems.coerceAtLeast(0)
        for (slice in slices) {
            val classIndex = idx
            idx += 1
            if (slice.id !in openClasses) continue
            if (slice.kind == null) {
                for (radio in slice.radios) {
                    if (radio.key == deviceKey) {
                        return OutlineSnap(classIndex, signatureIndex = null, radioIndex = idx)
                    }
                    idx += 1
                }
            } else {
                for (sig in slice.signatures) {
                    val sigKey = "${slice.id}/${sig.fleetId}"
                    val signatureIndex = idx
                    idx += 1
                    if (sigKey !in openSigs) continue
                    for (radio in sig.radios) {
                        if (radio.key == deviceKey) {
                            return OutlineSnap(classIndex, signatureIndex, radioIndex = idx)
                        }
                        idx += 1
                    }
                }
            }
        }
        return null
    }
}
