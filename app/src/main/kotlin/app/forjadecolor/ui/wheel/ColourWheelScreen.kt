package app.forjadecolor.ui.wheel

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.WheelColour
import app.forjadecolor.domain.paints.WheelKind
import app.forjadecolor.domain.paints.WheelPaints
import app.forjadecolor.domain.paints.placeOnWheel
import app.forjadecolor.ui.components.ColourDot
import app.forjadecolor.ui.components.Swatch
import app.forjadecolor.ui.inventory.InventoryViewModel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The painter's colour wheel. Tapping a colour outlines it, brings its harmonies forward and dims
 * the rest; below, each harmony lists the user's own paints of that colour.
 */
@Composable
fun ColourWheelScreen(container: AppContainer) {
    val vm = viewModel { InventoryViewModel(container) }
    LaunchedEffect(Unit) { vm.load() }
    val inventory by vm.inventory.collectAsState()
    val wheel = remember(inventory) { inventory?.let { placeOnWheel(it.paints) } }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { name -> WheelColour.entries.firstOrNull { it.name == name } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_wheel"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.wheel_title), style = MaterialTheme.typography.headlineSmall)
        if (selected == null) {
            Text(
                stringResource(R.string.wheel_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("wheel_hint"),
            )
        }
        ColourWheel(selected, onSelect = { selectedName = it.name }, modifier = Modifier.align(Alignment.CenterHorizontally))
        wheel?.let { w ->
            selected?.let { Harmonies(it, w) }
            Neutrals(w.neutrals)
        }
    }
}

private val WHEEL_SIZE = 280.dp
private val OUTER = 118.dp
private val POP = 14.dp
private val HOLE = 48.dp
private val TAP_TARGET = 44.dp

/** Centre of sector [i] in degrees, Compose style (0 = right, clockwise): red sits at the top. */
private fun centreAngle(i: Int) = -90.0 + i * 30.0

@Composable
private fun ColourWheel(selected: WheelColour?, onSelect: (WheelColour) -> Unit, modifier: Modifier = Modifier) {
    val highlighted = selected?.let { setOf(it, it.complementary) + it.analogous + it.triad }.orEmpty()
    val hole = MaterialTheme.colorScheme.background
    val outline = MaterialTheme.colorScheme.onBackground
    Box(modifier.size(WHEEL_SIZE)) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { tap ->
                    val dx = tap.x - size.width / 2f
                    val dy = tap.y - size.height / 2f
                    val distance = hypot(dx, dy)
                    if (distance < HOLE.toPx() || distance > (OUTER + POP).toPx()) return@detectTapGestures
                    val angle = atan2(dy, dx) * 180.0 / PI
                    val index = floor(((angle + 90.0 + 15.0).mod(360.0)) / 30.0).toInt()
                    onSelect(WheelColour.entries[index])
                }
            },
        ) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            WheelColour.entries.forEachIndexed { i, colour ->
                val lit = selected == null || colour in highlighted
                val radius = (if (selected != null && lit) OUTER + POP else OUTER).toPx()
                val start = (centreAngle(i) - 15.0).toFloat()
                val topLeft = Offset(centre.x - radius, centre.y - radius)
                val arc = Size(radius * 2, radius * 2)
                drawArc(wheelColor(colour).copy(alpha = if (lit) 1f else 0.25f), start + 0.5f, 29f, useCenter = true, topLeft = topLeft, size = arc)
                if (colour == selected) {
                    drawArc(outline, start + 0.5f, 29f, useCenter = true, topLeft = topLeft, size = arc, style = Stroke(3.dp.toPx()))
                }
            }
            drawCircle(hole, HOLE.toPx(), centre)
        }
        // One tap target per colour, so TalkBack and tests can reach each sector by name.
        val middle = (HOLE + OUTER) / 2
        WheelColour.entries.forEachIndexed { i, colour ->
            val a = centreAngle(i) * PI / 180.0
            val name = stringResource(colourName(colour))
            Box(
                Modifier
                    .offset(
                        x = WHEEL_SIZE / 2 + middle * cos(a).toFloat() - TAP_TARGET / 2,
                        y = WHEEL_SIZE / 2 + middle * sin(a).toFloat() - TAP_TARGET / 2,
                    )
                    .size(TAP_TARGET)
                    .clip(CircleShape)
                    .clickable { onSelect(colour) }
                    .semantics {
                        contentDescription = name
                        this.selected = colour == selected
                    }
                    .testTag("sector_${colour.name}"),
            )
        }
    }
}

@Composable
private fun Harmonies(selected: WheelColour, wheel: WheelPaints) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.testTag("wheel_selected"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ColourDot(wheelColor(selected), tag = "wheel_selected_dot", size = 36.dp)
                Column {
                    Text(stringResource(colourName(selected)), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(kindLabel(selected.kind)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.wheel_your_paints), style = MaterialTheme.typography.titleSmall)
            PaintList(wheel.paintsOf(selected))
        }
        Harmony("harmony_complementary", R.string.wheel_complementary, R.string.wheel_complementary_hint, listOf(selected.complementary), wheel)
        Harmony("harmony_analogous", R.string.wheel_analogous, R.string.wheel_analogous_hint, selected.analogous, wheel)
        Harmony("harmony_triad", R.string.wheel_triad, R.string.wheel_triad_hint, selected.triad, wheel)
    }
}

@Composable
private fun Harmony(tag: String, @StringRes title: Int, @StringRes hint: Int, colours: List<WheelColour>, wheel: WheelPaints) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        colours.forEach { colour ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ColourDot(wheelColor(colour), tag = "harmony_dot", size = 24.dp)
                Text(stringResource(colourName(colour)), style = MaterialTheme.typography.titleSmall)
            }
            PaintList(wheel.paintsOf(colour), Modifier.padding(start = 32.dp))
        }
    }
}

@Composable
private fun Neutrals(paints: List<Paint>) {
    Column(Modifier.fillMaxWidth().testTag("wheel_neutrals"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.wheel_neutrals), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.wheel_neutrals_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PaintList(paints)
    }
}

@Composable
private fun PaintList(paints: List<Paint>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (paints.isEmpty()) {
            Text(stringResource(R.string.wheel_no_paints), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        paints.forEach { p ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Swatch(p.hex)
                Text(p.code, fontFamily = FontFamily.Monospace)
                Text(p.name)
            }
        }
    }
}

/** The classic painter's wheel swatches. */
private fun wheelColor(colour: WheelColour): Color = Color(
    when (colour) {
        WheelColour.RED -> 0xFFFE2712
        WheelColour.RED_ORANGE -> 0xFFFC600A
        WheelColour.ORANGE -> 0xFFFB9902
        WheelColour.YELLOW_ORANGE -> 0xFFFCCC1A
        WheelColour.YELLOW -> 0xFFFEFE33
        WheelColour.YELLOW_GREEN -> 0xFFB2D732
        WheelColour.GREEN -> 0xFF66B032
        WheelColour.BLUE_GREEN -> 0xFF347C98
        WheelColour.BLUE -> 0xFF0247FE
        WheelColour.BLUE_VIOLET -> 0xFF4424D6
        WheelColour.VIOLET -> 0xFF8601AF
        WheelColour.RED_VIOLET -> 0xFFC21460
    },
)

@StringRes
private fun colourName(colour: WheelColour): Int = when (colour) {
    WheelColour.RED -> R.string.wheel_red
    WheelColour.RED_ORANGE -> R.string.wheel_red_orange
    WheelColour.ORANGE -> R.string.wheel_orange
    WheelColour.YELLOW_ORANGE -> R.string.wheel_yellow_orange
    WheelColour.YELLOW -> R.string.wheel_yellow
    WheelColour.YELLOW_GREEN -> R.string.wheel_yellow_green
    WheelColour.GREEN -> R.string.wheel_green
    WheelColour.BLUE_GREEN -> R.string.wheel_blue_green
    WheelColour.BLUE -> R.string.wheel_blue
    WheelColour.BLUE_VIOLET -> R.string.wheel_blue_violet
    WheelColour.VIOLET -> R.string.wheel_violet
    WheelColour.RED_VIOLET -> R.string.wheel_red_violet
}

@StringRes
private fun kindLabel(kind: WheelKind): Int = when (kind) {
    WheelKind.PRIMARY -> R.string.wheel_kind_primary
    WheelKind.SECONDARY -> R.string.wheel_kind_secondary
    WheelKind.TERTIARY -> R.string.wheel_kind_tertiary
}
