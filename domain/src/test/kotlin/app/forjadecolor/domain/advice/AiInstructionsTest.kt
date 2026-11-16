package app.forjadecolor.domain.advice

import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintType
import app.forjadecolor.domain.paints.SEED_INVENTORY
import app.forjadecolor.domain.paints.addPaint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AiInstructionsTest {
    private val extra = Paint("Citadel", "Nuln Oil", "Nuln Oil", "Shade", PaintType.WASH, "#222222")

    @Test
    fun `the fingerprint is stable for equal instructions`() {
        assertEquals(
            instructionsFingerprint(buildBrainInstructions(SEED_INVENTORY)),
            instructionsFingerprint(buildBrainInstructions(SEED_INVENTORY.copy())),
        )
    }

    @Test
    fun `the fingerprint changes when the inventory changes`() {
        assertNotEquals(
            instructionsFingerprint(buildBrainInstructions(SEED_INVENTORY)),
            instructionsFingerprint(buildBrainInstructions(SEED_INVENTORY.addPaint(extra))),
        )
    }

    @Test
    fun `status is never without a stored fingerprint, current when it matches and stale otherwise`() {
        val text = buildBrainInstructions(SEED_INVENTORY)
        assertEquals(InstructionsStatus.NEVER, instructionsStatus(null, text))
        assertEquals(InstructionsStatus.CURRENT, instructionsStatus(instructionsFingerprint(text), text))
        assertEquals(InstructionsStatus.STALE, instructionsStatus(instructionsFingerprint(text), buildBrainInstructions(SEED_INVENTORY.addPaint(extra))))
    }
}
