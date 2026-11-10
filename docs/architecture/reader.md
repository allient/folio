---
title: "PDF and EPUB reader"
description: "How Folio’s PDF and EPUB reader works: rendering, highlights and margin notes, search, OCR, bookmarks and export."
---

Android now has a Library for books and PDFs alongside notebooks. Books can be organized in the
same folders used by Knotes notebooks, and importing while a folder is selected files the new book
there. Book cards show a two-line title limit and reading percentage. Imported PDF files remain PDFs;
EPUB files are opened with Readium. Importing inside a notebook deliberately retains the existing
PDF-to-notebook-page conversion workflow. Older rasterized PDF imports cannot recover their original
text: reimport the original file into Books & PDFs.

## Engines and storage

- PDFium Android 2.0.3: native page rendering, selection geometry, text search. Visible page bitmaps are
  loaded on demand for single pages, vertical scrolling or two-page spreads, capped at 2560px wide and 4096px tall. Native documents/pages/text handles are closed.
- Readium Kotlin 3.4.0: established EPUB navigator, publication parsing, locator anchors, decoration
  highlights, table of contents, chapter search, reflow and reader preferences.
- PDFBox Android 2.0.27.0: background PDF annotation export only. Original text/vector content is retained;
  highlight, underline, ink and text comments are standard PDF annotations, not painted screenshots.
- ML Kit bundled Latin recognition 16.0.1: explicit on-device recognition of scanned PDF pages.
  OCR word boxes are bounded in memory to three pages; saved highlight geometry persists independently.
- Existing SQLite infrastructure: separate versioned reader database with WAL, indexed annotation rows,
  foreign keys and atomic writes. A process-wide ordered queue allows accepted writes to finish after
  an Activity closes. Room has not been added as an unused dependency or forced migration.
- Shared Kotlin document/annotation presentation models live in `apps/mobile/shared/.../reader`.

Originals are copied into private app storage with SHA-256 identities. Duplicate imports reuse the
library record. Import limits: 128 MiB per original; EPUB archive limits: 20,000 entries, 64 MiB per
resource and 512 MiB expanded. EPUB path traversal is rejected. Reader documents and annotations
influence the automatic backup signature. Library backups keep reader titles, content hashes, source URI
references, Knotes folder assignments, reading positions, bookmarks and annotations, but never
include PDF/EPUB bytes or covers. Restored books appear as unavailable until their original is
reimported; a matching content hash reconnects it to saved annotations, folder and progress.

## Features

- Open a folder’s options → **Edit folder** → **Mosaic**, then save. This general folder style shows a 2×2 mosaic of up to four recent items: All mixes notebook pages, canvases and book covers; Notes, Canvas and Books show their matching types. The editor previews the actual items, or sample tiles before items are added. Empty folders retain their artwork; deleted items are excluded. The style is saved per folder and works in grid and list views.
- PDF/EPUB filtering, clearable title/author search, shared folder organization, covers, and cover-level reading progress.
- PDF page navigation by horizontal swipe, page arrows, or page jump; pinch zoom and pan, text-range selection, highlights and underlines. Swipes only turn pages in Read mode at normal zoom so selection, ink, and zoomed panning retain their gestures.
- PDF/EPUB Contents uses chapter titles and nested, expandable subchapters when provided by the document. PDF outline destinations drive page navigation and the footer chapter label. PDFs without an outline explicitly fall back to page browsing. Tap outside Contents to dismiss it.
- Browser-style open-book tabs appear with the reader controls; **More → Show book tabs** saves their visibility preference. The pinned "N ▾" button opens a drop-down with Hide tab bar, Close other tabs and every open book (switch, or close from its "⋯" menu). Tabs remember opened books, switch without returning to the library, and can be closed without deleting a book. Reading positions and annotations remain saved.
- Gestures has saved controls for stylus writing (PDF), click/long press, sliding/dragging, pinch zoom (PDF), system bars, bottom and side gestures, and Back. Center tap always exposes controls as a recovery path; bottom swipe reveals controls; side taps navigate. Disabling system-bar gestures keeps Android bars visible; disabling Back opens Gestures instead of exiting.
- PDF display adjustments: Embolden and Sharpen Img (0–5), Watermark Bleaching and Dark Color Enhancement (0–100), and Image Smoothing. Adjustments are saved on the device, processed on workers, and do not change originals, OCR text or exports. Reset restores neutral display settings.
- The reading footer shows local time, battery percentage, chapter when available, and page/progress position instead of the full book title. It updates with system time and battery broadcasts while visible.
- Selection actions use a compact two-row icon toolbar anchored near the selected text, above when space permits and below otherwise, clamped within screen margins: underline styles, highlight, color palette and remove marks above; copy, note, in-book search, share and dismiss below. Unsupported EPUB underline styles are omitted.
- PDF ink, notes at a location, bookmarks, annotation list, editing/deleting note comments.
- EPUB selection menu for highlights/notes, persistent locator-based decorations and bookmarks.
- EPUB font size, line spacing, continuous/paged reading, light/sepia/dark themes.
- EPUB page turns by horizontal swipe in paged mode, with arrows retained as an accessible alternative; continuous mode remains vertically scrollable.
- Study view splits reading from the full Knotes notebook editor. It creates a saved notebook for the current book and seeds its page with the book title, page/progress reference, and selected passage when available.
- Search within a document with cancellable queries and bounded result lists.
- Markdown note export and annotated PDF sharing.
- Android Open With handling for PDF/EPUB content URIs.

## Verification

Build: `./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest :shared:testAndroidHostTest`

Instrumentation: `ReaderTest` exercises originals/deduplication, selection/search, persistence across
reading-position updates, annotated PDF export preserving text, EPUB import/anchors, archive traversal
rejection and full library backup/restore. `InkCanvasViewTest` covers pencil movement, small-selection
hit targets, resizing, cancellation, layer safety and pressure cutoff.

Run device tests on a disposable emulator. Gradle's `leaveApksInstalledAfterRun` is set to preserve
installations, but verify runner behavior before using any device with valuable local data. For
manual test runs, install app/test APKs with `adb install -r`, then invoke the instrumentation runner
without an uninstall or clear step.

## Remaining release work

This is an Android implementation. The iOS target remains the pre-existing starter shell: integrate
PDFKit and Readium Swift with a matching persistence adapter before advertising iOS reader support.
DRM/Kindle formats and password-protected PDF opening are not supported in this iteration. OCR is
Latin-script only, invoked per page; OCR text is not embedded back into the original PDF. PDF navigation
is single-page; tiled deep zoom is a future enhancement. Saved ink can be erased stroke by stroke or
removed from the annotation list. Existing external
PDF annotations are rendered, but importing/editing their semantic records is not implemented.

The open-source PDFBox Android port's latest published version is older than the rendering libraries;
keep it isolated to export, monitor upstream security changes and validate complex/encrypted PDFs
before release. No commercial SDK subscription, backend, cloud sync or tracking service was added.
Release builds should use AAB ABI delivery to avoid shipping every PDFium/ML Kit architecture to a device.

## PDF Format controls

The Format panel offers Single-page and Scrolling View, LTR/RTL two-page spreads with horizontal swipes, Zoom In/Out,
Marquee Zoom, Fit Screen, Fit Width, Marquee Crop, Reset, Margin (0–16) and Text Enhancement (0–17).
Draw a marquee to zoom into an area. Marquee Crop previews a rectangle with draggable
corners; Apply crop (checkmark) confirms it, while Cancel or Back leaves the saved crop
unchanged. While a confirmed crop is active, the Crop row shows only Marquee Crop and
Reset, including after reopening the reader. Reset restores the full page, normal zoom and
zero margin, and brings back Fit Screen and Fit Width. Crops affect only
the reading view; originals, annotation coordinates and exports remain unchanged.

LTR puts the earlier page on the left; RTL puts it on the right and reverses horizontal paging.
Single-page exits spreads; Scrolling View shows individual pages vertically.

PDF display, spread mode, direction, zoom, confirmed crop, margin, text enhancement, contrast, brightness
and split orientation are saved locally as reader-wide preferences and restored on reopening.
EPUB keeps its reflow controls. Scrolling View renders only visible PDF pages and retains
the current page when switching back to Single-page.

## Split View

Split View offers Current Doc (a second independently navigable view of the open PDF/EPUB),
Another Doc (choose a different library PDF/EPUB), Handwritten Notes (the saved study notebook),
and Text Notes (location-linked notes and a locally saved draft). Save note attaches text to the
primary reader’s current location. Close split view returns to one reader. Translation and AI Read
are not included.

Horizontal places panes side by side; Vertical stacks them, including on phones. Landscape left
and Landscape right lock the reader to that landscape direction; Auto follows device rotation.
The layout and orientation are saved, and an active split reopens with its original document.
Secondary navigation does not overwrite the main reader’s reading position.

On Android 16 and later, the reader follows display rotation and disables app-level landscape locks. Keep the
reader adaptive: opting into `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` letterboxes
its entire window on portrait tablets. Devices on Android 15 and earlier retain landscape left/right locks. See [Android’s large-screen behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16#large-screens).

### Text selection gestures

In PDF Read mode, holding text activates selection before the finger lifts; subsequent movement extends the range without panning or turning pages. Ordinary swipes retain navigation. Both selection handles can be dragged while the non-modal, horizontally scrollable toolbar stays open. The toolbar supports colored highlights, solid/dashed/wavy underlines, notes, copy, share and removing intersecting text marks on the same page. Long-press an existing mark to edit or delete it. Scans require Recognize text first.

Underline variants are stored as the optional `underlineStyle` field (`solid`, `dashed`, `wavy`) in the existing PDF anchor JSON. Missing values retain solid rendering. Wavy lines export as standard Squiggly annotations; dashed lines use Underline with dashed border styling (external PDF readers may render these as solid). Selection geometry and exports retain original page coordinates under zoom and crop. EPUB keeps Readium selection handles and offers Highlight, Underline and Add note; dashed/wavy actions are PDF-only.

`ReaderSelectionTest` checks hold-then-drag, adjusting the range with a handle, and removing only intersecting text marks. `ReaderTest` verifies that all three underline styles survive storage reopening and export as editable PDF annotations without changing the original. Colors and actions occupy separate toolbar rows so actions remain immediately reachable on phones.

`ReaderSplitViewTest` verifies that a Current Doc split restores both PDF views and the
full-display sizing despite saved landscape settings after recreation on Android 16 tablets.

Reader Normal mode has neither fit control selected. Fit Screen and Fit Width detect the
visible PDF content on a worker and trim blank margins (including scanned pages). Fit Screen
fits that content within the viewport; Fit Width fills its width. Reset clears fitting and
manual crop and returns to Normal. Reset Zoom clears zoom while retaining crop/fit settings.

## PDF ink with notebook pens

The drawing bar uses the notebook's shared brush presets (`editor` preferences, `BrushPresetBar`,
`BrushSettings`), so pens added or changed in either place appear in both. The reader remembers its last
pen as `reader-selected-brush` in `reader-preferences`. An ink annotation's anchor keeps the legacy keys
(`page`, `points` as `[x, y]` pairs, `width`, `normalizedWidth`) and, for brush ink, adds `brush` (brush
type name), `engine`, `variant`, `texture`, `grain`, `angle`, `sensitivity`, `pressures` (one value in 0–1 per point) and `times` (milliseconds since pen-down, one per
point, so the saved stroke replays exactly like the live one). The annotation colour is the pen colour. Anchors without `brush` are legacy ink and render
as a plain polyline, unchanged; no database migration is needed. Annotated PDF export flattens brush ink to
plain PDF Ink annotations (colour and width only).
