package app.forjadecolor.ui.theme

import androidx.compose.ui.graphics.Color

/** Warm "forge" palette: ember orange on charcoal. Text colours keep at least 4.5:1 against [Charcoal]. */
object ForgeColors {
    /** Same as `@color/forge_dark`, the window background, so there is no flash before the first frame. */
    val Charcoal = Color(0xFF1A1412)
    val Soot = Color(0xFF241C19)
    val Iron = Color(0xFF2E2420)
    val IronHigh = Color(0xFF3A2E29)
    val Ember = Color(0xFFFF8A4C)
    val EmberDeep = Color(0xFF7A3416)
    val Brass = Color(0xFFE8B04A)
    val Ash = Color(0xFFF3E9E2)
    val AshDim = Color(0xFFCBB9AE)
    val Outline = Color(0xFF8A766B)
    val OutlineDim = Color(0xFF4A3C35)
    val Error = Color(0xFFFFB4A9)
    val OnError = Color(0xFF690005)
}
