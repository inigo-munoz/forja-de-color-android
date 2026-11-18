package app.forjadecolor.data

import app.forjadecolor.domain.paints.BslRole
import app.forjadecolor.domain.paints.PaintFamily
import app.forjadecolor.domain.projects.BaseSetting
import app.forjadecolor.domain.projects.Look
import app.forjadecolor.domain.projects.PaintingStyle
import app.forjadecolor.domain.projects.addAngle
import app.forjadecolor.domain.projects.setLook

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.forjadecolor.domain.ForjaJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals

/**
 * Builds a database exactly as version 1 left it (from the exported schema 1.json), writes rows the
 * v1 app would have written, then opens it with the current ForjaDatabase and its migrations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForjaMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    /** Creates the tables exactly as schema [version] left them; rows are added by [fill]. */
    private fun createAt(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val schema = ForjaJson.parseToJsonElement(File("schemas/app.forjadecolor.data.ForjaDatabase/$version.json").readText())
            .jsonObject["database"]!!.jsonObject
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null)
        for (entity in schema["entities"]!!.jsonArray.map { it.jsonObject }) {
            val table = entity["tableName"]!!.jsonPrimitive.content
            db.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            (entity["indices"] as? JsonArray)?.forEach { db.execSQL((it as JsonObject)["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { db.execSQL((it as JsonPrimitive).content) }
        fill(db)
        db.version = version
        db.close()
    }

    private fun createV1() = createAt(1) { db ->
        db.execSQL("INSERT INTO projects VALUES ('p1', 'Paladín', '2026-11-10T09:00:00.000Z', 'IN_PROGRESS', 'i', NULL, 'Capa verde')")
        db.execSQL("INSERT INTO progress_photos VALUES ('p1', 0, 'pp-1', '2026-11-11T10:00:00.000Z', 'pp')")
        db.execSQL(
            "INSERT INTO advice_entries VALUES ('p1', 0, 'adv-1', '2026-11-10T09:30:00.000Z', ?, ?, ?)",
            arrayOf(RESPONSE, """[{"brand":"Citadel","code":"Abaddon Black"}]""", """["X999"]"""),
        )
        db.execSQL("INSERT INTO blobs VALUES ('i', 'image/jpeg', 'f1')")
        db.execSQL("INSERT INTO app_state VALUES ('lastExportAt', '2026-11-14T12:00:00.000Z')")
    }

    @Test
    fun `migrating from 1 keeps every row and reads advice back with no zones done`() = runBlocking {
        createV1()
        val db = ForjaDatabase.create(context, name)
        try {
            val dao = db.dao()
            assertEquals(
                listOf(ProjectEntity("p1", "Paladín", "2026-11-10T09:00:00.000Z", "IN_PROGRESS", "i", null, "Capa verde")),
                dao.projects(),
            )
            assertEquals(listOf(ProgressPhotoEntity("p1", 0, "pp-1", "2026-11-11T10:00:00.000Z", "pp")), dao.progress("p1"))
            assertEquals(
                listOf(AdviceEntity("p1", 0, "adv-1", "2026-11-10T09:30:00.000Z", RESPONSE, """[{"brand":"Citadel","code":"Abaddon Black"}]""", """["X999"]""", "[]")),
                dao.advice("p1"),
            )
            assertEquals(BlobEntity("i", "image/jpeg", "f1"), dao.blob("i"))
            assertEquals("2026-11-14T12:00:00.000Z", dao.state("lastExportAt"))

            val advice = RoomRepository(db, PhotoFiles(File(context.filesDir, "photos-migration"))).getProject("p1")!!.advice.single()
            assertEquals(emptyList(), advice.doneZones)
            assertEquals(listOf("X999"), advice.unknownBrushes)
            assertEquals(5, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun `migrating from 2 keeps every project, painted with my method until a style is chosen`() = runBlocking {
        createAt(2) { db ->
            db.execSQL("INSERT INTO projects VALUES ('p1', 'Paladín', '2026-11-10T09:00:00.000Z', 'DONE', NULL, 't', 'Capa verde')")
            db.execSQL("INSERT INTO advice_entries VALUES ('p1', 0, 'adv-1', '2026-11-10T09:30:00.000Z', ?, '[]', '[]', '[0]')", arrayOf(RESPONSE))
        }
        val db = ForjaDatabase.create(context, name)
        try {
            assertEquals(5, db.openHelper.readableDatabase.version)
            val repo = RoomRepository(db, PhotoFiles(File(context.filesDir, "photos-migration")))
            val p = repo.getProject("p1")!!
            assertEquals(listOf("Paladín", "t", "Capa verde"), listOf(p.name, p.targetImageBlobId, p.notes))
            assertEquals(listOf(0), p.advice.single().doneZones)
            assertEquals(Look(PaintingStyle.MY_METHOD), p.look)

            repo.saveProject(p.setLook(Look(PaintingStyle.GRIMDARK, BaseSetting.WINTER)))
            assertEquals(Look(PaintingStyle.GRIMDARK, BaseSetting.WINTER), repo.getProject("p1")!!.look)
        } finally {
            db.close()
        }
    }

    @Test
    fun `migrating 3 to 4 keeps every paint, with no English name until one is saved`() = runBlocking {
        createAt(3) { db ->
            db.execSQL("INSERT INTO paints VALUES (0, 'Vallejo', '72.001', 'Blanco Calavera', 'Game Color', 'OPAQUE', '#ffffff', 'BSL Black', 1, 'FOURTH')")
            db.execSQL("INSERT INTO paints VALUES (1, 'Citadel', 'Nuln Oil', 'Nuln Oil', 'Shade', 'WASH', '#1a1a1a', NULL, NULL, NULL)")
            db.execSQL("INSERT INTO app_state VALUES ('inventory.saved', '1')")
            db.execSQL("INSERT INTO projects VALUES ('p1', 'Paladín', '2026-11-10T09:00:00.000Z', 'DONE', NULL, NULL, 'Capa verde', 'NMM', 'WINTER')")
        }
        val db = ForjaDatabase.create(context, name)
        try {
            assertEquals(5, db.openHelper.readableDatabase.version)
            val repo = RoomRepository(db, PhotoFiles(File(context.filesDir, "photos-migration")))
            val inv = repo.getInventory()
            assertEquals(listOf("Blanco Calavera", "Nuln Oil"), inv.paints.map { it.name })
            assertEquals(listOf(null, null), inv.paints.map { it.nameEn })
            assertEquals(PaintFamily("BSL Black", 1, BslRole.FOURTH), inv.paints[0].family)
            assertEquals(Look(PaintingStyle.NMM, BaseSetting.WINTER), repo.getProject("p1")!!.look)

            repo.saveInventory(inv.copy(paints = inv.paints.map { if (it.code == "Nuln Oil") it.copy(nameEn = "Nuln Oil Shade") else it }))
            assertEquals(listOf(null, "Nuln Oil Shade"), repo.getInventory().paints.map { it.nameEn })
        } finally {
            db.close()
        }
    }

    @Test
    fun `paints survive from v1 and v2 with no English name`() = runBlocking {
        for (v in 1..2) {
            context.deleteDatabase(name)
            createAt(v) { db ->
                db.execSQL("INSERT INTO paints VALUES (0, 'Vallejo', '72.001', 'Blanco Calavera', 'Game Color', 'OPAQUE', '#ffffff', 'BSL Black', 1, 'FOURTH')")
                db.execSQL("INSERT INTO app_state VALUES ('inventory.saved', '1')")
            }
            val db = ForjaDatabase.create(context, name)
            try {
                val p = RoomRepository(db, PhotoFiles(File(context.filesDir, "photos-migration"))).getInventory().paints.single()
                assertEquals("Blanco Calavera" to null, p.name to p.nameEn, "from v$v")
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun `migrating 4 to 5 keeps every project with no extra angles, and angles are saved and read back`() = runBlocking {
        createAt(4) { db ->
            db.execSQL("INSERT INTO projects VALUES ('p1', 'Paladín', '2026-11-10T09:00:00.000Z', 'IN_PROGRESS', 'i', NULL, 'Capa verde', 'NMM', NULL)")
            db.execSQL("INSERT INTO blobs VALUES ('i', 'image/jpeg', 'f1')")
        }
        val db = ForjaDatabase.create(context, name)
        try {
            assertEquals(5, db.openHelper.readableDatabase.version)
            val repo = RoomRepository(db, PhotoFiles(File(context.filesDir, "photos-migration")))
            val p = repo.getProject("p1")!!
            assertEquals(listOf("Paladín", "i", "Capa verde"), listOf(p.name, p.initialPhotoBlobId, p.notes))
            assertEquals(PaintingStyle.NMM, p.style)
            assertEquals(emptyList(), p.extraPhotoBlobIds)

            repo.saveProject(p.addAngle("a1").addAngle("a2"))
            assertEquals(listOf("i", "a1", "a2"), repo.getProject("p1")!!.photos)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val RESPONSE =
            """{"version":1,"kind":"recipe","moment":"start","prep":["Imprimar"],"zones":[{"name":"capa","technique":"slapchop","paint":{"brand":"Vallejo","code":"72.021"},"dilution":"1:3","steps":["Aplicar"]}],"tips":[]}"""
    }
}
