package app.fieldwatch.i18n

import app.fieldwatch.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationTest {
    private val english = strings("values")
    private val chinese = strings("values-b+zh+Hans")

    @After
    fun restoreEnglishRenderer() {
        TextRuntime.renderer = null
        TextRuntime.localeProvider = { Locale.US }
    }

    @Test
    fun chineseCoversEveryTranslatableResourceAndPreservesFormatArguments() {
        assertEquals(english.keys - "app_name", chinese.keys)
        val plain = unformatted("values")
        for ((key, value) in chinese) {
            assertTrue("Empty translation: $key", value.isNotBlank())
            // Android's formatted=false strings can contain literal percentages.
            if (key !in plain) {
                assertEquals("Parameters: $key", parameters(english.getValue(key)), parameters(value))
                formatExample(value)
            }
        }
    }

    @Test
    fun chineseQuantityResourcesPreserveCountsIncludingZero() {
        fun quantities(folder: String): Map<String, String> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/$folder/plurals.xml"))
            val nodes = doc.getElementsByTagName("plurals")
            return (0 until nodes.length).associate { index ->
                val node = nodes.item(index) as org.w3c.dom.Element
                val other = (0 until node.getElementsByTagName("item").length).map {
                    node.getElementsByTagName("item").item(it) as org.w3c.dom.Element
                }.first { it.getAttribute("quantity") == "other" }
                node.getAttribute("name") to other.textContent
            }
        }
        val en = quantities("values")
        val zh = quantities("values-b+zh+Hans")
        assertEquals(en.keys, zh.keys)
        zh.forEach { (key, pattern) ->
            assertEquals(parameters(en.getValue(key)), parameters(pattern))
            listOf(0, 1, 2).forEach { count ->
                assertTrue(String.format(Locale.SIMPLIFIED_CHINESE, pattern, count).contains(count.toString()))
            }
        }
    }

    @Test
    fun translatedGuessDoesNotTurnGenericAdvertiserIntoAClassifiedDevice() {
        installChinese()
        val generic = Sighting(
            key = "BLE:AA:BB:CC:DD:EE:FF", kind = RadioKind.BLE, mac = "AA:BB:CC:DD:EE:FF",
            name = "", rssi = -45, rssiMin = -45, rssiMax = -45, channel = 0, frequencyMhz = 0,
            vendor = null, randomized = false, hiddenSsid = false, serviceUuids = emptyList(),
            manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "",
            firstSeen = 1L, lastSeen = 1L, hitCount = 1, fleetIds = emptySet(),
            rssiHistory = emptyList(), presence = emptyList(),
        )
        val guessed = DeviceExplain.guess(generic, emptyList())
        assertEquals("蓝牙低功耗广播设备", guessed.headline)
        assertNull(DeviceExplain.listLabel(generic))
        val tag = DeviceExplain.guess(generic, listOf("Apple AirTags"))
        assertTrue(tag.headline.contains("标签"))
        assertNotNull(DeviceExplain.listLabel(generic, listOf("Apple AirTags")))
    }

    @Test
    fun catalogOverlayNeverChangesUserFieldsOrSerializedStockRules() {
        val original = DefaultCatalog.fleets().first()
        val rulePack = Json.encodeToString(SignaturePack(fleets = listOf(original)))
        TextRuntime.renderer = { key, fallback, _ -> if (key.startsWith("catalog_")) "译文" else fallback }
        assertEquals("译文", original.displayName())
        assertEquals("用户命名", original.copy(name = "用户命名").displayName())
        assertEquals("用户备注", original.copy(notes = "用户备注").displayNotes())
        assertEquals(original.name, original.copy(builtIn = false).displayName())
        assertEquals(rulePack, Json.encodeToString(SignaturePack(fleets = listOf(original))))
    }

    @Test
    fun englishRemainsTheDomainFallbackWithoutAnAndroidRuntime() {
        assertEquals("Most likely", localized("missing_key", "Most likely"))
        assertEquals("75%", localized("missing_format", "%1\$s%%", 75))
        assertEquals("Finder tags", SignatureClass.FINDER.displayLabel())
    }

    @Test
    fun everyDomainTextKeyIsBoundToAnAndroidResource() {
        val root = File("src/main/java/app/fieldwatch")
        val adapter = File(root, "i18n/TextResources.kt").readText()
        val referenced = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            Regex("localized\\(\"([a-z0-9_]+)\"").findAll(file.readText()).map { it.groupValues[1] }
        }.toSet()
        val bound = Regex("\"([a-z0-9_]+)\" to R\\.string\\.").findAll(adapter).map { it.groupValues[1] }.toSet()
        assertEquals("Unbound domain keys", emptySet<String>(), referenced - bound)
        assertTrue(english.keys.containsAll(bound))
    }

    @Test
    fun stockFieldsHaveResourcesButUserDecodeValuesStayUntouched() {
        for (fleet in DefaultCatalog.fleets()) {
            val base = "catalog_${fleet.id.replace('-', '_')}"
            assertTrue(english.containsKey("${base}_name"))
            if (fleet.notes.isNotEmpty()) assertTrue(english.containsKey("${base}_notes"))
            if (fleet.attentionNote.isNotEmpty()) assertTrue(english.containsKey("${base}_attention"))
            fleet.decode?.fields?.forEach { field ->
                assertTrue(english.containsKey("${base}_decode_${field.id.replace('-', '_')}_label"))
            }
        }
        installChinese()
        val fleet = DefaultCatalog.fleets().first { it.id == "fleet-dult" }
        val row = DecodedFieldValue(fleet.id, fleet.name, "mode", "Mode", "separated", 1, 1,
            note = fleet.decode!!.fields.first { it.id == "mode" }.enumNotes!!.getValue("0"))
        val translated = CatalogText.decoded(row, listOf(fleet))
        assertEquals("模式", translated.label)
        assertEquals("离开主人", translated.display)
        assertTrue(translated.note.contains("约一天"))
        val edited = fleet.copy(decode = fleet.decode!!.copy(fields = fleet.decode!!.fields.map {
            if (it.id == "mode") it.copy(label = "自定义字段", enumLabels = mapOf("0" to "自定义状态")) else it
        }))
        val customRow = row.copy(label = "自定义字段", display = "自定义状态")
        assertEquals("自定义字段", CatalogText.decoded(customRow, listOf(edited)).label)
        assertEquals("自定义状态", CatalogText.decoded(customRow, listOf(edited)).display)
        assertEquals(row, CatalogText.decoded(row, listOf(fleet.copy(builtIn = false))))
        val field = fleet.decode!!.fields.first { it.id == "mode" }
        val saved = Json.encodeToString(fleet)
        assertEquals("模式", CatalogText.decodeField(fleet, field, "label", field.label))
        assertEquals("离开主人", CatalogText.decodeField(fleet, field, "enum", field.enumLabels!!.getValue("0"), "0"))
        assertEquals("用户状态", CatalogText.decodeField(fleet, field, "enum", "用户状态", "0"))
        assertEquals(field.label, CatalogText.decodeField(fleet.copy(builtIn = false), field, "label", field.label))
        assertEquals(saved, Json.encodeToString(fleet))
    }

    @Test
    fun matchingAndSavedRulesAreIdenticalAcrossLanguages() {
        val fleets = DefaultCatalog.fleets()
        val device = Sighting(
            key = "BLE:00:00:00:00:00:01", kind = RadioKind.BLE, mac = "00:00:00:00:00:01",
            name = "AirTag", rssi = -45, rssiMin = -45, rssiMax = -45, channel = 0, frequencyMhz = 0,
            vendor = null, randomized = false, hiddenSsid = false, serviceUuids = emptyList(),
            manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "",
            firstSeen = 1L, lastSeen = 1L, hitCount = 1, fleetIds = emptySet(),
            rssiHistory = emptyList(), presence = emptyList(),
        )
        val matcher = SignatureEngine()
        val hits = matcher.match(listOf(device), fleets, now = 2L)
        val saved = SignatureExchange.encode(SignaturePack(fleets = fleets))
        val confidence = DeviceExplain.guess(device, listOf("Apple AirTags")).confidence
        installChinese()
        fleets.forEach { it.displayName(); it.displayNotes(); it.displayAttention() }
        assertEquals(hits, matcher.match(listOf(device), fleets, now = 2L))
        assertEquals(saved, SignatureExchange.encode(SignaturePack(fleets = fleets)))
        assertEquals(confidence, DeviceExplain.guess(device, listOf("Apple AirTags")).confidence)
        assertEquals(fleets, SignatureExchange.parse(saved).fleets)
    }

    private fun installChinese() {
        TextRuntime.renderer = { key, fallback, args ->
            val pattern = chinese[key] ?: fallback
            if (args.isEmpty()) pattern else String.format(Locale.SIMPLIFIED_CHINESE, pattern, *args)
        }
    }

    @Test
    fun codTranslationPreservesCanonicalInferenceAndUnknownCodes() {
        val raw = CodDecoder.decode(0x020C)
        assertEquals("Phone", raw.major)
        assertEquals("Smartphone", raw.minor)
        installChinese()
        assertEquals("电话 / 智能手机", raw.displaySummary())
        assertEquals("Phone / Smartphone", raw.summary())
        assertFalse(codLabel("Keyboard + Pointing").contains("Keyboard"))
        assertTrue(codLabel("Major 0x1A").contains("0x1A"))
        assertEquals("User-provided unknown label", codLabel("User-provided unknown label"))
    }

    @Test
    fun runtimeDefaultsFollowLanguageWithoutChangingSavedNames() {
        assertEquals("off", DebriefPlaces.Off.note)
        assertEquals("1 h 2 min", Sit.fmtDuration(3_720_000))
        installChinese()
        TextRuntime.localeProvider = { Locale.SIMPLIFIED_CHINESE }
        assertEquals(chinese.getValue("debrief_report_off"), DebriefPlaces.Off.note)
        assertFalse(Sit.fmtDuration(3_720_000).contains("h"))
        assertTrue(Sit.defaultName(1_728_000_000_000).contains("月"))
    }

    private fun unformatted(folder: String): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$folder/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            .filter { it.getAttribute("formatted") == "false" }
            .map { it.getAttribute("name") }.toSet()
    }

    private fun strings(folder: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$folder/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent.removeSurrounding("\"")
                .replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t")
        }
    }

    private fun parameters(pattern: String): Map<Int, Char> {
        var implicit = 0
        return Regex("%(?:(\\d+)\\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])")
            .findAll(pattern).filter { it.groupValues[2] != "%" }.associate {
                (it.groupValues[1].toIntOrNull() ?: ++implicit) to it.groupValues[2].single()
            }
    }

    private fun formatExample(pattern: String) {
        val parameters = parameters(pattern)
        if (parameters.isEmpty()) return
        val args = (1..parameters.keys.max()).map { index ->
            when (parameters[index]?.lowercaseChar()) {
                'd', 'x', 'o' -> 42
                'f', 'e', 'g', 'a' -> 1.25
                else -> "示例"
            }
        }.toTypedArray()
        String.format(Locale.SIMPLIFIED_CHINESE, pattern, *args)
    }
}
