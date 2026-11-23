package app.fieldwatch.domain

import java.util.UUID

/** Sit and log files for an automation export. Same writers as Reports. */
object AutomationExport {
    fun sitBytes(
        kind: LogExportKind,
        devices: List<Sighting>,
        path: List<GpsSample>,
        gnss: List<GnssMark>,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        radios: LogExportRadios,
        versionName: String,
        deviceInfo: String,
        now: Long,
        tag: String,
        callsign: String,
        sitName: String,
    ): ByteArray {
        if (kind == LogExportKind.ATAK) {
            val packed = SitTakPackage.pack(
                devices, radios, path, gnss, fleets, watchlist, now, tag, callsign,
            ) ?: error(SitExport.emptyHint(kind, radios))
            return SitTakPackage.zip(packed, UUID.randomUUID().toString(), sitName, now)
        }
        return sitText(kind, devices, path, fleets, watchlist, radios, versionName, deviceInfo)
            .toByteArray(Charsets.UTF_8)
    }

    fun logMapText(
        kind: LogExportKind,
        pins: List<LogRadio>,
        versionName: String,
        deviceInfo: String,
    ): String {
        val fmt = GeoExport.formatOf(kind) ?: error(Automation.SIT_FORMAT)
        val located = pins.filter { it.hasPosition }
        if (located.isEmpty()) error(Automation.NO_LOG_GPS)
        return GeoExport.render(fmt, located, emptyMap(), versionName, deviceInfo)
    }

    private fun sitText(
        kind: LogExportKind,
        devices: List<Sighting>,
        path: List<GpsSample>,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        radios: LogExportRadios,
        versionName: String,
        deviceInfo: String,
    ): String {
        val custom = RadioBookmarks.labels(watchlist)
        val notes = RadioBookmarks.notes(watchlist)
        val mine = RadioBookmarks.mineKeys(watchlist)
        val extra = devices.filter { it.attentionNotes(fleets).isNotEmpty() }.map { it.key }.toSet()
        val rows = SitExport.rows(devices, radios)
        if (kind == LogExportKind.LOG_CSV || kind == LogExportKind.LOG_JSONL) {
            if (rows.isEmpty()) error(SitExport.emptyHint(kind, radios))
            return if (kind == LogExportKind.LOG_CSV) {
                SitExport.csv(devices, radios, custom, notes, extra, fleets, mine)
            } else {
                SitExport.jsonl(devices, radios, custom, notes, extra, fleets, mine)
            }
        }
        val pins = SitExport.mapRadios(devices, radios)
        if (pins.isEmpty()) error(SitExport.emptyHint(kind, radios))
        val fmt = GeoExport.formatOf(kind) ?: error(Automation.UNKNOWN_FORMAT)
        return GeoExport.render(
            fmt, pins, emptyMap(), versionName, deviceInfo,
            customNames = custom,
            observerNotes = notes,
            track = path,
        )
    }
}
