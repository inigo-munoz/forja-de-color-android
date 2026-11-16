package app.forjadecolor.ui.project

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import app.forjadecolor.domain.advice.AdviceEntry
import app.forjadecolor.domain.advice.Bsl
import app.forjadecolor.domain.advice.ConsultationPaint
import app.forjadecolor.domain.advice.ConsultationResponse
import app.forjadecolor.domain.advice.RecipeMoment
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.advice.Technique
import app.forjadecolor.domain.advice.TechniqueCardId
import app.forjadecolor.domain.advice.ZoneRecipe
import app.forjadecolor.domain.advice.buildImagePrompt
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.SEED_INVENTORY
import app.forjadecolor.domain.projects.ProgressPhoto
import app.forjadecolor.domain.projects.ProjectStatus
import app.forjadecolor.domain.projects.addAdvice
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.domain.storage.StoredBlob
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun v(code: String) = PaintRef("Vallejo", code)

private val RECIPE = AdviceEntry(
    id = "e1",
    receivedAt = "2026-11-11T10:00:00Z",
    unknownPaints = listOf(PaintRef("Citadel", "Abaddon Black")),
    unknownBrushes = listOf("X999"),
    response = RecipeResponse(
        moment = RecipeMoment.HALF_PAINTED,
        prep = listOf("Imprimar en negro"),
        tips = listOf("Capas finas"),
        zones = listOf(
            ZoneRecipe("capa", Technique.SLAPCHOP, paint = v("72.021"), dilution = "1:3", steps = listOf("Veladura"), brush = "X999"),
            ZoneRecipe("cara", Technique.BSL, bsl = Bsl(v("72.099"), v("72.071"), v("72.098")), steps = listOf("Base")),
            ZoneRecipe("peana", Technique.SLAPCHOP, paint = PaintRef("Citadel", "Abaddon Black"), dilution = "1:1", steps = emptyList()),
            ZoneRecipe(
                "botas", Technique.SLAPCHOP, paint = v("72.021"), dilution = "1:2",
                steps = listOf("Remata con un punto de Vallejo 72.001 en 2 capas"), brush = "B02002", alreadyPainted = true,
            ),
        ),
        complements = listOf(v("72.008")),
        remainingSteps = listOf("Barnizar"),
    ),
)

private val CONSULTATION = AdviceEntry(
    id = "e2",
    receivedAt = "2026-11-11T11:00:00Z",
    unknownPaints = emptyList(),
    unknownBrushes = emptyList(),
    response = ConsultationResponse(
        zone = "capa", question = "highlight", technique = "Luz de filo", steps = listOf("Pincel seco"),
        paints = listOf(ConsultationPaint(v("72.001"))), techniqueCard = TechniqueCardId.DRYBRUSH,
    ),
)

// Cases ported from the PWA's src/ui/project-view.test.ts and painted-image.test.ts.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectScreenTest {
    @get:Rule val compose = createComposeRule()
    private val app = TestApp()
    private val techniques = mutableListOf<TechniqueCardId>()
    private var asked = 0
    private var backs = 0

    private fun show(withPhoto: Boolean = false, withTarget: Boolean = false) {
        runBlocking {
            val photo = if (withPhoto) app.repository.putBlob(StoredBlob("image/jpeg", "raw".toByteArray())) else null
            val target = if (withTarget) app.repository.putBlob(StoredBlob("image/jpeg", "painted".toByteArray())) else null
            val p = createProject("Bárbara", app.now, "p1").addAdvice(RECIPE).addAdvice(CONSULTATION)
            app.repository.saveProject(p.copy(initialPhotoBlobId = photo, targetImageBlobId = target))
        }
        app.setContent(compose) {
            ProjectScreen(app.container, "p1", onAsk = { asked++ }, onOpenTechnique = { techniques += it }, onBack = { backs++ })
        }
        app.waitFor(compose) { compose.onAllNodesWithTag("toggle_done").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Selects one of the project tabs: "recipe", "diary" or "photos". */
    private fun openTab(name: String) = compose.onNodeWithTag("tab_$name").performScrollTo().performClick()

    private fun inEntry(id: String, tag: String) = compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("advice_$id")))

    // diary

    @Test
    fun `persists notes after typing`() {
        show()
        openTab("diary")
        compose.onNodeWithTag("notes").performScrollTo().performTextInput("capa roja")
        app.waitFor(compose) { app.project("p1")?.notes == "capa roja" }
    }

    @Test
    fun `adds a dated progress photo`() {
        show()
        openTab("photos")
        compose.onNodeWithTag("add_progress").performScrollTo().performClick()
        app.waitFor(compose) { app.project("p1")?.progress?.size == 1 }
        assertEquals("2026-11-11T10:00:00.000Z", app.project("p1")!!.progress[0].takenAt)
        app.waitFor(compose) { compose.onAllNodesWithTag("progress_photo").fetchSemanticsNodes().size == 1 }
    }

    @Test
    fun `tapping a progress photo shows it large, and closing goes back`() {
        show()
        openTab("photos")
        compose.onNodeWithTag("add_progress").performScrollTo().performClick()
        app.waitFor(compose) { compose.onAllNodesWithTag("open_progress_photo").fetchSemanticsNodes().size == 1 }

        compose.onNodeWithTag("open_progress_photo").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("progress_photo_large").assertIsDisplayed()

        compose.onNodeWithTag("close_photo").performClick()
        compose.onAllNodesWithTag("progress_photo_large").assertCountEquals(0)
    }

    @Test
    fun `adding a progress photo keeps them in date order`() {
        runBlocking {
            // an older photo taken later than the clock, so the new one must go first
            val p = createProject("Bárbara", app.now, "p1").copy(progress = listOf(ProgressPhoto("old", "2026-12-01T00:00:00.000Z", "b-old")))
            app.repository.saveProject(p)
        }
        app.setContent(compose) { ProjectScreen(app.container, "p1", onAsk = {}, onOpenTechnique = {}, onBack = {}) }
        app.waitFor(compose) { compose.onAllNodesWithTag("toggle_done").fetchSemanticsNodes().isNotEmpty() }
        openTab("photos")
        compose.onNodeWithTag("add_progress").performScrollTo().performClick()

        app.waitFor(compose) { app.project("p1")?.progress?.size == 2 }
        assertEquals(listOf("2026-11-11T10:00:00.000Z", "2026-12-01T00:00:00.000Z"), app.project("p1")!!.progress.map { it.takenAt })
    }

    @Test
    fun `sets an optional target image`() {
        show()
        openTab("photos")
        compose.onNodeWithTag("set_target").performScrollTo().performClick()
        app.waitFor(compose) { app.project("p1")?.targetImageBlobId != null }
    }

    @Test
    fun `says so when a progress photo cannot be read, and adds nothing`() {
        show()
        openTab("photos")
        app.photos.failure = IllegalArgumentException("not an image")
        compose.onNodeWithTag("add_progress").performScrollTo().performClick()

        compose.onNodeWithTag("progress_photo_error").performScrollTo().assertIsDisplayed()
        assertEquals(0, app.project("p1")!!.progress.size)
    }

    @Test
    fun `says so when a progress photo from the camera cannot be read`() {
        show()
        openTab("photos")
        app.photos.failure = IllegalArgumentException("not an image")
        compose.onNodeWithTag("add_progress_camera").performScrollTo().performClick()

        compose.onNodeWithTag("progress_photo_error").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `clears the progress photo error once a photo works`() {
        show()
        openTab("photos")
        app.photos.failure = IllegalArgumentException("not an image")
        compose.onNodeWithTag("add_progress").performScrollTo().performClick()
        compose.onNodeWithTag("progress_photo_error").performScrollTo().assertIsDisplayed()

        app.photos.failure = null
        compose.onNodeWithTag("add_progress").performScrollTo().performClick()

        app.waitFor(compose) { app.project("p1")?.progress?.size == 1 }
        compose.onAllNodesWithTag("progress_photo_error").assertCountEquals(0)
    }

    @Test
    fun `says so when the target image cannot be read, and keeps none`() {
        show()
        openTab("photos")
        app.photos.failure = IllegalArgumentException("not an image")
        compose.onNodeWithTag("set_target").performScrollTo().performClick()

        compose.onNodeWithTag("target_photo_error").performScrollTo().assertIsDisplayed()
        assertEquals(null, app.project("p1")!!.targetImageBlobId)
    }

    @Test
    fun `marks done and reopens`() {
        show()
        compose.onNodeWithTag("toggle_done").performScrollTo().performClick()
        app.waitFor(compose) { app.project("p1")?.status == ProjectStatus.DONE }
        compose.onNodeWithText("Reopen").performScrollTo().performClick()
        app.waitFor(compose) { app.project("p1")?.status == ProjectStatus.IN_PROGRESS }
    }

    @Test
    fun `keeps notes typed right before marking the project done`() {
        show()
        openTab("diary")
        compose.onNodeWithTag("notes").performScrollTo().performTextInput("no perder esto")
        compose.onNodeWithTag("toggle_done").performScrollTo().performClick()
        app.waitFor(compose) { app.project("p1")?.status == ProjectStatus.DONE && app.project("p1")?.notes == "no perder esto" }
    }

    @Test
    fun `keeps notes typed right before leaving the screen`() {
        runBlocking { app.repository.saveProject(createProject("Bárbara", app.now, "p1")) }
        var open by mutableStateOf(true)
        app.setContent(compose) {
            if (open) ProjectScreen(app.container, "p1", onAsk = {}, onOpenTechnique = {}, onBack = {})
        }
        app.waitFor(compose) { compose.onAllNodesWithTag("toggle_done").fetchSemanticsNodes().isNotEmpty() }
        openTab("diary")
        compose.onNodeWithTag("notes").performScrollTo().performTextInput("antes de salir")
        open = false // leaves before the typing pause that triggers the normal save
        app.waitFor(compose) { app.project("p1")?.notes == "antes de salir" }
    }

    @Test
    fun `ask the advisor opens the ask screen`() {
        show()
        compose.onNodeWithTag("ask").performScrollTo().performClick()
        assertEquals(1, asked)
    }

    // tabs

    @Test
    fun `shows three tabs with the recipe selected first`() {
        show()
        compose.onNodeWithTag("tab_recipe").assertIsSelected()
        compose.onNodeWithTag("tab_diary").assertIsNotSelected()
        compose.onNodeWithTag("tab_photos").assertIsNotSelected()
        inEntry("e1", "delete_advice").assertExists()
        compose.onNodeWithTag("notes").assertDoesNotExist()
    }

    @Test
    fun `the diary tab shows the notes and hides the advice`() {
        show()
        openTab("diary")
        compose.onNodeWithTag("tab_diary").assertIsSelected()
        compose.onNodeWithTag("notes").assertExists()
        compose.onNodeWithTag("advice_e1").assertDoesNotExist()
        compose.onNodeWithTag("add_progress").assertDoesNotExist()
    }

    @Test
    fun `the photos tab shows progress photos and the target image, and hides the notes`() {
        show()
        openTab("photos")
        compose.onNodeWithTag("add_progress").assertExists()
        compose.onNodeWithTag("set_target").assertExists()
        compose.onNodeWithTag("notes").assertDoesNotExist()
        compose.onNodeWithTag("advice_e1").assertDoesNotExist()
    }

    @Test
    fun `the recipe tab says how to get advice when there is none`() {
        runBlocking { app.repository.saveProject(createProject("Bárbara", app.now, "p1")) }
        app.setContent(compose) { ProjectScreen(app.container, "p1", onAsk = {}, onOpenTechnique = {}, onBack = {}) }
        app.waitFor(compose) { compose.onAllNodesWithTag("advice_empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("advice_empty").assert(hasText("No advice yet: tap Ask the advisor."))
    }

    @Test
    fun `delete project stays at the end whatever tab is selected`() {
        show()
        listOf("recipe", "diary", "photos").forEach {
            openTab(it)
            compose.onNodeWithTag("delete_project").assertExists()
        }
    }

    @Test
    fun `the selected tab survives recreation`() {
        runBlocking { app.repository.saveProject(createProject("Bárbara", app.now, "p1")) }
        val restoration = app.setRestorableContent(compose) { ProjectScreen(app.container, "p1", onAsk = {}, onOpenTechnique = {}, onBack = {}) }
        app.waitFor(compose) { compose.onAllNodesWithTag("toggle_done").fetchSemanticsNodes().isNotEmpty() }
        openTab("photos")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithTag("tab_photos").assertIsSelected()
        compose.onNodeWithTag("add_progress").assertExists()
    }

    @Test
    fun `notes typed right before switching tab are saved`() {
        runBlocking { app.repository.saveProject(createProject("Bárbara", app.now, "p1")) }
        app.setContent(compose) { ProjectScreen(app.container, "p1", onAsk = {}, onOpenTechnique = {}, onBack = {}) }
        app.waitFor(compose) { compose.onAllNodesWithTag("toggle_done").fetchSemanticsNodes().isNotEmpty() }
        openTab("diary")
        app.waitFor(compose) { compose.onAllNodesWithTag("notes").fetchSemanticsNodes().isNotEmpty() }
        // Frozen clock: the typing pause that triggers the normal save never elapses.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("notes").performScrollTo().performTextInput("antes de cambiar")
        compose.mainClock.advanceTimeByFrame()
        openTab("photos")
        repeat(3) { compose.mainClock.advanceTimeByFrame() }

        compose.onNodeWithTag("notes").assertDoesNotExist()
        app.waitFor(compose) { app.project("p1")?.notes == "antes de cambiar" }
    }

    // advice

    @Test
    fun `shows prep, slapchop zones with paint and dilution, bsl zones with three swatches, and tips`() {
        show()
        compose.onNodeWithText("Imprimar en negro").assertExists()
        compose.onNode(hasText("Azul Mágico", substring = true) and hasAnyAncestor(hasTestTag("zone_capa"))).assertExists()
        compose.onNode(hasText("1:3", substring = true) and hasAnyAncestor(hasTestTag("zone_capa"))).assertExists()
        compose.onNodeWithText("Capas finas").assertExists()
        compose.onAllNodes(hasTestTag("swatch") and hasAnyAncestor(hasTestTag("zone_cara"))).assertCountEquals(3)
    }

    @Test
    fun `shows complements, remaining steps and already-painted zones for half-painted`() {
        show()
        compose.onNode(hasText("Naranja Fuego", substring = true) and !hasAnyAncestor(hasTestTag("needed"))).assertExists()
        compose.onNodeWithText("Barnizar").assertExists()
        compose.onNode(hasText("already painted", substring = true) and hasAnyAncestor(hasTestTag("zone_botas"))).assertExists()
    }

    @Test
    fun `shows a consultation with its technique, steps, paints and a technique card link`() {
        show()
        compose.onNode(hasText("Luz de filo", substring = true)).assertExists()
        compose.onNode(hasText("Blanco Calavera", substring = true) and hasAnyAncestor(hasTestTag("advice_e2")) and !hasAnyAncestor(hasTestTag("needed"))).assertExists()
        inEntry("e2", "see_technique").performScrollTo().performClick()
        assertEquals(listOf(TechniqueCardId.DRYBRUSH), techniques)
    }

    @Test
    fun `shows a brush by code and name from the inventory, and an unknown brush code as before`() {
        show()
        compose.onNode(hasText("🖌 B02002 Detail núm. 2") and hasAnyAncestor(hasTestTag("zone_botas"))).assertExists()
        compose.onNode(hasText("🖌 X999") and hasAnyAncestor(hasTestTag("zone_capa"))).assertExists()
    }

    @Test
    fun `shows the owned paints a step mentions under it, keeping the step text`() {
        show()
        compose.onNodeWithText("Remata con un punto de Vallejo 72.001 en 2 capas").assertExists()
        compose.onAllNodes(hasTestTag("step_paint") and hasAnyAncestor(hasTestTag("zone_botas")))
            .assertCountEquals(1)[0].assert(hasText("72.001 Blanco Calavera", substring = true))
        compose.onAllNodes(hasTestTag("step_paint") and hasAnyAncestor(hasTestTag("zone_capa"))).assertCountEquals(0)
    }

    @Test
    fun `lists each needed paint once`() {
        show()
        compose.onAllNodes(hasTestTag("needed_paint") and hasAnyAncestor(hasTestTag("advice_e1")) and hasText("72.021", substring = true))
            .assertCountEquals(1)
    }

    @Test
    fun `flags unknown paints and brushes, and assigning a paint persists`() {
        show()
        inEntry("e1", "unknown_brush").assert(hasText("X999", substring = true))
        inEntry("e1", "assign").performScrollTo().performClick()
        compose.onNodeWithText("72.051 Negro").performScrollTo().performClick()

        app.waitFor(compose) { app.project("p1")!!.advice[0].unknownPaints.isEmpty() }
        val saved = app.project("p1")!!.advice[0].response as RecipeResponse
        assertEquals(v("72.051"), saved.zones.first { it.name == "peana" }.paint)
    }

    @Test
    fun `deletes an advice entry after confirming`() {
        show()
        inEntry("e1", "delete_advice").performScrollTo().performClick()
        compose.onNodeWithText("Delete this advice? This cannot be undone.").assertIsDisplayed()
        compose.onNodeWithTag("confirm_delete").performClick()
        app.waitFor(compose) { app.project("p1")?.advice?.map { it.id } == listOf("e2") }
    }

    @Test
    fun `delete advice sits at the end of its card, after the recipe and the needed paints`() {
        show()
        val delete = inEntry("e1", "delete_advice").fetchSemanticsNode().positionInRoot.y
        val needed = compose.onNode(hasTestTag("needed") and hasAnyAncestor(hasTestTag("advice_e1"))).fetchSemanticsNode().positionInRoot.y
        assertTrue(delete > needed, "delete at $delete, needed paints at $needed")
    }

    @Test
    fun `with a target image, Photos shows it as a thumbnail and offers to change it`() {
        show(withTarget = true)
        openTab("photos")
        compose.onNodeWithTag("set_target").performScrollTo().assert(hasText("Change target image"))
        val thumb = compose.onNodeWithTag("target_thumb").fetchSemanticsNode().size.width
        val screen = compose.onRoot().fetchSemanticsNode().size.width
        assertTrue(thumb < screen / 2, "thumbnail $thumb px on a $screen px screen")
    }

    @Test
    fun `without a target image, Photos offers to set one`() {
        show()
        openTab("photos")
        compose.onNodeWithTag("set_target").performScrollTo().assert(hasText("Set target image"))
        compose.onAllNodesWithTag("target_thumb").assertCountEquals(0)
    }

    @Test
    fun `keeps an advice entry when the deletion is cancelled`() {
        show()
        inEntry("e1", "delete_advice").performScrollTo().performClick()
        compose.onNodeWithTag("cancel_delete").performClick()

        compose.onAllNodesWithTag("confirm_delete").assertCountEquals(0)
        assertEquals(listOf("e1", "e2"), app.project("p1")!!.advice.map { it.id })
    }

    @Test
    fun `deletes the project with its photos after confirming, then goes back`() {
        show(withPhoto = true, withTarget = true)
        val photos = app.project("p1")!!.let { listOf(it.initialPhotoBlobId!!, it.targetImageBlobId!!) }
        compose.onNodeWithTag("delete_project").performScrollTo().performClick()
        compose.onNodeWithText("Delete this project and its photos? This cannot be undone.").assertIsDisplayed()
        compose.onNodeWithTag("confirm_delete").performClick()

        app.waitFor(compose) { backs == 1 }
        assertNull(app.project("p1"))
        photos.forEach { assertNull(runBlocking { app.repository.getBlob(it) }) }
    }

    @Test
    fun `keeps the project when its deletion is cancelled`() {
        show(withPhoto = true)
        compose.onNodeWithTag("delete_project").performScrollTo().performClick()
        compose.onNodeWithTag("cancel_delete").performClick()

        assertEquals("Bárbara", app.project("p1")!!.name)
        assertEquals(0, backs)
    }

    @Test
    fun `deletes a progress photo and its file after confirming`() {
        show()
        openTab("photos")
        compose.onNodeWithTag("add_progress").performScrollTo().performClick()
        app.waitFor(compose) { compose.onAllNodesWithTag("delete_progress_photo").fetchSemanticsNodes().size == 1 }
        val blobId = app.project("p1")!!.progress.single().blobId
        // Inside the horizontally scrolled row the test window's tap lands outside the button: use its click action.
        compose.onNodeWithTag("delete_progress_photo").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Delete this progress photo? This cannot be undone.").assertIsDisplayed()
        compose.onNodeWithTag("confirm_delete").performClick()

        app.waitFor(compose) { app.project("p1")?.progress?.isEmpty() == true }
        app.waitFor(compose) { runBlocking { app.repository.getBlob(blobId) } == null }
    }

    @Test
    fun `removes the target image and its file after confirming`() {
        show(withTarget = true)
        val blobId = app.project("p1")!!.targetImageBlobId!!
        openTab("photos")
        compose.onNodeWithTag("remove_target").performScrollTo().performClick()
        compose.onNodeWithText("Remove the target image? This cannot be undone.").assertIsDisplayed()
        compose.onNodeWithTag("confirm_delete").performClick()

        app.waitFor(compose) { app.project("p1")?.targetImageBlobId == null }
        app.waitFor(compose) { runBlocking { app.repository.getBlob(blobId) } == null }
    }

    @Test
    fun `replacing the target image deletes the old one`() {
        show(withTarget = true)
        val old = app.project("p1")!!.targetImageBlobId!!
        openTab("photos")
        compose.onNodeWithTag("set_target").performScrollTo().performClick()

        app.waitFor(compose) { app.project("p1")?.targetImageBlobId.let { it != null && it != old } }
        app.waitFor(compose) { runBlocking { app.repository.getBlob(old) } == null }
    }

    @Test
    fun `a missing photo file shows a placeholder instead of crashing`() {
        runBlocking { app.repository.saveProject(createProject("Bárbara", app.now, "p1").copy(initialPhotoBlobId = "gone")) }
        app.setContent(compose) { ProjectScreen(app.container, "p1", onAsk = {}, onOpenTechnique = {}, onBack = {}) }
        app.waitFor(compose) { compose.onAllNodesWithTag("photo_placeholder").fetchSemanticsNodes().isNotEmpty() }
    }

    // painted image via ChatGPT

    @Test
    fun `paint it with ChatGPT sends the recipe colours as an image prompt with the unpainted photo`() {
        show(withPhoto = true)
        inEntry("e1", "paint_image").performScrollTo().performClick()

        app.waitFor(compose) { app.bridge.sent.isNotEmpty() }
        val pkg = app.bridge.sent.single()
        assertEquals(buildImagePrompt(RECIPE.response as RecipeResponse, SEED_INVENTORY), pkg.text)
        assertTrue(pkg.text.contains("Azul Mágico #016ca7"))
        assertEquals(app.project("p1")!!.initialPhotoBlobId, pkg.imageId)
        inEntry("e1", "paint_image_status").assert(hasText("Save the image ChatGPT makes", substring = true))
    }

    @Test
    fun `explains how to bring the image back when the share sheet could not be used`() {
        app.bridge.result = SendResult.FALLBACK
        show(withPhoto = true)
        inEntry("e1", "paint_image").performScrollTo().performClick()
        app.waitFor(compose) { app.bridge.sent.isNotEmpty() }
        compose.waitForIdle()
        inEntry("e1", "paint_image_status").assert(hasText("Prompt copied and photo downloaded", substring = true))
    }

    // before / after

    @Test
    fun `before and after appears only when both the photo and the painted image exist`() {
        show(withPhoto = true)
        compose.onNodeWithTag("compare").assertDoesNotExist()
    }

    @Test
    fun `dragging the slider reveals more of the painted image`() {
        show(withPhoto = true, withTarget = true)
        app.waitFor(compose) { compose.onAllNodesWithTag("compare").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("compare").assertIsDisplayed()
        compose.onNodeWithTag("compare_painted").assert(SemanticsMatcher.expectValue(PaintedRevealStart, 0.5f))

        compose.onNodeWithTag("compare_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }

        compose.onNodeWithTag("compare_painted").assert(SemanticsMatcher.expectValue(PaintedRevealStart, 0.3f))
        assertNotNull(app.project("p1")!!.targetImageBlobId)
    }

    // zone progress (S11 item 3)

    @Test
    fun `ticking a zone off saves it, updates the progress and survives reopening the screen`() {
        show()
        inEntry("e1", "zone_progress").performScrollTo().assert(hasText("0 of 4 zones"))
        inEntry("e1", "zone_done_1").performScrollTo().assertIsOff().performClick()

        app.waitFor(compose) { app.project("p1")!!.advice[0].doneZones == listOf(1) }
        inEntry("e1", "zone_progress").assert(hasText("1 of 4 zones"))
        inEntry("e1", "zone_done_1").assertIsOn()

        inEntry("e1", "zone_done_1").performScrollTo().performClick()
        app.waitFor(compose) { app.project("p1")!!.advice[0].doneZones.isEmpty() }
        inEntry("e1", "zone_progress").assert(hasText("0 of 4 zones"))
    }

    @Test
    fun `zones already done show ticked and dimmed, and a consultation has no checkboxes`() {
        runBlocking { app.repository.saveProject(createProject("Bárbara", app.now, "p1").addAdvice(RECIPE.copy(doneZones = listOf(0, 3))).addAdvice(CONSULTATION)) }
        app.setContent(compose) { ProjectScreen(app.container, "p1", onAsk = {}, onOpenTechnique = {}, onBack = {}) }
        app.waitFor(compose) { compose.onAllNodesWithTag("toggle_done").fetchSemanticsNodes().isNotEmpty() }

        inEntry("e1", "zone_progress").performScrollTo().assert(hasText("2 of 4 zones"))
        inEntry("e1", "zone_done_0").assertIsOn()
        inEntry("e1", "zone_done_1").assertIsOff()
        compose.onNode(hasTestTag("zone_capa") and SemanticsMatcher.expectValue(ZoneDoneKey, true)).assertExists()
        compose.onNode(hasTestTag("zone_cara") and SemanticsMatcher.expectValue(ZoneDoneKey, false)).assertExists()
        compose.onNode(hasText("Veladura") and hasAnyAncestor(hasTestTag("zone_capa"))).assertExists()
        compose.onAllNodes(hasTestTag("zone_progress") and hasAnyAncestor(hasTestTag("advice_e2"))).assertCountEquals(0)
    }
}
