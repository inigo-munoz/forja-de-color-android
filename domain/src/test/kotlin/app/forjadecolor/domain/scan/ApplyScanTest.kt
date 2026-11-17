package app.forjadecolor.domain.scan

import app.forjadecolor.domain.paints.Brush
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintFamily
import app.forjadecolor.domain.paints.PaintType
import app.forjadecolor.domain.paints.addBrush
import app.forjadecolor.domain.paints.savePaintKeepingFamily
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplyScanTest {
    private val family = PaintFamily("BSL Black", 1)
    private val owned = Paint("Vallejo", "72.001", "Blanco Calavera", "Game Color", PaintType.OPAQUE, "#ffffff", family)
    private val inv = Inventory(listOf(owned), listOf(Brush("2", "Round")))

    @Test
    fun `a confirmed paint replaces the owned one but keeps its BSL family`() {
        val updated = inv.savePaintKeepingFamily(owned.copy(code = "72001", name = "Dead White", family = null))
        assertEquals(listOf(owned.copy(name = "Dead White")), updated.paints)
    }

    @Test
    fun `a brush with an existing code is updated, a new one is added`() {
        val updated = inv.addBrush(Brush(" 2 ", "Round kolinsky")).addBrush(Brush("0", "Detail"))
        assertEquals(listOf(Brush("2", "Round kolinsky"), Brush("0", "Detail")), updated.brushes)
    }

    @Test
    fun `applying a scan saves the confirmed items and skips incomplete ones`() {
        val newPaint = Paint("Vallejo", "72.090", "Black Wash", "Game Wash", PaintType.WASH, "#1a1a1a")
        val noCode = Paint("Army Painter", "", "Dragon Red", "", PaintType.OPAQUE, "#aa0000")
        val updated = inv.applyScan(listOf(newPaint, noCode), listOf(Brush("0", "Detail"), Brush("", "Nameless")))
        assertEquals(listOf(owned, newPaint), updated.paints)
        assertEquals(listOf(Brush("2", "Round"), Brush("0", "Detail")), updated.brushes)
    }
}
