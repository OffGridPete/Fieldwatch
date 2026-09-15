package app.fieldwatch.domain

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RadioKind { WIFI, BLE }

@Serializable
enum class ViewMode {
    RADAR, LIST, TIMELINE, HYBRID, BY_CLASS,
    ;

    fun label(): String = when (this) {
        RADAR -> "Classic radar"
        LIST -> "Strength list"
        TIMELINE -> "Timeline"
        HYBRID -> "Hybrid + sparklines"
        BY_CLASS -> "By class"
    }
}

@Serializable
enum class ScanIntensity { SAVER, BALANCED, PERFORMANCE }

@Serializable
enum class LogFormat { CSV, JSON }

@Serializable
enum class FilterLogic { AND, OR }

/** Live filter / Signatures bulk on-off. Independent of [Fleet.colorIndex]. */
@Serializable
enum class SignatureClass {
    FINDER,
    BEACON,
    SIGNAGE,
    WEARABLE,
    SURVEILLANCE,
    DRONE,
    HACKING,
    BODYWORN,
    LAW_ENFORCEMENT,
    VEHICLE,
    GLASSES,
    AUDIO,
    CAMERA,
    THERMOSTAT,
    LOCK,
    HEALTH,
    HOME,
    ISP,
    MESH,
    PHONE,
    OTHER,
    ;

    fun label(): String = when (this) {
        FINDER -> "Finder tags"
        BEACON -> "Retail beacons"
        SIGNAGE -> "Signage"
        WEARABLE -> "Wearables"
        SURVEILLANCE -> "Surveillance"
        DRONE -> "Drones"
        HACKING -> "Pentest"
        BODYWORN -> "Body-worn"
        LAW_ENFORCEMENT -> "Public safety"
        VEHICLE -> "Vehicle"
        GLASSES -> "Glasses"
        AUDIO -> "Audio"
        CAMERA -> "Cameras"
        THERMOSTAT -> "Thermostats"
        LOCK -> "Locks"
        HEALTH -> "Health"
        HOME -> "Home IoT"
        ISP -> "ISP / routers"
        MESH -> "Mesh"
        PHONE -> "Phones / PCs"
        OTHER -> "Other"
    }

    /** Short spoken form for watchlist voice. */
    fun speechLabel(): String = when (this) {
        FINDER -> "finder tags"
        BEACON -> "retail beacons"
        SIGNAGE -> "signage"
        WEARABLE -> "wearables"
        SURVEILLANCE -> "surveillance"
        DRONE -> "drones"
        HACKING -> "pentest"
        BODYWORN -> "body worn"
        LAW_ENFORCEMENT -> "public safety"
        VEHICLE -> "vehicle"
        GLASSES -> "glasses"
        AUDIO -> "audio"
        CAMERA -> "cameras"
        THERMOSTAT -> "thermostats"
        LOCK -> "locks"
        HEALTH -> "health"
        HOME -> "home I O T"
        ISP -> "I S P routers"
        MESH -> "mesh"
        PHONE -> "phones"
        OTHER -> "other"
    }

    /**
     * Body-worn was a leftover bucket (Axon / WatchGuard moved to Public safety).
     * Fold it into Wearables so Filters / By class / the editor show one class.
     * The enum value stays so old config.json and signature packs still decode.
     */
    fun folded(): SignatureClass = if (this == BODYWORN) WEARABLE else this

    companion object {
        val visible: List<SignatureClass>
            get() = entries.filter { it != BODYWORN }
    }
}

/** What watchlist voice says. Class is the Live glyph bucket; signature is the catalog row. */
@Serializable
enum class AlertVoiceWhat {
    CLASS,
    SIGNATURE,
    BOTH,
    ;

    fun label(): String = when (this) {
        CLASS -> "Class"
        SIGNATURE -> "Signature"
        BOTH -> "Class + signature"
    }
}

fun Fleet.speechName(): String = speakableWatchName(name)

fun speakableWatchName(name: String): String =
    name.replace('/', ' ')
        .replace('·', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "unmatched" }

fun spokenWatchClass(
    device: Sighting,
    fleets: List<Fleet>,
    target: WatchTarget? = null,
): String = spokenWatchPhrase(device, fleets, AlertVoiceWhat.CLASS, target)

fun spokenWatchPhrase(
    device: Sighting,
    fleets: List<Fleet>,
    what: AlertVoiceWhat,
    target: WatchTarget? = null,
): String {
    if (target?.deviceKey != null) {
        val named = target.label.trim().takeIf {
            it.isNotEmpty() && !it.equals(device.mac, ignoreCase = true)
        } ?: device.listTitle().takeIf {
            it.isNotBlank() && !it.equals(device.mac, ignoreCase = true)
        } ?: "radio"
        return speakableWatchName(named)
    }
    val preferred = target?.fleetId?.takeIf { it in device.fleetIds }
    val id = preferred ?: device.fleetIds.firstOrNull() ?: return "unmatched"
    val fleet = fleets.firstOrNull { it.id == id } ?: return "unmatched"
    val cls = fleet.kind.speechLabel()
    val sig = fleet.speechName()
    return when (what) {
        AlertVoiceWhat.CLASS -> cls
        AlertVoiceWhat.SIGNATURE -> sig
        AlertVoiceWhat.BOTH -> if (cls.equals(sig, ignoreCase = true)) cls else "$cls, $sig"
    }
}

/** Test alert uses a Finder-tags / AirTags example so you can hear the mix without a live hit. */
fun testWatchPhrase(what: AlertVoiceWhat): String = when (what) {
    AlertVoiceWhat.CLASS -> SignatureClass.FINDER.speechLabel()
    AlertVoiceWhat.SIGNATURE -> speakableWatchName("Apple AirTags")
    AlertVoiceWhat.BOTH -> "${SignatureClass.FINDER.speechLabel()}, ${speakableWatchName("Apple AirTags")}"
}

@Serializable
enum class SignatureListSort {
    NAME, CLASS,
    ;

    fun label(): String = when (this) {
        NAME -> "Name A–Z"
        CLASS -> "Class A–Z"
    }
}

@Serializable
enum class StrengthSort { INSTANT, AVERAGE }

@Serializable
enum class ListSort { STRENGTH, NEWEST, NEWEST_ALERT, FIRST_SEEN, ARRIVAL, NAME, SIGNATURES }

/** What the Live list title or subtitle shows. NONE is subtitle-only. */
@Serializable
enum class ListLine {
    ADVERTISED_NAME,
    NAME_AND_TYPE,
    MAC,
    NONE,
}

enum class RssiTrend { UP_FAST, UP, FLAT, DOWN, DOWN_FAST, UNKNOWN }

@Serializable
enum class RuleKind {
    OUI,
    MAC_PREFIX,
    NAME_CONTAINS,
    NAME_GLOB,
    SERVICE_UUID,
    MANUFACTURER_ID,
    MANUFACTURER_DATA,
    RADIO_KIND,
    HIDDEN_SSID,
    VENDOR_IE_OUI,
}

@Serializable
data class MatchRule(
    val kind: RuleKind,
    val text: String = "",
    val companyId: Int = 0,
    val dataPrefixHex: String = "",
    val radio: RadioKind? = null,
    val enabled: Boolean = true,
)

@Serializable
data class Fleet(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val matchAny: Boolean = true,
    val colorIndex: Int = 0,
    val rules: List<MatchRule> = emptyList(),
    val minPeers: Int = 0,
    val peerWindowSec: Int = 60,
    val clusterByOui: Boolean = false,
    val sequentialMac: Boolean = false,
    val notes: String = "",
    /** Operator-facing caution. Empty = no Live mark, no Extra attention card, no Debrief line. Separate from [notes], which still show on radio detail. */
    val attentionNote: String = "",
    val builtIn: Boolean = false,
    val kind: SignatureClass = SignatureClass.OTHER,
    /** Optional BLE cleartext field map. Null = no signature decode. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val decode: FleetDecode? = null,
) {
    /** Decode fields only apply to BLE ads. Hide the editor on Wi-Fi-only signatures. */
    fun canHaveBleDecode(): Boolean {
        if (rules.isEmpty()) return true
        return rules.any { it.couldMatchBle() }
    }
}

fun MatchRule.couldMatchBle(): Boolean = when (kind) {
    RuleKind.HIDDEN_SSID, RuleKind.VENDOR_IE_OUI -> false
    RuleKind.SERVICE_UUID, RuleKind.MANUFACTURER_ID, RuleKind.MANUFACTURER_DATA -> true
    RuleKind.RADIO_KIND -> radio != RadioKind.WIFI
    else -> radio != RadioKind.WIFI
}

@Serializable
enum class DecodeSource {
    @SerialName("manufacturerData") MANUFACTURER_DATA,
    @SerialName("serviceData") SERVICE_DATA,
}

@Serializable
enum class DecodeType {
    @SerialName("u8") U8,
    @SerialName("i8") I8,
    @SerialName("u16") U16,
    @SerialName("i16") I16,
    @SerialName("u24") U24,
    @SerialName("u32") U32,
    @SerialName("i32") I32,
    @SerialName("f32") F32,
    @SerialName("bits") BITS,
    @SerialName("utf8") UTF8,
    @SerialName("hex") HEX,
    @SerialName("mac") MAC,
    @SerialName("bool") BOOL,
}

@Serializable
enum class DecodeEndian {
    @SerialName("le") LE,
    @SerialName("be") BE,
}

@Serializable
enum class DecodeWhenOp {
    @SerialName("eq") EQ,
    @SerialName("neq") NEQ,
    @SerialName("mask") MASK,
    /** Payload byte length equals [DecodeWhen.length]. offset/valueHex ignored. */
    @SerialName("len") LEN,
}

@Serializable
data class DecodeWhen(
    val offset: Int,
    val length: Int = 1,
    val op: DecodeWhenOp,
    val valueHex: String,
    /** Extra constraint; both must pass. Stock maps use this for length + prefix. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val and: DecodeWhen? = null,
)

@Serializable
data class DecodeField(
    val id: String,
    val label: String,
    val offset: Int,
    val length: Int? = null,
    val type: DecodeType,
    val endian: DecodeEndian = DecodeEndian.LE,
    val bitOffset: Int? = null,
    val bitWidth: Int? = null,
    val scale: Double? = null,
    val offsetAdd: Double? = null,
    /** Remainder after the integer/float read, before [scale]. Govee packed humidity is `% 1000`. */
    val modulo: Double? = null,
    val unit: String? = null,
    @SerialName("enum") val enumLabels: Map<String, String>? = null,
    @SerialName("when") val gate: DecodeWhen? = null,
)

@Serializable
data class FleetDecode(
    val source: DecodeSource,
    val serviceUuid: String? = null,
    val companyId: Int? = null,
    val fields: List<DecodeField> = emptyList(),
)

fun DecodeType.defaultLength(): Int = when (this) {
    DecodeType.U8, DecodeType.I8, DecodeType.BOOL -> 1
    DecodeType.U16, DecodeType.I16 -> 2
    DecodeType.U24 -> 3
    DecodeType.U32, DecodeType.I32, DecodeType.F32 -> 4
    DecodeType.MAC -> 6
    DecodeType.BITS, DecodeType.UTF8, DecodeType.HEX -> 1
}

fun DecodeField.resolvedLength(): Int {
    if (length != null && length > 0) return length
    if (type == DecodeType.BITS) {
        val start = bitOffset ?: 0
        val width = bitWidth ?: 1
        return ((start + width + 7) / 8).coerceAtLeast(1)
    }
    return type.defaultLength()
}

fun List<Fleet>.sortedForCatalog(sort: SignatureListSort): List<Fleet> = when (sort) {
    SignatureListSort.NAME -> sortedBy { it.name.lowercase() }
    SignatureListSort.CLASS -> sortedWith(
        compareBy({ it.kind.label().lowercase() }, { it.name.lowercase() }),
    )
}

fun List<Fleet>.groupedByClass(): List<Pair<SignatureClass, List<Fleet>>> =
    groupBy { it.kind }
        .toList()
        .sortedBy { it.first.label().lowercase() }
        .map { (kind, rows) -> kind to rows.sortedBy { it.name.lowercase() } }

/** Signature name + Extra attention text for every matched signature on this radio that has one. */
fun Sighting.attentionNotes(fleets: List<Fleet>): List<Pair<String, String>> {
    if (fleetIds.isEmpty()) return emptyList()
    val byId = fleets.associateBy { it.id }
    return fleetIds.mapNotNull { id ->
        val fleet = byId[id] ?: return@mapNotNull null
        val note = fleet.attentionNote.trim()
        if (note.isEmpty()) null else fleet.name to note
    }
}

/** Signature name + Notes for every matched signature on this radio that has notes. Not Extra attention. */
fun Sighting.signatureNotes(fleets: List<Fleet>): List<Pair<String, String>> {
    if (fleetIds.isEmpty()) return emptyList()
    val byId = fleets.associateBy { it.id }
    return fleetIds.mapNotNull { id ->
        val fleet = byId[id] ?: return@mapNotNull null
        val note = fleet.notes.trim()
        if (note.isEmpty()) null else fleet.name to note
    }
}

@Serializable
data class FilterState(
    val namedOnly: Boolean = false,
    /** Live include: only radios with a custom name (Settings → Named radios). */
    val customNamesOnly: Boolean = false,
    /** Live include: bookmarked signatures or Named radios with Alert on. */
    val watchedOnly: Boolean = false,
    val useFleetFilter: Boolean = false,
    val excludeSignatures: Boolean = false,
    val fleetIds: Set<String> = emptySet(),
    /** Live include: only radios matching these signature ids. Empty set = no extra include gate. */
    val includeSignatures: Boolean = false,
    val includeFleetIds: Set<String> = emptySet(),
    val showWifi: Boolean = true,
    val showBle: Boolean = true,
    val rssiMin: Int = -100,
    val nameQuery: String = "",
    val ouiQuery: String = "",
    val logic: FilterLogic = FilterLogic.AND,
    val movingWithYou: Boolean = false,
    /** Hide already-seen radios; Mark seen / Reset seen live with this switch. */
    val arrivalsOnly: Boolean = false,
    /**
     * Hide Fast Pair account-key-only radios (plaza noise). Pairing-mode and
     * dual-chip radios stay. Hide selected Fast Pair still drops pairing-mode too.
     */
    val hideFastPairAccountKey: Boolean = false,
    /** Include or hide Live rows by [Fleet.kind]. Independent of signature matching on/off. */
    val useClassFilter: Boolean = false,
    val excludeClasses: Boolean = false,
    val classes: Set<SignatureClass> = emptySet(),
) {
    /** Show only is narrowing Live to at least one picked class. Empty Show only does not hide unmatched. */
    fun classIncludeActive(): Boolean =
        useClassFilter && !excludeClasses && classes.isNotEmpty()

    fun signatureIncludeActive(): Boolean =
        includeSignatures && includeFleetIds.isNotEmpty()

    fun namedOnlyImplied(): Boolean = classIncludeActive() || signatureIncludeActive()
}

@Serializable
data class FilterPreset(
    val id: String,
    val name: String,
    val filter: FilterState,
) {
    fun isBuiltIn(): Boolean = id in BuiltInPresetIds
}

private val BuiltInPresetIds = setOf(
    "all",
    "wifi",
    "ble",
    "strong",
    "with-you",
    "trackers",
    "hide-trackers",
    "hide-phones",
    // Retired stock chips. Kept so an upgrade does not treat them as custom.
    "named",
    "surveillance",
    "drones",
    "beacons",
    "signage",
    "wearables",
    "pentest",
    "locks",
    "bodyworn",
    "vehicle",
    "glasses",
    "audio",
    "cameras",
)

@Serializable
data class WatchTarget(
    val id: String,
    val fleetId: String? = null,
    val deviceKey: String? = null,
    val label: String,
    val vibrate: Boolean = true,
    val notify: Boolean = true,
    /** Device-key rows only. False = named radio, no pip/voice/flash. Missing JSON = on. */
    val alert: Boolean = true,
)

@Serializable
data class AppSettings(
    val darkTheme: Boolean = true,
    val keepScreenOn: Boolean = true,
    val intensity: ScanIntensity = ScanIntensity.PERFORMANCE,
    val loggingEnabled: Boolean = true,
    val logFormat: LogFormat = LogFormat.CSV,
    val logRotateKb: Int = 1024,
    val staleSec: Int = 45,
    val alertsEnabled: Boolean = true,
    val alertBeep: Boolean = true,
    /** Speak after a watchlist alert. Off by default. Not Hunt. What to say is [alertVoiceWhat]. */
    val alertVoice: Boolean = false,
    /** Class (Live glyph), signature name, or both. Only used while [alertVoice] is on. */
    val alertVoiceWhat: AlertVoiceWhat = AlertVoiceWhat.CLASS,
    val snapToBeep: Boolean = true,
    val alertShade: Boolean = false,
    val tagLocation: Boolean = true,
    /** Reverse-geocode GPS stamps in Debrief when the phone is online. Default on. */
    val onlineLookup: Boolean = true,
    val viewMode: ViewMode = ViewMode.LIST,
    val strengthSort: StrengthSort = StrengthSort.AVERAGE,
    val averageWindowSec: Int = 30,
    val listSort: ListSort = ListSort.STRENGTH,
    val showRssiBar: Boolean = true,
    val showFleetName: Boolean = true,
    val showFrequency: Boolean = false,
    val showSeenTimes: Boolean = false,
    /** Live row first line. Default is MAC. */
    val listTitleLine: ListLine = ListLine.MAC,
    /** Live row second line. Default is name, else type guess. NONE hides the line. */
    val listSubtitleLine: ListLine = ListLine.NAME_AND_TYPE,
    /** Brief hold seconds (0 = Off). Linger only; does not decay RSSI. */
    val decaySec: Int = 10,
    val scanControlsExpanded: Boolean = true,
    /** Unused after catalog v5; kept so old JSON still decodes. Source of truth is FilterState.arrivalsOnly. */
    val arrivalsOnly: Boolean = false,
    val arrivalHoldSec: Int = 30,
    val detectSsidKeywords: Boolean = true,
    val detectKnownOuis: Boolean = true,
    val detectVendorIes: Boolean = true,
    val detectBleRaven: Boolean = true,
    /** On-screen and sit-report MAC tail and GPS mask. Does not change logs or matching. JSON key demoMode. */
    val demoMode: Boolean = false,
    /**
     * Ask for Wi-Fi AP scans faster than the stock ~30 s cadence.
     * Only takes effect while Android Wi-Fi scan throttling is off
     * (Developer options). Fieldwatch cannot flip that OS switch.
     */
    val wifiFastScan: Boolean = false,
    /** Hunt-page geiger tick. Off by default. Not the watchlist chirp. */
    val huntBeep: Boolean = false,
    val huntVibrate: Boolean = false,
    /**
     * UDP Cursor-on-Target markers for ATAK / WinTAK / iTAK. Off by default.
     * Privacy mode pauses the feed so full MACs and coordinates are not sent.
     */
    val takEnabled: Boolean = false,
    val takHost: String = TakDefaults.HOST,
    val takPort: Int = TakDefaults.PORT,
    /** Extra attention signatures (body-cam, glasses, pentest, public-safety APs). */
    val takAttention: Boolean = true,
    /**
     * Radios whose decode map has sticky latitude/longitude (stock Remote ID,
     * or a custom map using those field ids). Default on so Remote ID pins
     * without Extra attention.
     */
    val takPayloadFix: Boolean = true,
    val takWatchlist: Boolean = false,
    /** Every labeled signature. Noisy. Off by default. */
    val takAllSignatures: Boolean = false,
    /**
     * Red-on-black field display. Off by default. Overrides Dark theme while on.
     * Phone brightness is unchanged.
     */
    val nightMode: Boolean = false,
    /** First-run click-through. Scanning does not start until [disclaimerRev] matches [DISCLAIMER_REV]. */
    val disclaimerAccepted: Boolean = false,
    val disclaimerRev: Int = 0,
    /** Signatures tab: Name A–Z (default) or Class A–Z. */
    val signatureListSort: SignatureListSort = SignatureListSort.NAME,
    /** By class: hide class headers with 0 radios. Off = show all (zeros stay). */
    val outlineHideEmpty: Boolean = false,
)

const val DISCLAIMER_REV = 3

fun AppSettings.disclaimerOk(): Boolean = disclaimerRev >= DISCLAIMER_REV

data class DetectionPolicy(
    val ssidKeywords: Boolean = true,
    val knownOuis: Boolean = true,
    val vendorIes: Boolean = true,
    val bleRaven: Boolean = true,
)

fun AppSettings.detectionPolicy() = DetectionPolicy(
    ssidKeywords = detectSsidKeywords,
    knownOuis = detectKnownOuis,
    vendorIes = detectVendorIes,
    bleRaven = detectBleRaven,
)

@Serializable
data class PersistedConfig(
    val version: Int = 1,
    val fleets: List<Fleet> = emptyList(),
    val filter: FilterState = FilterState(),
    val presets: List<FilterPreset> = emptyList(),
    val watchlist: List<WatchTarget> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val arrivalKnownKeys: Set<String> = emptySet(),
    /** Stock filter-chip ids the operator removed. Catalog migrates do not put these back. */
    val hiddenPresetIds: Set<String> = emptySet(),
)

data class RssiSample(
    val at: Long,
    val rssi: Int,
)

data class GpsSample(
    val at: Long,
    val lat: Double,
    val lon: Double,
    val rssi: Int = 0,
)

data class PresenceSpan(
    val start: Long,
    var end: Long?,
)

data class Sighting(
    val key: String,
    val kind: RadioKind,
    val mac: String,
    val name: String,
    val rssi: Int,
    val rssiMin: Int,
    val rssiMax: Int,
    val channel: Int,
    val frequencyMhz: Int,
    val vendor: String?,
    val randomized: Boolean,
    val hiddenSsid: Boolean,
    val serviceUuids: List<String>,
    val manufacturerId: Int?,
    val manufacturerDataHex: String,
    val rawHex: String,
    val extras: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val hitCount: Int,
    val fleetIds: Set<String>,
    val rssiHistory: List<RssiSample>,
    val presence: List<PresenceSpan>,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gone: Boolean = false,
    val vendorIeOuis: List<String> = emptyList(),
    val facts: RadioFacts = RadioFacts.Empty,
    val gpsTrail: List<GpsSample> = emptyList(),
    /** Fast Pair 3-byte model ID seen this session. Stays if the payload later lengthens. */
    val fastPairPairing: Boolean = false,
    /** Sticky advertised WGS84 from decode field ids latitude/longitude. */
    val payloadLat: Double? = null,
    val payloadLon: Double? = null,
    val payloadAlt: Double? = null,
    /** Remote ID System operator (pilot) location. Not the aircraft pin. */
    val payloadOpLat: Double? = null,
    val payloadOpLon: Double? = null,
) {
    val displayName: String
        get() = name.ifBlank { if (hiddenSsid) "<hidden>" else mac }

    /**
     * Live first-line title. BLE does not repeat the MAC (that is the second line).
     * Advertised name, else the same guess as the detail page (vendor · type), else unnamed LE.
     */
    fun listTitle(signatureNames: List<String> = emptyList()): String {
        val advertised = name.trim()
        if (advertised.isNotEmpty() && !advertised.equals(mac, ignoreCase = true)) return advertised
        if (kind == RadioKind.WIFI) return if (hiddenSsid) "<hidden>" else mac
        return DeviceExplain.listLabel(this, signatureNames) ?: "unnamed LE"
    }

    /** SSID or BLE local name; placeholders if blank. */
    fun advertisedName(): String {
        val advertised = name.trim()
        if (advertised.isNotEmpty() && !advertised.equals(mac, ignoreCase = true)) return advertised
        if (kind == RadioKind.WIFI) return if (hiddenSsid) "<hidden>" else mac
        return "unnamed LE"
    }

    fun listLineText(
        line: ListLine,
        signatureNames: List<String> = emptyList(),
        watchName: String? = null,
    ): String {
        val custom = watchName?.trim()?.takeIf { it.isNotEmpty() }
        return when (line) {
            ListLine.ADVERTISED_NAME -> custom ?: advertisedName()
            ListLine.NAME_AND_TYPE -> custom ?: listTitle(signatureNames)
            ListLine.MAC -> mac
            ListLine.NONE -> ""
        }
    }

    fun radioKindTag(): String = if (kind == RadioKind.WIFI) "AP" else "LE"

    fun statusCrumbs(): String = buildString {
        if (randomized) append("rand")
        if (fastPairPairing) {
            if (isNotEmpty()) append("  ")
            append("pair")
        }
        if (gone) {
            if (isNotEmpty()) append("  ")
            append("gone")
        }
    }

    val oui: String
        get() = mac.take(8)

    fun averageRssi(windowMs: Long, now: Long = System.currentTimeMillis(), floor: Int = -100): Double {
        val from = now - windowMs
        val heard = rssiHistory.filter { it.at >= from }
        if (heard.isNotEmpty()) return heard.map { it.rssi }.average()
        return rssi.toDouble().coerceAtLeast(floor.toDouble())
    }

    fun rssiTrend(count: Int = 12): RssiTrend {
        val samples = rssiHistory.takeLast(count.coerceAtLeast(4))
        if (samples.size < 4) return RssiTrend.UNKNOWN
        val mid = samples.size / 2
        val older = samples.take(mid).map { it.rssi }.average()
        val newer = samples.drop(mid).map { it.rssi }.average()
        val delta = newer - older
        return when {
            delta >= 8.0 -> RssiTrend.UP_FAST
            delta >= 3.0 -> RssiTrend.UP
            delta <= -8.0 -> RssiTrend.DOWN_FAST
            delta <= -3.0 -> RssiTrend.DOWN
            else -> RssiTrend.FLAT
        }
    }

    fun heardRssi(sort: StrengthSort, windowMs: Long, now: Long = System.currentTimeMillis()): Double {
        return if (sort == StrengthSort.AVERAGE) averageRssi(windowMs, now) else rssi.toDouble()
    }

    fun sortRssi(
        sort: StrengthSort,
        windowMs: Long,
        now: Long = System.currentTimeMillis(),
    ): Double = heardRssi(sort, windowMs, now)
}

data class Observation(
    val kind: RadioKind,
    val mac: String,
    val name: String,
    val rssi: Int,
    val channel: Int,
    val frequencyMhz: Int,
    val hiddenSsid: Boolean,
    val serviceUuids: List<String>,
    val manufacturerId: Int?,
    val manufacturerDataHex: String,
    val rawHex: String,
    val extras: String,
    val at: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fresh: Boolean = true,
    val vendorIeOuis: List<String> = emptyList(),
    val facts: RadioFacts = RadioFacts.Empty,
)

data class ScanStats(
    val wifiFrames: Long = 0,
    val bleAdvs: Long = 0,
    val devicesSeen: Int = 0,
    val namedNow: Int = 0,
    val wifiNow: Int = 0,
    val bleNow: Int = 0,
    val logLines: Long = 0,
    val scanning: Boolean = false,
    val lastWifiScanAt: Long = 0,
    val throttleHint: String = "",
)

object MacUtil {
    fun normalize(raw: String): String {
        val hex = raw.filter { it.isLetterOrDigit() }.uppercase()
        if (hex.length < 2) return raw.uppercase()
        return hex.chunked(2).joinToString(":")
    }

    fun prefixBytes(mac: String, n: Int = 3): String {
        val parts = normalize(mac).split(":")
        return parts.take(n.coerceAtMost(parts.size)).joinToString(":")
    }

    fun isRandomized(mac: String): Boolean {
        val first = normalize(mac).substringBefore(":").toIntOrNull(16) ?: return false
        return (first and 0x02) != 0 && (first and 0x01) == 0
    }

    /**
     * 24-bit OUI with the locally-administered bit cleared. Virtual BSSIDs
     * (guest / mesh / extra SSID) often set that bit on the burned-in vendor
     * prefix. Null if the address is already universal, multicast, or short.
     * Live still treats the original MAC as randomized.
     */
    fun wifiOui24Universal(mac: String): String? {
        val hex = normalize(mac).replace(":", "")
        if (hex.length < 6) return null
        val first = hex.substring(0, 2).toIntOrNull(16) ?: return null
        if ((first and 0x02) == 0) return null
        if ((first and 0x01) != 0) return null
        return "%02X".format(first and 0xFD) + hex.substring(2, 6)
    }

    fun matchesPrefix(mac: String, prefix: String): Boolean {
        val m = normalize(mac).replace(":", "")
        val p = prefix.filter { it.isLetterOrDigit() }.uppercase()
        if (p.isEmpty()) return false
        return m.startsWith(p)
    }

    fun last16(mac: String): Int {
        val hex = normalize(mac).replace(":", "")
        if (hex.length < 4) return 0
        return hex.takeLast(4).toIntOrNull(16) ?: 0
    }

    /** Last three octets as **:**:**. OUI stays. Empty / short strings unchanged. */
    fun screenMac(mac: String, demo: Boolean): String {
        if (!demo || mac.isBlank()) return mac
        val parts = normalize(mac).split(":").filter { it.isNotEmpty() }
        if (parts.size < 4) return mac
        val keep = parts.size - 3
        return parts.take(keep).joinToString(":") + ":**:**:**"
    }

    fun redactMacIn(text: String, mac: String, demo: Boolean): String {
        if (!demo || text.isEmpty() || mac.isBlank()) return text
        val masked = screenMac(mac, true)
        val n = normalize(mac)
        var out = text
        if (out.contains(mac, ignoreCase = true)) out = out.replace(mac, masked, ignoreCase = true)
        if (n != mac && out.contains(n, ignoreCase = true)) out = out.replace(n, masked, ignoreCase = true)
        val compact = n.replace(":", "")
        if (compact.length >= 12) {
            val maskedCompact = masked.replace(":", "")
            out = out.replace(compact, maskedCompact, ignoreCase = true)
        }
        return out
    }

    fun redactMacsIn(text: String, macs: Collection<String>, demo: Boolean): String {
        if (!demo || text.isEmpty()) return text
        var out = text
        macs.distinct().forEach { mac ->
            if (mac.isNotBlank()) out = redactMacIn(out, mac, true)
        }
        return out
    }
}

object TextMatch {
    fun contains(hay: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        return hay.contains(needle, ignoreCase = true)
    }

    fun glob(text: String, pattern: String): Boolean {
        if (pattern.isBlank()) return false
        val regex = buildString {
            append('^')
            pattern.forEach { ch ->
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(ch.toString()))
                }
            }
            append('$')
        }.toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(text)
    }
}

object Palette {
    val fleet = listOf(
        0xFF3DFF9A.toInt(),
        0xFFFFB020.toInt(),
        0xFFFF3D5A.toInt(),
        0xFF4FC3F7.toInt(),
        0xFFB388FF.toInt(),
        0xFFFF8A4C.toInt(),
        0xFFE8EEF2.toInt(),
        0xFF3D8B6E.toInt(),
        0xFF5BA3D9.toInt(),
    )

    fun color(index: Int): Int = fleet[index.mod(fleet.size)]
}

fun RadioKind.label(): String = if (this == RadioKind.WIFI) "Wi-Fi" else "BLE"

fun uuidShortOrFull(raw: String): String {
    val hex = raw.filter { it.isLetterOrDigit() }.uppercase()
    return if (hex.length == 4) {
        "0000$hex-0000-1000-8000-00805F9B34FB"
    } else {
        raw.uppercase()
    }
}

fun uuidAliases(raw: String): Set<String> {
    val hex = raw.filter { it.isLetterOrDigit() }.uppercase()
    val out = mutableSetOf(raw.uppercase(), hex)
    if (hex.length == 4) {
        out += "0x$hex"
        out += "0000$hex-0000-1000-8000-00805F9B34FB"
        out += "0000${hex}00001000800000805F9B34FB"
        return out
    }
    // 16-bit alias only for Bluetooth SIG base UUIDs (0000XXXX-0000-1000-8000-00805F9B34FB).
    if (hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB")) {
        val short = hex.substring(4, 8)
        out += short
        out += "0x$short"
        out += "0000$short-0000-1000-8000-00805F9B34FB"
    }
    return out
}
