package app.forjadecolor.ui.ask

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

/** Placeholder: the ask-the-advisor form, send and paste answer (T8). */
@Composable
fun AskScreen(
    container: AppContainer,
    projectId: String,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("screen_ask")) {
        Text(stringResource(R.string.ask_title), style = MaterialTheme.typography.headlineSmall)
    }
}
