package app.forjadecolor.ui.technique

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

/** Placeholder: a technique card read from assets/cards/<id>.md (T9). */
@Composable
fun TechniqueScreen(
    container: AppContainer,
    cardId: TechniqueCardId,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("screen_technique")) {
        Text(stringResource(R.string.project_technique), style = MaterialTheme.typography.headlineSmall)
    }
}
