package app.forjadecolor.ui.settings

import android.content.ContentResolver
import android.net.Uri
import app.forjadecolor.domain.isoString
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.storage.BackupFile
import app.forjadecolor.domain.storage.Repository
import app.forjadecolor.domain.storage.backupFileName
import app.forjadecolor.domain.storage.encodeBackup
import app.forjadecolor.domain.storage.exportAll
import app.forjadecolor.platform.FileTooLargeException
import app.forjadecolor.platform.MAX_BACKUP_BYTES
import app.forjadecolor.platform.readAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

// File I/O for the backup, kept out of the composables so it can be tested with plain streams.

/** The name offered in the save dialog, from [backupFileName] without exporting (so a cancelled picker records nothing). */
fun suggestedBackupName(now: Instant): String = backupFileName(
    BackupFile(exportedAt = isoString(now), inventory = Inventory(emptyList(), emptyList()), projects = emptyList(), blobs = emptyMap()),
)

/** Exports everything ([exportAll] records `lastExportAt`) and writes the PWA-format JSON to [uri]. */
suspend fun exportBackup(resolver: ContentResolver, uri: Uri, repo: Repository, now: Instant): Boolean {
    val text = encodeBackup(exportAll(repo, now))
    return withContext(Dispatchers.IO) {
        try {
            val out = resolver.openOutputStream(uri, "wt") ?: return@withContext false
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}

/**
 * The picked file's text, or null when it cannot be read (validation then says "not a backup file").
 * A file over [maxBytes] throws [FileTooLargeException] instead, so the screen can say so.
 */
suspend fun readBackupText(resolver: ContentResolver, uri: Uri, maxBytes: Long = MAX_BACKUP_BYTES): String? = withContext(Dispatchers.IO) {
    try {
        resolver.openInputStream(uri)?.use { it.readAtMost(maxBytes).toString(Charsets.UTF_8) }
    } catch (e: FileTooLargeException) {
        throw e
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }
}
