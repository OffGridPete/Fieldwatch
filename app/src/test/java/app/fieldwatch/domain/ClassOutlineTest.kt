package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassOutlineTest {
    private val classBy = mapOf(
        "fleet-gopro" to SignatureClass.CAMERA,
        "fleet-osmo" to SignatureClass.CAMERA,
        "fleet-dji" to SignatureClass.DRONE,
        "fleet-tapo" to SignatureClass.CAMERA,
        "fleet-tplink" to SignatureClass.ISP,
    )

    @Test
    fun unmatchedSitsLastAndClassCountsAreUniqueRadios() {
        val goproA = radio("a", "fleet-gopro")
        val goproB = radio("b", "fleet-gopro")
        val osmo = radio("c", "fleet-osmo")
        val drone = radio("d", "fleet-dji")
        val unnamed = radio("u")
        val slices = ClassOutline.of(listOf(goproA, goproB, osmo, drone, unnamed), classBy)
        assertEquals(SignatureClass.visible.size + 1, slices.size)
        val expected = SignatureClass.visible.sortedBy { it.label().lowercase() } + null
        assertEquals(expected, slices.map { it.kind })
        val cameras = slices.first { it.kind == SignatureClass.CAMERA }
        val drones = slices.first { it.kind == SignatureClass.DRONE }
        assertEquals(3, cameras.radios.size)
        assertEquals(2, cameras.signatures.size)
        assertEquals(2, cameras.signatures.first { it.fleetId == "fleet-gopro" }.radios.size)
        assertEquals(1, drones.radios.size)
        assertEquals(0, slices.first { it.kind == SignatureClass.FINDER }.radios.size)
        assertEquals("Unmatched", slices.last().label())
        assertEquals(1, slices.last().radios.size)
        assertTrue(slices.last().signatures.isEmpty())
    }

    @Test
    fun dualChipRadioCountsInEachClassOnce() {
        val dual = radio("x", "fleet-tapo", "fleet-tplink")
        val slices = ClassOutline.of(listOf(dual), classBy)
        assertEquals(SignatureClass.visible.size + 1, slices.size)
        assertEquals(1, slices.first { it.kind == SignatureClass.CAMERA }.radios.size)
        assertEquals(1, slices.first { it.kind == SignatureClass.ISP }.radios.size)
        assertEquals(1, ClassOutline.multiClassCount(listOf(dual), classBy))
        assertEquals(2, slices.sumOf { it.radios.size })
        assertEquals(1, slices.flatMap { it.radios }.distinctBy { it.key }.size)
    }

    @Test
    fun emptyAndAllUnmatchedKeepEveryClass() {
        val empty = ClassOutline.of(emptyList(), classBy)
        assertEquals(SignatureClass.visible.size + 1, empty.size)
        assertTrue(empty.dropLast(1).all { it.radios.isEmpty() })
        assertNull(empty.last().kind)
        val only = radio("u")
        val slices = ClassOutline.of(listOf(only), classBy)
        assertEquals(SignatureClass.visible.size + 1, slices.size)
        assertEquals(1, slices.last().radios.size)
        assertEquals(0, ClassOutline.multiClassCount(listOf(only), classBy))
    }

    @Test
    fun preservesLiveOrderInsideBuckets() {
        val loud = radio("z", "fleet-gopro")
        val quiet = radio("a", "fleet-gopro")
        val slices = ClassOutline.of(listOf(loud, quiet), classBy)
        val cameras = slices.first { it.kind == SignatureClass.CAMERA }
        assertEquals(listOf("z", "a"), cameras.radios.map { it.key })
        assertEquals(listOf("z", "a"), cameras.signatures.single().radios.map { it.key })
    }

    @Test
    fun signaturesAreAlphabeticalByNameNotCount() {
        val names = mapOf(
            "fleet-gopro" to "Zed",
            "fleet-osmo" to "Alpha",
            "fleet-tapo" to "Mike",
        )
        val slices = ClassOutline.of(
            listOf(
                radio("1", "fleet-gopro"),
                radio("2", "fleet-gopro"),
                radio("3", "fleet-gopro"),
                radio("4", "fleet-osmo"),
                radio("5", "fleet-tapo"),
            ),
            classBy,
            names,
        )
        val cameras = slices.first { it.kind == SignatureClass.CAMERA }
        assertEquals(
            listOf("fleet-osmo", "fleet-tapo", "fleet-gopro"),
            cameras.signatures.map { it.fleetId },
        )
    }

    @Test
    fun classesAreAlphabeticalByLabelUnmatchedLast() {
        val slices = ClassOutline.of(emptyList(), classBy)
        val labels = slices.dropLast(1).map { it.label() }
        assertEquals(labels.sortedBy { it.lowercase() }, labels)
        assertEquals("Audio", labels.first())
        assertEquals("Wearables", labels.last())
        assertEquals("Unmatched", slices.last().label())
        assertTrue(labels.none { it == "Body-worn" })
    }

    @Test
    fun bodywornRadiosSitInWearables() {
        val classBy = mapOf("fleet-old" to SignatureClass.BODYWORN)
        val slices = ClassOutline.of(listOf(radio("w", "fleet-old")), classBy)
        assertTrue(slices.none { it.kind == SignatureClass.BODYWORN })
        val wear = slices.first { it.kind == SignatureClass.WEARABLE }
        assertEquals(1, wear.radios.size)
        val reveal = ClassOutline.reveal(radio("w", "fleet-old"), classBy)
        assertEquals(setOf("WEARABLE"), reveal.classIds)
    }

    @Test
    fun viewModeLabelExists() {
        assertEquals("By class", ViewMode.BY_CLASS.label())
        assertEquals(5, ViewMode.entries.size)
    }

    @Test
    fun revealOpensClassAndSignature() {
        val r = ClassOutline.reveal(radio("a", "fleet-gopro"), classBy)
        assertEquals(setOf("CAMERA"), r.classIds)
        assertEquals(setOf("CAMERA/fleet-gopro"), r.sigKeys)
    }

    @Test
    fun revealUnmatchedOpensUnmatchedOnly() {
        val r = ClassOutline.reveal(radio("u"), classBy)
        assertEquals(setOf("unmatched"), r.classIds)
        assertTrue(r.sigKeys.isEmpty())
    }

    @Test
    fun revealDualChipOpensEachClass() {
        val r = ClassOutline.reveal(radio("x", "fleet-tapo", "fleet-tplink"), classBy)
        assertEquals(setOf("CAMERA", "ISP"), r.classIds)
        assertEquals(setOf("CAMERA/fleet-tapo", "ISP/fleet-tplink"), r.sigKeys)
    }

    @Test
    fun radioItemIndexNullWhenBranchClosed() {
        val gopro = radio("z", "fleet-gopro")
        val slices = ClassOutline.of(listOf(gopro), classBy, mapOf("fleet-gopro" to "GoPro"))
        assertNull(ClassOutline.radioItemIndex(slices, emptySet(), emptySet(), "z"))
        assertNull(ClassOutline.radioItemIndex(slices, setOf("CAMERA"), emptySet(), "z"))
    }

    @Test
    fun radioItemIndexFindsOpenedMatchedRow() {
        val gopro = radio("z", "fleet-gopro")
        val slices = ClassOutline.of(listOf(gopro), classBy, mapOf("fleet-gopro" to "GoPro"))
        val beforeCameras = SignatureClass.visible.count { it.label().lowercase() < "cameras" }
        // summary + closed class headers before Cameras + Cameras header + signature header + radio
        val expected = 1 + beforeCameras + 1 + 1
        assertEquals(
            expected,
            ClassOutline.radioItemIndex(
                slices,
                setOf("CAMERA"),
                setOf("CAMERA/fleet-gopro"),
                "z",
            ),
        )
        val snap = ClassOutline.outlineSnap(
            slices,
            setOf("CAMERA"),
            setOf("CAMERA/fleet-gopro"),
            "z",
        )
        assertEquals(1 + beforeCameras, snap!!.classIndex)
        assertEquals(1 + beforeCameras + 1, snap.signatureIndex)
        assertEquals(expected, snap.radioIndex)
    }

    @Test
    fun radioItemIndexFindsOpenedUnmatchedRow() {
        val unnamed = radio("u")
        val slices = ClassOutline.of(listOf(unnamed), classBy)
        val expected = 1 + SignatureClass.visible.size + 1
        assertEquals(
            expected,
            ClassOutline.radioItemIndex(slices, setOf("unmatched"), emptySet(), "u"),
        )
        val snap = ClassOutline.outlineSnap(slices, setOf("unmatched"), emptySet(), "u")
        assertEquals(1 + SignatureClass.visible.size, snap!!.classIndex)
        assertEquals(null, snap.signatureIndex)
        assertEquals(expected, snap.radioIndex)
    }

    @Test
    fun outlineSnapLeadingItemsSkipsEmptyWhenFiltered() {
        val gopro = radio("z", "fleet-gopro")
        val slices = ClassOutline.of(listOf(gopro), classBy, mapOf("fleet-gopro" to "GoPro"))
        val visible = slices.filter { it.radios.isNotEmpty() }
        val snap = ClassOutline.outlineSnap(
            visible,
            setOf("CAMERA"),
            setOf("CAMERA/fleet-gopro"),
            "z",
            leadingItems = 2,
        )
        assertEquals(2, snap!!.classIndex)
        assertEquals(3, snap.signatureIndex)
        assertEquals(4, snap.radioIndex)
    }

    @Test
    fun outlineSnapNullWhenBranchClosed() {
        val gopro = radio("z", "fleet-gopro")
        val slices = ClassOutline.of(listOf(gopro), classBy, mapOf("fleet-gopro" to "GoPro"))
        assertEquals(null, ClassOutline.outlineSnap(slices, emptySet(), emptySet(), "z"))
        assertEquals(
            null,
            ClassOutline.outlineSnap(slices, setOf("CAMERA"), emptySet(), "z"),
        )
    }

    private fun radio(key: String, vararg fleets: String) = Sighting(
        key = key,
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:${key.take(2).uppercase().padStart(2, '0')}",
        name = key,
        rssi = -40,
        rssiMin = -40,
        rssiMax = -40,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = false,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = null,
        manufacturerDataHex = "",
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = fleets.toSet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
    )
}
