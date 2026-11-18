# Base motif — a free-text theme for the base, basing materials and terrain in the ChatGPT image

Objective: the project's base can have a free-text motif (lava, graveyard…); the advisor may recommend basing materials the owner may not have; "Paint it with ChatGPT" may add terrain on the base only. Android and PWA.
Branch: feat/base-motif (on feat/project-angles, both repos). Delivery: ask-on-risk. Tests: Gradle, vitest.

## Specs

- S1 — Owner (2026-11-18): "si le indico la peana con un motivo, se genera algo?" → found: only 3 fixed settings, no materials, the image prompt forbids adding anything.
- S2 — Owner (2026-11-18), on the 3 proposals: "si, hazlo así".
- S3 — A free-text base motif next to the base setting (both optional), stored in the project, shown and edited where the style is chosen; it goes into the advisor prompt and the image prompt.
- S4 — When the base has a setting or motif, the advisor may recommend basing materials (sand, static grass, snow, pigments, tufts…) in an optional "baseMaterials" list; the app shows them marked as "you may not have them". Answers without the list keep working.
- S5 — When the base has a setting or motif, the image prompt lets ChatGPT add terrain on the base only, never changing the miniature; without one the prompt stays as it is.
- S6 — Storage: Android Room migration; backup v1 optional field; prompts byte-identical between apps (fixtures); prompts without a base setting/motif unchanged.

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S3–S6 | inline | done — PWA domain: motif, prompt lines, image exception, baseMaterials parse | |
| T2 | S3–S6 | inline | done — Android domain + Room v6 + fixtures | |
| T3 | S3, S4 | inline | done — UI both apps: motif field, materials in the recipe | |
| T4 | S1–S6 | delegated (verifier) | verifier PASS, advisory fixed; release 1.8.1; owner check pending | see L4 |

## Log

- L1 (2026-11-18, user): "si le indico la peana con un motivo, se genera algo?" Verified: base setting adds one line to the advisor and image prompts; no free text; materials not asked (inventory-only rule); image prompt says "do not add, remove or reshape anything".
- L2 (2026-11-18, user): "si, hazlo así". Feature document created.
- L3 (2026-11-18): T1–T3 done. Look.baseMotif (trimmed, no final full stop, max 100; blank → absent); advisor lines "BASE MOTIF: x." and, with a setting or motif, "BASE MATERIALS: … top-level \"baseMaterials\" list …; I may not have them."; image lines "Base theme: x." and "On the base only, you may add terrain that fits it …; leave the miniature itself exactly as it is." (absent without a base look, so plain prompts unchanged). RecipeResponse.baseMaterials optional, parser "recipe: baseMaterials must be a list of materials"; backup "project #N has an invalid base motif". Room v6 MIGRATION_5_6 ADD COLUMN baseMotif TEXT. UI: motif field in the style dialog/fields, summary shows it, recipe shows "For the base (materials you may not have)". Fixtures regenerated (prompt-looks, image-prompt-looks with two motif cases; backup project-2 motif). Tests written first (PWA RED observed). Android 368, PWA 274. Verifier launched.
- L4 (2026-11-18): verifier (items 1, 3): PASS, no blockers. Plain prompts, AI instructions and scan prompt byte-identical to baseline; base-look cases gain only the spec'd lines; migrations from v1–v5 reach v6; backup and parser messages identical. Advisory fixed: with a themed base the image prompt's first line now protects only the miniature ("Keep the exact pose, sculpt and framing of the miniature: do not add, remove or reshape anything on it."), unchanged otherwise (themedBase helper in both apps, RED observed in the PWA). Regression tests added (cleanMotif cuts/dots, blank motif adds no lines). Accepted advisories: BOM/control-char trimming differs between apps on hand-edited backups; a blank "" motif in a hand-edited backup re-exports as "". Scratch removed. Android 369, PWA 275. Release 1.8.1 (versionCode 20).
