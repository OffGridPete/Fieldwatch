package app.fieldwatch.domain

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
enum class RadioKind { WIFI, BLE }

@Serializable
enum class ViewMode {
    RADAR, LIST, TIMELINE, HYBRID, BY_CLASS,
    ;

    fun label(): String = when (this) {
        RADAR -> LanguageText.text("view.radar")
        LIST -> LanguageText.text("view.list")
        TIMELINE -> LanguageText.text("view.timeline")
        HYBRID -> LanguageText.text("view.hybrid")
        BY_CLASS -> LanguageText.text("view.by.class")
    }

    /** Title, subtitle, frequency, first/last, and the live path are list fields. Radar has none of them. */
    fun usesListFields(): Boolean = this != RADAR

    /** Strength bar on Strength list, Hybrid, and By class. Radar and Timeline show strength another way. */
    fun usesRssiBars(): Boolean = this == LIST || this == HYBRID || this == BY_CLASS
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
        FINDER -> LanguageText.text("class.finder")
        BEACON -> LanguageText.text("class.beacon")
        SIGNAGE -> LanguageText.text("class.signage")
        WEARABLE -> LanguageText.text("class.wearable")
        SURVEILLANCE -> LanguageText.text("class.surveillance")
        DRONE -> LanguageText.text("class.drone")
        HACKING -> LanguageText.text("class.hacking")
        BODYWORN -> LanguageText.text("class.bodyworn")
        LAW_ENFORCEMENT -> LanguageText.text("class.law")
        VEHICLE -> LanguageText.text("class.vehicle")
        GLASSES -> LanguageText.text("class.glasses")
        AUDIO -> LanguageText.text("class.audio")
        CAMERA -> LanguageText.text("class.camera")
        THERMOSTAT -> LanguageText.text("class.thermostat")
        LOCK -> LanguageText.text("class.lock")
        HEALTH -> LanguageText.text("class.health")
        HOME -> LanguageText.text("class.home")
        ISP -> LanguageText.text("class.isp")
        MESH -> LanguageText.text("class.mesh")
        PHONE -> LanguageText.text("class.phone")
        OTHER -> LanguageText.text("class.other")
    }

    /** Short spoken form for watchlist voice. Uses the control slot, not the chip cap. */
    fun speechLabel(): String = when (this) {
        FINDER -> LanguageText.text("speech.finder")
        BEACON -> LanguageText.text("speech.beacon")
        SIGNAGE -> LanguageText.text("speech.signage")
        WEARABLE -> LanguageText.text("speech.wearable")
        SURVEILLANCE -> LanguageText.text("speech.surveillance")
        DRONE -> LanguageText.text("speech.drone")
        HACKING -> LanguageText.text("speech.hacking")
        BODYWORN -> LanguageText.text("speech.bodyworn")
        LAW_ENFORCEMENT -> LanguageText.text("speech.law")
        VEHICLE -> LanguageText.text("speech.vehicle")
        GLASSES -> LanguageText.text("speech.glasses")
        AUDIO -> LanguageText.text("speech.audio")
        CAMERA -> LanguageText.text("speech.camera")
        THERMOSTAT -> LanguageText.text("speech.thermostat")
        LOCK -> LanguageText.text("speech.lock")
        HEALTH -> LanguageText.text("speech.health")
        HOME -> LanguageText.text("speech.home")
        ISP -> LanguageText.text("speech.isp")
        MESH -> LanguageText.text("speech.mesh")
        PHONE -> LanguageText.text("speech.phone")
        OTHER -> LanguageText.text("speech.other")
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
        CLASS -> LanguageText.text("voice.class")
        SIGNATURE -> LanguageText.text("voice.signature")
        BOTH -> LanguageText.text("voice.both")
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
    spokenNames: List<SpokenName> = emptyList(),
    watchlist: List<WatchTarget> = emptyList(),
): String {
    val individual = watchlist.firstOrNull { row ->
        row.deviceKey == device.key &&
            row.label.trim().isNotEmpty() &&
            !row.label.trim().equals(device.mac, ignoreCase = true)
    } ?: target?.takeIf { it.deviceKey != null }
    if (individual?.deviceKey != null) {
        val named = individual.label.trim().takeIf {
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
    val sig = if (what == AlertVoiceWhat.CLASS) {
        fleet.speechName()
    } else {
        SpokenNames.words(spokenNames, id, device.name, device.mac) ?: fleet.speechName()
    }
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
        NAME -> LanguageText.text("sort.name")
        CLASS -> LanguageText.text("sort.class")
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
    SERVICE_DATA,
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
    RuleKind.SERVICE_UUID, RuleKind.SERVICE_DATA, RuleKind.MANUFACTURER_ID, RuleKind.MANUFACTURER_DATA -> true
    RuleKind.RADIO_KIND -> radio != RadioKind.WIFI
    else -> radio != RadioKind.WIFI
}

@Serializable(with = DecodeSourceSerializer::class)
enum class DecodeSource {
    @SerialName("manufacturerData") MANUFACTURER_DATA,
    @SerialName("serviceData") SERVICE_DATA,
    /**
     * Pack named a source this APK does not parse. Never written.
     * [SignatureExchange.parsePack] drops the decode map and keeps the signature.
     */
    UNSUPPORTED,
}

object DecodeSourceSerializer : KSerializer<DecodeSource> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DecodeSource", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DecodeSource) {
        encoder.encodeString(
            when (value) {
                DecodeSource.MANUFACTURER_DATA -> "manufacturerData"
                DecodeSource.SERVICE_DATA -> "serviceData"
                DecodeSource.UNSUPPORTED -> "manufacturerData"
            },
        )
    }

    override fun deserialize(decoder: Decoder): DecodeSource =
        when (decoder.decodeString()) {
            "manufacturerData" -> DecodeSource.MANUFACTURER_DATA
            "serviceData" -> DecodeSource.SERVICE_DATA
            else -> DecodeSource.UNSUPPORTED
        }
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
    /** Inverse of [MASK]: every 1-bit in Hex must be clear in the payload slice. */
    @SerialName("nmask") NMASK,
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
    /**
     * Show this field's decoded label on the live row. Any signature can set it.
     * The list reads a value stored when the advertisement bytes change.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val live: Boolean = false,
    /**
     * Raw enum keys whose label uses the stronger live chip.
     * Empty means every live value uses the quiet chip.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val liveEmphasis: List<String> = emptyList(),
    /** Sentence for a named value, keyed like [enumLabels]. Detail shows it when that value is decoded. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val enumNotes: Map<String, String>? = null,
    @SerialName("when") val gate: DecodeWhen? = null,
)

@Serializable
data class FleetDecode(
    val source: DecodeSource,
    val serviceUuid: String? = null,
    val companyId: Int? = null,
    /**
     * Prepend the two-byte little-endian company ID to manufacturer data
     * before the field map runs. SYTPMS/BR stores status and battery there.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val includeCompanyId: Boolean = false,
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
    /** Live include: stable radios already heard at two or more places. */
    val seenAtPlaces: Boolean = false,
    /** Hide already-seen radios; Mark seen / Reset seen live with this switch. */
    val arrivalsOnly: Boolean = false,
    /**
     * Hide Fast Pair account-key-only radios (plaza noise). Pairing-mode and
     * dual-chip radios stay. Hide selected Fast Pair still drops pairing-mode too.
     */
    val hideFastPairAccountKey: Boolean = false,
    /** Include or hide Live rows by [Fleet.kind]. Off keeps [classes] and [excludeClasses]. */
    val useClassFilter: Boolean = false,
    val excludeClasses: Boolean = false,
    val classes: Set<SignatureClass> = emptySet(),
    /** Live hide: radios marked Mine. The sit and Debrief still include them. */
    val hideMine: Boolean = false,
    /**
     * Live hide: Bluetooth rows whose address can change.
     * That is the local bit, or an address Android calls random.
     * Wi-Fi stays. The sit still keeps them.
     */
    val hideRandomizedBle: Boolean = false,
) {
    /** Show only is narrowing Live to at least one picked class. Empty Show only does not hide unmatched. */
    fun classIncludeActive(): Boolean =
        useClassFilter && !excludeClasses && classes.isNotEmpty()

    fun signatureIncludeActive(): Boolean =
        includeSignatures && includeFleetIds.isNotEmpty()

    fun namedOnlyImplied(): Boolean = classIncludeActive() || signatureIncludeActive()

    /** Keep or hide row. On when any of its switches is on. */
    fun whoStaysOn(): Boolean =
        namedOnly || customNamesOnly || watchedOnly || hideMine ||
            hideFastPairAccountKey || hideRandomizedBle

    /**
     * Selected signatures is one list.
     * Show only is [includeSignatures]. Hide these is [excludeSignatures] while show is off.
     * An old save with both lists on counts as Show only.
     */
    fun signatureShowOn(): Boolean = includeSignatures

    fun signatureHideOn(): Boolean = excludeSignatures && !includeSignatures

    /** Selected signatures row. On when Show only or Hide these is on. */
    fun signaturesOn(): Boolean = signatureShowOn() || signatureHideOn()

    /** The one pick list. Show wins when an old save had two different lists. */
    fun selectedSignatureIds(): Set<String> = when {
        includeSignatures -> includeFleetIds
        excludeSignatures -> fleetIds
        includeFleetIds.isNotEmpty() -> includeFleetIds
        else -> fleetIds
    }

    /** Show only chip. Tap again to turn the mode off. Picks stay. */
    fun toggleSignatureShow(): FilterState {
        val ids = selectedSignatureIds()
        val on = !signatureShowOn()
        return copy(
            includeSignatures = on,
            excludeSignatures = false,
            includeFleetIds = ids,
            fleetIds = ids,
        )
    }

    /** Hide these chip. Tap again to turn the mode off. Picks stay. */
    fun toggleSignatureHide(): FilterState {
        val ids = selectedSignatureIds()
        val on = !signatureHideOn()
        return copy(
            includeSignatures = false,
            excludeSignatures = on,
            includeFleetIds = ids,
            fleetIds = ids,
        )
    }

    /** One signature row. Adding a pick while the mode is off turns Show only on. */
    fun toggleSignaturePick(id: String, checked: Boolean): FilterState {
        val ids = selectedSignatureIds().toMutableSet().apply {
            if (checked) add(id) else remove(id)
        }
        val show = signatureShowOn()
        val hide = signatureHideOn()
        val turnShowOn = checked && !show && !hide
        return copy(
            includeFleetIds = ids,
            fleetIds = ids,
            includeSignatures = show || turnShowOn,
            excludeSignatures = if (turnShowOn) false else hide,
        )
    }

    /** Signal, name, MAC, vendor row. On when RSSI, name, vendor, or OR is set. */
    fun fineOn(): Boolean =
        rssiMin > -100 || nameQuery.isNotBlank() || ouiQuery.isNotBlank() || logic == FilterLogic.OR

    fun clearWhoStays(): FilterState = copy(
        namedOnly = false,
        customNamesOnly = false,
        watchedOnly = false,
        hideMine = false,
        hideFastPairAccountKey = false,
        hideRandomizedBle = false,
    )

    fun clearClasses(): FilterState = copy(
        useClassFilter = false,
        excludeClasses = false,
        classes = emptySet(),
    )

    fun clearSelectedSignatures(): FilterState = copy(
        includeSignatures = false,
        excludeSignatures = false,
        includeFleetIds = emptySet(),
        fleetIds = emptySet(),
    )

    fun clearFine(): FilterState = copy(
        rssiMin = -100,
        nameQuery = "",
        ouiQuery = "",
        logic = FilterLogic.AND,
    )
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
    "watched",
    // Retired stock chips. Kept so an upgrade does not treat them as custom.
    "trackers",
    "hide-trackers",
    "hide-phones",
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
    /** Operator caption on this MAC. Empty = none. */
    val observerNotes: String = "",
    /** This radio is the operator's. Missing JSON = off. Alert is left as last set. */
    val mine: Boolean = false,
)

/**
 * Raster under Show live path, Reports → Path, and the path figure.
 * Missing JSON is [OSM]. Online place names and maps is the on/off.
 * OpenStreetMap, the USGS maps, and FAA VFR Sectional need no key.
 * The others take a key the user keeps on this phone. That key is not part of this setting.
 */
@Serializable
enum class MapBasemap {
    OSM,
    USGS_IMAGERY,
    USGS_TOPO,
    USGS_IMAGERY_TOPO,
    USGS_SHADED_RELIEF,
    USGS_HYDRO,
    FAA_VFR_SECTIONAL,
    ESRI_SATELLITE,
    ESRI_STREETS,
    ESRI_OUTDOOR,
    MAPTILER_SATELLITE,
    MAPTILER_STREETS,
    MAPTILER_OUTDOOR,
    STADIA_SATELLITE,
    STADIA_STREETS,
    STADIA_TERRAIN,
    THUNDERFOREST_STREETS,
    THUNDERFOREST_OUTDOORS,
    THUNDERFOREST_TRANSPORT,
    ;

    private data class Spec(
        val labelKey: String,
        val cacheName: String,
        val maxZoom: Int,
        val service: MapKeyService?,
        val host: String,
        val keyQuery: String?,
        val url: (Int, Int, Int) -> String,
        val credit: String,
        val creditPrint: String,
        val creditUrl: String?,
        val redirectRoots: List<String>,
        val minZoom: Int = 0,
        val usOnly: Boolean = false,
        val cautionKey: String? = null,
    )

    private val spec: Spec
        get() = when (this) {
            OSM -> Spec(
                labelKey = "map.openstreetmap",
                cacheName = "osm",
                maxZoom = 19,
                service = null,
                host = "tile.openstreetmap.org",
                keyQuery = null,
                url = { z, x, y -> "https://tile.openstreetmap.org/$z/$x/$y.png" },
                credit = "© OpenStreetMap contributors",
                creditPrint = "© OpenStreetMap contributors · openstreetmap.org/copyright",
                creditUrl = "https://www.openstreetmap.org/copyright",
                redirectRoots = listOf("openstreetmap.org"),
            )
            USGS_IMAGERY -> usgs(
                "map.usgs.imagery",
                "usgs-imagery",
                "USGSImageryOnly",
            )
            USGS_TOPO -> usgs(
                "map.usgs.topo",
                "usgs-topo",
                "USGSTopo",
            )
            USGS_IMAGERY_TOPO -> usgs(
                "map.usgs.imagerytopo",
                "usgs-imagerytopo",
                "USGSImageryTopo",
            )
            USGS_SHADED_RELIEF -> usgs(
                "map.usgs.relief",
                "usgs-relief",
                "USGSShadedReliefOnly",
                "U.S. Geological Survey (3DEP)",
            )
            USGS_HYDRO -> usgs(
                "map.usgs.hydro",
                "usgs-hydro",
                "USGSHydroCached",
                "U.S. Geological Survey (NHD)",
            )
            FAA_VFR_SECTIONAL -> Spec(
                labelKey = "map.faa.vfr",
                cacheName = "faa-vfr",
                maxZoom = 12,
                service = null,
                host = "tiles.arcgis.com",
                keyQuery = null,
                url = { z, x, y ->
                    "https://tiles.arcgis.com/tiles/ssFJjBXIUyZDrSYZ/arcgis/rest/services/" +
                        "VFR_Sectional/MapServer/tile/$z/$y/$x"
                },
                credit = "Federal Aviation Administration",
                creditPrint = "Federal Aviation Administration. Not for navigation. Charts update every 56 days.",
                creditUrl = "https://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/vfr/",
                redirectRoots = listOf("arcgis.com"),
                minZoom = 8,
                usOnly = true,
                cautionKey = "map.faa.not.for.navigation",
            )
            ESRI_SATELLITE -> esri(
                "map.esri.satellite",
                "esri-satellite",
                "imagery-labels",
                "© Esri, Maxar, Earthstar Geographics",
            )
            ESRI_STREETS -> esri(
                "map.esri.streets",
                "esri-streets",
                "streets",
                "© Esri, TomTom, Garmin, OSM",
            )
            ESRI_OUTDOOR -> esri(
                "map.esri.outdoor",
                "esri-outdoor",
                "outdoor",
                "© Esri, TomTom, Garmin, OSM",
            )
            MAPTILER_SATELLITE -> maptiler(
                "map.maptiler.satellite",
                "maptiler-satellite",
                "satellite-v4",
                "jpg",
                "© MapTiler",
            )
            MAPTILER_STREETS -> maptiler(
                "map.maptiler.streets",
                "maptiler-streets",
                "streets-v4",
                "png",
                "© MapTiler © OpenStreetMap",
            )
            MAPTILER_OUTDOOR -> maptiler(
                "map.maptiler.outdoor",
                "maptiler-outdoor",
                "outdoor-v4",
                "png",
                "© MapTiler © OpenStreetMap",
            )
            STADIA_SATELLITE -> stadia(
                "map.stadia.satellite",
                "stadia-satellite",
                "alidade_satellite",
                "jpg",
                20,
                "© Stadia Maps, CNES, Airbus DS, OSM",
            )
            STADIA_STREETS -> stadia(
                "map.stadia.streets",
                "stadia-streets",
                "alidade_smooth",
                "png",
                20,
                "© Stadia Maps © OpenMapTiles © OSM",
            )
            STADIA_TERRAIN -> stadia(
                "map.stadia.terrain",
                "stadia-terrain",
                "stamen_terrain",
                "png",
                18,
                "© Stadia Maps © Stamen Design © OSM",
            )
            THUNDERFOREST_STREETS -> thunderforest(
                "map.thunderforest.streets",
                "thunderforest-streets",
                "atlas",
            )
            THUNDERFOREST_OUTDOORS -> thunderforest(
                "map.thunderforest.outdoors",
                "thunderforest-outdoors",
                "outdoors",
            )
            THUNDERFOREST_TRANSPORT -> thunderforest(
                "map.thunderforest.transport",
                "thunderforest-transport",
                "transport",
            )
        }

    val labelKey: String get() = spec.labelKey

    /** One directory per style so a switch does not reuse another map's pictures. */
    val cacheName: String get() = spec.cacheName

    /** Highest zoom this service actually serves. Screen and print stay at or under it. */
    val maxZoom: Int get() = spec.maxZoom

    /** Lowest zoom this service serves. A coarser view asks the user to zoom in. */
    val minZoom: Int get() = spec.minZoom

    /** United States coverage. The picker says so. The list itself does not hide the row. */
    val usOnly: Boolean get() = spec.usOnly

    /** Extra corner phrase, localized. Null on every map except FAA VFR Sectional. */
    val cautionKey: String? get() = spec.cautionKey

    /** Second line in the Map list. Empty on a world map. */
    val subtitleKeys: List<String>
        get() = buildList {
            if (usOnly) add("map.united.states")
            cautionKey?.let { add(it) }
        }

    /** Null on OpenStreetMap, the USGS maps, and FAA VFR Sectional. One saved key covers every style of a service. */
    val service: MapKeyService? get() = spec.service

    val needsKey: Boolean get() = spec.service != null

    val host: String get() = spec.host

    /** Query name for the key. Null when [needsKey] is false. */
    val keyQuery: String? get() = spec.keyQuery

    /** Hosts a tile redirect may use. The key is not forwarded. */
    val redirectRoots: List<String> get() = spec.redirectRoots

    /** Picture address with no key. Slippy x/y. USGS and Esri serve z/y/x. */
    fun tileUrl(z: Int, x: Int, y: Int): String = spec.url(z, x, y)

    internal fun fixedTileUrl(z: Int, x: Int, y: Int): String = tileUrl(z, x, y)

    /** Corner line on the phone. The OpenStreetMap phrase stays this English. */
    val credit: String get() = spec.credit

    /**
     * Corner line with the localized FAA caution appended, then any overlay credits.
     * Other maps ignore [localizedCaution] and keep [credit].
     */
    fun screenCredit(
        localizedCaution: String?,
        overlays: Collection<MapOverlay> = emptyList(),
    ): String = screenCreditLines(localizedCaution, overlays).joinToString(" · ")

    /**
     * One line for the map, then one line for each overlay.
     * An empty overlay list is the map line alone.
     */
    fun screenCreditLines(
        localizedCaution: String?,
        overlays: Collection<MapOverlay> = emptyList(),
    ): List<String> {
        val base = if (cautionKey == null) {
            credit
        } else {
            val phrase = localizedCaution?.trim().orEmpty()
            if (phrase.isEmpty()) credit else "$credit. $phrase"
        }
        val extras = MapOverlay.ordered(overlays).map { it.basemap.credit }
        return if (extras.isEmpty()) listOf(base) else listOf(base) + extras
    }

    /** Screen lines, with the sensor-list credit on the map line when that layer is on. */
    fun shownCreditLines(
        localizedCaution: String?,
        overlays: Collection<MapOverlay>,
        includeSensorCredit: Boolean,
    ): List<String> {
        val parts = screenCreditLines(localizedCaution, overlays)
        return listOf(FlockCredit.append(parts.first(), includeSensorCredit)) + parts.drop(1)
    }

    /** Printed line. A PDF has no link, so OpenStreetMap names the license page. */
    val creditPrint: String get() = spec.creditPrint

    /** Printed line plus the overlay sources actually drawn on that picture. */
    fun creditLine(overlays: Collection<MapOverlay> = emptyList()): String =
        MapOverlay.joinCredit(creditPrint, overlays)

    /** Shaded relief and hydrography are overlays, not rows in the Map list. */
    val inPicker: Boolean
        get() = this != USGS_SHADED_RELIEF && this != USGS_HYDRO

    val creditUrl: String? get() = spec.creditUrl

    private fun usgs(
        labelKey: String,
        cacheName: String,
        serviceName: String,
        credit: String = "U.S. Geological Survey",
    ) = Spec(
        labelKey = labelKey,
        cacheName = cacheName,
        maxZoom = 16,
        service = null,
        host = "basemap.nationalmap.gov",
        keyQuery = null,
        url = { z, x, y ->
            "https://basemap.nationalmap.gov/arcgis/rest/services/$serviceName/MapServer/tile/$z/$y/$x"
        },
        credit = credit,
        creditPrint = credit,
        creditUrl = null,
        redirectRoots = listOf("nationalmap.gov", "usgs.gov"),
        usOnly = true,
    )

    private fun esri(labelKey: String, cacheName: String, style: String, credit: String) = Spec(
        labelKey = labelKey,
        cacheName = cacheName,
        maxZoom = 22,
        service = MapKeyService.ESRI,
        host = "static-map-tiles-api.arcgis.com",
        keyQuery = "token",
        url = { z, x, y ->
            "https://static-map-tiles-api.arcgis.com/arcgis/rest/services/" +
                "static-basemap-tiles-service/v1/arcgis/$style/static/tile/$z/$y/$x"
        },
        credit = credit,
        creditPrint = "$credit · static-map-tiles-api.arcgis.com",
        creditUrl = "https://www.esri.com/en-us/legal/terms/data-attributions",
        redirectRoots = listOf("arcgis.com", "arcgisonline.com"),
    )

    private fun maptiler(
        labelKey: String,
        cacheName: String,
        style: String,
        format: String,
        credit: String,
    ) = Spec(
        labelKey = labelKey,
        cacheName = cacheName,
        maxZoom = 22,
        service = MapKeyService.MAPTILER,
        host = "api.maptiler.com",
        keyQuery = "key",
        url = { z, x, y -> "https://api.maptiler.com/maps/$style/256/$z/$x/$y.$format" },
        credit = credit,
        creditPrint = "$credit · maptiler.com/copyright",
        creditUrl = "https://www.maptiler.com/copyright/",
        redirectRoots = listOf("maptiler.com"),
    )

    private fun stadia(
        labelKey: String,
        cacheName: String,
        style: String,
        format: String,
        maxZoom: Int,
        credit: String,
    ) = Spec(
        labelKey = labelKey,
        cacheName = cacheName,
        maxZoom = maxZoom,
        service = MapKeyService.STADIA,
        host = "tiles.stadiamaps.com",
        keyQuery = "api_key",
        url = { z, x, y -> "https://tiles.stadiamaps.com/tiles/$style/$z/$x/$y.$format" },
        credit = credit,
        creditPrint = "$credit · stadiamaps.com/attribution",
        creditUrl = "https://stadiamaps.com/attribution/",
        redirectRoots = listOf("stadiamaps.com"),
    )

    private fun thunderforest(labelKey: String, cacheName: String, style: String) = Spec(
        labelKey = labelKey,
        cacheName = cacheName,
        maxZoom = 22,
        service = MapKeyService.THUNDERFOREST,
        host = "api.thunderforest.com",
        keyQuery = "apikey",
        url = { z, x, y -> "https://api.thunderforest.com/$style/$z/$x/$y.png" },
        credit = "© Thunderforest © OpenStreetMap",
        creditPrint = "© Thunderforest © OpenStreetMap · thunderforest.com",
        creditUrl = "https://www.thunderforest.com/",
        redirectRoots = listOf("thunderforest.com"),
    )
}

/**
 * Pictures drawn on top of the chosen map. None is the start.
 * Relief is a gray hillshade and darkens the map under it.
 * Hydrography is transparent water and is drawn after relief.
 */
@Serializable
enum class MapOverlay {
    USGS_SHADED_RELIEF,
    USGS_HYDRO,
    ;

    val basemap: MapBasemap
        get() = when (this) {
            USGS_SHADED_RELIEF -> MapBasemap.USGS_SHADED_RELIEF
            USGS_HYDRO -> MapBasemap.USGS_HYDRO
        }

    /** Hillshade tiles are opaque gray. They darken the map instead of covering it. */
    val multiply: Boolean get() = this == USGS_SHADED_RELIEF

    companion object {
        /** Relief under the water lines. A set from JSON keeps this order. */
        fun ordered(selected: Collection<MapOverlay>): List<MapOverlay> =
            entries.filter { it in selected }

        fun key(selected: Collection<MapOverlay>): String =
            ordered(selected).joinToString(",") { it.name }.ifEmpty { "none" }

        fun joinCredit(base: String, selected: Collection<MapOverlay>): String {
            val extra = ordered(selected).joinToString(" · ") { it.basemap.credit }
            return if (extra.isEmpty()) base else "$base · $extra"
        }
    }
}

/**
 * Lower-left readout on the on-screen maps. This phone now.
 * Missing JSON is [OFF]. The path figure does not use it.
 */
@Serializable
enum class MapFooter {
    OFF,
    GPS,
    GRID,
    ;

    val labelKey: String
        get() = when (this) {
            OFF -> "ui.off.label"
            GPS -> "map.position.gps"
            GRID -> "ui.grid"
        }
}

/**
 * Compass picture on Show live path and Hunt. Two choices, so four pictures.
 * Tap the button for north-up or direction of travel. Double-tap the map for
 * the whole path or the close view around this phone. Reports stay north-up.
 */
object MapFrame {
    /** Whole path, north up. */
    const val NORTH = 0
    /** Whole path, travel up. */
    const val TRAVEL = 1
    /** Close view, travel up. Older saves used this as the only close view. */
    const val CLOSE = 2
    /** Close view, north up. */
    const val CLOSE_NORTH = 3

    /**
     * Half the short side of the close view, in meters.
     * The same scale as a sit that has not moved yet, about 220 m.
     */
    val CLOSE_HALF_M: Float = SitPathPlot.MIN_HALF_SPAN_M * SitPathPlot.FIT_PAD

    /** Tile window around you while the close view follows. Covers a turned wide band. */
    const val CLOSE_TILE_HALF_M = 560f

    /** -1 is an older file. [mapRotate] then picks north-up or travel, zoomed out. */
    fun resolved(saved: Int, mapRotate: Boolean): Int =
        if (saved in NORTH..CLOSE_NORTH) saved else if (mapRotate) TRAVEL else NORTH

    /** Close view around this phone. The whole path is the other zoom. */
    fun zoomed(frame: Int): Boolean = frame == CLOSE || frame == CLOSE_NORTH

    /** Direction of travel is up. North-up frames stay fixed. */
    fun turns(frame: Int): Boolean = frame == TRAVEL || frame == CLOSE

    /**
     * Course to draw. A finite held course stays put while the finger has the map.
     * North-up frames stay north-up. A missing course stays north-up.
     */
    fun drawnCourse(frame: Int, heldCourseDeg: Double?, liveCourseDeg: Double): Double {
        if (!turns(frame)) return Double.NaN
        if (heldCourseDeg != null && heldCourseDeg.isFinite()) return heldCourseDeg
        return if (liveCourseDeg.isFinite()) liveCourseDeg else Double.NaN
    }

    /**
     * Signed degrees from [from] to [to], in (-180, 180]. A turn past north
     * stays the short way. A missing angle is no turn.
     */
    fun shortestTurn(from: Double, to: Double): Double {
        if (!from.isFinite() || !to.isFinite()) return 0.0
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta <= -180.0) delta += 360.0
        return delta
    }

    /**
     * How long the drawn map spends turning toward the course that just arrived.
     * A GPS course shows up about once a second. Spreading the turn across that
     * gap keeps a curve moving. A late course still finishes within a second.
     */
    fun courseTurnMs(gapMs: Long): Int {
        val gap = if (gapMs <= 0L) COURSE_TURN_MIN_MS else gapMs
        return gap.coerceIn(COURSE_TURN_MIN_MS, COURSE_TURN_MAX_MS).toInt()
    }

    const val COURSE_TURN_MIN_MS = 200L
    const val COURSE_TURN_MAX_MS = 1_000L

    /** [pending] is the frame a tap already showed. The saved frame replaces it once the file catches up. */
    fun onScreen(pending: Int?, saved: Int): Int =
        if (pending != null && pending != saved) pending else saved

    /** Tap. North-up and direction of travel trade places. Zoom stays. */
    fun bearingToggled(current: Int): Int = when (current) {
        NORTH -> TRAVEL
        TRAVEL -> NORTH
        CLOSE -> CLOSE_NORTH
        CLOSE_NORTH -> CLOSE
        else -> NORTH
    }

    /** Double-tap. The whole path and the close view trade places. Bearing stays. */
    fun zoomToggled(current: Int): Int = when (current) {
        NORTH -> CLOSE_NORTH
        CLOSE_NORTH -> NORTH
        TRAVEL -> CLOSE
        CLOSE -> TRAVEL
        else -> NORTH
    }
}

/**
 * Camera marks on Show live path and Reports → Path. Hunt is unchanged.
 * An older file that saved the path-only choice reads as on.
 */
@Serializable(with = MapCameraMarksSerializer::class)
enum class MapCameraMarks {
    /** Every camera in this picture. */
    VIEW,

    /** No camera marks. */
    OFF,
    ;

    /** On, then off. */
    fun next(): MapCameraMarks = when (this) {
        VIEW -> OFF
        OFF -> VIEW
    }

    fun descriptionKey(): String = when (this) {
        VIEW -> "map.cameras.view"
        OFF -> "map.cameras.off"
    }

    companion object {
        /** A tap already showed [pending]. The saved value replaces it once the file catches up. */
        fun onScreen(pending: MapCameraMarks?, saved: MapCameraMarks): MapCameraMarks =
            if (pending != null && pending != saved) pending else saved
    }
}

object MapCameraMarksSerializer : KSerializer<MapCameraMarks> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MapCameraMarks", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MapCameraMarks) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): MapCameraMarks = when (decoder.decodeString()) {
        "OFF" -> MapCameraMarks.OFF
        "VIEW", "PATH" -> MapCameraMarks.VIEW
        else -> MapCameraMarks.VIEW
    }
}

@Serializable
data class AppSettings(
    /** Kept in settings packs. The UI is always dark; Night mode is the red overlay. */
    val darkTheme: Boolean = true,
    val keepScreenOn: Boolean = true,
    val intensity: ScanIntensity = ScanIntensity.PERFORMANCE,
    val loggingEnabled: Boolean = true,
    val logFormat: LogFormat = LogFormat.CSV,
    val logRotateKb: Int = 1024,
    val staleSec: Int = 45,
    val alertsEnabled: Boolean = true,
    val alertBeep: Boolean = true,
    /** Speak after a watchlist alert. On by default. Not Hunt. What to say is [alertVoiceWhat]. */
    val alertVoice: Boolean = true,
    /** Class (Live glyph), signature name, or both. Only used while [alertVoice] is on. */
    val alertVoiceWhat: AlertVoiceWhat = AlertVoiceWhat.BOTH,
    /**
     * Spoken stand-in for a signature plus one advertised network name.
     * Missing JSON is none. Export settings keeps the list. The catalog does not.
     */
    val spokenNames: List<SpokenName> = emptyList(),
    val snapToBeep: Boolean = true,
    val alertShade: Boolean = false,
    val tagLocation: Boolean = true,
    /**
     * Remember a stable tracker heard at two or more places, and a return after
     * a quiet stretch. Ordinary Wi-Fi and Bluetooth stay out. Missing JSON is on.
     */
    val placeWatch: Boolean = true,
    /** Reverse-geocode GPS stamps in Debrief when the phone is online. Default on. */
    val onlineLookup: Boolean = true,
    /** Which raster [onlineLookup] loads. Missing JSON is OpenStreetMap. Turning maps off keeps this. */
    val mapBasemap: MapBasemap = MapBasemap.OSM,
    /**
     * Shaded relief and hydrography on top of [mapBasemap]. Missing JSON is none.
     * An older file that picked either one as the map moves it here and returns to OpenStreetMap.
     */
    val mapOverlays: Set<MapOverlay> = emptySet(),
    /** Plate readers on the path maps. Off until chosen. Turning this off keeps the saved file. */
    val flockLayer: Boolean = false,
    /** Other sensors on the path maps. Off until chosen. Turning this off keeps the saved file. */
    val sensorLayer: Boolean = false,
    /** Notify when a plate reader is ahead. Off until chosen. Acts only while [flockLayer] is on. */
    val flockAlert: Boolean = false,
    /** Notify when a speed camera is ahead. Off until chosen. Acts only while [sensorLayer] is on. */
    val speedAlert: Boolean = false,
    /** Notify when a red-light camera is ahead. Off until chosen. Acts only while [sensorLayer] is on. */
    val redAlert: Boolean = false,
    /** Notify when a gunshot detector is ahead. Off until chosen. Acts only while [sensorLayer] is on. */
    val gunshotAlert: Boolean = false,
    /** Notify when another mapped camera is ahead. Off until chosen. Acts only while [sensorLayer] is on. */
    val cameraAlert: Boolean = false,
    /** Lower left of the on-screen map. Missing JSON is off. */
    val mapFooter: MapFooter = MapFooter.OFF,
    /**
     * Show live path and Hunt follow travel while the map is following.
     * Missing JSON keeps north up. The compass button is the only control.
     * Kept so an older file still decodes. [mapFrame] wins once it is set.
     */
    val mapRotate: Boolean = false,
    /**
     * Compass picture. [MapFrame.NORTH], [MapFrame.TRAVEL], [MapFrame.CLOSE],
     * or [MapFrame.CLOSE_NORTH]. -1 is an older file: [mapRotate] chooses
     * north-up or travel, zoomed out.
     */
    val mapFrame: Int = -1,
    /**
     * Camera marks on Show live path and Reports → Path.
     * Missing JSON shows every camera in the picture.
     */
    val mapCameras: MapCameraMarks = MapCameraMarks.VIEW,
    /** Kept so an older file still decodes. The double-tap no longer reads it. */
    val mapHome: Int = MapFrame.NORTH,
    val viewMode: ViewMode = ViewMode.BY_CLASS,
    val strengthSort: StrengthSort = StrengthSort.AVERAGE,
    val averageWindowSec: Int = 30,
    val listSort: ListSort = ListSort.STRENGTH,
    val showRssiBar: Boolean = true,
    /** Map of this phone's walk above the live list. Off until chosen. Radar leaves the saved value and does not show the map. */
    val showLivePath: Boolean = false,
    /** Share of the live list area given to the path. Dragging clamps it. */
    val livePathSplit: Float = 0.5f,
    val showFleetName: Boolean = true,
    val showFrequency: Boolean = true,
    val showSeenTimes: Boolean = true,
    /** Live row first line. Default is MAC. */
    val listTitleLine: ListLine = ListLine.MAC,
    /** Live row second line. Default is name, else type guess. NONE hides the line. */
    val listSubtitleLine: ListLine = ListLine.NAME_AND_TYPE,
    /** Brief hold seconds (0 = Off). Linger only; does not decay RSSI. */
    val decaySec: Int = 10,
    /** Live Tune / Display panel. Always starts collapsed; not restored from last session. */
    val scanControlsExpanded: Boolean = false,
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
     * When false (default), Debrief text/PDF inventories omit unmatched rotating BLE.
     * Counts, Extra attention, named signatures, bookmarks, payload pins, and Sit export still include them.
     */
    val debriefShowUnmatchedRandomBle: Boolean = false,
    /**
     * When false (default), Debrief and Compare print counts and the radios that
     * are Extra attention, named, marked Mine, or bookmarked. Compare also lists
     * a decoded value that changed. Full rosters return when this is on.
     */
    val debriefShowAllRadios: Boolean = false,
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
    /** GNSS line on this phone's TAK marker, plus a pin or oval where the phone was. Off until chosen. */
    val takGnss: TakGnssSend = TakGnssSend.OFF,
    /** How long that GNSS mark stays on the map. Missing JSON is 1 hour. */
    val takGnssHold: TakGnssHold = TakGnssHold.HOUR,
    /**
     * How long a radio spot keeps its name after it leaves the feed.
     * Missing JSON is 1 hour. The phone marker stays on the 2-minute refresh.
     */
    val takRadioHold: TakGnssHold = TakGnssHold.HOUR,
    /** Flood line on this phone's TAK marker while a flood is detected. */
    val takFlood: TakFloodSend = TakFloodSend.OFF,
    /**
     * Map name for this phone. Blank sends Fieldwatch- plus the install tag.
     * A typed name is copied with settings. The tag is not.
     */
    val takCallsign: String = "",
    /**
     * Red-on-black field display. Off by default. The rest of the UI is always dark.
     * Phone brightness is unchanged.
     */
    val nightMode: Boolean = false,
    /** First-run click-through. Scanning does not start until [disclaimerRev] matches [DISCLAIMER_REV]. */
    val disclaimerAccepted: Boolean = false,
    val disclaimerRev: Int = 0,
    /** Chrome overlay on Live after the license. Settings can show it again. */
    val liveTourDone: Boolean = false,
    /**
     * Last Live-tour offer applied on this phone. Missing or older than [LIVE_TOUR_REV]
     * shows the tour once more, then this catches up so later launches do not.
     */
    val liveTourRev: Int = 0,
    /** Signatures tab: Name A–Z (default) or Class A–Z. */
    val signatureListSort: SignatureListSort = SignatureListSort.NAME,
    /** By class: hide class headers with 0 radios. Off = show all (zeros stay). */
    val outlineHideEmpty: Boolean = false,
    /** Phone GNSS interference check. Off until this phone is calibrated and the user turns it on. */
    val gnssMonitor: Boolean = false,
    val gnssSensitivity: GnssSensitivity = GnssSensitivity.MEDIUM,
    /** Popup, red line, beep, and voice start at this confidence. */
    val gnssAlertFloor: GnssAlertFloor = GnssAlertFloor.MEDIUM,
    /** Gain down while the signal holds, plus clock and position checks. */
    val gnssSpoofChecks: Boolean = false,
    /** Pip for a GNSS hit. Independent of the watchlist beep. */
    val gnssBeep: Boolean = true,
    /** Spoken GNSS hit. On by default. Independent of watchlist voice. */
    val gnssVoice: Boolean = true,
    /** Silent shade card for a GNSS hit. Off unless chosen. Independent of the watchlist shade. */
    val gnssShade: Boolean = false,
    /** Ask the GPS chip to stay on. Android 12 and newer. Costs battery. */
    val gnssFullTracking: Boolean = false,
    /** Satellites fading together. Pocket, hand, and a car door can do this. */
    val gnssCorrelation: Boolean = false,
    /** A few phones report gain backwards. Off unless a router test rises. */
    val gnssInvertAgc: Boolean = false,
    /** Raw GNSS file around a hit. Off until chosen. Privacy mode does not record. */
    val gnssRawLog: Boolean = false,
    /** Watch trusted Wi-Fi names. Off until chosen. The learned list is a separate file. */
    val trustedWatch: Boolean = false,
    /** Alert when a trusted name is seen with a weaker profile. */
    val trustedDowngrade: Boolean = true,
    /** Alert when a trusted name is seen from a factory maker that was not learned. */
    val trustedVendor: Boolean = true,
    /** Alert when a trusted name is heard far from its usual places. */
    val trustedAway: Boolean = true,
    /** Meters. Missing JSON is 1 km. */
    val trustedAwayMeters: Int = 1_000,
    /** A quieter hear does not alert. Missing JSON is -85 dBm. */
    val trustedFloorDbm: Int = -85,
    /** Learn new access points during the window. Missing JSON is on. */
    val trustedLearn: Boolean = true,
    /** Days after the name was trusted. 0 is 1 hour. Missing JSON is 3 days. */
    val trustedLearnDays: Int = 3,
    /** Beep for this feature. Not the watchlist beep. */
    val trustedBeep: Boolean = true,
    /** Speak a trusted-network alert. Not the watchlist voice. */
    val trustedVoice: Boolean = true,
    /** System notification for this feature. Off until chosen. */
    val trustedShade: Boolean = false,
    /** Popup, beep, voice, and the sit block start at this confidence. */
    val trustedAlertFloor: GnssAlertFloor = GnssAlertFloor.MEDIUM,
    /** TAK pin for a trusted-network alert. Off until chosen. */
    val trustedTak: Boolean = false,
    /** How long that pin stays. Missing JSON is 1 hour. */
    val trustedTakHold: TakGnssHold = TakGnssHold.HOUR,
    /** Watch this phone's serving cell. Off until chosen. Places are a separate file. */
    val cellWatch: Boolean = false,
    /** Warn when a learned place is still and the serving cell is new. */
    val cellWarnNew: Boolean = true,
    /** Warn when a 4G or 5G place drops to 2G or 3G. */
    val cellWarnDrop: Boolean = true,
    /**
     * Days after a place is opened. 0 is none: the first visit is the only quiet one.
     * Missing JSON is 3, the same start trusted networks use.
     */
    val cellLearnDays: Int = 3,
    /** Beep for this feature. Not the watchlist beep. */
    val cellBeep: Boolean = true,
    /** Speak a cell warning. Not the watchlist voice. */
    val cellVoice: Boolean = true,
    /** System notification for this feature. Off until chosen. */
    val cellShade: Boolean = false,
)

const val DISCLAIMER_REV = 3

/** Bump when an upgrade should show the Live tour one more time. */
const val LIVE_TOUR_REV = 1

fun AppSettings.disclaimerOk(): Boolean = disclaimerRev >= DISCLAIMER_REV

/**
 * Shaded relief and hydrography used to be map choices.
 * A saved choice becomes that overlay on OpenStreetMap.
 */
fun AppSettings.withOverlayBasemaps(): AppSettings {
    val moved = when (mapBasemap) {
        MapBasemap.USGS_SHADED_RELIEF -> MapOverlay.USGS_SHADED_RELIEF
        MapBasemap.USGS_HYDRO -> MapOverlay.USGS_HYDRO
        else -> return this
    }
    return copy(
        mapBasemap = MapBasemap.OSM,
        mapOverlays = mapOverlays + moved,
    )
}

/** One offer per [LIVE_TOUR_REV]. A phone already on this rev is left as saved. */
fun AppSettings.offerLiveTourOnce(): AppSettings {
    if (liveTourRev >= LIVE_TOUR_REV) return this
    return copy(liveTourDone = false, liveTourRev = LIVE_TOUR_REV)
}

/** The path band sits over a list. Radar has no list, so the switch stays as saved and the band stays off. */
fun AppSettings.livePathOnScreen(): Boolean = showLivePath && viewMode != ViewMode.RADAR

data class DetectionPolicy(
    val ssidKeywords: Boolean = true,
    val knownOuis: Boolean = true,
    val vendorIes: Boolean = true,
    val bleRaven: Boolean = true,
)

fun AppSettings.gnssSettings(): List<Any> = listOf(
    gnssMonitor,
    gnssSensitivity,
    gnssAlertFloor,
    gnssSpoofChecks,
    gnssBeep,
    gnssVoice,
    gnssShade,
    gnssFullTracking,
    gnssCorrelation,
    gnssInvertAgc,
    gnssRawLog,
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
    /**
     * 4-character id for this install's TAK markers. Not part of a settings pack.
     * Blank until the first load fills it. A new install is filled in the seed.
     */
    val takInstallTag: String = "",
    /**
     * True after FIELDWATCH-SELF was deleted, or when this install never sent that name.
     * A missing key on an older file stays false so the delete can still go out.
     */
    val takLegacySelfRetired: Boolean = false,
)

data class RssiSample(
    val at: Long,
    val rssi: Int,
)

@Serializable
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
    /** Remote ID System operator location. Not the aircraft pin. */
    val payloadOpLat: Double? = null,
    val payloadOpLon: Double? = null,
    /** 0 takeoff, 1 live GPS, 2 fixed. Null until a System message says. */
    val payloadOpKind: Int? = null,
    /** Last known operator altitude in meters. Unknown stays null. */
    val payloadOpAlt: Double? = null,
    /** Sticky Remote ID Basic ID / Self ID. TAK keys the aircraft on uas_id when present. */
    val payloadUasId: String? = null,
    /**
     * Maker from a CTA-2063 serial on this radio.
     * Set only after a Serial Basic ID. A later Basic ID with no maker clears it.
     */
    val payloadAircraft: String? = null,
    val payloadSelfId: String? = null,
    val payloadHeading: Double? = null,
    val payloadSpeed: Double? = null,
    val payloadVspeed: Double? = null,
    /** Advertised fixes kept by the sit. Empty on a live radio that has not been saved into one. */
    val payloadTrail: List<PayloadFix> = emptyList(),
    /** Decoded labels a signature asked to show on the live row. Empty for every other radio. */
    val liveDecode: List<LiveDecodeChip> = emptyList(),
    /** When the last packet that carried an aircraft latitude was read. */
    val payloadAt: Long? = null,
    /** Where this phone was at [payloadAt]. Null when that packet had no phone fix. */
    val payloadPhoneLat: Double? = null,
    val payloadPhoneLon: Double? = null,
    /** When the last packet that carried an operator location was read. */
    val payloadOpAt: Long? = null,
    /** Where this phone was at [payloadOpAt]. */
    val payloadOpPhoneLat: Double? = null,
    val payloadOpPhoneLon: Double? = null,
    /**
     * Samsung SmartTag privacy ID while the latest FD5A frame is separated.
     * Not a device key. Null for with owner, unlabeled, and rejected frames.
     */
    val smartTagId: String? = null,
) {
    val displayName: String
        get() = name.ifBlank { if (hiddenSsid) "<hidden>" else mac }

    /**
     * Bluetooth row the detail page calls a random address.
     * The local bit, or an address type of Random. Wi-Fi is never this.
     */
    fun randomBleAddress(): Boolean =
        kind == RadioKind.BLE &&
            (randomized || facts.addressType.equals("Random", ignoreCase = true))

    /** Custom name from Named radios, else advertised / hidden / MAC. */
    fun reportName(customNames: Map<String, String>): String {
        val custom = customNames[key]?.trim()
        if (!custom.isNullOrEmpty()) return custom
        return displayName
    }

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
        val heard = rssiHistory.filter { it.at >= from && Rssi.measured(it.rssi) }
        if (heard.isNotEmpty()) return heard.map { it.rssi }.average()
        return (if (Rssi.measured(rssi)) rssi else floor).toDouble()
    }

    fun rssiTrend(count: Int = 12): RssiTrend {
        val samples = rssiHistory.filter { Rssi.measured(it.rssi) }.takeLast(count.coerceAtLeast(4))
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
    val wifiRadioOn: Boolean = false,
    val wifiWaitingOnOs: Boolean = false,
    val bleRadioOn: Boolean = false,
    val bleRunning: Boolean = false,
    val bleParked: Boolean = false,
    val bleRetrying: Boolean = false,
    val bleStarting: Boolean = false,
    val bleHitsLastMin: Int = 0,
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

    /** Local bit of the first octet. The address was assigned locally, whether or not the group bit is set. */
    fun isLocallyAdministered(mac: String): Boolean {
        val first = normalize(mac).substringBefore(":").toIntOrNull(16) ?: return false
        return (first and 0x02) != 0
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

    /**
     * Replace each known identifier with "masked". Whole token only, longest first,
     * so a shorter id does not eat the middle of a longer one. Blank and very short
     * strings are left alone.
     */
    fun redactTokensIn(text: String, tokens: Collection<String>): String {
        if (text.isEmpty()) return text
        val ids = tokens
            .map { it.trim() }
            .filter { it.length >= 4 }
            .distinct()
            .sortedByDescending { it.length }
        if (ids.isEmpty()) return text
        var out = text
        for (id in ids) {
            val re = Regex("(?<![A-Za-z0-9])${Regex.escape(id)}(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
            out = re.replace(out, "masked")
        }
        return out
    }

    /** Privacy-mode report text. MAC tails, known UAS ids, then coordinates. */
    fun redactPrivateText(
        text: String,
        macs: Collection<String>,
        uasIds: Collection<String>,
        demo: Boolean,
    ): String {
        if (!demo || text.isEmpty()) return text
        return Geo.redactCoordsIn(redactTokensIn(redactMacsIn(text, macs, true), uasIds), true)
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

fun RadioKind.label(): String =
    if (this == RadioKind.WIFI) LanguageText.text("radio.wifi") else LanguageText.text("radio.ble")

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
