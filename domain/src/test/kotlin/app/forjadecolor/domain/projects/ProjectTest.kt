package app.forjadecolor.domain.projects

import app.forjadecolor.domain.advice.AdviceEntry
import app.forjadecolor.domain.advice.ConsultationResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectTest {
    private val now = Instant.parse("2026-11-11T10:00:00Z")

    @Test
    fun `starts in progress with empty notes, progress and advice, dated like the PWA`() {
        val p = createProject("Bárbara", now, "p1")
        assertEquals(Project("p1", "Bárbara", "2026-11-11T10:00:00.000Z", ProjectStatus.IN_PROGRESS, notes = "", progress = emptyList(), advice = emptyList()), p)
    }

    @Test
    fun `keeps progress photos sorted by date, oldest first`() {
        val p = createProject("Bárbara", now, "p1")
            .addProgressPhoto(ProgressPhoto("b", "2026-11-12T00:00:00Z", "blob-b"))
            .addProgressPhoto(ProgressPhoto("a", "2026-11-10T00:00:00Z", "blob-a"))
        assertEquals(listOf("a", "b"), p.progress.map { it.id })
    }

    @Test
    fun `marks done and reopens`() {
        val p = createProject("Bárbara", now, "p1")
        assertEquals(ProjectStatus.DONE, p.markDone().status)
        assertEquals(ProjectStatus.IN_PROGRESS, p.markDone().reopen().status)
    }

    @Test
    fun `sets notes`() {
        assertEquals("capa roja", createProject("B", now, "p1").setNotes("capa roja").notes)
    }

    @Test
    fun `marks recipe zones done as a sorted, unique list and clears it when none are left`() {
        val entry = AdviceEntry("e1", "", ConsultationResponse(zone = "z", question = "free", technique = "t", steps = emptyList(), paints = emptyList()), emptyList(), emptyList())
        var p = createProject("B", now, "p1").addAdvice(entry)
        p = p.setZoneDone("e1", 2, true).setZoneDone("e1", 0, true).setZoneDone("e1", 2, true)
        assertEquals(listOf(0, 2), p.advice[0].doneZones)
        p = p.setZoneDone("e1", 0, false).setZoneDone("e1", 2, false)
        assertEquals(emptyList(), p.advice[0].doneZones)
    }
}
