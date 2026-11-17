package app.forjadecolor.ui.inventory

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.paints.BslRole
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintType
import app.forjadecolor.domain.paints.bslRole
import app.forjadecolor.domain.paints.savePaintKeepingFamily
import app.forjadecolor.domain.storage.Repository
import app.forjadecolor.ui.components.Swatch
import app.forjadecolor.ui.components.parseHex
import kotlin.math.abs
import kotlin.math.roundToInt

private val HEX = Regex("^#[0-9a-fA-F]{6}$")

/** Paints grouped by family as they appear in the inventory, each family lightest (1) to darkest (4). */
internal data class PaintGroups(val families: List<Pair<String, List<Paint>>>, val others: List<Paint>)

internal fun groupPaints(inv: Inventory): PaintGroups {
    val families = linkedMapOf<String, MutableList<Paint>>()
    val others = mutableListOf<Paint>()
    for (p in inv.paints) {
        val family = p.family
        if (family == null) others += p else families.getOrPut(family.name) { mutableListOf() } += p
    }
    return PaintGroups(families.map { (name, paints) -> name to paints.sortedBy { it.family!!.order } }, others)
}

/** Saves the paint through [savePaintKeepingFamily]: add or replace, keeping a BSL family. */
internal suspend fun savePaint(repo: Repository, paint: Paint) {
    repo.saveInventory(repo.getInventory().savePaintKeepingFamily(paint))
}

/** The fields of the add/update form while it is open; saved across recreation by [DraftSaver]. */
internal data class PaintDraft(
    val brand: String,
    val code: String,
    val name: String,
    val range: String,
    val type: PaintType,
    val hex: String,
) {
    companion object {
        // Same defaults as the PWA form.
        val NEW = PaintDraft("Vallejo", "", "", "Game Color", PaintType.OPAQUE, "#888888")

        fun of(p: Paint) = PaintDraft(p.brand, p.code, p.name, p.range, p.type, p.hex)
    }
}

/** Null (form closed) saves nothing, so it restores closed. */
private val DraftSaver = Saver<PaintDraft?, List<String>>(
    save = { d -> d?.let { listOf(it.brand, it.code, it.name, it.range, it.type.name, it.hex) } },
    restore = { l -> PaintDraft(l[0], l[1], l[2], l[3], PaintType.valueOf(l[4]), l[5]) },
)

/** Preset swatches for the colour picker, lowercase '#rrggbb'. */
internal val COLOR_PRESETS = listOf(
    "#000000", "#ffffff", "#808080", "#8b1a1a", "#d32f2f", "#f57c00", "#fbc02d", "#c9a47f",
    "#6d4c41", "#2e7d32", "#7cb342", "#00838f", "#1565c0", "#283593", "#6a1b9a", "#c2185b",
)

/** Hue (0..360), saturation and lightness (0..1) of a valid '#rrggbb', or null. */
internal fun hexToHsl(hex: String): FloatArray? {
    if (!HEX.matches(hex.trim())) return null
    val v = hex.trim().drop(1).toInt(16)
    val r = (v shr 16 and 0xFF) / 255f
    val g = (v shr 8 and 0xFF) / 255f
    val b = (v and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val l = (max + min) / 2
    val s = if (d == 0f) 0f else d / (1 - abs(2 * l - 1))
    var h = when {
        d == 0f -> 0f
        max == r -> 60 * (((g - b) / d) % 6)
        max == g -> 60 * ((b - r) / d + 2)
        else -> 60 * ((r - g) / d + 4)
    }
    if (h < 0) h += 360
    return floatArrayOf(h, s.coerceIn(0f, 1f), l)
}

internal fun hslToHex(h: Float, s: Float, l: Float): String {
    val c = (1 - abs(2 * l - 1)) * s
    val x = c * (1 - abs((h / 60) % 2 - 1))
    val m = l - c / 2
    val (r, g, b) = when ((h / 60).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun byte(v: Float) = ((v + m) * 255).roundToInt().coerceIn(0, 255)
    return "#%02x%02x%02x".format(byte(r), byte(g), byte(b))
}

/**
 * The paints and brushes the user owns. "+ Add paint" or tapping a paint opens the add/update form
 * in a dialog; the inventory lives in [InventoryViewModel], the open form in saveable state.
 */
@Composable
fun InventoryScreen(
    container: AppContainer,
) {
    val vm = viewModel { InventoryViewModel(container) }
    LaunchedEffect(Unit) { vm.load() }
    val inventory by vm.inventory.collectAsState()
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<PaintDraft?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_inventory"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.inventory_title), style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { draft = PaintDraft.NEW }, modifier = Modifier.testTag("add_paint")) {
            Text(stringResource(R.string.inventory_add_button))
        }
        inventory?.let { inv ->
            val edit = { p: Paint -> draft = PaintDraft.of(p) }
            val groups = groupPaints(inv)
            groups.families.forEach { (name, paints) -> PaintSection(familyLabel(name), "family_$name", paints, edit) }
            if (groups.others.isNotEmpty()) PaintSection(stringResource(R.string.inventory_other_paints), "family_other", groups.others, edit)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.inventory_brushes), style = MaterialTheme.typography.titleMedium)
                inv.brushes.forEach { b ->
                    Row(Modifier.testTag("brush").semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(b.code, fontFamily = FontFamily.Monospace)
                        Text(b.name)
                    }
                }
            }
        }
    }

    draft?.let { d ->
        PaintFormDialog(
            draft = d,
            onChange = { draft = it },
            onSave = { vm.save(it); draft = null },
            onCancel = { draft = null },
        )
    }
}

@Composable
private fun PaintSection(title: String, tag: String, paints: List<Paint>, onEdit: (Paint) -> Unit) {
    Column(Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        paints.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clickable { onEdit(p) }.testTag("paint").semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Swatch(p.hex)
                Text(p.code, fontFamily = FontFamily.Monospace)
                Text(p.name)
                bslRole(p)?.let { role ->
                    Text(stringResource(roleLabel(role)), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("role"))
                }
            }
        }
    }
}

private fun roleLabel(role: BslRole): Int = when (role) {
    BslRole.BASE -> R.string.bsl_role_base
    BslRole.SHADOW -> R.string.bsl_role_shadow
    BslRole.LIGHT -> R.string.bsl_role_light
    BslRole.FOURTH -> R.string.bsl_role_fourth
}

/** The add/update form; saving goes through [onSave] (the ViewModel and [savePaint]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaintFormDialog(draft: PaintDraft, onChange: (PaintDraft) -> Unit, onSave: (Paint) -> Unit, onCancel: () -> Unit) {
    val fields = listOf(draft.brand, draft.code, draft.name, draft.range).map { it.trim() }
    val valid = fields.all { it.isNotEmpty() } && HEX.matches(draft.hex.trim())

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).testTag("paint_form"), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.inventory_add_title), style = MaterialTheme.typography.titleMedium)
                FormField(R.string.inventory_brand, draft.brand, "field_brand") { onChange(draft.copy(brand = it)) }
                FormField(R.string.inventory_code, draft.code, "field_code") { onChange(draft.copy(code = it)) }
                FormField(R.string.inventory_name, draft.name, "field_name") { onChange(draft.copy(name = it)) }
                FormField(R.string.inventory_range, draft.range, "field_range") { onChange(draft.copy(range = it)) }
                Text(stringResource(R.string.inventory_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaintType.entries.forEach { t ->
                        val wire = t.name.lowercase()
                        FilterChip(
                            selected = draft.type == t,
                            onClick = { onChange(draft.copy(type = t)) },
                            label = { Text(stringResource(paintTypeLabel(t))) },
                            modifier = Modifier.testTag("type_$wire"),
                        )
                    }
                }
                ColorPicker(draft.hex) { onChange(draft.copy(hex = it)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_paint")) { Text(stringResource(R.string.inventory_cancel)) }
                    Button(
                        enabled = valid,
                        onClick = {
                            val (b, c, n, r) = fields
                            onSave(Paint(brand = b, code = c, name = n, range = r, type = draft.type, hex = draft.hex.trim().lowercase()))
                        },
                        modifier = Modifier.testTag("save_paint"),
                    ) { Text(stringResource(R.string.inventory_add)) }
                }
            }
        }
    }
}

/**
 * Preset swatches plus hue and lightness sliders, all writing the hex; the hex stays editable for exact
 * codes and drives the preview and the sliders. A grey has no hue, so moving the hue gives it some saturation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(hex: String, onChange: (String) -> Unit) {
    val valid = HEX.matches(hex.trim())
    val hsl = remember(hex) { hexToHsl(hex) } ?: floatArrayOf(0f, 0f, 0.5f)
    Text(stringResource(R.string.inventory_hex), style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { FormField(R.string.inventory_hex, hex, "color_hex", onChange) }
        Box(
            Modifier.size(40.dp).background(parseHex(hex) ?: Color.Transparent, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .testTag("color_preview")
                .semantics { stateDescription = if (valid) hex.trim().lowercase() else "" },
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        COLOR_PRESETS.forEachIndexed { i, preset ->
            Box(
                Modifier.size(32.dp).background(parseHex(preset)!!, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onChange(preset) }
                    .testTag("color_swatch_$i")
                    .semantics { contentDescription = preset },
            )
        }
    }
    Text(stringResource(R.string.inventory_hue), style = MaterialTheme.typography.labelMedium)
    Slider(
        value = hsl[0],
        onValueChange = { h -> onChange(hslToHex(h, if (hsl[1] < 0.05f) 0.6f else hsl[1], hsl[2])) },
        valueRange = 0f..360f,
        modifier = Modifier.testTag("color_hue"),
    )
    Text(stringResource(R.string.inventory_lightness), style = MaterialTheme.typography.labelMedium)
    Slider(
        value = hsl[2],
        onValueChange = { l -> onChange(hslToHex(hsl[0], hsl[1], l)) },
        modifier = Modifier.testTag("color_lightness"),
    )
}

@Composable
private fun FormField(label: Int, value: String, tag: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/**
 * Family names are stored and sent to the AI as written in the seed ("BSL Warm Flesh"); only what is
 * shown is translated. A family the user created keeps its own name.
 */
private val FAMILY_LABELS = mapOf(
    "BSL Warm Flesh" to R.string.family_warm_flesh,
    "BSL Flesh" to R.string.family_flesh,
    "BSL Yellow" to R.string.family_yellow,
    "BSL Orange" to R.string.family_orange,
    "BSL Magenta" to R.string.family_magenta,
    "BSL Violet" to R.string.family_violet,
    "BSL Blue" to R.string.family_blue,
    "BSL Turquoise" to R.string.family_turquoise,
    "BSL Cold Green" to R.string.family_cold_green,
    "BSL Green" to R.string.family_green,
    "BSL Black" to R.string.family_black,
    "BSL Multipurpose" to R.string.family_multipurpose,
)

@Composable
private fun familyLabel(name: String): String = FAMILY_LABELS[name]?.let { stringResource(it) } ?: name

@StringRes
private fun paintTypeLabel(type: PaintType): Int = when (type) {
    PaintType.OPAQUE -> R.string.paint_type_opaque
    PaintType.WASH -> R.string.paint_type_wash
    PaintType.METALLIC -> R.string.paint_type_metallic
    PaintType.MEDIUM -> R.string.paint_type_medium
    PaintType.PRIMER -> R.string.paint_type_primer
    PaintType.OTHER -> R.string.paint_type_other
}
