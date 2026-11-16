package app.forjadecolor.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.AppContainer
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.forjadecolor.ui.navigation.AppNavHost
import app.forjadecolor.ui.navigation.Routes
import app.forjadecolor.ui.theme.ForgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShellTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `bottom navigation shows the three sections and opens My paints`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        compose.setContent { ForgeTheme { AppNavHost(container) } }

        compose.onNodeWithTag("nav_projects").assertIsDisplayed()
        compose.onNodeWithTag("nav_inventory").assertIsDisplayed()
        compose.onNodeWithTag("nav_settings").assertIsDisplayed()
        compose.onNodeWithText("My paints", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Settings", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("screen_projects").assertIsDisplayed()

        compose.onNodeWithTag("nav_inventory").performClick()

        compose.onNodeWithTag("screen_inventory").assertIsDisplayed()
    }

    @Test
    fun `Projects tab returns to the projects list from Settings`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        compose.setContent { ForgeTheme { AppNavHost(container) } }

        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
        compose.onNodeWithTag("nav_projects").performClick()

        compose.onNodeWithTag("screen_projects").assertIsDisplayed()
    }

    @Test
    fun `Projects tab returns to the projects list from My paints`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        compose.setContent { ForgeTheme { AppNavHost(container) } }

        compose.onNodeWithTag("nav_inventory").performClick()
        compose.onNodeWithTag("screen_inventory").assertIsDisplayed()
        compose.onNodeWithTag("nav_projects").performClick()

        compose.onNodeWithTag("screen_projects").assertIsDisplayed()
    }

    @Test
    fun `Projects tab returns to the projects list from a project`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            ForgeTheme { AppNavHost(container, nav) }
        }

        compose.runOnUiThread { nav.navigate(Routes.project("missing")) }
        compose.onNodeWithTag("screen_project").assertIsDisplayed()
        compose.onNodeWithTag("nav_projects").performClick()

        compose.onNodeWithTag("screen_projects").assertIsDisplayed()
    }

    @Test
    fun `Projects tab shows the list after leaving a project for Settings`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            ForgeTheme { AppNavHost(container, nav) }
        }

        compose.runOnUiThread { nav.navigate(Routes.project("missing")) }
        compose.onNodeWithTag("screen_project").assertIsDisplayed()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
        compose.onNodeWithTag("nav_projects").performClick()

        compose.onNodeWithTag("screen_projects").assertIsDisplayed()
    }
}
