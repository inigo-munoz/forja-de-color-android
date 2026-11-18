package app.forjadecolor.data

import app.forjadecolor.domain.projects.BaseSetting
import app.forjadecolor.domain.projects.PaintingStyle

import androidx.room.withTransaction
import app.forjadecolor.domain.ForjaJson
import app.forjadecolor.domain.advice.AdviceEntry
import app.forjadecolor.domain.advice.AdviceResponse
import app.forjadecolor.domain.paints.BslRole
import app.forjadecolor.domain.paints.Brush
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintFamily
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.PaintType
import app.forjadecolor.domain.paints.SEED_INVENTORY
import app.forjadecolor.domain.projects.ProgressPhoto
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.projects.ProjectStatus
import app.forjadecolor.domain.storage.MetaKey
import app.forjadecolor.domain.storage.Repository
import app.forjadecolor.domain.storage.StoredBlob
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.util.UUID

private const val INVENTORY_SAVED = "inventory.saved"
private fun metaKey(key: MetaKey) = "meta.${key.name}"
private val REFS = ListSerializer(PaintRef.serializer())
private val STRINGS = ListSerializer(String.serializer())
private val INTS = ListSerializer(Int.serializer())

/** Repository port over Room (rows) and [PhotoFiles] (photo bytes). */
class RoomRepository(private val db: ForjaDatabase, private val photos: PhotoFiles) : Repository {
    private val dao = db.dao()

    override suspend fun getInventory(): Inventory {
        if (dao.state(INVENTORY_SAVED) == null) return SEED_INVENTORY
        return Inventory(dao.paints().map { it.toPaint() }, dao.brushes().map { Brush(it.code, it.name) })
    }

    override suspend fun saveInventory(inv: Inventory) = db.withTransaction { writeInventory(inv) }

    private suspend fun writeInventory(inv: Inventory) {
        dao.deletePaints()
        dao.deleteBrushes()
        dao.insertPaints(inv.paints.mapIndexed { i, p -> p.toEntity(i) })
        dao.insertBrushes(inv.brushes.mapIndexed { i, b -> BrushEntity(i, b.code, b.name) })
        dao.setState(StateEntity(INVENTORY_SAVED, "1"))
    }

    override suspend fun listProjects(): List<Project> = db.withTransaction { dao.projects().map { load(it) } }

    override suspend fun getProject(id: String): Project? = db.withTransaction { dao.project(id)?.let { load(it) } }

    override suspend fun saveProject(p: Project) = db.withTransaction {
        dao.upsertProject(p.toEntity())
        dao.deleteProgress(p.id)
        dao.deleteAdvice(p.id)
        insertChildren(p)
    }

    override suspend fun deleteProject(id: String) = dao.deleteProject(id)

    override suspend fun putBlob(blob: StoredBlob, id: String?): String {
        val blobId = id ?: UUID.randomUUID().toString()
        val fileName = photos.write(blob.bytes)
        val old = try {
            db.withTransaction { dao.blob(blobId).also { dao.upsertBlob(BlobEntity(blobId, blob.type, fileName)) } }
        } catch (e: Exception) {
            photos.delete(fileName)
            throw e
        }
        old?.let { photos.delete(it.fileName) }
        return blobId
    }

    override suspend fun getBlob(id: String): StoredBlob? {
        val row = dao.blob(id) ?: return null
        return photos.read(row.fileName)?.let { StoredBlob(row.type, it) }
    }

    override suspend fun deleteBlob(id: String) {
        val row = dao.blob(id) ?: return
        dao.deleteBlob(id)
        photos.delete(row.fileName)
    }

    override suspend fun getMeta(key: MetaKey): String? = dao.state(metaKey(key))

    override suspend fun setMeta(key: MetaKey, value: String) = dao.setState(StateEntity(metaKey(key), value))

    override suspend fun clearAll() {
        db.withTransaction {
            dao.deleteProjects()
            dao.deletePaints()
            dao.deleteBrushes()
            dao.deleteBlobs()
            dao.deleteState()
        }
        removeOrphanPhotos()
    }

    override suspend fun replaceAll(inventory: Inventory, projects: List<Project>, blobs: List<Pair<String, StoredBlob>>) {
        val written = mutableListOf<String>()
        try {
            val rows = blobs.map { (id, b) -> BlobEntity(id, b.type, photos.write(b.bytes).also { written += it }) }
            db.withTransaction {
                dao.deleteProjects()
                dao.deleteBlobs()
                writeInventory(inventory)
                for (p in projects) {
                    dao.insertProject(p.toEntity())
                    insertChildren(p)
                }
                dao.insertBlobs(rows)
            }
        } catch (e: Exception) {
            written.forEach { photos.delete(it) }
            throw e
        }
        removeOrphanPhotos()
    }

    /** Deletes photo files no row points to: the old ones after a restore, or leftovers from a crash. */
    suspend fun removeOrphanPhotos() {
        val referenced = dao.blobFileNames().toSet()
        (photos.names() - referenced).forEach { photos.delete(it) }
    }

    private suspend fun insertChildren(p: Project) {
        dao.insertProgress(p.progress.mapIndexed { i, x -> ProgressPhotoEntity(p.id, i, x.id, x.takenAt, x.blobId) })
        dao.insertAdvice(
            p.advice.mapIndexed { i, e ->
                AdviceEntity(
                    p.id, i, e.id, e.receivedAt,
                    ForjaJson.encodeToString(AdviceResponse.serializer(), e.response),
                    ForjaJson.encodeToString(REFS, e.unknownPaints),
                    ForjaJson.encodeToString(STRINGS, e.unknownBrushes),
                    ForjaJson.encodeToString(INTS, e.doneZones),
                )
            },
        )
    }

    private suspend fun load(row: ProjectEntity) = Project(
        id = row.id,
        name = row.name,
        createdAt = row.createdAt,
        status = ProjectStatus.valueOf(row.status),
        initialPhotoBlobId = row.initialPhotoBlobId,
        targetImageBlobId = row.targetImageBlobId,
        notes = row.notes,
        // An unknown name (written by a newer app) reads as no choice rather than failing.
        style = PaintingStyle.entries.firstOrNull { it.name == row.style },
        baseSetting = BaseSetting.entries.firstOrNull { it.name == row.baseSetting },
        extraPhotoBlobIds = ForjaJson.decodeFromString(STRINGS, row.extraPhotosJson),
        baseMotif = row.baseMotif,
        progress = dao.progress(row.id).map { ProgressPhoto(it.id, it.takenAt, it.blobId) },
        advice = dao.advice(row.id).map {
            AdviceEntry(
                it.id, it.receivedAt,
                ForjaJson.decodeFromString(AdviceResponse.serializer(), it.responseJson),
                ForjaJson.decodeFromString(REFS, it.unknownPaintsJson),
                ForjaJson.decodeFromString(STRINGS, it.unknownBrushesJson),
                ForjaJson.decodeFromString(INTS, it.doneZonesJson),
            )
        },
    )
}

private fun Project.toEntity() =
    ProjectEntity(
        id, name, createdAt, status.name, initialPhotoBlobId, targetImageBlobId, notes, style?.name, baseSetting?.name,
        ForjaJson.encodeToString(STRINGS, extraPhotoBlobIds), baseMotif,
    )

private fun Paint.toEntity(position: Int) =
    PaintEntity(position, brand, code, name, range, type.name, hex, family?.name, family?.order, family?.role?.name, nameEn)

private fun PaintEntity.toPaint() = Paint(
    brand, code, name, range, PaintType.valueOf(type), hex,
    familyName?.let { PaintFamily(it, familyOrder ?: 0, familyRole?.let(BslRole::valueOf)) },
    nameEn = nameEn,
)
