# Write a Fieldwatch language file

A language pack is a JSON file. It does not replace the APK, and it is not a fork of Fieldwatch. You host the file. Fieldwatch does not publish it and does not download it.

Language packs are made by other people. They are not reviewed or certified by Off Grid Pete LLC. The app shows that sentence itself. The sentence is not in the template, and a pack cannot change it.

The available languages that ship inside the APK are a separate draft. They were translated with AI. A line can be wrong. Perfection is not guaranteed. They are already on the phone. Do not tell those users to import the bundled file. If a bundled line is wrong, open a GitHub issue: https://github.com/OffGridPete/Fieldwatch/issues

Off Grid Pete LLC is not adding more languages to this repository. To share a translation, put the file in a repository of your own. People import that file. This guide is for a language that is not in the APK, or for a replacement a person chooses to import.

## Start from the English template

On the Fieldwatch version you are translating, open Settings → Language → Export English template. That file is the current English map.

Keep the keys. Edit the values. Delete any key you are not translating. Do not invent keys. Do not rename keys. Do not translate by searching the source for English sentences. A sentence that is not in the template cannot be translated.

A key is lowercase letters, digits, and dots, as in `tab.live` or `language.confirm.change`. Do not put an underscore in a key. A placeholder name may contain an underscore. A key may not.

A missing or blank key stays English. That is expected.

Set `language` to the name people should see. It can be at most 40 characters. Set `writtenFor` to the version written on the template you exported. Leave `writtenFor` as the version of that template. One pack replaces another. It does not merge.

The template also has a `slots` object, one slot name for each key. That object is there so you can see where the line sits. Import ignores it. The app uses the slot compiled into the version that reads the file.

The template also has a `max` object, one integer for each key. That integer is the character limit after the phone trims the line. Count the template text, including `{tokens}`, not the words you substitute for them. You may delete `max`. Import ignores it. The app uses the limit compiled into the version that reads the file. A line over that limit is left in English. Save report names each line that was too long.

## Placeholders

Keep every `{token}` exactly as exported, including the braces and the token name. The same names are required. The order may change. Do not add tokens. Do not use `%s`. A line that breaks this rule is shown in English.

There are no plural forms and no gender forms. One sentence has to read right for every count. In Polish, Czech, Latvian, and other Slavic or Baltic languages, that gets awkward. Use a form such as `count: {n}` rather than forcing the noun to agree with the number.

## What the phone does with the text

The text is display text. HTML, scripts, links, and format strings are shown as characters or dropped. They do not run. A pack cannot open a link, change a setting, change the catalog, or replace the APK.

## Length

Stay inside the slot for each key.

- A tab or a class chip is at most 18 characters.
- A chip, button, row, or spoken phrase is at most twice the English length, and never more than 48 characters.
- A dialog or other long sentence is at most three times the English length, and never more than 700 characters.
- The file is at most 352 KiB.

Count characters after the phone trims the line. A line that is too long is left in English. A newline is kept only when the English line has one.

On the import confirm dialog, Save report writes a text file. The headings stay English. It lists the keys that will be used, the keys that are missing, and each line that was too long, with the length and the limit. It also names a blank line, a line with an extra line break, a placeholder that does not match, and a key this version does not have. Send that file with the pack when a line comes back in English. The confirm dialog still shows the counts. Cancel leaves the current language.

For a tab or a class chip, pick a shorter word when the full word would pass 18 characters. A German compound is the usual case.

## Words to leave as written

Leave these words as written: Wi-Fi, Bluetooth, GPS, GNSS, USB, PDF, CSV, ATAK, SSID, MAC, RSSI, and OUI. Translate the ordinary words around them. Translate Fieldwatch terms such as sit, signature, and Extra attention into plain words.

## After a Fieldwatch update

Export a fresh English template and translate the lines the file does not have. Import the pack and use Save report. Missing names every key the file lacks. The dialog line "{n} keys are new since your pack." only counts keys added after `writtenFor`. Leave `writtenFor` as the version of that template.

## What to tell the person who imports the file

Tell them to import the file from Settings → Language, or from the first-run door when their phone language is not English and not one of the bundled languages. The in-app wording for phone users belongs in the Fieldwatch user manual. This guide does not replace that manual.

A bundled language is already in the APK. Do not tell those users to import it.
