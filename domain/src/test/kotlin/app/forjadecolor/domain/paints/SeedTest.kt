package app.forjadecolor.domain.paints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeedTest {
    private val paints = SEED_INVENTORY.paints
    private fun byFamily(name: String) = paints.filter { it.family?.name == name }.sortedBy { it.family!!.order }

    @Test
    fun `has the 48 Vallejo Game Color paints, all opaque`() {
        assertEquals(48, paints.size)
        paints.forEach {
            assertEquals("Vallejo", it.brand)
            assertEquals("Game Color", it.range)
            assertEquals(PaintType.OPAQUE, it.type)
        }
    }

    @Test
    fun `has 48 unique brand and code keys`() {
        assertEquals(48, paints.map(::paintKey).toSet().size)
    }

    @Test
    fun `groups them in 12 families with orders exactly 1 to 4`() {
        val families = paints.map { it.family?.name }.distinct()
        assertEquals(12, families.size)
        families.forEach { assertEquals(listOf(1, 2, 3, 4), byFamily(it!!).map { p -> p.family!!.order }) }
    }

    @Test
    fun `orders BSL Black from Blanco Calavera to Negro`() {
        val black = byFamily("BSL Black")
        assertEquals("72.001" to "Blanco Calavera", black[0].code to black[0].name)
        assertEquals("72.051" to "Negro", black[3].code to black[3].name)
    }

    @Test
    fun `starts BSL Warm Flesh with Carne Palida`() {
        val warm = byFamily("BSL Warm Flesh")
        assertEquals("72.003" to "Carne Pálida", warm[0].code to warm[0].name)
    }

    @Test
    fun `gives every paint a lowercase rrggbb display colour`() {
        paints.forEach { assertTrue(Regex("^#[0-9a-f]{6}$").matches(it.hex), it.hex) }
    }

    @Test
    fun `leaves the BSL role unset until confirmed from the book`() {
        paints.forEach { assertNull(it.family?.role) }
    }

    @Test
    fun `includes exactly the three brushes the user owns`() {
        assertEquals(
            listOf(Brush("B02001", "Detail núm. 1"), Brush("B02002", "Detail núm. 2"), Brush("B07001", "Dry Brush Small")),
            SEED_INVENTORY.brushes,
        )
    }
}
