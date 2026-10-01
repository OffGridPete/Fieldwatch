# Fieldwatch for Wear OS

Fieldwatch for Wear OS brings covert, wrist-worn radio frequency (RF) awareness and counter-surveillance to smartwatches. Designed specifically for circular displays (such as Google Pixel Watch and Samsung Galaxy Watch), the watch application transforms your smartwatch into a tactical RF companion and silent tracker locator.

---

## Table of Contents

1. [Key Features](#key-features)
2. [Operating Modes](#operating-modes)
   - [Companion Mode (Phone + Watch)](#companion-mode-phone--watch)
   - [Standalone Mode (Watch Only)](#standalone-mode-watch-only)
3. [Interface & Display Tour](#interface--display-tour)
   - [Classic CRT Radar](#classic-crt-radar)
   - [Tactile Wrist Hunt (Geiger Counter)](#tactile-wrist-hunt-geiger-counter)
   - [Dashboard & Live RF Density](#dashboard--live-rf-density)
   - [Surveillance Alerts Feed](#surveillance-alerts-feed)
   - [Wear OS Quick-Glance Tile](#wear-os-quick-glance-tile)
4. [Architecture & Communication](#architecture--communication)
   - [Data Layer Message Contracts](#data-layer-message-contracts)
   - [Data Flow Diagram](#data-flow-diagram)
5. [Hardware & OS Requirements](#hardware--os-requirements)
6. [Build & Installation Guide](#build--installation-guide)
   - [Compiling from Source](#compiling-from-source)
   - [Deploying to Watch via ADB](#deploying-to-watch-via-adb)
   - [Granting Runtime Permissions](#granting-runtime-permissions)
7. [Tactical Field Operations](#tactical-field-operations)
   - [Torso-Shielding Direction Finding](#torso-shielding-direction-finding)
   - [Covert Proximity Sweeping](#covert-proximity-sweeping)
8. [Battery & Privacy Guarantees](#battery--privacy-guarantees)

---

## Key Features

- **Classic CRT Phosphor Radar**: Authentic circular 360° rotating sweep beam with 22-step radial phosphor decay, multi-tier dBm range rings, color-coded RF contacts, and interactive target lock reticle.
- **Physical Rotary Crown Zoom**: Spin the watch crown to dynamically zoom the radar sweep disc from 1.0× to 3.5× magnification with crisp haptic detents.
- **Silent Tactile "Geiger Counter" (Wrist Hunt)**: Track down unwanted Apple AirTags, Samsung SmartTags, Tile beacons, and covert surveillance tags without looking at a screen. Haptic vibration pulses accelerate dynamically as signal loudness increases.
- **Dual-Mode Operation**: Seamlessly pairs with the Fieldwatch Android phone app for heavy signature processing, or functions as a standalone 10-second burst BLE scanner when untethered.
- **Glanceable Wear OS Tile**: Add the Fieldwatch RF tile to your watch face carousel to monitor nearby RF threat density instantly.
- **100% Offline & Private**: Zero internet permissions (`android.permission.INTERNET` is completely absent from the wear manifest). No accounts, no cloud dependencies, no analytics, no external data exfiltration.

---

## Operating Modes

### Companion Mode (Phone + Watch)

In Companion Mode, your Android phone acts as the primary scanning engine and signature database:
- The phone runs continuous high-gain background sweeps across 2.4 GHz / 5 GHz Wi-Fi access points and Bluetooth Low Energy advertisements.
- The phone matches detections against Fieldwatch's 88+ signature database (identifying Flock Safety ALPR, Axon body cameras, drones, smart glasses, pentest kits, and tracking tags).
- Processed RF summaries, high-priority watchlist alerts, and live signal strength metrics are pushed to the watch via Google Play Services Wearable Data Layer API.
- The watch can bidirectionally trigger the phone to initiate target hunting on a specific MAC address.

### Standalone Mode (Watch Only)

When disconnected from the phone (e.g., during athletic activities or covert operations without a phone):
- The watch utilizes its internal Bluetooth controller via `WatchBleScanner`.
- Executes battery-safe 10-second burst scans using low-latency BLE filter callbacks.
- Inspects BLE advertisement manufacturer data and service UUIDs to detect nearby Apple Find My devices, Samsung SmartTags, and Google Fast Pair transmitters.
- Automatically throttles between bursts to conserve the smartwatch battery.

---

## Interface & Display Tour

### Classic CRT Radar

The Classic Radar view (`ClassicRadarScreen.kt`) renders a tactical radar scope built directly with Jetpack Compose Canvas:

- **Phosphor Sweep Beam**: A bright CRT-green radial beam continuously rotates clockwise (3.2 seconds per 360° revolution).
- **Radial Phosphor Decay**: A 22-slice graduated alpha fan trails behind the beam, mimicking authentic P7 radar phosphor persistence.
- **Concentric dBm Range Rings**:
  - `-40 dBm`: Center ring (immediate proximity / touching).
  - `-60 dBm`: Mid-close ring (1–3 meters line of sight).
  - `-80 dBm`: Outer ring (nominal detection boundary).
  - `-100 dBm`: Perimeter edge (fringe reception).
- **Color-Coded Contacts**:
  - **Glowing Crimson Dot**: Watched target, surveillance equipment, or tracker tag (AirTag, SmartTag, Tile, Drone, ALPR).
  - **Cyan Dot**: Wi-Fi Access Point / Router.
  - **Phosphor Green Dot**: Bluetooth Low Energy peripheral / unnamed beacon.
- **Interactive Reticle**: Tap any blip on the radar canvas to snap a yellow targeting reticle onto the target and display a bottom HUD showing the device's advertised name, MAC address, and current RSSI, with a one-tap **Hunt** action.
- **Rotary Crown Zooming**: Rotate the physical Pixel Watch crown to smoothly scale radar view between 1.0× and 3.5× with tactile haptic clicks at each 0.25× step.

### Tactile Wrist Hunt (Geiger Counter)

The Wrist Hunt screen (`WristHuntScreen.kt`) enables eyes-free acoustic and tactile tracking:

- **Haptic Pacing**:
  - Signal weak (≤ -95 dBm): Long delay (1,200 ms between pulses).
  - Signal moderate (-75 dBm): Moderate pacing (400 ms between pulses).
  - Signal loud (≥ -45 dBm): Rapid Geiger-counter clicking (75 ms intervals) with maximum haptic intensity.
- **Visual Loudness Arc**: A sweeping circular meter fills the edge of the watch face from dim amber to neon emerald.
- **Silent Operations**: Allows you to walk towards a hidden transmitter with your hands at your sides or resting naturally, reading signal strength entirely through vibration against your wrist bone.

### Dashboard & Live RF Density

The Dashboard screen (`DashboardScreen.kt`) provides an instant high-level overview:
- Total active Wi-Fi access points in range.
- Total BLE beacons observed in the current window.
- Number of active surveillance or tracker alerts.
- Dedicated quick-launch buttons to enter **Classic Radar**, **Wrist Hunt**, or **Alerts**.

### Surveillance Alerts Feed

The Alerts screen (`AlertsScreen.kt`) displays a chronological list of flagged contacts:
- Highlights bookmarked devices, body-worn cameras, smart glasses, or tracker tags.
- Displays device name, MAC address, signal strength, and last-seen timestamp.
- Tapping any alert instantly locks that transmitter into the radar reticle or launches tactile Hunt mode.

### Wear OS Quick-Glance Tile

The Wear OS Tile (`FieldwatchTileService.kt`) is integrated directly into the system carousel:
- Accessible with a single swipe from your watch face.
- Shows total RF count (Wi-Fi + BLE) and any active alerts.
- Tapping the tile immediately launches Fieldwatch into the Radar view.

---

## Architecture & Communication

The Wear OS integration is built across two complementary modules:
- `:app`: The primary Android smartphone application.
- `:wear`: The standalone/companion Wear OS application.

### Data Layer Message Contracts

All communications between the phone and watch use protocol-buffers-free JSON payloads sent across Google Play Services `MessageClient`:

| Channel Path | Direction | Payload Contract | Description |
|---|---|---|---|
| `/fieldwatch/rf_summary` | Phone → Watch | `RfSummaryPayload` | Periodic summary (Wi-Fi count, BLE count, alerts count, active hunt MAC, and list of top contacts with polar coordinates). |
| `/fieldwatch/alerts` | Phone → Watch | `List<WearAlertPayload>` | Instant push notification when a watchlist device or tracker is detected. |
| `/fieldwatch/hunt_update` | Phone → Watch | `HuntUpdatePayload` | High-frequency RSSI stream for the active target during Hunt mode. |
| `/fieldwatch/hunt_control` | Watch → Phone | String (Target MAC) | Watch commands phone to engage or disengage tracking on a specified device. |

### Data Flow Diagram

```text
+--------------------------------------------------------------+
|                    SAMSUNG / ANDROID PHONE                   |
|                                                              |
|   +-------------------+              +-------------------+   |
|   |  Wi-Fi & BLE      |              |  88+ Signatures   |   |
|   |  Hardware Scanners|              |  Matching Engine  |   |
|   +---------+---------+              +---------+---------+   |
|             |                                  |             |
|             +-----------------+----------------+             |
|                               |                              |
|                               v                              |
|                   +------------------------+                 |
|                   |    PhoneWearBridge     |                 |
|                   +-----------+------------+                 |
+-------------------------------|------------------------------+
                                |  Wearable Data Layer
                                |  (Local Bluetooth / Wi-Fi)
+-------------------------------v------------------------------+
|                     PIXEL WATCH (WEAR OS)                    |
|                                                              |
|                 +----------------------------+               |
|                 |   WearDataReceiverService  |               |
|                 +-------------+--------------+               |
|                               |                              |
|                               v                              |
|                 +----------------------------+               |
|                 |    WearStateRepository     |               |
|                 +-------------+--------------+               |
|                               |                              |
|       +-----------------------+-----------------------+      |
|       |                       |                       |      |
|       v                       v                       v      |
| +-----------------+   +-----------------+   +--------------+ |
| |ClassicRadarView |   | WristHuntScreen |   | Wear OS Tile | |
| | (CRT Sweep &    |   | (Tactile Haptic |   | (Carousel    | |
| |  Rotary Crown)  |   |  Geiger Counter)|   |  Glance)     | |
| +-----------------+   +-----------------+   +--------------+ |
|                                                              |
|             +--------------------------------+               |
|             |   WatchBleScanner (Fallback)   |               |
|             +--------------------------------+               |
+--------------------------------------------------------------+
```

---

## Hardware & OS Requirements

- **Smartwatch Operating System**: Wear OS 3.0 or newer (API level 30 through 35).
- **Supported Form Factors**: Optimized for circular Wear OS displays (384×384 to 456×456+).
- **Tested Hardware**:
  - Google Pixel Watch 3 (456×456 circular AMOLED, Wear OS 5 / Android 14)
  - Samsung Galaxy A53 5G (`SM-A536E`, Android 16)
- **Rotary Crown**: Hardware rotary encoder supported via AndroidX Wear Compose rotary modifiers.

---

## Build & Installation Guide

### Compiling from Source

Both modules build cleanly via the project Gradle wrapper:

```bash
# Clone the repository
git clone https://github.com/toastmanAu/Fieldwatch.git
cd Fieldwatch

# Build both phone and wear APKs
./gradlew :wear:assembleDebug :app:assembleDebug
```

Generated APKs:
- Phone companion: `app/build/outputs/apk/debug/app-debug.apk`
- Wear OS app: `wear/build/outputs/apk/debug/wear-debug.apk`

### Deploying to Watch via ADB

1. **Enable Developer Options on the Watch**:
   - On the watch, go to **Settings → System → About → Versions**.
   - Tap **Build number** 7 times until you see *"You are now a developer!"*.
2. **Enable ADB & Wireless Debugging**:
   - Go to **Settings → Developer options**.
   - Enable **ADB debugging** and **Wireless debugging**.
3. **Connect from Workstation**:
   ```bash
   # Pair with watch (if using Wireless Debugging)
   adb pair <watch-ip>:<pairing-port>

   # Connect to watch
   adb connect <watch-ip>:<debugging-port>
   ```
4. **Sideload the Wear APK**:
   ```bash
   adb -s <watch-ip-or-serial> install -r wear/build/outputs/apk/debug/wear-debug.apk
   ```

### Granting Runtime Permissions

Grant the necessary Bluetooth and Location permissions via ADB or through the on-watch prompt:

```bash
adb -s <watch-ip-or-serial> shell pm grant app.fieldwatch.wear android.permission.BLUETOOTH_SCAN
adb -s <watch-ip-or-serial> shell pm grant app.fieldwatch.wear android.permission.BLUETOOTH_CONNECT
adb -s <watch-ip-or-serial> shell pm grant app.fieldwatch.wear android.permission.ACCESS_FINE_LOCATION
```

---

## Tactical Field Operations

### Torso-Shielding Direction Finding

Standard Bluetooth Low Energy antennas are omnidirectional and do not provide native angle-of-arrival (AoA) without multi-antenna arrays. However, you can achieve effective directional bearings using **human torso attenuation**:

1. Open **Classic Radar** or **Wrist Hunt** on your watch.
2. Hold your watch wrist against your solar plexus or upper chest so your torso completely shields the rear 180° arc.
3. Turn your body slowly in a 360° circle, pausing every 45 degrees for 2–3 seconds.
4. Note where the signal strength peaks (the human body attenuates 2.4 GHz RF signals by -15 dB to -25 dB).
5. The direction of maximum signal strength indicates the bearing of the transmitter.
6. Walk along that bearing. The wrist haptics will accelerate as you close the distance.

### Covert Proximity Sweeping

In environments where holding up a smartphone is conspicuous or prohibited:
- Keep your phone in your pocket, bag, or vehicle running Fieldwatch in the background.
- Wear your smartwatch naturally.
- As you move through a space, silent wrist vibrations notify you of high-priority surveillance tags or approaching tracking devices.
- A quick glance at the watch face or radar reticle identifies the target without drawing attention.

---

## Battery & Privacy Guarantees

### Zero Telemetry & 100% Offline

- **No Network Permission**: The Wear OS application does not declare `android.permission.INTERNET`. It cannot connect to external servers, download third-party assets, or leak identifiers.
- **Local Data Only**: All communication between phone and watch occurs over local Bluetooth / Wi-Fi links managed securely by Google Play Services Wearable API.
- **No Third-Party Analytics**: Zero SDKs from Firebase, Google Analytics, Sentry, or advertising networks are included.

### Battery Optimization

Smartwatch batteries are compact (typically 300–500 mAh). Fieldwatch preserves battery life through:
- **Low-Duty Burst Scanning**: Standalone mode limits continuous scanning to short 10-second bursts.
- **Companion Offloading**: The watch offloads CPU-intensive regex matching, GPS logging, and signal averaging to the phone.
- **OLED Black Efficiency**: The CRT Radar interface uses a pitch-black (`#000000`) background, turning off unlit OLED pixels across 85%+ of the display surface.
