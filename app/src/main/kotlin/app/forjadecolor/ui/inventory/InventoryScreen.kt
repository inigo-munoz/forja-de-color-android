package app.forjadecolor.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.PaintType
import app.forjadecolor.domain.paints.addPaint
import app.forjadecolor.domain.paints.findPaint
import app.forjadecolor.domain.storage.Repository
import kotlinx.coroutines.launch

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

/**
 * Saves the paint through [addPaint] (add or replace by brand + normalized code). Like the PWA, a paint
 * that replaces one in a BSL family keeps that family.
 */
internal suspend fun savePaint(repo: Repository, paint: Paint) {
    val current = repo.getInventory()
    val prior = current.findPaint(PaintRef(paint.brand, paint.code))
    repo.saveInventory(current.addPaint(if (prior?.family != null) paint.copy(family = prior.family) else paint))
}

/** The paints and brushes the user owns, and a form to add or update a paint. */
@Composable
fun InventoryScreen(
    container: AppContainer,
) {
    var inventory by remember { mutableStateOf<Inventory?>(null) }
    var version by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(version) { inventory = container.repository.getInventory() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_inventory"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.inventory_title), style = MaterialTheme.typography.headlineSmall)
        inventory?.let { inv ->
            val groups = groupPaints(inv)
            groups.families.forEach { (name, paints) -> PaintSection(name, "family_$name", paints) }
            if (groups.others.isNotEmpty()) PaintSection(stringResource(R.string.inventory_other_paints), "family_other", groups.others)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.inventory_brushes), style = MaterialTheme.typography.titleMedium)
                inv.brushes.forEach { b ->
                    Row(Modifier.testTag("brush").semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(b.code, fontFamily = FontFamily.Monospace)
                        Text(b.name)
                    }
                }
            }
            AddPaintForm(onSave = { paint -> scope.launch { savePaint(container.repository, paint); version++ } })
        }
    }
}

@Composable
private fun PaintSection(title: String, tag: String, paints: List<Paint>) {
    Column(Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        paints.forEach { p ->
            Row(
                Modifier.testTag("paint").semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Swatch(p.hex)
                Text(p.code, fontFamily = FontFamily.Monospace)
                Text(p.name)
            }
        }
    }
}

@Composable
private fun Swatch(hex: String, tag: String = "swatch") {
    val color = remember(hex) { parseHex(hex) }
    Box(
        Modifier.size(20.dp).background(color ?: Color.Transparent, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).testTag(tag),
    )
}

private fun parseHex(hex: String): Color? =
    if (HEX.matches(hex.trim())) Color(0xFF000000 or hex.trim().drop(1).toLong(16)) else null

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPaintForm(onSave: (Paint) -> Unit) {
    // Same defaults as the PWA form, which resets to them after each save.
    var brand by rememberSaveable { mutableStateOf("Vallejo") }
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf("Game Color") }
    var type by rememberSaveable { mutableStateOf(PaintType.OPAQUE) }
    var hex by rememberSaveable { mutableStateOf("#888888") }
    val fields = listOf(brand, code, name, range).map { it.trim() }
    val valid = fields.all { it.isNotEmpty() } && HEX.matches(hex.trim())

    Card(Modifier.fillMaxWidth().testTag("add_paint")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.inventory_add_title), style = MaterialTheme.typography.titleMedium)
            FormField(R.string.inventory_brand, brand, "field_brand") { brand = it }
            FormField(R.string.inventory_code, code, "field_code") { code = it }
            FormField(R.string.inventory_name, name, "field_name") { name = it }
            FormField(R.string.inventory_range, range, "field_range") { range = it }
            Text(stringResource(R.string.inventory_type), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaintType.entries.forEach { t ->
                    val wire = t.name.lowercase()
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(wire) }, modifier = Modifier.testTag("type_$wire"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { FormField(R.string.inventory_hex, hex, "field_hex") { hex = it } }
                Swatch(hex, tag = "hex_preview")
            }
            Button(
                enabled = valid,
                onClick = {
                    val (b, c, n, r) = fields
                    onSave(Paint(brand = b, code = c, name = n, range = r, type = type, hex = hex.trim().lowercase()))
                    brand = "Vallejo"; code = ""; name = ""; range = "Game Color"; type = PaintType.OPAQUE; hex = "#888888"
                },
                modifier = Modifier.testTag("save_paint"),
            ) { Text(stringResource(R.string.inventory_add)) }
        }
    }
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
