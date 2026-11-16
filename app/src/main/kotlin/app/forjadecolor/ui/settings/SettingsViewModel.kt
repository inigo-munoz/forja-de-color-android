package app.forjadecolor.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forjadecolor.AppContainer
import app.forjadecolor.domain.advice.buildBrainInstructions
import app.forjadecolor.domain.storage.ImportResult
import app.forjadecolor.domain.storage.MetaKey
import app.forjadecolor.domain.storage.importAll
import app.forjadecolor.domain.storage.parseBackupText
import app.forjadecolor.domain.storage.shouldRemindBackup
import app.forjadecolor.domain.storage.validateBackup
import app.forjadecolor.platform.FileTooLargeException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** What the last export or import said; errors are the domain's own messages, as in the PWA. */
sealed interface BackupStatus {
    data object Imported : BackupStatus
    data object Exported : BackupStatus
    data object ExportFailed : BackupStatus
    data object TooBig : BackupStatus
    data class Error(val message: String) : BackupStatus
}

data class SettingsState(
    /** Null until loaded. */
    val remind: Boolean? = null,
    val status: BackupStatus? = null,
    /** A valid backup waiting for the user to confirm it replaces everything. */
    val pendingImport: JsonElement? = null,
    val copied: Boolean = false,
)

/** Backup export and import, the backup reminder and the AI instructions, for [SettingsScreen]. */
class SettingsViewModel(
    private val container: AppContainer,
    /** Largest backup accepted. */
    private val maxBackupBytes: Long,
) : ViewModel() {
    private val repo = container.repository
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    suspend fun load() = refreshReminder()

    private suspend fun refreshReminder() {
        val remind = shouldRemindBackup(repo.getMeta(MetaKey.LAST_EXPORT_AT), container.now())
        _state.update { it.copy(remind = remind) }
    }

    /** Writes the backup to the file the user picked. */
    fun export(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val ok = exportBackup(resolver, uri, repo, container.now())
            _state.update { it.copy(status = if (ok) BackupStatus.Exported else BackupStatus.ExportFailed) }
            refreshReminder()
        }
    }

    /** Validates the picked file before asking, as the PWA does: an invalid file never reaches the confirmation. */
    fun pickedForImport(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val text = try {
                readBackupText(resolver, uri, maxBackupBytes)
            } catch (e: FileTooLargeException) {
                _state.update { it.copy(status = BackupStatus.TooBig) }
                return@launch
            }
            val parsed = parseBackupText(text.orEmpty())
            val invalid = validateBackup(parsed)
            _state.update { if (invalid != null) it.copy(status = BackupStatus.Error(invalid)) else it.copy(pendingImport = parsed) }
        }
    }

    fun cancelImport() = _state.update { it.copy(pendingImport = null) }

    /** Replaces all the data with the confirmed backup. */
    fun confirmImport() {
        val file = _state.value.pendingImport ?: return
        cancelImport()
        viewModelScope.launch {
            val status = when (val result = importAll(repo, file)) {
                ImportResult.Ok -> BackupStatus.Imported
                is ImportResult.Failed -> BackupStatus.Error(result.error)
            }
            _state.update { it.copy(status = status) }
            refreshReminder()
        }
    }

    /** Builds the AI instructions from the inventory and hands them to [copy] (the clipboard). */
    fun copyBrain(copy: (String) -> Unit) {
        viewModelScope.launch {
            copy(buildBrainInstructions(repo.getInventory()))
            _state.update { it.copy(copied = true) }
        }
    }
}
