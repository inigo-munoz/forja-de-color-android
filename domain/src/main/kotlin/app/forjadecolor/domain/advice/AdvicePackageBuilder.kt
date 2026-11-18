package app.forjadecolor.domain.advice

import app.forjadecolor.domain.projects.Look
import app.forjadecolor.domain.projects.PaintingStyle
import app.forjadecolor.domain.projects.adviceLookLines

import app.forjadecolor.domain.paints.BslRole
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.PaintType
import app.forjadecolor.domain.paints.findPaint
import app.forjadecolor.domain.paints.bslRole

// Prompt text must stay identical to the PWA's (src/advice/package.ts): the user's Claude Project /
// custom GPT is set up with it. Pinned by AdvicePackageTest against fixtures generated from the PWA.

// The translucency rule follows the inventory: with no inks or washes, translucent colour comes from thinning.
private const val NO_TRANSLUCENT = "- I have no translucent paints: make them by thinning my opaque paints. Every Slapchop zone needs a dilution ratio \"N:M\" (paint:medium or water), e.g. \"1:3\"."
private const val WITH_TRANSLUCENT = "- For translucent colour, use my inks and washes where they suit, or thin my opaque paints. Every Slapchop zone needs a dilution ratio \"N:M\" (paint:medium or water), e.g. \"1:3\"."

private fun method(inv: Inventory): String {
    val translucent = if (inv.paints.any { it.type == PaintType.INK || it.type == PaintType.WASH }) WITH_TRANSLUCENT else NO_TRANSLUCENT
    return """You are my miniature painting assistant. I paint fast but want a good-looking result.
METHOD
- Default technique is Slapchop: black primer, then a heavy grey drybrush and a lighter white drybrush to sketch light and shadow, then translucent colour on top.
$translucent
- Use BSL (base, shadow, light) triads only on detail zones: faces, skin, focal points. Prefer the BSL families listed below; each family is ordered 1 (lightest) to 4 (darkest).
- For a BSL zone use the family's base, shadow and light paints by their role (extra light for final highlights); a family without a shadow paint is shaded by glazing its base.
- Rules: only use paints and brushes from this inventory; cite them by brand and code. Never invent paints. Do not repeat the same paint needlessly.
- Write every human-readable text (zone names, steps, tips) in Spanish."""
}

private fun recipeFormat(moment: RecipeMoment): String {
    val halfPainted = if (moment == RecipeMoment.HALF_PAINTED) {
        """,
  "complements": [{ "brand": "Vallejo", "code": "72.008" }],
  "remainingSteps": ["..."]
  (mark zones already painted with "alreadyPainted": true; their steps are only the touch-ups they need, or empty)"""
    } else {
        ""
    }
    return """ANSWER FORMAT: reply with JSON only, in one ```json block, exactly this shape:
{
  "version": 1,
  "kind": "recipe",
  "moment": "${moment.wire}",
  "prep": ${if (moment == RecipeMoment.PRIMED) "[]" else "[\"step\", \"...\"]"},
  "zones": [
    { "name": "zone", "technique": "slapchop", "paint": { "brand": "Vallejo", "code": "72.021" }, "dilution": "1:3", "steps": ["..."], "brush": "B02001" },
    { "name": "zone", "technique": "bsl", "bsl": { "base": { "brand": "Vallejo", "code": "72.099" }, "shadow": { "brand": "Vallejo", "code": "72.071" }, "light": { "brand": "Vallejo", "code": "72.098" } }, "steps": ["..."] }
  ],
  "tips": ["..."]$halfPainted
}"""
}

private val CONSULTATION_FORMAT = """ANSWER FORMAT: reply with JSON only, in one ```json block, exactly this shape:
{
  "version": 1,
  "kind": "consultation",
  "zone": "zone",
  "question": "apply-layer | light | shadow | highlight | which-technique | free",
  "technique": "short name",
  "steps": ["..."],
  "paints": [{ "paint": { "brand": "Vallejo", "code": "72.021" }, "dilution": "1:3" }],
  "brush": "B02001",
  "techniqueCard": "slapchop | dilution | bsl | drybrush"
}"""

private val ROLE_WORDS = mapOf(BslRole.BASE to "base", BslRole.SHADOW to "shadow", BslRole.LIGHT to "light", BslRole.FOURTH to "extra light")

private fun inventoryLines(inv: Inventory): String {
    val paints = inv.paints.map { p ->
        val role = bslRole(p)?.let { ROLE_WORDS.getValue(it) } ?: ""
        listOf(p.brand, p.code, p.name, p.family?.name ?: p.range, p.family?.order?.toString() ?: "", role, p.type.name.lowercase()).joinToString("|")
    }
    val brushes = inv.brushes.map { "brush|${it.code}|${it.name}" }
    return (listOf("INVENTORY (brand|code|name|family|order|role|type)") + paints + brushes).joinToString("\n")
}

private fun momentRequest(req: AdviceRequest): String = when (req.moment) {
    Moment.START -> "MOMENT: start. The photo shows an unpainted miniature. Give me a palette by zones."
    Moment.PRIMED -> "MOMENT: primed. The photo shows my miniature already primed in black with the grey and white drybrush done. Skip priming and drybrushing (leave prep empty) and give me a palette by zones, starting from the colour layers."
    Moment.HALF_PAINTED -> "MOMENT: half-painted. The photo shows my miniature partly painted and I want to finish it: I do not want to repaint it. Review the zones already painted and suggest a fix (a wash, a glaze, a highlight) only when something clearly needs it; then give the steps to finish the zones that are left, with colours that match what is already painted."
    Moment.DURING -> {
        val details = if (req.freeText.isNullOrEmpty()) "" else " Details: ${req.freeText}"
        "MOMENT: during. Zone: \"${req.zone ?: ""}\". Question: ${req.question?.wire ?: "free"}.$details Tell me which technique to use and how, for that zone only."
    }
}

private fun describe(inv: Inventory, ref: PaintRef): String =
    inv.findPaint(ref)?.let { "${it.name} (${it.brand} ${it.code})" } ?: "${ref.brand} ${ref.code}"

/** Half-painted only: the latest recipe, so the AI keeps its colours and knows what is painted. */
private fun previousRecipe(req: AdviceRequest, inv: Inventory): String? {
    val previous = req.previous ?: return null
    if (req.moment != Moment.HALF_PAINTED) return null
    val done = previous.doneZones.toSet()
    val zones = previous.response.zones.mapIndexed { i, z ->
        val bsl = z.bsl
        val paints = when {
            z.technique == Technique.BSL && bsl != null ->
                "base ${describe(inv, bsl.base)}, shadow ${describe(inv, bsl.shadow)}, light ${describe(inv, bsl.light)}"
            z.paint != null -> describe(inv, z.paint)
            else -> "no paint"
        }
        "- ${z.name} (${z.technique.name.lowercase()}): $paints. ${if (i in done) "Painted." else "Not painted yet."}"
    }
    return (listOf("PREVIOUS RECIPE for this miniature (keep these colours unless there is a reason to change them, and say why if you change one):") + zones).joinToString("\n")
}

fun buildAdvicePackage(req: AdviceRequest, inv: Inventory, photoId: String): AdvicePackage {
    val format = when (req.moment) {
        Moment.START -> recipeFormat(RecipeMoment.START)
        Moment.PRIMED -> recipeFormat(RecipeMoment.PRIMED)
        Moment.HALF_PAINTED -> recipeFormat(RecipeMoment.HALF_PAINTED)
        Moment.DURING -> CONSULTATION_FORMAT
    }
    val look = adviceLookLines(Look(req.style ?: PaintingStyle.MY_METHOD, req.baseSetting))
    val sections = listOf(method(inv), inventoryLines(inv), momentRequest(req)) + listOfNotNull(previousRecipe(req, inv), look.joinToString("\n").ifEmpty { null }) + format
    val text = sections.joinToString("\n\n")
    return AdvicePackage(text, photoId)
}

fun buildBrainInstructions(inv: Inventory): String = listOf(
    method(inv),
    inventoryLines(inv),
    "When I send a photo I will say the moment (start, primed, half-painted or during). For start, primed and half-painted answer with this format (for primed, leave prep empty):",
    recipeFormat(RecipeMoment.START),
    "For during answer with this format:",
    CONSULTATION_FORMAT,
).joinToString("\n\n")
