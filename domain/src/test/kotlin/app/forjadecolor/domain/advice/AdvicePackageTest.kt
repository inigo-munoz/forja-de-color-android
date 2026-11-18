package app.forjadecolor.domain.advice

import app.forjadecolor.domain.paints.SEED_INVENTORY
import app.forjadecolor.domain.pwaFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvicePackageTest {
    private val start = buildAdvicePackage(AdviceRequest(Moment.START), SEED_INVENTORY, "photo-1")

    @Test
    fun `start prompt is identical to the PWA's`() {
        assertEquals(pwaFixture("prompt-start.txt"), start.text)
    }

    @Test
    fun `half-painted prompt is identical to the PWA's`() {
        assertEquals(pwaFixture("prompt-half-painted.txt"), buildAdvicePackage(AdviceRequest(Moment.HALF_PAINTED), SEED_INVENTORY, "p").text)
    }

    @Test
    fun `primed prompt is identical to the PWA's and asks for an empty prep without complements`() {
        val primed = buildAdvicePackage(AdviceRequest(Moment.PRIMED), SEED_INVENTORY, "p").text
        assertEquals(pwaFixture("prompt-primed.txt"), primed)
        assertTrue(primed.contains("\"prep\": []"))
        assertFalse(primed.contains("\"complements\""))
    }

    @Test
    fun `during prompt with zone, question and details is identical to the PWA's`() {
        val req = AdviceRequest(Moment.DURING, zone = "capa", question = Question.HIGHLIGHT, freeText = "borde dorado")
        assertEquals(pwaFixture("prompt-during.txt"), buildAdvicePackage(req, SEED_INVENTORY, "p").text)
    }

    @Test
    fun `during prompt without zone or question is identical to the PWA's`() {
        assertEquals(pwaFixture("prompt-during-bare.txt"), buildAdvicePackage(AdviceRequest(Moment.DURING), SEED_INVENTORY, "p").text)
    }

    @Test
    fun `brain instructions are identical to the PWA's and carry no moment request`() {
        val brain = buildBrainInstructions(SEED_INVENTORY)
        assertEquals(pwaFixture("brain-instructions.txt"), brain)
        assertFalse(brain.contains("MOMENT:"))
        assertTrue(brain.contains("start, primed, half-painted or during"))
    }

    @Test
    fun `lists every owned paint code and stays under 8000 characters`() {
        SEED_INVENTORY.paints.forEach { assertTrue(start.text.contains(it.code)) }
        assertTrue(start.text.length < 8000)
    }

    @Test
    fun `fills the BSL roles from the seed for a stored inventory without roles`() {
        val bare = SEED_INVENTORY.copy(paints = SEED_INVENTORY.paints.map { p -> p.copy(family = p.family?.copy(role = null)) })
        assertEquals(pwaFixture("prompt-start.txt"), buildAdvicePackage(AdviceRequest(Moment.START), bare, "p").text)
    }

    @Test
    fun `carries the photo as the image`() {
        assertEquals("photo-1", start.imageId)
    }

    private fun v(code: String) = app.forjadecolor.domain.paints.PaintRef("Vallejo", code)

    @Test
    fun `half-painted with the earlier recipe is identical to the PWA's`() {
        val recipe = RecipeResponse(
            moment = RecipeMoment.START, prep = listOf("Imprimar en negro"), tips = listOf("Capas finas"),
            zones = listOf(
                ZoneRecipe("túnica", Technique.SLAPCHOP, paint = v("72.032"), dilution = "1:3", steps = listOf("Aplicar"), brush = "B02001"),
                ZoneRecipe("cara", Technique.BSL, bsl = Bsl(v("72.099"), v("72.071"), v("72.098")), steps = emptyList()),
                ZoneRecipe("peana", Technique.SLAPCHOP, paint = app.forjadecolor.domain.paints.PaintRef("Citadel", "Abaddon Black"), dilution = "1:1", steps = emptyList()),
            ),
        )
        val req = AdviceRequest(Moment.HALF_PAINTED, previous = PreviousRecipe(recipe, listOf(0)))
        assertEquals(pwaFixture("prompt-half-painted-previous.txt"), buildAdvicePackage(req, SEED_INVENTORY, "p").text)
        // Only half-painted carries it.
        assertFalse("PREVIOUS RECIPE" in buildAdvicePackage(AdviceRequest(Moment.START, previous = PreviousRecipe(recipe, emptyList())), SEED_INVENTORY, "p").text)
    }

    @Test
    fun `with an ink in the inventory the method says to use inks, identical to the PWA's`() {
        val ink = app.forjadecolor.domain.paints.Paint("Vallejo", "72.091", "Sepia", "Game Color", app.forjadecolor.domain.paints.PaintType.INK, "#563d27")
        val inv = SEED_INVENTORY.copy(paints = SEED_INVENTORY.paints + ink)
        assertEquals(pwaFixture("brain-instructions-ink.txt"), buildBrainInstructions(inv))
        assertTrue("I have no translucent paints" in buildBrainInstructions(SEED_INVENTORY))
    }
}
