package app.forjadecolor.ui.share

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.projects.Project
import app.forjadecolor.domain.storage.StoredBlob
import app.forjadecolor.platform.IncomingShare
import app.forjadecolor.platform.resizeToJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A shared AI answer waiting for the ask screen of the project the user picked. It lives only in memory:
 * the projects screen puts it here and the ask screen takes it, once.
 */
class SharedAnswerInbox {
    private var pending: Pair<String, String>? = null

    @Synchronized fun put(projectId: String, answer: String) {
        pending = projectId to answer
    }

    @Synchronized fun hasAnswerFor(projectId: String): Boolean = pending?.first == projectId

    @Synchronized fun take(projectId: String): String? = pending?.takeIf { it.first == projectId }?.second?.also { pending = null }
}

/** Reads a shared image and shrinks it like every other photo the app stores. */
suspend fun readSharedImage(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("image unavailable")
    resizeToJpeg(bytes)
}

/**
 * Hands a share to the project the user picked: an answer goes to that project's ask screen (through
 * [sharedAnswers]); an image is stored and becomes the project's target image.
 */
suspend fun routeShare(container: AppContainer, context: Context, share: IncomingShare, projectId: String) {
    when (share) {
        is IncomingShare.Text -> container.sharedAnswers.put(projectId, share.text)
        is IncomingShare.Image -> {
            val blobId = container.repository.putBlob(StoredBlob("image/jpeg", readSharedImage(context, share.uri)))
            val project = container.repository.getProject(projectId) ?: return
            container.repository.saveProject(project.copy(targetImageBlobId = blobId))
        }
    }
}

/** Asks which project a share belongs to. */
@Composable
fun IncomingSharePrompt(share: IncomingShare, projects: List<Project>, onPick: (Project) -> Unit, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("share_prompt")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val what = if (share is IncomingShare.Text) R.string.t8_share_text else R.string.t8_share_image
            Text(stringResource(what), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (projects.isEmpty()) R.string.t8_share_no_projects else R.string.t8_share_which))
            projects.forEach { p ->
                OutlinedButton(onClick = { onPick(p) }, modifier = Modifier.fillMaxWidth().testTag("share_target_${p.id}")) { Text(p.name) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("share_dismiss")) { Text(stringResource(R.string.t8_share_dismiss)) }
        }
    }
}
