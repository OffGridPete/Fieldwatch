# What's new

Newest first. This is Fieldwatch (`app.fieldwatch`). Each build below is what Settings shows as the version.

Fieldwatch continues the Spectre 1.2.14 field build under a new name, application id, and the MIT License. It does not replace Spectre on a phone.

## 1.1.19 — 4 October 2026

- Catalog 91: DJI Power matches a Bluetooth name that starts with Power2000. It is a power station, Home IoT, and it is not bookmarked. Osmo cameras stay on the Osmo row. A DJI Power hit drops the DJI drone row. Other DJI radios stay on DJI. Model id 4500 reads Power 2000. The name is what selects the row.
- Atrius cart tag is the name of that iBeacon UUID. The advertisement does not name the store, so the row no longer says Target.
- A name flood counts randomized Bluetooth addresses. A factory address with a stable name stays out of that count. Pairing popups still count every address. A smaller cluster of pairing popups stays quiet.
- The pairing flood dialog says the burst can be many radios already advertising pairing, such as in a store, or one radio changing its address on every packet. A name flood can be tags in a store, or one radio changing its name and address. Continue leaves them on Live. Hide these takes them off Live. The sit and the log still keep them. The advertisement does not name the tool.
- During a sit, Continue or Hide these holds until the sit ends. The first burst still asks. Later bursts keep the red line and do not open the dialog. Hide these also takes later bursts in that sit off Live. Ending the sit asks again. With no sit open, the choice holds for about 15 minutes and does not slide. A later burst in that time keeps the red line. After 15 minutes the next burst asks again. Starting a sit while that hold is running keeps the answer until the sit ends. Pairing and name floods share the answer. A Wi-Fi beacon flood asks once on its own.

## 1.1.18 — 3 October 2026

- Live warns when many new Bluetooth addresses advertise a pairing popup, or many new names, within a few seconds at about the same loudness. One dialog, then a single line until that burst goes quiet. The advertisement does not name the tool.
- A pairing or name flood during a sit is one quiet Flood line on Debrief and Compare: the time, the kind, how many new addresses, and which sit on a compare. Those addresses are left out of the radio counts and lists. The line says how many were set aside. The sit file and the log still keep them. A sit saved before the addresses were stored still counts them with the other radios. The note is saved when the live warning appears.
- Hide this burst, on the Live flood line, takes that burst’s new addresses off the Live display until they would have left on their own. The sit file and the log still keep them. Debrief and Compare leave them out of the radio counts and lists. Alerts stay quiet for those addresses. The next burst starts with the switch off.
- Live warns when many new Wi-Fi names show up in one scan, about the same loudness, and are gone on the next scan. The first scan of a session stays quiet. A repeated name, a mesh, an extender, or a guest network is not counted. One dialog, then a single line, then the same Flood note. Those addresses are left out of the radio counts and lists. The sit file and the log still keep them. The advertisement does not name the tool.
- Pairing flood counts a Swift Pair beacon (beacon id, a pairing sub-scenario, and the reserved byte), so Hide this burst covers those new Microsoft Device addresses. Nearby Sharing still counts from its own advertisement.
- Pairing flood counts a Samsung Easy Setup buds or watch advertisement, so Hide this burst covers those new addresses. They still show as Samsung SmartTags. A tag advertising service FD5A or FD59 is unchanged.
- Pairing flood counts a LoveSpouse advertisement (company 0x00FF and its fixed prefix), so Hide this burst covers those new addresses. They stay unmatched. A shorter 0x00FF blob stays out.
- Catalog 89: Even G1 also matches the Even Realities company id. RayNeo matches only when the name starts with RayNeo and the advertisement uses the TCL company id. LiteOn camera radio adds E0:0A:F6 and 14:B5:CD, still with no Extra attention.
- Catalog 90: Remote ID also labels the French Direct Remote ID vendor IE. Position and pilot still come from the ASTM plate. Tello matches a name that starts with TELLO or RMTT. Potensic, Holy Stone, Hubsan, Yuneec, SwellPro, and Crazyflie match a name that starts with the brand. Parrot also matches Skycontroller. These drone rows are bookmarked and have no Extra attention.
- Remote ID names Freefly, BRINC, and Teal from the serial on the ASTM plate, when the ID type is a serial. The row stays Remote ID. A session ID and the French plate do not get that name.
- Mine marks a radio you can already name. The live list shows a Mine chip. That radio does not beep, speak, flash, vibrate, or raise a card while Mine is on, including when a bookmarked signature matches. Other radios of that signature still alert. Debrief and Compare list it under Marked mine and leave it out of the co-travel callouts. Turning Mine on fills a blank custom name the same way observer notes do.
- Filters → Who stays → Hide my radios takes radios marked Mine off the live list. The sit, the log, and Debrief still include them. Turning on Moving with you leaves this switch on. Live does not show a reminder line for it.
- A Bluetooth company name stays on the company line. It is no longer stored as the IEEE vendor when the MAC prefix is not in the list. An address with the local bit set is not called a factory address.
- Settings → Named radios puts the Wi-Fi or BLE mark on the name line. The custom name and MAC each stay on one line. Mine and Alert sit under the name.
- Debrief and Compare lead with counts. The PDF draws class, channel, and compare bars. A radio is listed when it is Extra attention, has a custom name, is marked Mine, or is bookmarked. Compare also lists a decoded value that changed. Reports → Sit report → Show all radios brings the full rosters back. Text has the same counts and the same lines. Sit export is unchanged.

## 1.1.17 — 1 October 2026

- Privacy mode no longer hides the map on Reports → Path, or on the Debrief and Compare letter path figures. Those tiles follow Online place names and maps. Privacy mode still masks MAC tails and coordinates, omits street names, and pauses the TAK / CoT feed.
- Reports → Path keeps about 400 m of ground on the short side when the GPS path is one fix or a short sit. A longer walk still fills the plot.

## 1.1.16 — 30 September 2026

- Reports → Path plots MAC alerts and signature alerts, each once at the strongest hear, as a class icon. A count is several in one place. The black dot is the start. The blue dot is you, at the last point. The list puts the Wi-Fi or BLE icon next to the MAC. The symbol key under the map is gone, and the alert rows sit closer together. An alert with a decoded latitude and longitude is drawn at the last advertised fix as a class icon. The advertised track on that card is a white dotted line. If that icon shares a spot with other alerts, the count lists them together. The pilot is a person icon, with no word label on the map. A lone class icon has no number box; tap it for that one radio. A count still lists only the radios in that spot. A fix farther than 2 km stays on that aircraft’s map. Other alerts stay at the strongest hear. Debrief and Compare PDF figures draw that track as a black dotted line, with a class icon at the last position and a person icon for the pilot. On a compare, the second sit’s track is a blue dotted line. Those PDF figures plot the same MAC alerts and signature alerts, at the last advertised position when the radio sent one. The Path key under that figure lists an advertised aircraft with the drone class icon, plus its live status, UAS id, last position, motion, and pilot position. A start or end mark that covers a detection still opens that detection.
- A decode field can ask to show its value on the live row, and which named values use the stronger chip. Catalog 85 turns that on for DULT mode: Separated stands out; Near owner stays quiet. The separated sentence is catalog text on that value. Older apps skip the new keys and still import the pack.
- Debrief and Compare quote that live label and its catalog sentence. A separated tag keeps the about-a-day sentence instead of the rotating-address line. Compare says when the decoded value changed between sits. Catalog 86 adds the near-owner sentence. A named sit stores the label, because the sit does not keep the advertisement bytes.
- Catalog 87: Google Find Hub mode uses the same live row. Separated is the stronger chip and keeps the about-a-day sentence. Nearby stays quiet, with a sentence that co-travel is often your own tag or someone who joined with their own keys.
- Catalog 88: Remote ID location status uses the same live row. Undeclared, Ground, Airborne, Emergency, and RID failure all show. Emergency is the stronger chip.
- A sit keeps a short trail of advertised positions, joined by UAS id on the report. A track within 2 km of the phone path shares that map. A farther aircraft gets its own map. Reports → Path draws that track as a white dotted line on an open or saved sit. The letter-size figure draws it in black dots. Last 15 minutes marks the current advertised position and does not keep a track. Sits recorded before this change keep a single last position.

## 1.1.15 — 28 September 2026

- Catalog 82: Flock Safety Cameras Extra attention is IEEE B4:1E:52 and Flock / FLCK / Flock-* / Condor / Falcon / Sparrow names only. LiteOn / module prefixes move to LiteOn camera radio (Cameras class, no Extra attention, not bookmarked). UGSI E0:4F:43 is dropped so a Ring- SSID is Ring, not a Flock pole.
- Sit export CSV / JSON lines include matched signatures and Extra attention family names (semicolon-separated). Extra attention true/false stays. GPX / KML / WiGLE unchanged.
- Named sit cap 6000 unique radios (was 3000). Unnamed BLE still drops first. Low-memory gate unchanged.
- Debrief text/PDF hide unmatched rotating BLE from inventories by default. Counts, Extra attention, named signatures, bookmarks, payload pins, and Sit export still include them. Reports switch: Show unmatched rotating BLE.
- Catalog 83: Penguin decode on XUNTONG 0x09C8 manufacturer data — MAC in payload and ASCII serial starting TN. Identity is still the company ID / Penguin* names.
- Catalog 84: DULT tracker on BLE service data FCB2 (IETF Detecting Unwanted Location Trackers). Decode Network ID and near-owner vs separated. No Extra attention. Chipolo / Pebblebee names may dual-label. A bare FCB2 UUID list does not match.
- Wi-Fi Remote ID ASTM message packs (vendor IE FA:0B:BC type 13) are framed as BLE FFFA (`0x0D` + counter + each 25-byte message) so the stock Remote ID Decode fields map, detail, and TAK Payload location use the same fields as BLE. GitHub catalog stays v2.
- Update stock catalog from GitHub: unknown decode sources keep the signature and drop only that field map. After import, a second dialog asks for a newer APK when any map was skipped.

## 1.1.14 — 27 September 2026

- Debrief / Compare PDF path figure: OpenStreetMap tiles when Online place names and maps is on and Privacy is off (same gate as Reports → Path). Full-width letter frame, thick green stays, numbered Extra attention (red) and bookmarked (blue) hits. Offline or Privacy: the north-up trace only.
- Remote ID Location: heading uses the OpenDroneID east/west flag (direction 0–179, +180 when that bit is set) instead of a ×2 scale. Horizontal speed (`hspeed`) decodes with the SpeedMult bit (×0.25, or ×0.75 + 63.75). Catalog 79. ATAK track course/speed follow those values.
- Catalog 80: BLE manufacturer 0x09C8 (XUNTONG) moves from Raven / ShotSpotter to Penguin. That ID is the Flock external battery, not the acoustic sensor. Raven keeps names, UUIDs 3100–3500, and OUI D4:11:D6.
- Catalog 81: Axon `BWCDEVICE` matches ASCII (and byte-reversed) in BLE service data, not the advertised name. Empty-UUID service-data rules contain that hex in any service payload.

## 1.1.13 — 26 September 2026

- TAK / CoT: advertised Remote ID aircraft pins include ATAK track course and speed when the Location message has them. Wi-Fi Remote ID (vendor IE FA:0B:BC type 0x0D) decodes Location / Basic ID / System the same way as BLE FFFA, so Payload location can pin a Wi-Fi-only drone. Vendor IE payload is no longer truncated at 24 bytes.

## 1.1.12 — 26 September 2026

- Google Find Hub tags (FEAA frames 40/41). Catalog 78. Eddystone UID/URL/TLM stay unmatched. Separated mode can hold a MAC about a day. GitHub update uses dist/fieldwatch-signatures-v2.json; 1.1.11 still reads the v1 pack (catalog 77).
- Catalog 77 (already on GitHub for 1.1.11): Flock/FS Ext drop Espressif and Silicon Labs OUIs; Axon TASER/Axon UUIDs; Meta FEB7/FEB8, Snap FE45, Vuzix; Remote ID Wi-Fi FA:0B:BC and BLE v0–v2 location extras.

## 1.1.11 — 26 September 2026

- Detail / share: RSSI 127 is Bluetooth “not available,” not transmit power. It is omitted from current, min/max, sparkline, Hunt, and share text.

## 1.1.10 — 26 September 2026

- Path: Extra attention and bookmarked radios as numbered dots at strongest RSSI. Observer notes on Path only if that radio is bookmarked. Present for the entire route is dropped. GPS trails keep spread samples across the sit instead of only the last 40. Path drops GPS spikes (out-and-back jumps or hops faster than about 150 km/h).
- Stock catalog: BLE TPMS. Aftermarket valve-cap sensors (TPMS* / FBB0 / manufacturer data 80–83) decode pressure, temperature, battery, and alarm. SYTPMS / BR (name BR or UUID 27A5) decode gauge pressure, temperature, battery, and motion. Tesla tsTPMS decodes pressure / temperature / battery when the sensor is awake. New rows: TireCheck, Bluetooth TPMS service. FOBO also matches service 00EE. Not a bare Nokia 0x0001 match. Catalog 76.

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
