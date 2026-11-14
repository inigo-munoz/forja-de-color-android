package app.forjadecolor.ui.settings

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

/** Placeholder: backup export/import and AI instructions (T9). */
@Composable
fun SettingsScreen(
    container: AppContainer,
) {
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("screen_settings")) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
    }
}
