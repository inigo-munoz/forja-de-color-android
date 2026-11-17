package app.forjadecolor.domain.paints

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PaintType {
    @SerialName("opaque") OPAQUE,
    @SerialName("wash") WASH,
    @SerialName("metallic") METALLIC,
    @SerialName("medium") MEDIUM,
    @SerialName("primer") PRIMER,
    @SerialName("other") OTHER,
}

@Serializable
enum class BslRole {
    @SerialName("base") BASE,
    @SerialName("shadow") SHADOW,
    @SerialName("light") LIGHT,
    @SerialName("fourth") FOURTH,
}

/** Identifies a paint. `code` is always stored normalized (see [normalizeCode]). */
@Serializable
data class PaintRef(val brand: String, val code: String)

@Serializable
data class PaintFamily(
    val name: String,
    /** 1 = lightest … 4 = darkest, as printed on the BSL set. */
    val order: Int,
    /** From the user's BSL book; absent for families without roles. Read it through [bslRole]. */
    val role: BslRole? = null,
)

@Serializable
data class Paint(
    val brand: String,
    val code: String,
    val name: String,
    val range: String,
    val type: PaintType,
    /** Approximate display colour, '#rrggbb'. Only for the interface. */
    val hex: String,
    val family: PaintFamily? = null,
    /** English name, when known; [name] is the Spanish one. Null in older data: read it through [englishName]. */
    val nameEn: String? = null,
) {
    val ref: PaintRef get() = PaintRef(brand, code)
}

@Serializable
data class Brush(val code: String, val name: String)

@Serializable
data class Inventory(val paints: List<Paint>, val brushes: List<Brush>)

private val VALLEJO_DIGITS = Regex("^\\d{5}$")

fun normalizeCode(brand: String, raw: String): String {
    val trimmed = raw.trim()
    if (brand.trim().lowercase() != "vallejo") return trimmed
    val digits = trimmed.replace(Regex("\\D"), "")
    return if (VALLEJO_DIGITS.matches(digits)) "${digits.take(2)}.${digits.drop(2)}" else trimmed
}

fun paintKey(ref: PaintRef): String = "${ref.brand.trim().lowercase()}|${normalizeCode(ref.brand, ref.code)}"

fun paintKey(paint: Paint): String = paintKey(paint.ref)
