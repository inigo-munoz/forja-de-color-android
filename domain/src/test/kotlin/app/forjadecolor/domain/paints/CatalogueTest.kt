package app.forjadecolor.domain.paints

import app.forjadecolor.domain.scan.ScanResult
import app.forjadecolor.domain.scan.checkInCatalogue
import app.forjadecolor.domain.scan.parseScanAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogueTest {
    private fun scanned(brand: String = "Vallejo", code: String = "", name: String = "", nameEn: String? = null, hex: String = "#888888") =
        Paint(brand, code, name, "", PaintType.OTHER, hex, nameEn = nameEn)

    @Test
    fun `carries the Vallejo, Citadel and Army Painter lists`() {
        assertEquals(setOf("Army Painter", "Citadel", "Vallejo"), CATALOGUE.map { it.brand }.toSet())
        assertTrue(CATALOGUE.size > 2000)
        val e = findInCatalogue("Vallejo", "72.010")!!
        assertEquals(listOf("Bloody Red", "Game Color", "#d41c1c"), listOf(e.nameEn, e.range, e.hex))
        assertEquals(PaintType.OPAQUE, e.type)
    }

    @Test
    fun `finds a paint by code however it is written, or by name for Citadel`() {
        assertEquals("Bloody Red", findInCatalogue("vallejo", "72010")?.nameEn)
        assertEquals("Nuln Oil", findInCatalogue("Citadel", "", "nuln oil")?.code)
        assertEquals(PaintType.WASH, findInCatalogue("Citadel", "", "nuln oil")?.type)
        assertEquals("Base", findInCatalogue("Games Workshop", "Abaddon Black")?.range)
        assertEquals("Abomination Gore", findInCatalogue("The Army Painter", "WP1401")?.nameEn)
        assertNull(findInCatalogue("Vallejo", "99.999"))
    }

    @Test
    fun `searches by code or name, ignoring case and accents, exact codes first`() {
        assertEquals("72.010", searchCatalogue("72.010").first().code)
        assertEquals("72.010", searchCatalogue("72010").first().code)
        assertTrue(searchCatalogue("nuln oil").any { it.code == "Nuln Oil" })
        assertTrue(searchCatalogue("BLOODY red").any { it.code == "72.010" })
        assertEquals(emptyList(), searchCatalogue(""))
        assertEquals(5, searchCatalogue("a", 5).size)
    }

    @Test
    fun `a catalogue paint takes the seed Spanish name for BSL codes, else the English name`() {
        val white = paintFromCatalogue(findInCatalogue("Vallejo", "72.001")!!)
        assertEquals(listOf("Blanco Calavera", "Dead White", "72.001"), listOf(white.name, white.nameEn, white.code))
        assertEquals("Bloody Red", paintFromCatalogue(findInCatalogue("Vallejo", "72.010")!!).name)
        assertEquals("Rojo Sanguina", paintFromCatalogue(findInCatalogue("Vallejo", "72.010")!!, "Rojo Sanguina").name)
    }

    @Test
    fun `a scanned paint found in the catalogue takes its data, keeping a Spanish name the AI gave`() {
        val found = lookUpInCatalogue(scanned(code = "72010", name = "Rojo Sanguina", nameEn = "Blood Red", hex = "#ff0000"))
        assertTrue(found.fromCatalogue)
        val p = found.paint
        assertEquals(listOf("Vallejo", "72.010", "Rojo Sanguina", "Bloody Red", "Game Color", "#d41c1c"), listOf(p.brand, p.code, p.name, p.nameEn, p.range, p.hex))
        assertEquals(PaintType.OPAQUE, p.type)

        val englishOnly = lookUpInCatalogue(scanned(brand = "Citadel", name = "Nuln Oil", nameEn = "Nuln Oil")).paint
        assertEquals(listOf("Nuln Oil", "Nuln Oil"), listOf(englishOnly.code, englishOnly.name))

        val unknown = scanned(brand = "Scale75", code = "SC-01", name = "Black", hex = "#111111")
        assertEquals(CatalogueLookup(unknown, false), lookUpInCatalogue(unknown))
    }

    @Test
    fun `checking a scan fills catalogue data, marks owned paints again and drops paints that turn out the same`() {
        val owned = SEED_INVENTORY.paints.first { it.code == "72.001" }
        val inv = Inventory(listOf(owned), emptyList())
        val parsed = assertIs<ScanResult.Success>(
            parseScanAnswer(
                """{"version": 1, "kind": "inventory", "paints": [
                  {"brand": "Vallejo", "code": "72010", "nameEs": "Rojo Sanguina", "nameEn": "Blood Red", "hex": "#ff0000"},
                  {"brand": "Vallejo", "code": "", "nameEn": "Dead White"},
                  {"brand": "Vallejo", "code": "72.001", "nameEn": "Dead White"},
                  {"brand": "Scale75", "code": "SC-01", "name": "Black", "hex": "#111111"}
                ], "brushes": [{"code": "0", "name": "Detail"}]}""",
                inv,
            ),
        )
        val r = checkInCatalogue(parsed, inv)
        assertEquals(
            listOf(Triple("72.010", "Rojo Sanguina", true), Triple("72.001", "Blanco Calavera", true), Triple("SC-01", "Black", false)),
            r.paints.map { Triple(it.paint.code, it.paint.name, it.fromCatalogue) },
        )
        assertEquals(owned, r.paints[1].existing)
        assertEquals(1, r.brushes.size)
    }
}
