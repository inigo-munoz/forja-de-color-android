package app.forjadecolor.domain.advice

import app.forjadecolor.domain.paints.PaintRef
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveTest {
    @Test
    fun `assigning an unknown paint replaces every occurrence and clears it from unknownPaints`() {
        val unknown = PaintRef("Citadel", "Abaddon Black")
        val owned = PaintRef("Vallejo", "72.051")
        val entry = AdviceEntry(
            id = "e1", receivedAt = "2026-11-11T10:00:00Z", unknownPaints = listOf(unknown), unknownBrushes = emptyList(),
            response = RecipeResponse(
                moment = RecipeMoment.START, prep = emptyList(), tips = emptyList(),
                zones = listOf(
                    ZoneRecipe("peana", Technique.SLAPCHOP, paint = unknown, dilution = "1:1", steps = emptyList()),
                    ZoneRecipe("cinturón", Technique.SLAPCHOP, paint = unknown, dilution = "1:2", steps = emptyList()),
                ),
            ),
        )
        val result = assignUnknownPaint(entry, unknown, owned)
        assertEquals(listOf(owned, owned), (result.response as RecipeResponse).zones.map { it.paint })
        assertEquals(emptyList(), result.unknownPaints)
    }
}
