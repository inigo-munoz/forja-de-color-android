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
| T2 | S5, S6 | inline | todo — Android storage (Room migration if needed) + backup round-trip | |
| T3 | S3–S5 | inline | todo — Android UI: choose style/setting in the project and in Ask | |
| T4 | S3–S7 | inline | todo — PWA: same model, prompts, backup, UI | |
| T5 | S1–S7 | inline | todo — independent verifier (storage + contract), device check, release | |

## Log

- L1 (2026-11-17, user): "Antes de la release vamos a incluir el tipo de pintado, recuerda que en la app que vimos había varios tipos" Verified in smarthobby-analisis/informe.md §6.3: 4 miniature styles (NMM, OSL, Vivid · High-Fantasy, Guerra · Grimdark) and 3 terrain styles (Invierno, Desierto, Fantasía), closed list. Forja has no style today.
- L2 (2026-11-17, user): "todos". Feature document created.
- L3 (2026-11-17): T1 done. PWA 06529e9: src/projects/style.ts (PAINTING_STYLES, BASE_SETTINGS, lookOf, adviceLookLines, imageLookLines), Project.style/baseSetting optional + setLook (my method stored absent), AdviceRequest extends Look, look section between MOMENT and the answer format, image line after the Style line, backup validation 'project #N has an unknown painting style or base setting'; 194 tests. Android 72c5488: domain/projects/PaintingStyle.kt mirror, Project nullable fields, AdviceRequest style/baseSetting, buildImagePrompt(look), same validation. Fixtures regenerated from the PWA: existing prompt fixtures unchanged byte for byte (my method adds nothing, AI instructions fingerprint unchanged), new prompt-looks.txt and image-prompt-looks.txt pin every style/setting, backup-v1.json project-2 now NMM + desert. Domain 113 tests green.
