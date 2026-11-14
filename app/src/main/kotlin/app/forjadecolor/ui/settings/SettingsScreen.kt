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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.advice.buildBrainInstructions
import app.forjadecolor.domain.storage.ImportResult
import app.forjadecolor.domain.storage.MetaKey
import app.forjadecolor.domain.storage.importAll
import app.forjadecolor.domain.storage.parseBackupText
import app.forjadecolor.domain.storage.shouldRemindBackup
import app.forjadecolor.domain.storage.validateBackup
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** What the last export or import said; errors are the domain's own messages, as in the PWA. */
private sealed interface Status {
    data object Imported : Status
    data object ExportFailed : Status
    data class Error(val message: String) : Status
}

/** Backup export/import through the system file picker, the backup reminder and the AI instructions. */
@Composable
fun SettingsScreen(
    container: AppContainer,
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val repo = container.repository
    val scope = rememberCoroutineScope()
    var remind by remember { mutableStateOf<Boolean?>(null) }
    var reload by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<Status?>(null) }
    var pendingImport by remember { mutableStateOf<JsonElement?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(reload) { remind = shouldRemindBackup(repo.getMeta(MetaKey.LAST_EXPORT_AT), container.now()) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                if (!exportBackup(resolver, uri, repo, container.now())) status = Status.ExportFailed
                reload++
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                // Validate before asking, as the PWA does: an invalid file never reaches the confirmation.
                val parsed = parseBackupText(readBackupText(resolver, uri).orEmpty())
                val invalid = validateBackup(parsed)
                if (invalid != null) status = Status.Error(invalid) else pendingImport = parsed
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_settings"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        val loaded = remind ?: return@Column
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
                status?.let { StatusText(it) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_brain), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_brain_help))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val text = buildBrainInstructions(repo.getInventory())
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_brain), text))
                            copied = true
                        }
                    },
                    modifier = Modifier.testTag("copy_brain"),
                ) { Text(stringResource(R.string.settings_copy_brain)) }
                if (copied) Text(stringResource(R.string.settings_copied), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }

    pendingImport?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            text = { Text(stringResource(R.string.settings_import_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImport = null
                        scope.launch {
                            status = when (val result = importAll(repo, file)) {
                                ImportResult.Ok -> Status.Imported
                                is ImportResult.Failed -> Status.Error(result.error)
                            }
                            reload++
                        }
                    },
                    modifier = Modifier.testTag("import_confirm"),
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }, modifier = Modifier.testTag("import_cancel")) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun StatusText(status: Status) {
    val live = Modifier.testTag("import_status").semantics { liveRegion = LiveRegionMode.Polite }
    when (status) {
        Status.Imported -> Text(stringResource(R.string.settings_import_ok), live)
        Status.ExportFailed -> Text(stringResource(R.string.settings_export_failed), live, color = MaterialTheme.colorScheme.error)
        is Status.Error -> Text(status.message, live, color = MaterialTheme.colorScheme.error)
    }
}
