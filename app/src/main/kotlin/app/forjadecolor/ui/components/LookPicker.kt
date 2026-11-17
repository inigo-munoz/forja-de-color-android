package app.forjadecolor.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.forjadecolor.R
import app.forjadecolor.domain.projects.BaseSetting
import app.forjadecolor.domain.projects.Look
import app.forjadecolor.domain.projects.PaintingStyle

/** "Style: My method · Base: No setting" with a Change button that opens [LookDialog]. */
@Composable
fun LookLine(look: Look, onChange: (Look) -> Unit, modifier: Modifier = Modifier) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.look_summary, stringResource(styleName(look.style)), stringResource(baseName(look.baseSetting))),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).testTag("look_line"),
        )
        TextButton(onClick = { editing = true }, modifier = Modifier.testTag("look_change")) { Text(stringResource(R.string.look_change)) }
    }
    if (editing) {
        LookDialog(look, onSave = { editing = false; onChange(it) }, onCancel = { editing = false })
    }
}

/** One style and one base setting, each a radio list with a line for beginners. Nothing changes until Save. */
@Composable
fun LookDialog(initial: Look, onSave: (Look) -> Unit, onCancel: () -> Unit) {
    var style by rememberSaveable { mutableStateOf(initial.style) }
    var base by rememberSaveable { mutableStateOf(initial.baseSetting) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).testTag("look_dialog"), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.look_style), style = MaterialTheme.typography.titleMedium)
                PaintingStyle.entries.forEach { s ->
                    Choice(styleName(s), styleHelp(s), selected = style == s, tag = "style_${wire(s.name)}") { style = s }
                }
                Text(stringResource(R.string.look_base), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                (listOf<BaseSetting?>(null) + BaseSetting.entries).forEach { b ->
                    Choice(baseName(b), baseHelp(b), selected = base == b, tag = "base_${b?.let { wire(it.name) } ?: "none"}") { base = b }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("look_cancel")) { Text(stringResource(R.string.inventory_cancel)) }
                    Button(onClick = { onSave(Look(style, base)) }, modifier = Modifier.testTag("look_save")) { Text(stringResource(R.string.look_save)) }
                }
            }
        }
    }
}

@Composable
private fun Choice(@StringRes name: Int, @StringRes help: Int?, selected: Boolean, tag: String, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(vertical = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(stringResource(name))
            help?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun wire(name: String) = name.lowercase().replace('_', '-')

@StringRes
private fun styleName(s: PaintingStyle): Int = when (s) {
    PaintingStyle.MY_METHOD -> R.string.style_my_method
    PaintingStyle.NMM -> R.string.style_nmm
    PaintingStyle.OSL -> R.string.style_osl
    PaintingStyle.VIVID -> R.string.style_vivid
    PaintingStyle.GRIMDARK -> R.string.style_grimdark
}

@StringRes
private fun styleHelp(s: PaintingStyle): Int = when (s) {
    PaintingStyle.MY_METHOD -> R.string.style_my_method_help
    PaintingStyle.NMM -> R.string.style_nmm_help
    PaintingStyle.OSL -> R.string.style_osl_help
    PaintingStyle.VIVID -> R.string.style_vivid_help
    PaintingStyle.GRIMDARK -> R.string.style_grimdark_help
}

@StringRes
private fun baseName(b: BaseSetting?): Int = when (b) {
    null -> R.string.base_none
    BaseSetting.WINTER -> R.string.base_winter
    BaseSetting.DESERT -> R.string.base_desert
    BaseSetting.FANTASY -> R.string.base_fantasy
}

@StringRes
private fun baseHelp(b: BaseSetting?): Int? = when (b) {
    null -> null
    BaseSetting.WINTER -> R.string.base_winter_help
    BaseSetting.DESERT -> R.string.base_desert_help
    BaseSetting.FANTASY -> R.string.base_fantasy_help
}
