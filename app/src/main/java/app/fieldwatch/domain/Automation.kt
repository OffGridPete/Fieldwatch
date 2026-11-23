package app.fieldwatch.domain

import java.util.Locale

/**
 * Actions another app can send. Export sit writes the newest saved sit,
 * the one most recently stopped. An open sit and the last 15 minutes stay out.
 * Open is sent to MainActivity. The other actions go to AutomationService,
 * which writes automation/status.txt. Close leaves the scan running.
 */
object Automation {
    const val OPEN = "app.fieldwatch.OPEN"
    const val CLOSE = "app.fieldwatch.CLOSE"
    const val START_SCAN = "app.fieldwatch.START_SCAN"
    const val STOP_SCAN = "app.fieldwatch.STOP_SCAN"
    const val SCAN_STATUS = "app.fieldwatch.SCAN_STATUS"
    const val START_SIT = "app.fieldwatch.START_SIT"
    const val STOP_SIT = "app.fieldwatch.STOP_SIT"
    const val EXPORT_SIT = "app.fieldwatch.EXPORT_SIT"
    const val EXPORT_LOG = "app.fieldwatch.EXPORT_LOG"

    const val EXTRA_FORMAT = "app.fieldwatch.extra.FORMAT"
    const val EXTRA_NAME = "app.fieldwatch.extra.NAME"
    const val EXTRA_RADIOS = "app.fieldwatch.extra.RADIOS"

    const val ALREADY_OPEN = "A sit is already open."
    const val NONE_OPEN = "No sit is open."
    const val NONE_SAVED = "No saved sit."
    const val UNKNOWN_ACTION = "Unknown action."
    const val UNKNOWN_FORMAT = "Unknown format."
    const val SIT_FORMAT = "That format is for a sit."
    const val NO_LOG_GPS = "No GPS-tagged radios in the log."
    const val SCREEN_STAYED = "The screen stayed open."

    enum class Action {
        CLOSE,
        START_SCAN,
        STOP_SCAN,
        SCAN_STATUS,
        START_SIT,
        STOP_SIT,
        EXPORT_SIT,
        EXPORT_LOG,
    }

    fun action(raw: String?): Action? = when (raw) {
        CLOSE -> Action.CLOSE
        START_SCAN -> Action.START_SCAN
        STOP_SCAN -> Action.STOP_SCAN
        SCAN_STATUS -> Action.SCAN_STATUS
        START_SIT -> Action.START_SIT
        STOP_SIT -> Action.STOP_SIT
        EXPORT_SIT -> Action.EXPORT_SIT
        EXPORT_LOG -> Action.EXPORT_LOG
        else -> null
    }

    /** Blank means CSV. Unknown text is null. */
    fun format(raw: String?): LogExportKind? {
        val key = raw?.trim()?.lowercase(Locale.US).orEmpty()
        if (key.isEmpty()) return LogExportKind.LOG_CSV
        return when (key) {
            "csv" -> LogExportKind.LOG_CSV
            "jsonl", "json", "json lines" -> LogExportKind.LOG_JSONL
            "gpx" -> LogExportKind.GPX
            "kml" -> LogExportKind.KML
            "geojson" -> LogExportKind.GEOJSON
            "wigle" -> LogExportKind.WIGLE
            "atak" -> LogExportKind.ATAK
            else -> null
        }
    }

    fun allowsLog(kind: LogExportKind): Boolean =
        kind != LogExportKind.GEOJSON && kind != LogExportKind.ATAK

    fun radios(raw: String?): LogExportRadios = when (raw?.trim()?.lowercase(Locale.US)) {
        "wifi", "wi-fi" -> LogExportRadios.WIFI
        "ble", "bluetooth" -> LogExportRadios.BLE
        else -> LogExportRadios.BOTH
    }

    /** Newest closed sit. An open row is not saved yet. */
    fun newestSaved(closed: List<SitSummary>): SitSummary? =
        closed.filter { !it.open }.maxWithOrNull(
            compareBy<SitSummary> { it.endAt ?: Long.MIN_VALUE }.thenBy { it.startAt },
        )

    fun status(fields: Status): String = buildString {
        append("ok=").append(fields.ok).append('\n')
        append("action=").append(fields.action).append('\n')
        append("scanning=").append(fields.scanning).append('\n')
        append("sit=").append(oneLine(fields.sit)).append('\n')
        append("saved=").append(oneLine(fields.saved)).append('\n')
        append("file=").append(oneLine(fields.file)).append('\n')
        append("format=").append(oneLine(fields.format)).append('\n')
        append("error=").append(oneLine(fields.error)).append('\n')
    }

    private fun oneLine(raw: String): String = raw.replace('\n', ' ').replace('\r', ' ')

    data class Status(
        val ok: Boolean,
        val action: String,
        val scanning: Boolean,
        val sit: String = "",
        val saved: String = "",
        val file: String = "",
        val format: String = "",
        val error: String = "",
    )
}
