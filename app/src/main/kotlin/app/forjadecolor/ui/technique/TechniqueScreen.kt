package app.forjadecolor.ui.technique

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.forjadecolor.AppContainer
import app.forjadecolor.R
import app.forjadecolor.domain.advice.TechniqueCardId

/** A technique card, read from `assets/cards/<id>.md` (the user's own text) and rendered as simple Markdown. */
@Composable
fun TechniqueScreen(
    container: AppContainer,
    cardId: TechniqueCardId,
    onBack: () -> Unit,
) {
    val assets = LocalContext.current.assets
    val card = remember(cardId) { parseCard(cardId, readCardMarkdown(assets, cardId)) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_technique"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("technique_back")) { Text(stringResource(R.string.technique_back)) }
        Text(card.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("technique_title"))
        card.blocks.forEach { MarkdownBlock(it) }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
    val type = MaterialTheme.typography
    when (block) {
        is MdBlock.Heading -> Text(boldText(block.text), style = if (block.level <= 2) type.titleMedium else type.titleSmall)
        is MdBlock.Paragraph -> Text(boldText(block.text), style = type.bodyMedium)
        is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = type.bodyMedium)
                    Text(boldText(item), style = type.bodyMedium)
                }
            }
        }
    }
}
