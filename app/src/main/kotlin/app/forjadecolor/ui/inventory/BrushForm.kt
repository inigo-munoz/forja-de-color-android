package app.forjadecolor.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.forjadecolor.R
import app.forjadecolor.domain.paints.Brush

/** The fields of the brush form while it is open; saved across recreation by [BrushDraftSaver]. */
internal data class BrushDraft(val code: String, val name: String) {
    companion object {
        val NEW = BrushDraft("", "")

        fun of(b: Brush) = BrushDraft(b.code, b.name)
    }
}

/** Null (form closed) saves nothing, so it restores closed. */
internal val BrushDraftSaver = Saver<BrushDraft?, List<String>>(
    save = { d -> d?.let { listOf(it.code, it.name) } },
    restore = { l -> BrushDraft(l[0], l[1]) },
)

/** Add or update a brush; the code (size or model) identifies it, so saving an existing code updates it. */
@Composable
internal fun BrushFormDialog(draft: BrushDraft, onChange: (BrushDraft) -> Unit, onSave: (Brush) -> Unit, onCancel: () -> Unit) {
    val code = draft.code.trim()
    val name = draft.name.trim()
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).testTag("brush_form"), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.inventory_brush_title), style = MaterialTheme.typography.titleMedium)
                FormField(R.string.inventory_brush_code, draft.code, "field_brush_code") { onChange(draft.copy(code = it)) }
                FormField(R.string.inventory_brush_name, draft.name, "field_brush_name") { onChange(draft.copy(name = it)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_brush")) { Text(stringResource(R.string.inventory_cancel)) }
                    Button(
                        enabled = code.isNotEmpty() && name.isNotEmpty(),
                        onClick = { onSave(Brush(code, name)) },
                        modifier = Modifier.testTag("save_brush"),
                    ) { Text(stringResource(R.string.inventory_brush_save)) }
                }
            }
        }
    }
}
