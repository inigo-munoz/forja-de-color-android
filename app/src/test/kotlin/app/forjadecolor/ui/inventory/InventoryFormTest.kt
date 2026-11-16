package app.forjadecolor.ui.inventory

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.AppContainer
import app.forjadecolor.data.ForjaDatabase
import app.forjadecolor.data.PhotoFiles
import app.forjadecolor.data.RoomRepository
import app.forjadecolor.domain.paints.SEED_INVENTORY
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The add/update paint form: opened from "+ Add paint" or a paint row, and its colour picker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryFormTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ForjaDatabase
    private lateinit var photoDir: File
    private lateinit var repo: RoomRepository
    private val container get() = AppContainer(context, repository = repo)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ForjaDatabase::class.java).allowMainThreadQueries().build()
        photoDir = File(context.filesDir, "photos-inventory-form-test").apply { deleteRecursively() }
        repo = RoomRepository(db, PhotoFiles(photoDir))
    }

    @After
    fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
    }

    private fun waitForList() =
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("paint").fetchSemanticsNodes().isNotEmpty() }

    private fun show() {
        val c = container
        compose.setContent { ForgeTheme { InventoryScreen(c) } }
        waitForList()
    }

    private fun fieldText(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun previewState(): String? =
        compose.onNodeWithTag("color_preview").fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.StateDescription) { null }

    @Test
    fun `the form is hidden until Add paint opens it, and cancelling closes it without saving`() {
        show()
        compose.onNodeWithTag("save_paint").assertDoesNotExist()

        compose.onNodeWithTag("add_paint").performClick()
        compose.onNodeWithTag("field_code").performScrollTo().performTextReplacement("99.999")
        compose.onNodeWithTag("field_name").performScrollTo().performTextReplacement("Never saved")
        compose.onNodeWithTag("cancel_paint").performScrollTo().performClick()

        compose.onNodeWithTag("save_paint").assertDoesNotExist()
        compose.waitForIdle()
        val inv = runBlocking { repo.getInventory() }
        assertEquals(48, inv.paints.size)
        assertTrue(inv.paints.none { it.code == "99.999" })
    }

    @Test
    fun `saving adds the paint and closes the form`() {
        show()

        compose.onNodeWithTag("add_paint").performClick()
        compose.onNodeWithTag("field_code").performScrollTo().performTextReplacement("70.950")
        compose.onNodeWithTag("field_name").performScrollTo().performTextReplacement("Negro")
        compose.onNodeWithTag("save_paint").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Negro", substring = true).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("save_paint").assertDoesNotExist()
        assertEquals(49, runBlocking { repo.getInventory() }.paints.size)
    }

    @Test
    fun `tapping a paint opens the form prefilled and saving updates it keeping its family`() {
        show()
        val seed = SEED_INVENTORY.paints.single { it.code == "72.003" }

        compose.onAllNodes(hasTestTag("paint") and hasText("72.003", substring = true)).onFirst().performScrollTo().performClick()
        assertEquals("72.003", fieldText("field_code"))
        assertEquals(seed.name, fieldText("field_name"))
        assertEquals(seed.hex.lowercase(), fieldText("color_hex").lowercase())

        compose.onNodeWithTag("field_name").performScrollTo().performTextReplacement("Renamed flesh")
        compose.onNodeWithTag("save_paint").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Renamed flesh", substring = true).fetchSemanticsNodes().isNotEmpty() }

        val inv = runBlocking { repo.getInventory() }
        assertEquals(48, inv.paints.size)
        val paint = inv.paints.single { it.code == "72.003" }
        assertEquals("Renamed flesh", paint.name)
        assertEquals(seed.family, paint.family)
    }

    @Test
    fun `picking a swatch sets the hex, typing a hex updates the preview, moving the hue changes the hex`() {
        show()
        compose.onNodeWithTag("add_paint").performClick()

        compose.onNodeWithTag("color_swatch_3").performScrollTo().performClick()
        assertEquals(COLOR_PRESETS[3], fieldText("color_hex"))
        assertEquals(COLOR_PRESETS[3], previewState())

        compose.onNodeWithTag("color_hex").performScrollTo().performTextReplacement("#C9A47F")
        assertEquals("#c9a47f", previewState())

        compose.onNodeWithTag("color_hex").performScrollTo().performTextReplacement("#888888")
        compose.onNodeWithTag("color_hue").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(200f) }
        val hex = fieldText("color_hex")
        assertNotEquals("#888888", hex)
        assertTrue(Regex("^#[0-9a-f]{6}$").matches(hex), hex)
        assertEquals(hex, previewState())

        compose.onNodeWithTag("color_lightness").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.2f) }
        assertNotEquals(hex, fieldText("color_hex"))
    }

    @Test
    fun `the open form and its fields survive recreation`() {
        val c = container
        val tester = StateRestorationTester(compose)
        tester.setContent { ForgeTheme { InventoryScreen(c) } }
        waitForList()

        compose.onNodeWithTag("add_paint").performClick()
        compose.onNodeWithTag("field_code").performScrollTo().performTextReplacement("99.999")
        compose.onNodeWithTag("color_swatch_1").performScrollTo().performClick()
        tester.emulateSavedInstanceStateRestore()
        waitForList()

        compose.onNodeWithTag("save_paint").assertExists()
        assertEquals("99.999", fieldText("field_code"))
        assertEquals(COLOR_PRESETS[1], fieldText("color_hex"))
        compose.onNodeWithTag("color_preview").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, COLOR_PRESETS[1]))
    }
}
