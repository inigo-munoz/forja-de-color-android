package app.forjadecolor.ui.inventory

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.forjadecolor.ui.project.TestApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Family and paint-type names are shown in the phone's language; the stored names never change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InventoryLabelsTest {
    @get:Rule val compose = createComposeRule()
    private val app = TestApp()

    private fun show() {
        app.setContent(compose) { InventoryScreen(app.container) }
        app.waitFor(compose) { compose.onAllNodesWithTagSafe("family_BSL Warm Flesh") }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagSafe(tag: String) =
        runCatching { onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode() }.isSuccess

    @Test
    @Config(qualifiers = "es")
    fun `in Spanish, family titles and paint types are translated`() {
        show()
        compose.onNodeWithText("BSL Piel pálida", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("BSL Verde frío", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("add_paint").performClick()
        compose.onNodeWithText("opaca", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("lavado", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English, family titles and paint types keep their names`() {
        show()
        compose.onNodeWithText("BSL Warm Flesh", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("add_paint").performClick()
        compose.onNodeWithText("opaque", useUnmergedTree = true).assertExists()
    }
}
