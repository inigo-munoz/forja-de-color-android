package app.forjadecolor.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// Internal schema of forja.db (see ForjaDatabase for the version history). The backup JSON, not this schema, is the contract with the PWA.
// Inventory rows and project children are keyed by position: order matters and the PWA allows duplicates.

@Entity(tableName = "paints", primaryKeys = ["position"])
data class PaintEntity(
    val position: Int,
    val brand: String,
    val code: String,
    val name: String,
    val range: String,
    val type: String,
    val hex: String,
    val familyName: String?,
    val familyOrder: Int?,
    val familyRole: String?,
    /** English name (v4); null in older rows. */
    val nameEn: String? = null,
)

@Entity(tableName = "brushes", primaryKeys = ["position"])
data class BrushEntity(val position: Int, val code: String, val name: String)

@Entity(tableName = "projects", primaryKeys = ["id"])
data class ProjectEntity(
    val id: String,
    val name: String,
    val createdAt: String,
    val status: String,
    val initialPhotoBlobId: String?,
    val targetImageBlobId: String?,
    val notes: String,
    /** PaintingStyle name; null = my method (v3). */
    val style: String? = null,
    /** BaseSetting name; null = no setting (v3). */
    val baseSetting: String? = null,
    /** Extra angles, a JSON list of photo ids (v5). */
    @ColumnInfo(defaultValue = "[]") val extraPhotosJson: String = "[]",
)

@Entity(
    tableName = "progress_photos",
    primaryKeys = ["projectId", "position"],
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
)
data class ProgressPhotoEntity(val projectId: String, val position: Int, val id: String, val takenAt: String, val blobId: String)

@Entity(
    tableName = "advice_entries",
    primaryKeys = ["projectId", "position"],
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
)
data class AdviceEntity(
    val projectId: String,
    val position: Int,
    val id: String,
    val receivedAt: String,
    /** The AI response as JSON, in the backup's shape. */
    val responseJson: String,
    val unknownPaintsJson: String,
    val unknownBrushesJson: String,
    /** Indices of the recipe zones ticked off as painted, as a JSON array (added in v2; rows from v1 read as none). */
    @ColumnInfo(defaultValue = "[]")
    val doneZonesJson: String,
)

/** A photo id mapped to its MIME type and the file holding its bytes (a random name, never the id). */
@Entity(tableName = "blobs", primaryKeys = ["id"], indices = [Index("fileName", unique = true)])
data class BlobEntity(val id: String, val type: String, val fileName: String)

@Entity(tableName = "app_state", primaryKeys = ["key"])
data class StateEntity(val key: String, val value: String)
