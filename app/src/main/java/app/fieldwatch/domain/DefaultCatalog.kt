package app.fieldwatch.domain

import java.util.UUID

/**
 * Stock catalog entry points. The fleet rows themselves live in
 * [GeneratedCatalog], generated from `dist/fieldwatch-signatures.json` by
 * `tools/gen_default_catalog.py` — edit the JSON, not the Kotlin.
 */
object DefaultCatalog {
    /**
     * Tesla phone-as-key uses Apple iBeacon layout (0x004C type 0x02 length 0x15)
     * so iOS can find the car in the background. That is the Tesla row, not iBeacon.
     */
    const val TESLA_IBEACON_MFG_PREFIX = "021574278BDAB64445208F0C720EAF059935"

    /**
     * Target Atrius basket tags advertise Apple iBeacon layout (0x004C type 0x02
     * length 0x15) with this UUID, plus service 0xB1BB. Not Acuity company 0x0346
     * on the hundreds of basket radios (two Florida stores, 2026-09-02).
     */
    const val TARGET_ATRIUS_IBEACON_MFG_PREFIX = "02155993A94C7D974DF79ABFE493BFD5D000"

    /**
     * DJI company 0x08AA manufacturer-data model id (u16 LE). Osmo cameras sit
     * in 0x0006–0x0022. Aircraft (Mavic 3 0x0070, Neo 2 0x007e, …) do not.
     * Do not put bare mfg(0x08AA) on the Osmo row — that is every DJI radio.
     */
    val OSMO_CAMERA_MFG_PREFIXES = listOf(
        "0600", // Osmo Action 1
        "1000", // Osmo Action 2
        "1200", // Osmo Action 3
        "1400", // Osmo Action 4
        "1500", // Osmo Action 5 Pro / Xtra Edge Pro
        "1700", // Osmo 360
        "1800", // Osmo Action 6
        "1900", // Osmo Nano
        "2000", // Osmo Pocket 3
        "2100", // Osmo Pocket 4
        "2200", // Osmo Pocket 4 Pro
    )

    /**
     * Stock palette slots by device class. Same index as [Palette.fleet].
     * Operators can still change any row in the editor.
     *
     *   0 phosphor green  LoRa / community mesh
     *   1 amber           surveillance — ALPR / Flock-family / municipal cameras, drones
     *   2 red             pentest kit and cheap UART overlays
     *   3 cyan            SmartTag / Tile / Pebblebee
     *   4 purple          Apple/Google phones and Find My tags
     *   5 orange          smart glasses, headphones / speakers
     *   6 silver          consumer / action cameras, PCs / home IoT / retail signage
     *   7 teal            public safety / vehicle
     *   8 clinical blue   scanners, BP, scales, CGM
     */

    /**
     * Extra attention rows: body-cam / public-safety APs, camera glasses,
     * recording wearables, pentest kit, and roadside / public camera + ALPR.
     * Plus every built-in Drone-class row. Not Tesla, not headphones, not
     * UniFi Protect, not BlueTOAD Spectra, not BlipTrack, not access-control locks.
     */
    fun defaultWatchlist(): List<WatchTarget> =
        fleets()
            .filter { it.builtIn && (it.attentionNote.isNotBlank() || it.kind == SignatureClass.DRONE) }
            .sortedBy { it.name.lowercase() }
            .map { fleet ->
                WatchTarget(id = "watch-${fleet.id}", fleetId = fleet.id, label = fleet.name)
            }

    /** Stock rows, name-sorted. Source of truth: `dist/fieldwatch-signatures.json`. */
    fun fleets(): List<Fleet> = GeneratedCatalog.fleets.sortedBy { it.name.lowercase() }

    fun newBlankFleet(): Fleet = Fleet(
        id = UUID.randomUUID().toString(),
        name = "New Signature",
        enabled = true,
        matchAny = true,
        colorIndex = 0,
        kind = SignatureClass.OTHER,
        rules = emptyList(),
    )
}
