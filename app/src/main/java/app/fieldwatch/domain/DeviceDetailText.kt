package app.fieldwatch.domain

import app.fieldwatch.radio.BleAdParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-text dump of the device-detail screen. Same fields, no sparkline/presence art.
 * Not a legal identity.
 */
object DeviceDetailText {
    fun build(
        device: Sighting,
        signatureNames: List<String>,
        now: Long = System.currentTimeMillis(),
        attentionNotes: List<Pair<String, String>> = emptyList(),
        signatureNotes: List<Pair<String, String>> = emptyList(),
        fleets: List<Fleet> = emptyList(),
        mine: Boolean = false,
        placeLines: List<String> = emptyList(),
        demo: Boolean = false,
        phoneLat: Double? = null,
        phoneLon: Double? = null,
    ): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US)
        val facts = device.facts
        val title = device.listTitle(signatureNames)
        val guess = DeviceExplain.guess(device, signatureNames)
        val out = StringBuilder()

        fun line(label: String, value: String) {
            out.append(label).append(": ").append(value.trim()).append('\n')
        }
        fun section(title: String) {
            out.append('\n').append("## ").append(title).append('\n')
        }

        out.append(LanguageText.text("device.share.title")).append('\n')
        out.append(iso.format(Date(now))).append('\n')
        out.append(LanguageText.text("device.share.experimental")).append('\n')
        out.append('\n')
        out.append(title).append('\n')
        line("MAC", device.mac)
        if (device.name.isNotBlank()) line(LanguageText.text("display.advertised"), device.name)
        if (mine) {
            line(LanguageText.text("ui.marked.mine"), LanguageText.text("device.share.mine"))
        }

        out.append('\n')
        out.append(LanguageText.text("ui.what.looks.like")).append(": ").append(guess.headline).append('\n')
        out.append(guess.because).append('\n')
        if (attentionNotes.isNotEmpty()) {
            section(LanguageText.text("chrome.extra.attention"))
            attentionNotes.forEach { (name, note) ->
                out.append(LanguageText.text("device.share.extra", mapOf("name" to name)))
                    .append(" ")
                    .append(note.trim())
                    .append('\n')
            }
            out.append(LanguageText.text("ui.pattern.match.not.identity")).append('\n')
        }
        if (signatureNotes.isNotEmpty()) {
            section(LanguageText.text("ui.notes.label"))
            signatureNotes.forEach { (name, note) ->
                out.append(name).append(": ").append(note.trim()).append('\n')
            }
        }

        section(LanguageText.text("ui.identity.label"))
        line(
            LanguageText.text("ui.radio.label"),
            if (device.kind == RadioKind.WIFI) {
                LanguageText.text("ui.wi.fi.access.point")
            } else {
                LanguageText.text("ui.bluetooth.low.energy.advertiser")
            },
        )
        line(LanguageText.text("ui.address.label"), DeviceExplain.addressExplain(device))
        vendorLine(device)?.let { line(LanguageText.text("ui.who.made"), it.replace('\n', ' ')) }
            ?: line(
                LanguageText.text("ui.oui.vendor.prefix"),
                LanguageText.text("device.oui.none", mapOf("oui" to device.oui)),
            )
        if (device.hiddenSsid) {
            line(LanguageText.text("ui.network.name.ssid"), LanguageText.text("ui.hidden.ap.beaconing.but"))
        }

        section(LanguageText.text("ui.signal.label"))
        if (device.gone) {
            line(LanguageText.text("ui.how.loud.here.rssi"), LanguageText.text("ui.not.available"))
            val last = Rssi.lastMeasured(device.rssi, device.rssiHistory)
            line(
                LanguageText.text("ui.last.heard.2"),
                last?.let { "$it dBm" } ?: LanguageText.text("ui.not.available"),
            )
        } else {
            line(LanguageText.text("ui.how.loud.here.rssi"), DeviceExplain.rssiExplain(device.rssi))
            out.append(LanguageText.text("ui.closer.0.dbm.louder")).append('\n')
        }
        line(
            LanguageText.text("ui.heard.range.session"),
            Rssi.sessionRange(device.rssiMin, device.rssiMax, device.rssiHistory),
        )
        facts.txPowerDbm?.let {
            line(LanguageText.text("ui.claimed.transmit.power"), LanguageText.text("device.tx", mapOf("n" to it.toString())))
        }
        if (device.channel != 0 || device.frequencyMhz != 0) {
            line(
                LanguageText.text("ui.channel.frequency"),
                buildString {
                    if (device.channel != 0) {
                        append(LanguageText.text("device.channel", mapOf("n" to device.channel.toString())))
                    }
                    if (device.frequencyMhz != 0) {
                        if (isNotEmpty()) append("  ·  ")
                        append("${device.frequencyMhz} MHz")
                    }
                    facts.channelWidth?.let {
                        append("  ·  ")
                        append(LanguageText.text("device.wide", mapOf("width" to it)))
                    }
                },
            )
        }
        facts.wifiStandard?.let { line(LanguageText.text("ui.wi.fi.generation"), it) }
        if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
            line(
                LanguageText.text("ui.center.frequencies"),
                listOfNotNull(
                    facts.centerFreq0?.let { "$it MHz" },
                    facts.centerFreq1?.let { "$it MHz" },
                ).joinToString("  ·  "),
            )
        }
        val rssiTail = device.rssiHistory.filter { Rssi.measured(it.rssi) }.takeLast(24)
        if (rssiTail.isNotEmpty()) {
            line(
                LanguageText.text("device.rssi.recent"),
                rssiTail.joinToString(", ") { it.rssi.toString() },
            )
        }

        if (device.kind == RadioKind.BLE) {
            section(LanguageText.text("ui.bluetooth.advertisement"))
            facts.primaryPhy?.let {
                val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                line(LanguageText.text("ui.radio.phy"), phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(phy) })
            }
            facts.connectable?.let {
                line(
                    LanguageText.text("ui.connectable.label"),
                    if (it) LanguageText.text("ui.yes.phone.could.open")
                    else LanguageText.text("ui.no.broadcast.only.can"),
                )
            }
            facts.advertisingIntervalMs?.let {
                line(
                    LanguageText.text("ui.how.often.advertises"),
                    LanguageText.text("device.burst", mapOf("n" to "%.0f".format(java.util.Locale.US, it))),
                )
            }
            facts.periodicIntervalMs?.let {
                line(LanguageText.text("ui.periodic.advertising"), "%.0f ms".format(java.util.Locale.US, it))
            }
            facts.advFlags?.let { flags ->
                line(LanguageText.text("ui.discoverability.label"), DeviceExplain.flagsExplain(flags))
                line(LanguageText.text("ui.flags.raw"), "0x%02X".format(java.util.Locale.US, flags))
            }
            facts.appearance?.let { value ->
                val name = RadioDb.appearance(value)
                line(
                    LanguageText.text("ui.what.says.appearance"),
                    name ?: LanguageText.text(
                        "device.appearance.unlisted",
                        mapOf("code" to "0x%04X".format(java.util.Locale.US, value)),
                    ),
                )
                line(LanguageText.text("ui.appearance.code"), "0x%04X".format(java.util.Locale.US, value))
            }
            CodDecoder.decodeOrNull(facts.deviceClass)?.let { cod ->
                line(
                    LanguageText.text("ui.classic.bluetooth.class"),
                    buildString {
                        append(cod.major)
                        if (cod.minor.isNotBlank()) append(" / ").append(cod.minor)
                        if (cod.services.isNotEmpty()) {
                            append(". ")
                            append(LanguageText.text("ui.also.offers"))
                            append(" ")
                            append(cod.services.joinToString(", "))
                        }
                    },
                )
            }
        }

        if (device.kind == RadioKind.WIFI) {
            section(LanguageText.text("ui.wi.fi.access.point.2"))
            facts.security?.let {
                line(LanguageText.text("ui.encryption.login"), DeviceExplain.wifiSecurityExplain(it))
                if (it.isNotBlank()) line(LanguageText.text("ui.security.string"), it)
            }
            facts.supportedRates?.let {
                line(LanguageText.text("ui.supported.rates"), "$it " + LanguageText.text("device.rates.note"))
            }
            facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                line(LanguageText.text("ui.capability.string"), it)
            }
            facts.interworking?.lines(demo)?.takeIf { it.isNotEmpty() }?.let { rows ->
                section(LanguageText.text("ui.ap.says"))
                out.append(LanguageText.text("ui.ap.says.note")).append('\n')
                rows.forEach { (label, value) -> line(label, value) }
            }
        }

        device.payloadAircraft?.trim()?.takeIf { it.isNotEmpty() }?.let {
            line(LanguageText.text("ui.aircraft.label"), it)
        }
        device.payloadAuth?.let { auth ->
            section(LanguageText.text("rid.auth.type"))
            out.append(auth.typeName()).append('\n')
            line(LanguageText.text("rid.auth.page"), auth.pageValue())
            auth.timeText()?.let { line(LanguageText.text("rid.auth.time"), it) }
            auth.dataLine()?.let { out.append(it).append('\n') }
            auth.pastLine()?.let { out.append(it).append('\n') }
            auth.note()?.let { out.append(it).append('\n') }
        }
        val french = FrenchRid.fromFacts(facts)
        if (french.present) {
            section(LanguageText.text("rid.french.plate"))
            french.lines(demo).forEach { (label, value) -> line(label, value) }
            french.note()?.let { out.append(it).append('\n') }
        }
        facts.homekit?.let { hk ->
            section("HomeKit")
            hk.rows(demo).forEach { (label, value) -> line(label, value) }
            hk.setupLine()?.let { out.append(it).append('\n') }
            out.append(hk.note()).append('\n')
        }
        facts.matter?.let { matter ->
            section(LanguageText.text("matter.mode"))
            out.append(matter.headline()).append('\n')
            matter.rows(demo).forEach { (label, value) -> line(label, value) }
            matter.flagLines().forEach { out.append(it).append('\n') }
            matter.note()?.let { out.append(it).append('\n') }
        }
        facts.sidewalk?.let { sidewalk ->
            section(LanguageText.text("sw.beacon"))
            out.append(sidewalk.headline()).append('\n')
            out.append(sidewalk.modeLine()).append('\n')
            sidewalk.batteryRow().let { (label, value) -> line(label, value) }
            sidewalk.protocolLine()?.let { out.append(it).append('\n') }
            out.append(sidewalk.note()).append('\n')
            sidewalk.registrationNote()?.let { out.append(it).append('\n') }
        }
        facts.bthome?.let { bthome ->
            section("BTHome")
            bthome.versionRow().let { (label, value) -> line(label, value) }
            bthome.triggerLine()?.let { out.append(it).append('\n') }
            bthome.encryptedLine()?.let { out.append(it).append('\n') }
            bthome.objectRows(demo).forEach { (label, value) -> line(label, value) }
            bthome.commandLines().forEach { out.append(it).append('\n') }
            out.append(bthome.note()).append('\n')
        }
        facts.serviceData.forEach { sd ->
            ExposureNotification.label(sd)?.let { label ->
                section(LanguageText.text("en.label"))
                label.rows.forEach { (name, value) -> line(name, value) }
                label.fallback?.let { out.append(it).append('\n') }
                out.append(LanguageText.text("en.note")).append('\n')
            }
        }
        appendAdvertised(device, demo, phoneLat, phoneLon, french, ::line)

        if (fleets.isNotEmpty() && (device.kind == RadioKind.BLE || device.kind == RadioKind.WIFI)) {
            val decoded = SignatureFieldDecoder.decodeSighting(device, fleets)
            if (decoded.isNotEmpty()) {
                section(LanguageText.text("ui.decoded.fields"))
                decoded.forEach { row ->
                    line(row.label, row.display)
                    if (row.note.isNotBlank()) line(LanguageText.text("ui.note.label"), row.note)
                }
            }
        }

        if (device.serviceUuids.isNotEmpty()) {
            section(LanguageText.text("ui.services.offers"))
            line(
                LanguageText.text("ui.service.ids"),
                device.serviceUuids.joinToString("; ") { uuid ->
                    DeviceExplain.uuidGloss(uuid)?.let { "$uuid  ·  $it" } ?: uuid
                },
            )
        }
        if (facts.serviceData.isNotEmpty()) {
            facts.serviceData.forEach { sd ->
                val named = RadioDb.serviceUuid(sd.uuid)?.let { " ($it)" } ?: ""
                if (!ExposureNotification.matches(sd.uuid)) {
                    AdvPayloadDecoder.decodeService(sd).forEach { field -> line(field.label, field.value) }
                }
                if (SmartTag.isService(sd.uuid)) {
                    SmartTag.parse(sd.dataHex)?.let { tag ->
                        tag.state?.let { line(LanguageText.text("device.state"), it) }
                        line(LanguageText.text("device.aging"), tag.aging.toString())
                        line(LanguageText.text("device.battery"), tag.battery)
                        line("UWB", tag.uwb)
                        val idText = if (demo) "masked" else tag.privacyId.hexSpaced()
                        line(
                            LanguageText.text("device.privacy"),
                            idText + "\n" + LanguageText.text("device.not.serial"),
                        )
                    }
                }
                line(
                    LanguageText.text("device.service.data", mapOf("id" to uuidShort(sd.uuid))) + named,
                    if (demo && sd.dataHex.isNotBlank()) maskedRawBytes(sd.dataHex, true)
                    else sd.dataHex.hexSpaced().ifBlank { LanguageText.text("device.empty") },
                )
            }
        }

        val mfg = facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let {
                listOf(MfgRecord(it, device.manufacturerDataHex))
            } ?: emptyList()
        }
        if (mfg.isNotEmpty()) {
            section(LanguageText.text("ui.maker.data.inside.ad"))
            mfg.forEach { rec ->
                val company = RadioDb.company(rec.companyId) ?: LanguageText.text("ui.not.bluetooth.company.list")
                line(
                    LanguageText.text(
                        "device.company",
                        mapOf("id" to "0x%04X".format(java.util.Locale.US, rec.companyId)),
                    ),
                    company,
                )
                BleAdParser.mfgDecodedFields(rec).forEach { (k, v) -> line(k, v) }
                if (rec.dataHex.isNotBlank()) {
                    line(
                        LanguageText.text("device.raw", mapOf("n" to (rec.dataHex.length / 2).toString())),
                        if (demo) maskedRawBytes(rec.dataHex, false) else rec.dataHex.hexSpaced(),
                    )
                }
            }
        }

        if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
            section(LanguageText.text("ui.wi.fi.vendor.tags"))
            val rows = facts.vendorIes.ifEmpty {
                device.vendorIeOuis.map { VendorIeRecord(it, -1, "") }
            }
            rows.forEach { ie ->
                val org = RadioDb.vendorForOui24(ie.oui)
                val type = if (ie.type >= 0) {
                    " " + LanguageText.text("device.vendor.type", mapOf("n" to ie.type.toString()))
                } else {
                    ""
                }
                line(
                    LanguageText.text("device.vendor", mapOf("oui" to ie.oui)) + type,
                    buildString {
                        append(
                            VendorIeLabel.lead(
                                ie,
                                org,
                                LanguageText.text("ui.unknown.ieee.oui"),
                                LanguageText.text("ui.extra.ap.information.element"),
                            ),
                        )
                        if (ie.dataHex.isNotBlank()) {
                            append(" ")
                            append(if (demo) maskedRawBytes(ie.dataHex, true) else ie.dataHex.hexSpaced())
                        }
                    },
                )
            }
        }

        section(LanguageText.text("ui.session.label"))
        line(LanguageText.text("ui.first.seen"), fmt.format(Date(device.firstSeen)))
        line(LanguageText.text("ui.last.seen"), fmt.format(Date(device.lastSeen)))
        line(LanguageText.text("ui.hits.label"), device.hitCount.toString())
        placeLines.forEach { out.append(it).append('\n') }
        Geo.screenCoord(device.latitude, device.longitude, demo)?.let {
            line(LanguageText.text("ui.last.fix"), it)
            out.append(LanguageText.text("device.last.fix.note")).append('\n')
        }
        Geo.screenGrid(device.latitude, device.longitude, demo)?.let { line(LanguageText.text("ui.grid"), it) }
        if (device.fleetIds.isNotEmpty()) {
            line(LanguageText.text("ui.matched.signatures"), signatureNames.joinToString("; ").ifBlank {
                device.fleetIds.joinToString("; ")
            })
        }
        if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
            line(
                LanguageText.text("ui.raw.advertisement"),
                if (demo) maskedRawBytes(device.rawHex, true) else device.rawHex.hexSpaced(),
            )
        }
        presenceLine(device, now, fmt)?.let { line(LanguageText.text("ui.presence.15.min"), it) }
        return out.toString().trimEnd() + "\n"
    }

    private fun appendAdvertised(
        device: Sighting,
        demo: Boolean,
        phoneLat: Double?,
        phoneLon: Double?,
        french: FrenchRid.Plate,
        line: (String, String) -> Unit,
    ) {
        if (PayloadLocation.validCoord(device.payloadLat, device.payloadLon)) {
            line(LanguageText.text("ui.aircraft.position"), Geo.screenCoord(device.payloadLat, device.payloadLon, demo)!!)
            Geo.screenGrid(device.payloadLat, device.payloadLon, demo)?.let { line(LanguageText.text("ui.grid"), it) }
            Geo.liveRange(phoneLat, phoneLon, device.payloadLat, device.payloadLon, demo)?.let {
                line(LanguageText.text("device.from.phone"), it)
            }
        }
        if (PayloadLocation.validCoord(device.payloadOpLat, device.payloadOpLon)) {
            val label = french.operatorLabel(device.payloadOpKind)
            line(label, Geo.screenCoord(device.payloadOpLat, device.payloadOpLon, demo)!!)
            Geo.screenGrid(device.payloadOpLat, device.payloadOpLon, demo)?.let { line(LanguageText.text("ui.grid"), it) }
            Geo.liveRange(phoneLat, phoneLon, device.payloadOpLat, device.payloadOpLon, demo)?.let {
                line(LanguageText.text("device.from.phone"), it)
            }
        }
    }

    private fun vendorLine(device: Sighting): String? {
        val parts = ArrayList<String>(3)
        device.vendor?.let {
            parts += LanguageText.text("device.vendor.line", mapOf("vendor" to it, "oui" to device.oui))
        }
        val mfgId = device.facts.mfgRecords.firstOrNull()?.companyId ?: device.manufacturerId
        if (mfgId != null) {
            val company = RadioDb.company(mfgId)
            parts += LanguageText.text(
                "device.company.ad",
                mapOf(
                    "company" to (company ?: LanguageText.text("device.unlisted")),
                    "id" to "0x%04X".format(java.util.Locale.US, mfgId),
                ),
            )
        }
        return parts.joinToString(" ").ifBlank { null }
    }

    private fun uuidShort(uuid: String): String {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return if (hex.length >= 8 && hex.startsWith("0000")) hex.substring(4, 8) else uuid.take(8)
    }

    private fun presenceLine(device: Sighting, now: Long, fmt: SimpleDateFormat): String? {
        if (device.presence.isEmpty()) return null
        val from = now - 15 * 60 * 1000L
        val spans = device.presence.filter { (it.end ?: now) >= from }
        if (spans.isEmpty()) return null
        return spans.joinToString("; ") { span ->
            val start = fmt.format(Date(span.start.coerceAtLeast(from)))
            val end = span.end?.let { fmt.format(Date(it)) } ?: LanguageText.text("device.presence.now")
            "$start–$end"
        }
    }
}
