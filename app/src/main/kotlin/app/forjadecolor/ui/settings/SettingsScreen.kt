package app.forjadecolor.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.platform.MAX_BACKUP_BYTES

/** Backup export/import through the system file picker, the backup reminder and the AI instructions; state lives in [SettingsViewModel]. */
@Composable
fun SettingsScreen(
    container: AppContainer,
    /** Largest backup accepted; tests lower it instead of building a 100 MB file. */
    maxBackupBytes: Long = MAX_BACKUP_BYTES,
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val vm = viewModel { SettingsViewModel(container, maxBackupBytes) }
    LaunchedEffect(Unit) { vm.load() }
    val state by vm.state.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(resolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickedForImport(resolver, uri)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_settings"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        val loaded = state.remind ?: return@Column
        if (loaded) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().testTag("backup_reminder"),
            ) { Text(stringResource(R.string.settings_backup_reminder), Modifier.padding(16.dp)) }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch(suggestedBackupName(container.now())) }, modifier = Modifier.fillMaxWidth().testTag("export")) {
                    Text(stringResource(R.string.settings_export))
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth().testTag("import"),
                ) { Text(stringResource(R.string.settings_import)) }
                state.status?.let { StatusText(it) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_brain), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_brain_help))
                OutlinedButton(
                    onClick = {
                        vm.copyBrain { text ->
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_brain), text))
                        }
                    },
                    modifier = Modifier.testTag("copy_brain"),
                ) { Text(stringResource(R.string.settings_copy_brain)) }
                if (state.copied) Text(stringResource(R.string.settings_copied), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }

    if (state.pendingImport != null) {
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            text = { Text(stringResource(R.string.settings_import_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = vm::confirmImport,
                    modifier = Modifier.testTag("import_confirm"),
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelImport, modifier = Modifier.testTag("import_cancel")) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun StatusText(status: BackupStatus) {
    val live = Modifier.testTag("import_status").semantics { liveRegion = LiveRegionMode.Polite }
    when (status) {
        BackupStatus.Imported -> Text(stringResource(R.string.settings_import_ok), live)
        BackupStatus.Exported -> Text(stringResource(R.string.settings_export_ok), live)
        BackupStatus.ExportFailed -> Text(stringResource(R.string.settings_export_failed), live, color = MaterialTheme.colorScheme.error)
        BackupStatus.TooBig -> Text(stringResource(R.string.settings_backup_too_big), live, color = MaterialTheme.colorScheme.error)
        is BackupStatus.Error -> Text(status.message, live, color = MaterialTheme.colorScheme.error)
    }
}
