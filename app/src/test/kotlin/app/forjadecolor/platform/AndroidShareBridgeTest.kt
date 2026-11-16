package app.forjadecolor.platform

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.domain.advice.AdvicePackage
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.storage.MetaKey
import app.forjadecolor.domain.storage.Repository
import app.forjadecolor.domain.storage.StoredBlob
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidShareBridgeTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val photo = byteArrayOf(1, 2, 3, 4)
    private val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @Before
    fun forgetFileProviderRoots() {
        // FileProvider caches its roots in a static map, but every Robolectric test gets a new data directory.
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { (it.get(null) as MutableMap<*, *>).clear() }
    }

    @Test
    fun `shares the photo and the prompt through the system chooser`() = runBlocking {
        val bridge = AndroidShareBridge(app, BlobsOnly(mapOf("p1" to StoredBlob("image/jpeg", photo))))

        val result = bridge.send(AdvicePackage("the prompt", "p1"))

        assertEquals(SendResult.SHARED, result)
        val chooser = assertNotNull(shadowOf(app).nextStartedActivity)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val share = assertNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("image/jpeg", share.type)
        assertEquals("the prompt", share.getStringExtra(Intent.EXTRA_TEXT))
        val stream = assertNotNull(IntentCompat.getParcelableExtra(share, Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals("content", stream.scheme)
        assertEquals("app.forjadecolor.files", stream.authority)
        assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(stream, share.clipData?.getItemAt(0)?.uri)
        assertContentEquals(photo, File(app.cacheDir, "share/forja-de-color.jpg").readBytes())
    }

    @Test
    fun `the prompt also lands on the clipboard and is read back as the answer`() = runBlocking {
        val bridge = AndroidShareBridge(app, BlobsOnly(mapOf("p1" to StoredBlob("image/jpeg", photo))))

        bridge.send(AdvicePackage("the prompt", "p1"))

        assertEquals("the prompt", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertEquals("the prompt", bridge.readAnswer())
    }

    @Test
    fun `a missing photo falls back after copying the prompt`() = runBlocking {
        val bridge = AndroidShareBridge(app, BlobsOnly(emptyMap()))

        val result = bridge.send(AdvicePackage("only text", "missing"))

        assertEquals(SendResult.FALLBACK, result)
        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals("only text", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `readAnswer is null when the clipboard is empty`() {
        assertNull(AndroidShareBridge(app, BlobsOnly(emptyMap())).readAnswer())
    }
}

/** Repository double that only serves photos; the bridge needs nothing else. */
private class BlobsOnly(private val blobs: Map<String, StoredBlob>) : Repository {
    override suspend fun getBlob(id: String) = blobs[id]
    override suspend fun getInventory(): Inventory = error("unused")
    override suspend fun saveInventory(inv: Inventory) = error("unused")
    override suspend fun listProjects(): List<Project> = error("unused")
    override suspend fun getProject(id: String): Project? = error("unused")
    override suspend fun saveProject(p: Project) = error("unused")
    override suspend fun deleteProject(id: String) = error("unused")
    override suspend fun putBlob(blob: StoredBlob, id: String?): String = error("unused")
    override suspend fun deleteBlob(id: String) = error("unused")
    override suspend fun getMeta(key: MetaKey): String? = error("unused")
    override suspend fun setMeta(key: MetaKey, value: String) = error("unused")
    override suspend fun clearAll() = error("unused")
    override suspend fun replaceAll(inventory: Inventory, projects: List<Project>, blobs: List<Pair<String, StoredBlob>>) =
        error("unused")
}
