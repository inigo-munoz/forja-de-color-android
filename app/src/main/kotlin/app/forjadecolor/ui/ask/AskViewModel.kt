package app.forjadecolor.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forjadecolor.AppContainer
import app.forjadecolor.domain.advice.AdviceRequest
import app.forjadecolor.domain.advice.Moment
import app.forjadecolor.domain.advice.PreviousRecipe
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.InstructionsStatus
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.advice.buildAdvicePackage
import app.forjadecolor.domain.projects.Look
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.projects.setLook
import app.forjadecolor.domain.storage.StoredBlob
import app.forjadecolor.ui.components.aiInstructionsStatus
import app.forjadecolor.ui.components.copyAiInstructions
import app.forjadecolor.ui.project.onMain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What sending said: nothing, a photo is needed, or the share sheet failed and the prompt is shown to copy. */
/** SHARED: the share sheet opened; the instructions are on the clipboard, since Claude keeps only the photo. */
enum class SendStatus { NONE, PHOTO_MISSING, CURRENT_PHOTO_MISSING, SHARED, FALLBACK }

data class AskState(
    val loaded: Boolean = false,
    val project: Project? = null,
    val sendStatus: SendStatus = SendStatus.NONE,
    /** The prompt to copy by hand, when the share sheet could not be used. */
    val prompt: String? = null,
    /** Why the last answer could not be saved. */
    val errors: List<String> = emptyList(),
    /** Whether the AI instructions last copied are today's; null until loaded. */
    val instructions: InstructionsStatus? = null,
    /** The AI instructions were just copied from this screen. */
    val instructionsCopied: Boolean = false,
)

/** Sends the advice package to the user's AI app and saves the answer, for [AskScreen]. */
class AskViewModel(private val container: AppContainer, private val projectId: String) : ViewModel() {
    private val repository = container.repository
    private val _state = MutableStateFlow(AskState())
    val state: StateFlow<AskState> = _state.asStateFlow()

    /** An answer shared into the app for this project arrives ready to save; taken once. */
    val sharedAnswer: String? = container.sharedAnswers.take(projectId)

    // The picked photo once stored, so sending twice does not store it twice.
    private var photoId: String? = null

    suspend fun load() {
        val project = repository.getProject(projectId)
        val instructions = aiInstructionsStatus(repository)
        _state.update { it.copy(loaded = true, project = project, instructions = instructions) }
    }

    /** Copies the AI instructions with [copy] (the clipboard), as Settings does. */
    fun copyInstructions(copy: (String) -> Unit) {
        viewModelScope.launch {
            copyAiInstructions(repository, copy)
            _state.update { it.copy(instructions = InstructionsStatus.CURRENT, instructionsCopied = true) }
        }
    }

    /** A new photo was picked or taken: it is stored on the next send. */
    fun photoChanged() {
        photoId = null
    }

    /** Saves the project's painting style and base setting, read fresh so nothing else is lost. */
    fun setLook(look: Look) {
        val id = _state.value.project?.id ?: return
        viewModelScope.launch {
            val fresh = repository.getProject(id) ?: return@launch
            val updated = fresh.setLook(look)
            repository.saveProject(updated)
            _state.update { it.copy(project = updated) }
        }
    }

    /** Sends [picked] (stored first), or else the project's latest stored photo, with [request]. */
    fun send(picked: ByteArray?, request: AdviceRequest) {
        val current = _state.value.project ?: return
        viewModelScope.launch {
            val fresh = repository.getProject(current.id) ?: current
            // Half-painted needs the miniature as it is now: never the unpainted initial photo.
            if (request.moment == Moment.HALF_PAINTED && picked == null && fresh.progress.isEmpty()) {
                _state.update { it.copy(sendStatus = SendStatus.CURRENT_PHOTO_MISSING) }
                return@launch
            }
            // Start and primed see the unpainted miniature from every angle, while there are angles and no
            // progress photo yet; otherwise the photo sent is the one it always was (latest progress, else main).
            val unpainted = fresh.extraPhotoBlobIds.isNotEmpty() && fresh.progress.isEmpty()
            val angles = if (picked == null && unpainted && (request.moment == Moment.START || request.moment == Moment.PRIMED)) {
                fresh.photos.filter { repository.getBlob(it) != null }
            } else {
                emptyList()
            }
            val id = when {
                picked != null -> photoId ?: repository.putBlob(StoredBlob("image/jpeg", picked)).also { photoId = it }
                angles.isNotEmpty() -> angles.first()
                else -> defaultPhotoId(fresh)?.takeIf { repository.getBlob(it) != null }
            }
            if (id == null) {
                _state.update { it.copy(sendStatus = SendStatus.PHOTO_MISSING) }
                return@launch
            }
            // The project's look as saved now: it may have been changed on this screen.
            val look = fresh.look
            // Half-painted carries the latest recipe and the zones ticked as painted.
            val recipe = fresh.advice.lastOrNull { it.response is RecipeResponse }
            val previous = if (request.moment == Moment.HALF_PAINTED && recipe != null) PreviousRecipe(recipe.response as RecipeResponse, recipe.doneZones) else null
            val pkg = buildAdvicePackage(request.copy(style = look.style, baseSetting = look.baseSetting, previous = previous), repository.getInventory(), id)
                .copy(extraImageIds = angles.drop(1))
            val fallback = container.advisorBridge.send(pkg) == SendResult.FALLBACK
            _state.update { it.copy(sendStatus = if (fallback) SendStatus.FALLBACK else SendStatus.SHARED, prompt = if (fallback) pkg.text else null) }
        }
    }

    /** Saves [raw]; [onSaved] runs on the main thread, [onRejected] when it shows errors instead. */
    fun accept(raw: String, onSaved: () -> Unit, onRejected: () -> Unit) {
        viewModelScope.launch {
            val failed = saveAnswer(container, projectId, raw)
            if (failed == null) {
                onMain(onSaved)
            } else {
                onRejected()
                _state.update { it.copy(errors = failed) }
            }
        }
    }

    /** The user read the errors and closed them. */
    fun clearErrors() {
        _state.update { it.copy(errors = emptyList()) }
    }

    /** The answer on the clipboard, if any. */
    fun readAnswer(): String? = container.advisorBridge.readAnswer()
}
