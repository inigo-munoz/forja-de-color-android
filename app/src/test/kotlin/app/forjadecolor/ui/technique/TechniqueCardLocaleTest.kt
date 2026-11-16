package app.forjadecolor.ui.technique

import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.AppContainer
import app.forjadecolor.domain.advice.TechniqueCardId
import app.forjadecolor.ui.theme.ForgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TechniqueCardLocaleTest {
    @get:Rule val compose = createComposeRule()

    private val assets = ApplicationProvider.getApplicationContext<Context>().assets

    private fun asset(path: String) = assets.open(path).bufferedReader().use { it.readText() }

    @Test
    fun `Spanish picks every card from cards-es`() {
        for (id in TechniqueCardId.entries) {
            assertEquals(asset("cards-es/${id.wire}.md"), readCardMarkdown(assets, id, "es"), "card ${id.wire}")
        }
    }

    @Test
    fun `English, or any other language, reads the English card`() {
        assertEquals(asset("cards/drybrush.md"), readCardMarkdown(assets, TechniqueCardId.DRYBRUSH, "en"))
        assertEquals(asset("cards/drybrush.md"), readCardMarkdown(assets, TechniqueCardId.DRYBRUSH, "fr"))
    }

    @Test
    fun `a missing Spanish card falls back to English, and a missing card is empty`() {
        val files = mapOf("cards/bsl.md" to "# BSL\n\nEnglish")
        val open = { path: String -> files[path]?.byteInputStream() ?: throw FileNotFoundException(path) }

        assertEquals("# BSL\n\nEnglish", readCardMarkdown(open, TechniqueCardId.BSL, "es"))
        assertEquals("", readCardMarkdown(open, TechniqueCardId.SLAPCHOP, "es"))
    }

    @Test
    @Config(qualifiers = "es")
    fun `the technique screen shows the Spanish card on a phone in Spanish`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        compose.setContent { ForgeTheme { TechniqueScreen(container, TechniqueCardId.DRYBRUSH, onBack = {}) } }

        compose.onNodeWithTag("technique_title").assertTextEquals("Pincel seco")
        compose.onNodeWithTag("technique_back").assertTextEquals("Volver")
        compose.onNodeWithText("Errores típicos").assertExists()
    }

    @Test
    @Config(qualifiers = "en")
    fun `the technique screen keeps the English card on a phone in English`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        compose.setContent { ForgeTheme { TechniqueScreen(container, TechniqueCardId.DRYBRUSH, onBack = {}) } }

        compose.onNodeWithTag("technique_back").assertTextEquals("Back")
        compose.onNodeWithText("Common mistakes").assertExists()
    }
}
