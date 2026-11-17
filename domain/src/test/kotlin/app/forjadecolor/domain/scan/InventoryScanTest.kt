package app.forjadecolor.domain.scan

import app.forjadecolor.domain.paints.Brush
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintFamily
import app.forjadecolor.domain.paints.PaintType
import app.forjadecolor.domain.pwaFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryScanTest {
    private val owned = Paint("Vallejo", "72.001", "Blanco Calavera", "Game Color", PaintType.OPAQUE, "#ffffff", PaintFamily("BSL Black", 1))
    private val inv = Inventory(listOf(owned), listOf(Brush("2", "Round")))

    private fun answer(body: String) = "Here is what I see:\n```json\n$body\n```\nHappy painting!"

    private fun success(raw: String): ScanResult.Success = assertIs<ScanResult.Success>(parseScanAnswer(raw, inv))
    private fun errors(raw: String): List<String> = assertIs<ScanResult.Failure>(parseScanAnswer(raw, inv)).errors

    @Test
    fun `the scan instructions are identical to the PWA's`() {
        assertEquals(pwaFixture("scan-prompt.txt"), buildScanPrompt())
    }

    @Test
    fun `the instructions ask to look up both names and the real colour, and not to invent`() {
        val prompt = buildScanPrompt()
        listOf("search the web", "\"nameEs\"", "\"nameEn\"", "leave it empty instead of inventing it").forEach { assertTrue(it in prompt, it) }
    }

    @Test
    fun `reads the Spanish and English names, falling back to the plain name`() {
        val result = success(
            """{"version": 1, "kind": "inventory", "paints": [
              {"brand": "Vallejo", "code": "72.090", "nameEs": "Lavado Negro", "nameEn": "Black Wash", "type": "wash", "hex": "#1a1a1a"},
              {"brand": "Citadel", "code": "Nuln Oil", "name": "Nuln Oil", "type": "wash"},
              {"brand": "Army Painter", "code": "WP1105", "nameEn": "Dragon Red"}
            ]}""",
        )
        assertEquals(
            listOf("Lavado Negro" to "Black Wash", "Nuln Oil" to null, "Dragon Red" to "Dragon Red"),
            result.paints.map { it.paint.name to it.paint.nameEn },
        )
    }

    @Test
    fun `the instructions ask for the scan JSON and say not to guess codes`() {
        val prompt = buildScanPrompt()
        assertTrue("\"kind\": \"inventory\"" in prompt)
        assertTrue("PAINT SCAN FORMAT" in prompt)
        assertTrue("leave \"code\" empty" in prompt)
    }

    @Test
    fun `reads paints and brushes, normalizing codes and marking the ones already owned`() {
        val result = success(
            answer(
                """{"version": 1, "kind": "inventory",
                  "paints": [
                    {"brand": "Vallejo", "code": "72001", "name": "Dead White", "range": "Game Color", "type": "opaque", "hex": "#FFFFFF"},
                    {"brand": "Vallejo", "code": "72.090", "name": "Black Wash", "range": "Game Wash", "type": "wash", "hex": "#1a1a1a"}
                  ],
                  "brushes": [{"code": "2", "name": "Round kolinsky"}, {"code": "0", "name": "Detail"}]}""",
            ),
        )

        assertEquals(listOf("72.001", "72.090"), result.paints.map { it.paint.code })
        assertEquals("#ffffff", result.paints[0].paint.hex)
        assertEquals(owned, result.paints[0].existing)
        assertNull(result.paints[1].existing)
        assertEquals(PaintType.WASH, result.paints[1].paint.type)
        assertEquals(Brush("2", "Round"), result.brushes[0].existing)
        assertNull(result.brushes[1].existing)
    }

    @Test
    fun `is lenient with what the AI gets loosely right`() {
        val result = success(
            """{"version": "1", "kind": "inventory",
              "paints": [
                {"brand": "Army Painter", "code": "", "name": "Dragon Red", "type": "Opaque", "hex": "red", "extra": 1},
                {"brand": "Vallejo", "code": "72.001", "name": "Dead White", "type": "glossy"},
                {"brand": "Vallejo", "code": "72001", "name": "Dead White again"},
                {"brand": "Vallejo", "code": "", "name": ""}
              ]}""",
        )

        assertEquals(listOf("Dragon Red", "Dead White"), result.paints.map { it.paint.name })
        val red = result.paints[0].paint
        assertEquals("", red.code)
        assertEquals(PaintType.OPAQUE, red.type)
        assertEquals("#888888", red.hex)
        assertEquals("", red.range)
        assertEquals(PaintType.OTHER, result.paints[1].paint.type)
        assertEquals(emptyList(), result.brushes)
    }

    @Test
    fun `explains what is wrong with an answer it cannot use`() {
        assertEquals(listOf("no JSON found in the answer"), errors("I could not read any label, sorry."))
        assertTrue(errors("```json\n{\"version\": 1, \"kind\": \"inventory\",\n```")[0].startsWith("invalid JSON"))
        assertEquals(listOf("version must be 1"), errors("""{"version": 2, "kind": "inventory", "paints": []}"""))
        assertEquals(listOf("this answer is not a paint list"), errors("""{"version": 1, "kind": "recipe"}"""))
        assertEquals(listOf("paints must be a list"), errors("""{"version": 1, "kind": "inventory", "paints": {}}"""))
        assertEquals(listOf("the AI did not find any paints or brushes"), errors("""{"version": 1, "kind": "inventory", "paints": [], "brushes": []}"""))
        assertEquals(listOf("this is the prompt, not the AI answer: paste what the AI replied"), errors(buildScanPrompt()))
        assertEquals(listOf("the answer is too long"), errors("x".repeat(MAX_SCAN_ANSWER_CHARS + 1)))
    }

    @Test
    fun `keeps at most the first 200 paints`() {
        val paints = (1..250).joinToString(",") { """{"brand": "Vallejo", "code": "70.${100 + it}", "name": "P$it"}""" }
        val result = success("""{"version": 1, "kind": "inventory", "paints": [$paints]}""")
        assertEquals(200, result.paints.size)
    }
}
