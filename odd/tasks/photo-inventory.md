# Photo inventory — add paints and brushes from a photo with the AI

Objective: add paints and brushes to "My paints" from a photo, via the existing share-and-paste AI flow, always confirmed by the user before saving. Android first, then the PWA.
Branch: feat/photo-inventory (both repos). Delivery: ask-on-risk. Tests: Gradle (JUnit + Robolectric) on Android, vitest on the PWA.

## Specs

- S1 — Owner (2026-11-17): "alguna mejora que nos falte? Por ejemplo incluir pinturas y pinceles con la camara y la IA?"
- S2 — Owner (2026-11-17), accepting the proposal: "si, con la confirmación por parte del usuario."
- S3 — Agreed flow (proposal accepted in S2): take or choose a photo of paint bottles or brushes → the app shares it with Claude or ChatGPT with fixed instructions asking for a list (brand, code, name, type, approximate colour) → the user pastes the answer → the app shows the list so the user confirms, corrects or discards each item → only confirmed items are saved. No API keys.
- S4 — Brushes can be added and edited by hand (today they cannot; part of the accepted proposal).
- S5 — Saving a scanned paint reuses the existing add/update rule (normalized code; an existing paint keeps its BSL family), and a brush with an existing code is updated, not duplicated.
- S6 — The PWA gets the same feature; the scan instructions are byte-identical in both apps (shared prompt contract).
- Assumption: the AI answer is a JSON block, parsed with the same hardening as advice answers (bounded size, unknown fields ignored); an unreadable answer shows an error and saves nothing.

## Tasks

| ID | Specs | Route | Status | Commit |
|----|-------|-------|--------|--------|
| T1 | S3, S5 | inline | done — domain: scan prompt, answer parser, merge (addPaint / addBrush) | f97d838 |
| T2 | S3 | inline | todo — Android: scan screen (photo, share, paste) | |
| T3 | S2, S3 | inline | todo — Android: confirmation list (confirm / correct / discard), save | |
| T4 | S4 | inline | done — Android: add/edit brush form | bf3bdff |
| T5 | S6 | inline | todo — PWA port, prompt fixtures shared | |
| T6 | S1–S6 | inline | todo — device check on the Pixel, release | |

## Log

- L1 (2026-11-17, user): "alguna mejora que nos falte? Por ejemplo incluir pinturas y pinceles con la camara y la IA?" Verified: brushes cannot be added or edited (only the 3 seed brushes are listed); paints are added one by one with a form. Proposed the share-and-paste scan with confirmation; barcode reading discarded for now (no paint database).
- L2 (2026-11-17, user): "si, con la confirmación por parte del usuario." Feature document created; Android first.
- L3 (2026-11-17): T1 done in f97d838. domain/scan/InventoryScan.kt (buildScanPrompt, parseScanAnswer: 100k-char cap, prompt-paste detection, reuses advice extractJson, lenient items, dedupe, max 200; applyScan skips incomplete items), InventoryOps savePaintKeepingFamily (moved from the app's savePaint, now reused there), findBrush/addBrush. RED (unresolved refs) → GREEN: 8 tests; inventory UI tests still green.
- L4 (2026-11-17): T4 done in bf3bdff: BrushForm.kt (BrushDraft, BrushFormDialog), '+ Add brush' and tap-to-edit in My paints, InventoryViewModel.saveBrush via addBrush. RED 3/3 failing → GREEN 3/3; PaintFormDialog/FormField/DraftSaver made internal for reuse in the scan confirmation.
