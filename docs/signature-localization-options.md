# Signature localization

Vision for a later phase. This document does not authorize that work. Phase 1 is the app language pack in `docs/language-pack-requirements.md`. Phase 1 does not translate the catalog. Do not start this phase until asked.

The purpose of writing it now is to keep one picture of localization. The language pack and any later signature work should follow the same rules: English stays in the official files, a missing line stays English, a pack is display text, and Off Grid Pete LLC does not review or certify translations.

## Two kinds of signature text

A signature row has words the operator reads, and words the matcher uses. Only the first kind can ever be translated.

| Text | Where it lives | Later phase |
| --- | --- | --- |
| Class names, such as Finder tags, Drones, Public safety | App code, `SignatureClass.label` and `speechLabel`. The catalog stores the enum token `DRONE`, not the English words. | Can use the language pack. It is app chrome, not a catalog row. |
| Signature name, such as Apple AirTags or Actiontec | `Fleet.name` in the catalog | Leave in English. See below. |
| Notes on the radio detail | `Fleet.notes` | Translate with a display overlay. |
| Extra attention warning | `Fleet.attentionNote` | Translate with a display overlay. A pack must not silence it. |
| Decode labels, named values, and the sentences for those values | `Fleet.decode` labels, `enum`, `enumNotes` | Translate with a display overlay. |
| Live chips | The decode label the catalog already chose | Follows the overlay. The chip text is not a separate copy. |

These stay as they are. Translating them changes what matches, what is saved, or what another tool reads:

- Fleet ids, rule text, name globs, OUIs, manufacturer prefixes, service UUIDs, and hex.
- The class token stored in the file (`DRONE`, `VEHICLE`).
- Decode ids, offsets, types, endian, scales, units such as `kPa` and `°C`, and the `when` gates.
- Anything heard on the air.
- Names and notes the user typed, including custom signatures and watch labels.
- WiGLE CSV, sit and log headers, and TAK/CoT. The TAK callsign is built from the signature name. That feed keeps the English catalog name even if a screen someday shows a translation.

Update stock catalog from GitHub replaces stock rows, including notes and Extra attention, from the English v2 file. Bookmarks, settings, and signatures the user added stay. A translation that is written into a copy of that file is wiped by the next update, or it becomes a second catalog that can change match rules. That is the constraint every option has to respect.

## Option A. Class labels in the language pack

Class names and their spoken forms are about twenty app sentences. They are not in the signature file.

They can be keys in the phase 1 language pack. Missing keys stay English. No catalog file changes. Update stock catalog does not affect them. Voice that speaks the class uses the same line. Voice that speaks the signature name still uses the English catalog name.

This does not translate notes, Extra attention, or decode labels.

Recommendation: do this when the language pack is built, or as the first slice afterward. It does not need a signature project. Phase 1 as written leaves these English. Pulling them into the language pack does not open the catalog.

## Option B. A display overlay for catalog prose

Recommended later phase.

A second file, separate from the language pack and separate from a signature pack, supplies translations for stock display text. The stored catalog stays the English catalog plus anything the user edited or added. Matching still uses that catalog. The overlay is consulted only when the screen, a spoken class line, a report, or a PDF needs words.

```json
{
  "format": "fieldwatch-signature-text",
  "formatVersion": 1,
  "language": "Français",
  "writtenForCatalog": 92,
  "strings": {
    "fleet-axon.attentionNote": {
      "source": "<hash of the English warning>",
      "text": "<translation>"
    }
  }
}
```

The key is the fleet id plus the field. The `source` hash is the English sentence the author translated. If Update stock catalog changes that sentence, the hash misses and the screen shows the new English until someone translates it. This is stricter than the language pack, on purpose. Catalog warnings change under the same id, and an old translation of a warning can be wrong.

Rules for the overlay:

- Translate notes, Extra attention, decode labels, enum labels, and enum notes.
- Leave `Fleet.name` in English. Those names are product and maker names, they are what people learn, and the TAK callsign uses them. An author guide can say the name is not in the template.
- Do not overlay a row the user added. Do not overlay a stock note the user has edited. Compare the stored text with the stock English. If the user changed it, show what the user wrote.
- If the English Extra attention is empty, the overlay cannot add one. If the English warning is present, a blank translation shows the English warning. A pack cannot turn Extra attention off.
- A missing key, a hash miss, or a failed file shows English.
- The overlay cannot add a rule, change a class, change a decode offset, or add a field.
- Same injection rules as the language pack. Display text only. The official not-reviewed sentence is compiled into the APK and is not a key in this file.
- Screen fit uses the same slots. Notes and warnings are body text. Decode labels and live chips are controls.
- Import is its own Settings action, with a confirm dialog. It does not replace the language pack, and Import signatures does not apply it. Update stock catalog does not delete it.
- The author exports an English template of those display strings from the catalog version on the phone. They host the finished file. Fieldwatch does not download it and does not publish a signature-translation repo.
- `writtenForCatalog` is informational except for the hash check. A pack written for catalog 92 still loads on catalog 93. Lines whose English changed fall back. New rows stay English.

The language pack and this overlay stay two files. The app version and the catalog version move on different days. One file that held both would let a UI translation touch fleet ids, and a catalog update would force a new UI pack.

## Option C. A translated signature pack

Reject this as the way to localize.

Someone translates `fieldwatch-signatures-v2.json` and the user imports that file. The translation is mixed with the match rules. A translator can change a glob, an OUI, or an Extra attention flag while editing prose. The next Update stock catalog from GitHub replaces the stock rows with English. A user who skips that update is stuck on a stale catalog.

A full translated catalog is a fork. It can change behavior. The language-pack rule is that a translation cannot change behavior.

## Option D. Languages inside the official catalog

Reject this too.

French notes in the stock file, or `notesFr` fields beside the English, would make Off Grid Pete LLC the publisher of those translations. Every catalog bump would carry every language. Older apps would have to ignore the extra fields. It contradicts the decision that translations are not reviewed and do not live in an official repo.

## Whole picture

| What the user reads | When | How |
| --- | --- | --- |
| Buttons, dialogs, Settings, the tour, fixed spoken phrases | Phase 1 | Language pack. English in the APK. |
| Class names and class speech | With the language pack, or immediately after | Same language pack. App strings. |
| Stock notes, Extra attention, decode labels | Later, if built | Display overlay. English catalog stays the matcher. |
| Signature names | Not translated | English catalog name on screen, in voice, and in TAK. |
| User-written names and notes | Never replaced | The user already chose the words. |
| Match rules and protocol | Never translated | English tokens and bytes. |

Every pack stays a file the user imports. A missing line stays English. Nothing is downloaded. Nothing is hosted as an official translation. The not-reviewed sentence stays official APK text.

## Not decided here

This file records the options and the recommended shape. It does not set the hash algorithm, the Settings wording, or the template layout. Those belong to the implementation request, if this phase is started.
