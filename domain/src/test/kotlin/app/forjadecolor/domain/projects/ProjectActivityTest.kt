package app.forjadecolor.domain.projects

import app.forjadecolor.domain.advice.AdviceEntry
import app.forjadecolor.domain.advice.ConsultationResponse
import app.forjadecolor.domain.advice.RecipeMoment
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.Technique
import app.forjadecolor.domain.advice.ZoneRecipe
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectActivityTest {
    private val created = Instant.parse("2026-11-01T10:00:00Z")

    private fun recipe(id: String, at: String, zones: Int, done: List<Int>) = AdviceEntry(
        id, at,
        RecipeResponse(moment = RecipeMoment.START, prep = emptyList(), zones = List(zones) { ZoneRecipe("z$it", Technique.SLAPCHOP, steps = emptyList()) }, tips = emptyList()),
        emptyList(), emptyList(), doneZones = done,
    )

    private fun consultation(id: String, at: String) = AdviceEntry(
        id, at, ConsultationResponse(zone = "z", question = "free", technique = "t", steps = emptyList(), paints = emptyList()), emptyList(), emptyList(),
    )

    @Test
    fun `a new project's last activity is its creation`() {
        val a = projectActivity(createProject("B", created, "p"))
        assertEquals(created, a.lastActivity)
        assertNull(a.zones)
    }

    @Test
    fun `last activity is the latest of creation, progress photos and advice`() {
        val p = createProject("B", created, "p")
            .addProgressPhoto(ProgressPhoto("f", "2026-11-05T09:00:00Z", "blob"))
            .addAdvice(consultation("c", "2026-11-03T08:00:00.000Z"))
        assertEquals(Instant.parse("2026-11-05T09:00:00Z"), projectActivity(p).lastActivity)

        val later = p.addAdvice(consultation("d", "2026-11-07T08:00:00.000Z"))
        assertEquals(Instant.parse("2026-11-07T08:00:00Z"), projectActivity(later).lastActivity)
    }

    @Test
    fun `zone progress comes from the newest recipe`() {
        val p = createProject("B", created, "p")
            .addAdvice(recipe("new", "2026-11-04T00:00:00Z", zones = 6, done = listOf(0, 2, 5)))
            .addAdvice(recipe("old", "2026-11-02T00:00:00Z", zones = 4, done = listOf(0)))
            .addAdvice(consultation("c", "2026-11-06T00:00:00Z"))
        assertEquals(ZoneProgress(done = 3, total = 6), projectActivity(p).zones)
    }

    @Test
    fun `no zone progress with only consultations`() {
        val p = createProject("B", created, "p").addAdvice(consultation("c", "2026-11-06T00:00:00Z"))
        assertNull(projectActivity(p).zones)
    }

    @Test
    fun `ignores ticked zones outside the recipe`() {
        val p = createProject("B", created, "p").addAdvice(recipe("r", "2026-11-04T00:00:00Z", zones = 2, done = listOf(1, 7)))
        assertEquals(ZoneProgress(done = 1, total = 2), projectActivity(p).zones)
    }
}
