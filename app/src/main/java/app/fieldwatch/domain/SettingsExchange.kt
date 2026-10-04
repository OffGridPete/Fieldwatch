package app.fieldwatch.domain

import app.fieldwatch.i18n.localized

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SettingsPack(
    val format: String,
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: String = "",
    val appVersion: String = "",
    val settings: AppSettings = AppSettings(),
    val filter: FilterState = FilterState(),
    val presets: List<FilterPreset> = emptyList(),
    val watchlist: List<WatchTarget> = emptyList(),
    val hiddenPresetIds: Set<String> = emptySet(),
) {
    companion object {
        const val FORMAT = "fieldwatch-settings"
        const val FORMAT_VERSION = 1
    }
}

data class SettingsImportResult(
    val namedRadios: Int = 0,
    val signatureWatches: Int = 0,
    val presets: Int = 0,
    val error: String? = null,
) {
    fun summary(): String {
        error?.let { return it }
        val preset = if (presets == 1) localized("restore_preset_one", "Restored Settings, the current filter, and %1\$s preset.", presets)
            else localized("restore_preset_many", "Restored Settings, the current filter, and %1\$s presets.", presets)
        val radio = if (namedRadios == 1) localized("restore_radio_one", "%1\$s named radio", namedRadios)
            else localized("restore_radio_many", "%1\$s named radios", namedRadios)
        val watch = if (signatureWatches == 1) localized("restore_watch_one", "%1\$s signature watch", signatureWatches)
            else localized("restore_watch_many", "%1\$s signature watches", signatureWatches)
        return "$preset $radio, $watch."
    }
}

object SettingsExchange {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun pack(
        settings: AppSettings,
        filter: FilterState,
        presets: List<FilterPreset>,
        watchlist: List<WatchTarget>,
        hiddenPresetIds: Set<String>,
        appVersion: String,
        exportedAt: String,
    ): SettingsPack = SettingsPack(
        format = SettingsPack.FORMAT,
        formatVersion = SettingsPack.FORMAT_VERSION,
        exportedAt = exportedAt,
        appVersion = appVersion,
        settings = settings,
        filter = filter,
        presets = presets,
        watchlist = watchlist,
        hiddenPresetIds = hiddenPresetIds,
    )

    fun encode(pack: SettingsPack): String = json.encodeToString(SettingsPack.serializer(), pack)

    fun parse(text: String): SettingsPack {
        val trimmed = text.trim().trimStart('\uFEFF')
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException(localized("settings_exchange_this_file_is_empty", "This file is empty."))
        }
        val pack = try {
            json.decodeFromString(SettingsPack.serializer(), trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                localized("settings_exchange_not_a_fieldwatch_settings_pack_export_from", "Not a Fieldwatch settings pack. Export from Settings → Export settings."),
                e,
            )
        }
        if (pack.format == SignaturePack.FORMAT || pack.format == SignaturePack.LEGACY_FORMAT) {
            throw IllegalArgumentException(
                localized("settings_exchange_that_is_a_signature_pack_use_import", "That is a signature pack. Use Import signatures."),
            )
        }
        if (pack.format != SettingsPack.FORMAT) {
            throw IllegalArgumentException(
                localized("settings_exchange_not_a_fieldwatch_settings_pack_open_a", "Not a Fieldwatch settings pack (open a fieldwatch-settings JSON file)."),
            )
        }
        return pack
    }

    /**
     * Replace Settings, the current filter, presets, and watchlist.
     * Keep the catalog, logs, GPS, already-seen keys, the local disclaimer click-through,
     * and whether this phone already showed the Live tour.
     */
    fun apply(local: PersistedConfig, pack: SettingsPack): Pair<PersistedConfig, SettingsImportResult> {
        val next = local.copy(
            settings = pack.settings.copy(
                disclaimerAccepted = local.settings.disclaimerAccepted,
                disclaimerRev = local.settings.disclaimerRev,
                liveTourDone = local.settings.liveTourDone,
                darkTheme = true,
                scanControlsExpanded = false,
            ),
            filter = pack.filter,
            presets = pack.presets,
            watchlist = pack.watchlist,
            hiddenPresetIds = pack.hiddenPresetIds,
        )
        val result = SettingsImportResult(
            namedRadios = pack.watchlist.count { !it.deviceKey.isNullOrBlank() },
            signatureWatches = pack.watchlist.count { !it.fleetId.isNullOrBlank() },
            presets = pack.presets.size,
        )
        return next to result
    }
}
