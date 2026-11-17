package app.forjadecolor.domain.paints

/** Adds the paint, or replaces the one with the same brand + code. The code is stored normalized. */
fun Inventory.addPaint(paint: Paint): Inventory {
    val normalized = paint.copy(code = normalizeCode(paint.brand, paint.code))
    val key = paintKey(normalized)
    val exists = paints.any { paintKey(it) == key }
    val updated = if (exists) paints.map { if (paintKey(it) == key) normalized else it } else paints + normalized
    return copy(paints = updated)
}

fun Inventory.removePaint(ref: PaintRef): Inventory {
    val key = paintKey(ref)
    return copy(paints = paints.filter { paintKey(it) != key })
}

fun Inventory.findPaint(ref: PaintRef): Paint? {
    val key = paintKey(ref)
    return paints.find { paintKey(it) == key }
}

private val LOOSE_CODE = Regex("\\d{2}\\.?\\d{3}")

/** Resolves free text such as "Vallejo 72.003" or "72003" against every brand in the inventory. */
fun Inventory.findPaintLoose(text: String): Paint? {
    val token = LOOSE_CODE.find(text)?.value ?: text.trim()
    return paints.map { it.brand }.distinct().firstNotNullOfOrNull { brand -> findPaint(PaintRef(brand, token)) }
}

/**
 * Owned paints whose code appears in [text] ("Vallejo 72.109", "72109"), distinct and in order of
 * appearance. Every code token is resolved with [findPaintLoose]; tokens not in the inventory are skipped.
 */
fun Inventory.paintsMentionedIn(text: String): List<Paint> =
    LOOSE_CODE.findAll(text).mapNotNull { findPaintLoose(it.value) }.distinctBy { paintKey(it) }.toList()

/**
 * Saves the paint through [addPaint]. Like the PWA, a paint that replaces one in a BSL family keeps
 * that family, so updating "72003" by hand or from a scan does not drop it from its set.
 */
fun Inventory.savePaintKeepingFamily(paint: Paint): Inventory {
    val prior = findPaint(PaintRef(paint.brand, paint.code))
    return addPaint(if (prior?.family != null) paint.copy(family = prior.family) else paint)
}

/** The owned brush with this code (trimmed, case-insensitive), if any. */
fun Inventory.findBrush(code: String): Brush? = brushes.find { it.code.equals(code.trim(), ignoreCase = true) }

/** Adds the brush, or replaces the one with the same code (trimmed, case-insensitive). */
fun Inventory.addBrush(brush: Brush): Inventory {
    val clean = Brush(brush.code.trim(), brush.name.trim())
    val exists = findBrush(clean.code) != null
    val updated = if (exists) brushes.map { if (it.code.equals(clean.code, ignoreCase = true)) clean else it } else brushes + clean
    return copy(brushes = updated)
}
