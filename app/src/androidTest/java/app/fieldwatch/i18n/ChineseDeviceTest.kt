package app.fieldwatch.i18n

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.filters.SdkSuppress
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.R
import app.fieldwatch.data.DebriefPdf
import app.fieldwatch.domain.*
import app.fieldwatch.qa.I18nQaActivity
import app.fieldwatch.radio.RadioPermissions
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ChineseDeviceTest {
    @get:Rule(order = 0) val permissions = GrantPermissionRule.grant(*RadioPermissions.required())
    @get:Rule(order = 1) val ui = createAndroidComposeRule<I18nQaActivity>()

    private fun language(tag: String) {
        ui.activityRule.scenario.onActivity { AppLanguage.select(tag) }
        val expected = if (tag.startsWith("zh")) "zh" else "en"
        ui.waitUntil(15_000) {
            runCatching {
                AppLanguage.selection() == tag &&
                    ui.activity.resources.configuration.locales[0].language == expected &&
                    ui.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        ui.waitForIdle()
    }

    @After fun reset() {
        ui.activityRule.scenario.onActivity { it.vm.cancelDraft(); AppLanguage.select("en") }
    }

    @Test fun newChineseDraftSurvivesRotationAndLanguageChangeWithoutBeingSaved() {
        language("zh-Hans")
        ui.onNodeWithText("识别特征", useUnmergedTree = true).performClick()
        ui.activityRule.scenario.onActivity { it.vm.beginNewFleet() }
        val field = hasSetTextAction() and hasText("新建识别特征")
        ui.onNode(field).assertExists().performTextReplacement("未保存草稿 QA / 中文")
        ui.activityRule.scenario.recreate()
        ui.onNode(hasSetTextAction() and hasText("未保存草稿 QA / 中文")).assertExists()
        language("en")
        ui.onNode(hasSetTextAction() and hasText("未保存草稿 QA / 中文")).assertExists()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Cancel").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Cancel").performClick()
        val app = ui.activity.application as FieldwatchApp
        assertFalse(app.config.fleets.any { it.name == "未保存草稿 QA / 中文" })
        ui.activityRule.scenario.onActivity { it.vm.beginNewFleet() }
        ui.onNode(hasSetTextAction() and hasText("New Signature")).assertExists()
    }

    @Test @SdkSuppress(minSdkVersion = 33) fun localeResourceContextAndSystemEntryStaySynchronized() {
        language("zh-Hans")
        assertEquals("zh", AppLanguage.locale().language)
        assertEquals("新建识别特征", appText(R.string.new_signature_name))
        assertFalse(DebriefPlaces.Off.note == "off")
        val manager = ui.activity.getSystemService(android.app.LocaleManager::class.java)
        assertEquals("zh-Hans", manager.applicationLocales.toLanguageTags())
        ui.activityRule.scenario.onActivity {
            manager.applicationLocales = android.os.LocaleList.forLanguageTags("en")
        }
        ui.waitForIdle()
        ui.waitUntil(10_000) { AppLanguage.selection() == "en" }
        assertEquals("New Signature", appText(R.string.new_signature_name))
        ui.activityRule.scenario.onActivity {
            manager.applicationLocales = android.os.LocaleList.forLanguageTags("fr")
        }
        ui.waitUntil(10_000) { AppLanguage.selection() == "fr" && AppLanguage.locale().language == "en" }
        assertEquals("New Signature", appText(R.string.new_signature_name))
        language("")
        assertTrue(manager.applicationLocales.isEmpty)
    }

    private fun verifyDraftRestoration(value: String) {
        ui.activityRule.scenario.recreate()
        ui.onNode(hasSetTextAction() and hasText(value)).assertExists()
        language("en")
        ui.onNode(hasSetTextAction() and hasText(value)).assertExists()
    }

    @Test fun presetNameAndSessionNameRemainUncommittedAcrossRecreation() {
        language("zh-Hans")
        ui.onNodeWithText("筛选", useUnmergedTree = true).performClick()
        ui.onNode(hasSetTextAction() and hasText(appText(R.string.filters_screen_save_current_as)))
            .performScrollTo().performTextReplacement("QA 未保存预设")
        verifyDraftRestoration("QA 未保存预设")
        assertFalse((ui.activity.application as FieldwatchApp).config.presets.any { it.name == "QA 未保存预设" })
        language("zh-Hans")
        ui.onNodeWithText("报告", useUnmergedTree = true).performClick()
        ui.onAllNodesWithText("开始会话").onFirst().performClick()
        ui.onNode(hasSetTextAction() and hasText(appText(R.string.reports_screen_name)))
            .performTextReplacement("QA 未保存会话")
        verifyDraftRestoration("QA 未保存会话")
        ui.onNodeWithText("Cancel").performClick()
        assertNull((ui.activity.application as FieldwatchApp).sits.ui.value.open)
    }

    @Test fun decodeDraftRestoresAndTranslatedStockLabelsAreNotWrittenBack() {
        language("zh-Hans")
        ui.onNodeWithText("识别特征", useUnmergedTree = true).performClick()
        val app = ui.activity.application as FieldwatchApp
        val stock = app.config.fleets.first { it.id == "fleet-dult" }
        ui.activityRule.scenario.onActivity { it.vm.editFleet(stock) }
        ui.onNodeWithText(appText(R.string.fleets_screen_fields, stock.decode!!.fields.size)).performScrollTo().performClick()
        ui.onNode(hasSetTextAction() and hasText("模式")).assertExists()
        val label = hasSetTextAction() and hasText("模式")
        ui.onNode(label).performScrollTo().performTextReplacement("QA 自定义模式")
        ui.onNode(hasSetTextAction() and hasText("离开主人")).performScrollTo().performTextReplacement("QA 自定义枚举")
        val mode = stock.decode!!.fields.first { it.id == "mode" }
        val note = CatalogText.decodeField(stock, mode, "note", mode.enumNotes!!.getValue("0"), "0")
        ui.onNode(hasSetTextAction() and hasText(note)).performScrollTo().performTextReplacement("QA 未保存备注")
        verifyDraftRestoration("QA 自定义模式")
        ui.onNode(hasSetTextAction() and hasText("QA 自定义枚举")).assertExists()
        ui.onNode(hasSetTextAction() and hasText("QA 未保存备注")).assertExists()
        ui.onNodeWithText("Back").performClick()
        ui.onNodeWithText("Cancel").performClick()
        assertEquals(stock, app.config.fleets.first { it.id == stock.id })
    }

    @Test fun editingOnlyNumericStockFieldPreservesCanonicalNamesAndNotes() {
        language("zh-Hans")
        ui.onNodeWithText("识别特征", useUnmergedTree = true).performClick()
        val app = ui.activity.application as FieldwatchApp
        val stock = app.config.fleets.first { it.displayName() != it.name }
        ui.activityRule.scenario.onActivity { it.vm.editFleet(stock) }
        ui.onNode(hasSetTextAction() and hasText(appText(R.string.fleets_screen_min_peers_0_off)))
            .performScrollTo().performTextReplacement("7")
        ui.onNodeWithText("保存").performClick()
        ui.waitUntil(10_000) { app.config.fleets.first { it.id == stock.id }.minPeers == 7 }
        assertEquals(stock.copy(minPeers = 7), app.config.fleets.first { it.id == stock.id })
        runBlocking { app.config.update { cfg -> cfg.copy(fleets = cfg.fleets.map { if (it.id == stock.id) stock else it }) } }
    }

    @Test fun generatesRealAndroidPdfsFromSyntheticFixturesInBothLanguages() {
        val dir = File(ui.activity.filesDir, "i18n-qa").apply { mkdirs() }
        val now = 1_728_000_900_000L
        val path = (0..30).map { i ->
            val shift = if (i < 8) 0.0 else if (i > 22) .003 else (i - 8) * .0002
            GpsSample(now - 900_000 + i * 30_000, 35.0 + shift, 110.0 + shift)
        }
        val transitPath = listOf(GpsSample(now - 900_000, 35.0, 110.0), GpsSample(now - 840_000, 35.0, 110.0)) +
            (0..11).map { i -> GpsSample(now - 780_000 + i * 5_000, 35.001 + i * .00027, 110.0) } +
            listOf(GpsSample(now - 600_000, 35.004, 110.0), GpsSample(now, 35.004, 110.0))
        assertTrue(Geo.legs(transitPath).any { !it.stay })
        assertTrue(Geo.legs(transitPath).any { it.stay })
        val fleet = DefaultCatalog.newBlankFleet().copy(
            id = "qa-attention", name = "Synthetic / 模拟识别特征", builtIn = false,
            kind = SignatureClass.FINDER, attentionNote = "QA 模拟说明，匹配不代表身份。".repeat(40),
        )
        val devices = (1..16).map { i ->
            val mac = "02:00:00:00:00:%02X".format(i)
            Sighting(
                key = "BLE:$mac", kind = if (i % 3 == 0) RadioKind.WIFI else RadioKind.BLE,
                mac = mac, name = if (i == 1) "QA 长中文名称 / Mixed text " + "监测样本".repeat(15) else "QA模拟设备-$i",
                rssi = -40 - i, rssiMin = -85, rssiMax = -40, channel = if (i % 3 == 0) 6 else 0,
                frequencyMhz = if (i % 3 == 0) 2437 else 0, vendor = "Synthetic vendor", randomized = false,
                hiddenSsid = false, serviceUuids = listOf("180F"), manufacturerId = null,
                manufacturerDataHex = "", rawHex = "", extras = "", firstSeen = now - 900_000,
                lastSeen = now, hitCount = 31, fleetIds = if (i == 1) setOf(fleet.id) else emptySet(),
                rssiHistory = path.map { RssiSample(it.at, -40 - i) }, presence = listOf(PresenceSpan(now - 900_000, now)),
                gpsTrail = path.map { it.copy(rssi = -40 - i) }, latitude = path.last().lat, longitude = path.last().lon,
            )
        }
        for (tag in listOf("en", "zh-Hans")) {
            language(tag)
            val settings = AppSettings(tagLocation = true, onlineLookup = false)
            val docs = linkedMapOf(
                "empty" to DebriefReport.document(emptyList(), emptyList(), settings, emptyList(), now),
                "ordinary" to DebriefReport.document(devices.takeLast(3), listOf(fleet), settings, emptyList(), now),
                "long" to DebriefReport.document(devices.take(2), listOf(fleet), settings, path, now,
                    observerNotes = mapOf(devices[0].key to "QA 观察备注：" + "中文混排 long note。".repeat(80))),
                "many-path" to DebriefReport.document(devices, listOf(fleet), settings, path, now),
                "transit" to DebriefReport.document(devices.takeLast(3), listOf(fleet), settings, transitPath, now),
                "compare" to SitDiff.document(
                    SitDiff.Side("QA 会话 A", false, devices.take(12).map { SitDiff.fromSighting(it, listOf(fleet), emptyMap()) }, path),
                    SitDiff.Side("QA 会话 B", false, devices.drop(4).map { SitDiff.fromSighting(it, listOf(fleet), emptyMap()) }, path),
                    showAllRadios = true),
            )
            for ((name, doc) in docs) {
                val file = File(dir, "$tag-$name.pdf")
                DebriefPdf.write(doc, file)
                assertTrue("$file", file.length() > 1000)
                File(dir, "$tag-$name.txt").writeText(doc.toPlainText())
            }
            val attention = docs.getValue("long")
            assertTrue(attention.sections.any { it.kind == ReportSectionKind.EXTRA_ATTENTION })
            // Titles are presentation: renaming one must retain the structured attention card.
            DebriefPdf.write(attention.copy(sections = attention.sections.map {
                if (it.kind == ReportSectionKind.EXTRA_ATTENTION) it.copy(title = "QA 自定义标题") else it
            }), File(dir, "$tag-retitled.pdf"))
            DebriefPdf.write(attention.copy(extraAttention = listOf(
                attention.extraAttention.first().copy(
                    signature = "QA 长识别特征 " + "混排中文 Mixed text。".repeat(25),
                    note = "QA 极长备注：" + "分页检验中文 mixed text 0123456789。".repeat(220) + " QA END OF NOTE",
                )
            )), File(dir, "$tag-overflow.pdf"))
            val dult = DefaultCatalog.fleets().first { it.id == "fleet-dult" }
            val decodedDevice = devices[1].copy(fleetIds = setOf(dult.id),
                facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FCB2", "0000")), deviceClass = 0x020C))
            val decoded = DebriefReport.document(listOf(decodedDevice), listOf(dult),
                settings.copy(debriefShowAllRadios = true), emptyList(), now)
            assertTrue(decoded.toPlainText().contains(if (tag == "zh-Hans") "离开主人" else "separated"))
            DebriefPdf.write(decoded, File(dir, "$tag-decoded.pdf"))
            File(dir, "$tag-detail.txt").writeText(DeviceDetailText.build(devices[0], listOf(fleet.name), now, fleets = listOf(fleet)))
            File(dir, "$tag-ai-detail.txt").writeText(DeviceDetailPrompt.build(decodedDevice, listOf(dult.name), settings, now = now, fleets = listOf(dult)))
        }
    }
}
