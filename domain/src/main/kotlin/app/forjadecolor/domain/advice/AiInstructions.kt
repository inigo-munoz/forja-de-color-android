package app.forjadecolor.domain.advice

import java.security.MessageDigest

/** Whether the AI instructions pasted into the user's Claude project or custom GPT match today's. */
enum class InstructionsStatus { NEVER, CURRENT, STALE }

/** SHA-256 of [instructions] in hex: equal text, equal fingerprint. Stored instead of the text itself. */
fun instructionsFingerprint(instructions: String): String =
    MessageDigest.getInstance("SHA-256").digest(instructions.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Compares the fingerprint of the last copied instructions, null if never copied, with [current]. */
fun instructionsStatus(copiedFingerprint: String?, current: String): InstructionsStatus = when (copiedFingerprint) {
    null -> InstructionsStatus.NEVER
    instructionsFingerprint(current) -> InstructionsStatus.CURRENT
    else -> InstructionsStatus.STALE
}
