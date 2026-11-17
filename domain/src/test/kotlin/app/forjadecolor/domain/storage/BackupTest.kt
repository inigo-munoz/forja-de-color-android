package app.forjadecolor.domain.storage

import app.forjadecolor.domain.projects.BaseSetting
import app.forjadecolor.domain.projects.Look
import app.forjadecolor.domain.projects.PaintingStyle
import app.forjadecolor.domain.projects.setLook

import app.forjadecolor.domain.ForjaJson
import app.forjadecolor.domain.advice.ConsultationResponse
import app.forjadecolor.domain.advice.RecipeMoment
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.projects.ProjectStatus
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.domain.projects.setZoneDone
import app.forjadecolor.domain.pwaFixture
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTest {
    private val now = Instant.parse("2026-11-11T10:00:00Z")
    private fun daysAgo(d: Long) = now.minusSeconds(d * 86_400).toString()

    private suspend fun populated(): InMemoryRepository {
        val repo = InMemoryRepository()
        val blobId = repo.putBlob(StoredBlob("image/jpeg", byteArrayOf(1, 2, 3, 250.toByte())))
        repo.saveProject(createProject("Bárbara", now, "p1").copy(initialPhotoBlobId = blobId, notes = "capa roja"))
        val inv = repo.getInventory()
        repo.saveInventory(inv.copy(brushes = inv.brushes.take(1)))
        return repo
    }

    private fun json(text: String): JsonElement = ForjaJson.parseToJsonElement(text)

    // Contract with the PWA (S2)
    @Test
    fun `imports a backup exported by the PWA with every field intact`() = runBlocking {
        val repo = populated()
        assertEquals(ImportResult.Ok, importAll(repo, parseBackupText(pwaFixture("backup-v1.json"))))

        val projects = repo.listProjects()
        assertEquals(listOf("project-1", "project-2"), projects.map { it.id })
        val paladin = projects[0]
        assertEquals("Capa verde", paladin.notes)
        assertEquals("photo-initial", paladin.initialPhotoBlobId)
        assertEquals("photo-target", paladin.targetImageBlobId)
        assertEquals(listOf("pp-1"), paladin.progress.map { it.id })
        assertIs<RecipeResponse>(paladin.advice[0].response)
        assertEquals(RecipeMoment.PRIMED, assertIs<RecipeResponse>(paladin.advice[2].response).moment)
        assertEquals(emptyList(), (paladin.advice[2].response as RecipeResponse).prep)
        assertEquals(listOf("X999"), assertIs<ConsultationResponse>(paladin.advice[1].response).let { paladin.advice[1].unknownBrushes })
        assertEquals(ProjectStatus.DONE, projects[1].status)
        assertEquals(4, repo.getInventory().paints.size)

        val initial = repo.getBlob("photo-initial")!!
        assertEquals("image/jpeg", initial.type)
        assertContentEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x00, 0x01, 0xfe.toByte()), initial.bytes)
        assertEquals("image/png", repo.getBlob("photo-target")!!.type)
    }

    @Test
    fun `re-exports a PWA backup as the same JSON the PWA wrote`() = runBlocking {
        val repo = InMemoryRepository()
        importAll(repo, parseBackupText(pwaFixture("backup-v1.json")))
        val exported = exportAll(repo, Instant.parse("2026-11-14T12:00:00Z"))
        assertEquals(json(pwaFixture("backup-v1.json")), json(encodeBackup(exported)))
    }

    // Behaviour carried over from the PWA
    @Test
    fun `round-trips projects, inventory and blob bytes`() = runBlocking {
        val repo = populated()
        val text = encodeBackup(exportAll(repo, now))
        val before = repo.listProjects() to repo.getInventory()
        repo.clearAll()
        assertEquals(ImportResult.Ok, importAll(repo, parseBackupText(text)))
        assertEquals(before, repo.listProjects() to repo.getInventory())
        val blob = repo.getBlob("blob-1")!!
        assertEquals("image/jpeg", blob.type)
        assertContentEquals(byteArrayOf(1, 2, 3, 250.toByte()), blob.bytes)
    }

    @Test
    fun `rejects foreign, newer or unreadable files and leaves existing data untouched`() = runBlocking {
        val empty = """"inventory":{"paints":[],"brushes":[]},"projects":[],"blobs":{}"""
        val cases = mapOf(
            """{"foo":1}""" to "this file is not a Forja de Color backup",
            """{"app":"other","version":1,"exportedAt":"",$empty}""" to "this file is not a Forja de Color backup",
            """{"app":"forja-de-color","version":2,"exportedAt":"",$empty}""" to "unsupported backup version: 2",
            "not json at all" to "not a backup file",
        )
        for ((text, message) in cases) {
            val repo = populated()
            val before = repo.listProjects() to repo.getInventory()
            assertEquals(ImportResult.Failed(message), importAll(repo, parseBackupText(text)))
            assertEquals(before, repo.listProjects() to repo.getInventory())
        }
    }

    @Test
    fun `deep validation rejects incomplete paints, brushes and projects`() = runBlocking {
        fun file(over: String) = """{"app":"forja-de-color","version":1,"exportedAt":"","inventory":{"paints":[],"brushes":[]},"projects":[],"blobs":{},$over}"""
        val cases = mapOf(
            """"inventory":{"paints":[{"code":"72.003","name":"x"}],"brushes":[]}""" to "paint #1 needs brand, code, name, range, type and hex",
            """"projects":[{"id":"p","name":"x","createdAt":"","status":"in-progress","notes":"","advice":[]}]""" to "project #1 is incomplete",
            """"inventory":{"paints":[],"brushes":[{"name":"x"}]}""" to "brush #1 needs code and name",
            """"blobs":{"a":{"type":"image/jpeg","base64":"%%%"}}""" to "backup has a damaged photo",
        )
        for ((over, message) in cases) {
            val repo = populated()
            val before = repo.listProjects()
            assertEquals(ImportResult.Failed(message), importAll(repo, parseBackupText(file(over))))
            assertEquals(before, repo.listProjects())
        }
    }

    @Test
    fun `restores in one step, so a failure mid-restore leaves the old data intact`() = runBlocking {
        val repo = populated()
        val before = repo.listProjects()
        val good = parseBackupText(encodeBackup(exportAll(repo, now)))
        repo.failReplaceAll = true
        val result = importAll(repo, good)
        assertEquals(ImportResult.Failed("could not restore the backup: disk full"), result)
        assertEquals(before, repo.listProjects())
    }

    @Test
    fun `records when the export happened`() = runBlocking {
        val repo = populated()
        exportAll(repo, now)
        assertEquals("2026-11-11T10:00:00.000Z", repo.getMeta(MetaKey.LAST_EXPORT_AT))
    }

    @Test
    fun `reminds when never exported, not after 3 days, yes after 8 days`() {
        assertTrue(shouldRemindBackup(null, now))
        assertEquals(false, shouldRemindBackup(daysAgo(3), now))
        assertTrue(shouldRemindBackup(daysAgo(8), now))
    }

    @Test
    fun `a valid file passes validation before the user confirms`() {
        assertNull(validateBackup(parseBackupText(pwaFixture("backup-v1.json"))))
    }

    @Test
    fun `names the file after the export day`() = runBlocking {
        assertEquals("forja-de-color-2026-11-11.json", backupFileName(exportAll(populated(), now)))
    }

    // Painting styles: optional, same validation message as the PWA
    private fun withLook(look: String) =
        """{"app":"forja-de-color","version":1,"exportedAt":"","inventory":{"paints":[],"brushes":[]},"blobs":{},""" +
            """"projects":[{"id":"p1","name":"B","createdAt":"","status":"in-progress","notes":"","progress":[],"advice":[]$look}]}"""

    @Test
    fun `accepts projects without a style and with a known style and base setting`() {
        assertNull(validateBackup(parseBackupText(withLook(""))))
        assertNull(validateBackup(parseBackupText(withLook(",\"style\":\"osl\",\"baseSetting\":\"desert\""))))
    }

    @Test
    fun `rejects an unknown style or base setting with the PWA's message`() {
        listOf(",\"style\":\"cel-shading\"", ",\"style\":3", ",\"baseSetting\":\"jungle\"").forEach {
            assertEquals("project #1 has an unknown painting style or base setting", validateBackup(parseBackupText(withLook(it))), it)
        }
    }

    @Test
    fun `a null or wrong-case style is rejected like in the PWA`() {
        listOf(",\"style\":null", ",\"baseSetting\":null", ",\"style\":\"NMM\"", ",\"baseSetting\":\"\"").forEach {
            assertEquals("project #1 has an unknown painting style or base setting", validateBackup(parseBackupText(withLook(it))), it)
        }
    }

    @Test
    fun `a backup rejected for its style leaves the data unchanged`() = runBlocking {
        val repo = InMemoryRepository()
        repo.saveProject(createProject("Keep", now, "k1").setLook(Look(PaintingStyle.OSL, BaseSetting.WINTER)))
        val before = exportAll(repo, now)
        val result = importAll(repo, parseBackupText(withLook(",\"style\":\"cel\"")))
        assertEquals(ImportResult.Failed("project #1 has an unknown painting style or base setting"), result)
        assertEquals(before, exportAll(repo, now))
    }

    @Test
    fun `reads the style the PWA exported`() = runBlocking {
        val repo = InMemoryRepository()
        importAll(repo, parseBackupText(pwaFixture("backup-v1.json")))
        val goblin = repo.getProject("project-2")!!
        assertEquals(PaintingStyle.NMM, goblin.style)
        assertEquals(BaseSetting.DESERT, goblin.baseSetting)
        assertNull(repo.getProject("project-1")!!.style)
    }

    // Done zones (S11 item 3): optional, additive, same validation messages as the PWA
    private fun withDone(doneZones: String?): String {
        val zone = """{"name":"z","technique":"slapchop","paint":{"brand":"Vallejo","code":"72.021"},"dilution":"1:1","steps":[]}"""
        val done = doneZones?.let { ""","doneZones":$it""" } ?: ""
        val recipe = """{"id":"e1","receivedAt":"","unknownPaints":[],"unknownBrushes":[],"response":{"version":1,"kind":"recipe","moment":"start","prep":[],"tips":[],"zones":[$zone,$zone,$zone]}$done}"""
        val consult = """{"id":"c","receivedAt":"","unknownPaints":[],"unknownBrushes":[],"response":{"version":1,"kind":"consultation","zone":"z","question":"free","technique":"t","steps":[],"paints":[]}}"""
        return """{"app":"forja-de-color","version":1,"exportedAt":"","inventory":{"paints":[],"brushes":[]},"blobs":{},""" +
            """"projects":[{"id":"p1","name":"B","createdAt":"","status":"in-progress","notes":"","progress":[],"advice":[$consult,$recipe]}]}"""
    }

    @Test
    fun `accepts advice without doneZones and with a valid list`() {
        assertNull(validateBackup(parseBackupText(withDone(null))))
        assertNull(validateBackup(parseBackupText(withDone("[0,2]"))))
        assertNull(validateBackup(parseBackupText(withDone("[]"))))
    }

    @Test
    fun `rejects malformed doneZones with the PWA's message and keeps existing data`() = runBlocking {
        val message = "project #1, advice #2 has invalid done zones: each must be a different zone index below 3"
        for (bad in listOf("1", "[1.5]", "[\"1\"]", "[-1]", "[3]", "[1,1]", "[true]", "null")) {
            val repo = populated()
            val before = repo.listProjects()
            assertEquals(ImportResult.Failed(message), importAll(repo, parseBackupText(withDone(bad))), bad)
            assertEquals(before, repo.listProjects())
        }
    }

    @Test
    fun `rejects doneZones on an entry with no zones`() {
        val file = withDone(null).replace(""""steps":[],"paints":[]}}""", """"steps":[],"paints":[]},"doneZones":[0]}""")
        assertEquals("project #1, advice #1 has invalid done zones: each must be a different zone index below 0", validateBackup(parseBackupText(file)))
    }

    @Test
    fun `exports doneZones only when some zone is done, and round-trips it`() = runBlocking {
        val repo = InMemoryRepository()
        importAll(repo, parseBackupText(withDone(null)))
        assertFalse("doneZones" in encodeBackup(exportAll(repo, now)))

        repo.saveProject(repo.getProject("p1")!!.setZoneDone("e1", 1, true))
        val text = encodeBackup(exportAll(repo, now))
        assertTrue(""""doneZones":[1]""" in text)
        repo.clearAll()
        assertEquals(ImportResult.Ok, importAll(repo, parseBackupText(text)))
        assertEquals(listOf(1), repo.getProject("p1")!!.advice[1].doneZones)
    }

    @Test
    fun `reads the doneZones the PWA exported`() = runBlocking {
        val repo = InMemoryRepository()
        importAll(repo, parseBackupText(pwaFixture("backup-v1.json")))
        val advice = repo.listProjects()[0].advice
        assertEquals(listOf(0, 2), advice[0].doneZones)
        assertEquals(emptyList(), advice[2].doneZones)
    }
}
