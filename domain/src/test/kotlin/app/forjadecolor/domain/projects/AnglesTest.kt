package app.forjadecolor.domain.projects

import app.forjadecolor.domain.ForjaJson
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AnglesTest {
    private val p = createProject("Búho", Instant.parse("2026-11-18T10:00:00Z"), "p1").copy(initialPhotoBlobId = "main")

    @Test
    fun `a project starts with only its main photo, and no extra field in its JSON`() {
        assertEquals(listOf("main"), p.photos)
        assertFalse("extraPhotoBlobIds" in ForjaJson.encodeToString(Project.serializer(), p))
    }

    @Test
    fun `keeps up to 3 extra angles after the main photo, in order, and they count as project photos`() {
        val full = listOf("a", "b", "c", "d").fold(p) { acc, id -> acc.addAngle(id) }
        assertEquals(3, MAX_ANGLES)
        assertEquals(listOf("main", "a", "b", "c"), full.photos)
        assertEquals(listOf("main", "a", "b", "c"), full.blobIds)
    }

    @Test
    fun `removing the last angle leaves the project as it was`() {
        val one = p.addAngle("a")
        assertEquals(listOf("b"), one.addAngle("b").removeAngle("a").extraPhotoBlobIds)
        assertEquals(p, one.removeAngle("a"))
    }
}
