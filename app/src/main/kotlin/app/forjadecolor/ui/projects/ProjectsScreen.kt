package app.forjadecolor.ui.projects

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.forjadecolor.AppContainer
import app.forjadecolor.R

/** Placeholder: the project list and the new-project form (T8). Also routes [AppContainer.incomingShares]. */
@Composable
fun ProjectsScreen(
    container: AppContainer,
    onOpenProject: (projectId: String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("screen_projects")) {
        Text(stringResource(R.string.projects_title), style = MaterialTheme.typography.headlineSmall)
    }
}
