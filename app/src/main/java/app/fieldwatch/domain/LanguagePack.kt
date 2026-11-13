package app.fieldwatch.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** One language file after the line rules. Nothing is stored until the user confirms. */
data class LanguagePreview(
    val language: String,
    val languageTag: String?,
    val writtenFor: String?,
    val formatVersion: Int,
    val accepted: Map<String, String>,
    val changed: Int,
    val stayEnglish: Int,
    val ignored: Int,
    /** Null when writtenFor is missing or not a version the app can compare. */
    val newSince: Int?,
    val formatNewer: Boolean,
    /** Lines in the file that this version did not keep. The ignored count is this list. */
    val issues: List<LanguageIssue> = emptyList(),
    /** English keys the file does not contain. A rejected line is an issue, not a missing key. */
    val missingKeys: List<String> = emptyList(),
    /** Keys added after writtenFor that this file does not keep. Empty when writtenFor cannot be compared. */
    val newKeys: List<String> = emptyList(),
)

/** One line in an imported pack that stayed English, or a key this version does not have. */
sealed class LanguageIssue {
    data class TooLong(val key: String, val length: Int, val cap: Int) : LanguageIssue()
    data class Placeholder(val key: String, val expected: Set<String>) : LanguageIssue()
    data class Unknown(val key: String) : LanguageIssue()
    data class Blank(val key: String) : LanguageIssue()
    data class Newline(val key: String) : LanguageIssue()
}

sealed class LanguageParse {
    data class Ok(val preview: LanguagePreview) : LanguageParse()
    data class Reject(val messageKey: String) : LanguageParse()
}

@Serializable
data class LanguageFile(
    val format: String = "",
    val formatVersion: Int = 0,
    val language: String = "",
    val languageTag: String? = null,
    val writtenFor: String? = null,
    val strings: Map<String, String> = emptyMap(),
)

@Serializable
private data class LanguageHeader(val format: String = "")

@Serializable
private data class LanguageTemplateFile(
    val format: String,
    val formatVersion: Int,
    val language: String,
    val languageTag: String,
    val writtenFor: String,
    val slots: Map<String, String>,
    val max: Map<String, Int>,
    val strings: Map<String, String>,
)

object LanguagePack {
    /**
     * Fits the English template and a same-length two-byte translation of it.
     * A file with every line at its slot cap stays over this and is refused.
     */
    const val MAX_BYTES = 352 * 1024
    const val MAX_KEYS = 2000
    const val MAX_NAME = 40
    const val MAX_WRITTEN_FOR = 32

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val token = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")
    private val tagRe = Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8}){0,2}$")

    fun parse(bytes: ByteArray): LanguageParse {
        if (bytes.isEmpty()) return LanguageParse.Reject(LanguageErrors.EMPTY)
        if (bytes.size > MAX_BYTES) return LanguageParse.Reject(LanguageErrors.LARGE)
        val text = decodeUtf8(bytes) ?: return LanguageParse.Reject(LanguageErrors.NOT_PACK)
        return parse(text)
    }

    fun parse(text: String): LanguageParse {
        val body = text.trim().trimStart('\uFEFF')
        if (body.isEmpty()) return LanguageParse.Reject(LanguageErrors.EMPTY)
        val header = try {
            json.decodeFromString(LanguageHeader.serializer(), body)
        } catch (_: Exception) {
            return LanguageParse.Reject(LanguageErrors.NOT_PACK)
        }
        when (header.format) {
            SettingsPack.FORMAT -> return LanguageParse.Reject(LanguageErrors.SETTINGS)
            SignaturePack.FORMAT, SignaturePack.LEGACY_FORMAT ->
                return LanguageParse.Reject(LanguageErrors.SIGNATURES)
        }
        if (header.format != EnglishCopy.FORMAT) return LanguageParse.Reject(LanguageErrors.NOT_PACK)
        val file = try {
            json.decodeFromString(LanguageFile.serializer(), body)
        } catch (_: Exception) {
            return LanguageParse.Reject(LanguageErrors.NOT_PACK)
        }
        if (file.formatVersion < 1) return LanguageParse.Reject(LanguageErrors.NOT_PACK)
        if (file.strings.size > MAX_KEYS) return LanguageParse.Reject(LanguageErrors.NOT_PACK)
        val name = clean(file.language, allowNewline = false).trim()
        if (name.isEmpty() || name.length > MAX_NAME) return LanguageParse.Reject(LanguageErrors.NOT_PACK)
        val tag = file.languageTag?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_WRITTEN_FOR && tagRe.matches(it) }
        val written = file.writtenFor?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_WRITTEN_FOR }
        val lines = acceptLines(file.strings)
        if (lines.accepted.isEmpty()) return LanguageParse.Reject(LanguageErrors.NONE)
        val writtenVersion = written?.let { parseVersion(it) }
        val newKeys = if (writtenVersion == null) {
            emptyList()
        } else {
            EnglishCopy.lines.mapNotNull { line ->
                val added = versionOf(line.addedIn) ?: return@mapNotNull null
                if (line.key !in lines.accepted && compareVersion(added, writtenVersion) > 0) line.key else null
            }.sorted()
        }
        val present = file.strings.keys
        val missingKeys = EnglishCopy.lines.map { it.key }.filter { it !in present }.sorted()
        return LanguageParse.Ok(
            LanguagePreview(
                language = name,
                languageTag = tag,
                writtenFor = written,
                formatVersion = file.formatVersion,
                accepted = lines.accepted,
                changed = lines.accepted.size,
                stayEnglish = EnglishCopy.lines.size - lines.accepted.size,
                ignored = lines.ignored,
                newSince = writtenVersion?.let { newKeys.size },
                formatNewer = file.formatVersion > EnglishCopy.FORMAT_VERSION,
                issues = lines.issues,
                missingKeys = missingKeys,
                newKeys = newKeys,
            ),
        )
    }

    /** Line rules for a map the app already stored. A bad line is dropped. The rest stay. */
    fun recheck(file: LanguageFile): Map<String, String>? {
        if (file.format != EnglishCopy.FORMAT || file.formatVersion < 1) return null
        if (file.strings.size > MAX_KEYS) return null
        val name = clean(file.language, allowNewline = false).trim()
        if (name.isEmpty() || name.length > MAX_NAME) return null
        return acceptLines(file.strings).accepted
    }

    /** Plain text a translator can send back. Headings stay English, like the author guide. */
    fun report(preview: LanguagePreview, phoneVersion: String): String {
        val tooLong = preview.issues.filterIsInstance<LanguageIssue.TooLong>().sortedBy { it.key }
        val placeholders = preview.issues.filterIsInstance<LanguageIssue.Placeholder>().sortedBy { it.key }
        val unknown = preview.issues.filterIsInstance<LanguageIssue.Unknown>().map { it.key }.sorted()
        val blank = preview.issues.filterIsInstance<LanguageIssue.Blank>().map { it.key }.sorted()
        val newlines = preview.issues.filterIsInstance<LanguageIssue.Newline>().map { it.key }.sorted()
        val imported = preview.accepted.keys.sorted()
        return buildString {
            appendLine("Fieldwatch language pack report")
            appendLine()
            appendLine("Language: ${preview.language}")
            preview.languageTag?.let { appendLine("Tag: $it") }
            preview.writtenFor?.let { appendLine("Written for $it. This phone is $phoneVersion.") }
            appendLine()
            appendLine("A key that is too long, blank, or has the wrong placeholders stays English.")
            appendLine()
            appendLine("Imported: ${imported.size}")
            appendLine("Too long: ${tooLong.size}")
            appendLine("Missing: ${preview.missingKeys.size}")
            appendLine("Wrong placeholders: ${placeholders.size}")
            appendLine("Unknown keys: ${unknown.size}")
            appendLine("Blank: ${blank.size}")
            appendLine("Extra line breaks: ${newlines.size}")
            if (preview.newSince != null) appendLine("New since this pack: ${preview.newKeys.size}")
            section("Too long", tooLong.map { it.key }) { key ->
                val hit = tooLong.first { it.key == key }
                "${hit.length} characters. The limit is ${hit.cap}."
            }
            section("Wrong placeholders", placeholders.map { it.key }) { key ->
                val expected = placeholders.first { it.key == key }.expected.sorted()
                if (expected.isEmpty()) {
                    "Keep the same placeholders as the English line."
                } else {
                    "Keep ${expected.joinToString(", ") { "{$it}" }}."
                }
            }
            section("Extra line breaks", newlines)
            section("Blank", blank)
            section("Unknown keys", unknown)
            section("Missing", preview.missingKeys)
            if (preview.newSince != null) section("New since this pack", preview.newKeys)
            section("Imported", imported)
        }
    }

    private fun StringBuilder.section(
        title: String,
        keys: List<String>,
        detail: ((String) -> String)? = null,
    ) {
        if (keys.isEmpty()) return
        appendLine()
        appendLine(title)
        for (key in keys) {
            appendLine(key)
            detail?.let { appendLine(it(key)) }
        }
    }

    fun template(versionName: String): String {
        val slots = LinkedHashMap<String, String>(EnglishCopy.lines.size)
        val max = LinkedHashMap<String, Int>(EnglishCopy.lines.size)
        val strings = LinkedHashMap<String, String>(EnglishCopy.lines.size)
        for (line in EnglishCopy.lines) {
            slots[line.key] = EnglishCopy.slotName(line.slot)
            max[line.key] = EnglishCopy.cap(line)
            strings[line.key] = line.text
        }
        val file = LanguageTemplateFile(
            format = EnglishCopy.FORMAT,
            formatVersion = EnglishCopy.FORMAT_VERSION,
            language = "English",
            languageTag = "en",
            writtenFor = versionName,
            slots = slots,
            max = max,
            strings = strings,
        )
        return json.encodeToString(LanguageTemplateFile.serializer(), file)
    }

    /**
     * Bundled file for this phone language, or null when the phone stays on the compiled English.
     * Does not change the Android locale.
     */
    fun bundledTag(locale: Locale): String? {
        val lang = locale.language.lowercase(Locale.US)
        val country = locale.country.uppercase(Locale.US)
        if (lang == "sr" && country == "ME") return "hr"
        if (lang == "cnr") return "hr"
        if (lang == "sr" || lang == "lb" || lang == "en" || lang.isEmpty()) return null
        return when (lang) {
            "sq", "bg", "hr", "cs", "da", "nl", "et", "fi", "fr", "de", "el", "hu", "is", "it",
            "lv", "lt", "mk", "pl", "pt", "ro", "sk", "sl", "es", "sv", "tr",
            -> lang
            "nb", "nn", "no" -> "nb"
            else -> null
        }
    }

    fun showImportDoor(locale: Locale, dismissed: Boolean, packLoaded: Boolean): Boolean {
        if (dismissed || packLoaded) return false
        if (locale.language.equals("en", ignoreCase = true)) return false
        return bundledTag(locale) == null
    }

    fun fill(template: String, values: Map<String, String>): String {
        val needed = placeholderNames(template)
        if (needed.any { it !in values }) return template
        val out = StringBuilder(template.length)
        var i = 0
        while (i < template.length) {
            val start = template.indexOf('{', i)
            if (start < 0) {
                out.append(template, i, template.length)
                break
            }
            out.append(template, i, start)
            val match = token.find(template, start)
            if (match == null || match.range.first != start) {
                out.append(template[start])
                i = start + 1
                continue
            }
            val name = match.groupValues[1]
            if (name in values) out.append(values.getValue(name)) else out.append(match.value)
            i = match.range.last + 1
        }
        return out.toString()
    }

    fun clean(raw: String, allowNewline: Boolean): String {
        val out = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch == '\r' -> Unit
                ch == '\n' -> if (allowNewline) out.append('\n')
                ch == '\uFEFF' || ch == '\u200E' || ch == '\u200F' ||
                    ch in '\u202A'..'\u202E' || ch in '\u2066'..'\u2069' -> Unit
                ch.code < 0x20 || ch.code == 0x7F -> Unit
                else -> {
                    val type = Character.getType(ch)
                    if (type != Character.CONTROL.toInt() && type != Character.FORMAT.toInt()) {
                        out.append(ch)
                    }
                }
            }
        }
        return out.toString()
    }

    fun parseVersion(raw: String): List<Int>? {
        val text = raw.trim()
        if (text.isEmpty() || text.any { it != '.' && !it.isDigit() }) return null
        if (text.startsWith('.') || text.endsWith('.') || ".." in text) return null
        return text.split('.').map { part -> part.toIntOrNull() ?: return null }
    }

    fun compareVersion(left: List<Int>, right: List<Int>): Int {
        val n = maxOf(left.size, right.size)
        for (i in 0 until n) {
            val delta = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
            if (delta != 0) return delta
        }
        return 0
    }

    private fun versionOf(raw: String): List<Int>? = parseVersion(raw)

    private data class AcceptedLines(
        val accepted: Map<String, String>,
        val ignored: Int,
        val issues: List<LanguageIssue>,
    )

    private fun acceptLines(strings: Map<String, String>): AcceptedLines {
        val accepted = LinkedHashMap<String, String>()
        val issues = ArrayList<LanguageIssue>()
        for ((key, value) in strings) {
            val line = EnglishCopy.byKey[key]
            if (line == null) {
                issues += LanguageIssue.Unknown(key)
                continue
            }
            if (value.any { it == '\n' || it == '\r' } && '\n' !in line.text) {
                issues += LanguageIssue.Newline(key)
                continue
            }
            val cleaned = clean(value, allowNewline = '\n' in line.text).trim()
            val cap = EnglishCopy.cap(line)
            if (cleaned.isEmpty()) {
                issues += LanguageIssue.Blank(key)
                continue
            }
            if (cleaned.length > cap) {
                issues += LanguageIssue.TooLong(key, cleaned.length, cap)
                continue
            }
            val expected = EnglishCopy.placeholders.getValue(key)
            if (placeholderNames(cleaned) != expected) {
                issues += LanguageIssue.Placeholder(key, expected)
                continue
            }
            accepted[key] = cleaned
        }
        return AcceptedLines(accepted, issues.size, issues)
    }

    private fun decodeUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

fun placeholderNames(text: String): Set<String> {
    val names = LinkedHashSet<String>()
    val re = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")
    for (match in re.findAll(text)) names.add(match.groupValues[1])
    return names
}
