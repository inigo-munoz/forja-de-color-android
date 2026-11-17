package app.forjadecolor.domain.scan

import app.forjadecolor.domain.ForjaJson
import app.forjadecolor.domain.advice.extractJson
import app.forjadecolor.domain.paints.Brush
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.PaintType
import app.forjadecolor.domain.paints.addBrush
import app.forjadecolor.domain.paints.findBrush
import app.forjadecolor.domain.paints.findPaint
import app.forjadecolor.domain.paints.lookUpInCatalogue
import app.forjadecolor.domain.paints.normalizeCode
import app.forjadecolor.domain.paints.paintKey
import app.forjadecolor.domain.paints.savePaintKeepingFamily
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Longest pasted answer read; a photo of a shelf yields a few kilobytes of JSON. */
const val MAX_SCAN_ANSWER_CHARS = 100_000

/** Most paints, and most brushes, taken from one answer. */
const val MAX_SCAN_ITEMS = 200

/** Colour given to a paint whose colour the AI did not give or gave in another form. */
const val UNKNOWN_HEX = "#888888"

// Only in the scan instructions, never in a real answer: the user pasted the prompt.
private const val PROMPT_MARKER = "PAINT SCAN FORMAT"
private val HEX = Regex("^#[0-9a-fA-F]{6}$")

/** The fixed instructions sent with the photo. Same text as the PWA (src/scan/scan.ts). */
fun buildScanPrompt(): String = """
You are helping a miniature painter add their paints and brushes to Forja de Color.
Look at the photo and list every paint bottle and every brush you can identify.

Rules:
- Only list what you can see. If you cannot read a code, leave "code" empty instead of guessing.
- For each paint, search the web (the maker's site or a colour chart) for its official name in Spanish ("nameEs") and in English ("nameEn") and its real colour ("hex", as #rrggbb). If you cannot confirm a name or the colour, leave it empty instead of inventing it.
- "brand" is the maker (for example Vallejo, Citadel, Army Painter) and "range" the product line (for example Game Color, Model Color).
- "type" is one of: opaque, wash, ink, metallic, medium, primer, other.
- For a brush, "code" is the size or model printed on it (for example 2, 0, 5/0) and "name" a short description.

$PROMPT_MARKER (reply with this JSON only):
```json
{
  "version": 1,
  "kind": "inventory",
  "paints": [
    { "brand": "Vallejo", "code": "72.001", "nameEs": "Blanco Calavera", "nameEn": "Dead White", "range": "Game Color", "type": "opaque", "hex": "#ffffff" }
  ],
  "brushes": [
    { "code": "2", "name": "Round synthetic" }
  ]
}
```
""".trim()

/**
 * A paint read from the photo; [existing] is the owned paint it would update, if any; [fromCatalogue]
 * when its data comes from the paint catalogue rather than the AI.
 */
data class ScannedPaint(val paint: Paint, val existing: Paint?, val fromCatalogue: Boolean = false)

/** A brush read from the photo; [existing] is the owned brush it would update, if any. */
data class ScannedBrush(val brush: Brush, val existing: Brush?)

sealed interface ScanResult {
    data class Success(val paints: List<ScannedPaint>, val brushes: List<ScannedBrush>) : ScanResult
    data class Failure(val errors: List<String>) : ScanResult
}

/**
 * Reads the AI's list of paints and brushes. Lenient with each item (unknown type → other, unreadable
 * colour → [UNKNOWN_HEX], missing code kept empty for the user to fill in), strict with the shape.
 * Nothing is saved here: the user confirms each item first.
 */
fun parseScanAnswer(raw: String, inv: Inventory): ScanResult {
    if (raw.length > MAX_SCAN_ANSWER_CHARS) return fail("the answer is too long")
    if (PROMPT_MARKER in raw) return fail("this is the prompt, not the AI answer: paste what the AI replied")
    val json = extractJson(raw) ?: return fail("no JSON found in the answer")
    val root = try {
        ForjaJson.parseToJsonElement(json)
    } catch (e: IllegalArgumentException) {
        return fail("invalid JSON: ${e.message?.lineSequence()?.firstOrNull().orEmpty()}")
    }
    if (root !is JsonObject) return fail("version must be 1")
    val version = (root["version"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
    if (version != 1) return fail("version must be 1")
    if (root.text("kind") != "inventory") return fail("this answer is not a paint list")
    val paintItems = root.list("paints") ?: return fail("paints must be a list")
    val brushItems = root.list("brushes") ?: return fail("brushes must be a list")

    val paints = paintItems.mapNotNull(::toPaint)
        .distinctBy { if (it.code.isBlank()) "name:${it.brand}|${it.name}" else paintKey(it) }
        .take(MAX_SCAN_ITEMS)
        .map { ScannedPaint(it, if (it.code.isBlank()) null else inv.findPaint(PaintRef(it.brand, it.code))) }
    val brushes = brushItems.mapNotNull(::toBrush)
        .distinctBy { it.code.lowercase() }
        .take(MAX_SCAN_ITEMS)
        .map { ScannedBrush(it, if (it.code.isBlank()) null else inv.findBrush(it.code)) }
    if (paints.isEmpty() && brushes.isEmpty()) return fail("the AI did not find any paints or brushes")
    return ScanResult.Success(paints, brushes)
}

/**
 * Saves what the user confirmed: paints through [savePaintKeepingFamily], brushes through [addBrush].
 * Items still missing a brand, code or name are skipped.
 */
fun Inventory.applyScan(paints: List<Paint>, brushes: List<Brush>): Inventory {
    val withPaints = paints.filter { isComplete(it) }.fold(this) { inv, p -> inv.savePaintKeepingFamily(p) }
    return brushes.filter { it.code.isNotBlank() && it.name.isNotBlank() }.fold(withPaints) { inv, b -> inv.addBrush(b) }
}

/** A paint can be saved once it has a brand, a code, a name and a readable colour. */
fun isComplete(p: Paint): Boolean =
    p.brand.isNotBlank() && p.code.isNotBlank() && p.name.isNotBlank() && HEX.matches(p.hex)

private fun fail(message: String) = ScanResult.Failure(listOf(message))

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

/** The array at [key]; empty when absent, null when it is something else. */
private fun JsonObject.list(key: String): List<JsonElement>? = when (val v = this[key]) {
    null -> emptyList()
    is JsonArray -> v
    else -> null
}

private fun toPaint(e: JsonElement): Paint? {
    val o = e as? JsonObject ?: return null
    val brand = o.text("brand")
    val code = o.text("code")
    val nameEn = o.text("nameEn")
    // Spanish is the app's main name; an answer with only "name" or only "nameEn" still works.
    val name = o.text("nameEs").ifEmpty { o.text("name") }.ifEmpty { nameEn }
    if (code.isBlank() && name.isBlank()) return null
    val type = PaintType.entries.firstOrNull { it.name.equals(o.text("type"), ignoreCase = true) } ?: PaintType.OTHER
    val hex = o.text("hex").lowercase().takeIf { HEX.matches(it) } ?: UNKNOWN_HEX
    return Paint(brand, if (code.isBlank()) "" else normalizeCode(brand, code), name, o.text("range"), type, hex, nameEn = nameEn.ifEmpty { null })
}

private fun toBrush(e: JsonElement): Brush? {
    val o = e as? JsonObject ?: return null
    val code = o.text("code")
    val name = o.text("name")
    return if (code.isBlank() && name.isBlank()) null else Brush(code, name)
}

/**
 * Looks every scanned paint up in the catalogue first ([lookUpInCatalogue]): found ones take the
 * catalogue's data and are flagged; owned paints are found again with the corrected codes, and two rows
 * that turn out to be the same paint are kept once. Same rule as the PWA.
 */
fun checkInCatalogue(result: ScanResult.Success, inv: Inventory): ScanResult.Success {
    val paints = result.paints.map { lookUpInCatalogue(it.paint) }
        .distinctBy { (p, _) -> if (p.code.isBlank()) "name:${p.brand}|${p.name}" else paintKey(p) }
        .map { (p, fromCatalogue) -> ScannedPaint(p, if (p.code.isBlank()) null else inv.findPaint(PaintRef(p.brand, p.code)), fromCatalogue) }
    return result.copy(paints = paints)
}
