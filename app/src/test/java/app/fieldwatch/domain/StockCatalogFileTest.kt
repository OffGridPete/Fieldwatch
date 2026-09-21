package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StockCatalogFileTest {
    @Test
    fun distPackMatchesDefaultCatalog() {
        val dest = distPackFile()
        val encoded = SignatureExchange.encode(
            SignatureExchange.pack(
                fleets = DefaultCatalog.fleets(),
                catalogVersion = ConfigStore.CATALOG_VERSION,
                appVersion = "stock",
                exportedAt = "",
            ),
        )
        if (System.getenv("WRITE_STOCK_CATALOG") == "1") {
            dest.parentFile?.mkdirs()
            dest.writeText(encoded)
        }
        assertTrue("Missing ${dest.absolutePath}. Run tests with WRITE_STOCK_CATALOG=1.", dest.isFile)
        val pack = SignatureExchange.parse(dest.readText())
        assertEquals("fieldwatch-signatures", pack.format)
        assertEquals(ConfigStore.CATALOG_VERSION, pack.catalogVersion)
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        assertEquals(stock.keys, pack.fleets.map { it.id }.toSet())
        for (row in pack.fleets) {
            val expect = stock.getValue(row.id)
            assertEquals(row.id, expect.name, row.name)
            assertEquals(row.id, expect.kind, row.kind)
            assertEquals(row.id, expect.attentionNote, row.attentionNote)
            assertEquals(row.id, expect.notes, row.notes)
            assertTrue(row.builtIn)
        }
    }

    private fun distPackFile(): File {
        val cwd = File(System.getProperty("user.dir")!!)
        val candidates = listOf(
            File(cwd, "dist/fieldwatch-signatures.json"),
            File(cwd.parentFile, "dist/fieldwatch-signatures.json"),
        )
        return candidates.first { it.parentFile?.exists() == true }
    }
}
