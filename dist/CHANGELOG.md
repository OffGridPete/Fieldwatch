# What's new

Newest first. This is Fieldwatch (`app.fieldwatch`). Each build below is what Settings shows as the version.

Fieldwatch continues the Spectre 1.2.14 field build under a new name, application id, and the MIT License. It does not replace Spectre on a phone.

## 1.1.43 — 9 November 2026

- A Bluetooth name of Quest, or one that starts with Quest followed by a space or a hyphen, or one that starts with Meta Quest, is Meta Quest. Quest 3S is that row. It is not labeled Ray-Ban / Meta glasses and it does not beep. A Meta company ID or service FEB8 without that name still matches the glasses row.
- Bluetooth channel and frequency are left blank. Android does not report the advertising channel, so the radio page and the log no longer show 2402 MHz as if it were measured. Wi-Fi channel and frequency stay. An older log row that already says 2402 stays as it was.
- Signature candidates keep a full custom service UUID. They no longer cut it down to four hex digits that can match a different Bluetooth service. A standard 16-bit service still meets its short form and its full Bluetooth UUID.
- A Wi-Fi signal of 0 is treated as no reading. It no longer sorts as the strongest radio or paints as a loud signal. A real reading still does.
- The log keeps the Bluetooth advertisement the phone already stored, up to 512 bytes. The raw field is that advertisement. Manufacturer data is its own field. A log row already saved stays as it was.
- Named radios is its own row on the Settings list, after Alerts. Signature and network stays on Alerts.
- Radio detail can show GitHub catalog request. It appears on an unmatched Bluetooth radio when the phone has a network that claims internet, the name looks like a factory model, and that name has been heard on two addresses this session. The tap opens a GitHub issue draft in the browser. Nothing is sent before that tap. The draft leaves out the address suffix, this phone's location, and the raw advertisement.
- Signatures → Class A–Z lists every class, including one with no signatures. The count is 0 until a signature is in that class. Body-worn stays under Wearables.
- Microsoft Nearby Sharing names the device type and whether sharing is set to My devices only or Everyone nearby. When it is Everyone nearby, the line includes the computer name from that advertisement. That background beacon is no longer called Swift Pair.
- Catalog 101 removes the Novatel Wireless row. Address 28:80:A2 stays on Inseego and does not beep as public safety. Hatch drops address C8:FA:9C. The Hatch names and company ID stay. Opening this app drops that row and that address from the built-in signatures, including when Update stock catalog already saved catalog 101 and left them in place. A rule you added stays. A custom copy of Novatel that is not built-in stays.
- Filters add Two-way radios and Military. Two-way radios has Icom ID-52, from the Bluetooth name ICOM BT(ID-52) while the radio is waiting to pair, plus wireless microphones Icom VS-3, Yaesu SSM-BT10, Yaesu SSM-BT20, Kenwood KHS-55BT, Kenwood KHS-56BT, and VGC BHM-88. On those microphones, Bluetooth is how the microphone works. Each row matches the published name on a Low Energy advertisement. A name that only appears on the Classic link stays quiet. Military has no signatures yet. Catalog 102 also matches Benshi-platform radios by the exact Bluetooth name UV-PRO, UV-50PRO, GA-5WB, VR-N75, VR-N76, VR-N7500, VR-N7600, or DB50-B, and BLE push-to-talk buttons named PTT-Z, PTT-AZ, or PTT-Z01. None of them beep.
- A signature file that uses a class, a rule, or a radio this app does not know asks you to update Fieldwatch first. Update now on that message checks for an app update. The catalog on the phone stays as it is. The same message is used for Update stock catalog, Import signatures, and Import settings.
- Wi-Fi vendor tags name WPA, WMM, WPS, Wi-Fi Direct, Miracast, Hotspot 2.0, MBO-OCE, EasyMesh, and OWE transition. A WMM tag no longer reads as Microsoft. Other vendor tags still show the company that owns the OUI.
- Catalog 100 adds ExpressLRS setup Wi-Fi, Xiaomi Mi Home service FE95, and an EZVIZ setup network. Each names the family, not one model. ExpressLRS beeps like the other drone rows. The other two do not.
- Catalog 99: DJI also matches the older DroneID Wi-Fi beacon, vendor IE 26:37:12. That labels the aircraft. It does not fill a position. ASTM Remote ID stays on the Remote ID row. Android 11 or newer can see that beacon field.

## 1.1.42 — 5 November 2026

- A live sit map keeps up on a long drive. The line, the stops, and the camera marks stay where they were.
- On Reports → Path, the camera picture for that view slides with the map. Where two wedges of the same color overlap, the tint is painted once.
- Hunt on a radio's detail page starts a new hunt. Leaving Hunt clears the walk.
- When the signal is lost, the large number reads - - and the line under it reads -- dBm · Signal Lost.
- Filters can hide randomized Bluetooth. Wi-Fi stays. The sit still keeps those radios.
- Bundled language files are written for this version. Signal Lost and Hide randomized Bluetooth are the lines added since 1.1.41.

## 1.1.41 — 4 November 2026

- Warning dialogs for GPS, a cell, a trusted network, and a flood use one layout. The reason is a paragraph. Readings sit under Details, with the label and the value on one line.
- The update offer has a Release Notes heading. Long notes scroll.
- When an app on this phone is generating the GPS location, the warning is Possible GPS interference. It says an app on this phone is generating the GPS location, and this phone is using that instead of the satellites.
- A cell warning keeps what happened in the title. The paragraph says Fieldwatch cannot tell the cause.
- Bundled language files are written for this version. Release Notes is the line added since 1.1.40.

## 1.1.40 — 3 November 2026

- When this phone is online at start, Fieldwatch asks GitHub whether a newer version is there. A newer one shows the notes, with Update now and Later. Later skips that version until a newer one is published. Settings can check again. Update now downloads the app, and Android still asks you to confirm the install. If GitHub does not answer, the app on this phone stays as it is.
- On-screen notes are shorter. They say what the control does. The scan status no longer calls the scan passive. Settings notes under the switches are one line. The longer explanation stays in the user guide.

## 1.1.39 — 2 November 2026

- A language file that comes with the app says it was written for this version. An imported file reports the keys added after the version it was written for. A translated line from an older file still shows.

## 1.1.38 — 2 November 2026

- Samsung SmartTags matches the SmartTag name or service FD5A. A phone, TV, Buds, or watch that only sends Samsung's company ID stays off that row. The stock catalog is 98. Opening this version drops that company ID from the built-in row. A rule you added stays. Update stock catalog from GitHub brings the same file.
- Reports lists radios heard at two or more places in the last 7 days, without a sit and without hearing them now. The map shows where this phone was. Each radio has its own color on the map and beside its name. Privacy mode hides the map and the coordinates.
- On that report, each stop is numbered. The time, the street, and the coordinate are on their own lines. The numbers are plain. The name is the live list's Name + type. Tap a radio to open it, including one that has left Live.
- Cell connection monitoring is off until Monitor the cell connection is on. The first still visit at a place only learns. How long to learn starts at 3 days. The choices are None, 1 day, 3 days, 7 days, and 14 days. A later visit can warn on a new cell, or on a drop to 2G or 3G. The dialog can list the country, the operator, the channel, and the band. Debrief gets a section when a warning overlapped the sit. The alert is separate from the watchlist. Diagnostics lists the camp even when the watch is off.
- Signature and network, under Alerts, saves words that replace the signature in the announcement. Those words apply to every radio of that signature with that network name. The network name is not spoken. A name on one radio still comes first. Saying the class only keeps the words saved and says the class. The list goes with Export settings.
- Trusted networks shows how long learning has left. How long to learn adds 1 hour. Changing the length does not forget access points already saved.
- Show live path and Reports → Path have a camera button. It hides the marks in the picture, or shows them again. The choice is saved. Hunt has no button. The list under Path, the sit report, and the printed path still follow the layer switches.
- The new settings and alert text is in all 26 languages that come with the app.

## 1.1.37 — 29 October 2026

- Show live path and Hunt turn with the heading as it changes. The map already on screen turns with it, so the edges stay filled. Standing still, or turning the phone in place, leaves the map where it is.
- A double-tap on Show live path or Hunt switches between the close view and the whole path right away.
- Coming back to the live list draws the list first. The map follows.

## 1.1.36 — 29 October 2026

- Each trusted-network warning names that network and the reason that fired. The title is Possible evil twin of "{name}": {reason}. The reason is one or more of these: open security, weaker security, unknown device, or away from usual places. The line under it is that access point's address. "00:13:37 belongs to Orient Power Home Network." is an extra line, and only when that address starts with 00:13:37. A warning for any other address does not include it. That line does not replace the network name or the reason, and that address block does not warn by itself.
- Trusted-network warning actions sit on their own lines.

## 1.1.35 — 29 October 2026

- Language packs cover more of the on-screen text: the live sit line, the radar, device explanations, filters, reports, and the human-readable Debrief and Compare wording, including the PDF. The English template lists a character limit for each line. Signature names and catalog notes stay English. CSV, JSON, GPX, KML, and the other machine exports stay as they are.
- Importing a language pack can save a text report. It names the keys that will be used, the keys that are missing, and each line that is too long, with the length and the limit.
- GNSS interference, Location and Maps, and TAK / CoT group the on/off switch apart from the choices under it. Each control has a short line about what it does. Warning level replaces Alert from. Warn when the signal holds replaces Spoofing checks. Keep the GPS receiver awake, Count satellites fading together, and Gain reads backwards replace the old receiver switches. Known cameras replaces Layers. Corner readout replaces Position. Display location on map is the title on that group.
- On Trusted networks, the on/off switch sits on its own. The warning checks are in a Warnings group, each with a short line about what it catches. Learning, and how a warning reaches you, are separate.
- On TAK / CoT, the GNSS heading is GNSS warnings. The Trusted networks heading on that page is gone. The note sits under Network warnings.
- On TAK / CoT, the Floods heading is BLE Flood warnings. A Wi-Fi beacon flood still uses that same line. The notes under the TAK controls are shorter, and Trusted networks has one.
- On TAK / CoT, under Trusted networks, that switch is Network warnings. The hold under it is How long a warning spot stays.
- The Trusted networks TAK pin, and how long that pin stays, are on Settings → TAK / CoT. Both stay dim until Watch trusted networks is on.
- The guide says Trusted networks is not a substitute for checking a network yourself. A quiet watch is not a finding that the network is safe.
- The Display switch on Live is Show map with live path.
- TAK / CoT to this phone no longer tries to pin its socket. Diagnostics says whether that same-phone feed reaches ATAK. It reaches ATAK when the socket opened on Wi-Fi or Ethernet. On cellular alone it needs Wi-Fi. A socket opened without Wi-Fi or Ethernet is still replaced once either connects.
- Privacy mode masks raw advertisement bytes on the radio page, in Share as text, and in one-radio AI Export. The row keeps its label and byte count, and the hex shows as masked. Logs and sit export stay complete.
- A Samsung SmartTag advertisement now says whether it is separated from its owner or with its owner, and a separated tag is followed across address changes.
- Show live path, Hunt, and Reports → Path draw saved map pictures when the phone has no connection. A place with no saved picture stays a plain line. New pictures are still downloaded only while online.
- The sit report leaves a camera list out when its layer switch is off. The count and the marks on the printed path stay off. The file stays on the phone. A switch that is on still names a list that is not on the phone.
- Trusted networks watches a Wi-Fi name you mark on that radio's page. It can alert when a later sighting of that name is weaker, from an unknown maker, or far from the places this phone usually hears it. The list, the learning window, and a TAK pin are under Settings. The pin starts off. The phone does not connect to the network or ask for its password.
- Show live path and Reports → Path open a card when you tap a known camera. The card names the kind, the company when the list has one, and the facing when one is stored. A number is several of that kind. Tap the mark again to close it. The card has no coordinates.
- Opening Fieldwatch no longer closes while the path reads a saved camera list. A failed camera download stays on the settings row and says why. A saved copy stays, and the path still opens.
- Importing a language pack, signatures, settings, trusted networks, or the stock catalog says why it failed. A file that cannot be read, is empty, is too large, or cannot be saved leaves what is already on the phone.
- A watchlist alert on Live opens in the list under Show live path. By class, the strength list, Timeline, and Hybrid keep that row in the space below the map.
- Tapping a known camera on the map uses the same card as a detection. The line meets that card. Cameras on the same spot are listed together.

## 1.1.34 — 27 October 2026

- TAK / CoT to this phone pins the loopback socket to lo before it is bound. A socket opened without Wi-Fi or Ethernet is replaced once either connects, including when cellular was already up. A socket opened on that LAN keeps sending after the LAN drops. Diagnostics lists whether TAK is on, the destination kind and port, whether the loopback socket is bound to lo, what was connected when it opened, and whether the last send succeeded. The host address stays off that page.

## 1.1.33 — 27 October 2026

- TAK / CoT to this phone still reaches ATAK when Fieldwatch starts with no Wi-Fi joined. A socket opened with no network is replaced once a network is there. A socket that already worked keeps sending after Wi-Fi drops.

## 1.1.32 — 26 October 2026

- Show live path draws alert dots and known cameras on top of the drive line.
- Hunt draws the walk before GPS locks. The line on that picture is No GPS yet. This walk is not on a map. Streets and the place in the corner stay off. Two fresh fixes place that walk when it is still about the first 20 m. A longer walk stays off the map.
- Reports → Path draws cameras and detections on top of the drive line. Zooming in splits a numbered group into separate spots, unless those spots are the same place.
- A real stop stays green. GPS jitter no longer flips that stretch back to blue.
- Show live path keeps the drive line on the map when the view is zoomed in.
- Zooming Reports → Path keeps the drive line inside the map.
- Reports → Path can be pinched and dragged. A double-tap fits the whole path again.
- A known camera speaks when it is still about 500 m ahead. The path report still counts cameras within about 150 m.
- The drive line is a slightly darker blue. A green stop is the same width as that blue line.
- Voice Alerts calls the red-light switch Alert for red light cameras.
- Hunt shows the latest GPS fix as soon as it opens. The circle is the size of that fix. The heat trail starts after two fresh fixes agree, and steps taken before that are left off. In the first 20 m, one tight fix that disagrees moves the dot onto that fix. A phone whose step sensor is missing, or never reports a step, counts steps from the walking motion. A compass jump does not move the walk unless the phone itself is turning.
- The sit report lists known cameras the same way as Reports → Path. The heading is Known cameras on path. Each type is the name, then how many faced the path and how many were on the path with no direction. A missing list is still named.
- Cameras on a sit path are drawn larger, with a light ring, so they stay visible on the blue path. Show live path and Hunt keep the smaller marks.
- Reports → Path leaves Known cameras on path off when that list is not on the phone, or when its layer switch is off. A list that is on still shows its rows. The debrief still counts a saved list.
- Reports → Path lists known cameras as one row for each type, with the same colors as the map. The line under the heading is the distance. The debrief keeps the full sentences.
- Reports → Path lists known cameras on the path under the radios that alerted. The lines match the sit report.
- Cameras sit on top of the path line. That is Reports → Path, the printed path, and Show live path.
- The printed path in a Debrief PDF and in Compare is the same blue as the path on the phone. A real stop is still green. The second sit stays slate. Reports → Path and that printed path show the cameras the sit report counts. A stored direction counts when it pointed toward the path. No stored direction counts as on the path. Gunshot detectors and a direction that pointed away are left out. Show live path and Hunt still draw the layer that is switched on.
- The sit report counts cameras along the path. A stored direction counts when it pointed toward the path. No stored direction counts as on the path. Flock, other plate readers, speed cameras, red lights, and other cameras are listed. Gunshot detectors are left out. The count uses the lists saved on this phone.
- Reports → Path leaves the corner line off a sit that has ended. That line is the grid, or masked while Privacy mode is on. An open sit still shows where this phone is.
- Show live path draws the drive as a thicker blue line. A real stop is still green.
- Zooming out on Show live path or Hunt shows one map picture for each spot. A closer picture no longer stays on top of the wider one.
- Scrolling Show live path or Hunt keeps the pictures already on screen and fills the new ground as each picture arrives. The next screen in the direction of that scroll is saved for the next drag.
- Show live path and Hunt save the next screen of the map along the direction of travel, and a little to the side when the course is turning. Those pictures stay off the screen until you get there.
- A double-tap on Show live path opens the same scale as a sit that has not moved yet. The short side reaches about 220 m.
- Hunt places the start on a GPS fix only when that fix is within about 12 m. It asks to count steps, and uses this phone's step sensor once that is allowed. Opening Hunt does not count as a step.
- Hunt still follows this phone on a handset whose step sensor will not report steps. That handset counts steps from the accelerometer.
- The pink pin is off the hunt map.
- Hunt map colors grade from one place into the next again. A loud pass still outweighs a much weaker one.
- A nearly gone hunt signal stays blue. The color moves gradually through the shades toward red as you get closer, and red shows by about −50 dBm. The place you are standing keeps the loudest reading heard there.
- Hunt keeps following this phone after the radio goes out of range. No color is added on that part of the walk, because there is no reading to paint.
- Hunt follows steps and the phone heading. GPS places that walk on the map and only nudges it when a good fix agrees. The circle on the dot grows as the walk gets longer. Hold the phone flat. A tilted phone pauses the steps. The place you are standing keeps the loudest reading heard there. The next place, about a step and a half on, gets the strength heard there, weaker included. The color grades evenly from one place into the next.
- Dragging or pinching Show live path or Hunt keeps the direction already chosen. Direction of travel stays up. It no longer snaps to north up.
- Show live path and Hunt fill the corners of the map when it turns with travel.
- Location and Maps keeps saved map pictures on the phone, up to 512 MB, and can clear them. Clear saved maps does not remove plate readers or other sensors.
- Location and Maps adds USGS Imagery Topo and FAA VFR Sectional. The USGS maps and FAA VFR Sectional cover the United States and do not ask for a key. FAA VFR Sectional says it is not for navigation. A view that is too far out for that chart asks you to zoom in. Under Map, Overlays can put USGS Shaded Relief and USGS Hydrography on top of the map you picked. Both cover the United States. Leave them off for the map alone. With an overlay on, the map credit and each overlay credit are separate lines on the right, so the grid on the left stays readable. With overlays off, the map credit stays one short line.
- Location and Maps adds two Layers. License plate reader cameras downloads the United States plate-reader list (about 37 MB). Other sensors downloads the speed camera, red light, gunshot detector, and other camera list, about 1 MB. The map pictures stay the same. Neither list is included with the app. Each has its own Update. Marks show on Show live path, Hunt, and Reports → Path. Flock plate readers are blue. Other plate readers are amber. Speed cameras are teal, red lights are red, gunshot detectors are purple dots, and other cameras are a smaller gray mark. A crowd of one type keeps that color. Each type has its own voice alert, off until turned on. Turning one on speaks that notice. A switch does not hide marks. A plate-reader alert names the company when the file has one.
- After that list finishes downloading, the row says Processing while the phone reads the file. A sensor with a known direction is a view cone. It stays small when the map is far out and grows when you zoom in. No direction, and every gunshot detector, is a dot. A saved copy that has only Flock cameras asks before Update, even when the downloaded list has not changed.
- On Location and Maps, the paragraph above Map has the same space before the box that Position already has.
- Bitchat is a Mesh signature. It matches the BLE service advertisement. It does not beep. Settings → Update stock catalog from GitHub adds it. Catalog 97.

## 1.1.31 — 23 October 2026

- The Wi-Fi, Bluetooth, and signature counts in the top bar each keep room for 888, so the line does not shift as the numbers change.
- Hunt no longer shows Very Close, Faint, or Gone. Watch the number, the sparkline, and the cloud. Beep and Vibrate still stay quiet when there is no packet, or the radio has left.
- Settings → Location and Maps can use world maps from Esri, MapTiler, Stadia, and Thunderforest. Those maps are marked Key. Paste the key from your own account. It stays on this phone, and Export settings leaves it out. OpenStreetMap and the USGS maps do not ask for a key. A bad key or a map that does not load shows a popup.
- A double-tap on Show live path switches between the whole path and a close view around this phone. The close view keeps this phone in the center, and the short side reaches a little past about 100 m. North up and direction of travel each keep that zoom.
- The drive line keeps a point every few meters, so it follows the street around a corner. A turn leaves the road already driven where it was. Green is still a real stop.
- Show live path and Hunt have a compass button where the N mark was. Tap it to switch between north up and direction of travel. The picture shows all four combinations. Zoomed out is a bent path, with N above it when north is up. Direction of travel puts the same compass letters as the close view above a smaller bent path. Zoomed in is those letters, or N above an arrow when north is up. Standing still keeps the last direction. A turn in place does not turn the map. Reports → Path and the saved path figure stay north-up.
- Show live path follows the road through a turn. A straight stretch still leaves GPS wobble off the line. Green is a real stop: this phone stayed within about 25 m for about 40 seconds. The rest of the drive is the plain line.
- Hunt leaves the top of the page for the map. Watch the number, the sparkline, and the cloud. The scale starts at Weak. The words beside the number follow the language pack.
- Hunt draws a heat cloud of where this phone heard that radio. The color runs from blue where it is weak, through cyan, green, yellow, and orange, to red where it is powerful. Walk or drive the area. The brightest patch is where it was loudest. The loudest mark sits in that patch. It is not a distance and not a pin on the other radio.
- Hunt opens close in, and the cloud is drawn finer. Steps fill in short moves between GPS fixes. A tight GPS fix still recenters the walk.
- The hunt cloud follows the walk. Hears connect, and the color changes smoothly from one to the next. Close parts of the walk fill in between the lines. A far gap stays open. The edge fades that color out.
- Hunt stays close on you as the walk gets longer. Leaving the page keeps the cloud until Reset, or until you hunt a different radio. A turn in place does not move the dot. The lower-left reading matches the dot. While GPS is moving, steps follow that direction. The top of the phone is used when GPS is stuck.
- Settings → Maps chooses the map under Show live path, Reports → Path, and the path figure. OpenStreetMap is the start. USGS Imagery and USGS Topo cover the United States. A small line in the corner of the map names the source.
- The radio page shows the grid beside the GPS. For an ordinary radio that is where this phone was. A Remote ID aircraft shows the aircraft position and the takeoff, pilot, or fixed point, each with a grid, plus the distance and bearing from this phone. A sit report uses where this phone was when that position was heard and names the time. An older sit shows the grid and leaves the distance and bearing off. Privacy mode masks the grid and leaves the distance and bearing off.
- Settings → Maps can show where this phone is now on the lower left of the map, as GPS or as a grid. Off leaves that corner empty. Privacy mode masks it. The corner stays put until this phone has moved.

## 1.1.30 — 22 October 2026

- Places no longer closes the app on Android 10 and 11.

## 1.1.29 — 22 October 2026

- On Radar, Display dims Title line, Subtitle line, Show live path, RSSI bars, Frequency, and First / last seen. Timeline dims RSSI bars. The saved choice stays, and it comes back on a view that uses it. View, Sort, Brief hold, and Signature names stay available.
- While Location is off, Fieldwatch does not ask for Wi-Fi or Bluetooth scans. Turning Location on waits a moment, then starts them again.
- If Fieldwatch closes from a crash, the next open shows what happened before anything else starts. Copy sends it. Continue opens the app. A normal close does not show it.

## 1.1.28 — 22 October 2026

- Show live path is on Display, the first switch, above RSSI bars. It starts off. Turn it on and a north-up map of this phone sits across the top of Live, with the list still under it. Drag the bar between the map and the list. While View is Radar the switch stays dimmed and does not turn off.
- The line starts where you are when the map opens and draws as soon as you move. A bend is kept when the drive leaves the last line by about 12 meters. Side-to-side GPS wobble stays off the line. With no sit, the line is the later of when you turned the switch on and 15 minutes ago. An open sit draws from the start of the sit, walking or driving. Dots are watchlist alerts. Pinch or drag holds the view. Recenter, or a double-tap, follows you again.
- Reports → Path uses that same line.
- Turning Location on while Fieldwatch is open pauses Wi-Fi and Bluetooth for a moment, then starts them again. A bad scan result is skipped instead of closing the app.
- Debrief and Compare path figures draw the street tiles solid. The disclaimer and the path caption use the body text.

## 1.1.27 — 16 October 2026

- Places remembers a stable tracker this phone hears at two or more places in the last 7 days, and a return after a quiet stretch. The lines are on that radio’s page and in the sit report, with where this phone was. A stop from another day includes the date. Online place names adds the street. Filters can limit Live to the ones you can hear now. A radio that changes its address is skipped. Ordinary Wi-Fi and Bluetooth stay off. There is no beep and no TAK marker. Each radio keeps the latest 8 stops. A ninth confirmed stop drops the oldest.
- The user manual explains Places: what a stop is, which radios qualify, and the limits.
- Signature candidates shows a progress dialog while it reads the log and matches the catalog. The bar follows the file.
- Sit export can write GeoJSON. It is the same map as the sit GPX and KML: this phone's path, and a point where this phone heard each radio.
- Settings opens as a list of topics. Tap a row for that page. The row shows the current choice. Back returns to the list. Named radios is its own row, after Watchlist. The row shows how many are saved.

- A Filters row names what is selected in the same muted text as Settings. That line is not green.
- The sit list is spaced a little tighter.
- The Signatures page says Bookmark to alert. The line about hiding a family on Filters is gone.
- The Sits line says Radios heard during a named stretch of time. Under Start sit it says Select Sit for actions below.
- Moving with you says Loud Bluetooth radios that stay with you.
- Presets says tap a preset to load it, long-press one to delete it, or save the current filters settings to a new preset.
- Selected signature classes says to pick Show only or Hide these, then the classes, and that a preset includes those selections.
- Selected signatures says to pick Show only or Hide these, then the signatures, and that a preset includes those selections.
- Edit signature drops the last sentence under Notes and the last sentence under Extra attention.
- Edit signature says the class is what Filters uses for Show only or Hide these.
- Edit signature and Decode fields use shorter, accurate lines for Min peers, color, rules, restore defaults, removing a decode map, and an empty preview.
- Settings → Diagnostics says it shows whether scanning is running, and the state of Wi-Fi, Bluetooth, Location, and GPS. The Diagnostics page no longer tells you to paste the copy into an issue.
- The Settings footer no longer says Wi-Fi and Bluetooth only.
- The Live tour points at the sliders in the top right. Open it shows how Live looks. Skip closes the tour. An upgrade to this version shows that card once, on the first Live screen. Settings → Show Live tour still brings it back.
- The language list uses the English name of each language, in alphabetical order. Use the phone's locale if available stays first.
- The user manual explains Language: the bundled drafts were translated with AI, a wrong line belongs in a GitHub issue, and a new language is a file in the author’s own repository. The author guide is linked from that section.
- An off switch keeps a light knob, so it still looks like it can be turned on. A switch that cannot be used stays faded.
- Reports keeps Sits open. Path, Debrief, Sit export, Compare, Log Export, and Signature candidates each open their own page. The line under each row is a short description. Signature candidates explains the search, and Find candidates starts it. Path, Debrief, Sit export, and Compare sit under a line that names the sit they use, with a bar on the left in the same green as those rows. Log and Signature candidates stay full width under that group.
- Keep or hide calls the three keep switches Keep signature matches only, Keep watched only, and Keep named only. Hide my radios is Hide Mine.
- Filters keeps Presets and Radios open at the top. Moving with you, Seen at more than one place, and New detections are switches on that list. Keep or hide, Selected signature classes, Selected signatures, and Signal, name, MAC, vendor open their own page. The row names what is selected in that section.
- Selected signatures is one list. Show only and Hide these sit above it, the same way Selected signature classes works. Turning a chip off keeps the picks.
- Sit export can write an ATAK data package. ATAK imports the zip. It has this phone's path, a point for each radio, an aircraft pin and its short track when a position was advertised, an operator pin when that position was advertised, and a GNSS hit as a pin on the path. The ellipse and floods stay on the live feed.
- Each phone gets its own TAK marker. The name is Fieldwatch plus a 4-character tag, unless you type one. Two phones on one network no longer share that marker. A Remote ID aircraft with a UAS ID is still one shared marker. This is not direction finding.
- Settings → GNSS interference can record the phone’s raw GPS measurements around a hit. The switch is off until you turn it on. Share last recording is on that page. Privacy mode does not record.

## 1.1.26 — 12 October 2026

- A Remote ID operator location says what that point is. Takeoff is the launch spot. Pilot is a live GPS operator. Fixed is a point the aircraft declared. An older sit says Operator. The pin stays Orange. A known operator altitude is shown. Unknown stays blank. The card can say the aircraft height is above takeoff or above the ground. EU category and class show only when the plate says the classification is EU.
- Settings calls the longer GNSS mark an ellipse. A short hit is still a pin. A longer hit is one ellipse from the start to the last place, half as wide as it is long.
- The user manual, under GNSS interference, explains how a hit is decided.

## 1.1.25 — 11 October 2026

- A GNSS hit can put a mark on the ATAK map where this phone was. A short hit is a pin. If the phone keeps moving while the hit lasts, the mark becomes an oval from where it started to where it was last heard, half as wide as it is long. The words also stay on this phone's Fieldwatch marker. The mark does not say where the cause is.
- How long that mark stays is a setting: 1 hour, 6 hours, 24 hours, or until deleted. The default is 1 hour. Until deleted stays until GNSS on TAK is turned off.
- A radio spot on TAK keeps its name after the radio leaves. How long a spot stays is 1 hour, 6 hours, 24 hours, or until deleted. The default is 1 hour. This phone's Fieldwatch marker still refreshes about every 2 minutes. Turning the feed off, or Privacy mode on, takes those radio spots off the map. A short GNSS pin that grows into an oval does not leave a Fieldwatch spot behind.

## 1.1.24 — 10 October 2026

- The icons in the top bar show whether Wi-Fi, Bluetooth, and signatures are healthy. Green means that part is working, including the wait between Wi-Fi scans and a planned Bluetooth rest. A signature count of 0 stays green. Gray means the scan is stopped. Dim gray means that radio is off. Red means Location is off while that scan is running, or Android is refusing it. The words after the icons stay in the secondary text color. Night mode folds the colors into the red display.
- A GPS mark with no number sits in that bar while the GNSS interference check is on. Green when the check is armed and quiet. Gray while it is still settling or learning, while the phone has paused GPS, while this phone graded Poor, or while the scan is stopped. Red while a hit is up. A pause stays gray.
- Faster Wi-Fi AP scans matches Developer options. The switch is on only when Wi-Fi scan throttling is off. Tap it to open that page. About every 8 seconds instead of the normal wait. Fieldwatch cannot change that switch for you.
- The window uses the full height of the phone, so the top bar, the list, and the tabs still draw on phones that were leaving them blank.

## 1.1.23 — 9 October 2026

- A GPS pause is not interference. If the satellites and the gain disappear together, or the phone reports the receiver stopped, Live says GNSS paused by the phone. There is no popup, red line, or TAK line. The check waits a few seconds after GPS returns before it watches for a drop again. A real drop is still a gain change while satellites are being tracked.

## 1.1.22 — 9 October 2026

- Catalog 94. Flipper Zero also matches official-firmware services 3080–3083, so a renamed official unit still matches. Tile also matches FEEC, the pre-activation service. Polar is a new Wearable row: company 0x006B, service FEEE, or a Bluetooth name that starts with Polar. A Polar strap that also sends the GoPro heart-rate service stays on Polar.
- A GPS warning posts a phone notification only when Settings → GNSS interference → System notification is on. That switch is off until you turn it on, and it is separate from the watchlist switch. The popup, the red line, the beep, and the voice are unchanged.
- After calibration, Settings says Calibration complete and names this phone Good, Fair, or Poor. Good says the check should work with a steady view of the sky, away from other electronics.
- The GPS popup says what was noticed and why, in plain words. The receiver numbers come after that, under Details. The red line, the sit, and the path note keep the shorter technical line.
- A clock mismatch, a position mismatch, or a jump faster than travel does not warn on its own. Those count with a gain drop, or with each other. A network fix of 100 m or coarser does not count. The centers have to be clearly apart, and the mismatch has to hold.
- A pairing or name flood stays quiet while this phone is moving faster than a walk and the burst is about -80 dBm or weaker. A loud burst still warns at any speed. Standing or walking still warns at any loudness. A Wi-Fi beacon flood is unchanged.
- TAK / CoT can add a flood note to this phone's marker when a flood is detected, until the flood ends. Off until you choose On. Not a separate pin, and it does not change the red line on Live. A store full of radios advertising pairing can cause it. The note does not say it was one radio.
- The GNSS check ignores the first minute after it starts. Those readings are not the baseline. It then learns for about a minute, and a warning can come after that. The saved calibration for this phone still applies.
- A GNSS detection is a red diamond on Reports → Path, where this phone was. The note under the map, and the Debrief and Compare section, have the time, the level, and what changed. The diamond is not where the interference came from. A sit with no GPS fix still lists the hit, with no diamond.
- GNSS interference check stays off until this phone is calibrated, then until you turn it on. Calibrate this phone takes about a minute outside, standing still, away from a router. The result stays on this phone until you calibrate again and is left out of Export settings. Good, Fair, or Poor says how useful the check will be on this GPS. A weaker receiver can still tag a path. Poor leaves the popup off. While Fieldwatch is scanning, a hit opens a popup, then a red line on Live. Debrief and Compare record it. Spoofing checks, voice, sensitivity, and the confidence that alerts are separate switches. Voice for this check is on. A Wi-Fi router or other electronics next to the phone can cause a hit. Fieldwatch cannot tell the source or the distance. Diagnostics lists the receiver facts and the calibration grade, and does not include GPS coordinates. TAK / CoT can add that line to this phone's marker: Off, While alerting, Red line, or Any hit. It is not a separate pin.
- Live shows a banner when Location is off. Turn on opens the phone’s Location screen when that screen exists. Android will not return Wi-Fi or Bluetooth results while Location is off.
- Diagnostics stops naming Wi-Fi scanning and Bluetooth scanning when that radio is already on. A refused Wi-Fi scan or a parked Bluetooth scan says the radio is on.
- Catalog 93. Flock Safety Cameras keeps B4:1E:52 and a name that starts with Flock-. A name that only contains Flock, FLCK, Condor, Falcon, or Sparrow no longer matches. FS Ext Battery keeps the name and a name that starts with FS Ext. Silicon Labs prefixes and FS_* are gone. Penguin keeps the XUNTONG manufacturer ID and a name that starts with Penguin-. LiteOn camera radio drops B8:35:32. 08:3A:88 stays. That prefix is Universal Global Scientific Industrial, not Lite-On.
- Pairing flood leaves out the Windows background beacon (company 0x0006 starting with 01). Swift Pair still counts: beacon id 03, a pairing sub-scenario, and the reserved byte. Find My stays out, including a Find My advertisement whose later bytes look like proximity pairing.
- Settings → Diagnostics starts with What to check. It names a switch that is off and needs to be on. If that does not explain an empty Live list, Copy still pastes the page.
- Voice alerts can reach a speech engine on Android 11 and newer. Android 10 already could. The phone still needs a public engine with a downloaded voice, Voice on, and the media volume up.

## 1.1.21 — 6 October 2026

- Privacy mode masks a Remote ID in the sit-report path key: the MAC tail, the UAS id, and the aircraft and pilot coordinates. Altitude, course, and speed stay. The map and the dotted track still draw. The same UAS id is masked in the rest of Debrief, Compare, and AI Export. The log, sit export, and GPX/KML/WiGLE files are unchanged.
- WiGLE CSV prefixes a name whose first non-space character is =, +, -, or @ when that name is not a plain number, same as sit CSV and log CSV. RSSI, coordinates, and a normal network name stay as they were.
- Settings → Diagnostics, under Show Live tour, lists the phone, Android version, catalog, and whether Wi-Fi, Bluetooth, and Location are scanning. It also lists Faster Wi-Fi AP scans, Allow background usage, and Unrestricted battery. Copy pastes those lines into an issue. It has no network names and no GPS coordinates. An empty Live list points there when a scan is blocked.

## 1.1.20 — 5 October 2026

- Sit CSV and log CSV prefix a cell whose first character is =, +, -, or @, so a spreadsheet keeps the name or note as text. A plain number, including a negative coordinate, stays as it was. WiGLE CSV is unchanged.
- Import settings asks before it applies a file that would turn the TAK / CoT feed on or change its destination. A file that leaves the feed and the destination as they are still imports with no extra step. Cancel leaves the phone as it was.
- Catalog 92: LiteOn camera radio drops 48:27:EA (Samsung) and 82:6B:F2 (a local address, not a factory block). F8:A2:D6 stays. Ray-Ban / Meta glasses also matches a Bluetooth name that starts with Meta RB Display. Axon notes say body-worn or fixed ALPR. That public address prefix is shared by body cameras and fixed readers. Remote ID names Autel from a serial that starts with 1748C, and Skydio from 1668B, when the ID type is a serial. A shorter prefix does not get that name.

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
