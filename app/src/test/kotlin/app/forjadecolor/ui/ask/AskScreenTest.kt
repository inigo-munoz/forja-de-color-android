package app.forjadecolor.ui.ask

import android.graphics.Bitmap
import android.content.ClipboardManager
import androidx.compose.ui.test.onAllNodesWithText
import app.forjadecolor.domain.advice.buildBrainInstructions
import app.forjadecolor.domain.advice.instructionsFingerprint
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintType
import app.forjadecolor.domain.paints.addPaint
import app.forjadecolor.domain.storage.MetaKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
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

    private var restoration: StateRestorationTester? = null

    private fun show(readAnswer: String? = ANSWER, sendResult: SendResult = SendResult.SHARED, restorable: Boolean = false, withPhoto: Boolean = true) {
        app.bridge.answer = readAnswer
        app.bridge.result = sendResult
        runBlocking {
            photoId = app.repository.putBlob(StoredBlob("image/jpeg", "photo".toByteArray()))
            val project = createProject("Bárbara", app.now, "p1").addAdvice(PRIOR)
            app.repository.saveProject(if (withPhoto) project.copy(initialPhotoBlobId = photoId) else project)
        }
        val screen = @Composable { AskScreen(app.container, "p1", onDone = { done++ }) }
        if (restorable) restoration = app.setRestorableContent(compose, screen) else app.setContent(compose, screen)
        app.waitFor(compose) { compose.onAllNodesWithTag("send").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    @Test
    fun `offers the four moments in order`() {
        show()
        val tops = listOf("start", "primed", "half-painted", "during").map {
            compose.onNodeWithTag("moment_$it").assertExists().fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
        assertEquals(4, tops.distinct().size)
    }

    @Test
    fun `sends a primed package asking for a recipe without prep`() {
        show()
        click("moment_primed")
        compose.onNodeWithTag("during_fields").assertDoesNotExist()
        click("send")

        app.waitFor(compose) { app.bridge.sent.isNotEmpty() }
        val pkg = app.bridge.sent.single()
        assertTrue(pkg.text.contains("MOMENT: primed."))
        assertTrue(pkg.text.contains("\"moment\": \"primed\""))
        assertTrue(pkg.text.contains("\"prep\": []"))
        assertEquals(photoId, pkg.imageId)
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
        app.photos.nextPhoto = jpeg()
        compose.onAllNodesWithTag("ask_photo_preview").assertCountEquals(0)

        click("ask_take_photo")

        compose.onNodeWithTag("ask_photo_preview").performScrollTo().assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `keeps the taken photo when the screen is recreated, as on rotation`() {
        show(restorable = true)
        app.photos.nextPhoto = jpeg()
        click("ask_take_photo")
        compose.onNodeWithTag("ask_photo_preview").performScrollTo().assertIsDisplayed()

        restoration!!.emulateSavedInstanceStateRestore()

        // The recreated screen loads its project again before showing the form.
        app.waitFor(compose) { compose.onAllNodesWithTag("send").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ask_photo_preview").performScrollTo().assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `removes the taken photo, so the project photo is sent instead`() {
        show()
        app.photos.nextPhoto = jpeg()
        click("ask_take_photo")
        compose.onNodeWithTag("ask_photo_preview").performScrollTo().assertIsDisplayed()

        click("ask_photo_preview_remove")

        compose.onAllNodesWithTag("ask_photo_preview").assertCountEquals(0)
        click("send")
        app.waitFor(compose) { app.bridge.sent.isNotEmpty() }
        assertEquals(photoId, app.bridge.sent.single().imageId)
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
    fun `an unusable answer opens a blocking dialog with the errors, closed with OK`() {
        val bad = """{"version":1,"kind":"recipe","moment":"start","prep":[],"tips":[],"zones":[{"name":"capa","technique":"slapchop","steps":[]}]}"""
        show(readAnswer = bad)
        click("paste")
        app.waitFor(compose) { compose.onAllNodesWithTag("answer_errors").fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(isDialog()).assertExists()
        compose.onNode(hasAnyAncestor(isDialog()) and hasText(Regex("capa.*dilution"))).assertExists()
        compose.onNodeWithTag("errors_ok").performClick()

        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithTag("answer").assert(hasText(bad))
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

    @Test
    fun `numbers the three steps in order`() {
        show()
        val texts = listOf("1. Choose the moment", "2. Send it to your AI", "3. Come back and paste the answer")
        val tops = (1..3).map { i ->
            compose.onNodeWithTag("ask_step_$i").assert(hasText(texts[i - 1])).fetchSemanticsNode().positionInRoot.y
        }
        assertEquals(tops.sorted(), tops)
        assertEquals(3, tops.distinct().size)
    }

    @Test
    fun `explains each moment under its option`() {
        show()
        mapOf(
            "start" to "Unpainted miniature: full palette from the primer.",
            "primed" to "Already black-primed and drybrushed: recipe from the colour layers.",
            "half-painted" to "Some zones are painted: what is left and matching colours.",
            "during" to "A specific question about one zone.",
        ).forEach { (wire, help) ->
            compose.onNodeWithTag("moment_help_$wire", useUnmergedTree = true).assert(hasText(help))
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `says the project photo will be sent until a photo is taken`() {
        show()
        compose.onNodeWithTag("ask_default_photo").performScrollTo().assert(hasText("The project photo will be sent.", substring = true))
        compose.onNodeWithTag("ask_default_photo_thumb", useUnmergedTree = true).assertExists()

        app.photos.nextPhoto = jpeg()
        click("ask_take_photo")

        compose.onNodeWithTag("ask_photo_preview").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("ask_default_photo").assertCountEquals(0)
    }

    @Test
    fun `asks to choose or take a photo when the project has none`() {
        show(withPhoto = false)
        compose.onNodeWithTag("ask_default_photo").assert(hasText("Choose or take a photo to send.", substring = true))
        compose.onAllNodesWithTag("ask_default_photo_thumb", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `explains where to copy the answer from`() {
        show()
        compose.onNodeWithTag("ask_paste_help")
            .assert(hasText("Copy the answer in Claude or ChatGPT, or share it to Forja de Color."))
    }

    @Test
    @Config(qualifiers = "es")
    fun `explains the steps and moments in Spanish`() {
        show()
        compose.onNodeWithTag("ask_step_1").assert(hasText("1. Elige el momento"))
        compose.onNodeWithTag("moment_help_start", useUnmergedTree = true).assert(hasText("Miniatura sin pintar: paleta completa desde la imprimación."))
    }

    private val extraPaint = Paint("Citadel", "Nuln Oil", "Nuln Oil", "Shade", PaintType.WASH, "#222222")

    private fun instructions(): String = runBlocking { buildBrainInstructions(app.repository.getInventory()) }

    private fun storeCopiedFingerprint() = runBlocking {
        app.repository.setMeta(MetaKey.AI_INSTRUCTIONS_COPIED, instructionsFingerprint(instructions()))
    }

    private fun waitForTag(tag: String) = app.waitFor(compose) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `never copied, invites to copy the AI instructions`() {
        show()
        waitForTag("ask_ai_instructions")
        compose.onNodeWithText("Using a Claude project or a custom GPT? Copy the AI instructions.").performScrollTo()
            .assert(hasAnyAncestor(hasTestTag("ask_ai_instructions")))
        compose.onNodeWithTag("ask_copy_instructions").assertExists()
    }

    @Test
    fun `copying puts the instructions on the clipboard, remembers them and hides the line`() {
        show()
        waitForTag("ask_copy_instructions")
        click("ask_copy_instructions")

        app.waitFor(compose) { compose.onAllNodesWithText("Copied.").fetchSemanticsNodes().isNotEmpty() }
        val clip = app.context.getSystemService(ClipboardManager::class.java).primaryClip
        assertEquals(instructions(), clip?.getItemAt(0)?.text?.toString())
        assertEquals(instructionsFingerprint(instructions()), runBlocking { app.repository.getMeta(MetaKey.AI_INSTRUCTIONS_COPIED) })
        compose.onNodeWithTag("ask_copy_instructions").assertDoesNotExist()
        compose.onNodeWithText("Using a Claude project or a custom GPT? Copy the AI instructions.").assertDoesNotExist()
    }

    @Test
    fun `instructions changed since the last copy warns to copy them again`() {
        storeCopiedFingerprint()
        runBlocking { app.repository.saveInventory(app.repository.getInventory().addPaint(extraPaint)) }
        show()
        waitForTag("ask_ai_instructions")
        compose.onNodeWithText("The AI instructions changed since you last copied them: copy them again.").performScrollTo()
            .assert(hasAnyAncestor(hasTestTag("ask_ai_instructions")))
        compose.onNodeWithTag("ask_copy_instructions").assertExists()
    }

    @Test
    fun `instructions current shows nothing about them`() {
        storeCopiedFingerprint()
        show()
        compose.waitForIdle()
        compose.onNodeWithTag("ask_ai_instructions").assertDoesNotExist()
        compose.onNodeWithTag("ask_copy_instructions").assertDoesNotExist()
    }
}

private fun hasText(regex: Regex) = SemanticsMatcher("text matches $regex") { node ->
    node.config.getOrNull(SemanticsProperties.Text)?.any { regex.containsMatchIn(it.text) } == true
}

private fun jpeg(): ByteArray = ByteArrayOutputStream().also {
    Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
}.toByteArray()
