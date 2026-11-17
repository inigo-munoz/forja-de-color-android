package app.forjadecolor.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.paints.displayName
import app.forjadecolor.ui.ask.AnswerErrorsDialog
import app.forjadecolor.ui.ask.copyToClipboard
import app.forjadecolor.ui.components.PhotoPreview
import app.forjadecolor.ui.components.Swatch
import app.forjadecolor.ui.components.rememberPendingPhoto
import app.forjadecolor.ui.inventory.BrushDraft
import app.forjadecolor.ui.inventory.BrushDraftSaver
import app.forjadecolor.ui.inventory.BrushFormDialog
import app.forjadecolor.ui.inventory.DraftSaver
import app.forjadecolor.ui.inventory.PaintDraft
import app.forjadecolor.ui.inventory.PaintFormDialog
import app.forjadecolor.ui.project.LocalPhotoSources

/**
 * Adds paints and brushes from a photo: 1. the photo, 2. send it to the AI with the scan instructions,
 * 3. paste the answer. The list it reads waits for the user to tick, correct or discard each item;
 * only "Add to my paints" writes the inventory, then [onDone].
 */
@Composable
fun ScanScreen(container: AppContainer, onDone: () -> Unit) {
    val vm = viewModel { ScanViewModel(container) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var photo by rememberPendingPhoto()
    var answer by rememberSaveable { mutableStateOf("") }
    var showAnswer by rememberSaveable { mutableStateOf(false) }

    val sources = LocalPhotoSources.current
    val pick = sources.picker({}) { photo = it }
    val shoot = sources.camera({}) { photo = it }
    val paste: () -> Unit = {
        val raw = vm.readAnswer()
        if (raw.isNullOrBlank()) {
            showAnswer = true
        } else {
            answer = raw
            vm.read(raw)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_scan"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.headlineSmall)
        val list = state.list
        if (list != null) {
            ConfirmList(list, vm, onSaved = onDone)
        } else {
            Step(R.string.scan_step_photo) {
                Text(stringResource(R.string.scan_photo_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = pick, modifier = Modifier.testTag("scan_choose_photo")) { Text(stringResource(R.string.t8_choose_photo)) }
                    OutlinedButton(onClick = shoot, modifier = Modifier.testTag("scan_take_photo")) { Text(stringResource(R.string.t8_take_photo)) }
                }
                photo?.let { PhotoPreview(it, tag = "scan_photo_preview", onRemove = { photo = null }) }
            }
            Step(R.string.scan_step_send) {
                Button(onClick = { vm.send(photo) }, modifier = Modifier.testTag("scan_send")) { Text(stringResource(R.string.ask_send)) }
                when (state.sendStatus) {
                    ScanSendStatus.NONE -> {}
                    ScanSendStatus.PHOTO_MISSING -> Text(stringResource(R.string.ask_no_photo), modifier = Modifier.testTag("scan_send_status"))
                    ScanSendStatus.FALLBACK -> Text(
                        "${stringResource(R.string.ask_fallback)} ${stringResource(R.string.ask_prompt_box)}",
                        modifier = Modifier.testTag("scan_send_status"),
                    )
                }
                state.prompt?.let { text ->
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("scan_prompt"))
                    }
                    OutlinedButton(onClick = { copyToClipboard(context, text) }, modifier = Modifier.testTag("scan_copy_prompt")) {
                        Text(stringResource(R.string.ask_copy_prompt))
                    }
                }
            }
            Step(R.string.scan_step_paste) {
                Text(stringResource(R.string.ask_paste_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = paste, modifier = Modifier.testTag("scan_paste")) { Text(stringResource(R.string.ask_paste)) }
                if (showAnswer) {
                    OutlinedTextField(
                        answer, { answer = it },
                        placeholder = { Text(stringResource(R.string.ask_paste_here)) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("scan_answer"),
                    )
                    Button(onClick = { vm.read(answer) }, modifier = Modifier.testTag("scan_read")) { Text(stringResource(R.string.scan_read)) }
                }
            }
        }
    }

    if (state.errors.isNotEmpty()) AnswerErrorsDialog(state.errors, onClose = vm::clearErrors)
}

@Composable
private fun Step(title: Int, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Every item read from the photo, ticked when complete; tapping one opens the paint or brush form to correct it. */
@Composable
private fun ConfirmList(list: ScanList, vm: ScanViewModel, onSaved: () -> Unit) {
    var paintIndex by rememberSaveable { mutableIntStateOf(-1) }
    var paintDraft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<PaintDraft?>(null) }
    var brushIndex by rememberSaveable { mutableIntStateOf(-1) }
    var brushDraft by rememberSaveable(stateSaver = BrushDraftSaver) { mutableStateOf<BrushDraft?>(null) }

    Column(Modifier.testTag("scan_list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.scan_confirm_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (list.paints.isNotEmpty()) Text(stringResource(R.string.scan_paints), style = MaterialTheme.typography.titleMedium)
        list.paints.forEachIndexed { i, item ->
            ItemRow(
                tag = "scan_paint_$i", includeTag = "scan_paint_include_$i",
                included = item.included, enabled = item.complete,
                onToggle = { vm.togglePaint(i) },
                onEdit = { paintIndex = i; paintDraft = PaintDraft.of(item.paint) },
            ) {
                Swatch(item.paint.hex)
                Column(Modifier.weight(1f)) {
                    Text(listOf(item.paint.brand, item.paint.code).filter { it.isNotBlank() }.joinToString(" "), fontFamily = FontFamily.Monospace)
                    Text(displayName(item.paint))
                    val owned = item.existing
                    if (owned != null && item.complete) {
                        Text(
                            stringResource(R.string.already_owned, displayName(owned), owned.code),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("scan_paint_owned_$i"),
                        )
                    } else {
                        Status(item.complete, item.updates)
                    }
                }
            }
        }
        if (list.brushes.isNotEmpty()) Text(stringResource(R.string.inventory_brushes), style = MaterialTheme.typography.titleMedium)
        list.brushes.forEachIndexed { i, item ->
            ItemRow(
                tag = "scan_brush_$i", includeTag = "scan_brush_include_$i",
                included = item.included, enabled = item.complete,
                onToggle = { vm.toggleBrush(i) },
                onEdit = { brushIndex = i; brushDraft = BrushDraft.of(item.brush) },
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.brush.code, fontFamily = FontFamily.Monospace)
                    Text(item.brush.name)
                    Status(item.complete, item.updates)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = vm::discard, modifier = Modifier.testTag("scan_discard")) { Text(stringResource(R.string.scan_discard)) }
            Button(enabled = list.toSave > 0, onClick = { vm.save(onSaved) }, modifier = Modifier.testTag("scan_save")) {
                Text(pluralStringResource(R.plurals.scan_save, list.toSave, list.toSave))
            }
        }
    }

    paintDraft?.let { d ->
        PaintFormDialog(
            draft = d,
            onChange = { paintDraft = it },
            onSave = { vm.correctPaint(paintIndex, it); paintDraft = null },
            onCancel = { paintDraft = null },
        )
    }
    brushDraft?.let { d ->
        BrushFormDialog(
            draft = d,
            onChange = { brushDraft = it },
            onSave = { vm.correctBrush(brushIndex, it); brushDraft = null },
            onCancel = { brushDraft = null },
        )
    }
}

@Composable
private fun ItemRow(
    tag: String,
    includeTag: String,
    included: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(8.dp).testTag(tag),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = included && enabled, onCheckedChange = { onToggle() }, enabled = enabled, modifier = Modifier.testTag(includeTag))
            content()
        }
    }
}

@Composable
private fun Status(complete: Boolean, updates: Boolean) {
    val (text, color) = when {
        !complete -> R.string.scan_incomplete to MaterialTheme.colorScheme.error
        updates -> R.string.scan_updates to MaterialTheme.colorScheme.onSurfaceVariant
        else -> R.string.scan_new to MaterialTheme.colorScheme.primary
    }
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = color)
}
