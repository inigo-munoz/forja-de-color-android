# Paint catalogue — Vallejo, Citadel and Army Painter lists saved in the app, searched before the AI

Objective: the app carries the paint lists of Vallejo, Citadel and Army Painter; adding a paint (by hand or from a photo) looks there first and only then relies on the AI. Android and PWA, same data.
Branch: feat/paint-catalogue (from feat/paint-names, both repos). Delivery: ask-on-risk. Tests: Gradle, vitest.

## Specs

- S1 — Owner (2026-11-17): "quiero que hagas la revisión de todo lo que tengo en el movil ahora mismo. Si hace falta descarga de los principales fabricantes sus listas de pinturas para tenerlas ya guardadas y buscarlas primero en la app y luego en la IA"
- S2 — Owner (2026-11-17), on the proposal to use github.com/Arcturus5404/miniature-paints (MIT) starting with Vallejo, Citadel and Army Painter: "dale".
- S3 — The catalogue is generated from that dataset (commit pinned, MIT notice kept) into one JSON file, identical in both apps: brand, code, English name, range, type (inferred from the range: wash, metallic, primer, medium…), approximate colour. Citadel has no codes: its name is its code.
- S4 — Adding a paint by hand: a search box finds catalogue paints by code or name (ignoring case and accents); picking one fills brand, code, names, range, type and colour, which the user can still edit before saving.
- S5 — Adding from a photo: each paint the AI read is looked up in the catalogue first (brand + code, or brand + name for Citadel); when found, the catalogue's code, English name, range and type win and the list says it was checked in the catalogue; the AI's data is used only for paints not in the catalogue.
- S6 — Spanish name: the catalogue has none; a catalogue paint keeps the Spanish name the AI gave, else the seed's Spanish name for the BSL codes, else the English name.
- Assumption: the catalogue colour is approximate (some are clearly off, e.g. Nuln Oil); it fills the colour but the user sees and can correct it before saving. The AI scan prompt is not changed.

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S3 | inline | done — generator script + catalogue JSON in both apps, license notice | b2c28b6; PWA 2fe1e92 |
| T2 | S3–S6 | inline | done — domain both apps: load, search, find, enrich scanned paints | b2c28b6; PWA 2fe1e92 |
| T3 | S4, S5 | inline | done — Android UI: catalogue search in the add form, "checked in the catalogue" in the scan list | b67858d |
| T4 | S4, S5 | inline | done — PWA UI, same | PWA 54d3d9c |
| T5 | S1–S6 | inline | partly — suites green, release 1.5.0 installed; on-device check pending (phone locked) | |

## Log

- L1 (2026-11-17, user): request in S1. Owner's paints reviewed (see odd/tasks/paint-names.md L6, L8). Dataset found: Arcturus5404/miniature-paints (MIT, 35 brands, last push 2025-03), pinned commit 22791a682e1d155da388f4f856cd7196cd9f2677. Vallejo 1279 lines (Game Color 144, Model Color 222, …), Citadel 463 (no codes), Army Painter 715 (some codes "null"). English names only.
- L2 (2026-11-17, user): "dale". Feature document created.
- L3 (2026-11-17): T1–T4 done. tools/gen-catalogue.py builds catalogue.json (2424 paints: Vallejo 1268, Army Painter 704, Citadel 452; rows [brand, code, nameEn, range, type, hex]; Citadel/name-only codes use the name; airbrush versions get "(Air)" so the brush-on paint keeps the plain name) into domain/src/main/resources and the PWA src/paints, byte-identical, with catalogue-LICENSE.txt (MIT). Domain both apps: findInCatalogue (code any format, else name; brand aliases), searchCatalogue, paintFromCatalogue (Spanish: given, else seed, else English), lookUpInCatalogue, checkInCatalogue on scans. UI: catalogue search in the add form (Android only when adding), "Checked in the catalogue" in the scan list. Tests written first; RED observed for PWA domain and UI, not separately for the Android domain/UI and the scan check. Test examples changed because the catalogue now acts on them: invented code 72.090 "Black Wash" is really 72.090 Black Green (now 73.201 Black Wash); Army Painter "Dragon Red" gets its code from the catalogue (incomplete example moved to Scale75); owned "Dead White" keeps its Spanish name. ProjectScreenTest "the selected tab survives recreation" failed once in a full run and passed 3/3 alone: flaky, unrelated. Android 337 tests, PWA 239. Release 1.5.0 (versionCode 9, APK 1.9 MB, catalogue packaged) installed; on-device smoke not run (phone dozing). Known limits: catalogue colours approximate (e.g. Nuln Oil grey); a name shared by several ranges matches the first one.
