package app.forjadecolor.ui.wheel

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.AppContainer
import app.forjadecolor.data.ForjaDatabase
import app.forjadecolor.data.PhotoFiles
import app.forjadecolor.data.RoomRepository
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintType
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

/** The colour wheel: tapping a colour shows its harmonies with the user's own paints. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColourWheelScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ForjaDatabase
    private lateinit var photoDir: File
    private lateinit var repo: RoomRepository
    private val container get() = AppContainer(context, repository = repo)

    private fun paint(code: String, name: String, hex: String, type: PaintType = PaintType.OPAQUE) =
        Paint("Vallejo", code, name, "Game Color", type, hex)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ForjaDatabase::class.java).allowMainThreadQueries().build()
        photoDir = File(context.filesDir, "photos-wheel-test").apply { deleteRecursively() }
        repo = RoomRepository(db, PhotoFiles(photoDir))
        runBlocking {
            repo.saveInventory(
                Inventory(
                    listOf(
                        paint("72.011", "Test Scarlet", "#c02321"),
                        paint("72.029", "Test Moss", "#2e6b2e"),
                        paint("72.051", "Test Black", "#000000"),
                        paint("72.052", "Test Gold", "#d4af37", PaintType.METALLIC),
                    ),
                    emptyList(),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
    }

    private fun waitForPaints() =
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("wheel_neutrals").fetchSemanticsNodes().isNotEmpty() }

    private fun section(tag: String, text: String) =
        compose.onNode(hasText(text) and hasAnyAncestorTag(tag), useUnmergedTree = true)

    private fun hasAnyAncestorTag(tag: String) = androidx.compose.ui.test.hasAnyAncestor(hasTestTag(tag))

    @Test
    fun `before choosing, the wheel invites a tap and lists the neutral paints`() {
        val c = container
        compose.setContent { ForgeTheme { ColourWheelScreen(c) } }
        waitForPaints()

        compose.onNodeWithTag("wheel_hint").assertIsDisplayed()
        compose.onNodeWithTag("harmony_complementary").assertDoesNotExist()
        section("wheel_neutrals", "Test Black").assertExists()
        section("wheel_neutrals", "Test Gold").assertExists()
    }

    @Test
    fun `tapping red shows its harmonies with the user's paints in each colour`() {
        val c = container
        compose.setContent { ForgeTheme { ColourWheelScreen(c) } }
        waitForPaints()

        compose.onNodeWithTag("sector_RED").performClick()

        section("wheel_selected", "Red").assertExists()
        section("wheel_selected", "Primary").assertExists()
        section("wheel_selected", "Test Scarlet").assertExists()
        section("harmony_complementary", "Green").assertExists()
        section("harmony_complementary", "Test Moss").assertExists()
        section("harmony_analogous", "Red-orange").assertExists()
        section("harmony_analogous", "Red-violet").assertExists()
        section("harmony_triad", "Yellow").assertExists()
        section("harmony_triad", "Blue").assertExists()
        val emptyTriad = compose.onAllNodes(hasText("None of your paints.") and hasAnyAncestorTag("harmony_triad"), useUnmergedTree = true)
        kotlin.test.assertEquals(2, emptyTriad.fetchSemanticsNodes().size)
    }

    @Test
    fun `the chosen colour survives the screen being recreated`() {
        val restore = StateRestorationTester(compose)
        val c = container
        restore.setContent { ForgeTheme { ColourWheelScreen(c) } }
        waitForPaints()
        compose.onNodeWithTag("sector_GREEN").performClick()

        restore.emulateSavedInstanceStateRestore()
        waitForPaints()

        section("wheel_selected", "Green").assertExists()
        section("harmony_complementary", "Test Scarlet").assertExists()
    }
}
