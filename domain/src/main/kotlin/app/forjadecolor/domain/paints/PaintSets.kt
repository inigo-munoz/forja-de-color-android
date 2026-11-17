package app.forjadecolor.domain.paints

import app.forjadecolor.domain.scan.ScanResult
import app.forjadecolor.domain.scan.ScannedPaint
import app.forjadecolor.domain.scan.checkInCatalogue
import java.text.Normalizer

/** A boxed set whose paints are also sold one by one; added in one go through the confirmation list. */
data class PaintSet(val brand: String, val code: String, val name: String, val paints: List<String>)

// Contents checked with three retailers on 2026-11-17 (Miniature Market, Oakridge, Element Games).
// Same data as the PWA (src/paints/sets.ts).
val PAINT_SETS: List<PaintSet> = listOf(
    PaintSet(
        "Vallejo", "72.212", "Non-Metallic Metal (NMM) by Ángel Giráldez",
        listOf("72.001", "72.019", "72.024", "72.050", "72.051", "72.091", "72.151", "72.153"),
    ),
    PaintSet(
        "Vallejo", "72.193", "Non-Metallic Metal (NMM) by Ángel Giráldez, new formula",
        listOf("72.001", "72.019", "72.024", "72.062", "72.043", "72.091", "72.050", "72.051"),
    ),
)

private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
private fun compact(s: String) = fold(s).replace(Regex("[.\\s-]"), "")

fun findSet(code: String): PaintSet? = PAINT_SETS.firstOrNull { compact(it.code) == compact(code) }

/** Sets whose code or name matches every word of the query. */
fun searchSets(query: String): List<PaintSet> {
    val words = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return PAINT_SETS.filter { s ->
        words.all { w -> w in fold("${s.brand} ${s.code} ${s.name}") || w.replace(Regex("[.\\s-]"), "") in compact(s.code) }
    }
}

/** The set as a list to confirm, like a photo scan: catalogue data, paints the user owns marked. */
fun setAsScan(set: PaintSet, inv: Inventory): ScanResult.Success {
    val paints = set.paints.mapNotNull { code -> findInCatalogue(set.brand, code)?.let { ScannedPaint(paintFromCatalogue(it), null) } }
    return checkInCatalogue(ScanResult.Success(paints, emptyList()), inv)
}
