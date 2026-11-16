package app.forjadecolor.ui.settings

import android.net.Uri
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.domain.storage.BackupFile
import app.forjadecolor.domain.storage.encodeBackup
import app.forjadecolor.domain.paints.SEED_INVENTORY
import app.forjadecolor.platform.MAX_BACKUP_BYTES
import app.forjadecolor.ui.project.TestApp
import app.forjadecolor.ui.project.awaitOnMain
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {
    private val app = TestApp()
    private val resolver = app.context.contentResolver

    private fun file(text: String): Uri = Uri.parse("content://test/${text.hashCode()}.json").also {
        shadowOf(resolver).registerInputStream(it, ByteArrayInputStream(text.toByteArray()))
    }

    @Test
    fun `an invalid file shows the domain error and never waits for confirmation`() {
        val vm = SettingsViewModel(app.container, MAX_BACKUP_BYTES)

        vm.pickedForImport(resolver, file("""{"foo":1}"""))

        awaitOnMain { vm.state.value.status != null }
        assertEquals(BackupStatus.Error("this file is not a Forja de Color backup"), vm.state.value.status)
        assertNull(vm.state.value.pendingImport)
    }

    @Test
    fun `a valid backup waits for confirmation, then replaces the data and says it was imported`() {
        runBlocking { app.repository.saveProject(createProject("Bárbara", app.now, "p1")) }
        val backup = BackupFile(exportedAt = "2026-11-10T10:00:00Z", inventory = SEED_INVENTORY, projects = emptyList(), blobs = emptyMap())
        val vm = SettingsViewModel(app.container, MAX_BACKUP_BYTES)

        vm.pickedForImport(resolver, file(encodeBackup(backup)))
        awaitOnMain { vm.state.value.pendingImport != null }
        assertNotNull(vm.state.value.pendingImport)
        assertEquals(1, app.projects().size)

        vm.confirmImport()

        awaitOnMain { vm.state.value.status == BackupStatus.Imported }
        assertNull(vm.state.value.pendingImport)
        assertEquals(emptyList(), app.projects())
    }
}
