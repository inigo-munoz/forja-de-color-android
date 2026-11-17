package app.forjadecolor.ui.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.advice.TechniqueCardId
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.projects.ProjectStatus
import app.forjadecolor.ui.components.LookLine
import app.forjadecolor.ui.components.ConfirmDialog
import app.forjadecolor.ui.components.Confirmation
import kotlinx.coroutines.delay

/** Where the painted image starts showing in the before/after comparison, from 0 (all painted) to 1. */
val PaintedRevealStart = SemanticsPropertyKey<Float>("PaintedRevealStart")
private var SemanticsPropertyReceiver.paintedRevealStart by PaintedRevealStart

private const val NOTES_DEBOUNCE_MS = 800L

/** The sections below the project header, one shown at a time. */
private enum class ProjectTab(val tag: String, val label: Int) {
    RECIPE("tab_recipe", R.string.project_tab_recipe),
    DIARY("tab_diary", R.string.project_tab_diary),
    PHOTOS("tab_photos", R.string.project_tab_photos),
}

/**
 * One project: title, before/after and the main actions on top, then tabs for the advice (Recipe),
 * the notes (Diary) and the progress and target photos (Photos); "Delete project" closes the screen
 * under any tab. State lives in [ProjectViewModel].
 */
@Composable
fun ProjectScreen(
    container: AppContainer,
    projectId: String,
    onAsk: () -> Unit,
    onOpenTechnique: (TechniqueCardId) -> Unit,
    onBack: () -> Unit,
) {
    val repository = container.repository
    val vm = viewModel(key = "project:$projectId") { ProjectViewModel(container, projectId) }
    LaunchedEffect(projectId) {
        // An answer shared from the AI app for this project: go straight to saving it.
        if (vm.load()) onMain(onAsk)
    }
    val state by vm.state.collectAsState()
    // Every deletion waits here for the user to confirm it.
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    // The progress photo shown large, by its blob id.
    var openPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    // Survives rotation; declared before the loading return so restoring never loses it.
    var selectedTab by rememberSaveable { mutableStateOf(ProjectTab.RECIPE) }

    val current = state.project
    val inventory = state.inventory
    if (current == null) {
        Column(Modifier.fillMaxSize().padding(16.dp).testTag("screen_project")) {
            if (state.loaded) Text(stringResource(R.string.t8_not_found))
        }
        return
    }

    val sources = LocalPhotoSources.current
    // A photo that cannot be read says so next to its buttons, instead of the tap seeming to do nothing.
    var progressError by rememberSaveable { mutableStateOf(false) }
    var targetError by rememberSaveable { mutableStateOf(false) }
    val addProgress: (ByteArray) -> Unit = { bytes ->
        progressError = false
        vm.addProgressPhoto(bytes)
    }
    val setTarget: (ByteArray) -> Unit = { bytes ->
        targetError = false
        vm.setTarget(bytes)
    }
    val pickProgress = sources.picker({ progressError = true }, addProgress)
    val shootProgress = sources.camera({ progressError = true }, addProgress)
    val pickTarget = sources.picker({ targetError = true }, setTarget)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_project"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(current.name, style = MaterialTheme.typography.headlineSmall)
        ProjectHeaderImage(container, current)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAsk, modifier = Modifier.testTag("ask")) { Text(stringResource(R.string.project_ask)) }
            OutlinedButton(
                onClick = vm::toggleDone,
                modifier = Modifier.testTag("toggle_done"),
            ) { Text(stringResource(if (current.status == ProjectStatus.DONE) R.string.project_reopen else R.string.project_mark_done)) }
        }
        LookLine(current.look, onChange = vm::setLook)

        PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            ProjectTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    text = { Text(stringResource(tab.label)) },
                    modifier = Modifier.testTag(tab.tag),
                )
            }
        }

        when (selectedTab) {
            ProjectTab.RECIPE -> {
                if (current.advice.isEmpty()) {
                    Text(stringResource(R.string.project_advice_empty), modifier = Modifier.testTag("advice_empty"))
                }
                current.advice.asReversed().forEach { entry ->
                    AdviceCard(
                        entry,
                        inventory,
                        onDelete = {
                            confirmation = Confirmation(R.string.confirm_delete_advice, R.string.action_delete) { vm.deleteAdvice(entry.id) }
                        },
                        onAssign = { unknown, owned -> vm.assignPaint(entry.id, unknown, owned) },
                        onZoneDone = { zone, done -> vm.setZoneDone(entry.id, zone, done) },
                        onOpenTechnique = onOpenTechnique,
                        onPaintImage = vm::paintImage,
                    )
                }
            }
            ProjectTab.DIARY -> {
                // Saved on the app scope (see ProjectViewModel.saveNotes): the last save runs as the card
                // leaves composition, when the screen closes or another tab is selected.
                NotesCard(current.notes, vm::saveNotes)
            }
            ProjectTab.PHOTOS -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.project_progress), style = MaterialTheme.typography.titleMedium)
                        if (current.progress.isEmpty()) {
                            Text(
                                stringResource(R.string.project_progress_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("progress_empty"),
                            )
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            current.progress.forEach { photo ->
                                Column(Modifier.testTag("progress_photo")) {
                                    BlobImage(
                                        repository,
                                        photo.blobId,
                                        Modifier.size(96.dp).clickable { openPhoto = photo.blobId }.testTag("open_progress_photo"),
                                    )
                                    Text(photo.takenAt.take(10), style = MaterialTheme.typography.labelSmall)
                                    TextButton(
                                        onClick = {
                                            confirmation = Confirmation(R.string.confirm_delete_progress, R.string.action_delete) { vm.deleteProgressPhoto(photo) }
                                        },
                                        modifier = Modifier.testTag("delete_progress_photo"),
                                    ) { Text(stringResource(R.string.project_delete_photo)) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = pickProgress, modifier = Modifier.testTag("add_progress")) { Text(stringResource(R.string.project_add_progress)) }
                            OutlinedButton(onClick = shootProgress, modifier = Modifier.testTag("add_progress_camera")) { Text(stringResource(R.string.t8_take_photo)) }
                        }
                        if (progressError) PhotoErrorText("progress_photo_error")
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.project_target), style = MaterialTheme.typography.titleMedium)
                        // A thumbnail: the full image is already in the before/after at the top.
                        current.targetImageBlobId?.let { BlobImage(repository, it, Modifier.size(120.dp).testTag("target_thumb")) }
                        if (current.targetImageBlobId != null) {
                            TextButton(
                                onClick = {
                                    confirmation = Confirmation(R.string.confirm_remove_target, R.string.action_remove) { vm.removeTarget() }
                                },
                                modifier = Modifier.testTag("remove_target"),
                            ) { Text(stringResource(R.string.project_remove_target)) }
                        }
                        OutlinedButton(onClick = pickTarget, modifier = Modifier.testTag("set_target")) {
                            Text(stringResource(if (current.targetImageBlobId != null) R.string.project_change_target else R.string.project_set_target))
                        }
                        if (targetError) PhotoErrorText("target_photo_error")
                    }
                }
            }
        }

        TextButton(
            onClick = {
                confirmation = Confirmation(R.string.confirm_delete_project, R.string.action_delete) { vm.delete(onBack) }
            },
            modifier = Modifier.testTag("delete_project"),
        ) { Text(stringResource(R.string.project_delete), color = MaterialTheme.colorScheme.error) }
    }

    confirmation?.let { ConfirmDialog(it, onDismiss = { confirmation = null }) }
    openPhoto?.let { blobId -> LargePhoto(container, blobId, onClose = { openPhoto = null }) }
}

/** A photo shown as large as the screen allows, over the project, until closed. */
@Composable
private fun LargePhoto(container: AppContainer, blobId: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BlobImage(
                container.repository,
                blobId,
                Modifier.fillMaxWidth().aspectRatio(1f).testTag("progress_photo_large"),
                contentScale = ContentScale.Fit,
            )
            Button(onClick = onClose, modifier = Modifier.testTag("close_photo")) { Text(stringResource(R.string.action_close)) }
        }
    }
}

@Composable
private fun PhotoErrorText(tag: String) {
    Text(
        stringResource(R.string.t8_photo_error),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Before/after when both the photo and the painted image exist; otherwise the photo alone, if any. */
@Composable
private fun ProjectHeaderImage(container: AppContainer, project: Project) {
    val photoId = project.initialPhotoBlobId
    val targetId = project.targetImageBlobId
    val bothExist by produceState(false, photoId, targetId) {
        value = photoId != null && targetId != null &&
            container.repository.getBlob(photoId) != null && container.repository.getBlob(targetId) != null
    }
    when {
        bothExist && photoId != null && targetId != null -> CompareView(container, photoId, targetId)
        photoId != null -> BlobImage(container.repository, photoId, Modifier.fillMaxWidth().aspectRatio(1f))
    }
}

/** Unpainted photo underneath, painted image on top shown from the slider rightwards (as the PWA's clip-path). */
@Composable
private fun CompareView(container: AppContainer, beforeId: String, afterId: String) {
    var revealStart by remember { mutableFloatStateOf(0.5f) }
    Column(Modifier.fillMaxWidth().testTag("compare")) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds()) {
            BlobImage(container.repository, beforeId, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize()
                    .semantics { paintedRevealStart = revealStart }
                    .testTag("compare_painted")
                    .drawWithContent { clipRect(left = size.width * revealStart) { this@drawWithContent.drawContent() } },
            ) {
                BlobImage(container.repository, afterId, Modifier.fillMaxSize())
            }
        }
        Slider(value = revealStart, onValueChange = { revealStart = it }, modifier = Modifier.fillMaxWidth().testTag("compare_slider"))
        Text(stringResource(R.string.project_compare), style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Notes are saved while typing (debounced), when the field loses focus, when the app goes to the
 * background and when the card leaves composition (the screen closes or another tab is selected),
 * so Android discarding the app cannot lose them.
 */
@Composable
private fun NotesCard(stored: String, save: (String) -> Unit) {
    var text by remember { mutableStateOf(stored) }
    var saved by remember { mutableStateOf(stored) }
    val flush = {
        if (text != saved) {
            saved = text
            save(text)
        }
    }
    LaunchedEffect(text) {
        if (text != saved) {
            delay(NOTES_DEBOUNCE_MS)
            flush()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { flush() }
    val latestText by rememberUpdatedState(text)
    val latestSaved by rememberUpdatedState(saved)
    val latestSave by rememberUpdatedState(save)
    DisposableEffect(Unit) {
        onDispose {
            // The screen's own scope is cancelled with it; this last write must still reach storage.
            if (latestText != latestSaved) latestSave(latestText)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.project_notes), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("notes").onFocusChanged { if (!it.isFocused) flush() },
            )
        }
    }
}
