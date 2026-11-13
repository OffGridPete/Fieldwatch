package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterworkingTest {
    private fun body(vararg data: Int) = data.map { it.toByte() }.toByteArray()

    @Test
    fun chargeablePoliceStationHasNoInternet() {
        val iw = Interworking.parse(body(0x02, 0x02, 0x04))!!
        assertEquals(2, iw.accessType)
        assertFalse(iw.internet)
        assertFalse(iw.emergencyReachable)
        assertEquals(2, iw.venueGroup)
        assertEquals(4, iw.venueType)
        assertNull(iw.hessid)
        val lines = iw.lines(false)
        assertEquals("Chargeable public network", lines[0].second)
        assertEquals("No", lines[1].second)
        assertEquals("Business · Police Station", lines.first { it.first == "Venue" }.second)
        assertFalse(lines.any { it.first == "Can reach emergency services" })
        assertNull(iw.vehicleSentence())
    }

    @Test
    fun freePublicVehicleKeepsHotspotId() {
        val iw = Interworking.parse(body(0x13, 0x0A, 0x01, 0x02, 0x00, 0x00, 0x00, 0x00, 0x01))!!
        assertEquals(3, iw.accessType)
        assertTrue(iw.internet)
        assertFalse(iw.emergencyReachable)
        assertEquals(10, iw.venueGroup)
        assertEquals(1, iw.venueType)
        assertEquals("02:00:00:00:00:01", iw.hessid)
        val lines = iw.lines(false)
        assertEquals("Free public network", lines[0].second)
        assertEquals("Yes", lines[1].second)
        assertEquals("Vehicular · Automobile or Truck", lines.first { it.first == "Venue" }.second)
        assertEquals("02:00:00:00:00:01", lines.first { it.first == "Hotspot ID" }.second)
        assertEquals("02:00:00:**:**:**", iw.lines(true).first { it.first == "Hotspot ID" }.second)
        assertEquals("It says it is in a vehicle (Automobile or Truck).", iw.vehicleSentence())
    }

    @Test
    fun oneByteShowsNetworkAndInternetOnly() {
        val iw = Interworking.parse(body(0x03))!!
        assertEquals(3, iw.accessType)
        assertFalse(iw.internet)
        assertNull(iw.venueGroup)
        assertNull(iw.hessid)
        val labels = iw.lines(false).map { it.first }
        assertEquals(listOf("Network type", "Internet"), labels)
    }

    @Test
    fun emergencyRowOnlyWhenTheBitIsSet() {
        val on = Interworking.parse(body(0x45))!!
        assertEquals(5, on.accessType)
        assertFalse(on.internet)
        assertTrue(on.emergencyReachable)
        assertEquals("Yes", on.lines(false).first { it.first == "Can reach emergency services" }.second)
        val off = Interworking.parse(body(0x05))!!
        assertEquals("Emergency services only network", off.lines(false)[0].second)
        assertFalse(off.lines(false).any { it.first == "Can reach emergency services" })
    }

    @Test
    fun twoByteBodyIsIgnored() {
        assertNull(Interworking.parse(body(0x02, 0x04)))
        assertNull(Interworking.parse(body()))
    }

    @Test
    fun shorterLaterBeaconKeepsVenueAndHotspotId() {
        val rich = Interworking.parse(body(0x13, 0x0A, 0x01, 0x02, 0x00, 0x00, 0x00, 0x00, 0x01))
        val short = Interworking.parse(body(0x03))
        val merged = Interworking.merge(rich, short)!!
        assertEquals(3, merged.accessType)
        assertFalse(merged.internet)
        assertEquals(10, merged.venueGroup)
        assertEquals(1, merged.venueType)
        assertEquals("02:00:00:00:00:01", merged.hessid)
        assertEquals(rich, Interworking.merge(rich, null))
        val kept = RadioFacts(interworking = rich).merge(RadioFacts())
        assertEquals(rich, kept.interworking)
    }

    @Test
    fun explicitUnspecifiedVenueReplacesThenHides() {
        val police = Interworking.parse(body(0x02, 0x02, 0x04))
        val none = Interworking.parse(body(0x02, 0x00, 0x00))
        val merged = Interworking.merge(police, none)!!
        assertEquals(0, merged.venueGroup)
        assertEquals(0, merged.venueType)
        assertFalse(merged.lines(false).any { it.first == "Venue" })
    }

    @Test
    fun businessTypeFiveStaysUnnamed() {
        assertEquals("Post Office", Interworking.typeName(2, 6))
        assertEquals("type 5", Interworking.typeName(2, 5))
        assertEquals("Unspecified", Interworking.typeName(10, 0))
        assertEquals("group 12", Interworking.groupName(12))
    }
}
