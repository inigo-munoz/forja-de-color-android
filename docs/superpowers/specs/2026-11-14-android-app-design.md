# Forja de Color for Android — design

Date: 2026-11-14 · Status: approved to start ("arranca directo"), calls below are the builder's and can be corrected.

## 1. Intent

Forja de Color helps the owner paint miniatures fast with the paints they own: from a photo it
builds a zone-by-zone recipe (Slapchop with diluted paints by default, BSL base/shadow/light on
detail zones). The AI step runs through the user's own Claude / ChatGPT apps by share-and-paste.

A PWA v1 exists (`../forja-de-color`, Vite + TypeScript, GitHub Pages). This project rebuilds it
as a native Android app with the same stack, architecture and workflow as Dive App
(`../dive-app`). The PWA stays alive for desktop use.

## 2. Goals, non-goals, anti-goals

**Goals (v1 = parity with the PWA, plus native wins)**
- Every PWA feature: projects (name + initial photo, notes, progress photos, target image,
  done / reopen), the advisor (moments start / half-painted / during, zone, question, details),
  recipe and consultation rendering, needed paints, unknown paints with "assign one of mine",
  unknown brushes, delete advice, "paint it with ChatGPT" + before/after slider, inventory
  (seeded Vallejo Game Color BSL set + brushes, add / update paint), technique cards, settings
  (export / import backup, 7-day backup reminder, copy AI instructions).
- **Reads and writes the PWA backup format unchanged**, so the app starts with the user's data and
  both versions can exchange it.
- Native wins: share to Claude / ChatGPT with `ACTION_SEND`; receive the AI answer and the painted
  image back through the share sheet ("Share → Forja de Color"); take a photo with the camera or
  pick it with the system Photo Picker.

**Non-goals**
- Desktop, iOS, cloud sync, accounts.
- New features beyond the PWA other than the native wins above.

**Anti-goals (a failure even if it works)**
- The app calls an AI API or any network service.
- A backup exported by one version cannot be imported by the other.
- Data loss on import: an invalid file must never touch existing data.

## 3. Constraints

- No network permission at all.
- Technique card text is the user's, written from their BSL book, never AI-generated. The cards
  are still `TODO(user)` in the PWA and carry over as such.
- Prompts sent to the AI stay byte-identical to the PWA's (`advice/package.ts`,
  `advice/image-prompt.ts`), so the user's Claude Project / custom GPT keeps working.
- UI copy stays in English like the PWA (`ui/copy.ts`), kept in `strings.xml` so a Spanish
  translation is a resource change. AI-written texts are in Spanish, as the prompt asks.

## 4. Approaches considered

1. **Native Kotlin, Dive App architecture (picked by the owner).** Same toolchain and patterns
   as Dive App; the PWA's pure TypeScript domain ports almost line by line to `:domain`, and its
   Vitest suites serve as the spec for the JUnit ones.
2. **Wrap the PWA (TWA / Capacitor).** Cheapest, keeps one codebase, but it is not "like Dive
   App" and keeps the Web Share / clipboard limits.
3. **Kotlin Multiplatform with a web target.** One domain for both, but a large detour for a
   personal app with a working PWA.

## 5. Architecture

Modules (as in Dive App, without `:content`):

- `:domain` — pure Kotlin, no Android. Ports of `paints/`, `projects/`, `advice/`
  (schema, package, image prompt, parse, resolve), `storage/backup` (format, validation,
  reminder) and the ports `Repository` and `AdvisorBridge`. kotlinx.serialization for JSON.
- `:app` — Android adapters and UI: Room repository, photo store, share bridge, share-target
  receiver, image resizing, Compose screens, navigation.

Package / application id: `app.forjadecolor`. minSdk 26, compile/target 37, JDK 17, same
version catalog as Dive App.

Screens (Navigation Compose): Projects · Project · Ask · Inventory · Settings · Technique card.

## 6. Where everything is stored

| What | Where | Why |
|---|---|---|
| Source code | `/media/inigo/Loki/Mis Repos/forja-de-color-android`, branch `main`. GitHub remote only when the owner authorizes it. | Separate repo like Dive App; the PWA repo keeps its Pages deploy untouched. |
| Inventory, projects, advice, progress-photo metadata | Room database `forja.db` in the app's internal storage. Tables: `paints`, `brushes`, `projects`, `progress_photos`, `advice_entries` (the AI response kept as its JSON text). | Same as Dive App; queryable; migrations under test. |
| Photos and painted images | JPEG files in `filesDir/photos/<blobId>.jpg`, referenced by id from Room. Resized to 1600 px on the long side, quality 85, as in the PWA. | Keeps images out of SQLite; ids map 1:1 to the backup's `blobs`. |
| Small settings (`lastExportAt`) | DataStore Preferences. | Same as Dive App. |
| Full backup (data + photos) | A JSON file in the PWA format, written where the user chooses with the system file picker (Downloads, Google Drive…), named `forja-de-color-YYYY-MM-DD.json`. Reminder after 7 days without exporting. | The shared contract with the PWA, and the only backup that always includes photos. |
| Android automatic backup | On, for the database and settings only; photos are excluded. | Android stops backing up an app that exceeds 25 MB, and photos would reach that quickly. A restore without photos still shows every project; missing images show a placeholder. |
| Seed inventory and technique cards | Bundled in the app (`:domain` seed, card Markdown in `assets/cards/`). | Read-only content. |

Import replaces everything in one Room transaction plus a staged photo directory that is swapped
in only after the transaction commits; a file that fails validation changes nothing.

## 7. Behaviour carried over exactly

- Paint identity: `normalizeCode` (Vallejo `72003` → `72.003`), `paintKey`, add-or-replace,
  loose lookup by `NN.NNN` token.
- Parse of the pasted answer: prompt-marker rejection, fenced or bare JSON extraction, the same
  normalization (`"1"` version, `1,5:3` dilutions) and every validation message, the
  owned-paint resolution and unknown paints / brushes.
- Backup validation messages and the replace-all-or-nothing import.
- Progress photos sorted by date; advice entries appended.

## 8. Left to the builder

Visual design (Material 3, a palette fitting "forge of colour"), the launcher icon, screen layout
details, and the internal Room schema beyond the table list above.
