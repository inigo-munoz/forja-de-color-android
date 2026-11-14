Objective: native Android rebuild of Forja de Color, same stack/architecture/workflow as Dive App; PWA kept for desktop.
Branch: `main` (new repo, no remote yet) · Delivery: ask-on-risk · Runner: Gradle (`GRADLE_USER_HOME=/media/inigo/Loki/dev-tools/gradle-home ./gradlew test`), JUnit 5 in `:domain`, JUnit 4 + Robolectric in `:app`.
Design: `docs/superpowers/specs/2026-11-14-android-app-design.md`.

## Specs

- S1 — "Rehacerla como app Android nativa en Kotlin, con la misma arquitectura que Dive App y pasando la lógica de la PWA." Parity with every PWA feature (design §2).
- S2 — "Mantené el formato del backup JSON como contrato compartido entre las dos." The app imports a PWA backup and exports one the PWA accepts (`app: 'forja-de-color'`, `version: 1`).
- S3 — "también hay que ver dónde se guardará todo." Storage as in design §6.
- S4 — The app never calls an AI API or the network (PWA principle, README).
- S5 — Prompts sent to the AI are identical to the PWA's (assumption: needed so the user's Claude Project / GPT keeps working).

## Tasks

| ID | Specs | Task | Route | Status | Commit |
|---|---|---|---|---|---|
| T1 | S1 | Gradle scaffold: `:domain` + `:app`, empty app builds, tests run | inline | todo | |
| T2 | S1 | Domain paints: paint, inventory, seed | inline | todo | |
| T3 | S1, S5 | Domain advice: schema, package, image prompt (prompt text pinned against the PWA) | inline | todo | |
| T4 | S1 | Domain advice: parse, resolve | inline | todo | |
| T5 | S1, S2 | Domain projects + backup format, validation, reminder; fixture exported by the PWA | inline | todo | |
| T6 | S2, S3 | Storage: Room, photo files, DataStore, atomic replace-all, auto-backup rules | inline | todo | |
| T7 | S1, S4 | Platform: share bridge, share target, image resize, camera / Photo Picker | inline | todo | |
| T8 | S1 | UI: projects, project (with before/after), ask | inline | todo | |
| T9 | S1, S2 | UI: inventory, settings (export / import with the file picker), technique cards | inline | todo | |
| T10 | S2 | Device check: install, import a real PWA backup, export and import it back in the PWA | inline | todo | |

Route evidence: one developer, sequential dependent tasks, no Writer-trigger reason; everything inline.
Risk notes: T6 touches persisted format (high-risk item 1) — own commit.

## Log

- L1 (2026-11-14, user): "vamos a crear la app de forja de color como estamos creando la de dive app." → clarified: "las dos cosas" (vault + workflow, and native Android) → "dale, empezá con el vault y la app Android" → "arranca directo. tambien hay que ver donde se guardara todo."
- L2 (2026-11-14): vault created at `/media/inigo/Loki/Obsidian/Forja de Color`. Compatibility of share-and-paste, storage and backup with Android verified against the PWA code (`platform/share-bridge.ts`, `storage/*`).
- L3 (2026-11-14): design written; storage decisions in design §6. Repo created at `/media/inigo/Loki/Mis Repos/forja-de-color-android`.
