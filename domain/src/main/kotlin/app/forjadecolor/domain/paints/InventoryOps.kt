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
