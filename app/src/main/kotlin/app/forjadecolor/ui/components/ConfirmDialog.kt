package app.forjadecolor.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

/** A destructive action waiting for the user to confirm it. */
class Confirmation(@StringRes val message: Int, @StringRes val action: Int, val onConfirm: () -> Unit)

/** Asks before something that cannot be undone; the confirm button is in the error colour. */
@Composable
fun ConfirmDialog(confirmation: Confirmation, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(confirmation.message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    confirmation.onConfirm()
                },
                modifier = Modifier.testTag("confirm_delete"),
            ) { Text(stringResource(confirmation.action), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_delete")) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
