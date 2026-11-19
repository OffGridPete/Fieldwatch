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
            "Drone monitoring is in Settings. " +
                "Drone swarm detection warns when Remote ID detects new drones in the time window you set. " +
                "Drone perimeter detection warns when a drone using Remote ID is inside the distance you pick: " +
                "100 m, 250 m, 500 m, or 1 km. It starts at 250 m and needs a GPS fix of 50 m or better. " +
                "Beep and voice are on until you turn them off. System notification is off until you turn it on. " +
                "The warning still opens if those are off. " +
                "The live row says within that distance while the drone is still inside.\n\n" +
                "Low battery protection mode is off until you turn it on. " +
                "A low battery slows the scan to Battery saver, then can stop it. " +
                "The Wi-Fi and Bluetooth icons turn yellow while Battery saver is on. " +
                "Plugging in undoes the slow-down. A stop stays stopped until you open Fieldwatch.\n\n" +
                "Tracker monitoring beeps and speaks when a warning first opens. " +
                "Both are on until you turn one off. System notification is off until you turn it on. " +
                "The popup still opens if those are off. " +
                "Separated tag with you says a tracking tag is away from its owner, " +
                "how long this phone has heard it, and at how many places. " +
                "Heard at two places says a tracking tag was heard where this phone was.\n\n" +
                "These warnings are a guide, not proof. Fieldwatch will not catch every tracker, " +
                "and a warning does not mean someone is tracking you. " +
                "If you think you are in danger, contact local law enforcement.\n\n" +
                "The Settings list calls that section Signature watchlist alerts.\n\n" +
                "The stock catalog names more radios. Two-way radios are in, and none of them beep. " +
                "Mentra Live glasses, the Steiner M830 LRF binocular, and TBS Crossfire do beep. " +
                "The other new rows do not. Brilliant Frame is now Brilliant Labs glasses (Frame / Halo). " +
                "Even G1 is now Even Realities G1 / G2. Apple AirTags is now Find My (AirTag or other Apple). " +
                "Update stock catalog from GitHub pulls the new signature file. " +
                "A phone still on 1.1.44 keeps using the older file.\n\n" +
                "Bundled language files are written for this version."
        val file = listOf(File("dist/app-update.json"), File("../dist/app-update.json")).first { it.isFile }
        assertEquals(AppUpdateNote(55, "1.2.0", published), AppUpdate.parse(file.readText()))
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
