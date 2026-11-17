package app.forjadecolor.domain.advice

import app.forjadecolor.domain.ForjaJson
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.findPaint
import app.forjadecolor.domain.paints.findPaintLoose
import app.forjadecolor.domain.paints.paintKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** An answer accepted from the AI, before it gets an id and a date. */
data class ParsedAdvice(val response: AdviceResponse, val unknownPaints: List<PaintRef>, val unknownBrushes: List<String>) {
    fun toEntry(id: String, receivedAt: String) = AdviceEntry(id, receivedAt, response, unknownPaints, unknownBrushes)
}

sealed interface ParseResult {
    data class Success(val entry: ParsedAdvice, val neededPaints: List<PaintRef>) : ParseResult
    data class Failure(val errors: List<String>) : ParseResult
}

// Same rules and messages as the PWA (src/advice/parse.ts).
private val DILUTION = Regex("""(\d+(?:[.,]\d+)?)\s*:\s*(\d+(?:[.,]\d+)?)""")
private val CARDS = TechniqueCardId.entries.map { it.wire }
// Markers that only appear in the prompt we send, never in a real answer.
private val PROMPT_MARKERS = listOf("ANSWER FORMAT", "INVENTORY (brand|code")
private val FENCE = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

/** The JSON in an AI answer: a fenced block, or from the first { to the last }. Shared with the scan parser. */
internal fun extractJson(raw: String): String? {
    FENCE.find(raw)?.let { return it.groupValues[1].trim() }
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    return if (start >= 0 && end > start) raw.substring(start, end + 1) else null
}

private fun JsonElement?.isStr() = this is JsonPrimitive && isString
private fun JsonElement?.str() = if (isStr()) (this as JsonPrimitive).content else null
private fun JsonElement?.isStrArray() = this is JsonArray && all { it.isStr() }
private fun JsonElement?.isRef() = this is JsonObject && this["brand"].isStr() && this["code"].isStr()
private fun JsonElement?.isBool() = this is JsonPrimitive && !isString && booleanOrNull != null
private fun JsonElement?.isOne() = this is JsonPrimitive && !isString && this !is JsonNull && doubleOrNull == 1.0

/** How JavaScript's String(value) would print the field, for messages. */
private fun JsonElement?.shown(): String = when (this) {
    null -> "undefined"
    is JsonPrimitive -> content
    else -> toString()
}

/** Tolerates harmless variations of a reasonable answer before validating it. */
private fun normalize(data: JsonElement): JsonElement {
    if (data !is JsonObject) return data
    val out = data.toMutableMap()
    if (out["version"].str() == "1") out["version"] = JsonPrimitive(1)
    fun fix(d: JsonElement?): JsonElement? {
        val s = d.str() ?: return d
        val m = DILUTION.find(s) ?: return d
        return JsonPrimitive("${m.groupValues[1].replace(',', '.')}:${m.groupValues[2].replace(',', '.')}")
    }
    fun fixAll(key: String) {
        val list = out[key] as? JsonArray ?: return
        out[key] = JsonArray(
            list.map { item ->
                if (item is JsonObject && "dilution" in item) JsonObject(item + ("dilution" to (fix(item["dilution"]) ?: JsonNull))) else item
            },
        )
    }
    fixAll("zones")
    fixAll("paints")
    return JsonObject(out)
}

private fun validate(data: JsonElement): List<String> {
    if (data !is JsonObject) return listOf("the answer is not a JSON object")
    if (!data["version"].isOne()) return listOf("unsupported version: ${data["version"].shown()}")
    val errors = mutableListOf<String>()
    when (data["kind"].str()) {
        "recipe" -> validateRecipe(data, errors)
        "consultation" -> validateConsultation(data, errors)
        else -> errors += "kind must be \"recipe\" or \"consultation\""
    }
    return errors
}

private fun validateRecipe(data: JsonObject, errors: MutableList<String>) {
    val moment = data["moment"].str()
    if (moment != "start" && moment != "primed" && moment != "half-painted") errors += "recipe: moment must be \"start\", \"primed\" or \"half-painted\""
    if (!data["prep"].isStrArray()) errors += "recipe: prep must be a list of steps"
    if (!data["tips"].isStrArray()) errors += "recipe: tips must be a list"
    val zones = data["zones"]
    if (zones !is JsonArray || zones.isEmpty()) {
        errors += "recipe: zones must be a non-empty list"
    } else {
        zones.forEachIndexed { i, z -> validateZone(z, i, errors) }
    }
    val complements = data["complements"]
    if (complements != null && !(complements is JsonArray && complements.all { it.isRef() })) errors += "recipe: complements must be paints"
    val remaining = data["remainingSteps"]
    if (remaining != null && !remaining.isStrArray()) errors += "recipe: remainingSteps must be a list of steps"
}

private fun validateZone(z: JsonElement, i: Int, errors: MutableList<String>) {
    val name = (z as? JsonObject)?.get("name").str() ?: "#${i + 1}"
    if (z !is JsonObject || !z["name"].isStr()) {
        errors += "zone $name: missing name"
        return
    }
    if (!z["steps"].isStrArray()) errors += "zone $name: steps must be a list"
    if ("brush" in z && !z["brush"].isStr()) errors += "zone $name: brush must be a brush code"
    if ("alreadyPainted" in z && !z["alreadyPainted"].isBool()) errors += "zone $name: alreadyPainted must be true or false"
    val painted = z["alreadyPainted"].let { it is JsonPrimitive && !it.isString && it.booleanOrNull == true }
    when (z["technique"].str()) {
        "slapchop" -> {
            if ("paint" in z || !painted) {
                if (!z["paint"].isRef()) errors += "zone $name: slapchop needs a paint"
            }
            if ("dilution" in z || !painted) {
                val d = z["dilution"].str()
                if (d == null) errors += "zone $name: slapchop needs a dilution"
                else if (!DILUTION.containsMatchIn(d)) errors += "zone $name: dilution \"$d\" must look like \"1:3\""
            }
        }
        "bsl" -> {
            val b = z["bsl"]
            val complete = b is JsonObject && b["base"].isRef() && b["shadow"].isRef() && b["light"].isRef()
            if (("bsl" in z || !painted) && !complete) errors += "zone $name: bsl needs base, shadow and light"
        }
        else -> errors += "zone $name: technique must be \"slapchop\" or \"bsl\""
    }
}

private fun validateConsultation(data: JsonObject, errors: MutableList<String>) {
    for (f in listOf("zone", "question", "technique")) {
        if (!data[f].isStr()) errors += "consultation: missing $f"
    }
    if (!data["steps"].isStrArray()) errors += "consultation: steps must be a list"
    val paints = data["paints"]
    if (!(paints is JsonArray && paints.all { it is JsonObject && it["paint"].isRef() })) {
        errors += "consultation: paints must be a list of { paint }"
    }
    if ("brush" in data && !data["brush"].isStr()) errors += "consultation: brush must be a brush code"
    if ("techniqueCard" in data && data["techniqueCard"].str() !in CARDS) {
        errors += "consultation: techniqueCard must be one of ${CARDS.joinToString(", ")}"
    }
}

private fun brushesOf(r: AdviceResponse): List<String> = when (r) {
    is ConsultationResponse -> listOfNotNull(r.brush)
    is RecipeResponse -> r.zones.mapNotNull { it.brush }
}

fun parseAdviceResponse(raw: String, inv: Inventory): ParseResult {
    if (PROMPT_MARKERS.any { raw.contains(it) }) {
        return ParseResult.Failure(listOf("this is the prompt, not the AI answer: copy the answer from your chat and paste again"))
    }
    val json = extractJson(raw) ?: return ParseResult.Failure(listOf("no JSON found in the answer"))
    val parsed = try {
        ForjaJson.parseToJsonElement(json)
    } catch (e: IllegalArgumentException) {
        return ParseResult.Failure(listOf("invalid JSON: ${e.message}"))
    }
    val data = normalize(parsed)
    val errors = validate(data)
    if (errors.isNotEmpty()) return ParseResult.Failure(errors)

    val decoded = try {
        // version may arrive as 1.0; it is validated as 1, so store it as 1.
        ForjaJson.decodeFromJsonElement(AdviceResponse.serializer(), JsonObject((data as JsonObject) + ("version" to JsonPrimitive(1))))
    } catch (e: IllegalArgumentException) {
        return ParseResult.Failure(listOf("the answer could not be read: ${e.message}"))
    }

    val unknown = LinkedHashMap<String, PaintRef>()
    val response = mapPaintRefs(decoded) { ref ->
        val owned = inv.findPaint(ref) ?: inv.findPaintLoose("${ref.brand} ${ref.code}")
        if (owned != null) {
            PaintRef(owned.brand, owned.code)
        } else {
            unknown[paintKey(ref)] = ref
            ref
        }
    }
    val ownedBrushes = inv.brushes.map { it.code.lowercase() }.toSet()
    val unknownBrushes = brushesOf(response).filter { it.trim().lowercase() !in ownedBrushes }.distinct()

    return ParseResult.Success(ParsedAdvice(response, unknown.values.toList(), unknownBrushes), neededPaints(response))
}
