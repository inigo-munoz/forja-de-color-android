package app.forjadecolor.domain.paints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaintSetsTest {
    @Test
    fun `knows Vallejo's NMM sets by Angel Giraldez, every paint in the catalogue`() {
        assertEquals(listOf("72.001", "72.019", "72.024", "72.050", "72.051", "72.091", "72.151", "72.153"), findSet("72212")!!.paints)
        assertEquals(8, findSet("72.193")!!.paints.size)
        PAINT_SETS.forEach { assertEquals(it.paints.size, setAsScan(it, Inventory(emptyList(), emptyList())).paints.size, it.code) }
    }

    @Test
    fun `finds a set by code or by words of its name`() {
        assertEquals(listOf("72.212"), searchSets("72212").map { it.code })
        assertEquals(listOf("72.212", "72.193"), searchSets("nmm").map { it.code })
        assertEquals(2, searchSets("giraldez").size)
        assertEquals(emptyList(), searchSets(""))
    }

    @Test
    fun `turns a set into a list to confirm - catalogue data, owned paints marked`() {
        val list = setAsScan(findSet("72.212")!!, SEED_INVENTORY)
        assertEquals(
            listOf("72.001" to true, "72.019" to true, "72.024" to true, "72.050" to false, "72.051" to true, "72.091" to false, "72.151" to false, "72.153" to false),
            list.paints.map { it.paint.code to (it.existing != null) },
        )
        assertEquals("Sepia" to PaintType.INK, list.paints[5].paint.name to list.paints[5].paint.type)
        assertEquals("Blanco Calavera", list.paints[0].paint.name)
        assertTrue(list.paints.all { it.fromCatalogue })
    }
}
