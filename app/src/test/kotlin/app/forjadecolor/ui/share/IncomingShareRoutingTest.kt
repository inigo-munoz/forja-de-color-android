package app.forjadecolor.ui.share

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.platform.IncomingShare
import app.forjadecolor.ui.navigation.AppNavHost
import app.forjadecolor.ui.project.TestApp
import app.forjadecolor.ui.project.readFixture
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IncomingShareRoutingTest {
    @get:Rule val compose = createComposeRule()
    private val app = TestApp()

    private fun showApp() {
        runBlocking {
            app.repository.saveProject(createProject("Druida", app.now, "p1"))
            app.repository.saveProject(createProject("Bárbara", app.now, "p2"))
        }
        app.setContent(compose) { AppNavHost(app.container) }
    }

    @Test
    fun `a shared AI answer ends as saved advice on the chosen project`() {
        app.container.incomingShares.offer(IncomingShare.Text(readFixture("chatgpt-start-dryad.json")))
        showApp()

        app.waitFor(compose) { compose.onAllNodesWithTag("share_target_p2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("share_target_p2").performScrollTo().performClick()
        app.waitFor(compose) { compose.onAllNodesWithTag("save_answer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("save_answer").performScrollTo().performClick()

        app.waitFor(compose) { app.project("p2")!!.advice.size == 1 }
        assertTrue((app.project("p2")!!.advice.single().response as RecipeResponse).zones.isNotEmpty())
        assertTrue(app.project("p1")!!.advice.isEmpty())
        assertNull(app.container.incomingShares.current.value)
        app.waitFor(compose) { compose.onAllNodesWithTag("screen_project").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `a shared painted image becomes the chosen project's target image`() {
        val uri = Uri.parse("content://test.share/painted.png")
        val png = ByteArrayOutputStream().also { Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        shadowOf(app.context.contentResolver).registerInputStream(uri, ByteArrayInputStream(png))
        app.container.incomingShares.offer(IncomingShare.Image(uri))
        showApp()

        app.waitFor(compose) { compose.onAllNodesWithTag("share_target_p1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("share_target_p1").performScrollTo().performClick()

        app.waitFor(compose) { app.project("p1")!!.targetImageBlobId != null }
        assertNotNull(runBlocking { app.repository.getBlob(app.project("p1")!!.targetImageBlobId!!) })
        assertNull(app.container.incomingShares.current.value)
        app.waitFor(compose) { compose.onAllNodesWithTag("screen_project").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `dismissing a share drops it without touching any project`() {
        app.container.incomingShares.offer(IncomingShare.Text("hola"))
        showApp()
        app.waitFor(compose) { compose.onAllNodesWithTag("share_dismiss").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("share_dismiss").performScrollTo().performClick()

        app.waitFor(compose) { compose.onAllNodesWithTag("share_prompt").fetchSemanticsNodes().isEmpty() }
        assertNull(app.container.incomingShares.current.value)
        assertTrue(app.projects().all { it.advice.isEmpty() && it.targetImageBlobId == null })
    }
}
