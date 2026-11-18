# Half-painted context — the AI knows the earlier recipe, what is painted, the paint types and gets a current photo

Objective: for a miniature already started, the advisor prompt carries the previous recipe and which zones are done, the inventory says each paint's type, the translucency rule matches the inventory, and "half-painted" asks for a current photo. Android and PWA, prompts byte-identical.
Branch: feat/half-painted-context (both repos). Delivery: ask-on-risk. Tests: Gradle, vitest.

## Specs

- S1 — Owner (2026-11-18): "revisa a la hora de tener ya una miniatura empezada para ver como funciona el texto para la IA." Review found 4 problems (see L1).
- S2 — Owner (2026-11-18), on the 4 proposals: "vale, si lo ves bien adelante".
- S3 — Half-painted prompt includes the project's latest recipe: each zone with technique and paints, marked painted / not painted yet (the zones the user ticked), and asks to keep those colours unless there is a reason to change them.
- S4 — Inventory lines carry each paint's type (opaque, wash, ink, metallic, medium, primer, other).
- S5 — The "I have no translucent paints" rule appears only when the inventory has no inks or washes; otherwise the method says to use them (or thin opaque paints).
- S6 — Half-painted without a new photo and without progress photos does not send the unpainted initial photo: it asks for a current photo.
- S7 — Prompts stay byte-identical between apps (fixtures regenerated from the PWA). The AI instructions change (S4/S5): the owner re-copies them once.

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S3–S5, S7 | inline | todo — PWA prompt builder + tests | |
| T2 | S3–S5, S7 | inline | todo — Android prompt builder, fixtures, tests | |
| T3 | S3, S6 | inline | todo — both apps: Ask passes the previous recipe; half-painted photo rule | |
| T4 | S1–S7 | inline | todo — suites, device check, release | |

## Log

- L1 (2026-11-18, user): "revisa a la hora de tener ya una miniatura empezada para ver como funciona el texto para la IA." Read-only review of prompt-half-painted: (1) previous recipe not sent; (2) ticked zones not sent; (3) METHOD says "I have no translucent paints" though the owner now has an ink, mediums and thinner, and inventory lines have no type; (4) without a new photo it sends the last progress photo, else the unpainted initial photo.
- L2 (2026-11-18, user): "vale, si lo ves bien adelante". Feature document created.
