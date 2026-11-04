package app.fieldwatch.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The small GitHub file that says which version is newest, and whether this phone should ask.
 * The notes are the publisher's sentences, shown as written.
 */
data class AppUpdateNote(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
)

sealed class AppUpdateDecision {
    data class Newer(val note: AppUpdateNote) : AppUpdateDecision()
    data object Quiet : AppUpdateDecision()
}

/** What the open app is asking the person to do. Null means no update dialog. */
sealed class AppUpdatePrompt {
    data object Checking : AppUpdatePrompt()
    data class Offer(val note: AppUpdateNote) : AppUpdatePrompt()
    data class Current(val versionName: String) : AppUpdatePrompt()
    data object Unreachable : AppUpdatePrompt()
    data object Downloading : AppUpdatePrompt()
    data object Failed : AppUpdatePrompt()
    data object Allow : AppUpdatePrompt()
}

object AppUpdate {
    const val MAX_NOTE_BYTES = 16 * 1024
    const val MAX_NOTES_CHARS = 6_000
    const val MAX_APK_BYTES = 32 * 1024 * 1024

    private val versionName = Regex("""\d+\.\d+\.\d+""")
    private val json = Json { ignoreUnknownKeys = true }

    /** Null when a required field is missing, the wrong type, or the notes are blank. */
    fun parse(text: String): AppUpdateNote? {
        if (text.isBlank() || text.length > MAX_NOTE_BYTES) return null
        val obj = try {
            json.parseToJsonElement(text.trim().trimStart('\uFEFF')) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val codeEl = obj["versionCode"] as? JsonPrimitive ?: return null
        if (codeEl.isString) return null
        val code = codeEl.intOrNull ?: return null
        if (code <= 0) return null
        val nameEl = obj["versionName"] as? JsonPrimitive ?: return null
        if (!nameEl.isString) return null
        val name = nameEl.content.trim()
        if (!versionName.matches(name) || name.length > 32) return null
        val notesEl = obj["notes"] as? JsonPrimitive ?: return null
        if (!notesEl.isString) return null
        val notes = notesEl.content.trim()
        if (notes.isEmpty() || notes.length > MAX_NOTES_CHARS) return null
        return AppUpdateNote(code, name, notes)
    }

    /**
     * A higher versionCode asks. The same code, or an older one, stays quiet.
     * [skippedCode] is the version the person already set aside. Settings passes null and asks anyway.
     */
    fun decide(installedCode: Int, note: AppUpdateNote, skippedCode: Int?): AppUpdateDecision {
        if (note.versionCode > installedCode && skippedCode != note.versionCode) {
            return AppUpdateDecision.Newer(note)
        }
        return AppUpdateDecision.Quiet
    }

    /** An APK is a zip. The first four bytes are the local-file signature. */
    fun apkLooksLikeZip(header: ByteArray): Boolean {
        return header.size >= 4 &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
    }
}

/** Version the person chose Later for. Not part of exported settings. */
object AppUpdateSkip {
    private const val NAME = "app-update-skip.txt"

    fun read(filesDir: File): Int? {
        val text = runCatching { File(filesDir, NAME).readText() }.getOrNull() ?: return null
        return text.trim().toIntOrNull()?.takeIf { it > 0 }
    }

    fun write(filesDir: File, versionCode: Int) {
        if (versionCode <= 0) return
        runCatching { File(filesDir, NAME).writeText(versionCode.toString()) }
    }
}

internal object AppUpdateLines {
    val all: List<EnglishLine> = listOf(
        en("update.allow.body", "Android asks once. Then come back and tap Update now again.", LanguageSlot.BODY),
        en("update.allow.title", "Allow installs from Fieldwatch", LanguageSlot.CONTROL),
        en("update.checking", "Checking for an update", LanguageSlot.CONTROL),
        en("update.current.body", "Fieldwatch {version} is the newest one on GitHub.", LanguageSlot.BODY),
        en("update.current.title", "This phone is current", LanguageSlot.CONTROL),
        en("update.downloading", "Downloading the update", LanguageSlot.CONTROL),
        en("update.failed.body", "The file from GitHub could not be installed. The app on this phone is unchanged.", LanguageSlot.BODY),
        en("update.failed.title", "Could not install the update", LanguageSlot.CONTROL),
        en("update.later", "Later", LanguageSlot.CONTROL),
        en("update.now", "Update now", LanguageSlot.CONTROL),
        en("update.notes", "Release Notes", LanguageSlot.CONTROL),
        en("update.settings", "Check for an update", LanguageSlot.CONTROL),
        en("update.settings.note", "Looks on GitHub when this phone is online. The install still asks you to confirm.", LanguageSlot.BODY),
        en("update.unreachable.body", "GitHub did not answer. The app on this phone is unchanged.", LanguageSlot.BODY),
        en("update.unreachable.title", "Could not check for an update", LanguageSlot.CONTROL),
    )
}
