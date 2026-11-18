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
- Assumption: a photo picked on the Ask screen is sent alone, as today. (Superseded by S8.)
- S8 — Owner (2026-11-18), correcting S5: "No, lol hace mal, si la miniatura esta a medio piintar las fotos son esas, las de medio pintar, que pueda elegir entre las fotos del proyecto." The Ask screen lets the user choose which of the project's photos to send (main, angles, progress), several at once; by default start/primed select the main photo and angles, half-painted/during the latest progress photo; a newly taken or chosen photo is added to the selection.

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S4, S7 | inline | done — domain both apps | Android 5b3a298; PWA 4c3add5 |
| T2 | S7 | inline | done — Android Room v5 + migration test | 5b3b14a |
| T3 | S5 | inline | done — several images in the package and bridges; Ask sends all angles for start/primed | ae886fb; PWA 4c3add5 + UI |
| T4 | S3, S6 | inline | done — Android UI | ab465dc |
| T5 | S3, S6 | inline | done — PWA UI | see L3 |
| T7 | S8, S9 | inline | done — both apps: choose the photos to send (Choose a project photo dialog/panel) | Android see L6; PWA 3cc54f4 |
| T6 | S1–S8 | delegated (verifier) | partly — verifier ran: 2 blockers fixed (L4); device check, release pending | |

## Log

- L1 (2026-11-18, user): "que se puedan incluir varias fotos para nuevos proyectos, para poder hacer fotos desde varios angulos." Verified: a project keeps one initial photo; the advisor sends one image.
- L2 (2026-11-18, user): "si, hazlo así". Feature document created.
- L3 (2026-11-18): T1–T5 done. Model: extraPhotoBlobIds (Android List with @EncodeDefault NEVER; PWA optional, absent when empty), MAX_ANGLES = 3, photos/photosOf, addAngle/removeAngle, blobIds includes angles (backups and photo clean-up). Backup validation "project #N has invalid extra photos"; fixture backup-v1.json project-1 has angle "photo-angle". Room v5 MIGRATION_4_5 ADD COLUMN extraPhotosJson TEXT NOT NULL DEFAULT '[]'. Share: several images → ACTION_SEND_MULTIPLE (main first, forja-de-color-N.jpg) / Web Share files. Ask: start/primed without a picked photo sends main + angles; half-painted/during the current photo. UI: New project takes up to 4 photos (Android pending list kept across recreation; PWA multiple file input), Photos tab "Angles" add/remove with confirmation (removed file deleted). Tests: RED observed for PWA domain/UI, Android domain and bridge; Room and UI tests written with the code. Android 360 tests, PWA 263 + build. Verifier launched (items 1, 3).
- L4 (2026-11-18): verifier (items 1, 3): schema and backup contract PASS; 2 blockers fixed in one batch: (1) start/primed had stopped sending the latest progress photo even without angles — now angles go only with angles and no progress, otherwise the old choice (regression tests both apps); (2) PWA New project lacked per-photo thumbnails/remove — rebuilt with an append-only photo input (no capture attribute, gallery kept), thumbnails with Remove, max 4 with a notice. Advisory fixed: PWA angle removal keeps a photo another project uses. Android 361, PWA 265.
- L5 (2026-11-18, user): S8 correction — choose the photos on the Ask screen.
- S9 — Owner (2026-11-18): "incluye un botón que sea Elegir foto de proyecto. Y manten hacer foto y Elegir foto(para la galeria del movil)"
- L6 (2026-11-18): T7 done. Ask step 2 (Android): buttons "Elegir foto del proyecto" (dialog with every project photo, newest progress first then main and angles, tap to toggle, up to 4, Done), "Hacer foto", "Elegir foto"; below, the project photos that will be sent. Defaults per moment: start main+angles; primed/during latest progress else main; half-painted latest progress only (none → "current photo" message). A photo taken/chosen here goes first. PWA: same chooser as a panel; its single file input already offers camera or gallery on phones (no capture attribute, an earlier rule). Replaces the automatic angle rule of L4. Android 362 tests, PWA 266. Release 1.7.1 (versionCode 17).
