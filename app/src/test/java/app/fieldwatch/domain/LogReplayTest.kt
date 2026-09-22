package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogReplayTest {
    private val csvHeader =
        "timestamp,iso,kind,mac,name,rssi,channel,freq,oui,vendor,fleets,mfg,uuids,flags,raw,lat,lon,vendor_ie\n"

    @Test
    fun csvRowsMergeToOneRadio() {
        val log = csvHeader +
            "1000,iso,BLE,aa:bb:cc:dd:ee:01,Tag,-60,0,0,,Acme,,4C,,,AB12,,,\n" +
            "2000,iso,BLE,AA:BB:CC:DD:EE:01,Tag,-55,0,0,,Acme,,4C,,,AB12,,,\n"
        val radios = LogReplay.parse(log)
        assertEquals(1, radios.size)
        val r = radios[0]
        assertEquals("AA:BB:CC:DD:EE:01", r.mac)
        assertEquals("Tag", r.name)
        assertEquals(-55, r.rssi) // last row wins
        assertEquals(1000L, r.firstSeen)
        assertEquals(2000L, r.lastSeen)
        assertEquals(2, r.hits)
        assertEquals(0x4C, r.manufacturerId)
        assertEquals("AB12", r.manufacturerDataHex)
    }

    @Test
    fun csvWithoutHeaderUsesDefaultLayout() {
        // A rotated part or a hand-cut excerpt has no header line.
        val log = "3000,iso,WIFI,00:11:22:33:44:55,CafeWiFi,-70,6,2437,,Cisco,,,,,,,\n"
        val radios = LogReplay.parse(log)
        assertEquals(1, radios.size)
        assertEquals(RadioKind.WIFI, radios[0].kind)
        assertEquals("CafeWiFi", radios[0].name)
        assertEquals(3000L, radios[0].firstSeen)
    }

    @Test
    fun jsonLinesParseAndMerge() {
        val log = """
            {"ts":500,"kind":"BLE","mac":"aa:bb:cc:dd:ee:02","name":"Buds","rssi":-48,"mfg":76,"rand":true}
            {"ts":900,"kind":"BLE","mac":"AA:BB:CC:DD:EE:02","rssi":-40,"uuids":"FE2C"}
        """.trimIndent()
        val radios = LogReplay.parse(log)
        assertEquals(1, radios.size)
        val r = radios[0]
        assertEquals("Buds", r.name)
        assertEquals(-40, r.rssi)
        assertEquals(listOf("FE2C"), r.serviceUuids)
        assertTrue(r.randomized)
        assertEquals(2, r.hits)
    }

    @Test
    fun junkLinesAreSkipped() {
        val log = csvHeader +
            "\n" +
            "garbage line with no commas that parses to nothing\n" +
            "1500,iso,NOPE,AA:BB:CC:DD:EE:03,X,-1,0,0,,,,,,,,,\n" +
            "1600,iso,BLE,AA:BB:CC:DD:EE:03,Real,-70,0,0,,,,,,,,,\n"
        val radios = LogReplay.parse(log)
        assertEquals(1, radios.size)
        assertEquals("Real", radios[0].name)
    }

    @Test
    fun flagsAndHiddenSurviveReplay() {
        val log = csvHeader +
            "1000,iso,WIFI,02:11:22:33:44:55,,-80,0,0,,,,,,RAND HIDDEN,,,,\n"
        val r = LogReplay.parse(log).single()
        assertTrue(r.randomized)
        assertTrue(r.hiddenSsid)
    }

    @Test
    fun emptyInputGivesEmptyList() {
        assertTrue(LogReplay.parse("").isEmpty())
        assertTrue(LogReplay.parse("\n\n").isEmpty())
    }

    @Test
    fun mfgFieldParsesHexFirst() {
        // "mfg" is written hex by the logger ("4C"); a bare digit string is still hex-parsed.
        val log = csvHeader +
            "1000,iso,BLE,AA:BB:CC:DD:EE:04,X,-50,0,0,,,,76,,,,,\n" +
            "2000,iso,BLE,AA:BB:CC:DD:EE:05,Y,-50,0,0,,,,4C,,,,,\n"
        val radios = LogReplay.parse(log).associateBy { it.mac }
        assertEquals(0x76, radios.getValue("AA:BB:CC:DD:EE:04").manufacturerId)
        assertEquals(0x4C, radios.getValue("AA:BB:CC:DD:EE:05").manufacturerId)
    }

    @Test
    fun nullJsonMfgStaysNull() {
        val log = """{"ts":1,"kind":"BLE","mac":"AA:BB:CC:DD:EE:06","mfg":null}"""
        assertNull(LogReplay.parse(log).single().manufacturerId)
    }

    @Test
    fun csvRowsKeepPositionChannelAndSecurity() {
        // New-format rows: "sec" is the trailing column added after vendor_ie.
        val log = csvHeader.trimEnd('\n') + ",sec\n" +
            "1000,iso,WIFI,00:11:22:33:44:55,CafeWiFi,-70,6,2437,,Cisco,,,,,,48.8566,2.3522,,RSN PSK CCMP\n" +
            "2000,iso,WIFI,00:11:22:33:44:55,,,,,,,,,,,,,,\n"
        val r = LogReplay.parse(log).single()
        assertEquals(48.8566, r.latitude!!, 0.0001)
        assertEquals(2.3522, r.longitude!!, 0.0001)
        assertEquals(6, r.channel)
        assertEquals(2437, r.frequencyMhz)
        assertEquals("RSN PSK CCMP", r.security)
        assertTrue(r.hasPosition)
    }

    @Test
    fun jsonRowsKeepPositionAndSecurity() {
        val log = """
            {"ts":500,"kind":"WIFI","mac":"00:11:22:33:44:66","rssi":-60,"channel":11,"lat":48.85,"lon":2.35,"sec":"RSN SAE CCMP"}
            {"ts":900,"kind":"WIFI","mac":"00:11:22:33:44:66","lat":null,"lon":null}
        """.trimIndent()
        val r = LogReplay.parse(log).single()
        assertEquals(48.85, r.latitude!!, 0.001)
        assertEquals(2.35, r.longitude!!, 0.001)
        assertEquals(11, r.channel)
        assertEquals("RSN SAE CCMP", r.security)
        assertEquals(2, r.hits)
    }

    @Test
    fun toSightingCarriesRadioFields() {
        val r = LogReplay.parse(
            csvHeader +
                "1000,iso,WIFI,00:11:22:33:44:77,AP,-70,36,5180,,Acme,,,,,,48.1,2.1,,\n",
        ).single()
        val s = r.toSighting()
        assertEquals(r.key, s.key)
        assertEquals(36, s.channel)
        assertEquals(5180, s.frequencyMhz)
        assertEquals(48.1, s.latitude!!, 0.001)
        assertEquals(r.rssi, s.rssiMin)
        assertEquals(r.rssi, s.rssiMax)
        assertEquals(r.hits, s.hitCount)
    }
}
