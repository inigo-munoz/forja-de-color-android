package app.forjadecolor.domain.advice

import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.SEED_INVENTORY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Answers captured on 2026-11-14 from ChatGPT and Claude, given the real start prompt
 * (pwa/prompt-start.txt) and a photo of a primed, drybrushed dryad. They prove the prompt and the
 * parser agree on what the AIs actually write, not on what we assume they write.
 */
class RealAnswersTest {
    private fun answer(name: String) = checkNotNull(javaClass.getResource("/ai/$name")).readText()
    private fun v(code: String) = PaintRef("Vallejo", code)

    private fun parsed(raw: String): ParseResult.Success = assertIs(parseAdviceResponse(raw, SEED_INVENTORY))

    @Test
    fun `the ChatGPT answer parses, with every paint and brush owned`() {
        val r = parsed(answer("chatgpt-start-dryad.json"))
        val recipe = assertIs<RecipeResponse>(r.entry.response)
        assertEquals(8, recipe.zones.size)
        assertEquals(listOf(Technique.BSL, Technique.BSL), recipe.zones.filter { it.technique == Technique.BSL }.map { it.technique })
        assertEquals(emptyList(), r.entry.unknownPaints)
        assertEquals(emptyList(), r.entry.unknownBrushes)
        assertEquals(
            listOf("72.124", "72.032", "72.026", "72.011", "72.099", "72.071", "72.098", "72.042", "72.045", "72.001", "72.007").map(::v),
            r.neededPaints,
        )
    }

    @Test
    fun `the Claude answer parses, with every paint and brush owned`() {
        val r = parsed(answer("claude-start-dryad.json"))
        val recipe = assertIs<RecipeResponse>(r.entry.response)
        assertEquals(listOf("Arco de ramas y tronco", "Astas", "Vestido", "Pelo", "Piel (cara, brazos, torso)", "Cinturón (punto focal secundario)", "Peana"), recipe.zones.map { it.name })
        assertEquals(emptyList(), r.entry.unknownPaints)
        assertEquals(emptyList(), r.entry.unknownBrushes)
        assertEquals(
            listOf("72.124", "72.042", "72.026", "72.009", "72.099", "72.071", "72.098", "72.006", "72.007", "72.005", "72.028").map(::v),
            r.neededPaints,
        )
    }

    @Test
    fun `both answers also parse when copied with the json fence and chat prose around them`() {
        for (name in listOf("chatgpt-start-dryad.json", "claude-start-dryad.json")) {
            parsed("Aquí tienes la receta:\n\n```json\n${answer(name)}\n```\n\n¿Quieres que ajuste algo?")
        }
    }

    @Test
    fun `the image prompt for the ChatGPT recipe names each zone with its colours`() {
        val recipe = parsed(answer("chatgpt-start-dryad.json")).entry.response as RecipeResponse
        val prompt = buildImagePrompt(recipe, SEED_INVENTORY)
        assertEquals(true, prompt.contains("- Vestido y falda: Verde Jade #007254"))
        assertEquals(true, prompt.contains("- Piel del rostro, pecho y brazos: base Piel #e3ac86, shadows Piel de Bárbaro #926640, highlights Carne Élfica #ebd7b9"))
    }
}
