package app.fieldwatch.domain

import java.util.UUID

data class SignatureCandidate(
    val id: String,
    val proposedName: String,
    val kind: SignatureClass,
    val radioKind: RadioKind,
    val distinctRadios: Int,
    val rules: List<MatchRule>,
    val why: String,
    val examples: List<String>,
    val extraCount: Int,
    val notes: String,
    val colorIndex: Int,
) {
    val ruleLabel: String
        get() = rules.joinToString("  ·  ") { ruleShortLabel(it) }
}

data class CandidateReport(
    val families: List<SignatureCandidate>,
    val uniqueRadios: Int,
    val unmatchedRadios: Int,
    val skippedRandomized: Int,
    val skippedHouseLike: Int,
    val skippedOther: Int,
    val sourceLabel: String = "Rotating log",
)

enum class FamilyVerdict { STRONG, POSSIBLE, SINGLE, TAGGED }

data class SignatureFamilyHint(
    val verdict: FamilyVerdict,
    val title: String,
    val body: String,
    val logCount: Int,
    val liveCount: Int,
    val displayCount: Int,
    val ruleLabel: String?,
    val radioKind: RadioKind,
)

internal fun ruleShortLabel(rule: MatchRule): String = when (rule.kind) {
    RuleKind.NAME_GLOB, RuleKind.NAME_CONTAINS -> rule.text
    RuleKind.VENDOR_IE_OUI -> "vendor IE ${rule.text}"
    RuleKind.OUI, RuleKind.MAC_PREFIX -> "OUI ${rule.text}"
    RuleKind.SERVICE_UUID -> "UUID ${rule.text}"
    RuleKind.MANUFACTURER_DATA -> "mfg 0x%04X %s".format(rule.companyId, rule.dataPrefixHex)
    RuleKind.MANUFACTURER_ID -> "mfg 0x%04X".format(rule.companyId)
    else -> rule.kind.name
}

/**
 * Cluster unmatched log radios by a shared unique on-air ID.
 * Frequency is distinct MACs, not packet count. Not every unknown radio.
 */
object SignatureCandidates {
    const val MAX_FAMILIES = 20
    const val MIN_RADIOS = 2
    const val STRONG_MIN_RADIOS = 8

    fun analyze(
        radios: List<LogRadio>,
        fleets: List<Fleet>,
        engine: SignatureEngine = SignatureEngine(),
        sourceLabel: String = "Rotating log",
    ): CandidateReport {
        val sightings = radios.map { it.toSighting() }
        val hits = if (sightings.isEmpty()) emptyMap() else engine.match(sightings, fleets)
        val unmatched = radios.filter { radio ->
            hits[radio.key].isNullOrEmpty()
        }
        var skippedRandomized = 0
        var skippedHouseLike = 0
        var skippedOther = 0
        val usable = ArrayList<LogRadio>(unmatched.size)
        unmatched.forEach { radio ->
            when {
                radio.hasStructuredId() -> usable += radio
                radio.randomized -> skippedRandomized++
                isHouseLikeName(radio.name) -> skippedHouseLike++
                else -> skippedOther++
            }
        }
        val clusters = buildClusters(usable)
        val merged = mergeOverlapping(clusters)
        val viable = merged.filter { it.members.size >= MIN_RADIOS }
        val clusteredKeys = viable.flatMap { it.members }.toSet()
        skippedOther += usable.count { it.key !in clusteredKeys }
        val families = viable
            .sortedWith(
                compareByDescending<Cluster> { it.members.size }
                    .thenBy { kindRank(it.primary.kind) }
                    .thenBy { it.proposedName.lowercase() },
            )
            .take(MAX_FAMILIES)
            .map { it.toCandidate() }
        return CandidateReport(
            families = families,
            uniqueRadios = radios.size,
            unmatchedRadios = unmatched.size,
            skippedRandomized = skippedRandomized,
            skippedHouseLike = skippedHouseLike,
            skippedOther = skippedOther,
            sourceLabel = sourceLabel,
        )
    }

    /**
     * For one radio on detail: is its best on-air ID a catalog family?
     * Same ID quality as [analyze]. Distinct MACs, not packets.
     */
    fun assessFamily(
        device: Sighting,
        live: List<Sighting>,
        log: List<LogRadio>,
        fleets: List<Fleet>,
    ): SignatureFamilyHint {
        val tagged = fleets.filter { it.id in device.fleetIds }.map { it.name.trim() }.filter { it.isNotEmpty() }
        if (device.fleetIds.isNotEmpty()) {
            val names = tagged.ifEmpty { listOf("a catalog signature") }
            return SignatureFamilyHint(
                verdict = FamilyVerdict.TAGGED,
                title = "Already tagged",
                body = "Matched ${names.joinToString(", ")}. A second signature can still dual-label this radio (store UUID, product OUI).",
                logCount = 0,
                liveCount = 0,
                displayCount = 0,
                ruleLabel = null,
                radioKind = device.kind,
            )
        }
        val self = device.toProbe()
        val prints = fingerprintsOf(self)
        if (prints.isEmpty()) {
            return SignatureFamilyHint(
                verdict = FamilyVerdict.SINGLE,
                title = "This radio only",
                body = "No unique on-air ID to cluster on. Randomized addresses, house-like names, and generic chips are skipped.",
                logCount = 0,
                liveCount = 0,
                displayCount = 0,
                ruleLabel = null,
                radioKind = device.kind,
            )
        }
        val logRadios = log.distinctBy { it.key }
        val liveRadios = live.map { it.toProbe() }.distinctBy { it.key }
        val logKeys = logRadios.associate { it.key to fingerprintsOf(it).map { fp -> fp.key }.toSet() }
        val liveKeys = liveRadios.associate { it.key to fingerprintsOf(it).map { fp -> fp.key }.toSet() }
        val scored = prints.mapNotNull { fp ->
            val logHit = logRadios.filter { logKeys[it.key]?.contains(fp.key) == true }
            val liveHit = liveRadios.filter { liveKeys[it.key]?.contains(fp.key) == true }
            if (!idViable(fp, logHit, liveHit)) return@mapNotNull null
            Triple(fp, logHit.size, liveHit.size)
        }
        val best = scored.maxWithOrNull(
            compareBy<Triple<Fingerprint, Int, Int>> { maxOf(it.second, it.third) }
                .thenByDescending { kindRank(it.first.rule.kind) },
        )
        if (best == null) {
            return SignatureFamilyHint(
                verdict = FamilyVerdict.SINGLE,
                title = "This radio only",
                body = "No unique on-air ID to cluster on. Randomized addresses, house-like names, and generic chips are skipped.",
                logCount = 0,
                liveCount = 0,
                displayCount = 0,
                ruleLabel = null,
                radioKind = device.kind,
            )
        }
        val fp = best.first
        val logN = best.second
        val liveN = best.third
        val familyN = maxOf(logN, liveN)
        val kindLabel = idKindLabel(fp.rule)
        val clause = countClause(device.kind, logN, liveN)
        val ruleText = ruleShortLabel(fp.rule)
        if (familyN < MIN_RADIOS) {
            return SignatureFamilyHint(
                verdict = FamilyVerdict.SINGLE,
                title = "This radio only",
                body = "No other MAC in the log or on the air shares this $kindLabel. A signature from here will mostly tag this address.",
                logCount = logN,
                liveCount = liveN,
                displayCount = 0,
                ruleLabel = ruleText,
                radioKind = device.kind,
            )
        }
        val strong = familyN >= STRONG_MIN_RADIOS
        return SignatureFamilyHint(
            verdict = if (strong) FamilyVerdict.STRONG else FamilyVerdict.POSSIBLE,
            title = if (strong) "Strong family" else "Possible family",
            body = if (strong) {
                "Same $kindLabel on $clause. That is a catalog pattern, not this MAC."
            } else {
                "Same $kindLabel on $clause. Thin sample — a possible catalog family."
            },
            logCount = logN,
            liveCount = liveN,
            displayCount = if (logN > 0) logN else liveN,
            ruleLabel = ruleText,
            radioKind = device.kind,
        )
    }

    fun suggestFleet(candidate: SignatureCandidate): Fleet = Fleet(
        id = UUID.randomUUID().toString(),
        name = candidate.proposedName,
        enabled = true,
        matchAny = true,
        colorIndex = candidate.colorIndex,
        rules = candidate.rules,
        notes = candidate.notes,
        builtIn = false,
        kind = candidate.kind,
    )

    internal fun nameGlobOf(name: String): String? {
        val n = name.trim()
        if (n.isBlank() || n.startsWith("<") || isHouseLikeName(n)) return null
        val bracket = BRACKET_NAME.matchEntire(n)
        if (bracket != null) {
            val token = bracket.groupValues[1]
            if (token.length >= 3 && !isSkippedPrefix(token)) return "[$token]*"
        }
        val sepAt = n.indexOfFirst { it == '-' || it == '_' }
        if (sepAt < 3) return null
        val prefix = n.take(sepAt)
        if (isSkippedPrefix(prefix) || isHouseLikeName(prefix)) return null
        if (prefix.length < 3) return null
        val sep = n[sepAt]
        val suffix = n.drop(sepAt + 1).takeWhile { it != ' ' }
        val hex = suffix.filter { it.isLetterOrDigit() }
        val hexOnlySuffix = hex.isNotEmpty() && hex.length == suffix.length && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        return if (hexOnlySuffix && hex.length >= 6) {
            "$prefix$sep${"?".repeat(hex.length)}"
        } else {
            "$prefix*"
        }
    }

    /** Create-from-device used to emit DIRECT* / ANDROID* globs. Those are every Direct AP. */
    internal fun isOverbroadCreateName(text: String): Boolean {
        val t = text.trim().uppercase().trimEnd('*').trimEnd('-').trimEnd('_')
        return t.isNotEmpty() && t in SKIP_PREFIX
    }

    internal fun isHouseLikeName(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return true
        val tokens = n.lowercase().split(Regex("""[\s\-_]+""")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val first = tokens.first()
        if (HOUSE_WORDS.any { it == first || first.startsWith(it) }) return true
        if (tokens.any { it == "guest" || it.startsWith("guest") }) {
            val product = first.any { ch -> ch.isDigit() } || (first == first.uppercase() && first.length >= 4)
            if (!product) return true
        }
        val token = n.takeWhile { it != ' ' }
        val noSep = '-' !in token && '_' !in token
        val letters = token.all { it.isLetter() }
        val titleOrLower = token != token.uppercase()
        return noSep && letters && titleOrLower && token.length in 3..18 && !token.any { it.isDigit() }
    }

    internal fun isChipModuleVendor(vendor: String?): Boolean {
        val v = vendor?.lowercase() ?: return false
        return CHIP_VENDOR.any { it in v }
    }

    internal fun isProtocolIe(oui: String): Boolean {
        val hex = oui.filter { it.isLetterOrDigit() }.uppercase()
        return hex.take(6) in PROTOCOL_IE
    }

    private fun buildClusters(radios: List<LogRadio>): List<Cluster> {
        val buckets = LinkedHashMap<String, Pair<Fingerprint, MutableList<LogRadio>>>()
        radios.forEach { radio ->
            fingerprintsOf(radio).forEach { fp ->
                val slot = buckets.getOrPut(fp.key) { fp to ArrayList() }
                slot.second += radio
            }
        }
        val out = ArrayList<Cluster>()
        buckets.values.forEach { (fp, members) ->
            clusterOf(fp, distinctRadios(members))?.let { out += it }
        }
        return out
    }

    private fun clusterOf(fp: Fingerprint, distinct: List<LogRadio>): Cluster? {
        if (distinct.size < MIN_RADIOS) return null
        val members = distinct.map { it.key }.toMutableSet()
        return when (fp.rule.kind) {
            RuleKind.NAME_GLOB, RuleKind.NAME_CONTAINS -> {
                val glob = fp.rule.text
                val kind = distinct.first().kind
                Cluster(
                    key = fp.key,
                    primary = fp.rule,
                    radioKind = kind,
                    members = members,
                    radios = distinct,
                    proposedName = glob.takeWhile { it != '*' && it != '?' && it != '-' && it != '_' }
                        .ifBlank { glob.trimEnd('*') },
                    why = "Same name glob on ${distinct.size} ${kind.radioWord(distinct.size)} — not a house SSID.",
                )
            }
            RuleKind.VENDOR_IE_OUI -> {
                val oui = fp.rule.text
                val hex = oui.filter { it.isLetterOrDigit() }.uppercase()
                val vendor = RadioDb.vendorForOui24(hex)
                Cluster(
                    key = fp.key,
                    primary = fp.rule,
                    radioKind = RadioKind.WIFI,
                    members = members,
                    radios = distinct,
                    proposedName = vendor?.take(22) ?: "Vendor IE $oui",
                    why = "Same vendor IE on ${distinct.size} BSSIDs. Not WPS / P2P.",
                )
            }
            RuleKind.SERVICE_UUID -> {
                val short = fp.rule.text
                val named = RadioDb.serviceUuid(short)
                Cluster(
                    key = fp.key,
                    primary = fp.rule,
                    radioKind = RadioKind.BLE,
                    members = members,
                    radios = distinct,
                    proposedName = named?.take(22) ?: "UUID $short",
                    why = "Same service UUID on ${distinct.size} BLE advertisers.",
                )
            }
            RuleKind.MANUFACTURER_DATA, RuleKind.MANUFACTURER_ID -> {
                val company = fp.rule.companyId
                val named = RadioDb.company(company)
                Cluster(
                    key = fp.key,
                    primary = fp.rule,
                    radioKind = RadioKind.BLE,
                    members = members,
                    radios = distinct,
                    proposedName = named?.take(22) ?: "Company 0x%04X".format(company),
                    why = "Same manufacturer data prefix on ${distinct.size} BLE advertisers.",
                )
            }
            RuleKind.OUI, RuleKind.MAC_PREFIX -> {
                if (!ouiViable(distinct)) return null
                val oui = fp.rule.text
                val vendor = distinct.firstNotNullOfOrNull { it.vendor } ?: RadioDb.vendorForOui24(oui)
                Cluster(
                    key = fp.key,
                    primary = fp.rule,
                    radioKind = RadioKind.WIFI,
                    members = members,
                    radios = distinct,
                    proposedName = vendor?.take(22) ?: oui,
                    why = "Same IEEE OUI on ${distinct.size} stable BSSIDs. Not a chip-module prefix.",
                )
            }
            else -> null
        }
    }

    private fun fingerprintsOf(radio: LogRadio): List<Fingerprint> {
        val out = ArrayList<Fingerprint>(8)
        nameGlobOf(radio.name)?.let { glob ->
            if (!isHouseLikeName(radio.name)) {
                out += Fingerprint(
                    key = "glob:$glob",
                    rule = MatchRule(RuleKind.NAME_GLOB, text = glob, radio = radio.kind),
                )
            }
        }
        radio.vendorIeOuis.forEach { ie ->
            val hex = ie.filter { it.isLetterOrDigit() }.uppercase().take(6)
            if (hex.length == 6 && hex !in PROTOCOL_IE) {
                val oui = hex.chunked(2).joinToString(":")
                out += Fingerprint(
                    key = "ie:$hex",
                    rule = MatchRule(RuleKind.VENDOR_IE_OUI, text = oui, radio = RadioKind.WIFI),
                )
            }
        }
        if (radio.kind == RadioKind.BLE) {
            radio.serviceUuids.forEach { uuid ->
                val short = uuidShort(uuid)
                if (short != null && short !in GENERIC_UUID) {
                    out += Fingerprint(
                        key = "uuid:$short",
                        rule = MatchRule(RuleKind.SERVICE_UUID, text = short, radio = RadioKind.BLE),
                    )
                }
            }
            val mfg = radio.manufacturerId
            val prefix = radio.manufacturerDataHex.filter { it.isLetterOrDigit() }.uppercase().take(2)
            if (mfg != null && mfg !in BROAD_COMPANY && prefix.length == 2) {
                out += Fingerprint(
                    key = "mfg:%04X:$prefix".format(mfg),
                    rule = MatchRule(
                        RuleKind.MANUFACTURER_DATA,
                        companyId = mfg,
                        dataPrefixHex = prefix,
                        radio = RadioKind.BLE,
                    ),
                )
            }
        }
        if (!radio.randomized && !isChipModuleVendor(radio.vendor) && !isSkippedOuiVendor(radio.vendor)) {
            val oui = MacUtil.prefixBytes(radio.mac, 3)
            if (oui.length >= 8) {
                out += Fingerprint(
                    key = "oui:$oui",
                    rule = MatchRule(RuleKind.OUI, text = oui, radio = RadioKind.WIFI),
                )
            }
        }
        return out
    }

    private fun idViable(fp: Fingerprint, logHit: List<LogRadio>, liveHit: List<LogRadio>): Boolean {
        if (fp.rule.kind != RuleKind.OUI && fp.rule.kind != RuleKind.MAC_PREFIX) return true
        return ouiViable(logHit) || ouiViable(liveHit)
    }

    private fun ouiViable(members: List<LogRadio>): Boolean {
        if (members.size < MIN_RADIOS) return false
        val names = members.map { it.name.trim() }.filter { it.isNotEmpty() && !isHouseLikeName(it) }.distinct()
        return members.size >= 3 || names.size >= 2
    }

    private fun idKindLabel(rule: MatchRule): String = when (rule.kind) {
        RuleKind.NAME_GLOB, RuleKind.NAME_CONTAINS -> "name glob"
        RuleKind.VENDOR_IE_OUI -> "vendor IE"
        RuleKind.SERVICE_UUID -> "service UUID"
        RuleKind.MANUFACTURER_DATA, RuleKind.MANUFACTURER_ID -> "manufacturer data prefix"
        RuleKind.OUI, RuleKind.MAC_PREFIX -> "IEEE OUI"
        else -> "on-air ID"
    }

    private fun countClause(kind: RadioKind, logN: Int, liveN: Int): String {
        val logWord = kind.radioWord(logN)
        val liveWord = kind.radioWord(liveN)
        return when {
            logN > 0 && liveN > 0 -> "$logN $logWord in the log ($liveN on the air now)"
            logN > 0 -> "$logN $logWord in the log"
            liveN > 0 -> "$liveN $liveWord on the air now"
            else -> "1 ${kind.radioWord(1)}"
        }
    }

    private fun Sighting.toProbe(): LogRadio = LogRadio(
        kind = kind,
        mac = mac,
        name = name,
        vendor = vendor,
        manufacturerId = manufacturerId,
        manufacturerDataHex = manufacturerDataHex,
        serviceUuids = serviceUuids,
        vendorIeOuis = vendorIeOuis,
        randomized = randomized,
        hiddenSsid = hiddenSsid,
        rssi = rssi,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        hits = hitCount,
    )

    private fun mergeOverlapping(clusters: List<Cluster>): List<Cluster> {
        if (clusters.size <= 1) return clusters
        val ranked = clusters.sortedBy { kindRank(it.primary.kind) }.toMutableList()
        var i = 0
        while (i < ranked.size) {
            val keep = ranked[i]
            var j = i + 1
            while (j < ranked.size) {
                val other = ranked[j]
                val n = minOf(keep.members.size, other.members.size)
                val overlap = keep.members.intersect(other.members).size
                if (n > 0 && overlap * 10 >= n * 6) {
                    keep.extra += other.primary
                    keep.members += other.members
                    keep.radios = distinctRadios(keep.radios + other.radios)
                    ranked.removeAt(j)
                } else {
                    j++
                }
            }
            i++
        }
        return ranked
    }

    private fun Cluster.toCandidate(): SignatureCandidate {
        val rules = (listOf(primary) + extra).distinctBy { it.kind to it.text to it.companyId to it.dataPrefixHex }
        val examples = radios
            .map { it.exampleLabel() }
            .filter { it.isNotBlank() }
            .distinct()
        val shown = examples.take(2)
        val extraN = (examples.size - shown.size).coerceAtLeast(0)
        val kind = guessClass(radioKind, primary)
        val word = radioKind.radioWord(members.size)
        return SignatureCandidate(
            id = key,
            proposedName = proposedName.replace(Regex("[^A-Za-z0-9 _.-\\[\\]]"), "").take(22)
                .ifBlank { proposedName.take(22) },
            kind = kind,
            radioKind = radioKind,
            distinctRadios = members.size,
            rules = rules,
            why = why,
            examples = shown,
            extraCount = extraN,
            notes = "${members.size} $word in the log matched ${ruleShortLabel(primary)}. Shared on-air ID, not a one-radio MAC. Change the name or class, then Save.",
            colorIndex = colorFor(kind),
        )
    }

    private fun guessClass(radioKind: RadioKind, rule: MatchRule): SignatureClass = when {
        radioKind == RadioKind.BLE -> SignatureClass.OTHER
        rule.kind == RuleKind.OUI -> SignatureClass.ISP
        else -> SignatureClass.HOME
    }

    private fun colorFor(kind: SignatureClass): Int = when (kind) {
        SignatureClass.MESH -> 0
        SignatureClass.SURVEILLANCE, SignatureClass.DRONE -> 1
        SignatureClass.HACKING -> 2
        SignatureClass.WEARABLE, SignatureClass.BODYWORN, SignatureClass.BEACON, SignatureClass.FINDER -> 3
        SignatureClass.PHONE -> 4
        SignatureClass.GLASSES, SignatureClass.AUDIO -> 5
        SignatureClass.CAMERA, SignatureClass.HOME, SignatureClass.ISP,
        SignatureClass.SIGNAGE, SignatureClass.THERMOSTAT, SignatureClass.LOCK,
        SignatureClass.OTHER,
        -> 6
        SignatureClass.LAW_ENFORCEMENT, SignatureClass.VEHICLE -> 7
        SignatureClass.HEALTH -> 8
    }

    private fun kindRank(kind: RuleKind): Int = when (kind) {
        RuleKind.NAME_GLOB, RuleKind.NAME_CONTAINS -> 0
        RuleKind.VENDOR_IE_OUI -> 1
        RuleKind.SERVICE_UUID -> 2
        RuleKind.MANUFACTURER_DATA, RuleKind.MANUFACTURER_ID -> 3
        RuleKind.OUI, RuleKind.MAC_PREFIX -> 4
        else -> 5
    }

    private fun distinctRadios(members: List<LogRadio>): List<LogRadio> =
        members.distinctBy { it.key }

    private fun uuidShort(raw: String): String? {
        val hex = raw.filter { it.isLetterOrDigit() }.uppercase()
        val short = when {
            hex.length == 4 -> hex
            hex.length >= 8 -> hex.substring(4, 8)
            else -> return null
        }
        return short
    }

    private fun isSkippedPrefix(prefix: String): Boolean {
        val p = prefix.trim().uppercase().removePrefix("[").removeSuffix("]")
        return p in SKIP_PREFIX || p.length < 3
    }

    private fun isSkippedOuiVendor(vendor: String?): Boolean {
        val v = vendor?.lowercase() ?: return false
        return SKIP_OUI_VENDOR.any { it in v }
    }

    private fun LogRadio.hasStructuredId(): Boolean {
        if (nameGlobOf(name) != null && !isHouseLikeName(name)) return true
        if (vendorIeOuis.any { !isProtocolIe(it) }) return true
        if (kind == RadioKind.BLE && serviceUuids.any { uuidShort(it) !in GENERIC_UUID && uuidShort(it) != null }) return true
        if (kind == RadioKind.BLE && manufacturerId != null && manufacturerId !in BROAD_COMPANY) return true
        if (!randomized && !isChipModuleVendor(vendor) && !isSkippedOuiVendor(vendor)) return true
        return false
    }

    private fun LogRadio.exampleLabel(): String {
        val n = name.trim()
        if (n.isNotEmpty() && !n.equals(mac, ignoreCase = true) && !hiddenSsid) return n
        return mac
    }

    private fun RadioKind.radioWord(n: Int): String = when {
        this == RadioKind.WIFI && n == 1 -> "AP"
        this == RadioKind.WIFI -> "APs"
        n == 1 -> "advertiser"
        else -> "advertisers"
    }

    private data class Fingerprint(
        val key: String,
        val rule: MatchRule,
    )

    private data class Cluster(
        val key: String,
        val primary: MatchRule,
        val extra: MutableList<MatchRule> = mutableListOf(),
        val radioKind: RadioKind,
        val members: MutableSet<String>,
        var radios: List<LogRadio>,
        val proposedName: String,
        val why: String,
        val id: String = key,
    )

    private val BRACKET_NAME = Regex("""\[([A-Za-z][A-Za-z0-9 ]{2,20})\].*""")

    private val HOUSE_WORDS = listOf(
        "guest", "xfinity", "xfinitywifi", "hilton", "toast", "attwifi",
        "androidap", "iphone", "ipad", "myspectrum",
    )

    private val SKIP_PREFIX = setOf(
        "DIRECT", "GUEST", "HOME", "WIFI", "WIRELESS", "SETUP", "NETGEAR", "LINKSYS",
        "DLINK", "TP-LINK", "TPLINK", "ATT", "XFINITY", "HILTON", "TOAST", "IPHONE",
        "IPAD", "ANDROID", "MYWIFI", "DEFAULT", "TY",
    )

    private val CHIP_VENDOR = listOf(
        "espressif", "mediatek", "ampak", "qualcomm", "universal global scientific",
        "realtek",
    )

    private val SKIP_OUI_VENDOR = listOf(
        "google", "apple", "samsung electronics", "microsoft",
    )

    private val PROTOCOL_IE = setOf("0050F2", "000FAC", "506F9A", "8CFDF0")

    /** Company IDs that are a whole OS / vendor, not a product. */
    private val BROAD_COMPANY = setOf(
        0x004C, // Apple
        0x00E0, // Google
        0x0075, // Samsung
        0x0006, // Microsoft
        0x0001, // Nokia (TPMS clones)
        0x00D2, // Google (alt)
    )

    private val GENERIC_UUID = setOf(
        "FEF3", "FCF1", "FCB2", "FFF0", "FEAA", "FFF2",
        "FD5A", "FEED", "FEDD", "FD44",
        "1800", "1801", "180A", "180F", "1812", "181A", "FE2C",
    )
}
