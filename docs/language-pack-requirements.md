# Fieldwatch language pack

Requirements for a later implementation. This document does not authorize that work by itself. Do not start it until asked. When asked, implement this document and stop there. Do not ship, tag, sideload, or open a GitHub Release unless that request says so.

The official Fieldwatch APK stays English. Someone else may write a language file. A user imports that file on the phone. They do not fork the repo, they do not sign an APK, and they do not publish another Fieldwatch. Off Grid Pete LLC does not review language packs. The app must say that before a pack is applied.

## Outcome

Settings can import one language pack, export an English template, and remove the pack. With no pack, every app sentence is the English compiled into the APK. With a pack, a line the pack translates is shown in that language, and every other line stays English.

The pack changes words on the screen. It cannot add a control, change what a button does, change a setting, change the catalog, open a link, or replace the APK.

## No code, no injection

This is a hard requirement. A language pack is untrusted data from someone Off Grid Pete LLC did not review. The implementation has to make code injection impossible, not unlikely.

The JSON parser is the only thing that reads the file. Use the kotlinx.serialization parser the app already uses. Decode into one fixed data class whose `strings` field is a map of string to string. Do not write a custom parser. Do not enable polymorphic deserialization, and do not decode into a free-form tree that the app then interprets. Unknown fields are discarded. Strict JSON only: no comments, no trailing commas, no YAML, and no second document after the object.

The parser's job ends when those strings exist. It does not decide whether a sentence is malicious. A line that contains `<script>` or a URL is still display text. After the parse, nothing in the app evaluates, compiles, interprets, or looks up code from a pack key or a pack value. There is no second pass that treats the text as a command.

A denylist of words such as `script` or `javascript:` is not the protection. Those characters may appear on screen as text. The protection is that no Fieldwatch code path can execute them.

These paths stay closed to pack text:

- No `eval`, script engine, JavaScript, WebView, `Html.fromHtml`, or HTML-to-PDF step.
- No `String.format`, `MessageFormat`, or any other formatter. `{n}` is one literal replace with a value the app supplies. The pack string is not scanned again after that replace. `%s` and `%n` are characters.
- No linkifying, no `ClickableText` URL, and no intent built from the text. A visible URL is not opened and not fetched.
- No use of the text as a file path, content URI, preference key, database string, log format, route, class name, or reflection target.
- No `Runtime.exec`, dynamic code loading, native library, or added Android component. The file is one JSON object, not a zip, an APK, or a dex.
- The language tag does not change the Android locale.
- Spoken phrases go to text-to-speech as plain text, not markup.
- PDF and notification text are drawn as plain characters.
- A translated line written to CSV still goes through `CsvCells`.
- The official sentence that packs are not reviewed or certified is compiled into the APK. The pack cannot replace, shorten, or hide it.
- Keys the app does not already have are dropped. A key cannot select a feature.

The app stores only the cleaned string map it wrote itself. On the next start it parses that file with the same rules. If that parse fails, the phone returns to English. The original import file is not kept and not parsed again.

Reject the whole file when the JSON is nested deeper than one object of strings, when a value is not a string, or when the file is larger than 256 KiB. Drop a line that is longer than its screen slot allows, as specified under Screen fit. Strip control and bidirectional characters at import, as specified below. Do not run those checks by executing the string.

## Where packs live

Do not add a translations folder to the Fieldwatch repo. Do not create an Off Grid Pete language-pack repo, and do not have the app download packs from GitHub or from anywhere else. A pack hosted by Off Grid Pete LLC would read as official even with a README that says the files are not verified.

A translator copies the English template, fills in the lines they want, and hosts that JSON file themselves. The user obtains the file and imports it. The phone keeps one cleaned pack in app-private storage. Export settings does not include it.

## What is translated

Translate app chrome: sentences and labels the app itself writes.

That includes Compose text, button labels, dialog titles and bodies, empty states, Settings rows, Diagnostics, the Live tour, flood dialogs, notices, content descriptions, notification text the app writes, fixed spoken phrases the app composes, share-sheet subjects, and the headings and sentences in on-screen reports and in the Debrief and Compare PDFs.

A line that inserts a number or a name is one sentence with a placeholder, such as `{n}`. The app supplies the inserted value. The pack supplies the sentence.

## What stays as it is

These stay out of the pack:

- The launcher name. `strings.xml` keeps `app_name` as Fieldwatch.
- The user manual, `instruction.txt`, the changelog, and the README. Those are English documents.
- The stock catalog and any catalog the user imports. Signature names, notes, Extra attention, and decode labels stay in that file. Class names are app sentences, not catalog rows. Phase 1 leaves them English. A later phase can put them in this pack. Catalog prose is a separate overlay, described in `docs/signature-localization-options.md`. Phase 1 does not build that overlay.
- Anything heard on the air: names, SSIDs, manufacturer data, OUIs, MACs, RSSI.
- Anything the user typed: observer notes, custom signature names, watch phrases, preset names, sit notes.
- Machine-readable exports. WiGLE CSV, sit and log CSV column headers, TAK/CoT tags, and other protocol or file-format tokens stay English. A translated header would break a file someone already parses.
- Numbers the app formats for files and coordinates. Those keep `Locale.US` and the existing `String.format` calls. A pack string is never the format pattern.
- Which voice line is spoken, and in what order. A pack may translate a fixed phrase. It must not change the watch priority. The first matching signature watch still gets the single spoken phrase. A named radio with Alert on still speaks its own name only when that watch receives the phrase. A phrase the user wrote is spoken as stored.

Decoded values, radio rows, and catalog hits are data. The Live row fields that change all the time (name, MAC, RSSI, and the catalog class) are not looked up in the pack.

## Fallback

English in the APK is the source of truth. The lookup returns the pack line only when all of these are true:

- A pack is loaded.
- The key is present.
- The value is not blank after trimming.
- The placeholders in the pack line are exactly the placeholders in the English line. Same names. Order may differ. Repeating a placeholder the English line uses is fine.

Every other case returns the English sentence:

- No pack is loaded.
- The pack file failed to load.
- The key is absent.
- The value is empty or whitespace.
- The pack line drops a placeholder, or it adds a `{token}` the English line does not have.
- The app is about to fill a placeholder and does not have a value for it.

A translation that is present and complete is shown even when the pack was written for an older Fieldwatch. If the English sentence later changes under the same key, the old translation stays until the pack is updated. The app does not compare the wording. If that English change adds or removes a placeholder, the placeholder rule above sends that one line back to English.

An unknown key in the pack is ignored. The pack cannot create a label the app does not already have.

## Lookup and runtime cost

There is one lookup, usable from Compose and from the domain layer (reports, PDF, notifications, voice). It is not a Composable-only helper.

The English sentences live in one compiled map of key to text. Screens read that map through the lookup. A new sentence is added to the map and read through the lookup. A literal written only at the call site will never be translated.

With no pack, the lookup checks that the map is absent and returns the English string. It does not read a file and it does not parse JSON.

On import, the file is parsed once, checked, and stored as the cleaned map. On process start, that stored map is loaded once before the first screen. The open screen redraws once when a pack is applied or removed.

A lookup is a map read, a blank check, and a placeholder-name check against the English line. Placeholder sets for the English lines are computed when the English map is built. Filling `{n}` is a single plain replace with the value the app passes. The inserted value is literal text. It is not scanned for placeholders or format markers. `%s` and `%n` in a pack line are characters on screen.

Do not parse JSON when a screen opens. Do not search the pack by the English sentence. Do not repeat the import checks on each frame. Do not run `String.format` on pack text. Do not pass pack text through HTML, `AnnotatedString` link annotations, or a WebView.

Scanning, signature matching, and laying out the Live list remain the work that dominates a frame.

## File format

One JSON object, UTF-8. A leading byte-order mark is stripped before parsing. Unknown top-level fields are ignored. `strings` values are JSON strings. A value that is an object, array, or number is dropped.

```json
{
  "format": "fieldwatch-language",
  "formatVersion": 1,
  "language": "Français",
  "languageTag": "fr",
  "writtenFor": "1.1.21",
  "strings": {
    "settings.import_language": "Importer un pack de langue…",
    "live.heard_ago": "entendu il y a {n} s"
  }
}
```

| Field | Rule |
| --- | --- |
| `format` | Required. Must be `fieldwatch-language`. |
| `formatVersion` | Required positive integer. Version 1 is the version this app writes. A higher version still loads when `strings` is a string map. The confirm text says the pack format is newer and only the lines were used. |
| `language` | Required display name, 1 to 40 characters after trim and cleaning. |
| `languageTag` | Optional. Stored and shown. It does not change the Android locale, layout direction, or number formats. If it is not a short BCP 47 tag, ignore the tag and keep the pack. |
| `writtenFor` | Optional, at most 32 characters. The Fieldwatch version the author used. Informational. A mismatch does not reject the pack. |
| `strings` | Required object. Key to line. Missing keys stay English. |

Keys in the file use the same ids as the English map. A key is ASCII lowercase letters, digits, and dots, for example `settings.diagnostics` or `live.flood_hide`. The English sentence is not the key.

Reject the whole file, and leave any pack already on the phone in place, when:

- The file is empty, is not UTF-8, or is larger than 256 KiB.
- The root is not one JSON object.
- `format` is missing or wrong. A settings pack gets "That is a settings pack. Use Import settings." A signature pack gets "That is a signature pack. Use Import signatures." Anything else gets "Not a Fieldwatch language pack."
- `formatVersion` is missing or not a positive integer.
- `language` is missing or blank after cleaning.
- `strings` is missing or is not an object.
- The file has more than 2000 entries in `strings`.
- After the line rules below, no known key has usable text.

Drop a single line, count it, and keep English for that key when:

- The key is not in the English map.
- The value is empty, whitespace, or longer than the slot for that key allows.
- The value contains a newline and the English line does not.
- The placeholder names do not match the English line.

Cleaning, applied to the language name and to each accepted line:

- Remove ASCII controls other than a newline the English line already allows. Remove other control and format characters, including the byte-order mark and the bidirectional marks U+200E, U+200F, U+202A through U+202E, and U+2066 through U+2069.
- Change carriage return to nothing.
- If cleaning leaves the language name empty, reject the file. If cleaning leaves a line empty, drop that line.

The stored pack is the cleaned result the app writes: language name, accepted tag, `writtenFor`, and the accepted key-to-line map. The original file is not kept. Lookup still applies the placeholder rule against the current English map, so an app update that changes placeholders falls back to English for those keys without another import.

## First launch

The phone language does not switch Fieldwatch. It only decides whether to show a one-time door. The app stays English until a pack is imported.

Show the door once, before the disclaimer, when the phone language is not English, no pack is loaded, and the user has not already dismissed it. Import opens the file picker immediately. If the user backs out of the picker, the door stays up. Continue in English closes the door and stores that choice. Do not show it again after an update. Settings keeps Import language pack for later.

The door says that Fieldwatch is in English, and that the user can import a language-pack file if they already have one. It does not say a pack is waiting to be downloaded.

The door also shows this official sentence: "Language packs are made by other people. They are not reviewed or certified by Off Grid Pete LLC."

That sentence, and the rest of the door, is compiled into the APK. It is not a pack key. A pack must not be able to replace, shorten, or hide it. For a phone language you are willing to write yourself, ship that short door in that language. Any other language gets the English door.

## Import, replace, and remove

Settings gets three actions, placed with the other import and export actions:

- Import language pack…
- Export English template…
- Remove language pack, shown only while a pack is loaded.

Also show the current language: "English", or the cleaned language name.

Import uses the same kind of document picker as Import settings. Reading and parsing run off the main thread. Nothing is stored until the user confirms.

The confirm dialog shows:

- The language name.
- `writtenFor` and this phone's version, when `writtenFor` is present.
- How many lines will change.
- How many English lines will stay English.
- How many lines in the file were ignored, including lines that were too long for their slot.
- The same official sentence as the door: "Language packs are made by other people. They are not reviewed or certified by Off Grid Pete LLC." This sentence is compiled into the APK and is not a pack key. The pack cannot replace it.

The buttons are Import and Cancel. Cancel leaves the current language alone. Import replaces the whole pack. It does not merge. A key that was translated before, and is absent from the new pack, returns to English.

Remove asks for confirmation, then deletes the stored pack and redraws the open screen in English.

These leave the language pack alone:

- Import settings and Export settings.
- Import signatures, export signatures, and Update stock catalog.
- Restore defaults.

A language pack leaves the catalog, settings, watches, logs, and sits alone.

## English template

Export English template writes a `fieldwatch-language` file from the same English map the app uses. `language` is English. `writtenFor` is the current `versionName`. `strings` contains every key. A translator edits the values and the language name, and may delete keys they are not translating. Importing that template unchanged shows English.

There is no second hand-maintained copy of the English text. The template is generated from the map.

## Keys and new copy

Keys stay stable. Renaming a key makes existing packs show English for that line. Prefer a new key over renaming one.

Changing the English text under the same key is allowed. Packs keep their old line until they are updated, unless the placeholders changed.

The first implementation moves the existing user-visible chrome onto the map. Wording stays the same. This is not a copy edit.

Every later user-visible sentence goes into the map. Tests cover the lookup. A test lists the English keys, checks they are unique and well formed, and checks that every placeholder in the English text matches the token grammar `{` plus an ASCII identifier plus `}`.

## Placeholders and plurals

The token grammar is `{` + a letter followed by letters, digits, or underscores + `}`. The first version has no plural categories, no gender, and no select. A count is digits inside one sentence that reads correctly for any count. Translators write that one sentence.

The phone locale does not select a language. The imported pack is the only switch. Layout direction stays as it is today. The language tag does not flip the app to right-to-left.

## Screen fit

A single character cap does not keep the screens looking right. A tab and a warning dialog do not have the same room. The layout fits a normal translation. The cap only rejects a line that cannot look acceptable in its slot. Do not shrink type to fit.

Each English key has a slot, compiled into the app. The pack cannot change a key's slot. Count characters after cleaning. A line over its cap is dropped and that key stays English. The confirm dialog counts how many lines were too long.

| Slot | Where it is used | Cap | How it is drawn |
| --- | --- | --- | --- |
| `tab` | Bottom bar: Live, Filters, Signatures, Reports, Settings | 18 characters | At most two lines. A longer word stays inside its tab. |
| `control` | Chips, buttons, one-line rows, and fixed spoken phrases | Twice the English length, at most 48 characters | Chips stay one line and ellipsize. The row of chips wraps. Full-width buttons may use two lines. List rows keep today's one-line ellipsis. A spoken phrase stays a short phrase. |
| `body` | Dialogs, the tour, empty states, Diagnostics explanations, report sentences, and PDF sentences | Three times the English length, at least 80 characters, at most 700 | Wrap. Dialogs and pages scroll. Do not ellipsize a warning. PDF text wraps inside the margins. |

The file cap of 256 KiB, the language name cap of 40 characters, and the cap of 2000 keys stay. Those protect the phone from a huge file. They are not the visual rule.

The English template may include a `slots` object so an author can see the slot for each key. Import ignores that object. If it disagrees with the app, the compiled slot wins.

A test pack, used in tests and not shipped, fills every key to its cap so the longest accepted translation is checked on screen. Do not ship that pack.

## Safety

Follow "No code, no injection" above. That section wins if another line in this document is looser.

- Cap the file at 256 KiB, each line at its screen-fit slot, the language name at 40 characters, and the map at 2000 entries.
- Strip the control and bidirectional characters listed above at import.
- The name shown on the confirm dialog is the cleaned name.

## Tests

The implementation is done when these are true:

- No pack returns English, and the lookup does not read a file.
- A missing key, an empty value, and a whitespace value return English.
- A complete line returns the pack text, including when `writtenFor` is an older version.
- A dropped or extra placeholder returns English.
- `{n}` is filled by a plain replace. A value that itself contains `{n}` or `%s` is inserted once, as characters.
- `%s` inside a pack line is shown as characters. `String.format` is not called with pack text.
- A line containing `<script>`, `javascript:`, `intent:`, `file:`, `content:`, or a URL is shown as characters. The test fails if any of those cause a WebView, an HTML render, a fetched URL, or an intent.
- A nested JSON value, a second document after the object, and a file that is a zip or an APK are rejected. A pack already on the phone stays in place.
- The official not-reviewed sentence is the compiled sentence, even when the loaded pack has a key that tries to replace it.
- Applying a pack does not change the Android locale, the watchlist, settings, the catalog, or which voice phrase is chosen.
- An unknown key is ignored. An unknown top-level field is ignored.
- Empty file, garbage, a settings pack, a signature pack, a file over 256 KiB, and a pack with no usable known lines are rejected. A pack already on the phone stays in place.
- Newlines are kept only when the English line has a newline. Bidirectional marks are removed.
- Confirm is required before apply. Cancel does not change the language.
- A second pack replaces the first. Keys only in the first pack return to English.
- Remove restores English and deletes the stored pack.
- Import settings, import signatures, catalog update, and restore defaults do not change the language pack.
- Process start loads the stored map once. A stored line whose placeholders no longer match the English falls back to English.
- A translated line written to CSV still gets the formula guard.
- The English template contains every key and is generated from the map.
- Fixed voice phrases can be translated. The choice of which phrase speaks is unchanged. A user-written phrase is unchanged.
- WiGLE and other machine-readable headers stay English.
- The launcher name stays Fieldwatch.

## Author guide

People who write a pack need their own English instructions. The phone manual is for the person importing a file. The author guide is for the person producing one. Write the author guide with the feature, after the English map exists, so it can point at Export English template and not at a hand-copied key list.

Put the guide in this repo as documentation of the format. It does not contain translated packs, and it is not a place for people to upload one.

The guide needs to say:

- A pack is a JSON file. It does not replace the APK, and it is not a fork. Off Grid Pete LLC does not review or certify it. The author hosts the file. Fieldwatch does not publish it or download it.
- Start from Settings → Export English template on the Fieldwatch version being translated. Keep the keys. Edit the values. Delete any key that is not being translated. Do not invent keys, rename keys, or translate by searching the source for English sentences. A sentence that is not in the template cannot be translated.
- A missing or blank key stays English. That is expected.
- Keep every `{token}` exactly as exported, including the braces and the token name. Do not add tokens. Do not use `%s`. A line that breaks this rule is shown in English.
- The text is display text. HTML, scripts, links, and format strings are shown as characters or dropped. They do not run.
- Stay inside the slot for each key. A tab is at most 18 characters. A chip, button, row, or spoken phrase is at most twice the English length and never more than 48 characters. A dialog or other long sentence is at most three times the English length and never more than 700 characters. The language name is at most 40 characters. The file is at most 256 KiB. A line that is too long is left in English. A newline is kept only when the English line has one.
- Set `language` to the name users should see, and `writtenFor` to the version of the template. One pack replaces another. It does not merge.
- The sentence that packs are not reviewed or certified is official text. It is not in the template, and a pack cannot change it.
- Tell users to import the file from Settings, or from the first-run door when their phone is not in English. The guide can describe those steps. The in-app wording for phone users still belongs in the user manual.

## Manual later

When this is actually shipped, add a short English section to the user manual for import, remove, partial packs staying in English, and the fact that packs are made by other people and are not reviewed or certified by Off Grid Pete LLC. Add a changelog line. The manual stays English. Do that in the ship, not as a drive-by while the feature is still unreleased. The author guide above is a separate document.
