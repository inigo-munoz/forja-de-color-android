package app.forjadecolor.domain.projects

import app.forjadecolor.domain.advice.RecipeResponse
import java.time.Instant

/** Recipe zones ticked off as painted, out of the recipe's zones. */
data class ZoneProgress(val done: Int, val total: Int)

/** What the project list shows under each project: when it was last touched and how far its recipe is. */
data class ProjectActivity(val lastActivity: Instant, val zones: ZoneProgress?)

/**
 * Last activity is the latest of the creation, progress photos and advice dates (nothing stores an
 * "updated at"); zone progress comes from the newest recipe, or is null without one (or without zones).
 */
fun projectActivity(project: Project): ProjectActivity {
    val created = parse(project.createdAt) ?: Instant.EPOCH
    val dates = project.progress.mapNotNull { parse(it.takenAt) } + project.advice.mapNotNull { parse(it.receivedAt) }
    val last = (dates + created).max()
    val newestRecipe = project.advice
        .filter { it.response is RecipeResponse }
        .withIndex()
        .maxWithOrNull(compareBy({ parse(it.value.receivedAt) ?: Instant.EPOCH }, { it.index }))
        ?.value
    val zones = newestRecipe?.let { e ->
        (e.response as RecipeResponse).zones.takeIf { it.isNotEmpty() }?.let { z -> ZoneProgress(z.indices.count { it in e.doneZones }, z.size) }
    }
    return ProjectActivity(last, zones)
}

private fun parse(iso: String): Instant? = runCatching { Instant.parse(iso) }.getOrNull()
