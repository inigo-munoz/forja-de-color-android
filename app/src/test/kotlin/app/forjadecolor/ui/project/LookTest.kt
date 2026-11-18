package app.forjadecolor.ui.project

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.forjadecolor.domain.advice.AdviceEntry
import app.forjadecolor.domain.advice.RecipeMoment
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.Technique
import app.forjadecolor.domain.advice.ZoneRecipe
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.projects.BaseSetting
import app.forjadecolor.domain.projects.Look
import app.forjadecolor.domain.projects.PaintingStyle
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.projects.addAdvice
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.domain.projects.setLook
import app.forjadecolor.domain.storage.StoredBlob
import app.forjadecolor.ui.ask.AskScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val RECIPE = AdviceEntry(
    "e1", "2026-11-10T10:00:00Z",
    RecipeResponse(
        moment = RecipeMoment.START, prep = emptyList(), tips = emptyList(),
        zones = listOf(ZoneRecipe("capa", Technique.SLAPCHOP, paint = PaintRef("Vallejo", "72.021"), dilution = "1:3", steps = emptyList())),
    ),
    emptyList(), emptyList(),
)

/** The painting style lives in the project: chosen there or in Ask, and followed by every prompt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LookTest {
    @get:Rule val compose = createComposeRule()
    private val app = TestApp()

    private fun save(change: (Project) -> Project = { it }) = runBlocking {
        val photo = app.repository.putBlob(StoredBlob("image/jpeg", "photo".toByteArray()))
        app.repository.saveProject(change(createProject("Bárbara", app.now, "p1").copy(initialPhotoBlobId = photo)))
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    private fun showProject() {
        app.setContent(compose) { ProjectScreen(app.container, "p1", onAsk = {}, onOpenTechnique = {}, onBack = {}) }
        app.waitFor(compose) { compose.onAllNodesWithTag("look_line").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun showAsk() {
        app.setContent(compose) { AskScreen(app.container, "p1", onDone = {}) }
        app.waitFor(compose) { compose.onAllNodesWithTag("look_line").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a project starts with my method and no base setting`() {
        save()
        showProject()
        compose.onNodeWithTag("look_line").assert(hasText("My method", substring = true))
        compose.onNodeWithTag("look_line").assert(hasText("No setting", substring = true))
    }

    @Test
    fun `choosing a style and a base setting in the project saves them`() {
        save()
        showProject()

        click("look_change")
        click("style_osl")
        click("base_desert")
        click("look_save")

        app.waitFor(compose) { app.project("p1")?.look == Look(PaintingStyle.OSL, BaseSetting.DESERT) }
        compose.onNodeWithTag("look_line").assert(hasText("OSL", substring = true))
        compose.onNodeWithTag("look_line").assert(hasText("Desert", substring = true))
    }

    @Test
    fun `cancelling the style dialog changes nothing`() {
        save()
        showProject()

        click("look_change")
        click("style_nmm")
        click("look_cancel")

        compose.onNodeWithTag("look_dialog").assertDoesNotExist()
        compose.waitForIdle()
        assertEquals(Look(), app.project("p1")!!.look)
    }

    @Test
    fun `the advisor prompt follows the project's style`() {
        save { it.setLook(Look(PaintingStyle.VIVID, BaseSetting.WINTER)) }
        showAsk()

        click("send")

        app.waitFor(compose) { app.bridge.sent.isNotEmpty() }
        val text = app.bridge.sent.single().text
        assertTrue("STYLE: Vivid" in text)
        assertTrue("BASE SETTING: winter" in text)
    }

    @Test
    fun `changing the style in Ask saves it in the project and the next prompt uses it`() {
        save()
        showAsk()

        click("look_change")
        click("style_nmm")
        click("look_save")
        app.waitFor(compose) { app.project("p1")?.style == PaintingStyle.NMM }
        click("send")

        app.waitFor(compose) { app.bridge.sent.isNotEmpty() }
        assertTrue("STYLE: NMM" in app.bridge.sent.single().text)
    }

    @Test
    fun `my method sends the prompt as before, without a style section`() {
        save()
        showAsk()
        click("send")
        app.waitFor(compose) { app.bridge.sent.isNotEmpty() }
        assertFalse("STYLE:" in app.bridge.sent.single().text)
    }

    @Test
    fun `paint it with ChatGPT follows the project's style`() {
        save { it.setLook(Look(PaintingStyle.GRIMDARK, BaseSetting.FANTASY)).addAdvice(RECIPE) }
        showProject()

        click("paint_image")

        app.waitFor(compose) { app.bridge.sent.isNotEmpty() }
        val text = app.bridge.sent.single().text
        assertTrue("Painting style: grimdark" in text)
        assertTrue("Base: magical ground" in text)
    }

    @Test
    fun `a free-text base motif is saved, shown and sent to the advisor`() {
        save()
        showProject()
        click("look_change")
        compose.onNodeWithTag("look_motif").performScrollTo().performTextReplacement("río de lava")
        click("look_save")

        app.waitFor(compose) { app.project("p1")?.baseMotif == "río de lava" }
        compose.onNodeWithTag("look_line").assert(hasText("río de lava", substring = true))
    }

    @Test
    fun `a recipe shows the basing materials the advisor recommends, as maybe not owned`() {
        save { it.addAdvice(RECIPE.copy(response = (RECIPE.response as RecipeResponse).copy(baseMaterials = listOf("arena fina", "nieve artificial")))) }
        showProject()
        compose.onNodeWithTag("base_materials").performScrollTo().assert(hasText("arena fina", substring = true))
        compose.onNodeWithTag("base_materials").assert(hasText("nieve artificial", substring = true))
    }
}
