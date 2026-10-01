# Fieldwatch Wear OS Module (`:wear`)

This directory contains the standalone and companion Wear OS application for Fieldwatch, optimized for circular smartwatches (e.g. Google Pixel Watch, Samsung Galaxy Watch) running Wear OS 3.0+ (API 30–35).

---

## Highlights

- **Classic CRT Radar (`ui/screens/ClassicRadarScreen.kt`)**: 360° rotating radar sweep beam with 22-step radial phosphor decay, concentric dBm range rings, color-coded RF contacts (AirTags, SmartTags, Wi-Fi, BLE), interactive targeting reticle, and physical rotary crown zoom (1.0× to 3.5×).
- **Tactile Geiger Counter (`ui/screens/WristHuntScreen.kt`)**: Silent, covert direction finding using dynamic wrist vibration haptics that accelerate as you approach a transmitter.
- **Wear OS Tile (`tile/FieldwatchTileService.kt`)**: Glanceable watch carousel tile showing real-time RF density and active alerts.
- **Dual-Mode Engine**: Operates as a phone companion via Google Play Services Wearable Data Layer (`service/WearDataReceiverService.kt`), or as an untethered standalone burst scanner (`radio/WatchBleScanner.kt`).
- **100% Offline & Private**: Zero internet permissions (`android.permission.INTERNET` is not declared), zero analytics, zero external network requests.

---

## Full Documentation

For complete architecture details, message contracts, hardware compatibility, and tactical operations guide, see:
- [Fieldwatch for Wear OS Guide](../docs/WEAR_OS.md)

---

## Building & Sideloading

```bash
# Build the Wear OS APK from root repository
./gradlew :wear:assembleDebug

# Sideload to connected Wear OS watch
adb -s <watch-ip-or-serial> install -r wear/build/outputs/apk/debug/wear-debug.apk

# Grant permissions
adb -s <watch-ip-or-serial> shell pm grant app.fieldwatch.wear android.permission.BLUETOOTH_SCAN
adb -s <watch-ip-or-serial> shell pm grant app.fieldwatch.wear android.permission.BLUETOOTH_CONNECT
adb -s <watch-ip-or-serial> shell pm grant app.fieldwatch.wear android.permission.ACCESS_FINE_LOCATION
```
