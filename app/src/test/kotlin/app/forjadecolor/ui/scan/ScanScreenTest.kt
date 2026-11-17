package app.forjadecolor.ui.scan

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.paints.Brush
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.PaintType
import app.forjadecolor.domain.paints.findPaint
import app.forjadecolor.domain.scan.buildScanPrompt
import app.forjadecolor.ui.project.TestApp
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SCAN = """Here you go:
```json
{"version": 1, "kind": "inventory",
 "paints": [
   {"brand": "Vallejo", "code": "72001", "name": "Dead White", "range": "Game Color", "type": "opaque", "hex": "#fafafa"},
   {"brand": "Vallejo", "code": "73.201", "name": "Black Wash", "range": "Game Color Wash", "type": "wash", "hex": "#1a1a1a"},
   {"brand": "Scale75", "code": "", "name": "Dragon Red", "range": "Scalecolor", "type": "opaque", "hex": "#aa1111"}
 ],
 "brushes": [{"code": "B02001", "name": "Detail 1, new"}, {"code": "0", "name": "Kolinsky detail"}]}
```"""

/** Adding paints and brushes from a photo: send it with the scan instructions, paste the answer, confirm. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app = TestApp()
    private var done = 0

    private fun show(answer: String? = SCAN, sendResult: SendResult = SendResult.SHARED) {
        app.bridge.answer = answer
        app.bridge.result = sendResult
        app.setContent(compose) { ScanScreen(app.container, onDone = { done++ }) }
        app.waitFor(compose) { compose.onAllNodesWithTag("scan_send").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()

    private fun inventory() = runBlocking { app.repository.getInventory() }

    private fun waitForList() = app.waitFor(compose) { compose.onAllNodesWithTag("scan_list").fetchSemanticsNodes().isNotEmpty() }

    private fun checked(tag: String) =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On

    @Test
    fun `sends the photo with the scan instructions and does not keep a copy`() {
        show()
        click("scan_choose_photo")
        click("scan_send")

        app.waitFor(compose) { app.bridge.sent.isNotEmpty() }
        val sent = app.bridge.sent.single()
        assertEquals(buildScanPrompt(), sent.text)
        // Deleted once the share is handed over: it belongs to no project.
        app.waitFor(compose) { runBlocking { app.repository.getBlob(sent.imageId) } == null }
    }

    @Test
    fun `without a photo it asks for one and sends nothing`() {
        show()
        click("scan_send")

        compose.onNodeWithTag("scan_send_status").assertExists()
        assertTrue(app.bridge.sent.isEmpty())
    }

    @Test
    fun `when sharing is not possible the instructions are shown to copy`() {
        show(sendResult = SendResult.FALLBACK)
        click("scan_take_photo")
        click("scan_send")

        app.waitFor(compose) { compose.onAllNodesWithTag("scan_prompt").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `an answer it cannot use shows why and no list`() {
        show(answer = "Sorry, the photo is too blurry.")
        click("scan_paste")

        app.waitFor(compose) { compose.onAllNodesWithTag("answer_errors").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("scan_list").assertDoesNotExist()
    }

    @Test
    fun `the list waits for confirmation, and saving adds only what is ticked`() {
        show()
        click("scan_paste")
        waitForList()

        // Dead White (72.001) is already owned: flagged and unticked; ticking it updates the owned one.
        assertTrue(!checked("scan_paint_include_0"))
        compose.onNodeWithTag("scan_paint_owned_0", useUnmergedTree = true).assert(hasText("You already have it: Blanco Calavera · Dead White (72.001)"))
        compose.onNodeWithTag("scan_paint_owned_1", useUnmergedTree = true).assertDoesNotExist()
        click("scan_paint_include_0")
        assertTrue(checked("scan_paint_include_1"))
        compose.onNodeWithTag("scan_paint_include_2").assertIsNotEnabled()
        assertEquals(48, inventory().paints.size)

        click("scan_brush_include_1")
        click("scan_save")

        app.waitFor(compose) { done == 1 }
        val inv = inventory()
        assertEquals(49, inv.paints.size)
        val white = inv.findPaint(PaintRef("Vallejo", "72.001"))!!
        // Checked in the catalogue: "Dead White" is its English name, so the Spanish one is kept.
        assertEquals("Blanco Calavera" to "Dead White", white.name to white.nameEn)
        assertEquals("BSL Black", white.family?.name)
        assertEquals(PaintType.WASH, inv.findPaint(PaintRef("Vallejo", "73.201"))!!.type)
        assertTrue(inv.paints.none { it.name == "Dragon Red" })
        assertEquals(Brush("B02001", "Detail 1, new"), inv.brushes.first())
        assertEquals(3, inv.brushes.size)
    }

    @Test
    fun `paints found in the catalogue take its data and say so`() {
        show(answer = """{"version": 1, "kind": "inventory", "paints": [
          {"brand": "Vallejo", "code": "72010", "nameEs": "Rojo Sanguina", "nameEn": "Blood Red", "hex": "#ff0000"},
          {"brand": "Scale75", "code": "SC-01", "name": "Black", "hex": "#111111"}]}""")
        click("scan_paste")
        waitForList()

        compose.onNodeWithTag("scan_paint_catalogue_0", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("scan_paint_catalogue_1", useUnmergedTree = true).assertDoesNotExist()
        click("scan_save")
        app.waitFor(compose) { done == 1 }
        val saved = inventory().findPaint(PaintRef("Vallejo", "72.010"))!!
        assertEquals(listOf("Rojo Sanguina", "Bloody Red", "#d41c1c", "Game Color"), listOf(saved.name, saved.nameEn, saved.hex, saved.range))
    }

    @Test
    fun `a set opens straight as a list to confirm, with the paints already owned unticked`() {
        app.setContent(compose) { ScanScreen(app.container, setCode = "72.212", onDone = { done++ }) }
        waitForList()

        assertEquals(listOf(false, false, false, true, false, true, true, true), (0..7).map { checked("scan_paint_include_$it") })
        click("scan_save")
        app.waitFor(compose) { done == 1 }
        val codes = inventory().paints.map { it.code }
        assertEquals(52, codes.size)
        assertTrue(listOf("72.050", "72.091", "72.151", "72.153").all { it in codes })
    }

    @Test
    fun `tapping an item corrects it before saving`() {
        show()
        click("scan_paste")
        waitForList()

        click("scan_paint_2")
        compose.onNodeWithTag("field_code").performScrollTo().performTextReplacement("SC-15")
        click("save_paint")

        app.waitFor(compose) { checked("scan_paint_include_2") }
        click("scan_save")
        app.waitFor(compose) { done == 1 }
        assertEquals("Dragon Red", inventory().findPaint(PaintRef("Scale75", "SC-15"))?.name)
    }

    @Test
    fun `discarding the list saves nothing and goes back to the steps`() {
        show()
        click("scan_paste")
        waitForList()

        click("scan_discard")

        compose.onNodeWithTag("scan_list").assertDoesNotExist()
        compose.onNodeWithTag("scan_send").assertExists()
        assertEquals(48, inventory().paints.size)
        assertEquals(0, done)
    }
}
