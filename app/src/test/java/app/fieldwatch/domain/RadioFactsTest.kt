package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioFactsTest {
    @Test
    fun connectableYesSurvivesScanResponse() {
        val adv = RadioFacts(connectable = true)
        val scanRsp = RadioFacts(connectable = false)
        assertEquals(true, adv.merge(scanRsp).connectable)
        assertEquals(true, scanRsp.merge(adv).connectable)
    }

    @Test
    fun connectableStaysNoUntilAConnectableAd() {
        val first = RadioFacts(connectable = false)
        val again = RadioFacts(connectable = false)
        assertEquals(false, first.merge(again).connectable)
    }

    @Test
    fun connectableNullDoesNotClear() {
        val known = RadioFacts(connectable = false)
        assertEquals(false, known.merge(RadioFacts()).connectable)
        assertNull(RadioFacts().merge(RadioFacts()).connectable)
    }

    @Test
    fun eddystoneFramesAccumulateInsteadOfReplacing() {
        val uid = RadioFacts(
            serviceData = listOf(ServiceDataRecord("FEAA", "00AABBCCDDEEFF00112233445566778899")),
        )
        val url = RadioFacts(
            serviceData = listOf(ServiceDataRecord("FEAA", "1001676F6F676C6507")),
        )
        val tlm = RadioFacts(
            serviceData = listOf(ServiceDataRecord("0000FEAA-0000-1000-8000-00805F9B34FB", "2000ABCD")),
        )
        val merged = uid.merge(url).merge(tlm)
        assertEquals(3, merged.serviceData.size)
        assertEquals(setOf("00", "10", "20"), merged.serviceData.map { it.dataHex.take(2) }.toSet())
    }

    @Test
    fun otherServiceDataStillReplacesByUuid() {
        val first = RadioFacts(serviceData = listOf(ServiceDataRecord("FE2C", "AA")))
        val second = RadioFacts(serviceData = listOf(ServiceDataRecord("FE2C", "BBCC")))
        val merged = first.merge(second)
        assertEquals(1, merged.serviceData.size)
        assertEquals("BBCC", merged.serviceData.single().dataHex)
    }
}
