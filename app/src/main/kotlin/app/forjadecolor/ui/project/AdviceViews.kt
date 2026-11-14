package app.forjadecolor.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.forjadecolor.R
import app.forjadecolor.domain.advice.AdviceEntry
import app.forjadecolor.domain.advice.ConsultationResponse
import app.forjadecolor.domain.advice.RecipeResponse
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.advice.Technique
import app.forjadecolor.domain.advice.TechniqueCardId
import app.forjadecolor.domain.advice.ZoneRecipe
import app.forjadecolor.domain.advice.neededPaints
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.findPaint
import app.forjadecolor.domain.paints.paintKey
import kotlinx.coroutines.launch

/** '#rrggbb' to a colour; grey when the stored text is not a valid hex. */
fun parseHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

/** One advice entry: recipe or consultation, paints needed, unknown paints to assign, unknown brushes. */
@Composable
internal fun AdviceCard(
    entry: AdviceEntry,
    inventory: Inventory,
    onDelete: () -> Unit,
    onAssign: (unknown: PaintRef, owned: PaintRef) -> Unit,
    onOpenTechnique: (TechniqueCardId) -> Unit,
    /** Sends the image prompt; null when there is no photo to paint. */
    onPaintImage: suspend (RecipeResponse) -> SendResult?,
) {
    val unknown = remember(entry) { entry.unknownPaints.map(::paintKey).toSet() }
    val label: @Composable (PaintRef) -> Unit = { PaintLabel(inventory, it, unknown) }
    val scope = rememberCoroutineScope()
    var imageStatus by remember { mutableStateOf<Int?>(null) }

    Card(Modifier.fillMaxWidth().testTag("advice_${entry.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${stringResource(R.string.project_advice)} · ${entry.receivedAt.take(10)}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onDelete, modifier = Modifier.testTag("delete_advice")) { Text(stringResource(R.string.project_delete_advice)) }

            when (val r = entry.response) {
                is RecipeResponse -> {
                    RecipeView(r, label)
                    Button(
                        onClick = { scope.launch { onPaintImage(r)?.let { imageStatus = paintImageMessage(it) } } },
                        modifier = Modifier.testTag("paint_image"),
                    ) { Text(stringResource(R.string.project_paint_image)) }
                    imageStatus?.let { Text(stringResource(it), modifier = Modifier.testTag("paint_image_status")) }
                }
                is ConsultationResponse -> ConsultationView(r, label, onOpenTechnique)
            }

            Column(Modifier.testTag("needed")) {
                Heading(stringResource(R.string.project_needed))
                neededPaints(entry.response).forEach { ref ->
                    Box(Modifier.semantics(mergeDescendants = true) {}.testTag("needed_paint")) { label(ref) }
                }
            }
            entry.unknownPaints.forEach { u -> AssignPicker(inventory, u) { owned -> onAssign(u, owned) } }
            entry.unknownBrushes.forEach { b ->
                Text(
                    "${stringResource(R.string.project_unknown_brush)}: $b",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("unknown_brush"),
                )
            }
        }
    }
}

internal fun paintImageMessage(result: SendResult): Int =
    if (result == SendResult.FALLBACK) R.string.project_paint_image_fallback else R.string.project_paint_image_sent

@Composable
private fun RecipeView(r: RecipeResponse, label: @Composable (PaintRef) -> Unit) {
    Heading(stringResource(R.string.project_prep))
    NumberedList(r.prep)
    r.zones.forEach { ZoneView(it, label) }
    r.complements?.takeIf { it.isNotEmpty() }?.let { complements ->
        Heading(stringResource(R.string.project_complements))
        complements.forEach { label(it) }
    }
    r.remainingSteps?.takeIf { it.isNotEmpty() }?.let {
        Heading(stringResource(R.string.project_remaining))
        NumberedList(it)
    }
    Heading(stringResource(R.string.project_tips))
    NumberedList(r.tips)
}

@Composable
private fun ZoneView(z: ZoneRecipe, label: @Composable (PaintRef) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("zone_${z.name}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val technique = if (z.technique == Technique.BSL) "BSL" else "Slapchop"
        val painted = if (z.alreadyPainted == true) " (${stringResource(R.string.project_already_painted)})" else ""
        Text("${z.name} · $technique$painted", fontWeight = FontWeight.SemiBold)
        val bsl = z.bsl
        if (z.technique == Technique.BSL && bsl != null) {
            listOf(R.string.t8_bsl_base to bsl.base, R.string.t8_bsl_shadow to bsl.shadow, R.string.t8_bsl_light to bsl.light).forEach { (role, ref) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${stringResource(role)}: ")
                    label(ref)
                }
            }
        } else if (z.paint != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                label(z.paint!!)
                z.dilution?.let { Text(" · $it", fontWeight = FontWeight.Bold) }
            }
        }
        z.brush?.let { Text("🖌 $it", style = MaterialTheme.typography.labelMedium) }
        NumberedList(z.steps)
    }
}

@Composable
private fun ConsultationView(c: ConsultationResponse, label: @Composable (PaintRef) -> Unit, onOpenTechnique: (TechniqueCardId) -> Unit) {
    Text("${c.zone} · ${stringResource(R.string.project_technique)}: ${c.technique}", fontWeight = FontWeight.SemiBold)
    NumberedList(c.steps)
    c.paints.forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            label(p.paint)
            p.dilution?.let { Text(" · $it", fontWeight = FontWeight.Bold) }
        }
    }
    c.brush?.let { Text("🖌 $it", style = MaterialTheme.typography.labelMedium) }
    c.techniqueCard?.let { card ->
        TextButton(onClick = { onOpenTechnique(card) }, modifier = Modifier.testTag("see_technique")) { Text(stringResource(R.string.project_see_technique)) }
    }
}

/** An owned paint as swatch + code + name; a paint not in the inventory (or still unassigned) as a warning. */
@Composable
private fun PaintLabel(inventory: Inventory, ref: PaintRef, unknown: Set<String>) {
    val owned = inventory.findPaint(ref)
    if (owned == null || paintKey(ref) in unknown) {
        Text("⚠ ${ref.brand} ${ref.code} (${stringResource(R.string.project_unknown)})", color = MaterialTheme.colorScheme.error)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(14.dp).background(parseHex(owned.hex), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).testTag("swatch"),
        )
        Text("${owned.code} ${owned.name}")
    }
}

@Composable
private fun AssignPicker(inventory: Inventory, unknown: PaintRef, onPick: (PaintRef) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("assign")) {
            Text("${stringResource(R.string.project_assign)}: ${unknown.brand} ${unknown.code}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            inventory.paints.forEach { p ->
                DropdownMenuItem(text = { Text("${p.code} ${p.name}") }, onClick = {
                    open = false
                    onPick(p.ref)
                })
            }
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun NumberedList(items: List<String>) {
    items.forEachIndexed { i, s ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${i + 1}.")
            Text(s)
        }
    }
}

