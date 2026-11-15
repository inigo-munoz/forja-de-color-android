package app.forjadecolor.ui.ask

import android.graphics.Bitmap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import app.forjadecolor.AppContainer
import app.forjadecolor.domain.advice.AdviceEntry
import app.forjadecolor.domain.advice.RecipeMoment
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.advice.Technique
import app.forjadecolor.domain.advice.ZoneRecipe
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.projects.addAdvice
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.storage.Repository
import app.forjadecolor.domain.storage.StoredBlob
import app.forjadecolor.ui.project.TestApp
import app.forjadecolor.ui.project.readFixture
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val ANSWER = """{"version":1,"kind":"recipe","moment":"start","prep":["Imprimar en negro"],"tips":["Capas finas"],""" +
    """"zones":[{"name":"capa","technique":"slapchop","paint":{"brand":"Vallejo","code":"72.021"},"dilution":"1:3","steps":["Veladura"]}]}"""

private val PRIOR = AdviceEntry(
    "e0", "2026-11-10T10:00:00Z",
    RecipeResponse(
        moment = RecipeMoment.START, prep = emptyList(), tips = emptyList(),
        zones = listOf(ZoneRecipe("capa", Technique.SLAPCHOP, paint = PaintRef("Vallejo", "72.021"), dilution = "1:3", steps = emptyList())),
    ),
    emptyList(), emptyList(),
)

// Cases ported from the PWA's src/ui/ask-view.test.ts.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AskScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app = TestApp()
    private var done = 0
    private lateinit var photoId: String

    private fun show(readAnswer: String? = ANSWER, sendResult: SendResult = SendResult.SHARED) {
        app.bridge.answer = readAnswer
        app.bridge.result = sendResult
        runBlocking {
            photoId = app.repository.putBlob(StoredBlob("image/jpeg", "photo".toByteArray()))
            app.repository.saveProject(createProject("Bárbara", app.now, "p1").addAdvice(PRIOR).copy(initialPhotoBlobId = photoId))
        }
        app.setContent(compose) { AskScreen(app.container, "p1", onDone = { done++ }) }
        app.waitFor(compose) { compose.onAllNodesWithTag("send").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    @Test
    fun `offers the three moments`() {
        show()
        compose.onNodeWithTag("moment_start").assertExists()
        compose.onNodeWithTag("moment_half-painted").assertExists()
        compose.onNodeWithTag("moment_during").assertExists()
    }

    @Test
    fun `asks zone and question only while painting, offering the recipe zones`() {
        show()
        compose.onNodeWithTag("during_fields").assertDoesNotExist()
        click("moment_during")
        compose.onNodeWithTag("during_fields").assertExists()
        compose.onNodeWithTag("zone_option_capa").assertExists()
    }

    @Test
    fun `sends a package built for the chosen moment, using the project photo when none is picked`() {
        show()
        click("moment_during")
        click("question_highlight")
        click("send")

        app.waitFor(compose) { app.bridge.sent.isNotEmpty() }
        val pkg = app.bridge.sent.single()
        assertTrue(pkg.text.contains("\"kind\": \"consultation\""))
        assertTrue(pkg.text.contains("capa"))
        assertTrue(pkg.text.contains("highlight"))
        assertEquals(photoId, pkg.imageId)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `shows the taken photo before sending it`() {
        show()
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out)
        app.photos.nextPhoto = out.toByteArray()
        compose.onAllNodesWithTag("ask_photo_preview").assertCountEquals(0)

        click("ask_take_photo")

        compose.onNodeWithTag("ask_photo_preview").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `sends a freshly picked photo, stored first`() {
        show()
        app.photos.nextPhoto = byteArrayOf(4, 4)
        click("ask_choose_photo")
        click("send")

        app.waitFor(compose) { app.bridge.sent.isNotEmpty() }
        val sentId = app.bridge.sent.single().imageId
        assertTrue(sentId != photoId)
        assertContentEquals(byteArrayOf(4, 4), runBlocking { app.repository.getBlob(sentId) }!!.bytes)
    }

    @Test
    fun `explains the fallback and shows the prompt to copy when the share sheet could not be used`() {
        show(sendResult = SendResult.FALLBACK)
        click("send")
        app.waitFor(compose) { compose.onAllNodesWithTag("prompt").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("send_status").assert(hasText("Prompt copied and image downloaded — paste both in your chat", substring = true))
        compose.onNodeWithTag("prompt").assert(hasText("ANSWER FORMAT", substring = true))
        compose.onNodeWithTag("copy_prompt").assertExists()
    }

    @Test
    fun `saves a valid pasted answer and returns to the project`() {
        show()
        click("paste")
        app.waitFor(compose) { done == 1 }
        assertEquals(2, app.project("p1")!!.advice.size)
        assertEquals("2026-11-11T10:00:00.000Z", app.project("p1")!!.advice[1].receivedAt)
    }

    @Test
    fun `saving the real ChatGPT answer stores the advice with its zones`() {
        val raw = readFixture("chatgpt-start-dryad.json")
        show(readAnswer = raw)
        click("paste")

        app.waitFor(compose) { done == 1 }
        val saved = app.project("p1")!!.advice.last().response as RecipeResponse
        assertTrue(saved.zones.isNotEmpty())
        assertTrue(app.project("p1")!!.advice.last().unknownPaints.isEmpty())
    }

    @Test
    fun `shows a text field when the clipboard cannot be read, and saves from it`() {
        show(readAnswer = null)
        click("paste")
        compose.onNodeWithTag("answer").performScrollTo().performTextInput("Aquí va:\n```json\n$ANSWER\n```")
        click("save_answer")
        app.waitFor(compose) { app.project("p1")!!.advice.size == 2 }
    }

    @Test
    fun `shows every error and keeps the pasted text when the answer is invalid`() {
        val bad = """{"version":1,"kind":"recipe","moment":"start","prep":[],"tips":[],"zones":[{"name":"capa","technique":"slapchop","steps":[]}]}"""
        show(readAnswer = bad)
        click("paste")
        app.waitFor(compose) { compose.onAllNodesWithTag("answer_errors").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("The answer could not be used:").assertExists()
        compose.onNode(hasText(Regex("capa.*paint"))).assertExists()
        compose.onNode(hasText(Regex("capa.*dilution"))).assertExists()
        compose.onNodeWithTag("answer").assert(hasText(bad))
        assertEquals(1, app.project("p1")!!.advice.size)
        assertEquals(0, done)
    }

    @Test
    fun `a pasted prompt shows the this-is-the-prompt error and stores nothing`() {
        show(readAnswer = "ANSWER FORMAT: reply with JSON only")
        click("paste")
        app.waitFor(compose) { compose.onAllNodesWithTag("answer_errors").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("this is the prompt, not the AI answer", substring = true)).performScrollTo().assertIsDisplayed()
        assertEquals(1, app.project("p1")!!.advice.size)
    }

    @Test
    fun `shows an error instead of failing silently when saving throws`() {
        val failing = object : Repository by app.repository {
            override suspend fun saveProject(p: Project) = throw IllegalStateException("disk full")
        }
        app.bridge.answer = ANSWER
        runBlocking { app.repository.saveProject(createProject("Bárbara", app.now, "p1")) }
        val container = AppContainer(app.context, repository = failing, advisorBridge = app.bridge, now = { app.now })
        app.setContent(compose) { AskScreen(container, "p1", onDone = { done++ }) }
        app.waitFor(compose) { compose.onAllNodesWithTag("paste").fetchSemanticsNodes().isNotEmpty() }
        click("paste")
        app.waitFor(compose) { compose.onAllNodesWithTag("answer_errors").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("disk full", substring = true)).assertExists()
        assertEquals(0, done)
    }
}

private fun hasText(regex: Regex) = SemanticsMatcher("text matches $regex") { node ->
    node.config.getOrNull(SemanticsProperties.Text)?.any { regex.containsMatchIn(it.text) } == true
}
