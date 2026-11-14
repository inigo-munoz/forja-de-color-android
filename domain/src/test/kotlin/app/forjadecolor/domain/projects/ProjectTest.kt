package app.forjadecolor.domain.projects

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
}
