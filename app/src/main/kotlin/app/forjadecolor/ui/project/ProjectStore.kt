package app.forjadecolor.ui.project

import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.storage.Repository
import app.forjadecolor.domain.storage.deletePhotosIfUnused
import app.forjadecolor.domain.storage.deleteProjectWithPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Runs a navigation callback on the main thread, whatever thread the storage call resumed on. */
suspend fun onMain(action: () -> Unit) = withContext(Dispatchers.Main.immediate) { action() }

/**
 * All writes from one project screen go through here, one at a time, and each re-reads the stored
 * project first (like the PWA's write queue): a notes save and a "Mark as done" fired back to back
 * cannot both start from the same old copy and lose one of the two changes.
 */
class ProjectStore(private val repository: Repository, private val projectId: String) {
    private val mutex = Mutex()

    suspend fun load(): Project? = repository.getProject(projectId)

    /** Applies [change] to the latest stored project and saves it; null when the project no longer exists. */
    suspend fun update(change: suspend (Project) -> Project): Project? = mutex.withLock {
        val latest = repository.getProject(projectId) ?: return@withLock null
        change(latest).also { repository.saveProject(it) }
    }

    /**
     * Like [update], then deletes the photos [change] dropped (unless another project uses them), once
     * the change is saved: a replaced or removed photo must not keep filling storage and backups.
     */
    suspend fun updateDroppingPhotos(change: suspend (Project) -> Pair<Project, List<String?>>): Project? = mutex.withLock {
        val latest = repository.getProject(projectId) ?: return@withLock null
        val (next, dropped) = change(latest)
        repository.saveProject(next)
        repository.deletePhotosIfUnused(dropped)
        next
    }

    /** Deletes the project and the photos only it used, in turn with the writes above. */
    suspend fun delete() = mutex.withLock { repository.deleteProjectWithPhotos(projectId) }
}
