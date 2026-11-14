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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.projects.ProjectStatus
import app.forjadecolor.domain.projects.createProject
import app.forjadecolor.domain.storage.StoredBlob
import app.forjadecolor.ui.project.BlobImage
import app.forjadecolor.ui.project.LocalPhotoSources
import app.forjadecolor.ui.project.onMain
import app.forjadecolor.ui.share.IncomingSharePrompt
import app.forjadecolor.ui.share.routeShare
import kotlinx.coroutines.launch
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

/** The project list, the new-project form, and the routing of anything shared into the app. */
@Composable
fun ProjectsScreen(
    container: AppContainer,
    onOpenProject: (projectId: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var projects by remember { mutableStateOf<List<Project>?>(null) }
    LaunchedEffect(Unit) { projects = orderProjects(container.repository.listProjects()) }
    val share by container.incomingShares.current.collectAsState()
    var shareError by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_projects"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.projects_title), style = MaterialTheme.typography.headlineSmall)

        val pending = share
        if (pending != null) {
            IncomingSharePrompt(
                pending,
                projects.orEmpty(),
                onPick = { p ->
                    scope.launch {
                        val taken = container.incomingShares.consume() ?: return@launch
                        val ok = runCatching { routeShare(container, context, taken, p.id) }.isSuccess
                        shareError = !ok
                        if (ok) onMain { onOpenProject(p.id) }
                    }
                },
                onDismiss = { container.incomingShares.consume() },
            )
        }
        if (shareError) Text(stringResource(R.string.t8_share_image_failed), color = MaterialTheme.colorScheme.error)

        val list = projects
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

        NewProjectForm(onCreate = { name, photo ->
            // One tap, one project: a second tap while saving is ignored.
            if (!creating) {
                creating = true
                scope.launch {
                    val id = createAndSaveProject(container, name, photo).id
                    onMain { onOpenProject(id) }
                }
            }
        })
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
        Text("${project.name} · $status")
    }
}

@Composable
private fun NewProjectForm(onCreate: (name: String, photo: ByteArray?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var photoError by remember { mutableStateOf(false) }
    val sources = LocalPhotoSources.current
    val onPhoto: (ByteArray) -> Unit = { photo = it; photoError = false }
    val onError: (Throwable) -> Unit = { photoError = true }
    val pick = sources.picker(onError, onPhoto)
    val shoot = sources.camera(onError, onPhoto)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            if (photo != null) Text(stringResource(R.string.t8_photo_ready))
            if (photoError) Text(stringResource(R.string.t8_photo_error), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = { name.trim().takeIf { it.isNotEmpty() }?.let { onCreate(it, photo) } },
                modifier = Modifier.testTag("new_project_create"),
            ) { Text(stringResource(R.string.projects_create)) }
        }
    }
}
