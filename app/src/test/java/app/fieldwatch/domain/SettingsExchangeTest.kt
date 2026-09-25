package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsExchangeTest {
    @Test
    fun factorySettingsVoiceAndJump() {
        val stock = AppSettings()
        assertTrue(stock.alertVoice)
        assertEquals(AlertVoiceWhat.BOTH, stock.alertVoiceWhat)
        assertTrue(stock.snapToBeep)
        assertTrue(stock.darkTheme)
        assertTrue(stock.keepScreenOn)
        assertTrue(stock.alertBeep)
        assertEquals(ViewMode.BY_CLASS, stock.viewMode)
        assertTrue(stock.showRssiBar)
        assertTrue(stock.showFleetName)
        assertTrue(stock.showFrequency)
        assertTrue(stock.showSeenTimes)
    }

    private val stockFleets = DefaultCatalog.fleets()
    private val stockPresets = FilterEngine().defaultPresets(stockFleets)

    private fun localConfig(
        settings: AppSettings = AppSettings(
            disclaimerAccepted = true,
            disclaimerRev = DISCLAIMER_REV,
            nightMode = false,
            demoMode = false,
        ),
        filter: FilterState = FilterState(),
        presets: List<FilterPreset> = stockPresets,
        watchlist: List<WatchTarget> = DefaultCatalog.defaultWatchlist(),
        hiddenPresetIds: Set<String> = emptySet(),
        fleets: List<Fleet> = stockFleets,
    ) = PersistedConfig(
        version = 72,
        fleets = fleets,
        filter = filter,
        presets = presets,
        watchlist = watchlist,
        settings = settings,
        arrivalKnownKeys = setOf("BLE:AA:BB:CC:DD:EE:FF"),
        hiddenPresetIds = hiddenPresetIds,
    )

    private fun samplePack() = SettingsExchange.pack(
        settings = AppSettings(
            disclaimerAccepted = false,
            disclaimerRev = 0,
            nightMode = true,
            darkTheme = false,
            demoMode = true,
            keepScreenOn = false,
            takEnabled = true,
            takHost = "192.168.0.9",
        ),
        filter = FilterState(showWifi = false, showBle = true),
        presets = stockPresets + FilterPreset(
            id = "custom-plaza",
            name = "plaza -80",
            filter = FilterState(rssiMin = -80, showWifi = false, showBle = true),
        ),
        watchlist = listOf(
            WatchTarget(
                id = "named-bag",
                deviceKey = "BLE:C3:A6:A9:11:22:33",
                label = "bag tag",
                alert = true,
                observerNotes = "in the bag",
            ),
            WatchTarget(
                id = "watch-airtag",
                fleetId = "fleet-airtag",
                label = "Apple AirTags",
            ),
        ),
        hiddenPresetIds = setOf("hide-phones"),
        appVersion = "1.0.1",
        exportedAt = "2026-09-16T00:00:00Z",
    )

    @Test
    fun roundTripKeepsNamedRadiosAndPresets() {
        val json = SettingsExchange.encode(samplePack())
        assertTrue(json.contains("\"format\": \"fieldwatch-settings\""))
        assertFalse(json.contains("\"fleets\""))
        val pack = SettingsExchange.parse(json)
        assertEquals(SettingsPack.FORMAT, pack.format)
        assertEquals(1, pack.watchlist.count { it.deviceKey == "BLE:C3:A6:A9:11:22:33" })
        assertEquals("in the bag", pack.watchlist.single { it.deviceKey != null }.observerNotes)
        assertEquals("plaza -80", pack.presets.last().name)
        assertEquals(setOf("hide-phones"), pack.hiddenPresetIds)
        assertTrue(pack.filter.showBle)
        assertFalse(pack.filter.showWifi)
    }

    @Test
    fun applyReplacesSetupButKeepsCatalogDisclaimerAndSeenKeys() {
        val customFleet = Fleet(
            id = "custom-keep-me",
            name = "Keep me",
            rules = listOf(MatchRule(RuleKind.NAME_CONTAINS, text = "KeepMe")),
        )
        val local = localConfig(
            fleets = stockFleets + customFleet,
            filter = FilterState(showWifi = true, showBle = true),
            watchlist = DefaultCatalog.defaultWatchlist(),
        )
        val (next, result) = SettingsExchange.apply(local, samplePack())
        assertEquals(null, result.error)
        assertEquals(1, result.namedRadios)
        assertEquals(1, result.signatureWatches)
        assertEquals(stockPresets.size + 1, result.presets)
        assertTrue(result.summary().contains("1 named radio"))
        assertTrue(next.settings.nightMode)
        assertTrue(next.settings.darkTheme)
        assertTrue(next.settings.demoMode)
        assertEquals("192.168.0.9", next.settings.takHost)
        assertTrue(next.settings.disclaimerAccepted)
        assertEquals(DISCLAIMER_REV, next.settings.disclaimerRev)
        assertFalse(next.filter.showWifi)
        assertEquals(setOf("hide-phones"), next.hiddenPresetIds)
        assertTrue(next.presets.any { it.id == "custom-plaza" })
        assertEquals("bag tag", next.watchlist.single { it.deviceKey != null }.label)
        assertEquals("in the bag", next.watchlist.single { it.deviceKey != null }.observerNotes)
        assertTrue(next.fleets.any { it.id == "custom-keep-me" })
        assertEquals(local.fleets.size, next.fleets.size)
        assertEquals(setOf("BLE:AA:BB:CC:DD:EE:FF"), next.arrivalKnownKeys)
    }

    @Test
    fun applyTwiceIsIdempotent() {
        val local = localConfig()
        val (once, _) = SettingsExchange.apply(local, samplePack())
        val (twice, result) = SettingsExchange.apply(once, samplePack())
        assertEquals(once, twice)
        assertEquals(1, result.namedRadios)
    }

    @Test
    fun parseRejectsSignaturePackAndEmpty() {
        val signatures = SignatureExchange.encode(
            SignatureExchange.pack(
                fleets = stockFleets.take(1),
                catalogVersion = 1,
                appVersion = "1.0.1",
                exportedAt = "2026-09-16T00:00:00Z",
            ),
        )
        try {
            SettingsExchange.parse(signatures)
            throw AssertionError("expected parse to fail")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("signature pack"))
        }
        try {
            SettingsExchange.parse("")
            throw AssertionError("expected parse to fail")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("empty"))
        }
        try {
            SettingsExchange.parse("{\"version\":68,\"fleets\":[]}")
            throw AssertionError("expected parse to fail")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Not a Fieldwatch settings pack"))
        }
    }
}
