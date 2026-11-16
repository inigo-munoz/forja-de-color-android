package app.forjadecolor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val HEX = Regex("^#[0-9a-fA-F]{6}$")

/** A paint's approximate colour as a small outlined circle; transparent when [hex] is unreadable. */
@Composable
fun Swatch(hex: String, tag: String = "swatch", size: Dp = 20.dp) {
    val color = remember(hex) { parseHex(hex) }
    ColourDot(color ?: Color.Transparent, tag, size)
}

/** An outlined circle of [color]. */
@Composable
fun ColourDot(color: Color, tag: String = "swatch", size: Dp = 20.dp) {
    Box(
        Modifier.size(size).background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).testTag(tag),
    )
}

/** '#rrggbb' as a colour, or null when it is not one. */
fun parseHex(hex: String): Color? =
    if (HEX.matches(hex.trim())) Color(0xFF000000 or hex.trim().drop(1).toLong(16)) else null
