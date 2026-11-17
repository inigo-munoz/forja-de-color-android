package app.forjadecolor.domain.paints

/**
 * The paint's English name: its own, else the seed's for the same brand + code (inventories stored
 * before English names existed). Never written back to stored data. Same rule as the PWA.
 */
fun englishName(paint: Paint): String? =
    paint.nameEn?.trim()?.ifEmpty { null } ?: SEED_INVENTORY.findPaint(paint.ref)?.nameEn

/** "Blanco Calavera · Dead White"; just the name when there is no English one or it is the same. */
fun displayName(paint: Paint): String {
    val en = englishName(paint)
    return if (en != null && !en.equals(paint.name.trim(), ignoreCase = true)) "${paint.name} · $en" else paint.name
}
