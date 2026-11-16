package app.forjadecolor.ui.project

import android.os.Looper
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.domain.projects.setNotes
import app.forjadecolor.domain.storage.StoredBlob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the main looper (where viewModelScope works) until [condition] holds, for at most 5 s. */
fun awaitOnMain(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 5_000
    while (!condition()) {
        check(System.currentTimeMillis() < deadline) { "condition not met in 5 s" }
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(10)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectViewModelTest {
    private val app = TestApp()

    @Test
    fun `loading again shows the project as stored now, as when coming back from the ask screen`() {
        runBlocking { app.repository.saveProject(createProject("Bárbara", app.now, "p1")) }
        val vm = ProjectViewModel(app.container, "p1")
        runBlocking { vm.load() }
        runBlocking { app.repository.saveProject(app.project("p1")!!.setNotes("cambiado fuera")) }

        assertFalse(runBlocking { vm.load() })

        assertTrue(vm.state.value.loaded)
        assertEquals("cambiado fuera", vm.state.value.project?.notes)
    }

    @Test
    fun `loading says when an answer shared from the AI app waits for the project`() {
        runBlocking { app.repository.saveProject(createProject("Bárbara", app.now, "p1")) }
        app.container.sharedAnswers.put("p1", "{}")

        assertTrue(runBlocking { ProjectViewModel(app.container, "p1").load() })
    }

    @Test
    fun `delete removes the project and its photos, then runs the way back`() {
        val photo = runBlocking { app.repository.putBlob(StoredBlob("image/jpeg", byteArrayOf(1))) }
        runBlocking { app.repository.saveProject(createProject("Bárbara", app.now, "p1").copy(initialPhotoBlobId = photo)) }
        val vm = ProjectViewModel(app.container, "p1")
        var backs = 0

        vm.delete { backs++ }

        awaitOnMain { backs == 1 }
        assertNull(app.project("p1"))
        assertNull(runBlocking { app.repository.getBlob(photo) })
    }
}
