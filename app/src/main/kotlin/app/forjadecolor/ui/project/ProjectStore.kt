package app.forjadecolor.ui.project

import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.storage.Repository
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
}
