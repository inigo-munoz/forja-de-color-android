package app.forjadecolor.ui.project

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.advice.AdvicePackage
import app.forjadecolor.domain.advice.TechniqueCardId
import app.forjadecolor.domain.advice.assignUnknownPaint
import app.forjadecolor.domain.advice.buildImagePrompt
import app.forjadecolor.domain.isoString
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.projects.ProgressPhoto
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.projects.ProjectStatus
import app.forjadecolor.domain.projects.addProgressPhoto
import app.forjadecolor.domain.projects.markDone
import app.forjadecolor.domain.projects.reopen
import app.forjadecolor.domain.projects.setNotes
import app.forjadecolor.domain.storage.StoredBlob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Where the painted image starts showing in the before/after comparison, from 0 (all painted) to 1. */
val PaintedRevealStart = SemanticsPropertyKey<Float>("PaintedRevealStart")
private var SemanticsPropertyReceiver.paintedRevealStart by PaintedRevealStart

private const val NOTES_DEBOUNCE_MS = 800L

/** One project: before/after, advice, notes, progress photos and the target image. */
@Composable
fun ProjectScreen(
    container: AppContainer,
    projectId: String,
    onAsk: () -> Unit,
    onOpenTechnique: (TechniqueCardId) -> Unit,
    onBack: () -> Unit,
) {
    val repository = container.repository
    val store = remember(projectId) { ProjectStore(repository, projectId) }
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var project by remember { mutableStateOf<Project?>(null) }
    var inventory by remember { mutableStateOf(Inventory(emptyList(), emptyList())) }
    LaunchedEffect(projectId) {
        inventory = repository.getInventory()
        project = store.load()
        loaded = true
        // An answer shared from the AI app for this project: go straight to saving it.
        if (container.sharedAnswers.hasAnswerFor(projectId)) onMain(onAsk)
    }
    val update: (suspend (Project) -> Project) -> Unit = { change ->
        scope.launch { store.update(change)?.let { project = it } }
    }

    val current = project
    if (current == null) {
        Column(Modifier.fillMaxSize().padding(16.dp).testTag("screen_project")) {
            if (loaded) Text(stringResource(R.string.t8_not_found))
        }
        return
    }

    val sources = LocalPhotoSources.current
    // A photo that cannot be read says so next to its buttons, instead of the tap seeming to do nothing.
    var progressError by rememberSaveable { mutableStateOf(false) }
    var targetError by rememberSaveable { mutableStateOf(false) }
    val addProgress: (ByteArray) -> Unit = { bytes ->
        progressError = false
        update { p ->
            val blobId = repository.putBlob(StoredBlob("image/jpeg", bytes))
            p.addProgressPhoto(ProgressPhoto(UUID.randomUUID().toString(), isoString(container.now()), blobId))
        }
    }
    val setTarget: (ByteArray) -> Unit = { bytes ->
        targetError = false
        update { p -> p.copy(targetImageBlobId = repository.putBlob(StoredBlob("image/jpeg", bytes))) }
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
                onClick = { update { p -> if (p.status == ProjectStatus.DONE) p.reopen() else p.markDone() } },
                modifier = Modifier.testTag("toggle_done"),
            ) { Text(stringResource(if (current.status == ProjectStatus.DONE) R.string.project_reopen else R.string.project_mark_done)) }
        }

        current.advice.asReversed().forEach { entry ->
            AdviceCard(
                entry,
                inventory,
                onDelete = { update { p -> p.copy(advice = p.advice.filter { it.id != entry.id }) } },
                onAssign = { unknown, owned ->
                    update { p -> p.copy(advice = p.advice.map { if (it.id == entry.id) assignUnknownPaint(it, unknown, owned) else it }) }
                },
                onOpenTechnique = onOpenTechnique,
                onPaintImage = { recipe ->
                    val photoId = current.initialPhotoBlobId ?: return@AdviceCard null
                    if (repository.getBlob(photoId) == null) return@AdviceCard null
                    container.advisorBridge.send(AdvicePackage(buildImagePrompt(recipe, inventory), photoId))
                },
            )
        }

        // Notes are written on the app scope: the last save runs as the screen closes, when its own scope is gone.
        NotesCard(current.notes) { text ->
            container.appScope.launch { store.update { p -> p.setNotes(text) }?.let { project = it } }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.project_progress), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.progress.forEach { photo ->
                        Column(Modifier.testTag("progress_photo")) {
                            BlobImage(repository, photo.blobId, Modifier.size(96.dp))
                            Text(photo.takenAt.take(10), style = MaterialTheme.typography.labelSmall)
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
                current.targetImageBlobId?.let { BlobImage(repository, it, Modifier.fillMaxWidth().aspectRatio(1f)) }
                OutlinedButton(onClick = pickTarget, modifier = Modifier.testTag("set_target")) { Text(stringResource(R.string.project_set_target)) }
                if (targetError) PhotoErrorText("target_photo_error")
            }
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
 * background and when the screen closes, so Android discarding the app cannot lose them.
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
