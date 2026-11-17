package app.forjadecolor.domain.paints

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColourWheelTest {
    private fun paint(code: String, hex: String, type: PaintType = PaintType.OPAQUE) =
        Paint("Vallejo", code, code, "Model Color", type, hex)

    @Test
    fun `places pure colours on the painter's RYB wheel`() {
        assertEquals(WheelColour.RED, wheelColourOf("#ff0000"))
        assertEquals(WheelColour.ORANGE, wheelColourOf("#ff8000"))
        assertEquals(WheelColour.YELLOW, wheelColourOf("#ffff00"))
        assertEquals(WheelColour.GREEN, wheelColourOf("#00ff00"))
        assertEquals(WheelColour.BLUE, wheelColourOf("#0000ff"))
        assertEquals(WheelColour.VIOLET, wheelColourOf("#8000ff"))
        assertEquals(WheelColour.RED_ORANGE, wheelColourOf("#FF4000"))
        assertEquals(WheelColour.YELLOW_GREEN, wheelColourOf("#80ff00"))
    }

    @Test
    fun `greys, black, white and unreadable colours have no place on the wheel`() {
        listOf("#808080", "#000000", "#ffffff", "#1a1a1a", "#7a7570", "red", "").forEach {
            assertNull(wheelColourOf(it), it)
        }
    }

    @Test
    fun `harmonies follow the wheel - opposite, neighbours and triad`() {
        assertEquals(WheelColour.GREEN, WheelColour.RED.complementary)
        assertEquals(listOf(WheelColour.RED_VIOLET, WheelColour.RED_ORANGE), WheelColour.RED.analogous)
        assertEquals(listOf(WheelColour.YELLOW, WheelColour.BLUE), WheelColour.RED.triad)
        assertEquals(WheelColour.VIOLET, WheelColour.YELLOW.complementary)
        assertEquals(WheelColour.ORANGE, WheelColour.BLUE.complementary)
        assertEquals(listOf(WheelColour.VIOLET, WheelColour.RED), WheelColour.RED_VIOLET.analogous)
    }

    @Test
    fun `each colour knows if it is primary, secondary or tertiary`() {
        assertEquals(WheelKind.PRIMARY, WheelColour.BLUE.kind)
        assertEquals(WheelKind.SECONDARY, WheelColour.GREEN.kind)
        assertEquals(WheelKind.TERTIARY, WheelColour.BLUE_GREEN.kind)
    }

    @Test
    fun `the user's paints go to their colour, neutrals and metallics apart, mediums left out`() {
        val red = paint("70.926", "#b01c1c")
        val wash = paint("73.200", "#c81e1e", PaintType.WASH)
        val green = paint("70.890", "#2e6b2e")
        val black = paint("70.950", "#141414")
        val gold = paint("70.996", "#d4af37", PaintType.METALLIC)
        val medium = paint("70.470", "#ffffff", PaintType.MEDIUM)

        val wheel = placeOnWheel(listOf(red, green, black, gold, medium, wash))

        assertEquals(listOf(red, wash), wheel.paintsOf(WheelColour.RED))
        assertEquals(listOf(green), wheel.paintsOf(WheelColour.GREEN))
        assertEquals(emptyList(), wheel.paintsOf(WheelColour.BLUE))
        assertEquals(listOf(black, gold), wheel.neutrals)
    }

    @Test
    fun `groups paints by wheel colour in wheel order, then neutrals, then mediums, skipping empty groups`() {
        val green = paint("70.890", "#2e6b2e")
        val red = paint("70.926", "#b01c1c")
        val black = paint("70.950", "#141414")
        val medium = paint("70.470", "#ffffff", PaintType.MEDIUM)
        val wash = paint("73.200", "#c81e1e", PaintType.WASH)
        val gold = paint("70.996", "#d4af37", PaintType.METALLIC)

        val groups = groupByColour(listOf(green, red, black, medium, wash, gold))

        assertEquals(
            listOf(
                ColourGroup.Hue(WheelColour.RED) to listOf(red, wash),
                ColourGroup.Hue(WheelColour.GREEN) to listOf(green),
                ColourGroup.Neutrals to listOf(black, gold),
                ColourGroup.Mediums to listOf(medium),
            ),
            groups,
        )
        assertEquals(emptyList(), groupByColour(emptyList()))
    }
}
