package app.forjadecolor.domain.paints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BslRoleTest {
    private fun stored(code: String, family: PaintFamily? = null, brand: String = "Vallejo") =
        Paint(brand = brand, code = code, name = "x", range = "Game Color", type = PaintType.OPAQUE, hex = "#000000", family = family)

    @Test
    fun `fills the role from the seed for a stored paint without one`() {
        assertEquals(BslRole.SHADOW, bslRole(stored("72.019", PaintFamily("BSL Blue", 4))))
        assertEquals(BslRole.FOURTH, bslRole(stored("72.003", PaintFamily("BSL Warm Flesh", 1))))
    }

    @Test
    fun `matches the seed through the normalized code and brand case`() {
        assertEquals(BslRole.LIGHT, bslRole(stored("72021", PaintFamily("BSL Blue", 2), brand = "vallejo")))
    }

    @Test
    fun `keeps an explicit role`() {
        assertEquals(BslRole.BASE, bslRole(stored("72.019", PaintFamily("BSL Blue", 4, BslRole.BASE))))
    }

    @Test
    fun `gives none when the family name differs from the seed`() {
        assertNull(bslRole(stored("72.019", PaintFamily("Custom", 4))))
    }

    @Test
    fun `gives none without a family or for a paint the seed has no role for`() {
        assertNull(bslRole(stored("72.019")))
        assertNull(bslRole(stored("72.011", PaintFamily("BSL Multipurpose", 1))))
    }
}
