package app.forjadecolor.domain.projects

import app.forjadecolor.domain.advice.AdviceRequest
import app.forjadecolor.domain.advice.Bsl
import app.forjadecolor.domain.advice.Moment
import app.forjadecolor.domain.advice.RecipeMoment
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.Technique
import app.forjadecolor.domain.advice.ZoneRecipe
import app.forjadecolor.domain.advice.buildAdvicePackage
import app.forjadecolor.domain.advice.buildImagePrompt
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.SEED_INVENTORY
import app.forjadecolor.domain.pwaFixture
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaintingStyleTest {
    // Same order as the PWA fixture generator: each style, each base setting, then OSL + winter.
    private val looks = PaintingStyle.entries.map { Look(style = it) } +
        BaseSetting.entries.map { Look(baseSetting = it) } +
        Look(PaintingStyle.OSL, BaseSetting.WINTER) +
        Look(baseMotif = "río de lava") +
        Look(PaintingStyle.GRIMDARK, BaseSetting.DESERT, "ruinas")

    private fun v(code: String) = PaintRef("Vallejo", code)

    @Test
    fun `every style and base setting writes the PWA's advisor prompt`() {
        val empty = Inventory(emptyList(), emptyList())
        val prompts = looks.map { buildAdvicePackage(AdviceRequest(Moment.START, style = it.style, baseSetting = it.baseSetting, baseMotif = it.baseMotif), empty, "p").text }
        assertEquals(pwaFixture("prompt-looks.txt"), prompts.joinToString("\n-----\n"))
    }

    @Test
    fun `every style and base setting writes the PWA's image prompt`() {
        val recipe = RecipeResponse(
            moment = RecipeMoment.START, prep = listOf("Imprimar en negro"), tips = listOf("Capas finas"),
            zones = listOf(
                ZoneRecipe("túnica", Technique.SLAPCHOP, paint = v("72.032"), dilution = "1:3", steps = listOf("Aplicar"), brush = "B02001"),
                ZoneRecipe("cara", Technique.BSL, bsl = Bsl(v("72.099"), v("72.071"), v("72.098")), steps = emptyList()),
                ZoneRecipe("peana", Technique.SLAPCHOP, paint = PaintRef("Citadel", "Abaddon Black"), dilution = "1:1", steps = emptyList()),
            ),
        )
        assertEquals(pwaFixture("image-prompt-looks.txt"), looks.joinToString("\n-----\n") { buildImagePrompt(recipe, SEED_INVENTORY, it) })
    }

    @Test
    fun `a project without a style is painted with my method, and setLook stores my method as absent`() {
        val p = createProject("Búho", Instant.parse("2026-11-17T10:00:00Z"), "p1")
        assertEquals(Look(PaintingStyle.MY_METHOD), p.look)
        val styled = p.setLook(Look(PaintingStyle.NMM, BaseSetting.WINTER))
        assertEquals(Look(PaintingStyle.NMM, BaseSetting.WINTER), styled.look)
        assertEquals(p, styled.setLook(Look(PaintingStyle.MY_METHOD)))
        assertNull(styled.setLook(Look(PaintingStyle.MY_METHOD)).style)
    }

    @Test
    fun `a free-text motif is stored trimmed next to the base setting, and a blank one is null`() {
        val p = createProject("Búho", Instant.parse("2026-11-18T10:00:00Z"), "p1")
        assertEquals(Look(PaintingStyle.MY_METHOD, BaseSetting.WINTER, "ruinas nevadas"), p.setLook(Look(baseSetting = BaseSetting.WINTER, baseMotif = "  ruinas nevadas.  ")).look)
        assertNull(p.setLook(Look(baseMotif = "   ")).baseMotif)
    }
}
