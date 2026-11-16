package app.forjadecolor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ForgeScheme = darkColorScheme(
    primary = ForgeColors.Ember,
    onPrimary = ForgeColors.Charcoal,
    primaryContainer = ForgeColors.EmberDeep,
    onPrimaryContainer = ForgeColors.Ash,
    secondary = ForgeColors.Brass,
    onSecondary = ForgeColors.Charcoal,
    secondaryContainer = ForgeColors.IronHigh,
    onSecondaryContainer = ForgeColors.Ash,
    background = ForgeColors.Charcoal,
    onBackground = ForgeColors.Ash,
    surface = ForgeColors.Charcoal,
    onSurface = ForgeColors.Ash,
    surfaceVariant = ForgeColors.Iron,
    onSurfaceVariant = ForgeColors.AshDim,
    surfaceContainerLowest = ForgeColors.Charcoal,
    surfaceContainerLow = ForgeColors.Soot,
    surfaceContainer = ForgeColors.Soot,
    surfaceContainerHigh = ForgeColors.Iron,
    surfaceContainerHighest = ForgeColors.IronHigh,
    outline = ForgeColors.Outline,
    outlineVariant = ForgeColors.OutlineDim,
    error = ForgeColors.Error,
    onError = ForgeColors.OnError,
)

private val ForgeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/** Always-dark forge theme for the whole app. */
@Composable
fun ForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ForgeScheme, shapes = ForgeShapes, content = content)
}
