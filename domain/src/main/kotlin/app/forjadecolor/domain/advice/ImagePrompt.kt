package app.forjadecolor.domain.advice

import app.forjadecolor.domain.projects.Look
import app.forjadecolor.domain.projects.imageLookLines
import app.forjadecolor.domain.projects.themedBase

import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.findPaint

private fun describe(inv: Inventory, ref: PaintRef): String =
    inv.findPaint(ref)?.let { "${it.name} ${it.hex}" } ?: "${ref.brand} ${ref.code}"

/**
 * Prompt for an image model (ChatGPT) to paint the miniature in the attached photo with the colours
 * of a recipe. Image generation stays outside the app, like the recipe itself. Same text as the PWA.
 */
fun buildImagePrompt(recipe: RecipeResponse, inv: Inventory, look: Look = Look()): String {
    val zones = recipe.zones.map { z ->
        val bsl = z.bsl
        when {
            z.technique == Technique.BSL && bsl != null ->
                "- ${z.name}: base ${describe(inv, bsl.base)}, shadows ${describe(inv, bsl.shadow)}, highlights ${describe(inv, bsl.light)}"
            z.paint != null -> "- ${z.name}: ${describe(inv, z.paint)}"
            else -> "- ${z.name}: keep as it is"
        }
    }
    return (
        listOf(
            // With a themed base, terrain may be added on the base, so this line protects only the miniature.
            if (themedBase(look)) {
                "Edit the attached photo of a miniature so it looks painted. Keep the exact pose, sculpt and framing of the miniature: do not add, remove or reshape anything on it."
            } else {
                "Edit the attached photo of a miniature so it looks painted. Keep the exact pose, sculpt, base and framing: do not add, remove or reshape anything."
            },
            "Style: tabletop-quality \"slapchop\" painting — strong light and shadow from a white drybrush over black primer, with translucent colour on top; detail zones (faces, skin) with smooth base / shadow / highlight.",
        ) + imageLookLines(look) + "Paint each zone with these colours:" + zones + "Photorealistic photo of a painted miniature on a plain neutral background. Reply with the image only."
        ).joinToString("\n")
}
