package app.forjadecolor.ui.ask

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.advice.AdviceRequest
import app.forjadecolor.domain.advice.Moment
import app.forjadecolor.domain.advice.ParseResult
import app.forjadecolor.domain.advice.Question
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.advice.buildAdvicePackage
import app.forjadecolor.domain.advice.parseAdviceResponse
import app.forjadecolor.domain.isoString
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.projects.addAdvice
import app.forjadecolor.domain.storage.StoredBlob
import app.forjadecolor.ui.components.PhotoPreview
import app.forjadecolor.ui.project.LocalPhotoSources
import app.forjadecolor.ui.project.onMain
import kotlinx.coroutines.launch
import java.util.UUID

/** Zone names from the project's recipes, each once, for the "while painting" question. */
fun recipeZones(p: Project): List<String> =
    p.advice.flatMap { e -> (e.response as? RecipeResponse)?.zones?.map { it.name }.orEmpty() }.distinct()

/** What to ask: only "while painting" carries a zone (typed wins over picked), a question and details. */
fun buildRequest(moment: Moment, typedZone: String, pickedZone: String?, question: Question, details: String): AdviceRequest =
    if (moment == Moment.DURING) {
        AdviceRequest(moment, zone = typedZone.trim().ifEmpty { pickedZone.orEmpty() }, question = question, freeText = details.trim().ifEmpty { null })
    } else {
        AdviceRequest(moment)
    }

/** The stored photo to send when none was picked: the latest progress photo, else the initial photo. */
fun defaultPhotoId(p: Project): String? = p.progress.lastOrNull()?.blobId ?: p.initialPhotoBlobId

/** Parses an answer and adds it to the project. Returns the errors to show, or null when it was saved. */
suspend fun saveAnswer(container: AppContainer, projectId: String, raw: String): List<String>? {
    val repository = container.repository
    return when (val parsed = parseAdviceResponse(raw, repository.getInventory())) {
        is ParseResult.Failure -> parsed.errors
        is ParseResult.Success -> try {
            val latest = repository.getProject(projectId) ?: return listOf("project not found")
            repository.saveProject(latest.addAdvice(parsed.entry.toEntry(UUID.randomUUID().toString(), isoString(container.now()))))
            null
        } catch (e: Exception) {
            listOf(e.message ?: e.toString())
        }
    }
}

private val MOMENT_LABELS = listOf(
    Moment.START to R.string.ask_moment_start,
    Moment.HALF_PAINTED to R.string.ask_moment_half_painted,
    Moment.DURING to R.string.ask_moment_during,
)

private val QUESTION_LABELS = listOf(
    Question.APPLY_LAYER to R.string.ask_question_apply_layer,
    Question.LIGHT to R.string.ask_question_light,
    Question.SHADOW to R.string.ask_question_shadow,
    Question.HIGHLIGHT to R.string.ask_question_highlight,
    Question.WHICH_TECHNIQUE to R.string.ask_question_which_technique,
    Question.FREE to R.string.ask_question_free,
)

/** Ask the advisor: build the package, send it to the user's AI app, then paste and save the answer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(
    container: AppContainer,
    projectId: String,
    onDone: () -> Unit,
) {
    val repository = container.repository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var project by remember { mutableStateOf<Project?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(projectId) {
        project = repository.getProject(projectId)
        loaded = true
    }

    // An answer shared into the app for this project arrives ready to save.
    val shared = remember(projectId) { container.sharedAnswers.take(projectId) }
    var moment by rememberSaveable { mutableStateOf(Moment.START) }
    var pickedZone by rememberSaveable { mutableStateOf<String?>(null) }
    var typedZone by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf(Question.APPLY_LAYER) }
    var details by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var photoId by remember { mutableStateOf<String?>(null) }
    var sendStatus by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf<String?>(null) }
    var answer by rememberSaveable { mutableStateOf(shared.orEmpty()) }
    var showAnswer by rememberSaveable { mutableStateOf(shared != null) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }

    val current = project
    if (current == null) {
        Column(Modifier.fillMaxSize().padding(16.dp).testTag("screen_ask")) {
            if (loaded) Text(stringResource(R.string.t8_not_found))
        }
        return
    }
    val zones = remember(current) { recipeZones(current) }
    val photoMissing = stringResource(R.string.ask_photo)
    val fallbackText = "${stringResource(R.string.ask_fallback)} ${stringResource(R.string.ask_prompt_box)}"

    val sources = LocalPhotoSources.current
    val onPhoto: (ByteArray) -> Unit = { photo = it; photoId = null }
    val pick = sources.picker({}, onPhoto)
    val shoot = sources.camera({}, onPhoto)

    val send: () -> Unit = {
        scope.launch {
            val picked = photo
            val id = when {
                picked != null -> photoId ?: repository.putBlob(StoredBlob("image/jpeg", picked)).also { photoId = it }
                else -> defaultPhotoId(current)?.takeIf { repository.getBlob(it) != null }
            }
            if (id == null) {
                sendStatus = photoMissing
                return@launch
            }
            val req = buildRequest(moment, typedZone, pickedZone ?: zones.firstOrNull(), question, details)
            val pkg = buildAdvicePackage(req, repository.getInventory(), id)
            val fallback = container.advisorBridge.send(pkg) == SendResult.FALLBACK
            sendStatus = if (fallback) fallbackText else ""
            prompt = if (fallback) pkg.text else null
        }
    }
    val accept: (String) -> Unit = { raw ->
        scope.launch {
            val failed = saveAnswer(container, projectId, raw)
            if (failed == null) {
                onMain(onDone)
            } else {
                answer = raw
                showAnswer = true
                errors = failed
            }
        }
    }
    val paste: () -> Unit = {
        val raw = container.advisorBridge.readAnswer()
        if (raw.isNullOrBlank()) showAnswer = true else accept(raw)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_ask"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${stringResource(R.string.ask_title)} · ${current.name}", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ask_moment), style = MaterialTheme.typography.titleMedium)
                MOMENT_LABELS.forEach { (m, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = moment == m, onClick = { moment = m }, role = Role.RadioButton)
                            .testTag("moment_${m.wire}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = moment == m, onClick = null)
                        Text(stringResource(label), Modifier.padding(start = 8.dp))
                    }
                }

                if (moment == Moment.DURING) {
                    Column(Modifier.testTag("during_fields"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ask_zone), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            zones.forEach { z ->
                                FilterChip(
                                    selected = (pickedZone ?: zones.first()) == z,
                                    onClick = { pickedZone = z },
                                    label = { Text(z) },
                                    modifier = Modifier.testTag("zone_option_$z"),
                                )
                            }
                        }
                        OutlinedTextField(
                            typedZone, { typedZone = it },
                            placeholder = { Text(stringResource(R.string.ask_zone)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("zone_text"),
                        )
                        Text(stringResource(R.string.ask_question), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QUESTION_LABELS.forEach { (q, label) ->
                                FilterChip(
                                    selected = question == q,
                                    onClick = { question = q },
                                    label = { Text(stringResource(label)) },
                                    modifier = Modifier.testTag("question_${q.wire}"),
                                )
                            }
                        }
                        OutlinedTextField(
                            details, { details = it },
                            placeholder = { Text(stringResource(R.string.ask_details)) },
                            modifier = Modifier.fillMaxWidth().testTag("details"),
                        )
                    }
                }

                Text(stringResource(R.string.ask_photo), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = pick, modifier = Modifier.testTag("ask_choose_photo")) { Text(stringResource(R.string.t8_choose_photo)) }
                    OutlinedButton(onClick = shoot, modifier = Modifier.testTag("ask_take_photo")) { Text(stringResource(R.string.t8_take_photo)) }
                }
                photo?.let { PhotoPreview(it, tag = "ask_photo_preview") }

                Button(onClick = send, modifier = Modifier.testTag("send")) { Text(stringResource(R.string.ask_send)) }
                if (sendStatus.isNotEmpty()) Text(sendStatus, modifier = Modifier.testTag("send_status"))
                prompt?.let { text ->
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("prompt"))
                    }
                    OutlinedButton(onClick = { copyToClipboard(context, text) }, modifier = Modifier.testTag("copy_prompt")) {
                        Text(stringResource(R.string.ask_copy_prompt))
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = paste, modifier = Modifier.testTag("paste")) { Text(stringResource(R.string.ask_paste)) }
                if (errors.isNotEmpty()) {
                    Column(Modifier.testTag("answer_errors")) {
                        Text(stringResource(R.string.ask_errors), color = MaterialTheme.colorScheme.error)
                        errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
                    }
                }
                if (showAnswer) {
                    OutlinedTextField(
                        answer, { answer = it },
                        placeholder = { Text(stringResource(R.string.ask_paste_here)) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("answer"),
                    )
                    Button(onClick = { accept(answer) }, modifier = Modifier.testTag("save_answer")) { Text(stringResource(R.string.ask_save)) }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    try {
        clipboard.setPrimaryClip(ClipData.newPlainText("Forja de Color prompt", text))
    } catch (e: SecurityException) {
        // The prompt stays on screen, selectable, to copy by hand.
    }
}
