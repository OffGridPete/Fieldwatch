package app.fieldwatch.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private val notes =
        "A language file that comes with the app says it was written for this version. " +
            "An imported file reports the keys added after the version it was written for. " +
            "A translated line from an older file still shows."

    private val note = AppUpdateNote(50, "1.1.40", "A newer build.")

    @Test
    fun aCompleteFileParses() {
        val parsed = AppUpdate.parse(
            """{"versionCode":49,"versionName":"1.1.39","notes":"$notes","extra":true}""",
        )
        assertEquals(AppUpdateNote(49, "1.1.39", notes), parsed)
    }

    @Test
    fun thePublishedNotesFileIsThisVersion() {
        val published =
            "This is a small update which cleans up some text on warning dialogs. " +
                "Also an opportunity for you to test out the new auto update feature.\n\n" +
                "Warning dialogs for GPS, a cell, a trusted network, and a flood use one layout. " +
                "The reason is a paragraph. Readings sit under Details, with the label and the value on one line.\n\n" +
                "The update offer has a Release Notes heading. Long notes scroll.\n\n" +
                "When an app on this phone is generating the GPS location, the warning is Possible GPS interference. " +
                "It says an app on this phone is generating the GPS location, and this phone is using that instead of the satellites.\n\n" +
                "A cell warning keeps what happened in the title. " +
                "The paragraph says Fieldwatch cannot tell the cause.\n\n" +
                "Bundled language files are written for this version."
        val file = listOf(File("dist/app-update.json"), File("../dist/app-update.json")).first { it.isFile }
        assertEquals(AppUpdateNote(51, "1.1.41", published), AppUpdate.parse(file.readText()))
    }

    @Test
    fun aFileThatIsNotAReleaseIsRefused() {
        assertNull(AppUpdate.parse(""))
        assertNull(AppUpdate.parse("{"))
        assertNull(AppUpdate.parse("[]"))
        assertNull(AppUpdate.parse("""{"versionName":"1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":"49","versionName":"1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":49.5,"versionName":"1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":0,"versionName":"1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":-1,"versionName":"1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"v1.1.39","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"1.1","notes":"Hi"}"""))
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"1.1.39","notes":"  "}"""))
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"1.1.39","notes":""}"""))
        val huge = "x".repeat(AppUpdate.MAX_NOTES_CHARS + 1)
        assertNull(AppUpdate.parse("""{"versionCode":49,"versionName":"1.1.39","notes":"$huge"}"""))
        assertNull(AppUpdate.parse("{" + " ".repeat(AppUpdate.MAX_NOTE_BYTES) + "}"))
    }

    @Test
    fun aNewerVersionAsksUntilItIsSetAside() {
        assertEquals(AppUpdateDecision.Newer(note), AppUpdate.decide(49, note, skippedCode = null))
        assertEquals(AppUpdateDecision.Newer(note), AppUpdate.decide(49, note, skippedCode = 49))
        assertTrue(AppUpdate.decide(50, note, skippedCode = null) is AppUpdateDecision.Quiet)
        assertTrue(AppUpdate.decide(51, note, skippedCode = null) is AppUpdateDecision.Quiet)
        assertTrue(AppUpdate.decide(49, note, skippedCode = 50) is AppUpdateDecision.Quiet)
    }

    @Test
    fun theSkipFileRemembersOneVersionCode() {
        val dir = java.nio.file.Files.createTempDirectory("fw-update").toFile()
        assertNull(AppUpdateSkip.read(dir))
        AppUpdateSkip.write(dir, 50)
        assertEquals(50, AppUpdateSkip.read(dir))
        File(dir, "app-update-skip.txt").writeText("nope")
        assertNull(AppUpdateSkip.read(dir))
        AppUpdateSkip.write(dir, 0)
        assertNull(AppUpdateSkip.read(dir))
    }

    @Test
    fun anApkStartsWithAZipLocalHeader() {
        assertTrue(AppUpdate.apkLooksLikeZip(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFalse(AppUpdate.apkLooksLikeZip(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0)))
        assertFalse(AppUpdate.apkLooksLikeZip(byteArrayOf(0x50, 0x4B)))
    }

    @Test
    fun updateLinesBelongToTheNextRelease() {
        val keys = listOf(
            "update.allow.body",
            "update.allow.title",
            "update.checking",
            "update.current.body",
            "update.current.title",
            "update.downloading",
            "update.failed.body",
            "update.failed.title",
            "update.later",
            "update.now",
            "update.notes",
            "update.settings",
            "update.settings.note",
            "update.unreachable.body",
            "update.unreachable.title",
        )
        assertEquals(keys.size, AppUpdateLines.all.size)
        for (key in keys) {
            val line = EnglishCopy.byKey.getValue(key)
            val added = if (key == "update.notes") "1.1.41" else "1.1.40"
            assertEquals(key, added, line.addedIn)
            assertTrue(line.text.isNotBlank())
        }
        assertEquals("Update now", EnglishCopy.text("update.now"))
        assertEquals("Release Notes", EnglishCopy.text("update.notes"))
        assertEquals("Later", EnglishCopy.text("update.later"))
        assertTrue(EnglishCopy.text("update.current.body").contains("{version}"))
    }
}
