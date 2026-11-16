package app.forjadecolor.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.AppContainer
import app.forjadecolor.ui.navigation.AppNavHost
import app.forjadecolor.ui.theme.ForgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranslationTest {
    @get:Rule val compose = createComposeRule()

    private fun openSettings() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        compose.setContent { ForgeTheme { AppNavHost(container) } }
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "es")
    fun `a phone in Spanish shows the interface in Spanish`() {
        openSettings()

        compose.onNodeWithText("Proyectos", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Mis pinturas", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Exportar copia de seguridad").assertIsDisplayed()
        compose.onNodeWithText("Importar copia de seguridad").assertIsDisplayed()
        compose.onNodeWithText("My paints", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en")
    fun `a phone in English keeps the interface in English`() {
        openSettings()

        compose.onNodeWithText("Projects", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("My paints", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Export backup").assertIsDisplayed()
        compose.onNodeWithText("Import backup").assertIsDisplayed()
    }

    @Test
    fun `every string in values has a Spanish translation in values-es`() {
        val english = stringNames(File("src/main/res/values"))
        val spanish = stringNames(File("src/main/res/values-es"))

        assertTrue(english.isNotEmpty(), "no strings found in values/")
        assertEquals(emptySet(), english - spanish - UNTRANSLATED, "strings missing from values-es/")
        assertEquals(emptySet(), spanish - english, "values-es/ strings that values/ does not have")
    }

    private fun stringNames(dir: File): Set<String> {
        val files = dir.listFiles { f -> f.extension == "xml" }.orEmpty()
        return files.flatMap { file ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
        }.toSet()
    }

    private companion object {
        /** The brand name is the same in every language. */
        val UNTRANSLATED = setOf("app_name")
    }
}
