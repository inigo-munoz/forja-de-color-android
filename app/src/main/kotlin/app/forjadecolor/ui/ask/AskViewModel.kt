package app.forjadecolor.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forjadecolor.AppContainer
import app.forjadecolor.domain.advice.AdviceRequest
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.advice.buildAdvicePackage
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.storage.StoredBlob
import app.forjadecolor.ui.project.onMain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What sending said: nothing, a photo is needed, or the share sheet failed and the prompt is shown to copy. */
enum class SendStatus { NONE, PHOTO_MISSING, FALLBACK }

data class AskState(
    val loaded: Boolean = false,
    val project: Project? = null,
    val sendStatus: SendStatus = SendStatus.NONE,
    /** The prompt to copy by hand, when the share sheet could not be used. */
    val prompt: String? = null,
    /** Why the last answer could not be saved. */
    val errors: List<String> = emptyList(),
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
        _state.update { it.copy(loaded = true, project = project) }
    }

    /** A new photo was picked or taken: it is stored on the next send. */
    fun photoChanged() {
        photoId = null
    }

    /** Sends [picked] (stored first), or else the project's latest stored photo, with [request]. */
    fun send(picked: ByteArray?, request: AdviceRequest) {
        val current = _state.value.project ?: return
        viewModelScope.launch {
            val id = when {
                picked != null -> photoId ?: repository.putBlob(StoredBlob("image/jpeg", picked)).also { photoId = it }
                else -> defaultPhotoId(current)?.takeIf { repository.getBlob(it) != null }
            }
            if (id == null) {
                _state.update { it.copy(sendStatus = SendStatus.PHOTO_MISSING) }
                return@launch
            }
            val pkg = buildAdvicePackage(request, repository.getInventory(), id)
            val fallback = container.advisorBridge.send(pkg) == SendResult.FALLBACK
            _state.update { it.copy(sendStatus = if (fallback) SendStatus.FALLBACK else SendStatus.NONE, prompt = if (fallback) pkg.text else null) }
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
