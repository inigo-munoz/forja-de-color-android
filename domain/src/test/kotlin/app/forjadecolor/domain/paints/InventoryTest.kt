package app.forjadecolor.domain.paints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InventoryTest {
    private val carnePalida = Paint(
        brand = "Vallejo", code = "72.003", name = "Carne Pálida", range = "Game Color", type = PaintType.OPAQUE,
        hex = "#e6c9a8", family = PaintFamily("BSL Warm Flesh", 1),
    )
    private val negro = Paint(
        brand = "Vallejo", code = "72.051", name = "Negro", range = "Game Color", type = PaintType.OPAQUE,
        hex = "#000000", family = PaintFamily("BSL Black", 4),
    )
    private val base = Inventory(paints = listOf(carnePalida, negro), brushes = emptyList())

    @Test
    fun `replaces a paint with the same brand and code instead of duplicating it`() {
        val result = base.addPaint(carnePalida.copy(name = "Renamed"))
        assertEquals(2, result.paints.size)
        assertEquals("Renamed", result.findPaint(PaintRef("Vallejo", "72.003"))?.name)
    }

    @Test
    fun `adds a paint with a new key`() {
        assertEquals(3, base.addPaint(negro.copy(code = "72.001", name = "Blanco Calavera")).paints.size)
    }

    @Test
    fun `stores the added paint with a normalized code`() {
        val result = base.addPaint(negro.copy(code = "72001", name = "Blanco Calavera"))
        assertEquals("72.001", result.paints.last().code)
    }

    @Test
    fun `removes a paint by reference`() {
        assertEquals(listOf(carnePalida), base.removePaint(PaintRef("Vallejo", "72.051")).paints)
    }

    @Test
    fun `finds a paint written as Vallejo 72_003`() {
        assertEquals("Carne Pálida", base.findPaintLoose("Vallejo 72.003")?.name)
    }

    @Test
    fun `finds a paint written as a bare 72003`() {
        assertEquals("Carne Pálida", base.findPaintLoose("72003")?.name)
    }

    @Test
    fun `returns null for a paint not in the inventory`() {
        assertNull(base.findPaintLoose("Citadel Abaddon Black"))
    }
    @Test
    fun `finds the owned paints mentioned in a sentence, distinct and in order of appearance`() {
        val text = "Añade un punto de Vallejo 72.051 en 2 capas a 1:3, luego 72003, remata con Vallejo 72.051 y 72.999"
        assertEquals(listOf("Negro", "Carne Pálida"), base.paintsMentionedIn(text).map { it.name })
    }

    @Test
    fun `finds no paints in a sentence without owned codes`() {
        assertEquals(emptyList(), base.paintsMentionedIn("Diluye 1:3 y aplica 2 capas de Citadel Abaddon Black"))
    }
}
