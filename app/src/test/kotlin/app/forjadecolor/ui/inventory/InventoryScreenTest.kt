package app.forjadecolor.ui.inventory

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.AppContainer
import app.forjadecolor.data.ForjaDatabase
import app.forjadecolor.data.PhotoFiles
import app.forjadecolor.data.RoomRepository
import app.forjadecolor.domain.paints.BslRole
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintFamily
import app.forjadecolor.domain.paints.PaintType
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ForjaDatabase
    private lateinit var photoDir: File
    private lateinit var repo: RoomRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ForjaDatabase::class.java).allowMainThreadQueries().build()
        photoDir = File(context.filesDir, "photos-inventory-test").apply { deleteRecursively() }
        repo = RoomRepository(db, PhotoFiles(photoDir))
    }

    @After
    fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
    }

    private fun show() {
        val container = AppContainer(context, repository = repo)
        compose.setContent { ForgeTheme { InventoryScreen(container) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("paint").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `shows the 12 BSL families lightest first, with swatches, and the three brushes`() {
        show()

        compose.onAllNodesWithTag("paint").assertCountEquals(48)
        val families = SEED_INVENTORY.paints.mapNotNull { it.family?.name }.distinct()
        assertEquals(12, families.size)
        families.forEach { compose.onNodeWithTag("family_$it").assertExists() }
        val blackRows = compose.onNodeWithTag("family_BSL Black").onChildren().filter(hasTestTag("paint"))
        blackRows.assertCountEquals(4)
        blackRows[0].assert(hasText("72.001", substring = true)).assert(hasText("Blanco Calavera", substring = true))
        blackRows[3].assert(hasText("72.051", substring = true))
        compose.onAllNodesWithTag("swatch", useUnmergedTree = true).assertCountEquals(48)
        compose.onAllNodesWithTag("brush").assertCountEquals(3)
        compose.onNodeWithText("Dry Brush Small", substring = true).assertExists()
        compose.onNodeWithText("Other paints").assertDoesNotExist()
    }

    @Test
    fun `shows the BSL role next to each paint in a family, filled from the seed for stored paints without roles`() {
        runBlocking {
            val bare = SEED_INVENTORY.paints.map { p: Paint -> p.copy(family = p.family?.copy(role = null)) }
            repo.saveInventory(SEED_INVENTORY.copy(paints = bare))
        }
        show()

        val blue = compose.onNodeWithTag("family_BSL Blue").onChildren().filter(hasTestTag("paint"))
        listOf("extra light", "light", "base", "shadow").forEachIndexed { i, role -> blue[i].assert(hasText(role)) }
        compose.onAllNodesWithTag("role", useUnmergedTree = true).assertCountEquals(44)
    }

    @Test
    @Config(qualifiers = "es")
    fun `shows the BSL roles in Spanish`() {
        show()

        val blue = compose.onNodeWithTag("family_BSL Blue").onChildren().filter(hasTestTag("paint"))
        listOf("luz extra", "luz", "base", "sombra").forEachIndexed { i, role -> blue[i].assert(hasText(role)) }
    }

    @Test
    fun `adding Vallejo 72003 replaces the stored paint with code 72_003 and keeps its family`() {
        show()
        compose.onNodeWithTag("add_paint").performClick()

        compose.onNodeWithTag("field_code").performScrollTo().performTextReplacement("72003")
        compose.onNodeWithTag("field_name").performScrollTo().performTextReplacement("Carne Pálida (renombrada)")
        compose.onNodeWithTag("color_hex").performScrollTo().performTextReplacement("#C9A47F")
        compose.onNodeWithTag("save_paint").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Carne Pálida (renombrada)", substring = true).fetchSemanticsNodes().isNotEmpty() }

        val inv = runBlocking { repo.getInventory() }
        assertEquals(48, inv.paints.size)
        val paint = inv.paints.single { it.code == "72.003" }
        assertEquals("Carne Pálida (renombrada)", paint.name)
        assertEquals("#c9a47f", paint.hex)
        assertEquals(PaintFamily("BSL Warm Flesh", 1, BslRole.FOURTH), paint.family)
        assertEquals(SEED_INVENTORY.brushes, inv.brushes)
    }

    @Test
    fun `a new paint without a family is saved and listed under Other paints`() {
        show()
        compose.onNodeWithTag("add_paint").performClick()

        compose.onNodeWithTag("field_brand").performScrollTo().performTextReplacement("Citadel")
        compose.onNodeWithTag("field_code").performScrollTo().performTextReplacement("Nuln Oil")
        compose.onNodeWithTag("field_name").performScrollTo().performTextReplacement("Nuln Oil")
        compose.onNodeWithTag("field_range").performScrollTo().performTextReplacement("Shade")
        compose.onNodeWithTag("type_wash").performScrollTo().performClick()
        compose.onNodeWithTag("save_paint").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Other paints").fetchSemanticsNodes().isNotEmpty() }

        val inv = runBlocking { repo.getInventory() }
        assertEquals(49, inv.paints.size)
        val added = inv.paints.last()
        assertEquals(listOf("Citadel", "Nuln Oil", "Nuln Oil", "Shade", "#888888"), listOf(added.brand, added.code, added.name, added.range, added.hex))
        assertEquals(PaintType.WASH, added.type)
        assertEquals(null, added.family)
        compose.onNodeWithTag("family_other").onChildren().filter(hasTestTag("paint")).assertCountEquals(1)
    }
}
