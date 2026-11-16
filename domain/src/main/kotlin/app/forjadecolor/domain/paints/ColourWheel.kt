package app.forjadecolor.domain.paints

import kotlin.math.floor

enum class WheelKind { PRIMARY, SECONDARY, TERTIARY }

/**
 * The painter's 12-colour RYB wheel (red, yellow and blue as primaries), clockwise from red.
 * Harmonies are index arithmetic: opposite +6, neighbours ±1, triad ±4.
 */
enum class WheelColour(val kind: WheelKind) {
    RED(WheelKind.PRIMARY),
    RED_ORANGE(WheelKind.TERTIARY),
    ORANGE(WheelKind.SECONDARY),
    YELLOW_ORANGE(WheelKind.TERTIARY),
    YELLOW(WheelKind.PRIMARY),
    YELLOW_GREEN(WheelKind.TERTIARY),
    GREEN(WheelKind.SECONDARY),
    BLUE_GREEN(WheelKind.TERTIARY),
    BLUE(WheelKind.PRIMARY),
    BLUE_VIOLET(WheelKind.TERTIARY),
    VIOLET(WheelKind.SECONDARY),
    RED_VIOLET(WheelKind.TERTIARY),
    ;

    private fun step(n: Int) = entries[(ordinal + n).mod(entries.size)]

    /** The opposite colour: the strongest contrast. */
    val complementary: WheelColour get() = step(6)

    /** The two neighbours, counter-clockwise first: a soft harmony. */
    val analogous: List<WheelColour> get() = listOf(step(-1), step(1))

    /** The other two corners of the triangle, clockwise first: a balanced contrast. */
    val triad: List<WheelColour> get() = listOf(step(4), step(-4))
}

private val HEX = Regex("^#[0-9a-fA-F]{6}$")

/** Below this saturation a colour reads as grey, black or white. */
private const val MIN_SATURATION = 0.2

/** Below this brightness a colour reads as black. */
private const val MIN_VALUE = 0.15

/** (screen hue, painter's wheel hue) in degrees: the RYB wheel gives yellow and orange more room. */
private val RGB_TO_RYB = listOf(0.0 to 0.0, 30.0 to 60.0, 60.0 to 120.0, 120.0 to 180.0, 180.0 to 210.0, 240.0 to 240.0, 280.0 to 300.0, 360.0 to 360.0)

/**
 * Where a '#rrggbb' colour falls on the painter's wheel; null for greys, black, white and
 * anything that is not a readable colour. The paint colours are approximate, so is this.
 */
fun wheelColourOf(hex: String): WheelColour? {
    val h = hex.trim()
    if (!HEX.matches(h)) return null
    val rgb = h.drop(1).toLong(16)
    val r = (rgb shr 16 and 0xFF) / 255.0
    val g = (rgb shr 8 and 0xFF) / 255.0
    val b = (rgb and 0xFF) / 255.0
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    if (max < MIN_VALUE || delta / max < MIN_SATURATION) return null
    val hue = when (max) {
        r -> 60 * (((g - b) / delta).mod(6.0))
        g -> 60 * ((b - r) / delta + 2)
        else -> 60 * ((r - g) / delta + 4)
    }
    val ryb = toRyb(hue)
    val sector = floor(((ryb + 15) % 360) / 30).toInt()
    return WheelColour.entries[sector]
}

private fun toRyb(hue: Double): Double {
    val (from, to) = RGB_TO_RYB.zipWithNext().first { (_, end) -> hue <= end.first }
    return from.second + (hue - from.first) / (to.first - from.first) * (to.second - from.second)
}

/** The user's paints sorted onto the wheel; [neutrals] holds those with no colour of their own. */
class WheelPaints(private val byColour: Map<WheelColour, List<Paint>>, val neutrals: List<Paint>) {
    fun paintsOf(colour: WheelColour): List<Paint> = byColour[colour].orEmpty()
}

/**
 * Sorts paints by wheel colour, keeping their order. Metallics and greys go to the neutrals;
 * mediums are not colours and are left out.
 */
fun placeOnWheel(paints: List<Paint>): WheelPaints {
    val coloured = mutableMapOf<WheelColour, MutableList<Paint>>()
    val neutrals = mutableListOf<Paint>()
    paints.filter { it.type != PaintType.MEDIUM }.forEach { p ->
        val colour = if (p.type == PaintType.METALLIC) null else wheelColourOf(p.hex)
        if (colour == null) neutrals += p else coloured.getOrPut(colour) { mutableListOf() } += p
    }
    return WheelPaints(coloured, neutrals)
}
