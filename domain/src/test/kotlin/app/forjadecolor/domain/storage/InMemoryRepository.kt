package app.forjadecolor.domain.storage

import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.SEED_INVENTORY
import app.forjadecolor.domain.projects.Project

/** Test double for the Repository port. `failReplaceAll` simulates a restore that fails midway. */
class InMemoryRepository : Repository {
    var inventory: Inventory? = null
    val projects = linkedMapOf<String, Project>()
    val blobs = linkedMapOf<String, StoredBlob>()
    val meta = mutableMapOf<MetaKey, String>()
    var failReplaceAll = false
    private var next = 0

    override suspend fun getInventory() = inventory ?: SEED_INVENTORY
    override suspend fun saveInventory(inv: Inventory) { inventory = inv }
    override suspend fun listProjects() = projects.values.toList()
    override suspend fun getProject(id: String) = projects[id]
    override suspend fun saveProject(p: Project) { projects[p.id] = p }
    override suspend fun deleteProject(id: String) { projects.remove(id) }
    override suspend fun putBlob(blob: StoredBlob, id: String?): String = (id ?: "blob-${++next}").also { blobs[it] = blob }
    override suspend fun getBlob(id: String) = blobs[id]
    override suspend fun deleteBlob(id: String) { blobs.remove(id) }
    override suspend fun getMeta(key: MetaKey) = meta[key]
    override suspend fun setMeta(key: MetaKey, value: String) { meta[key] = value }
    override suspend fun clearAll() { inventory = null; projects.clear(); blobs.clear(); meta.clear() }
    override suspend fun replaceAll(inventory: Inventory, projects: List<Project>, blobs: List<Pair<String, StoredBlob>>) {
        if (failReplaceAll) error("disk full")
        this.inventory = inventory
        this.projects.clear(); projects.forEach { this.projects[it.id] = it }
        this.blobs.clear(); blobs.forEach { (id, b) -> this.blobs[id] = b }
    }
}
