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
    fun csvKeepsLastGpsFixAndChannel() {
        fun row(
            rssi: String,
            ch: String,
            freq: String,
            lat: String,
            lon: String,
        ) = listOf(
            "2000", "iso", "WIFI", "00:11:22:33:44:55", "Cafe", rssi, ch, freq,
            "", "Acme", "", "", "", "", "", lat, lon, "",
        ).joinToString(",")
        val log = csvHeader +
            row("-80", "1", "2412", "", "") + "\n" +
            row("-70", "6", "2437", "37.5", "-122.1") + "\n"
        val r = LogReplay.parse(log).single()
        assertEquals(6, r.channel)
        assertEquals(2437, r.frequencyMhz)
        assertEquals(37.5, r.latitude!!, 0.0001)
        assertEquals(-122.1, r.longitude!!, 0.0001)
        assertTrue(r.hasPosition)
    }

    @Test
    fun lineKindReadsCsvAndJson() {
        val csv = listOf(
            "2000", "iso", "BLE", "AA:BB:CC:DD:EE:01", "Tag", "-50", "0", "0",
            "", "", "", "", "", "", "", "", "", "",
        ).joinToString(",")
        assertEquals(RadioKind.BLE, LogReplay.lineKind(csv, json = false))
        assertEquals(RadioKind.WIFI, LogReplay.lineKind("""{"kind":"WIFI","mac":"00:11:22:33:44:55"}""", json = true))
        assertTrue(LogExportRadios.WIFI.matches(RadioKind.WIFI))
        assertTrue(!LogExportRadios.WIFI.matches(RadioKind.BLE))
        assertTrue(LogExportRadios.BOTH.matches(RadioKind.BLE))
    }

    @Test
    fun csvRoundTripToJsonlKeepsKindMacAndGps() {
        val csv = listOf(
            "2000", "iso", "WIFI", "00:11:22:33:44:55", "Cafe", "-70", "6", "2437",
            "", "Acme", "", "4C", "", "RAND", "AB", "37.5", "-122.1", "",
        ).joinToString(",")
        val json = LogReplay.csvRowToJson(csv)!!
        assertTrue(json.contains("\"kind\":\"WIFI\""))
        assertTrue(json.contains("\"mac\":\"00:11:22:33:44:55\""))
        assertTrue(json.contains("\"rand\":true"))
        val back = LogReplay.jsonRowToCsv(json)!!
        val cols = back.split(',')
        assertEquals("WIFI", cols[2])
        assertEquals("00:11:22:33:44:55", cols[3])
        assertEquals("37.500000", cols[15])
        assertEquals("-122.100000", cols[16])
        assertTrue(cols[13].contains("RAND"))
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
}
