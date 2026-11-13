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
            "A popup lets you know when a tracker seems to be following you. " +
                "Tracker monitoring is in Settings, and both warnings stay off until you turn one on. " +
                "Separated tag with you warns when a tag says it is away from its owner, " +
                "this phone has heard it for 30 minutes with no gap over 20 minutes, " +
                "and GPS has three places at least 100 m apart. " +
                "Heard at two places warns when a finder with a stable address reaches two stops. " +
                "There is no beep. Open radio opens that radio. " +
                "Dismiss keeps that warning down while the same condition continues. " +
                "A quiet notification shows the same lines when Fieldwatch is not on screen.\n\n" +
                "These warnings are a guide, not proof. Fieldwatch will not catch every tracker, " +
                "and a warning does not mean someone is tracking you. " +
                "If you think you are in danger, contact local law enforcement.\n\n" +
                "Pebblebee also matches Bluetooth service FA25. A tag that leaves its name off still matches. " +
                "Google Find Hub shows a 20-byte or 32-byte ephemeral id. Frame 0x40 is labeled not separated. " +
                "A Find My advertisement of 25 bytes says it is broadcasting a public key. " +
                "A 2-byte advertisement says it is the short form, often a tag near its owner, and that this is not certain. " +
                "The battery level shows only when the recent-owner bit is set.\n\n" +
                "Exposure Notification names a Bluetooth advertisement on service FD6F and leaves the identifier and the metadata off the page. " +
                "BTHome shows the measurements a sensor advertises, and an open door or window shows on the live row. " +
                "Amazon Sidewalk shows the beacon type and the battery band, and registration mode shows on the live row. " +
                "An Echo without that service stays Amazon. " +
                "Matter setup shows the vendor ID, the product ID, and the discriminator, and proximity ranging leaves the hidden id off the page. " +
                "HomeKit names the category and the paired line. An unpaired accessory shows that category on the live row. " +
                "None of these beep.\n\n" +
                "Remote ID authentication shows the type, page, and time the plate sends. " +
                "The signature bytes stay off the page and are not checked. " +
                "A location message in the same session keeps that block. " +
                "French Direct Remote ID can fill the aircraft position, altitude, speed, course, and the takeoff pin when that Wi-Fi beacon is present. " +
                "Height above takeoff shows on the radio page and is not the aircraft pin. Android 11 or newer.\n\n" +
                "A Wi-Fi access point can say what kind of network it is, whether it has internet, a venue, and a hotspot id. " +
                "Detail shows that when the beacon includes it. The access point sets these. They are not checked. " +
                "Compare and Debrief keep the percent moving on a large sit. " +
                "Compare (text) and Debrief (text) share a text file. If a share cannot open, Fieldwatch says why.\n\n" +
                "Bundled language files are written for this version. A language file can be up to 352 KiB."
        val file = listOf(File("dist/app-update.json"), File("../dist/app-update.json")).first { it.isFile }
        assertEquals(AppUpdateNote(54, "1.1.44", published), AppUpdate.parse(file.readText()))
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
