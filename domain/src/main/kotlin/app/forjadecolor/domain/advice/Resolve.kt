package app.forjadecolor.domain.advice

import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.paintKey

/** Applies [fn] to every PaintRef in a response, in order of appearance, returning a new response. */
fun mapPaintRefs(r: AdviceResponse, fn: (PaintRef) -> PaintRef): AdviceResponse = when (r) {
    is ConsultationResponse -> r.copy(paints = r.paints.map { it.copy(paint = fn(it.paint)) })
    is RecipeResponse -> r.copy(
        zones = r.zones.map { z ->
            val paint = z.paint?.let(fn)
            val bsl = z.bsl?.let { Bsl(fn(it.base), fn(it.shadow), fn(it.light)) }
            z.copy(paint = paint, bsl = bsl)
        },
        complements = r.complements?.map(fn),
    )
}

/** Every PaintRef in a response, in order of appearance (duplicates included). */
fun allPaintRefs(r: AdviceResponse): List<PaintRef> {
    val refs = mutableListOf<PaintRef>()
    mapPaintRefs(r) { refs += it; it }
    return refs
}

/** Paints a response uses, each once, in order of first appearance. */
fun neededPaints(r: AdviceResponse): List<PaintRef> = allPaintRefs(r).distinctBy(::paintKey)

fun assignUnknownPaint(e: AdviceEntry, unknown: PaintRef, owned: PaintRef): AdviceEntry {
    val key = paintKey(unknown)
    return e.copy(
        response = mapPaintRefs(e.response) { if (paintKey(it) == key) owned else it },
        unknownPaints = e.unknownPaints.filter { paintKey(it) != key },
    )
}
