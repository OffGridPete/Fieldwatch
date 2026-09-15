package app.fieldwatch.domain

class SignatureEngine {
    companion object {
        /** 802.11 WPA (Microsoft) and RSN (IEEE) vendor IE OUIs — not a product brand. */
        private val WIFI_PROTOCOL_IE_OUIS = setOf("0050F2", "000FAC")
    }

    /**
     * Catalog OUI lists are thousands of 3-byte prefixes. Walking them with
     * any() on every Live refresh is O(devices × rules). Index by hex OUI.
     */
    @Volatile private var cachedFleets: List<Fleet>? = null
    @Volatile private var cached: Compiled? = null

    fun match(
        devices: Collection<Sighting>,
        fleets: List<Fleet>,
        now: Long = System.currentTimeMillis(),
        policy: DetectionPolicy = DetectionPolicy(),
    ): Map<String, Set<String>> {
        val compiled = compiled(fleets)
        val byKey = LinkedHashMap<String, MutableSet<String>>(devices.size)
        devices.forEach { device ->
            val hits = linkedSetOf<String>()
            val ouiHit = compiled.ouiHits(device)
            compiled.plansFor(device.kind).forEach { idx ->
                val plan = compiled.plans[idx]
                if (!plan.cluster && fleetHits(device, plan, ouiHit[idx])) {
                    hits += plan.id
                }
            }
            dropProtocolIBeacon(hits, compiled)
            dropDjiWhenOsmoCamera(hits)
            dropAirTagsWhenAppleDevice(hits, device)
            dropCiscoWhenMeraki(hits)
            byKey[device.key] = hits
        }
        applyClusters(devices, compiled, byKey, now)
        return byKey
    }

    fun suggestFleet(device: Sighting): Fleet {
        val rules = mutableListOf<MatchRule>()
        // Full address first so this radio is labeled even if it has no name.
        rules += MatchRule(RuleKind.MAC_PREFIX, text = device.mac)
        if (device.name.isNotBlank() && !device.hiddenSsid) {
            val glob = SignatureCandidates.nameGlobOf(device.name)
            rules += if (glob != null) {
                MatchRule(RuleKind.NAME_GLOB, text = glob)
            } else {
                // DIRECT- / ANDROID- / house SSIDs: pin this name, not the generic prefix.
                MatchRule(RuleKind.NAME_CONTAINS, text = device.name.take(32))
            }
        }
        device.serviceUuids.take(3).forEach { uuid ->
            val short = uuid.filter { it.isLetterOrDigit() }.uppercase().let { hex ->
                if (hex.length >= 8) hex.substring(4, 8) else hex.take(4)
            }
            if (short.isNotBlank()) rules += MatchRule(RuleKind.SERVICE_UUID, text = short)
        }
        device.manufacturerId?.let { id ->
            val prefix = device.manufacturerDataHex.take(2)
            if (prefix.isNotBlank()) {
                rules += MatchRule(RuleKind.MANUFACTURER_DATA, companyId = id, dataPrefixHex = prefix)
            } else {
                rules += MatchRule(RuleKind.MANUFACTURER_ID, companyId = id)
            }
        }
        val hasIdentity = rules.any { it.kind != RuleKind.MAC_PREFIX }
        if (!hasIdentity) {
            rules += MatchRule(RuleKind.OUI, text = device.oui)
        }
        val suggested = when {
            device.name.isNotBlank() && !device.hiddenSsid ->
                device.name.replace(Regex("[^A-Za-z0-9 _.-]"), "").take(22).ifBlank { "Signature ${device.oui}" }
            device.vendor != null -> "${device.vendor} ${device.oui}"
            else -> "${device.kind.label()} ${device.mac.takeLast(8)}"
        }
        val rotateNote = if (device.randomized) {
            " Address is randomized and may change; prefer name / UUID / manufacturer rules."
        } else ""
        return Fleet(
            id = java.util.UUID.randomUUID().toString(),
            name = suggested,
            enabled = true,
            matchAny = true,
            colorIndex = if (device.kind == RadioKind.BLE) 3 else 0,
            rules = rules,
            notes = "Created from ${device.kind.label()} ${device.mac}.$rotateNote Keep the MAC rule to track this radio; keep name/UUID/mfg to match siblings.",
        )
    }

    private fun needsCluster(fleet: Fleet): Boolean =
        fleet.minPeers > 0 || fleet.clusterByOui || fleet.sequentialMac

    private fun compiled(fleets: List<Fleet>): Compiled {
        val hit = cached
        if (hit != null && cachedFleets === fleets) return hit
        val next = compile(fleets)
        cached = next
        cachedFleets = fleets
        return next
    }

    private fun compile(fleets: List<Fleet>): Compiled {
        val plans = ArrayList<FleetPlan>(fleets.size)
        val bssidWifi = HashMap<String, MutableList<Int>>(4096)
        val bssidBle = HashMap<String, MutableList<Int>>(256)
        val vendorIe = HashMap<String, MutableList<Int>>(4096)
        val longOui = ArrayList<LongOui>(8)
        val wifiPlans = ArrayList<Int>(fleets.size)
        val blePlans = ArrayList<Int>(fleets.size)
        fleets.forEachIndexed { idx, fleet ->
            val active = fleet.rules.filter { it.enabled }
            val other = ArrayList<FastRule>(8)
            active.forEach { rule ->
                when (rule.kind) {
                    RuleKind.OUI ->
                        indexOui(rule.text, rule.radio, idx, bssid = true, vendor = true, bssidWifi, bssidBle, vendorIe, longOui)
                    RuleKind.MAC_PREFIX ->
                        indexOui(rule.text, rule.radio, idx, bssid = true, vendor = false, bssidWifi, bssidBle, vendorIe, longOui)
                    RuleKind.VENDOR_IE_OUI ->
                        indexOui(rule.text, rule.radio, idx, bssid = false, vendor = true, bssidWifi, bssidBle, vendorIe, longOui)
                    else -> compileOther(rule)?.let { other += it }
                }
            }
            plans += FleetPlan(
                id = fleet.id,
                fleet = fleet,
                cluster = needsCluster(fleet),
                matchAny = fleet.matchAny,
                otherRules = other,
                rawRules = active,
            )
            when (radioScope(active)) {
                RadioKind.WIFI -> wifiPlans += idx
                RadioKind.BLE -> blePlans += idx
                null -> {
                    wifiPlans += idx
                    blePlans += idx
                }
            }
        }
        return Compiled(
            plans = plans,
            wifiPlans = wifiPlans.toIntArray(),
            blePlans = blePlans.toIntArray(),
            bssidWifi = freeze(bssidWifi),
            bssidBle = freeze(bssidBle),
            vendorIe = freeze(vendorIe),
            longOui = longOui,
        )
    }

    private fun indexOui(
        text: String,
        radio: RadioKind?,
        fleetIdx: Int,
        bssid: Boolean,
        vendor: Boolean,
        bssidWifi: MutableMap<String, MutableList<Int>>,
        bssidBle: MutableMap<String, MutableList<Int>>,
        vendorIe: MutableMap<String, MutableList<Int>>,
        longOui: MutableList<LongOui>,
    ) {
        val hex = hexOnly(text)
        if (hex.isEmpty()) return
        if (hex.length != 6) {
            longOui += LongOui(hex, radio, bssid, vendor, fleetIdx)
            return
        }
        if (bssid) {
            if (radio != RadioKind.BLE) addIdx(bssidWifi, hex, fleetIdx)
            if (radio != RadioKind.WIFI) addIdx(bssidBle, hex, fleetIdx)
        }
        if (vendor && radio != RadioKind.BLE && hex !in WIFI_PROTOCOL_IE_OUIS) {
            addIdx(vendorIe, hex, fleetIdx)
        }
    }

    private fun addIdx(map: MutableMap<String, MutableList<Int>>, key: String, idx: Int) {
        val list = map.getOrPut(key) { ArrayList(2) }
        if (idx !in list) list.add(idx)
    }

    private fun freeze(map: Map<String, List<Int>>): Map<String, IntArray> {
        if (map.isEmpty()) return emptyMap()
        val out = HashMap<String, IntArray>(map.size)
        map.forEach { (k, v) -> out[k] = v.toIntArray() }
        return out
    }

    /**
     * WIFI = every rule is Wi-Fi-only (AP rows). BLE = every rule is BLE-only.
     * null = mixed or unscoped (Tesla, DJI, UniFi name, custom) — still searched on both.
     */
    private fun radioScope(rules: List<MatchRule>): RadioKind? {
        var wifi = false
        var ble = false
        var both = false
        rules.forEach { rule ->
            when (ruleScope(rule)) {
                RadioKind.WIFI -> wifi = true
                RadioKind.BLE -> ble = true
                null -> both = true
            }
        }
        return when {
            both || (wifi && ble) -> null
            wifi -> RadioKind.WIFI
            ble -> RadioKind.BLE
            else -> null
        }
    }

    private fun ruleScope(rule: MatchRule): RadioKind? = when (rule.kind) {
        RuleKind.VENDOR_IE_OUI, RuleKind.HIDDEN_SSID -> RadioKind.WIFI
        RuleKind.RADIO_KIND -> rule.radio
        RuleKind.SERVICE_UUID, RuleKind.MANUFACTURER_ID, RuleKind.MANUFACTURER_DATA ->
            rule.radio ?: RadioKind.BLE
        else -> rule.radio
    }

    private fun compileOther(rule: MatchRule): FastRule? = when (rule.kind) {
        RuleKind.NAME_CONTAINS ->
            if (rule.text.isBlank()) null else FastRule.Contains(rule.text, rule.radio)
        RuleKind.NAME_GLOB ->
            if (rule.text.isBlank()) null else FastRule.Glob(compileGlob(rule.text), rule.radio)
        RuleKind.SERVICE_UUID ->
            if (rule.text.isBlank()) null else FastRule.Uuid(uuidAliases(rule.text), rule.radio)
        RuleKind.MANUFACTURER_ID ->
            FastRule.MfgId(rule.companyId, rule.radio)
        RuleKind.MANUFACTURER_DATA -> {
            val prefix = hexOnly(rule.dataPrefixHex)
            if (prefix.isEmpty()) null else FastRule.MfgData(rule.companyId, prefix, rule.radio)
        }
        RuleKind.RADIO_KIND -> FastRule.Radio(rule.radio)
        RuleKind.HIDDEN_SSID -> FastRule.Hidden
        RuleKind.OUI, RuleKind.MAC_PREFIX, RuleKind.VENDOR_IE_OUI -> null
    }

    private fun compileGlob(pattern: String): Regex {
        val body = buildString {
            append('^')
            pattern.forEach { ch ->
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    else -> append(Regex.escape(ch.toString()))
                }
            }
            append('$')
        }
        return body.toRegex(RegexOption.IGNORE_CASE)
    }

    private fun fleetHits(device: Sighting, plan: FleetPlan, ouiHit: Boolean): Boolean {
        if (plan.id == "fleet-ibeacon" && isTeslaPhoneKeyIBeacon(device)) return false
        if (plan.rawRules.isEmpty()) return false
        if (!plan.matchAny) {
            return plan.rawRules.all { ruleHits(device, it) }
        }
        if (ouiHit) return true
        if (plan.otherRules.isEmpty()) return false
        return plan.otherRules.any { it.hits(device) }
    }

    /**
     * iBeacon is a payload layout, not a product. If a radio already matched a
     * non-beacon signature (Sony TV, Tesla phone-key, …), drop the iBeacon chip.
     * Minew / Estimote / Kontakt / Target Atrius basket stay dual-labeled — those rows are beacon class.
     */
    private fun dropProtocolIBeacon(hits: MutableSet<String>, compiled: Compiled) {
        if ("fleet-ibeacon" !in hits) return
        val otherProduct = hits.any { id ->
            if (id == "fleet-ibeacon") return@any false
            compiled.plans.any { it.id == id && it.fleet.kind != SignatureClass.BEACON }
        }
        if (otherProduct) hits.remove("fleet-ibeacon")
    }

    /** Osmo cameras share DJI company 0x08AA. Prefer the Osmo Cameras row over DJI. */
    private fun dropDjiWhenOsmoCamera(hits: MutableSet<String>) {
        if ("fleet-osmo" in hits) hits.remove("fleet-dji")
    }

    /**
     * Meraki APs are Cisco-owned and typically include Cisco Systems vendor
     * IE 00:00:0C (CCX / AP-name). The BSSID is Meraki; do not also chip Cisco.
     */
    private fun dropCiscoWhenMeraki(hits: MutableSet<String>) {
        if ("fleet-meraki" in hits) hits.remove("fleet-cisco")
    }

    /**
     * Offline Finding (0x12) is the Find My network protocol, not an AirTag
     * identity. iPhones advertise it so the network can locate them. Drop
     * AirTags when Continuity / Apple Device already labeled the radio,
     * unless the advertised name is AirTag or UUID FD44 is present.
     */
    private fun dropAirTagsWhenAppleDevice(hits: MutableSet<String>, device: Sighting) {
        if ("fleet-airtag" !in hits) return
        if (device.name.contains("AirTag", ignoreCase = true)) return
        if (hasFindMyAccessoryUuid(device)) return
        val appleProduct = "fleet-apple-device" in hits || "fleet-apple-audio" in hits
        if (appleProduct || TrackerMatch.isAppleContinuity(device)) {
            hits.remove("fleet-airtag")
        }
    }

    private fun hasFindMyAccessoryUuid(device: Sighting): Boolean {
        val want = uuidAliases("FD44")
        val have = device.serviceUuids + device.facts.serviceData.map { it.uuid }
        return have.any { uuid -> uuidAliases(uuid).any { it in want } }
    }

    /** Tesla phone-key ads use Apple iBeacon so iOS can find the car. Label Tesla, not iBeacon. */
    private fun isTeslaPhoneKeyIBeacon(device: Sighting): Boolean {
        val prefix = DefaultCatalog.TESLA_IBEACON_MFG_PREFIX
        return mfgRecords(device).any { rec ->
            rec.companyId == 0x004C && hexOnly(rec.dataHex).startsWith(prefix)
        }
    }

    private fun ruleHits(device: Sighting, rule: MatchRule): Boolean {
        if (rule.kind != RuleKind.RADIO_KIND && rule.radio != null && device.kind != rule.radio) {
            return false
        }
        return when (rule.kind) {
            RuleKind.OUI, RuleKind.MAC_PREFIX ->
                MacUtil.matchesPrefix(device.mac, rule.text) ||
                    (rule.kind == RuleKind.OUI && wifiVendorIeHitsOui(device, rule.text)) ||
                    (rule.kind == RuleKind.OUI && recoveredWifiOuiHits(device, rule.text))
            RuleKind.NAME_CONTAINS ->
                device.name.isNotBlank() && TextMatch.contains(device.name, rule.text)
            RuleKind.NAME_GLOB ->
                device.name.isNotBlank() && TextMatch.glob(device.name, rule.text)
            RuleKind.SERVICE_UUID -> {
                val want = uuidAliases(rule.text)
                val have = device.serviceUuids + device.facts.serviceData.map { it.uuid }
                have.any { uuid -> uuidAliases(uuid).any { it in want } }
            }
            RuleKind.MANUFACTURER_ID ->
                mfgRecords(device).any { it.companyId == rule.companyId }
            RuleKind.MANUFACTURER_DATA -> {
                val prefix = hexOnly(rule.dataPrefixHex)
                prefix.isNotEmpty() && mfgRecords(device).any { rec ->
                    (rule.companyId == 0 || rec.companyId == rule.companyId) &&
                        hexOnly(rec.dataHex).startsWith(prefix)
                }
            }
            RuleKind.RADIO_KIND ->
                rule.radio == null || device.kind == rule.radio
            RuleKind.HIDDEN_SSID ->
                device.hiddenSsid
            RuleKind.VENDOR_IE_OUI ->
                wifiVendorIeHitsOui(device, rule.text)
        }
    }

    /** Virtual BSSID: guest/mesh radios set the local bit on a burned-in 24-bit OUI. */
    private fun recoveredWifiOuiHits(device: Sighting, prefix: String): Boolean {
        if (device.kind != RadioKind.WIFI) return false
        val univ = MacUtil.wifiOui24Universal(device.mac) ?: return false
        val want = prefix.filter { it.isLetterOrDigit() }.uppercase()
        return want.length == 6 && univ == want
    }

    /**
     * Wi-Fi vendor IEs (detail “Vendor OUI” list), not the BSSID.
     * Skip WPA (00:50:F2) and RSN (00:0F:AC) — those are protocol tags, not the product.
     * UniFi virtual BSSIDs also hit on the recovered universal OUI when that prefix is cataloged.
     */
    private fun wifiVendorIeHitsOui(device: Sighting, prefix: String): Boolean {
        if (device.kind != RadioKind.WIFI) return false
        val want = hexOnly(prefix)
        if (want.isEmpty() || want.take(6) in WIFI_PROTOCOL_IE_OUIS) return false
        return device.vendorIeOuis.any { ie ->
            val hex = hexOnly(ie)
            if (hex.take(6) in WIFI_PROTOCOL_IE_OUIS) return@any false
            hex.startsWith(want)
        }
    }

    private fun mfgRecords(device: Sighting): List<MfgRecord> {
        val fromFacts = device.facts.mfgRecords
        if (fromFacts.isNotEmpty()) return fromFacts
        val id = device.manufacturerId ?: return emptyList()
        return listOf(MfgRecord(id, device.manufacturerDataHex))
    }

    private fun applyClusters(
        devices: Collection<Sighting>,
        compiled: Compiled,
        byKey: MutableMap<String, MutableSet<String>>,
        now: Long,
    ) {
        compiled.plans.forEachIndexed { idx, plan ->
            if (!plan.cluster) return@forEachIndexed
            val fleet = plan.fleet
            val windowMs = (if (fleet.peerWindowSec <= 0) 60 else fleet.peerWindowSec) * 1000L
            val live = devices.filter { now - it.lastSeen <= windowMs }
            live.forEach { a ->
                val aOui = compiled.ouiHits(a)
                val aEligible = when {
                    plan.rawRules.isEmpty() -> true
                    fleetHits(a, plan, aOui[idx]) -> true
                    fleet.clusterByOui && (a.name.isBlank() || a.randomized) -> true
                    else -> false
                }
                if (!aEligible) return@forEach
                var peers = 1
                live.forEach { b ->
                    if (b.key == a.key) return@forEach
                    if (fleet.clusterByOui && a.oui != b.oui) return@forEach
                    if (fleet.sequentialMac) {
                        val diff = kotlin.math.abs(MacUtil.last16(a.mac) - MacUtil.last16(b.mac))
                        if (diff > 64) return@forEach
                    }
                    if (plan.rawRules.isNotEmpty() && !fleet.clusterByOui) {
                        val bOui = compiled.ouiHits(b)
                        if (!fleetHits(b, plan, bOui[idx])) return@forEach
                    }
                    peers++
                }
                val need = if (fleet.minPeers <= 0) 3 else fleet.minPeers
                if (peers >= need) {
                    byKey.getOrPut(a.key) { linkedSetOf() }.add(plan.id)
                }
            }
        }
    }

    private class Compiled(
        val plans: List<FleetPlan>,
        val wifiPlans: IntArray,
        val blePlans: IntArray,
        val bssidWifi: Map<String, IntArray>,
        val bssidBle: Map<String, IntArray>,
        val vendorIe: Map<String, IntArray>,
        val longOui: List<LongOui>,
    ) {
        fun plansFor(kind: RadioKind): IntArray =
            if (kind == RadioKind.WIFI) wifiPlans else blePlans

        fun ouiHits(device: Sighting): BooleanArray {
            val hits = BooleanArray(plans.size)
            val macHex = hexOnly(device.mac)
            val oui6 = if (macHex.length >= 6) macHex.substring(0, 6) else macHex
            val bssidMap = if (device.kind == RadioKind.WIFI) bssidWifi else bssidBle
            mark(hits, bssidMap[oui6])
            if (device.kind == RadioKind.WIFI) {
                MacUtil.wifiOui24Universal(device.mac)?.let { univ ->
                    mark(hits, bssidMap[univ])
                }
                device.vendorIeOuis.forEach { ie ->
                    val hex = hexOnly(ie)
                    if (hex.length < 6) return@forEach
                    val ie6 = hex.substring(0, 6)
                    if (ie6 in WIFI_PROTOCOL_IE_OUIS) return@forEach
                    mark(hits, vendorIe[ie6])
                }
            }
            if (longOui.isNotEmpty() && macHex.isNotEmpty()) {
                longOui.forEach { rule ->
                    if (rule.radio != null && rule.radio != device.kind) return@forEach
                    if (rule.bssid && macHex.startsWith(rule.hex)) {
                        hits[rule.fleetIdx] = true
                    }
                    if (rule.vendor && device.kind == RadioKind.WIFI) {
                        device.vendorIeOuis.forEach { ie ->
                            val hex = hexOnly(ie)
                            if (hex.take(6) in WIFI_PROTOCOL_IE_OUIS) return@forEach
                            if (hex.startsWith(rule.hex)) hits[rule.fleetIdx] = true
                        }
                    }
                }
            }
            return hits
        }

        private fun mark(hits: BooleanArray, idxs: IntArray?) {
            if (idxs == null) return
            idxs.forEach { hits[it] = true }
        }
    }

    private class FleetPlan(
        val id: String,
        val fleet: Fleet,
        val cluster: Boolean,
        val matchAny: Boolean,
        val otherRules: List<FastRule>,
        val rawRules: List<MatchRule>,
    )

    private class LongOui(
        val hex: String,
        val radio: RadioKind?,
        val bssid: Boolean,
        val vendor: Boolean,
        val fleetIdx: Int,
    )

    private sealed class FastRule {
        abstract fun hits(device: Sighting): Boolean

        protected fun radioOk(device: Sighting, radio: RadioKind?): Boolean =
            radio == null || device.kind == radio

        class Contains(val needle: String, val radio: RadioKind?) : FastRule() {
            override fun hits(device: Sighting): Boolean =
                radioOk(device, radio) && device.name.isNotBlank() &&
                    TextMatch.contains(device.name, needle)
        }

        class Glob(val regex: Regex, val radio: RadioKind?) : FastRule() {
            override fun hits(device: Sighting): Boolean =
                radioOk(device, radio) && device.name.isNotBlank() && regex.matches(device.name)
        }

        class Uuid(val aliases: Set<String>, val radio: RadioKind?) : FastRule() {
            override fun hits(device: Sighting): Boolean {
                if (!radioOk(device, radio)) return false
                val have = device.serviceUuids + device.facts.serviceData.map { it.uuid }
                return have.any { uuid -> uuidAliases(uuid).any { it in aliases } }
            }
        }

        class MfgId(val id: Int, val radio: RadioKind?) : FastRule() {
            override fun hits(device: Sighting): Boolean =
                radioOk(device, radio) && mfg(device).any { it.companyId == id }
        }

        class MfgData(val id: Int, val prefix: String, val radio: RadioKind?) : FastRule() {
            override fun hits(device: Sighting): Boolean {
                if (!radioOk(device, radio)) return false
                return mfg(device).any { rec ->
                    (id == 0 || rec.companyId == id) && hexOnly(rec.dataHex).startsWith(prefix)
                }
            }
        }

        class Radio(val kind: RadioKind?) : FastRule() {
            override fun hits(device: Sighting): Boolean =
                kind == null || device.kind == kind
        }

        object Hidden : FastRule() {
            override fun hits(device: Sighting): Boolean = device.hiddenSsid
        }

        companion object {
            fun mfg(device: Sighting): List<MfgRecord> {
                val fromFacts = device.facts.mfgRecords
                if (fromFacts.isNotEmpty()) return fromFacts
                val id = device.manufacturerId ?: return emptyList()
                return listOf(MfgRecord(id, device.manufacturerDataHex))
            }
        }
    }
}

private fun hexOnly(raw: String): String {
    if (raw.isEmpty()) return raw
    var colon = false
    for (ch in raw) {
        if (ch == ':' || ch == '-') {
            colon = true
            break
        }
        if (!ch.isLetterOrDigit()) {
            colon = true
            break
        }
    }
    if (!colon) return raw.uppercase()
    return buildString(raw.length) {
        for (ch in raw) {
            if (ch.isLetterOrDigit()) append(ch.uppercaseChar())
        }
    }
}
