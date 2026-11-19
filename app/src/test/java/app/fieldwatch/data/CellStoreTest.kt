package app.fieldwatch.data

import app.fieldwatch.domain.CellFix
import app.fieldwatch.domain.CellGen
import app.fieldwatch.domain.CellIdentity
import app.fieldwatch.domain.CellReading
import app.fieldwatch.domain.CellSwitches
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

class CellStoreTest {
    @Test
    fun aSecondSaveReplacesTheFileAndKeepsThePlaces() {
        val dir = tempDir()
        val store = store(dir)
        store.tick(reading(100L), still(), switches(), 1_000L)
        assertTrue(store.flushForTest())
        store.tick(reading(200L), still(), switches(), 2_000L)
        assertTrue(store.flushForTest())
        val again = store(dir)
        again.load()
        val cells = again.bookFlow.value.places.single().cells.map { it.identity.cell }
        assertEquals(listOf(100L, 200L), cells)
        assertTrue(again.backupFile().readText().contains("\"cell\":200"))
    }

    @Test
    fun aTornFileLoadsTheLastGoodCopy() {
        val dir = tempDir()
        val store = store(dir)
        store.tick(reading(100L), still(), switches(), 1_000L)
        assertTrue(store.flushForTest())
        val live = File(dir, "cell-places.json")
        live.writeText("{torn")
        val again = store(dir)
        again.load()
        assertEquals(100L, again.bookFlow.value.places.single().cells.single().identity.cell)
        assertTrue(live.readText().contains("\"cell\":100"))
    }

    @Test
    fun anEmptyFileLoadsTheLastGoodCopy() {
        val dir = tempDir()
        val store = store(dir)
        store.tick(reading(100L), still(), switches(), 1_000L)
        assertTrue(store.flushForTest())
        val live = File(dir, "cell-places.json")
        live.writeText("")
        val again = store(dir)
        again.load()
        assertEquals(1, again.bookFlow.value.places.size)
        assertTrue(live.readText().contains("\"cell\":100"))
    }

    @Test
    fun aDamagedFileIsKeptWhenThereIsNoBackup() {
        val dir = tempDir()
        val live = File(dir, "cell-places.json")
        live.writeText("NOT A CELL FILE")
        val store = store(dir)
        store.load()
        assertTrue(store.bookFlow.value.places.isEmpty())
        val parked = File(dir, "cell-places.json.corrupt")
        assertEquals("NOT A CELL FILE", parked.readText())
        store.tick(reading(100L), still(), switches(), 1_000L)
        assertTrue(store.flushForTest())
        assertEquals("NOT A CELL FILE", parked.readText())
        assertFalse(live.readText().contains("NOT A CELL FILE"))
        assertEquals(100L, store.bookFlow.value.places.single().cells.single().identity.cell)
    }

    @Test
    fun aGoodFileGetsABackupWhenItIsOpened() {
        val dir = tempDir()
        val store = store(dir)
        store.tick(reading(100L), still(), switches(), 1_000L)
        assertTrue(store.flushForTest())
        assertTrue(store.backupFile().delete())
        val again = store(dir)
        again.load()
        assertTrue(again.backupFile().readText().contains("\"cell\":100"))
        assertEquals(100L, again.bookFlow.value.places.single().cells.single().identity.cell)
    }

    @Test
    fun anUnloadedStoreDoesNotReplaceTheFile() {
        val dir = tempDir()
        val store = store(dir)
        store.tick(reading(100L), still(), switches(), 1_000L)
        assertTrue(store.flushForTest())
        val live = File(dir, "cell-places.json")
        val before = live.readBytes()
        val fresh = store(dir)
        assertTrue(fresh.flushForTest())
        assertTrue(live.readBytes().contentEquals(before))
    }

    @Test
    fun forgettingTheLastPlaceRemovesItFromTheFile() {
        val dir = tempDir()
        val store = store(dir)
        store.tick(reading(100L), still(), switches(), 1_000L)
        assertTrue(store.flushForTest())
        val id = store.bookFlow.value.places.single().id
        store.forget(id, 2_000L)
        assertTrue(store.flushForTest())
        val again = store(dir)
        again.load()
        assertTrue(again.bookFlow.value.places.isEmpty())
    }

    @Test
    fun aLeftoverTemporaryFileIsIgnored() {
        val dir = tempDir()
        val store = store(dir)
        store.tick(reading(100L), still(), switches(), 1_000L)
        assertTrue(store.flushForTest())
        File(dir, "cell-places.json.tmp").writeText("{torn")
        val again = store(dir)
        again.load()
        assertEquals(100L, again.bookFlow.value.places.single().cells.single().identity.cell)
    }

    private fun store(dir: File) = CellStore(File(dir, "cell-places.json"), CoroutineScope(EmptyCoroutineContext))

    private fun tempDir(): File = File("build/tmp/cell-store-test-${System.nanoTime()}").apply { mkdirs() }

    private fun reading(cell: Long) = CellReading(
        registered = true,
        generation = CellGen.LTE,
        identity = CellIdentity(CellGen.LTE, "310-410", 1000, cell),
    )

    private fun still() = CellFix(lat = 28.0, lon = -81.0, accuracyM = 10f, speedMps = 0f)

    private fun switches() = CellSwitches(watch = true, learnDays = 0)
}
