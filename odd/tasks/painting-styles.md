# Painting styles — choose a style for the miniature and a setting for the base

Objective: the project keeps a painting style (and a base setting); the advisor's recipe, tips and the "Paint it with ChatGPT" image follow it. Android and PWA, prompts byte-identical.
Branch: feat/painting-styles (from feat/photo-inventory, Android; from main, PWA). Delivery: ask-on-risk. Tests: Gradle (JUnit + Robolectric), vitest.

## Specs

- S1 — Owner (2026-11-17): "Antes de la release vamos a incluir el tipo de pintado, recuerda que en la app que vimos había varios tipos"
- S2 — Owner (2026-11-17), on the proposed list (Mi método + NMM, OSL, Vivid, Grimdark; terrain styles Winter, Desert, Fantasy left for later): "todos" → all of them.
- S3 — Miniature styles (closed list, one chosen): My method (default, the current quick-and-showy BSL/slapchop method), NMM, OSL, Vivid · High-Fantasy, Grimdark. Each with a one-line explanation for beginners.
- S4 — Base setting (closed list, optional): none (default), Winter, Desert, Fantasy. Assumption: kept as a second choice because the reference app had them as a separate "Terrain" tab.
- S5 — Stored in the project; can be changed when asking the advisor; the recipe/consultation prompt and the image prompt include it.
- S6 — Old backups and old projects keep working (no style = My method, no setting); the backup stays version 1 with an optional field.
- S7 — Prompts stay byte-identical between Android and the PWA (shared fixtures).

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S3–S7 | inline | done — domain: style/setting model, project + backup field, prompt and image-prompt text (PWA first as source of fixtures) | 72c5488; PWA 06529e9 |
| T2 | S5, S6 | inline | done — Android storage (Room migration if needed) + backup round-trip | 22f04e0 |
| T3 | S3–S5 | inline | done — Android UI: choose style/setting in the project and in Ask | e5981c6 |
| T4 | S3–S7 | inline | done — PWA: same model, prompts, backup, UI | PWA fac32b1 |
| T5 | S1–S7 | delegated (verifier) | partly done — verifier PASS all S#, release 1.3.0 installed; owner's device check pending | see L7 |

## Log

- L1 (2026-11-17, user): "Antes de la release vamos a incluir el tipo de pintado, recuerda que en la app que vimos había varios tipos" Verified in smarthobby-analisis/informe.md §6.3: 4 miniature styles (NMM, OSL, Vivid · High-Fantasy, Guerra · Grimdark) and 3 terrain styles (Invierno, Desierto, Fantasía), closed list. Forja has no style today.
- L2 (2026-11-17, user): "todos". Feature document created.
- L3 (2026-11-17): T1 done. PWA 06529e9: src/projects/style.ts (PAINTING_STYLES, BASE_SETTINGS, lookOf, adviceLookLines, imageLookLines), Project.style/baseSetting optional + setLook (my method stored absent), AdviceRequest extends Look, look section between MOMENT and the answer format, image line after the Style line, backup validation 'project #N has an unknown painting style or base setting'; 194 tests. Android 72c5488: domain/projects/PaintingStyle.kt mirror, Project nullable fields, AdviceRequest style/baseSetting, buildImagePrompt(look), same validation. Fixtures regenerated from the PWA: existing prompt fixtures unchanged byte for byte (my method adds nothing, AI instructions fingerprint unchanged), new prompt-looks.txt and image-prompt-looks.txt pin every style/setting, backup-v1.json project-2 now NMM + desert. Domain 113 tests green.
- L4 (2026-11-17): T2 done in 22f04e0. Risk: item 1 (Room migration). ProjectEntity style/baseSetting TEXT NULL, ForjaDatabase v3 with MIGRATION_2_3 (two ADD COLUMN), schema 3.json exported, unknown names read as null. RED: migration tests expected v3 (got 2), RoomRepository PWA backup re-export lost the style → GREEN: new 2→3 test (rows kept, my method, style saved and read back), 1→3 chain, backup round-trip. Independent verifier pending at T5.
- L5 (2026-11-17): T3 done in e5981c6. ui/components/LookPicker.kt (LookLine summary + Change → LookDialog with radio lists and a beginner line each; nothing saved until Save), in the project header (ProjectViewModel.setLook) and Ask step 1 (AskViewModel.setLook reads the project fresh); Ask send and Paint it with ChatGPT use the saved look. LookTest 7/7 (tests written before the code but RED not run on its own); full suite 309 tests, 0 failures.
- L6 (2026-11-17): T4 done in PWA fac32b1: src/ui/look-fields.ts (two selects + beginner line), in the project view (update → setLook) and Ask (saved fresh; send reads the saved look), paint image uses lookOf(project). look.test.ts RED 4/4 → GREEN; 198 tests, tsc, build green.
- L7 (2026-11-17): independent verifier (items 1 and 3): no blockers, S1–S7 PASS. Probes: fixtures regenerated at baseline and branch (existing prompts/AI instructions identical), unstyled export byte-identical vs baseline, 1→3 and 2→3 migrations keep rows, unknown Room names read as my method, both apps reject null/wrong-case/unknown looks with the same message and leave data untouched. Advisories: KDoc misplaced in Backup.kt (fixed); a hand-edited backup with "my-method" is kept as a value (harmless); a Send in the same instant as Save may use the previous look (accepted). Probes committed as regression tests (Android Room + Backup, PWA backup). Scratch worktrees removed. Full Android suite 312 tests, 0 failures; PWA 199. Release 1.3.0 (versionCode 6) installed on the Pixel.
