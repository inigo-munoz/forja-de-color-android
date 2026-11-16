package app.forjadecolor.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.AppContainer
import app.forjadecolor.data.ForjaDatabase
import app.forjadecolor.data.PhotoFiles
import app.forjadecolor.data.RoomRepository
import app.forjadecolor.domain.ForjaJson
import app.forjadecolor.domain.advice.buildBrainInstructions
import app.forjadecolor.domain.isoString
import app.forjadecolor.domain.paints.SEED_INVENTORY
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.domain.storage.BackupFile
import app.forjadecolor.domain.storage.MetaKey
import app.forjadecolor.domain.storage.parseBackupText
import app.forjadecolor.domain.storage.validateBackup
import app.forjadecolor.ui.theme.ForgeTheme
import app.forjadecolor.platform.MAX_BACKUP_BYTES
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stands in for the system file picker: every launch answers at once with [result]. */
private class FakePicker(var result: Uri?) : ActivityResultRegistry() {
    val inputs = mutableListOf<Any?>()

    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        inputs += input
        dispatchResult(requestCode, result)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-11-11T10:00:00Z")
    private val picker = FakePicker(null)
    private lateinit var db: ForjaDatabase
    private lateinit var photoDir: File
    private lateinit var repo: RoomRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ForjaDatabase::class.java).allowMainThreadQueries().build()
        photoDir = File(context.filesDir, "photos-settings-test").apply { deleteRecursively() }
        repo = RoomRepository(db, PhotoFiles(photoDir))
        runBlocking { repo.saveProject(createProject("Bárbara", now, "p1")) }
    }

    @After
    fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
    }

    private fun show(maxBackupBytes: Long = MAX_BACKUP_BYTES) {
        val container = AppContainer(context, repository = repo, now = { now })
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = picker
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) { ForgeTheme { SettingsScreen(container, maxBackupBytes) } }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("export").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun pickToRead(text: String): Uri {
        val uri = Uri.parse("content://test/backup.json")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(text.toByteArray()))
        picker.result = uri
        return uri
    }

    @Test
    fun `export writes a backup the PWA accepts, named by date, and records lastExportAt`() {
        val uri = Uri.parse("content://test/out.json")
        val out = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(uri, out)
        picker.result = uri
        show()

        compose.onNodeWithTag("export").performClick()
        compose.waitUntil(5_000) { out.size() > 0 && runBlocking { repo.getMeta(MetaKey.LAST_EXPORT_AT) } != null }

        assertEquals(listOf<Any?>("forja-de-color-2026-11-11.json"), picker.inputs)
        val json = parseBackupText(out.toString(Charsets.UTF_8.name()))
        assertNull(validateBackup(json))
        val file = ForjaJson.decodeFromJsonElement(BackupFile.serializer(), json!!)
        assertEquals("forja-de-color", file.app)
        assertEquals(listOf("Bárbara"), file.projects.map { it.name })
        assertEquals(isoString(now), runBlocking { repo.getMeta(MetaKey.LAST_EXPORT_AT) })
    }

    @Test
    fun `says so when the export worked`() {
        val uri = Uri.parse("content://test/out.json")
        shadowOf(context.contentResolver).registerOutputStream(uri, ByteArrayOutputStream())
        picker.result = uri
        show()

        compose.onNodeWithTag("export").performClick()

        waitForText("Backup exported.")
    }

    @Test
    fun `cancelling the export picker records no export`() {
        show()

        compose.onNodeWithTag("export").performClick()
        compose.waitForIdle()

        assertEquals(1, picker.inputs.size)
        assertNull(runBlocking { repo.getMeta(MetaKey.LAST_EXPORT_AT) })
        compose.onNodeWithTag("backup_reminder").assertExists()
        compose.onNodeWithText("Backup exported.").assertDoesNotExist()
    }

    @Test
    fun `the reminder shows when never exported and hides right after an export`() {
        val uri = Uri.parse("content://test/out.json")
        shadowOf(context.contentResolver).registerOutputStream(uri, ByteArrayOutputStream())
        show()
        compose.onNodeWithText("You have not exported a backup for a while. Export one so you do not lose your projects.").assertExists()

        picker.result = uri
        compose.onNodeWithTag("export").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("backup_reminder").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `the reminder is hidden after a recent export`() {
        runBlocking { repo.setMeta(MetaKey.LAST_EXPORT_AT, "2026-11-10T10:00:00Z") }
        show()

        compose.onNodeWithTag("backup_reminder").assertDoesNotExist()
    }

    @Test
    fun `importing the PWA backup after confirming replaces all the data`() {
        val text = File("../domain/src/test/resources/pwa/backup-v1.json").readText()
        val backup = ForjaJson.decodeFromJsonElement(BackupFile.serializer(), parseBackupText(text)!!)
        pickToRead(text)
        show()

        compose.onNodeWithTag("import").performClick()
        waitForText("Importing replaces ALL your current projects, photos and paints with the backup. Continue?")
        compose.onNodeWithTag("import_confirm").performClick()
        waitForText("Backup imported.")

        assertEquals(backup.projects, runBlocking { repo.listProjects() })
        assertEquals(backup.inventory, runBlocking { repo.getInventory() })
        backup.blobs.keys.forEach { id -> assertTrue(runBlocking { repo.getBlob(id) } != null, "photo $id restored") }
    }

    @Test
    fun `importing an invalid file shows the error, asks nothing and changes nothing`() {
        pickToRead("""{"foo":1}""")
        show()

        compose.onNodeWithTag("import").performClick()
        waitForText("this file is not a Forja de Color backup")

        compose.onNodeWithTag("import_confirm").assertDoesNotExist()
        compose.onNodeWithText("Importing replaces", substring = true).assertDoesNotExist()
        assertEquals(listOf("Bárbara"), runBlocking { repo.listProjects() }.map { it.name })
    }

    @Test
    fun `importing a file over the size limit says it is too big, asks nothing and changes nothing`() {
        pickToRead("""{"app":"forja-de-color","version":1}""")
        show(maxBackupBytes = 10)

        compose.onNodeWithTag("import").performClick()
        waitForText("This file is too big to be a Forja de Color backup.")

        compose.onNodeWithTag("import_confirm").assertDoesNotExist()
        assertEquals(listOf("Bárbara"), runBlocking { repo.listProjects() }.map { it.name })
    }

    @Test
    fun `importing a file that is not JSON says it is not a backup file`() {
        pickToRead("not json")
        show()

        compose.onNodeWithTag("import").performClick()
        waitForText("not a backup file")

        assertEquals(listOf("Bárbara"), runBlocking { repo.listProjects() }.map { it.name })
    }

    @Test
    fun `cancelling the import confirmation changes nothing`() {
        pickToRead("""{"app":"forja-de-color","version":1,"exportedAt":"","inventory":{"paints":[],"brushes":[]},"projects":[],"blobs":{}}""")
        show()

        compose.onNodeWithTag("import").performClick()
        waitForText("Importing replaces", substring = true)
        compose.onNodeWithTag("import_cancel").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Importing replaces", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Backup imported.").assertDoesNotExist()
        assertEquals(listOf("Bárbara"), runBlocking { repo.listProjects() }.map { it.name })
        assertEquals(SEED_INVENTORY, runBlocking { repo.getInventory() })
    }

    @Test
    fun `Copy AI instructions puts the brain instructions on the clipboard`() {
        show()

        compose.onNodeWithTag("copy_brain").performClick()
        waitForText("Copied.")

        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
        assertEquals(buildBrainInstructions(SEED_INVENTORY), clip?.getItemAt(0)?.text?.toString())
    }
}
