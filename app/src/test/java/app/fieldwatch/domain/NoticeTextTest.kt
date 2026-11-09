package app.fieldwatch.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeTextTest {
    @Test
    fun paragraphsKeepThePublishedNotes() {
        val file = listOf(File("dist/app-update.json"), File("../dist/app-update.json")).first { it.isFile }
        val note = AppUpdate.parse(file.readText())!!
        val parts = NoticeText.paragraphs(note.notes)
        assertEquals(6, parts.size)
        assertEquals(note.notes, parts.joinToString("\n\n"))
        assertTrue(parts[0].startsWith("A Bluetooth name of Quest"))
        assertTrue(parts[5].startsWith("Bundled language files"))
    }

    @Test
    fun blankLinesSplitAndEmptyOnesDrop() {
        assertEquals(emptyList<String>(), NoticeText.paragraphs(" \n\n "))
        assertEquals(listOf("One"), NoticeText.paragraphs("  One  "))
        assertEquals(listOf("One\nTwo"), NoticeText.paragraphs("One\nTwo"))
        assertEquals(listOf("One", "Two"), NoticeText.paragraphs("One\n\n\nTwo"))
        assertEquals(listOf("One", "Two"), NoticeText.paragraphs("One\r\n\r\nTwo"))
        assertEquals(listOf("One", "Two"), NoticeText.paragraphs("One\n \nTwo"))
    }

    @Test
    fun aLabelLineBecomesARowAndAMacStaysWhole() {
        val rows = NoticeText.rows("Generation: 5G\nPCI: 12\nAA:BB:CC:DD:EE:FF")
        assertEquals(NoticeText.Row("Generation", "5G"), rows[0])
        assertEquals(NoticeText.Row("PCI", "12"), rows[1])
        assertEquals(NoticeText.Row(null, "AA:BB:CC:DD:EE:FF"), rows[2])
        val band = "GPS L1: AGC +1.0 dB, signal -2.0 dB, 8 satellites (baseline 11)"
        assertEquals(
            NoticeText.Row("GPS L1", "AGC +1.0 dB, signal -2.0 dB, 8 satellites (baseline 11)"),
            NoticeText.rows(band).single(),
        )
    }
}
