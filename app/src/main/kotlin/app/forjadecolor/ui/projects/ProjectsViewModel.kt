package app.forjadecolor.ui.projects

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forjadecolor.AppContainer
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.platform.IncomingShare
import app.forjadecolor.ui.project.onMain
import app.forjadecolor.ui.share.routeShare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The project list, creating a project and routing what other apps shared, for [ProjectsScreen]. */
class ProjectsViewModel(private val container: AppContainer) : ViewModel() {
    private val _projects = MutableStateFlow<List<Project>?>(null)

    /** Null until loaded; ordered by [orderProjects]. */
    val projects: StateFlow<List<Project>?> = _projects.asStateFlow()

    /** Something shared into the app, waiting for the user to pick its project. */
    val share: StateFlow<IncomingShare?> = container.incomingShares.current

    private val _shareError = MutableStateFlow(false)
    val shareError: StateFlow<Boolean> = _shareError.asStateFlow()

    private var creating = false

    /**
     * Starts the screen afresh each time it is shown (this ViewModel outlives it while a project is
     * open): projects change elsewhere, and a new project can be created again.
     */
    suspend fun load() {
        creating = false
        _shareError.value = false
        _projects.value = null
        _projects.value = orderProjects(container.repository.listProjects())
    }

    /** Hands the waiting share to [project], then opens it; a share that cannot be read says so instead. */
    fun routeShareTo(context: Context, project: Project, onOpen: (projectId: String) -> Unit) {
        viewModelScope.launch {
            val taken = container.incomingShares.consume() ?: return@launch
            val ok = runCatching { routeShare(container, context, taken, project.id) }.isSuccess
            _shareError.value = !ok
            if (ok) onMain { onOpen(project.id) }
        }
    }

    fun dismissShare() {
        container.incomingShares.consume()
    }

    /** One tap, one project: a second tap while saving is ignored. */
    fun create(name: String, photos: List<ByteArray>, onCreated: (projectId: String) -> Unit) {
        if (creating) return
        creating = true
        viewModelScope.launch {
            val id = createAndSaveProject(container, name, photos).id
            onMain { onCreated(id) }
        }
    }
}
