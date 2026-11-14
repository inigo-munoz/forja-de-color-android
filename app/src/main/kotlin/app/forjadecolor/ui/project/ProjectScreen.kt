package app.forjadecolor.ui.project

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
import app.forjadecolor.domain.advice.TechniqueCardId

/** Placeholder: one project: photos, target image, before/after, advice (T8). */
@Composable
fun ProjectScreen(
    container: AppContainer,
    projectId: String,
    onAsk: () -> Unit,
    onOpenTechnique: (TechniqueCardId) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("screen_project")) {
        Text(stringResource(R.string.project_advice), style = MaterialTheme.typography.headlineSmall)
    }
}
