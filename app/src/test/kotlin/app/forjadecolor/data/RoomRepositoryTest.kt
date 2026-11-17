package app.forjadecolor.data

import app.forjadecolor.domain.projects.Look
import app.forjadecolor.domain.projects.PaintingStyle

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.domain.ForjaJson
import app.forjadecolor.domain.advice.AdviceEntry
import app.forjadecolor.domain.advice.RecipeMoment
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.Technique
import app.forjadecolor.domain.advice.ZoneRecipe
import app.forjadecolor.domain.paints.BslRole
import app.forjadecolor.domain.paints.Brush
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintFamily
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.PaintType
import app.forjadecolor.domain.paints.SEED_INVENTORY
import app.forjadecolor.domain.projects.ProgressPhoto
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.domain.storage.ImportResult
import app.forjadecolor.domain.storage.MetaKey
import app.forjadecolor.domain.storage.StoredBlob
import app.forjadecolor.domain.storage.encodeBackup
import app.forjadecolor.domain.storage.exportAll
import app.forjadecolor.domain.storage.importAll
import app.forjadecolor.domain.storage.parseBackupText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ForjaDatabase
    private lateinit var photoDir: File
    private lateinit var repo: RoomRepository
    private val now = Instant.parse("2026-11-11T10:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ForjaDatabase::class.java).allowMainThreadQueries().build()
        photoDir = File(context.filesDir, "photos-test").apply { deleteRecursively() }
        repo = RoomRepository(db, PhotoFiles(photoDir))
    }

    @After
    fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
    }

    private fun photoFiles() = photoDir.listFiles().orEmpty().map { it.name }.toSet()

    @Test
    fun `serves the seed inventory until one is saved, then keeps order, families and roles`() = runBlocking {
        assertEquals(SEED_INVENTORY, repo.getInventory())
        val inv = Inventory(
            paints = listOf(
                Paint("Vallejo", "72.051", "Negro", "Game Color", PaintType.OPAQUE, "#000000", PaintFamily("BSL Black", 4, BslRole.SHADOW)),
                Paint("Citadel", "Nuln Oil", "Nuln Oil", "Shade", PaintType.WASH, "#222222"),
            ),
            brushes = listOf(Brush("B07001", "Dry Brush Small"), Brush("B02001", "Detail núm. 1")),
        )
        repo.saveInventory(inv)
        assertEquals(inv, repo.getInventory())
        repo.saveInventory(Inventory(emptyList(), emptyList()))
        assertEquals(Inventory(emptyList(), emptyList()), repo.getInventory())
    }

    @Test
    fun `saves, lists by id, reads and deletes projects with their photos and advice`() = runBlocking {
        val advice = AdviceEntry(
            "a1", "2026-11-11T10:05:00.000Z",
            RecipeResponse(
                moment = RecipeMoment.START, prep = listOf("Imprimar"), tips = emptyList(),
                zones = listOf(ZoneRecipe("capa", Technique.SLAPCHOP, paint = PaintRef("Vallejo", "72.021"), dilution = "1:3", steps = listOf("Aplicar"))),
            ),
            unknownPaints = listOf(PaintRef("Citadel", "Abaddon Black")), unknownBrushes = listOf("X999"),
        )
        val b = createProject("Bárbara", now, "p2").copy(
            initialPhotoBlobId = "i", targetImageBlobId = "t", notes = "capa roja",
            progress = listOf(ProgressPhoto("x", "2026-11-12T00:00:00.000Z", "px"), ProgressPhoto("y", "2026-11-13T00:00:00.000Z", "py")),
            advice = listOf(advice, advice.copy(id = "a2")),
        )
        val a = createProject("Ana", now, "p1")
        repo.saveProject(b)
        repo.saveProject(a)
        assertEquals(listOf(a, b), repo.listProjects())
        assertEquals(b, repo.getProject("p2"))

        val edited = b.copy(notes = "otra", progress = b.progress.take(1), advice = emptyList())
        repo.saveProject(edited)
        assertEquals(edited, repo.getProject("p2"))

        repo.deleteProject("p2")
        assertNull(repo.getProject("p2"))
        assertEquals(listOf(a), repo.listProjects())
    }

    @Test
    fun `stores photo bytes and type in files whose names never come from the id`() = runBlocking {
        val id = repo.putBlob(StoredBlob("image/jpeg", byteArrayOf(1, 2, 3)))
        assertEquals(StoredBlob("image/jpeg", byteArrayOf(1, 2, 3)), repo.getBlob(id))

        repo.putBlob(StoredBlob("image/png", byteArrayOf(9)), "../../databases/evil")
        assertEquals(StoredBlob("image/png", byteArrayOf(9)), repo.getBlob("../../databases/evil"))
        assertTrue(photoFiles().none { it.contains("evil") || it.contains("..") })
        assertEquals(2, photoFiles().size)
    }

    @Test
    fun `replacing a photo by id or deleting it removes its old file`() = runBlocking {
        repo.putBlob(StoredBlob("image/jpeg", byteArrayOf(1)), "p")
        repo.putBlob(StoredBlob("image/jpeg", byteArrayOf(2)), "p")
        assertContentEquals(byteArrayOf(2), repo.getBlob("p")!!.bytes)
        assertEquals(1, photoFiles().size)
        repo.deleteBlob("p")
        assertNull(repo.getBlob("p"))
        assertEquals(emptySet(), photoFiles())
    }

    @Test
    fun `a photo whose file is missing reads as absent instead of failing`() = runBlocking {
        repo.putBlob(StoredBlob("image/jpeg", byteArrayOf(1)), "p")
        photoDir.listFiles()!!.forEach { it.delete() } // e.g. a cloud restore, which does not carry photos
        assertNull(repo.getBlob("p"))
    }

    @Test
    fun `keeps state and clears everything on request`() = runBlocking {
        repo.setMeta(MetaKey.LAST_EXPORT_AT, "2026-11-11T10:00:00.000Z")
        assertEquals("2026-11-11T10:00:00.000Z", repo.getMeta(MetaKey.LAST_EXPORT_AT))
        repo.saveProject(createProject("Ana", now, "p1"))
        repo.putBlob(StoredBlob("image/jpeg", byteArrayOf(1)), "p")
        repo.saveInventory(Inventory(emptyList(), emptyList()))
        repo.clearAll()
        assertNull(repo.getMeta(MetaKey.LAST_EXPORT_AT))
        assertEquals(emptyList(), repo.listProjects())
        assertNull(repo.getBlob("p"))
        assertEquals(SEED_INVENTORY, repo.getInventory())
        assertEquals(emptySet(), photoFiles())
    }

    @Test
    fun `replaceAll swaps every row and photo, keeping state`() = runBlocking {
        repo.saveProject(createProject("Ana", now, "old"))
        repo.putBlob(StoredBlob("image/jpeg", byteArrayOf(1)), "old-photo")
        repo.setMeta(MetaKey.LAST_EXPORT_AT, "2026-11-11T10:00:00.000Z")
        val inv = Inventory(SEED_INVENTORY.paints.take(1), emptyList())
        repo.replaceAll(inv, listOf(createProject("Nuevo", now, "new")), listOf("new-photo" to StoredBlob("image/png", byteArrayOf(7))))
        assertEquals(listOf("new"), repo.listProjects().map { it.id })
        assertEquals(inv, repo.getInventory())
        assertNull(repo.getBlob("old-photo"))
        assertEquals(StoredBlob("image/png", byteArrayOf(7)), repo.getBlob("new-photo"))
        assertEquals(1, photoFiles().size)
        assertEquals("2026-11-11T10:00:00.000Z", repo.getMeta(MetaKey.LAST_EXPORT_AT))
    }

    @Test
    fun `a replaceAll that fails midway leaves the old rows and photos and no new files`() = runBlocking {
        val old = createProject("Ana", now, "old")
        repo.saveProject(old)
        repo.putBlob(StoredBlob("image/jpeg", byteArrayOf(1)), "old-photo")
        val filesBefore = photoFiles()
        val dup = createProject("Dup", now, "same")
        assertFailsWith<Exception> {
            repo.replaceAll(SEED_INVENTORY, listOf(dup, dup), listOf("new-photo" to StoredBlob("image/png", byteArrayOf(7))))
        }
        assertEquals(listOf(old), repo.listProjects())
        assertEquals(StoredBlob("image/jpeg", byteArrayOf(1)), repo.getBlob("old-photo"))
        assertEquals(filesBefore, photoFiles())
    }

    @Test
    fun `removes photo files no row points to, as after a crash`() = runBlocking {
        repo.putBlob(StoredBlob("image/jpeg", byteArrayOf(1)), "kept")
        File(photoDir, "orphan").writeBytes(byteArrayOf(5))
        repo.removeOrphanPhotos()
        assertEquals(1, photoFiles().size)
        assertContentEquals(byteArrayOf(1), repo.getBlob("kept")!!.bytes)
    }

    @Test
    fun `imports the PWA backup into Room and exports the same JSON`() = runBlocking {
        val text = File("../domain/src/test/resources/pwa/backup-v1.json").readText()
        assertEquals(ImportResult.Ok, importAll(repo, parseBackupText(text)))
        val exported = encodeBackup(exportAll(repo, Instant.parse("2026-11-14T12:00:00Z")))
        assertEquals(ForjaJson.parseToJsonElement(text), ForjaJson.parseToJsonElement(exported))
    }

    @Test
    fun `keeps the zones ticked off on an advice entry`() = runBlocking {
        val zone = ZoneRecipe("capa", Technique.SLAPCHOP, paint = PaintRef("Vallejo", "72.021"), dilution = "1:3", steps = emptyList())
        val entry = AdviceEntry(
            "a1", "2026-11-11T10:05:00.000Z",
            RecipeResponse(moment = RecipeMoment.START, prep = emptyList(), tips = emptyList(), zones = listOf(zone, zone, zone)),
            emptyList(), emptyList(), doneZones = listOf(0, 2),
        )
        val p = createProject("Ana", now, "p1").copy(advice = listOf(entry, entry.copy(id = "a2", doneZones = emptyList())))
        repo.saveProject(p)
        assertEquals(p, repo.getProject("p1"))
    }

    @Test
    fun `an unknown style name in Room reads as my method instead of failing`() = runBlocking {
        db.dao().insertProject(ProjectEntity("p", "N", "2026-11-11T10:00:00.000Z", "IN_PROGRESS", null, null, "", "HOLOGRAPHIC", "SWAMP"))
        assertEquals(Look(PaintingStyle.MY_METHOD, null), repo.getProject("p")!!.look)
    }
}
