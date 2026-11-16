package app.forjadecolor.ui.projects

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.domain.projects.markDone
import app.forjadecolor.ui.project.TestApp
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Cases ported from the PWA's src/ui/projects-view.test.ts.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app = TestApp()
    private val opened = mutableListOf<String>()

    private fun show() = app.setContent(compose) { ProjectsScreen(app.container, onOpenProject = { opened += it }) }

    @Test
    fun `creates a project with its photo stored, then opens it`() {
        app.photos.nextPhoto = byteArrayOf(1, 2, 3, 4, 5)
        show()
        compose.onNodeWithTag("new_project_name").performScrollTo().performTextInput("Bárbara")
        compose.onNodeWithTag("new_project_choose_photo").performScrollTo().performClick()
        compose.onNodeWithTag("new_project_create").performScrollTo().performClick()

        app.waitFor(compose) { opened.isNotEmpty() }
        val p = app.projects().single()
        assertEquals("Bárbara", p.name)
        assertEquals("2026-11-11T10:00:00.000Z", p.createdAt)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), runBlocking { app.repository.getBlob(p.initialPhotoBlobId!!) }!!.bytes)
        assertEquals(listOf(p.id), opened)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `shows the taken photo in the new-project form before creating it`() {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out)
        app.photos.nextPhoto = out.toByteArray()
        show()
        compose.onAllNodesWithTag("new_project_photo_preview").assertCountEquals(0)

        compose.onNodeWithTag("new_project_take_photo").performScrollTo().performClick()

        compose.onNodeWithTag("new_project_photo_preview").performScrollTo().assertIsDisplayed()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `keeps the taken photo when the screen is recreated, as on rotation`() {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out)
        app.photos.nextPhoto = out.toByteArray()
        val restoration = app.setRestorableContent(compose) { ProjectsScreen(app.container, onOpenProject = { opened += it }) }
        compose.onNodeWithTag("new_project_name").performScrollTo().performTextInput("Búho")
        compose.onNodeWithTag("new_project_take_photo").performScrollTo().performClick()
        compose.onNodeWithTag("new_project_photo_preview").performScrollTo().assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("new_project_photo_preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("new_project_create").performScrollTo().performClick()
        app.waitFor(compose) { opened.isNotEmpty() }
        val p = app.projects().single()
        assertContentEquals(out.toByteArray(), runBlocking { app.repository.getBlob(p.initialPhotoBlobId!!) }!!.bytes)
    }

    @Test
    fun `creates a project without a photo, as the photo is optional`() {
        show()
        compose.onNodeWithTag("new_project_name").performScrollTo().performTextInput("Druida")
        compose.onNodeWithTag("new_project_create").performScrollTo().performClick()

        app.waitFor(compose) { opened.isNotEmpty() }
        assertNull(app.projects().single().initialPhotoBlobId)
    }

    @Test
    fun `does not create a project without a name`() {
        show()
        compose.onNodeWithTag("new_project_name").performScrollTo().performTextInput("   ")
        compose.onNodeWithTag("new_project_create").performScrollTo().performClick()
        compose.waitForIdle()

        assertTrue(app.projects().isEmpty())
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `lists projects in progress before finished ones`() {
        runBlocking {
            app.repository.saveProject(createProject("Terminada", app.now, "a").markDone())
            app.repository.saveProject(createProject("En curso", app.now, "b"))
        }
        show()

        app.waitFor(compose) { compose.onAllNodesWithTag("project_link").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithText("En curso · In progress").assertIsDisplayed()
        val links = compose.onAllNodesWithTag("project_link").fetchSemanticsNodes()
        val texts = links.map { node -> node.config.toString() }
        assertTrue(texts[0].contains("En curso"))
        assertTrue(texts[1].contains("Terminada · Done"))

        compose.onNodeWithText("Terminada · Done").performClick()
        assertEquals(listOf("a"), opened)
    }

    @Test
    fun `shows the empty state without projects`() {
        show()
        compose.onNodeWithText("No projects yet.").assertIsDisplayed()
    }
}
