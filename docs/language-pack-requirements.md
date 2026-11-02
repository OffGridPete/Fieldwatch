# Fieldwatch language pack

Requirements for a later implementation. This document does not authorize that work by itself. Do not start it until asked. When asked, implement this document and stop there. Do not ship, tag, sideload, or open a GitHub Release unless that request says so.

English in the APK is the source of truth. The APK also carries a bundled language for each available language listed below. Those files start as an AI draft. The copy in this repo is kept complete for the current English lines: every key is present, and every line fits its slot. A missing line stays English. The phone language selects a bundled language when one matches. Every other language is a file the user imports. Fieldwatch does not download either kind. Off Grid Pete LLC does not review an imported pack. The app must say that before an imported pack is applied. A bundled language shows a different official sentence, the AI sentence below. The Language page also shows the author-guide sentence. A pack cannot hide any of these sentences.

## Outcome

Settings has its own Language page. From there the user can follow the phone, choose English or a bundled language, import one language pack, export an English template, and remove an imported pack. With nothing else selected, a matching phone language uses the bundled file, and every other phone stays English. A line the active language translates is shown in that language. Every missing or rejected line stays English.

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
- The official sentences are compiled into the APK. That is the not-reviewed sentence, the AI sentence, and the author-guide sentence. A pack cannot replace, shorten, or hide any of them.
- Keys the app does not already have are dropped. A key cannot select a feature.

The app stores only the cleaned string map it wrote itself. On the next start it parses that file with the same rules. If that parse fails, the phone returns to English. The original import file is not kept and not parsed again.

Reject the whole file when the JSON is nested deeper than one object of strings, when a value is not a string, or when the file is larger than 264 KiB. Drop a line that is longer than its screen slot allows, as specified under Screen fit. Strip control and bidirectional characters at import, as specified below. Do not run those checks by executing the string.

## Where packs live

Bundled languages are files in this repo, packaged into the APK. The app does not download them. Do not add any other language to this repo, and do not create an Off Grid Pete language-pack repo. A language that is not in the list below is hosted by whoever wrote it. The user obtains that file and imports it.

Only the active language is parsed, once, when it is chosen and again on the next process start. The other bundled files stay compressed and unread. A bundled file uses the same line rules as an import. A line that is too long, or whose placeholders do not match, stays English. An imported pack is stored as the cleaned map the app writes. Export settings does not include that file, and it does not include the Language page choice. Follow the phone, English, or a bundled language stays on this phone, the same way the TAK tag does.

The bundled files start as an AI draft. Each file in this repo is kept complete for the English lines in that version: every key is present, and every line fits its slot, so the phone shows the bundled line. A line that is missing, too long, or has the wrong placeholders stays English. While a bundled language is active, the Language page shows this compiled sentence: "This language was translated with AI. If a line is wrong, please open a GitHub issue." The same sentence is shown once when that language is first selected, including when the phone language selects it on first launch. It is not a pack key.

## Bundled languages

Match the phone's language, not the country. `fr` and `fr-CA` both use French. The default choice is to follow the phone. An explicit choice in Settings stays until the user changes it. An imported pack replaces the bundled language until it is removed. Remove then returns to the phone language when that language is bundled, and to English when it is not.

| Language | Tags that select it |
| --- | --- |
| Albanian | `sq` |
| Bulgarian | `bg` |
| Croatian | `hr`, `cnr`, and `sr-ME` |
| Czech | `cs` |
| Danish | `da` |
| Dutch | `nl` |
| Estonian | `et` |
| Finnish | `fi` |
| French | `fr` |
| German | `de` |
| Greek | `el` |
| Hungarian | `hu` |
| Icelandic | `is` |
| Italian | `it` |
| Latvian | `lv` |
| Lithuanian | `lt` |
| Macedonian | `mk` |
| Norwegian | `nb`, `nn`, and `no` |
| Polish | `pl` |
| Portuguese | `pt` |
| Romanian | `ro` |
| Slovak | `sk` |
| Slovenian | `sl` |
| Spanish | `es` |
| Swedish | `sv` |
| Turkish | `tr` |

English is the compiled map, not a bundled file. A phone set to Luxembourgish (`lb`) stays English. French and German cover Luxembourg, so `fr-LU` and `de-LU` follow those files. A Montenegrin phone uses the Croatian file. A Serbian phone (`sr` without `ME`) stays English. Any other language stays English until the user imports a file.

## What is translated

Translate app chrome: sentences and labels the app itself writes.

That includes Compose text, button labels, dialog titles and bodies, empty states, Settings rows, Diagnostics, the Live tour, flood dialogs, notices, content descriptions, notification text the app writes, fixed spoken phrases the app composes, share-sheet subjects, and the headings and sentences in on-screen reports and in the Debrief and Compare PDFs.

A line that inserts a number or a name is one sentence with a placeholder, such as `{n}`. The app supplies the inserted value. The pack supplies the sentence.

## What stays as it is

These stay out of the pack:

- The launcher name. `strings.xml` keeps `app_name` as Fieldwatch.
- The user manual, `instruction.txt`, the changelog, and the README. Those are English documents.
- The stock catalog and any catalog the user imports. Signature names stay English. Notes, Extra attention, and decode labels stay in the English catalog. The bundled languages in this pack do not translate them. A separate file, described in `docs/signature-localization-options.md`, can supply those translations. The user exports that file, translates it, and imports it. This pack does not include it, and this document does not build it. Class names and the short spoken class names are app sentences and are in this pack. The class chip uses the `classchip` slot.
- Protocol and brand names, written as they are in English: Wi-Fi, Bluetooth, GPS, GNSS, USB, PDF, CSV, ATAK, SSID, MAC, RSSI, and OUI. Ordinary words are translated. Fieldwatch terms such as sit, signature, and Extra attention are translated into plain words in that language.
- Anything heard on the air: names, SSIDs, manufacturer data, OUIs, MACs, RSSI.
- Anything the user typed: observer notes, custom signature names, watch phrases, preset names, sit notes.
- Machine-readable exports. WiGLE CSV, sit and log CSV column headers, TAK/CoT tags, and other protocol or file-format tokens stay English. A translated header would break a file someone already parses.
- The readable text on an ATAK pin stays English, including the remarks the app writes. CoT tags stay English. A pack does not change either. A callsign or name heard on the air, or one the user typed, is still sent as stored. A team whose phones are in different languages then reads the same pin.
- Numbers the app formats for files and coordinates. Those keep `Locale.US` and the existing `String.format` calls. A pack string is never the format pattern.
- Which voice line is spoken, and in what order. A pack may translate a fixed phrase. It must not change the watch priority. The first matching signature watch still gets the single spoken phrase. A named radio with Alert on still speaks its own name only when that watch receives the phrase. A phrase the user wrote is spoken as stored.

Decoded values, radio rows, and catalog hits are data. The Live row fields that change all the time (name, MAC, and RSSI) are not looked up in the pack. The class label is.

## Fallback

English in the APK is the source of truth. The same lookup serves a bundled language and an imported pack. It returns that line only when all of these are true:

- A bundled language or an imported pack is active.
- The key is present.
- The value is not blank after trimming.
- The placeholders in the line are exactly the placeholders in the English line. Same names. Order may differ. Repeating a placeholder the English line uses is fine.

Every other case returns the English sentence:

- No bundled language is active and no imported pack is loaded.
- The active file failed to load.
- The key is absent.
- The value is empty or whitespace.
- The pack line drops a placeholder, or it adds a `{token}` the English line does not have.
- The app is about to fill a placeholder and does not have a value for it.

A translation that is present and complete is shown even when it was written for an older Fieldwatch. If the English sentence later changes under the same key, the old translation stays until that language is updated. The app does not compare the wording. If that English change adds or removes a placeholder, the placeholder rule above sends that one line back to English.

An unknown key is ignored. A language file cannot create a label the app does not already have.

## Lookup and runtime cost

There is one lookup, usable from Compose and from the domain layer (reports, PDF, notifications, voice). It is not a Composable-only helper.

The English sentences live in one compiled map of key to text. Screens read that map through the lookup. A new sentence is added to the map and read through the lookup. A literal written only at the call site will never be translated.

With neither a bundled language nor an imported pack, the lookup checks that the map is absent and returns the English string. It does not read a file and it does not parse JSON.

On import, and when a bundled language is chosen, the file is parsed once, checked, and kept as the cleaned map. On process start, that map is loaded once before the first screen. The open screen redraws once when the language changes.

A lookup is a map read, a blank check, and a placeholder-name check against the English line. Placeholder sets for the English lines are computed when the English map is built. Filling `{n}` is a single plain replace with the value the app passes. The inserted value is literal text. It is not scanned for placeholders or format markers. `%s` and `%n` in a pack line are characters on screen.

Do not parse JSON when a screen opens. Do not search the pack by the English sentence. Do not repeat the import checks on each frame. Do not run `String.format` on pack text. Do not pass pack text through HTML, `AnnotatedString` link annotations, or a WebView.

Scanning, signature matching, and laying out the Live list remain the work that dominates a frame.

## File format

One JSON object, UTF-8. A leading byte-order mark is stripped before parsing. Unknown top-level fields are ignored. `strings` values are JSON strings. A value that is an object, array, or number rejects the whole file. The language already on the phone stays in place.

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

- The file is empty, is not UTF-8, or is larger than 264 KiB.
- The root is not one JSON object.
- `format` is missing or wrong. A settings pack gets "That is a settings pack. Use Import settings." A signature pack gets "That is a signature pack. Use Import signatures." Anything else gets "Not a Fieldwatch language pack."
- `formatVersion` is missing or not a positive integer.
- `language` is missing or blank after cleaning.
- `strings` is missing or is not an object, or any value in it is not a string.
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

Follow the bundled-language table. A match selects that language and shows the AI sentence once. It does not show the import door.

Show the import door once, before the disclaimer, when the phone language is not English, no bundled language matches, no pack is loaded, and the user has not already dismissed it. Import opens the file picker immediately. If the user backs out of the picker, the door stays up. Continue in English closes the door and stores that choice. Do not show it again after an update. The Language page keeps Import language pack for later.

The door says that Fieldwatch is in English, and that the user can import a language-pack file if they already have one. It does not say a pack is waiting to be downloaded.

The door also shows this official sentence: "Language packs are made by other people. They are not reviewed or certified by Off Grid Pete LLC."

That sentence, the AI sentence, and the rest of the door are compiled into the APK. They are not pack keys. A pack must not be able to replace, shorten, or hide them. The import door is English.

## Language page

Language is its own page in Settings, on the same list as Appearance, Scanning, and the rest. It is not part of Settings backup. The row shows the language in use, such as French or English.

The page does three jobs:

- Set. Follow the phone, English, or one bundled language. Follow the phone is the usual choice. An explicit choice stays until the user changes it. Choosing English turns a bundled language off on this phone. It does not delete that language from the app.
- Add. Import language pack… opens the file picker for a language that is not bundled. Fieldwatch does not download a language.
- Remove. Remove language pack is shown only while an imported pack is loaded. It deletes that file. The page then returns to Follow the phone.

Export English template is on this page for someone writing a file. It is not part of everyday use. Next to it, the page shows this compiled sentence: "To write a language file, export the English template and follow the author guide on GitHub." The link points at the author guide in this repo. The app does not download the guide. The sentence is not a pack key, and a pack cannot change it.

The page shows the current language: "English", the bundled language name, or the cleaned name of an imported pack. While a bundled language is active and no imported pack has replaced it, the page shows the AI sentence. An imported pack does not get that sentence. Its confirm dialog shows the not-reviewed sentence instead.

Import uses the same kind of document picker as Import settings. Reading and parsing run off the main thread. Nothing is stored until the user confirms.

The confirm dialog shows:

- The language name.
- `writtenFor` and this phone's version, when `writtenFor` is present.
- How many lines will change.
- How many English lines will stay English.
- How many lines in the file were ignored, including lines that were too long for their slot.
- How many keys are new since this pack, when `writtenFor` is present and comparable. That count is the keys added after that version which this pack does not translate. Those keys are included in the stay-English count. Omit this line when `writtenFor` is missing or not a version the app can compare. A mismatch still does not reject the pack.
- The same official sentence as the door: "Language packs are made by other people. They are not reviewed or certified by Off Grid Pete LLC." This sentence is compiled into the APK and is not a pack key. The pack cannot replace it.

The buttons are Import and Cancel. Cancel leaves the current language alone. Import replaces the whole pack. It does not merge. A key that was translated before, and is absent from the new pack, returns to English.

Remove asks for confirmation, then deletes the stored pack. The open screen redraws in the phone's bundled language, or in English when the phone language is not bundled.

These leave the language pack alone:

- Import settings and Export settings. Neither copies the Language page choice or the imported file.
- Import signatures, export signatures, and Update stock catalog.
- Restore defaults.

A language pack leaves the catalog, settings, watches, logs, and sits alone.

## English template

Export English template writes a `fieldwatch-language` file from the same English map the app uses. `language` is English. `writtenFor` is the current `versionName`. `strings` contains every key. The file also has a `max` object, one integer per key, the character limit after trim. Authors may delete `max`. Import ignores it. The app uses the limit compiled into that version. A translator edits the values and the language name, and may delete keys they are not translating. Importing that template unchanged shows English.

There is no second hand-maintained copy of the English text. The template is generated from the map.

## Keys and new copy

Keys stay stable. Renaming a key makes existing packs show English for that line. Prefer a new key over renaming one.

Each key records the app version that added it. Keys that arrive with the first map share that version. The confirm dialog uses this to count keys new since `writtenFor`.

Changing the English text under the same key is allowed when the meaning stays the same. Packs keep their old line until they are updated, unless the placeholders changed. When the meaning of a safety sentence changes, give it a new key so the old translation falls back to English. Nothing watches the sentences in the background. The edit that changes the English includes this check.

The first implementation moves the existing user-visible chrome onto the map. Wording stays the same. This is not a copy edit.

Every later user-visible sentence goes into the map. Tests cover the lookup. A test lists the English keys, checks they are unique and well formed, and checks that every placeholder in the English text matches the token grammar `{` plus an ASCII identifier plus `}`.

## Placeholders and plurals

The token grammar is `{` + a letter followed by letters, digits, or underscores + `}`. The first version has no plural categories, no gender, and no select. A count is digits inside one sentence that reads correctly for any count. Translators write that one sentence.

In Polish, Czech, Latvian, and other Slavic or Baltic languages, one sentence that reads right for every count gets awkward. For version 1 the author guide tells them to use a form such as `count: {n}`. Do not add plural categories to make those sentences agree.

The phone language selects a bundled language, as listed above. It does not change the Android locale, layout direction, or number formats. The language tag does not flip the app to right-to-left. An imported pack replaces the bundled language until it is removed.

## Screen fit

A single character cap does not keep the screens looking right. A tab and a warning dialog do not have the same room. The layout fits a normal translation. The cap only rejects a line that cannot look acceptable in its slot. Do not shrink type to fit.

Each English key has a slot, compiled into the app. The pack cannot change a key's slot. Count characters after cleaning. A line over its cap is dropped and that key stays English. The confirm dialog counts how many lines were too long.

| Slot | Where it is used | Cap | How it is drawn |
| --- | --- | --- | --- |
| `tab` | Bottom bar: Live, Filters, Signatures, Reports, Settings | 18 characters | At most two lines. A longer word stays inside its tab. |
| `classchip` | Class names on the Filters chips. Two chips share a row, at 11sp, with an icon, on one line. | 18 characters | One line. A longer name would ellipsize, so it is dropped and the English name stays. The spoken class name uses `control`, not this slot. |
| `control` | Chips, buttons, one-line rows, and fixed spoken phrases | Twice the English length, at most 48 characters | Chips stay one line and ellipsize. The row of chips wraps. Full-width buttons may use two lines. List rows keep today's one-line ellipsis. A spoken phrase stays a short phrase. |
| `body` | Dialogs, the tour, empty states, Diagnostics explanations, report sentences, and PDF sentences | Three times the English length, at least 80 characters, at most 700 | Wrap. Dialogs and pages scroll. Do not ellipsize a warning. PDF text wraps inside the margins. |

Eighteen characters fits most tab words and the class chips. The longest English class names are about 14 characters and already fit. Twice that length would ellipsize on the chip, so the class chip uses 18. A German compound, and the same kind of long word in other languages, can pass that cap. The author guide tells them to pick a shorter word. A line over the cap stays English. The dialog counts it as ignored. It does not name the word.

On-screen text uses the phone's font. That font covers Cyrillic, so Bulgarian and Macedonian do not need a font in the APK. The Debrief and Compare PDFs must draw those letters too. A test renders a Cyrillic line in a Debrief PDF and fails if those characters are missing.

The file cap of 264 KiB, the language name cap of 40 characters, and the cap of 2000 keys stay. Those protect the phone from a huge file. They are not the visual rule.

The English template may include a `slots` object so an author can see the slot for each key. Import ignores that object. If it disagrees with the app, the compiled slot wins.

A test pack, used in tests and not shipped, fills every key to its cap so the longest accepted translation is checked on screen. Do not ship that pack.

## Safety

Follow "No code, no injection" above. That section wins if another line in this document is looser.

- Cap the file at 264 KiB, each line at its screen-fit slot, the language name at 40 characters, and the map at 2000 entries.
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
- The AI sentence is the compiled sentence. A pack key cannot replace it. It is shown when a bundled language is first selected, and on the Language page while that language is active and no imported pack has replaced it.
- The author-guide sentence is the compiled sentence. A pack key cannot replace it. It is shown next to Export English template, and its link points at the guide in this repo.
- Language is its own Settings page. Settings backup does not contain the language actions.
- Applying a pack does not change the Android locale, the watchlist, settings, the catalog, or which voice phrase is chosen.
- An unknown key is ignored. An unknown top-level field is ignored.
- Empty file, garbage, a settings pack, a signature pack, a file over 264 KiB, and a pack with no usable known lines are rejected. A pack already on the phone stays in place.
- Newlines are kept only when the English line has a newline. Bidirectional marks are removed.
- Confirm is required before apply. Cancel does not change the language.
- A second pack replaces the first. Keys only in the first pack return to English.
- Remove deletes the stored pack. A bundled phone language returns. Any other phone returns to English.
- Import settings, import signatures, catalog update, and restore defaults do not change the language or the Language page choice. Export settings does not include either.
- Process start loads the stored map once. A stored line whose placeholders no longer match the English falls back to English.
- A translated line written to CSV still gets the formula guard.
- The English template contains every key and is generated from the map.
- Fixed voice phrases can be translated. The choice of which phrase speaks is unchanged. A user-written phrase is unchanged.
- WiGLE and other machine-readable headers stay English.
- ATAK pin remarks and CoT tags stay English. A pack key cannot change them.
- A Debrief PDF page draws a Cyrillic sentence, and the test fails if those characters are missing.
- When `writtenFor` is an older known version, confirm reports how many keys were added after it and are not translated. When `writtenFor` is missing, that count is omitted and the pack still loads.
- The launcher name stays Fieldwatch.
- A French phone selects bundled French without an import. A language that is not bundled stays English. Luxembourgish stays English. `fr-LU` selects French and `de-LU` selects German. Croatian, Montenegrin, and `sr-ME` select Croatian. Serbian (`sr` without `ME`) stays English.
- A missing key in a bundled language stays English.
- A class-chip line longer than 18 characters stays English. The spoken class name may be longer, up to its `control` cap.
- The English lines that use Wi-Fi, Bluetooth, GPS, GNSS, USB, PDF, CSV, ATAK, SSID, MAC, RSSI, or OUI still contain those words.

## Author guide

People who write a pack need their own English instructions. The phone manual is for the person importing a file. The author guide is for the person producing one. Write the author guide with the feature, after the English map exists, so it can point at Export English template and not at a hand-copied key list.

Put the guide in this repo as documentation of the format. It does not contain translated packs, and it is not a place for people to upload one. The Language page links to that file. The link is compiled into the app.

The guide needs to say:

- A pack is a JSON file. It does not replace the APK, and it is not a fork. Off Grid Pete LLC does not review or certify it. The author hosts the file. Fieldwatch does not publish it or download it.
- Start from Settings → Language → Export English template on the Fieldwatch version being translated. Keep the keys. Edit the values. Delete any key that is not being translated. Do not invent keys, rename keys, or translate by searching the source for English sentences. A sentence that is not in the template cannot be translated.
- A missing or blank key stays English. That is expected.
- Keep every `{token}` exactly as exported, including the braces and the token name. Do not add tokens. Do not use `%s`. A line that breaks this rule is shown in English.
- There are no plural forms. One sentence has to read right for every count. In Polish, Czech, Latvian, and other Slavic or Baltic languages, that gets awkward. Use a form such as `count: {n}` rather than forcing the noun to agree with the number.
- The text is display text. HTML, scripts, links, and format strings are shown as characters or dropped. They do not run.
- Stay inside the slot for each key. A tab or a class chip is at most 18 characters. A chip, button, row, or spoken phrase is at most twice the English length and never more than 48 characters. A dialog or other long sentence is at most three times the English length and never more than 700 characters. The language name is at most 40 characters. The file is at most 264 KiB. A line that is too long is left in English. A newline is kept only when the English line has one.
- For a tab or a class chip, pick a shorter word when the full word would pass 18 characters. A German compound is the usual case. A line over the cap stays English. The confirm dialog counts it. Save report on that dialog writes a text file that names the key, the length, and the limit, plus missing keys, placeholder mismatches, blank lines, extra line breaks, and unknown keys. The headings stay English.
- Leave these words as written: Wi-Fi, Bluetooth, GPS, GNSS, USB, PDF, CSV, ATAK, SSID, MAC, RSSI, and OUI. Translate ordinary words. Translate Fieldwatch terms such as sit, signature, and Extra attention into plain words.
- After a Fieldwatch update, export a fresh English template and translate the lines the file does not have. Import the pack and use Save report. Missing names every key the file lacks. The dialog line "{n} keys are new since your pack." only counts keys added after `writtenFor`. Leave `writtenFor` as the version of that template.
- Set `language` to the name users should see, and `writtenFor` to the version of the template. One pack replaces another. It does not merge.
- The sentence that packs are not reviewed or certified is official text. It is not in the template, and a pack cannot change it.
- Tell users to import the file from Settings, or from the first-run door when their phone language is not English and not one of the bundled languages. The guide can describe those steps. The in-app wording for phone users still belongs in the user manual. A bundled language is already in the APK. Do not tell those users to import it.

## Manual later

When this is actually shipped, add a short English section to the user manual. It covers the phone language selecting a bundled language, missing lines staying English, the AI sentence and the request to open a GitHub issue, and import and remove for every other language. An imported pack is made by other people and is not reviewed or certified by Off Grid Pete LLC. Add a changelog line. The manual stays English. Do that in the ship, not as a drive-by while the feature is still unreleased. The author guide above is a separate document.
