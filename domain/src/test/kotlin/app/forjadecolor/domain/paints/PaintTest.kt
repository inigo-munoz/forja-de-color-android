package app.forjadecolor.domain.paints

import kotlin.test.Test
import kotlin.test.assertEquals

class PaintTest {
    @Test
    fun `formats a bare 5-digit Vallejo code as NN_NNN`() {
        assertEquals("72.003", normalizeCode("Vallejo", "72003"))
    }

    @Test
    fun `trims surrounding spaces from a Vallejo code`() {
        assertEquals("72.003", normalizeCode("Vallejo", " 72.003 "))
    }

    @Test
    fun `only trims codes of other brands`() {
        assertEquals("Abaddon-Black", normalizeCode("Citadel", "  Abaddon-Black "))
    }

    @Test
    fun `paint key is case-insensitive on the brand`() {
        assertEquals(paintKey(PaintRef("VALLEJO", "72.003")), paintKey(PaintRef("vallejo", "72.003")))
    }
}
