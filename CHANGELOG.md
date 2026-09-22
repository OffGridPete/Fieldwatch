# What's new

Newest first. This is Fieldwatch (`app.fieldwatch`). Each build below is what Settings shows as the version.

Fieldwatch continues the Spectre 1.2.14 field build under a new name, application id, and the MIT License. It does not replace Spectre on a phone.

## 1.1.3 — 21 September 2026

- Live Tune (Display) overlays the radar/list instead of pushing it down. The panel stays collapsed at launch. In portrait it uses the height above the tab bar; in landscape it scrolls, with a fade and down-chevron when more options sit below.
- Tighter FIELDWATCH header, Filters/Signatures subtitle bar, and bottom tab bar.
- Night mode is under Appearance. Dark theme is no longer a switch — the display is always dark. An upgrade or settings import with Dark theme off is forced on.

## 1.1.2 — 21 September 2026

- Stock signature: BlueTOAD Spectra (Iteris Vantage Velocity / Spectra CV roadside Bluetooth travel-time reader). Surveillance class. Labels on a BlueTOAD / Vantage Velocity / Spectra CV name or Iteris OUI `00:14:7B`. No Extra attention and not a stock bookmark — quiet cabinets and 5.9 GHz C-V2X will not appear.
- Stock signatures: BlipTrack (travel-time, no beep); Hanwha Wisenet, Uniview, Rhombus (cameras, Extra attention); MeshCore, goTenna, SenseCAP, RAK WisGate (mesh, no beep); GhostESP and Bruce (pentest Extra attention, GhostNet / BruceNet only). Existing phones now get the new Extra attention bookmarks (GhostESP, Bruce, Hanwha, Uniview, Rhombus) without Restore.
- Locks class is now labeled Access control. ASSA ABLOY, SALTO, dormakaba, and Paxton move there from Surveillance (door readers, not cameras). Stored class value is still LOCK.
- Settings footer shows Catalog N. Update stock catalog from GitHub replaces stock rows (including Extra attention) from the repo JSON; bookmarks and Settings stay. Needs internet. Offline: Import signatures.

## 1.1.1 — 20 September 2026

- Reports → Sits: a short note on what a sit is, and that Sit report uses the open sit, a selected saved sit, or last 15 minutes.

## 1.1.0 — 19 September 2026

- Named sits. Optional: Reports → Start sit. Debrief and AI Export use that window instead of the last 15 minutes in RAM. Live list, Filters, Hunt, and TAK are unchanged if you never start one.

## 1.0.8 — 19 September 2026

- TAK remarks are a short card when you inspect a marker: callsign, radio kind, MAC, RSSI, heard-here vs advertised vs pilot, signatures, Extra attention. Map label is still the 32-character callsign.

## 1.0.7 — 19 September 2026

- TAK heard-here pins hold the loudest hear (closest approach) instead of following the operator. A weaker hear still refreshes the same lat/lon every ~10 s so ATAK does not drop the marker. Advertised Remote ID / pilot pins still follow the payload. Not direction-finding.

## 1.0.6 — 19 September 2026

- Radar sweep runs off the display refresh so it still turns when Developer options Animator duration scale is off. The trail fades off the beam; contacts brighten when the sweep paints them.
- New installs / Restore: Live display is By class. RSSI bars, Signature names, Frequency, and First / last seen are on. Existing phones keep the view they already chose.

## 1.0.5 — 18 September 2026

- Sideload APK is signed with an Off Grid Pete LLC release certificate, not the Android debug cert. Certificate SHA-256 is in `instruction.txt`. Phones that already have 1.0.4 or earlier must uninstall first; Android will not update over a different signer.

## 1.0.4 — 18 September 2026

- Dropped unused `RECEIVE_BOOT_COMPLETED`. Fieldwatch never started at boot; scanners flagged a permission with no receiver.

## 1.0.3 — 17 September 2026

- TAK / CoT: Remote ID keeps one aircraft marker that moves (sticky UAS ID, not the rotating BLE MAC). Decoded pilot lat/lon is a second pin, linked to the aircraft.
- Heard-here callsigns end in (here); Extra attention uses Maroon, advertised drones Yellow, pilot Orange. Radios that leave are dropped on ATAK instead of sitting ~120 s.
- Settings Feed status shows pins on the feed and sends this tick, plus dest, error, and time. Destination chips: This phone (`127.0.0.1:10011`), LAN multicast (`239.2.3.1:6969`), Custom. UDP only — a TAK server’s TCP 8087 is not this feed.
- New installs / Restore: Voice on watched signature on; What to say is Class + signature. Dark theme, Keep screen on, Jump to new watched detection, and Beep were already on.
- Stock bookmarks include Extra attention (including every Surveillance row that has Extra attention text) plus every built-in Drone-class row (DJI, Remote ID, Skydio, Autel, Parrot, HOVERAir). Existing phones keep their current Settings and watchlist unless you Restore defaults.

## 1.0.2 — 16 September 2026

- Settings backup: Export settings / Save settings / Import settings. Named radios, filter presets, and Settings switches; not the catalog, logs, or GPS. Done and error show an OK dialog.
- Import signatures uses the same OK / error dialogs.
- Bottom tabs cut immediately (no 700 ms fade).

## 1.0.1 — 15 September 2026

- Moving with you is BLE only. Wi-Fi access points stay off (a loud AP you drive past paints your path). Filters shows BLE only while that switch is on.

## 1.0.0 — 15 September 2026

- New app: Fieldwatch (`app.fieldwatch`). Sideload next to Spectre; data does not migrate.
- MIT License for Fieldwatch source. Apache-2.0 libraries and IEEE / Bluetooth SIG lookup tables: see NOTICE.
- Operator-visible name is Fieldwatch (launcher, notification, Debrief, TAK, first-run).
- Signature export uses `fieldwatch-signatures`. Spectre packs (`spectre-signatures`) still import.
- Includes Spectre 1.2.14: Android 12–14 no longer crash on the first BLE advertisement; Android 15 still shows Public / Random from the stack. Sideload APK is not a debug build. BLE scan starts about half a second after Wi-Fi at launch.
