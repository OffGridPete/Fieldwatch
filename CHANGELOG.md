# What's new

Newest first. This is Fieldwatch (`app.fieldwatch`). Each build below is what Settings shows as the version.

Fieldwatch continues the Spectre 1.2.14 field build under a new name, application id, and the MIT License. It does not replace Spectre on a phone.

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
