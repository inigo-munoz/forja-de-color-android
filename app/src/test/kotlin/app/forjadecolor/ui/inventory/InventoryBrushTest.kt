package app.forjadecolor.ui.inventory

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.AppContainer
import app.forjadecolor.data.ForjaDatabase
import app.forjadecolor.data.PhotoFiles
import app.forjadecolor.data.RoomRepository
import app.forjadecolor.domain.paints.Brush
import app.forjadecolor.ui.theme.ForgeTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals

/** Brushes are added with "+ Add brush" and edited by tapping one; the code identifies the brush. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryBrushTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ForjaDatabase
    private lateinit var photoDir: File
    private lateinit var repo: RoomRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ForjaDatabase::class.java).allowMainThreadQueries().build()
        photoDir = File(context.filesDir, "photos-inventory-brush-test").apply { deleteRecursively() }
        repo = RoomRepository(db, PhotoFiles(photoDir))
    }

    @After
    fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
    }

    private fun show() {
        val c = AppContainer(context, repository = repo)
        compose.setContent { ForgeTheme { InventoryScreen(c) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("brush").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun brushes() = runBlocking { repo.getInventory().brushes }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun `Add brush saves a new brush once it has a code and a name`() {
        show()
        compose.onNodeWithTag("add_brush").performScrollTo().performClick()
        compose.onNodeWithTag("save_brush").assertIsNotEnabled()

        compose.onNodeWithTag("field_brush_code").performTextReplacement(" 5/0 ")
        compose.onNodeWithTag("field_brush_name").performTextReplacement("Kolinsky detail")
        compose.onNodeWithTag("save_brush").performClick()

        waitForText("Kolinsky detail")
        assertEquals(4, brushes().size)
        assertEquals(Brush("5/0", "Kolinsky detail"), brushes().last())
        compose.onNodeWithTag("brush_form").assertDoesNotExist()
    }

    @Test
    fun `tapping a brush edits it in place instead of adding another`() {
        show()
        compose.onAllNodesWithTag("brush").onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("field_brush_name").performTextReplacement("Detail size 1, worn")
        compose.onNodeWithTag("save_brush").performClick()

        waitForText("Detail size 1, worn")
        assertEquals(3, brushes().size)
        assertEquals("B02001", brushes().first().code)
    }

    @Test
    fun `cancelling the brush form saves nothing`() {
        show()
        compose.onNodeWithTag("add_brush").performScrollTo().performClick()
        compose.onNodeWithTag("field_brush_code").performTextReplacement("9")
        compose.onNodeWithTag("field_brush_name").performTextReplacement("Never saved")
        compose.onNodeWithTag("cancel_brush").performClick()

        compose.onNodeWithTag("brush_form").assertDoesNotExist()
        compose.waitForIdle()
        assertEquals(3, brushes().size)
    }

    @Test
    fun `Add from a photo opens the scan`() {
        var opened = 0
        val c = AppContainer(context, repository = repo)
        compose.setContent { ForgeTheme { InventoryScreen(c, onScan = { opened++ }) } }
        compose.onNodeWithTag("scan_paints").performClick()
        assertEquals(1, opened)
    }
}
