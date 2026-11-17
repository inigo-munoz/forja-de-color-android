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
| T1 | S3–S5 | inline | done — domain both apps: nameEn, englishName fallback to seed, scan prompt + parser (nameEs/nameEn), backup validation, seed names, fixtures | Android 4045adc; PWA 0786bf0 |
| T2 | S4 | inline | done — Android Room v4 (paints.nameEn) + migration test | 7e3bc50 |
| T3 | S4, S6, S7 | inline | done — Android UI: both names in lists, scan warning + unticked, form warning, English name field | 4045adc |
| T4 | S4, S6, S7 | inline | done — PWA UI, same | PWA 51036fd |
| T5 | S1–S7 | delegated (verifier) | todo — independent verifier (storage + contract), device check, release | |

## Log

- L1 (2026-11-17, user): "Hay que ver que haga busqueda con la IA para comprobar el color de la pintura y sus nombres en ingles y español y si ya la tiene que de un aviso". Verified: the scan prompt asks only for an approximate colour from the photo; paints keep one name (seed names are Spanish); an owned paint in the scan list only shows "Updates the one you have" and comes ticked.
- L2 (2026-11-17, user): "si, hazlo así". Feature document created; English names of the 48 seed paints being verified online by a research agent.
- L3 (2026-11-17): research agent verified the 48 English names on Vallejo's official Game Color chart (CC266 Rev03, Sep 2025; acrylicosvallejo.com blocks automated access). It also found 3 Spanish names in the seed that differ from Vallejo's: 72.100 "Carne Rosa" (seed: Carne Rosada), 72.004 "Piel de Elfos" (seed: Piel de Elfo), 72.124 "Marrón Gorgona" (seed: Marrón Gargona). NOT changed: the Spanish names come from the owner's list and feed the advisor prompt (changing them changes the AI instructions); raised to the owner.
- L4 (2026-11-17): T1–T3 done. PWA 0786bf0: names.ts (englishName with seed fallback, displayName), Paint.nameEn, scan prompt asks to search the web for nameEs/nameEn and real hex and to leave unconfirmed fields empty, parser reads nameEs/nameEn/name, backup 'paint #N has an invalid English name', seed ENGLISH_NAMES; 227 tests. Android 7e3bc50 (Room v4 MIGRATION_3_4 ADD COLUMN paints.nameEn, 3→4 test) and 4045adc (domain mirror, scan-prompt fixture regenerated from the PWA, advisor prompt fixtures unchanged, UI: displayName in My paints/wheel/scan, English name field, "You already have it" in the add form and the scan list, owned scan items unticked). Tests written first; RED observed for the PWA domain, not run separately for Android UI and the migration. Two test setups adjusted: wheel test paints used real seed codes (now showing English names), migration test needed the inventory-saved flag. Android 326 tests green. Pending: T4 PWA UI, T5 verifier + device + release.
- L5 (2026-11-17, user, mid-turn): "quiero que hagas la revisión de todo lo que tengo en el movil ahora mismo. Si hace falta descarga de los principales fabricantes sus listas de pinturas para tenerlas ya guardadas y buscarlas primero en la app y luego en la IA" — scope question asked (what to review; catalogue left for v2 in the design); waiting for the answer.
- L6 (2026-11-17, user): "si, las pinturas que tengo guardadas en la app" → owner exported a backup (Downloads, pulled to scratch). 56 paints, 3 brushes: 48 BSL match the seed except 72.004 hex changed by the owner (#d99a7d vs #cb8854); 8 paints outside BSL being verified online by a research agent. No duplicate codes or names.
- L7 (2026-11-17): T4 done in PWA 51036fd (displayName in My paints/wheel/scan, English name field, live already-owned warning in the add form, owned scan paints unticked with the warning; owned brushes keep the plain "updates" line as on Android). RED 3/3 → GREEN; 229 tests, tsc, build. Independent verifier launched (items 1 and 3).
- L8 (2026-11-17): review of the owner's 8 non-BSL paints (research agent, Vallejo charts 2014 rev14 + 2025 CC266): 72.010 Spanish name should be "Rojo Sanguina" (stored "Rojo Sangre"); code "18" should be "72.018" Stormy Blue / Azul Tormenta, absent from the 2025 chart (likely discontinued); 72.036 is now "Bronze Brown" (old "Bronze Fleshtone" / "Bronceado"); 70.524 Thinner Medium is a colourless medium (#ffffff placeholder fine); others OK (72.022 Ultramarine Blue / Azul Ultramar, 72.034 Bone White / Blanco Hueso, 72.035 Dead Flesh / Carne Muerta, 72.030 Goblin Green / Verde Goblin); no hex clearly off. Owner data not changed. Catalogue source candidate: github.com/Arcturus5404/miniature-paints (MIT, 35 brands incl. Vallejo, Citadel, Army Painter; approximate hex; last push 2025-03).
