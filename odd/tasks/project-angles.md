# Project angles — several photos of a new miniature, all sent to the AI

Objective: a project keeps its main photo plus up to 3 extra angles; the advisor sends them all for start and primed. Android and PWA.
Branch: feat/project-angles (on feat/half-painted-context, both repos). Delivery: ask-on-risk. Tests: Gradle, vitest.

## Specs

- S1 — Owner (2026-11-18): "que se puedan incluir varias fotos para nuevos proyectos, para poder hacer fotos desde varios angulos."
- S2 — Owner (2026-11-18), on the proposal: "si, hazlo así".
- S3 — New project: add several photos (take or choose), up to 4, each shown as a thumbnail that can be removed. The first is the main photo.
- S4 — The main photo keeps its uses: list and header, Before/after, "Paint it with ChatGPT".
- S5 — Advisor, moments start and primed with no photo picked on the Ask screen: all the project's photos (main + angles) go to Claude/ChatGPT at once. Half-painted and during keep sending the current photo.
- S6 — Project Photos tab: add or remove angles later (up to 3 besides the main photo).
- S7 — Storage: Android Room migration; backup stays version 1 with an optional field; old backups import; deleting a project or an angle removes its unused photo files.
- Assumption: a photo picked on the Ask screen is sent alone, as today.

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S4, S7 | inline | todo — domain both apps: extraPhotoBlobIds, blobIds, backup validation, fixture | |
| T2 | S7 | inline | todo — Android Room v5 + migration test | |
| T3 | S5 | inline | todo — both apps: several images in the advice package and the share bridges; Ask sends all angles for start/primed | |
| T4 | S3, S6 | inline | todo — Android UI: New project with several photos, angles in the Photos tab | |
| T5 | S3, S6 | inline | todo — PWA UI | |
| T6 | S1–S7 | delegated (verifier) | todo — independent verifier (items 1, 3), device check, release | |

## Log

- L1 (2026-11-18, user): "que se puedan incluir varias fotos para nuevos proyectos, para poder hacer fotos desde varios angulos." Verified: a project keeps one initial photo; the advisor sends one image.
- L2 (2026-11-18, user): "si, hazlo así". Feature document created.
