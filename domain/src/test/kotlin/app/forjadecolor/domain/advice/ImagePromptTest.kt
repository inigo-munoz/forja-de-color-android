package app.forjadecolor.domain.advice

import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.SEED_INVENTORY
import app.forjadecolor.domain.pwaFixture
import kotlin.test.Test
import kotlin.test.assertEquals

class ImagePromptTest {
    private fun v(code: String) = PaintRef("Vallejo", code)

    @Test
    fun `image prompt is identical to the PWA's`() {
        val recipe = RecipeResponse(
            moment = RecipeMoment.START, prep = listOf("Imprimar en negro"), tips = listOf("Capas finas"),
            zones = listOf(
                ZoneRecipe("túnica", Technique.SLAPCHOP, paint = v("72.032"), dilution = "1:3", steps = listOf("Aplicar"), brush = "B02001"),
                ZoneRecipe("cara", Technique.BSL, bsl = Bsl(v("72.099"), v("72.071"), v("72.098")), steps = emptyList()),
                ZoneRecipe("peana", Technique.SLAPCHOP, paint = PaintRef("Citadel", "Abaddon Black"), dilution = "1:1", steps = emptyList()),
            ),
        )
        assertEquals(pwaFixture("image-prompt.txt"), buildImagePrompt(recipe, SEED_INVENTORY))
    }
}
