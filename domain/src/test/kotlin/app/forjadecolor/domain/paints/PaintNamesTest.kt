package app.forjadecolor.domain.paints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaintNamesTest {
    private fun stored(code: String = "72.001", brand: String = "Vallejo", name: String = "Blanco Calavera", nameEn: String? = null) =
        Paint(brand, code, name, "Game Color", PaintType.OPAQUE, "#ffffff", nameEn = nameEn)

    @Test
    fun `every seed BSL paint has its English name`() {
        assertTrue(SEED_INVENTORY.paints.all { !it.nameEn.isNullOrBlank() })
        assertEquals("Dead White", SEED_INVENTORY.paints.first { it.code == "72.001" }.nameEn)
    }

    @Test
    fun `a paint saved before English names existed takes it from the seed by code`() {
        assertEquals("Dead White", englishName(stored()))
        assertEquals("Dead White", englishName(stored(code = "72001")))
    }

    @Test
    fun `its own English name wins, and a paint outside the seed may have none`() {
        assertEquals("Skull White", englishName(stored(nameEn = "Skull White")))
        assertNull(englishName(stored(brand = "Citadel", code = "Abaddon Black", name = "Abaddon Black")))
    }

    @Test
    fun `shows both names, or one when they are the same or there is no English name`() {
        assertEquals("Blanco Calavera · Dead White", displayName(stored()))
        assertEquals("Abaddon Black", displayName(stored(brand = "Citadel", code = "X", name = "Abaddon Black", nameEn = "Abaddon Black")))
        assertEquals("Nuln Oil", displayName(stored(brand = "Citadel", code = "X", name = "Nuln Oil")))
    }
}
