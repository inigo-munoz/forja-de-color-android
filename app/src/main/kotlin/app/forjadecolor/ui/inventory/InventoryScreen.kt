package app.forjadecolor.ui.inventory

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

/** Placeholder: the paints and brushes inventory (T9). */
@Composable
fun InventoryScreen(
    container: AppContainer,
) {
    Column(Modifier.fillMaxSize().padding(16.dp).testTag("screen_inventory")) {
        Text(stringResource(R.string.inventory_title), style = MaterialTheme.typography.headlineSmall)
    }
}
