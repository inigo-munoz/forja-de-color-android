package app.forjadecolor.ui.projects

import app.forjadecolor.ui.project.TestApp
import app.forjadecolor.ui.project.awaitOnMain
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectsViewModelTest {
    private val app = TestApp()

    @Test
    fun `a second tap while saving creates nothing, but coming back to the list allows a new project`() {
        val vm = ProjectsViewModel(app.container)
        val opened = mutableListOf<String>()

        vm.create("Bárbara", null) { opened += it }
        vm.create("Bárbara", null) { opened += it }
        awaitOnMain { opened.size == 1 }
        assertEquals(1, app.projects().size)

        // The list stays on the back stack while the project is open, so its ViewModel outlives the screen.
        runBlocking { vm.load() }
        vm.create("Búho", null) { opened += it }
        awaitOnMain { opened.size == 2 }
        assertEquals(setOf("Bárbara", "Búho"), app.projects().map { it.name }.toSet())
    }
}
