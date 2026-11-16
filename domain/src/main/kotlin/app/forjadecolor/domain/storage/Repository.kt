package app.forjadecolor.domain.storage

import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.projects.Project

enum class MetaKey {
    LAST_EXPORT_AT,
    /** Fingerprint of the AI instructions last copied (see instructionsFingerprint). */
    AI_INSTRUCTIONS_COPIED,
}

/** A stored photo: its MIME type and bytes. */
class StoredBlob(val type: String, val bytes: ByteArray) {
    override fun equals(other: Any?) = other is StoredBlob && other.type == type && other.bytes.contentEquals(bytes)
    override fun hashCode() = 31 * type.hashCode() + bytes.contentHashCode()
}

/** Port: where the app keeps the inventory, projects, photos and small settings. */
interface Repository {
    suspend fun getInventory(): Inventory
    suspend fun saveInventory(inv: Inventory)
    suspend fun listProjects(): List<Project>
    suspend fun getProject(id: String): Project?
    suspend fun saveProject(p: Project)
    suspend fun deleteProject(id: String)
    /** Stores a blob; `id` is only passed when restoring a backup, so references stay valid. */
    suspend fun putBlob(blob: StoredBlob, id: String? = null): String
    suspend fun getBlob(id: String): StoredBlob?
    suspend fun deleteBlob(id: String)
    suspend fun getMeta(key: MetaKey): String?
    suspend fun setMeta(key: MetaKey, value: String)
    suspend fun clearAll()
    /** Replaces everything in a single step: either all of it is written or nothing changes. */
    suspend fun replaceAll(inventory: Inventory, projects: List<Project>, blobs: List<Pair<String, StoredBlob>>)
}
