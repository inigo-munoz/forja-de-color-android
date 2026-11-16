package app.forjadecolor.domain.paints

/**
 * The paint's BSL role: its own, else the seed's for the same brand + code when the family name matches
 * (inventories stored before roles existed). Never written back to stored data. Same as the PWA's
 * `bslRole` (`src/paints/roles.ts`).
 */
fun bslRole(paint: Paint): BslRole? {
    val family = paint.family ?: return null
    family.role?.let { return it }
    val seed = SEED_INVENTORY.findPaint(paint.ref)
    return if (seed?.family?.name == family.name) seed.family.role else null
}
