package app.forjadecolor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ForjaDao {
    @Query("SELECT * FROM paints ORDER BY position") suspend fun paints(): List<PaintEntity>
    @Query("SELECT * FROM brushes ORDER BY position") suspend fun brushes(): List<BrushEntity>
    @Insert suspend fun insertPaints(rows: List<PaintEntity>)
    @Insert suspend fun insertBrushes(rows: List<BrushEntity>)
    @Query("DELETE FROM paints") suspend fun deletePaints()
    @Query("DELETE FROM brushes") suspend fun deleteBrushes()

    @Query("SELECT * FROM projects ORDER BY id") suspend fun projects(): List<ProjectEntity>
    @Query("SELECT * FROM projects WHERE id = :id") suspend fun project(id: String): ProjectEntity?
    @Insert suspend fun insertProject(row: ProjectEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProject(row: ProjectEntity)
    @Query("DELETE FROM projects WHERE id = :id") suspend fun deleteProject(id: String)
    @Query("DELETE FROM projects") suspend fun deleteProjects()

    @Query("SELECT * FROM progress_photos WHERE projectId = :projectId ORDER BY position") suspend fun progress(projectId: String): List<ProgressPhotoEntity>
    @Insert suspend fun insertProgress(rows: List<ProgressPhotoEntity>)
    @Query("DELETE FROM progress_photos WHERE projectId = :projectId") suspend fun deleteProgress(projectId: String)

    @Query("SELECT * FROM advice_entries WHERE projectId = :projectId ORDER BY position") suspend fun advice(projectId: String): List<AdviceEntity>
    @Insert suspend fun insertAdvice(rows: List<AdviceEntity>)
    @Query("DELETE FROM advice_entries WHERE projectId = :projectId") suspend fun deleteAdvice(projectId: String)

    @Query("SELECT * FROM blobs WHERE id = :id") suspend fun blob(id: String): BlobEntity?
    @Query("SELECT fileName FROM blobs") suspend fun blobFileNames(): List<String>
    @Insert suspend fun insertBlobs(rows: List<BlobEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBlob(row: BlobEntity)
    @Query("DELETE FROM blobs WHERE id = :id") suspend fun deleteBlob(id: String)
    @Query("DELETE FROM blobs") suspend fun deleteBlobs()

    @Query("SELECT value FROM app_state WHERE `key` = :key") suspend fun state(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setState(row: StateEntity)
    @Query("DELETE FROM app_state") suspend fun deleteState()
}
