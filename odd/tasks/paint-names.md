# Paint names and lookup — English and Spanish names, AI web lookup, "already owned" warning

Objective: paints keep a Spanish and an English name; the photo scan asks the AI to look up the official names and real colour; a paint the user already owns is flagged and left unticked. Android and PWA.
Branch: feat/paint-names (both repos). Delivery: ask-on-risk. Tests: Gradle (JUnit + Robolectric), vitest.

## Specs

- S1 — Owner (2026-11-17): "Hay que ver que haga busqueda con la IA para comprobar el color de la pintura y sus nombres en ingles y español y si ya la tiene que de un aviso"
- S2 — Owner (2026-11-17), accepting the proposal: "si, hazlo así".
- S3 — Scan instructions ask the AI to search the web (maker's site or a colour chart) for each paint it identifies: official name in Spanish and in English and its real colour; anything it cannot confirm is left empty instead of invented. Same text in both apps.
- S4 — Each paint keeps a Spanish name (the existing name) and an optional English name; lists show both, e.g. "Blanco Calavera · Dead White". Stored data: Android Room migration; backup stays version 1 with an optional field; old backups still read.
- S5 — The 48 seed BSL paints get their English name, verified online. Paints saved before this change get it from the seed by code (like the BSL roles), without rewriting stored data.
- S6 — Scan list: a paint the user already owns shows a clear warning "Ya la tienes: Blanco Calavera (72.001)" and comes unticked; ticking it updates the owned one.
- S7 — Manual add form: typing a code the user already owns shows the same warning (only when adding, not when editing that paint).
- Assumption: the advisor prompt's inventory lines stay as they are (no English name), so the AI instructions do not change and need no re-copy.

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S3–S5 | inline | todo — domain both apps: nameEn, englishName fallback to seed, scan prompt + parser (nameEs/nameEn), backup validation, seed names, fixtures | |
| T2 | S4 | inline | todo — Android Room v4 (paints.nameEn) + migration test | |
| T3 | S4, S6, S7 | inline | todo — Android UI: both names in lists, scan warning + unticked, form warning, English name field | |
| T4 | S4, S6, S7 | inline | todo — PWA UI, same | |
| T5 | S1–S7 | delegated (verifier) | todo — independent verifier (storage + contract), device check, release | |

## Log

- L1 (2026-11-17, user): "Hay que ver que haga busqueda con la IA para comprobar el color de la pintura y sus nombres en ingles y español y si ya la tiene que de un aviso". Verified: the scan prompt asks only for an approximate colour from the photo; paints keep one name (seed names are Spanish); an owned paint in the scan list only shows "Updates the one you have" and comes ticked.
- L2 (2026-11-17, user): "si, hazlo así". Feature document created; English names of the 48 seed paints being verified online by a research agent.
