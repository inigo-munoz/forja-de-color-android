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
    fun `carries the photo as the image`() {
        assertEquals("photo-1", start.imageId)
    }
}
