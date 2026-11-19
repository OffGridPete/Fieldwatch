package app.fieldwatch.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterQueryDraftTest {
    @Test
    fun firstLetterStaysWhileTheSaveIsStillEmpty() {
        val draft = FilterQueryDraft("")
        draft.edit("a")
        draft.observe("")
        assertEquals("a", draft.text)
        draft.observe("a")
        assertEquals("a", draft.text)
    }

    @Test
    fun aLaterLetterIsNotReplacedByThePreviousSave() {
        val draft = FilterQueryDraft("")
        draft.edit("a")
        draft.edit("ab")
        draft.observe("a")
        assertEquals("ab", draft.text)
        draft.observe("ab")
        assertEquals("ab", draft.text)
    }

    @Test
    fun resetAndPresetReplaceWhatWasTyped() {
        val draft = FilterQueryDraft("hi")
        draft.edit("hit")
        draft.observe("hit")
        draft.observe("")
        assertEquals("", draft.text)

        draft.edit("a")
        draft.observe("camera")
        assertEquals("camera", draft.text)
        draft.observe("a")
        assertEquals("camera", draft.text)
    }
}
