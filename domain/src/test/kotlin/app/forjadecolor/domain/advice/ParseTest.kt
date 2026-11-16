package app.forjadecolor.domain.advice

import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.SEED_INVENTORY
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParseTest {
    private fun ref(code: String, brand: String = "Vallejo") = buildJsonObject { put("brand", brand); put("code", code) }

    private fun slap(code: String, name: String = "capa", edit: MutableMap<String, JsonElement>.() -> Unit = {}): JsonObject {
        val m = mutableMapOf<String, JsonElement>(
            "name" to JsonPrimitive(name), "technique" to JsonPrimitive("slapchop"), "paint" to ref(code),
            "dilution" to JsonPrimitive("1:3"), "steps" to buildJsonArray { add(JsonPrimitive("Aplicar")) },
        )
        m.edit()
        return JsonObject(m)
    }

    private fun recipe(vararg zones: JsonElement, edit: MutableMap<String, JsonElement>.() -> Unit = {}): JsonObject {
        val m = mutableMapOf<String, JsonElement>(
            "version" to JsonPrimitive(1), "kind" to JsonPrimitive("recipe"), "moment" to JsonPrimitive("start"),
            "prep" to buildJsonArray { add(JsonPrimitive("Imprimar en negro")) }, "zones" to buildJsonArray { zones.forEach { add(it) } },
            "tips" to buildJsonArray { add(JsonPrimitive("Capas finas")) },
        )
        m.edit()
        return JsonObject(m)
    }

    private fun parse(o: JsonElement) = parseAdviceResponse(o.toString(), SEED_INVENTORY)
    private fun errorsOf(r: ParseResult) = assertIs<ParseResult.Failure>(r).errors.joinToString(" ")
    private fun ok(r: ParseResult) = assertIs<ParseResult.Success>(r)

    // extraction
    @Test fun `extracts JSON from prose with a json fence`() {
        ok(parseAdviceResponse("Aquí tienes:\n```json\n${recipe(slap("72.021"))}\n```\n¡Suerte!", SEED_INVENTORY))
    }

    @Test fun `accepts a bare JSON object`() { ok(parse(recipe(slap("72.021")))) }

    @Test fun `accepts a primed recipe with no prep steps`() {
        val r = ok(parse(recipe(slap("72.021")) { put("moment", JsonPrimitive("primed")); put("prep", buildJsonArray {}) }))
        val response = assertIs<RecipeResponse>(r.entry.response)
        assertEquals(RecipeMoment.PRIMED, response.moment)
        assertEquals(emptyList(), response.prep)
    }

    @Test fun `rejects a recipe with an unknown moment naming the accepted ones`() {
        assertContains(
            errorsOf(parse(recipe(slap("72.021")) { put("moment", JsonPrimitive("during")) })),
            "recipe: moment must be \"start\", \"primed\" or \"half-painted\"",
        )
    }

    @Test fun `rejects text with no JSON`() {
        assertContains(errorsOf(parseAdviceResponse("Lo siento, no puedo ayudar.", SEED_INVENTORY)), "no JSON found in the answer")
    }

    @Test fun `rejects invalid JSON`() {
        assertContains(errorsOf(parseAdviceResponse("```json\n{ \"version\": 1, \n```", SEED_INVENTORY)), "invalid JSON")
    }

    @Test fun `rejects an unsupported version`() {
        assertContains(errorsOf(parse(recipe(slap("72.021")) { put("version", JsonPrimitive(2)) })), "unsupported version: 2")
    }

    // validation
    @Test fun `names the zone and the field when a slapchop zone has no dilution`() {
        assertContains(errorsOf(parse(recipe(slap("72.021", "capa roja") { remove("dilution") }))), "zone capa roja: slapchop needs a dilution")
    }

    @Test fun `accepts 1_3 and rejects a little water as dilution`() {
        ok(parse(recipe(slap("72.021"))))
        assertContains(
            errorsOf(parse(recipe(slap("72.021") { put("dilution", JsonPrimitive("a little water")) }))),
            "zone capa: dilution \"a little water\" must look like \"1:3\"",
        )
    }

    @Test fun `requires base, shadow and light on a bsl zone`() {
        val zone = buildJsonObject {
            put("name", "cara"); put("technique", "bsl"); putJsonObject("bsl") { put("base", ref("72.099")) }; putJsonArray("steps") {}
        }
        assertContains(errorsOf(parse(recipe(zone))), "zone cara: bsl needs base, shadow and light")
    }

    // resolution
    @Test fun `resolves loosely written codes to the owned paint`() {
        val r = ok(parse(recipe(slap("72021"), slap("Vallejo 72.020", "botas"))))
        assertEquals(emptyList(), r.entry.unknownPaints)
        assertEquals(listOf("72.021", "72.020"), (r.entry.response as RecipeResponse).zones.map { it.paint?.code })
    }

    @Test fun `keeps the recipe and flags a paint not in the inventory`() {
        val r = ok(parse(recipe(slap("x") { put("paint", ref("Abaddon Black", "Citadel")) })))
        assertEquals(listOf(PaintRef("Citadel", "Abaddon Black")), r.entry.unknownPaints)
    }

    @Test fun `flags a brush not in the inventory and matches owned brushes case-insensitively`() {
        val r = ok(parse(recipe(slap("72.021") { put("brush", JsonPrimitive("b02001")) }, slap("72.020", "botas") { put("brush", JsonPrimitive("X999")) })))
        assertEquals(listOf("X999"), r.entry.unknownBrushes)
    }

    @Test fun `lists a paint used in two zones only once among the needed paints`() {
        val r = ok(parse(recipe(slap("72.049"), slap("72.049", "peana"))))
        assertEquals(listOf(PaintRef("Vallejo", "72.049")), r.neededPaints)
    }

    @Test fun `parses a consultation`() {
        val c = buildJsonObject {
            put("version", 1); put("kind", "consultation"); put("zone", "capa"); put("question", "highlight"); put("technique", "Luz de filo")
            putJsonArray("steps") { add(JsonPrimitive("Pincel seco")) }
            putJsonArray("paints") { add(buildJsonObject { put("paint", ref("72.001")) }) }
            put("techniqueCard", "drybrush")
        }
        val r = ok(parse(c))
        assertEquals(listOf(PaintRef("Vallejo", "72.001")), r.neededPaints)
        assertEquals(TechniqueCardId.DRYBRUSH, (r.entry.response as ConsultationResponse).techniqueCard)
    }

    // hardening: the prompt pasted back by mistake
    @Test fun `rejects every prompt itself`() {
        Moment.entries.forEach { m ->
            val pkg = buildAdvicePackage(AdviceRequest(m, zone = "capa", question = Question.LIGHT), SEED_INVENTORY, "p")
            assertContains(errorsOf(parseAdviceResponse(pkg.text, SEED_INVENTORY)), "this is the prompt, not the AI answer")
        }
    }

    // hardening: malformed optional fields are rejected, not stored
    @Test fun `rejects malformed optional fields`() {
        fun consultation(edit: MutableMap<String, JsonElement>.() -> Unit) = JsonObject(
            mutableMapOf<String, JsonElement>(
                "version" to JsonPrimitive(1), "kind" to JsonPrimitive("consultation"), "zone" to JsonPrimitive("capa"),
                "question" to JsonPrimitive("light"), "technique" to JsonPrimitive("t"), "steps" to buildJsonArray {}, "paints" to buildJsonArray {},
            ).apply(edit),
        )
        val cases = listOf(
            recipe(slap("72.021")) { put("moment", JsonPrimitive("half-painted")); put("remainingSteps", JsonPrimitive("acabar")) } to "remainingSteps",
            recipe(slap("72.021") { put("brush", JsonPrimitive(5)) }) to "brush",
            recipe(slap("72.021") { put("alreadyPainted", JsonPrimitive("yes")) }) to "alreadyPainted",
            consultation { put("techniqueCard", JsonPrimitive("slapchop | dilution")) } to "techniqueCard",
            consultation { put("brush", buildJsonObject {}) } to "brush",
        )
        cases.forEach { (obj, field) -> assertContains(errorsOf(parse(obj)), field) }
    }

    // hardening: reasonable answers are accepted
    @Test fun `accepts dilutions with words or spaces, normalized to 1_3`() {
        listOf("1:3 (pintura:agua)", "1 : 3").forEach { d ->
            val r = ok(parse(recipe(slap("72.021") { put("dilution", JsonPrimitive(d)) })))
            assertEquals("1:3", (r.entry.response as RecipeResponse).zones[0].dilution)
        }
    }

    @Test fun `accepts version 1 written as a string`() {
        ok(parse(recipe(slap("72.021")) { put("version", JsonPrimitive("1")) }))
    }

    @Test fun `accepts an already-painted slapchop zone with no paint or dilution`() {
        val zone = buildJsonObject { put("name", "capa"); put("technique", "slapchop"); putJsonArray("steps") {}; put("alreadyPainted", true) }
        ok(parse(recipe(zone) { put("moment", JsonPrimitive("half-painted")) }))
    }

    @Test fun `reports a consultation paint with a non-text dilution instead of crashing`() {
        val c = buildJsonObject {
            put("version", 1); put("kind", "consultation"); put("zone", "capa"); put("question", "light"); put("technique", "t")
            putJsonArray("steps") {}; putJsonArray("paints") { add(buildJsonObject { put("paint", ref("72.001")); put("dilution", 3) }) }
        }
        assertTrue(errorsOf(parse(c)).isNotBlank())
    }
}
