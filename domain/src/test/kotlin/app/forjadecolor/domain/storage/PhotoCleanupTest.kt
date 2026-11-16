package app.forjadecolor.domain.storage

import app.forjadecolor.domain.projects.ProgressPhoto
import app.forjadecolor.domain.projects.createProject
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoCleanupTest {
    private val now = Instant.parse("2026-11-16T10:00:00Z")
    private val repo = InMemoryRepository()

    private fun photo(name: String) = runBlocking { repo.putBlob(StoredBlob("image/jpeg", name.toByteArray()), name) }

    @Test
    fun `deleting a project deletes it with its photo, target image and progress photos`() = runBlocking {
        val p = createProject("Búho", now, "p1").copy(
            initialPhotoBlobId = photo("raw"),
            targetImageBlobId = photo("painted"),
            progress = listOf(ProgressPhoto("g1", "2026-11-16T10:00:00.000Z", photo("step1"))),
        )
        repo.saveProject(p)
        photo("other-unused")

        repo.deleteProjectWithPhotos("p1")

        assertNull(repo.getProject("p1"))
        assertEquals(setOf("other-unused"), repo.blobs.keys)
    }

    @Test
    fun `deleting a project keeps a photo another project still uses`() = runBlocking {
        val shared = photo("shared")
        repo.saveProject(createProject("Búho", now, "p1").copy(initialPhotoBlobId = shared))
        repo.saveProject(createProject("Druida", now, "p2").copy(targetImageBlobId = shared))

        repo.deleteProjectWithPhotos("p1")

        assertEquals(setOf("shared"), repo.blobs.keys)
    }

    @Test
    fun `deleting a missing project changes nothing`() = runBlocking {
        photo("raw")

        repo.deleteProjectWithPhotos("nope")

        assertEquals(setOf("raw"), repo.blobs.keys)
    }

    @Test
    fun `deletes only the given photos that no project uses`() = runBlocking {
        val used = photo("used")
        val dropped = photo("dropped")
        photo("untouched")
        repo.saveProject(createProject("Búho", now, "p1").copy(initialPhotoBlobId = used))

        repo.deletePhotosIfUnused(listOf(used, dropped, null))

        assertEquals(setOf("used", "untouched"), repo.blobs.keys)
    }
}
