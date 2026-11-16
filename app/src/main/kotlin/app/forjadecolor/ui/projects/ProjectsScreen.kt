package app.forjadecolor.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.projects.ProjectStatus
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.domain.projects.projectActivity
import app.forjadecolor.domain.storage.StoredBlob
import app.forjadecolor.ui.components.PhotoPreview
import app.forjadecolor.ui.components.rememberPendingPhoto
import app.forjadecolor.ui.project.BlobImage
import app.forjadecolor.ui.project.LocalPhotoSources
import app.forjadecolor.ui.share.IncomingSharePrompt
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

/** In-progress projects first, then finished ones; newest first within each group (as the PWA). */
fun orderProjects(projects: List<Project>): List<Project> =
    projects.sortedWith(compareBy<Project> { if (it.status == ProjectStatus.IN_PROGRESS) 0 else 1 }.thenByDescending { it.createdAt })

/** Creates and stores a project, with its photo when one was taken; returns the new project. */
suspend fun createAndSaveProject(container: AppContainer, name: String, photo: ByteArray?): Project {
    val photoId = photo?.let { container.repository.putBlob(StoredBlob("image/jpeg", it)) }
    val project = createProject(name, container.now(), UUID.randomUUID().toString()).copy(initialPhotoBlobId = photoId)
    container.repository.saveProject(project)
    return project
}

/** The project list, the "+ New project" button opening its form in a dialog, and the routing of anything shared into the app; state lives in [ProjectsViewModel]. */
@Composable
fun ProjectsScreen(
    container: AppContainer,
    onOpenProject: (projectId: String) -> Unit,
) {
    val vm = viewModel { ProjectsViewModel(container) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load() }
    val projects by vm.projects.collectAsState()
    val share by vm.share.collectAsState()
    val shareError by vm.shareError.collectAsState()
    var formOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_projects"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.projects_title), style = MaterialTheme.typography.headlineSmall)

        // Always the same button (a tap while the list loads still lands), stretched full width while there are no projects yet.
        val list = projects
        Button(
            onClick = { formOpen = true },
            modifier = (if (list?.isEmpty() == true) Modifier.fillMaxWidth() else Modifier).testTag("new_project_open"),
        ) { Text(stringResource(R.string.projects_new_button)) }

        val pending = share
        if (pending != null) {
            IncomingSharePrompt(
                pending,
                projects.orEmpty(),
                onPick = { p -> vm.routeShareTo(context, p, onOpenProject) },
                onDismiss = vm::dismissShare,
            )
        }
        if (shareError) Text(stringResource(R.string.t8_share_image_failed), color = MaterialTheme.colorScheme.error)

        if (list != null) {
            if (list.isEmpty()) {
                Text(stringResource(R.string.projects_empty))
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        list.forEach { p -> ProjectRow(container, p) { onOpenProject(p.id) } }
                    }
                }
            }
        }
    }

    if (formOpen) {
        NewProjectDialog(
            onCreate = { name, photo -> formOpen = false; vm.create(name, photo, onOpenProject) },
            onCancel = { formOpen = false },
        )
    }
}

@Composable
private fun ProjectRow(container: AppContainer, project: Project, onClick: () -> Unit) {
    val status = stringResource(if (project.status == ProjectStatus.DONE) R.string.projects_done else R.string.projects_in_progress)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("project_link"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        project.initialPhotoBlobId?.let { BlobImage(container.repository, it, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))) }
        Column {
            Text("${project.name} · $status")
            Text(
                activityLine(project),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("project_activity"),
            )
        }
    }
}

/** "Last activity: <short local date>", plus " · 3 of 6 zones" when the newest recipe has zones. */
@Composable
private fun activityLine(project: Project): String {
    val activity = projectActivity(project)
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0])
    val date = stringResource(R.string.projects_last_activity, format.format(activity.lastActivity.atZone(ZoneId.systemDefault())))
    return activity.zones?.let { "$date · " + stringResource(R.string.projects_zone_progress, it.done, it.total) } ?: date
}

/** The new-project form in a dialog; its fields survive rotation, and cancelling creates nothing. */
@Composable
private fun NewProjectDialog(onCreate: (name: String, photo: ByteArray?) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var photo by rememberPendingPhoto()
    var photoError by remember { mutableStateOf(false) }
    val sources = LocalPhotoSources.current
    val onPhoto: (ByteArray) -> Unit = { photo = it; photoError = false }
    val onError: (Throwable) -> Unit = { photoError = true }
    val pick = sources.picker(onError, onPhoto)
    val shoot = sources.camera(onError, onPhoto)

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.projects_new), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.projects_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_name"),
                )
                Text(stringResource(R.string.projects_photo), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = pick, modifier = Modifier.testTag("new_project_choose_photo")) { Text(stringResource(R.string.t8_choose_photo)) }
                    OutlinedButton(onClick = shoot, modifier = Modifier.testTag("new_project_take_photo")) { Text(stringResource(R.string.t8_take_photo)) }
                }
                photo?.let { PhotoPreview(it, tag = "new_project_photo_preview", onRemove = { photo = null }) }
                if (photoError) Text(stringResource(R.string.t8_photo_error), color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("new_project_cancel")) { Text(stringResource(R.string.inventory_cancel)) }
                    Button(
                        onClick = { name.trim().takeIf { it.isNotEmpty() }?.let { onCreate(it, photo) } },
                        modifier = Modifier.testTag("new_project_create"),
                    ) { Text(stringResource(R.string.projects_create)) }
                }
            }
        }
    }
}
