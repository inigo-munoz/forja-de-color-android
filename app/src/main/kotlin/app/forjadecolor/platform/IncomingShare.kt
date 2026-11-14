package app.forjadecolor.platform

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Something another app shared into Forja de Color: the AI's answer, or the painted image. */
sealed interface IncomingShare {
    data class Text(val text: String) : IncomingShare
    data class Image(val uri: Uri) : IncomingShare
}

/** Reads an `ACTION_SEND` of text or an image; anything else is null. */
fun parseIncomingShare(intent: Intent?): IncomingShare? {
    if (intent?.action != Intent.ACTION_SEND) return null
    val type = intent.type.orEmpty()
    return when {
        type.startsWith("text/") ->
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { IncomingShare.Text(it) }
        type.startsWith("image/") ->
            (IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri)
                ?.let { IncomingShare.Image(it) }
        else -> null
    }
}

/** The latest share waiting for a screen to route it; a screen takes it with [consume] so it is handled once. */
class IncomingShares {
    private val state = MutableStateFlow<IncomingShare?>(null)
    val current: StateFlow<IncomingShare?> = state.asStateFlow()

    fun offer(share: IncomingShare?) {
        if (share != null) state.value = share
    }

    fun consume(): IncomingShare? = state.getAndUpdate { null }
}
