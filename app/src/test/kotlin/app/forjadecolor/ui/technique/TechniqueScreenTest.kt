package app.forjadecolor.ui.technique

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.AppContainer
import app.forjadecolor.domain.advice.TechniqueCardId
import app.forjadecolor.ui.theme.ForgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TechniqueScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `shows the card title from its Markdown heading and the body text`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        var backs = 0
        compose.setContent { ForgeTheme { TechniqueScreen(container, TechniqueCardId.SLAPCHOP, onBack = { backs++ }) } }

        compose.onNodeWithTag("technique_title").assertTextEquals("Slapchop")
        compose.onNodeWithText("TODO(user): write from the BSL book").assertExists()
        compose.onNodeWithText("# Slapchop", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("technique_back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the title falls back to the card id when the Markdown has no heading`() {
        val card = parseCard(TechniqueCardId.DRYBRUSH, "Just text")
        assertEquals("drybrush", card.title)
        assertEquals(listOf<MdBlock>(MdBlock.Paragraph("Just text")), card.blocks)
    }

    @Test
    fun `renders headings, paragraphs, bullet lists and bold`() {
        val card = parseCard(TechniqueCardId.BSL, "# BSL\n\nIntro line one\ncontinues.\n\n## Steps\n- **Base** first\n* Shadow\n\nEnd.")
        assertEquals("BSL", card.title)
        assertEquals(
            listOf(
                MdBlock.Paragraph("Intro line one continues."),
                MdBlock.Heading(2, "Steps"),
                MdBlock.Bullets(listOf("**Base** first", "Shadow")),
                MdBlock.Paragraph("End."),
            ),
            card.blocks,
        )
        val bold = boldText("**Base** first")
        assertEquals("Base first", bold.text)
        assertEquals(1, bold.spanStyles.size)
        assertEquals(0 until 4, bold.spanStyles[0].start until bold.spanStyles[0].end)
    }
}
