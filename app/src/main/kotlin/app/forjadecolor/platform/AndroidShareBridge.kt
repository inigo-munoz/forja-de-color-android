package app.forjadecolor.platform

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.forjadecolor.domain.advice.AdvicePackage
import app.forjadecolor.domain.advice.AdvisorBridge
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.storage.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** FileProvider authority declared in the manifest. */
fun fileAuthority(context: Context) = "${context.packageName}.files"

/**
 * AdvisorBridge over the Android share sheet: the photo goes as a content URI and the prompt as text.
 * The prompt is put on the clipboard first because some share targets keep the image and drop the text.
 */
class AndroidShareBridge(context: Context, private val repository: Repository) : AdvisorBridge {
    private val context = context.applicationContext
    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override suspend fun send(pkg: AdvicePackage): SendResult {
        copyPrompt(pkg.text)
        val blob = repository.getBlob(pkg.imageId) ?: return SendResult.FALLBACK
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "share").apply { mkdirs() }.resolve(SHARED_NAME).apply { writeBytes(blob.bytes) }
        }
        val uri = FileProvider.getUriForFile(context, fileAuthority(context), file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, pkg.text)
            clipData = ClipData.newRawUri(SHARED_NAME, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(share, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            SendResult.SHARED
        } catch (e: ActivityNotFoundException) {
            SendResult.FALLBACK // the prompt is already on the clipboard
        }
    }

    override fun readAnswer(): String? = try {
        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotEmpty() }
    } catch (e: SecurityException) {
        null // the system refused clipboard access: the user pastes by hand
    }

    private fun copyPrompt(text: String) {
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(PROMPT_LABEL, text))
        } catch (e: SecurityException) {
            // Not fatal: the share itself still carries the text, and the screen shows it to copy.
        }
    }

    private companion object {
        const val SHARED_NAME = "forja-de-color.jpg"
        const val PROMPT_LABEL = "Forja de Color prompt"
    }
}
