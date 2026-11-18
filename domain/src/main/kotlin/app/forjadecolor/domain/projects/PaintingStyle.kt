package app.forjadecolor.domain.projects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How the miniature is painted. [MY_METHOD] is the quick, showy BSL / slapchop method the app was built for. */
@Serializable
enum class PaintingStyle {
    @SerialName("my-method") MY_METHOD,
    @SerialName("nmm") NMM,
    @SerialName("osl") OSL,
    @SerialName("vivid") VIVID,
    @SerialName("grimdark") GRIMDARK,
}

/** Optional setting for the base (snow, sand…). No setting is the default. */
@Serializable
enum class BaseSetting {
    @SerialName("winter") WINTER,
    @SerialName("desert") DESERT,
    @SerialName("fantasy") FANTASY,
}

/** The style and base setting a prompt follows. */
data class Look(
    val style: PaintingStyle = PaintingStyle.MY_METHOD,
    val baseSetting: BaseSetting? = null,
    /** Free-text theme for the base ("lava", "graveyard"…), alone or with a setting. */
    val baseMotif: String? = null,
)

/** Longest base motif kept. */
const val MAX_MOTIF = 100

/** A motif as stored: trimmed, without a final full stop, at most [MAX_MOTIF] characters; empty → null. Same as the PWA. */
fun cleanMotif(m: String?): String? = m?.trim()?.trimEnd('.')?.take(MAX_MOTIF)?.trim()?.ifEmpty { null }

// With a themed base the advisor may go beyond the paint inventory, for basing materials only.
private const val BASE_MATERIALS = "BASE MATERIALS: you may also recommend basing materials for this base (sand, static grass, snow, pigments, tufts…) in a top-level \"baseMaterials\" list of short strings; I may not have them."
private const val IMAGE_TERRAIN = "On the base only, you may add terrain that fits it (snow, rocks, plants, effects); leave the miniature itself exactly as it is."

// Prompt text: same as the PWA (src/projects/style.ts). My method adds nothing, so the prompts and
// the AI instructions stay as they were.
private fun adviceStyle(s: PaintingStyle): String? = when (s) {
    PaintingStyle.MY_METHOD -> null
    PaintingStyle.NMM -> "STYLE: NMM (non-metallic metal). Paint every metal with non-metallic paints only, faking the shine with strong light-to-dark gradients and sharp highlights."
    PaintingStyle.OSL -> "STYLE: OSL (object source lighting). Choose one glowing light source on the miniature and tint the nearby surfaces with its colour, brightest closest to it."
    PaintingStyle.VIVID -> "STYLE: Vivid · High-Fantasy. Bright, saturated colours like an animated film, with strong contrast between zones."
    PaintingStyle.GRIMDARK -> "STYLE: Grimdark. Muted, desaturated colours with wear, dirt, rust and battle damage."
}

private fun adviceBase(b: BaseSetting): String = when (b) {
    BaseSetting.WINTER -> "BASE SETTING: winter. Snow, ice and cold tones on the base."
    BaseSetting.DESERT -> "BASE SETTING: desert. Sand, dry earth and warm tones on the base."
    BaseSetting.FANTASY -> "BASE SETTING: fantasy. Magical, unreal colours on the base."
}

private fun imageStyle(s: PaintingStyle): String? = when (s) {
    PaintingStyle.MY_METHOD -> null
    PaintingStyle.NMM -> "Painting style: NMM — metals painted with flat colours and sharp highlights instead of metallic paint."
    PaintingStyle.OSL -> "Painting style: OSL — a glowing light source on the miniature tints the nearby surfaces."
    PaintingStyle.VIVID -> "Painting style: vivid high fantasy — bright, saturated colours like an animated film."
    PaintingStyle.GRIMDARK -> "Painting style: grimdark — muted colours with wear, dirt and battle damage."
}

private fun imageBase(b: BaseSetting): String = when (b) {
    BaseSetting.WINTER -> "Base: winter ground with snow and ice."
    BaseSetting.DESERT -> "Base: desert sand and dry earth."
    BaseSetting.FANTASY -> "Base: magical ground with unreal colours."
}

/** Lines for the advisor prompt; empty for my method without a base setting. */
fun adviceLookLines(look: Look): List<String> = buildList {
    adviceStyle(look.style)?.let {
        add(it)
        add("Keep my method above for speed, but follow this style in the colours, the steps and the tips.")
    }
    look.baseSetting?.let { add(adviceBase(it)) }
    val motif = cleanMotif(look.baseMotif)
    motif?.let { add("BASE MOTIF: $it.") }
    if (look.baseSetting != null || motif != null) add(BASE_MATERIALS)
}

/** Lines for the image prompt; empty for my method without a base setting. */
fun imageLookLines(look: Look): List<String> {
    val motif = cleanMotif(look.baseMotif)
    val terrain = if (look.baseSetting != null || motif != null) IMAGE_TERRAIN else null
    return listOfNotNull(imageStyle(look.style), look.baseSetting?.let(::imageBase), motif?.let { "Base theme: $it." }, terrain)
}
