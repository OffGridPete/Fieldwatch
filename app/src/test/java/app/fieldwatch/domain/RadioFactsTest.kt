package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun laterShorterHomeKitReplacesThePairedBitAndAShortPacketDoesNot() {
        val lock = "0611" + "00AAAAAAAAAAAA060001000102DEADBEEF"
        val camera = "060D" + "01222022F38F011100FA920202"
        val paired = AdvPayloadDecoder.homekitFacts(listOf(MfgRecord(0x004C, lock)))!!
        val unpaired = AdvPayloadDecoder.homekitFacts(listOf(MfgRecord(0x004C, camera)))!!
        assertTrue(paired.paired)
        assertFalse(unpaired.paired)
        val stored = RadioFacts(mfgRecords = listOf(MfgRecord(0x004C, lock)), homekit = paired)
        val heard = RadioFacts(mfgRecords = listOf(MfgRecord(0x004C, camera)), homekit = unpaired)
        val merged = stored.merge(heard)
        assertEquals(unpaired, merged.homekit)
        assertEquals(lock, merged.mfgRecords.single().dataHex)

        val short = RadioFacts(mfgRecords = listOf(MfgRecord(0x004C, "06080102030405060708")))
        assertNull(short.homekit)
        assertEquals(unpaired, merged.merge(short).homekit)
    }

    @Test
    fun laterShorterMatterReplacesTheCommissionableBlock() {
        val setup = "00AB03F1FF008001"
        val commissionable = Matter.read(listOf(ServiceDataRecord("FFF6", setup)))!!
        val opcode = Matter.read(listOf(ServiceDataRecord("FFF6", "01")))!!
        assertTrue(commissionable.commissionable)
        assertFalse(opcode.commissionable)
        val stored = RadioFacts(
            serviceData = listOf(ServiceDataRecord("FFF6", setup)),
            matter = commissionable,
        )
        val heard = RadioFacts(
            serviceData = listOf(ServiceDataRecord("FFF6", "01")),
            matter = opcode,
        )
        val merged = stored.merge(heard)
        assertEquals(opcode, merged.matter)
        assertEquals(setup, merged.serviceData.single().dataHex)
        assertEquals(opcode, merged.merge(RadioFacts()).matter)
    }

    @Test
    fun laterShorterSidewalkReplacesTheRegistrationBlock() {
        val registration = Sidewalk.read(listOf(MfgRecord(0x0171, "0433DEAD")), listOf("03FE"))!!
        val presence = Sidewalk.read(listOf(MfgRecord(0x0171, "2121")), listOf("03FE"))!!
        val stored = RadioFacts(
            mfgRecords = listOf(MfgRecord(0x0171, "0433DEAD")),
            sidewalk = registration,
        )
        val heard = RadioFacts(
            mfgRecords = listOf(MfgRecord(0x0171, "2121")),
            sidewalk = presence,
        )
        val merged = stored.merge(heard)
        assertEquals(presence, merged.sidewalk)
        // Different application-id prefixes stay as two records. The longer hex is not the facts.
        assertEquals(listOf("0433DEAD", "2121"), merged.mfgRecords.map { it.dataHex })
        val fragment = RadioFacts(sidewalk = Sidewalk.read(listOf(MfgRecord(0x0171, "04")), listOf("03FE")))
        assertNull(fragment.sidewalk)
        assertEquals(presence, merged.merge(fragment).sidewalk)
        assertEquals(presence, merged.merge(RadioFacts()).sidewalk)
    }

    @Test
    fun laterShorterBthomeKeepsTheDoorTheNewPacketOmits() {
        val door = Bthome.read(listOf(ServiceDataRecord("FCD2", "401A01")))!!
        val temp = Bthome.read(listOf(ServiceDataRecord("FCD2", "4002C409")))!!
        val stored = RadioFacts(
            serviceData = listOf(ServiceDataRecord("FCD2", "401A01")),
            bthome = door,
        )
        val heard = RadioFacts(
            serviceData = listOf(ServiceDataRecord("FCD2", "4002C409")),
            bthome = temp,
        )
        val merged = stored.merge(heard)
        assertEquals("4002C409", merged.serviceData.single().dataHex)
        assertEquals(listOf(0x02, 0x1A), merged.bthome!!.objects.map { it.id })
        assertEquals("Open", merged.bthome.chip()!!.text)

        val encrypted = Bthome.read(listOf(ServiceDataRecord("FCD2", "41")))!!
        val hidden = merged.merge(
            RadioFacts(serviceData = listOf(ServiceDataRecord("FCD2", "41")), bthome = encrypted),
        )
        assertEquals("4002C409", hidden.serviceData.single().dataHex)
        assertTrue(hidden.bthome!!.hideObjects)
        assertTrue(hidden.bthome.encrypted)
        assertEquals(listOf(0x02, 0x1A), hidden.bthome.objects.map { it.id })
        assertNull(hidden.bthome.chip())

        val closed = Bthome.read(listOf(ServiceDataRecord("FCD2", "401A00")))!!
        val shut = hidden.merge(
            RadioFacts(serviceData = listOf(ServiceDataRecord("FCD2", "401A00")), bthome = closed),
        )
        assertFalse(shut.bthome!!.hideObjects)
        assertEquals(0, shut.bthome.objects.single { it.id == 0x1A }.state)
        assertEquals(1, shut.bthome.objects.count { it.id == 0x02 })
        assertNull(shut.bthome.chip())

        val empty = RadioFacts(serviceData = listOf(ServiceDataRecord("FCD2", "")))
        assertNull(empty.bthome)
        assertEquals(shut.bthome, shut.merge(empty).bthome)
        assertEquals(shut.bthome, shut.merge(RadioFacts()).bthome)
    }
}
