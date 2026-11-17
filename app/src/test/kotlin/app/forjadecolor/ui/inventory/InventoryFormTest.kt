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
    fun `searching the catalogue fills the form with the chosen paint, still editable`() {
        show()
        compose.onNodeWithTag("add_paint").performClick()
        compose.onNodeWithTag("catalogue_search").performScrollTo().performTextReplacement("72010")
        compose.onNodeWithTag("catalogue_result_0").performScrollTo().assert(hasText("Bloody Red", substring = true)).performClick()

        assertEquals(listOf("Vallejo", "72.010", "Bloody Red", "Bloody Red", "Game Color"), listOf("field_brand", "field_code", "field_name", "field_name_en", "field_range").map(::fieldText))
        assertEquals("#d41c1c", fieldText("color_hex"))
        compose.onNodeWithTag("field_name").performScrollTo().performTextReplacement("Rojo Sanguina")
        compose.onNodeWithTag("save_paint").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Rojo Sanguina", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val saved = runBlocking { repo.getInventory() }.paints.single { it.code == "72.010" }
        assertEquals(listOf("Rojo Sanguina", "Bloody Red"), listOf(saved.name, saved.nameEn))
    }

    @Test
    fun `the catalogue search also offers sets, which open as a list to confirm`() {
        var opened: String? = null
        val c = container
        compose.setContent { ForgeTheme { InventoryScreen(c, onAddSet = { opened = it }) } }
        waitForList()
        compose.onNodeWithTag("add_paint").performClick()
        compose.onNodeWithTag("catalogue_search").performScrollTo().performTextReplacement("72212")
        compose.onNodeWithTag("catalogue_set_0").performScrollTo().assert(hasText("Non-Metallic Metal", substring = true)).performClick()

        assertEquals("72.212", opened)
        compose.onNodeWithTag("paint_form").assertDoesNotExist()
    }

    @Test
    fun `a paint you own can be deleted after confirming, and cancelling keeps it`() {
        show()
        compose.onNodeWithTag("add_paint").performClick()
        compose.onNodeWithTag("delete_paint").assertDoesNotExist()
        compose.onNodeWithTag("cancel_paint").performScrollTo().performClick()

        compose.onAllNodes(hasTestTag("paint") and hasText("72.003", substring = true)).onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("delete_paint").performScrollTo().performClick()
        compose.onNodeWithTag("cancel_delete").performClick()
        compose.waitForIdle()
        assertEquals(48, runBlocking { repo.getInventory() }.paints.size)

        compose.onNodeWithTag("delete_paint").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_delete").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("paint") and hasText("72.003", substring = true)).fetchSemanticsNodes().isEmpty() }

        val inv = runBlocking { repo.getInventory() }
        assertEquals(47, inv.paints.size)
        assertTrue(inv.paints.none { it.code == "72.003" })
        compose.onNodeWithTag("paint_form").assertDoesNotExist()
    }

    @Test
    fun `deleting removes the paint that was opened, even after its code was edited`() {
        show()
        compose.onAllNodes(hasTestTag("paint") and hasText("72.003", substring = true)).onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("field_code").performScrollTo().performTextReplacement("72.001")
        compose.onNodeWithTag("delete_paint").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_delete").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("paint") and hasText("72.003", substring = true)).fetchSemanticsNodes().isEmpty() }

        val codes = runBlocking { repo.getInventory() }.paints.map { it.code }
        assertTrue("72.001" in codes)
        assertTrue("72.003" !in codes)
    }

    @Test
    fun `the catalogue search is only offered when adding`() {
        show()
        compose.onAllNodes(hasTestTag("paint") and hasText("72.003", substring = true)).onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("catalogue_search").assertDoesNotExist()
    }

    @Test
    fun `adding a code you already have warns with the paint you own`() {
        show()
        compose.onNodeWithTag("add_paint").performClick()
        compose.onNodeWithTag("already_owned").assertDoesNotExist()

        compose.onNodeWithTag("field_code").performScrollTo().performTextReplacement("72001")

        compose.onNodeWithTag("already_owned").performScrollTo().assert(hasText("You already have it: Blanco Calavera · Dead White (72.001)"))
        compose.onNodeWithTag("field_code").performScrollTo().performTextReplacement("72.999")
        compose.onNodeWithTag("already_owned").assertDoesNotExist()
    }

    @Test
    fun `editing a paint you own does not warn, and its English name is saved`() {
        show()
        compose.onAllNodes(hasTestTag("paint") and hasText("72.003", substring = true)).onFirst().performScrollTo().performClick()
        compose.onNodeWithTag("already_owned").assertDoesNotExist()

        compose.onNodeWithTag("field_name_en").performScrollTo().performTextReplacement("Pale Flesh (mine)")
        compose.onNodeWithTag("save_paint").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Pale Flesh (mine)", substring = true).fetchSemanticsNodes().isNotEmpty() }

        assertEquals("Pale Flesh (mine)", runBlocking { repo.getInventory() }.paints.single { it.code == "72.003" }.nameEn)
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
