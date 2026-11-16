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
    fun `gives each BSL family its roles from the book, lightest first`() {
        fun roles(name: String) = byFamily(name).map { it.family!!.role }
        val usual = listOf(BslRole.FOURTH, BslRole.LIGHT, BslRole.BASE, BslRole.SHADOW)
        listOf(
            "BSL Warm Flesh", "BSL Flesh", "BSL Yellow", "BSL Orange", "BSL Magenta", "BSL Violet",
            "BSL Blue", "BSL Turquoise", "BSL Cold Green", "BSL Black",
        ).forEach { assertEquals(usual, roles(it), it) }
        assertEquals(listOf("72.118", "72.021", "72.020", "72.019"), byFamily("BSL Blue").map { it.code })
        assertEquals(
            listOf("72.122" to BslRole.FOURTH, "72.032" to BslRole.LIGHT, "72.123" to BslRole.LIGHT, "72.028" to BslRole.BASE),
            byFamily("BSL Green").map { it.code to it.family!!.role },
        )
        roles("BSL Multipurpose").forEach { assertNull(it) }
    }

    @Test
    fun `includes exactly the three brushes the user owns`() {
        assertEquals(
            listOf(Brush("B02001", "Detail núm. 1"), Brush("B02002", "Detail núm. 2"), Brush("B07001", "Dry Brush Small")),
            SEED_INVENTORY.brushes,
        )
    }
}
