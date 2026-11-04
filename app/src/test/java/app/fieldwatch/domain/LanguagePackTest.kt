package app.fieldwatch.domain

import app.fieldwatch.data.DebriefPdf
import app.fieldwatch.data.LanguageAssets
import app.fieldwatch.data.LanguageSnapshot
import app.fieldwatch.data.LanguageStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LanguagePackTest {
    @After
    fun clearLanguage() {
        LanguageText.clear()
    }

    @Test
    fun noPackReturnsEnglishWithoutReadingAFile() {
        var reads = 0
        val store = LanguageStore(tempFile(), LanguageAssets {
            reads++
            null
        })
        val snap = store.load(Locale.ENGLISH)
        LanguageText.apply(snap.overlay)
        assertEquals(0, reads)
        assertTrue(snap.overlay.isEmpty())
        assertEquals("Live", LanguageText.text("tab.live"))
        assertEquals("Finder tags", SignatureClass.FINDER.label())
        assertEquals(0, reads)
    }

    @Test
    fun missingEmptyAndWhitespaceStayEnglish() {
        val blank = LanguagePack.parse(pack("tab.live" to "   ", "tab.pause" to "").toByteArray())
        assertEquals(LanguageErrors.NONE, (blank as LanguageParse.Reject).messageKey)
        assertEquals("Live", LanguageText.text("tab.live"))
        val parsed = ok(pack("tab.live" to "   ", "tab.pause" to "", "tab.filters" to "Filtres"))
        LanguageText.apply(parsed.accepted)
        assertEquals("Live", LanguageText.text("tab.live"))
        assertEquals("Pause", LanguageText.text("tab.pause"))
        assertEquals("Filtres", LanguageText.text("tab.filters"))
        assertFalse(parsed.accepted.containsKey("tab.live"))
        assertFalse(parsed.accepted.containsKey("tab.pause"))
    }

    @Test
    fun completeOlderLineIsUsed() {
        val parsed = ok(pack("tab.live" to "Direkt", writtenFor = "1.1.21"))
        assertEquals("1.1.21", parsed.writtenFor)
        LanguageText.apply(parsed.accepted)
        assertEquals("Direkt", LanguageText.text("tab.live"))
    }

    @Test
    fun placeholderMismatchStaysEnglish() {
        val parsed = ok(pack("language.confirm.change" to "changed", "tab.live" to "Direct"))
        LanguageText.apply(parsed.accepted)
        assertEquals("{n} lines will change.", LanguageText.text("language.confirm.change"))
        assertEquals("Direct", LanguageText.text("tab.live"))
        assertFalse(parsed.accepted.containsKey("language.confirm.change"))
        val onlyBad = LanguagePack.parse(pack("tab.live" to "Live {n}").toByteArray())
        assertEquals(LanguageErrors.NONE, (onlyBad as LanguageParse.Reject).messageKey)
        assertEquals("3 lignes", ok(pack("language.confirm.change" to "{n} lignes")).let {
            LanguageText.apply(it.accepted)
            LanguageText.text("language.confirm.change", mapOf("n" to "3"))
        })
    }

    @Test
    fun placeholderFillDoesNotRescanTheValue() {
        LanguageText.apply(mapOf("language.confirm.change" to "%s {n} %s"))
        val filled = LanguageText.text("language.confirm.change", mapOf("n" to "%s {n}"))
        assertEquals("%s %s {n} %s", filled)
        assertFalse(filled.contains("String.format"))
    }

    @Test
    fun missingPlaceholderValueReturnsEnglish() {
        LanguageText.apply(mapOf("language.confirm.change" to "{n} lignes"))
        assertEquals(
            "{n} lines will change.",
            LanguageText.text("language.confirm.change", emptyMap()),
        )
    }

    @Test
    fun scriptAndUrlsStayCharacters() {
        val raw = "<script>alert(1)</script> javascript: intent: file: content: https://evil.example"
        val parsed = ok(pack("language.err.not" to raw))
        LanguageText.apply(parsed.accepted)
        assertEquals(raw, LanguageText.text("language.err.not"))
        assertEquals(OfficialSentences.NOT_REVIEWED, OfficialSentences.NOT_REVIEWED)
        assertFalse(EnglishCopy.byKey.containsKey("official.not_reviewed"))
    }

    @Test
    fun officialSentencesAreNotKeys() {
        LanguageText.apply(
            mapOf(
                "official.not_reviewed" to "hidden",
                "official.ai" to "hidden",
                "official.author" to "hidden",
            ),
        )
        assertEquals(
            "Language packs are made by other people. They are not reviewed or certified by Off Grid Pete LLC.",
            OfficialSentences.NOT_REVIEWED,
        )
        assertEquals(
            "This language was translated with AI. If a line is wrong, please open a GitHub issue.",
            OfficialSentences.AI,
        )
        assertEquals(
            "To write a language file, export the English template and follow the author guide on GitHub.",
            OfficialSentences.AUTHOR_GUIDE,
        )
        assertEquals(
            "https://github.com/OffGridPete/Fieldwatch/blob/main/docs/language-pack-author-guide.md",
            OfficialSentences.AUTHOR_GUIDE_URL,
        )
        assertTrue(EnglishCopy.lines.none { it.text == OfficialSentences.NOT_REVIEWED })
        assertTrue(EnglishCopy.lines.none { it.text == OfficialSentences.AI })
        assertTrue(EnglishCopy.lines.none { it.text == OfficialSentences.AUTHOR_GUIDE })
        assertEquals("Export English template", LanguageExportCopy.BUTTON)
        assertEquals("Fieldwatch English template", LanguageExportCopy.SHARE)
        assertEquals("Could not export the English template", LanguageExportCopy.FAILED)
        assertTrue(EnglishCopy.lines.none { it.key == "language.export" || it.key == "ui.fieldwatch.english.template" || it.key == "ui.could.not.export.english" })
    }

    @Test
    fun nestedSecondDocumentAndZipAreRejected() {
        LanguageText.apply(mapOf("tab.live" to "Direkt"))
        val nested = """{"format":"fieldwatch-language","formatVersion":1,"language":"X","strings":{"tab.live":{"a":"b"}}}"""
        assertReject(LanguagePack.parse(nested.toByteArray()))
        assertEquals("Direkt", LanguageText.text("tab.live"))
        val two = """{"format":"fieldwatch-language","formatVersion":1,"language":"X","strings":{"tab.live":"Bonjour"}}{"format":"nope"}"""
        assertReject(LanguagePack.parse(two.toByteArray()))
        assertEquals("Direkt", LanguageText.text("tab.live"))
        val zip = zipBytes()
        assertReject(LanguagePack.parse(zip))
        assertEquals("Direkt", LanguageText.text("tab.live"))
    }

    @Test
    fun wrongFilesAreRejectedAndTheCurrentPackStays() {
        val store = storeWith(pack("tab.live" to "Direkt"))
        LanguageText.apply(store.current().overlay)
        assertEquals("Direkt", LanguageText.text("tab.live"))
        assertEquals(LanguageErrors.EMPTY, (LanguagePack.parse(ByteArray(0)) as LanguageParse.Reject).messageKey)
        assertEquals(LanguageErrors.NOT_PACK, (LanguagePack.parse("not json".toByteArray()) as LanguageParse.Reject).messageKey)
        assertEquals(
            LanguageErrors.SETTINGS,
            (LanguagePack.parse("""{"format":"fieldwatch-settings","formatVersion":1}""".toByteArray()) as LanguageParse.Reject).messageKey,
        )
        assertEquals(
            "That is a settings pack. Use Import settings.",
            EnglishCopy.text(LanguageErrors.SETTINGS),
        )
        assertEquals(
            LanguageErrors.SIGNATURES,
            (LanguagePack.parse("""{"format":"fieldwatch-signatures","formatVersion":1}""".toByteArray()) as LanguageParse.Reject).messageKey,
        )
        assertEquals(
            LanguageErrors.SIGNATURES,
            (LanguagePack.parse("""{"format":"spectre-signatures"}""".toByteArray()) as LanguageParse.Reject).messageKey,
        )
        assertEquals(
            "That is a signature pack. Use Import signatures.",
            EnglishCopy.text(LanguageErrors.SIGNATURES),
        )
        assertEquals(
            "Not a Fieldwatch language pack.",
            EnglishCopy.text(LanguageErrors.NOT_PACK),
        )
        val big = ByteArray(LanguagePack.MAX_BYTES + 1) { ' '.code.toByte() }
        assertEquals(LanguageErrors.LARGE, (LanguagePack.parse(big) as LanguageParse.Reject).messageKey)
        val useless = """{"format":"fieldwatch-language","formatVersion":1,"language":"X","strings":{"not.a.key":"Hi"}}"""
        assertEquals(LanguageErrors.NONE, (LanguagePack.parse(useless.toByteArray()) as LanguageParse.Reject).messageKey)
        assertEquals("Direkt", LanguageText.text("tab.live"))
        assertEquals("Direkt", store.current().overlay.getValue("tab.live"))
    }

    @Test
    fun unknownKeyAndUnknownFieldAreIgnored() {
        val raw = """{"format":"fieldwatch-language","formatVersion":1,"language":"Français","extra":"hello","strings":{"tab.live":"Direct","made.up":"nope"}}"""
        val parsed = ok(raw)
        assertEquals("Français", parsed.language)
        assertEquals(mapOf("tab.live" to "Direct"), parsed.accepted)
        assertEquals(1, parsed.ignored)
    }

    @Test
    fun newlinesAndBidi() {
        val kept = ok(pack("language.remove.body" to "Une ligne.\n\nDeux."))
        assertEquals("Une ligne.\n\nDeux.", kept.accepted.getValue("language.remove.body"))
        val dropped = ok(pack("tab.live" to "Bon\njour", "tab.pause" to "Pa\u200Euse"))
        assertFalse(dropped.accepted.containsKey("tab.live"))
        assertEquals("Pause", dropped.accepted.getValue("tab.pause"))
        assertEquals(1, dropped.ignored)
    }

    @Test
    fun confirmCountsAndNewSince() {
        val older = ok(pack("tab.live" to "Direct", writtenFor = "1.1.21"))
        assertEquals(1, older.changed)
        assertEquals(EnglishCopy.lines.size - 1, older.stayEnglish)
        assertEquals(EnglishCopy.lines.size - 1, older.newSince)
        assertFalse(older.formatNewer)
        val current = ok(pack("tab.live" to "Direct", writtenFor = "1.1.27"))
        val base = LanguagePack.parseVersion("1.1.27")!!
        val addedLater = EnglishCopy.lines.count {
            LanguagePack.compareVersion(LanguagePack.parseVersion(it.addedIn)!!, base) > 0
        }
        assertEquals(addedLater, current.newSince)
        assertTrue(current.newKeys.contains("cell.master"))
        assertFalse(current.newKeys.contains("tab.pause"))
        val since138 = ok(pack("tab.live" to "Direct", writtenFor = "1.1.38"))
        assertEquals(14, since138.newSince)
        assertTrue(since138.newKeys.contains("update.now"))
        val caughtUp = ok(pack("tab.live" to "Direct", writtenFor = "1.1.40"))
        assertEquals(0, caughtUp.newSince)
        val missing = ok(pack("tab.live" to "Direct"))
        assertNull(missing.newSince)
        val odd = ok(pack("tab.live" to "Direct", writtenFor = "v1.1.21"))
        assertNull(odd.newSince)
        assertEquals("v1.1.21", odd.writtenFor)
        val newer = ok(
            """{"format":"fieldwatch-language","formatVersion":9,"language":"X","writtenFor":"1.1.27","strings":{"tab.live":"Direct"}}""",
        )
        assertTrue(newer.formatNewer)
        assertEquals("Direct", newer.accepted.getValue("tab.live"))
    }

    @Test
    fun importReportNamesTheLinesATranslatorCanFix() {
        val parsed = ok(
            pack(
                "tab.live" to "Direct",
                "tab.filters" to "Hi {n}",
                "class.law" to "X".repeat(19),
                "language.confirm.change" to "no token",
                "tab.pause" to "Bon\njour",
                "language.cancel" to "   ",
                "made.up" to "nope",
                writtenFor = "1.1.21",
            ),
        )
        assertEquals(setOf("tab.live"), parsed.accepted.keys)
        assertEquals(6, parsed.ignored)
        assertEquals(6, parsed.issues.size)
        val tooLong = parsed.issues.filterIsInstance<LanguageIssue.TooLong>().single()
        assertEquals("class.law", tooLong.key)
        assertEquals(19, tooLong.length)
        assertEquals(18, tooLong.cap)
        val placeholders = parsed.issues.filterIsInstance<LanguageIssue.Placeholder>()
        assertEquals(setOf("tab.filters", "language.confirm.change"), placeholders.map { it.key }.toSet())
        assertEquals(setOf("n"), placeholders.first { it.key == "language.confirm.change" }.expected)
        assertTrue(placeholders.first { it.key == "tab.filters" }.expected.isEmpty())
        assertEquals(listOf("made.up"), parsed.issues.filterIsInstance<LanguageIssue.Unknown>().map { it.key })
        assertEquals(listOf("language.cancel"), parsed.issues.filterIsInstance<LanguageIssue.Blank>().map { it.key })
        assertEquals(listOf("tab.pause"), parsed.issues.filterIsInstance<LanguageIssue.Newline>().map { it.key })
        assertFalse(parsed.missingKeys.contains("class.law"))
        assertFalse(parsed.missingKeys.contains("tab.live"))
        assertTrue(parsed.missingKeys.contains("tab.settings"))
        assertEquals(parsed.newSince, parsed.newKeys.size)
        assertTrue(parsed.newKeys.contains("tab.settings"))
        assertFalse(parsed.newKeys.contains("tab.live"))
        val text = LanguagePack.report(parsed, "1.1.34")
        assertTrue(text.contains("Language: Test"))
        assertTrue(text.contains("Written for 1.1.21. This phone is 1.1.34."))
        assertTrue(text.contains("Imported: 1"))
        assertTrue(text.contains("Too long: 1"))
        assertTrue(text.contains("class.law"))
        assertTrue(text.contains("19 characters. The limit is 18."))
        assertTrue(text.contains("Keep {n}."))
        assertTrue(text.contains("Keep the same placeholders as the English line."))
        assertTrue(text.contains("made.up"))
        assertTrue(text.contains("tab.settings"))
        assertTrue(text.contains("\nImported\ntab.live\n"))
        assertFalse(text.contains("Direct"))
        assertFalse(text.contains("nope"))
    }

    @Test
    fun classChipCapAndSpokenClassCap() {
        val longChip = "X".repeat(19)
        val spoken = "Y".repeat(20)
        val parsed = ok(pack("class.law" to longChip, "speech.law" to spoken))
        assertFalse(parsed.accepted.containsKey("class.law"))
        assertEquals(spoken, parsed.accepted.getValue("speech.law"))
        LanguageText.apply(parsed.accepted)
        assertEquals("Public safety", SignatureClass.LAW_ENFORCEMENT.label())
        assertEquals(spoken, SignatureClass.LAW_ENFORCEMENT.speechLabel())
        assertTrue(spoken.length <= EnglishCopy.cap("speech.law"))
    }

    @Test
    fun capFillingPackIsAcceptedAndNotShipped() {
        val strings = LinkedHashMap<String, String>()
        for (line in EnglishCopy.lines) {
            val marks = EnglishCopy.placeholders.getValue(line.key).joinToString("") { "{$it}" }
            val pad = EnglishCopy.cap(line) - marks.length
            strings[line.key] = "é".repeat(pad) + marks
        }
        val packed = json(strings)
        val bytes = packed.toByteArray(Charsets.UTF_8)
        val parsed = LanguagePack.parse(bytes)
        if (bytes.size <= LanguagePack.MAX_BYTES) {
            val preview = (parsed as LanguageParse.Ok).preview
            assertEquals(EnglishCopy.lines.size, preview.accepted.size)
            assertEquals(0, preview.ignored)
            LanguageText.apply(preview.accepted)
            for (line in EnglishCopy.lines) {
                assertEquals(EnglishCopy.cap(line), LanguageText.text(line.key).length)
            }
        } else {
            // Every line at its slot cap, written with a two-byte letter, is over 264 KiB.
            // The file cap stays. That whole file is refused. The packs in the app are smaller.
            // Download-reason lines sat past 16 KiB. The TAK setting notes sit past 20 KiB.
            // The trusted-network setting notes sit past 28 KiB.
            // The report and device sentences sit past 96 KiB.
            // The signature-and-network note sits past 112 KiB.
            // The in-app update lines sit past 120 KiB. 121 KiB still refuses a runaway line.
            assertReject(parsed)
            assertEquals(LanguageErrors.LARGE, (parsed as LanguageParse.Reject).messageKey)
            assertTrue(
                "cap-fill file is ${bytes.size} bytes",
                bytes.size < LanguagePack.MAX_BYTES + 121 * 1024,
            )
        }
        val shipped = repoFile("app/src/main/assets/lang")
        if (shipped.isDirectory) {
            val joined = shipped.listFiles().orEmpty().joinToString("\n") { it.readText() }
            assertFalse(joined.contains("é".repeat(40)))
        }
    }

    @Test
    fun secondPackReplacesTheFirst() {
        val phone = Locale.FRANCE
        val store = LanguageStore(tempFile(), assets())
        store.load(phone)
        store.confirm(ok(pack("tab.live" to "Un", "tab.pause" to "Deux")), phone)
        store.confirm(ok(pack("tab.live" to "Trois")), phone)
        LanguageText.apply(store.current().overlay)
        assertEquals("Trois", LanguageText.text("tab.live"))
        assertEquals("Pause", LanguageText.text("tab.pause"))
    }

    @Test
    fun cancelDoesNotChangeTheLanguage() {
        val phone = Locale.FRANCE
        val store = LanguageStore(tempFile(), assets())
        val before = store.load(phone)
        val pending = ok(pack("tab.live" to "Trois"))
        LanguageText.apply(before.overlay)
        assertEquals(before.overlay, store.current().overlay)
        assertEquals("Direct", LanguageText.text("tab.live"))
        assertTrue(pending.accepted.containsKey("tab.live"))
        assertFalse(store.current().imported)
    }

    @Test
    fun removeReturnsToThePhoneLanguage() {
        val file = tempFile()
        val french = LanguageStore(file, assets())
        french.load(Locale.FRANCE)
        french.confirm(ok(pack("tab.live" to "Importé")), Locale.FRANCE)
        val back = french.remove(Locale.FRANCE)
        assertFalse(back.imported)
        assertEquals("fr", back.activeTag)
        assertEquals("Direct", back.overlay.getValue("tab.live"))
        val other = LanguageStore(tempFile(), assets())
        other.load(Locale.JAPAN)
        other.confirm(ok(pack("tab.live" to "Importé")), Locale.JAPAN)
        val english = other.remove(Locale.JAPAN)
        assertEquals("English", english.activeName)
        assertTrue(english.overlay.isEmpty())
        assertEquals(LanguageSnapshot.CHOICE_FOLLOW, english.choice)
    }

    @Test
    fun settingsImportDoesNotTouchTheLanguageFile() {
        val file = tempFile()
        val store = LanguageStore(file, assets())
        store.load(Locale.FRANCE)
        store.choose(LanguageSnapshot.CHOICE_ENGLISH, Locale.FRANCE)
        val before = file.readText()
        val local = PersistedConfig(settings = AppSettings(demoMode = true), takInstallTag = "AB23")
        val pack = SettingsExchange.pack(
            settings = AppSettings(demoMode = false),
            filter = FilterState(),
            presets = emptyList(),
            watchlist = emptyList(),
            hiddenPresetIds = emptySet(),
            appVersion = "1.1.27",
            exportedAt = "2026-10-20T00:00:00Z",
        )
        val (next, _) = SettingsExchange.apply(local, pack)
        assertEquals(before, file.readText())
        assertEquals("AB23", local.takInstallTag)
        assertFalse(next.settings.demoMode)
        val encoded = SettingsExchange.encode(pack)
        assertFalse(encoded.contains("fieldwatch-language"))
        assertFalse(encoded.contains("doorDismissed"))
        assertEquals(LanguageSnapshot.CHOICE_ENGLISH, store.current().choice)
    }

    @Test
    fun processStartDropsALineWhosePlaceholdersDrifted() {
        val file = tempFile()
        val store = LanguageStore(file, assets())
        store.load(Locale.US)
        store.confirm(ok(pack("tab.live" to "Direct", "language.confirm.change" to "{n} lignes")), Locale.US)
        val disk = LanguagePack.json.decodeFromString(app.fieldwatch.data.LanguageDisk.serializer(), file.readText())
        val drifted = disk.copy(
            pack = disk.pack!!.copy(
                strings = disk.pack!!.strings + ("tab.pause" to "Pause {gone}"),
            ),
        )
        file.writeText(LanguagePack.json.encodeToString(app.fieldwatch.data.LanguageDisk.serializer(), drifted))
        val again = LanguageStore(file, assets()).load(Locale.US)
        LanguageText.apply(again.overlay)
        assertEquals("Direct", LanguageText.text("tab.live"))
        assertEquals("Pause", LanguageText.text("tab.pause"))
        assertEquals("3 lignes", LanguageText.text("language.confirm.change", mapOf("n" to "3")))
        file.writeText("{")
        val broken = LanguageStore(file, assets()).load(Locale.FRANCE)
        assertEquals("English", broken.activeName)
        assertTrue(broken.overlay.isEmpty())
        assertFalse(broken.overlay.containsKey("tab.live"))
    }

    @Test
    fun translatedCsvCellKeepsTheFormulaGuard() {
        LanguageText.apply(mapOf("tab.live" to "=HYPERLINK(\"x\")"))
        val cell = CsvCells.quote(LanguageText.text("tab.live"))
        assertTrue(cell.startsWith("\"'"))
        assertTrue(cell.contains("=HYPERLINK"))
    }

    @Test
    fun templateContainsEveryKey() {
        val text = LanguagePack.template("1.1.27")
        val parsed = ok(text)
        assertEquals("English", parsed.language)
        assertEquals("1.1.27", parsed.writtenFor)
        assertEquals(EnglishCopy.lines.size, parsed.accepted.size)
        assertTrue(text.contains("\"slots\""))
        assertTrue(text.contains("\"max\""))
        val max = Json.parseToJsonElement(text).jsonObject.getValue("max").jsonObject
        assertEquals(EnglishCopy.lines.size, max.size)
        for (line in EnglishCopy.lines) {
            assertEquals(line.text, parsed.accepted.getValue(line.key))
            assertEquals(EnglishCopy.cap(line), max.getValue(line.key).jsonPrimitive.int)
        }
        val withMax = ok(
            """{"format":"fieldwatch-language","formatVersion":1,"language":"Test","languageTag":"fr","max":{"tab.live":1},"strings":{"tab.live":"Direct"}}""",
        )
        assertEquals("Direct", withMax.accepted.getValue("tab.live"))
        val over = ok(pack("class.law" to "X".repeat(19), "tab.live" to "Direct"))
        assertFalse(over.accepted.containsKey("class.law"))
        assertEquals("Direct", over.accepted.getValue("tab.live"))
    }

    @Test
    fun voiceChoiceStaysAndUserPhraseStays() {
        LanguageText.apply(mapOf("speech.finder" to "balises", "voice.signature" to "changed"))
        assertEquals("balises", SignatureClass.FINDER.speechLabel())
        assertEquals("balises", testWatchPhrase(AlertVoiceWhat.CLASS))
        assertEquals("Apple AirTags", testWatchPhrase(AlertVoiceWhat.SIGNATURE))
        assertTrue(testWatchPhrase(AlertVoiceWhat.BOTH).contains("Apple AirTags"))
        assertEquals("Kitchen cam", speakableWatchName("Kitchen cam"))
        val device = sighting("radio-1", "Kitchen cam")
        val named = WatchTarget(id = "w", deviceKey = device.key, label = "Kitchen cam")
        assertEquals("Kitchen cam", spokenWatchPhrase(device, emptyList(), AlertVoiceWhat.CLASS, named))
        val fleet = Fleet(id = "f", name = "Apple AirTags", kind = SignatureClass.FINDER)
        val hit = device.copy(fleetIds = setOf("f"))
        assertEquals("balises", spokenWatchPhrase(hit, listOf(fleet), AlertVoiceWhat.CLASS))
        assertEquals("Apple AirTags", spokenWatchPhrase(hit, listOf(fleet), AlertVoiceWhat.SIGNATURE))
    }

    @Test
    fun wigleHeadersAndAtakRemarksStayEnglish() {
        LanguageText.apply(mapOf("header.wifi.on" to "Wi-Fi oui", "tak.remarks" to "HACKED", "cot.type" to "HACKED"))
        val wigle = "MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type"
        assertTrue(wigle.contains("MAC,SSID"))
        assertTrue(wigle.contains("RSSI"))
        val remarks = TakGnss.remarks(null, emptyList())
        assertTrue(remarks.contains("Where this phone was. Not the source."))
        assertFalse(remarks.contains("HACKED"))
        val xml = CotEvent.selfXml(1.0, 2.0, 0L, "AB23", "Scout")
        assertTrue(xml.contains("type=\"a-f-G-U-C\""))
        assertTrue(xml.contains("Fieldwatch TAK heartbeat (this phone)"))
        assertFalse(xml.contains("HACKED"))
        val pin = CotEvent.remarks(sighting("aa", "Porch"), emptyList(), advertised = false)
        assertTrue(pin.contains("heard here (operator GPS)"))
        assertTrue(pin.contains("Wi-Fi"))
        assertFalse(pin.contains("HACKED"))
    }

    @Test
    fun cyrillicIsNotStrippedBeforeThePdfDraw() {
        val line = "Здравей свят"
        assertEquals(line, LanguagePack.clean(line, allowNewline = false))
        assertEquals(line, DebriefPdf.charactersDrawn(line))
        assertEquals("sans-serif", DebriefPdf.PDF_SANS)
    }

    @Test
    fun launcherNameStaysFieldwatch() {
        val xml = repoFile("src/main/res/values/strings.xml").readText()
        assertTrue(xml.contains(">Fieldwatch<"))
    }

    @Test
    fun phoneLanguageSelectsTheBundledFile() {
        assertEquals("fr", LanguagePack.bundledTag(Locale.FRANCE))
        assertEquals("fr", LanguagePack.bundledTag(Locale.CANADA_FRENCH))
        assertEquals("fr", LanguagePack.bundledTag(Locale.forLanguageTag("fr-LU")))
        assertEquals("de", LanguagePack.bundledTag(Locale.forLanguageTag("de-LU")))
        assertNull(LanguagePack.bundledTag(Locale.forLanguageTag("lb")))
        assertEquals("hr", LanguagePack.bundledTag(Locale.forLanguageTag("hr-HR")))
        assertEquals("hr", LanguagePack.bundledTag(Locale.forLanguageTag("cnr")))
        assertEquals("hr", LanguagePack.bundledTag(Locale.forLanguageTag("sr-ME")))
        assertNull(LanguagePack.bundledTag(Locale.forLanguageTag("sr-RS")))
        assertNull(LanguagePack.bundledTag(Locale.JAPAN))
        assertEquals("nb", LanguagePack.bundledTag(Locale.forLanguageTag("nb-NO")))
        assertEquals("nb", LanguagePack.bundledTag(Locale.forLanguageTag("nn-NO")))
        assertEquals("nb", LanguagePack.bundledTag(Locale.forLanguageTag("no")))
        assertNull(LanguagePack.bundledTag(Locale.ENGLISH))
        val store = LanguageStore(tempFile(), assets())
        val french = store.load(Locale.FRANCE)
        assertEquals("fr", french.activeTag)
        assertEquals("Direct", french.overlay.getValue("tab.live"))
        assertTrue(french.showAi)
        assertFalse(french.showDoor)
        LanguageText.apply(french.overlay)
        assertEquals("Filters", LanguageText.text("tab.filters"))
        val japan = LanguageStore(tempFile(), assets()).load(Locale.JAPAN)
        assertTrue(japan.showDoor)
        assertEquals("English", japan.activeName)
        val dismissed = LanguageStore(tempFile(), assets())
        dismissed.load(Locale.JAPAN)
        assertFalse(dismissed.dismissDoor(Locale.JAPAN).showDoor)
    }

    @Test
    fun languageMenuIsEnglishAlphabetical() {
        val menu = (listOf("English") + BundledLanguages.all.map { it.english }).sorted()
        assertEquals(
            listOf(
                "Albanian", "Bulgarian", "Croatian", "Czech", "Danish", "Dutch", "English",
                "Estonian", "Finnish", "French", "German", "Greek", "Hungarian", "Icelandic",
                "Italian", "Latvian", "Lithuanian", "Macedonian", "Norwegian", "Polish",
                "Portuguese", "Romanian", "Slovak", "Slovenian", "Spanish", "Swedish", "Turkish",
            ),
            menu,
        )
    }

    @Test
    fun bundledAssetFilesMatchTheLineRules() {
        val dir = repoFile("src/main/assets/lang")
        assertTrue(dir.isDirectory)
        val tags = BundledLanguages.all.map { it.tag }.sorted()
        val files = dir.listFiles().orEmpty().map { it.nameWithoutExtension }.sorted()
        assertEquals(tags, files)
        val tokens = listOf("Wi-Fi", "Bluetooth", "GPS", "GNSS", "PDF", "CSV", "ATAK", "SSID", "MAC", "RSSI", "OUI")
        for (bundled in BundledLanguages.all) {
            val parsed = LanguagePack.parse(File(dir, "${bundled.tag}.json").readBytes())
            val preview = (parsed as LanguageParse.Ok).preview
            assertEquals(bundled.name, preview.language)
            assertEquals("${bundled.tag} writtenFor", "1.1.40", preview.writtenFor)
            assertTrue("${bundled.tag} display.live.path", preview.accepted.containsKey("display.live.path"))
            assertTrue("${bundled.tag} display.recenter", preview.accepted.containsKey("display.recenter"))
            assertTrue("${bundled.tag} map.north.up", preview.accepted.containsKey("map.north.up"))
            assertTrue("${bundled.tag} map.rotate.travel", preview.accepted.containsKey("map.rotate.travel"))
            assertTrue("${bundled.tag} map.close.heading", preview.accepted.containsKey("map.close.heading"))
            assertTrue("${bundled.tag} map.close.north", preview.accepted.containsKey("map.close.north"))
            assertTrue("${bundled.tag} map.key.mark", preview.accepted.containsKey("map.key.mark"))
            assertTrue("${bundled.tag} map.esri.satellite", preview.accepted.containsKey("map.esri.satellite"))
            assertTrue("${bundled.tag} map.thunderforest.transport", preview.accepted.containsKey("map.thunderforest.transport"))
            assertTrue("${bundled.tag} map.usgs.imagerytopo", preview.accepted.containsKey("map.usgs.imagerytopo"))
            assertTrue("${bundled.tag} map.usgs.relief", preview.accepted.containsKey("map.usgs.relief"))
            assertTrue("${bundled.tag} map.usgs.hydro", preview.accepted.containsKey("map.usgs.hydro"))
            assertTrue("${bundled.tag} map.faa.vfr", preview.accepted.containsKey("map.faa.vfr"))
            assertTrue("${bundled.tag} map.united.states", preview.accepted.containsKey("map.united.states"))
            assertTrue("${bundled.tag} map.faa.not.for.navigation", preview.accepted.containsKey("map.faa.not.for.navigation"))
            assertTrue("${bundled.tag} map.zoom.in", preview.accepted.containsKey("map.zoom.in"))
            assertTrue("${bundled.tag} settings.map.overlays", preview.accepted.containsKey("settings.map.overlays"))
            assertTrue("${bundled.tag} settings.layer.flock", preview.accepted.containsKey("settings.layer.flock"))
            assertTrue("${bundled.tag} settings.layer.note", preview.accepted.containsKey("settings.layer.note"))
            assertTrue("${bundled.tag} settings.layer.other", preview.accepted.containsKey("settings.layer.other"))
            assertTrue("${bundled.tag} settings.layer.other.note", preview.accepted.containsKey("settings.layer.other.note"))
            assertTrue("${bundled.tag} settings.layer.other.download.title", preview.accepted.containsKey("settings.layer.other.download.title"))
            assertTrue("${bundled.tag} settings.layer.other.download.body", preview.accepted.containsKey("settings.layer.other.download.body"))
            assertTrue("${bundled.tag} settings.layer.alert", preview.accepted.containsKey("settings.layer.alert"))
            assertTrue("${bundled.tag} settings.layer.alerts", preview.accepted.containsKey("settings.layer.alerts"))
            assertTrue("${bundled.tag} settings.layer.alert.plate", preview.accepted.containsKey("settings.layer.alert.plate"))
            assertTrue("${bundled.tag} settings.layer.alert.red", preview.accepted.containsKey("settings.layer.alert.red"))
            assertTrue("${bundled.tag} settings.layer.alert.speed", preview.accepted.containsKey("settings.layer.alert.speed"))
            assertTrue("${bundled.tag} settings.layer.alert.gun", preview.accepted.containsKey("settings.layer.alert.gun"))
            assertTrue("${bundled.tag} settings.layer.alert.camera", preview.accepted.containsKey("settings.layer.alert.camera"))
            assertTrue("${bundled.tag} alert.speed.ahead", preview.accepted.containsKey("alert.speed.ahead"))
            assertTrue("${bundled.tag} alert.red.ahead", preview.accepted.containsKey("alert.red.ahead"))
            assertTrue("${bundled.tag} alert.gun.ahead", preview.accepted.containsKey("alert.gun.ahead"))
            assertTrue("${bundled.tag} alert.camera.ahead", preview.accepted.containsKey("alert.camera.ahead"))
            assertTrue("${bundled.tag} alert.camera.looking", preview.accepted.containsKey("alert.camera.looking"))
            assertTrue("${bundled.tag} alert.flock.looking", preview.accepted.containsKey("alert.flock.looking"))
            assertTrue("${bundled.tag} alert.flock.ahead", preview.accepted.containsKey("alert.flock.ahead"))
            assertTrue("${bundled.tag} alert.plate.looking", preview.accepted.containsKey("alert.plate.looking"))
            assertTrue("${bundled.tag} alert.plate.ahead", preview.accepted.containsKey("alert.plate.ahead"))
            assertTrue("${bundled.tag} alert.plate.unknown.ahead", preview.accepted.containsKey("alert.plate.unknown.ahead"))
            assertTrue("${bundled.tag} settings.layer.more", preview.accepted.containsKey("settings.layer.more"))
            assertTrue("${bundled.tag} settings.layer.saved", preview.accepted.containsKey("settings.layer.saved"))
            assertTrue("${bundled.tag} settings.layer.downloading", preview.accepted.containsKey("settings.layer.downloading"))
            assertTrue("${bundled.tag} settings.layer.processing", preview.accepted.containsKey("settings.layer.processing"))
            assertTrue("${bundled.tag} settings.layer.failed.reach", preview.accepted.containsKey("settings.layer.failed.reach"))
            assertTrue("${bundled.tag} settings.layer.failed.trust", preview.accepted.containsKey("settings.layer.failed.trust"))
            assertTrue("${bundled.tag} settings.layer.failed.server", preview.accepted.containsKey("settings.layer.failed.server"))
            assertTrue("${bundled.tag} settings.layer.failed.server.code", preview.accepted.containsKey("settings.layer.failed.server.code"))
            assertTrue("${bundled.tag} settings.layer.failed.space", preview.accepted.containsKey("settings.layer.failed.space"))
            assertTrue("${bundled.tag} settings.layer.failed.large", preview.accepted.containsKey("settings.layer.failed.large"))
            assertTrue("${bundled.tag} settings.layer.failed.empty", preview.accepted.containsKey("settings.layer.failed.empty"))
            assertTrue("${bundled.tag} settings.layer.failed.read", preview.accepted.containsKey("settings.layer.failed.read"))
            assertTrue("${bundled.tag} settings.layer.failed.save", preview.accepted.containsKey("settings.layer.failed.save"))
            assertTrue("${bundled.tag} settings.layer.failed.unchanged", preview.accepted.containsKey("settings.layer.failed.unchanged"))
            assertTrue("${bundled.tag} import.failed.save", preview.accepted.containsKey("import.failed.save"))
            assertTrue("${bundled.tag} import.failed.kept", preview.accepted.containsKey("import.failed.kept"))
            assertTrue("${bundled.tag} catalog.failed.server", preview.accepted.containsKey("catalog.failed.server"))
            assertTrue("${bundled.tag} catalog.failed.server.code", preview.accepted.containsKey("catalog.failed.server.code"))
            assertTrue("${bundled.tag} catalog.failed.empty", preview.accepted.containsKey("catalog.failed.empty"))
            assertTrue("${bundled.tag} catalog.failed.read", preview.accepted.containsKey("catalog.failed.read"))
            assertTrue("${bundled.tag} ui.radios.here", preview.accepted.containsKey("ui.radios.here"))
            assertTrue("${bundled.tag} ui.move.path", preview.accepted.containsKey("ui.move.path"))
            assertTrue("${bundled.tag} ui.waiting.gps.fix", preview.accepted.containsKey("ui.waiting.gps.fix"))
            assertTrue("${bundled.tag} hunt.no.gps.walk", preview.accepted.containsKey("hunt.no.gps.walk"))
            assertTrue("${bundled.tag} ui.cameras.on.path", preview.accepted.containsKey("ui.cameras.on.path"))
            assertTrue("${bundled.tag} ui.cameras.within", preview.accepted.containsKey("ui.cameras.within"))
            assertTrue("${bundled.tag} ui.cameras.facing.count", preview.accepted.containsKey("ui.cameras.facing.count"))
            assertTrue("${bundled.tag} ui.cameras.near.count", preview.accepted.containsKey("ui.cameras.near.count"))
            assertTrue("${bundled.tag} ui.cameras.kind.gun", preview.accepted.containsKey("ui.cameras.kind.gun"))
            assertTrue("${bundled.tag} ui.map.camera.facing", preview.accepted.containsKey("ui.map.camera.facing"))
            assertTrue("${bundled.tag} map.cameras.view", preview.accepted.containsKey("map.cameras.view"))
            assertTrue("${bundled.tag} map.cameras.off", preview.accepted.containsKey("map.cameras.off"))
            assertTrue(
                "${bundled.tag} missing ${preview.missingKeys}",
                preview.missingKeys.isEmpty(),
            )
            assertTrue(
                "${bundled.tag} dropped ${preview.issues}",
                preview.issues.isEmpty(),
            )
            for ((key, value) in preview.accepted) {
                val english = EnglishCopy.text(key)
                assertTrue("${bundled.tag} $key over cap", value.length <= EnglishCopy.cap(key))
                for (token in tokens) {
                    if (english.contains(token)) {
                        assertTrue("${bundled.tag} $key lost $token", value.contains(token))
                    }
                }
                if (EnglishCopy.byKey.getValue(key).slot == LanguageSlot.CLASSCHIP) {
                    assertTrue("${bundled.tag} $key chip ${value.length}", value.length <= 18)
                }
            }
        }
    }

    @Test
    fun spokenNameLinesAreInEveryPack() {
        val keys = listOf(
            "settings.spoken.names",
            "spoken.card.note",
            "spoken.name",
            "spoken.as",
            "spoken.words",
            "spoken.network",
            "spoken.save",
            "spoken.remove",
            "spoken.one",
            "spoken.count",
            "spoken.held",
            "spoken.remove.ask",
            "spoken.name.note",
            "spoken.class.only",
            "spoken.empty",
            "spoken.named.instead",
        )
        val dir = repoFile("src/main/assets/lang")
        for (bundled in BundledLanguages.all) {
            val parsed = LanguagePack.parse(File(dir, "${bundled.tag}.json").readBytes())
            val accepted = (parsed as LanguageParse.Ok).preview.accepted
            for (key in keys) {
                assertTrue("${bundled.tag} $key", accepted.containsKey(key))
                assertEquals(
                    "${bundled.tag} $key",
                    EnglishCopy.placeholders.getValue(key),
                    placeholderNames(accepted.getValue(key)),
                )
            }
        }
    }

    @Test
    fun englishProtocolNamesStayInTheMap() {
        val tokens = listOf(
            "Wi-Fi", "Bluetooth", "GPS", "GNSS", "PDF", "CSV", "ATAK", "SSID", "MAC", "RSSI", "OUI",
        )
        for (token in tokens) {
            assertTrue(
                token,
                EnglishCopy.lines.any { it.text.contains(token) },
            )
        }
        assertTrue(EnglishCopy.lines.none { it.text.contains("USB") })
    }

    @Test
    fun englishKeysAreWellFormed() {
        val keyRe = Regex("^[a-z0-9]+(\\.[a-z0-9]+)+$")
        val tokenRe = Regex("\\{[A-Za-z][A-Za-z0-9_]*\\}")
        assertEquals(EnglishCopy.lines.size, EnglishCopy.lines.map { it.key }.toSet().size)
        for (line in EnglishCopy.lines) {
            assertTrue(line.key, keyRe.matches(line.key))
            assertEquals(placeholderNames(line.text), EnglishCopy.placeholders.getValue(line.key))
            for (match in tokenRe.findAll(line.text)) {
                assertEquals(match.range.first, line.text.lastIndexOf('{', match.range.first))
            }
        }
    }

    @Test
    fun authorGuideExists() {
        val guide = repoFile("../docs/language-pack-author-guide.md")
        val text = guide.readText()
        assertTrue(text.contains("Export English template"))
        assertTrue(text.contains("count: {n}"))
        assertTrue(text.contains("not reviewed or certified"))
        assertTrue(text.contains("Wi-Fi"))
        assertTrue(text.contains("264 KiB"))
        assertTrue(text.contains("Save report"))
        assertTrue(text.contains("`max`"))
    }

    @Test
    fun nonUtf8IsRejected() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00)
        assertEquals(LanguageErrors.NOT_PACK, (LanguagePack.parse(bytes) as LanguageParse.Reject).messageKey)
    }

    @Test
    fun choosingEnglishDoesNotReadABundledFile() {
        var reads = 0
        val store = LanguageStore(tempFile(), LanguageAssets {
            reads++
            bundled("fr")
        })
        store.load(Locale.FRANCE)
        assertEquals(1, reads)
        val english = store.choose(LanguageSnapshot.CHOICE_ENGLISH, Locale.FRANCE)
        assertEquals(1, reads)
        assertTrue(english.overlay.isEmpty())
        assertEquals("English", english.activeName)
        assertFalse(english.showAi)
    }

    private fun ok(text: String): LanguagePreview {
        val parsed = LanguagePack.parse(text.toByteArray(Charsets.UTF_8))
        assertTrue(parsed.toString(), parsed is LanguageParse.Ok)
        return (parsed as LanguageParse.Ok).preview
    }

    private fun assertReject(parsed: LanguageParse) {
        assertTrue(parsed.toString(), parsed is LanguageParse.Reject)
    }

    private fun pack(vararg lines: Pair<String, String>, writtenFor: String? = null): String = json(
        lines.toMap(),
        writtenFor,
    )

    private fun json(strings: Map<String, String>, writtenFor: String? = null): String {
        val body = strings.entries.joinToString(",") { (key, value) ->
            "\"$key\":${jsonString(value)}"
        }
        val written = writtenFor?.let { ""","writtenFor":${jsonString(it)}""" }.orEmpty()
        return """{"format":"fieldwatch-language","formatVersion":1,"language":"Test","languageTag":"fr"$written,"strings":{$body}}"""
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun zipBytes(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun tempFile(): File {
        val file = File.createTempFile("fieldwatch-language", ".json")
        file.delete()
        file.deleteOnExit()
        return file
    }

    private fun assets(): LanguageAssets = LanguageAssets { tag ->
        if (tag == "fr") bundled("fr") else null
    }

    private fun bundled(tag: String): ByteArray = """
        {"format":"fieldwatch-language","formatVersion":1,"language":"Français","languageTag":"$tag","writtenFor":"1.1.27","strings":{"tab.live":"Direct"}}
    """.trim().toByteArray()

    private fun storeWith(text: String): LanguageStore {
        val store = LanguageStore(tempFile(), assets())
        store.load(Locale.US)
        store.confirm(ok(text), Locale.US)
        return store
    }

    private fun sighting(key: String, name: String) = Sighting(
        key = key,
        kind = RadioKind.WIFI,
        mac = "AA:BB:CC:DD:EE:FF",
        name = name,
        rssi = -40,
        rssiMin = -40,
        rssiMax = -40,
        channel = 1,
        frequencyMhz = 2412,
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
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
    )

    private fun repoFile(path: String): File {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.exists() } ?: candidates.last()
    }
}
