package app.forjadecolor.data

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

    private fun createV1() {
        val schema = ForjaJson.parseToJsonElement(File("schemas/app.forjadecolor.data.ForjaDatabase/1.json").readText())
            .jsonObject["database"]!!.jsonObject
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile!!.mkdirs() }, null)
        for (entity in schema["entities"]!!.jsonArray.map { it.jsonObject }) {
            val table = entity["tableName"]!!.jsonPrimitive.content
            db.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            (entity["indices"] as? JsonArray)?.forEach { db.execSQL((it as JsonObject)["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        schema["setupQueries"]!!.jsonArray.forEach { db.execSQL((it as JsonPrimitive).content) }
        db.execSQL("INSERT INTO projects VALUES ('p1', 'Paladín', '2026-11-10T09:00:00.000Z', 'IN_PROGRESS', 'i', NULL, 'Capa verde')")
        db.execSQL("INSERT INTO progress_photos VALUES ('p1', 0, 'pp-1', '2026-11-11T10:00:00.000Z', 'pp')")
        db.execSQL(
            "INSERT INTO advice_entries VALUES ('p1', 0, 'adv-1', '2026-11-10T09:30:00.000Z', ?, ?, ?)",
            arrayOf(RESPONSE, """[{"brand":"Citadel","code":"Abaddon Black"}]""", """["X999"]"""),
        )
        db.execSQL("INSERT INTO blobs VALUES ('i', 'image/jpeg', 'f1')")
        db.execSQL("INSERT INTO app_state VALUES ('lastExportAt', '2026-11-14T12:00:00.000Z')")
        db.version = 1
        db.close()
    }

    @Test
    fun `migrating 1 to 2 keeps every row and reads advice back with no zones done`() = runBlocking {
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
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    private companion object {
        const val RESPONSE =
            """{"version":1,"kind":"recipe","moment":"start","prep":["Imprimar"],"zones":[{"name":"capa","technique":"slapchop","paint":{"brand":"Vallejo","code":"72.021"},"dilution":"1:3","steps":["Aplicar"]}],"tips":[]}"""
    }
}
