package app.forjadecolor.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forjadecolor.AppContainer
import app.forjadecolor.domain.advice.AdvicePackage
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.advice.assignUnknownPaint
import app.forjadecolor.domain.advice.buildImagePrompt
import app.forjadecolor.domain.isoString
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.projects.ProgressPhoto
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.projects.ProjectStatus
import app.forjadecolor.domain.projects.addProgressPhoto
import app.forjadecolor.domain.projects.markDone
import app.forjadecolor.domain.projects.reopen
import app.forjadecolor.domain.projects.setNotes
import app.forjadecolor.domain.storage.StoredBlob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** What the project screen shows: nothing until [loaded], then the project (null when it no longer exists). */
data class ProjectState(
    val loaded: Boolean = false,
    val project: Project? = null,
    val inventory: Inventory = Inventory(emptyList(), emptyList()),
)

/** One project's data and every change made from its screen; all writes go through one [ProjectStore]. */
class ProjectViewModel(private val container: AppContainer, private val projectId: String) : ViewModel() {
    private val repository = container.repository
    private val store = ProjectStore(repository, projectId)
    private val _state = MutableStateFlow(ProjectState())
    val state: StateFlow<ProjectState> = _state.asStateFlow()

    /**
     * Loads the project afresh each time the screen is shown (it may have changed in the ask screen).
     * Returns true when an answer shared from the AI app waits for this project.
     */
    suspend fun load(): Boolean {
        _state.value = ProjectState()
        val inventory = repository.getInventory()
        _state.value = ProjectState(loaded = true, project = store.load(), inventory = inventory)
        return container.sharedAnswers.hasAnswerFor(projectId)
    }

    private fun show(project: Project) = _state.update { it.copy(project = project) }

    private fun update(change: suspend (Project) -> Project) {
        viewModelScope.launch { store.update(change)?.let(::show) }
    }

    private fun updateDroppingPhotos(change: suspend (Project) -> Pair<Project, List<String?>>) {
        viewModelScope.launch { store.updateDroppingPhotos(change)?.let(::show) }
    }

    fun toggleDone() = update { p -> if (p.status == ProjectStatus.DONE) p.reopen() else p.markDone() }

    fun deleteAdvice(entryId: String) = update { p -> p.copy(advice = p.advice.filter { it.id != entryId }) }

    fun assignPaint(entryId: String, unknown: PaintRef, owned: PaintRef) = update { p ->
        p.copy(advice = p.advice.map { if (it.id == entryId) assignUnknownPaint(it, unknown, owned) else it })
    }

    fun addProgressPhoto(bytes: ByteArray) = update { p ->
        val blobId = repository.putBlob(StoredBlob("image/jpeg", bytes))
        p.addProgressPhoto(ProgressPhoto(UUID.randomUUID().toString(), isoString(container.now()), blobId))
    }

    fun deleteProgressPhoto(photo: ProgressPhoto) = updateDroppingPhotos { p ->
        p.copy(progress = p.progress.filter { it.id != photo.id }) to listOf(photo.blobId)
    }

    /** Replacing the target deletes the old one, unless another project uses it. */
    fun setTarget(bytes: ByteArray) = updateDroppingPhotos { p ->
        p.copy(targetImageBlobId = repository.putBlob(StoredBlob("image/jpeg", bytes))) to listOf(p.targetImageBlobId)
    }

    fun removeTarget() = updateDroppingPhotos { p -> p.copy(targetImageBlobId = null) to listOf(p.targetImageBlobId) }

    /** Notes are written on the app scope: the last save runs as the screen closes, when its own scope is gone. */
    fun saveNotes(text: String) {
        container.appScope.launch { store.update { p -> p.setNotes(text) }?.let(::show) }
    }

    /** Deletes the project and the photos only it used, then [onDeleted] runs on the main thread. */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            store.delete()
            onMain(onDeleted)
        }
    }

    /** Sends the image prompt with the unpainted photo; null when there is no photo to paint. */
    suspend fun paintImage(recipe: RecipeResponse): SendResult? {
        val current = _state.value
        val photoId = current.project?.initialPhotoBlobId ?: return null
        if (repository.getBlob(photoId) == null) return null
        return container.advisorBridge.send(AdvicePackage(buildImagePrompt(recipe, current.inventory), photoId))
    }
}
