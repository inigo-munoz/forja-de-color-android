package app.forjadecolor.domain.advice

/** How a package reached the AI: through the system share sheet, or copied for the user to paste by hand. */
enum class SendResult { SHARED, FALLBACK }

/** Port: hands an advice package to the user's own AI app and reads its answer back. Never calls a network API. */
interface AdvisorBridge {
    suspend fun send(pkg: AdvicePackage): SendResult

    /** The answer the user copied from the AI app, or null when nothing can be read. */
    fun readAnswer(): String?
}
