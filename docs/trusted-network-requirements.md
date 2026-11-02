# Trusted networks

Requirements for a later implementation. This document does not authorize that work by itself. Do not start it until asked. When asked, implement this document and stop there. Do not ship, tag, sideload, or open a GitHub Release unless that request says so.

Fieldwatch watches Wi-Fi networks the user has marked trusted and alerts when a sighting looks like someone else using that name. Nothing else is flagged. The phone stays receive-only. This work uses scan results the app already collects. It does not connect, probe, or transmit on its own.

## Out of scope

- No change to GNSS detection, the GNSS dialog, the red line, or the TAK line on the Fieldwatch marker. A GNSS pin is a separate decision and is not part of this work.
- Floods stay a note on the Fieldwatch marker. They do not get a pin.
- No catalog change. Trusted networks are not signatures.
- No user-manual rewrite unless a later request asks for one.

## Outcome

On a Wi-Fi detail page the user can trust that network's name. Fieldwatch remembers the access points it was shown, the security those access points used, and the places the phone was. Later, a sighting of that name that is weaker, from an unknown maker, or far from those places can alert. When TAK/CoT is on and this feature's pin switch is on, the alert also drops a pin where the phone was when that suspect was loudest. That pin switch starts off.

Untrusted names never alert. A trusted name that still matches what was learned stays quiet.

## Marking trust

The control is **Trust this network**. It sits on Wi-Fi device detail, next to Mine. It is absent on Bluetooth. Mine still means one radio stays listed and does not beep. Trust means the network name is watched. The two switches do not change each other.

Trust is the SSID string Fieldwatch already stores (`ssidClean()` in `WifiRadio.kt`). Match is exact and case-sensitive. Fieldwatch does not have the raw 802.11 bytes. A blank name, a hidden network, and the placeholder `<unknown ssid>` cannot be trusted. The switch stays off and explains that a hidden name cannot be watched.

Turning trust on does not silently keep every access point in view. The phone lists the access points that share that exact name right now, with vendor, band, and security, and the user unchecks any that are not theirs. Only the checked ones are learned. If several vendors are already on that name, the list says so. The user can still trust it. A different maker is not learned later by the learning rules. It is checked here, or learned from an alert with **Learn this access point**.

A helper, **Also trust these?**, lists other names visible now that share a learned access point's OUI and the same security. Each is unchecked. Similar-looking names are never grouped in, and nothing in that list is trusted until the user ticks it. The help on that screen says only the exact name is watched. A guest name, a 5 GHz name, and a name one character off are left alone.

A long-press on the Live list is not required.

## What is stored

One record per trusted SSID, in its own store. Not in the signature catalog and not in the live device list.

For each learned access point:

- BSSID
- OUI and vendor, when the address is a real factory OUI
- Band and channel
- Security profile
- First seen, last seen
- Loudest RSSI, and the phone's position at that loudest hear, when a good fix exists

Security is a set of profiles, not a single "strongest." WPA2 on one band and WPA3 on another are both normal. Below Android 11 the profile comes from the capabilities string. From Android 11 up, reuse `WifiIeParser` (RSN and WPA). From Android 13 up, `ScanResult.getSecurityTypes()` may refine that. Rank used only for downgrade checks: open, then WEP, then WPA, then WPA2, then WPA3. A mixed profile stores each level that was actually seen. OWE is not treated as open.

A blank security reading is not open. Open is an explicit open network. A sighting that includes a weaker profile and a stronger one is judged by the stronger one. A blank reading does not downgrade, and it is not stored as open.

Places are a small set of clusters, at most eight. A new fix within 300 m of a cluster updates that cluster. The screen never shows coordinates. A cluster is "a usual place," and the user can remove one. Clusters are not written into the export file. When the name already has eight places, the next place replaces the cluster that was updated least recently. **Remember this place** uses that same rule.

An ignored address is stored on the record and is not a learned access point. Its maker and security do not join the baseline. The user can stop ignoring one. Ignored addresses are included in the export. Places are not.

A locally administered address (the local bit) has no vendor. It is stored as the BSSID only.

## Learning

**Learn new access points** defaults on. The learning window defaults to 3 days from the moment that name was trusted. The choices are 1 hour, 1 day, 3 days, 7 days, and 14 days. 1 hour is stored as 0. The name row says Learning has 2 days left, or the hours or minutes that remain. The choices do not list the names. When the window is over, the row says Learning has ended. The same lines are in all 26 bundled languages. Turning the switch off hides the lines. Pause learning does not stop the clock. While the window is open, and learning is on:

- A new BSSID with the same name, a security profile already in the set, and a good fix near a usual place is added silently.
- A sibling is the easy case: same OUI, same security, only the last bytes differ, near a usual place. A locally administered address is not a sibling.
- A new BSSID with the same OUI and the same security, but not a sibling, is information only until it has been heard on 2 fresh scans near a usual place. Then it is learned. It does not alert.

A known BSSID heard far from every usual place, with security already in the set, adds a place and does not alert. That is the router traveling with the user. After the window, a far sighting alerts instead, and the alert can offer **Remember this place**. Remembering adds the cluster and ends that away-from-home episode. With eight places already stored, that new place replaces the cluster updated least recently. A different factory maker is not added by these rules, during the window or after it.

**This network travels** is off until the user turns it on for that name. While it is on, the away rule stays off, new places are not added, and **Remember this place** is not offered. Weaker security and an unknown maker still alert.

Pausing learning keeps the record and stops new access points and new places. **Forget this network** asks once, then deletes the record for that name. Learned access points, ignored addresses, places, and any open episode go with it. Pins for that name get the tombstone the feed already uses for a dropped marker. The name is no longer watched. Removing one access point, or one usual place, does not forget the name.

A fresh scan is one `fresh` result from `WifiRadio`, not a second read of the cached list.

## Detection

The master switch defaults off. While it is off, nothing alerts and no pin is sent. Records stay. Each rule below has its own switch. All three default on. Confidence uses the existing Low / Medium / High labels.

A hit needs 2 fresh scans of that suspect BSSID before it alerts. A hear quieter than the loudness floor does not count toward those two scans and does not alert. The floor defaults to -85 dBm, so -86 dBm and quieter stay quiet. The settings screen can change it. Learning during the window can still accept a quiet access point at a usual place. One episode per suspect BSSID. Further scans of the same suspect update the episode. They do not open a new alert. A rise in confidence may notify again. The episode ends when that BSSID has been absent for 10 minutes, or the user dismisses it. Dismiss does not ignore the address. An ignored address produces no later episode.

**Security downgrade** (Medium, or High when the sighting is open or WEP and the baseline has never been either). The name is seen with a profile weaker than every profile in the set. The comparison uses the stronger profile in that sighting. Open and WEP always qualify when the set has never included them and the sighting has no stronger profile. This fires for any BSSID on that name, learned or not. A WPA2 sighting is not a downgrade when WPA2 is already in the set, including when WPA3 was also seen. A blank security reading does not fire this rule.

A shared-password sighting is also a downgrade when every learned profile for that name is enterprise login. Enterprise is 802.1X, FT-802.1X, 802.1X-SHA256, SUITE-B, and SUITE-B-192, and the same words in the capabilities string on older Android. A shared password is PSK, FT-PSK, PSK-SHA256, SAE, and FT-SAE. A higher version rank does not save it, so WPA3-SAE against an 802.1X-only set still downgrades. When the learned set already contains a shared password, a shared-password sighting is not a downgrade on this rule.

**Unknown vendor** (Medium). The name is seen from a BSSID whose factory OUI is not among the learned access points. A locally administered address does not fire this rule. A sibling that is still inside the learning rule does not fire it either.

**Away from home** (Medium). The name is seen more than the distance from every usual place. The default distance is 1 km. This rule stays off until that name has at least one usual place. An imported record has none. A name trusted while location is off has none. Those hears do not alert as away. **This network travels** also leaves the rule off. The fix must be good: accuracy tighter than 100 m, and not a paused receiver. A coarse fix, a missing fix, or a paused receiver does not alert and does not add a place. During the learning window, a known BSSID with matching security takes the "add a place" path above instead of this alert.

**Stronger signal.** When this hear is at a usual place and at least 15 dB louder than the loudest RSSI stored for that place, confidence goes up one notch. This is not its own alert. No stored RSSI means no notch.

**OUI 00:13:37.** Information only. The IEEE registry lists that block as Orient Power Home Network. The warning line is "{oui} belongs to Orient Power Home Network." It does not raise confidence and it does not alert by itself. The screen does not name any tool.

Known limit, stated in the help on that screen: an access point that copies a learned BSSID and a learned security profile is not caught, except when it is far from every usual place after the learning window. Only the exact name is watched. Catching a sighting can take a few minutes because Wi-Fi scans are throttled (about four scans in two minutes unless the phone's faster scan is on, then about every 8 seconds).

## Alerts

Wording names what was seen. It does not say an attack was detected, and it does not say a jammer or a spoofer was found.

> Possible evil twin of "{ssid}": {reason}

Reasons stay short: "open security", "weaker security", "unknown device", "away from usual places". Two reasons can be joined with a comma. The OUI note, when present, is a second line and not part of the reason.

The alert follows the GNSS pattern: popup, optional beep, optional voice, optional system notification, and a minimum confidence. Defaults: beep on, voice on, notification off, minimum Medium. These switches belong to this feature. They do not change the watchlist or GNSS switches. Voice speaks the same sentence, and it repeats only when confidence rises.

When the suspect address is not already learned, the alert offers **Learn this access point** and **Ignore this address**. Learn adds that BSSID, its maker when the address has a factory OUI, its band, and the security just seen. Ignore stores the address only. It is not learned, and its maker and security do not join the baseline. Either choice ends the episode. A pin already sent for that BSSID gets the tombstone. Later hears of an ignored address stay quiet. A learned address that is only away from home offers **Remember this place** instead, unless **This network travels** is on. **Forget this network** is on the trusted-network screen, not on the alert.

On screen, MAC addresses follow the masking already used everywhere else. The alert does not show coordinates.

The sit report includes one block when an episode occurred during that sit. Debrief text and the Debrief PDF use it. The lines are the name, the reason, and the time. The address is included and follows the same masking as the rest of the report. That block has no coordinates. The log is unchanged. Compare does not add the block. AI Export pastes the sit report, so the block is included there.

## TAK pin

When TAK/CoT is configured, the feed is running, and this feature's TAK switch is on, an episode sends one point for the suspect BSSID. The TAK pin switch defaults off. With it off, an alert sends no pin.

The pin is where the phone was, not a surveyed position of the access point. It uses the heard-here rule in `TakPublish.heardHereAction`: the pin moves only when a later hear of that same suspect is louder. A weaker hear refreshes the same latitude and longitude on the normal interval so ATAK does not stale-drop it. The callsign does not ride along on the Fieldwatch self marker.

One pin per suspect BSSID. A different suspect BSSID is a different pin. The uid is stable for that BSSID for as long as the name stays trusted.

Callsign, trimmed like the other callsigns:

> Possible evil twin: {ssid}

Remarks: the SSID, the suspect BSSID and vendor, the reason, the confidence, and the time. No claim that the pin is the access point's own coordinates.

Color and CoT type must not be the Fieldwatch self marker (Cyan, uid `FIELDWATCH-SELF`) and must not reuse a heard-radio or Remote ID type. Pick one pair and keep it.

Stale time is how long the pin remains after the last hear. Choices are 1 hour, 6 hours, 24 hours, and until deleted. The default is 1 hour. Until deleted means the pin stays until the user forgets the network, removes that access point, ignores that address, or turns the TAK switch off. Those actions send the tombstone the feed already uses for a dropped marker.

Privacy mode already pauses the feed. This feature sends nothing while the feed is paused.

## Settings

Settings gets one row, **Trusted networks**, opening its own screen. That screen holds:

- Master on/off.
- The three rule switches: security downgrade, unknown vendor, away from home.
- Away distance, default 1 km.
- Loudness floor, default -85 dBm. A quieter hear does not alert.
- Learn new access points, and the learning-window length.
- Beep, voice, system notification, and minimum confidence.
- TAK pin on/off, and stale time. The pin switch defaults off.
- The trusted list. One row per SSID: learned access-point count, bands, last seen. Tap a row for the access points (BSSID, vendor, band, security), remove one access point, pause learning, turn on **This network travels**, review ignored addresses, or rename the row. Stopping an ignore leaves the address unlearned. **Forget this network** is a swipe or a tap on that row, and it is the same action as turning Trust off on the Wi-Fi page. It asks once, then deletes the name, including its ignored addresses. Rename changes the label the user sees. The match key stays the SSID.
- Export and import of the trusted list as JSON. The file includes names, learned access points, security, the travels switch, and ignored addresses. It does not include places or coordinates. The export screen says that places stay on this phone. Import of a broken file does not wipe the list already on the phone.

New `AppSettings` fields have defaults so an old settings file still loads. The learned list itself is not stuffed into `AppSettings`. It is its own file, with `ignoreUnknownKeys` and defaults, same as the other stores.

## What the phone must not do

No association, no `WifiManager` connect, no local-only hotspot, and no extra probe. The scan Fieldwatch already runs stays as it is. Do not describe that scan as passive.

Do not add a transmit path to "test" a network.

## Tests

Unit tests on synthetic scan snapshots, using `WifiIeParser.parseIes` where the frame matters:

- Open or WEP against a WPA2/WPA3 set alerts. WPA2 against a set that already contains WPA2 does not, even if WPA3 is also in the set. A blank security reading does not alert. A sighting that contains both open and WPA2 is judged as WPA2. PSK or SAE against a set that is only 802.1X alerts, including WPA3-SAE. PSK against a set that already contains PSK does not alert on that rule.
- Unknown factory OUI alerts. A locally administered address does not. OUI `00:13:37` does not alert by itself. A different factory maker is not learned by the learning rules.
- Far from every place, after the window, alerts. A fix coarser than 100 m does not. A known BSSID during the window adds a place and does not alert. Away does not alert when the name has no usual place yet. A ninth place replaces the cluster updated least recently. **This network travels** leaves away off and adds no place.
- A sibling near a usual place is learned and does not alert.
- One fresh scan does not alert. The second does. A repeat read of the same cached result does not count. A hear quieter than -85 dBm does not count and does not alert. A quiet hear at a usual place can still be learned during the window.
- One pin uid per suspect BSSID. A louder hear moves it. A weaker hear keeps the latitude and longitude. A second BSSID is a second uid.
- An untrusted name produces no alert and no pin.
- Learn this access point adds that BSSID and the security just seen, and it ends the episode. It is not offered for a learned address that is only away from home.
- Ignore this address ends the episode and stores the address unlearned. A later hear of that address does not alert, and the baseline is unchanged. Dismiss alone allows a new episode after the address has been absent for 10 minutes.
- The TAK pin switch defaults off. With it off, an alert sends no pin.
- Forget this network deletes the record. That name no longer alerts, and its pins are tombstoned. Removing one access point leaves the name in place.
- An old settings JSON without the new fields still decodes.
- A sit that contains an episode includes the trusted-network block with the name, the reason, and the time, and without coordinates. A sit with no episode omits the block.

Field check, when a phone test is asked for: mark the home mesh, after unchecking any neighbor on the same name, and confirm it stays quiet. Mixed WPA2 and WPA3 at home must stay quiet. Then, from a second phone, start a hotspot with the same name and open security. The watch phone cannot see its own hotspot. With the TAK pin switch on, that must raise a downgrade alert and a TAK pin at the louder spot. Repeat with a WPA2 hotspot whose OUI is not the home router's, and confirm the unknown-vendor alert. Confirm an untrusted name stays quiet.
