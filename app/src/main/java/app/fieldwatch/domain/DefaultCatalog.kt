package app.fieldwatch.domain

import java.util.UUID

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
     */
    private object Hue {
        const val MESH = 0          // phosphor green — LoRa / community mesh
        const val SURVEILLANCE = 1  // amber — ALPR / Flock-family / municipal cameras
        const val DRONE = 1         // same amber; class (not color) splits poles vs aircraft
        const val HACKING = 2       // red — pentest kit and cheap UART overlays
        const val TRACKER = 3       // cyan — SmartTag / Tile / Pebblebee
        const val FIND_MY = 4       // purple — Apple/Google phones and Find My tags
        const val GLASSES = 5       // orange — smart glasses
        const val AUDIO = 5         // same orange; class splits glasses vs headphones / speakers
        const val CAMERA = 6        // silver — consumer / action cameras (not poles)
        const val HOME_CAM = 6      // silver — PCs / home IoT / retail signage
        const val LAW = 7           // teal — public safety / vehicle (class splits them)
        const val VEHICLE = 7       // same teal; class (not color) splits LE vs civilian vehicle
        const val HEALTH = 8        // clinical blue — scanners, BP, scales, CGM
    }

    /**
     * Bookmark these on first launch / Restore so they beep (and speak, with Voice on).
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

    fun fleets(): List<Fleet> = listOf(
        flockCameras(),
        ravenAcoustic(),
        airTags(),
        smartTags(),
        tileTrackers(),
        ibeacon(),
        targetAtriusBasket(),
        minew(),
        estimote(),
        kontakt(),
        penguin(),
        pigvision(),
        fsExtBattery(),
        appleDevice(),
        appleAudio(),
        microsoftDevice(),
        tesla(),
        teslaTstpms(),
        ford(),
        hondaMotor(),
        hyundaiMotor(),
        toyota(),
        nissanMotor(),
        subaru(),
        bmw(),
        volkswagen(),
        porsche(),
        jaguarLandRover(),
        bydAuto(),
        googleDevice(),
        fastPair(),
        sony(),
        bose(),
        garmin(),
        amazon(),
        fitbit(),
        oura(),
        logitech(),
        jblHarman(),
        sonos(),
        gopro(),
        osmo(),
        insta360(),
        dji(),
        remoteId(),
        skydio(),
        autel(),
        parrot(),
        hoverAir(),
        netgear(),
        tpLink(),
        asus(),
        linksys(),
        eero(),
        googleWifi(),
        huawei(),
        plume(),
        phoneHotspot(),
        dlink(),
        dwnet(),
        belkin(),
        xfinity(),
        spectrum(),
        attWifi(),
        verizon(),
        starlink(),
        meraki(),
        cisco(),
        aruba(),
        ruckus(),
        ruijie(),
        fortinet(),
        mikrotik(),
        engenius(),
        zyxel(),
        peplink(),
        openwrt(),
        arris(),
        mist(),
        tMobile(),
        humax(),
        sagemcom(),
        arcadyan(),
        askey(),
        calix(),
        nokiaNsn(),
        airties(),
        tenda(),
        wavlink(),
        sercomm(),
        luxul(),
        sophos(),
        aumovio(),
        centuryLink(),
        gmHotspot(),
        audiMmi(),
        extremeNetworks(),
        adtran(),
        cambium(),
        trendnet(),
        cudy(),
        snapAv(),
        vantiva(),
        hitron(),
        actiontec(),
        buffalo(),
        grandstream(),
        edgecore(),
        watchGuard(),
        mojo(),
        winegard(),
        inseego(),
        franklin(),
        synology(),
        glInet(),
        chipolo(),
        pebblebee(),
        verkada(),
        vigilant(),
        eufy(),
        wyze(),
        ring(),
        arlo(),
        nest(),
        nestThermostat(),
        nestWeave(),
        ecobee(),
        sensi(),
        honeywellHome(),
        haiku(),
        tuya(),
        seos(),
        augustLock(),
        schlage(),
        nuki(),
        salto(),
        dormakaba(),
        lockly(),
        kevo(),
        masterLock(),
        igloohome(),
        tedee(),
        paxton(),
        kwikset(),
        myq(),
        chevroletHotspot(),
        uconnect(),
        carPlay(),
        carlink(),
        rivian(),
        goodyearTpms(),
        schraderTpms(),
        pacificTpms(),
        hufTpms(),
        foboTpms(),
        ruuvi(),
        blueMaestro(),
        sensorPush(),
        samsara(),
        govee(),
        hpPrinter(),
        epson(),
        lgWebosTv(),
        roku(),
        nespresso(),
        radiacode(),
        shokz(),
        mercedesMbux(),
        motive(),
        peopleNet(),
        cradlepoint(),
        airlink(),
        compex(),
        novatelWireless(),
        utilityInc(),
        tapo(),
        reolink(),
        hikvision(),
        dahua(),
        meshtastic(),
        helium(),
        meshCore(),
        goTenna(),
        senseCap(),
        rakWisGate(),
        genetec(),
        blueToadSpectra(),
        blipTrack(),
        hanwhaWisenet(),
        uniview(),
        rhombus(),
        rekor(),
        axon(),
        watchGuardVideo(),
        digitalAlly(),
        revealMedia(),
        wolfcom(),
        panasonicIpro(),
        avigilon(),
        axis(),
        haydenAi(),
        miovision(),
        tattile(),
        liveViewLvt(),
        unifi(),
        unifiAp(),
        unifiProtect(),
        hobbyBleSerial(),
        metaGlasses(),
        snapSpectacles(),
        brilliantFrame(),
        evenG1(),
        hak5Pineapple(),
        flipperZero(),
        pwnagotchi(),
        marauderDeauther(),
        ghostEsp(),
        bruceFirmware(),
        porkchop(),
        pokemonGoPlus(),
        hatch(),
        bhyve(),
        samsungAppliance(),
        ecoWater(),
        fieldy(),
        plaud(),
        limitlessPendant(),
        beePendant(),
        omiPendant(),
        friendPendant(),
        retailLedSign(),
        electronicShelfLabel(),
        honeywellXenonHc(),
        omronHealthcare(),
        withings(),
        dexcom(),
    ).sortedBy { it.name.lowercase() }

    private fun flockCameras() = Fleet(
        id = "fleet-flock-cameras",
        name = "Flock Safety Cameras",
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Flock-style roadside ALPR / camera pole. A Flock- name is the stronger hit; LiteOn and Espressif boards are also used on unrelated products.",
        attentionNote = "Flock-style roadside ALPR / camera pole — reads plates and can be used to locate a vehicle. IEEE B4:1E:52 or a Flock-* SSID is the stronger hit; LiteOn and Espressif OUIs are component vendors used on many products. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = buildList {
            // IEEE MA-L registered to Flock Safety (2024-05-09)
            add(oui("B4:1E:52"))
            // LiteOn / camera radio prefixes commonly seen on Falcon/Sparrow
            listOf(
                "70:C9:4E", "3C:91:80", "D8:F3:BC", "80:30:49", "B8:35:32",
                "14:5A:FC", "74:4C:A1", "08:3A:88", "9C:2F:9D", "C0:35:32",
                "94:08:53", "E4:AA:EA", "F4:6A:DD", "F8:A2:D6", "24:B2:B9",
                "00:F4:8D", "D0:39:57", "E8:D0:FC", "E0:4F:43", "B8:1E:A4",
                "70:08:94", "3C:71:BF", "58:00:E3", "5C:93:A2", "64:6E:69",
                "48:27:EA", "A4:CF:12", "82:6B:F2",
            ).forEach { add(oui(it)) }
            add(name("Flock"))
            add(name("FLCK"))
            add(glob("Flock-*"))
            add(glob("Flock-??????"))
            add(name("CONDOR"))
            add(name("FALCON"))
            add(name("SPARROW"))
            // 802.11 vendor IE (element 221) OUIs used by Lite-On radio firmware
            add(vendorIe("00:80:19"))
            add(vendorIe("00:0A:EB"))
        },
    )

    private fun ravenAcoustic() = Fleet(
        id = "fleet-raven",
        name = "Raven / ShotSpotter",
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Flock Raven or ShotSpotter-style acoustic gunshot sensor, usually on a pole with cameras.",
        builtIn = true,
        rules = listOf(
            name("RAVEN"),
            name("ShotSpotter"),
            name("SoundThinking"),
            name("Shot Spotter"),
            uuid("3100"),
            uuid("3200"),
            uuid("3300"),
            uuid("3400"),
            uuid("3500"),
            mfg(0x09C8),
            oui("D4:11:D6"),
        ),
    )

    private fun airTags() = Fleet(
        id = "fleet-airtag",
        name = "Apple AirTags",
        colorIndex = Hue.FIND_MY,
        kind = SignatureClass.FINDER,
        matchAny = true,
        notes = "Apple AirTag or Find My accessory. iPhones also send Find My so they can be located — that stays on Apple Device unless the name is AirTag. Addresses rotate.",
        builtIn = true,
        rules = listOf(
            name("AirTag"),
            name("Find My"),
            mfgData(0x004C, "12"),
            uuid("FD44"),
        ),
    )

    private fun smartTags() = Fleet(
        id = "fleet-smarttag",
        name = "Samsung SmartTags",
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.FINDER,
        matchAny = true,
        notes = "Samsung SmartTag / SmartTag+ item finder. Addresses often rotate.",
        builtIn = true,
        rules = listOf(
            name("SmartTag"),
            name("Smart Tag"),
            name("Galaxy SmartTag"),
            uuid("FD5A"),
            mfg(0x0075),
        ),
    )

    private fun ibeacon() = Fleet(
        id = "fleet-ibeacon",
        name = "iBeacon",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.BEACON,
        matchAny = true,
        notes = "Generic proximity beacon. Stores, baskets, TVs, and cars can all send this Apple layout. Mute in a dense mall.",
        builtIn = true,
        rules = listOf(
            mfgData(0x004C, "0215"),
            bleName("iBeacon"),
            bleGlob("*iBeacon*"),
        ),
    )

    private fun targetAtriusBasket() = Fleet(
        id = "fleet-target-atrius",
        name = "Target Atrius basket",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.BEACON,
        matchAny = true,
        notes = "Target shopping-basket tag. Dual-labels with generic iBeacon; this row is the store basket.",
        builtIn = true,
        rules = listOf(
            mfgData(0x004C, TARGET_ATRIUS_IBEACON_MFG_PREFIX),
            uuid("B1BB"),
        ),
    )

    private fun minew() = Fleet(
        id = "fleet-minew",
        name = "Minew",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.BEACON,
        matchAny = true,
        notes = "Minew location or asset beacon. Often also sends iBeacon or Eddystone on the same radio.",
        builtIn = true,
        rules = listOf(
            oui("AC:23:3F"),
            bleName("Minew"),
            bleGlob("Minew*"),
        ),
    )

    private fun estimote() = Fleet(
        id = "fleet-estimote",
        name = "Estimote",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.BEACON,
        matchAny = true,
        notes = "Store or venue location beacon / sticker. Decoded fields can show Nearable vs telemetry.",
        builtIn = true,
        decode = CatalogDecodes.estimote,
        rules = listOf(
            mfg(0x015D),
            bleName("Estimote"),
            bleGlob("Estimote*"),
        ),
    )

    private fun kontakt() = Fleet(
        id = "fleet-kontakt",
        name = "Kontakt.io",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.BEACON,
        matchAny = true,
        notes = "Store or venue location beacon. Decoded fields can show battery, TX, and whether it is moving.",
        builtIn = true,
        decode = CatalogDecodes.kontakt,
        rules = listOf(
            mfg(0x01FD),
            bleName("Kontakt"),
            bleGlob("Kontakt*"),
        ),
    )

    private fun tileTrackers() = Fleet(
        id = "fleet-tile",
        name = "Tile Trackers",
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.FINDER,
        matchAny = true,
        notes = "Tile item finder. Decoded fields can show a rotating private id — not a serial.",
        builtIn = true,
        decode = CatalogDecodes.tile,
        rules = listOf(
            name("Tile"),
            uuid("FEED"),
            uuid("FEDD"),
            mfg(0x00C7),
        ),
    )

    private fun penguin() = Fleet(
        id = "fleet-penguin",
        name = "Penguin",
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Penguin Flock-family / roadside camera provisioning name. Name-only, low uniqueness.",
        attentionNote = "Penguin is a Flock-family / roadside camera provisioning name. Name-only, low uniqueness. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Penguin"),
            name("PENGUIN"),
            glob("Penguin*"),
        ),
    )

    private fun pigvision() = Fleet(
        id = "fleet-pigvision",
        name = "Pigvision",
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Pigvision Flock-family / roadside camera name.",
        attentionNote = "Pigvision is a Flock-family / roadside camera name. Name-only. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Pigvision"),
            name("PigVision"),
            name("PIGVISION"),
            glob("Pigvision*"),
        ),
    )

    private fun fsExtBattery() = Fleet(
        id = "fleet-fs-ext-battery",
        name = "FS Ext Battery",
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "External battery pack usually associated with a Flock-style camera pole. Silicon Labs boards also appear on unrelated IoT.",
        attentionNote = "Usually associated with a Flock-style camera — an external battery pack on the pole. Name hits are stronger; Silicon Labs OUIs also appear on unrelated IoT. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = buildList {
            add(name("FS Ext Battery"))
            add(glob("FS_*"))
            add(glob("FS Ext*"))
            listOf(
                "04:0D:84", "1C:34:F1", "38:5B:44", "94:34:69",
                "B4:E3:F9", "F0:82:C0", "58:8E:81", "EC:1B:BD",
                "90:35:EA",
            ).forEach { add(oui(it)) }
        },
    )

    private fun appleDevice() = Fleet(
        id = "fleet-apple-device",
        name = "Apple Device",
        enabled = true,
        colorIndex = Hue.FIND_MY,
        kind = SignatureClass.PHONE,
        matchAny = true,
        notes = "iPhone, iPad, or Mac advertising Continuity (Nearby, Handoff, AirDrop, Instant Hotspot). Find My on this radio is the phone locating itself, not a second AirTag. Not AirPods.",
        builtIn = true,
        rules = listOf(
            mfgData(0x004C, "10"),
            mfgData(0x004C, "0F"),
            mfgData(0x004C, "0B"),
            mfgData(0x004C, "05"),
            mfgData(0x004C, "0C"),
            mfgData(0x004C, "0D"),
            mfgData(0x004C, "0E"),
            mfgData(0x004C, "08"),
            mfgData(0x004C, "0A"),
            name("iPhone"),
            name("iPad"),
            name("MacBook"),
        ),
    )

    private fun appleAudio() = Fleet(
        id = "fleet-apple-audio",
        name = "Apple audio",
        enabled = true,
        colorIndex = Hue.AUDIO,
        kind = SignatureClass.AUDIO,
        matchAny = true,
        notes = "AirPods, Beats, or an Apple TV/speaker advertising AirPlay. Not an AirTag and not the iPhone itself.",
        builtIn = true,
        rules = listOf(
            mfgData(0x004C, "07"),
            mfgData(0x004C, "09"),
            bleName("AirPods"),
            bleName("Beats"),
        ),
    )

    private fun microsoftDevice() = Fleet(
        id = "fleet-microsoft",
        name = "Microsoft Device",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.PHONE,
        matchAny = true,
        notes = "Windows PC, Surface, or Xbox advertising Swift Pair / Nearby Sharing.",
        builtIn = true,
        rules = listOf(
            mfg(0x0006),
            bleName("Surface"),
            bleName("Xbox"),
        ),
    )

    private fun tesla() = Fleet(
        id = "fleet-tesla",
        name = "Tesla",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Tesla phone-as-key, vehicle, Wall Connector, or TeslaGW Wi-Fi. iOS also sees an iBeacon layout from the car — that is still Tesla, not a mall beacon. Tire sensors are the tsTPMS row.",
        builtIn = true,
        rules = listOf(
            mfg(0x022B),
            uuid("FE96"),
            uuid("FE97"),
            mfgData(0x004C, TESLA_IBEACON_MFG_PREFIX),
            bleName("Tesla"),
            bleName("Cybertruck"),
            bleGlob("S????????????????C"),
            bleGlob("S????????????????D"),
            bleGlob("S????????????????P"),
            bleGlob("S????????????????R"),
            wifiGlob("TeslaGW*"),
            wifiGlob("tesla-vehicle"),
            wifiGlob("TeslaWallConnector*"),
            wifiGlob("Cybertruck*"),
        ),
    )

    private fun ford() = oemVehicle(
        id = "fleet-ford",
        name = "Ford",
        notes = "Ford or Lincoln phone-as-key / infotainment. Not a dealer Wi-Fi name.",
        rules = listOf(
            mfg(0x0723),
            bleName("Ford"),
            bleName("Lincoln"),
        ),
    )

    private fun hondaMotor() = oemVehicle(
        id = "fleet-honda",
        name = "Honda",
        notes = "Honda or Acura phone-as-key / infotainment.",
        rules = listOf(
            mfg(0x0915),
            bleName("Honda"),
            bleName("Acura"),
        ),
    )

    private fun hyundaiMotor() = oemVehicle(
        id = "fleet-hyundai",
        name = "Hyundai",
        notes = "Hyundai or Genesis phone-as-key / infotainment. Not a dealer Wi-Fi name.",
        rules = listOf(
            mfg(0x0826),
            bleName("Hyundai"),
            bleName("Genesis"),
        ),
    )

    private fun toyota() = oemVehicle(
        id = "fleet-toyota",
        name = "Toyota",
        notes = "Toyota or Lexus phone-as-key, or a factory TOYOTA / LEXUS hotspot.",
        rules = listOf(
            mfg(0x0977),
            bleName("Toyota"),
            bleName("Lexus"),
            wifiGlob("TOYOTA*"),
            wifiGlob("LEXUS*"),
        ),
    )

    private fun nissanMotor() = oemVehicle(
        id = "fleet-nissan",
        name = "Nissan",
        notes = "Nissan or Infiniti phone-as-key / infotainment.",
        rules = listOf(
            mfg(0x0BA6),
            bleName("Nissan"),
            bleName("Infiniti"),
        ),
    )

    private fun subaru() = oemVehicle(
        id = "fleet-subaru",
        name = "Subaru",
        notes = "Subaru phone-as-key / infotainment. Not Starlink satellite internet.",
        rules = listOf(
            mfg(0x0A10),
            bleName("Subaru"),
        ),
    )

    private fun bmw() = oemVehicle(
        id = "fleet-bmw",
        name = "BMW",
        notes = "BMW phone-as-key or in-car hotspot. Factory BMW_ Wi-Fi is the car, not a dealer showroom.",
        rules = listOf(
            mfg(0x05EB),
            bleName("BMW"),
            wifiGlob("BMW_*"),
        ),
    )

    private fun volkswagen() = oemVehicle(
        id = "fleet-volkswagen",
        name = "Volkswagen",
        notes = "Volkswagen phone-as-key or My VW hotspot. Skoda, SEAT, and Porsche have their own rows.",
        rules = listOf(
            mfg(0x011F),
            uuid("FE30"),
            uuid("FE31"),
            bleName("Volkswagen"),
            bleName("VW"),
            wifiGlob("My VW*"),
        ),
    )

    private fun porsche() = oemVehicle(
        id = "fleet-porsche",
        name = "Porsche",
        notes = "Porsche phone-as-key or factory Porsche_WLAN hotspot. Separate from Volkswagen.",
        rules = listOf(
            mfg(0x0120),
            bleName("Porsche"),
            wifiGlob("Porsche_WLAN*"),
        ),
    )

    private fun jaguarLandRover() = oemVehicle(
        id = "fleet-jlr",
        name = "Jaguar Land Rover",
        notes = "Jaguar, Land Rover, or Range Rover phone-as-key / infotainment.",
        rules = listOf(
            mfg(0x020B),
            bleName("Jaguar"),
            bleName("Land Rover"),
            bleName("Range Rover"),
        ),
    )

    private fun bydAuto() = oemVehicle(
        id = "fleet-byd",
        name = "BYD",
        notes = "BYD phone-as-key or vehicle BLE. Pattern match, not a specific model.",
        rules = listOf(
            mfg(0x0C34),
            bleName("BYD"),
        ),
    )

    private fun oemVehicle(
        id: String,
        name: String,
        notes: String,
        rules: List<MatchRule>,
    ) = Fleet(
        id = id,
        name = name,
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = notes,
        builtIn = true,
        rules = rules,
    )

    private fun googleDevice() = Fleet(
        id = "fleet-google",
        name = "Google",
        enabled = true,
        colorIndex = Hue.FIND_MY,
        kind = SignatureClass.PHONE,
        matchAny = true,
        notes = "Pixel phone or Chromecast. Fast Pair accessories are the Fast Pair row.",
        builtIn = true,
        rules = listOf(
            mfg(0x00E0),
            bleName("Pixel"),
            bleName("Chromecast"),
            bleName("Google Pixel"),
        ),
    )

    private fun fastPair() = Fleet(
        id = "fleet-fast-pair",
        name = "Fast Pair",
        enabled = true,
        colorIndex = Hue.FIND_MY,
        kind = SignatureClass.PHONE,
        matchAny = true,
        notes = "Android accessory (buds, watch, phone) advertising Fast Pair. Pairing-mode is tap-to-pair; longer ads are already-paired plaza noise. Not a person. Filters can hide the plaza chips.",
        builtIn = true,
        rules = listOf(
            uuid("FE2C"),
        ),
    )

    private fun sony() = Fleet(
        id = "fleet-sony",
        name = "Sony",
        enabled = true,
        colorIndex = Hue.AUDIO,
        kind = SignatureClass.AUDIO,
        matchAny = true,
        notes = "Sony headphones, TV, or camera. Bravia TVs also send a generic iBeacon for Cast — Fieldwatch keeps Sony.",
        builtIn = true,
        rules = listOf(
            mfg(0x012D),
            bleName("Sony"),
            bleName("WH-1000"),
            bleName("WF-1000"),
        ),
    )

    private fun bose() = Fleet(
        id = "fleet-bose",
        name = "Bose",
        enabled = true,
        colorIndex = Hue.AUDIO,
        kind = SignatureClass.AUDIO,
        matchAny = true,
        notes = "Bose headphones or speaker. Quiet Charge / QC buds advertise even when in the case.",
        builtIn = true,
        rules = listOf(
            mfg(0x009E),
            uuid("FE21"),
            uuid("FEBE"),
            bleName("Bose"),
        ),
    )

    private fun garmin() = Fleet(
        id = "fleet-garmin",
        name = "Garmin",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.WEARABLE,
        matchAny = true,
        notes = "Garmin watch, bike computer, or inReach messenger.",
        builtIn = true,
        rules = listOf(
            mfg(0x0087),
            uuid("FE1F"),
            bleName("Garmin"),
        ),
    )

    private fun amazon() = Fleet(
        id = "fleet-amazon",
        name = "Amazon",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Amazon Echo, Fire, or Kindle when it advertises. Not every AmazonBasics gadget.",
        builtIn = true,
        rules = listOf(
            mfg(0x0171),
            bleName("Echo"),
            bleName("Amazon"),
            bleName("Fire TV"),
        ),
    )

    private fun fitbit() = Fleet(
        id = "fleet-fitbit",
        name = "Fitbit",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.WEARABLE,
        matchAny = true,
        notes = "Fitbit watch or tracker. Advertisements are identity-only; step counts are not in the broadcast.",
        builtIn = true,
        rules = listOf(
            mfg(0x018E),
            uuid("FD62"),
            uuid("FD63"),
            bleName("Fitbit"),
        ),
    )

    private fun oura() = Fleet(
        id = "fleet-oura",
        name = "Oura",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.WEARABLE,
        matchAny = true,
        notes = "Oura wellness ring. Always-on BLE while worn. Pattern match, not that person.",
        builtIn = true,
        rules = listOf(
            mfg(0x02B2),
            bleName("Oura"),
        ),
    )

    private fun logitech() = Fleet(
        id = "fleet-logitech",
        name = "Logitech",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Logitech mouse, keyboard, or webcam. Office noise.",
        builtIn = true,
        rules = listOf(
            mfg(0x01DA),
            uuid("FE61"),
            bleName("Logitech"),
            bleName("Logi"),
        ),
    )

    private fun jblHarman() = Fleet(
        id = "fleet-jbl",
        name = "JBL / Harman",
        enabled = true,
        colorIndex = Hue.AUDIO,
        kind = SignatureClass.AUDIO,
        matchAny = true,
        notes = "JBL, Harman Kardon, or some car-audio BLE. Headphones, speakers, or a head unit.",
        builtIn = true,
        rules = listOf(
            mfg(0x0057),
            bleName("JBL"),
            bleName("Harman"),
        ),
    )

    private fun sonos() = Fleet(
        id = "fleet-sonos",
        name = "Sonos",
        enabled = true,
        colorIndex = Hue.AUDIO,
        kind = SignatureClass.AUDIO,
        matchAny = true,
        notes = "Sonos home/office speaker. Setup or idle ads. Not a tracker.",
        builtIn = true,
        rules = listOf(
            mfg(0x05A7),
            uuid("FE07"),
            bleName("Sonos"),
        ),
    )

    private fun gopro() = Fleet(
        id = "fleet-gopro",
        name = "GoPro",
        enabled = true,
        colorIndex = Hue.CAMERA,
        kind = SignatureClass.CAMERA,
        matchAny = true,
        notes = "GoPro action camera. Decoded fields can show whether it is awake, in Wi-Fi AP mode, or pairing.",
        builtIn = true,
        decode = CatalogDecodes.gopro,
        rules = listOf(
            uuid("FEA5"),
            uuid("FEA6"),
            bleName("GoPro"),
            bleGlob("GoPro*"),
        ),
    )

    private fun osmo() = Fleet(
        id = "fleet-osmo",
        name = "Osmo",
        enabled = true,
        colorIndex = Hue.CAMERA,
        kind = SignatureClass.CAMERA,
        matchAny = true,
        notes = "DJI Osmo handheld / action camera (Action, Pocket, 360, Nano). Not a flying DJI aircraft.",
        builtIn = true,
        decode = CatalogDecodes.djiModel,
        rules = OSMO_CAMERA_MFG_PREFIXES.map { mfgData(0x08AA, it) } + listOf(
            glob("OsmoAction*"),
            glob("Osmo Action*"),
            glob("OsmoPocket*"),
            glob("Osmo Pocket*"),
            glob("Osmo360*"),
            glob("Osmo 360*"),
            glob("OsmoNano*"),
            glob("Osmo Nano*"),
            glob("XtraEdgePro*"),
            glob("Xtra Edge Pro*"),
        ),
    )

    private fun insta360() = Fleet(
        id = "fleet-insta360",
        name = "Insta360",
        enabled = true,
        colorIndex = Hue.CAMERA,
        kind = SignatureClass.CAMERA,
        matchAny = true,
        notes = "Insta360 action / 360 camera. The advertised name is often the model plus serial. Consumer camera, not a pole.",
        builtIn = true,
        rules = listOf(
            mfg(0x10D7),
            name("Insta360"),
            glob("Insta360*"),
            glob("X3 *"),
            glob("X4 *"),
            glob("X5 *"),
            glob("Ace Pro*"),
            glob("GO 3*"),
            glob("GO3*"),
            glob("GO Ultra*"),
            glob("ONE X*"),
            glob("ONE RS*"),
            glob("ONE R *"),
        ),
    )

    private fun dji() = Fleet(
        id = "fleet-dji",
        name = "DJI",
        enabled = true,
        colorIndex = Hue.DRONE,
        kind = SignatureClass.DRONE,
        matchAny = true,
        notes = "DJI aircraft, controller, or setup Wi-Fi. Handheld Osmo cameras are the Osmo row. In-flight digital license plate is the Remote ID row.",
        builtIn = true,
        decode = CatalogDecodes.djiModel,
        rules = listOf(
            mfg(0x08AA),
            bleName("DJI"),
            bleGlob("DJI*"),
            wifiName("DJI"),
            wifiGlob("DJI*"),
        ),
    )

    private fun netgear() = Fleet(
        id = "fleet-netgear",
        name = "NETGEAR",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "NETGEAR or Orbi home router / mesh. Renamed SSIDs still hit on the board vendor.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("NETGEAR*"),
                wifiGlob("Netgear*"),
                wifiGlob("Orbi*"),
                wifiName("NETGEAR"),
            ),
            ApVendorOuis.NETGEAR,
        ),
    )

    private fun tpLink() = Fleet(
        id = "fleet-tplink",
        name = "TP-Link",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "TP-Link or Deco home router / mesh. A Tapo camera on a TP-Link board can also hit this row.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("TP-Link*"),
                wifiGlob("TP-LINK*"),
                wifiGlob("TPLink*"),
                wifiGlob("Deco*"),
                wifiName("TP-Link"),
            ),
            ApVendorOuis.TPLINK,
        ),
    )

    private fun asus() = Fleet(
        id = "fleet-asus",
        name = "ASUS",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "ASUS home router or mesh. An ASUS laptop hotspot can also hit.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("ASUS*"),
                wifiGlob("ASUS_*"),
                wifiName("ASUS"),
            ),
            ApVendorOuis.ASUS,
        ),
    )

    private fun linksys() = Fleet(
        id = "fleet-linksys",
        name = "Linksys",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Linksys or Velop home mesh. Some Velop units use Belkin boards.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Linksys*"),
                wifiGlob("linksys*"),
                wifiGlob("Velop*"),
                wifiName("Linksys"),
            ),
            ApVendorOuis.LINKSYS,
        ),
    )

    private fun eero() = Fleet(
        id = "fleet-eero",
        name = "Eero",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Amazon Eero mesh. Not an Echo speaker.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiName("eero"),
                wifiGlob("eero*"),
                wifiGlob("Eero*"),
            ),
            ApVendorOuis.EERO,
        ),
    )

    private fun googleWifi() = Fleet(
        id = "fleet-google-wifi",
        name = "Google Wifi",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Google Wifi / Nest Wifi mesh. Not a Pixel phone and not a Nest camera.",
        builtIn = true,
        rules = listOf(
            wifiName("Google Wifi"),
            wifiName("GoogleWifi"),
            wifiGlob("Google Wifi*"),
            wifiName("Nest Wifi"),
            wifiGlob("Nest Wifi*"),
        ),
    )

    private fun huawei() = Fleet(
        id = "fleet-huawei",
        name = "Huawei",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Huawei home router, or a phone hotspot on a public Huawei address. Randomized hotspots need the factory SSID. Not Honor.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("HUAWEI*"),
                wifiGlob("Huawei*"),
                wifiName("HUAWEI"),
                wifiName("Huawei"),
            ),
            ApVendorOuis.HUAWEI,
        ),
    )

    private fun plume() = Fleet(
        id = "fleet-plume",
        name = "Plume",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Plume SuperPod / HomePass mesh. ISP-branded pods (xFi and similar) often use the carrier OEM board instead.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Plume*"),
                wifiName("Plume"),
                wifiGlob("SuperPod*"),
                wifiGlob("Superpod*"),
            ),
            ApVendorOuis.PLUME,
        ),
    )

    private fun phoneHotspot() = Fleet(
        id = "fleet-phone-hotspot",
        name = "Phone hotspot",
        enabled = true,
        colorIndex = Hue.FIND_MY,
        kind = SignatureClass.PHONE,
        matchAny = true,
        notes = "Phone personal hotspot on a factory name (AndroidAP, Galaxy, Pixel). Custom hotspot names miss. iPhone hotspots stay on Apple Device.",
        builtIn = true,
        rules = listOf(
            wifiGlob("AndroidAP*"),
            wifiGlob("Galaxy-*"),
            wifiGlob("Galaxy *"),
            wifiGlob("Galaxy_*"),
            wifiGlob("Pixel-*"),
            wifiGlob("Pixel *"),
            wifiGlob("Pixel_*"),
        ),
    )

    private fun dlink() = Fleet(
        id = "fleet-dlink",
        name = "D-Link",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "D-Link home router. Renamed SSIDs still hit on the board vendor.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("D-Link*"),
                wifiGlob("DLink*"),
                wifiGlob("dlink*"),
                wifiGlob("DIR-*"),
            ),
            ApVendorOuis.DLINK,
        ),
    )

    private fun dwnet() = Fleet(
        id = "fleet-dwnet",
        name = "DWnet",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "DWnet consumer / SMB access point. Cloud SSIDs are house names, so the board vendor is the hit.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.DWNET),
    )

    private fun belkin() = Fleet(
        id = "fleet-belkin",
        name = "Belkin",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Belkin home router. Some Linksys Velop units land here too.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Belkin*"),
                wifiGlob("belkin*"),
                wifiName("Belkin"),
            ),
            ApVendorOuis.BELKIN,
        ),
    )

    private fun xfinity() = Fleet(
        id = "fleet-xfinity",
        name = "Xfinity",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Comcast xfinitywifi hotspot or Xfinity / XFSETUP gateway name. Most boxes are Arris / Hitron / Technicolor OEM — those hit the OEM row.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiName("xfinitywifi"),
                wifiGlob("xfinitywifi*"),
                wifiGlob("XFSETUP*"),
                wifiGlob("Xfinity*"),
                wifiGlob("XFINITY*"),
            ),
            ApVendorOuis.COMCAST,
        ),
    )

    private fun spectrum() = Fleet(
        id = "fleet-spectrum",
        name = "Spectrum",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Charter Spectrum setup or hotspot Wi-Fi. Boxes are usually Arris / Hitron / Technicolor OEM.",
        builtIn = true,
        rules = listOf(
            wifiGlob("SpectrumSetup*"),
            wifiGlob("MySpectrumWiFi*"),
            wifiName("SpectrumWiFi"),
            wifiGlob("SpectrumWiFi*"),
            wifiName("Spectrum Mobile"),
            wifiGlob("Spectrum Mobile*"),
            wifiName("Spectrum Free Trial"),
            wifiGlob("Spectrum Free Trial*"),
        ),
    )

    private fun attWifi() = Fleet(
        id = "fleet-att",
        name = "AT&T",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "AT&T hotspot or gateway (attwifi, ATT-GUEST, Pace-style factory names). Many boxes are Pace / Arris OEM.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiName("attwifi"),
                wifiGlob("ATTWIFI*"),
                wifiGlob("ATTWifi*"),
                wifiGlob("ATT-WIFI*"),
                wifiGlob("ATT-Wifi*"),
                wifiGlob("ATT???????"),
                wifiGlob("ATT???????-*"),
                wifiGlob("ATT???????_*"),
                wifiGlob("ATT??????? *"),
                wifiGlob("ATT-GUEST*"),
                wifiGlob("ATT-Guest*"),
                wifiGlob("2WIRE*"),
                wifiGlob("2Wire*"),
            ),
            ApVendorOuis.ATT + ApVendorOuis.TWOWIRE,
        ),
    )

    private fun verizon() = Fleet(
        id = "fleet-verizon",
        name = "Verizon",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Verizon or Fios hotspot / gateway name. Many FiOS boxes are Actiontec OEM.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Verizon-*"),
                wifiGlob("VerizonFiOS*"),
                wifiGlob("Fios-*"),
                wifiGlob("MyVerizon*"),
                wifiName("Verizon"),
            ),
            ApVendorOuis.VERIZON,
        ),
    )

    private fun starlink() = Fleet(
        id = "fleet-starlink",
        name = "Starlink",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Starlink router. The BSSID is often randomized now — the STARLINK name is the usual hit.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("STARLINK*"),
                wifiGlob("Starlink*"),
                wifiName("STARLINK"),
                wifiName("Starlink"),
            ),
            ApVendorOuis.SPACEX,
        ),
    )

    private fun meraki() = Fleet(
        id = "fleet-meraki",
        name = "Meraki",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Cisco Meraki campus / cloud access point. Renamed site SSIDs still hit on the board vendor. Not a camera pole.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Meraki*"),
                wifiName("Meraki"),
            ),
            ApVendorOuis.MERAKI,
        ),
    )

    private fun glInet() = Fleet(
        id = "fleet-glinet",
        name = "GL.iNet",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "GL.iNet travel router. Chip-module boards without a GL name miss.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("GL-iNet*"),
                wifiGlob("GL-Inet*"),
                wifiGlob("GL-MT*"),
                wifiGlob("GL-AR*"),
                wifiGlob("GL-AXT*"),
                wifiName("GL.iNet"),
            ),
            ApVendorOuis.GLINET,
        ),
    )

    private fun chipolo() = Fleet(
        id = "fleet-chipolo",
        name = "Chipolo",
        enabled = true,
        colorIndex = Hue.FIND_MY,
        kind = SignatureClass.FINDER,
        matchAny = true,
        notes = "Chipolo item finder (Find Hub / Find My). A tag you clip to keys or a bag.",
        builtIn = true,
        rules = listOf(
            name("Chipolo"),
            glob("Chipolo*"),
        ),
    )

    private fun pebblebee() = Fleet(
        id = "fleet-pebblebee",
        name = "Pebblebee / moto tag",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.FINDER,
        matchAny = true,
        notes = "Pebblebee or Motorola moto tag item finder. A tag you clip to keys or a bag.",
        builtIn = true,
        rules = listOf(
            name("Pebblebee"),
            glob("Pebblebee*"),
            name("moto tag"),
            name("Moto Tag"),
        ),
    )

    private fun verkada() = Fleet(
        id = "fleet-verkada",
        name = "Verkada",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Verkada cloud camera, including LPR-capable bullets, on buildings and some public sites.",
        attentionNote = "Verkada cloud cameras, including LPR-capable bullets. Used on buildings and some public sites — video and sometimes plates. Name-only. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Verkada"),
            glob("Verkada*"),
        ),
    )

    private fun vigilant() = Fleet(
        id = "fleet-vigilant",
        name = "Motorola Vigilant",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Motorola Vigilant plate reader used by agencies and parking.",
        attentionNote = "Motorola Vigilant is LPR — plate readers used by agencies and parking. Name-only when advertised. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Vigilant Solutions"),
            name("Motorola Vigilant"),
            name("Vigilant"),
        ),
    )

    private fun eufy() = Fleet(
        id = "fleet-eufy",
        name = "eufy Security",
        enabled = true,
        colorIndex = Hue.CAMERA,
        kind = SignatureClass.CAMERA,
        matchAny = true,
        notes = "eufy home camera or tag. Common in houses. Consumer camera, not a roadside pole.",
        builtIn = true,
        rules = listOf(
            name("eufy"),
            name("EufyCam"),
            glob("eufy*"),
            glob("Eufy*"),
        ),
    )

    private fun wyze() = Fleet(
        id = "fleet-wyze",
        name = "Wyze",
        enabled = true,
        colorIndex = Hue.CAMERA,
        kind = SignatureClass.CAMERA,
        matchAny = true,
        notes = "Wyze home camera. Very common consumer gear, not a roadside pole.",
        builtIn = true,
        rules = listOf(
            name("WyzeCam"),
            name("Wyze"),
            glob("Wyze*"),
        ),
    )

    private fun ring() = Fleet(
        id = "fleet-ring",
        name = "Ring",
        enabled = true,
        colorIndex = Hue.CAMERA,
        kind = SignatureClass.CAMERA,
        matchAny = true,
        notes = "Amazon Ring doorbell or camera. Common on houses. Consumer camera, not a roadside pole.",
        builtIn = true,
        rules = listOf(
            glob("Ring-*"),
            name("Ring Doorbell"),
            name("Ring Camera"),
            name("Ring Setup"),
        ),
    )

    private fun arlo() = Fleet(
        id = "fleet-arlo",
        name = "Arlo",
        enabled = true,
        colorIndex = Hue.CAMERA,
        kind = SignatureClass.CAMERA,
        matchAny = true,
        notes = "Arlo home camera or its base-station Wi-Fi. Consumer camera, not a roadside pole. Renamed SSIDs still hit on the board vendor.",
        builtIn = true,
        rules = listOf(
            name("Arlo"),
            glob("Arlo*"),
            glob("ARLO_VMB_*"),
        ) + ApVendorOuis.ARLO.map { oui(it) },
    )

    private fun nest() = Fleet(
        id = "fleet-nest",
        name = "Nest",
        enabled = true,
        colorIndex = Hue.CAMERA,
        kind = SignatureClass.CAMERA,
        matchAny = true,
        notes = "Google Nest home camera. Consumer camera, not the Nest thermostat row.",
        builtIn = true,
        rules = listOf(
            name("Nestcam"),
            name("Nest Cam"),
            name("Nest-Hello"),
            glob("Nest-*"),
        ),
    )

    private fun nestThermostat() = Fleet(
        id = "fleet-nest-thermostat",
        name = "Nest Thermostat",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.THERMOSTAT,
        matchAny = true,
        notes = "Nest thermostat. Temperature sensors on some generations can hit this too. Not a Nest camera.",
        builtIn = true,
        rules = listOf(
            mfg(0x01B5),
            bleName("Nest Thermostat"),
            bleGlob("Nest Thermostat*"),
        ),
    )

    private fun nestWeave() = Fleet(
        id = "fleet-nest-weave",
        name = "Nest Weave",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Nest Protect, thermostat, or other Weave-over-BLE home device. Randomized address. Decoded fields can show product and pairing.",
        builtIn = true,
        decode = CatalogDecodes.nestWeave,
        rules = listOf(
            uuid("FEAF"),
            uuid("FEB0"),
        ),
    )

    private fun ecobee() = Fleet(
        id = "fleet-ecobee",
        name = "ecobee",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.THERMOSTAT,
        matchAny = true,
        notes = "ecobee thermostat. Room sensors can hit the same row. Premium uses BLE for setup / Spotify.",
        builtIn = true,
        rules = listOf(
            mfg(0x07D6),
            bleName("ecobee"),
            bleGlob("ecobee*"),
            bleGlob("ecoBee*"),
        ),
    )

    private fun sensi() = Fleet(
        id = "fleet-sensi",
        name = "Sensi",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.THERMOSTAT,
        matchAny = true,
        notes = "Sensi thermostat in BLE setup.",
        builtIn = true,
        rules = listOf(
            bleName("Sensi"),
            bleGlob("Sensi*"),
        ),
    )

    private fun honeywellHome() = Fleet(
        id = "fleet-honeywell-home",
        name = "Honeywell Home",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.THERMOSTAT,
        matchAny = true,
        notes = "Honeywell Home / Lyric / Resideo thermostat (including Amazon Smart Thermostat). Not Honeywell industrial or smoke gear. T9/T10 room sensors are 900 MHz, not BLE.",
        builtIn = true,
        rules = listOf(
            bleName("Honeywell Home"),
            bleGlob("Honeywell Home*"),
            bleName("Lyric Thermostat"),
            bleGlob("Lyric T*"),
            bleName("Amazon Smart Thermostat"),
            bleGlob("Amazon Smart Thermostat*"),
        ),
    )

    private fun haiku() = Fleet(
        id = "fleet-haiku",
        name = "Haiku Fan",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Big Ass Fans Haiku or Mammoth ceiling fan.",
        builtIn = true,
        rules = listOf(
            uuid("E0FC1000-1FB1-4168-96DF-B3F057A86E01"),
            bleName("Haiku Fan"),
            bleGlob("Haiku Fan*"),
            bleName("Mammoth Fan"),
            bleGlob("Mammoth Fan*"),
        ),
    )

    private fun tuya() = Fleet(
        id = "fleet-tuya",
        name = "Tuya",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Tuya plug, light, camera, or sensor. Dense in some apartments. Decoded fields can show whether it is bound.",
        builtIn = true,
        decode = CatalogDecodes.tuya,
        rules = listOf(
            mfg(0x07D0),
            uuid("FD50"),
            bleName("TUYA"),
            bleGlob("TUYA*"),
            bleGlob("Tuya*"),
        ),
    )

    private fun seos() = Fleet(
        id = "fleet-seos",
        name = "ASSA ABLOY",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "ASSA ABLOY / HID Seos or Yale access credential. Phones on HID Mobile Access can advertise a Seos name.",
        builtIn = true,
        rules = listOf(
            mfg(0x012E),
            mfg(0x0124),
            mfg(0x0BDE),
            uuid("FCBF"),
            uuid("00009800-0000-1000-8000-00177A000002"),
            bleName("Seos"),
            bleName("Yale"),
            bleGlob("Yale*"),
        ),
    )

    private fun augustLock() = Fleet(
        id = "fleet-august",
        name = "August",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "August smart lock (now ASSA-owned). Door lock, not a camera.",
        builtIn = true,
        rules = listOf(
            mfg(0x01D1),
            uuid("FE24"),
            bleName("August"),
            bleGlob("August*"),
        ),
    )

    private fun schlage() = Fleet(
        id = "fleet-schlage",
        name = "Schlage",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "Schlage / Allegion smart lock (Encode and similar).",
        builtIn = true,
        rules = listOf(
            mfg(0x013B),
            uuid("FCF4"),
            bleName("Schlage"),
            bleGlob("Schlage*"),
            bleGlob("SCHLAGE*"),
        ),
    )

    private fun nuki() = Fleet(
        id = "fleet-nuki",
        name = "Nuki",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "Nuki smart lock or opener (retrofit on a European cylinder).",
        builtIn = true,
        rules = listOf(
            uuid("A92EE000-5501-11E4-916C-0800200C9A66"),
            uuid("A92EE100-5501-11E4-916C-0800200C9A66"),
            uuid("A92EE200-5501-11E4-916C-0800200C9A66"),
            uuid("A92EE300-5501-11E4-916C-0800200C9A66"),
            uuid("A92AE200-5501-11E4-916C-0800200C9A66"),
            bleName("Nuki"),
            bleGlob("Nuki*"),
        ),
    )

    private fun salto() = Fleet(
        id = "fleet-salto",
        name = "SALTO",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "SALTO commercial access lock or reader.",
        builtIn = true,
        rules = listOf(
            mfg(0x0199),
            bleName("SALTO"),
            bleGlob("SALTO*"),
            bleGlob("Salto*"),
        ),
    )

    private fun dormakaba() = Fleet(
        id = "fleet-dormakaba",
        name = "dormakaba",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "dormakaba, Saflok, or Oracode hotel / commercial lock.",
        builtIn = true,
        rules = listOf(
            mfg(0x0C64),
            bleName("dormakaba"),
            bleGlob("dormakaba*"),
            bleName("Saflok"),
            bleGlob("Saflok*"),
            bleName("Oracode"),
            bleGlob("Oracode*"),
        ),
    )

    private fun lockly() = Fleet(
        id = "fleet-lockly",
        name = "Lockly",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "Lockly smart lock, usually while in BLE setup.",
        builtIn = true,
        rules = listOf(
            bleName("LOCKLY"),
            bleGlob("LOCKLY*"),
            bleGlob("Lockly*"),
        ),
    )

    private fun kevo() = Fleet(
        id = "fleet-kevo",
        name = "Kevo",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "Kwikset Kevo smart lock.",
        builtIn = true,
        rules = listOf(
            mfg(0x015E),
            bleName("Unikey"),
            bleGlob("Unikey*"),
            bleName("Kevo"),
            bleGlob("Kevo*"),
        ),
    )

    private fun masterLock() = Fleet(
        id = "fleet-master-lock",
        name = "Master Lock",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "Master Lock Bluetooth padlock.",
        builtIn = true,
        rules = listOf(
            mfg(0x014B),
            bleName("Master Lock"),
            bleGlob("Master Lock*"),
        ),
    )

    private fun igloohome() = Fleet(
        id = "fleet-igloohome",
        name = "igloohome",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "igloohome keybox or smart lock.",
        builtIn = true,
        rules = listOf(
            mfg(0x05BA),
            bleName("igloohome"),
            bleGlob("igloohome*"),
            bleGlob("Igloohome*"),
        ),
    )

    private fun tedee() = Fleet(
        id = "fleet-tedee",
        name = "Tedee",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "Tedee retrofit smart lock.",
        builtIn = true,
        rules = listOf(
            mfg(0x0725),
            bleName("Tedee"),
            bleGlob("Tedee*"),
        ),
    )

    private fun paxton() = Fleet(
        id = "fleet-paxton",
        name = "Paxton",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "Paxton / Net2 door reader or access panel.",
        builtIn = true,
        rules = listOf(
            mfg(0x0196),
            bleName("Paxton"),
            bleGlob("Paxton*"),
            bleName("Net2"),
            bleGlob("Net2*"),
        ),
    )

    private fun kwikset() = Fleet(
        id = "fleet-kwikset",
        name = "Kwikset",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.LOCK,
        matchAny = true,
        notes = "Kwikset smart lock. Kevo has its own row.",
        builtIn = true,
        rules = listOf(
            bleName("Kwikset"),
            bleGlob("Kwikset*"),
        ),
    )

    private fun myq() = Fleet(
        id = "fleet-myq",
        name = "myQ",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Chamberlain myQ garage-door hub.",
        builtIn = true,
        rules = listOf(
            mfg(0x0878),
            uuid("26D91A37-C279-4D0F-96A1-532CE41CE0F6"),
            bleName("MyQ"),
            bleGlob("MyQ-*"),
        ),
    )

    private fun chevroletHotspot() = Fleet(
        id = "fleet-chevrolet",
        name = "Chevrolet hotspot",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Chevrolet in-car hotspot (myChevrolet). Cadillac / GMC / Buick are the GM hotspot row. Not a dealer.",
        builtIn = true,
        rules = listOf(
            wifiName("myChevrolet"),
            wifiGlob("myChevrolet*"),
        ),
    )

    private fun uconnect() = Fleet(
        id = "fleet-uconnect",
        name = "Uconnect",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Stellantis Uconnect in-car hotspot (Chrysler, Jeep, Ram, Dodge, Fiat). BSSID often randomized.",
        builtIn = true,
        rules = listOf(
            wifiGlob("Uconnect*"),
            wifiName("Uconnect"),
        ),
    )

    private fun carPlay() = Fleet(
        id = "fleet-carplay",
        name = "CarPlay",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "In-car CarPlay / Alpine head-unit hotspot. Factory name from the dash, not an ISP.",
        builtIn = true,
        rules = listOf(
            wifiGlob("CarPlay*"),
            wifiName("CarPlay"),
        ),
    )

    private fun carlink() = Fleet(
        id = "fleet-carlink",
        name = "CARLINK",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Aftermarket CarPlay / Android Auto adapter hotspot (CARLINK-). Head-unit dongle, not the car’s own modem.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("CARLINK-??????"),
            ),
            listOf("CC:57:63", "68:8F:C9"),
        ),
    )

    private fun rivian() = Fleet(
        id = "fleet-rivian",
        name = "Rivian",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Rivian phone-as-key, camp speaker, or sensor. Phones can advertise Rivian Sensor.",
        builtIn = true,
        rules = listOf(
            mfg(0x0941),
            bleName("Rivian"),
            bleGlob("Rivian*"),
        ),
    )

    private fun govee() = Fleet(
        id = "fleet-govee",
        name = "Govee",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Govee light or hygrometer. Lights usually only send a name; hygrometers can decode temp / humidity / battery below.",
        builtIn = true,
        decode = CatalogDecodes.govee,
        rules = listOf(
            bleName("Govee"),
            bleGlob("Govee*"),
            bleGlob("GBK_*"),
            bleGlob("ihoment_*"),
            bleGlob("GV5108*"),
            bleGlob("GVH5*"),
            bleGlob("GVH5075*"),
        ),
    )

    private fun hpPrinter() = Fleet(
        id = "fleet-hp",
        name = "HP",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "HP home/office printer (ENVY, HP-Print). Office noise. Not HPE Aruba campus Wi-Fi.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                mfg(0x0065),
                uuid("FE78"),
                bleName("ENVY"),
                bleGlob("ENVY*"),
                wifiGlob("HP-Print*"),
            ),
            ApVendorOuis.HPINC,
        ),
    )

    private fun mercedesMbux() = Fleet(
        id = "fleet-mbux",
        name = "Mercedes MBUX",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Mercedes MBUX in-car hotspot. Factory name from the car.",
        builtIn = true,
        rules = listOf(
            mfg(0x017C),
            bleName("Mercedes"),
            wifiName("MBUX"),
            wifiGlob("MBUX*"),
        ),
    )

    private fun motive() = Fleet(
        id = "fleet-motive",
        name = "Motive",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Motive (KeepTruckin) electronic logging device / fleet Wi-Fi in a truck.",
        builtIn = true,
        rules = listOf(
            wifiGlob("Motive *"),
            wifiGlob("Motive_*"),
            wifiGlob("Motive Hotspot*"),
            wifiGlob("KeepTruckin*"),
        ),
    )

    private fun peopleNet() = Fleet(
        id = "fleet-peoplenet",
        name = "PeopleNet",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "PeopleNet truck ELD / fleet Wi-Fi. Separate from Motive.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("PNet*"),
            ),
            ApVendorOuis.PEOPLENET,
        ),
    )

    private fun goodyearTpms() = Fleet(
        id = "fleet-goodyear",
        name = "Goodyear TPMS",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Goodyear intelligent-tire BLE, not the 315/433 MHz valve-stem TPMS in most cars. Pattern match, not that car.",
        builtIn = true,
        rules = listOf(
            mfg(0x0B99),
        ),
    )

    private fun schraderTpms() = Fleet(
        id = "fleet-schrader",
        name = "Schrader TPMS",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Schrader aftermarket BLE TPMS (AirCheck, trailer, RV). Not the 315/433 MHz factory stems.",
        builtIn = true,
        rules = listOf(
            mfg(0x0601),
        ),
    )

    private fun pacificTpms() = Fleet(
        id = "fleet-pacific-tpms",
        name = "Pacific TPMS",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Pacific Industrial OEM tire electronics. BLE TPMS on some newer vehicles.",
        builtIn = true,
        rules = listOf(
            mfg(0x0E32),
        ),
    )

    private fun hufTpms() = Fleet(
        id = "fleet-huf",
        name = "Huf",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Huf tire sensor or vehicle access (door handle / PEPS). Not only a valve stem.",
        builtIn = true,
        rules = listOf(
            mfg(0x070A),
        ),
    )

    private fun foboTpms() = Fleet(
        id = "fleet-fobo",
        name = "FOBO TPMS",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "FOBO aftermarket BLE tire-pressure sensor. Motorcycle or car. Pattern match, not that vehicle.",
        builtIn = true,
        rules = listOf(
            mfg(0x0127),
            bleName("FOBO"),
            bleGlob("FOBO*"),
        ),
    )

    private fun ruuvi() = Fleet(
        id = "fleet-ruuvi",
        name = "Ruuvi",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Ruuvi broadcast sensor tag (temp / humidity / pressure / motion). Decoded fields on this page parse the sensor payload.",
        builtIn = true,
        decode = CatalogDecodes.ruuvi,
        rules = listOf(
            mfg(0x0499),
            bleName("Ruuvi"),
            bleGlob("Ruuvi*"),
        ),
    )

    private fun blueMaestro() = Fleet(
        id = "fleet-bluemaestro",
        name = "Blue Maestro",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Blue Maestro Tempo Disc temperature / humidity logger. Decoded fields can show version, battery, and temperature.",
        builtIn = true,
        decode = CatalogDecodes.blueMaestro,
        rules = listOf(
            mfg(0x0133),
        ),
    )

    private fun sensorPush() = Fleet(
        id = "fleet-sensorpush",
        name = "SensorPush",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "SensorPush temperature / humidity logger.",
        builtIn = true,
        rules = listOf(
            uuid("EF090000-11D6-42BA-93B8-9DD7EC090AA9"),
            uuid("EF090000-11D6-42BA-93B8-9DD7EC090AB0"),
            bleName("SensorPush"),
            bleGlob("SensorPush*"),
        ),
    )

    private fun samsara() = Fleet(
        id = "fleet-samsara",
        name = "Samsara",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Samsara fleet tracker or vehicle Wi-Fi. Truck / van telematics, adjacent to Motive.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                mfg(0x0B6B),
                bleName("Samsara"),
                bleGlob("Samsara*"),
                wifiGlob("Samsara*"),
            ),
            ApVendorOuis.SAMSARA,
        ),
    )

    private fun tapo() = Fleet(
        id = "fleet-tapo",
        name = "Tapo",
        enabled = true,
        colorIndex = Hue.CAMERA,
        kind = SignatureClass.CAMERA,
        matchAny = true,
        notes = "TP-Link Tapo home camera. Consumer camera, not a roadside pole.",
        builtIn = true,
        rules = listOf(
            name("Tapo"),
            glob("Tapo*"),
        ),
    )

    private fun reolink() = Fleet(
        id = "fleet-reolink",
        name = "Reolink",
        enabled = true,
        colorIndex = Hue.CAMERA,
        kind = SignatureClass.CAMERA,
        matchAny = true,
        notes = "Reolink home / small-business camera. Consumer camera, not a roadside pole.",
        builtIn = true,
        rules = listOf(
            name("Reolink"),
            glob("Reolink*"),
        ),
    )

    private fun hikvision() = Fleet(
        id = "fleet-hikvision",
        name = "Hikvision",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Hikvision camera. Common on commercial CCTV and some public poles.",
        attentionNote = "Hikvision cameras. Common on commercial CCTV and some public poles. Name-only. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Hikvision"),
            name("HIKVISION"),
            glob("Hikvision*"),
        ),
    )

    private fun dahua() = Fleet(
        id = "fleet-dahua",
        name = "Dahua",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Dahua camera. Common on commercial CCTV and some public poles.",
        attentionNote = "Dahua cameras. Common on commercial CCTV and some public poles. Name-only. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Dahua"),
            name("DAHUA"),
            glob("Dahua*"),
        ),
    )

    private fun meshtastic() = Fleet(
        id = "fleet-meshtastic",
        name = "Meshtastic",
        enabled = true,
        colorIndex = Hue.MESH,
        kind = SignatureClass.MESH,
        matchAny = true,
        notes = "Meshtastic LoRa mesh node. Off-grid text/location radios, not cellular.",
        builtIn = true,
        rules = listOf(
            name("Meshtastic"),
            glob("Meshtastic*"),
            glob("Meshtastic_*"),
            uuid("6ba1b218"),
        ),
    )

    private fun helium() = Fleet(
        id = "fleet-helium",
        name = "Helium",
        enabled = true,
        colorIndex = Hue.MESH,
        kind = SignatureClass.MESH,
        matchAny = true,
        notes = "Helium / LoRaWAN hotspot when it advertises a name.",
        builtIn = true,
        rules = listOf(
            name("Helium"),
            glob("Helium*"),
        ),
    )

    private fun meshCore() = Fleet(
        id = "fleet-meshcore",
        name = "MeshCore",
        enabled = true,
        colorIndex = Hue.MESH,
        kind = SignatureClass.MESH,
        matchAny = true,
        notes = "MeshCore LoRa companion radio. Off-grid text/location, not cellular. Name-only — Nordic UART UUID is every ESP32 serial board and is not this row.",
        builtIn = true,
        rules = listOf(
            bleName("MeshCore"),
            bleGlob("MeshCore*"),
        ),
    )

    private fun goTenna() = Fleet(
        id = "fleet-gotenna",
        name = "goTenna",
        enabled = true,
        colorIndex = Hue.MESH,
        kind = SignatureClass.MESH,
        matchAny = true,
        notes = "goTenna Mesh or Pro companion radio. Pairs over BLE; the mesh itself is UHF and Fieldwatch cannot hear it. Pro is sold to agencies. Pattern match, not that operator.",
        builtIn = true,
        rules = listOf(
            uuid("1276aaee-df5e-11e6-bf01-fe55135034f3"),
            uuid("f0abaaee-ebfa-f96f-28da-076c35a521db"),
            bleName("goTenna"),
            bleGlob("goTenna*"),
            bleGlob("gotenna*"),
        ),
    )

    private fun senseCap() = Fleet(
        id = "fleet-sensecap",
        name = "SenseCAP",
        enabled = true,
        colorIndex = Hue.MESH,
        kind = SignatureClass.MESH,
        matchAny = true,
        notes = "Seeed SenseCAP LoRaWAN / Helium indoor gateway setup AP (SenseCAP_XXXXXX). Quiet once it is on Ethernet. Helium-named units can also hit the Helium row.",
        builtIn = true,
        rules = listOf(
            wifiName("SenseCAP"),
            wifiGlob("SenseCAP*"),
            wifiGlob("SenseCAP_*"),
        ),
    )

    private fun rakWisGate() = Fleet(
        id = "fleet-rak-wisgate",
        name = "RAK WisGate",
        enabled = true,
        colorIndex = Hue.MESH,
        kind = SignatureClass.MESH,
        matchAny = true,
        notes = "RAKwireless WisGate LoRaWAN gateway setup AP (RAK7268_XXXX and similar). Quiet once it is on Ethernet.",
        builtIn = true,
        rules = listOf(
            wifiGlob("RAK7*"),
            wifiGlob("RAK7268*"),
            wifiGlob("RAK7249*"),
            wifiGlob("RAK7289*"),
            wifiGlob("RAK7391*"),
            name("WisGate"),
            glob("WisGate*"),
        ),
    )

    private fun genetec() = Fleet(
        id = "fleet-genetec",
        name = "Genetec AutoVu",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Genetec AutoVu parking or roadside plate reader.",
        attentionNote = "Genetec AutoVu is municipal / parking ALPR — reads plates at lots and roadside. Name-only when advertised. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Genetec"),
            name("AutoVu"),
            glob("Genetec*"),
            glob("AutoVu*"),
        ),
    )

    private fun blueToadSpectra() = Fleet(
        id = "fleet-bluetoad",
        name = "BlueTOAD Spectra",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Iteris roadside Bluetooth travel-time reader (Vantage Velocity, now BlueTOAD Spectra / Spectra CV). Samples phones, headsets, and in-car Bluetooth as vehicles pass; matching the same ID at two points gives speed. About 100 m. A sample, not a full count. Quiet or Ethernet-only cabinets may not advertise. Spectra CV also uses 5.9 GHz C-V2X, which Fieldwatch cannot hear. IEEE Iteris OUI can also hit other Iteris roadside kit. Pattern match, not that cabinet.",
        builtIn = true,
        rules = listOf(
            // IEEE MA-L registered to Iteris, Inc.
            oui("00:14:7B"),
            name("BlueTOAD"),
            glob("BlueTOAD*"),
            name("Vantage Velocity"),
            glob("VantageVelocity*"),
            glob("Vantage-Velocity*"),
            name("Spectra CV"),
            glob("SpectraCV*"),
            glob("Spectra-CV*"),
            name("TrafficCast"),
            glob("TrafficCast*"),
            name("VantageARGUS"),
            glob("VantageARGUS*"),
            name("BlueARGUS"),
            glob("BlueARGUS*"),
        ),
    )

    private fun blipTrack() = Fleet(
        id = "fleet-bliptrack",
        name = "BlipTrack",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "BLIP Systems BlipTrack roadside Bluetooth/Wi-Fi travel-time sensor. Same job as BlueTOAD Spectra: samples passing phones and in-car radios at two points for speed. Quiet or Ethernet-only cabinets may not advertise. Pattern match, not that cabinet.",
        builtIn = true,
        rules = listOf(
            // IEEE MA-L registered to BLIP Systems
            oui("00:0E:A5"),
            name("BlipTrack"),
            glob("BlipTrack*"),
            name("BLIP Systems"),
            glob("BLIP-Track*"),
        ),
    )

    private fun hanwhaWisenet() = Fleet(
        id = "fleet-hanwha-wisenet",
        name = "Hanwha Wisenet",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Hanwha Vision / Wisenet camera (ex-Samsung Techwin). Common on commercial CCTV and some public poles.",
        attentionNote = "Hanwha Vision / Wisenet cameras. Common on commercial CCTV and some public poles. A *_WISENET setup SSID is the stronger hit; IEEE 00:09:18 is Samsung Techwin. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            oui("00:09:18"),
            name("Wisenet"),
            glob("Wisenet*"),
            glob("*_WISENET"),
            glob("*WISENET*"),
            name("Hanwha"),
            glob("Hanwha*"),
        ),
    )

    private fun uniview() = Fleet(
        id = "fleet-uniview",
        name = "Uniview",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Uniview / UNV / Uniarch camera. Common on commercial CCTV and some public poles.",
        attentionNote = "Uniview / UNV cameras. Common on commercial CCTV and some public poles. IEEE Zhejiang Uniview OUIs or a Uniview / UNV- name. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = buildList {
            listOf(
                "14:BA:88", "48:EA:63", "6C:F1:7E", "88:26:3F", "C4:79:05",
            ).forEach { add(oui(it)) }
            add(name("Uniview"))
            add(glob("Uniview*"))
            add(glob("UNV-*"))
            add(name("Uniarch"))
            add(glob("Uniarch*"))
        },
    )

    private fun rhombus() = Fleet(
        id = "fleet-rhombus",
        name = "Rhombus",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Rhombus cloud camera. BLE is loudest when the camera is unregistered or offline.",
        attentionNote = "Rhombus cloud cameras on buildings and some public sites. IEEE CC:47:BD or a Rhombus name. BLE often only while unregistered or offline. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            oui("CC:47:BD"),
            name("Rhombus"),
            glob("Rhombus*"),
        ),
    )

    private fun rekor() = Fleet(
        id = "fleet-rekor",
        name = "Rekor",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Rekor highway or transit plate reader.",
        attentionNote = "Rekor is highway / transit ALPR — reads plates on roads and at checkpoints. Name-only. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Rekor"),
            glob("Rekor*"),
        ),
    )

    private fun axon() = Fleet(
        id = "fleet-axon",
        name = "Axon",
        enabled = true,
        colorIndex = Hue.LAW,
        kind = SignatureClass.LAW_ENFORCEMENT,
        matchAny = true,
        notes = "Axon body-worn camera, in-car system, dock, or TASER. Quiet or LTE-only units will not appear.",
        attentionNote = "Axon body-worn, in-car (Fleet), dock, or TASER gear. IEEE OUI 00:25:DF is Axon Enterprise. Body 3/4 often advertise BLE on that public OUI while worn. A name like Axon Body is a pattern, not that officer. Quiet or LTE-only units will not appear. The word Axon also hits some ZTE phones. Look with your eyes. Not identity.",
        builtIn = true,
        rules = listOf(
            oui("00:25:DF"),
            name("Axon Fleet"),
            name("Axon Body"),
            name("Axon Dock"),
            name("BWCDEVICE"),
            glob("Axon*"),
            uuid("FE6C"),
        ),
    )

    private fun watchGuardVideo() = Fleet(
        id = "fleet-watchguard",
        name = "WatchGuard Video",
        enabled = true,
        colorIndex = Hue.LAW,
        kind = SignatureClass.LAW_ENFORCEMENT,
        matchAny = true,
        notes = "WatchGuard Video body-worn or in-car camera (now Motorola). Not the WatchGuard firewall company. Patrol units may stay quiet.",
        attentionNote = "WatchGuard Video body-worn or in-car (VISTA / V300 family). IEEE OUI 00:1D:96 is WatchGuard Video, not the WatchGuard firewall company. Patrol units may stay quiet. Pattern match, not identity. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            oui("00:1D:96"),
            name("WatchGuard"),
            name("Watchguard"),
            glob("WatchGuard*"),
            name("VISTA WiFi"),
            name("VISTA XLT"),
        ),
    )

    private fun digitalAlly() = Fleet(
        id = "fleet-digital-ally",
        name = "Digital Ally",
        enabled = true,
        colorIndex = Hue.LAW,
        kind = SignatureClass.LAW_ENFORCEMENT,
        matchAny = true,
        notes = "Digital Ally body-worn or in-car camera (FirstVu / EVO). Quiet or LTE-only units will not appear.",
        attentionNote = "Digital Ally body-worn or in-car (FirstVu / EVO family). IEEE OUI 00:23:BD is Digital Ally, Inc. — camera gear, not a chip vendor. Patrol units may stay on LTE and stay quiet. Pattern match, not that officer. Look with your eyes. Not identity.",
        builtIn = true,
        rules = listOf(
            oui("00:23:BD"),
            name("Digital Ally"),
            glob("DigitalAlly*"),
            name("FirstVu"),
            glob("FirstVu*"),
            name("EVO-HD"),
            name("VuLink"),
        ),
    )

    private fun revealMedia() = Fleet(
        id = "fleet-reveal-media",
        name = "Reveal Media",
        enabled = true,
        colorIndex = Hue.LAW,
        kind = SignatureClass.LAW_ENFORCEMENT,
        matchAny = true,
        notes = "Reveal Media / BodyWorn camera. Common in UK and some US agencies. Quiet units will not appear.",
        attentionNote = "Reveal Media body-worn camera (D-series / BodyWorn). Name-only when advertised. Patrol units may stay quiet. Pattern match, not that officer. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Reveal Media"),
            glob("Reveal D*"),
            glob("Reveal-D*"),
            name("BodyWorn"),
            glob("BodyWorn*"),
            glob("RS2-*"),
        ),
    )

    private fun wolfcom() = Fleet(
        id = "fleet-wolfcom",
        name = "Wolfcom",
        enabled = true,
        colorIndex = Hue.LAW,
        kind = SignatureClass.LAW_ENFORCEMENT,
        matchAny = true,
        notes = "Wolfcom body-worn or in-car camera. Quiet units will not appear.",
        attentionNote = "Wolfcom body-worn or in-car camera. Name-only when advertised. Patrol units may stay quiet. Pattern match, not that officer. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Wolfcom"),
            glob("Wolfcom*"),
            glob("WOLFCOM*"),
        ),
    )

    private fun panasonicIpro() = Fleet(
        id = "fleet-panasonic-ipro",
        name = "Panasonic i-PRO",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Panasonic i-PRO camera or Arbitrator in-car system. Panasonic TVs and phones use other names.",
        attentionNote = "Panasonic i-PRO camera or Arbitrator in-car video. Used on buildings and some patrol cars — video and sometimes plates. Name-only (i-PRO / Arbitrator). Not every Panasonic radio. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("i-PRO"),
            glob("i-PRO*"),
            glob("iPRO*"),
            name("Arbitrator"),
            glob("Arbitrator*"),
        ),
    )

    private fun avigilon() = Fleet(
        id = "fleet-avigilon",
        name = "Avigilon",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Avigilon building or municipal camera, sometimes with LPR.",
        attentionNote = "Motorola Avigilon cameras / LPR. Used on municipal poles and commercial sites — video and sometimes plates. Name-only. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Avigilon"),
            glob("Avigilon*"),
        ),
    )

    private fun axis() = Fleet(
        id = "fleet-axis",
        name = "Axis",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Axis camera, common on municipal poles and public CCTV.",
        attentionNote = "Axis Communications cameras, common on municipal poles and public CCTV. Name-only (AXIS-). Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            glob("AXIS-*"),
            glob("Axis-*"),
            name("AXIS-"),
            name("Axis Camera"),
        ),
    )

    private fun haydenAi() = Fleet(
        id = "fleet-hayden-ai",
        name = "Hayden AI",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Hayden AI bus- or vehicle-mounted camera used for parking and traffic enforcement.",
        attentionNote = "Hayden AI cameras ride on buses and city vehicles — video and plates. Name-only when advertised. LTE-only units will not appear. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Hayden AI"),
            glob("HaydenAI*"),
            glob("Hayden-AI*"),
        ),
    )

    private fun miovision() = Fleet(
        id = "fleet-miovision",
        name = "Miovision",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Miovision intersection / traffic camera (SmartLink / Scout).",
        attentionNote = "Miovision traffic cameras at intersections. Video and sometimes plates. Name-only when advertised. Many units are cellular-only and stay quiet. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Miovision"),
            glob("Miovision*"),
        ),
    )

    private fun tattile() = Fleet(
        id = "fleet-tattile",
        name = "Tattile",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "Tattile ALPR camera, common on European roads and some US sites.",
        attentionNote = "Tattile plate readers on roads and at gates. Name-only when advertised. Pattern match, not that camera. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("Tattile"),
            glob("Tattile*"),
        ),
    )

    private fun liveViewLvt() = Fleet(
        id = "fleet-lvt",
        name = "LVT LiveView",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "LiveView Technologies (LVT) solar surveillance trailer. Most units are cellular-only and will not appear.",
        attentionNote = "LVT / LiveView solar camera trailer — parking lots, construction, some city parks. Video and sometimes plates. Most units use cellular and stay quiet on Wi-Fi/BLE. A LiveView or LVT- name is a pattern, not that trailer. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("LiveView"),
            glob("LiveView*"),
            glob("LVT-*"),
            glob("LVT_*"),
        ),
    )

    private fun unifi() = Fleet(
        id = "fleet-unifi",
        name = "UniFi",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "UniFi / Ubiquiti name on Wi-Fi or BLE. Prefer UniFi AP for BSSID hits. Instant cameras stay on UniFi Protect.",
        builtIn = true,
        rules = listOf(
            name("UniFi"),
            name("Ubiquiti"),
            glob("UniFi*"),
            glob("UAP-*"),
        ),
    )

    private fun unifiAp() = Fleet(
        id = "fleet-unifi-ap",
        name = "UniFi AP",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "UniFi / Ubiquiti Wi-Fi access point (including airMAX / AmpliFi). Renamed SSIDs still hit on the board vendor or Ubiquiti vendor tag. Instant cameras stay on UniFi Protect.",
        builtIn = true,
        rules = buildList {
            add(wifiName("UniFi"))
            add(wifiName("Ubiquiti"))
            add(wifiName("UBNT"))
            add(wifiGlob("UniFi*"))
            add(wifiGlob("UAP-*"))
            add(wifiGlob("UBNT*"))
            // IEEE MA-L registered to Ubiquiti Inc (through 2026-06-17). Not the shared IAB 00:50:C2:B0:4.
            listOf(
                "00:15:6D", "00:27:22", "04:18:D6", "0C:EA:14", "18:E8:29", "1C:0B:8B",
                "1C:6A:1B", "24:5A:4C", "24:A4:3C", "28:70:4E", "2C:E5:BD", "44:D9:E7",
                "58:D6:1F", "60:22:32", "68:2E:3C", "68:72:51", "68:D7:9A", "6C:63:F8",
                "70:A7:41", "74:83:C2", "74:AC:B9", "74:F9:2C", "74:FA:29", "78:45:58",
                "78:8A:20", "80:2A:A8", "84:78:48", "8C:30:66", "8C:ED:E1", "90:41:B2",
                "94:2A:6F", "9C:05:D6", "A4:F8:FF", "A8:9C:6C", "AC:8B:A9", "B4:FB:E4",
                "CC:35:D9", "D0:21:F9", "D4:89:C1", "D8:B3:70", "D8:C2:62", "DC:9F:DB",
                "E0:63:DA", "E4:38:83", "F0:9F:C2", "F4:92:BF", "F4:E2:C6", "FC:EC:DA",
            ).forEach { add(wifiOui(it)) }
        },
    )

    private fun hobbyBleSerial() = Fleet(
        id = "fleet-hobby-ble",
        name = "Hobby BLE serial",
        enabled = true,
        colorIndex = Hue.HACKING,
        kind = SignatureClass.HACKING,
        matchAny = true,
        notes = "Cheap hobby UART module advertised under a default name (HM-10, JDY, CC41, ESP32 BLE). Common on printers, cars, and DIY. Fieldwatch does not see Classic HC-05/HC-06.",
        attentionNote = "These default BLE serial names are cheap hobby modules. Some pump/ATM overlays have used boards like this so someone nearby can pull data over Bluetooth. The same modules show up on printers, cars, and DIY. If this radio is loud next to a card reader, treat it with caution and look with your eyes. Not proof of a skimmer. A miss is not a clean bill (name changed, Classic HC-05/HC-06, or cellular). Fieldwatch does not connect and does not try default PINs. Fieldwatch does not see Classic HC-05/HC-06.",
        builtIn = true,
        rules = listOf(
            bleName("HMSoft"),
            bleGlob("HMSoft*"),
            bleName("HM-10"),
            bleGlob("HM-10*"),
            bleName("CC41-A"),
            bleGlob("CC41*"),
            bleName("AT-09"),
            bleGlob("AT-09*"),
            bleName("JDY-08"),
            bleName("JDY-10"),
            bleName("JDY-16"),
            bleName("JDY-31"),
            bleGlob("JDY-*"),
            bleName("BT05"),
            bleName("MLT-BT05"),
            bleName("ESP32"),
            bleGlob("ESP32-*"),
        ),
    )

    private fun metaGlasses() = Fleet(
        id = "fleet-meta-glasses",
        name = "Ray-Ban / Meta glasses",
        enabled = true,
        colorIndex = Hue.GLASSES,
        kind = SignatureClass.GLASSES,
        matchAny = true,
        notes = "Ray-Ban Meta / Oakley Meta smart glasses, or a Quest / other Meta wearable.",
        attentionNote = "Meta / Luxottica BLE — often Ray-Ban Meta smart glasses. The same company IDs show up on Quest headsets and other Meta wearables. Not proof someone is recording. A miss is not a clean bill (paired and quiet, asleep, or a different brand). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            mfg(0x01AB),
            mfg(0x058E),
            mfg(0x0D53),
            bleName("Ray-Ban"),
            bleName("RayBan"),
            bleGlob("Ray-Ban*"),
            bleGlob("RayBan*"),
            bleName("Meta View"),
            bleName("Oakley Meta"),
        ),
    )

    private fun snapSpectacles() = Fleet(
        id = "fleet-snap-spectacles",
        name = "Snap Spectacles",
        enabled = true,
        colorIndex = Hue.GLASSES,
        kind = SignatureClass.GLASSES,
        matchAny = true,
        notes = "Snap Spectacles or other Snap BLE product.",
        attentionNote = "Snapchat BLE company ID — used by Snap Spectacles. Other Snap BLE products could match. Not proof of recording. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            mfg(0x03C2),
            bleName("Snap Spectacles"),
            bleName("Spectacles"),
            bleGlob("Spectacles*"),
        ),
    )

    private fun brilliantFrame() = Fleet(
        id = "fleet-brilliant-frame",
        name = "Brilliant Frame",
        enabled = true,
        colorIndex = Hue.GLASSES,
        kind = SignatureClass.GLASSES,
        matchAny = true,
        notes = "Brilliant Labs Frame AR glasses.",
        attentionNote = "Brilliant Labs Frame AR glasses. BLE service 7A230001. Not proof someone is recording. A miss is not a clean bill (off, or a different brand). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            uuid("7A230001-5475-A6A4-654C-576174636800"),
            bleName("Brilliant Frame"),
            bleGlob("Brilliant Frame*"),
        ),
    )

    private fun evenG1() = Fleet(
        id = "fleet-even-g1",
        name = "Even G1",
        enabled = true,
        colorIndex = Hue.GLASSES,
        kind = SignatureClass.GLASSES,
        matchAny = true,
        notes = "Even Realities G1 glasses. Name-only (Even G1). Nordic UART is too common to use as a rule.",
        attentionNote = "Even Realities G1 glasses. Name-only when advertised (Even G1). Not proof of recording. A miss is not a clean bill (off, renamed, or paired and quiet). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            bleName("Even G1"),
            bleGlob("Even G1*"),
        ),
    )

    private fun hak5Pineapple() = Fleet(
        id = "fleet-hak5-pineapple",
        name = "Hak5 Pineapple",
        enabled = true,
        colorIndex = Hue.HACKING,
        kind = SignatureClass.HACKING,
        matchAny = true,
        notes = "Hak5 WiFi Pineapple setup or management AP. Cloned café SSIDs from PineAP look like ordinary Wi-Fi and will not hit this row.",
        attentionNote = "Hak5 WiFi Pineapple setup or management AP (Pineapple_XXXX). That is the admin radio, not every rogue SSID PineAP might impersonate — those look like ordinary café Wi-Fi. A miss is not a clean bill (renamed, or only cloning). Pattern match, not identity. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            glob("Pineapple_*"),
            name("Pineapple_"),
            name("WiFi Pineapple"),
            name("Hak5"),
            glob("Hak5*"),
        ),
    )

    private fun flipperZero() = Fleet(
        id = "fleet-flipper",
        name = "Flipper Zero",
        enabled = true,
        colorIndex = Hue.HACKING,
        kind = SignatureClass.HACKING,
        matchAny = true,
        notes = "Flipper Zero (or other Flipper Devices). Default name starts with Flipper; custom firmware can hide it.",
        attentionNote = "Flipper Zero (or other Flipper Devices) BLE. Default name starts with Flipper; newer units use IEEE OUI 0C:FA:22. Custom firmware can change the name and MAC. Not proof of an attack. A miss is not a clean bill (Bluetooth off, or renamed). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            oui("0C:FA:22"),
            bleName("Flipper"),
            bleGlob("Flipper*"),
            bleName("Flipper Zero"),
        ),
    )

    private fun pwnagotchi() = Fleet(
        id = "fleet-pwnagotchi",
        name = "Pwnagotchi",
        enabled = true,
        colorIndex = Hue.HACKING,
        kind = SignatureClass.HACKING,
        matchAny = true,
        notes = "Pwnagotchi-style Wi-Fi handshake collector. Classic units use a distinctive BSSID and the name pwnagotchi.",
        attentionNote = "Pwnagotchi-style Wi-Fi handshake collector. Classic units beacon BSSID de:ad:be:ef:de:ad. A pwnagotchi name is a pattern. Not proof of an attack. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            MatchRule(RuleKind.MAC_PREFIX, text = "DE:AD:BE:EF:DE:AD"),
            name("pwnagotchi"),
            glob("pwnagotchi*"),
        ),
    )

    private fun marauderDeauther() = Fleet(
        id = "fleet-marauder",
        name = "Marauder / Deauther",
        enabled = true,
        colorIndex = Hue.HACKING,
        kind = SignatureClass.HACKING,
        matchAny = true,
        notes = "ESP32 Marauder or Spacehuhn-style Wi-Fi deauther on a default name. Same boards are DIY; renamed units miss.",
        attentionNote = "ESP32 Marauder or Spacehuhn-style Wi-Fi deauther default names. Same boards are DIY. Not proof of an attack. A miss is not a clean bill (renamed). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("MarauderAP"),
            name("Marauder"),
            glob("Marauder*"),
            name("ESP32 Marauder"),
            name("Deauther"),
            glob("Deauther*"),
        ),
    )

    private fun ghostEsp() = Fleet(
        id = "fleet-ghostesp",
        name = "GhostESP",
        enabled = true,
        colorIndex = Hue.HACKING,
        kind = SignatureClass.HACKING,
        matchAny = true,
        notes = "GhostESP ESP32 audit firmware. Default setup AP is GhostNet. Same boards are DIY; renamed units miss.",
        attentionNote = "GhostESP ESP32 audit firmware default AP (GhostNet). Same boards are DIY. Not proof of an attack. A miss is not a clean bill (renamed). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            wifiName("GhostNet"),
            wifiGlob("GhostNet*"),
        ),
    )

    private fun bruceFirmware() = Fleet(
        id = "fleet-bruce",
        name = "Bruce",
        enabled = true,
        colorIndex = Hue.HACKING,
        kind = SignatureClass.HACKING,
        matchAny = true,
        notes = "Bruce ESP32 pentest firmware. Default setup AP is BruceNet. Same boards are DIY; renamed units and evil-portal SSIDs miss.",
        attentionNote = "Bruce ESP32 pentest firmware default AP (BruceNet). Same boards are DIY. Evil-portal SSIDs look like ordinary Wi-Fi and will not hit this row. Not proof of an attack. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            wifiName("BruceNet"),
            wifiGlob("BruceNet*"),
        ),
    )

    private fun porkchop() = Fleet(
        id = "fleet-porkchop",
        name = "Porkchop",
        enabled = true,
        colorIndex = Hue.HACKING,
        kind = SignatureClass.HACKING,
        matchAny = true,
        notes = "Porkchop pentest firmware on a Cardputer or Cheap Yellow Display. Default AP name PORKCHOP. BLE spam that spoofs Apple/Android is not this row.",
        attentionNote = "M5PORKCHOP / Porkchop — pocket Wi-Fi pentest firmware (Cardputer or Cheap Yellow Display). Default CYD remote AP is named PORKCHOP. BACON-mode fake APs brand vendor IE 50:52:4B. A miss is not a clean bill (renamed, passive only, or no AP). Pattern match, not identity, not proof of an attack. Look with your eyes.",
        builtIn = true,
        rules = listOf(
            name("PORKCHOP"),
            glob("PORKCHOP*"),
            glob("M5PORKCHOP*"),
            vendorIe("50:52:4B"),
        ),
    )

    private fun pokemonGoPlus() = Fleet(
        id = "fleet-pokemon-go-plus",
        name = "Pokemon GO Plus",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.WEARABLE,
        matchAny = true,
        notes = "Nintendo Pokémon GO Plus or Plus + wrist accessory. Not a Joy-Con or Switch.",
        builtIn = true,
        rules = listOf(
            uuid("138C35B6-0000-1000-8000-00805F9B34FB"),
            uuid("21c50462-67cb-63a3-5c4c-82b5b9939aeb"),
            bleName("Pokemon GO Plus"),
            bleGlob("Pokemon GO Plus*"),
        ),
    )

    private fun hatch() = Fleet(
        id = "fleet-hatch",
        name = "Hatch",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Hatch Rest / Restore / Mini sound machine. Nursery / bedroom noise.",
        builtIn = true,
        rules = listOf(
            mfg(0x0434),
            oui("C8:FA:9C"),
            bleName("Hatch Rest"),
            bleGlob("Hatch Rest*"),
            bleGlob("Hatch Restore*"),
            bleGlob("Hatch Mini*"),
        ),
    )

    private fun bhyve() = Fleet(
        id = "fleet-bhyve",
        name = "Orbit B-hyve",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Orbit B-hyve hose timer / irrigation.",
        builtIn = true,
        rules = listOf(
            oui("44:67:55"),
            uuid("FE32"),
            bleName("bhyve"),
            bleGlob("bhyve*"),
            bleGlob("B-hyve*"),
        ),
    )

    private fun samsungAppliance() = Fleet(
        id = "fleet-samsung-appliance",
        name = "Samsung appliance",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Samsung Family Hub fridge, range, or oven setup Wi-Fi. Not a SmartTag.",
        builtIn = true,
        rules = listOf(
            wifiGlob("[fridge]*"),
            wifiGlob("[oven]*"),
            wifiGlob("[range]*"),
            wifiGlob("[cooktop]*"),
            wifiGlob("[refrigerator]*"),
        ),
    )

    private fun ecoWater() = Fleet(
        id = "fleet-ecowater",
        name = "EcoWater",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "EcoWater / water-softener setup Wi-Fi. Home plumbing IoT.",
        builtIn = true,
        rules = listOf(
            wifiGlob("H2O-????????????"),
        ),
    )

    private fun fieldy() = Fleet(
        id = "fleet-fieldy",
        name = "Fieldy",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.WEARABLE,
        matchAny = true,
        notes = "Fieldy pendant — wearable AI note-taker.",
        attentionNote = "Fieldy wearable AI note-taker (pendant). It records conversations and transcribes them. Not proof someone is recording you. A miss is not a clean bill (off, paired and quiet, or a different brand). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            bleName("Fieldy"),
            bleGlob("Fieldy*"),
        ),
    )

    private fun plaud() = Fleet(
        id = "fleet-plaud",
        name = "Plaud Note",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.WEARABLE,
        matchAny = true,
        notes = "Plaud Note / NotePin AI meeting recorder.",
        attentionNote = "Plaud Note / NotePin AI recorder. It records meetings. Not proof someone is recording you. A miss is not a clean bill (off, renamed, or a different brand). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            bleName("Plaud Note"),
            bleGlob("Plaud Note*"),
            bleGlob("Plaud NotePin*"),
        ),
    )

    private fun limitlessPendant() = Fleet(
        id = "fleet-limitless",
        name = "Limitless Pendant",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.WEARABLE,
        matchAny = true,
        notes = "Limitless / Rewind AI pendant — wearable conversation recorder.",
        attentionNote = "Limitless Pendant wearable recorder. BLE service 632de001. It records conversations. Not proof someone is recording you. A miss is not a clean bill (off, paired and quiet, or a different brand). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            uuid("632DE001-604C-446B-A80F-7963E950F3FB"),
            bleName("Limitless"),
            bleGlob("Limitless*"),
        ),
    )

    private fun beePendant() = Fleet(
        id = "fleet-bee",
        name = "Bee Pendant",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.WEARABLE,
        matchAny = true,
        notes = "Bee Pioneer wearable recorder (now Amazon). Always-on audio capture.",
        attentionNote = "Bee Pioneer wearable recorder (Amazon). BLE service 03d5d5c4. It records conversations. Not proof someone is recording you. A miss is not a clean bill (off, or a different brand). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            uuid("03D5D5C4-A86C-11EE-9D89-8F2089A49E7E"),
            bleName("Bee Pioneer"),
            bleGlob("Bee Pioneer*"),
        ),
    )

    private fun omiPendant() = Fleet(
        id = "fleet-omi",
        name = "Omi",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.WEARABLE,
        matchAny = true,
        notes = "Omi / OpenGlass wearable recorder or camera glasses. Arduino-default 19B10000 is too common to use as a rule.",
        attentionNote = "Omi pendant or OpenGlass camera glasses. Name or BLE service 23ba7924. It can record audio (OpenGlass also has a camera). Not proof someone is recording you. A miss is not a clean bill (off, renamed, or a DIY board using other names). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            uuid("23BA7924-0000-1000-7450-346EAC492E92"),
            bleGlob("Omi"),
            bleGlob("Omi-*"),
            bleName("OpenGlass"),
            bleGlob("OpenGlass*"),
        ),
    )

    private fun friendPendant() = Fleet(
        id = "fleet-friend-pendant",
        name = "Friend Pendant",
        enabled = true,
        colorIndex = Hue.TRACKER,
        kind = SignatureClass.WEARABLE,
        matchAny = true,
        notes = "Friend AI necklace — wearable companion that listens.",
        attentionNote = "Friend Pendant / necklace. BLE service 1a3fd0e7. It listens to conversations. Not proof someone is recording you. A miss is not a clean bill (off, or a different brand). Look with your eyes.",
        builtIn = true,
        rules = listOf(
            uuid("1A3FD0E7-B1F3-AC9E-2E49-B647B2C4F8DA"),
            bleName("Friend Pendant"),
            bleGlob("Friend Pendant*"),
        ),
    )

    private fun retailLedSign() = Fleet(
        id = "fleet-retail-led-sign",
        name = "Retail LED sign",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.SIGNAGE,
        matchAny = true,
        notes = "BLE LED message display. The advertised name is the sign text, not a product name.",
        builtIn = true,
        rules = listOf(
            uuid("56D63956-93E7-11EE-B9D1-0242AC120002"),
        ),
    )

    private fun electronicShelfLabel() = Fleet(
        id = "fleet-esl",
        name = "Electronic shelf label",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.SIGNAGE,
        matchAny = true,
        notes = "Electronic shelf label (store price tag) on the Bluetooth ESL service. Most Hanshow / SES-imagotag tags use a private radio and will not hit this.",
        builtIn = true,
        rules = listOf(
            uuid("1857"),
        ),
    )

    private fun unifiProtect() = Fleet(
        id = "fleet-unifi-protect",
        name = "UniFi Protect",
        enabled = true,
        colorIndex = Hue.SURVEILLANCE,
        kind = SignatureClass.SURVEILLANCE,
        matchAny = true,
        notes = "UniFi Protect Instant camera in BLE setup. Not a UniFi Wi-Fi access point.",
        builtIn = true,
        rules = listOf(
            bleGlob("UVC G* Instant"),
            bleName("UVC G3 Instant"),
            bleName("UVC G4 Instant"),
            bleName("UVC G6 Instant"),
        ),
    )

    private fun teslaTstpms() = Fleet(
        id = "fleet-tesla-tstpms",
        name = "Tesla tsTPMS",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Tesla BLE tire sensor. Pattern match, not that car. Phone-as-key stays on the Tesla row.",
        builtIn = true,
        rules = listOf(
            bleName("tsTPMS"),
            bleGlob("tsTPMS*"),
        ),
    )

    private fun radiacode() = Fleet(
        id = "fleet-radiacode",
        name = "RadiaCode",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "RadiaCode handheld radiation detector.",
        builtIn = true,
        rules = listOf(
            uuid("E63215E5-7003-49D8-96B0-B024798FB901"),
            bleName("RadiaCode"),
            bleGlob("RadiaCode*"),
        ),
    )

    private fun lgWebosTv() = Fleet(
        id = "fleet-lg-webos",
        name = "LG webOS TV",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "LG webOS TV. Unnamed LG radios can also hit this row.",
        builtIn = true,
        rules = listOf(
            uuid("FEB9"),
            bleName("webOS TV"),
            bleGlob("[LG] webOS*"),
            bleGlob("webOS TV*"),
        ),
    )

    private fun roku() = Fleet(
        id = "fleet-roku",
        name = "Roku",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Roku streaming stick or Roku TV. Often a hidden Wi-Fi Direct AP for the remote. Not an ISP router.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("DIRECT-roku*"),
                wifiGlob("DIRECT-Roku*"),
                wifiGlob("Roku-*"),
            ),
            ApVendorOuis.ROKU,
        ),
    )

    private fun nespresso() = Fleet(
        id = "fleet-nespresso",
        name = "Nespresso",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Nespresso machine (Vertuo / Barista).",
        builtIn = true,
        rules = listOf(
            mfg(0x0225),
            uuid("06AA1910-F22A-11E3-9DAA-0002A5D5C51B"),
            uuid("65241910-0253-11E7-93AE-92361F002671"),
            uuid("96600100-526E-4676-A11A-AF1EB848165B"),
            bleName("Vertuo"),
            bleGlob("Vertuo*"),
            bleGlob("Venus_*"),
        ),
    )

    private fun epson() = Fleet(
        id = "fleet-epson",
        name = "Epson",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Epson EcoTank or WorkForce printer (Wi-Fi Direct or BLE).",
        builtIn = true,
        rules = listOf(
            wifiGlob("*EPSON-ET-*"),
            wifiGlob("*EPSON-WF-*"),
            bleGlob("EPSON-ET-*"),
            bleGlob("EPSON-WF-*"),
            bleName("EPSON-ET"),
            bleName("EPSON-WF"),
        ),
    )

    private fun shokz() = Fleet(
        id = "fleet-shokz",
        name = "Shokz",
        enabled = true,
        colorIndex = Hue.AUDIO,
        kind = SignatureClass.AUDIO,
        matchAny = true,
        notes = "Shokz bone-conduction headphones (OpenRun / OpenFit). Worn on the head, not a tracker.",
        builtIn = true,
        rules = listOf(
            bleName("Shokz"),
            bleName("OpenRun"),
            bleName("OpenFit"),
            bleGlob("LE-OpenRun*"),
            bleGlob("OpenRun*"),
            bleGlob("OpenFit*"),
        ),
    )

    private fun remoteId() = Fleet(
        id = "fleet-remote-id",
        name = "Remote ID",
        enabled = true,
        colorIndex = Hue.DRONE,
        kind = SignatureClass.DRONE,
        matchAny = true,
        notes = "In-flight drone digital license plate (ASTM / FAA Remote ID). Decoded fields can show ID, position, and operator. Pattern match, not a tail number. Wi-Fi Remote ID often misses on stock Android.",
        builtIn = true,
        decode = CatalogDecodes.remoteId,
        rules = listOf(
            uuid("FFFA"),
        ),
    )

    private fun skydio() = Fleet(
        id = "fleet-skydio",
        name = "Skydio",
        enabled = true,
        colorIndex = Hue.DRONE,
        kind = SignatureClass.DRONE,
        matchAny = true,
        notes = "Skydio drone (common in US public-safety / enterprise). In-flight Remote ID is often Wi-Fi and easy to miss — the Remote ID row is the license plate.",
        builtIn = true,
        rules = listOf(
            name("Skydio"),
            glob("Skydio*"),
            bleGlob("Skydio*"),
            wifiGlob("Skydio*"),
        ),
    )

    private fun autel() = Fleet(
        id = "fleet-autel",
        name = "Autel",
        enabled = true,
        colorIndex = Hue.DRONE,
        kind = SignatureClass.DRONE,
        matchAny = true,
        notes = "Autel drone, controller, or setup Wi-Fi. In-flight digital license plate is the Remote ID row.",
        builtIn = true,
        rules = listOf(
            name("Autel"),
            glob("Autel*"),
            wifiGlob("Autel_*"),
            bleGlob("Autel*"),
        ),
    )

    private fun parrot() = Fleet(
        id = "fleet-parrot",
        name = "Parrot",
        enabled = true,
        colorIndex = Hue.DRONE,
        kind = SignatureClass.DRONE,
        matchAny = true,
        notes = "Parrot ANAFI or Bebop drone. In-flight digital license plate is the Remote ID row. Not a Parrot car kit.",
        builtIn = true,
        rules = listOf(
            name("ANAFI"),
            glob("ANAFI*"),
            name("Bebop"),
            glob("Bebop*"),
            bleGlob("ANAFI*"),
            wifiGlob("ANAFI*"),
            bleGlob("Bebop*"),
            wifiGlob("Bebop*"),
        ),
    )

    private fun hoverAir() = Fleet(
        id = "fleet-hoverair",
        name = "HOVERAir",
        enabled = true,
        colorIndex = Hue.DRONE,
        kind = SignatureClass.DRONE,
        matchAny = true,
        notes = "HOVERAir pocket selfie drone. In-flight digital license plate (PRO / PROMAX) is the Remote ID row.",
        builtIn = true,
        rules = listOf(
            name("HOVERAir"),
            glob("HOVERAir*"),
            wifiGlob("Hover*"),
            wifiGlob("HoverX1*"),
            bleGlob("Hover*"),
            bleGlob("HOVERAir*"),
        ),
    )

    private fun cradlepoint() = Fleet(
        id = "fleet-cradlepoint",
        name = "Cradlepoint",
        enabled = true,
        colorIndex = Hue.LAW,
        kind = SignatureClass.LAW_ENFORCEMENT,
        matchAny = true,
        notes = "Cradlepoint vehicle router (IBR / R-series). Common in police, EMS, utilities, and commercial fleets. Hidden SSIDs still hit on the board vendor.",
        attentionNote = "Ericsson Cradlepoint vehicle router (IBR / R-series). Common in US police and public-safety fleets; also utilities, EMS, and commercial fleet. Hidden or renamed SSIDs still hit on the CradlePoint IEEE OUI. Pattern match, not that agency or that car. Look with your eyes.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiName("Cradlepoint"),
                wifiGlob("Cradlepoint*"),
                wifiGlob("IBR*"),
                wifiGlob("IBR900*"),
                wifiGlob("IBR1100*"),
                wifiGlob("IBR1700*"),
                wifiGlob("IBR600*"),
                wifiGlob("IBR600C*"),
                wifiGlob("IBR650*"),
                wifiGlob("IBR950*"),
                wifiGlob("IBR200*"),
                wifiGlob("R1900*"),
                wifiGlob("R2100*"),
                wifiGlob("R2105*"),
                wifiGlob("R920*"),
            ),
            ApVendorOuis.CRADLEPOINT,
        ),
    )

    private fun airlink() = Fleet(
        id = "fleet-airlink",
        name = "AirLink",
        enabled = true,
        colorIndex = Hue.LAW,
        kind = SignatureClass.LAW_ENFORCEMENT,
        matchAny = true,
        notes = "Sierra Wireless AirLink vehicle / fleet gateway. Common in public-safety and commercial fleets. Hidden SSIDs still hit on the board vendor.",
        attentionNote = "Sierra Wireless AirLink vehicle gateway. Common in US police and public-safety fleets; also commercial fleet. Hidden or renamed SSIDs still hit on the Sierra Wireless IEEE OUI. Pattern match, not that agency or that car. Look with your eyes.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiName("AirLink"),
                wifiGlob("AirLink*"),
                wifiGlob("AIRLINK*"),
            ),
            ApVendorOuis.AIRLINK,
        ),
    )

    private fun compex() = Fleet(
        id = "fleet-compex",
        name = "Compex",
        enabled = true,
        colorIndex = Hue.LAW,
        kind = SignatureClass.LAW_ENFORCEMENT,
        matchAny = true,
        notes = "Compex vehicle or agency Wi-Fi access point. Same boards appear on other Compex radios.",
        attentionNote = "Compex Wi-Fi AP. Some US public-safety agencies use these in vehicles. The same IEEE OUIs appear on other Compex radios. Pattern match, not that agency. Look with your eyes.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("114K-*"),
            ),
            ApVendorOuis.COMPEX,
        ),
    )

    private fun novatelWireless() = Fleet(
        id = "fleet-novatel",
        name = "Novatel Wireless",
        enabled = true,
        colorIndex = Hue.LAW,
        kind = SignatureClass.LAW_ENFORCEMENT,
        matchAny = true,
        notes = "Novatel Wireless / Inseego vehicle radio. The same prefix is also on some consumer MiFi hotspots.",
        attentionNote = "Novatel Wireless / Inseego OUI 28:80:A2. Reported in public-safety vehicle AP work. Same prefix is also on some Inseego consumer MiFi radios. Pattern match, not that agency. Look with your eyes.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.NOVATEL),
    )

    private fun utilityInc() = Fleet(
        id = "fleet-utility-inc",
        name = "Utility Inc",
        enabled = true,
        colorIndex = Hue.LAW,
        kind = SignatureClass.LAW_ENFORCEMENT,
        matchAny = true,
        notes = "Utility, Inc vehicle or public-safety access point.",
        attentionNote = "Utility, Inc radio. Reported in public-safety vehicle AP work. Pattern match, not that agency. Look with your eyes.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.UTILITY_INC),
    )

    private fun cisco() = Fleet(
        id = "fleet-cisco",
        name = "Cisco",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Cisco Aironet / Catalyst / Business access point. Fieldwatch sees AP beacons, not phones or switches. Meraki has its own row.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Cisco*"),
                wifiGlob("tsunami"),
            ),
            ApVendorOuis.CISCO + ApVendorOuis.CISCO_SPVTG,
        ),
    )

    private fun aruba() = Fleet(
        id = "fleet-aruba",
        name = "Aruba",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "HPE Aruba campus or Instant On Wi-Fi access point. An HPE BSSID here is an AP, not a server. Not an HP printer.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Aruba*"),
                wifiGlob("SetMeUp*"),
                wifiGlob("InstantOn*"),
            ),
            ApVendorOuis.HPE,
        ),
    )

    private fun ruckus() = Fleet(
        id = "fleet-ruckus",
        name = "Ruckus",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "RUCKUS Unleashed / ZoneFlex campus access point. Not the Arris cable-modem family.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Ruckus*"),
                wifiGlob("Configure.Me*"),
                wifiGlob("Config.Me*"),
            ),
            ApVendorOuis.RUCKUS,
        ),
    )

    private fun ruijie() = Fleet(
        id = "fleet-ruijie",
        name = "Ruijie",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Ruijie / Reyee campus or SMB access point.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("@Reyee*"),
                wifiGlob("Reyee*"),
                wifiGlob("Ruijie*"),
            ),
            ApVendorOuis.RUIJIE,
        ),
    )

    private fun fortinet() = Fleet(
        id = "fleet-fortinet",
        name = "Fortinet",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Fortinet FortiAP / FortiWiFi campus or branch access point.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Fortinet*"),
                wifiGlob("FortiAP*"),
                wifiGlob("FAP-config*"),
            ),
            ApVendorOuis.FORTINET,
        ),
    )

    private fun mikrotik() = Fleet(
        id = "fleet-mikrotik",
        name = "MikroTik",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "MikroTik RouterOS access point or travel router.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("MikroTik*"),
            ),
            ApVendorOuis.MIKROTIK,
        ),
    )

    private fun engenius() = Fleet(
        id = "fleet-engenius",
        name = "EnGenius",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "EnGenius Cloud / ECW access point.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("EnGenius*"),
                wifiGlob("EnMGMT*"),
            ),
            ApVendorOuis.ENGENIUS,
        ),
    )

    private fun zyxel() = Fleet(
        id = "fleet-zyxel",
        name = "Zyxel",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Zyxel home or SMB gateway / access point.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Zyxel*"),
            ),
            ApVendorOuis.ZYXEL,
        ),
    )

    private fun peplink() = Fleet(
        id = "fleet-peplink",
        name = "Peplink",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Peplink / Pepwave travel or branch router.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Peplink*"),
                wifiGlob("Pepwave*"),
            ),
            ApVendorOuis.PEPLINK,
        ),
    )

    private fun openwrt() = Fleet(
        id = "fleet-openwrt",
        name = "OpenWrt",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "OpenWrt factory SSID on a flashed travel / DIY router that was never renamed.",
        builtIn = true,
        rules = listOf(
            wifiGlob("OpenWrt*"),
        ),
    )

    private fun arris() = Fleet(
        id = "fleet-arris",
        name = "Arris",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Arris / SURFboard cable gateway. After the ISP renames the Wi-Fi, the board vendor still hits.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Arris*"),
                wifiGlob("SURFboard*"),
            ),
            ApVendorOuis.ARRIS + ApVendorOuis.COMMSCOPE,
        ),
    )

    private fun mist() = Fleet(
        id = "fleet-mist",
        name = "Mist",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Juniper Mist campus access point. Cloud SSIDs are site names; the board vendor is the hit.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.MIST),
    )

    private fun tMobile() = Fleet(
        id = "fleet-tmobile",
        name = "T-Mobile",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "T-Mobile Home Internet or hotspot. Gateways are often HUMAX / Arcadyan / Askey OEM.",
        builtIn = true,
        rules = listOf(
            wifiGlob("TMOBILE*"),
            wifiGlob("T-Mobile*"),
        ),
    )

    private fun humax() = Fleet(
        id = "fleet-humax",
        name = "HUMAX",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "HUMAX 5G or cable gateway. Often T-Mobile Home Internet in the field.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.HUMAX),
    )

    private fun sagemcom() = Fleet(
        id = "fleet-sagemcom",
        name = "Sagemcom",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Sagemcom ISP gateway. Common Comcast / other cable OEM.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.SAGEMCOM),
    )

    private fun arcadyan() = Fleet(
        id = "fleet-arcadyan",
        name = "Arcadyan",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Arcadyan ISP gateway or mesh. Common Verizon / T-Mobile OEM.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.ARCADYAN),
    )

    private fun askey() = Fleet(
        id = "fleet-askey",
        name = "Askey",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Askey 5G / ISP gateway. Common T-Mobile Home Internet OEM.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.ASKEY),
    )

    private fun calix() = Fleet(
        id = "fleet-calix",
        name = "Calix",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Calix fiber gateway (GigaSpire class). After rename, the board vendor still hits.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.CALIX),
    )

    private fun nokiaNsn() = Fleet(
        id = "fleet-nokia",
        name = "Nokia",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Nokia ISP gateway or small cell. Not a Nokia phone.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.NOKIA_NSN),
    )

    private fun airties() = Fleet(
        id = "fleet-airties",
        name = "AirTies",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "AirTies ISP mesh extender. Carrier-issued home mesh.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.AIRTIES),
    )

    private fun tenda() = Fleet(
        id = "fleet-tenda",
        name = "Tenda",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Tenda consumer router or extender.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Tenda*"),
                wifiName("Tenda"),
            ),
            ApVendorOuis.TENDA,
        ),
    )

    private fun wavlink() = Fleet(
        id = "fleet-wavlink",
        name = "WAVLINK",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "WAVLINK consumer travel / home router.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("WAVLINK*"),
                wifiGlob("Wavlink*"),
            ),
            ApVendorOuis.WINSTARS,
        ),
    )

    private fun sercomm() = Fleet(
        id = "fleet-sercomm",
        name = "Sercomm",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Sercomm ISP gateway. Common cable / fiber OEM.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.SERCOMM),
    )

    private fun luxul() = Fleet(
        id = "fleet-luxul",
        name = "Luxul",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Luxul / Legrand small-business access point.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.LUXUL),
    )

    private fun sophos() = Fleet(
        id = "fleet-sophos",
        name = "Sophos",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Sophos firewall or access point. Campus / SMB Wi-Fi, not a camera pole.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.SOPHOS),
    )

    private fun aumovio() = Fleet(
        id = "fleet-aumovio",
        name = "AUMOVIO",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Vehicle Wi-Fi from an AUMOVIO (ex-Continental) module. The car’s AP, not an ISP router.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.AUMOVIO),
    )

    private fun centuryLink() = Fleet(
        id = "fleet-centurylink",
        name = "CenturyLink",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "CenturyLink factory gateway name. Renamed fiber Wi-Fi misses this row.",
        builtIn = true,
        rules = listOf(
            wifiGlob("CenturyLink*"),
        ),
    )

    private fun gmHotspot() = Fleet(
        id = "fleet-gm-hotspot",
        name = "GM hotspot",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "GM in-car hotspot (Cadillac, GMC, Buick). myChevrolet has its own row. BSSID is often randomized.",
        builtIn = true,
        rules = listOf(
            mfg(0x0068),
            wifiGlob("myCadillac*"),
            wifiGlob("myGMC*"),
            wifiGlob("myBuick*"),
            wifiGlob("CADILLAC*"),
            wifiGlob("BUICK*"),
            wifiGlob("CHEVROLET*"),
        ),
    )

    private fun audiMmi() = Fleet(
        id = "fleet-audi-mmi",
        name = "Audi MMI",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Audi in-car MMI hotspot. Factory name from the car, not a dealer.",
        builtIn = true,
        rules = listOf(
            mfg(0x010E),
            bleName("Audi"),
            wifiGlob("Audi_MMI_*"),
            wifiGlob("Audi MMI*"),
        ),
    )

    private fun extremeNetworks() = Fleet(
        id = "fleet-extreme",
        name = "Extreme",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Extreme Networks campus access point. Cloud SSIDs are site names; the board vendor is the hit.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.EXTREME),
    )

    private fun adtran() = Fleet(
        id = "fleet-adtran",
        name = "Adtran",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Adtran fiber gateway. Common CenturyLink / Lumen / Quantum Fiber OEM.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Adtran*"),
            ),
            ApVendorOuis.ADTRAN,
        ),
    )

    private fun cambium() = Fleet(
        id = "fleet-cambium",
        name = "Cambium",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Cambium or IgniteNet outdoor / WISP access point.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Cambium*"),
                wifiGlob("cnPilot*"),
                wifiGlob("IgniteNet*"),
            ),
            ApVendorOuis.CAMBIUM,
        ),
    )

    private fun trendnet() = Fleet(
        id = "fleet-trendnet",
        name = "TRENDnet",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "TRENDnet consumer access point.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("TRENDnet*"),
            ),
            ApVendorOuis.TRENDNET,
        ),
    )

    private fun cudy() = Fleet(
        id = "fleet-cudy",
        name = "Cudy",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Cudy travel or home router.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Cudy*"),
            ),
            ApVendorOuis.CUDY,
        ),
    )

    private fun snapAv() = Fleet(
        id = "fleet-snapav",
        name = "SnapAV",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.HOME,
        matchAny = true,
        notes = "Control4 / Wattbox home-AV processor or power unit.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Control4*"),
                wifiGlob("Wattbox*"),
                wifiGlob("WattBox*"),
            ),
            ApVendorOuis.SNAPAV,
        ),
    )

    private fun vantiva() = Fleet(
        id = "fleet-vantiva",
        name = "Vantiva",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Vantiva (ex-Technicolor) ISP gateway. After the ISP renames the Wi-Fi, the board vendor still hits.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Technicolor*"),
                wifiGlob("THOMSON*"),
                wifiGlob("Vantiva*"),
            ),
            ApVendorOuis.VANTIVA,
        ),
    )

    private fun hitron() = Fleet(
        id = "fleet-hitron",
        name = "Hitron",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Hitron cable gateway. Common Xfinity OEM; the Xfinity name row can also hit.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Hitron*"),
            ),
            ApVendorOuis.HITRON,
        ),
    )

    private fun actiontec() = Fleet(
        id = "fleet-actiontec",
        name = "Actiontec",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Actiontec FiOS / Frontier gateway. Often a Verizon box; the Verizon name row can also hit.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Actiontec*"),
            ),
            ApVendorOuis.ACTIONTEC,
        ),
    )

    private fun buffalo() = Fleet(
        id = "fleet-buffalo",
        name = "Buffalo",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Buffalo AirStation home router.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Buffalo*"),
                wifiGlob("AirStation*"),
            ),
            ApVendorOuis.BUFFALO,
        ),
    )

    private fun grandstream() = Fleet(
        id = "fleet-grandstream",
        name = "Grandstream",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Grandstream GWN office access point.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Grandstream*"),
                wifiGlob("GWN*"),
            ),
            ApVendorOuis.GRANDSTREAM,
        ),
    )

    private fun edgecore() = Fleet(
        id = "fleet-edgecore",
        name = "Edgecore",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Edgecore campus / open Wi-Fi access point. Cloud SSIDs are site names; the board vendor is the hit.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.EDGECORE),
    )

    private fun watchGuard() = Fleet(
        id = "fleet-watchguard-ap",
        name = "WatchGuard AP",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "WatchGuard firewall or access point. Not WatchGuard Video body-worn cameras.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.WATCHGUARD),
    )

    private fun mojo() = Fleet(
        id = "fleet-mojo",
        name = "Mojo",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Mojo / Arista Cognitive Wi-Fi campus access point.",
        builtIn = true,
        rules = withWifiOuis(emptyList(), ApVendorOuis.MOJO),
    )

    private fun winegard() = Fleet(
        id = "fleet-winegard",
        name = "Winegard",
        enabled = true,
        colorIndex = Hue.VEHICLE,
        kind = SignatureClass.VEHICLE,
        matchAny = true,
        notes = "Winegard RV or marine Wi-Fi antenna / router.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Winegard*"),
            ),
            ApVendorOuis.WINEGARD,
        ),
    )

    private fun inseego() = Fleet(
        id = "fleet-inseego",
        name = "Inseego",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Inseego 5G / MiFi hotspot.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Inseego*"),
            ),
            ApVendorOuis.INSEEGO,
        ),
    )

    private fun franklin() = Fleet(
        id = "fleet-franklin",
        name = "Franklin",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Franklin 5G / LTE home-internet gateway (often carrier-issued). Guest SSIDs still hit.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("RG3100*"),
            ),
            ApVendorOuis.FRANKLIN,
        ),
    )

    private fun synology() = Fleet(
        id = "fleet-synology",
        name = "Synology",
        enabled = true,
        colorIndex = Hue.HOME_CAM,
        kind = SignatureClass.ISP,
        matchAny = true,
        notes = "Synology NAS or router access point.",
        builtIn = true,
        rules = withWifiOuis(
            listOf(
                wifiGlob("Synology*"),
            ),
            ApVendorOuis.SYNOLOGY,
        ),
    )

    private fun honeywellXenonHc() = Fleet(
        id = "fleet-honeywell-xenon-hc",
        name = "Honeywell Xenon HC",
        enabled = true,
        colorIndex = Hue.HEALTH,
        kind = SignatureClass.HEALTH,
        matchAny = true,
        notes = "Honeywell Xenon healthcare barcode scanner or its charge base (white, disinfectant-ready). Clinic / hospital kit, not a home thermostat and not a warehouse-only Xenon.",
        builtIn = true,
        rules = listOf(
            bleGlob("Xenon_*HC*"),
            bleGlob("Xenon_CCB-U00-H*"),
            bleName("CCB-U00-HC"),
            bleGlob("1962h*"),
            bleGlob("1952h*"),
            bleGlob("1902h*"),
        ),
    )

    private fun omronHealthcare() = Fleet(
        id = "fleet-omron",
        name = "Omron",
        enabled = true,
        colorIndex = Hue.HEALTH,
        kind = SignatureClass.HEALTH,
        matchAny = true,
        notes = "Omron blood-pressure cuff or body-composition scale. Home / clinic health kit, not industrial Omron.",
        builtIn = true,
        rules = listOf(
            mfg(0x020E),
            bleName("OMRON"),
            bleGlob("OMRON*"),
            bleGlob("HEM-*"),
            bleGlob("BLESmart_*"),
        ),
    )

    private fun withings() = Fleet(
        id = "fleet-withings",
        name = "Withings",
        enabled = true,
        colorIndex = Hue.HEALTH,
        kind = SignatureClass.HEALTH,
        matchAny = true,
        notes = "Withings (Nokia Health) scale or BPM Connect cuff. Not a Nokia phone and not a Nokia ISP gateway.",
        builtIn = true,
        rules = listOf(
            bleName("Withings"),
            bleGlob("Withings*"),
            bleGlob("WBS0*"),
            bleName("BPM Connect"),
        ),
    )

    private fun dexcom() = Fleet(
        id = "fleet-dexcom",
        name = "Dexcom",
        enabled = true,
        colorIndex = Hue.HEALTH,
        kind = SignatureClass.HEALTH,
        matchAny = true,
        notes = "Dexcom continuous glucose monitor (G6 / G7). Pattern match, not a patient.",
        builtIn = true,
        rules = listOf(
            bleName("Dexcom"),
            bleGlob("Dexcom*"),
        ),
    )

    private fun oui(prefix: String) = MatchRule(RuleKind.OUI, text = prefix)
    private fun vendorIe(prefix: String) = MatchRule(RuleKind.VENDOR_IE_OUI, text = prefix)
    private fun name(text: String) = MatchRule(RuleKind.NAME_CONTAINS, text = text)
    private fun glob(pattern: String) = MatchRule(RuleKind.NAME_GLOB, text = pattern)
    private fun bleName(text: String) =
        MatchRule(RuleKind.NAME_CONTAINS, text = text, radio = RadioKind.BLE)
    private fun bleGlob(pattern: String) =
        MatchRule(RuleKind.NAME_GLOB, text = pattern, radio = RadioKind.BLE)
    private fun wifiName(text: String) =
        MatchRule(RuleKind.NAME_CONTAINS, text = text, radio = RadioKind.WIFI)
    private fun wifiGlob(pattern: String) =
        MatchRule(RuleKind.NAME_GLOB, text = pattern, radio = RadioKind.WIFI)
    private fun wifiOui(prefix: String) =
        MatchRule(RuleKind.OUI, text = prefix, radio = RadioKind.WIFI)
    private fun withWifiOuis(base: List<MatchRule>, ouis: List<String>) =
        base + ouis.map { wifiOui(it) }
    private fun uuid(short: String) = MatchRule(RuleKind.SERVICE_UUID, text = short)
    private fun mfg(id: Int) = MatchRule(RuleKind.MANUFACTURER_ID, companyId = id)
    private fun mfgData(id: Int, prefix: String) =
        MatchRule(RuleKind.MANUFACTURER_DATA, companyId = id, dataPrefixHex = prefix)

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
