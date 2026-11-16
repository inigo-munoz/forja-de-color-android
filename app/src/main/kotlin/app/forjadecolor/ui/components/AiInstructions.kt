package app.forjadecolor.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import app.forjadecolor.R
import app.forjadecolor.domain.advice.InstructionsStatus
import app.forjadecolor.domain.advice.buildBrainInstructions
import app.forjadecolor.domain.advice.instructionsFingerprint
import app.forjadecolor.domain.advice.instructionsStatus
import app.forjadecolor.domain.storage.MetaKey
import app.forjadecolor.domain.storage.Repository

/** Whether the AI instructions last copied match the ones built from today's inventory. */
suspend fun aiInstructionsStatus(repo: Repository): InstructionsStatus =
    instructionsStatus(repo.getMeta(MetaKey.AI_INSTRUCTIONS_COPIED), buildBrainInstructions(repo.getInventory()))

/**
 * The one copy action, shared by Settings and Ask: builds the AI instructions from the inventory,
 * hands them to [copy] (the clipboard) and remembers their fingerprint, so a later change can be noticed.
 */
suspend fun copyAiInstructions(repo: Repository, copy: (String) -> Unit) {
    val text = buildBrainInstructions(repo.getInventory())
    copy(text)
    repo.setMeta(MetaKey.AI_INSTRUCTIONS_COPIED, instructionsFingerprint(text))
}

/** Puts the AI instructions on the system clipboard, labelled as such. */
fun clipboardCopier(context: Context): (String) -> Unit = { text ->
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.settings_brain), text))
}
