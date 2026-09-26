# What's new

Newest first. This is Fieldwatch (`app.fieldwatch`). Each build below is what Settings shows as the version.

Fieldwatch continues the Spectre 1.2.14 field build under a new name, application id, and the MIT License. It does not replace Spectre on a phone.

## 1.1.9 — 25 September 2026

- Path: stays as thick green on the line, time ticks, header stop / entire-route counts, RSSI min–max on Present for the entire route. Each Extra attention / Named radio plots once at strongest RSSI. Entire-route uses first/last heard vs the sit window (not the 40-sample GPS trail).
- Settings → Online place names and maps (default on) also loads OSM tiles under Path. Tiles fill the plot box then clip, with extra map around the route. Offline, no tiles, or Privacy mode: north-up trace only, no error. User manual updated.

## 1.1.8 — 25 September 2026

- Reports → Sit export: its own card under Sit report, same Format and radios chips as Log export (CSV, JSON lines, GPX, KML, WiGLE). One row per unique radio in the selected sit (or last 15 minutes), not the rotating log. GPX / KML include this phone’s path as a track plus hear-points. Privacy mode does not mask the file. The log card is titled Log export. User manual §5.6.2 / §11.6 spells sit vs log.
- Path legend: Line = this phone on its own row; Blue = Named in cyan (same as Named dots).

## 1.1.7 — 25 September 2026

- Named radios: Observer notes (up to 280 characters) on the same KIND+MAC as the custom name. Cyan block on detail under the name; a saved custom name is the large title, advertised name smaller. Edit on detail or Settings → Named radios. Saving notes without a name still creates the Named-radio row (suggested label, Alert off). Live list shows a cyan notes chip next to Extra attention “!”. Debrief lists Observer notes after Where you were; Compare after Windows. Path and AI Export list heard radios and the note. Extra attention stays gold. BLE privacy addresses still hide the pencil. Settings backup includes the note.
- Debrief / Compare PDF: stay/transit lines, Channel occupancy / Loudest APs and other “Label:” kickers are bold; bullets and Path key numbers are structured.
- Reports use the custom name from Named radios (not the advertised SSID/LE name) in Debrief, Compare, Path, AI Export, and GPX/KML. WiGLE CSV still writes the advertised SSID.
- Reports → Path: north-up plot of this sit (or last 15 minutes). Operator GPS track, scale bar, Extra attention / Named dots. Stacked counts tap for one inset. No map tiles. Hear-points, not radio fixes. Debrief PDF and Compare PDF include a letter-size operator-path figure (compare overlays both walks).
- Reports → Log: Format (Log file — CSV, Log file — JSON lines, GPX — GPS Exchange, KML — Google Earth, WiGLE CSV — wigle.net) and radios (Both / Wi-Fi only / BLE only). Rotating file is JSON lines. CSV / maps are Share/Save projections. Hear-point pins are this phone. Fieldwatch does not upload. Settings CSV/JSON chips removed.
- User manual rewritten for Path, Compare, Log export, Observer notes, custom names, and the Live notes chip. Screenshots recaptured in Privacy mode.

## 1.1.6 — 24 September 2026

- Decode field numbers and radar zoom use `Locale.US`, so French/German phones keep a period (`26.48 °C`, `×1.5`). Parser unit tests and GitHub Actions (`testDebugUnitTest` + debug APK) on push/PR.
- Reports → Compare sits: this sit (open, selected, or last 15 minutes) vs a second saved sit. Compare (text) and Compare (PDF) — same letter layout as Debrief. Compare AI Export is an addendum (overlap, exclusive Extra attention / Named radios), not a rewrite of the lists. Presence: only in this sit, only in the second, in both. Kind + MAC. Extra attention and Named radios marked. Privacy mode on the share text.
- Sit-report AI Export is the same addendum shape: onboard Debrief verbatim, then 5/15-minute rates, RSSI bands, Extra attention and finder-tag IDs for a tracking stress-test — not a second Wi-Fi/BLE roster.

## 1.1.5 — 24 September 2026

- Detail “What this looks like” uses a matched catalog family instead of a generic SSID guess. A `DIRECT-rR-Raven-*` AP is a Raven / ShotSpotter sensor, not a phone or TV on Wi-Fi Direct.
- Removed the stock **Unknown Signature** catch-all (`ESP_*`, `ANDROID-`, `DIRECT-`, `UNIT-`). Those names were not a product family and dual-labeled real rows (Raven, Roku, Epson). Generic `DIRECT-` SSIDs stay unmatched; the guess can still say Wi-Fi Direct.
- Custom name on detail is always available for Wi-Fi, including locally administered BSSIDs (vehicle / mesh / guest APs). BLE privacy addresses still hide the pencil. Identity copy no longer calls a Wi-Fi local-bit BSSID a rotating privacy MAC.
- Stock Extra attention: Digital Ally body/in-car (IEEE 00:23:BD); Limitless, Bee, Omi, and Friend wearable recorders (unique BLE services / names); Brilliant Frame and Even G1 glasses; Reveal Media and Wolfcom bodycams; Panasonic i-PRO / Arbitrator; Hayden AI, Miovision, Tattile, and LVT LiveView (name-only — cellular units stay quiet).
- Stock filter chips: All traffic, Wi-Fi only, BLE only, Strong signal, Moving with you, Watched only. Trackers / Hide trackers / Hide phones left the stock set (class chips + Save current as… still make those sits). Existing custom chips that duplicate a stock name or filter are folded on upgrade.
- User manual Chapter 14 Technical specifications / How it works (Fig. 20).

## 1.1.4 — 21 September 2026

- Slightly smaller switches. Outlined fields and dropdowns share the same tight inner padding (Display, signature editor, Decode, Filters, Settings TAK, sit/name dialogs). Rule Kind/Value and Manufacturer data fields no longer overlap.
- Opening Display dims Live and blocks taps on radios behind it. Tap the dim area to close.

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
