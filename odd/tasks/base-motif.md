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
| T1 | S3–S6 | inline | todo — PWA domain: motif, prompt lines, image exception, baseMaterials parse | |
| T2 | S3–S6 | inline | todo — Android domain + Room v6 + fixtures | |
| T3 | S3, S4 | inline | todo — UI both apps: motif field, materials in the recipe | |
| T4 | S1–S6 | delegated (verifier) | todo — verifier (items 1, 3), device check, release | |

## Log

- L1 (2026-11-18, user): "si le indico la peana con un motivo, se genera algo?" Verified: base setting adds one line to the advisor and image prompts; no free text; materials not asked (inventory-only rule); image prompt says "do not add, remove or reshape anything".
- L2 (2026-11-18, user): "si, hazlo así". Feature document created.
