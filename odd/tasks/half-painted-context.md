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
| T1 | S3–S5, S7 | inline | done — PWA prompt builder + tests | PWA a9639a5 |
| T2 | S3–S5, S7 | inline | done — Android prompt builder, fixtures, tests | see L3 |
| T3 | S3, S6 | inline | done — both apps: Ask passes the previous recipe; half-painted photo rule | see L3 |
| T4 | S1–S7 | inline | partly — suites green, release 1.6.5; owner check and re-copy of the AI instructions pending | |

## Log

- L1 (2026-11-18, user): "revisa a la hora de tener ya una miniatura empezada para ver como funciona el texto para la IA." Read-only review of prompt-half-painted: (1) previous recipe not sent; (2) ticked zones not sent; (3) METHOD says "I have no translucent paints" though the owner now has an ink, mediums and thinner, and inventory lines have no type; (4) without a new photo it sends the last progress photo, else the unpainted initial photo.
- L2 (2026-11-18, user): "vale, si lo ves bien adelante". Feature document created.
- L3 (2026-11-18): done in both apps. Prompt: method(inv) chooses NO_TRANSLUCENT or WITH_TRANSLUCENT ("use my inks and washes where they suit, or thin my opaque paints"); inventory header "(brand|code|name|family|order|role|type)" with the type last; half-painted adds "PREVIOUS RECIPE for this miniature (keep these colours unless there is a reason to change them, and say why if you change one):" with one line per zone "- name (technique): paints. Painted./Not painted yet." after the MOMENT line. Ask: half-painted without a picked photo and without progress photos stops with "For half-painted, take or choose a current photo…" instead of sending the initial photo; the latest recipe + doneZones go in the request. Fixtures regenerated from the PWA (all advisor prompts and brain-instructions change by the type column; new prompt-half-painted-previous.txt and brain-instructions-ink.txt). PWA 254 tests (RED observed), Android 351 (RED observed for the domain; UI tests written with the code). Two PWA format tests updated to the new column order (spec change S4). Release 1.6.5 (versionCode 15).
