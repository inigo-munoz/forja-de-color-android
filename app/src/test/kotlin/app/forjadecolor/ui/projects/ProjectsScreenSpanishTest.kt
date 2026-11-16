package app.forjadecolor.ui.projects

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.forjadecolor.domain.advice.AdviceEntry
import app.forjadecolor.domain.advice.RecipeMoment
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.Technique
import app.forjadecolor.domain.advice.ZoneRecipe
import app.forjadecolor.domain.projects.addAdvice
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.ui.project.TestApp
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es")
class ProjectsScreenSpanishTest {
    @get:Rule val compose = createComposeRule()
    private val app = TestApp()

    @Test
    fun `shows the new-project button and the activity line in Spanish`() {
        val zones = List(6) { ZoneRecipe("z$it", Technique.SLAPCHOP, steps = emptyList()) }
        val recipe = RecipeResponse(moment = RecipeMoment.START, prep = emptyList(), zones = zones, tips = emptyList())
        runBlocking {
            app.repository.saveProject(
                createProject("Bárbara", app.now, "a")
                    .addAdvice(AdviceEntry("r", "2026-11-13T12:00:00.000Z", recipe, emptyList(), emptyList(), doneZones = listOf(0, 1, 2))),
            )
        }
        app.setContent(compose) { ProjectsScreen(app.container, onOpenProject = {}) }

        compose.onNodeWithText("+ Nuevo proyecto").assertIsDisplayed()
        app.waitFor(compose) { compose.onAllNodesWithTag("project_activity", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Última actividad: 13 nov 2026 · 3 de 6 zonas", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("new_project_open").assertIsDisplayed()
    }
}
