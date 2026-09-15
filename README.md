# Fieldwatch

Fieldwatch is a receive-only Wi-Fi access-point and Bluetooth LE watcher for Android. It listens. It does not associate, inject, or talk to a Fieldwatch server. No account, no ads, no dongle.

It was Spectre through 1.2.14. This tree is the same field tool under a new name, a new application id (`app.fieldwatch`), and the MIT License. Installing Fieldwatch does not replace Spectre on a phone; it is a separate app.

This is a hobby. Sideload files live in `dist/` once a build is made. The public home for source and sideload will be this repository.

If you spot an error, something stupid, or have a feature idea — in the app or the documentation — please open a GitHub issue on [OffGridPete/Fieldwatch](https://github.com/OffGridPete/Fieldwatch/issues) once that repository is public.

## Safety & disclaimer

This is a hobby project, provided as-is under the MIT License. A few things to know before you do:

- Use at your own risk. Using Fieldwatch is your responsibility. To the maximum extent permitted by law, Off Grid Pete LLC is not liable for indirect, incidental, special, consequential, or punitive damages arising from its use.
- There is no guarantee that trackers, cameras, tags, access points, or any other device will be found, named, or reported. Radios that are off, cellular-only, asleep, randomized, quiet, or outside what this handset’s OS exposes will not appear. Each phone has its own radios, firmware, scan quotas, and OEM battery policies. Software cannot address those limits.
- Pattern matches, GPS co-travel (“Moving with you” / “possible tail”), Debrief language, and AI Export output are hypotheses — not identity, not a legal finding, and not a complete RF capture. You are solely responsible for how you use this app and this document, and for complying with local law. By using the software or this manual you accept these terms and the MIT License.
- Location data, if tagging is on, is this phone at hear-time — not the other radio. There is no Fieldwatch server. Stamps stay on the handset until you share them. Logs keep full coordinates even when Privacy mode masks the screen and sit reports. Debrief, Share log, AI Export (sit or one radio), and radio-detail Share as text can take that path off the phone. Online place names use the system geocoder (often the OEM / Google network), not a Fieldwatch cloud. How you store, share, or publish those files is your responsibility.

## Put it on a phone

Copy the `dist/` folder onto the phone (USB, Drive, or Files) and open `Fieldwatch.apk`.

| File | What it is |
|---|---|
| `dist/Fieldwatch.apk` | Sideload APK |
| `dist/instruction.txt` | Permissions, first launch |
| `dist/Fieldwatch_User_Manual.pdf` | User manual |
| `CHANGELOG.md` | What changed in each build |
| `LICENSE` | MIT License |
| `NOTICE` | Third-party attribution |

Android 10+. Allow install from the app you used to open the APK. Play Protect may warn that it is not from Play — expected. Full steps are in `instruction.txt`.

```bash
adb install -r dist/Fieldwatch.apk
```

Fieldwatch will not overwrite an existing Spectre install (`app.spectre`). Uninstall Spectre when you no longer need it.

Signature packs exported from Spectre (`spectre-signatures`) still import.

## What Fieldwatch is not

- Not Wi-Fi clients, probe-only stations, or 802.11 monitor mode
- Not Bluetooth Classic inquiry (HC-05 / HC-06 will not appear)
- Not cellular
- Not direction finding

## Copyright and license

Copyright (c) 2026 Off Grid Pete LLC.

Fieldwatch source is licensed under the [MIT License](LICENSE). AndroidX, Kotlin, and related libraries remain Apache-2.0. IEEE and Bluetooth SIG assigned-number tables in `radiodb.bin` are subject to those organizations’ terms. See [NOTICE](NOTICE).
