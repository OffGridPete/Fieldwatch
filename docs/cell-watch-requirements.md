# Cell connection monitoring

Requirements for a later implementation. This document does not authorize that work by itself. Do not start it until asked. When asked, implement the first ship in this document and stop there. The Later section is a list so those ideas are not forgotten. Do not build them in the first ship. Do not ship, tag, sideload, or open a GitHub Release unless that request says so.

Fieldwatch can watch the cell tower this phone is camped on. Stock Android can read that serving cell, receive-only, through `TelephonyManager.getAllCellInfo()`. That is enough to learn which cell this phone uses at a place, and to warn when the camp changes. It is not enough to see an identity request, a null cipher, a forged system message, or any other phone.

This watch sits with the GPS watch. It is not a row in the live radio list and it is not a signature. The catalog stays as it is. The README line "Not cellular" stays true for other devices: a camera or a tracker that only speaks cell still does not appear in the list.

## Out of scope

- No catalog change. No new signature. Catalog count stays where it is.
- No live-list row for the carrier or the tower.
- No `READ_PHONE_STATE` permission. Fine location is already granted, and `getAllCellInfo()` uses that.
- No faster GPS rate. The walk threshold already in travel course stays as it is.
- No reading of Android's cellular-security or cipher notification. When the phone has that setting, the help points at it. Fieldwatch does not claim to read it.
- The user guide covers the first ship in §5.12. Do not describe a Later item as if it shipped.
- No change to GNSS detection, trusted networks, floods, or the TAK self marker.
- Nothing in the Later section.

## Outcome

The watch defaults off. While it is off, nothing is learned and nothing warns.

While it is on, the scan service reads the registered cell on the cadence it already uses. The first time this phone is still at a place, Fieldwatch stores the serving cell and does not warn. A later visit to that place can warn. A warning names what this phone did. It does not name a cause, and it does not say an attack was detected.

A sit that overlaps a warning gets one block on Debrief. The popup does not wait for a sit.

## What is read

One registered cell, the `CellInfo` whose `isRegistered` is true. Its class is the generation:

- GSM is 2G
- WCDMA is 3G
- LTE is 4G
- NR is 5G

Store, when the phone filled the field:

- Generation
- PLMN, as MCC-MNC
- Area code, TAC on LTE and NR, LAC on GSM and WCDMA
- Cell ID
- Physical cell ID
- Channel, EARFCN on LTE, NR-ARFCN on NR, ARFCN on GSM and WCDMA
- Signal, RSRP or the equivalent the class provides
- Timing advance

An empty list, a missing registered cell, or an unavailable identity is not a cell. Unavailable is `CellInfo.UNAVAILABLE` and the old maximum integer. Those hears do not learn and do not warn. Neighbor cells are counted for Diagnostics and are not stored as serving cells. A second SIM is noted for Diagnostics and is not compared.

The identity of a cell is the generation, the PLMN, the area code, and the cell ID. Physical cell ID and channel are stored and shown. They are not the identity. A change of physical cell ID on the same identity does not warn.

Cached `getAllCellInfo()` is the reading. Do not call `requestCellInfoUpdate()` in the first ship.

## Places and learning

A place is a cluster of good fixes, the same 300 m rule trusted networks use for a usual place. The phone is still when the last GPS fix is slower than 0.7 m/s and it has not stepped 4 m. A fix coarser than 100 m, a missing fix, or a paused receiver does not open a place, does not learn, and does not warn.

A moving phone does not open a place. Cells heard while moving are not added to a place the phone is only passing.

The first still visit to a place only learns. Every distinct serving identity heard while still on that visit is stored. Nothing warns on that visit. The visit ends when the phone leaves the cluster or the watch is turned off.

How long to learn can keep later visits quiet too. The choices are None, 1 day, 3 days, 7 days, and 14 days. The start is 3 days, counted from the moment the place was opened. Trusted networks uses those day lengths and also has 1 hour. None is only on this page. While that time lasts, a later still visit stores a new 4G or 5G cell and does not warn. None leaves the first visit as the only quiet visit. Changing the choice does not forget cells already saved. A place whose clock has run out warns on the next new cell.

A drop to 2G or 3G still warns on a later visit during that time, once the first visit has ended and the place has only learned 4G and 5G. That camp is not stored unless the person taps Learn this cell. Dismissing the drop does not store it either.

A later still visit can warn once the time has ended, or on the next visit when the choice is None. Learning continues either way: a cell that warned can be learned from the warning, and a cell that was used without a warning is stored too.

At most 32 cells per place. When the place is full, the next new cell replaces the one heard least recently. The screen can remove one cell or forget the place.

## Warnings

Two warnings. Each needs the place to already have a baseline from an earlier visit, and its switch on the settings page. Both switches start on.

**A cell this place has not used.** The phone is still, and the serving identity is not in the place. The PLMN is part of the identity, so a new network code on an otherwise familiar area is this warning. One episode per identity. Further reads update the episode. They do not open a second warning. The episode ends when that identity has been gone for 10 minutes, or the user dismisses it.

**A drop to 2G or 3G.** The place's learned generations are only 4G and 5G, and the registered cell is now GSM or WCDMA. This one also fires while the phone is moving inside that place. A place that already learned 2G or 3G does not fire it. A phone that is not inside a baselined place does not fire it. One episode per drop. It ends when the phone is back on 4G or 5G for 10 minutes, or the user dismisses it.

A new area code while still is not its own warning. It is already covered when the identity is new. An area-code change on a cell the place already uses, with the same cell ID, does not warn by itself.

Dismiss ends the episode and does not forget the cell. **Learn this cell** stores the identity on the place and ends the episode. **Forget this place** asks once, then deletes the cluster and its cells.

A warning is not repeated for the same episode. There is no confidence ladder in the first ship.

## Words

The warning sentence is one of these.

> This phone camped on a cell it has not used here.

> This phone dropped to 2G.

> This phone dropped to 3G.

Voice speaks that sentence, and only when a new episode starts. The settings page and the popup use the lines in those sections. New on-screen strings follow the language-pack rules. English stays in the app. All 26 bundled languages get the new lines. Pack text is not passed through `String.format`. Caps stay as they are. `writtenFor` stays the template's version.

## Popup

The warning uses the same dialog as a GPS warning. One dialog at a time. A second episode waits until this one is closed.

The title is the warning sentence.

The body is one short paragraph. A new cell says this phone is camped on a tower it has not used at this place. A drop names 2G or 3G. The next sentence says Fieldwatch cannot tell the cause. The body has no coordinates, and it does not say an attack was detected.

Details sits under the body, in the smaller muted style a GPS warning uses for its readings. One line each. A line the phone left empty is left out.

- Generation
- Network
- Country, when the country of the first three digits is known
- Operator, when the shipped list has one name for that code
- Area
- Cell
- PCI, when the phone filled it
- Channel, when the phone filled it
- Band, when that channel maps to a band. More than one band is named on the line.

The network number stays. A missing name leaves that line off. The name is who the code is assigned to. It does not name a cause. Signal and timing advance are not on the dialog.

The actions are one column, the way a trusted-network warning stacks its buttons. Learn this cell, then Dismiss. Learn this cell stores the identity on the place and closes the dialog. Dismiss closes it and does not forget the cell. Forget this place is not on this dialog.

## Sit record

When an episode's time falls inside a sit, Debrief text and the Debrief PDF get one block. The lines are the sentence, the time, and the identity fields the phone filled. Country and operator are included when the list has them. Signal and timing advance are included only when the phone filled them. The cell ID and the area code follow privacy mode, the same way a coordinate does. Generation, network, country, and operator stay. The block has no coordinates of its own. Compare does not add the block. The log is unchanged. AI Export pastes the sit report, so the block is included there.

## Settings

Settings gets one row, **Cell connection monitoring**. The row opens its own screen. The screen uses the same cards as Trusted networks and GNSS interference. The on/off switch sits on its own card. Each later control has one short line about what it does. The cards stay separate.

**Watch.** This card has no title of its own. The line at the top says this learns the tower the phone is camped on at a place, then warns when that camp changes. It does not see another phone. The switch is **Monitor the cell connection**. It starts off. The line under it says this turns warnings on and off. With it off, nothing warns. Places already learned stay.

**Warnings.** The title is Warnings. The line under the title says these run only while Monitor the cell connection is on.

**How long to learn.** The label matches trusted networks. The choices are None, 1 day, 3 days, 7 days, and 14 days. The start is 3 days. The line under the choices says the clock is counted from the first still visit, that a later visit stores a new 4G or 5G cell and does not warn while it lasts, that None stops when that first visit ends, that changing it does not forget cells already saved, and that a drop to 2G or 3G still warns after the first visit. The place row says Learning has 2 days left, or the hours or minutes that remain. The choices do not list the places. When the window is over, the row says Learning has ended. None has no clock. The same lines are in all 26 bundled languages.

- **Warn on a new cell.** Starts on. The line says it warns when the phone is still at a place it already learned, and the tower is one it has not used there.
- **Warn on a drop to 2G or 3G.** Starts on. The line says it warns when a place that stayed on 4G or 5G drops to 2G or 3G. In the US, a drop to 2G is the one to take seriously. Allow 2G can be turned off in the phone's own settings.

**How a warning reaches you.** The title matches Trusted networks. The line under the title says the dialog still shows if these are off, and that beep and voice on this page do not change the watchlist, GNSS, or trusted networks.

- **Beep.** Starts on. The line says it is the same sound as the other warnings.
- **Voice.** Starts on. The line says it speaks the warning sentence.
- **System notification.** Starts off. The line says it is a silent notice in the shade, and the dialog still shows.

**What this cannot see.** Three short lines, and no switches. Another phone, a SIM tracker, a silent text, an identity request, or the cipher. A quiet watch is an empty report. It is not an all-clear. If the phone has its own cellular-security notification, this card names that setting and says Fieldwatch does not read it.

**Places.** Until a place exists, one line says no places yet, and that a place is learned the first time the phone stays still there. The card then shows the place this phone is inside, when there is one, and the latest visits, up to 10. When more are saved, the last row is a count, such as 24 more places, and it opens the full list. That list groups Here, Today, This week, and Older, and only the rows on screen are drawn. The row title is the street and city when Online place names and maps is on and this phone is online. Offline, or with that switch off, the title is the coordinate of where this phone was standing. A name already saved stays hidden while that switch is off. Privacy mode masks the title and leaves the street off. When Online place names and maps is on, a map sits above the list. Each pin is a place where this phone stood. A tap opens that place. A pinch zooms and a drag moves the picture. A double tap returns to the full view. The place this phone is inside uses a different color. Back from a pin returns to this page with the map at the top. Privacy mode leaves the map off. With the switch off, the list is alone. With no connection, a picture already saved still shows. Where none was saved, the list is alone. Places far from this phone stay on the list. When there is no fix, the map uses the newest place. The lookup runs when the list is open and is kept on the place, so a later open does not ask again. Under the title, a row shows the generations it has used, how many cells, and the last visit. When this phone has a fix from the last 30 seconds, the row adds a line. **Here** means the phone is inside that place, within 300 m. Farther away, the line is a distance, in meters until 1 km and then in kilometers. The line is left off when there is no current fix. The row does not show a map. Tap a row to open that place. The back arrow returns to the list that opened it. From the full list, the next press returns to the card. From the card, the next press returns to the settings list. A swipe to the left asks to forget that place. The row snaps back, and the place stays until the question is confirmed. The question is the same one as on the place screen. There is still no cap and no automatic expiry. Forget stays a confirmed choice.

The place screen title is the same street or coordinate. Privacy mode masks it. Each cell is its own section, and that section title is the generation. Network, country, operator, area, and cell are lines in that section, with the label on the left and the value on the right. Country and operator are included when the list has them. PCI and channel are included when the phone filled them. Band is included when that channel maps to a band, with the downlink frequency. More than one band is named on the line, with or, and a shared frequency is shown once. A 2G channel from 512 to 810 lists the 1800 frequency and the 1900 frequency. A blank field is left off. Camped is a line in that section, with the label on the left and the number of stays on the right. A stay counts once. First camped and Last camped are lines in that section, with the label on the left and the time on the right. Signal is the strength of that last camp, in dBm, when the phone filled it. A cell saved before First camped uses the last time already stored. A later stay keeps that date and moves Last camped. A hop back during the same stay does not add another. The names are stored in the app. **Remove this cell** is at the bottom of that section. **Forget this place** sits below the sections. It asks once, then deletes the place and its cells.

New `AppSettings` fields have defaults so an old settings file still loads. The two warning switches default on. How long to learn defaults to 3. None is stored as 0. Places and cells are their own file, with `ignoreUnknownKeys` and defaults, same as the other stores. They are not stuffed into `AppSettings`. The length travels with Export settings. The places do not.

## Diagnostics

Settings → Diagnostics gets a short block, readings only:

- Whether a registered cell was returned
- Generation, when one was returned
- Which identity fields were empty
- How many neighbor cells were returned
- Whether a second SIM returned a registered cell

No network code, no country, no operator, and no GPS coordinates. An empty serving cell is a blank reading. It is not a warning.

## What the phone must not do

No call, no SMS, no radio transmit, and no extra location request. The GPS the app already runs stays as it is.

Do not put this phone's cell in the signature catalog, the live list, or Mine.

Do not claim the app detected a Stingray, an IMSI catcher, a jammer, or a spoofer. Do not use those words on screen.

## Tests

Unit tests on synthetic cell snapshots. No phone required.

- The first still visit to a place stores the cell and does not warn. With None, a later still visit with a new identity warns once. A repeat read of that identity does not open a second episode.
- With 3 days, a later still visit before the clock ends stores a new 4G cell and does not warn. At 3 days it warns and does not store that cell. A drop to 2G during the 3 days warns and is not stored, including after Dismiss.
- A new PLMN on an otherwise new identity is the same warning. A physical-cell-ID change on a stored identity does not warn.
- An unavailable cell ID does not learn and does not warn. An empty list does not warn.
- A drop to GSM warns when the place has only learned LTE and NR, including while moving inside that place. It does not warn when the place already learned GSM. It does not warn outside a baselined place. It does not warn when Warn on a drop to 2G or 3G is off. A new cell does not warn when Warn on a new cell is off.
- A moving phone does not open a place and does not add a cell to a place it is passing.
- A fix coarser than 100 m does not learn and does not warn.
- A 33rd cell at a place replaces the one heard least recently.
- Privacy mode masks the cell ID and the area code in the sit block. The generation, the network, the country, and the operator stay.
- A place with no phone fix, or with only one of the two coordinates, has no distance. At the place, the distance is inside 300 m. A point 400 m north of the place is outside.

## Later

Do not build these until a later request picks one. They are here so they are not forgotten.

**Neighbor-cell collapse.** One strong serving cell and no neighbors, at a place where this phone usually reports many neighbors. Confirm on the A54 and the Moto first. Diagnostics above is the evidence. If either phone returns no neighbors in ordinary use, this alert does not ship. A phone that never reports neighbors must say so. An empty neighbor list on that phone is not a collapse.

**Dual-SIM.** One SIM changed and the other stayed as it was. Many phones report only the active data SIM. Lab on both phones first, using the Diagnostics reading. No alert until both phones show a real second cell.

**Public cell pack.** Optional import. A cell missing from the pack, or listed far from this place, can add a note on a warning that already fired. It is never its own warning. OpenCelliD is CC-BY-SA and the full dump is too big for the APK. BeaconDB is the other candidate. Mozilla Location Service is gone. Check the license and the size of a clipped pack before any file format. Do not bundle a world database. Do not download one in the background. The country and operator names compiled into the app are a separate small list. They do not locate a tower.

**A fresher reading.** If a short drop to 2G is missed because `getAllCellInfo()` was stale, look at `requestCellInfoUpdate()` on the scan cadence. Do not add a second timer until that miss is real.

**Physical cell ID or channel change.** The same cell ID at a place, with a new physical cell ID or a new channel. Not a warning until the first ship has been used and this case shows up in a sit.

**Path map.** A mark on the sit path where the serving cell or the generation changed. The Debrief block is the record until then. No new map layer in the first ship.

**TAK pin.** Where the phone was when the episode was loudest, not a surveyed position of the tower. Same heard-here rule as a trusted-network pin. Default off. No pin in the first ship.

**Export.** Places and cells as a file that stays on the phone the user picks. Coordinates stay out of the file, the same way trusted-network places stay on the phone.

**Rayhunter.** The only path here to an identity request, a 2G redirect command, a null cipher, or an incomplete system message. Optional import of a hotspot log onto a sit, labeled as the hotspot's record, never as a reading from this phone. Hotspots people use: Orbic RC400L and TP-Link M7350. Before any import, read the NDJSON export and the web UI and write the field list down. Stock Fieldwatch still does not detect a Stingray. This import is not a paid upgrade of the same detector.

**Phone permission.** Only if Diagnostics on both phones shows an identity field empty that `READ_PHONE_STATE` would fill, and only after that is checked against the platform docs. The first ship does not ask for it.

**Android cipher notification.** Revisit only if the platform documents an API a normal app can read. Until then the help points at the setting and Fieldwatch does not read it.

**iOS.** Not a product. The radio-access type is all a stock iPhone app can see, and there is no cell ID. The help can say so in one sentence. No iOS work.
