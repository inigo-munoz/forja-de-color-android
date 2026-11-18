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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.advice.AdviceRequest
import app.forjadecolor.domain.advice.InstructionsStatus
import app.forjadecolor.domain.advice.Moment
import app.forjadecolor.domain.advice.ParseResult
import app.forjadecolor.domain.advice.Question
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.parseAdviceResponse
import app.forjadecolor.domain.isoString
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.projects.addAdvice
import app.forjadecolor.ui.components.LookLine
import app.forjadecolor.ui.components.PhotoPreview
import app.forjadecolor.ui.components.clipboardCopier
import app.forjadecolor.ui.project.BlobImage
import app.forjadecolor.ui.components.rememberPendingPhoto
import app.forjadecolor.ui.project.LocalPhotoSources
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

/** Each moment with its label and the one-line help shown under it. */
private val MOMENT_LABELS = listOf(
    Triple(Moment.START, R.string.ask_moment_start, R.string.ask_moment_start_help),
    Triple(Moment.PRIMED, R.string.ask_moment_primed, R.string.ask_moment_primed_help),
    Triple(Moment.HALF_PAINTED, R.string.ask_moment_half_painted, R.string.ask_moment_half_painted_help),
    Triple(Moment.DURING, R.string.ask_moment_during, R.string.ask_moment_during_help),
)

private val QUESTION_LABELS = listOf(
    Question.APPLY_LAYER to R.string.ask_question_apply_layer,
    Question.LIGHT to R.string.ask_question_light,
    Question.SHADOW to R.string.ask_question_shadow,
    Question.HIGHLIGHT to R.string.ask_question_highlight,
    Question.WHICH_TECHNIQUE to R.string.ask_question_which_technique,
    Question.FREE to R.string.ask_question_free,
)

/** Ask the advisor: build the package, send it to the user's AI app, then paste and save the answer; state lives in [AskViewModel]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskScreen(
    container: AppContainer,
    projectId: String,
    onDone: () -> Unit,
) {
    val vm = viewModel(key = "ask:$projectId") { AskViewModel(container, projectId) }
    val context = LocalContext.current
    LaunchedEffect(projectId) { vm.load() }
    val state by vm.state.collectAsState()

    // An answer shared into the app for this project arrives ready to save.
    val shared = vm.sharedAnswer
    var moment by rememberSaveable { mutableStateOf(Moment.START) }
    var pickedZone by rememberSaveable { mutableStateOf<String?>(null) }
    var typedZone by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf(Question.APPLY_LAYER) }
    var details by rememberSaveable { mutableStateOf("") }
    var photo by rememberPendingPhoto()
    var answer by rememberSaveable { mutableStateOf(shared.orEmpty()) }
    var showAnswer by rememberSaveable { mutableStateOf(shared != null) }

    val current = state.project
    if (current == null) {
        Column(Modifier.fillMaxSize().padding(16.dp).testTag("screen_ask")) {
            if (state.loaded) Text(stringResource(R.string.t8_not_found))
        }
        return
    }
    val zones = remember(current) { recipeZones(current) }
    val sendStatus = when (state.sendStatus) {
        SendStatus.NONE -> ""
        SendStatus.SHARED -> stringResource(R.string.paste_instructions_hint)
        SendStatus.PHOTO_MISSING -> stringResource(R.string.ask_photo)
        SendStatus.CURRENT_PHOTO_MISSING -> stringResource(R.string.ask_current_photo)
        SendStatus.FALLBACK -> "${stringResource(R.string.ask_fallback)} ${stringResource(R.string.ask_prompt_box)}"
    }
    val prompt = state.prompt
    val errors = state.errors

    val sources = LocalPhotoSources.current
    val onPhoto: (ByteArray) -> Unit = { photo = it; vm.photoChanged() }
    val pick = sources.picker({}, onPhoto)
    val shoot = sources.camera({}, onPhoto)

    val send: () -> Unit = {
        vm.send(photo, buildRequest(moment, typedZone, pickedZone ?: zones.firstOrNull(), question, details))
    }
    val accept: (String) -> Unit = { raw ->
        vm.accept(raw, onSaved = onDone, onRejected = {
            answer = raw
            showAnswer = true
        })
    }
    val paste: () -> Unit = {
        val raw = vm.readAnswer()
        if (raw.isNullOrBlank()) showAnswer = true else accept(raw)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_ask"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${stringResource(R.string.ask_title)} · ${current.name}", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepHeading(1, R.string.ask_step_moment)
                LookLine(current.look, onChange = vm::setLook)
                MOMENT_LABELS.forEach { (m, label, help) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = moment == m, onClick = { moment = m }, role = Role.RadioButton)
                            .testTag("moment_${m.wire}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = moment == m, onClick = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(stringResource(label))
                            Text(
                                stringResource(help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("moment_help_${m.wire}"),
                            )
                        }
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
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepHeading(2, R.string.ask_step_send)
                Text(stringResource(R.string.ask_photo), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = pick, modifier = Modifier.testTag("ask_choose_photo")) { Text(stringResource(R.string.t8_choose_photo)) }
                    OutlinedButton(onClick = shoot, modifier = Modifier.testTag("ask_take_photo")) { Text(stringResource(R.string.t8_take_photo)) }
                }
                val picked = photo
                if (picked != null) {
                    PhotoPreview(picked, tag = "ask_photo_preview", onRemove = { photo = null })
                } else {
                    DefaultPhoto(container, defaultPhotoId(current))
                }

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
                StepHeading(3, R.string.ask_step_paste)
                Text(
                    stringResource(R.string.ask_paste_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ask_paste_help"),
                )
                Button(onClick = paste, modifier = Modifier.testTag("paste")) { Text(stringResource(R.string.ask_paste)) }
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

        AiInstructionsLine(state.instructions, state.instructionsCopied, onCopy = { vm.copyInstructions(clipboardCopier(context)) })
    }

    // A modal, not a line above the answer: with a long answer that line was off screen and saving seemed to do nothing.
    if (errors.isNotEmpty()) AnswerErrorsDialog(errors, onClose = vm::clearErrors)
}

@Composable
private fun StepHeading(number: Int, text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("ask_step_$number"))
}

/** With no photo picked: the project photo that will be sent, or a nudge to pick one when there is none. */
@Composable
private fun DefaultPhoto(container: AppContainer, photoId: String?) {
    // Merged so the thumbnail and its sentence read as one item.
    Row(Modifier.semantics(mergeDescendants = true) {}.testTag("ask_default_photo"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (photoId != null) {
            BlobImage(container.repository, photoId, Modifier.size(56.dp).testTag("ask_default_photo_thumb"))
            Text(stringResource(R.string.ask_default_photo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.ask_no_photo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Only matters for free chat in the user's Claude project or custom GPT: invites to copy, or warns they are out of date. */
@Composable
private fun AiInstructionsLine(status: InstructionsStatus?, copied: Boolean, onCopy: () -> Unit) {
    val text = when (status) {
        InstructionsStatus.NEVER -> R.string.ai_instructions_never
        InstructionsStatus.STALE -> R.string.ai_instructions_stale
        InstructionsStatus.CURRENT, null -> null
    }
    if (text != null) {
        Row(Modifier.fillMaxWidth().testTag("ask_ai_instructions"), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodySmall,
                color = if (status == InstructionsStatus.STALE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCopy, modifier = Modifier.testTag("ask_copy_instructions")) { Text(stringResource(R.string.ai_instructions_copy)) }
        }
    } else if (copied) {
        Text(
            stringResource(R.string.settings_copied),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
internal fun AnswerErrorsDialog(errors: List<String>, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.ask_errors)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("answer_errors"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, modifier = Modifier.testTag("errors_ok")) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    try {
        clipboard.setPrimaryClip(ClipData.newPlainText("Forja de Color prompt", text))
    } catch (e: SecurityException) {
        // The prompt stays on screen, selectable, to copy by hand.
    }
}
