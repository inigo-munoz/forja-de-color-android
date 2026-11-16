package app.forjadecolor.domain.advice

import app.forjadecolor.domain.paints.PaintRef
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Moment(val wire: String) { START("start"), PRIMED("primed"), HALF_PAINTED("half-painted"), DURING("during") }

enum class Question(val wire: String) {
    APPLY_LAYER("apply-layer"),
    LIGHT("light"),
    SHADOW("shadow"),
    HIGHLIGHT("highlight"),
    WHICH_TECHNIQUE("which-technique"),
    FREE("free"),
}

@Serializable
enum class TechniqueCardId(val wire: String) {
    @SerialName("slapchop") SLAPCHOP("slapchop"),
    @SerialName("dilution") DILUTION("dilution"),
    @SerialName("bsl") BSL("bsl"),
    @SerialName("drybrush") DRYBRUSH("drybrush"),
}

@Serializable
enum class RecipeMoment(val wire: String) {
    @SerialName("start") START("start"),
    /** The miniature is already primed and drybrushed: prep is empty. */
    @SerialName("primed") PRIMED("primed"),
    @SerialName("half-painted") HALF_PAINTED("half-painted"),
}

@Serializable
enum class Technique {
    @SerialName("slapchop") SLAPCHOP,
    @SerialName("bsl") BSL,
}

@Serializable
data class Bsl(val base: PaintRef, val shadow: PaintRef, val light: PaintRef)

@Serializable
data class ZoneRecipe(
    val name: String,
    val technique: Technique,
    /** Slapchop: required unless already painted, with a dilution "N:M" (paint:medium). */
    val paint: PaintRef? = null,
    val dilution: String? = null,
    /** BSL: required unless already painted. */
    val bsl: Bsl? = null,
    val steps: List<String>,
    val brush: String? = null,
    val alreadyPainted: Boolean? = null,
)

@Serializable
sealed interface AdviceResponse {
    val version: Int
}

@Serializable
@SerialName("recipe")
data class RecipeResponse(
    override val version: Int = 1,
    val moment: RecipeMoment,
    val prep: List<String>,
    val zones: List<ZoneRecipe>,
    val tips: List<String>,
    /** half-painted only */
    val complements: List<PaintRef>? = null,
    val remainingSteps: List<String>? = null,
) : AdviceResponse

@Serializable
data class ConsultationPaint(val paint: PaintRef, val dilution: String? = null)

@Serializable
@SerialName("consultation")
data class ConsultationResponse(
    override val version: Int = 1,
    val zone: String,
    /** Kept as text: the PWA accepts any question the AI echoes back. */
    val question: String,
    val technique: String,
    val steps: List<String>,
    val paints: List<ConsultationPaint>,
    val brush: String? = null,
    val techniqueCard: TechniqueCardId? = null,
) : AdviceResponse

@Serializable
data class AdviceEntry(
    val id: String,
    val receivedAt: String,
    val response: AdviceResponse,
    val unknownPaints: List<PaintRef>,
    val unknownBrushes: List<String>,
    /**
     * Recipes only: indices into the recipe's zones the user ticked off as painted, sorted and unique.
     * Optional in the backup (absent means none) and never written when empty, like the PWA.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val doneZones: List<Int> = emptyList(),
)

data class AdviceRequest(
    val moment: Moment,
    val zone: String? = null,
    val question: Question? = null,
    val freeText: String? = null,
)

/** What is shared with the user's AI chat: the prompt and the id of the stored photo. */
data class AdvicePackage(val text: String, val imageId: String)
