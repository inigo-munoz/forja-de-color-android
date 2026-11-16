package app.forjadecolor.ui.technique

import android.content.res.AssetManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import app.forjadecolor.domain.advice.TechniqueCardId
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Locale

/** The few Markdown blocks a technique card uses. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullets(val items: List<String>) : MdBlock
}

data class TechniqueCard(val id: TechniqueCardId, val title: String, val blocks: List<MdBlock>)

private val TITLE = Regex("^# (.+)$", RegexOption.MULTILINE)
private val LEADING_TITLE = Regex("^# .+\n+")
private val HEADING = Regex("^(#{1,6}) (.+)$")
private val BULLET = Regex("^[-*] (.+)$")
private val BOLD = Regex("\\*\\*(.+?)\\*\\*")

/**
 * The card's Markdown for [language] (an ISO 639 code such as "es"): `assets/cards-<language>/<id>.md` when that
 * translation exists, else the English `assets/cards/<id>.md`; empty when neither exists, as in the PWA.
 */
fun readCardMarkdown(
    assets: AssetManager,
    id: TechniqueCardId,
    language: String = Locale.getDefault().language,
): String = readCardMarkdown(assets::open, id, language)

/** Same as above over any [open] that throws [FileNotFoundException] for a missing path, so the fallback is testable. */
fun readCardMarkdown(open: (String) -> InputStream, id: TechniqueCardId, language: String): String {
    val paths = listOfNotNull(
        "cards-$language/${id.wire}.md".takeIf { language.isNotEmpty() && language != "en" },
        "cards/${id.wire}.md",
    )
    for (path in paths) {
        try {
            return open(path).bufferedReader().use { it.readText() }
        } catch (e: FileNotFoundException) {
            // try the next one
        }
    }
    return ""
}

/** Like the PWA's `getCard`: the title is the first `# ` heading, else the id; the body drops a leading title. */
fun parseCard(id: TechniqueCardId, markdown: String): TechniqueCard {
    val text = markdown.replace("\r\n", "\n")
    val title = TITLE.find(text)?.groupValues?.get(1)?.trim() ?: id.wire
    return TechniqueCard(id, title, parseBlocks(text.replaceFirst(LEADING_TITLE, "")))
}

private fun parseBlocks(body: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    val bullets = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) blocks += MdBlock.Paragraph(paragraph.joinToString(" "))
        if (bullets.isNotEmpty()) blocks += MdBlock.Bullets(bullets.toList())
        paragraph.clear()
        bullets.clear()
    }
    for (raw in body.lines()) {
        val line = raw.trim()
        val heading = HEADING.find(line)
        val bullet = BULLET.find(line)
        when {
            line.isEmpty() -> flush()
            heading != null -> {
                flush()
                blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            }
            bullet != null -> {
                if (paragraph.isNotEmpty()) flush()
                bullets += bullet.groupValues[1].trim()
            }
            else -> {
                if (bullets.isNotEmpty()) flush()
                paragraph += line
            }
        }
    }
    flush()
    return blocks
}

/** Inline `**bold**` as a bold span; everything else stays plain text. */
fun boldText(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in BOLD.findAll(text)) {
        append(text.substring(last, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        last = match.range.last + 1
    }
    append(text.substring(last))
}
